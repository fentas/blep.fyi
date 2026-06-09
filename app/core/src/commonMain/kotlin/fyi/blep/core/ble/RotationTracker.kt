package fyi.blep.core.ble

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Tunable thresholds for [RotationTracker]. */
data class RotationTuning(
    val staleMs: Long = 30_000L,    // a track unseen this long has left range (or fully handed over)
    val handoverMs: Long = 20_000L, // a successor id must appear within this AFTER the old id goes quiet
    val overlapMs: Long = 3_000L,   // brief window both ids may co-advertise during a handover
    val dbGate: Int = 6,            // *floor* for the |Δrssi| gate; widened to the device's own jitter
    // Each id is tracked with a tiny α-β filter: a smoothed level (α = emaWeight) plus a
    // trend (β), so a handover is matched against where the old id was *heading*, not a
    // frozen last value — and the leftover prediction residual is the device's true jitter
    // (a moving device's drift no longer pollutes it).
    val emaWeight: Double = 0.5,    // α: level smoothing of the RSSI tracker
    val beta: Double = 0.10,        // β: how fast the trend (slope) adapts
    val slopeMaxPerMs: Double = 0.02, // clamp the trend to ±20 dBm/s (beyond ⇒ a noise spike, not motion)
    val devWeight: Double = 0.25,   // smoothing of the running jitter (residual) estimate
    // The gate and the handover confidence scale to each device's *own* measured jitter:
    // a Δ within the noise is a great match, not a weak one. The estimate is a running
    // mean of the residual, clamped — floored so we never claim a noiseless reading,
    // ceilinged so a wild/transient reading can't widen the gate into false merges.
    val minNoise: Double = 2.0,     // jitter floor (dBm) — even a steady link wobbles this much
    val maxNoise: Double = 4.0,     // jitter ceiling — caps how far the gate can ever open
    val gateK: Double = 2.5,        // gate ≈ this × the device's jitter (never below dbGate)
    // Proximity prior: the closer the handover, the more certain it's the same device —
    // a tag right next to you that vanishes as another appears in that range almost
    // can't be anything else (nothing teleports to your side), whereas far-out churn is
    // people passing. So close range lifts the confidence floor; far range leaves it to
    // the dB/jitter match. (Crowding at the range is already handled by the contested
    // split, so this is purely about distance.)
    val nearDbm: Double = -60.0,    // at/above this ⇒ full proximity prior
    val farDbm: Double = -85.0,     // at/below this ⇒ none (just the dB match)
    val proxCap: Double = 0.85,     // most confidence the proximity prior alone can grant
)

/** One id this device has worn: how long it was seen, and — for a retired id — how
 *  confident the handover that ended it was. The live id has [current] = true. */
data class WornId(
    val address: String,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val quality: Double,           // 0..1 confidence of the hop that retired this id (1.0 for the live id)
    val current: Boolean = false,
) {
    /** How long this id was seen (ongoing for the live id). */
    val durationMs: Long get() = (lastSeenMs - firstSeenMs).coerceAtLeast(0L)
}

/** What the UI can show about a device's identity churn. */
data class RotationStats(
    val address: String,          // the device's current (latest) id
    val rssi: Int,                // smoothed signal of the current id
    val firstSeenMs: Long,        // when this physical device was first seen (carried across rotations)
    val lastSeenMs: Long,
    val rotations: Int,           // id handovers attributed to it (0 = stable id so far)
    val addressesSeen: Int,       // distinct ids it has worn (= rotations + 1)
    val confidence: Double,       // 0..1 *cumulative mean* of every handover's quality (not reset per hop)
    val contested: Boolean = false,          // shares an unresolved lineage with the ids in [alternatives]
    val alternatives: List<String> = emptyList(), // other current ids this rotation might belong to
    val history: List<WornId> = emptyList(), // every worn id (oldest first, live id last) with per-id timing + hop quality
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
        var rssi: Double,            // α-β tracked level (dBm)
        var slope: Double = 0.0,     // α-β tracked trend (dBm per ms)
        var dev: Double = 0.0,       // running mean |prediction residual| = this id's true jitter
        var firstSeenMs: Long,
        var lastSeenMs: Long,
        val bornMs: Long,
        var rotations: Int = 0,
        var addressesSeen: Int = 1,
        var qualitySum: Double = 0.0, // sum of per-handover qualities (definite handovers only)
        var fingerprint: String? = null, // rotation-stable payload signature, when known
        var origin: String = address, // oldest address in the lineage — a stable identity token
        val addresses: MutableSet<String> = linkedSetOf(address), // every address this device has worn
        val worn: MutableList<WornId> = mutableListOf(), // retired ancestor ids (oldest first), each with its hop quality
    ) {
        /** The full worn history including the current live id (oldest first, live last). */
        fun history(): List<WornId> = worn + WornId(address, bornMs, lastSeenMs, quality = 1.0, current = true)
    }

    /** An orphaned lineage that could belong to >1 surviving id — kept, not discarded. */
    private class Branch(
        val firstSeenMs: Long,
        val rotations: Int,
        val addressesSeen: Int,
        val qualitySum: Double,
        val rssi: Double,
        val dev: Double,
        val fingerprint: String?,
        val origin: String,
        val addresses: Set<String>,
        val candidates: MutableSet<String>,
        val worn: List<WornId>, // old's lineage (ancestors + old itself); old's quality is finalised on resolve
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
            // α-β filter step: predict the level forward along the trend, correct by the
            // residual. The residual is the genuine jitter (the trend is already removed),
            // so a steadily-moving device reads as low-noise, not high-noise.
            val dtMs = (timeMs - exact.lastSeenMs).coerceAtLeast(1L).toDouble()
            val predicted = exact.rssi + exact.slope * dtMs
            val residual = rssi - predicted
            exact.rssi = predicted + tuning.emaWeight * residual
            exact.slope = (exact.slope + tuning.beta / dtMs * residual)
                .coerceIn(-tuning.slopeMaxPerMs, tuning.slopeMaxPerMs)
            exact.dev = exact.dev * (1 - tuning.devWeight) + abs(residual) * tuning.devWeight
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
            return tr.toStats(tr.rotations, tr.addressesSeen, confidence, history = tr.history())
        }
        // Contested: tentatively fold in the branch lineage, but split its quality by
        // how many candidates still claim it (so confidence reflects the fork).
        val hopQ = quality(branch.rssi, tr.rssi, noise(branch.dev, tr.dev), branch.candidates.size, fpMatch(branch.fingerprint, tr.fingerprint))
        val rot = tr.rotations + branch.rotations + 1
        val addr = tr.addressesSeen + branch.addressesSeen
        val confidence = ((tr.qualitySum + branch.qualitySum + hopQ) / rot).coerceIn(0.0, 1.0)
        val branchHist = branch.worn.toMutableList()
        if (branchHist.isNotEmpty()) branchHist[branchHist.lastIndex] = branchHist.last().copy(quality = hopQ)
        return tr.toStats(
            rot, addr, confidence, contested = true, alternatives = (branch.candidates - address).toList(),
            firstSeenMs = minOf(tr.firstSeenMs, branch.firstSeenMs), history = branchHist + tr.history(),
        )
    }

    /** True when a handover *involving* [address] is pending resolution — a predecessor
     *  has just gone quiet (a plausible same-device match) but hasn't retired yet, so
     *  the rotation count is about to tick. Lets the UI show "correlating…" instead of
     *  snapping from "no change" to "changed Nx". */
    fun isCorrelating(address: String, nowMs: Long): Boolean {
        val heir = tracks.firstOrNull { it.address == address } ?: return false
        return tracks.any { old ->
            old.address != address &&
                nowMs - old.lastSeenMs > tuning.overlapMs && // gone quiet
                nowMs - old.lastSeenMs <= tuning.staleMs &&   // but not yet retired
                isSuccessor(old, heir, nowMs)
        }
    }

    fun all(): List<RotationStats> = tracks.mapNotNull { statsFor(it.address) }

    /** Follow a (possibly already-rotated) address to the current live id carrying its
     *  lineage. If [address] is still a live track it's returned as-is; otherwise we
     *  return whichever live track has worn it. Lets a detail page pinned to an old id
     *  move to the device's new id once the handover resolves, instead of going dead. */
    fun currentAddressFor(address: String): String? {
        tracks.firstOrNull { it.address == address }?.let { return it.address }
        return tracks.firstOrNull { address in it.addresses }?.address
    }

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
                    dev = old.dev,
                    fingerprint = old.fingerprint,
                    origin = old.origin,
                    addresses = old.addresses.toSet(),
                    candidates = heirs.map { it.address }.toMutableSet(),
                    worn = old.worn + WornId(old.address, old.bornMs, old.lastSeenMs, quality = 1.0), // quality finalised on resolve
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
                        val q = quality(b.rssi, s.rssi, noise(b.dev, s.dev), candidateCount = 1, fpMatch(b.fingerprint, s.fingerprint))
                        s.firstSeenMs = minOf(s.firstSeenMs, b.firstSeenMs)
                        s.rotations += b.rotations + 1
                        s.addressesSeen += b.addressesSeen
                        s.qualitySum += b.qualitySum + q
                        if (s.fingerprint == null) s.fingerprint = b.fingerprint
                        s.addresses.addAll(b.addresses)
                        // finalise the (previously contested) hop quality on the branch's last id, then prepend.
                        val resolved = b.worn.toMutableList()
                        if (resolved.isNotEmpty()) resolved[resolved.lastIndex] = resolved.last().copy(quality = q)
                        s.worn.addAll(0, resolved)
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
        val gate = effectiveGate(noise(old.dev, heir.dev), fpMatch(old.fingerprint, heir.fingerprint))
        // Compare the heir against where the old id was *heading* at the handover, not its
        // last frozen value — a device approaching/receding still lines up.
        return abs(heir.rssi - projected(old, heir.bornMs)) <= gate
    }

    private fun mergeInto(heir: Track, old: Track, candidateCount: Int) {
        val q = quality(projected(old, heir.bornMs), heir.rssi, noise(old.dev, heir.dev), candidateCount, fpMatch(old.fingerprint, heir.fingerprint))
        heir.firstSeenMs = minOf(heir.firstSeenMs, old.firstSeenMs)
        heir.rotations += old.rotations + 1
        heir.addressesSeen += old.addressesSeen
        heir.qualitySum += old.qualitySum + q
        if (heir.fingerprint == null) heir.fingerprint = old.fingerprint
        heir.addresses.addAll(old.addresses)
        // old's lineage (its ancestors + old itself, ended by this q-quality hop) precedes heir's.
        heir.worn.addAll(0, old.worn + WornId(old.address, old.bornMs, old.lastSeenMs, q))
        heir.origin = old.origin // the older lineage's origin wins (continuity)
    }

    /** The old id's level projected forward to [toMs] along its trend (bounded to the
     *  handover horizon, never backwards), so a moving device is matched where it's going. */
    private fun projected(t: Track, toMs: Long): Double {
        val horizon = (toMs - t.lastSeenMs).coerceIn(0L, tuning.handoverMs).toDouble()
        return t.rssi + t.slope * horizon
    }

    /** The jitter the two ids share — a running mean of their residuals, clamped to
     *  [[RotationTuning.minNoise], [RotationTuning.maxNoise]]: floored so we never claim a
     *  noiseless reading, ceilinged so one wild id can't open the gate to false merges. */
    private fun noise(devA: Double, devB: Double): Double =
        maxOf(devA, devB).coerceIn(tuning.minNoise, tuning.maxNoise)

    /** |Δrssi| a successor may sit from the old id: the bigger of the [RotationTuning.dbGate]
     *  floor and a multiple of the device's own jitter, doubled when a payload matches. */
    private fun effectiveGate(noise: Double, fpMatch: Boolean): Double {
        val g = maxOf(tuning.dbGate.toDouble(), ceil(tuning.gateK * noise))
        return if (fpMatch) g * 2 else g
    }

    /** 0..1 proximity prior from the handover's range: 1 when right next to you (≤ a
     *  couple of metres), 0 out in the ambient churn. */
    private fun proximity(rssi: Double): Double =
        ((rssi - tuning.farDbm) / (tuning.nearDbm - tuning.farDbm).coerceAtLeast(1.0)).coerceIn(0.0, 1.0)

    /** Handover quality. Three lifting factors, whichever is strongest, then divided
     *  across rival candidates:
     *   - the **range match** relative to the device's own jitter (Δ within the noise = 1);
     *   - a **payload-fingerprint** match (corroborates a loose range);
     *   - the **proximity prior** — a close handover is near-certain regardless of dB. */
    private fun quality(r1: Double, r2: Double, noise: Double, candidateCount: Int, fpMatch: Boolean = false): Double {
        val over = (abs(r1 - r2) - noise).coerceAtLeast(0.0)        // how far past the noise floor
        val span = (effectiveGate(noise, fpMatch = false) - noise).coerceAtLeast(1.0)
        val range = (1.0 - over / span).coerceIn(0.0, 1.0)
        val fpFloor = if (fpMatch) 0.85 else 0.0
        val proxFloor = tuning.proxCap * proximity(maxOf(r1, r2)) // the closer of the two readings
        val base = maxOf(range, fpFloor, proxFloor)
        return base / candidateCount.coerceAtLeast(1)
    }

    private fun fpMatch(a: String?, b: String?): Boolean = a != null && a == b
    private fun fpMismatch(a: String?, b: String?): Boolean = a != null && b != null && a != b

    private fun Track.toStats(
        rotations: Int,
        addressesSeen: Int,
        confidence: Double,
        contested: Boolean = false,
        alternatives: List<String> = emptyList(),
        firstSeenMs: Long = this.firstSeenMs,
        history: List<WornId> = emptyList(),
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
        history = history,
    )
}
