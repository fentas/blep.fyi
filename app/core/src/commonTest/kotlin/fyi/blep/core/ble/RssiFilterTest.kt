package fyi.blep.core.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RssiFilterTest {

    @Test
    fun firstSampleSeedsAverage() {
        val f = RssiFilter(alpha = 0.3)
        assertNull(f.smoothed)
        assertEquals(-70.0, f.update(-70), 1e-9)
        assertEquals(-70.0, f.smoothed)
    }

    @Test
    fun deltaIsZeroUntilTwoSamples() {
        val f = RssiFilter()
        assertEquals(0.0, f.delta, 1e-9)
        f.update(-70)
        assertEquals(0.0, f.delta, 1e-9)
    }

    @Test
    fun emaMovesTowardNewSamplesByAlpha() {
        val f = RssiFilter(alpha = 0.5)
        f.update(-80)            // seed -80
        val v = f.update(-60)    // -80 + 0.5*(-60 - -80) = -70
        assertEquals(-70.0, v, 1e-9)
        assertEquals(10.0, f.delta, 1e-9) // got stronger by 10 dB
    }

    @Test
    fun alphaOneTracksRawExactly() {
        val f = RssiFilter(alpha = 1.0)
        f.update(-90)
        assertEquals(-50.0, f.update(-50), 1e-9)
    }

    @Test
    fun rejectsInvalidAlpha() {
        assertFailsWith<IllegalArgumentException> { RssiFilter(alpha = 0.0) }
        assertFailsWith<IllegalArgumentException> { RssiFilter(alpha = 1.5) }
    }

    @Test
    fun resetClearsState() {
        val f = RssiFilter()
        f.update(-70); f.update(-60)
        f.reset()
        assertNull(f.smoothed)
        assertTrue(f.delta == 0.0)
    }
}
