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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
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
    /** Latest raw RSSI (dBm) of the device being tracked, for display. */
    var lastRssi by mutableStateOf<Int?>(null)
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
        startDiscovery()
    }

    fun startDiscovery() {
        trackJob?.cancel(); trackJob = null
        status = null
        lastRssi = null
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
            try {
                scanner.rssi(device.id).collect { rssi ->
                    lastRssi = rssi
                    val st = session.onSample(rssi, clock.elapsedNow().inWholeMilliseconds)
                    status = st
                    if (st.phase == TrackingPhase.COMPLETE) {
                        screen = Screen.Done(device)
                        // Stop ranging — the Done screen doesn't need live RSSI.
                        trackJob?.cancel()
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                // Lost the radio (permission/adapter) — fall back to discovery.
                startDiscovery()
            }
        }
    }

    private fun restartScan() {
        scanJob?.cancel()
        scanJob = scope.launch {
            // Self-healing scan: scanning can throw if Bluetooth permission isn't
            // granted yet (it's requested asynchronously at launch) or the adapter
            // is off. Catch it, surface the reason, and retry so the list starts
            // populating the moment the user taps "Allow".
            while (isActive) {
                try {
                    scanner.devices(includeUnnamed = true).collect { list ->
                        availability = ScanAvailability.READY
                        devices = list.map { it.copy(alias = aliases[it.id] ?: it.alias) }
                    }
                } catch (c: CancellationException) {
                    throw c
                } catch (e: Throwable) {
                    availability = if (e.message?.contains("permission", ignoreCase = true) == true)
                        ScanAvailability.PERMISSION_REQUIRED else ScanAvailability.BLUETOOTH_OFF
                    devices = emptyList()
                }
                delay(2000)
            }
        }
    }
}
