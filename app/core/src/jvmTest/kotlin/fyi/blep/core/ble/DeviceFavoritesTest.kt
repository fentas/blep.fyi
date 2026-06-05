package fyi.blep.core.ble

import fyi.blep.core.platform.createKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceFavoritesTest {

    @Test
    fun empty_by_default() {
        val f = DeviceFavorites(createKeyValueStore())
        assertTrue(f.ids().isEmpty())
        assertFalse(f.isFavorite("aa:bb"))
    }

    @Test
    fun toggle_adds_then_removes_and_reports_new_state() {
        val f = DeviceFavorites(createKeyValueStore())
        assertTrue(f.toggle("aa:bb"))          // now favourite
        assertTrue(f.isFavorite("aa:bb"))
        assertEquals(setOf("aa:bb"), f.ids())
        assertFalse(f.toggle("aa:bb"))         // toggled back off
        assertFalse(f.isFavorite("aa:bb"))
        assertTrue(f.ids().isEmpty())
    }

    @Test
    fun blank_id_is_ignored() {
        val f = DeviceFavorites(createKeyValueStore())
        assertFalse(f.toggle("  "))
        assertTrue(f.ids().isEmpty())
    }

    @Test
    fun survives_a_new_instance_on_the_same_store() {
        val store = createKeyValueStore()
        DeviceFavorites(store).toggle("keys")
        // A fresh instance over the same persisted store still sees it.
        assertTrue(DeviceFavorites(store).isFavorite("keys"))
    }

    @Test
    fun caps_the_set_newest_wins() {
        val f = DeviceFavorites(createKeyValueStore(), maxEntries = 2)
        f.toggle("a"); f.toggle("b"); f.toggle("c")
        val ids = f.ids()
        assertEquals(2, ids.size)
        assertTrue("c" in ids && "b" in ids)
        assertFalse("a" in ids)               // oldest dropped past the cap
    }
}
