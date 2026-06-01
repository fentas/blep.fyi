package fyi.blep.wear

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fyi.blep.core.ble.BleScanner
import fyi.blep.core.model.BleDevice
import fyi.blep.core.tracking.TrackingPhase
import fyi.blep.core.tracking.TrackingSession
import fyi.blep.core.tracking.TrackingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * Minimal state holder for the watch app. Mirrors the phone's `BlepController`
 * but is intentionally separate: Wear uses plain Jetpack Compose (not Compose
 * Multiplatform), so it can't share the phone's composables. The tracking logic
 * itself is the same [TrackingSession] from `:core`.
 */
class WearController(
    private val scanner: BleScanner,
    private val scope: CoroutineScope,
) {
    var devices by mutableStateOf<List<BleDevice>>(emptyList())
        private set
    var tracking by mutableStateOf<BleDevice?>(null)
        private set
    var status by mutableStateOf<TrackingStatus?>(null)
        private set

    private var scanJob: Job? = null
    private var trackJob: Job? = null

    init { startDiscovery() }

    fun startDiscovery() {
        trackJob?.cancel(); trackJob = null
        tracking = null; status = null
        scanJob?.cancel()
        scanJob = scope.launch {
            scanner.devices(includeUnnamed = false).collectLatest { devices = it }
        }
    }

    fun track(device: BleDevice) {
        scanJob?.cancel(); scanJob = null
        tracking = device
        val session = TrackingSession()
        status = session.status
        trackJob = scope.launch {
            val clock = TimeSource.Monotonic.markNow()
            scanner.rssi(device.id).collect { rssi ->
                val st = session.onSample(rssi, clock.elapsedNow().inWholeMilliseconds)
                status = st
                if (st.phase == TrackingPhase.COMPLETE) trackJob?.cancel()
            }
        }
    }

    val isComplete: Boolean get() = status?.phase == TrackingPhase.COMPLETE
}
