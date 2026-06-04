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
) {
    /** Addresses the user marked "it's mine" — never observed, never alerted. */
    private val muted: MutableSet<String> = history?.mutedAddresses()?.toMutableSet() ?: mutableSetOf()

    fun reset() = detector.reset()

    /** Mark a tracker as the user's own so it stops being flagged (persisted). */
    fun mute(address: String) {
        muted += address
        history?.mute(address)
    }

    fun alerts(): Flow<List<TrackerAlert>> = flow {
        emit(emptyList())
        scanner.advertisements().collect { adv ->
            // A muted address contributes to neither detection signal…
            if (adv.address !in muted) detector.observe(TrackerClassifier.classify(adv))
            // …and is filtered out of the result in case it was seen pre-mute.
            emit(enrich(detector.evaluate(adv.timeMs)).filter { it.trackingAddress !in muted })
        }
    }

    private fun enrich(alerts: List<TrackerAlert>): List<TrackerAlert> {
        val h = history?.takeIf { it.enabled() } ?: return alerts
        val now = nowEpochMs()
        return alerts.map { alert ->
            h.record(alert.kind, now)
            val cs = h.crossSession(alert.kind, now)
            if (cs != null && cs.persistent) {
                alert.copy(
                    severity = Severity.ALERT,
                    detail = alert.detail +
                        " It's shown up near you across ${cs.distinctHours} separate hours — a strong sign it's travelling with you.",
                )
            } else {
                alert
            }
        }
    }
}
