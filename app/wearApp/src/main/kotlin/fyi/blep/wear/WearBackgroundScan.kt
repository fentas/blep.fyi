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
import fyi.blep.core.ble.createBleScanner
import fyi.blep.core.platform.createKeyValueStore
import fyi.blep.core.safety.SafetyHistory
import fyi.blep.core.safety.SafetyScanner
import fyi.blep.core.safety.Severity
import fyi.blep.core.safety.TrackerDetector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "blep.wear.safety"
private const val CH_ALERT = "blep.safety.alert"
private const val NOTI_ALERT = 2001
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

        val safety = SafetyScanner(createBleScanner(), TrackerDetector(), SafetyHistory(createKeyValueStore()))
        val hit = withTimeoutOrNull(SCAN_WINDOW_MS) {
            safety.alerts().first { list -> list.any { it.severity == Severity.ALERT } }
        }
        if (hit != null) notifyTracker(applicationContext)
        return Result.success()
    }

    private fun notifyTracker(ctx: Context) {
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
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setContentTitle(ctx.getString(R.string.noti_alert_title))
            .setContentText(ctx.getString(R.string.noti_alert_text))
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTI_ALERT, n) }
    }
}
