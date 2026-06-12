package fyi.blep.core.spatial

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * The rasterised fog-of-war: every directional reveal (stand here, face there,
 * read this much signal) stamps the grid cells inside its cone with a **running
 * average** of strength — so overlapping reveals blend instead of stacking, and
 * the UI can paint smooth fog with one soft splat per cell rather than visible
 * wedge edges.
 *
 * The average is bounded-window (recent samples dominate after ~[AVG_WINDOW]
 * hits), so the map keeps adapting as you re-cover ground with fresh readings.
 */
class SignalFog(
    private val cellM: Double = 1.0,
    private val reachM: Double = 8.0,
    private val halfAngleRad: Double = 0.73, // ≈ 42° half-width, the body-shield lobe
) {

    class Cell(val x: Double, val y: Double) {
        var strength01 = 0.0
        var hits = 0
        var lastMs = 0L
    }

    private val cells = HashMap<Long, Cell>()

    fun reset() = cells.clear()

    val size: Int get() = cells.size

    /** Stamps one reveal cone into the grid, averaging into covered cells. */
    fun stamp(pos: Vec2, bearingRad: Double, strength01: Double, nowMs: Long) {
        val gx0 = floor((pos.x - reachM) / cellM).toInt()
        val gx1 = ceil((pos.x + reachM) / cellM).toInt()
        val gy0 = floor((pos.y - reachM) / cellM).toInt()
        val gy1 = ceil((pos.y + reachM) / cellM).toInt()
        for (gx in gx0..gx1) {
            for (gy in gy0..gy1) {
                val cx = (gx + 0.5) * cellM
                val cy = (gy + 0.5) * cellM
                val dx = cx - pos.x
                val dy = cy - pos.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > reachM) continue
                // The cell under your feet always counts; beyond that only the cone ahead.
                if (dist > cellM && abs(angleDelta(bearingOf(Vec2(dx, dy)), bearingRad)) > halfAngleRad) continue
                val k = (gx.toLong() and 0xFFFFFFFFL) shl 32 or (gy.toLong() and 0xFFFFFFFFL)
                val c = cells.getOrPut(k) { Cell(cx, cy) }
                val window = if (c.hits < AVG_WINDOW) c.hits + 1 else AVG_WINDOW
                c.strength01 += (strength01 - c.strength01) / window
                c.hits++
                c.lastMs = nowMs
            }
        }
    }

    /** Every fogged cell — the smooth map layer the UI splats. */
    fun all(): Collection<Cell> = cells.values

    private companion object {
        const val AVG_WINDOW = 6
    }
}
