package fyi.blep.core.spatial

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

/** Which evidence a [Cue] came from — picks the wording and how much to trust it. */
enum class CueKind { RECOVER, SIGNAL, TARGET }

/**
 * One guidance decision as an absolute **world** bearing to walk (rad, clockwise
 * from north), plus how it was derived. Keeping it world-absolute (not "turn 30°
 * left") is what lets [GuidanceStabilizer] commit to a direction across ticks.
 */
data class Cue(
    val worldBearingRad: Double,
    val confidence: Float,
    val kind: CueKind,
    val distanceM: Double? = null,
)

/**
 * Turns a confident spatial estimate into a turn-by-turn instruction relative to
 * the way you're currently facing — e.g. "turn 30° left · ~8 m". Returns null
 * until the estimate is confident and a real compass heading exists (otherwise a
 * left/right cue would be meaningless).
 *
 * Split into [evaluate] (what does this snapshot say, as a world bearing) and
 * [phrase] (render it against your heading). [instruction] chains them for the
 * stateless callers; a stateful caller runs [evaluate] → [GuidanceStabilizer] →
 * [phrase] so the cue commits to a direction instead of thrashing.
 */
object SpatialGuidance {
    fun instruction(snapshot: SpatialSnapshot, tuning: SpatialTuning = SpatialTuning()): String? {
        val cue = evaluate(snapshot, tuning) ?: return null
        return phrase(cue, snapshot.headingRad, tuning)
    }

    /** The best cue for this snapshot, as a world bearing, or null if none is trustworthy. */
    fun evaluate(snapshot: SpatialSnapshot, tuning: SpatialTuning = SpatialTuning()): Cue? {
        if (!snapshot.headingKnown) return null // a left/right cue needs a compass

        // 0) Recovery: if the signal has dropped well below the warmest spot you
        //    walked through, you've wandered off — head back to it instead of
        //    chasing a now-misleading bearing.
        val warm = snapshot.warmestBearingRad
        if (warm != null && snapshot.recovering) return Cue(warm, RECOVER_CONFIDENCE, CueKind.RECOVER)

        // 1) Compass + body-shielding bearing first. Holding the phone to your body
        //    makes RSSI directional, which is exactly what *breaks* range-based
        //    trilateration (the signal is strong whenever you face the target, so
        //    the filter thinks it's on top of you) — so the heading the signal
        //    peaks in is the trustworthy cue.
        val signal = snapshot.signalBearingRad
        if (signal != null && snapshot.signalBearingConfidence >= tuning.signalMinConfidence) {
            return Cue(signal, snapshot.signalBearingConfidence, CueKind.SIGNAL)
        }

        // 2) Fall back to the triangulated target (e.g. you moved without turning,
        //    so there's no swept bearing yet) — turn-by-turn with a distance.
        val est = snapshot.target
        val bearing = est.bearingRad; val distance = est.distanceM
        if (bearing != null && distance != null && est.confidence >= tuning.guidanceMinConfidence) {
            return Cue(bearing, est.confidence, CueKind.TARGET, distance)
        }
        return null
    }

    /** Renders a [cue] as a turn instruction relative to [headingRad]. */
    fun phrase(cue: Cue, headingRad: Double, tuning: SpatialTuning = SpatialTuning()): String {
        val aheadDeg = tuning.aheadDeg
        val deg = angleDelta(cue.worldBearingRad, headingRad) * 180.0 / PI
        return when (cue.kind) {
            CueKind.RECOVER -> if (abs(deg) < aheadDeg) "straight ahead — warmer" else "${turnPhrase(deg, aheadDeg)} — warmer"
            CueKind.SIGNAL -> if (abs(deg) < aheadDeg) "facing the signal" else "${turnPhrase(deg, aheadDeg)} to the signal"
            CueKind.TARGET -> "${turnPhrase(deg, aheadDeg)} · ${distanceWord(cue.distanceM ?: 0.0)}"
        }
    }

    private fun turnPhrase(deg: Double, aheadDeg: Double): String = when { // + = clockwise = right
        abs(deg) < aheadDeg -> "straight ahead"
        deg > 0 -> "turn ${roundTo5(deg)}° right"
        else -> "turn ${roundTo5(-deg)}° left"
    }

    /** A floor-difference hint, e.g. "↑ 1 floor up" / "↓ 2 floors down", or null. */
    fun floorHint(floorDelta: Int): String? = when {
        floorDelta > 0 -> "↑ $floorDelta floor${plural(floorDelta)} up"
        floorDelta < 0 -> "↓ ${-floorDelta} floor${plural(-floorDelta)} down"
        else -> null
    }

    private fun plural(n: Int) = if (n == 1) "" else "s"

    private fun roundTo5(deg: Double): Int = ((deg / 5.0).roundToInt() * 5).coerceIn(5, 180)

    private fun distanceWord(m: Double): String = if (m < 1.5) "almost there" else "~${m.roundToInt()} m"

    // Recovery is a deliberate state, not a measured bearing — give it a moderate
    // confidence so the stabilizer treats a recover↔signal flip like any other.
    private const val RECOVER_CONFIDENCE = 0.5f
}

/**
 * Commits the guidance to a **direction** so it stops thrashing when no single cue
 * is reliable. Near the target, body-shielding swings every cue ±10 dB as you
 * turn, so the raw arbitration flip-flops; committing damps that.
 *
 * **But commitment is only safe in a stable field.** Under canopy / multipath the
 * signal genuinely changes from step to step, and there you *must* re-evaluate —
 * holding a direction just walks you into a tree. So the stabilizer is
 * **environment-aware**: it engages only while [SpatialSnapshot.signalVolatilityDb]
 * is below [noisyVolatilityDb] (clean line-of-sight), and passes guidance straight
 * through, untouched, once the field turns noisy (with hysteresis so it doesn't
 * chatter at the boundary).
 *
 * When engaged it: adopts a small correction instantly (responsive tracking), only
 * switches on a *reversal* once the new direction has persisted [persistTicks]
 * samples, and briefly holds through a one-frame dropout ([holdTicks]).
 *
 * Stateful — one per tracking session; call [reset] when the session restarts.
 */
class GuidanceStabilizer(
    private val adoptDeltaRad: Double = 90.0 * PI / 180.0,
    private val persistTicks: Int = 2,
    private val holdTicks: Int = 2,
    private val noisyVolatilityDb: Double = 2.2,
) {
    private var committed: Double? = null
    private var committedKind: CueKind = CueKind.SIGNAL
    private var pending: Double? = null
    private var pendingCount = 0
    private var unsupported = 0
    private var noisy = false // hysteretic regime flag

    fun reset() {
        committed = null; pending = null; pendingCount = 0; unsupported = 0; noisy = false
    }

    /** Folds the raw [cue] for this tick into the committed direction, given the
     *  current environmental [volatilityDb]. In a noisy field, commitment is off
     *  and the cue passes through unchanged. */
    fun stabilize(cue: Cue?, volatilityDb: Double): Cue? {
        // Regime classification with hysteresis: enter "noisy" at the threshold,
        // leave it only once the field has calmed to 80% of it.
        noisy = if (noisy) volatilityDb > noisyVolatilityDb * 0.8 else volatilityDb >= noisyVolatilityDb
        if (noisy) {
            committed = null; pending = null; pendingCount = 0; unsupported = 0
            return cue // fickle field: trust the freshest read, don't commit
        }

        if (cue == null) {
            val held = committed
            if (held != null && unsupported < holdTicks) { unsupported++; return Cue(held, 0f, committedKind) }
            committed = null; pending = null; pendingCount = 0; unsupported = 0
            return null
        }
        unsupported = 0
        val cur = committed
        // First fix, or a small correction in the same general direction → adopt now.
        if (cur == null || abs(angleDelta(cue.worldBearingRad, cur)) < adoptDeltaRad) {
            committed = cue.worldBearingRad; committedKind = cue.kind
            pending = null; pendingCount = 0
            return cue
        }
        // A reversal: only switch once the new direction has held for persistTicks.
        if (pending != null && abs(angleDelta(cue.worldBearingRad, pending!!)) < adoptDeltaRad) {
            pendingCount++
        } else {
            pending = cue.worldBearingRad; pendingCount = 1
        }
        if (pendingCount >= persistTicks) {
            committed = cue.worldBearingRad; committedKind = cue.kind
            pending = null; pendingCount = 0
            return cue
        }
        // Otherwise stay the course (keep walking the committed world direction).
        return Cue(cur, cue.confidence, committedKind, cue.distanceM)
    }
}
