package fyi.blep.core.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RotationTrackerTest {

    @Test
    fun stable_id_has_no_rotations() {
        val rt = RotationTracker()
        rt.observe("A", -50, 0)
        rt.observe("A", -50, 5_000)
        rt.observe("A", -50, 10_000)
        val s = rt.statsFor("A")!!
        assertEquals(0, s.rotations)
        assertEquals(1, s.addressesSeen)
        assertEquals(0L, s.firstSeenMs)
        assertEquals(1.0, s.confidence)
        assertFalse(s.contested)
    }

    @Test
    fun clean_rotation_carries_the_lineage_with_full_confidence() {
        val rt = RotationTracker()
        rt.observe("A", -50, 0)
        rt.observe("A", -50, 5_000)         // A last seen 5s
        rt.observe("B", -50, 6_000)         // B appears at the same range as A goes quiet
        rt.observe("B", -50, 30_000)
        rt.observe("B", -50, 36_000)        // A is now stale ⇒ A→B handover resolved
        assertNull(rt.statsFor("A"))        // A retired into B
        val b = rt.statsFor("B")!!
        assertEquals(1, b.rotations)
        assertEquals(2, b.addressesSeen)
        assertEquals(0L, b.firstSeenMs)     // first-seen carried from A
        assertEquals(1.0, b.confidence)     // exact dB match
        assertFalse(b.contested)
    }

    @Test
    fun two_devices_at_the_same_range_are_not_merged() {
        val rt = RotationTracker()
        for (t in 0..40_000 step 5_000) {
            rt.observe("A", -50, t.toLong())
            rt.observe("B", -50, t.toLong())   // both persist — neither hands over
        }
        assertEquals(0, rt.statsFor("A")!!.rotations)
        assertEquals(0, rt.statsFor("B")!!.rotations)
    }

    @Test
    fun a_new_device_at_a_different_range_is_its_own_track() {
        val rt = RotationTracker()
        rt.observe("A", -30, 0)
        rt.observe("A", -30, 5_000)
        rt.observe("C", -90, 3_000)          // a far device, present the whole time
        rt.observe("C", -90, 30_000)
        rt.observe("B", -30, 6_000)          // A rotates to B at the near range
        rt.observe("B", -30, 30_000)
        rt.observe("B", -30, 36_000)         // A stale ⇒ A→B (C is out of dB range)
        rt.observe("C", -90, 36_000)
        assertEquals(1, rt.statsFor("B")!!.rotations)
        val c = rt.statsFor("C")!!
        assertEquals(0, c.rotations)         // the far device never joined the rotation
        assertEquals(3_000L, c.firstSeenMs)
    }

    @Test
    fun a_weaker_dB_match_lowers_confidence() {
        val rt = RotationTracker()
        // Far out (≈-82), where the proximity prior is negligible, so a loose dB match is
        // all the evidence there is.
        rt.observe("A", -82, 0)
        rt.observe("A", -82, 5_000)
        rt.observe("B", -87, 6_000)          // 5 dB off (gate is 6) — a loose match
        rt.observe("B", -87, 30_000)
        rt.observe("B", -87, 36_000)
        val b = rt.statsFor("B")!!
        assertEquals(1, b.rotations)
        assertTrue(b.confidence in 0.2..0.4, "expected a low-but-positive confidence, was ${b.confidence}")
    }

    @Test
    fun a_close_handover_is_confident_even_on_a_loose_dB_match() {
        val rt = RotationTracker()
        // Right next to you (≈-50). A device that vanishes here as another appears in the
        // same close range almost can't be anything else — the proximity prior carries it
        // even though the dB jump (4) is loose.
        rt.observe("A", -49, 0)
        rt.observe("A", -49, 5_000)
        rt.observe("B", -53, 6_000)
        rt.observe("B", -53, 30_000)
        rt.observe("B", -53, 36_000)
        val b = rt.statsFor("B")!!
        assertEquals(1, b.rotations)
        assertTrue(b.confidence >= 0.8, "a close handover should be confident, was ${b.confidence}")
    }

    @Test
    fun jitter_around_an_unchanged_mean_stays_high_confidence() {
        val rt = RotationTracker()
        // A real phone-to-tag link swings ±4 dB while nothing moves. A jitters around
        // -83 and builds up a jitter estimate…
        var t = 0L
        listOf(-83, -79, -87, -82, -85, -81, -83).forEach { rt.observe("A", it, t); t += 700 }
        // …then rotates to B, the *same* device, so the mean is unchanged — only the
        // noisy swing differs. B persists until A retires.
        rt.observe("B", -85, t + 1_000)
        var bt = t + 1_000
        listOf(-81, -87, -83, -86, -82, -84).forEach { bt += 7_000; rt.observe("B", it, bt) }
        val b = rt.statsFor("B")!!
        assertEquals(1, b.rotations, "a jittery handover should still correlate")
        // The means barely differ; the spread is just RF noise. The old flat 1−Δ/7 curve
        // read the noise as evidence-against and gave ~0.6 — now a within-jitter Δ is the
        // strong match it actually is.
        assertTrue(b.confidence >= 0.85, "within-jitter Δ should read as high confidence, was ${b.confidence}")
    }

    @Test
    fun a_moving_device_correlates_along_its_trend() {
        val rt = RotationTracker()
        // The tag is being carried toward you: its signal climbs steadily (~1 dB/s).
        var t = 0L
        listOf(-88, -86, -84, -82, -80).forEach { rt.observe("A", it, t); t += 2_000 }
        // It rotates just as it settles near you (~-78). A frozen last-value compare would
        // see A at ~-81 vs B at -78 and dock confidence; the trend projects A to ~-80, so
        // the handover lines up.
        rt.observe("B", -78, t + 2_000)
        var bt = t + 2_000
        listOf(-77, -79, -78, -78).forEach { bt += 7_000; rt.observe("B", it, bt) }
        bt += 7_000; rt.observe("B", -78, bt) // A now stale ⇒ handover resolves
        val b = rt.statsFor("B")!!
        assertEquals(1, b.rotations, "an approaching device should still correlate across the rotation")
        assertTrue(b.confidence >= 0.85, "the trend should make this a confident match, was ${b.confidence}")
    }

    @Test
    fun a_collision_keeps_both_candidates_as_a_contested_branch() {
        val rt = RotationTracker()
        rt.observe("X", -50, 0)
        rt.observe("Y", -50, 0)
        rt.observe("X", -50, 5_000)
        rt.observe("Y", -50, 5_000)          // X and Y both quiet at 5s, same range
        rt.observe("P", -50, 6_000)          // two successors appear together — ambiguous
        rt.observe("Q", -50, 6_500)
        rt.observe("P", -50, 30_000)
        rt.observe("Q", -50, 30_000)
        rt.observe("P", -50, 36_000)         // X,Y stale ⇒ contested fork onto {P,Q}
        rt.observe("Q", -50, 36_000)
        val p = rt.statsFor("P")!!
        val q = rt.statsFor("Q")!!
        assertTrue(p.contested && q.contested, "both should be flagged contested")
        assertEquals(listOf("Q"), p.alternatives)
        assertEquals(listOf("P"), q.alternatives)
        assertTrue(p.confidence <= 0.6, "split probability, was ${p.confidence}") // not committed
    }

    @Test
    fun a_branch_resolves_to_the_survivor_when_the_other_candidate_leaves() {
        val rt = RotationTracker()
        rt.observe("X", -50, 0); rt.observe("Y", -50, 0)
        rt.observe("X", -50, 5_000); rt.observe("Y", -50, 5_000)
        rt.observe("P", -50, 6_000); rt.observe("Q", -50, 6_500)
        rt.observe("P", -50, 30_000); rt.observe("Q", -50, 30_000)
        rt.observe("P", -50, 36_000); rt.observe("Q", -50, 36_000) // contested {P,Q}
        // Q leaves; P keeps advertising.
        rt.observe("P", -50, 60_000)
        rt.observe("P", -50, 67_000)         // Q now stale ⇒ branch resolves to P
        assertNull(rt.statsFor("Q"))
        val p = rt.statsFor("P")!!
        assertFalse(p.contested)             // no longer a fork
        assertEquals(1, p.rotations)         // absorbed exactly one lineage (not double-counted)
        assertEquals(1.0, p.confidence)
    }

    @Test
    fun history_records_each_id_with_its_lifetime_and_hop_quality() {
        val rt = RotationTracker()
        // Far range, so the hop quality comes from the dB/jitter match (a clean Δ within
        // the noise), not the proximity floor — exercising the path under test.
        rt.observe("A", -82, 0)
        rt.observe("A", -82, 5_000)      // A seen across 5 s
        rt.observe("B", -82, 6_000)      // handover (within the window)
        rt.observe("B", -82, 30_000)
        rt.observe("B", -82, 36_000)     // A now stale ⇒ A→B
        val h = rt.statsFor("B")!!.history
        assertEquals(2, h.size)
        assertEquals("A", h[0].address)
        assertEquals(5_000L, h[0].durationMs)   // how long A was seen
        assertTrue(h[0].quality >= 0.8, "a clean hop at range, was ${h[0].quality}")
        assertFalse(h[0].current)
        assertEquals("B", h[1].address)
        assertTrue(h[1].current)                // the live id
    }

    @Test
    fun confidence_is_a_cumulative_mean_over_all_rotations() {
        val rt = RotationTracker()
        // Three clean close hops A→B→C→D, each successor appearing within the handover
        // window as its predecessor goes quiet — confidence is averaged, not reset.
        listOf(
            "A" to 0L, "A" to 5_000L,
            "B" to 6_000L, "B" to 30_000L, "B" to 36_000L,
            "C" to 37_000L, "C" to 60_000L, "C" to 67_000L,
            "D" to 68_000L, "D" to 90_000L, "D" to 98_000L,
        ).forEach { (id, t) -> rt.observe(id, -50, t) }
        val d = rt.statsFor("D")!!
        assertEquals(3, d.rotations)
        assertTrue(d.confidence >= 0.8, "mean of three good hops, was ${d.confidence}")
        assertEquals(4, d.history.size)           // every worn id is listed
    }

    @Test
    fun reports_correlating_while_a_handover_is_pending() {
        val rt = RotationTracker()
        rt.observe("A", -50, 0)
        rt.observe("A", -50, 5_000)      // A last heard at 5 s
        rt.observe("B", -50, 6_000)      // B appears as A goes quiet
        rt.observe("B", -50, 12_000)     // A quiet 7 s — not retired yet, but B is a match
        assertTrue(rt.isCorrelating("B", 12_000))
        assertEquals(0, rt.statsFor("B")!!.rotations) // not committed yet ⇒ UI shows "correlating…"
        rt.observe("B", -50, 36_000)     // A now stale ⇒ resolves
        assertFalse(rt.isCorrelating("B", 36_000))
        assertEquals(1, rt.statsFor("B")!!.rotations)
    }

    @Test
    fun identity_carries_the_whole_lineage_of_addresses() {
        val rt = RotationTracker()
        rt.observe("A", -50, 0)
        rt.observe("A", -50, 5_000)
        rt.observe("B", -50, 6_000)
        rt.observe("B", -50, 30_000)
        rt.observe("B", -50, 36_000)        // A→B handover
        val id = rt.identityFor("B")!!
        assertEquals(setOf("A", "B"), id.addresses) // both worn ids
        assertFalse(id.contested)
        assertNull(rt.identityFor("A")) // A is no longer a current address (it retired into B)
    }

    @Test
    fun current_address_follows_a_device_across_its_rotation() {
        val rt = RotationTracker()
        rt.observe("A", -50, 0)
        rt.observe("A", -50, 5_000)
        assertEquals("A", rt.currentAddressFor("A")) // still its own head
        rt.observe("B", -50, 6_000)
        rt.observe("B", -50, 30_000)
        rt.observe("B", -50, 36_000)                 // A→B handover resolved
        assertEquals("B", rt.currentAddressFor("A")) // a page pinned to A now follows to B
        assertEquals("B", rt.currentAddressFor("B"))
        assertNull(rt.currentAddressFor("Z"))        // never seen
    }

    @Test
    fun a_stable_device_is_its_own_identity() {
        val rt = RotationTracker()
        rt.observe("solo", -60, 0)
        rt.observe("solo", -60, 5_000)
        val id = rt.identityFor("solo")!!
        assertEquals(setOf("solo"), id.addresses)
        assertEquals(1.0, id.confidence)
    }

    @Test
    fun a_matching_payload_fingerprint_bridges_a_wider_dB_jump() {
        val rt = RotationTracker()
        rt.observe("A", -50, 0, "fp")
        rt.observe("A", -50, 5_000, "fp")
        rt.observe("B", -58, 6_000, "fp")    // 8 dB jump — beyond the 6 dB gate…
        rt.observe("B", -58, 30_000, "fp")
        rt.observe("B", -58, 36_000, "fp")   // …but the matching fingerprint widens it
        assertEquals(1, rt.statsFor("B")!!.rotations)
    }

    @Test
    fun a_mismatched_payload_fingerprint_vetoes_a_same_range_handover() {
        val rt = RotationTracker()
        rt.observe("A", -50, 0, "fpA")
        rt.observe("A", -50, 5_000, "fpA")
        rt.observe("B", -50, 6_000, "fpB")   // identical range, different device class
        rt.observe("B", -50, 30_000, "fpB")
        rt.observe("B", -50, 36_000, "fpB")
        assertEquals(0, rt.statsFor("B")!!.rotations) // not merged — payload says it's a different thing
    }

    @Test
    fun a_matching_fingerprint_lifts_confidence_on_a_loose_range_match() {
        val rt = RotationTracker()
        // Far out, so neither range nor proximity carries it — only the fingerprint does.
        rt.observe("A", -82, 0, "fp")
        rt.observe("A", -82, 5_000, "fp")
        rt.observe("B", -87, 6_000, "fp")    // 5 dB off — RSSI-only would be ~0.25…
        rt.observe("B", -87, 30_000, "fp")
        rt.observe("B", -87, 36_000, "fp")
        val b = rt.statsFor("B")!!
        assertEquals(1, b.rotations)
        assertTrue(b.confidence >= 0.85, "payload should corroborate, was ${b.confidence}")
    }
}
