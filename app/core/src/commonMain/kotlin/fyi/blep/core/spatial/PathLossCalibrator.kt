package fyi.blep.core.spatial

import kotlin.math.log10
import kotlin.math.max

/**
 * Fits the path-loss model `rssi = A − 10·n·log10(d)` from the signal samples and
 * a (confident) target position, by weighted linear regression of RSSI against
 * log-distance. Lets the tracker learn the environment's attenuation instead of
 * relying on fixed defaults. Returns null until there's enough distance spread.
 */
object PathLossCalibrator {

    private const val MIN_POINTS = 6
    private const val MIN_LOG_SPREAD = 0.3 // ≈ a 2× range ratio across the samples

    /** @return fitted (rssiAt1m, exponent) clamped to sane bounds, or null. */
    fun calibrate(points: List<TrackPoint>, target: Vec2): Pair<Double, Double>? {
        if (points.size < MIN_POINTS) return null
        var wSum = 0.0; var sx = 0.0; var sy = 0.0
        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
        for (p in points) {
            val d = max((p.pos - target).length, 0.5)
            val x = log10(d)
            val w = p.strength01 + 0.05
            wSum += w; sx += w * x; sy += w * p.rssi
            if (x < minX) minX = x; if (x > maxX) maxX = x
        }
        if (wSum < 1e-9 || (maxX - minX) < MIN_LOG_SPREAD) return null
        val mx = sx / wSum; val my = sy / wSum
        var sxx = 0.0; var sxy = 0.0
        for (p in points) {
            val d = max((p.pos - target).length, 0.5)
            val x = log10(d) - mx
            val w = p.strength01 + 0.05
            sxx += w * x * x; sxy += w * x * (p.rssi - my)
        }
        if (sxx < 1e-9) return null
        val slope = sxy / sxx              // = −10·n
        val intercept = my - slope * mx    // = rssiAt1m (RSSI at log10(d)=0 → d=1 m)
        val n = (-slope / 10.0).coerceIn(1.6, 6.0)
        val a = intercept.coerceIn(-75.0, -40.0)
        return a to n
    }
}
