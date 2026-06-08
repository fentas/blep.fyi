package fyi.blep.core.tracking

import fyi.blep.core.ble.RssiFilter
import fyi.blep.core.ble.SignalTrend
import fyi.blep.core.ble.Trend
import fyi.blep.core.spatial.MotionSample
import fyi.blep.core.spatial.angleDelta
import kotlin.math.abs

/**
 * The body-shielding tracking heuristic as a pure, deterministic state machine.
 *
 * Feed it smoothed-over-time RSSI samples via [onSample]; it returns a fresh
 * [TrackingStatus] each time and advances through [TrackingPhase] using ΔRSSI
 * trends. It holds no platform, coroutine, or I/O dependencies, so it is fully
 * unit-testable on the JVM.
 *
 * ### The idea
 * Holding the phone flat against the chest lets the body shield the signal from
 * behind, so the radio is effectively directional: signal peaks when the target
 * is *in front of you*. The flow is therefore:
 *  1. **Calibrate** a baseline (phone at chest).
 *  2. **Sweep** — turn in place until the signal peaks then dips (bearing found).
 *  3. **Walk** forward while it keeps improving; stop when it dips (overshoot).
 *  4. **Reorient** (sweep again) as needed, then **pinpoint** low to the ground.
 *  5. **Complete** when the signal is strong and steady.
 *
 * RSSI is noisy and multipath-prone, so this is an *assistive heuristic*, not a
 * precise locator — thresholds live in [TrackingTuning].
 */
class TrackingSession(
    private val tuning: TrackingTuning = TrackingTuning(),
) {
    private val filter = RssiFilter(tuning.emaAlpha)
    private val trend = SignalTrend(tuning.flatBand)

    private var phase = TrackingPhase.CALIBRATION
    private var phaseStartMs: Long = -1
    private var legEntryRssi: Double = 0.0
    private var completeHold = 0
    private var pinpointLoss = 0

    /** Averaged baseline captured during calibration (chest position), or null. */
    var baselineRssi: Double? = null
        private set

    /** The most recently emitted status (calibration prompt before any sample). */
    var status: TrackingStatus = calibrationPrompt(0f)
        private set

    private var calSum = 0.0
    private var calCount = 0

    // Compass-derived rotation accumulated during the current sweep leg, used to
    // require the user has actually turned before locking a bearing.
    private var prevHeading: Double? = null
    private var rotatedRad = 0.0
    private var sweepHeadingKnown = false

    /** Feeds one raw RSSI sample at [timeMs] and returns the new status. */
    fun onSample(rssi: Int, timeMs: Long): TrackingStatus = onSample(rssi, timeMs, null)

    /**
     * Feeds one RSSI sample plus optional [motion] context. The motion is used
     * only to make the heuristic more robust (e.g. don't lock a sweep bearing
     * until the user has actually rotated); with `motion == null` the behaviour
     * is identical to the RSSI-only [onSample].
     */
    fun onSample(rssi: Int, timeMs: Long, motion: MotionSample?): TrackingStatus {
        val smoothed = filter.update(rssi)
        val movement = trend.update(smoothed)
        val proximity = tuning.proximityOf(smoothed)

        // Accumulate how far we've turned this sweep leg, when a heading exists.
        if ((phase == TrackingPhase.AXIS_SWEEP || phase == TrackingPhase.REORIENT) && motion?.headingRad != null) {
            prevHeading?.let { rotatedRad += abs(angleDelta(motion.headingRad, it)) }
            prevHeading = motion.headingRad
            sweepHeadingKnown = true
        }

        val next = when (phase) {
            TrackingPhase.CALIBRATION -> calibrate(smoothed, timeMs, proximity)
            TrackingPhase.AXIS_SWEEP, TrackingPhase.REORIENT ->
                sweep(smoothed, movement, proximity)
            TrackingPhase.VECTOR_WALK -> walk(smoothed, movement, proximity)
            TrackingPhase.PINPOINT -> pinpoint(smoothed, proximity)
            TrackingPhase.COMPLETE -> complete()
        }
        status = next
        return next
    }

    /** Resets all state back to the start of calibration. */
    fun reset() {
        filter.reset()
        trend.reset()
        phase = TrackingPhase.CALIBRATION
        phaseStartMs = -1
        legEntryRssi = 0.0
        completeHold = 0
        pinpointLoss = 0
        baselineRssi = null
        calSum = 0.0
        calCount = 0
        rotatedRad = 0.0
        prevHeading = null
        sweepHeadingKnown = false
        status = calibrationPrompt(0f)
    }

    // ── Phase handlers ──────────────────────────────────────────────────────

    private fun calibrate(smoothed: Double, timeMs: Long, proximity: Float): TrackingStatus {
        if (phaseStartMs < 0) phaseStartMs = timeMs
        calSum += smoothed
        calCount++
        return if (timeMs - phaseStartMs >= tuning.calibrationMs) {
            baselineRssi = calSum / calCount
            enterLeg(TrackingPhase.AXIS_SWEEP, smoothed)
            TrackingStatus(
                phase = TrackingPhase.AXIS_SWEEP,
                guidance = Guidance(
                    "Now turn slowly",
                    "Rotate on the spot. I'll tell you when you're facing it.",
                    Tone.NEUTRAL,
                    GuidanceCue.SWEEP_START,
                ),
                proximity = proximity,
                arrow = arrow(curl = 0.4f, proximity = proximity),
            )
        } else {
            calibrationPrompt(proximity)
        }
    }

    private fun sweep(smoothed: Double, movement: Trend, proximity: Float): TrackingStatus {
        val rose = trend.peak - legEntryRssi >= tuning.sweepRiseDb
        val pastPeak = trend.dropFromPeak() >= tuning.sweepPeakDropDb
        // When we can measure rotation, require a real turn before locking — a
        // peak-then-dip while standing still is noise, not a bearing.
        val turnedEnough = !sweepHeadingKnown || rotatedRad >= tuning.minSweepRotationRad
        if (rose && pastPeak && turnedEnough) {
            // We turned through the strongest bearing — lock it in.
            val target = if (proximity >= tuning.pinpointProximity)
                TrackingPhase.PINPOINT else TrackingPhase.VECTOR_WALK
            enterLeg(target, smoothed)
            return statusForLegStart(target, proximity)
        }
        val cue = when (movement) {
            Trend.RISING -> Cue("Keep turning", "Warmer — you're facing it more.", Tone.WARMER, 0.4f, GuidanceCue.SWEEP_WARMER)
            Trend.FALLING -> Cue("Turn back", "Colder — go the other way.", Tone.COLDER, -0.4f, GuidanceCue.SWEEP_COLDER)
            Trend.FLAT -> Cue("Turn slowly", "Keep rotating to find the strongest point.", Tone.NEUTRAL, 0.4f, GuidanceCue.SWEEP_FLAT)
        }
        return TrackingStatus(phase, cue.guidance, proximity, arrow(cue.curl, proximity))
    }

    private fun walk(smoothed: Double, movement: Trend, proximity: Float): TrackingStatus {
        val rose = trend.peak - legEntryRssi >= tuning.walkRiseDb
        val overshot = rose && trend.dropFromPeak() >= tuning.walkPeakDropDb
        if (proximity >= tuning.pinpointProximity) {
            enterLeg(TrackingPhase.PINPOINT, smoothed)
            return statusForLegStart(TrackingPhase.PINPOINT, proximity)
        }
        if (overshot) {
            enterLeg(TrackingPhase.REORIENT, smoothed)
            return TrackingStatus(
                TrackingPhase.REORIENT,
                Guidance("Stop — turn again", "You passed it. Turn slowly to re-aim.", Tone.STOP, GuidanceCue.WALK_OVERSHOOT),
                proximity,
                arrow(-0.4f, proximity),
            )
        }
        val cue = when (movement) {
            Trend.RISING -> Cue("Keep going", "Warmer — straight ahead.", Tone.WARMER, 0f, GuidanceCue.WALK_WARMER)
            Trend.FALLING -> Cue("Stop", "Colder — pause and re-aim.", Tone.COLDER, 0.28f, GuidanceCue.WALK_COLDER)
            Trend.FLAT -> Cue("Walk forward", "Move slowly straight ahead.", Tone.NEUTRAL, 0f, GuidanceCue.WALK_FLAT)
        }
        return TrackingStatus(phase, cue.guidance, proximity, arrow(cue.curl, proximity))
    }

    private fun pinpoint(smoothed: Double, proximity: Float): TrackingStatus {
        if (proximity >= tuning.completeProximity) {
            completeHold++
            pinpointLoss = 0
            if (completeHold >= tuning.completeHoldSamples) {
                phase = TrackingPhase.COMPLETE
                return complete()
            }
        } else {
            completeHold = 0
            // Lost the close signal? Hand back to a re-sweep rather than dead-end.
            if (proximity < tuning.pinpointProximity - tuning.pinpointRegressMargin) {
                pinpointLoss++
                if (pinpointLoss >= tuning.pinpointLossSamples) {
                    enterLeg(TrackingPhase.REORIENT, smoothed)
                    return TrackingStatus(
                        TrackingPhase.REORIENT,
                        Guidance("Lost it — turn again", "Signal dropped. Turn slowly to re-aim.", Tone.STOP, GuidanceCue.PINPOINT_LOST),
                        proximity,
                        arrow(0.4f, proximity),
                    )
                }
            } else {
                pinpointLoss = 0
            }
        }
        return TrackingStatus(
            TrackingPhase.PINPOINT,
            Guidance("Almost there", "Kneel down and search low, near the floor.", Tone.WARMER, GuidanceCue.PINPOINT),
            proximity,
            arrow(curl = 0.0f, proximity = proximity),
        )
    }

    // proximity is pinned to 1f and the arrow collapses to 0 by design: once
    // complete the UI shows the celebration state, not live signal.
    private fun complete(): TrackingStatus = TrackingStatus(
        TrackingPhase.COMPLETE,
        Guidance("Finished", "Congratulations — you found it!", Tone.DONE, GuidanceCue.COMPLETE),
        proximity = 1f,
        arrow = ArrowDirective(curl = 0f, scale = 0f),
    )

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun enterLeg(target: TrackingPhase, smoothed: Double) {
        phase = target
        legEntryRssi = smoothed
        completeHold = 0
        pinpointLoss = 0
        // Rotation is per-leg, like the peak.
        rotatedRad = 0.0
        prevHeading = null
        sweepHeadingKnown = false
        // Peak is per-leg so dropFromPeak measures movement within this leg only.
        trend.reset()
        trend.update(smoothed)
    }

    private fun statusForLegStart(target: TrackingPhase, proximity: Float): TrackingStatus =
        when (target) {
            TrackingPhase.VECTOR_WALK -> TrackingStatus(
                target,
                Guidance("Walk forward", "Found it — move slowly straight ahead.", Tone.WARMER, GuidanceCue.WALK_FOUND),
                proximity, arrow(0f, proximity),
            )
            TrackingPhase.PINPOINT -> TrackingStatus(
                target,
                Guidance("Almost there", "Kneel down and search low, near the floor.", Tone.WARMER, GuidanceCue.PINPOINT),
                proximity, arrow(0.0f, proximity),
            )
            else -> TrackingStatus(
                target,
                Guidance("Turn slowly", "Rotate to re-aim.", Tone.NEUTRAL, GuidanceCue.REORIENT),
                proximity, arrow(0.4f, proximity),
            )
        }

    private fun calibrationPrompt(proximity: Float) = TrackingStatus(
        TrackingPhase.CALIBRATION,
        Guidance("Hold at your chest", "Keep the phone flat against your chest and stay still.", Tone.NEUTRAL, GuidanceCue.CALIBRATE),
        proximity,
        ArrowDirective(curl = 0f, scale = 0.8f),
    )

    /** Arrow scales from 0.7 (far) to 1.4 (close). */
    private fun arrow(curl: Float, proximity: Float) =
        ArrowDirective(curl = curl, scale = 0.7f + proximity * 0.7f)

    /** A guidance line plus the arrow curl that goes with it. */
    private data class Cue(val title: String, val detail: String, val tone: Tone, val curl: Float, val cue: GuidanceCue) {
        val guidance get() = Guidance(title, detail, tone, cue)
    }
}
