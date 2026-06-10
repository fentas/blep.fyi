package fyi.blep.core.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceTableTest {

    @Test
    fun dedupesByIdAndKeepsLatestRssi() {
        val t = DeviceTable()
        t.upsert("a", "Keys", -80, false)
        t.upsert("a", "Keys", -60, false)
        val snap = t.snapshot(includeUnnamed = true)
        assertEquals(1, snap.size)
        assertEquals(-60, snap.first().rssi)
    }

    @Test
    fun hidesUnnamedUnlessRequested() {
        val t = DeviceTable()
        t.upsert("named", "Earbuds", -70, false)
        t.upsert("anon", null, -50, false)
        assertEquals(1, t.snapshot(includeUnnamed = false).size)
        assertEquals(2, t.snapshot(includeUnnamed = true).size)
    }

    @Test
    fun ordersConnectedThenNamedThenByRssi() {
        val t = DeviceTable()
        t.upsert("weakNamed", "Far", -90, false)
        t.upsert("strongNamed", "Near", -50, false)
        t.upsert("connected", "Buds", -85, true)
        t.upsert("anon", null, -40, false)
        val ids = t.snapshot(includeUnnamed = true).map { it.id }
        // connected first, then named by rssi desc, unnamed last
        assertEquals(listOf("connected", "strongNamed", "weakNamed", "anon"), ids)
    }

    @Test
    fun preservesAliasAcrossUpserts() {
        val t = DeviceTable()
        t.upsert("a", "Keys", -80, false)
        t.setAlias("a", "My Keychain")
        t.upsert("a", "Keys", -55, false)
        val d = t.snapshot(includeUnnamed = true).first()
        assertEquals("My Keychain", d.alias)
        assertEquals("My Keychain", d.displayName)
    }

    @Test
    fun blankAliasFallsBackToName() {
        val t = DeviceTable()
        t.upsert("a", "Keys", -80, false)
        t.setAlias("a", "   ")
        assertEquals("Keys", t.snapshot(includeUnnamed = true).first().displayName)
    }

    @Test
    fun unnamedDeviceFallsBackToIdAndNotNamed() {
        val t = DeviceTable()
        t.upsert("anon", null, -60, false)
        val d = t.snapshot(includeUnnamed = true).first()
        assertFalse(d.isNamed)
        assertEquals("anon", d.displayName) // the raw id, so unnamed devices stay distinguishable
    }

    @Test
    fun pruneRemovesStaleDevices() {
        val t = DeviceTable()
        t.upsert("fresh", "Near", -60, false, seenAtMs = 9_000)
        t.upsert("stale", "Gone", -80, false, seenAtMs = 1_000)
        t.prune(nowMs = 10_000, ttlMs = 5_000) // stale last seen 9s ago > 5s ttl
        val ids = t.snapshot(includeUnnamed = true).map { it.id }
        assertEquals(listOf("fresh"), ids)
    }

    @Test
    fun clearEmptiesTable() {
        val t = DeviceTable()
        t.upsert("a", "Keys", -80, false)
        t.clear()
        assertTrue(t.snapshot(includeUnnamed = true).isEmpty())
    }
}
