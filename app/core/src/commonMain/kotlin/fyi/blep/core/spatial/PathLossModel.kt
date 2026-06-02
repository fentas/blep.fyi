package fyi.blep.core.spatial

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Mutable log-distance path-loss model `rssi(d) = rssiAt1m − 10·n·log10(d)`.
 * Seeded from [SpatialTuning] defaults and refined live by [PathLossCalibrator]
 * as the target is localised, so ranging adapts to the actual environment.
 */
class PathLossModel(var rssiAt1m: Double, var exponent: Double) {

    fun expectedRssi(distanceM: Double): Double =
        rssiAt1m - 10.0 * exponent * log10(max(distanceM, 0.5))

    fun rangeOf(rssi: Double, maxRangeM: Double): Double =
        10.0.pow((rssiAt1m - rssi) / (10.0 * exponent)).coerceIn(0.1, maxRangeM)

    companion object {
        fun from(tuning: SpatialTuning) = PathLossModel(tuning.rssiAt1m, tuning.pathLossExponent)
    }
}
