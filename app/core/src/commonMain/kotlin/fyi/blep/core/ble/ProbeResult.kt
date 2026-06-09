package fyi.blep.core.ble

/**
 * What a single, one-shot GATT connection learned about a device — gathered actively
 * (we connect briefly), unlike everything else here which is passive listening.
 *
 * The point is a **stable identity signal that survives MAC rotation**: a GAP name or
 * a Device Information Service serial doesn't change when the privacy address rotates,
 * so it can re-link a device the RSSI handover lost — *and* it enriches the detail
 * page ("Galaxy Buds", "manufacturer: Samsung") instead of a bare random address.
 *
 * [connectable] = false records that the connect was refused or timed out. That isn't
 * a failure to discard — it's itself a stable trait (most privacy tags *are*
 * non-connectable), so we cache it and never re-probe. Null fields = nothing readable
 * there without bonding (which we never force).
 */
data class ProbeResult(
    val connectable: Boolean,
    val name: String? = null,          // GAP device name (0x2A00 / advertised)
    val manufacturer: String? = null,  // Device Information Service 0x2A29
    val model: String? = null,         // DIS model number 0x2A24
    val firmware: String? = null,      // DIS firmware revision 0x2A26
    val serial: String? = null,        // DIS serial 0x2A25 (rare without bonding)
    val serviceUuids: List<String> = emptyList(),
) {
    /** Did we actually learn anything beyond "(non-)connectable"? */
    val isInformative: Boolean
        get() = !name.isNullOrBlank() || !manufacturer.isNullOrBlank() ||
            !model.isNullOrBlank() || !serial.isNullOrBlank()

    /** A human label for the device, if the probe found one. */
    val label: String?
        get() = name?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(manufacturer?.takeIf { it.isNotBlank() }, model?.takeIf { it.isNotBlank() })
                .takeIf { it.isNotEmpty() }?.joinToString(" ")

    /**
     * A cross-rotation re-identification key, or null if nothing distinctive enough was
     * learned. A serial is unique; a personalised name (one a user set, so not just the
     * generic model) is nearly so. We deliberately do **not** key on the manufacturer /
     * model / service-UUID set alone — those are shared by every unit of a product, so
     * merging on them would fuse two identical earbuds. The re-correlator decides how
     * much to trust a given key (see uniqueness rules there).
     */
    val identityKey: String?
        get() = serial?.takeIf { it.isNotBlank() }?.let { "ser:$it" }
            ?: name?.takeIf { it.isNotBlank() && !looksGeneric(it) }?.let { "nm:${it.trim().lowercase()}" }

    private fun looksGeneric(name: String): Boolean {
        // A name that just restates the model (no owner mark) isn't a unique id.
        val n = name.trim().lowercase()
        return model?.takeIf { it.isNotBlank() }?.let { n == it.trim().lowercase() } ?: false
    }
}
