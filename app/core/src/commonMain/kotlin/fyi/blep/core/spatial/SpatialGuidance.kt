package fyi.blep.core.spatial

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns a confident spatial estimate into a turn-by-turn instruction relative to
 * the way you're currently facing — e.g. "turn 30° left · ~8 m". Returns null
 * until the estimate is confident and a real compass heading exists (otherwise a
 * left/right cue would be meaningless).
 */
object SpatialGuidance {
    const val MIN_CONFIDENCE = 0.4f
    const val SIGNAL_MIN_CONFIDENCE = 0.35f
    private const val AHEAD_DEG = 22.0

    const val RECOVER_DB = 6.0

    fun instruction(snapshot: SpatialSnapshot): String? {
        if (!snapshot.headingKnown) return null // a left/right cue needs a compass

        // 0) Recovery: if the signal has dropped well below the warmest spot you
        //    walked through, you've wandered off — head back to it instead of
        //    chasing a now-misleading bearing.
        val warm = snapshot.warmestBearingRad
        if (warm != null && snapshot.belowWarmestDb >= RECOVER_DB) {
            val deg = angleDelta(warm, snapshot.headingRad) * 180.0 / PI
            return if (abs(deg) < AHEAD_DEG) "straight ahead — warmer" else "${turnPhrase(deg)} — warmer"
        }

        // 1) Compass + body-shielding bearing first. Holding the phone to your
        //    body makes RSSI directional, which is exactly what *breaks*
        //    range-based trilateration (the signal is strong whenever you face the
        //    target, so the filter thinks it's right on top of you) — so the
        //    heading the signal peaks in is the trustworthy cue.
        val signal = snapshot.signalBearingRad
        if (signal != null && snapshot.signalBearingConfidence >= SIGNAL_MIN_CONFIDENCE) {
            val deg = angleDelta(signal, snapshot.headingRad) * 180.0 / PI
            return if (abs(deg) < AHEAD_DEG) "facing the signal" else "${turnPhrase(deg)} to the signal"
        }

        // 2) Fall back to the triangulated target (e.g. you moved without turning,
        //    so there's no swept bearing yet) — turn-by-turn with a distance.
        val est = snapshot.target
        val bearing = est.bearingRad; val distance = est.distanceM
        if (bearing != null && distance != null && est.confidence >= MIN_CONFIDENCE) {
            val deg = angleDelta(bearing, snapshot.headingRad) * 180.0 / PI
            return "${turnPhrase(deg)} · ${distanceWord(distance)}"
        }
        return null
    }

    private fun turnPhrase(deg: Double): String = when { // + = clockwise = right
        abs(deg) < AHEAD_DEG -> "straight ahead"
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
}
