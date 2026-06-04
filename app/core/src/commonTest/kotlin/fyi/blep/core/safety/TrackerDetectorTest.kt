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
        // A continuous close presence wearing a new random address each minute.
        feed(d, 0, 6 * 60_000, 20_000) { t ->
            TrackerSighting(address = "anon-${t / 60_000}", rssi = -65, timeMs = t,
                kind = TrackerKind.UNKNOWN, randomAddress = true)
        }
        val a = d.evaluate(6 * 60_000).single()
        assertEquals(Severity.WARN, a.severity)
        assertEquals(TrackerKind.UNKNOWN, a.kind)
        assertEquals(AlertReason.ROTATION, a.reason)
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
        val a = d.evaluate(3_000).single()
        assertEquals(Severity.WARN, a.severity)
        assertEquals(TrackerKind.UNKNOWN, a.kind)
    }
}
