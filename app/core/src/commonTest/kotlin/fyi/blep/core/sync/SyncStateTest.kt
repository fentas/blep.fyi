package fyi.blep.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncStateTest {
    private val E = SyncState.EMPTY

    @Test fun mergeUnionsAndIsCommutative() {
        val a = E.withSet(Section.FAVORITES, setOf("X"), 100)
        val b = E.withSet(Section.FAVORITES, setOf("Y"), 100)
        assertEquals(setOf("X", "Y"), a.merge(b).favoriteIds())
        assertEquals(a.merge(b), b.merge(a)) // commutative
    }

    @Test fun mergeIsIdempotent() {
        val a = E.withSet(Section.TETHERED, setOf("X", "Y"), 10)
        assertEquals(a, a.merge(a))
        assertEquals(a, a.merge(a).merge(a))
    }

    @Test fun newerWriteWins() {
        val older = E.withNames(mapOf("X" to "Keys"), 100)
        val newer = E.withNames(mapOf("X" to "Wallet"), 200)
        assertEquals("Wallet", older.merge(newer).aliasMap()["X"])
        assertEquals("Wallet", newer.merge(older).aliasMap()["X"]) // order-independent
    }

    @Test fun removalPropagatesViaTombstone() {
        val added = E.withSet(Section.FAVORITES, setOf("X"), 100)
        val removed = added.withSet(Section.FAVORITES, emptySet(), 200)
        // A peer that still thinks X is a favourite (older) must NOT resurrect it.
        val stalePeer = E.withSet(Section.FAVORITES, setOf("X"), 100)
        assertEquals(emptySet(), removed.merge(stalePeer).favoriteIds())
    }

    @Test fun clearedNameTombstonesAndWins() {
        val named = E.withNames(mapOf("X" to "Keys"), 100)
        val cleared = named.withNames(emptyMap(), 300)
        assertNull(cleared.aliasMap()["X"])
        val stalePeer = E.withNames(mapOf("X" to "Keys"), 100)
        assertNull(cleared.merge(stalePeer).aliasMap()["X"])
    }

    @Test fun unchangedLocalEditKeepsState() {
        val s = E.withSetting("sensitivity", "STRICT", 1)
        assertEquals(s, s.withSetting("sensitivity", "STRICT", 2)) // same value → no ts bump
        val f = E.withSet(Section.MUTED, setOf("A"), 1)
        assertEquals(f, f.withSet(Section.MUTED, setOf("A"), 9))
    }

    @Test fun encodeDecodeRoundtrips() {
        val s = E.withSet(Section.FAVORITES, setOf("A", "B"), 5)
            .withSet(Section.TETHERED, setOf("C"), 6)
            .withNames(mapOf("A" to "My keys"), 7)
            .withSetting("sensitivity", "STRICT", 8)
        val r = SyncState.decode(s.encode())
        assertEquals(s, r)
        assertEquals(setOf("A", "B"), r.favoriteIds())
        assertEquals(setOf("C"), r.tetheredIds())
        assertEquals("My keys", r.aliasMap()["A"])
        assertEquals("STRICT", r.setting("sensitivity"))
    }

    @Test fun decodeToleratesGarbageLines() {
        val r = SyncState.decode("F\tA\t1\t1\nGARBAGE\n\nX\ty")
        assertEquals(setOf("A"), r.favoriteIds())
    }

    @Test fun threeWayConvergence() {
        // Independent edits on three replicas must all merge to the same state regardless
        // of pairing order — the CRDT property the whole feature relies on.
        val p1 = E.withSet(Section.FAVORITES, setOf("A"), 10).withNames(mapOf("A" to "Keys"), 11)
        val p2 = E.withSet(Section.FAVORITES, setOf("B"), 12)
        val p3 = E.withSet(Section.TETHERED, setOf("C"), 13).withNames(mapOf("A" to "Bag"), 20)
        val abc = p1.merge(p2).merge(p3)
        val cab = p3.merge(p1).merge(p2)
        assertEquals(abc, cab)
        assertEquals(setOf("A", "B"), abc.favoriteIds())
        assertEquals(setOf("C"), abc.tetheredIds())
        assertEquals("Bag", abc.aliasMap()["A"]) // ts 20 > 11
        assertTrue(true)
    }
}
