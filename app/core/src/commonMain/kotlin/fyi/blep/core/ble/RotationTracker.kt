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
 * A device's stable internal **identity**, recovered across its rotating addresses.
 * [addresses] is every id this physical device has worn (so a rename/flag saved under
 * one can follow it to the next); [id] is a stable token (its oldest address). Trust
 * [addresses] only when [confidence] is high and not [contested] — propagating a label
 * across a weak link would move it onto the wrong device.
 */
data class Identity(
    val id: String,
    val addresses: Set<String>,
    val confidence: Double,
    val contested: Boolean,
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
        var fingerprint: String? = null, // rotation-stable payload signature, when known
        var origin: String = address, // oldest address in the lineage — a stable identity token
        val addresses: MutableSet<String> = linkedSetOf(address), // every address this device has worn
    )

    /** An orphaned lineage that could belong to >1 surviving id — kept, not discarded. */
    private class Branch(
        val firstSeenMs: Long,
        val rotations: Int,
        val addressesSeen: Int,
        val qualitySum: Double,
        val rssi: Double,
        val fingerprint: String?,
        val origin: String,
        val addresses: Set<String>,
        val candidates: MutableSet<String>,
    )

    private val tracks = mutableListOf<Track>()
    private val branches = mutableListOf<Branch>()

    fun reset() {
        tracks.clear()
        branches.clear()
    }

    /** [fingerprint] is an optional rotation-stable payload signature (see
     *  payloadFingerprint): a match corroborates a handover (widens the dB gate +
     *  lifts confidence), a mismatch vetoes it, null leaves it on RSSI alone. */
    fun observe(address: String, rssi: Int, timeMs: Long, fingerprint: String? = null) {
        if (address.isBlank()) return
        reconcile(timeMs)
        val exact = tracks.firstOrNull { it.address == address }
        if (exact != null) {
            exact.rssi = ema(exact.rssi, rssi)
            exact.lastSeenMs = timeMs
            if (fingerprint != null) exact.fingerprint = fingerprint
            return
        }
        // A brand-new id is born as its own track. If it's really a rotation of a
        // device whose old id is about to go quiet, reconcile() links them when that
        // old id dies (definite merge) or parks the lineage as a branch (contested).
        tracks += Track(address = address, rssi = rssi.toDouble(), firstSeenMs = timeMs, lastSeenMs = timeMs, bornMs = timeMs, fingerprint = fingerprint)
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
        val hopQ = quality(branch.rssi, tr.rssi, branch.candidates.size, fpMatch(branch.fingerprint, tr.fingerprint))
        val rot = tr.rotations + branch.rotations + 1
        val addr = tr.addressesSeen + branch.addressesSeen
        val confidence = ((tr.qualitySum + branch.qualitySum + hopQ) / rot).coerceIn(0.0, 1.0)
        return tr.toStats(rot, addr, confidence, contested = true, alternatives = (branch.candidates - address).toList(), firstSeenMs = minOf(tr.firstSeenMs, branch.firstSeenMs))
    }

    fun all(): List<RotationStats> = tracks.mapNotNull { statsFor(it.address) }

    /** The stable identity behind a current address (its lineage of worn addresses +
     *  how sure we are), or null if we've never seen it. The caller decides whether to
     *  trust [Identity.addresses] for propagating a label, based on the confidence. */
    fun identityFor(address: String): Identity? {
        val tr = tracks.firstOrNull { it.address == address } ?: return null
        val stats = statsFor(address) ?: return null
        return Identity(id = tr.origin, addresses = tr.addresses.toSet(), confidence = stats.confidence, contested = stats.contested)
    }

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
                    fingerprint = old.fingerprint,
                    origin = old.origin,
                    addresses = old.addresses.toSet(),
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
                        val q = quality(b.rssi, s.rssi, candidateCount = 1, fpMatch(b.fingerprint, s.fingerprint))
                        s.firstSeenMs = minOf(s.firstSeenMs, b.firstSeenMs)
                        s.rotations += b.rotations + 1
                        s.addressesSeen += b.addressesSeen
                        s.qualitySum += b.qualitySum + q
                        if (s.fingerprint == null) s.fingerprint = b.fingerprint
                        s.addresses.addAll(b.addresses)
                        s.origin = b.origin
                        claimed += s.address
                    }
                    branches.remove(b)
                }
                0 -> branches.remove(b)
                // else: still contested — leave it for a future tick to resolve.
            }
        }
    }

    private fun isSuccessor(old: Track, heir: Track, nowMs: Long): Boolean {
        if (nowMs - heir.lastSeenMs > tuning.staleMs) return false
        // born when the old id went quiet (a touch of overlap allowed), never before:
        // an id that predates the old's last sighting was coexisting, not a rotation.
        if (heir.bornMs < old.lastSeenMs - tuning.overlapMs || heir.bornMs > old.lastSeenMs + tuning.handoverMs) return false
        // Payload veto/boost: a different device class can't be the same device (veto);
        // a matching one corroborates the range, so we tolerate a wider dB jump.
        if (fpMismatch(old.fingerprint, heir.fingerprint)) return false
        val gate = if (fpMatch(old.fingerprint, heir.fingerprint)) tuning.dbGate * 2 else tuning.dbGate
        return abs(heir.rssi - old.rssi) <= gate
    }

    private fun mergeInto(heir: Track, old: Track, candidateCount: Int) {
        val q = quality(old.rssi, heir.rssi, candidateCount, fpMatch(old.fingerprint, heir.fingerprint))
        heir.firstSeenMs = minOf(heir.firstSeenMs, old.firstSeenMs)
        heir.rotations += old.rotations + 1
        heir.addressesSeen += old.addressesSeen
        heir.qualitySum += old.qualitySum + q
        if (heir.fingerprint == null) heir.fingerprint = old.fingerprint
        heir.addresses.addAll(old.addresses)
        heir.origin = old.origin // the older lineage's origin wins (continuity)
    }

    /** Handover quality: closer range ⇒ higher, divided across rival candidates;
     *  a payload-fingerprint match lifts a loose range match (it corroborates). */
    private fun quality(r1: Double, r2: Double, candidateCount: Int, fpMatch: Boolean = false): Double {
        val range = (1.0 - abs(r1 - r2) / (tuning.dbGate + 1.0)).coerceIn(0.0, 1.0)
        val base = if (fpMatch) maxOf(range, 0.85) else range
        return base / candidateCount.coerceAtLeast(1)
    }

    private fun fpMatch(a: String?, b: String?): Boolean = a != null && a == b
    private fun fpMismatch(a: String?, b: String?): Boolean = a != null && b != null && a != b

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
