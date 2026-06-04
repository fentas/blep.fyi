package fyi.blep.core.safety

/** Known separated-tracker protocols we can recognise from an advertisement. */
enum class TrackerKind { FIND_MY, GOOGLE_FIND_MY, TILE, SMARTTAG, DULT, UNKNOWN }

/** How loud to be about a suspected tracker. */
enum class Severity { INFO, WARN, ALERT }

/**
 * One normalised BLE advertisement sighting fed to [TrackerDetector]. The platform
 * scanner fills [kind]/[separated] from the manufacturer data + service UUIDs when
 * it recognises a tracker protocol, and [randomAddress] when the address looks like
 * a rotating/resolvable private address (the privacy MACs that defeat correlation).
 */
data class TrackerSighting(
    val address: String,
    val rssi: Int,
    val timeMs: Long,
    val kind: TrackerKind = TrackerKind.UNKNOWN,
    val separated: Boolean = false,
    val randomAddress: Boolean = false,
)

/** A suspected unwanted tracker to surface — and an address to hand the finder. */
data class TrackerAlert(
    val severity: Severity,
    val kind: TrackerKind,
    val title: String,
    val detail: String,
    val rssi: Int,                 // strongest recent reading, for the "find it" step
    val trackingAddress: String?,  // best current address to track down
)

/** Thresholds for [TrackerDetector] (all overridable / unit-tunable). */
data class TrackerTuning(
    val windowMs: Long = 15 * 60_000L,   // how much sighting history to keep
    val closeDbm: Int = -75,             // "near you" (roughly within a couple of metres)
    val nearbyMs: Long = 30_000L,        // close & present this long ⇒ "nearby"
    val followingMs: Long = 5 * 60_000L, // close & present this long ⇒ "following you"
    val bucketMs: Long = 60_000L,        // time-bucket for the rotation coverage estimate
    val rotationMinDistinct: Int = 4,    // distinct rotating IDs before it's suspicious
    val rotationMinCoverage: Double = 0.6, // fraction of recent time something was close
)

/**
 * Detects whether an unwanted Bluetooth tracker (an AirTag, Tile, SmartTag, a
 * Find My / DULT beacon, or an anonymous rotating-MAC device) is travelling **with
 * you** — the anti-stalking mirror of blep's normal hunt.
 *
 * Two fused signals:
 *  1. **By protocol.** A *separated* tracker advertises a recognisable type. Its
 *     identity may rotate, but the *kind* persisting close by is the tell — so we
 *     accumulate close presence per kind, which sidesteps the rotation entirely.
 *  2. **By rotation pattern.** For anonymous devices, the un-correlation *is* the
 *     correlation: a steady close presence carried by a churn of short-lived
 *     random addresses (one vanishing as the next appears, all at the same range)
 *     is the fingerprint of a privacy-rotating tracker shadowing you — and catches
 *     the pre-DULT / third-party trackers that type detection misses.
 *
 * Pure and deterministic; feed it [observe] and read [evaluate]. The platform
 * scan + UI live on top.
 */
class TrackerDetector(private val tuning: TrackerTuning = TrackerTuning()) {
    private val recent = ArrayDeque<TrackerSighting>()

    fun reset() = recent.clear()

    /** Records one advertisement sighting and ages out anything past the window. */
    fun observe(sighting: TrackerSighting) {
        recent.addLast(sighting)
        prune(sighting.timeMs)
    }

    private fun prune(nowMs: Long) {
        while (recent.isNotEmpty() && nowMs - recent.first().timeMs > tuning.windowMs) recent.removeFirst()
    }

    /** The current suspected trackers, strongest threat first. */
    fun evaluate(nowMs: Long): List<TrackerAlert> {
        prune(nowMs)
        val close = recent.filter { it.rssi >= tuning.closeDbm }
        if (close.isEmpty()) return emptyList()
        val alerts = mutableListOf<TrackerAlert>()

        // 1) Known tracker protocols — track presence per *kind* (rotation-proof).
        for (kind in TrackerKind.entries) {
            if (kind == TrackerKind.UNKNOWN) continue
            val ks = close.filter { it.kind == kind }
            if (ks.isEmpty()) continue
            val durMs = ks.maxOf { it.timeMs } - ks.minOf { it.timeMs }
            val rssi = ks.maxOf { it.rssi }
            val addr = ks.maxByOrNull { it.timeMs }?.address
            when {
                durMs >= tuning.followingMs -> alerts += TrackerAlert(
                    Severity.ALERT, kind,
                    "A ${label(kind)} may be following you",
                    "It has been near you for ${durMs / 60_000} min. If it isn't yours, find and disable it.",
                    rssi, addr,
                )
                ks.any { it.separated } || durMs >= tuning.nearbyMs -> alerts += TrackerAlert(
                    Severity.WARN, kind,
                    "Unknown ${label(kind)} nearby",
                    "A tracker that's separated from its owner is advertising close to you.",
                    rssi, addr,
                )
            }
        }

        // 2) Rotation pattern — anonymous churn that stays close.
        val unknown = close.filter { it.kind == TrackerKind.UNKNOWN && it.randomAddress }
        val distinct = unknown.map { it.address }.toHashSet().size
        if (distinct >= tuning.rotationMinDistinct && coverage(unknown, nowMs) >= tuning.rotationMinCoverage) {
            alerts += TrackerAlert(
                Severity.WARN, TrackerKind.UNKNOWN,
                "Something keeps reappearing near you",
                "$distinct anonymous devices have shadowed you at close range — possibly one tracker rotating its ID.",
                unknown.maxOf { it.rssi }, unknown.maxByOrNull { it.timeMs }?.address,
            )
        }
        return alerts.sortedByDescending { it.severity.ordinal }
    }

    /** Fraction of recent time-buckets that had at least one close sighting — high
     *  coverage + many distinct IDs = a continuous presence wearing new identities. */
    private fun coverage(sightings: List<TrackerSighting>, nowMs: Long): Double {
        if (sightings.isEmpty()) return 0.0
        val span = nowMs - sightings.minOf { it.timeMs }
        if (span < tuning.bucketMs) return if (sightings.isNotEmpty()) 1.0 else 0.0
        val buckets = (span / tuning.bucketMs).toInt() + 1
        val hit = sightings.map { ((nowMs - it.timeMs) / tuning.bucketMs).toInt() }.toHashSet().size
        return hit.toDouble() / buckets
    }

    private fun label(kind: TrackerKind) = when (kind) {
        TrackerKind.FIND_MY -> "AirTag / Find My tracker"
        TrackerKind.GOOGLE_FIND_MY -> "Find My Device tracker"
        TrackerKind.TILE -> "Tile"
        TrackerKind.SMARTTAG -> "SmartTag"
        TrackerKind.DULT -> "tracker"
        TrackerKind.UNKNOWN -> "device"
    }
}
