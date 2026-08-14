package fyi.blep.wear

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fyi.blep.core.ble.BleScanner
import fyi.blep.core.ble.DeviceAliases
import fyi.blep.core.ble.DeviceFavorites
import fyi.blep.core.model.BleDevice
import fyi.blep.core.platform.createKeyValueStore
import fyi.blep.core.sync.SyncManager
import fyi.blep.core.sync.SyncMessage
import fyi.blep.core.sync.SyncSettings
import fyi.blep.core.sync.SyncSink
import fyi.blep.core.sync.SyncSource
import fyi.blep.core.sync.Sighting
import fyi.blep.core.sync.createSyncTransport
import fyi.blep.core.tether.DeviceTether
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
import fyi.blep.core.tracking.TrackingStatus
import fyi.blep.core.tracking.signalFreshness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeMark
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
    private val haptic: Haptic = createHaptic(),
) {
    var devices by mutableStateOf<List<BleDevice>>(emptyList())
        private set
    var tracking by mutableStateOf<BleDevice?>(null)
        private set
    var status by mutableStateOf<TrackingStatus?>(null)
        private set
    var spatial by mutableStateOf<SpatialSnapshot?>(null)
        private set
    /** Stabilised turn-by-turn cue (committed direction in clean fields); the UI
     *  localizes it. Structured rather than a pre-formatted String so the watch
     *  can translate it the same way the phone does. */
    var guidance by mutableStateOf<GuidanceLine?>(null)
        private set
    /** Live signal — the watch shows the dB and a plain "it's right here" at point-blank. */
    var lastRssi by mutableStateOf<Int?>(null)
        private set
    /** No fresh RSSI recently (out of range / off). */
    var signalLost by mutableStateOf(false)
        private set
    /** Ids the user tethered (leave/return alert). Drives the list indicator. */
    var tetheredIds by mutableStateOf<Set<String>>(emptySet())
        private set

    private val tether = DeviceTether(createKeyValueStore())
    // Synced from the phone so the watch shows your names + favourites too.
    private val aliasStore = DeviceAliases(createKeyValueStore())
    private val favStore = DeviceFavorites(createKeyValueStore())
    private var sync: SyncManager? = null
    private var lastRssiMark: TimeMark? = null
    private var trackStartMark: TimeMark? = null
    private var arrived = false
    private var scanJob: Job? = null
    private var trackJob: Job? = null
    private var motionJob: Job? = null
    private var hapticJob: Job? = null
    private val spatialTuning = SpatialTuning()
    private val spatialTracker = SpatialTracker(spatialTuning)
    private val guidanceStabilizer = GuidanceStabilizer()
    private var latestMotion: MotionSample? = null

    init {
        tetheredIds = tether.ids()
        startSync()
        startDiscovery()
    }

    /** Overlay the phone-synced name + favourite onto a scanned device. */
    private fun overlay(d: BleDevice): BleDevice =
        d.copy(alias = aliasStore.of(d.id) ?: d.alias, isFavorite = favStore.isFavorite(d.id))

    private fun startSync() {
        val source = object : SyncSource {
            override fun favorites() = favStore.ids()
            override fun tethered() = tether.ids()
            override fun muted() = emptySet<String>()
            override fun aliases() = aliasStore.all()
            override fun settings() = emptyMap<String, String>()
        }
        val sink = object : SyncSink {
            override fun applyFavorites(ids: Set<String>) { favStore.replace(ids); reoverlay() }
            override fun applyTethered(ids: Set<String>) { tether.replace(ids); tetheredIds = ids; reoverlay() }
            override fun applyMuted(ids: Set<String>) {}
            override fun applyAliases(map: Map<String, String>) { aliasStore.replaceAll(map); reoverlay() }
            override fun applySetting(name: String, value: String) {}
            override fun onMessage(msg: SyncMessage) {} // PhoneTetherService handles relayed alerts
        }
        sync = SyncManager(createSyncTransport(), SyncSettings(createKeyValueStore()), source, sink, scope).also { it.start() }
        // Scan fusion: relay what the watch sees to the phone (opt-in; no-op unless on).
        scope.launch {
            while (isActive) {
                delay(5000)
                sync?.sendSightings(devices.map { Sighting(it.id, it.rssi, it.displayName) })
            }
        }
    }

    /** Re-apply name/favourite overlays to the current list after a sync. */
    private fun reoverlay() { devices = devices.map(::overlay) }

    fun isTethered(id: String): Boolean = id in tetheredIds

    /** Demo only: star one device and watch another, so the discovery filters have
     *  something to filter. In real use both sets arrive from the phone over the Data
     *  Layer, which a scripted demo has no way to reach. Mirrors the phone's
     *  BlepController.seedDemoIdentity. */
    fun seedDemo() {
        favStore.replace(setOf("keys"))
        tether.replace(setOf("wallet"))
        tetheredIds = tether.ids()
        reoverlay()
    }

    /** Toggle a leave/return ("left behind") alert on a device. The periodic safety
     *  worker watches the tethered set and notifies when one leaves/returns range. */
    fun toggleTether(device: BleDevice) {
        tether.toggle(device.id)
        tetheredIds = tether.ids()
        haptic.success()
        sync?.localChanged()
    }

    fun startDiscovery() {
        trackJob?.cancel(); trackJob = null
        motionJob?.cancel(); motionJob = null
        hapticJob?.cancel(); hapticJob = null
        tracking = null; status = null
        spatial = null; guidance = null; latestMotion = null
        lastRssi = null; lastRssiMark = null; signalLost = false; arrived = false
        spatialTracker.reset(); guidanceStabilizer.reset()
        scanJob?.cancel()
        scanJob = scope.launch {
            // Self-healing: scanning throws until the BLE permission is granted.
            while (isActive) {
                try {
                    scanner.devices(includeUnnamed = false).collect { list -> devices = list.map(::overlay) }
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
        spatialTracker.reset(); guidanceStabilizer.reset(); spatial = null; guidance = null; latestMotion = null
        lastRssi = null; lastRssiMark = null; signalLost = false; arrived = false
        trackStartMark = TimeSource.Monotonic.markNow()

        motionJob = scope.launch {
            try {
                motionProvider.motion().collect { sample ->
                    latestMotion = sample
                    val snap = spatialTracker.update((lastRssi ?: -100).toDouble(), sample)
                    spatial = snap
                    guidance = guidanceStabilizer.guideLine(snap, spatialTuning)
                    val fresh = signalFreshness(
                        sinceLastRssiMs = lastRssiMark?.elapsedNow()?.inWholeMilliseconds,
                        sinceStartMs = trackStartMark?.elapsedNow()?.inWholeMilliseconds,
                    )
                    signalLost = fresh.lost
                }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) { /* no sensors — RSSI-only */ }
        }

        hapticJob = scope.launch {
            while (isActive) {
                val p = status?.proximity ?: 0f
                val interval = HapticCadence.intervalMs(p)
                if (interval == null) delay(250) else { haptic.pulse(p); delay(interval) }
            }
        }

        trackJob = scope.launch {
            val clock = TimeSource.Monotonic.markNow()
            try {
                scanner.rssi(device.id).collect { rssi ->
                    lastRssi = rssi
                    lastRssiMark = TimeSource.Monotonic.markNow()
                    // Discount RSSI swings caused by rotating/tilting the watch.
                    if (latestMotion?.reorienting != true) {
                        val st = session.onSample(rssi, clock.elapsedNow().inWholeMilliseconds)
                        status = st
                        if (st.phase == TrackingPhase.COMPLETE && !arrived) {
                            arrived = true
                            haptic.success()
                            // Keep ranging (trackJob) alive so the live dB still drives the
                            // point-blank "it's right here" pinpoint on this screen; only
                            // the Geiger pulse + motion stop.
                            motionJob?.cancel(); hapticJob?.cancel()
                        }
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
