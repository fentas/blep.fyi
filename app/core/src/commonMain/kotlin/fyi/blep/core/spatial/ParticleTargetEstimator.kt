package fyi.blep.core.spatial

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Recursive Bayesian estimate of the target position via a **3-D** particle
 * filter (horizontal x/y + altitude z).
 *
 * Each particle is a candidate target location. The cloud is seeded on a ring at
 * the path-loss range of the first sample (and spread over ±a few metres in
 * altitude), then every RSSI sample reweights each particle by how well the
 * path-loss model at its 3-D distance matches the reading; low effective-sample-
 * size triggers systematic resampling with jitter.
 *
 * Altitude is only weakly observable until you actually change floors: with all
 * samples taken at one height the up/down sign stays ambiguous (so the vertical
 * spread stays wide and [Estimate.semiVerticalM] large); climbing or descending
 * breaks the symmetry and collapses z onto the target's true floor.
 *
 * Accumulates evidence over time, so it's far steadier under multipath than a
 * per-frame fit, and the weighted covariance yields a real uncertainty ellipse.
 * Deterministic given [rng]'s seed.
 */
class ParticleTargetEstimator(
    private val tuning: SpatialTuning = SpatialTuning(),
    private val model: PathLossModel = PathLossModel.from(tuning),
    private val count: Int = 600,
    private val rng: Random = Random(1),
    private val measurementSigmaDb: Double = 3.5,
    private val jitterM: Double = 0.5,
    private val seedVerticalM: Double = 5.0,
    /** Assumed target wander (m/s). >0 lets the filter follow a *moving* target;
     *  the cloud diffuses by this each second so old evidence doesn't pin a stale
     *  spot. Small enough that a static target still localises tightly. */
    private val targetDriftMps: Double = 0.35,
) {
    private val px = DoubleArray(count)
    private val py = DoubleArray(count)
    private val pz = DoubleArray(count)
    private val w = DoubleArray(count)
    private var seeded = false
    private var lastTimeMs = -1L

    /** One particle-filter estimate of the target. */
    data class Estimate(
        val mean: Vec2,
        val meanZ: Double,
        val semiMajorM: Double,
        val semiMinorM: Double,
        val ellipseRad: Double,
        val semiVerticalM: Double,
        val confidence: Float,
    )

    fun reset() { seeded = false; lastTimeMs = -1L }

    /** Folds one RSSI [rssi] measured at [samplePos] / [sampleAltitude], at [timeMs], into the posterior. */
    fun update(samplePos: Vec2, sampleAltitude: Double, rssi: Double, timeMs: Long): Estimate {
        if (!seeded) {
            seedRing(samplePos, sampleAltitude, rssi)
        } else {
            val dt = if (lastTimeMs < 0) 0.0 else (timeMs - lastTimeMs).coerceAtLeast(0) / 1000.0
            predict(dt.coerceAtMost(2.0)) // target may have wandered since the last sample
            reweight(samplePos, sampleAltitude, rssi)
        }
        lastTimeMs = timeMs
        return estimate()
    }

    /** Process step: diffuse the cloud to allow following a moving target. */
    private fun predict(dt: Double) {
        val std = targetDriftMps * dt
        if (std < 1e-6) return
        for (i in 0 until count) {
            px[i] += gauss() * std
            py[i] += gauss() * std
            pz[i] += gauss() * std * 0.5 // targets change floor far less often than they move
        }
    }

    private fun gauss(): Double {
        val u1 = rng.nextDouble().coerceAtLeast(1e-12)
        val u2 = rng.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }

    private fun seedRing(p: Vec2, altitude: Double, rssi: Double) {
        val r0 = model.rangeOf(rssi, tuning.maxRangeM)
        for (i in 0 until count) {
            val a = rng.nextDouble(0.0, 2 * PI)
            val r = (r0 * (0.3 + 1.4 * rng.nextDouble())).coerceIn(0.5, tuning.maxRangeM)
            px[i] = p.x + sin(a) * r
            py[i] = p.y + cos(a) * r
            pz[i] = altitude + (rng.nextDouble() * 2 - 1) * seedVerticalM
            w[i] = 1.0 / count
        }
        seeded = true
    }

    private fun reweight(p: Vec2, altitude: Double, rssi: Double) {
        val twoSigSq = 2.0 * measurementSigmaDb * measurementSigmaDb
        var sum = 0.0
        for (i in 0 until count) {
            val dx = px[i] - p.x; val dy = py[i] - p.y; val dz = pz[i] - altitude
            val d = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.5)
            val e = rssi - model.expectedRssi(d)
            w[i] *= exp(-(e * e) / twoSigSq)
            sum += w[i]
        }
        if (sum <= 1e-300) { seedRing(p, altitude, rssi); return } // degenerate — reseed
        for (i in 0 until count) w[i] /= sum

        var sq = 0.0
        for (i in 0 until count) sq += w[i] * w[i]
        if (1.0 / sq < count / 2.0) resampleWithJitter()
    }

    private fun resampleWithJitter() {
        val nx = DoubleArray(count); val ny = DoubleArray(count); val nz = DoubleArray(count)
        val step = 1.0 / count
        val u = rng.nextDouble() * step
        var c = w[0]; var i = 0
        for (j in 0 until count) {
            val target = u + j * step
            while (target > c && i < count - 1) { i++; c += w[i] }
            nx[j] = px[i] + (rng.nextDouble() * 2 - 1) * jitterM
            ny[j] = py[i] + (rng.nextDouble() * 2 - 1) * jitterM
            nz[j] = pz[i] + (rng.nextDouble() * 2 - 1) * jitterM
        }
        for (j in 0 until count) { px[j] = nx[j]; py[j] = ny[j]; pz[j] = nz[j]; w[j] = 1.0 / count }
    }

    private fun estimate(): Estimate {
        var mx = 0.0; var my = 0.0; var mz = 0.0
        for (i in 0 until count) { mx += w[i] * px[i]; my += w[i] * py[i]; mz += w[i] * pz[i] }
        var sxx = 0.0; var sxy = 0.0; var syy = 0.0; var szz = 0.0
        for (i in 0 until count) {
            val dx = px[i] - mx; val dy = py[i] - my; val dz = pz[i] - mz
            sxx += w[i] * dx * dx; sxy += w[i] * dx * dy; syy += w[i] * dy * dy; szz += w[i] * dz * dz
        }
        val tr = sxx + syy
        val disc = sqrt(max(0.0, (tr / 2) * (tr / 2) - (sxx * syy - sxy * sxy)))
        val semiMajor = sqrt(max(0.0, tr / 2 + disc))
        val semiMinor = sqrt(max(0.0, tr / 2 - disc))
        val angle = 0.5 * atan2(2 * sxy, sxx - syy)
        // Confidence falls as the cloud's larger horizontal spread grows.
        val confidence = (1.0 / (1.0 + semiMajor / 3.0)).coerceIn(0.0, 1.0).toFloat()
        return Estimate(Vec2(mx, my), mz, semiMajor, semiMinor, angle, sqrt(max(0.0, szz)), confidence)
    }
}
