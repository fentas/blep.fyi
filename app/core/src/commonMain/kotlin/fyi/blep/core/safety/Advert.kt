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
    private const val SAMSUNG = 0x0075
    private const val APPLE_FINDMY_OFFLINE = 0x12 // offline-finding payload = separated from owner

    private val TILE = setOf("feed", "feec")
    private val SMARTTAG = setOf("fd5a")
    // DULT "accessory-not-with-owner" / Find My Device network — verify on-device.
    private val DULT = setOf("fd44", "feaa")

    fun classify(a: RawAdvert): TrackerSighting {
        val random = a.addressType == AddressType.RANDOM
        val svc = a.serviceUuids.map { it.lowercase() }.toHashSet()

        a.manufacturerData[APPLE]?.let { m ->
            if (m.isNotEmpty() && (m[0].toInt() and 0xFF) == APPLE_FINDMY_OFFLINE) {
                return sighting(a, TrackerKind.FIND_MY, separated = true, random)
            }
        }
        return when {
            svc.any { it in TILE } -> sighting(a, TrackerKind.TILE, separated = true, random)
            svc.any { it in SMARTTAG } || a.manufacturerData.containsKey(SAMSUNG) ->
                sighting(a, TrackerKind.SMARTTAG, separated = true, random)
            svc.any { it in DULT } -> sighting(a, TrackerKind.DULT, separated = true, random)
            else -> sighting(a, TrackerKind.UNKNOWN, separated = false, random)
        }
    }

    private fun sighting(a: RawAdvert, kind: TrackerKind, separated: Boolean, random: Boolean) =
        TrackerSighting(a.address, a.rssi, a.timeMs, kind, separated, random)
}
