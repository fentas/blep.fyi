package fyi.blep.core.tether

import fyi.blep.core.platform.createKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceTetherTest {
    @Test fun toggleOnOff() {
        val t = DeviceTether(createKeyValueStore())
        assertFalse(t.isTethered("A"))
        assertTrue(t.toggle("A"))           // returns new state = on
        assertTrue(t.isTethered("A"))
        assertEquals(setOf("A"), t.ids())
        assertFalse(t.toggle("A"))          // off again
        assertFalse(t.isTethered("A"))
    }

    @Test fun blankIdIgnored() {
        val t = DeviceTether(createKeyValueStore())
        assertFalse(t.toggle(""))
        assertTrue(t.ids().isEmpty())
    }

    @Test fun persistsAndCapsEntries() {
        val store = createKeyValueStore()
        val t = DeviceTether(store, maxEntries = 3)
        listOf("A", "B", "C", "D").forEach { t.toggle(it) }
        // Oldest dropped once over the cap; newest kept.
        assertEquals(3, t.ids().size)
        assertTrue("D" in t.ids())
        assertFalse("A" in t.ids())
        // A fresh instance reads the same persisted set.
        assertEquals(t.ids(), DeviceTether(store, maxEntries = 3).ids())
    }

    @Test fun clearWipes() {
        val t = DeviceTether(createKeyValueStore())
        t.toggle("A")
        t.clear()
        assertTrue(t.ids().isEmpty())
        assertEquals(0, t.sizeBytes())
    }
}
