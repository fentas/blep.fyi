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
    private const val AHEAD_DEG = 22.0

    fun instruction(snapshot: SpatialSnapshot): String? {
        val est = snapshot.target
        val bearing = est.bearingRad ?: return null
        val distance = est.distanceM ?: return null
        if (!snapshot.headingKnown || est.confidence < MIN_CONFIDENCE) return null

        val deg = angleDelta(bearing, snapshot.headingRad) * 180.0 / PI // + = clockwise = right
        val turn = when {
            abs(deg) < AHEAD_DEG -> "straight ahead"
            deg > 0 -> "turn ${roundTo5(deg)}° right"
            else -> "turn ${roundTo5(-deg)}° left"
        }
        return "$turn · ${distanceWord(distance)}"
    }

    private fun roundTo5(deg: Double): Int = ((deg / 5.0).roundToInt() * 5).coerceIn(5, 180)

    private fun distanceWord(m: Double): String = if (m < 1.5) "almost there" else "~${m.roundToInt()} m"
}
