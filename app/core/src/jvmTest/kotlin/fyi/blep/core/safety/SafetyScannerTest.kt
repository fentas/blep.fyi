package fyi.blep.core.safety

import fyi.blep.core.ble.BleScanner
import fyi.blep.core.ble.IdentityStore
import fyi.blep.core.ble.ProbeResult
import fyi.blep.core.ble.ScanAvailability
import fyi.blep.core.model.BleDevice
import fyi.blep.core.platform.createKeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end scenarios for the whole safety pipeline (advert → classify → detect →
 * cross-session enrich → mute filter), the analogue of the tracking sim suite: feed
 * a scripted advertisement stream into a real [SafetyScanner] and assert the alert
 * that reaches the UI.
 */
class SafetyScannerTest {

    private val hourMs = 3_600_000L

    // Fast thresholds so durations stay in the milliseconds the scripts use.
    private val tuning = TrackerTuning(
        closeDbm = -75,
        nearbyMs = 1_000,
        followingMs = 3_000,
        bucketMs = 1_000,
        rotationMinHandovers = 1,
        rotationMinCoverage = 0.5,
    )

    /** Emits a fixed advertisement script, then completes (so `alerts()` completes). */
    private class ScriptedScanner(private val adverts: List<RawAdvert>) : BleScanner {
        override val availability: Flow<ScanAvailability> = flowOf(ScanAvailability.READY)
        override fun devices(includeUnnamed: Boolean, measureConnectedSignal: Boolean): Flow<List<BleDevice>> = flowOf(emptyList())
        override fun rssi(deviceId: String): Flow<Int> = emptyFlow()
        override fun advertisements(): Flow<RawAdvert> = adverts.asFlow()
    }

    private fun findMy(addr: String, rssi: Int, t: Long) = RawAdvert(
        address = addr, rssi = rssi, timeMs = t, addressType = AddressType.RANDOM,
        manufacturerData = mapOf(0x004C to byteArrayOf(0x12, 0x00)),
    )

    private fun tile(addr: String, rssi: Int, t: Long) =
        RawAdvert(addr, rssi, t, AddressType.RANDOM, serviceUuids = listOf("feed"))

    private fun anon(addr: String, rssi: Int, t: Long) =
        RawAdvert(addr, rssi, t, AddressType.RANDOM)

    private fun scanner(adverts: List<RawAdvert>) = ScriptedScanner(adverts)

    private suspend fun lastAlerts(s: SafetyScanner) = s.alerts().toList().last()

    @Test
    fun following_airtag_escalates_to_alert() = runTest {
        // A Find My tag close for 4 s (> followingMs) — the classic "it's with you".
        val adverts = (0..4).map { findMy("AA:11", -60, it * 1_000L) }
        val out = lastAlerts(SafetyScanner(scanner(adverts), TrackerDetector(tuning)))
        assertEquals(1, out.size)
        assertEquals(TrackerKind.FIND_MY, out[0].kind)
        assertEquals(Severity.ALERT, out[0].severity)
        assertEquals("AA:11", out[0].trackingAddress)
    }

    @Test
    fun brief_separated_tile_warns() = runTest {
        // A separated Tile seen once close — WARN, with an address to hand the finder.
        val out = lastAlerts(SafetyScanner(scanner(listOf(tile("BB:22", -55, 0))), TrackerDetector(tuning)))
        assertEquals(TrackerKind.TILE, out[0].kind)
        assertEquals(Severity.WARN, out[0].severity)
    }

    @Test
    fun rotating_anonymous_churn_warns() = runTest {
        // One device wearing two ids: "01" advertises close for 20 s, goes quiet, and
        // "02" takes over at the same range. The correlator only attributes the handover
        // once "01" has been silent past its stale window, so the script has to run long
        // enough for that to resolve — a couple of one-off addresses is not evidence.
        val adverts = (0..20).map { anon("01", -60, it * 1_000L) } +
            (22..60).map { anon("02", -60, it * 1_000L) }
        val out = lastAlerts(SafetyScanner(scanner(adverts), TrackerDetector(tuning)))
        assertEquals(TrackerKind.UNKNOWN, out[0].kind)
        assertEquals(Severity.WARN, out[0].severity)
        assertEquals(AlertReason.ROTATION, out[0].reason)
    }

    @Test
    fun a_crowd_of_uncorrelated_strangers_never_warns() = runTest {
        // Walking through town: a steady churn of close private addresses, each seen
        // briefly and never linked to another. High coverage, plenty of distinct ids —
        // the shape that used to fire — but it is a crowd, not one device following you.
        val adverts = (0..60).map { anon("stranger-$it", -60, it * 1_000L) }
        assertTrue(lastAlerts(SafetyScanner(scanner(adverts), TrackerDetector(tuning))).isEmpty())
    }

    @Test
    fun far_device_never_alerts() = runTest {
        val adverts = listOf(
            RawAdvert("CC:33", -95, 0, AddressType.PUBLIC),
            RawAdvert("CC:33", -94, 1_000, AddressType.PUBLIC),
        )
        assertTrue(lastAlerts(SafetyScanner(scanner(adverts), TrackerDetector(tuning))).isEmpty())
    }

    @Test
    fun marking_mine_silences_that_address() = runTest {
        val adverts = (0..4).map { findMy("AA:11", -60, it * 1_000L) }
        val s = SafetyScanner(scanner(adverts), TrackerDetector(tuning))
        s.mute("AA:11")
        assertTrue(lastAlerts(s).isEmpty())
    }

    @Test
    fun unmute_re_flags_a_previously_muted_address() = runTest {
        val adverts = (0..4).map { findMy("AA:11", -60, it * 1_000L) }
        val s = SafetyScanner(scanner(adverts), TrackerDetector(tuning))
        s.mute("AA:11")
        s.unmute("AA:11") // changed my mind / accidental tap
        val out = lastAlerts(s)
        assertEquals(1, out.size)
        assertEquals("AA:11", out[0].trackingAddress)
    }

    @Test
    fun muting_one_tag_still_flags_a_different_one() = runTest {
        val adverts = (0..4).flatMap {
            listOf(findMy("AA:MINE", -60, it * 1_000L), findMy("AA:THREAT", -58, it * 1_000L))
        }
        val s = SafetyScanner(scanner(adverts), TrackerDetector(tuning))
        s.mute("AA:MINE")
        val out = lastAlerts(s)
        // The mine one is gone; the other Find My address still drives an alert.
        assertEquals(1, out.size)
        assertEquals("AA:THREAT", out[0].trackingAddress)
    }

    @Test
    fun cross_session_history_promotes_a_brief_sighting() = runTest {
        val history = SafetyHistory(createKeyValueStore())
        history.setEnabled(true)
        // Seen close across three earlier hours today…
        history.record(TrackerKind.FIND_MY, 1 * hourMs)
        history.record(TrackerKind.FIND_MY, 2 * hourMs)
        history.record(TrackerKind.FIND_MY, 3 * hourMs)
        val now = 3 * hourMs + 5_000
        // …now a brief fresh sighting that alone would only be a WARN.
        val adverts = listOf(findMy("AA:11", -60, 0), findMy("AA:11", -60, 1_000))
        val s = SafetyScanner(scanner(adverts), TrackerDetector(tuning), history, nowEpochMs = { now })
        val out = lastAlerts(s)
        assertEquals(Severity.ALERT, out[0].severity)
        assertEquals(3, out[0].crossSessionHours)
    }

    @Test
    fun history_disabled_does_not_promote() = runTest {
        val history = SafetyHistory(createKeyValueStore())
        history.setEnabled(false)
        history.setEnabled(true) // seed via enabled, then disable
        history.record(TrackerKind.FIND_MY, 1 * hourMs)
        history.record(TrackerKind.FIND_MY, 2 * hourMs)
        history.record(TrackerKind.FIND_MY, 3 * hourMs)
        history.setEnabled(false) // off ⇒ no enrichment (and log cleared)
        val adverts = listOf(findMy("AA:11", -60, 0), findMy("AA:11", -60, 1_000))
        val s = SafetyScanner(scanner(adverts), TrackerDetector(tuning), history, nowEpochMs = { 3 * hourMs + 5_000 })
        val out = lastAlerts(s)
        assertEquals(Severity.WARN, out[0].severity) // not promoted
    }

    @Test
    fun routine_single_context_recurrence_is_not_promoted() = runTest {
        val history = SafetyHistory(createKeyValueStore())
        history.setEnabled(true)
        // Seen close across 4 separate hours, but always in the SAME context (your commute)
        // — the classic false positive (same train, same crowd, every day).
        (1..4).forEach { history.record(TrackerKind.FIND_MY, it * hourMs, context = "commute") }
        val now = 4 * hourMs + 5_000
        val adverts = listOf(findMy("AA:11", -60, 0), findMy("AA:11", -60, 1_000))
        val s = SafetyScanner(scanner(adverts), TrackerDetector(tuning), history, nowEpochMs = { now })
        val out = lastAlerts(s)
        assertEquals(Severity.WARN, out[0].severity) // recurs, but routine ⇒ stays a WARN
    }

    @Test
    fun diverse_contexts_promote_a_brief_sighting() = runTest {
        val history = SafetyHistory(createKeyValueStore())
        history.setEnabled(true)
        // Seen close across THREE unrelated contexts (home baseline, train, work) —
        // followed you as your surroundings changed.
        history.record(TrackerKind.FIND_MY, 1 * hourMs, context = "home")
        history.record(TrackerKind.FIND_MY, 2 * hourMs, context = "home")
        history.record(TrackerKind.FIND_MY, 3 * hourMs, context = "train")
        history.record(TrackerKind.FIND_MY, 4 * hourMs, context = "work")
        val now = 4 * hourMs + 5_000
        val adverts = listOf(findMy("AA:11", -60, 0), findMy("AA:11", -60, 1_000))
        val s = SafetyScanner(scanner(adverts), TrackerDetector(tuning), history, nowEpochMs = { now })
        val out = lastAlerts(s)
        assertEquals(Severity.ALERT, out[0].severity)
    }

    @Test
    fun multiple_places_promote_a_brief_sighting() = runTest {
        val history = SafetyHistory(createKeyValueStore())
        history.setEnabled(true)
        // Location-aware: seen in two distinct coarse places — moved with you.
        history.record(TrackerKind.FIND_MY, 1 * hourMs, place = "cellA")
        history.record(TrackerKind.FIND_MY, 2 * hourMs, place = "cellB")
        val now = 2 * hourMs + 5_000
        val adverts = listOf(findMy("AA:11", -60, 0), findMy("AA:11", -60, 1_000))
        val s = SafetyScanner(scanner(adverts), TrackerDetector(tuning), history, nowEpochMs = { now })
        val out = lastAlerts(s)
        assertEquals(Severity.ALERT, out[0].severity)
        assertEquals(2, out[0].crossSessionPlaces)
    }

    // ── persistent-identity follower (the interval-scan / wear catch) ──────────────
    private val followerWindow = 90L * 60_000

    @Test
    fun persistent_alert_fires_for_a_re_linked_rotating_identity() {
        var clock = 1_000_000L
        val store = IdentityStore(createKeyValueStore(), now = { clock })
        // Check 1: device A probed, learns a serial.
        store.seen(setOf("A"))
        store.recordProbe("A", ProbeResult(connectable = true, name = "Buds", serial = "S"))
        // Check 2 (30 min later): it has rotated to B; the serial re-links it to A.
        clock += 30 * 60_000
        store.seen(setOf("B"))
        store.recordProbe("B", ProbeResult(connectable = true, name = "Buds", serial = "S"))
        // 100 min after first sight, B is still present — the interval scan now "sees" the
        // same physical device persisting across its rotations.
        val now = 1_000_000L + 100 * 60_000
        val alerts = persistentTrackerAlerts(store, mapOf("B" to -50), now, followerWindow, emptySet(), emptySet())
        assertEquals(1, alerts.size)
        assertEquals(AlertReason.PERSISTENT, alerts[0].reason)
        assertEquals("B", alerts[0].trackingAddress)
        assertEquals("Buds", alerts[0].label)
        assertTrue(alerts[0].durationMs >= followerWindow)
    }

    @Test
    fun no_persistent_alert_without_rotation_age_or_when_muted() {
        var clock = 1_000_000L
        val store = IdentityStore(createKeyValueStore(), now = { clock })
        val old = 1_000_000L + 100 * 60_000
        // A single, never-rotated address — even if old — isn't "persistent" (likely your own).
        store.seen(setOf("X"))
        assertTrue(persistentTrackerAlerts(store, mapOf("X" to -50), old, followerWindow, emptySet(), emptySet()).isEmpty())
        // A re-linked rotating identity, but younger than the window.
        store.seen(setOf("A")); store.recordProbe("A", ProbeResult(connectable = true, serial = "S2"))
        clock += 5 * 60_000
        store.seen(setOf("B")); store.recordProbe("B", ProbeResult(connectable = true, serial = "S2"))
        val soon = 1_000_000L + 30 * 60_000
        assertTrue(persistentTrackerAlerts(store, mapOf("B" to -50), soon, followerWindow, emptySet(), emptySet()).isEmpty())
        // Old + rotated, but muted or already in the base alerts ⇒ suppressed.
        assertTrue(persistentTrackerAlerts(store, mapOf("B" to -50), old, followerWindow, setOf("B"), emptySet()).isEmpty())
        assertTrue(persistentTrackerAlerts(store, mapOf("B" to -50), old, followerWindow, emptySet(), setOf("B")).isEmpty())
    }

    @Test
    fun a_persistent_alert_surfaces_through_the_whole_flow() = runTest {
        // Exercises the channelFlow path (not just the pure helper): close-present
        // tracking + the persistent emission, with a pre-known rotating identity.
        var clock = 10_000_000L
        val store = IdentityStore(createKeyValueStore(), now = { clock })
        store.seen(setOf("A")); store.recordProbe("A", ProbeResult(connectable = true, name = "Buds", serial = "S"))
        clock += 30 * 60_000
        store.seen(setOf("B")); store.recordProbe("B", ProbeResult(connectable = true, name = "Buds", serial = "S"))
        val now = 10_000_000L + 120 * 60_000 // 2 h after first sight; B is close + present
        val s = SafetyScanner(
            scanner(listOf(anon("B", -60, 0), anon("B", -60, 1_000))),
            TrackerDetector(tuning), identityStore = store, nowEpochMs = { now },
        )
        val persistent = lastAlerts(s).firstOrNull { it.reason == AlertReason.PERSISTENT }
        assertNotNull(persistent)
        assertEquals("B", persistent.trackingAddress)
        assertEquals("Buds", persistent.label)
    }
}
