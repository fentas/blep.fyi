package fyi.blep

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fyi.blep.core.ble.BleScanner
import fyi.blep.core.ble.ScanAvailability
import fyi.blep.core.model.BleDevice
import fyi.blep.core.platform.createKeyValueStore
import fyi.blep.core.safety.SafetyHistory
import fyi.blep.core.safety.SafetyScanner
import fyi.blep.core.safety.TrackerAlert
import fyi.blep.core.safety.TrackerDetector
import fyi.blep.core.safety.TrackerTuning
import fyi.blep.core.spatial.GuidanceStabilizer
import fyi.blep.core.spatial.Haptic
import fyi.blep.core.spatial.HapticCadence
import fyi.blep.core.spatial.MotionProvider
import fyi.blep.core.spatial.MotionSample
import fyi.blep.core.spatial.SpatialSnapshot
import fyi.blep.core.spatial.SpatialTracker
import fyi.blep.core.spatial.SpatialTuning
import fyi.blep.core.spatial.createHaptic
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Top-level navigation destinations. */
sealed interface Screen {
    data object Discovery : Screen
    data object Safety : Screen // "is something tracking me?" scan
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
    private val motionProvider: MotionProvider = createMotionProvider(),
    private val haptic: Haptic = createHaptic(),
    safetyTuning: TrackerTuning = TrackerTuning(),
    private val safetyHistory: SafetyHistory = SafetyHistory(createKeyValueStore()),
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
    /** True when no fresh RSSI has arrived recently (target out of range or off) —
     *  lets the UI show a "no signal" hint instead of guiding on a stale reading. */
    var signalLost by mutableStateOf(false)
        private set
    /** Seconds since the target was last heard (for the "last heard Xs ago" hint). */
    var signalAgeSec by mutableStateOf(0)
        private set
    /** Suspected unwanted trackers from the safety scan, strongest threat first. */
    var safetyAlerts by mutableStateOf<List<TrackerAlert>>(emptyList())
        private set
    /** Opt-in: remember tracker encounters across sessions to catch one that keeps
     *  reappearing near you over hours (the "is it following me?" signal). */
    var rememberEncounters by mutableStateOf(safetyHistory.enabled())
        private set
    /** Live spatial picture (track + target estimate) when motion sensors feed it. */
    var spatial by mutableStateOf<SpatialSnapshot?>(null)
        private set
    /** Stabilised turn-by-turn line (commits to a direction in clean fields, stays
     *  reactive in noisy ones). Null until guidance is confident. */
    var guidance by mutableStateOf<String?>(null)
        private set
    /** Whether the audible tracking tone is on (haptics stay regardless). */
    var soundOn by mutableStateOf(true)
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
    private var motionJob: Job? = null
    private var hapticJob: Job? = null

    private val spatialTuning = SpatialTuning()
    private val spatialTracker = SpatialTracker(spatialTuning)
    private val guidanceStabilizer = GuidanceStabilizer()
    private val safetyScanner = SafetyScanner(scanner, TrackerDetector(safetyTuning), safetyHistory)
    private var safetyJob: Job? = null
    // Latest motion sample; both flows run on the same (Main) dispatcher, so a
    // plain var is safe to share between the RSSI and motion collectors.
    private var latestMotion: MotionSample? = null
    private var lastRssiMark: TimeMark? = null   // when the last RSSI arrived
    private var trackStartMark: TimeMark? = null // when this tracking session began

    init {
        startDiscovery()
    }

    fun startDiscovery() {
        trackJob?.cancel(); trackJob = null
        motionJob?.cancel(); motionJob = null
        hapticJob?.cancel(); hapticJob = null
        safetyJob?.cancel(); safetyJob = null
        safetyAlerts = emptyList()
        status = null
        lastRssi = null
        signalLost = false
        signalAgeSec = 0
        lastRssiMark = null
        trackStartMark = null
        spatial = null
        guidance = null
        latestMotion = null
        spatialTracker.reset()
        guidanceStabilizer.reset()
        screen = Screen.Discovery
        restartScan()
    }

    fun toggleUnnamed() {
        // The scan always collects everything; this only flips what's shown.
        includeUnnamed = !includeUnnamed
    }

    /** Mute/unmute the audible tracking tone (the Geiger tick); haptics stay on. */
    fun toggleSound() {
        soundOn = !soundOn
        haptic.setSoundEnabled(soundOn)
    }

    /** User-assigned rename, overlaid on scan results. */
    fun rename(device: BleDevice, alias: String?) {
        val clean = alias?.trim().orEmpty()
        if (clean.isEmpty()) aliases.remove(device.id) else aliases[device.id] = clean
        devices = devices.map { if (it.id == device.id) it.copy(alias = aliases[it.id]) else it }
    }

    /** Start the "is something tracking me?" scan and show its screen. */
    fun openSafetyScan() {
        scanJob?.cancel(); scanJob = null
        safetyScanner.reset()
        safetyAlerts = emptyList()
        screen = Screen.Safety
        safetyJob?.cancel()
        safetyJob = scope.launch {
            try {
                safetyScanner.alerts().collect { safetyAlerts = it }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                // Radio unavailable — leave the list empty.
            }
        }
    }

    /** Toggle cross-session memory for the safety scan (clears the log when turned off). */
    fun toggleRememberEncounters() {
        val on = !rememberEncounters
        safetyHistory.setEnabled(on)
        rememberEncounters = on
    }

    /** Find a suspected tracker by handing its address to the normal hunt. */
    fun findTracker(alert: TrackerAlert) {
        val addr = alert.trackingAddress ?: return
        safetyJob?.cancel(); safetyJob = null
        track(BleDevice(id = addr, name = alert.title, rssi = alert.rssi))
    }

    fun track(device: BleDevice) {
        scanJob?.cancel(); scanJob = null
        screen = Screen.Tracking(device)
        val session = TrackingSession()
        status = session.status
        spatialTracker.reset()
        guidanceStabilizer.reset()
        spatial = null
        guidance = null
        latestMotion = null
        trackStartMark = TimeSource.Monotonic.markNow()
        lastRssiMark = null
        signalLost = false
        signalAgeSec = 0

        // Spatial track: drive the SpatialTracker from the motion stream (a single
        // time base), tagging each sample with the latest RSSI. Emits nothing on
        // platforms without motion sensors, so `spatial` simply stays null there.
        // The motion stream ticks steadily, so it also re-evaluates signal freshness.
        motionJob = scope.launch {
            try {
                motionProvider.motion().collect { sample ->
                    latestMotion = sample
                    val snap = spatialTracker.update((lastRssi ?: FALLBACK_RSSI).toDouble(), sample)
                    spatial = snap
                    guidance = guidanceStabilizer.guide(snap, spatialTuning)
                    val sinceRssi = lastRssiMark?.elapsedNow()
                    val sinceStart = trackStartMark?.elapsedNow()
                    signalLost = (sinceRssi != null && sinceRssi > SIGNAL_LOST_AFTER) ||
                        (sinceRssi == null && sinceStart != null && sinceStart > NO_SIGNAL_GRACE)
                    signalAgeSec = (sinceRssi ?: sinceStart)?.inWholeSeconds?.toInt() ?: 0
                }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                // Sensors unavailable — ignore; RSSI tracking continues.
            }
        }

        // Geiger-counter haptic/audio: pulse faster the closer you get.
        hapticJob = scope.launch {
            while (isActive) {
                val p = status?.proximity ?: 0f
                val interval = HapticCadence.intervalMs(p)
                if (interval == null) {
                    delay(250)
                } else {
                    haptic.pulse(p)
                    // A distinct double-tap when you're basically on top of it.
                    if (p >= VERY_CLOSE) { delay(55); haptic.pulse(p) }
                    delay(interval)
                }
            }
        }

        trackJob = scope.launch {
            val clock = TimeSource.Monotonic.markNow()
            try {
                scanner.rssi(device.id).collect { rssi ->
                    lastRssi = rssi
                    lastRssiMark = TimeSource.Monotonic.markNow()
                    // Ignore RSSI swings while the phone is being rotated/tilted —
                    // those are antenna/body geometry, not the target moving.
                    if (latestMotion?.reorienting != true) {
                        val st = session.onSample(rssi, clock.elapsedNow().inWholeMilliseconds)
                        status = st
                        if (st.phase == TrackingPhase.COMPLETE) {
                            screen = Screen.Done(device)
                            haptic.success()
                            // Stop ranging — the Done screen doesn't need live RSSI.
                            trackJob?.cancel()
                            motionJob?.cancel()
                            hapticJob?.cancel()
                        }
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

    private companion object {
        /** Stand-in RSSI for spatial samples taken before the first real reading. */
        const val FALLBACK_RSSI = -100
        /** No RSSI for this long after having one = signal lost. */
        val SIGNAL_LOST_AFTER = 4.seconds
        /** No RSSI at all for this long after starting = never acquired / device off. */
        val NO_SIGNAL_GRACE = 6.seconds
        /** Proximity at/above which the haptic adds a distinct "right here" double-tap. */
        const val VERY_CLOSE = 0.92f
    }
}
