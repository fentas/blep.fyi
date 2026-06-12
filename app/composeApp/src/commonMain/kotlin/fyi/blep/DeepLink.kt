package fyi.blep

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hand-off from platform entry points (a tapped notification) into the shared controller.
 * The platform side sets a value; the controller collects it, acts, and clears it. A flow
 * (not a one-shot) so it works whether the app is cold-starting or already running.
 */
object DeepLink {
    /** Device id from a tapped tracker-alert notification — open its panel + mark suspect. */
    val suspectId = MutableStateFlow<String?>(null)
}
