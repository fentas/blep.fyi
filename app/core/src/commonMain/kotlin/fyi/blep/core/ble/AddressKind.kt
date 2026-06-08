package fyi.blep.core.ble

/** What a device's id tells us about whether it stays put or rotates. */
enum class AddressKind {
    /** A fixed, IEEE-assigned MAC — stable over time (often a paired/classic device). */
    PUBLIC,

    /** A private/random MAC — rotates periodically by design (AirTags, phones, most
     *  modern peripherals), so it reappears under a new id. */
    RANDOM,

    /** An opaque, OS-scoped identifier (Apple's per-app UUID) — never a hardware MAC,
     *  and already churns with the device's privacy identity. */
    OPAQUE,
}

/**
 * Classify a device id string without any platform APIs (so it works in shared UI).
 *
 * Android ids are MACs (`AA:BB:CC:DD:EE:FF`); the two most-significant bits of the
 * first octet distinguish a random/private address (`0b00`/`0b01`/`0b11` → rotates)
 * from a public IEEE one (`0b10` → stable) — the same rule the Android scanner uses.
 * Apple ids aren't MACs at all (a `CBPeripheral` UUID), so they're [OPAQUE].
 */
fun addressKind(id: String): AddressKind {
    val first = id.substringBefore(':', missingDelimiterValue = "")
    if (id.count { it == ':' } == 5 && first.length == 2) {
        val msb = first.toIntOrNull(16) ?: return AddressKind.OPAQUE
        return if ((msb and 0xC0) == 0x80) AddressKind.PUBLIC else AddressKind.RANDOM
    }
    return AddressKind.OPAQUE
}
