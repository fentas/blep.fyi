package fyi.blep

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fyi.blep.core.ble.DeviceAliases
import fyi.blep.core.ble.DeviceFlags
import fyi.blep.core.ble.createBleScanner
import fyi.blep.core.platform.createKeyValueStore
import fyi.blep.core.safety.SafetyHistory
import fyi.blep.core.safety.SafetyScanner
import fyi.blep.core.safety.Severity
import fyi.blep.core.safety.TrackerDetector
import fyi.blep.core.sync.SyncMessage
import fyi.blep.core.sync.SyncSettings
import fyi.blep.core.sync.SyncTransport
import fyi.blep.core.sync.createSyncTransport
import fyi.blep.core.tether.DeviceTether
import fyi.blep.core.tether.PresenceMonitor
import fyi.blep.core.tether.TetherAlertDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getString
import fyi.blep.resources.Res
import fyi.blep.resources.noti_alert_text
import fyi.blep.resources.noti_alert_title
import fyi.blep.resources.noti_channel_alert
import fyi.blep.resources.noti_channel_ongoing
import fyi.blep.resources.noti_channel_tether
import fyi.blep.resources.noti_channel_tether_info
import fyi.blep.resources.noti_flagged_text
import fyi.blep.resources.noti_flagged_title
import fyi.blep.resources.noti_ongoing_text
import fyi.blep.resources.noti_ongoing_watch
import fyi.blep.resources.noti_tether_back_text
import fyi.blep.resources.noti_tether_back_title
import fyi.blep.resources.noti_tether_left_text
import fyi.blep.resources.noti_tether_left_title
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "blep.background.scan"
private const val CH_ALERT = "blep.safety.alert"
private const val CH_ONGOING = "blep.safety.ongoing"
private const val CH_TETHER = "blep.tether.alert"       // leave: high importance (buzz/ring)
private const val CH_TETHER_INFO = "blep.tether.info"   // return: low importance (quiet)
private const val NOTI_ALERT = 1001
private const val NOTI_ONGOING = 1002
private const val NOTI_FLAGGED = 1003
private const val NOTI_TETHER_BASE = 2000               // + per-device hash, so devices don't collide
private const val SCAN_WINDOW_MS = 20_000L
private const val BONDED_REFRESH_MS = 30_000L

actual object BackgroundScan {
    actual fun applyPeriodic(enabled: Boolean, intervalMinutes: Int) {
        val ctx = BlepApp.ctx ?: return
        val wm = WorkManager.getInstance(ctx)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val minutes = intervalMinutes.toLong().coerceAtLeast(15) // WorkManager floor
        val req = PeriodicWorkRequestBuilder<BackgroundScanWorker>(minutes, TimeUnit.MINUTES).build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    actual fun setForeground(active: Boolean) {
        val ctx = BlepApp.ctx ?: return
        val intent = Intent(ctx, BackgroundScanService::class.java)
        if (active) ContextCompat.startForegroundService(ctx, intent) else ctx.stopService(intent)
    }
}

/** Periodic (app-closed) check: scan a short window for a following tracker, notify. */
class BackgroundScanWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        // Honor battery saver: the periodic (ambient) check skips its cycle while the
        // device is in power-save mode. The user-initiated foreground service is left
        // alone — it's a deliberate, visible "watch closely now" action.
        val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm?.isPowerSaveMode == true) return Result.success()
        val ctx = applicationContext
        val text = loadNotiText()
        val scanner = createBleScanner()
        val settings = AppSettings()
        val tether = DeviceTether(createKeyValueStore())
        val monitor = PresenceMonitor(createKeyValueStore())
        val aliases = DeviceAliases(createKeyValueStore())
        coroutineScope {
            // Safety: alert on a confirmed following tracker.
            launch {
                val safety = SafetyScanner(scanner, TrackerDetector(settings.scanSensitivity().tuning), SafetyHistory(createKeyValueStore()))
                val hit = withTimeoutOrNull(SCAN_WINDOW_MS) {
                    safety.alerts().first { list -> list.any { it.severity == Severity.ALERT } }
                }
                hit?.firstOrNull { it.severity == Severity.ALERT }?.let { notifyTracker(ctx, text, it.trackingAddress) }
            }
            // Tether: watch this window for a tethered device leaving/returning. Interval
            // granularity, so a leave surfaces on the next cycle — acceptable off-screen.
            if (tether.ids().isNotEmpty()) {
                launch {
                    val btAdapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
                    val bonded = runCatching { btAdapter?.bondedDevices?.map { it.address }?.toSet() }.getOrNull().orEmpty()
                    withTimeoutOrNull(SCAN_WINDOW_MS) {
                        scanner.devices(includeUnnamed = true, measureConnectedSignal = false).collect { list ->
                            checkTethers(ctx, text, list, tether, monitor, aliases, settings.tetherAlert(), bonded)
                        }
                    }
                }
            }
        }
        return Result.success()
    }
}

/** Foreground service: keeps a safety scan alive when the app isn't on screen. */
class BackgroundScanService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var aclWatch: AclTetherWatch? = null
    private var workJob: Job? = null
    private var sync: SyncTransport? = null

    /** Relay an event to the paired watch (item 5: phone detects, watch buzzes), if the
     *  user left tracker-alert sync on. Fire-and-forget. */
    private fun relay(msg: SyncMessage) {
        if (SyncSettings(createKeyValueStore()).alerts()) sync?.sendMessage(msg.encode())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A tether/flag toggle re-delivers onStartCommand; tear down the previous work first
        // so we re-evaluate what to run (and never stack duplicate scans).
        workJob?.cancel()
        aclWatch?.unregister()
        // Load the localized notification text (shared Compose resources, suspend) and
        // go foreground first — an in-memory resource read is well within the FGS
        // start window — then run only what's needed.
        workJob = scope.launch {
            val text = loadNotiText()
            ensureChannels(this@BackgroundScanService, text)
            // Tracker scanning is governed solely by the "keep scanning when minimized"
            // setting — only the foreground service honours it (the interval worker has its
            // own toggle; the in-app safety screen is always available). The ongoing
            // notification reflects whether we're scanning for trackers or just watching.
            val trackerScan = AppSettings().foregroundScan()
            // startForeground can be rejected — the connected-device FGS type needs BLE
            // permission, which may not be granted yet — so fail soft, never crash.
            val started = runCatching {
                ServiceCompat.startForeground(
                    this@BackgroundScanService, NOTI_ONGOING, ongoingNotification(this@BackgroundScanService, text, trackerScan),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
                )
            }.isSuccess
            if (!started) {
                stopSelf()
                return@launch
            }
            val scanner = createBleScanner()
            val safety = SafetyScanner(scanner, TrackerDetector(AppSettings().scanSensitivity().tuning), SafetyHistory(createKeyValueStore()))
            val flagStore = DeviceFlags(createKeyValueStore())
            val aliasStore = DeviceAliases(createKeyValueStore())
            val tetherStore = DeviceTether(createKeyValueStore())
            val presence = PresenceMonitor(createKeyValueStore())
            // Bonded tethered devices are caught by their connection-state (ACL) events —
            // event-driven, scan-free, rotation-proof — so they're excluded from the scan
            // path below. Unbonded advertising tags stay on the scan-based monitor.
            val btAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
            val bondedStart = runCatching { btAdapter?.bondedDevices?.map { it.address }?.toSet() }.getOrNull().orEmpty()
            sync = createSyncTransport()
            aclWatch = AclTetherWatch(
                this@BackgroundScanService, scope,
                onLeft = { id, name -> notifyTetherLeft(this@BackgroundScanService, text, name, id); relay(SyncMessage.TetherLeft(id, name)) },
                onReturned = { id, name -> notifyTetherReturned(this@BackgroundScanService, text, name, id); relay(SyncMessage.TetherReturned(id, name)) },
            ).also { it.register() }
            // Tracker (safety) scan — the battery-heavy continuous scan. Run it ONLY when
            // tracker-scanning is actually enabled, so a service that's up purely to watch a
            // tethered device doesn't drag in a scan the user never asked for.
            if (trackerScan) {
                launch {
                    runCatching {
                        safety.alerts().collect { list ->
                            list.firstOrNull { it.severity == Severity.ALERT }?.let {
                                notifyTracker(this@BackgroundScanService, text, it.trackingAddress)
                                relay(SyncMessage.TrackerAlert(it.label)) // buzz the watch too
                            }
                        }
                    }
                }
            }
            // Flag watch (ongoing in-range notice) + unbonded-tether watch share one scan
            // stream — but only spin it up when something actually needs scanning: a flag, or
            // a tether on an UNbonded advertising tag. Bonded tethers ride the ACL hook above
            // (no scan), so tethering only paired devices runs the service completely scan-free.
            val needScan = flagStore.ids().isNotEmpty() || (tetherStore.ids() - bondedStart).isNotEmpty()
            if (needScan) {
                launch {
                    // The bonded set can change while the service runs (a new pairing), but a
                    // Bluetooth-stack IPC per scan emission is waste — refresh it on a timer.
                    var bonded = bondedStart
                    var bondedAtMs = android.os.SystemClock.elapsedRealtime()
                    runCatching {
                        scanner.devices(includeUnnamed = true, measureConnectedSignal = false).collect { list ->
                            val here = list.firstOrNull { it.id in flagStore.ids() && it.isPresent }
                            if (here != null) notifyFlagged(this@BackgroundScanService, text, aliasStore.of(here.id) ?: here.displayName)
                            else cancelFlagged(this@BackgroundScanService)
                            val tick = android.os.SystemClock.elapsedRealtime()
                            if (tick - bondedAtMs >= BONDED_REFRESH_MS) {
                                bonded = runCatching { btAdapter?.bondedDevices?.map { it.address }?.toSet() }.getOrNull() ?: bonded
                                bondedAtMs = tick
                            }
                            checkTethers(this@BackgroundScanService, text, list, tetherStore, presence, aliasStore, AppSettings().tetherAlert(), bonded, onEvent = { relay(it) })
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        aclWatch?.unregister()
        sync?.close()
        scope.cancel()
        super.onDestroy()
    }
}

// ── notifications ────────────────────────────────────────────────────────────
// Text comes from the shared, 14-locale Compose resources (the same set the UI
// uses) — read in a coroutine via the suspend [loadNotiText], then passed to the
// pure builders below. No Android R / per-module string duplication.
private data class NotiText(
    val channelAlert: String,
    val channelOngoing: String,
    val channelTether: String,
    val channelTetherInfo: String,
    val ongoingText: String,
    val ongoingWatchText: String,
    val alertTitle: String,
    val alertText: String,
    val flaggedTitle: String,
    val flaggedText: String,
    val tetherLeftTitle: String,
    val tetherLeftText: String,
    val tetherBackTitle: String,
    val tetherBackText: String,
)

private suspend fun loadNotiText() = NotiText(
    channelAlert = getString(Res.string.noti_channel_alert),
    channelOngoing = getString(Res.string.noti_channel_ongoing),
    channelTether = getString(Res.string.noti_channel_tether),
    channelTetherInfo = getString(Res.string.noti_channel_tether_info),
    ongoingText = getString(Res.string.noti_ongoing_text),
    ongoingWatchText = getString(Res.string.noti_ongoing_watch),
    alertTitle = getString(Res.string.noti_alert_title),
    alertText = getString(Res.string.noti_alert_text),
    flaggedTitle = getString(Res.string.noti_flagged_title),
    flaggedText = getString(Res.string.noti_flagged_text),
    tetherLeftTitle = getString(Res.string.noti_tether_left_title),
    tetherLeftText = getString(Res.string.noti_tether_left_text),
    tetherBackTitle = getString(Res.string.noti_tether_back_title),
    tetherBackText = getString(Res.string.noti_tether_back_text),
)

/** The app's display name from its manifest label — single source of truth, no
 *  duplicated app_name string in this library. */
private fun appLabel(ctx: Context): String =
    ctx.applicationInfo.loadLabel(ctx.packageManager).toString()

private fun ensureChannels(ctx: Context, t: NotiText) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
    nm.createNotificationChannel(NotificationChannel(CH_ALERT, t.channelAlert, NotificationManager.IMPORTANCE_HIGH))
    nm.createNotificationChannel(NotificationChannel(CH_ONGOING, t.channelOngoing, NotificationManager.IMPORTANCE_LOW))
    // Leave alert buzzes/rings per the user's channel settings; the return notice is quiet.
    nm.createNotificationChannel(NotificationChannel(CH_TETHER, t.channelTether, NotificationManager.IMPORTANCE_HIGH))
    nm.createNotificationChannel(NotificationChannel(CH_TETHER_INFO, t.channelTetherInfo, NotificationManager.IMPORTANCE_LOW))
}

/** Stable per-device notification id so a device's leave/return notices replace each
 *  other and different devices don't collide. */
private fun tetherNotiId(deviceId: String): Int = NOTI_TETHER_BASE + (deviceId.hashCode() and 0x3FFF)

/**
 * Run one scan-list sample through the [PresenceMonitor] and fire any leave/return
 * notification the [dir] setting allows. Shared by the foreground service (continuous)
 * and the periodic worker (interval). Re-reads the tether set each call so toggles take
 * effect live.
 */
private fun checkTethers(
    ctx: Context,
    t: NotiText,
    list: List<fyi.blep.core.model.BleDevice>,
    tether: DeviceTether,
    monitor: PresenceMonitor,
    aliases: DeviceAliases,
    dir: TetherAlertDirection,
    bondedExcluded: Set<String> = emptySet(),
    onEvent: (SyncMessage) -> Unit = {},
) {
    // Bonded devices are handled by the ACL connection-state watch — don't double-track
    // them on the scan path (and a bonded, non-advertising device would otherwise read as
    // "absent" and mis-fire a leave).
    val tethered = tether.ids() - bondedExcluded
    if (tethered.isEmpty()) return
    val present = list.filter { it.isPresent }.map { it.id }.toSet()
    val labels = list.filter { it.isPresent && it.id in tethered }
        .associate { it.id to (aliases.of(it.id) ?: it.displayName) }
    val events = monitor.update(tethered, present, labels = labels)
    for ((id, ev) in events) {
        val name = monitor.labelOf(id) ?: aliases.of(id) ?: t.tetherLeftTitle
        when (ev) {
            PresenceMonitor.Event.LEFT -> if (dir.onLeave) { notifyTetherLeft(ctx, t, name, id); onEvent(SyncMessage.TetherLeft(id, name)) }
            PresenceMonitor.Event.RETURNED -> if (dir.onReturn) { notifyTetherReturned(ctx, t, name, id); onEvent(SyncMessage.TetherReturned(id, name)) }
        }
    }
}

/** High-priority "you left it behind" alert (buzzes/rings per the channel's settings). */
private fun notifyTetherLeft(ctx: Context, t: NotiText, name: String, deviceId: String) {
    ensureChannels(ctx, t)
    if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
    val n = NotificationCompat.Builder(ctx, CH_TETHER)
        .setContentTitle(t.tetherLeftTitle)
        .setContentText(t.tetherLeftText.format(name))
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setAutoCancel(true)
        .setContentIntent(openAppIntent(ctx))
        .build()
    runCatching { NotificationManagerCompat.from(ctx).notify(tetherNotiId(deviceId), n) }
}

/** Quiet "it's back in range" notice (low-importance channel — no buzz). */
private fun notifyTetherReturned(ctx: Context, t: NotiText, name: String, deviceId: String) {
    ensureChannels(ctx, t)
    if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
    val n = NotificationCompat.Builder(ctx, CH_TETHER_INFO)
        .setContentTitle(t.tetherBackTitle)
        .setContentText(t.tetherBackText.format(name))
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setAutoCancel(true)
        .setContentIntent(openAppIntent(ctx))
        .build()
    runCatching { NotificationManagerCompat.from(ctx).notify(tetherNotiId(deviceId), n) }
}

/** Intent extra: device id a tapped tracker alert should open (see MainActivity/DeepLink). */
const val EXTRA_SUSPECT_ID = "fyi.blep.SUSPECT_ID"

/** Open the app without hard-coding its Activity (it lives in the app module). With a
 *  [suspectId], the tap deep-links to that device's panel — SINGLE_TOP so an already-open
 *  app gets it via onNewIntent, and UPDATE_CURRENT so the extra isn't stale-cached. */
private fun openAppIntent(ctx: Context, suspectId: String? = null): PendingIntent {
    val i = (ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: Intent())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    suspectId?.let { i.putExtra(EXTRA_SUSPECT_ID, it) }
    return PendingIntent.getActivity(
        ctx, if (suspectId != null) 1 else 0, i,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun ongoingNotification(ctx: Context, t: NotiText, trackerScan: Boolean): Notification {
    ensureChannels(ctx, t)
    return NotificationCompat.Builder(ctx, CH_ONGOING)
        .setContentTitle(appLabel(ctx))
        .setContentText(if (trackerScan) t.ongoingText else t.ongoingWatchText)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setOngoing(true)
        .setContentIntent(openAppIntent(ctx))
        .build()
}

private fun notifyTracker(ctx: Context, t: NotiText, suspectId: String? = null) {
    ensureChannels(ctx, t)
    if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
    val n = NotificationCompat.Builder(ctx, CH_ALERT)
        .setContentTitle(t.alertTitle)
        .setContentText(t.alertText)
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(openAppIntent(ctx, suspectId)) // tap → that device's panel
        .build()
    runCatching { NotificationManagerCompat.from(ctx).notify(NOTI_ALERT, n) }
}

/** Ongoing, low-key notification naming a flagged device that's currently in range. */
private fun notifyFlagged(ctx: Context, t: NotiText, name: String) {
    ensureChannels(ctx, t)
    if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
    val n = NotificationCompat.Builder(ctx, CH_ONGOING)
        .setContentTitle(t.flaggedTitle)
        .setContentText(t.flaggedText.format(name))
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setOngoing(true)
        .setOnlyAlertOnce(true) // refreshes silently each scan tick while present
        .setContentIntent(openAppIntent(ctx))
        .build()
    runCatching { NotificationManagerCompat.from(ctx).notify(NOTI_FLAGGED, n) }
}

private fun cancelFlagged(ctx: Context) {
    runCatching { NotificationManagerCompat.from(ctx).cancel(NOTI_FLAGGED) }
}
