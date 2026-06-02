package fyi.blep.core.spatial

/**
 * A tiny pedometer: detects walking steps from the magnitude of *linear*
 * acceleration (gravity removed, in m/s²) and converts them to travelled
 * distance. Runs platform-side off whatever accelerometer stream a provider
 * already reads, so it needs no activity-recognition permission.
 *
 * Detection is a debounced peak with hysteresis: a step is counted when the
 * signal rises past [thresholdMps2] (and enough time has passed since the last
 * step), then it must dip back below half the threshold before another can fire.
 */
class StepCounter(
    private val stepLengthM: Double = 0.72,
    private val thresholdMps2: Double = 1.2,
    private val minIntervalMs: Long = 280,
) {
    private var armed = true
    private var lastStepMs = -1L

    fun reset() {
        armed = true
        lastStepMs = -1L
    }

    /**
     * Feeds one acceleration magnitude at [timeMs]; returns the distance to add
     * for this sample — [stepLengthM] when a step is detected, else 0.
     */
    fun onAccel(timeMs: Long, magnitudeMps2: Double): Double {
        if (magnitudeMps2 > thresholdMps2 && armed &&
            (lastStepMs < 0 || timeMs - lastStepMs >= minIntervalMs)
        ) {
            armed = false
            lastStepMs = timeMs
            return stepLengthM
        }
        if (magnitudeMps2 < thresholdMps2 * 0.5) armed = true // re-arm on the trough
        return 0.0
    }
}
