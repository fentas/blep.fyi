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
    fun reset() = detector.reset()

    fun alerts(): Flow<List<TrackerAlert>> = flow {
        emit(emptyList())
        scanner.advertisements().collect { adv ->
            detector.observe(TrackerClassifier.classify(adv))
            emit(enrich(detector.evaluate(adv.timeMs)))
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
