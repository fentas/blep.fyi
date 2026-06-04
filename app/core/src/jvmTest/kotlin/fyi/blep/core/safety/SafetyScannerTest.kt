package fyi.blep.core.safety

import fyi.blep.core.ble.BleScanner
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
        rotationMinDistinct = 3,
        rotationMinCoverage = 0.5,
    )

    /** Emits a fixed advertisement script, then completes (so `alerts()` completes). */
    private class ScriptedScanner(private val adverts: List<RawAdvert>) : BleScanner {
        override val availability: Flow<ScanAvailability> = flowOf(ScanAvailability.READY)
        override fun devices(includeUnnamed: Boolean): Flow<List<BleDevice>> = flowOf(emptyList())
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
        // Three short-lived random addresses, all close, back to back — the rotation tell.
        val adverts = listOf(anon("01", -60, 0), anon("02", -60, 1_100), anon("03", -60, 2_200))
        val out = lastAlerts(SafetyScanner(scanner(adverts), TrackerDetector(tuning)))
        assertEquals(TrackerKind.UNKNOWN, out[0].kind)
        assertEquals(Severity.WARN, out[0].severity)
        assertTrue(out[0].title.contains("reappearing"))
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
        assertTrue(out[0].detail.contains("separate hours"))
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
}
