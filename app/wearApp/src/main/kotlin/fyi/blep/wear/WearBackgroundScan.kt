package fyi.blep.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fyi.blep.core.ble.DeviceAliases
import fyi.blep.core.ble.IdentityStore
import fyi.blep.core.ble.createBleScanner
import fyi.blep.core.platform.createKeyValueStore
import fyi.blep.core.safety.SafetyHistory
import fyi.blep.core.safety.SafetyScanner
import fyi.blep.core.safety.Severity
import fyi.blep.core.safety.TrackerDetector
import fyi.blep.core.tether.DeviceTether
import fyi.blep.core.tether.PresenceMonitor
import fyi.blep.core.tether.TetherAlertDirection
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "blep.wear.safety"
private const val CH_ALERT = "blep.safety.alert"
private const val CH_TETHER = "blep.tether.alert"
private const val CH_TETHER_INFO = "blep.tether.info"
private const val NOTI_ALERT = 2001
private const val NOTI_TETHER_BASE = 2100
private const val SCAN_WINDOW_MS = 20_000L

/**
 * The watch's anti-tracking check: a **periodic** (~30 min) WorkManager scan with
 * **no foreground service** — so it's light on the watch battery and needs no Play
 * foreground-service declaration (the phone runs the continuous version). Reuses the
 * shared `core` safety pipeline and posts a localized notification if a tracker
 * looks like it's following you. Honors battery-saver mode.
 */
object WearSafetyScan {
    /** Enqueue the periodic check (idempotent — KEEP, so relaunching won't reset it). */
    fun enqueue(context: Context, intervalMinutes: Long = 30) {
        val req = PeriodicWorkRequestBuilder<WearSafetyWorker>(
            intervalMinutes.coerceAtLeast(15), TimeUnit.MINUTES,
        ).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class WearSafetyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        // Honor battery saver — skip this cycle when the watch is conserving power.
        val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm?.isPowerSaveMode == true) return Result.success()

        // Identity layer: an interval scan can't see a rotating tracker's churn, so it
        // probes close suspects and persists their stable identity — letting it catch a
        // follower that keeps coming back across checks even as its address rotates.
        // Inherently ≥2-cycle latency: one check learns/persists the identity (the probe
        // takes up to ~12 s of this 20 s window), a later check sees it recur → alerts.
        val ctx = applicationContext
        val scanner = createBleScanner()
        val tether = DeviceTether(createKeyValueStore())
        val monitor = PresenceMonitor(createKeyValueStore())
        val aliases = DeviceAliases(createKeyValueStore())
        coroutineScope {
            launch {
                val safety = SafetyScanner(
                    scanner, TrackerDetector(), SafetyHistory(createKeyValueStore()),
                    identityStore = IdentityStore(createKeyValueStore()),
                )
                val hit = withTimeoutOrNull(SCAN_WINDOW_MS) {
                    safety.alerts().first { list -> list.any { it.severity == Severity.ALERT } }
                }
                hit?.firstOrNull { it.severity == Severity.ALERT }?.let { notifyTracker(ctx, it.label) }
            }
            // Tether watch: notify if a tethered device left/returned this window. Interval
            // cadence, so a leave surfaces on the next periodic check — the watch's
            // battery-friendly model (the phone runs the continuous version).
            if (tether.ids().isNotEmpty()) {
                launch {
                    withTimeoutOrNull(SCAN_WINDOW_MS) {
                        scanner.devices(includeUnnamed = true).collect { list ->
                            checkTethers(ctx, list, tether, monitor, aliases)
                        }
                    }
                }
            }
        }
        return Result.success()
    }

    private fun checkTethers(
        ctx: Context,
        list: List<fyi.blep.core.model.BleDevice>,
        tether: DeviceTether,
        monitor: PresenceMonitor,
        aliases: DeviceAliases,
    ) {
        val tethered = tether.ids()
        if (tethered.isEmpty()) return
        val present = list.filter { it.isPresent }.map { it.id }.toSet()
        val labels = list.filter { it.isPresent && it.id in tethered }
            .associate { it.id to (aliases.of(it.id) ?: it.displayName) }
        val events = monitor.update(tethered, present, labels = labels)
        // Wear keeps it simple: always alert on both transitions (no per-direction setting).
        val dir = TetherAlertDirection.BOTH
        for ((id, ev) in events) {
            val name = monitor.labelOf(id) ?: aliases.of(id) ?: ctx.getString(R.string.noti_tether_left_title)
            when (ev) {
                PresenceMonitor.Event.LEFT -> if (dir.onLeave) notifyTether(ctx, id, true, name)
                PresenceMonitor.Event.RETURNED -> if (dir.onReturn) notifyTether(ctx, id, false, name)
            }
        }
    }

    private fun notifyTether(ctx: Context, deviceId: String, left: Boolean, name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(NotificationChannel(CH_TETHER, ctx.getString(R.string.noti_channel_tether), NotificationManager.IMPORTANCE_HIGH))
            nm?.createNotificationChannel(NotificationChannel(CH_TETHER_INFO, ctx.getString(R.string.noti_channel_tether_info), NotificationManager.IMPORTANCE_LOW))
        }
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, if (left) CH_TETHER else CH_TETHER_INFO)
            .setContentTitle(ctx.getString(if (left) R.string.noti_tether_left_title else R.string.noti_tether_back_title))
            .setContentText(ctx.getString(if (left) R.string.noti_tether_left_text else R.string.noti_tether_back_text, name))
            .setSmallIcon(if (left) android.R.drawable.stat_sys_warning else android.R.drawable.stat_notify_sync)
            .setPriority(if (left) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTI_TETHER_BASE + (deviceId.hashCode() and 0x3F), n) }
    }

    private fun notifyTracker(ctx: Context, label: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CH_ALERT, ctx.getString(R.string.noti_channel_alert), NotificationManager.IMPORTANCE_HIGH),
            )
        }
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val text = label?.takeIf { it.isNotBlank() }?.let { ctx.getString(R.string.noti_alert_named, it) }
            ?: ctx.getString(R.string.noti_alert_text)
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setContentTitle(ctx.getString(R.string.noti_alert_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTI_ALERT, n) }
    }
}
