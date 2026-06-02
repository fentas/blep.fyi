package fyi.blep.core.spatial

/**
 * Maps proximity to a pulse interval for "Geiger counter" haptic/audio feedback:
 * the closer you are, the faster the ticks. Returns null when there's nothing
 * useful to signal (no/!weak signal), so the device stays silent rather than
 * buzzing aimlessly.
 */
object HapticCadence {
    const val FAR_MS = 1200L   // slow ticks when barely warm
    const val NEAR_MS = 110L   // rapid ticks right on top of it
    const val SILENT_BELOW = 0.05f

    fun intervalMs(proximity: Float): Long? {
        if (proximity < SILENT_BELOW) return null
        val p = proximity.coerceIn(0f, 1f)
        return (FAR_MS - (FAR_MS - NEAR_MS) * p).toLong().coerceIn(NEAR_MS, FAR_MS)
    }
}
