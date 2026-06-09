package fyi.blep

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fyi.blep.core.ble.DeviceFlags
import fyi.blep.core.platform.createKeyValueStore
import fyi.blep.core.tether.DeviceTether

/**
 * Re-arms the off-screen watches after a reboot, so a tethered or flagged device keeps
 * being watched without the user reopening the app. The periodic safety worker is
 * re-enqueued; the foreground watch (which hosts the ACL receiver — see [AclTetherWatch])
 * is restarted if anything still wants it. Best-effort: starting a foreground service
 * from boot can be refused on newer Android, in which case opening the app re-arms it.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = AppSettings()
        if (settings.backgroundScan()) {
            runCatching { BackgroundScan.applyPeriodic(true, settings.scanIntervalMinutes()) }
        }
        val tethered = DeviceTether(createKeyValueStore()).ids().isNotEmpty()
        val flagged = DeviceFlags(createKeyValueStore()).ids().isNotEmpty()
        if (tethered || flagged) runCatching { BackgroundScan.setForeground(true) }
    }
}
