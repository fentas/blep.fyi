package fyi.blep.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncMessageTest {
    @Test fun roundtripsEachKind() {
        val msgs = listOf(
            SyncMessage.TrackerAlert("AirTag"),
            SyncMessage.TrackerAlert(null),
            SyncMessage.TetherLeft("AA:BB", "My keys"),
            SyncMessage.TetherReturned("AA:BB", "My keys"),
        )
        for (m in msgs) assertEquals(m, SyncMessage.decode(m.encode()))
    }

    @Test fun toleratesTabsInLabels() {
        val m = SyncMessage.TetherLeft("AA:BB", "weird\tname")
        // Tabs are sanitised on encode, so the decoded name has no tab but still decodes.
        val d = SyncMessage.decode(m.encode()) as SyncMessage.TetherLeft
        assertEquals("AA:BB", d.id)
    }

    @Test fun unknownReturnsNull() {
        assertNull(SyncMessage.decode("WAT\tx"))
        assertNull(SyncMessage.decode(""))
    }
}
