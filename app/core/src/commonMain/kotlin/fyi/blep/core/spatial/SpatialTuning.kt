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
    /**
     * Minimum 3-D movement (m) since the last folded sample before a new RSSI
     * reading is triangulated. Standing still gives no new geometry, so folding
     * more noise there only makes the estimate wander — this gates it out.
     */
    val minTriangulationStepM: Double = 0.7,
    /**
     * Assumed target wander (m/s). 0 = stationary (finding lost things — the
     * default). Raise it to let the estimate follow a target that moves while you
     * hunt; the cost is a slightly less steady fix.
     */
    val targetDriftMps: Double = 0.0,

    // ── particle filter ───────────────────────────────────────────────────────
    /** Number of particles in the recursive Bayesian filter. */
    val particleCount: Int = 600,
    /** Assumed RSSI measurement noise (dB) — how much the filter trusts a sample. */
    val measurementSigmaDb: Double = 3.5,
    /** Per-step particle jitter (m) — exploration vs. steadiness of the cloud. */
    val particleJitterM: Double = 0.3,

    // ── reporting gates ───────────────────────────────────────────────────────
    /** Path bounding-box diagonal (m) required before any estimate is offered. */
    val minSpreadM: Double = 1.5,
    /** Filter confidence required before a *position* (not just a bearing) is reported. */
    val reportConfidence: Float = 0.30f,

    // ── online path-loss calibration ──────────────────────────────────────────
    /** Only refit path-loss when the filter is at least this confident. */
    val calibrateConfidence: Float = 0.5f,
    /** Samples between path-loss refits. */
    val calibrateEvery: Int = 15,
    /** Fraction of each new fit eased in (low = smooth, high = reactive). */
    val calibrateEase: Double = 0.25,

    // ── vertical / floors ─────────────────────────────────────────────────────
    /** Assumed storey height (m) — one grid level and the floor-delta unit. */
    val floorHeightM: Double = 3.0,

    // ── spatial-memory grid + warmest-spot recovery ───────────────────────────
    /** Heat-grid cell size (m) within a floor. */
    val gridCellM: Double = 1.0,
    /** Must be this far (m) off the warmest cell before it points you back. */
    val warmestMinOffsetM: Double = 1.5,
    /** Signal must drop this many dB below the warmest spot to trigger recovery. */
    val recoverDb: Double = 6.0,

    // ── angular signal field (compass direction-finding) ──────────────────────
    /** Heading bins for the swept RSSI field. */
    val angularBins: Int = 24,
    /** EMA weight for new readings per bin (0.5 = equal blend). */
    val angularBinEma: Double = 0.5,
    /** Fraction of bins that must be sampled for full sweep coverage (~290°). */
    val angularCoverageFraction: Double = 0.8,
    /** Front/back dB difference that counts as a fully peaked field. */
    val angularPeakednessDb: Double = 5.0,
    /** While the signal is cooling (you've walked past), travel (m) over which a
     *  swept bin decays to half — short = re-sweep soon after overshooting. */
    val angularStaleHalfLifeM: Double = 3.0,

    // ── guidance thresholds ───────────────────────────────────────────────────
    /** Within this many degrees of a bearing reads as "straight ahead". */
    val aheadDeg: Double = 22.0,
    /** Swept-bearing confidence required before the signal cue is trusted. */
    val signalMinConfidence: Float = 0.35f,
    /** Triangulated-target confidence required before its turn-by-turn is shown. */
    val guidanceMinConfidence: Float = 0.4f,
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

    // Swift-friendly factory (Kotlin's all-default constructor exports no zero-arg init).
    companion object {
        fun default() = SpatialTuning()
    }
}
