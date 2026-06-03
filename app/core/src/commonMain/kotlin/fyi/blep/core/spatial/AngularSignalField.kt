package fyi.blep.core.spatial

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
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
 *
 * The field is anchored to *where you swept it*. Walking straight only refreshes
 * the bin you face, so the rest ages — but only **while the signal is cooling**
 * (the heading you're walking has dropped well below the field's peak, i.e. you've
 * passed the target or turned off it). A straight approach that keeps getting
 * warmer never ages, so a good forward bearing isn't thrown away; but once you sail
 * past, the stale bins fade, confidence falls, and a fresh sweep is prompted —
 * which is the only way to notice the target has slid to your side.
 */
class AngularSignalField(
    private val binCount: Int = 24,
    private val binEma: Double = 0.5,
    private val coverageFraction: Double = 0.8,
    private val peakednessDb: Double = 5.0,
    private val staleHalfLifeM: Double = 3.0,
) {
    private val binRssi = DoubleArray(binCount) { Double.NaN }
    private val binFresh = DoubleArray(binCount) { 0.0 } // 1 = just sampled, →0 once stale
    private val binSize = 2.0 * PI / binCount

    fun reset() {
        for (i in 0 until binCount) { binRssi[i] = Double.NaN; binFresh[i] = 0.0 }
    }

    /** Folds an [rssi] reading at [headingRad] into its bin. If [movedM] > 0 and the
     *  reading is cooling (well below the field's peak), every bin ages first —
     *  stale geometry from back there fades so the bearing must be re-earned. */
    fun update(headingRad: Double, rssi: Double, movedM: Double = 0.0) {
        if (movedM > 0.0) {
            var max = -Double.MAX_VALUE
            for (i in 0 until binCount) if (live(i) && binRssi[i] > max) max = binRssi[i]
            if (max > -Double.MAX_VALUE && rssi < max - COOL_MARGIN_DB) {
                val keep = exp(-ln(2.0) * movedM / staleHalfLifeM)
                for (i in 0 until binCount) binFresh[i] *= keep
            }
        }
        val h = ((headingRad % (2 * PI)) + 2 * PI) % (2 * PI)
        val i = (h / binSize).toInt().coerceIn(0, binCount - 1)
        binRssi[i] = if (binRssi[i].isNaN()) rssi else binRssi[i] * (1.0 - binEma) + rssi * binEma
        binFresh[i] = 1.0
    }

    /** A bin counts only while it's both sampled and still fresh enough to trust. */
    private fun live(i: Int) = !binRssi[i].isNaN() && binFresh[i] > FRESH_MIN

    /** Bearing (rad, clockwise from north) of the strongest signal, or null. */
    val bearingRad: Double?
        get() {
            var min = Double.MAX_VALUE; var seen = 0
            for (i in 0 until binCount) if (live(i)) { seen++; if (binRssi[i] < min) min = binRssi[i] }
            if (seen < 2) return null
            var sx = 0.0; var sy = 0.0
            for (i in 0 until binCount) {
                if (!live(i)) continue
                val w = (binRssi[i] - min + 0.1).let { it * it } * binFresh[i]
                val c = (i + 0.5) * binSize
                sx += w * sin(c); sy += w * cos(c)
            }
            if (sx == 0.0 && sy == 0.0) return null
            return atan2(sx, sy)
        }

    /**
     * 0f … 1f. Needs a clear front/back difference **and** a near-full *fresh* sweep
     * — otherwise the "peak" is just the strongest heading sampled so far (the edge
     * of where you've turned, or stale data from metres back), which points the
     * wrong way. Trust it only once you've recently turned past the peak both sides.
     */
    val confidence: Float
        get() {
            var min = Double.MAX_VALUE; var max = -Double.MAX_VALUE; var fresh = 0.0; var seen = 0
            for (i in 0 until binCount) if (live(i)) {
                seen++; fresh += binFresh[i]
                if (binRssi[i] < min) min = binRssi[i]; if (binRssi[i] > max) max = binRssi[i]
            }
            if (seen < 2) return 0f
            val coverage = (fresh / (binCount * coverageFraction)).coerceIn(0.0, 1.0) // fresh full sweep
            val peakedness = ((max - min) / peakednessDb).coerceIn(0.0, 1.0)           // front/back = full
            if (!peakInterior()) return (coverage * peakedness * 0.4).toFloat() // edge peak: distrust
            return (coverage * peakedness).toFloat()
        }

    /** True when we've turned a clear margin *past* the peak on both sides — the
     *  strongest live bin has live neighbours out to ±2, and the signal is falling
     *  off on each side (not still rising toward an unswept stronger heading). The
     *  ±2 + descending test is what stops us committing to the *edge* of a partial
     *  sweep, which reads as a peak but points ~30° short of the true one. */
    private fun peakInterior(): Boolean {
        var bi = -1; var bv = -Double.MAX_VALUE
        for (i in 0 until binCount) if (live(i) && binRssi[i] > bv) { bv = binRssi[i]; bi = i }
        if (bi < 0) return false
        val l1 = (bi - 1 + binCount) % binCount; val r1 = (bi + 1) % binCount
        val l2 = (bi - 2 + binCount) % binCount; val r2 = (bi + 2) % binCount
        if (!live(l1) || !live(r1) || !live(l2) || !live(r2)) return false
        // Signal must descend outward on both sides — a real bump, not a slope.
        return binRssi[l2] <= binRssi[l1] && binRssi[r2] <= binRssi[r1]
    }

    private companion object {
        const val FRESH_MIN = 0.2     // below this a bin is too stale to count
        const val COOL_MARGIN_DB = 5.0 // forward signal this far below peak = cooling → age
    }
}
