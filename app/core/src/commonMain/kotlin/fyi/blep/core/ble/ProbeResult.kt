package fyi.blep.core.ble

/**
 * What a single, one-shot GATT connection learned about a device — gathered actively
 * (we connect briefly), unlike everything else here which is passive listening.
 *
 * The point is a **stable identity signal that survives MAC rotation**, layered from the
 * most explicit to the most subtle (per the GATT-deep-poke design):
 *  1. Device Information Service (0x180A): a [serial] is a globally unique passport;
 *     [manufacturer]/[model]/[firmware]/[hardware] narrow it to a product line + revision.
 *  2. Structural fingerprint: the GATT skeleton — the exact set of services (incl. the
 *     128-bit vendor-specific ones) and each characteristic's properties — is a hard
 *     signature of the *model* that never changes with the MAC ([structure]).
 *  3. Stateful telemetry: [batteryPct] is temporal glue — a device that drops at 82%
 *     and reappears at 82/81% moments later is almost certainly the same one.
 *  4. Pairing posture: even a locked-down device fingerprints by *how* it refuses —
 *     [needsPairing] records that a protected read demanded authentication/encryption.
 *
 * [connectable] = false records a refused/timed-out connect — itself a stable trait
 * (most privacy tags can't be connected). Null fields = nothing readable there without
 * bonding (which we never force).
 */
data class ProbeResult(
    val connectable: Boolean,
    val name: String? = null,          // GAP device name (0x2A00 / advertised)
    val manufacturer: String? = null,  // DIS 0x2A29
    val model: String? = null,         // DIS model number 0x2A24
    val firmware: String? = null,      // DIS firmware revision 0x2A26
    val hardware: String? = null,      // DIS hardware revision 0x2A27
    val serial: String? = null,        // DIS serial 0x2A25 (rare without bonding)
    val serviceUuids: List<String> = emptyList(), // every advertised service (incl. 128-bit custom)
    val structure: String? = null,     // stable hash of the GATT skeleton (services + char properties)
    val serviceCount: Int = 0,
    val batteryPct: Int? = null,       // 0x2A19 — temporal glue; persisted as last-known + its probe time
    val needsPairing: Boolean = false, // a protected read returned an auth/encryption error
) {
    /** Did we learn anything beyond "(non-)connectable"? */
    val isInformative: Boolean
        get() = !name.isNullOrBlank() || !manufacturer.isNullOrBlank() || !model.isNullOrBlank() ||
            !serial.isNullOrBlank() || !hardware.isNullOrBlank() || !structure.isNullOrBlank()

    /** A human label for the device, if the probe found one. */
    val label: String?
        get() = name?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(manufacturer?.takeIf { it.isNotBlank() }, model?.takeIf { it.isNotBlank() })
                .takeIf { it.isNotEmpty() }?.joinToString(" ")

    /**
     * A cross-rotation re-identification key, or null if nothing distinctive enough was
     * learned. A serial is unique; a personalised name (one a user set, not the generic
     * model) is nearly so. We deliberately do **not** key on manufacturer/model/structure
     * alone — those are shared by every unit of a product, so merging on them would fuse
     * two identical earbuds. (The structure still *corroborates*; it just isn't a unit id.)
     */
    val identityKey: String?
        get() = serial?.takeIf { it.isNotBlank() }?.let { "ser:$it" }
            ?: name?.takeIf { it.isNotBlank() && !looksGeneric(it) }?.let { "nm:${it.trim().lowercase()}" }

    private fun looksGeneric(name: String): Boolean {
        val n = name.trim().lowercase()
        return model?.takeIf { it.isNotBlank() }?.let { n == it.trim().lowercase() } ?: false
    }

    /** Serialise for the IdentityStore so the Device-info card + telemetry re-correlation
     *  survive a restart (and an interval scan can match across sessions). Unit-separator
     *  delimited; the separators are stripped from values. Battery is included as the
     *  temporal anchor (it's "last known" once persisted). */
    fun pack(): String = listOf(
        name, manufacturer, model, firmware, hardware, serial, structure,
        serviceCount.toString(), if (connectable) "1" else "0", if (needsPairing) "1" else "0",
        batteryPct?.toString(),
    ).joinToString(US) { (it ?: "").replace(US, " ").replace('\t', ' ').replace('\n', ' ') }

    companion object {
        private const val US = "\u001F" // ASCII unit separator — absent from device strings

        fun unpack(s: String): ProbeResult {
            val p = s.split(US)
            fun g(i: Int) = p.getOrNull(i)?.takeIf { it.isNotEmpty() }
            return ProbeResult(
                connectable = g(8) == "1",
                name = g(0), manufacturer = g(1), model = g(2), firmware = g(3), hardware = g(4),
                serial = g(5), structure = g(6), serviceCount = g(7)?.toIntOrNull() ?: 0,
                needsPairing = g(9) == "1", batteryPct = g(10)?.toIntOrNull(),
            )
        }
    }
}
