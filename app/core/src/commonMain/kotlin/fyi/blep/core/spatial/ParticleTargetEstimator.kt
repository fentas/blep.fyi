package fyi.blep.core.spatial

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Recursive Bayesian estimate of the target position via a particle filter.
 *
 * Each particle is a candidate target location. The cloud is seeded on a ring at
 * the path-loss range of the first sample, then every RSSI sample reweights each
 * particle by how well the path-loss model at its distance matches the reading;
 * low effective-sample-size triggers systematic resampling with a little jitter
 * (process noise) so the filter stays diverse and can drift with a moving target.
 *
 * Unlike a per-frame least-squares fit this **accumulates evidence over time**,
 * so it's far steadier under multipath, and the weighted covariance of the cloud
 * yields a genuine uncertainty ellipse. Deterministic given [rng]'s seed.
 */
class ParticleTargetEstimator(
    private val tuning: SpatialTuning = SpatialTuning(),
    private val model: PathLossModel = PathLossModel.from(tuning),
    private val count: Int = 600,
    private val rng: Random = Random(1),
    private val measurementSigmaDb: Double = 3.5,
    private val jitterM: Double = 0.5,
) {
    private val px = DoubleArray(count)
    private val py = DoubleArray(count)
    private val w = DoubleArray(count)
    private var seeded = false

    /** One particle-filter estimate of the target. */
    data class Estimate(
        val mean: Vec2,
        val semiMajorM: Double,
        val semiMinorM: Double,
        val ellipseRad: Double,
        val confidence: Float,
    )

    fun reset() { seeded = false }

    /** Folds one RSSI [rssi] measured at [samplePos] into the posterior. */
    fun update(samplePos: Vec2, rssi: Double): Estimate {
        if (!seeded) seedRing(samplePos, rssi) else reweight(samplePos, rssi)
        return estimate()
    }

    private fun seedRing(p: Vec2, rssi: Double) {
        val r0 = model.rangeOf(rssi, tuning.maxRangeM)
        for (i in 0 until count) {
            val a = rng.nextDouble(0.0, 2 * PI)
            val r = (r0 * (0.3 + 1.4 * rng.nextDouble())).coerceIn(0.5, tuning.maxRangeM)
            px[i] = p.x + sin(a) * r
            py[i] = p.y + cos(a) * r
            w[i] = 1.0 / count
        }
        seeded = true
    }

    private fun reweight(p: Vec2, rssi: Double) {
        val twoSigSq = 2.0 * measurementSigmaDb * measurementSigmaDb
        var sum = 0.0
        for (i in 0 until count) {
            val d = hypot(px[i] - p.x, py[i] - p.y).coerceAtLeast(0.5)
            val e = rssi - model.expectedRssi(d)
            w[i] *= exp(-(e * e) / twoSigSq)
            sum += w[i]
        }
        if (sum <= 1e-300) { seedRing(p, rssi); return } // degenerate — reseed
        for (i in 0 until count) w[i] /= sum

        var sq = 0.0
        for (i in 0 until count) sq += w[i] * w[i]
        if (1.0 / sq < count / 2.0) resampleWithJitter()
    }

    private fun resampleWithJitter() {
        val nx = DoubleArray(count); val ny = DoubleArray(count)
        val step = 1.0 / count
        var u = rng.nextDouble() * step
        var c = w[0]; var i = 0
        for (j in 0 until count) {
            val target = u + j * step
            while (target > c && i < count - 1) { i++; c += w[i] }
            nx[j] = px[i] + (rng.nextDouble() * 2 - 1) * jitterM
            ny[j] = py[i] + (rng.nextDouble() * 2 - 1) * jitterM
        }
        for (j in 0 until count) { px[j] = nx[j]; py[j] = ny[j]; w[j] = 1.0 / count }
    }

    private fun estimate(): Estimate {
        var mx = 0.0; var my = 0.0
        for (i in 0 until count) { mx += w[i] * px[i]; my += w[i] * py[i] }
        var sxx = 0.0; var sxy = 0.0; var syy = 0.0
        for (i in 0 until count) {
            val dx = px[i] - mx; val dy = py[i] - my
            sxx += w[i] * dx * dx; sxy += w[i] * dx * dy; syy += w[i] * dy * dy
        }
        val tr = sxx + syy
        val disc = sqrt(max(0.0, (tr / 2) * (tr / 2) - (sxx * syy - sxy * sxy)))
        val semiMajor = sqrt(max(0.0, tr / 2 + disc))
        val semiMinor = sqrt(max(0.0, tr / 2 - disc))
        val angle = 0.5 * atan2(2 * sxy, sxx - syy)
        // Confidence falls as the cloud's larger spread grows (≈3 m → ~0.5).
        val confidence = (1.0 / (1.0 + semiMajor / 3.0)).coerceIn(0.0, 1.0).toFloat()
        return Estimate(Vec2(mx, my), semiMajor, semiMinor, angle, confidence)
    }
}
