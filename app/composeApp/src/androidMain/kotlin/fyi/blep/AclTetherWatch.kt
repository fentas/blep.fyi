package fyi.blep

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import fyi.blep.core.ble.DeviceAliases
import fyi.blep.core.platform.createKeyValueStore
import fyi.blep.core.tether.DeviceTether
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Leave/return detection for **bonded** tethered devices via the OS connection-state
 * broadcast (ACL connect/disconnect) — event-driven, no scanning, and rotation-proof:
 * a bond resolves to the device's stable identity address, so it works for the very
 * devices (a phone, a watch, earbuds, a car) that defeat advertisement scanning.
 *
 * Must be **runtime-registered** — manifest receivers stopped getting ACL events in
 * Android 8 — so it's hosted by [BackgroundScanService]; a tether keeps that foreground
 * service alive, which is why this keeps working with the app's UI closed. Unbonded
 * advertising tags don't form ACL links, so they stay on the scan-based
 * [fyi.blep.core.tether.PresenceMonitor]; the two tracks are partitioned by bond state
 * (the service excludes bonded ids from the scan path), so they never double-fire.
 */
class AclTetherWatch(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val onLeft: (deviceId: String, name: String) -> Unit,
    private val onReturned: (deviceId: String, name: String) -> Unit,
) {
    private val tether = DeviceTether(createKeyValueStore())
    private val aliases = DeviceAliases(createKeyValueStore())
    private val pendingLeave = HashMap<String, Job>() // debounce a momentary blip
    private val leftFired = HashSet<String>()         // ids currently in the "left" state

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val device = intentDevice(intent) ?: return
            val addr = runCatching { device.address }.getOrNull() ?: return
            if (addr !in tether.ids()) return
            when (action) {
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> onDisconnect(addr, device)
                BluetoothDevice.ACTION_ACL_CONNECTED -> onConnect(addr, device)
            }
        }
    }

    fun register() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        // ACL_* are protected system broadcasts → not-exported is correct.
        runCatching { ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }
    }

    fun unregister() {
        runCatching { ctx.unregisterReceiver(receiver) }
        pendingLeave.values.forEach { it.cancel() }
        pendingLeave.clear()
    }

    private fun nameOf(addr: String, device: BluetoothDevice): String =
        aliases.of(addr)
            ?: runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: addr

    private fun onDisconnect(addr: String, device: BluetoothDevice) {
        if (addr in leftFired || addr in pendingLeave) return
        pendingLeave[addr] = scope.launch {
            delay(LEAVE_DEBOUNCE_MS) // ride out a quick drop-and-reconnect
            pendingLeave.remove(addr)
            leftFired.add(addr)
            if (AppSettings().tetherAlert().onLeave) onLeft(addr, nameOf(addr, device))
        }
    }

    private fun onConnect(addr: String, device: BluetoothDevice) {
        pendingLeave.remove(addr)?.cancel() // it was just a blip — never alert
        if (leftFired.remove(addr) && AppSettings().tetherAlert().onReturn) {
            onReturned(addr, nameOf(addr, device))
        }
    }

    companion object {
        const val LEAVE_DEBOUNCE_MS = 10_000L
    }
}

@Suppress("DEPRECATION")
private fun intentDevice(intent: Intent): BluetoothDevice? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
