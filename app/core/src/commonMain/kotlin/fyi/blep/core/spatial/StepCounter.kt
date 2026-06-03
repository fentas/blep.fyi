package fyi.blep.core.spatial

/**
 * A walking pedometer that's deliberately conservative: it credits travelled
 * distance only once a **rhythmic walking cadence** is established, so handling
 * or tilting the phone — which spikes the accelerometer but isn't walking —
 * doesn't shove your position around.
 *
 * It detects acceleration peaks (debounced, with hysteresis), but a peak only
 * counts toward distance when it follows the previous one at a plausible walking
 * interval ([minIntervalMs]…[maxIntervalMs]). Consecutive in-rhythm peaks build a
 * streak; distance is credited only after [warmupSteps] of them and stops the
 * moment the rhythm breaks. Isolated spikes never build a streak, so they move
 * you nowhere.
 *
 * Works off whatever *linear* acceleration magnitude (gravity removed, m/s²) a
 * provider already reads, so it needs no activity-recognition permission.
 */
class StepCounter(
    private val stepLengthM: Double = 0.72,
    private val thresholdMps2: Double = 2.0,
    private val minIntervalMs: Long = 260,
    private val maxIntervalMs: Long = 1100,
    private val warmupSteps: Int = 3,
) {
    private var armed = true
    private var lastStepMs = -1L
    private var streak = 0

    fun reset() {
        armed = true
        lastStepMs = -1L
        streak = 0
    }

    /**
     * Feeds one acceleration magnitude at [timeMs]; returns the distance to add —
     * [stepLengthM] only while a confirmed walking rhythm is underway, else 0.
     */
    fun onAccel(timeMs: Long, magnitudeMps2: Double): Double {
        // Lost the rhythm (stood still / stopped) → require a fresh warm-up.
        if (lastStepMs >= 0 && timeMs - lastStepMs > maxIntervalMs) streak = 0

        var credited = 0.0
        if (magnitudeMps2 > thresholdMps2 && armed) {
            val interval = if (lastStepMs < 0) -1L else timeMs - lastStepMs
            if (interval in minIntervalMs..maxIntervalMs) {
                streak++
                if (streak >= warmupSteps) credited = stepLengthM
            } else {
                streak = 1 // a step, but not in walking rhythm — start over
            }
            lastStepMs = timeMs
            armed = false
        }
        if (magnitudeMps2 < thresholdMps2 * 0.5) armed = true // re-arm on the trough
        return credited
    }
}
