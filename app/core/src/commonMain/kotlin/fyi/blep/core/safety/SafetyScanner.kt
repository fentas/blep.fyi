package fyi.blep.core.safety

import fyi.blep.core.ble.BleScanner
import fyi.blep.core.platform.epochMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Runs the "is something tracking me?" scan: feeds the scanner's raw advertisement
 * stream through the [TrackerClassifier] into the [TrackerDetector], and emits the
 * current set of suspected-tracker [TrackerAlert]s. Cold — collecting starts the
 * scan, cancelling stops it.
 *
 * When the user has opted into [history], alerts are also recorded and then enriched
 * with cross-session context: a tracker seen close across several separate hours is
 * promoted to a full ALERT, because that persistence is what a single live scan can't
 * see.
 */
class SafetyScanner(
    private val scanner: BleScanner,
    private val detector: TrackerDetector = TrackerDetector(),
    private val history: SafetyHistory? = null,
    private val nowEpochMs: () -> Long = ::epochMillis,
    // Coarse on-device place cell for the current location, or null (location-aware
    // detection off / no permission). Sampled when an encounter is recorded.
    private val place: () -> String? = { null },
) {
    /** Addresses the user marked "it's mine" — never observed, never alerted. */
    private val muted: MutableSet<String> = history?.mutedAddresses()?.toMutableSet() ?: mutableSetOf()

    fun reset() = detector.reset()

    /** Mark a tracker as the user's own so it stops being flagged (persisted). */
    fun mute(address: String) {
        muted += address
        history?.mute(address)
    }

    /** Reverse a [mute] — the address is observed and alerted on again. */
    fun unmute(address: String) {
        muted -= address
        history?.unmute(address)
    }

    fun alerts(): Flow<List<TrackerAlert>> = flow {
        emit(emptyList())
        scanner.advertisements().collect { adv ->
            // A muted address contributes to neither detection signal…
            if (adv.address !in muted) detector.observe(TrackerClassifier.classify(adv))
            // Sample the coarse place + stable-crowd backdrop once, into a context signature.
            val here = place()
            val context = listOfNotNull(here, detector.backdropFingerprint(adv.timeMs))
                .joinToString("|").ifBlank { null }
            // …and a muted address is filtered out of the result in case it was seen pre-mute.
            emit(enrich(detector.evaluate(adv.timeMs), here, context).filter { it.trackingAddress !in muted })
        }
    }

    private fun enrich(alerts: List<TrackerAlert>, place: String?, context: String?): List<TrackerAlert> {
        val log = history?.takeIf { it.enabled() } ?: return alerts
        val now = nowEpochMs()
        return alerts.map { alert ->
            log.record(alert.kind, now, place, context)
            val cross = log.crossSession(alert.kind, now)
            if (cross != null && promote(cross)) {
                // Promote + carry the hour/place counts; the UI appends the localized sentence.
                alert.copy(
                    severity = Severity.ALERT,
                    crossSessionHours = cross.distinctHours,
                    crossSessionPlaces = cross.distinctPlaces,
                )
            } else {
                alert
            }
        }
    }

    /**
     * Promote a live sighting to ALERT on cross-session evidence — but key on context
     * *diversity*, not raw recurrence (docs/detection.md):
     *  - moved across distinct places, or recurred across ≥2 contexts beyond your
     *    learned baseline ⇒ following you;
     *  - recurrence within a *single* known context (your home/desk) is routine, so
     *    the hour-based [CrossSession.persistent] signal is suppressed there;
     *  - with no context signal at all (location off, no stable backdrop) we can't
     *    tell, so the hour fallbacks still apply.
     */
    private fun promote(cross: CrossSession): Boolean {
        val routine = cross.distinctContexts == 1 // everything seen in one known context
        return cross.multiPlace || cross.diverse || cross.veryPersistent || (cross.persistent && !routine)
    }
}
