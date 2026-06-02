package fyi.blep.core.spatial

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A 2-D point/vector in the session's **local ENU frame**: metres east (+x) and
 * north (+y) from the calibration origin. All spatial maths lives in this frame
 * so it is independent of GPS being available.
 */
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Double) = Vec2(x * s, y * s)

    fun dot(o: Vec2) = x * o.x + y * o.y
    val length: Double get() = hypot(x, y)

    fun normalizedOrZero(): Vec2 {
        val l = length
        return if (l < 1e-9) ZERO else Vec2(x / l, y / l)
    }

    companion object {
        val ZERO = Vec2(0.0, 0.0)

        /**
         * Unit vector for a compass [headingRad] (0 = north/+y, increasing
         * clockwise so π/2 = east/+x) — the forward direction you're facing.
         */
        fun heading(headingRad: Double) = Vec2(sin(headingRad), cos(headingRad))
    }
}

/** Compass bearing (rad, clockwise from north/+y) pointing along [v]. */
fun bearingOf(v: Vec2): Double = atan2(v.x, v.y)

/** Smallest signed difference a − b wrapped to (−π, π]. */
fun angleDelta(a: Double, b: Double): Double {
    var d = (a - b) % (2 * PI)
    if (d > PI) d -= 2 * PI
    if (d < -PI) d += 2 * PI
    return d
}

/** A WGS84 coordinate (degrees), used only at the platform edge. */
data class GeoPoint(val lat: Double, val lon: Double)

/**
 * Projects WGS84 coordinates to the local ENU frame using an equirectangular
 * approximation around [originLat]/[originLon]. Accurate to well under a metre
 * over the tens-of-metres ranges BLE tracking operates in.
 */
class LocalFrame(private val originLat: Double, private val originLon: Double) {
    private val mPerDegLat = 110_540.0
    private val mPerDegLon = 111_320.0 * cos(originLat * PI / 180.0)

    fun toLocal(p: GeoPoint) = Vec2(
        x = (p.lon - originLon) * mPerDegLon,
        y = (p.lat - originLat) * mPerDegLat,
    )
}
