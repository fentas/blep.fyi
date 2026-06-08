package fyi.blep.core.ble

import fyi.blep.core.platform.createKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceAliasesTest {

    @Test
    fun empty_by_default() {
        val a = DeviceAliases(createKeyValueStore())
        assertTrue(a.all().isEmpty())
        assertNull(a.of("aa:bb"))
    }

    @Test
    fun set_then_read_then_clear() {
        val a = DeviceAliases(createKeyValueStore())
        assertEquals("Keys", a.set("aa:bb", "  Keys  ")) // trimmed
        assertEquals("Keys", a.of("aa:bb"))
        assertNull(a.set("aa:bb", "   "))                // blank clears
        assertNull(a.of("aa:bb"))
        assertTrue(a.all().isEmpty())
    }

    @Test
    fun blank_id_is_ignored() {
        val a = DeviceAliases(createKeyValueStore())
        assertNull(a.set("  ", "Keys"))
        assertTrue(a.all().isEmpty())
    }

    @Test
    fun tabs_and_newlines_are_flattened_so_the_line_format_survives() {
        val a = DeviceAliases(createKeyValueStore())
        a.set("id", "My\tlost\nkeys")
        assertEquals("My lost keys", a.of("id"))
        assertEquals(1, a.all().size) // didn't split into bogus extra entries
    }

    @Test
    fun survives_a_new_instance_on_the_same_store() {
        val store = createKeyValueStore()
        DeviceAliases(store).set("keys", "Spare keys")
        // A fresh instance over the same persisted store still sees the rename.
        assertEquals("Spare keys", DeviceAliases(store).of("keys"))
    }

    @Test
    fun caps_the_map_newest_wins() {
        val a = DeviceAliases(createKeyValueStore(), maxEntries = 2)
        a.set("a", "A"); a.set("b", "B"); a.set("c", "C")
        val all = a.all()
        assertEquals(2, all.size)
        assertEquals("C", all["c"]); assertEquals("B", all["b"])
        assertNull(all["a"]) // oldest dropped past the cap
    }
}
