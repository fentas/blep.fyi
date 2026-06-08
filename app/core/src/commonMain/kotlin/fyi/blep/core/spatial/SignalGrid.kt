package fyi.blep.core.spatial

import kotlin.math.floor

/**
 * A coarse 3-D grid of the signal you've walked through: every ~[cellM] of travel
 * drops into a cell that holds a smoothed RSSI for that spot. It's the tracker's
 * spatial memory — the strongest cell is the warmest place you've stood, so when
 * you wander off you can be guided straight back to it instead of orbiting.
 *
 * It's 3-D, but the vertical cell is a whole floor ([zCellM] ≈ a storey), so
 * within a level it's effectively a 2-D heat-grid and across levels it keeps
 * floors apart.
 */
class SignalGrid(private val cellM: Double = 1.0, private val zCellM: Double = 3.0) {

    class Cell(val x: Double, val y: Double, val z: Double, var rssi: Double)

    private val cells = HashMap<Long, Cell>()

    fun reset() = cells.clear()

    /** Folds a reading at [pos]/[altitude] into its cell (EMA-smoothed). */
    fun update(pos: Vec2, altitude: Double, rssi: Double) {
        val k = key(pos.x, pos.y, altitude)
        val c = cells[k]
        if (c == null) cells[k] = Cell(pos.x, pos.y, altitude, rssi)
        else c.rssi = c.rssi * 0.6 + rssi * 0.4
    }

    /** The warmest cell visited so far, or null if empty. */
    fun strongest(): Cell? {
        var best: Cell? = null
        for (c in cells.values) if (best == null || c.rssi > best.rssi) best = c
        return best
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
