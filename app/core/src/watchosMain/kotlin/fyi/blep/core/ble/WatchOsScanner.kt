package fyi.blep.core.ble

import fyi.blep.core.model.BleDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * No-op scanner for watchOS.
 *
 * Kable does not publish a watchOS artifact, and the watchOS app doesn't need
 * it: it performs CoreBluetooth scanning in Swift and feeds RSSI straight into
 * the shared [fyi.blep.core.tracking.TrackingSession]. This actual exists only
 * to satisfy the `expect fun createBleScanner()` for the watchOS target and is
 * not used by the watch app.
 */
internal class UnsupportedBleScanner : BleScanner {
    override val availability: Flow<ScanAvailability> = flowOf(ScanAvailability.UNSUPPORTED)
    override fun devices(includeUnnamed: Boolean, measureConnectedSignal: Boolean): Flow<List<BleDevice>> = flowOf(emptyList())
    override fun rssi(deviceId: String): Flow<Int> = emptyFlow()
}

actual fun createBleScanner(): BleScanner = UnsupportedBleScanner()
