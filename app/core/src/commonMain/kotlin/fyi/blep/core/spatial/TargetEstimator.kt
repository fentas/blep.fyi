package fyi.blep.core.spatial

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Predicts where the target is from the RSSI field sampled along the path.
 *
 * Two complementary methods, blended by how much usable geometry exists:
 *
 *  - **Gradient bearing** (works almost immediately): a weighted plane fit of
 *    RSSI over position gives the uphill direction — i.e. the bearing toward the
 *    target. Enough to say "you're heading the wrong way" after a few metres.
 *  - **Range multilateration** (once you've moved enough): each sample's RSSI is
 *    converted to a rough range via the path-loss model, and the target position
 *    is the weighted least-squares intersection of those range circles.
 *
 * Both are approximate — RSSI is noisy and multipath-prone — so every estimate
 * carries a [TargetEstimate.confidence] the UI can fade with.
 */
object TargetEstimator {

    private const val MIN_SPREAD_M = 2.0      // movement spread before LSQ is meaningful
    private const val GOOD_SPREAD_M = 7.0     // spread at which geometry is considered strong
    private const val DET_EPS = 1e-6

    fun estimate(points: List<TrackPoint>, here: Vec2, tuning: SpatialTuning): TargetEstimate {
        if (points.size < 3) return TargetEstimate.NONE

        val anchors = declusterStrongest(points, tuning)
        if (anchors.size < 3) return gradientOnly(points, here, anchors)

        val spread = maxPairwiseDistance(anchors)
        val gradient = gradientBearing(anchors)

        if (spread < MIN_SPREAD_M) return gradientOnly(points, here, anchors)

        val solved = multilaterate(anchors, tuning)
            ?: return gradientOnly(points, here, anchors)

        val toTarget = solved - here
        val distance = toTarget.length
        // Reject implausible solutions (beyond model range) — lean on the gradient.
        if (distance > tuning.maxRangeM * 1.5) return gradientOnly(points, here, anchors)

        val rms = rangeResidualRms(anchors, solved, tuning)
        val spreadScore = ((spread - MIN_SPREAD_M) / (GOOD_SPREAD_M - MIN_SPREAD_M)).coerceIn(0.0, 1.0)
        val countScore = min(1.0, anchors.size / 8.0)
        val fitScore = 1.0 / (1.0 + rms / 3.0)
        val confidence = (spreadScore * countScore * fitScore).coerceIn(0.0, 1.0).toFloat()

        // Prefer the LSQ bearing, but if confidence is weak blend toward the gradient.
        val bearing = if (gradient != null && confidence < 0.4f) {
            bearingOf(toTarget.normalizedOrZero() * confidence.toDouble() + Vec2.heading(gradient) * (1.0 - confidence))
        } else {
            bearingOf(toTarget)
        }

        return TargetEstimate(
            position = solved,
            bearingRad = bearing,
            distanceM = distance,
            confidence = confidence,
        )
    }

    // ── Gradient-only (early / poor geometry) ─────────────────────────────────

    private fun gradientOnly(points: List<TrackPoint>, here: Vec2, anchors: List<TrackPoint>): TargetEstimate {
        val g = gradientBearing(anchors.ifEmpty { points }) ?: return TargetEstimate.NONE
        val strongest = points.maxByOrNull { it.rssi } ?: return TargetEstimate.NONE
        // Coarse remaining distance from the strongest sample's range.
        val dist = (here - strongest.pos).length.coerceAtLeast(1.0)
        val conf = (0.18f + 0.12f * min(1f, points.size / 12f)).coerceAtMost(0.35f)
        return TargetEstimate(
            position = here + Vec2.heading(g) * dist,
            bearingRad = g,
            distanceM = dist,
            confidence = conf,
        )
    }

    /** Weighted plane fit of RSSI over position; returns the uphill bearing. */
    private fun gradientBearing(pts: List<TrackPoint>): Double? {
        if (pts.size < 3) return null
        var wSum = 0.0; var cx = 0.0; var cy = 0.0; var rMean = 0.0
        for (p in pts) {
            val w = (p.strength01 + 0.05).toDouble()
            wSum += w; cx += w * p.pos.x; cy += w * p.pos.y; rMean += w * p.rssi
        }
        if (wSum < 1e-9) return null
        cx /= wSum; cy /= wSum; rMean /= wSum
        var sxx = 0.0; var sxy = 0.0; var syy = 0.0; var sxr = 0.0; var syr = 0.0
        for (p in pts) {
            val w = (p.strength01 + 0.05).toDouble()
            val dx = p.pos.x - cx; val dy = p.pos.y - cy; val dr = p.rssi - rMean
            sxx += w * dx * dx; sxy += w * dx * dy; syy += w * dy * dy
            sxr += w * dx * dr; syr += w * dy * dr
        }
        // Ridge term so a near-collinear path (little spread on one axis) still
        // resolves to the dominant uphill direction instead of going singular.
        val ridge = 1e-3 * (sxx + syy) + 1e-6
        val g = solve2x2(sxx + ridge, sxy, sxy, syy + ridge, sxr, syr) ?: return null
        if (g.length < 1e-6) return null
        return bearingOf(g) // uphill = toward stronger signal = toward target
    }

    // ── Range multilateration ────────────────────────────────────────────────

    private fun multilaterate(anchors: List<TrackPoint>, tuning: SpatialTuning): Vec2? {
        val ref = anchors.maxByOrNull { it.rssi } ?: return null
        val rRef = tuning.rangeOf(ref.rssi)
        val kRef = ref.pos.dot(ref.pos) - rRef * rRef

        var a11 = 0.0; var a12 = 0.0; var a22 = 0.0; var b1 = 0.0; var b2 = 0.0
        var used = 0
        for (p in anchors) {
            if (p === ref) continue
            val ri = tuning.rangeOf(p.rssi)
            val a = (p.pos - ref.pos) * 2.0                 // row of the linear system
            val c = (p.pos.dot(p.pos) - ri * ri) - kRef
            val w = (p.strength01 + 0.05).toDouble()
            a11 += w * a.x * a.x; a12 += w * a.x * a.y; a22 += w * a.y * a.y
            b1 += w * a.x * c; b2 += w * a.y * c
            used++
        }
        if (used < 2) return null
        return solve2x2(a11, a12, a12, a22, b1, b2)
    }

    private fun rangeResidualRms(anchors: List<TrackPoint>, target: Vec2, tuning: SpatialTuning): Double {
        var sum = 0.0
        for (p in anchors) {
            val predicted = (p.pos - target).length
            val measured = tuning.rangeOf(p.rssi)
            val d = predicted - measured
            sum += d * d
        }
        return sqrt(sum / anchors.size)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Keep the strongest sample per grid cell so dense clusters don't dominate. */
    private fun declusterStrongest(points: List<TrackPoint>, tuning: SpatialTuning): List<TrackPoint> {
        val cell = tuning.anchorCellM
        val best = HashMap<Long, TrackPoint>()
        for (p in points) {
            val gx = kotlin.math.floor(p.pos.x / cell).toInt()
            val gy = kotlin.math.floor(p.pos.y / cell).toInt()
            val key = (gx.toLong() shl 32) xor (gy.toLong() and 0xffffffffL)
            val cur = best[key]
            if (cur == null || p.rssi > cur.rssi) best[key] = p
        }
        // Cap to the strongest anchors to bound the solve cost.
        return best.values.sortedByDescending { it.rssi }.take(48)
    }

    private fun maxPairwiseDistance(pts: List<TrackPoint>): Double {
        // Bounding-box diagonal is an O(n) proxy for spread.
        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        for (p in pts) {
            if (p.pos.x < minX) minX = p.pos.x; if (p.pos.x > maxX) maxX = p.pos.x
            if (p.pos.y < minY) minY = p.pos.y; if (p.pos.y > maxY) maxY = p.pos.y
        }
        return Vec2(maxX - minX, maxY - minY).length
    }

    private fun solve2x2(a11: Double, a12: Double, a21: Double, a22: Double, b1: Double, b2: Double): Vec2? {
        val det = a11 * a22 - a12 * a21
        if (abs(det) < DET_EPS) return null
        return Vec2((b1 * a22 - a12 * b2) / det, (a11 * b2 - b1 * a21) / det)
    }
}
