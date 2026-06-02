package fyi.blep.core.spatial

import kotlin.math.pow

/**
 * Tunables for the spatial layer: the log-distance path-loss model used to turn
 * RSSI into a rough range, plus signal→strength mapping and field-sampling
 * limits. Defaults are reasonable for indoor BLE; calibration can refine them.
 */
data class SpatialTuning(
    /** Expected RSSI (dBm) at 1 m — the path-loss reference. */
    val rssiAt1m: Double = -59.0,
    /** Path-loss exponent n (free space ≈ 2, cluttered indoor ≈ 2.5–4). */
    val pathLossExponent: Double = 2.5,
    /** RSSI mapped to strength 0f (far). */
    val rssiFar: Double = -95.0,
    /** RSSI mapped to strength 1f (near). */
    val rssiNear: Double = -45.0,
    /** Clamp range estimates to this maximum (m) so weak samples can't explode. */
    val maxRangeM: Double = 60.0,
    /** Minimum spacing (m) between stored field samples; closer ones merge. */
    val minSampleSpacingM: Double = 0.4,
    /** Grid cell size (m) for de-clustering anchors before multilateration. */
    val anchorCellM: Double = 1.0,
    /** Hard cap on retained path points (older ones are decimated). */
    val maxPathPoints: Int = 4000,
) {
    init {
        require(rssiNear > rssiFar) { "rssiNear must be greater (stronger) than rssiFar" }
        require(pathLossExponent > 0.0) { "pathLossExponent must be positive" }
    }

    /** Maps RSSI to a [0f,1f] strength for colouring/weighting. */
    fun strength01(rssi: Double): Float =
        ((rssi - rssiFar) / (rssiNear - rssiFar)).coerceIn(0.0, 1.0).toFloat()

    /** Log-distance path-loss estimate of range (m) for an RSSI sample. */
    fun rangeOf(rssi: Double): Double =
        10.0.pow((rssiAt1m - rssi) / (10.0 * pathLossExponent)).coerceIn(0.1, maxRangeM)
}
