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

    fun instruction(snapshot: SpatialSnapshot): String? {
        if (!snapshot.headingKnown) return null // a left/right cue needs a compass

        // 1) Triangulated target → full turn-by-turn with a distance.
        val est = snapshot.target
        val bearing = est.bearingRad; val distance = est.distanceM
        if (bearing != null && distance != null && est.confidence >= MIN_CONFIDENCE) {
            val deg = angleDelta(bearing, snapshot.headingRad) * 180.0 / PI
            return "${turnPhrase(deg)} · ${distanceWord(distance)}"
        }

        // 2) Before triangulation, the compass + body-shielding still give a
        //    direction: turn toward the heading the signal is strongest in.
        val signal = snapshot.signalBearingRad
        if (signal != null && snapshot.signalBearingConfidence >= SIGNAL_MIN_CONFIDENCE) {
            val deg = angleDelta(signal, snapshot.headingRad) * 180.0 / PI
            return if (abs(deg) < AHEAD_DEG) "facing the signal" else "${turnPhrase(deg)} to the signal"
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
