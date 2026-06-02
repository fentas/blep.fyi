package fyi.blep.core.spatial

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Estimates which floor the target is on relative to where you are now, from
 * barometric altitude + signal strength. RSSI alone can't tell you the target's
 * height, but if you climb/descend while hunting, the altitude at which the
 * signal was strongest reveals the target's floor.
 *
 * It keeps a signal²-weighted mean of the altitudes samples were taken at (strong
 * readings dominate), and reports the difference from the current altitude in
 * floors. Until you've actually changed altitude by ~half a floor it reports 0
 * (we simply can't know).
 */
class FloorEstimator(private val floorHeightM: Double = 3.0) {
    private var weightSum = 0.0
    private var altWeightedSum = 0.0
    private var currentAlt = 0.0
    private var minAlt = Double.MAX_VALUE
    private var maxAlt = -Double.MAX_VALUE

    fun reset() {
        weightSum = 0.0
        altWeightedSum = 0.0
        currentAlt = 0.0
        minAlt = Double.MAX_VALUE
        maxAlt = -Double.MAX_VALUE
    }

    fun update(altitudeM: Double, strength01: Float) {
        currentAlt = altitudeM
        if (altitudeM < minAlt) minAlt = altitudeM
        if (altitudeM > maxAlt) maxAlt = altitudeM
        val w = strength01.toDouble().pow(2) + 1e-4 // emphasise strong (= near) readings
        weightSum += w
        altWeightedSum += w * altitudeM
    }

    /** Floors to the target relative to here: +above, −below, 0 if unknown/same. */
    val floorDelta: Int
        get() {
            if (weightSum < 1e-9 || (maxAlt - minAlt) < floorHeightM * 0.5) return 0
            val targetAlt = altWeightedSum / weightSum
            return ((targetAlt - currentAlt) / floorHeightM).roundToInt()
        }
}
