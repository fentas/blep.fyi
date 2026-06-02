package fyi.blep.core.spatial

/**
 * Platform haptic (and light audio) feedback for tracking. A single short pulse
 * whose strength tracks proximity, plus a distinct success cue. No-op on
 * platforms without a vibrator so callers never need to branch.
 */
interface Haptic {
    /** One short tick; [intensity] in [0f,1f] scales strength. */
    fun pulse(intensity: Float)

    /** A distinct "found it" cue. */
    fun success()

    /** Release any audio/vibrator resources. */
    fun release()
}

/** Creates the platform [Haptic]. */
expect fun createHaptic(): Haptic

/** Shared no-op used by platforms without haptics. */
object NoHaptic : Haptic {
    override fun pulse(intensity: Float) {}
    override fun success() {}
    override fun release() {}
}
