package fyi.blep.core.spatial

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StepCounterTest {

    /** Feeds a peak then a trough [steps] times at [periodMs] between peaks. */
    private fun walk(counter: StepCounter, steps: Int, periodMs: Long): Double {
        var distance = 0.0
        var t = 0L
        repeat(steps) {
            distance += counter.onAccel(t, 3.0)              // peak
            distance += counter.onAccel(t + periodMs / 2, 0.2) // trough (re-arm)
            t += periodMs
        }
        return distance
    }

    @Test
    fun credits_distance_only_after_a_walking_rhythm_is_confirmed() {
        val counter = StepCounter(stepLengthM = 0.7, warmupSteps = 3)
        // 10 rhythmic steps → the first 2 warm up (no credit), the rest count.
        val distance = walk(counter, steps = 10, periodMs = 500)
        assertEquals(8 * 0.7, distance, 1e-9)
    }

    @Test
    fun isolated_phone_jostles_move_you_nowhere() {
        val counter = StepCounter(stepLengthM = 0.7)
        var distance = 0.0
        // Spikes far apart (no rhythm) — like picking up / tilting the phone.
        distance += counter.onAccel(0, 4.0)
        distance += counter.onAccel(200, 0.1)
        distance += counter.onAccel(3000, 4.0)   // 3 s later: rhythm broken
        distance += counter.onAccel(3200, 0.1)
        distance += counter.onAccel(8000, 4.0)
        assertEquals(0.0, distance, 1e-9)
    }

    @Test
    fun a_brief_walk_then_stopping_resets_the_warm_up() {
        val counter = StepCounter(stepLengthM = 0.7, warmupSteps = 3)
        walk(counter, steps = 6, periodMs = 500)           // walking → credits
        // Stop for a while, then a single step: rhythm lost, must warm up again.
        val resumed = counter.onAccel(20_000, 3.0)
        assertEquals(0.0, resumed, 1e-9)
    }

    @Test
    fun still_device_produces_no_steps() {
        val counter = StepCounter()
        var distance = 0.0
        var t = 0L
        repeat(20) { distance += counter.onAccel(t, 0.3); t += 100 }
        assertEquals(0.0, distance, 1e-9)
    }

    @Test
    fun step_distance_drives_dead_reckoning_without_speed() {
        val dr = DeadReckoner()
        dr.update(MotionSample(timeMs = 0, headingRad = 0.0))          // face north, no move
        dr.update(MotionSample(timeMs = 500, headingRad = 0.0, stepDistanceM = 0.7))
        dr.update(MotionSample(timeMs = 1000, headingRad = 0.0, stepDistanceM = 0.7))
        assertEquals(0.0, dr.position.x, 1e-9)
        assertTrue(kotlin.math.abs(dr.position.y - 1.4) < 1e-9, "expected 1.4 m north, got ${dr.position.y}")
    }
}
