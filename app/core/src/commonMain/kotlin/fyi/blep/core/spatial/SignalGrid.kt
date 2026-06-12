package fyi.blep.core.spatial

import kotlin.math.floor

/**
 * A coarse 3-D grid of the signal you've walked through: every ~[cellM] of travel
 * drops into a cell that holds the best signal read at that spot. It's the
 * tracker's spatial memory — the strongest cell is the warmest place you've
 * stood (recovery guidance), and the whole explored set is the **fog-of-war
 * signal field** the radar renders.
 *
 * Cell smoothing is an **up-biased EMA** (fast up, slow down): the body shields
 * ~10 dB depending on which way you face, so plain averaging would paint a map
 * of where you happened to be turned. Latching near the best reading converges
 * on the unshielded level at a spot regardless of heading — while a spot that's
 * genuinely in shadow (a wall between it and the target) stays low at *every*
 * heading, so real obstructions still show.
 *
 * It's 3-D, but the vertical cell is a whole floor ([zCellM] ≈ a storey), so
 * within a level it's effectively a 2-D heat-grid and across levels it keeps
 * floors apart.
 */
class SignalGrid(private val cellM: Double = 1.0, private val zCellM: Double = 3.0) {

    class Cell(val x: Double, val y: Double, val z: Double, var rssi: Double) {
        var hits: Int = 1
        var lastMs: Long = 0L
    }

    private val cells = HashMap<Long, Cell>()

    fun reset() = cells.clear()

    /** Folds a reading at [pos]/[altitude] into its cell (up-biased EMA). */
    fun update(pos: Vec2, altitude: Double, rssi: Double, nowMs: Long = 0L) {
        val k = key(pos.x, pos.y, altitude)
        val c = cells[k]
        if (c == null) {
            cells[k] = Cell(pos.x, pos.y, altitude, rssi).also { it.lastMs = nowMs }
        } else {
            c.rssi = if (rssi > c.rssi) c.rssi * 0.5 + rssi * 0.5 else c.rssi * 0.88 + rssi * 0.12
            c.hits++
            c.lastMs = nowMs
        }
    }

    /** The warmest cell visited so far, or null if empty. */
    fun strongest(): Cell? {
        var best: Cell? = null
        for (c in cells.values) if (best == null || c.rssi > best.rssi) best = c
        return best
    }

    /** Every explored cell on the same floor as [zRef] — the fog-of-war layer. */
    fun cellsOnFloor(zRef: Double): List<Cell> {
        val gz = floor(zRef / zCellM).toInt()
        return cells.values.filter { floor(it.z / zCellM).toInt() == gz }
    }

    private fun key(x: Double, y: Double, z: Double): Long {
        val gx = floor(x / cellM).toInt()
        val gy = floor(y / cellM).toInt()
        val gz = floor(z / zCellM).toInt()
        // pack three signed 21-bit indices (~±1M cells per axis) into a Long
        return ((gx.toLong() and 0x1FFFFF) shl 42) or
            ((gy.toLong() and 0x1FFFFF) shl 21) or
            (gz.toLong() and 0x1FFFFF)
    }
}
