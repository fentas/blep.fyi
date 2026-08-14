package fyi.blep.wear

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fyi.blep.core.ble.BleScanner
import fyi.blep.core.ble.DeviceAliases
import fyi.blep.core.ble.DeviceFavorites
import fyi.blep.core.ble.ProbeResult
import fyi.blep.core.ble.RotationStats
import fyi.blep.core.ble.RotationTracker
import fyi.blep.core.ble.WornId
import fyi.blep.core.safety.payloadFingerprint
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

// Shared with the phone's BlepController — the key it publishes the unnamed toggle under.
private const val SETTING_SHOW_UNNAMED = "showUnnamed"

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
    /** What the paired phone hears (scan fusion), folded into the list beside our own
     *  readings. Empty when the phone isn't reporting or fusion is off. */
    var remoteSightings by mutableStateOf<List<Sighting>>(emptyList())
        private set

    /** Whether unnamed devices are listed. The watch has no toggle of its own — it
     *  follows the phone's over the settings sync, which is why the watch used to show
     *  far fewer devices than the phone with no way to explain the difference. */
    var includeUnnamed by mutableStateOf(false)
        private set

    /** Ids the user tethered (leave/return alert). Drives the list indicator. */
    var tetheredIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Rotation lineage for the device whose detail page is open, or null when it has
     *  never been correlated. Only meaningful while [watchRotation] is running. */
    var detailRotation by mutableStateOf<RotationStats?>(null)
        private set
    /** What a GATT probe learned about the open device (maker/model/firmware/battery),
     *  or null until one is asked for. */
    var detailProbe by mutableStateOf<ProbeResult?>(null)
        private set
    /** True while a probe is in flight, so the button can say so. */
    var probing by mutableStateOf(false)
        private set

    private val tether = DeviceTether(createKeyValueStore())
    // Synced from the phone so the watch shows your names + favourites too.
    private val aliasStore = DeviceAliases(createKeyValueStore())
    private val favStore = DeviceFavorites(createKeyValueStore())
    // Correlates rotating addresses into one device, exactly as the phone does. Fed only
    // while a detail page is open: it needs the continuous advertisement stream, and
    // holding the radio open for that all the time is a battery cost the watch shouldn't
    // pay just to keep a history nobody is looking at.
    private val rotationTracker = RotationTracker()
    private var rotationJob: Job? = null
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
            override fun applySetting(name: String, value: String) {
                if (name == SETTING_SHOW_UNNAMED) {
                    value.toBooleanStrictOrNull()?.let {
                        includeUnnamed = it
                    }
                }
            }
            // PhoneTetherService handles relayed alerts; scan fusion lands here. The
            // watch has been sending its sightings to the phone all along while throwing
            // away everything coming back — which is the direction that actually helps,
            // since the phone has the better radio and the longer list.
            override fun onMessage(msg: SyncMessage) {
                if (msg is SyncMessage.Sightings) remoteSightings = msg.devices
            }
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

    /**
     * What the list shows. The scan deliberately asks for *everything*: DeviceTable
     * filters on the advertised name, inside the scanner, before [overlay] has had a
     * chance to attach the phone-synced alias or favourite flag. Filtering there meant a
     * device you had renamed — or starred — was discarded before the watch could know it
     * had a label, which is why the watch listed fewer named devices than the phone.
     *
     * So the filter runs here instead, and treats a device as named if it carries any
     * human label at all, whether that came from the advert or from you.
     */
    val visibleDevices: List<BleDevice>
        get() = fuse(devices).filter {
            it.isFavorite || isTethered(it.id) || includeUnnamed || it.displayName != it.id
        }

    /** Fold in the phone's readings. Its value sits beside ours as
     *  [BleDevice.remoteRssi] rather than replacing or averaging it, and a device only
     *  the phone can hear joins the list — the watch's radio is the weaker of the two,
     *  so this is where fusion earns its keep. */
    private fun fuse(list: List<BleDevice>): List<BleDevice> {
        if (remoteSightings.isEmpty()) return list
        val remote = remoteSightings.associateBy { it.id }
        val merged = list.map { d -> remote[d.id]?.let { d.copy(remoteRssi = it.rssi) } ?: d }
        val known = list.mapTo(HashSet()) { it.id }
        return merged + remoteSightings.filter { it.id !in known }.map {
            overlay(
                BleDevice(
                    id = it.id,
                    name = it.name.takeIf { n -> n.isNotBlank() && n != it.id },
                    rssi = BleDevice.RSSI_UNKNOWN,
                    remoteRssi = it.rssi,
                    isTethered = isTethered(it.id),
                ),
            )
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
        // A correlated lineage takes minutes of real adverts to build (an id must go
        // quiet and a successor appear), which a scripted screenshot run has no time for.
        // Seeded directly, exactly as the phone seeds its detail page.
        val m = 60_000L
        demoRotation = RotationStats(
            address = "C4:2A:1B:90:EF:01", rssi = -58, firstSeenMs = 0, lastSeenMs = 0,
            rotations = 3, addressesSeen = 4, confidence = 0.92,
            history = listOf(
                WornId("C4:2A:1B:11:00:01", 0, 16 * m, 0.90),
                WornId("C4:2A:1B:35:00:02", 0, 15 * m, 0.94),
                WornId("C4:2A:1B:7E:00:03", 0, 14 * m, 0.91),
                WornId("C4:2A:1B:90:EF:01", 0, 12 * m, 1.0, current = true),
            ),
        )
    }

    /** Demo only — stands in for a lineage the live correlator can't build in seconds. */
    private var demoRotation: RotationStats? = null

    /**
     * Start correlating rotating addresses while a detail page is open, so it can show
     * the same id history the phone does. Stops on [stopWatchingRotation]; the stream is
     * not held open outside the page.
     */
    fun watchRotation(deviceId: String) {
        rotationJob?.cancel()
        detailRotation = demoRotation
        detailProbe = null
        rotationJob = scope.launch {
            try {
                scanner.advertisements().collect { adv ->
                    rotationTracker.observe(adv.address, adv.rssi, adv.timeMs, payloadFingerprint(adv))
                    // Follow the id forward: if it rotates while the page is open, the
                    // page should track the device, not the address it arrived with.
                    val live = rotationTracker.currentAddressFor(deviceId) ?: deviceId
                    detailRotation = rotationTracker.statsFor(live) ?: demoRotation
                }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                // No advert stream (permission, radio off) — the page just shows no history.
            }
        }
    }

    fun stopWatchingRotation() {
        rotationJob?.cancel(); rotationJob = null
        detailRotation = null
        detailProbe = null
        probing = false
    }

    /**
     * One short GATT connect to learn a device's identity (maker/model/firmware/battery).
     * Explicitly user-triggered, never automatic: a connect costs radio time and the
     * phone gates the same probe behind a dwell threshold for that reason.
     */
    fun probeDevice(deviceId: String) {
        if (probing) return
        probing = true
        scope.launch {
            try {
                detailProbe = scanner.probe(deviceId)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                detailProbe = ProbeResult(connectable = false)
            } finally {
                probing = false
            }
        }
    }

    /** Star/unstar a device. Favourites are shared with the phone over the Data Layer,
     *  so this propagates in both directions like a rename does. */
    fun toggleFavorite(device: BleDevice) {
        favStore.toggle(device.id)
        reoverlay()
        haptic.success()
        sync?.localChanged()
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
                    scanner.devices(includeUnnamed = true, measureConnectedSignal = true).collect { list -> devices = list.map(::overlay) }
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
