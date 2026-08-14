package fyi.blep.core.safety

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackerDetectorTest {

    private fun feed(d: TrackerDetector, from: Long, to: Long, stepMs: Long, make: (Long) -> TrackerSighting) {
        var t = from
        while (t <= to) { d.observe(make(t)); t += stepMs }
    }

    /** A correlator that puts *every* address on one confidently-linked lineage — the
     *  "we watched this device hand over" case the rotation heuristic now requires. */
    private fun oneDevice(addressesSeen: Int = 4, rotations: Int = 3, confidence: Double = 0.9, contested: Boolean = false):
        (String) -> Lineage? = { Lineage("dev", addressesSeen, rotations, confidence, contested) }

    /** A correlator that has linked nothing — the crowd case (every address is its own
     *  device, no handover ever observed). */
    private val strangers: (String) -> Lineage? = { addr -> Lineage(addr, 1, 0, 1.0, false) }

    @Test
    fun a_separated_findmy_close_for_minutes_escalates_to_following() {
        val d = TrackerDetector()
        // A separated AirTag close by, identity rotating, every 30 s for 6 minutes.
        feed(d, 0, 6 * 60_000, 30_000) { t ->
            TrackerSighting(address = "rot-${t / 120_000}", rssi = -60, timeMs = t,
                kind = TrackerKind.FIND_MY, separated = true, randomAddress = true)
        }
        val a = d.evaluate(6 * 60_000).first { it.kind == TrackerKind.FIND_MY }
        assertEquals(Severity.ALERT, a.severity)
        assertEquals(AlertReason.FOLLOWING, a.reason)
        assertTrue(a.trackingAddress != null) // hand-off to the finder
    }

    @Test
    fun a_brief_separated_tracker_is_a_warning_not_an_alert() {
        val d = TrackerDetector()
        d.observe(TrackerSighting("x", rssi = -55, timeMs = 1000, kind = TrackerKind.TILE, separated = true))
        val a = d.evaluate(1500).single()
        assertEquals(Severity.WARN, a.severity)
        assertEquals(TrackerKind.TILE, a.kind)
    }

    @Test
    fun rotating_anonymous_churn_that_stays_close_is_flagged() {
        val d = TrackerDetector()
        // A continuous close presence wearing a new random address each minute, and the
        // correlator linked those ids into one device (it watched the handovers).
        feed(d, 0, 6 * 60_000, 20_000) { t ->
            TrackerSighting(address = "anon-${t / 60_000}", rssi = -65, timeMs = t,
                kind = TrackerKind.UNKNOWN, randomAddress = true)
        }
        val a = d.evaluate(6 * 60_000, oneDevice(addressesSeen = 7, rotations = 6)).single()
        assertEquals(Severity.WARN, a.severity)
        assertEquals(TrackerKind.UNKNOWN, a.kind)
        assertEquals(AlertReason.ROTATION, a.reason)
        assertEquals(7, a.distinctCount) // the device's own id count, not the crowd's
    }

    @Test
    fun a_crowd_of_uncorrelated_rotating_strangers_is_not_a_tracker() {
        val d = TrackerDetector()
        // Walking through town: a churn of close private addresses with high coverage —
        // the exact shape the old pooled count flagged — but the correlator never linked
        // any two of them, so this is a crowd, not one device shadowing you.
        feed(d, 0, 6 * 60_000, 20_000) { t ->
            TrackerSighting(address = "stranger-${t / 20_000}", rssi = -65, timeMs = t,
                kind = TrackerKind.UNKNOWN, randomAddress = true)
        }
        assertTrue(d.evaluate(6 * 60_000, strangers).isEmpty())
    }

    @Test
    fun rotation_needs_a_correlator_at_all() {
        val d = TrackerDetector()
        // With no correlator supplied (an interval scanner), rotation stays silent
        // rather than guessing from the surrounding population.
        feed(d, 0, 6 * 60_000, 20_000) { t ->
            TrackerSighting(address = "anon-${t / 60_000}", rssi = -65, timeMs = t,
                kind = TrackerKind.UNKNOWN, randomAddress = true)
        }
        assertTrue(d.evaluate(6 * 60_000).isEmpty())
    }

    @Test
    fun a_contested_lineage_does_not_alert() {
        val d = TrackerDetector()
        // An unresolved fork between two candidate devices — naming one would name the
        // wrong device, so hold until the correlator settles it.
        feed(d, 0, 6 * 60_000, 20_000) { t ->
            TrackerSighting(address = "anon-${t / 60_000}", rssi = -65, timeMs = t,
                kind = TrackerKind.UNKNOWN, randomAddress = true)
        }
        assertTrue(d.evaluate(6 * 60_000, oneDevice(contested = true)).isEmpty())
    }

    @Test
    fun a_low_confidence_lineage_does_not_alert() {
        val d = TrackerDetector()
        feed(d, 0, 6 * 60_000, 20_000) { t ->
            TrackerSighting(address = "anon-${t / 60_000}", rssi = -65, timeMs = t,
                kind = TrackerKind.UNKNOWN, randomAddress = true)
        }
        // Linked, but the hops were weak guesses — below rotationMinConfidence.
        assertTrue(d.evaluate(6 * 60_000, oneDevice(confidence = 0.2)).isEmpty())
    }

    @Test
    fun one_stable_nearby_device_is_not_a_tracker() {
        val d = TrackerDetector()
        // Your own earbuds: one stable address, close, not separated, not random.
        feed(d, 0, 6 * 60_000, 30_000) { t ->
            TrackerSighting(address = "buds", rssi = -50, timeMs = t, randomAddress = false)
        }
        assertTrue(d.evaluate(6 * 60_000).isEmpty())
    }

    @Test
    fun far_devices_are_ignored() {
        val d = TrackerDetector()
        feed(d, 0, 6 * 60_000, 20_000) { t ->
            TrackerSighting(address = "far-${t / 60_000}", rssi = -92, timeMs = t,
                kind = TrackerKind.UNKNOWN, randomAddress = true)
        }
        assertTrue(d.evaluate(6 * 60_000).isEmpty())
    }

    @Test
    fun a_few_distinct_passers_by_do_not_trip_the_rotation_heuristic() {
        val d = TrackerDetector()
        // Two different people's phones pass close, briefly — not a continuous shadow.
        d.observe(TrackerSighting("p1", rssi = -60, timeMs = 1000, randomAddress = true))
        d.observe(TrackerSighting("p2", rssi = -60, timeMs = 2000, randomAddress = true))
        assertTrue(d.evaluate(3000).isEmpty())
    }

    @Test
    fun a_separated_findmy_present_past_nearby_but_under_following_is_a_warning() {
        val d = TrackerDetector()
        // A non-rotating separated tag, close for ~45 s — past `nearbyMs`, under
        // `followingMs`: the duration trigger (not just `separated`) should WARN.
        feed(d, 0, 45_000, 5_000) { t ->
            TrackerSighting("stable", rssi = -60, timeMs = t, kind = TrackerKind.FIND_MY, separated = false)
        }
        val a = d.evaluate(45_000).single()
        assertEquals(Severity.WARN, a.severity)
        assertEquals(TrackerKind.FIND_MY, a.kind)
    }

    @Test
    fun churn_of_non_private_addresses_does_not_trip_the_rotation_heuristic() {
        val d = TrackerDetector()
        // Many distinct close UNKNOWN addresses, but PUBLIC (randomAddress=false) —
        // the rotation tell keys on rotating *private* MACs, so this must not fire.
        feed(d, 0, 6 * 60_000, 20_000) { t ->
            TrackerSighting("pub-${t / 60_000}", rssi = -65, timeMs = t,
                kind = TrackerKind.UNKNOWN, randomAddress = false)
        }
        assertTrue(d.evaluate(6 * 60_000).isEmpty())
    }

    @Test
    fun rapid_rotation_inside_a_single_bucket_still_trips() {
        // Distinct private addresses churning fast (sub-bucket span) — coverage's
        // short-span path should treat it as fully present, not divide-by-near-zero.
        val d = TrackerDetector(TrackerTuning(bucketMs = 60_000))
        listOf(0L, 1_000, 2_000, 3_000).forEach { t ->
            d.observe(TrackerSighting("anon-$t", rssi = -60, timeMs = t,
                kind = TrackerKind.UNKNOWN, randomAddress = true))
        }
        val a = d.evaluate(3_000, oneDevice()).single()
        assertEquals(Severity.WARN, a.severity)
        assertEquals(TrackerKind.UNKNOWN, a.kind)
    }
}
