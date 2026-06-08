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
