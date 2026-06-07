package fyi.blep.core.safety

/** BLE address kind — a RANDOM address is the rotating/resolvable-private kind
 *  that privacy-rotating trackers use (and that the rotation heuristic keys on). */
enum class AddressType { PUBLIC, RANDOM, UNKNOWN }

/**
 * A raw BLE advertisement, normalised across platforms. The platform scanner fills
 * these out of the scan record; [TrackerClassifier] turns it into a
 * [TrackerSighting] in common code so the protocol detection is unit-tested rather
 * than buried per-platform.
 *
 * @param serviceUuids lower-case hex — 16-bit as "feed", 128-bit fully expanded.
 * @param manufacturerData company id → payload bytes.
 */
data class RawAdvert(
    val address: String,
    val rssi: Int,
    val timeMs: Long,
    val addressType: AddressType = AddressType.UNKNOWN,
    val serviceUuids: List<String> = emptyList(),
    val manufacturerData: Map<Int, ByteArray> = emptyMap(),
)

private const val BT_BASE_SUFFIX = "-0000-1000-8000-00805f9b34fb"

/** Collapse a Bluetooth-base 128-bit UUID to its 16-bit short form ("0000feed-…" →
 *  "feed"); anything else is returned unchanged. Shared by the platform scanners so
 *  the [TrackerClassifier]'s service-UUID matching sees one consistent form. */
fun shortServiceUuid(uuid: String): String =
    if (uuid.length == 36 && uuid.endsWith(BT_BASE_SUFFIX)) uuid.substring(4, 8) else uuid

/**
 * Recognises separated-tracker protocols from a [RawAdvert]. Conservative: only the
 * well-established fingerprints are matched; everything else stays UNKNOWN and is
 * left to the rotation heuristic. The less-certain service UUIDs are marked to
 * verify against real hardware on the internal track.
 */
object TrackerClassifier {
    private const val APPLE = 0x004C
    private const val APPLE_FINDMY = 0x12         // Apple data-type byte for offline finding
    private const val FINDMY_SEPARATED_LEN = 0x19 // full "lost" advert (public key); 0x02 = with owner

    private val TILE = setOf("feed", "feec")      // Tile, Inc. (0xFEED / 0xFEEC)
    private val SMARTTAG = setOf("fd5a")           // Samsung SmartTag Find network (0xFD5A)
    // Cross-vendor "accessory not with owner" / Find My Device network. 0xFD44 (Apple
    // Find My) is solid; 0xFEAA (Eddystone, used by Google's FMDN) is lower-confidence
    // because plain Eddystone beacons share it — flagged for real-hardware validation
    // (docs/tracker-validation.md).
    private val DULT = setOf("fd44", "feaa")

    fun classify(a: RawAdvert): TrackerSighting {
        val random = a.addressType == AddressType.RANDOM
        val svc = a.serviceUuids.map { it.lowercase() }.toHashSet()

        // Apple Find My: scan the (type,len,value) TLV chain for the 0x12 offline-finding
        // record. The full-length record is the *separated* (lost) tag; the short 0x02
        // record is a tag still with its owner — recognise it but don't call it separated.
        a.manufacturerData[APPLE]?.let { m ->
            appleFindMySeparated(m)?.let { sep ->
                return sighting(a, TrackerKind.FIND_MY, separated = sep, random)
            }
        }
        return when {
            svc.any { it in TILE } -> sighting(a, TrackerKind.TILE, separated = true, random)
            // SmartTag is keyed on its service UUID only — the bare Samsung company id
            // (0x0075) rides on every Galaxy phone/watch/buds, so matching it would flag
            // half a room of Samsung gear as trackers.
            svc.any { it in SMARTTAG } -> sighting(a, TrackerKind.SMARTTAG, separated = true, random)
            svc.any { it in DULT } -> sighting(a, TrackerKind.DULT, separated = true, random)
            else -> sighting(a, TrackerKind.UNKNOWN, separated = false, random)
        }
    }

    /** Walk Apple's manufacturer-data TLV chain for the Find My type (0x12). Returns
     *  null if absent; true for the full *separated/lost* advert (len ≥ 0x19); false
     *  for the short *with-owner* advert. Bounds-checked against malformed payloads. */
    private fun appleFindMySeparated(m: ByteArray): Boolean? {
        var i = 0
        while (i + 1 < m.size) {
            val type = m[i].toInt() and 0xFF
            val len = m[i + 1].toInt() and 0xFF
            if (type == APPLE_FINDMY) return len >= FINDMY_SEPARATED_LEN
            i += 2 + len // advance past this TLV's value
        }
        return null
    }

    private fun sighting(a: RawAdvert, kind: TrackerKind, separated: Boolean, random: Boolean) =
        TrackerSighting(a.address, a.rssi, a.timeMs, kind, separated, random)
}
