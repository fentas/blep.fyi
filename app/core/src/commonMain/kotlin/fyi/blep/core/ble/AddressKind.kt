package fyi.blep.core.ble

/** What a device's id tells us about whether it stays put or rotates. */
enum class AddressKind {
    /** A fixed, IEEE-assigned MAC — stable over time (often a paired/classic device). */
    PUBLIC,

    /** A *static* random MAC — random, but fixed while the device stays powered (it only
     *  changes on a reboot). Common for paired peripherals and Wear watches. Does NOT
     *  rotate periodically, so for the user's purposes it behaves like a stable id. */
    STATIC,

    /** A *resolvable/non-resolvable* private MAC — rotates periodically by design (AirTags,
     *  phones, most modern peripherals), so it reappears under a new id. */
    RANDOM,

    /** An opaque, OS-scoped identifier (Apple's per-app UUID) — never a hardware MAC,
     *  and already churns with the device's privacy identity. */
    OPAQUE,
}

/**
 * Classify a device id string without any platform APIs (so it works in shared UI).
 *
 * Android ids are MACs (`AA:BB:CC:DD:EE:FF`). The two most-significant bits of the first
 * octet carry the BLE address type:
 *  - `0b10` → public IEEE MAC ([PUBLIC], stable)
 *  - `0b11` → static random ([STATIC]) — random but fixed until reboot, so it does NOT
 *    rotate on a schedule
 *  - `0b01` (resolvable) / `0b00` (non-resolvable) → private ([RANDOM]) — rotates periodically
 *
 * Apple ids aren't MACs at all (a `CBPeripheral` UUID), so they're [OPAQUE]. This only sees
 * the *type* of address; whether a device has actually been observed rotating is the
 * RotationTracker's job (the device-detail history), not this.
 */
fun addressKind(id: String): AddressKind {
    val first = id.substringBefore(':', missingDelimiterValue = "")
    if (id.count { it == ':' } == 5 && first.length == 2) {
        val msb = first.toIntOrNull(16) ?: return AddressKind.OPAQUE
        return when (msb and 0xC0) {
            0x80 -> AddressKind.PUBLIC // 0b10 — IEEE public, permanent
            0xC0 -> AddressKind.STATIC // 0b11 — static random, fixed until reboot
            else -> AddressKind.RANDOM // 0b01 resolvable / 0b00 non-resolvable — rotates
        }
    }
    return AddressKind.OPAQUE
}
