package fyi.blep

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fyi.blep.core.ble.BleScanner
import fyi.blep.core.ble.ScanAvailability
import fyi.blep.core.model.BleDevice
import fyi.blep.core.tracking.TrackingPhase
import fyi.blep.core.tracking.TrackingSession
import fyi.blep.core.tracking.TrackingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/** Top-level navigation destinations. */
sealed interface Screen {
    data object Discovery : Screen
    data class Tracking(val device: BleDevice) : Screen
    data class Done(val device: BleDevice) : Screen
}

/**
 * Owns app state and bridges the [BleScanner] + [TrackingSession] into Compose
 * state. UI-framework-only (no Android/iOS types), so it's shared by the phone
 * and watch apps and constructed once per [scope].
 */
class BlepController(
    private val scanner: BleScanner,
    private val scope: CoroutineScope,
) {
    var screen by mutableStateOf<Screen>(Screen.Discovery)
        private set
    var devices by mutableStateOf<List<BleDevice>>(emptyList())
        private set
    var availability by mutableStateOf(ScanAvailability.READY)
        private set
    var includeUnnamed by mutableStateOf(false)
        private set
    var status by mutableStateOf<TrackingStatus?>(null)
        private set

    /** Devices shown in the list, honouring the unnamed toggle. */
    val visibleDevices: List<BleDevice>
        get() = if (includeUnnamed) devices else devices.filter { it.isNamed }

    /** How many discovered devices are currently hidden as unnamed. */
    val unnamedCount: Int
        get() = devices.count { !it.isNamed }

    private val aliases = mutableMapOf<String, String>()
    private var scanJob: Job? = null
    private var trackJob: Job? = null

    init {
        observeAvailability()
        startDiscovery()
    }

    fun startDiscovery() {
        trackJob?.cancel(); trackJob = null
        status = null
        screen = Screen.Discovery
        restartScan()
    }

    fun toggleUnnamed() {
        // The scan always collects everything; this only flips what's shown.
        includeUnnamed = !includeUnnamed
    }

    /** User-assigned rename, overlaid on scan results. */
    fun rename(device: BleDevice, alias: String?) {
        val clean = alias?.trim().orEmpty()
        if (clean.isEmpty()) aliases.remove(device.id) else aliases[device.id] = clean
        devices = devices.map { if (it.id == device.id) it.copy(alias = aliases[it.id]) else it }
    }

    fun track(device: BleDevice) {
        scanJob?.cancel(); scanJob = null
        screen = Screen.Tracking(device)
        val session = TrackingSession()
        status = session.status
        trackJob = scope.launch {
            val clock = TimeSource.Monotonic.markNow()
            scanner.rssi(device.id).collect { rssi ->
                val st = session.onSample(rssi, clock.elapsedNow().inWholeMilliseconds)
                status = st
                if (st.phase == TrackingPhase.COMPLETE) {
                    screen = Screen.Done(device)
                    // Stop ranging — the Done screen doesn't need live RSSI.
                    trackJob?.cancel()
                }
            }
        }
    }

    private fun restartScan() {
        scanJob?.cancel()
        scanJob = scope.launch {
            // Always collect everything; the UI filters via [visibleDevices].
            scanner.devices(includeUnnamed = true).collectLatest { list ->
                devices = list.map { it.copy(alias = aliases[it.id] ?: it.alias) }
            }
        }
    }

    private fun observeAvailability() {
        scope.launch { scanner.availability.collect { availability = it } }
    }
}
