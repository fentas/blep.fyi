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
 * @property isFlagged user-flagged for priority watching — escalates the background
 *   check to a continuous foreground watch with a notification while it's in range.
 */
data class BleDevice(
    val id: String,
    val name: String?,
    val rssi: Int,
    val isConnected: Boolean = false,
    val isPaired: Boolean = false,
    val alias: String? = null,
    val isFavorite: Boolean = false,
    val isFlagged: Boolean = false,
    /** A leave/return ("left-behind") alert is set on this device. */
    val isTethered: Boolean = false,
    /** Wall-clock (epoch ms) of the last sighting with a live signal; 0 if never. Carried
     *  forward on a pinned-but-absent device so the UI can show how long it's been gone. */
    val seenAtMs: Long = 0L,
    /** What the paired phone/watch hears for this device, when scan fusion is on; null
     *  if the peer hasn't reported it. Kept *beside* [rssi] rather than blended into it:
     *  the two radios sit a metre apart on opposite sides of a body that attenuates
     *  BLE, so the gap between them is a directional reading in its own right, and
     *  averaging would destroy exactly the information that makes it worth sending. */
    val remoteRssi: Int? = null,
) {
    /** True when no live advertisement RSSI is available (bonded, not advertising). */
    val rssiUnknown: Boolean get() = rssi == RSSI_UNKNOWN

    /** The strongest reading anything of yours can hear. "How close is this?" is
     *  answered by whichever radio hears it best, so this — not an average — is what
     *  the list should sort and label by. */
    val bestRssi: Int get() = maxOf(rssi, remoteRssi ?: RSSI_UNKNOWN)

    /** The peer hears it and we don't, or hears it better. Worth marking in the list:
     *  the row is reporting something this device cannot detect on its own. */
    val heardBetterRemotely: Boolean get() = remoteRssi != null && remoteRssi > rssi

    /** Detectable as *nearby* right now: a live advertisement signal, an active
     *  connection (connected ⇒ in range, even without an advert), or the paired device
     *  hearing it — if your watch can hear it, it is near you, whatever your phone
     *  makes of it. A bonded device that's none of these is unknowable, so it stays out
     *  of the nearby list and lives only in the "all paired" manager. */
    val isPresent: Boolean get() = !rssiUnknown || isConnected || remoteRssi != null
    /** True when the device advertises a non-blank name. */
    val isNamed: Boolean get() = !name.isNullOrBlank()

    /** What the UI should show: alias › advertised name › the raw identifier. Falling back to
     *  the id (not a generic "Unnamed device") keeps a list of unnamed devices distinguishable. */
    val displayName: String
        get() = alias?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: id

    companion object {
        /** Sentinel RSSI for a bonded device that isn't advertising. */
        const val RSSI_UNKNOWN: Int = -127
    }
}
