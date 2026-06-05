package fyi.blep.core.model

/**
 * A Bluetooth Low Energy device as surfaced to the UI.
 *
 * @property id stable platform identifier (Android: MAC/opaque id, Apple: CBPeripheral UUID).
 * @property name advertised name, or `null` when the device advertises none.
 * @property rssi most recent raw signal strength in dBm (negative; closer to 0 is stronger).
 * @property isConnected whether the OS currently reports an active connection to this device.
 * @property isPaired whether the device is bonded/paired (e.g. a watch) — it may
 *   not advertise, so it's tracked via a GATT connection rather than scan RSSI.
 * @property alias user-assigned rename, takes precedence over [name] for display.
 * @property isFavorite user-starred — pinned into the main list even when the
 *   device isn't advertising (overlaid by the app, not the scanner).
 */
data class BleDevice(
    val id: String,
    val name: String?,
    val rssi: Int,
    val isConnected: Boolean = false,
    val isPaired: Boolean = false,
    val alias: String? = null,
    val isFavorite: Boolean = false,
) {
    /** True when no live advertisement RSSI is available (bonded, not advertising). */
    val rssiUnknown: Boolean get() = rssi == RSSI_UNKNOWN
    /** True when the device advertises a non-blank name. */
    val isNamed: Boolean get() = !name.isNullOrBlank()

    /** What the UI should show: alias › advertised name › a neutral placeholder. */
    val displayName: String
        get() = alias?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: "Unnamed device"

    companion object {
        /** Sentinel RSSI for a bonded device that isn't advertising. */
        const val RSSI_UNKNOWN: Int = -127
    }
}
