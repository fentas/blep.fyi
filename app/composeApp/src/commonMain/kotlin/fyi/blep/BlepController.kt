package fyi.blep

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fyi.blep.core.ble.BleScanner
import fyi.blep.core.ble.DeviceAliases
import fyi.blep.core.ble.DeviceFavorites
import fyi.blep.core.ble.DeviceFlags
import fyi.blep.core.ble.IdentityStore
import fyi.blep.core.ble.ProbeResult
import fyi.blep.core.ble.RotationStats
import fyi.blep.core.ble.RotationTracker
import fyi.blep.core.ble.WornId
import fyi.blep.core.ble.ScanAvailability
import fyi.blep.core.model.BleDevice
import fyi.blep.core.platform.coarsePlaceCell
import fyi.blep.core.platform.createKeyValueStore
import fyi.blep.core.platform.epochMillis
import fyi.blep.core.safety.SafetyHistory
import fyi.blep.core.safety.SafetyScanner
import fyi.blep.core.safety.payloadFingerprint
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
import fyi.blep.core.sync.SyncManager
import fyi.blep.core.sync.SyncMessage
import fyi.blep.core.sync.SyncSettings
import fyi.blep.core.sync.Sighting
import fyi.blep.core.sync.SyncSink
import fyi.blep.core.sync.SyncSource
import fyi.blep.core.sync.createSyncTransport
import fyi.blep.core.tether.DeviceTether
import fyi.blep.core.tether.PresenceMonitor
import fyi.blep.core.tether.TetherAlertDirection
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

// Keys for settings that sync between phone and watch (see SyncSource.settings()).
private const val SETTING_SENSITIVITY = "sensitivity"
private const val SETTING_TETHER_ALERT = "tetherAlert"

/** Top-level navigation destinations. */
sealed interface Screen {
    data object Onboarding : Screen // first-run intro, shown before Discovery
    data object Discovery : Screen
    data object Safety : Screen // "is something tracking me?" scan
    data object Settings : Screen
    data class Tracking(val device: BleDevice) : Screen
    data class Done(val device: BleDevice) : Screen
    data class DeviceDetail(val device: BleDevice) : Screen // rename + identity + rotation history
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
    private val aliasStore: DeviceAliases = DeviceAliases(createKeyValueStore()),
    private val flags: DeviceFlags = DeviceFlags(createKeyValueStore()),
    private val tether: DeviceTether = DeviceTether(createKeyValueStore()),
    private val settings: AppSettings = AppSettings(),
    private val identityStore: IdentityStore = IdentityStore(createKeyValueStore(), ttlMs = settings.identityTtlDays().toLong() * AppSettings.DAY_MS),
    private val skipOnboarding: Boolean = false, // demo mode jumps straight to discovery
) {
    var screen by mutableStateOf<Screen>(Screen.Discovery)
        private set
    var devices by mutableStateOf<List<BleDevice>>(emptyList())
        private set
    // Dedicated low-latency signal for the device whose detail page is open (the same
    // fast stream the hunt uses) — refreshes ~3–10× quicker than the shared scan snapshot.
    var detailRssi by mutableStateOf<Int?>(null)
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
    /** Whether the audible tracking tone is on. */
    var soundOn by mutableStateOf(settings.trackingSound())
        private set
    /** Whether vibration feedback during tracking is on. */
    var hapticsOn by mutableStateOf(settings.haptics())
        private set
    /** Range connected devices via GATT so they show a live signal in the list
     *  (they don't advertise). Persisted; default on. */
    var measureConnectedSignal by mutableStateOf(settings.measureConnectedSignal())
        private set
    /** How hard blep scans for trackers in the background — Off / Interval / Continuous
     *  (Continuous supersedes Interval). Left-behind/flag watching is separate (always-auto)
     *  and can keep the service alive on its own in watch-only mode. Persisted. */
    var scanMode by mutableStateOf(settings.scanMode())
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
    /** How long device identities (rename/flag/first-seen) are remembered after last
     *  seen, in days — user-configurable. */
    var identityTtlDays by mutableStateOf(settings.identityTtlDays())
        private set

    /** Actively probe a lingering device (one short GATT connect) to learn its identity.
     *  On by default; one-shot + cached per device. */
    var probeEnabled by mutableStateOf(settings.probeEnabled())
        private set
    /** Minutes a device must have been around before it's worth a probe (ignores passers-by). */
    var probeThresholdMinutes by mutableStateOf(settings.probeThresholdMinutes())
        private set
    /** The address an on-demand probe is in flight for (drives the detail-page spinner —
     *  per-device, so it can't bleed onto a different device's panel). */
    var probingId by mutableStateOf<String?>(null)
        private set
    /** Bytes of on-device data blep is storing (identities, names, history, the safety
     *  log) — shown in Settings with a Clear action. Refreshed when Settings opens. */
    var storageBytes by mutableStateOf(0)
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
            .filter { it.isPresent || it.isFavorite || it.isTethered || it.isFlagged || it.id in suspectIds } // nearby, starred, watched, or suspected
            // The unnamed toggle skips favourites/watched/suspects — a suspected tracker is
            // usually unnamed, and hiding it would defeat the marking.
            .filter { it.isFavorite || it.isTethered || it.isFlagged || it.id in suspectIds || includeUnnamed || it.isNamed }
            .sortedWith(
                compareByDescending<BleDevice> { it.isFavorite }
                    .thenByDescending { it.isTethered || it.isFlagged }           // watched devices pin near the top too
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

    // Seeded from the persisted store so renames survive a restart; kept in memory
    // for the hot overlay path and written through on every change.
    private val aliases = aliasStore.all().toMutableMap()
    private var favoriteIds: Set<String> = favorites.ids()
    private var flaggedIds: Set<String> = flags.ids()
    private var tetheredIds: Set<String> = tether.ids()

    /** Devices the safety layer currently suspects (a live alert, or the tracker-alert
     *  notification the user tapped to get here). Session-scoped; tints their list rows. */
    var suspectIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Whether the continuous foreground watch service is currently running — drives the
     *  status indicator on the main screen. Kept in step by [applyForeground], the single
     *  point through which the service is started/stopped. */
    var foregroundActive by mutableStateOf(
        settings.scanMode() == ScanMode.CONTINUOUS || flags.ids().isNotEmpty() || tether.ids().isNotEmpty(),
    )
        private set
    // Shares the same on-disk key as the service/worker monitors (same prefs file), so the
    // Settings storage row + Clear cover the remembered presence state too.
    private val presence = PresenceMonitor(createKeyValueStore())

    // ── phone↔watch sync (Wear Data Layer) ────────────────────────────────────
    /** Per-category sync toggles ("Sync" settings menu). */
    val syncSettings = SyncSettings(createKeyValueStore())
    /** Whether the paired watch is reachable + nearby (for the UI / future role split). */
    var watchNearby by mutableStateOf(false)
        private set
    /** Devices the paired watch is currently seeing (scan fusion; empty unless opted in). */
    var remoteSightings by mutableStateOf<List<Sighting>>(emptyList())
        private set
    /** How many devices the watch sees that the phone currently doesn't (fusion gain). */
    val watchOnlyCount: Int get() = remoteSightings.count { s -> devices.none { it.id == s.id } }
    private var sync: SyncManager? = null

    // "Sync" settings menu — master + per-category, mirrored as Compose state.
    var syncEnabled by mutableStateOf(syncSettings.enabled()); private set
    var syncFavorites by mutableStateOf(syncSettings.favorites()); private set
    var syncNames by mutableStateOf(syncSettings.names()); private set
    var syncTethered by mutableStateOf(syncSettings.tethered()); private set
    var syncAlerts by mutableStateOf(syncSettings.alerts()); private set
    var syncScans by mutableStateOf(syncSettings.scans()); private set

    fun toggleSync(on: Boolean) { syncSettings.setEnabled(on); syncEnabled = on; sync?.start() }
    fun toggleSyncScans(on: Boolean) { syncSettings.setScans(on); syncScans = on; if (!on) remoteSightings = emptyList() }
    fun toggleSyncFavorites(on: Boolean) { syncSettings.setFavorites(on); syncFavorites = on; syncPush() }
    fun toggleSyncNames(on: Boolean) { syncSettings.setNames(on); syncNames = on; syncPush() }
    fun toggleSyncTethered(on: Boolean) { syncSettings.setTethered(on); syncTethered = on; syncPush() }
    fun toggleSyncAlerts(on: Boolean) { syncSettings.setAlerts(on); syncAlerts = on }
    private var scanJob: Job? = null
    private var detailSignalJob: Job? = null
    private var probeJob: Job? = null
    private var trackJob: Job? = null
    // Full probe results for the session (incl. volatile battery) — the detail page's
    // device-info card. The name + re-correlation key persist via the IdentityStore.
    private val probeResults = mutableMapOf<String, ProbeResult>()
    private var motionJob: Job? = null
    private var hapticJob: Job? = null

    private val spatialTuning = SpatialTuning()
    private val spatialTracker = SpatialTracker(spatialTuning)
    // Correlates rotating addresses back into logical devices across the scan stream
    // (id-switch handovers); fed in restartScan, read by the device detail page/list.
    private val rotationTracker = RotationTracker()
    private val rotationClock = TimeSource.Monotonic.markNow()

    // ── demo: a pre-seeded rotating tracker so the detail panel shows the identity
    // features (id-change history, rename) without waiting for real rotations ──
    // Declared before init{} (which kicks off the scan + probe worker that read it).
    private class DemoIdentity(val stats: RotationStats, val worn: List<String>, val firstSeenAgoMs: Long)
    private val demoIdentity = mutableMapOf<String, DemoIdentity>()
    private var lastIdentitySyncMark: TimeMark? = null // throttle persisted-identity writes
    private val guidanceStabilizer = GuidanceStabilizer()
    private var safetyScanner = buildSafetyScanner()
    private fun buildSafetyScanner() =
        SafetyScanner(
            scanner,
            TrackerDetector(safetyTuning ?: scanSensitivity.tuning),
            safetyHistory,
            // The shared identity layer: probe close suspects + persist their stable id,
            // so a rotating follower is caught + named (here and on the watch). Off in demo.
            identityStore = identityStore.takeIf { demoIdentity.isEmpty() },
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
        haptic.setVibrationEnabled(hapticsOn) // …and the haptics preference
        BackgroundScan.applyPeriodic(scanMode == ScanMode.INTERVAL, scanIntervalMinutes)
        // Authoritative availability: the platform scanner proactively reports
        // adapter/permission state, so the banner reflects the real reason (and
        // recovers the moment the user fixes it) instead of guessing from a thrown
        // exception's message.
        scope.launch { scanner.availability.collect { availability = it } }
        if (skipOnboarding || settings.onboarded()) startDiscovery() else screen = Screen.Onboarding
        // NB: startSync() is deliberately NOT called here — it reads Compose state
        // (tetherAlert, scanSensitivity via source.settings()) that is declared further down
        // the class body and so isn't initialized yet during this init block. It runs from a
        // trailing init block instead, once every property is constructed. See the bottom.
    }

    // ── phone↔watch sync wiring ────────────────────────────────────────────────
    private fun startSync() {
        val source = object : SyncSource {
            override fun favorites() = favorites.ids()
            override fun tethered() = tether.ids()
            override fun muted() = safetyHistory.mutedAddresses()
            override fun aliases() = aliasStore.all()
            override fun settings() = mapOf(
                SETTING_SENSITIVITY to scanSensitivity.name,
                SETTING_TETHER_ALERT to tetherAlert.name,
            )
        }
        val sink = object : SyncSink {
            override fun applyFavorites(ids: Set<String>) {
                favorites.replace(ids); favoriteIds = ids
                devices = devices.map { it.copy(isFavorite = it.id in ids) }
            }
            override fun applyTethered(ids: Set<String>) {
                tether.replace(ids); tetheredIds = ids
                devices = devices.map { it.copy(isTethered = it.id in ids) }
                syncWatch()
            }
            override fun applyMuted(ids: Set<String>) { safetyHistory.replaceMuted(ids) }
            override fun applyAliases(map: Map<String, String>) {
                aliasStore.replaceAll(map)
                aliases.clear(); aliases.putAll(map)
                devices = devices.map { it.copy(alias = aliases[it.id]) }
            }
            override fun applySetting(name: String, value: String) {
                when (name) {
                    SETTING_SENSITIVITY -> ScanSensitivity.fromName(value).let {
                        if (it != scanSensitivity) { scanSensitivity = it; settings.setScanSensitivity(it); safetyScanner = buildSafetyScanner() }
                    }
                    SETTING_TETHER_ALERT -> TetherAlertDirection.fromName(value).let {
                        if (it != tetherAlert) { tetherAlert = it; settings.setTetherAlert(it) }
                    }
                }
            }
            override fun onMessage(msg: SyncMessage) {
                if (msg is SyncMessage.Sightings) remoteSightings = msg.devices // scan fusion
            }
            override fun onPeerNearby(nearby: Boolean) { watchNearby = nearby }
        }
        sync = SyncManager(createSyncTransport(), syncSettings, source, sink, scope).also { it.start() }
        // Scan fusion: relay what we're seeing to the watch on a throttle (opt-in; the
        // send is a no-op unless "sync scans" is on).
        scope.launch {
            while (isActive) {
                delay(5.seconds)
                sync?.sendSightings(devices.filter { it.isPresent }.map { Sighting(it.id, it.rssi, it.displayName) })
            }
        }
    }

    /** Re-publish local state after the user changed something synced. */
    private fun syncPush() = sync?.localChanged()

    /** First-run intro finished (completed or skipped) — never show it again. */
    fun finishOnboarding() {
        settings.setOnboarded(true)
        startDiscovery()
    }

    /** Light/dark/system theme preference (persisted; the root theme reads it). */
    var themeMode by mutableStateOf(settings.themeMode())
        private set

    fun selectTheme(mode: ThemeMode) {
        themeMode = mode
        settings.setThemeMode(mode)
    }

    /** Set the background tracker-scan mode (Off / Interval / Continuous). Interval schedules
     *  periodic WorkManager checks; Continuous runs the foreground service. Left-behind/flag
     *  watching is independent — a watch keeps the service alive (watch-only) regardless. */
    fun selectScanMode(mode: ScanMode) {
        scanMode = mode
        settings.setScanMode(mode)
        BackgroundScan.applyPeriodic(mode == ScanMode.INTERVAL, scanIntervalMinutes)
        // A watch keeps the service alive (the service reads scanMode to decide whether it
        // also tracker-scans, or just watches). Otherwise Continuous starts it immediately
        // only on the safety screen — elsewhere it spins up when the app is minimised.
        if (flaggedIds.isNotEmpty() || tetheredIds.isNotEmpty()) applyForeground(true)
        else applyForeground(mode == ScanMode.CONTINUOUS && screen is Screen.Safety)
    }

    /** The single point that starts/stops the foreground service, so [foregroundActive]
     *  (the main-screen status indicator) always reflects the real service state. */
    private fun applyForeground(on: Boolean) {
        BackgroundScan.setForeground(on)
        foregroundActive = on
    }

    /** Quick-disable from the foreground-service status modal: stop everything that keeps the
     *  service alive — every "Watch this device" (flag) and every left-behind watch — and turn
     *  the foreground scan off, so the service actually stops. */
    fun disableForegroundService() {
        unflagAll()
        unwatchAll()
        selectScanMode(ScanMode.OFF)
    }

    /** Clear every "Watch this device" flag at once (they keep the foreground service alive). */
    fun unflagAll() {
        if (flaggedIds.isEmpty()) return
        flags.clear()
        flaggedIds = emptySet()
        devices = devices.map { if (it.isFlagged) it.copy(isFlagged = false) else it }
        syncPush()
    }

    /** Background scan interval in minutes (clamped 15–240; persisted). Always settable, even
     *  when not in Interval mode (the slider stays live); only re-schedules if Interval is on. */
    fun setScanInterval(minutes: Int) {
        scanIntervalMinutes = minutes.coerceIn(AppSettings.INTERVAL_MIN, AppSettings.INTERVAL_MAX)
        settings.setScanIntervalMinutes(scanIntervalMinutes)
        if (scanMode == ScanMode.INTERVAL) BackgroundScan.applyPeriodic(true, scanIntervalMinutes)
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
        applyForeground(false) // leaving safety → drop the foreground service
        syncWatch() // …unless a flagged device still wants the continuous watch
        screen = Screen.Discovery
        restartScan()
    }

    fun toggleUnnamed() {
        // The scan always collects everything; this only flips what's shown.
        includeUnnamed = !includeUnnamed
        settings.setShowUnnamed(includeUnnamed)
    }

    /** Mute/unmute the audible tracking tone (the Geiger tick). */
    fun toggleSound() {
        soundOn = !soundOn
        haptic.setSoundEnabled(soundOn)
        settings.setTrackingSound(soundOn)
    }

    /** Enable/disable tracking vibration feedback (persisted). */
    fun toggleHaptics() {
        hapticsOn = !hapticsOn
        haptic.setVibrationEnabled(hapticsOn)
        settings.setHaptics(hapticsOn)
    }

    fun openSettings() {
        refreshStorage()
        screen = Screen.Settings
    }

    /** Sum the on-disk footprint of everything blep remembers (for the Settings row). */
    fun refreshStorage() {
        storageBytes = identityStore.sizeBytes() + aliasStore.sizeBytes() + flags.sizeBytes() +
            favorites.sizeBytes() + safetyHistory.sizeBytes() + tether.sizeBytes() + presence.sizeBytes()
    }

    /** Forget everything blep has learned about devices — identities, names, flags,
     *  favourites, rotation history, identified info, and the tracker log. Settings and
     *  the "it's mine" mutes are kept. */
    fun clearStorage() {
        identityStore.clear()
        aliasStore.clear(); aliases.clear()
        flags.clear(); flaggedIds = emptySet()
        favorites.clear(); favoriteIds = emptySet()
        tether.clear(); tetheredIds = emptySet()
        presence.clear()
        safetyHistory.clear()
        probeResults.clear()
        rotationTracker.reset()
        devices = devices.map { it.copy(alias = null, isFavorite = false, isFlagged = false) }
        syncWatch() // nothing flagged now → the watch service can stand down
        refreshStorage()
    }

    /** Open the per-device detail page (identity, rename, rotation history). The fast
     *  detail signal is driven by the screen via [startDetailSignal], keyed on the
     *  current (possibly rotated) address so it follows the device. */
    fun openDeviceDetail(device: BleDevice) { screen = Screen.DeviceDetail(device) }

    /** A tapped tracker-alert notification lands here: mark the device suspect and open its
     *  panel. The device may not be in the snapshot yet (the scan just started) — synthesize
     *  a shell so the panel opens immediately; the live signal fills in as the scan sees it. */
    fun openSuspect(id: String) {
        suspectIds = suspectIds + id
        val device = devices.firstOrNull { it.id == id }
            ?: BleDevice(id = id, name = null, rssi = BleDevice.RSSI_UNKNOWN, alias = effectiveAlias(id))
                // Seed it into the snapshot so the list shows the marked row right away
                // (the retention merge keeps suspects, like favourites, once present).
                .also { devices = devices + it }
        openDeviceDetail(device)
    }

    /** The device's current live address, following any id rotation since the detail
     *  page was opened — so a watched device's page tracks its lineage instead of
     *  dying on the id it has since rotated away from. Returns the input id if nothing
     *  is correlated (or in demo, where the seeded id is the head). */
    fun currentAddressFor(id: String): String? {
        if (id in demoIdentity) return id
        rotationTracker.currentAddressFor(id)?.let { return it }
        return identityStore.addressesFor(id).firstOrNull { addr -> devices.any { it.id == addr } } ?: id
    }

    /** While the detail page is open, subscribe to the dedicated low-latency RSSI for
     *  this device (≈0.3 s GATT poll for a bonded device, no batching for an
     *  advertiser) — far quicker than the shared scan's coarse snapshot. Re-keyed by
     *  the screen when the device rotates. Demo keeps the scripted list value. */
    fun startDetailSignal(id: String) {
        detailSignalJob?.cancel()
        detailRssi = null
        if (demoIdentity.isNotEmpty()) return // demo: the scripted curve isn't a real signal
        detailSignalJob = scope.launch {
            runCatching { scanner.rssi(id).collect { detailRssi = it } }
        }
    }

    /** Stop the fast detail signal (the detail page left the composition). */
    fun stopDetailSignal() {
        detailSignalJob?.cancel(); detailSignalJob = null
        detailRssi = null
    }

    // ── active probe: learn a lingering device's identity with one GATT connect ──────
    // Amortised to once-per-identity (the IdentityStore caches the outcome, refusals
    // included), dwell-gated so passers-by are ignored, one-at-a-time. Tied to the
    // discovery scan; demo's auto-worker is off (the on-demand button still works).
    private fun startProbeWorker() {
        probeJob?.cancel(); probeJob = null
        if (!probeEnabled || demoIdentity.isNotEmpty()) return
        probeJob = scope.launch {
            while (isActive) {
                delay(PROBE_TICK_MS)
                val target = nextProbeCandidate() ?: continue
                probeAndRecord(target)
                delay(PROBE_COOLDOWN_MS) // gentle on the radio; never hammer
            }
        }
    }

    /** Probe one device, cache the result (in-memory + persisted), and refresh the list.
     *  A failed/refused probe records a non-connectable result so we don't re-poke it. */
    private suspend fun probeAndRecord(address: String): ProbeResult {
        val result = runCatching { scanner.probe(address) }.getOrNull() ?: ProbeResult(connectable = false)
        probeResults[address] = result
        identityStore.recordProbe(address, result)
        refreshProbeNames()
        return result
    }

    /** The longest-resident, present, still-unprobed device past the dwell threshold —
     *  oldest first-seen wins (most likely to matter; passers-by never qualify). */
    private fun nextProbeCandidate(): String? {
        val thresholdMs = probeThresholdMinutes.toLong() * 60_000L
        val nowEpoch = epochMillis()
        return devices.asSequence()
            .filter { it.isPresent && !it.rssiUnknown && !identityStore.isProbed(it.id) }
            .mapNotNull { d -> identityStore.firstSeenOf(d.id)?.let { d.id to it } }
            .filter { (it.second) <= nowEpoch - thresholdMs }
            .minByOrNull { it.second }?.first
    }

    /** On-demand probe from the detail page — bypasses the dwell gate (the user asked),
     *  and re-probes even a known device to refresh the live device-info card. */
    fun probeNow(device: BleDevice) {
        if (probingId != null) return // one on-demand probe at a time
        probingId = device.id
        scope.launch {
            try {
                probeAndRecord(device.id)
            } finally {
                probingId = null
            }
        }
    }

    /** The label a probe found for this device (or its lineage), shown on the detail page. */
    fun probeLabel(id: String): String? = identityStore.probeLabelOf(id)
    fun isProbed(id: String): Boolean = identityStore.isProbed(id)

    /** The full probe result for this device (or any id in its lineage), for the detail
     *  page's device-info card. The live session's result (incl. battery) wins; otherwise
     *  the descriptive blob persisted in the IdentityStore (so the card survives a restart). */
    fun probeInfo(id: String): ProbeResult? =
        demoIdentity[id]?.let { DEMO_PROBE }
            ?: probeResults[id] ?: identityStore.addressesFor(id).firstNotNullOfOrNull { probeResults[it] }
            ?: identityStore.probeDetailOf(id)?.let { runCatching { ProbeResult.unpack(it) }.getOrNull() }

    /** Re-map the list so a freshly-probed name appears immediately (the next scan tick
     *  would do it anyway via the devices() mapping; this just makes it snappy). */
    private fun refreshProbeNames() {
        devices = devices.map { if (it.name == null) it.copy(name = identityStore.probeLabelOf(it.id)) else it }
    }

    /** Toggle the active-probe feature (persisted); (re)starts the worker if discovering.
     *  Named to avoid clashing with the generated `probeEnabled` setter. */
    fun toggleProbe(on: Boolean) {
        probeEnabled = on
        settings.setProbeEnabled(on)
        if (screen is Screen.Discovery) startProbeWorker() else probeJob?.cancel()
    }

    fun setProbeThreshold(minutes: Int) {
        probeThresholdMinutes = minutes.coerceIn(AppSettings.PROBE_MIN_MINUTES, AppSettings.PROBE_MAX_MINUTES)
        settings.setProbeThresholdMinutes(probeThresholdMinutes)
    }

    /** Rotation/identity-churn stats for a device id, or null if uncorrelated yet. */
    fun rotationStats(id: String): RotationStats? = demoIdentity[id]?.stats ?: rotationTracker.statsFor(id)

    /** True while a handover involving this id is pending (a matching predecessor went
     *  quiet but hasn't retired) — the detail page shows "correlating…". */
    fun isCorrelating(id: String): Boolean =
        id !in demoIdentity && rotationTracker.isCorrelating(id, rotationClock.elapsedNow().inWholeMilliseconds)

    /** How long ago this device was first seen (ms), carried across its id rotations.
     *  Prefers the persisted first-seen (stable — survives the live track ageing out and
     *  app restarts); falls back to the live track only until the first identity sync. */
    fun rotationFirstSeenAgoMs(id: String): Long? {
        demoIdentity[id]?.let { return it.firstSeenAgoMs }
        identityStore.firstSeenOf(id)?.let { return epochMillis() - it }
        return rotationTracker.statsFor(id)?.let { rotationClock.elapsedNow().inWholeMilliseconds - it.firstSeenMs }
    }

    /** Demo only: seed the showcase tracker's rotation history + rename. */
    fun seedDemoIdentity() {
        val cur = "C4:2A:1B:90:EF:01"
        val worn = listOf("C4:2A:1B:11:00:01", "C4:2A:1B:35:00:02", "C4:2A:1B:7E:00:03", cur)
        val m = 60_000L
        val history = listOf(
            WornId("C4:2A:1B:11:00:01", 0, 16 * m, 0.90),
            WornId("C4:2A:1B:35:00:02", 0, 15 * m, 0.94),
            WornId("C4:2A:1B:7E:00:03", 0, 14 * m, 0.91),
            WornId(cur, 0, 12 * m, 1.0, current = true),
        )
        demoIdentity[cur] = DemoIdentity(
            stats = RotationStats(address = cur, rssi = -62, firstSeenMs = 0, lastSeenMs = 0, rotations = 3, addressesSeen = 4, confidence = 0.92, history = history),
            worn = worn,
            firstSeenAgoMs = 57 * 60_000L,
        )
        aliases[cur] = "Bag tag" // a rename that follows the rotation
        devices = devices.map { if (it.id == cur) it.copy(alias = "Bag tag") else it }
    }

    /** The ids this device has worn (its rotation lineage), current id last. For the
     *  detail page's history list. Empty when there's nothing correlated. */
    fun deviceHistory(id: String): List<String> =
        demoIdentity[id]?.worn
            ?: rotationTracker.identityFor(id)?.addresses?.let { (it - id).sorted() + id }
            ?: listOf(id)

    // ── identity: rename/flag follow a device across its rotating addresses ──────
    // Resolved across the persisted identity's address set, so a label saved under one
    // address is found under all of them — even after a restart.
    private fun effectiveAlias(id: String): String? = identityStore.addressesFor(id).firstNotNullOfOrNull { aliases[it] }
    private fun effectiveFlagged(id: String): Boolean = identityStore.addressesFor(id).any { it in flaggedIds }

    /** Persist the address↔identity groupings the live correlator is confident about,
     *  and refresh last-seen for everything present (TTL). Throttled — disk writes on
     *  every 2 s scan tick would be wasteful. */
    private fun syncIdentities(present: List<BleDevice>) {
        val mark = lastIdentitySyncMark
        if (mark != null && mark.elapsedNow() < IDENTITY_SYNC_INTERVAL) return
        val presentIds = present.asSequence().filter { !it.rssiUnknown }.map { it.id }.toSet()
        if (presentIds.isEmpty()) return
        lastIdentitySyncMark = TimeSource.Monotonic.markNow()
        identityStore.seen(presentIds)
        for (id in presentIds) {
            val identity = rotationTracker.identityFor(id) ?: continue
            // Only persist a high-confidence, uncontested rotation lineage — a weak link
            // would later drag a rename onto the wrong device.
            if (!identity.contested && identity.confidence >= IDENTITY_MIN_CONF && identity.addresses.size > 1) {
                identityStore.link(identity.addresses)
            }
        }
    }

    /** Toggle location-aware detection (persisted). The scanner reads this live, so
     *  no rebuild is needed. */
    fun toggleLocationAware(on: Boolean) {
        locationAware = on
        settings.setLocationAware(on)
    }

    /** Set how long device identities are remembered (days, persisted); applies to the
     *  store live so the next prune uses it. */
    fun setIdentityTtl(days: Int) {
        val d = days.coerceIn(AppSettings.IDENTITY_TTL_MIN_DAYS, AppSettings.IDENTITY_TTL_MAX_DAYS)
        identityTtlDays = d
        settings.setIdentityTtlDays(d)
        identityStore.ttlMs = d.toLong() * AppSettings.DAY_MS
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
        syncPush()
    }

    /** Toggle GATT ranging of connected devices; persisted, re-runs discovery so
     *  the change takes effect immediately. */
    fun toggleConnectedSignal(on: Boolean) {
        if (on == measureConnectedSignal) return
        measureConnectedSignal = on
        settings.setMeasureConnectedSignal(on)
        if (screen is Screen.Discovery) restartScan()
    }

    /** User-assigned rename, overlaid on scan results and persisted across restarts. */
    fun rename(device: BleDevice, alias: String?) {
        val clean = aliasStore.set(device.id, alias) // persist + normalize (null = cleared)
        if (clean == null) aliases.remove(device.id) else aliases[device.id] = clean
        devices = devices.map { if (it.id == device.id) it.copy(alias = aliases[it.id]) else it }
        syncPush()
    }

    /** Star/unstar a device so it always shows in the main list (persisted). */
    fun toggleFavorite(device: BleDevice) {
        val nowFavorite = favorites.toggle(device.id)
        favoriteIds = favorites.ids()
        devices = devices.map { if (it.id == device.id) it.copy(isFavorite = nowFavorite) else it }
        syncPush()
    }

    /** Flag/unflag a device for priority watching (persisted). A flag escalates the
     *  background check to a continuous foreground watch (notifies while in range);
     *  removing the last flag drops it again. Returns the new flagged state. */
    fun toggleFlag(device: BleDevice): Boolean {
        val nowFlagged = flags.toggle(device.id)
        flaggedIds = flags.ids()
        devices = devices.map { if (it.id == device.id) it.copy(isFlagged = nowFlagged) else it }
        syncWatch()
        return nowFlagged
    }

    /** Whether a device has a leave/return ("left behind") alert set on it. */
    fun isTethered(id: String): Boolean = id in tetheredIds

    /** Tether/untether a device: alert (notification + vibrate) when it leaves Bluetooth
     *  range, and when it returns. Like a flag, a tether keeps the continuous foreground
     *  watch alive so the leave is caught promptly. Returns the new tethered state. */
    fun toggleTether(device: BleDevice): Boolean {
        val now = tether.toggle(device.id)
        tetheredIds = tether.ids()
        devices = devices.map { if (it.id == device.id) it.copy(isTethered = now) else it }
        syncWatch()
        syncPush()
        return now
    }

    /** Whether the left-behind explainer modal has been dismissed for good. */
    var watchExplained by mutableStateOf(settings.watchExplainerDismissed())
        private set

    /** Remember the user ticked "don't show again" on the left-behind explainer. */
    fun dismissWatchExplainer() {
        settings.setWatchExplainerDismissed(true)
        watchExplained = true
    }

    /** Whether the "Watch this device" (flag) explainer modal has been dismissed for good. */
    var flagExplained by mutableStateOf(settings.flagExplainerDismissed())
        private set

    /** Remember the user ticked "don't show again" on the flag explainer. */
    fun dismissFlagExplainer() {
        settings.setFlagExplainerDismissed(true)
        flagExplained = true
    }

    /** How many devices currently have a left-behind ("watch") alert set. */
    val watchedCount: Int get() = (flaggedIds + tetheredIds).size

    /** Turn off every left-behind watch at once (used when disabling the foreground service,
     *  which the watches depend on). Mirrors the per-device untether bookkeeping. */
    fun unwatchAll() {
        if (tetheredIds.isEmpty()) return
        tether.replace(emptySet())
        tetheredIds = emptySet()
        devices = devices.map { if (it.isTethered) it.copy(isTethered = false) else it }
        syncWatch()
        syncPush()
    }

    /** Which tethered-device transitions raise an alert (Leave / Return / Both; persisted). */
    var tetherAlert by mutableStateOf(settings.tetherAlert())
        private set

    fun selectTetherAlert(dir: TetherAlertDirection) {
        tetherAlert = dir
        settings.setTetherAlert(dir)
        syncPush()
    }

    /** Keep a continuous foreground watch alive while any device is flagged or tethered
     *  (the service reads those sets + scans for them). Doesn't tear down the foreground
     *  service while the safety screen still wants it. */
    private fun syncWatch() {
        if (flaggedIds.isNotEmpty() || tetheredIds.isNotEmpty()) applyForeground(true)
        else if (screen !is Screen.Safety) applyForeground(false)
    }

    /** Start the "is something tracking me?" scan and show its screen. */
    fun openSafetyScan() {
        scanJob?.cancel(); scanJob = null; probeJob?.cancel(); probeJob = null
        safetyScanner.reset()
        safetyAlerts = emptyList()
        if (scanMode == ScanMode.CONTINUOUS) applyForeground(true)
        screen = Screen.Safety
        safetyJob?.cancel()
        safetyJob = scope.launch {
            try {
                // The SafetyScanner now probes suspects + persists identity itself (shared
                // with the watch), so the controller just surfaces the alerts.
                safetyScanner.alerts().collect { list ->
                    safetyAlerts = list
                    // Remember who's suspected so the discovery list can mark those rows.
                    suspectIds = suspectIds + list.mapNotNull { it.trackingAddress }
                }
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
        suspectIds = suspectIds - addr // "it's mine" → stop marking its row
        lastMuted = alert
        syncPush()
    }

    /** Undo the most recent [muteTracker] — the tracker is watched (and flagged) again. */
    fun undoMute() {
        lastMuted?.trackingAddress?.let { safetyScanner.unmute(it) }
        lastMuted = null
        syncPush()
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
        scanJob?.cancel(); scanJob = null; probeJob?.cancel(); probeJob = null
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
                        if (st.phase == TrackingPhase.COMPLETE && screen !is Screen.Done) {
                            screen = Screen.Done(device)
                            haptic.success()
                            // Stop the Geiger pulse + motion, but keep ranging alive:
                            // the Done screen shows the live dB so you can sweep the
                            // last few cm to the exact spot.
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
            // Rotation correlator — fingerprint feed. The raw advertisement stream
            // carries the payload (manufacturer data / service UUIDs), so a payload
            // fingerprint can corroborate or veto an id-switch handover (the bare
            // device list has no payload). Cancelled with the scan job.
            launch {
                runCatching {
                    scanner.advertisements().collect { adv ->
                        rotationTracker.observe(
                            adv.address, adv.rssi,
                            rotationClock.elapsedNow().inWholeMilliseconds,
                            payloadFingerprint(adv),
                        )
                    }
                }
            }
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
                        // NB: the correlator is fed *only* from the raw advertisement
                        // stream above, never from this snapshot. The snapshot is a table
                        // that keeps echoing a device for ~12 s after it actually goes
                        // silent (the prune TTL), so feeding it here dragged a rotated-away
                        // id's last-seen forward — making its real successor look like it
                        // had *coexisted* with it, which vetoes the handover. A lone device
                        // would then never correlate. The advert stream carries true
                        // per-advert timestamps, so an id that stops is seen to stop.
                        syncIdentities(list)
                        val nowMs = epochMillis()
                        val prevSeen = devices.associate { it.id to it.seenAtMs }
                        val mapped = list.map {
                            it.copy(
                                // an unnamed device shows the name a probe learned for it, if any
                                name = it.name ?: identityStore.probeLabelOf(it.id),
                                alias = effectiveAlias(it.id) ?: it.alias,
                                isFavorite = it.id in favoriteIds,
                                isFlagged = effectiveFlagged(it.id),
                                isTethered = it.id in tetheredIds,
                                // stamp the last-seen-with-signal time; keep the old one for a
                                // bonded device that's in the snapshot but not advertising.
                                seenAtMs = if (it.isPresent) nowMs else (prevSeen[it.id] ?: 0L),
                            )
                        }
                        // Keep favourites and watched ("left-behind") devices on the list even
                        // after they go silent and the scanner drops them — like a pinned
                        // contact. They reappear as *absent* (no live RSSI) instead of vanishing,
                        // so a watched item you've walked away from stays visible and manageable.
                        // Bonded devices the scanner already retains; this covers non-bonded ones
                        // (a tracker on your keys). Re-derived from the live sets each tick, so
                        // un-favouriting / un-watching an absent device drops it next pass.
                        val present = mapped.mapTo(HashSet()) { it.id }
                        val pinned = devices
                            .filter { (it.id in favoriteIds || it.id in tetheredIds || effectiveFlagged(it.id) || it.id in suspectIds) && it.id !in present }
                            .map {
                                it.copy(
                                    rssi = BleDevice.RSSI_UNKNOWN, isConnected = false,
                                    isFavorite = it.id in favoriteIds,
                                    isFlagged = effectiveFlagged(it.id),
                                    isTethered = it.id in tetheredIds,
                                )
                            }
                        devices = mapped + pinned
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
        startProbeWorker()
    }

    // Runs LAST in construction — after every `by mutableStateOf` property above is
    // initialized — because startSync() → SyncManager.start() → localChanged() synchronously
    // reads Compose state through source.settings() (tetherAlert, scanSensitivity). Calling it
    // from the earlier init block crashed on startup with a null State (the delegate for a
    // property declared later in the class body didn't exist yet).
    init {
        if (demoIdentity.isEmpty()) startSync() // no Data Layer noise in demo/screenshots
        // Tapped tracker-alert notifications (cold or warm start) → that device's panel.
        scope.launch {
            DeepLink.suspectId.collect { id ->
                if (id != null) {
                    DeepLink.suspectId.value = null // consume
                    openSuspect(id)
                }
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
        /** Min correlation confidence before a rotation lineage is trusted to carry a
         *  rename/flag (a weak link could move it onto the wrong device). */
        const val IDENTITY_MIN_CONF = 0.6
        /** How often the persisted identity groupings are written (throttle). */
        val IDENTITY_SYNC_INTERVAL = 30.seconds
        /** Demo only: a rich probe result so the device-info card has something to show. */
        val DEMO_PROBE = ProbeResult(
            connectable = true, name = "Pixel Buds Pro", manufacturer = "Google", model = "GA03201",
            firmware = "4.0.1", hardware = "1.2", serial = "GB-PBP-8842",
            structure = "k3f9qz", serviceCount = 7, batteryPct = 82, needsPairing = false,
        )
        /** How often the probe worker looks for a candidate to interrogate. */
        const val PROBE_TICK_MS = 4_000L
        /** Quiet gap after a probe before the next, so the radio is never hammered. */
        const val PROBE_COOLDOWN_MS = 6_000L
    }
}
