package fyi.blep.core.tether

import fyi.blep.core.platform.createKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresenceMonitorTest {
    private val A = setOf("A")
    private val AB = setOf("A", "B")

    private fun monitor() = PresenceMonitor(createKeyValueStore(), leaveGraceMs = 10_000, seenRefreshMs = 5_000)

    @Test fun seedThenLeaveThenReturn() {
        val m = monitor()
        // First sighting (present) just seeds — no alert.
        assertTrue(m.update(A, setOf("A"), 0L).isEmpty())
        // Absent but still inside the grace window — no LEFT yet.
        assertTrue(m.update(A, emptySet(), 5_000L).isEmpty())
        // Absent past the grace — LEFT fires once.
        assertEquals(PresenceMonitor.Event.LEFT, m.update(A, emptySet(), 11_000L)["A"])
        // Already committed-absent — no repeat.
        assertTrue(m.update(A, emptySet(), 30_000L).isEmpty())
        // Back in range — RETURNED fires immediately.
        assertEquals(PresenceMonitor.Event.RETURNED, m.update(A, setOf("A"), 31_000L)["A"])
    }

    @Test fun briefFlickerDoesNotFireLeave() {
        val m = monitor()
        m.update(A, setOf("A"), 0L)            // seed present
        m.update(A, setOf("A"), 6_000L)        // still present
        assertTrue(m.update(A, emptySet(), 9_000L).isEmpty())  // gone 3s < grace
        // Present again before grace elapsed — never counted as left, so no RETURNED either.
        val e = m.update(A, setOf("A"), 12_000L)
        assertFalse(e.containsKey("A"))
    }

    @Test fun neverSeenPresentNeverAlerts() {
        val m = monitor()
        assertTrue(m.update(A, emptySet(), 0L).isEmpty())        // seed absent
        assertTrue(m.update(A, emptySet(), 100_000L).isEmpty())  // still absent — nothing
    }

    @Test fun stateSurvivesAcrossInstances() {
        // The foreground service and the periodic worker are different objects sharing the
        // same store; a leave seen by a fresh instance must still fire (background path).
        val store = createKeyValueStore()
        PresenceMonitor(store, leaveGraceMs = 10_000).update(A, setOf("A"), 0L)   // seed present
        val e = PresenceMonitor(store, leaveGraceMs = 10_000).update(A, emptySet(), 20_000L)
        assertEquals(PresenceMonitor.Event.LEFT, e["A"])
    }

    @Test fun devicesAreIndependent() {
        val m = monitor()
        m.update(AB, setOf("A", "B"), 0L)               // both seeded present
        val e = m.update(AB, setOf("B"), 20_000L)       // A gone, B still here
        assertEquals(PresenceMonitor.Event.LEFT, e["A"])
        assertFalse(e.containsKey("B"))
    }

    @Test fun untetheringPrunesState() {
        val m = monitor()
        m.update(A, setOf("A"), 0L)
        assertTrue(m.sizeBytes() > 0)
        m.update(emptySet(), emptySet(), 1_000L)
        assertEquals(0, m.sizeBytes())
    }

    @Test fun remembersLabelForLeaveAlert() {
        val m = monitor()
        m.update(A, setOf("A"), 0L, labels = mapOf("A" to "My keys"))   // learn name while present
        val e = m.update(A, emptySet(), 20_000L)                         // left — name no longer in scan
        assertEquals(PresenceMonitor.Event.LEFT, e["A"])
        assertEquals("My keys", m.labelOf("A"))
    }

    @Test fun clearWipesState() {
        val m = monitor()
        m.update(A, setOf("A"), 0L)
        m.clear()
        assertEquals(0, m.sizeBytes())
    }
}
