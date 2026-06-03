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
    fun filter_finds_the_target_one_floor_up_after_you_climb() {
        val tracker = SpatialTracker(tuning)
        val tgt = Vec2(8.0, 8.0); val tgtZ = 3.0
        var truth = Vec2.ZERO; var lastT = -1L; var i = 0
        var last: SpatialSnapshot? = null

        fun step(timeMs: Long, headingRad: Double, stepDist: Double, altitude: Double) {
            val dt = if (lastT < 0) 0.0 else (timeMs - lastT) / 1000.0
            lastT = timeMs
            if (dt > 0.0 && stepDist > 0.0) truth += Vec2.heading(headingRad) * stepDist
            val horiz = (truth - tgt).length
            val d = kotlin.math.sqrt(horiz * horiz + (altitude - tgtZ) * (altitude - tgtZ)).coerceAtLeast(0.5)
            val rssi = -59.0 - 25.0 * kotlin.math.log10(d) + 0.4 * sin(i++.toDouble())
            last = tracker.update(
                rssi,
                MotionSample(timeMs = timeMs, headingRad = headingRad, stepDistanceM = stepDist, relativeAltitudeM = altitude, moving = stepDist > 0),
            )
        }

        var t = 0L
        // Ground floor L-walk to (6,6) for horizontal lock.
        repeat(13) { step(t, 0.0, if (t == 0L) 0.0 else 0.5, 0.0); t += 500 }
        repeat(12) { step(t, PI / 2, 0.5, 0.0); t += 500 }
        // Climb diagonally to (8,8) while rising to +3 m (one floor up).
        repeat(6) { k -> step(t, PI / 4, 0.5, ((k + 1) * 0.5).coerceAtMost(3.0)); t += 500 }
        // Stand by the target up here for a moment.
        repeat(4) { step(t, PI / 4, 0.0, 3.0); t += 500 }
        // Walk back down to the ground floor.
        repeat(3) { k -> step(t, PI / 4, 0.0, (3.0 - (k + 1)).coerceAtLeast(0.0)); t += 500 }

        assertTrue(last!!.floorDelta >= 1, "expected target ≥1 floor up, got ${last!!.floorDelta}")
    }

    @Test
    fun estimate_follows_a_moving_target() {
        // Following a moving target needs the process drift turned up (default is 0
        // — a stationary target, the common case).
        val tracker = SpatialTracker(SpatialTuning(targetDriftMps = 0.6))
        // The user paces back and forth along x (to keep triangulating) while the
        // target slides south from (8,8) to (8,1).
        var truth = Vec2.ZERO; var lastT = -1L; var i = 0
        var last: SpatialSnapshot? = null
        val startTgt = Vec2(8.0, 8.0); val endTgt = Vec2(8.0, 1.0)
        val steps = 60
        for (k in 0 until steps) {
            val t = k * 500L
            val dt = if (lastT < 0) 0.0 else (t - lastT) / 1000.0; lastT = t
            // pace: x oscillates 0..6, heading flips
            val goingRight = (k / 6) % 2 == 0
            val heading = if (goingRight) PI / 2 else -PI / 2 // pace east/west (no y drift)
            if (dt > 0.0) truth += Vec2.heading(heading) * 0.5
            val tgt = startTgt + (endTgt - startTgt) * (k.toDouble() / (steps - 1)) // slides south
            val d = (truth - tgt).length.coerceAtLeast(0.5)
            val rssi = -59.0 - 25.0 * kotlin.math.log10(d) + 0.4 * sin(i++.toDouble())
            last = tracker.update(rssi, MotionSample(timeMs = t, headingRad = heading, stepDistanceM = 0.5, moving = true))
        }
        val est = last!!.target.position!!
        // It should have tracked the target south — closer to where it ended up
        // than to where it began.
        assertTrue((est - endTgt).length < (est - startTgt).length, "did not follow the target: est=$est")
        assertTrue((est - endTgt).length < 6.0, "estimate ${(est - endTgt).length} m off the moving target")
    }

    @Test
    fun a_stationary_target_does_not_wander_while_you_stand_still() {
        val tracker = SpatialTracker(tuning) // default: stationary target, drift 0
        val target = Vec2(8.0, 8.0)
        var truth = Vec2.ZERO; var lastT = -1L; var i = 0
        var snap: SpatialSnapshot? = null
        fun step(timeMs: Long, headingRad: Double, stepDist: Double) {
            val dt = if (lastT < 0) 0.0 else (timeMs - lastT) / 1000.0; lastT = timeMs
            if (dt > 0.0 && stepDist > 0.0) truth += Vec2.heading(headingRad) * stepDist
            val d = (truth - target).length.coerceAtLeast(0.5)
            val rssi = -59.0 - 25.0 * kotlin.math.log10(d) + 1.0 * sin(i * 1.7) // signal keeps fluctuating
            snap = tracker.update(rssi, MotionSample(timeMs = timeMs, headingRad = headingRad, stepDistanceM = stepDist, moving = stepDist > 0))
            i++
        }
        var t = 0L
        repeat(13) { step(t, 0.0, if (it == 0) 0.0 else 0.5); t += 500 }   // walk north
        repeat(12) { step(t, PI / 2, 0.5); t += 500 }                       // walk east → localised
        val before = snap!!.target.position
        assertTrue(before != null, "should have localised before standing still")
        // Now STAND STILL (no travel) while the noisy signal keeps changing.
        repeat(40) { step(t, PI / 2, 0.0); t += 500 }
        val after = snap!!.target.position!!
        assertTrue((after - before!!).length < 0.5, "estimate wandered ${(after - before).length} m while standing still")
    }

    @Test
    fun coarse_gps_fixes_are_ignored_until_they_converge() {
        val dr = DeadReckoner()
        // A fresh, wildly inaccurate fix (like Maps' initial 200 m circle) must
        // not teleport us.
        dr.update(MotionSample(timeMs = 0, position = Vec2(500.0, 0.0), positionAccuracyM = 200.0))
        assertTrue(dr.position.length < 1.0, "coarse fix moved us to ${dr.position}")
        // Once it has converged to a usable accuracy, it's trusted.
        repeat(5) { dr.update(MotionSample(timeMs = (it + 1) * 500L, position = Vec2(10.0, 0.0), positionAccuracyM = 5.0)) }
        assertTrue(dr.position.x > 4.0, "accurate fix not used: ${dr.position}")
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
