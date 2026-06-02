package fyi.blep.core.tracking

/**
 * Tunable thresholds for the tracking heuristic. Defaults are sensible for
 * indoor BLE tracking; tests and field calibration can override them.
 *
 * All RSSI values are dBm (negative; closer to 0 = stronger/closer).
 */
data class TrackingTuning(
    /** Smoothed RSSI mapped to proximity 0f. */
    val rssiFar: Double = -95.0,
    /** Smoothed RSSI mapped to proximity 1f. */
    val rssiNear: Double = -45.0,
    /** EMA smoothing factor for the RSSI filter. */
    val emaAlpha: Double = 0.3,
    /** Slope band (dBm) treated as "flat" for trend detection. */
    val flatBand: Double = 1.5,
    /** Time (ms) the phone is held at the chest to log a baseline. */
    val calibrationMs: Long = 2500,
    /** Rise (dBm) above a leg's entry value that proves a real peak exists. */
    val sweepRiseDb: Double = 3.0,
    /** Drop (dBm) from the sweep peak that confirms we turned past the bearing. */
    val sweepPeakDropDb: Double = 4.0,
    /** Rise (dBm) above the walk's entry value that proves we're closing in. */
    val walkRiseDb: Double = 3.0,
    /** Drop (dBm) from the walk peak that means "you overshot — stop". */
    val walkPeakDropDb: Double = 5.0,
    /** Proximity at/above which a walk leg hands off to pinpoint. */
    val pinpointProximity: Float = 0.78f,
    /** Proximity at/above which, when sustained, tracking completes. */
    val completeProximity: Float = 0.94f,
    /** Consecutive in-range samples required to declare completion. */
    val completeHoldSamples: Int = 4,
    /** Drop below [pinpointProximity] by this much counts as "lost it again". */
    val pinpointRegressMargin: Float = 0.18f,
    /** Consecutive lost-signal samples in pinpoint before handing back to reorient. */
    val pinpointLossSamples: Int = 4,
    /**
     * Minimum rotation (radians) the user must actually turn before a sweep is
     * allowed to lock onto a bearing. Only enforced when a compass heading is
     * available — it stops RSSI noise from declaring a false peak while standing
     * still. ~0.35 rad ≈ 20°.
     */
    val minSweepRotationRad: Double = 0.35,
) {
    init {
        require(rssiNear > rssiFar) { "rssiNear must be stronger (greater) than rssiFar" }
    }

    /** Maps a smoothed RSSI to proximity in [0f, 1f]. */
    fun proximityOf(smoothedRssi: Double): Float {
        val frac = (smoothedRssi - rssiFar) / (rssiNear - rssiFar)
        return frac.coerceIn(0.0, 1.0).toFloat()
    }

    // Swift/ObjC can't call a Kotlin constructor whose params all have defaults
    // (no zero-arg init is exported), so expose an explicit factory for it.
    companion object {
        fun default() = TrackingTuning()
    }
}
