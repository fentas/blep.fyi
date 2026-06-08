package fyi.blep.core.ble

import kotlin.math.abs
import kotlin.math.roundToInt

/** Tunable thresholds for [RotationTracker]. */
data class RotationTuning(
    val staleMs: Long = 30_000L,    // a track unseen this long has left range (or fully handed over)
    val handoverMs: Long = 20_000L, // a successor id must appear within this AFTER the old id goes quiet
    val overlapMs: Long = 3_000L,   // brief window both ids may co-advertise during a handover
    val dbGate: Int = 6,            // |Δrssi| (dBm) for a successor to count as the same device's new range
    val emaWeight: Double = 0.5,    // smoothing of the running RSSI estimate
)

/** What the UI can show about a device's identity churn. */
data class RotationStats(
    val address: String,          // the device's current (latest) id
    val rssi: Int,                // smoothed signal of the current id
    val firstSeenMs: Long,        // when this physical device was first seen (carried across rotations)
    val lastSeenMs: Long,
    val rotations: Int,           // id handovers attributed to it (0 = stable id so far)
    val addressesSeen: Int,       // distinct ids it has worn (= rotations + 1)
    val confidence: Double,       // 0..1 mean quality of those handovers (1.0 when none contested)
    val contested: Boolean = false,          // shares an unresolved lineage with the ids in [alternatives]
    val alternatives: List<String> = emptyList(), // other current ids this rotation might belong to
)

/**
 * Correlates a churn of rotating BLE addresses back into logical *devices*, so the
 * UI can say "this thing has changed its id N times, first seen X ago" even though a
 * privacy-rotating tracker wears a fresh random address every ~15 min.
 *
 * The signal is the **handover**: one id goes quiet exactly as a new id appears at
 * the *same range* (within [RotationTuning.dbGate] dBm), so the close-by population
 * stays the same size — one out, one in. A new id at a *different* range with no
 * matching departure is just a new device (the population grew). We resolve this on
 * disappearance, not appearance, so two devices that merely sit at the same range are
 * never falsely merged — a merge only happens when an old id actually dies while a
 * matching successor lives on.
 *
 * Collisions don't throw data away. When a dying id has **more than one** plausible
 * successor (or two die together at the same range), the orphaned lineage becomes a
 * [Branch] shared across the candidates — both keep it, with the probability split —
 * and it's revalidated each tick until the ambiguity resolves itself: a candidate
 * leaves, or one keeps rotating and the other doesn't. While unresolved, the affected
 * stats report [RotationStats.contested] with the [RotationStats.alternatives], so the
 * detail page can show the fork transparently.
 *
 * Pure and deterministic: feed it [observe], read [statsFor]/[all]. RSSI-only and
 * heuristic, so [RotationStats.confidence] carries the uncertainty.
 */
class RotationTracker(private val tuning: RotationTuning = RotationTuning()) {

    private class Track(
        var address: String,
        var rssi: Double,
        var firstSeenMs: Long,
        var lastSeenMs: Long,
        val bornMs: Long,
        var rotations: Int = 0,
        var addressesSeen: Int = 1,
        var qualitySum: Double = 0.0, // sum of per-handover qualities (definite handovers only)
    )

    /** An orphaned lineage that could belong to >1 surviving id — kept, not discarded. */
    private class Branch(
        val firstSeenMs: Long,
        val rotations: Int,
        val addressesSeen: Int,
        val qualitySum: Double,
        val rssi: Double,
        val candidates: MutableSet<String>,
    )

    private val tracks = mutableListOf<Track>()
    private val branches = mutableListOf<Branch>()

    fun reset() {
        tracks.clear()
        branches.clear()
    }

    fun observe(address: String, rssi: Int, timeMs: Long) {
        if (address.isBlank()) return
        reconcile(timeMs)
        val exact = tracks.firstOrNull { it.address == address }
        if (exact != null) {
            exact.rssi = ema(exact.rssi, rssi)
            exact.lastSeenMs = timeMs
            return
        }
        // A brand-new id is born as its own track. If it's really a rotation of a
        // device whose old id is about to go quiet, reconcile() links them when that
        // old id dies (definite merge) or parks the lineage as a branch (contested).
        tracks += Track(address = address, rssi = rssi.toDouble(), firstSeenMs = timeMs, lastSeenMs = timeMs, bornMs = timeMs)
    }

    fun statsFor(address: String): RotationStats? {
        val tr = tracks.firstOrNull { it.address == address } ?: return null
        val branch = branches.firstOrNull { address in it.candidates }
        if (branch == null) {
            val confidence = if (tr.rotations == 0) 1.0 else (tr.qualitySum / tr.rotations).coerceIn(0.0, 1.0)
            return tr.toStats(tr.rotations, tr.addressesSeen, confidence)
        }
        // Contested: tentatively fold in the branch lineage, but split its quality by
        // how many candidates still claim it (so confidence reflects the fork).
        val hopQ = quality(branch.rssi, tr.rssi, branch.candidates.size)
        val rot = tr.rotations + branch.rotations + 1
        val addr = tr.addressesSeen + branch.addressesSeen
        val confidence = ((tr.qualitySum + branch.qualitySum + hopQ) / rot).coerceIn(0.0, 1.0)
        return tr.toStats(rot, addr, confidence, contested = true, alternatives = (branch.candidates - address).toList(), firstSeenMs = minOf(tr.firstSeenMs, branch.firstSeenMs))
    }

    fun all(): List<RotationStats> = tracks.mapNotNull { statsFor(it.address) }

    // ── correlation ──────────────────────────────────────────────────────────

    private fun reconcile(nowMs: Long) {
        retireStale(nowMs)
        resolveBranches(nowMs)
    }

    /** Retire tracks whose id has gone quiet, handing their lineage to a successor
     *  (definite merge) or to a [Branch] when the successor is ambiguous. */
    private fun retireStale(nowMs: Long) {
        val stale = tracks.filter { nowMs - it.lastSeenMs > tuning.staleMs }
        for (old in stale) {
            tracks.remove(old)
            val heirs = tracks.filter { heir -> isSuccessor(old, heir, nowMs) }
            when {
                heirs.size == 1 -> mergeInto(heirs[0], old, candidateCount = 1)
                heirs.size >= 2 -> branches += Branch(
                    firstSeenMs = old.firstSeenMs,
                    rotations = old.rotations,
                    addressesSeen = old.addressesSeen,
                    qualitySum = old.qualitySum,
                    rssi = old.rssi,
                    candidates = heirs.map { it.address }.toMutableSet(),
                )
                // 0 heirs → the device left range; its lineage ends (nothing to carry).
            }
        }
    }

    /** As candidates thin out, collapse a branch onto its sole survivor (definite),
     *  or drop it when every candidate has left. */
    private fun resolveBranches(nowMs: Long) {
        val live = tracks.asSequence().filter { nowMs - it.lastSeenMs <= tuning.staleMs }.map { it.address }.toHashSet()
        // A survivor absorbs at most one branch per pass: if N old ids fork onto the
        // same lone survivor, only one of them rotated into it — the rest left with the
        // candidate(s) that departed, so committing them all would double-count.
        val claimed = HashSet<String>()
        for (b in branches.toList()) {
            b.candidates.retainAll(live)
            val survivors = tracks.filter { it.address in b.candidates }
            when (survivors.size) {
                1 -> {
                    val s = survivors[0]
                    if (s.address !in claimed) {
                        val q = quality(b.rssi, s.rssi, candidateCount = 1)
                        s.firstSeenMs = minOf(s.firstSeenMs, b.firstSeenMs)
                        s.rotations += b.rotations + 1
                        s.addressesSeen += b.addressesSeen
                        s.qualitySum += b.qualitySum + q
                        claimed += s.address
                    }
                    branches.remove(b)
                }
                0 -> branches.remove(b)
                // else: still contested — leave it for a future tick to resolve.
            }
        }
    }

    private fun isSuccessor(old: Track, heir: Track, nowMs: Long): Boolean =
        nowMs - heir.lastSeenMs <= tuning.staleMs &&
            // born when the old id went quiet (a touch of overlap allowed), never before:
            // an id that predates the old's last sighting was coexisting, not a rotation.
            heir.bornMs >= old.lastSeenMs - tuning.overlapMs &&
            heir.bornMs <= old.lastSeenMs + tuning.handoverMs &&
            abs(heir.rssi - old.rssi) <= tuning.dbGate

    private fun mergeInto(heir: Track, old: Track, candidateCount: Int) {
        val q = quality(old.rssi, heir.rssi, candidateCount)
        heir.firstSeenMs = minOf(heir.firstSeenMs, old.firstSeenMs)
        heir.rotations += old.rotations + 1
        heir.addressesSeen += old.addressesSeen
        heir.qualitySum += old.qualitySum + q
    }

    /** Handover quality: closer range ⇒ higher, divided across rival candidates. */
    private fun quality(r1: Double, r2: Double, candidateCount: Int): Double {
        val range = (1.0 - abs(r1 - r2) / (tuning.dbGate + 1.0)).coerceIn(0.0, 1.0)
        return range / candidateCount.coerceAtLeast(1)
    }

    private fun ema(prev: Double, x: Int): Double = prev * (1 - tuning.emaWeight) + x * tuning.emaWeight

    private fun Track.toStats(
        rotations: Int,
        addressesSeen: Int,
        confidence: Double,
        contested: Boolean = false,
        alternatives: List<String> = emptyList(),
        firstSeenMs: Long = this.firstSeenMs,
    ) = RotationStats(
        address = address,
        rssi = rssi.roundToInt(),
        firstSeenMs = firstSeenMs,
        lastSeenMs = lastSeenMs,
        rotations = rotations,
        addressesSeen = addressesSeen,
        confidence = confidence,
        contested = contested,
        alternatives = alternatives,
    )
}
