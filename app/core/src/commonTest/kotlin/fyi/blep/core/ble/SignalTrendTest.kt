package fyi.blep.core.ble

import kotlin.test.Test
import kotlin.test.assertEquals

class SignalTrendTest {

    @Test
    fun firstSampleIsFlatAndSetsPeak() {
        val t = SignalTrend()
        assertEquals(Trend.FLAT, t.update(-80.0))
        assertEquals(-80.0, t.peak, 1e-9)
    }

    @Test
    fun classifiesRisingFallingFlatWithHysteresis() {
        val t = SignalTrend(flatBand = 1.5)
        t.update(-80.0)
        assertEquals(Trend.RISING, t.update(-76.0))  // +4
        assertEquals(Trend.FALLING, t.update(-82.0)) // -6
        assertEquals(Trend.FLAT, t.update(-81.0))    // +1 within band
    }

    @Test
    fun tracksPeakAndDropFromPeak() {
        val t = SignalTrend()
        t.update(-80.0)
        t.update(-60.0) // new peak
        t.update(-72.0)
        assertEquals(-60.0, t.peak, 1e-9)
        assertEquals(12.0, t.dropFromPeak(), 1e-9)
    }

    @Test
    fun resetClearsPeak() {
        val t = SignalTrend()
        t.update(-50.0)
        t.reset()
        assertEquals(0.0, t.dropFromPeak(), 1e-9)
    }
}
