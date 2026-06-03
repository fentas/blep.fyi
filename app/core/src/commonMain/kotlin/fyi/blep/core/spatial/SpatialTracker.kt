package fyi.blep.core.spatial

import kotlin.math.abs
import kotlin.math.roundToInt

/** One point of the walked path with the signal sampled there. */
data class TrackPoint(
    val pos: Vec2,
    val rssi: Double,
    val timeMs: Long,
    /** RSSI mapped to [0f,1f] for colouring the trail. */
    val strength01: Float,
)

/** A prediction of where the target is. */
data class TargetEstimate(
    /** Best-guess target position in the local frame, or null if unknown. */
    val position: Vec2?,
    /** Compass bearing (rad, clockwise from north) from "here" to the target. */
    val bearingRad: Double?,
    /** Straight-line distance estimate (m), or null. */
    val distanceM: Double?,
    /** 0f (a guess) … 1f (confident). */
    val confidence: Float,
    /** Uncertainty-ellipse semi-axes (m) + orientation (rad), from the filter
     *  covariance. Null until a position is being reported. */
    val semiMajorM: Double? = null,
    val semiMinorM: Double? = null,
    val ellipseRad: Double? = null,
) {
    companion object {
        val NONE = TargetEstimate(null, null, null, 0f)
    }
}

/** Everything the spatial map UI needs for one frame. */
data class SpatialSnapshot(
    val here: Vec2,
    val headingRad: Double,
    /** Whether [headingRad] is a real compass heading (else it's a default 0). */
    val headingKnown: Boolean,
    val velocity: Vec2,
    val path: List<TrackPoint>,
    val target: TargetEstimate,
    /** −1f (walking away) … +1f (walking toward), velocity·bearing. 0 if unknown. */
    val onCourse: Float,
    /** Floors to the target relative to here: +above, −below, 0 if same/unknown. */
    val floorDelta: Int = 0,
    /** Compass bearing the signal is strongest in (from turning on the spot),
     *  before the target is triangulated — null/0 until you've turned enough. */
    val signalBearingRad: Double? = null,
    val signalBearingConfidence: Float = 0f,
    /** Bearing back to the strongest spot you've stood in, and how many dB the
     *  signal has dropped since — lets the UI say "warmer back that way" when you
     *  wander off, so you recover instead of orbiting. Null until you've left it. */
    val warmestBearingRad: Double? = null,
    val belowWarmestDb: Double = 0.0,
    /** Sticky recovery state (hysteresis): true once the signal dropped well below
     *  the warmest spot, staying true until you've climbed most of the way back —
     *  so guidance commits to "head back" instead of flip-flopping at the edge. */
    val recovering: Boolean = false,
    /** How much the raw RSSI jitters around its smooth track (dB, mean-abs-dev) —
     *  low in clean line-of-sight, high under canopy / multipath / heavy noise.
     *  Lets guidance pick a behaviour suited to the current environment. */
    val signalVolatilityDb: Double = 0.0,
)

/**
 * Builds the spatial picture from fused motion + RSSI: integrates pose with a
 * [DeadReckoner], records the signal field along the path, and re-estimates the
 * target each sample. Pure and deterministic — unit-testable on the JVM.
 *
 * It runs **alongside** the RSSI-only [fyi.blep.core.tracking.TrackingSession];
 * if motion samples carry no usable sensor data the path collapses to a point
 * and the gradient/multilateration simply stay low-confidence, so nothing breaks.
 */
class SpatialTracker(private val tuning: SpatialTuning = SpatialTuning()) {
    private val reckoner = DeadReckoner()
    private val pathLoss = PathLossModel.from(tuning)
    private val particles = ParticleTargetEstimator(
        tuning, pathLoss,
        count = tuning.particleCount,
        measurementSigmaDb = tuning.measurementSigmaDb,
        jitterM = tuning.particleJitterM,
        targetDriftMps = tuning.targetDriftMps,
    )
    private val angular = AngularSignalField(
        binCount = tuning.angularBins,
        binEma = tuning.angularBinEma,
        coverageFraction = tuning.angularCoverageFraction,
        peakednessDb = tuning.angularPeakednessDb,
        staleHalfLifeM = tuning.angularStaleHalfLifeM,
    )
    private val grid = SignalGrid(cellM = tuning.gridCellM, zCellM = tuning.floorHeightM)
    private var signalEma = Double.NaN
    private var signalVolatility = Double.NaN // steady-heading |Δrssi| EMA = environmental noise
    private var lastVolHeading: Double? = null
    private var lastVolRssi = Double.NaN
    private val points = ArrayList<TrackPoint>()
    // Path bounding box, maintained incrementally so pathSpread() is O(1).
    private var pathMinX = Double.MAX_VALUE; private var pathMaxX = -Double.MAX_VALUE
    private var pathMinY = Double.MAX_VALUE; private var pathMaxY = -Double.MAX_VALUE
    private var frame: LocalFrame? = null
    private var samplesSinceCalibration = 0
    // Position + altitude of the last sample folded into the filter, so we only
    // re-triangulate after real movement (not on noise while standing still).
    private var lastFoldPos: Vec2? = null
    private var lastFoldAlt = 0.0
    private var lastAngularPos: Vec2? = null
    private var recovering = false

    val path: List<TrackPoint> get() = points

    fun reset() {
        reckoner.reset()
        particles.reset()
        angular.reset()
        grid.reset()
        signalEma = Double.NaN
        signalVolatility = Double.NaN
        lastVolHeading = null
        lastVolRssi = Double.NaN
        pathLoss.rssiAt1m = tuning.rssiAt1m
        pathLoss.exponent = tuning.pathLossExponent
        points.clear()
        pathMinX = Double.MAX_VALUE; pathMaxX = -Double.MAX_VALUE
        pathMinY = Double.MAX_VALUE; pathMaxY = -Double.MAX_VALUE
        frame = null
        samplesSinceCalibration = 0
        lastFoldPos = null
        lastFoldAlt = 0.0
        lastAngularPos = null
        recovering = false
    }

    /**
     * Swift/ObjC-friendly entry point that takes only non-null primitives (no
     * Kotlin nullable `Double?`/`Vec2?` to bridge) and manages the local frame
     * internally from raw GPS lat/lon. Booleans gate the optional fields.
     */
    fun updateGeo(
        rssi: Double,
        timeMs: Long,
        headingRad: Double,
        hasHeading: Boolean,
        lat: Double,
        lon: Double,
        hasFix: Boolean,
        positionAccuracyM: Double,
        speedMps: Double,
        moving: Boolean,
        reorienting: Boolean,
        relativeAltitudeM: Double = 0.0,
    ): SpatialSnapshot {
        val pos = if (hasFix) {
            val f = frame ?: LocalFrame(lat, lon).also { frame = it }
            f.toLocal(GeoPoint(lat, lon))
        } else {
            null
        }
        return update(
            rssi,
            MotionSample(
                timeMs = timeMs,
                position = pos,
                positionAccuracyM = if (hasFix) positionAccuracyM else Double.NaN,
                headingRad = if (hasHeading) headingRad else null,
                speedMps = speedMps,
                relativeAltitudeM = relativeAltitudeM,
                moving = moving,
                reorienting = reorienting,
            ),
        )
    }

    /** Feeds one [rssi] sample with its [motion] context; returns the new snapshot. */
    fun update(rssi: Double, motion: MotionSample): SpatialSnapshot {
        val here = reckoner.update(motion)
        recordSample(here, rssi, motion.timeMs)
        val altitude = motion.relativeAltitudeM
        // Compass directionality (works while turning in place, before triangulation).
        // Feed it travel so the field can age stale bins once you walk past the
        // target (the bearing must be re-earned, not coasted on).
        if (motion.headingRad != null) {
            val movedM = lastAngularPos?.let { (here - it).length } ?: 0.0
            lastAngularPos = here
            angular.update(motion.headingRad, rssi, movedM)
        }
        // Spatial memory of where the signal was strong (for recovery).
        signalEma = if (signalEma.isNaN()) rssi else signalEma * 0.6 + rssi * 0.4
        // Classify the environment by how much the signal jitters *that turning
        // can't explain*: measure |Δrssi| only between consecutive samples taken at
        // a near-steady heading (so the ±10 dB body-shield swing during a sweep
        // doesn't masquerade as environmental noise). Low = clean line-of-sight,
        // high = canopy / multipath / heavy noise.
        val heading = motion.headingRad
        if (heading != null && lastVolHeading != null && !lastVolRssi.isNaN()) {
            val turned = abs(angleDelta(heading, lastVolHeading!!))
            if (turned < VOL_STEADY_RAD) {
                val jitter = abs(rssi - lastVolRssi)
                signalVolatility = if (signalVolatility.isNaN()) jitter else signalVolatility * 0.9 + jitter * 0.1
            }
        }
        if (heading != null) { lastVolHeading = heading; lastVolRssi = rssi }
        grid.update(here, altitude, signalEma)

        // Only fold a sample into the filter once we've actually moved (3-D) since
        // the last one — new geometry. Standing still adds only noise, which would
        // make the estimate wander; there we reuse the last estimate unchanged.
        val moved = lastFoldPos?.let {
            val dxy = (here - it).length; val dz = altitude - lastFoldAlt
            kotlin.math.sqrt(dxy * dxy + dz * dz)
        } ?: Double.MAX_VALUE
        val est = if (moved >= tuning.minTriangulationStepM) {
            lastFoldPos = here; lastFoldAlt = altitude
            particles.update(here, altitude, rssi, motion.timeMs)
        } else {
            particles.peek() ?: run {
                lastFoldPos = here; lastFoldAlt = altitude
                particles.update(here, altitude, rssi, motion.timeMs)
            }
        }
        val spread = pathSpread()
        val target = when {
            spread < tuning.minSpreadM -> TargetEstimate(null, null, null, 0f)
            est.confidence >= tuning.reportConfidence -> {
                maybeCalibrate(est)
                val toTarget = est.mean - here
                TargetEstimate(
                    position = est.mean,
                    bearingRad = bearingOf(toTarget),
                    distanceM = toTarget.length,
                    confidence = est.confidence,
                    semiMajorM = est.semiMajorM,
                    semiMinorM = est.semiMinorM,
                    ellipseRad = est.ellipseRad,
                )
            }
            // Moved a little but not localised yet: offer just a gradient bearing.
            else -> TargetEstimate(null, TargetEstimator.gradientBearing(points), null, est.confidence.coerceAtMost(0.25f))
        }

        val onCourse = target.bearingRad?.let { b ->
            val v = reckoner.velocity.normalizedOrZero()
            if (v.length < 1e-6) 0f else v.dot(Vec2.heading(b)).toFloat().coerceIn(-1f, 1f)
        } ?: 0f

        // Hysteresis: engage recovery once you've dropped well below the warmest
        // spot, and hold it until you've climbed most of the way back — otherwise
        // guidance flip-flops (recover ↔ chase) every time you cross the threshold.
        // (One grid scan per tick — both belowWarmest and the bearing reuse it.)
        val warmest = grid.strongest()
        val belowWarmest = warmest?.let { (it.rssi - signalEma).coerceAtLeast(0.0) } ?: 0.0
        recovering = when {
            belowWarmest >= tuning.recoverDb -> true
            belowWarmest <= tuning.recoverDb * RECOVER_EXIT_FRACTION -> false
            else -> recovering
        }

        return SpatialSnapshot(
            here = here,
            headingRad = reckoner.headingRad,
            headingKnown = reckoner.hasHeading,
            velocity = reckoner.velocity,
            path = points,
            target = target,
            onCourse = onCourse,
            floorDelta = floorDelta(est, altitude),
            signalBearingRad = angular.bearingRad,
            signalBearingConfidence = angular.confidence,
            warmestBearingRad = warmestBearing(here, warmest),
            belowWarmestDb = belowWarmest,
            recovering = recovering,
            signalVolatilityDb = if (signalVolatility.isNaN()) 0.0 else signalVolatility,
        )
    }

    /** Bearing back to the [warmest] cell, once you've moved off it. */
    private fun warmestBearing(here: Vec2, warmest: SignalGrid.Cell?): Double? {
        val best = warmest ?: return null
        val toBest = Vec2(best.x, best.y) - here
        return if (toBest.length > tuning.warmestMinOffsetM) bearingOf(toBest) else null
    }

    /**
     * Floors to the target relative to here, from the filter's vertical estimate —
     * but only once altitude is well resolved (which needs you to have actually
     * changed floors; otherwise up/down is ambiguous and this stays 0).
     */
    private fun floorDelta(est: ParticleTargetEstimator.Estimate, currentAltitude: Double): Int {
        val dz = est.meanZ - currentAltitude
        return if (est.semiVerticalM < tuning.floorHeightM && abs(dz) >= tuning.floorHeightM * 0.5) {
            (dz / tuning.floorHeightM).roundToInt()
        } else {
            0
        }
    }

    /**
     * Periodically refit the path-loss model from the localised target, easing the
     * new fit in so the filter adapts to the environment without jumping.
     */
    private fun maybeCalibrate(est: ParticleTargetEstimator.Estimate) {
        if (est.confidence < tuning.calibrateConfidence) return
        if (++samplesSinceCalibration < tuning.calibrateEvery) return
        samplesSinceCalibration = 0
        val fit = PathLossCalibrator.calibrate(points, est.mean) ?: return
        pathLoss.rssiAt1m += (fit.first - pathLoss.rssiAt1m) * tuning.calibrateEase
        pathLoss.exponent += (fit.second - pathLoss.exponent) * tuning.calibrateEase
    }

    /** Bounding-box diagonal of the path — a movement proxy. The box is maintained
     *  incrementally in [recordSample] (it only grows), so this is O(1); only used
     *  to gate "have we moved enough to estimate?", which is monotonic anyway. */
    private fun pathSpread(): Double =
        if (points.size < 2) 0.0 else Vec2(pathMaxX - pathMinX, pathMaxY - pathMinY).length

    private fun recordSample(here: Vec2, rssi: Double, timeMs: Long) {
        if (here.x < pathMinX) pathMinX = here.x; if (here.x > pathMaxX) pathMaxX = here.x
        if (here.y < pathMinY) pathMinY = here.y; if (here.y > pathMaxY) pathMaxY = here.y
        val last = points.lastOrNull()
        val strength = tuning.strength01(rssi)
        if (last != null && (here - last.pos).length < tuning.minSampleSpacingM) {
            // Stayed put: keep the strongest reading at this spot rather than piling up.
            if (rssi > last.rssi) points[points.lastIndex] = TrackPoint(here, rssi, timeMs, strength)
            return
        }
        points.add(TrackPoint(here, rssi, timeMs, strength))
        if (points.size > tuning.maxPathPoints) decimate()
    }

    /** Drops every other point once the cap is hit, halving density but keeping shape. */
    private fun decimate() {
        var w = 0
        for (r in points.indices) if (r % 2 == 0) { points[w] = points[r]; w++ }
        while (points.size > w) points.removeAt(points.lastIndex)
    }

    private companion object {
        // Recovery releases once the signal is back within this fraction of recoverDb
        // of the warmest spot — the lower hysteresis band that stops flip-flopping.
        const val RECOVER_EXIT_FRACTION = 0.3
        // Only sample environmental jitter between headings this close (rad ≈ 5°),
        // so the body-shield swing during a sweep isn't counted as noise.
        const val VOL_STEADY_RAD = 0.09
    }
}
