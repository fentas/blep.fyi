package fyi.blep.core.spatial

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpatialTrackerTest {

    private val tuning = SpatialTuning()           // rssiAt1m -59, n 2.5
    private val target = Vec2(8.0, 8.0)

    /** Path-loss RSSI for a true distance, with a tiny deterministic wobble. */
    private fun rssiAt(distance: Double, i: Int): Double {
        val d = distance.coerceAtLeast(0.5)
        return -59.0 - 25.0 * log10(d) + 0.4 * sin(i.toDouble())
    }

    /** Simulates an L-shaped walk past the target and returns the last snapshot. */
    private fun walkL(): Pair<SpatialTracker, SpatialSnapshot> {
        val tracker = SpatialTracker(tuning)
        var truth = Vec2.ZERO
        var lastT = -1L
        var i = 0
        var last: SpatialSnapshot? = null

        fun step(timeMs: Long, headingRad: Double, speed: Double) {
            val dt = if (lastT < 0) 0.0 else (timeMs - lastT) / 1000.0
            lastT = timeMs
            if (dt > 0.0) truth += Vec2.heading(headingRad) * (speed * dt)
            val rssi = rssiAt((truth - target).length, i++)
            last = tracker.update(
                rssi,
                MotionSample(timeMs = timeMs, headingRad = headingRad, speedMps = speed, moving = true),
            )
        }

        var t = 0L
        // Leg 1: north (+y) to (0,6)
        while (t <= 6_000) { step(t, 0.0, 1.0); t += 500 }
        // Leg 2: east (+x) to (6,6)
        while (t <= 12_000) { step(t, PI / 2, 1.0); t += 500 }
        return tracker to last!!
    }

    @Test
    fun deadReckoning_reconstructs_the_L_path() {
        val (_, snap) = walkL()
        // Ended near (6,6) by integrating heading + speed (no GPS).
        assertEquals(6.0, snap.here.x, 0.6)
        assertEquals(6.0, snap.here.y, 0.6)
        assertTrue(snap.path.size > 10, "path should accumulate samples")
    }

    @Test
    fun estimator_locates_the_target_after_an_L_walk() {
        val (_, snap) = walkL()
        val est = snap.target
        assertTrue(est.position != null, "expected a position estimate")
        val err = (est.position!! - target).length
        assertTrue(err < 4.0, "target estimate $err m off (pos=${est.position})")
        assertTrue(est.confidence > 0.3f, "confidence too low: ${est.confidence}")
    }

    @Test
    fun estimate_carries_an_uncertainty_ellipse_that_is_finite() {
        val (_, snap) = walkL()
        val est = snap.target
        assertTrue(est.semiMajorM != null && est.semiMinorM != null, "expected an uncertainty ellipse")
        assertTrue(est.semiMajorM!! >= est.semiMinorM!!, "major axis must be >= minor")
        assertTrue(est.semiMajorM!! < 15.0, "uncertainty unexpectedly huge: ${est.semiMajorM}")
    }

    @Test
    fun bearing_points_at_the_target_and_oncourse_is_positive() {
        val (_, snap) = walkL()
        val trueBearing = bearingOf(target - snap.here)
        val err = abs(angleDelta(snap.target.bearingRad!!, trueBearing))
        // Generous: at the final ~3 m range a small position error spans a wide
        // angle, so position accuracy (<4 m) is the meaningful check, not bearing.
        assertTrue(err < 50.0 * PI / 180.0, "bearing ${err * 180 / PI}° off")
        // Final leg walks roughly toward the target, so on-course should be > 0.
        assertTrue(snap.onCourse > 0f, "onCourse=${snap.onCourse}")
    }

    @Test
    fun gps_fix_pulls_the_track_toward_the_reported_position() {
        val tracker = SpatialTracker(tuning)
        // No heading/speed, just GPS fixes: the track should follow them.
        repeat(8) { k ->
            val p = Vec2(k.toDouble(), 0.0)
            val snap = tracker.update(
                rssi = -70.0,
                motion = MotionSample(timeMs = k * 500L, position = p, positionAccuracyM = 4.0),
            )
            if (k == 7) assertTrue((snap.here - p).length < 2.0, "GPS not tracked: ${snap.here}")
        }
    }

    @Test
    fun updateGeo_projects_raw_latlon_into_the_local_track() {
        val tracker = SpatialTracker(tuning)
        // Origin fix, then ~1 arc-second east (~21 m at this latitude) — the local
        // x should grow, y stay ~0, proving the lat/lon projection works.
        tracker.updateGeo(-70.0, 0L, 0.0, false, 50.0, 8.0, true, 4.0, 0.0, false, false)
        val snap = tracker.updateGeo(-68.0, 500L, 0.0, false, 50.0, 8.0003, true, 4.0, 0.0, true, false)
        assertTrue(snap.here.x > 10.0, "expected eastward local x, got ${snap.here}")
        assertTrue(kotlin.math.abs(snap.here.y) < 2.0, "expected ~0 northing, got ${snap.here.y}")
    }

    @Test
    fun no_sensors_degrades_to_a_single_point_without_crashing() {
        val tracker = SpatialTracker(tuning)
        var snap: SpatialSnapshot? = null
        repeat(20) { k -> snap = tracker.update(-80.0, MotionSample(timeMs = k * 500L)) }
        assertEquals(Vec2.ZERO, snap!!.here)              // never moved
        assertEquals(0f, snap!!.target.confidence)        // nothing to estimate from
        assertEquals(0f, snap!!.onCourse)
    }
}
