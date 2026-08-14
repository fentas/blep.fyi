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
import kotlin.test.assertNotEquals

/**
 * Scenario simulation for the anti-tracking **detection** layer — the safety analogue
 * of the tracking `simulation_suite`. It plays scripted multi-context "days" through
 * the *real* [SafetyScanner] (so the live detector, the GPS-free backdrop-crowd
 * fingerprint, and the cross-session context-diversity promotion all run for real) and
 * asserts the outcome that would reach the user:
 *
 *  - a tag that **follows you across unrelated contexts** escalates to ALERT;
 *  - a tag that only ever appears in **one routine context** (your commute, your home)
 *    does **not** — the false positive context-diversity exists to kill.
 *
 * Run `make sim-safety` to print the scenario table; run as a normal test to assert it.
 * See docs/detection.md.
 */
class SafetySimulationTest {

    private val hourMs = 3_600_000L

    // Fast thresholds so a single close sighting lands as a WARN within the script.
    private val tuning = TrackerTuning(
        closeDbm = -75, nearbyMs = 1_000, followingMs = 3_000,
        bucketMs = 1_000, rotationMinHandovers = 1, rotationMinCoverage = 0.5,
    )

    /** A separated ("lost") Apple Find My tag — rotates its address, so it's keyed by kind. */
    private fun findMy(addr: String, t: Long) = RawAdvert(
        addr, rssi = -60, timeMs = t, addressType = AddressType.RANDOM,
        manufacturerData = mapOf(0x004C to byteArrayOf(0x12, 0x19, 0x00)),
    )

    /** A stable, non-tracker nearby device — part of the place's backdrop crowd. */
    private fun backdrop(addr: String, t: Long) = RawAdvert(addr, rssi = -60, timeMs = t, addressType = AddressType.PUBLIC)

    private class ScriptedScanner(private val adverts: List<RawAdvert>) : BleScanner {
        override val availability: Flow<ScanAvailability> = flowOf(ScanAvailability.READY)
        override fun devices(includeUnnamed: Boolean, measureConnectedSignal: Boolean): Flow<List<BleDevice>> = flowOf(emptyList())
        override fun rssi(deviceId: String): Flow<Int> = emptyFlow()
        override fun advertisements(): Flow<RawAdvert> = adverts.asFlow()
    }

    /** One outing/visit: the coarse place (null = location-aware off) and the stable
     *  nearby-device backdrop that distinguishes it (home gear vs office gear vs …). */
    private data class Outing(val name: String, val place: String?, val backdrop: List<String>, val hour: Long)

    /** Play a single outing as a fresh live scan over the shared cross-session [history];
     *  returns the worst severity surfaced for the Find My suspect (or null if unflagged). */
    private suspend fun play(history: SafetyHistory, outing: Outing, suspectAddr: String): Severity? {
        val now = outing.hour * hourMs + 1_000
        // Backdrop first (so the context fingerprint is settled), then the suspect last.
        val adverts = outing.backdrop.mapIndexed { i, a -> backdrop(a, i * 10L) } + findMy(suspectAddr, 1_000L)
        val scanner = SafetyScanner(
            ScriptedScanner(adverts), TrackerDetector(tuning), history,
            nowEpochMs = { now }, place = { outing.place },
        )
        return scanner.alerts().toList().last().firstOrNull { it.kind == TrackerKind.FIND_MY }?.severity
    }

    private suspend fun runScenario(outings: List<Outing>, suspectAddr: String = "AA:11"): Pair<Severity?, CrossSession?> {
        val history = SafetyHistory(createKeyValueStore())
        history.setEnabled(true)
        var last: Severity? = null
        outings.forEach { last = play(history, it, suspectAddr) }
        val now = outings.last().hour * hourMs + 2_000
        return last to history.crossSession(TrackerKind.FIND_MY, now)
    }

    @Test
    fun scenario_suite() = runTest {
        // Backdrops: each location has its own stable set of devices (your gear, fixed beacons).
        val home = listOf("home-tv", "home-speaker")
        val train = listOf("train-beacon", "regular-commuter-buds")
        val work = listOf("work-printer", "work-monitor")

        data class Row(val name: String, val expectAlert: Boolean, val severity: Severity?, val cs: CrossSession?)
        val rows = mutableListOf<Row>()

        // A) Planted follower, location OFF — the same tag appears across THREE unrelated
        //    backdrops (home, train, work). Context diversity alone (no GPS) should ALERT.
        run {
            val (sev, cs) = runScenario(
                listOf(
                    Outing("home", null, home, 1),
                    Outing("train", null, train, 2),
                    Outing("work", null, work, 3),
                ),
            )
            rows += Row("planted follower (no GPS, 3 contexts)", true, sev, cs)
        }

        // B) Commute neighbour's tag — only ever in the ONE train context, several days.
        //    Recurs, but it's routine ⇒ must NOT escalate.
        run {
            val (sev, cs) = runScenario(
                (1..4).map { Outing("train-day$it", null, train, it * 24L) },
            )
            rows += Row("commute neighbour (1 context x4)", false, sev, cs)
        }

        // C) Household gadget — only ever at home, many hours. Routine ⇒ must NOT escalate.
        run {
            val (sev, cs) = runScenario(
                (1..4).map { Outing("home-h$it", null, home, it.toLong()) },
            )
            rows += Row("household gadget (home only x4)", false, sev, cs)
        }

        // D) Follower with location ON — seen in two distinct coarse places ⇒ ALERT (multi-place).
        run {
            val (sev, cs) = runScenario(
                listOf(
                    Outing("placeA", "cell-A", emptyList(), 1),
                    Outing("placeB", "cell-B", emptyList(), 2),
                ),
            )
            rows += Row("follower (location on, 2 places)", true, sev, cs)
        }

        // E) Brief stranger — a separated tag seen once. WARN, never promoted.
        run {
            val (sev, cs) = runScenario(listOf(Outing("once", null, home, 1)))
            rows += Row("brief stranger (1 sighting)", false, sev, cs)
        }

        // ---- print the scenario table (captured by `make sim-safety`) ----
        val sb = StringBuilder()
        sb.appendLine("safety detection — scenario suite (Find My suspect)")
        sb.appendLine("%-38s %-8s %4s %4s %4s  %-6s %s".format("scenario", "result", "ctx", "nbl", "hrs", "want", "ok"))
        for (r in rows) {
            val promoted = r.severity == Severity.ALERT
            val ok = if (promoted == r.expectAlert) "✓" else "✗ FAIL"
            sb.appendLine(
                "%-38s %-8s %4d %4d %4d  %-6s %s".format(
                    r.name, r.severity ?: "none",
                    r.cs?.distinctContexts ?: 0, r.cs?.nonBaselineContexts ?: 0, r.cs?.distinctHours ?: 0,
                    if (r.expectAlert) "ALERT" else "—", ok,
                ),
            )
        }
        println(sb.toString())

        // ---- assertions ----
        for (r in rows) {
            if (r.expectAlert) {
                assertEquals(Severity.ALERT, r.severity, "${r.name} should escalate to ALERT")
            } else {
                assertNotEquals(Severity.ALERT, r.severity, "${r.name} should NOT escalate (routine)")
            }
        }
    }
}
