package fyi.blep.core.spatial

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StepCounterTest {

    @Test
    fun counts_one_step_per_acceleration_peak() {
        val counter = StepCounter(stepLengthM = 0.7)
        var distance = 0.0
        var t = 0L
        // 10 walking oscillations: a high peak then a low trough, ~500 ms apart.
        repeat(10) {
            distance += counter.onAccel(t, 3.0); t += 250      // peak (step)
            distance += counter.onAccel(t, 0.2); t += 250      // trough (re-arm)
        }
        assertEquals(10 * 0.7, distance, 1e-9)
    }

    @Test
    fun debounces_rapid_spikes_into_a_single_step() {
        val counter = StepCounter(stepLengthM = 0.7, minIntervalMs = 280)
        var distance = 0.0
        // Several spikes within the debounce window with no trough between → 1 step.
        distance += counter.onAccel(0, 3.0)
        distance += counter.onAccel(50, 3.2)
        distance += counter.onAccel(100, 3.1)
        assertEquals(0.7, distance, 1e-9)
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
        // Two 0.7 m steps north with no speed field set at all.
        dr.update(MotionSample(timeMs = 500, headingRad = 0.0, stepDistanceM = 0.7))
        dr.update(MotionSample(timeMs = 1000, headingRad = 0.0, stepDistanceM = 0.7))
        assertEquals(0.0, dr.position.x, 1e-9)
        assertTrue(kotlin.math.abs(dr.position.y - 1.4) < 1e-9, "expected 1.4 m north, got ${dr.position.y}")
    }
}
