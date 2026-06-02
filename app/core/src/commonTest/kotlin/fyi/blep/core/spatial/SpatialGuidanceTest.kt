package fyi.blep.core.spatial

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpatialGuidanceTest {

    private fun snap(headingRad: Double, bearingRad: Double?, distanceM: Double?, confidence: Float, headingKnown: Boolean = true) =
        SpatialSnapshot(
            here = Vec2.ZERO,
            headingRad = headingRad,
            headingKnown = headingKnown,
            velocity = Vec2.ZERO,
            path = emptyList(),
            target = TargetEstimate(position = Vec2(1.0, 1.0), bearingRad = bearingRad, distanceM = distanceM, confidence = confidence),
            onCourse = 0f,
        )

    @Test
    fun facing_north_target_east_says_turn_right() {
        val s = snap(headingRad = 0.0, bearingRad = PI / 2, distanceM = 8.0, confidence = 0.6f)
        assertEquals("turn 90° right · ~8 m", SpatialGuidance.instruction(s))
    }

    @Test
    fun target_to_the_left_says_turn_left() {
        val s = snap(headingRad = 0.0, bearingRad = -PI / 2, distanceM = 5.0, confidence = 0.6f)
        assertEquals("turn 90° left · ~5 m", SpatialGuidance.instruction(s))
    }

    @Test
    fun small_angle_says_straight_ahead() {
        val s = snap(headingRad = 0.0, bearingRad = 0.1, distanceM = 12.0, confidence = 0.6f)
        assertTrue(SpatialGuidance.instruction(s)!!.startsWith("straight ahead"))
    }

    @Test
    fun null_without_confidence_or_heading() {
        assertNull(SpatialGuidance.instruction(snap(0.0, PI / 2, 8.0, confidence = 0.2f)))
        assertNull(SpatialGuidance.instruction(snap(0.0, PI / 2, 8.0, confidence = 0.6f, headingKnown = false)))
        assertNull(SpatialGuidance.instruction(snap(0.0, null, null, confidence = 0.6f)))
    }
}
