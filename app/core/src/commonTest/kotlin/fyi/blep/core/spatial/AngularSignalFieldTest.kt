package fyi.blep.core.spatial

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertTrue

class AngularSignalFieldTest {

    @Test
    fun finds_the_bearing_of_the_strongest_signal_as_you_turn() {
        val field = AngularSignalField()
        // Target to the east (π/2): RSSI peaks there as we sweep a full turn.
        var deg = 0
        while (deg < 360) {
            val h = deg * PI / 180.0
            field.update(h, -70.0 + 8.0 * cos(h - PI / 2)) // −62 at east, −78 at west
            deg += 10
        }
        val bearing = field.bearingRad!!
        assertTrue(abs(angleDelta(bearing, PI / 2)) < 25.0 * PI / 180.0, "bearing ${bearing * 180 / PI}° off east")
        assertTrue(field.confidence > 0.4f, "confidence too low: ${field.confidence}")
    }

    @Test
    fun barely_turning_gives_low_confidence() {
        val field = AngularSignalField()
        field.update(0.0, -70.0)
        field.update(0.08, -69.0)
        assertTrue(field.confidence < 0.3f, "expected low confidence, got ${field.confidence}")
    }

    @Test
    fun reset_clears_the_field() {
        val field = AngularSignalField()
        var deg = 0
        while (deg < 360) { field.update(deg * PI / 180.0, -65.0 + cos(deg * PI / 180.0)); deg += 15 }
        field.reset()
        assertTrue(field.bearingRad == null && field.confidence == 0f)
    }
}
