package fyi.blep.wear

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fyi.blep.core.ble.BleScanner
import fyi.blep.core.model.BleDevice
import fyi.blep.core.spatial.MotionProvider
import fyi.blep.core.spatial.MotionSample
import fyi.blep.core.spatial.SpatialSnapshot
import fyi.blep.core.spatial.SpatialTracker
import fyi.blep.core.spatial.createMotionProvider
import fyi.blep.core.tracking.TrackingPhase
import fyi.blep.core.tracking.TrackingSession
import fyi.blep.core.tracking.TrackingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
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
    private val motionProvider: MotionProvider = createMotionProvider(),
) {
    var devices by mutableStateOf<List<BleDevice>>(emptyList())
        private set
    var tracking by mutableStateOf<BleDevice?>(null)
        private set
    var status by mutableStateOf<TrackingStatus?>(null)
        private set
    var spatial by mutableStateOf<SpatialSnapshot?>(null)
        private set

    private var scanJob: Job? = null
    private var trackJob: Job? = null
    private var motionJob: Job? = null
    private val spatialTracker = SpatialTracker()
    private var latestMotion: MotionSample? = null

    init { startDiscovery() }

    fun startDiscovery() {
        trackJob?.cancel(); trackJob = null
        motionJob?.cancel(); motionJob = null
        tracking = null; status = null
        spatial = null; latestMotion = null
        spatialTracker.reset()
        scanJob?.cancel()
        scanJob = scope.launch {
            // Self-healing: scanning throws until the BLE permission is granted.
            while (isActive) {
                try {
                    scanner.devices(includeUnnamed = false).collect { devices = it }
                } catch (c: CancellationException) {
                    throw c
                } catch (_: Throwable) {
                    devices = emptyList()
                }
                delay(2000)
            }
        }
    }

    fun track(device: BleDevice) {
        scanJob?.cancel(); scanJob = null
        tracking = device
        val session = TrackingSession()
        status = session.status
        spatialTracker.reset(); spatial = null; latestMotion = null

        var lastRssi: Int? = null
        motionJob = scope.launch {
            try {
                motionProvider.motion().collect { sample ->
                    latestMotion = sample
                    spatial = spatialTracker.update((lastRssi ?: -100).toDouble(), sample)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) { /* no sensors — RSSI-only */ }
        }

        trackJob = scope.launch {
            val clock = TimeSource.Monotonic.markNow()
            try {
                scanner.rssi(device.id).collect { rssi ->
                    lastRssi = rssi
                    // Discount RSSI swings caused by rotating/tilting the watch.
                    if (latestMotion?.reorienting != true) {
                        val st = session.onSample(rssi, clock.elapsedNow().inWholeMilliseconds)
                        status = st
                        if (st.phase == TrackingPhase.COMPLETE) { trackJob?.cancel(); motionJob?.cancel() }
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                startDiscovery()
            }
        }
    }

    val isComplete: Boolean get() = status?.phase == TrackingPhase.COMPLETE
}
