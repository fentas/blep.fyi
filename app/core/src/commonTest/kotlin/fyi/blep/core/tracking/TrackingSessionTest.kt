package fyi.blep.core.tracking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackingSessionTest {

    // alpha = 1.0 → smoothed == raw, so transitions are easy to reason about.
    private fun session() = TrackingSession(TrackingTuning(emaAlpha = 1.0))

    /** Feeds calibration samples long enough to finish calibration. */
    private fun TrackingSession.calibrate(rssi: Int = -90): TrackingStatus {
        var status = onSample(rssi, 0)
        var t = 250L
        while (t <= 2500L) {
            status = onSample(rssi, t)
            t += 250
        }
        return status
    }

    @Test
    fun startsInCalibrationAskingForChestHold() {
        val s = session()
        val st = s.onSample(-90, 0)
        assertEquals(TrackingPhase.CALIBRATION, st.phase)
        assertTrue(st.guidance.title.contains("chest", ignoreCase = true))
    }

    @Test
    fun calibrationCompletesAndRecordsBaselineThenSweeps() {
        val s = session()
        val st = s.calibrate(rssi = -88)
        assertEquals(TrackingPhase.AXIS_SWEEP, st.phase)
        assertEquals(-88.0, s.baselineRssi!!, 1e-9)
    }

    @Test
    fun sweepGuidesWarmerWhenRisingAndColderWhenFalling() {
        val s = session()
        s.calibrate()
        assertEquals(Tone.WARMER, s.onSample(-86, 2750).guidance.tone) // rising
        // small dip (2 dB) stays below the 4 dB lock threshold -> COLDER, no transition
        val dip = s.onSample(-88, 3000)
        assertEquals(Tone.COLDER, dip.guidance.tone)
        assertEquals(TrackingPhase.AXIS_SWEEP, dip.phase)
    }

    @Test
    fun sweepLocksOntoBearingAndStartsWalk() {
        val s = session()
        s.calibrate() // entry -90
        s.onSample(-88, 2750) // rising
        s.onSample(-86, 3000) // rose >= 3
        s.onSample(-84, 3250) // peak -84
        val locked = s.onSample(-89, 3500) // dropped 5 from peak -> lock
        assertEquals(TrackingPhase.VECTOR_WALK, locked.phase)
    }

    @Test
    fun walkOvershootTriggersReorient() {
        val s = session()
        s.calibrate()
        // get into walk
        s.onSample(-88, 2750); s.onSample(-86, 3000); s.onSample(-84, 3250)
        s.onSample(-89, 3500) // -> VECTOR_WALK, entry -89
        s.onSample(-80, 3750) // rising
        s.onSample(-70, 4000) // peak -70
        val reorient = s.onSample(-78, 4250) // drop 8 from peak -> overshoot
        assertEquals(TrackingPhase.REORIENT, reorient.phase)
        assertEquals(Tone.STOP, reorient.guidance.tone)
    }

    @Test
    fun fullFlowReachesCompletion() {
        val s = session()
        s.calibrate()
        // sweep -> walk
        s.onSample(-88, 2750); s.onSample(-86, 3000); s.onSample(-84, 3250)
        s.onSample(-89, 3500) // VECTOR_WALK
        // walk closing in -> pinpoint at proximity >= 0.78 (rssi >= -56)
        s.onSample(-80, 3750); s.onSample(-70, 4000); s.onSample(-60, 4250)
        val pinpoint = s.onSample(-55, 4500)
        assertEquals(TrackingPhase.PINPOINT, pinpoint.phase)
        // sustain near-field (proximity >= 0.94 => rssi >= -48) for hold samples
        var last = pinpoint
        var t = 4750L
        repeat(4) { last = s.onSample(-47, t); t += 250 }
        assertEquals(TrackingPhase.COMPLETE, last.phase)
        assertEquals(Tone.DONE, last.guidance.tone)
        assertEquals(0f, last.arrow.scale)
        assertEquals(1f, last.proximity)
    }

    @Test
    fun pinpointHandsBackToReorientWhenSignalIsLost() {
        val s = session()
        s.calibrate()
        s.onSample(-88, 2750); s.onSample(-86, 3000); s.onSample(-84, 3250)
        s.onSample(-89, 3500) // VECTOR_WALK
        s.onSample(-80, 3750); s.onSample(-70, 4000); s.onSample(-60, 4250)
        val pinpoint = s.onSample(-55, 4500)
        assertEquals(TrackingPhase.PINPOINT, pinpoint.phase)
        // signal collapses (proximity well below pinpoint threshold) for N samples
        var last = pinpoint
        var t = 4750L
        repeat(4) { last = s.onSample(-92, t); t += 250 }
        assertEquals(TrackingPhase.REORIENT, last.phase)
    }

    @Test
    fun proximityIsClampedToUnitRange() {
        val tuning = TrackingTuning()
        assertEquals(0f, tuning.proximityOf(-120.0))
        assertEquals(1f, tuning.proximityOf(-20.0))
        assertTrue(tuning.proximityOf(-70.0) in 0f..1f)
    }

    @Test
    fun resetReturnsToCalibration() {
        val s = session()
        s.calibrate()
        s.reset()
        assertEquals(TrackingPhase.CALIBRATION, s.status.phase)
        assertTrue(s.baselineRssi == null)
    }
}
