package fyi.blep.core.spatial

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
) {
    companion object {
        val NONE = TargetEstimate(null, null, null, 0f)
    }
}

/** Everything the spatial map UI needs for one frame. */
data class SpatialSnapshot(
    val here: Vec2,
    val headingRad: Double,
    val velocity: Vec2,
    val path: List<TrackPoint>,
    val target: TargetEstimate,
    /** −1f (walking away) … +1f (walking toward), velocity·bearing. 0 if unknown. */
    val onCourse: Float,
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
    private val points = ArrayList<TrackPoint>()
    private var frame: LocalFrame? = null

    val path: List<TrackPoint> get() = points

    fun reset() {
        reckoner.reset()
        points.clear()
        frame = null
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
                moving = moving,
                reorienting = reorienting,
            ),
        )
    }

    /** Feeds one [rssi] sample with its [motion] context; returns the new snapshot. */
    fun update(rssi: Double, motion: MotionSample): SpatialSnapshot {
        val here = reckoner.update(motion)
        recordSample(here, rssi, motion.timeMs)

        val target = TargetEstimator.estimate(points, here, tuning)
        val onCourse = target.bearingRad?.let { b ->
            val v = reckoner.velocity.normalizedOrZero()
            if (v.length < 1e-6) 0f else v.dot(Vec2.heading(b)).toFloat().coerceIn(-1f, 1f)
        } ?: 0f

        return SpatialSnapshot(
            here = here,
            headingRad = reckoner.headingRad,
            velocity = reckoner.velocity,
            path = points,
            target = target,
            onCourse = onCourse,
        )
    }

    private fun recordSample(here: Vec2, rssi: Double, timeMs: Long) {
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
}
