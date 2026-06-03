package fyi.blep.core.spatial

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Body-shielding done with the **compass**: as you turn on the spot it records the
 * smoothed RSSI per heading bin, then reports the bearing the signal is strongest
 * in — i.e. roughly the direction of the target — without you having to walk.
 *
 * The bearing is the circular mean of the bin headings weighted by how far each
 * bin's signal rises above the weakest direction (squared, so the peak dominates).
 * [confidence] needs both enough of a turn (coverage) and a real front/back
 * difference (peakedness) before it's worth trusting.
 */
class AngularSignalField(private val binCount: Int = 24) {
    private val binRssi = DoubleArray(binCount) { Double.NaN }
    private val binSize = 2.0 * PI / binCount

    fun reset() {
        for (i in 0 until binCount) binRssi[i] = Double.NaN
    }

    fun update(headingRad: Double, rssi: Double) {
        val h = ((headingRad % (2 * PI)) + 2 * PI) % (2 * PI)
        val i = (h / binSize).toInt().coerceIn(0, binCount - 1)
        binRssi[i] = if (binRssi[i].isNaN()) rssi else binRssi[i] * 0.5 + rssi * 0.5
    }

    /** Bearing (rad, clockwise from north) of the strongest signal, or null. */
    val bearingRad: Double?
        get() {
            var min = Double.MAX_VALUE; var seen = 0
            for (v in binRssi) if (!v.isNaN()) { seen++; if (v < min) min = v }
            if (seen < 2) return null
            var sx = 0.0; var sy = 0.0
            for (i in 0 until binCount) {
                val v = binRssi[i]
                if (v.isNaN()) continue
                val w = (v - min + 0.1).let { it * it }
                val c = (i + 0.5) * binSize
                sx += w * sin(c); sy += w * cos(c)
            }
            if (sx == 0.0 && sy == 0.0) return null
            return atan2(sx, sy)
        }

    /**
     * 0f … 1f. Needs a clear front/back difference **and** a near-full sweep —
     * otherwise the "peak" is just the strongest heading sampled so far (the edge
     * of where you've turned), which points the wrong way. Trust it only once
     * you've turned past the peak from both sides.
     */
    val confidence: Float
        get() {
            var min = Double.MAX_VALUE; var max = -Double.MAX_VALUE; var seen = 0
            for (v in binRssi) if (!v.isNaN()) { seen++; if (v < min) min = v; if (v > max) max = v }
            if (seen < 2) return 0f
            val coverage = (seen / (binCount * 0.8)).coerceIn(0.0, 1.0) // ~290° swept = full
            val peakedness = ((max - min) / 5.0).coerceIn(0.0, 1.0)     // ~5 dB front/back = full
            if (!peakInterior()) return (coverage * peakedness * 0.4).toFloat() // edge peak: distrust
            return (coverage * peakedness).toFloat()
        }

    /** True when the strongest bin has sampled (non-empty) neighbours on both
     *  sides — i.e. we've actually turned through the peak, not stopped at it. */
    private fun peakInterior(): Boolean {
        var bi = -1; var bv = -Double.MAX_VALUE
        for (i in 0 until binCount) { val v = binRssi[i]; if (!v.isNaN() && v > bv) { bv = v; bi = i } }
        if (bi < 0) return false
        val left = binRssi[(bi - 1 + binCount) % binCount]
        val right = binRssi[(bi + 1) % binCount]
        return !left.isNaN() && !right.isNaN()
    }
}
