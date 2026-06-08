package fyi.blep.core.safety

/**
 * A coarse, rotation-stable fingerprint of a [RawAdvert]'s *structure* — used by the
 * rotation correlator to corroborate (or veto) a handover between two addresses.
 *
 * The trick is to hash only the bytes that **don't** rotate with the MAC:
 *  - the advertised **service UUIDs** (a device class keeps the same set), and
 *  - per manufacturer block, the **company id + payload length + the leading
 *    record-type byte** — never the rest of the value, which is exactly where the
 *    rotating secret lives (e.g. Apple Find My's public key sits *after* its
 *    type/length header, so we keep `0x12`/len and drop the key).
 *
 * So the same physical device fingerprints identically across its rotations, while
 * different device *classes* (an AirTag vs. earbuds vs. a SmartTag) fingerprint
 * differently. It is **not** a unique-device id: two AirTags share a fingerprint — so
 * a match *corroborates* an RSSI handover, a mismatch *vetoes* it, and a null (no
 * distinctive payload) leaves the correlator on RSSI alone.
 *
 * NOTE: "leading byte is stable" holds for Apple/Microsoft/most BLE stacks but should
 * be validated per-vendor on real hardware (docs/tracker-validation.md) — a vendor
 * that puts a counter in byte 0 would weaken (never falsify) the match.
 */
fun payloadFingerprint(advert: RawAdvert): String? {
    val parts = ArrayList<String>()
    advert.serviceUuids.asSequence().map { it.lowercase() }.distinct().sorted().forEach { parts += "s$it" }
    // sortedMapOf-style stable ordering over company ids
    for (company in advert.manufacturerData.keys.sorted()) {
        val bytes = advert.manufacturerData[company] ?: continue
        val recordType = if (bytes.isNotEmpty()) bytes[0].toInt() and 0xFF else -1
        parts += "m${company.toString(16)}.${bytes.size}.$recordType"
    }
    if (parts.isEmpty()) return null
    return stableHash(parts.joinToString("|"))
}

/** Deterministic across platforms + process restarts (unlike String.hashCode on
 *  Native), so a fingerprint stays comparable for the life of a correlation. */
private fun stableHash(s: String): String {
    var h = 1125899906842597L
    for (c in s) h = 31 * h + c.code
    return h.toString(36)
}
