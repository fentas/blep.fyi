package fyi.blep.core.spatial

import kotlin.math.log10
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathLossCalibratorTest {

    private fun pointsFor(a: Double, n: Double, target: Vec2): List<TrackPoint> {
        val out = ArrayList<TrackPoint>()
        for (d in intArrayOf(1, 2, 3, 4, 6, 8, 11, 16)) {
            val pos = target + Vec2(d.toDouble(), 0.0)
            val rssi = a - 10.0 * n * log10(d.toDouble())
            out.add(TrackPoint(pos, rssi, 0L, 0.6f))
        }
        return out
    }

    @Test
    fun recovers_known_path_loss_parameters() {
        val target = Vec2(3.0, -2.0)
        val fit = PathLossCalibrator.calibrate(pointsFor(a = -62.0, n = 3.2, target = target), target)!!
        assertEquals(-62.0, fit.first, 1.5)
        assertEquals(3.2, fit.second, 0.3)
    }

    @Test
    fun returns_null_without_enough_distance_spread() {
        // All samples at ~the same distance from the target → can't fit a slope.
        val target = Vec2.ZERO
        val pts = (0 until 8).map { TrackPoint(Vec2(5.0, it * 0.01), -75.0, 0L, 0.5f) }
        assertNull(PathLossCalibrator.calibrate(pts, target))
    }

    @Test
    fun clamps_exponent_into_a_sane_range() {
        val target = Vec2.ZERO
        // Absurdly steep fall-off would imply n far above physical — must clamp.
        val pts = (1..8).map { d -> TrackPoint(Vec2(d.toDouble(), 0.0), -50.0 - 200.0 * log10(d.toDouble()), 0L, 0.5f) }
        val fit = PathLossCalibrator.calibrate(pts, target)!!
        assertTrue(fit.second in 1.6..6.0, "exponent not clamped: ${fit.second}")
    }
}
