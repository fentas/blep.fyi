package fyi.blep.core.spatial

/**
 * Cheap, stateless RSSI-gradient bearing — a weighted plane fit of signal over
 * position whose uphill direction points toward the target. Used for an early
 * "which way?" hint before the recursive [ParticleTargetEstimator] has gathered
 * enough geometry to localise a position.
 */
object TargetEstimator {

    /** Weighted plane fit of RSSI over position; returns the uphill bearing, or null. */
    fun gradientBearing(points: List<TrackPoint>): Double? {
        if (points.size < 3) return null
        var wSum = 0.0; var cx = 0.0; var cy = 0.0; var rMean = 0.0
        for (p in points) {
            val w = p.strength01 + 0.05
            wSum += w; cx += w * p.pos.x; cy += w * p.pos.y; rMean += w * p.rssi
        }
        if (wSum < 1e-9) return null
        cx /= wSum; cy /= wSum; rMean /= wSum
        var sxx = 0.0; var sxy = 0.0; var syy = 0.0; var sxr = 0.0; var syr = 0.0
        for (p in points) {
            val w = p.strength01 + 0.05
            val dx = p.pos.x - cx; val dy = p.pos.y - cy; val dr = p.rssi - rMean
            sxx += w * dx * dx; sxy += w * dx * dy; syy += w * dy * dy
            sxr += w * dx * dr; syr += w * dy * dr
        }
        // Ridge so a near-collinear path still resolves to the dominant direction.
        val ridge = 1e-3 * (sxx + syy) + 1e-6
        val g = solve2x2(sxx + ridge, sxy, sxy, syy + ridge, sxr, syr) ?: return null
        if (g.length < 1e-6) return null
        return bearingOf(g) // uphill = toward stronger signal = toward target
    }

    private fun solve2x2(a11: Double, a12: Double, a21: Double, a22: Double, b1: Double, b2: Double): Vec2? {
        val det = a11 * a22 - a12 * a21
        if (kotlin.math.abs(det) < 1e-9) return null
        return Vec2((b1 * a22 - a12 * b2) / det, (a11 * b2 - b1 * a21) / det)
    }
}
