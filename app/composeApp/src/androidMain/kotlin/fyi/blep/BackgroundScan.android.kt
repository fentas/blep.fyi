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
import fyi.blep.core.ble.createBleScanner
import fyi.blep.core.platform.createKeyValueStore
import fyi.blep.core.safety.SafetyHistory
import fyi.blep.core.safety.SafetyScanner
import fyi.blep.core.safety.Severity
import fyi.blep.core.safety.TrackerDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getString
import fyi.blep.resources.Res
import fyi.blep.resources.noti_alert_text
import fyi.blep.resources.noti_alert_title
import fyi.blep.resources.noti_channel_alert
import fyi.blep.resources.noti_channel_ongoing
import fyi.blep.resources.noti_ongoing_text
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "blep.background.scan"
private const val CH_ALERT = "blep.safety.alert"
private const val CH_ONGOING = "blep.safety.ongoing"
private const val NOTI_ALERT = 1001
private const val NOTI_ONGOING = 1002
private const val SCAN_WINDOW_MS = 20_000L

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
        val safety = SafetyScanner(createBleScanner(), TrackerDetector(AppSettings().scanSensitivity().tuning), SafetyHistory(createKeyValueStore()))
        val hit = withTimeoutOrNull(SCAN_WINDOW_MS) {
            safety.alerts().first { list -> list.any { it.severity == Severity.ALERT } }
        }
        hit?.firstOrNull { it.severity == Severity.ALERT }?.let { notifyTracker(applicationContext, loadNotiText()) }
        return Result.success()
    }
}

/** Foreground service: keeps a safety scan alive when the app isn't on screen. */
class BackgroundScanService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Load the localized notification text (shared Compose resources, suspend) and
        // go foreground first — an in-memory resource read is well within the FGS
        // start window — then keep the safety scan running.
        scope.launch {
            val text = loadNotiText()
            ensureChannels(this@BackgroundScanService, text)
            ServiceCompat.startForeground(
                this@BackgroundScanService, NOTI_ONGOING, ongoingNotification(this@BackgroundScanService, text),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
            )
            val safety = SafetyScanner(createBleScanner(), TrackerDetector(AppSettings().scanSensitivity().tuning), SafetyHistory(createKeyValueStore()))
            runCatching {
                safety.alerts().collect { list ->
                    if (list.any { it.severity == Severity.ALERT }) notifyTracker(this@BackgroundScanService, text)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
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
    val ongoingText: String,
    val alertTitle: String,
    val alertText: String,
)

private suspend fun loadNotiText() = NotiText(
    channelAlert = getString(Res.string.noti_channel_alert),
    channelOngoing = getString(Res.string.noti_channel_ongoing),
    ongoingText = getString(Res.string.noti_ongoing_text),
    alertTitle = getString(Res.string.noti_alert_title),
    alertText = getString(Res.string.noti_alert_text),
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
}

/** Open the app without hard-coding its Activity (it lives in the app module). */
private fun openAppIntent(ctx: Context): PendingIntent {
    val i = (ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: Intent())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE)
}

private fun ongoingNotification(ctx: Context, t: NotiText): Notification {
    ensureChannels(ctx, t)
    return NotificationCompat.Builder(ctx, CH_ONGOING)
        .setContentTitle(appLabel(ctx))
        .setContentText(t.ongoingText)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setOngoing(true)
        .setContentIntent(openAppIntent(ctx))
        .build()
}

private fun notifyTracker(ctx: Context, t: NotiText) {
    ensureChannels(ctx, t)
    if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
    val n = NotificationCompat.Builder(ctx, CH_ALERT)
        .setContentTitle(t.alertTitle)
        .setContentText(t.alertText)
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(openAppIntent(ctx))
        .build()
    runCatching { NotificationManagerCompat.from(ctx).notify(NOTI_ALERT, n) }
}
