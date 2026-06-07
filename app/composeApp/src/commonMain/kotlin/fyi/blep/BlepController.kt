package fyi.blep

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fyi.blep.core.ble.BleScanner
import fyi.blep.core.ble.DeviceFavorites
import fyi.blep.core.ble.ScanAvailability
import fyi.blep.core.model.BleDevice
import fyi.blep.core.platform.coarsePlaceCell
import fyi.blep.core.platform.createKeyValueStore
import fyi.blep.core.safety.SafetyHistory
import fyi.blep.core.safety.SafetyScanner
import fyi.blep.core.safety.TrackerAlert
import fyi.blep.core.safety.ScanSensitivity
import fyi.blep.core.safety.TrackerDetector
import fyi.blep.core.safety.TrackerTuning
import fyi.blep.core.spatial.GuidanceLine
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
import fyi.blep.core.tracking.signalFreshness
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
    data object Onboarding : Screen // first-run intro, shown before Discovery
    data object Discovery : Screen
    data object Safety : Screen // "is something tracking me?" scan
    data object Settings : Screen
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
    private val safetyTuning: TrackerTuning? = null, // demo override; else the sensitivity preset
    private val safetyHistory: SafetyHistory = SafetyHistory(createKeyValueStore()),
    private val favorites: DeviceFavorites = DeviceFavorites(createKeyValueStore()),
    private val settings: AppSettings = AppSettings(),
) {
    var screen by mutableStateOf<Screen>(Screen.Discovery)
        private set
    var devices by mutableStateOf<List<BleDevice>>(emptyList())
        private set
    var availability by mutableStateOf(ScanAvailability.READY)
        private set
    var includeUnnamed by mutableStateOf(settings.showUnnamed())
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
    /** The tracker just muted via "It's mine", for the brief Undo affordance; null
     *  once the snackbar is dismissed or undone. */
    var lastMuted by mutableStateOf<TrackerAlert?>(null)
        private set
    /** Live spatial picture (track + target estimate) when motion sensors feed it. */
    var spatial by mutableStateOf<SpatialSnapshot?>(null)
        private set
    /** Stabilised turn-by-turn line (commits to a direction in clean fields, stays
     *  reactive in noisy ones). Null until guidance is confident. */
    var guidance by mutableStateOf<GuidanceLine?>(null)
        private set
    /** Whether the audible tracking tone is on (haptics stay regardless). */
    var soundOn by mutableStateOf(settings.trackingSound())
        private set
    /** Range connected devices via GATT so they show a live signal in the list
     *  (they don't advertise). Persisted; default on. */
    var measureConnectedSignal by mutableStateOf(settings.measureConnectedSignal())
        private set
    /** Keep a safety scan alive off-screen via a foreground service. Persisted; off. */
    var foregroundScanEnabled by mutableStateOf(settings.foregroundScan())
        private set
    /** Periodic background safety scan (WorkManager) while the app is closed. Off. */
    var backgroundScanEnabled by mutableStateOf(settings.backgroundScan())
        private set
    /** Background scan interval, minutes (15–240). */
    var scanIntervalMinutes by mutableStateOf(settings.scanIntervalMinutes())
        private set
    /** Detection sensitivity preset for the safety scan. */
    var scanSensitivity by mutableStateOf(settings.scanSensitivity())
        private set
    /** Location-aware detection (opt-in): sample a coarse on-device place on a
     *  suspect sighting to count distinct places. */
    var locationAware by mutableStateOf(settings.locationAware())
        private set

    /**
     * The main discovery list: everything genuinely **nearby** ([BleDevice.isPresent]
     * — a live signal or an active connection, paired or not) plus the user's
     * **favourites** (always, even when absent — still GATT-trackable). Silent,
     * non-favourite, unconnected bonded devices are left out so a long paired list
     * doesn't bury what's in range; they live in [pairedDevices] (the manager).
     * Favourites pin to the top.
     */
    val visibleDevices: List<BleDevice>
        get() = devices
            .filter { it.isPresent || it.isFavorite }                    // nearby (incl. connected) or starred
            .filter { it.isFavorite || includeUnnamed || it.isNamed }    // unnamed toggle applies to non-favourites
            .sortedWith(
                compareByDescending<BleDevice> { it.isFavorite }
                    .thenByDescending { it.isConnected }
                    .thenByDescending { it.rssi },
            )

    /** Count for the "N nearby" badge — only genuinely-present devices, so a
     *  favourite that's pinned but absent never inflates it. */
    val nearbyCount: Int
        get() = devices.count { it.isPresent && (includeUnnamed || it.isNamed) }

    /** How many *nearby* unnamed devices are hidden (favourites excluded — they're
     *  never gated by the unnamed toggle). */
    val unnamedCount: Int
        get() = devices.count { !it.isNamed && it.isPresent && !it.isFavorite }

    /** Every bonded/paired device, for the "all paired" manager where favourites are
     *  curated. Favourites and connected devices first, then by name. */
    val pairedDevices: List<BleDevice>
        get() = devices.filter { it.isPaired }
            .sortedWith(
                compareByDescending<BleDevice> { it.isFavorite }
                    .thenByDescending { it.isConnected }
                    .thenByDescending { it.isNamed }
                    .thenBy { it.displayName.lowercase() },
            )

    private val aliases = mutableMapOf<String, String>()
    private var favoriteIds: Set<String> = favorites.ids()
    private var scanJob: Job? = null
    private var trackJob: Job? = null
    private var motionJob: Job? = null
    private var hapticJob: Job? = null

    private val spatialTuning = SpatialTuning()
    private val spatialTracker = SpatialTracker(spatialTuning)
    private val guidanceStabilizer = GuidanceStabilizer()
    private var safetyScanner = buildSafetyScanner()
    private fun buildSafetyScanner() =
        SafetyScanner(
            scanner,
            TrackerDetector(safetyTuning ?: scanSensitivity.tuning),
            safetyHistory,
            place = { if (locationAware) coarsePlaceCell() else null },
        )
    private var safetyJob: Job? = null
    // Latest motion sample; both flows run on the same (Main) dispatcher, so a
    // plain var is safe to share between the RSSI and motion collectors.
    private var latestMotion: MotionSample? = null
    private var lastRssiMark: TimeMark? = null   // when the last RSSI arrived
    private var trackStartMark: TimeMark? = null // when this tracking session began

    init {
        haptic.setSoundEnabled(soundOn) // apply the persisted sound preference
        BackgroundScan.applyPeriodic(backgroundScanEnabled, scanIntervalMinutes)
        // Authoritative availability: the platform scanner proactively reports
        // adapter/permission state, so the banner reflects the real reason (and
        // recovers the moment the user fixes it) instead of guessing from a thrown
        // exception's message.
        scope.launch { scanner.availability.collect { availability = it } }
        if (settings.onboarded()) startDiscovery() else screen = Screen.Onboarding
    }

    /** First-run intro finished (completed or skipped) — never show it again. */
    fun finishOnboarding() {
        settings.setOnboarded(true)
        startDiscovery()
    }

    /** Foreground service that keeps a safety scan alive off-screen (persisted). */
    fun setForegroundScanning(on: Boolean) {
        foregroundScanEnabled = on
        settings.setForegroundScan(on)
        BackgroundScan.setForeground(on && screen is Screen.Safety)
    }

    /** Periodic background safety scan while the app is closed (persisted). */
    fun setBackgroundScanning(on: Boolean) {
        backgroundScanEnabled = on
        settings.setBackgroundScan(on)
        BackgroundScan.applyPeriodic(on, scanIntervalMinutes)
    }

    /** Background scan interval in minutes (clamped 15–240; persisted). */
    fun setScanInterval(minutes: Int) {
        scanIntervalMinutes = minutes.coerceIn(AppSettings.INTERVAL_MIN, AppSettings.INTERVAL_MAX)
        settings.setScanIntervalMinutes(scanIntervalMinutes)
        if (backgroundScanEnabled) BackgroundScan.applyPeriodic(true, scanIntervalMinutes)
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
        BackgroundScan.setForeground(false) // leaving safety → drop the foreground service
        screen = Screen.Discovery
        restartScan()
    }

    fun toggleUnnamed() {
        // The scan always collects everything; this only flips what's shown.
        includeUnnamed = !includeUnnamed
        settings.setShowUnnamed(includeUnnamed)
    }

    /** Mute/unmute the audible tracking tone (the Geiger tick); haptics stay on. */
    fun toggleSound() {
        soundOn = !soundOn
        haptic.setSoundEnabled(soundOn)
        settings.setTrackingSound(soundOn)
    }

    fun openSettings() { screen = Screen.Settings }

    /** Toggle location-aware detection (persisted). The scanner reads this live, so
     *  no rebuild is needed. */
    fun toggleLocationAware(on: Boolean) {
        locationAware = on
        settings.setLocationAware(on)
    }

    /** Switch the detection sensitivity preset (persisted); re-runs the safety scan
     *  with the new thresholds if it's open. Ignored in demo (fixed tuning). */
    fun selectScanSensitivity(s: ScanSensitivity) {
        if (s == scanSensitivity) return
        scanSensitivity = s
        settings.setScanSensitivity(s)
        if (safetyTuning == null) {
            safetyScanner = buildSafetyScanner()
            if (screen is Screen.Safety) openSafetyScan()
        }
    }

    /** Toggle GATT ranging of connected devices; persisted, re-runs discovery so
     *  the change takes effect immediately. */
    fun toggleConnectedSignal(on: Boolean) {
        if (on == measureConnectedSignal) return
        measureConnectedSignal = on
        settings.setMeasureConnectedSignal(on)
        if (screen is Screen.Discovery) restartScan()
    }

    /** User-assigned rename, overlaid on scan results. */
    fun rename(device: BleDevice, alias: String?) {
        val clean = alias?.trim().orEmpty()
        if (clean.isEmpty()) aliases.remove(device.id) else aliases[device.id] = clean
        devices = devices.map { if (it.id == device.id) it.copy(alias = aliases[it.id]) else it }
    }

    /** Star/unstar a device so it always shows in the main list (persisted). */
    fun toggleFavorite(device: BleDevice) {
        val nowFavorite = favorites.toggle(device.id)
        favoriteIds = favorites.ids()
        devices = devices.map { if (it.id == device.id) it.copy(isFavorite = nowFavorite) else it }
    }

    /** Start the "is something tracking me?" scan and show its screen. */
    fun openSafetyScan() {
        scanJob?.cancel(); scanJob = null
        safetyScanner.reset()
        safetyAlerts = emptyList()
        if (foregroundScanEnabled) BackgroundScan.setForeground(true)
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

    /** Mark a suspected tracker as the user's own — mutes it (persisted) and drops
     *  it from the current list. Rotating tags may reappear under a new address. */
    fun muteTracker(alert: TrackerAlert) {
        val addr = alert.trackingAddress ?: return
        safetyScanner.mute(addr)
        safetyAlerts = safetyAlerts.filterNot { it.trackingAddress == addr }
        lastMuted = alert
    }

    /** Undo the most recent [muteTracker] — the tracker is watched (and flagged) again. */
    fun undoMute() {
        lastMuted?.trackingAddress?.let { safetyScanner.unmute(it) }
        lastMuted = null
    }

    /** Dismiss the "muted" undo affordance without undoing. */
    fun clearMuteUndo() { lastMuted = null }

    /** Find a suspected tracker by handing its address to the normal hunt. The
     *  display [name] is the localized alert title, resolved by the UI. */
    fun findTracker(alert: TrackerAlert, name: String) {
        val addr = alert.trackingAddress ?: return
        safetyJob?.cancel(); safetyJob = null
        track(BleDevice(id = addr, name = name, rssi = alert.rssi))
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
                    guidance = guidanceStabilizer.guideLine(snap, spatialTuning)
                    val fresh = signalFreshness(
                        sinceLastRssiMs = lastRssiMark?.elapsedNow()?.inWholeMilliseconds,
                        sinceStartMs = trackStartMark?.elapsedNow()?.inWholeMilliseconds,
                        lostAfterMs = SIGNAL_LOST_AFTER.inWholeMilliseconds,
                        graceMs = NO_SIGNAL_GRACE.inWholeMilliseconds,
                    )
                    signalLost = fresh.lost
                    signalAgeSec = fresh.ageSec
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
                    scanner.devices(includeUnnamed = true, measureConnectedSignal = measureConnectedSignal).collect { list ->
                        // availability is driven solely by scanner.availability now;
                        // don't override it here (an early empty emission would falsely
                        // flip it to READY while permission is actually missing).
                        devices = list.map {
                            it.copy(alias = aliases[it.id] ?: it.alias, isFavorite = it.id in favoriteIds)
                        }
                    }
                } catch (c: CancellationException) {
                    throw c
                } catch (e: Throwable) {
                    // The availability flow reports the precise reason (adapter off /
                    // permission missing); here we just clear the stale list and let
                    // the self-healing loop retry.
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
