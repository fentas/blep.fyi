package fyi.blep.core.spatial

/**
 * A fused snapshot from the device's motion/position sensors, normalised into
 * the session's local frame. Every field is optional/best-effort so the tracker
 * degrades gracefully: with no sensors at all this is just `MotionSample(timeMs)`
 * and the tracker falls back to RSSI-only behaviour.
 *
 * @property position local ENU metres from the origin, if a usable fix exists
 *   (GPS). Null indoors / before a fix.
 * @property positionAccuracyM 1σ horizontal accuracy of [position] in metres
 *   (smaller = better); NaN if unknown.
 * @property headingRad compass heading the device is pointing, 0 = north/+y,
 *   clockwise; null if the magnetometer is unavailable/uncalibrated.
 * @property headingAccuracyRad 1σ heading accuracy; NaN if unknown.
 * @property speedMps horizontal ground speed (GPS- or step-derived).
 * @property verticalMps vertical velocity (accelerometer-derived); positive up.
 *   Used to notice the user crouching to "search low".
 * @property moving whether the device is translating (vs held still) — from the
 *   accelerometer/step detector. Lets us tell real walking from RSSI noise.
 * @property reorienting whether the device is being rotated/tilted sharply right
 *   now (large gyro/heading rate). RSSI swings during this are body/antenna
 *   geometry, not the target moving, so they should be discounted.
 */
data class MotionSample(
    val timeMs: Long,
    val position: Vec2? = null,
    val positionAccuracyM: Double = Double.NaN,
    val headingRad: Double? = null,
    val headingAccuracyRad: Double = Double.NaN,
    val speedMps: Double = 0.0,
    val verticalMps: Double = 0.0,
    val moving: Boolean = false,
    val reorienting: Boolean = false,
)
