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

/** Why a tracker is being surfaced. The UI turns this (+ [TrackerAlert.kind] and the
 *  params) into localized title/detail text — no English lives in the detector.
 *  PERSISTENT: the same *physical device* (re-identified by an active probe — serial /
 *  GATT structure / battery) keeps recurring across checks even though its address
 *  rotated, which an interval scan can't otherwise see. */
enum class AlertReason { FOLLOWING, SEPARATED_NEARBY, ROTATION, PERSISTENT }

/** A suspected unwanted tracker to surface — and an address to hand the finder.
 *  Structured (not pre-rendered text) so the UI can localize it. */
data class TrackerAlert(
    val severity: Severity,
    val kind: TrackerKind,
    val reason: AlertReason,
    val rssi: Int,                    // strongest recent reading, for the "find it" step
    val trackingAddress: String?,     // best current address to track down
    val durationMs: Long = 0L,        // FOLLOWING / PERSISTENT: how long it's been near you
    val distinctCount: Int = 0,       // ROTATION: number of anonymous IDs seen
    val crossSessionHours: Int = 0,   // set when promoted by cross-session history
    val crossSessionPlaces: Int = 0,  // distinct coarse places seen (location-aware only)
    val label: String? = null,        // a name an active probe learned for it, if any
)

/**
 * What the rotation correlator ([fyi.blep.core.ble.RotationTracker]) knows about the
 * *physical device* behind a rotating address. Supplied to [TrackerDetector] so the
 * rotation heuristic can count one device's identities instead of the crowd's.
 *
 * A null lineage means the address has never been linked to anything — its churn is
 * strangers, not rotation, and must not raise an alert.
 */
data class Lineage(
    val id: String,             // stable token for the physical device (its oldest address)
    val addressesSeen: Int,     // distinct ids this device has worn
    val rotations: Int,         // handovers we actually watched happen (0 = never linked)
    val confidence: Double,     // 0..1 mean quality of those handovers
    val contested: Boolean,     // shares an unresolved fork with another lineage
)

/** Thresholds for [TrackerDetector] (all overridable / unit-tunable). */
data class TrackerTuning(
    val windowMs: Long = 15 * 60_000L,   // how much sighting history to keep
    val closeDbm: Int = -75,             // "near you" (roughly within a couple of metres)
    val nearbyMs: Long = 30_000L,        // close & present this long ⇒ "nearby"
    val followingMs: Long = 5 * 60_000L, // close & present this long ⇒ "following you"
    val bucketMs: Long = 60_000L,        // time-bucket for the rotation coverage estimate
    // Rotation is judged **per correlated device**, never over the pooled crowd: a
    // handover we watched happen is the evidence, so N here means N observed hops
    // (a device that has worn N+1 ids), not N unrelated addresses standing near you.
    val rotationMinHandovers: Int = 1,     // correlated handovers before it's suspicious
    val rotationMinConfidence: Double = 0.5, // how sure those handovers must be
    val rotationMinCoverage: Double = 0.6, // fraction of recent time it was close
)

/**
 * User-selectable detection sensitivity — a [TrackerTuning] preset trading false
 * positives against how fast/eagerly a follower is flagged. (Manual for now;
 * movement-based auto-switching is future work — see docs/detection.md.)
 */
enum class ScanSensitivity(val tuning: TrackerTuning) {
    /** Fewer alerts — for busy/crowded places where strangers churn (commute). */
    RELAXED(TrackerTuning(closeDbm = -65, nearbyMs = 60_000L, followingMs = 10 * 60_000L, rotationMinHandovers = 2, rotationMinConfidence = 0.65, rotationMinCoverage = 0.7)),

    /** The default balance. */
    BALANCED(TrackerTuning()),

    /** Most vigilant — flags sooner and from a bit further (somewhere unfamiliar). */
    STRICT(TrackerTuning(closeDbm = -82, nearbyMs = 20_000L, followingMs = 3 * 60_000L, rotationMinHandovers = 1, rotationMinConfidence = 0.35, rotationMinCoverage = 0.5));

    companion object {
        fun fromName(name: String?): ScanSensitivity =
            entries.firstOrNull { it.name == name } ?: BALANCED
    }
}

/**
 * Detects whether an unwanted Bluetooth tracker (an AirTag, Tile, SmartTag, a
 * Find My / DULT beacon, or an anonymous rotating-MAC device) is travelling **with
 * you** — the anti-tracking mirror of blep's normal hunt.
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
 *     This is judged **per correlated device**, via [lineageOf]. Counting distinct
 *     addresses across the whole close population instead measures *crowd density* —
 *     four strangers' phones on a busy pavement trivially clear any such threshold —
 *     which is why the churn must first be tied to one lineage by an observed
 *     handover. No correlation ⇒ no rotation alert.
 *
 * Pure and deterministic; feed it [observe] and read [evaluate]. The platform
 * scan + UI live on top.
 */
class TrackerDetector(private val tuning: TrackerTuning = TrackerTuning()) {
    private val recent = ArrayDeque<TrackerSighting>()

    /** "Near you" threshold (dBm) — the probe/persistence layer only bothers with close devices. */
    val closeDbm: Int get() = tuning.closeDbm

    fun reset() = recent.clear()

    /** Records one advertisement sighting and ages out anything past the window. */
    fun observe(sighting: TrackerSighting) {
        recent.addLast(sighting)
        prune(sighting.timeMs)
    }

    private fun prune(nowMs: Long) {
        while (recent.isNotEmpty() && nowMs - recent.first().timeMs > tuning.windowMs) recent.removeFirst()
    }

    /**
     * The current suspected trackers, strongest threat first.
     *
     * @param lineageOf resolves an address to the physical device behind it, or null
     *   when it has never been correlated. The default — "nothing is correlated" —
     *   disables rotation detection entirely, which is the right behaviour for a
     *   scanner that cannot correlate (the watch's interval scan never sees the
     *   continuous churn); it leans on [AlertReason.PERSISTENT] instead of guessing
     *   from the crowd.
     */
    fun evaluate(nowMs: Long, lineageOf: (String) -> Lineage? = { null }): List<TrackerAlert> {
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
                    Severity.ALERT, kind, AlertReason.FOLLOWING, rssi, addr, durationMs = durMs,
                )
                ks.any { it.separated } || durMs >= tuning.nearbyMs -> alerts += TrackerAlert(
                    Severity.WARN, kind, AlertReason.SEPARATED_NEARBY, rssi, addr,
                )
            }
        }

        // 2) Rotation pattern — anonymous churn that stays close, tied to ONE device.
        // Grouped by lineage (insertion-ordered, so the alert order stays deterministic);
        // uncorrelated addresses are dropped rather than pooled — see the class doc.
        val unknown = close.filter { it.kind == TrackerKind.UNKNOWN && it.randomAddress }
        val lineages = LinkedHashMap<String, Lineage>()
        val byLineage = LinkedHashMap<String, MutableList<TrackerSighting>>()
        for (s in unknown) {
            val lin = lineageOf(s.address) ?: continue // never linked ⇒ a stranger, not a rotation
            lineages[lin.id] = lin
            byLineage.getOrPut(lin.id) { mutableListOf() } += s
        }
        for ((id, sightings) in byLineage) {
            val lin = lineages[id] ?: continue
            // A contested lineage is an unresolved fork between two candidate devices —
            // alerting on it would name the wrong one, so wait for it to settle.
            if (lin.contested) continue
            if (lin.rotations < tuning.rotationMinHandovers) continue
            if (lin.confidence < tuning.rotationMinConfidence) continue
            if (coverage(sightings, nowMs) < tuning.rotationMinCoverage) continue
            alerts += TrackerAlert(
                Severity.WARN, TrackerKind.UNKNOWN, AlertReason.ROTATION,
                sightings.maxOf { it.rssi }, sightings.maxByOrNull { it.timeMs }?.address,
                distinctCount = lin.addressesSeen,
            )
        }
        return alerts.sortedByDescending { it.severity.ordinal }
    }

    /** Fraction of recent time-buckets that had at least one close sighting — high
     *  coverage + many distinct IDs = a continuous presence wearing new identities. */
    private fun coverage(sightings: List<TrackerSighting>, nowMs: Long): Double {
        if (sightings.isEmpty()) return 0.0
        val span = nowMs - sightings.minOf { it.timeMs }
        if (span < tuning.bucketMs) return 1.0 // all within one bucket ⇒ fully covered
        val buckets = (span / tuning.bucketMs).toInt() + 1
        val hit = sightings.map { ((nowMs - it.timeMs) / tuning.bucketMs).toInt() }.toHashSet().size
        return hit.toDouble() / buckets
    }

    /**
     * A coarse, order-independent signature of the *stable* nearby-device backdrop —
     * the close, non-rotating, non-tracker addresses around you right now. Your home
     * set differs from your desk set differs from the café's, so this lets the
     * cross-session log tell apart the **contexts** a suspect recurs in *without GPS*
     * (see docs/detection.md). Rotating strangers (random addresses) and trackers are
     * excluded — they're churn, not backdrop. Null when there's no stable backdrop.
     */
    fun backdropFingerprint(nowMs: Long): String? {
        prune(nowMs)
        val backdrop = recent.asSequence()
            .filter { it.rssi >= tuning.closeDbm && !it.randomAddress && it.kind == TrackerKind.UNKNOWN }
            .map { it.address }
            .toHashSet()
        if (backdrop.isEmpty()) return null
        return stableHash(backdrop.sorted().joinToString(","))
    }

    /** Deterministic across platforms and process restarts (unlike String.hashCode on
     *  Native), so a persisted backdrop signature stays comparable over the week. */
    private fun stableHash(s: String): String {
        var h = 1125899906842597L
        for (c in s) h = 31 * h + c.code
        return h.toString(36)
    }
}
