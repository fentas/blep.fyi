package fyi.blep.core.spatial

/**
 * Estimates the user's pose (position, heading, velocity) in the local frame by
 * integrating motion samples, fusing GPS when a usable fix is present.
 *
 * - **Heading** comes straight from the compass when available.
 * - **Position** is dead-reckoned by stepping along the current heading at the
 *   reported ground speed; whenever a GPS fix arrives it is blended in with a
 *   weight set by its accuracy (a complementary filter). This keeps a smooth,
 *   drift-bounded track that works indoors (no GPS) and outdoors (GPS-assisted).
 */
class DeadReckoner {
    var position = Vec2.ZERO
        private set
    var headingRad = 0.0
        private set
    var velocity = Vec2.ZERO
        private set

    private var lastTimeMs = -1L
    private var hasHeading = false

    fun reset() {
        position = Vec2.ZERO
        headingRad = 0.0
        velocity = Vec2.ZERO
        lastTimeMs = -1L
        hasHeading = false
    }

    /** Integrates one [sample]; returns the updated [position]. */
    fun update(sample: MotionSample): Vec2 {
        val dt = if (lastTimeMs < 0) 0.0 else (sample.timeMs - lastTimeMs).coerceAtLeast(0) / 1000.0
        lastTimeMs = sample.timeMs

        sample.headingRad?.let { headingRad = it; hasHeading = true }

        // Dead-reckon along the heading we're facing. Prefer step-counted distance
        // (pedestrian dead reckoning) over speed×dt — it's far truer indoors.
        val forward = when {
            !hasHeading -> 0.0
            sample.stepDistanceM > 0.0 -> sample.stepDistanceM
            sample.moving && dt > 0.0 -> sample.speedMps * dt
            else -> 0.0
        }
        val stepped = position + Vec2.heading(headingRad) * forward

        // Blend a GPS fix in proportion to how much we trust its accuracy.
        var fused = stepped
        val pos = sample.position
        if (pos != null && sample.positionAccuracyM.isFinite() && sample.positionAccuracyM > 0.0) {
            val k = gpsTrust(sample.positionAccuracyM)
            fused = stepped * (1.0 - k) + pos * k
        }

        velocity = if (dt > 1e-3) (fused - position) * (1.0 / dt) else velocity
        position = fused
        return position
    }

    /** Trust weight for a GPS fix: ~0.6 at a 5 m fix, fading to ~0 by 60 m. */
    private fun gpsTrust(accuracyM: Double): Double = (5.0 / accuracyM).coerceIn(0.0, 0.6)
}
