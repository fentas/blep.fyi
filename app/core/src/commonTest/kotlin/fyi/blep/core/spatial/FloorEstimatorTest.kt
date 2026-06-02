package fyi.blep.core.spatial

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloorEstimatorTest {

    @Test
    fun reports_zero_until_you_actually_change_altitude() {
        val fe = FloorEstimator()
        repeat(12) { fe.update(altitudeM = 0.0, strength01 = 0.6f) }
        assertEquals(0, fe.floorDelta)
    }

    @Test
    fun strongest_signal_one_floor_up_points_up_when_back_down() {
        val fe = FloorEstimator(floorHeightM = 3.0)
        repeat(5) { fe.update(0.0, 0.2f) }   // weak on this floor
        repeat(5) { fe.update(3.0, 0.9f) }   // strong one floor up
        fe.update(0.0, 0.2f)                 // walked back down
        assertTrue(fe.floorDelta >= 1, "expected target ~1 floor up, got ${fe.floorDelta}")
    }

    @Test
    fun strongest_signal_below_points_down() {
        val fe = FloorEstimator(floorHeightM = 3.0)
        repeat(5) { fe.update(0.0, 0.2f) }   // weak up here
        repeat(5) { fe.update(-3.0, 0.9f) }  // strong one floor down
        fe.update(0.0, 0.2f)                 // back up
        assertTrue(fe.floorDelta <= -1, "expected target ~1 floor down, got ${fe.floorDelta}")
    }
}
