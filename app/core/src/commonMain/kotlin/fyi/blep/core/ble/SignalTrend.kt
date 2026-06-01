package fyi.blep.core.ble

/** Direction the (smoothed) signal is moving. */
enum class Trend { RISING, FALLING, FLAT }

/**
 * Tracks the running peak of a smoothed signal and classifies its short-term
 * trend with hysteresis, so small jitter around a plateau reads as [Trend.FLAT]
 * instead of flapping between rising/falling.
 *
 * @param flatBand dBm band around zero slope treated as flat.
 */
class SignalTrend(private val flatBand: Double = 1.5) {

    /** Highest smoothed value observed since the last [reset]. */
    var peak: Double = Double.NEGATIVE_INFINITY
        private set

    private var last: Double? = null

    /** Feeds the next smoothed value and returns the current trend. */
    fun update(smoothed: Double): Trend {
        if (smoothed > peak) peak = smoothed
        val prev = last
        last = smoothed
        if (prev == null) return Trend.FLAT
        val change = smoothed - prev
        return when {
            change > flatBand -> Trend.RISING
            change < -flatBand -> Trend.FALLING
            else -> Trend.FLAT
        }
    }

    /** How far the latest value has dropped below the running peak (dBm, ≥ 0). */
    fun dropFromPeak(): Double {
        val l = last ?: return 0.0
        if (peak == Double.NEGATIVE_INFINITY) return 0.0
        return (peak - l).coerceAtLeast(0.0)
    }

    fun reset() {
        peak = Double.NEGATIVE_INFINITY
        last = null
    }
}
