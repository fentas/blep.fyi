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
import fyi.blep.R
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
        hit?.firstOrNull { it.severity == Severity.ALERT }?.let { notifyTracker(applicationContext) }
        return Result.success()
    }
}

/** Foreground service: keeps a safety scan alive when the app isn't on screen. */
class BackgroundScanService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels(this)
        ServiceCompat.startForeground(
            this, NOTI_ONGOING, ongoingNotification(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
        )
        scope.launch {
            val safety = SafetyScanner(createBleScanner(), TrackerDetector(AppSettings().scanSensitivity().tuning), SafetyHistory(createKeyValueStore()))
            runCatching {
                safety.alerts().collect { list ->
                    if (list.any { it.severity == Severity.ALERT }) notifyTracker(this@BackgroundScanService)
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

// ── notifications (English for now; in-app UI is localized) ──────────────────
private fun ensureChannels(ctx: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
    nm.createNotificationChannel(
        NotificationChannel(CH_ALERT, ctx.getString(R.string.noti_channel_alert), NotificationManager.IMPORTANCE_HIGH),
    )
    nm.createNotificationChannel(
        NotificationChannel(CH_ONGOING, ctx.getString(R.string.noti_channel_ongoing), NotificationManager.IMPORTANCE_LOW),
    )
}

private fun openAppIntent(ctx: Context): PendingIntent {
    val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE)
}

private fun ongoingNotification(ctx: Context): Notification {
    ensureChannels(ctx)
    return NotificationCompat.Builder(ctx, CH_ONGOING)
        .setContentTitle(ctx.getString(R.string.app_name))
        .setContentText(ctx.getString(R.string.noti_ongoing_text))
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setOngoing(true)
        .setContentIntent(openAppIntent(ctx))
        .build()
}

private fun notifyTracker(ctx: Context) {
    ensureChannels(ctx)
    if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
    val n = NotificationCompat.Builder(ctx, CH_ALERT)
        .setContentTitle(ctx.getString(R.string.noti_alert_title))
        .setContentText(ctx.getString(R.string.noti_alert_text))
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(openAppIntent(ctx))
        .build()
    runCatching { NotificationManagerCompat.from(ctx).notify(NOTI_ALERT, n) }
}
