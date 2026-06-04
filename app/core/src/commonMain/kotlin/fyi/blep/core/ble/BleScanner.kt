package fyi.blep.core.ble

import fyi.blep.core.model.BleDevice
import fyi.blep.core.safety.RawAdvert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Platform-agnostic BLE scanning surface.
 *
 * Implementations are cold/flow-based: collection starts scanning and
 * cancellation stops it, so lifecycle is driven entirely by the collector.
 */
interface BleScanner {

    /** Whether the scanner can run right now (adapter on + permissions granted). */
    val availability: Flow<ScanAvailability>

    /**
     * Continuously updated discovery list.
     * @param includeUnnamed surface devices with no advertised name.
     */
    fun devices(includeUnnamed: Boolean = false): Flow<List<BleDevice>>

    /** Live RSSI stream (dBm) for a single device, used during tracking. */
    fun rssi(deviceId: String): Flow<Int>

    /**
     * Raw advertisement stream for the safety scan (unwanted-tracker detection),
     * carrying manufacturer data / service UUIDs / address type so trackers can be
     * recognised. Default is empty so platforms that don't surface raw records yet
     * simply contribute nothing.
     */
    fun advertisements(): Flow<RawAdvert> = emptyFlow()
}

/** Why scanning may be unavailable — lets the UI prompt for the right fix. */
enum class ScanAvailability {
    READY,
    BLUETOOTH_OFF,
    PERMISSION_REQUIRED,
    LOCATION_OFF, // Android < 12 requires location services for BLE scans
    UNSUPPORTED,
}

/** Creates the platform [BleScanner]. */
expect fun createBleScanner(): BleScanner
