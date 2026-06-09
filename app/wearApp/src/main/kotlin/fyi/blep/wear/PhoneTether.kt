package fyi.blep.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import fyi.blep.core.platform.createKeyValueStore
import java.util.concurrent.TimeUnit

/**
 * "You left your phone behind" on the watch, via the Wear **Data Layer** — the watch↔phone
 * companion link. The system delivers peer connect/disconnect events to this service even
 * when the app isn't open (no foreground service needed → friendly to the watch battery),
 * which is the whole reason we use the Data Layer here instead of a BLE scan: a phone
 * rotates its advertising address, but the companion bond is stable.
 *
 * A disconnect is debounced (a 12 s WorkManager check) so a momentary blip or the phone
 * just toggling Bluetooth doesn't cry wolf; a reconnect within that window cancels it.
 */
class PhoneTetherService : WearableListenerService() {
    override fun onPeerDisconnected(node: Node) {
        PhoneTether.onPhoneMaybeAway(this)
    }

    override fun onPeerConnected(node: Node) {
        PhoneTether.onPhoneBack(this)
    }
}

/** Re-checks (after the debounce) that the phone is really gone, then alerts. */
class PhoneAwayWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!PhoneTether.enabled(applicationContext)) return Result.success()
        val nearby = runCatching {
            Tasks.await(Wearable.getNodeClient(applicationContext).connectedNodes)
                .any { it.isNearby }
        }.getOrDefault(false)
        if (!nearby) PhoneTether.notifyPhoneAway(applicationContext)
        return Result.success()
    }
}

object PhoneTether {
    private const val WORK_AWAY = "blep.wear.phoneAway"
    private const val CH_TETHER = "blep.tether.alert"
    private const val CH_TETHER_INFO = "blep.tether.info"
    private const val NOTI_PHONE = 2200
    private const val DEBOUNCE_S = 12L
    private const val KEY_ENABLED = "wear.phoneTether"
    private const val KEY_FIRED = "wear.phoneTether.fired"

    fun enabled(ctx: Context): Boolean = createKeyValueStore().getBoolean(KEY_ENABLED, true)
    fun setEnabled(ctx: Context, on: Boolean) {
        createKeyValueStore().putBoolean(KEY_ENABLED, on)
        if (!on) {
            WorkManager.getInstance(ctx).cancelUniqueWork(WORK_AWAY)
            NotificationManagerCompat.from(ctx).cancel(NOTI_PHONE)
        }
    }

    fun onPhoneMaybeAway(ctx: Context) {
        if (!enabled(ctx)) return
        val req = OneTimeWorkRequestBuilder<PhoneAwayWorker>()
            .setInitialDelay(DEBOUNCE_S, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(WORK_AWAY, ExistingWorkPolicy.REPLACE, req)
    }

    fun onPhoneBack(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK_AWAY)
        val store = createKeyValueStore()
        if (store.getBoolean(KEY_FIRED, false)) {
            store.putBoolean(KEY_FIRED, false)
            if (enabled(ctx)) notifyPhoneBack(ctx)
        }
    }

    fun notifyPhoneAway(ctx: Context) {
        createKeyValueStore().putBoolean(KEY_FIRED, true)
        ensureChannels(ctx)
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(ctx, CH_TETHER)
            .setContentTitle(ctx.getString(R.string.noti_phone_left_title))
            .setContentText(ctx.getString(R.string.noti_phone_left_text))
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTI_PHONE, n) }
    }

    private fun notifyPhoneBack(ctx: Context) {
        ensureChannels(ctx)
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(ctx, CH_TETHER_INFO)
            .setContentTitle(ctx.getString(R.string.noti_tether_back_title))
            .setContentText(ctx.getString(R.string.noti_phone_back_text))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTI_PHONE, n) }
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH_TETHER, ctx.getString(R.string.noti_channel_tether), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_TETHER_INFO, ctx.getString(R.string.noti_channel_tether_info), NotificationManager.IMPORTANCE_LOW))
    }
}
