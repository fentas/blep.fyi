package fyi.blep.core.tracking

/** The guided-tracking phases, mirroring the on-screen flow. */
enum class TrackingPhase {
    /** Logging the baseline signal with the phone held at the chest. */
    CALIBRATION,

    /** Turning in place to find the bearing of strongest signal. */
    AXIS_SWEEP,

    /** Walking forward while the signal keeps improving. */
    VECTOR_WALK,

    /** Re-sweeping after a walk leg to correct the bearing. */
    REORIENT,

    /** Close range: searching low / at floor level. */
    PINPOINT,

    /** Target reached. */
    COMPLETE,
}

/** Emotional colour of the current guidance, used to tint UI + copy. */
enum class Tone { NEUTRAL, WARMER, COLDER, STOP, DONE }

/**
 * What the arrow should do. Rotation is *symbolic intent* from the signal model
 * (0° = "go / forward", 180° = "turn around", ±90° = "keep turning"); the UI
 * layer may fuse it with the device heading sensors for a world-space arrow.
 * Scale grows as the target gets closer and collapses on completion.
 */
data class ArrowDirective(
    val rotationDeg: Float,
    val scale: Float,
)

/** Human-facing guidance copy for a phase. */
data class Guidance(
    val title: String,
    val detail: String,
    val tone: Tone,
)

/**
 * Full immutable snapshot the UI renders. Produced by [TrackingSession] for
 * every signal sample.
 *
 * @property proximity 0f (far) … 1f (pinpoint), derived from smoothed RSSI.
 */
data class TrackingStatus(
    val phase: TrackingPhase,
    val guidance: Guidance,
    val proximity: Float,
    val arrow: ArrowDirective,
)
