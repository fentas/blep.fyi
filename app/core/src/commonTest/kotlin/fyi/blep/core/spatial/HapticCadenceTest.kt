package fyi.blep.core.spatial

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HapticCadenceTest {

    @Test
    fun silent_when_there_is_no_useful_signal() {
        assertNull(HapticCadence.intervalMs(0f))
        assertNull(HapticCadence.intervalMs(0.04f))
    }

    @Test
    fun ticks_get_faster_as_you_get_closer() {
        val far = HapticCadence.intervalMs(0.1f)!!
        val mid = HapticCadence.intervalMs(0.5f)!!
        val near = HapticCadence.intervalMs(1.0f)!!
        assertTrue(far > mid && mid > near, "expected monotonically faster: $far > $mid > $near")
        assertEquals(HapticCadence.NEAR_MS, near)
    }

    @Test
    fun interval_stays_within_bounds() {
        for (i in 0..100) {
            val p = i / 100f
            val ms = HapticCadence.intervalMs(p) ?: continue
            assertTrue(ms in HapticCadence.NEAR_MS..HapticCadence.FAR_MS, "out of bounds at $p: $ms")
        }
    }
}
