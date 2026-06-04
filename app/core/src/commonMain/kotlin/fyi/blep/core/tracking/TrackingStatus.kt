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
 * What the arrow should do — expressed as a *shape*, not a rotation.
 *
 * @property curl signed bend of the arrow body in [-1f, 1f]:
 *   `0` = straight (go forward), small magnitude = a gentle bend left/right,
 *   ~`±0.55` = a curl that reads as "turn / rotate on the spot" (sign picks the
 *   direction), larger = a tighter U-turn / "come down here". The UI morphs the
 *   arrow between these poses rather than rotating a rigid arrow.
 * @property scale grows as the target gets closer; collapses to 0 on completion.
 */
data class ArrowDirective(
    val curl: Float,
    val scale: Float,
)

/**
 * Stable key for each distinct guidance message — lets the UI localize the copy.
 * The English [Guidance.title]/[Guidance.detail] remain the default (and are what
 * the Wear + watchOS apps render); the phone maps [cue] to its own translations.
 */
enum class GuidanceCue {
    NONE,
    CALIBRATE,
    SWEEP_START, SWEEP_WARMER, SWEEP_COLDER, SWEEP_FLAT,
    WALK_WARMER, WALK_COLDER, WALK_FLAT, WALK_OVERSHOOT, WALK_FOUND,
    REORIENT, PINPOINT, PINPOINT_LOST, COMPLETE,
}

/** Human-facing guidance copy for a phase (English) + a [cue] key for localization. */
data class Guidance(
    val title: String,
    val detail: String,
    val tone: Tone,
    val cue: GuidanceCue = GuidanceCue.NONE,
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
