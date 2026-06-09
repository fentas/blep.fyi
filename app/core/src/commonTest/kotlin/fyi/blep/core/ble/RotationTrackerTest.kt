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
        rt.observe("A", -50, 0)
        rt.observe("A", -50, 5_000)
        rt.observe("B", -55, 6_000)          // 5 dB off (gate is 6) — a loose match
        rt.observe("B", -55, 30_000)
        rt.observe("B", -55, 36_000)
        val b = rt.statsFor("B")!!
        assertEquals(1, b.rotations)
        assertTrue(b.confidence in 0.2..0.4, "expected a low-but-positive confidence, was ${b.confidence}")
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
        rt.observe("A", -50, 0, "fp")
        rt.observe("A", -50, 5_000, "fp")
        rt.observe("B", -55, 6_000, "fp")    // 5 dB off — RSSI-only would be ~0.29…
        rt.observe("B", -55, 30_000, "fp")
        rt.observe("B", -55, 36_000, "fp")
        val b = rt.statsFor("B")!!
        assertEquals(1, b.rotations)
        assertTrue(b.confidence >= 0.85, "payload should corroborate, was ${b.confidence}")
    }
}
