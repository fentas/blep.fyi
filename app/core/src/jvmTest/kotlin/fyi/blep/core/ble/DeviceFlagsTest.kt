package fyi.blep.core.ble

import fyi.blep.core.platform.createKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceFlagsTest {

    @Test
    fun empty_by_default() {
        val f = DeviceFlags(createKeyValueStore())
        assertTrue(f.ids().isEmpty())
        assertFalse(f.isFlagged("aa:bb"))
    }

    @Test
    fun toggle_adds_then_removes() {
        val f = DeviceFlags(createKeyValueStore())
        assertTrue(f.toggle("aa:bb"))
        assertTrue(f.isFlagged("aa:bb"))
        assertFalse(f.toggle("aa:bb"))
        assertTrue(f.ids().isEmpty())
    }

    @Test
    fun survives_a_new_instance_on_the_same_store() {
        val store = createKeyValueStore()
        DeviceFlags(store).toggle("tag")
        assertTrue(DeviceFlags(store).isFlagged("tag"))
    }

    @Test
    fun caps_newest_wins() {
        val f = DeviceFlags(createKeyValueStore(), maxEntries = 2)
        f.toggle("a"); f.toggle("b"); f.toggle("c")
        val ids = f.ids()
        assertEquals(2, ids.size)
        assertFalse("a" in ids)
    }
}
