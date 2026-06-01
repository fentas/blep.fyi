package fyi.blep.core.ble

/**
 * Smooths noisy RSSI readings with an exponential moving average (EMA) and
 * exposes the change (ΔRSSI) between successive smoothed values.
 *
 * RSSI from BLE advertisements is jittery (±5–10 dBm is common even when
 * still), so every layer above this one consumes the *smoothed* value rather
 * than raw samples.
 *
 * @param alpha smoothing factor in (0, 1]. Higher reacts faster but is noisier;
 *   0.3 keeps the body-shielding sweep responsive without chasing jitter.
 */
class RssiFilter(private val alpha: Double = 0.3) {

    init {
        require(alpha > 0.0 && alpha <= 1.0) { "alpha must be in (0, 1], was $alpha" }
    }

    private var ema: Double? = null
    private var previous: Double? = null

    /** Latest smoothed value in dBm, or `null` before the first sample. */
    val smoothed: Double? get() = ema

    /**
     * Feeds a raw sample and returns the updated smoothed value.
     * The first sample seeds the average directly.
     */
    fun update(rssi: Int): Double {
        previous = ema
        val next = ema?.let { it + alpha * (rssi - it) } ?: rssi.toDouble()
        ema = next
        return next
    }

    /**
     * Change between the two most recent smoothed values (current − previous).
     * Positive means the signal got stronger (warmer). `0.0` until two samples
     * have been seen.
     */
    val delta: Double
        get() {
            val cur = ema ?: return 0.0
            val prev = previous ?: return 0.0
            return cur - prev
        }

    fun reset() {
        ema = null
        previous = null
    }
}
