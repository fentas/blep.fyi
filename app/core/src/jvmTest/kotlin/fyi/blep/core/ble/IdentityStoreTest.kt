package fyi.blep.core.ble

import fyi.blep.core.platform.createKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdentityStoreTest {

    private val day = 24L * 60 * 60_000

    @Test
    fun unknown_address_has_no_identity() {
        assertNull(IdentityStore(createKeyValueStore()).identityOf("aa:bb"))
    }

    @Test
    fun a_single_stable_address_is_its_own_identity() {
        val s = IdentityStore(createKeyValueStore())
        s.link(setOf("paired-watch"))
        assertEquals("paired-watch", s.identityOf("paired-watch"))
    }

    @Test
    fun linked_addresses_resolve_to_one_identity() {
        val s = IdentityStore(createKeyValueStore())
        s.link(setOf("A", "B"))
        val id = s.identityOf("A")
        assertNotNull(id)
        assertEquals(id, s.identityOf("B"))
    }

    @Test
    fun overlapping_links_merge_into_one_identity() {
        val s = IdentityStore(createKeyValueStore())
        s.link(setOf("A", "B"))
        s.link(setOf("B", "C")) // B bridges them
        assertEquals(s.identityOf("A"), s.identityOf("C"))
    }

    @Test
    fun the_id_is_stable_as_more_addresses_are_added() {
        val s = IdentityStore(createKeyValueStore())
        s.link(setOf("m1"))
        val id = s.identityOf("m1")
        s.link(setOf("m1", "m0")) // m0 sorts before m1, but the id must not change
        assertEquals(id, s.identityOf("m1"))
        assertEquals(id, s.identityOf("m0"))
    }

    @Test
    fun survives_a_new_instance_on_the_same_store() {
        val store = createKeyValueStore()
        IdentityStore(store).link(setOf("A", "B"))
        assertEquals("A", IdentityStore(store).identityOf("B")) // re-resolves after "restart"
    }

    @Test
    fun expires_after_the_ttl() {
        val store = createKeyValueStore()
        var clock = 1_000_000L
        IdentityStore(store, ttlMs = 14 * day, now = { clock }).link(setOf("A", "B"))
        // A fresh instance 15 days later prunes the stale identity on load.
        clock += 15 * day
        assertNull(IdentityStore(store, ttlMs = 14 * day, now = { clock }).identityOf("A"))
    }

    @Test
    fun first_seen_is_recorded_and_preserved_while_last_seen_refreshes_the_ttl() {
        val store = createKeyValueStore()
        var clock = 1_000_000L
        IdentityStore(store, ttlMs = 14 * day, now = { clock }).seen(setOf("watch"))
        assertEquals(1_000_000L, IdentityStore(store, now = { clock }).firstSeenOf("watch")) // recorded
        clock += 5 * day
        IdentityStore(store, ttlMs = 14 * day, now = { clock }).seen(setOf("watch")) // seen again → ttl refreshes
        // 15 days after first sight, but only 10 since last → still alive, first-seen unchanged.
        clock += 10 * day
        val later = IdentityStore(store, ttlMs = 14 * day, now = { clock })
        assertEquals(1_000_000L, later.firstSeenOf("watch")) // not reset by being seen again
    }

    @Test
    fun a_probe_is_cached_and_not_repeated() {
        val store = createKeyValueStore()
        val s = IdentityStore(store)
        s.seen(setOf("A"))
        assertTrue(!s.isProbed("A"))
        s.recordProbe("A", connectable = true, label = "Pixel Buds Pro", key = "ser:SN-1")
        assertTrue(s.isProbed("A"))
        assertEquals("Pixel Buds Pro", s.probeLabelOf("A"))
        // survives a restart so we never re-poke a known device.
        assertTrue(IdentityStore(store).isProbed("A"))
        assertEquals("Pixel Buds Pro", IdentityStore(store).probeLabelOf("A"))
    }

    @Test
    fun a_non_connectable_probe_still_marks_it_probed() {
        val s = IdentityStore(createKeyValueStore())
        s.seen(setOf("A"))
        s.recordProbe("A", connectable = false, label = null, key = null)
        assertTrue(s.isProbed("A")) // a refusal is a stable trait — don't keep retrying
    }

    @Test
    fun a_matching_probe_key_re_identifies_a_rotated_device() {
        val s = IdentityStore(createKeyValueStore())
        // We probe address A and learn its serial.
        s.seen(setOf("A"))
        s.recordProbe("A", connectable = true, label = "Jan's Buds", key = "ser:SN-9")
        // Later it rotates to B — the RSSI handover missed it, so B looks brand new.
        s.seen(setOf("B"))
        s.recordProbe("B", connectable = true, label = "Jan's Buds", key = "ser:SN-9")
        // Same serial ⇒ same device: A and B are now one identity, carrying the label.
        assertEquals(s.identityOf("A"), s.identityOf("B"))
        assertEquals("Jan's Buds", s.probeLabelOf("B"))
    }

    @Test
    fun a_probe_result_carries_across_a_later_rotation_merge() {
        val s = IdentityStore(createKeyValueStore())
        s.seen(setOf("A"))
        s.recordProbe("A", connectable = true, label = "Buds", key = "ser:SN-2")
        s.seen(setOf("B"))
        s.link(setOf("A", "B")) // RSSI handover stitches A→B afterwards
        assertEquals("Buds", s.probeLabelOf("B")) // the probe followed the merge
        assertTrue(s.isProbed("B"))
    }

    @Test
    fun seen_keeps_an_identity_alive_past_the_ttl() {
        val store = createKeyValueStore()
        var clock = 1_000_000L
        IdentityStore(store, ttlMs = 14 * day, now = { clock }).link(setOf("A"))
        clock += 10 * day
        IdentityStore(store, ttlMs = 14 * day, now = { clock }).seen(setOf("A")) // refresh
        clock += 10 * day // 20 days since the link, but only 10 since "seen"
        assertTrue(IdentityStore(store, ttlMs = 14 * day, now = { clock }).identityOf("A") != null)
    }
}
