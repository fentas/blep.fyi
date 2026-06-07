package fyi.blep.core.tracking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The controller's "signal lost / age" state machine, tested as a pure function. */
class SignalFreshnessTest {

    @Test
    fun fresh_reading_is_not_lost_and_reports_its_age() {
        val f = signalFreshness(sinceLastRssiMs = 1_500, sinceStartMs = 9_000)
        assertFalse(f.lost)
        assertEquals(1, f.ageSec) // age tracks the last reading, not time since start
    }

    @Test
    fun a_stale_reading_is_lost() {
        val f = signalFreshness(sinceLastRssiMs = 4_001, sinceStartMs = 20_000)
        assertTrue(f.lost)
        assertEquals(4, f.ageSec)
    }

    @Test
    fun at_the_threshold_it_is_not_yet_lost() {
        // Strictly-greater-than, so exactly lostAfterMs is still considered fresh.
        assertFalse(signalFreshness(sinceLastRssiMs = 4_000, sinceStartMs = null).lost)
    }

    @Test
    fun never_acquired_within_grace_is_not_lost() {
        val f = signalFreshness(sinceLastRssiMs = null, sinceStartMs = 3_000)
        assertFalse(f.lost)
        assertEquals(3, f.ageSec) // falls back to time-since-start
    }

    @Test
    fun never_acquired_past_grace_is_lost() {
        // Device off / out of range / never seen — flagged after the grace period.
        assertTrue(signalFreshness(sinceLastRssiMs = null, sinceStartMs = 6_001).lost)
    }

    @Test
    fun not_tracking_yet_is_not_lost_and_zero_age() {
        val f = signalFreshness(sinceLastRssiMs = null, sinceStartMs = null)
        assertFalse(f.lost)
        assertEquals(0, f.ageSec)
    }

    @Test
    fun custom_thresholds_are_honoured() {
        assertTrue(signalFreshness(sinceLastRssiMs = 1_001, sinceStartMs = null, lostAfterMs = 1_000).lost)
        assertTrue(signalFreshness(sinceLastRssiMs = null, sinceStartMs = 2_001, graceMs = 2_000).lost)
    }
}
