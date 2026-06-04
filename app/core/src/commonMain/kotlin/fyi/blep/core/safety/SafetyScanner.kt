package fyi.blep.core.safety

import fyi.blep.core.ble.BleScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Runs the "is something tracking me?" scan: feeds the scanner's raw advertisement
 * stream through the [TrackerClassifier] into the [TrackerDetector], and emits the
 * current set of suspected-tracker [TrackerAlert]s. Cold — collecting starts the
 * scan, cancelling stops it.
 */
class SafetyScanner(
    private val scanner: BleScanner,
    private val detector: TrackerDetector = TrackerDetector(),
) {
    fun reset() = detector.reset()

    fun alerts(): Flow<List<TrackerAlert>> = flow {
        emit(emptyList())
        scanner.advertisements().collect { adv ->
            detector.observe(TrackerClassifier.classify(adv))
            emit(detector.evaluate(adv.timeMs))
        }
    }
}
