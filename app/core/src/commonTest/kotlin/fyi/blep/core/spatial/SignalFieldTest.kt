package fyi.blep.core.spatial

import kotlin.math.log10
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The fog-of-war signal field: cells must latch the unshielded signal level at a
 * spot (body shielding mustn't paint the map), and a genuine obstruction — a
 * wall casting a ~16 dB shadow toward the target — must show up as strongly
 * negative residuals in exactly the shadowed cells. This is the JVM proof that
 * "see the wall in the field" works before any UI exists.
 */
class SignalFieldTest {

    // ── body shielding vs the up-biased cell EMA ─────────────────────────────
    @Test
    fun cellLatchesUnshieldedLevelRegardlessOfHeading() {
        val grid = SignalGrid(cellM = 1.0)
        val spot = Vec2(2.0, 2.0)
        // Standing on one spot turning: facing the target reads −60, facing away
        // the body shields ~10 dB → −70. Alternate plenty of both.
        repeat(12) {
            grid.update(spot, 0.0, -70.0, nowMs = it * 1000L)
            grid.update(spot, 0.0, -60.0, nowMs = it * 1000L)
        }
        val cell = grid.strongest()!!
        assertTrue(cell.rssi > -63.0, "cell should latch near the unshielded −60, was ${cell.rssi}")
    }

    // ── the wall experiment ──────────────────────────────────────────────────
    @Test
    fun wallShadowAppearsInResiduals() {
        // World: target at (0, 9); a wall along y = 4.5 attenuating 16 dB. The
        // same log-distance model generates the readings and (as the calibrated
        // PathLossModel) computes the residuals — like the live calibrator
        // converging on the true environment.
        val model = PathLossModel(rssiAt1m = -59.0, exponent = 2.5)
        val target = Vec2(0.0, 9.0)
        val wallY = 4.5
        fun shadowed(p: Vec2): Boolean {
            // Does the segment p→target cross the wall line? (wall is infinite in x
            // here — the worst case for "can the field still see it".)
            return (p.y - wallY) * (target.y - wallY) < 0
        }
        fun rssiAt(p: Vec2): Double {
            val d = max((p - target).length, 0.5)
            val base = -59.0 - 10.0 * 2.5 * log10(d)
            return base + (if (shadowed(p)) -16.0 else 0.0)
        }

        // Walk a lattice on both sides of the wall, with a little deterministic
        // jitter (±1.5 dB) and a few shielded (low) repeats per spot.
        val grid = SignalGrid(cellM = 1.0)
        var t = 0L
        var i = 0
        for (gy in 0..8) {
            for (gx in -3..3) {
                val p = Vec2(gx + 0.5, gy + 0.5)
                val noise = 1.5 * kotlin.math.sin(i * 1.3)
                grid.update(p, 0.0, rssiAt(p) + noise, nowMs = t)
                grid.update(p, 0.0, rssiAt(p) - 9.0, nowMs = t) // body-shielded repeat
                grid.update(p, 0.0, rssiAt(p) - noise, nowMs = t)
                t += 500; i++
            }
        }

        // Residuals exactly as SpatialTracker computes them.
        val cells = grid.cellsOnFloor(0.0)
        assertTrue(cells.size >= 50, "lattice should populate the grid")
        val residuals = cells.map { c ->
            val p = Vec2(c.x, c.y)
            Triple(p, c.rssi - model.expectedRssi((p - target).length), shadowed(p))
        }
        val shadowRes = residuals.filter { it.third }.map { it.second }
        val clearRes = residuals.filter { !it.third }.map { it.second }
        assertTrue(shadowRes.isNotEmpty() && clearRes.isNotEmpty())

        val shadowMean = shadowRes.average()
        val clearMean = clearRes.average()
        // The wall must separate cleanly: shadowed cells read far below the model,
        // clear cells close to it — with a wide gap between the two populations.
        assertTrue(shadowMean < -10.0, "shadow cells should read ≪ expected, mean was $shadowMean")
        assertTrue(clearMean > -5.0, "clear cells should sit near expected, mean was $clearMean")
        assertTrue(clearMean - shadowMean > 8.0, "wall contrast too weak: clear $clearMean vs shadow $shadowMean")

        // And per-cell classification at the UI's −8 dB shadow threshold should be
        // essentially clean on both sides.
        val misShadow = residuals.count { it.third && it.second >= -8.0 }
        val misClear = residuals.count { !it.third && it.second < -8.0 }
        assertTrue(misShadow <= residuals.count { it.third } / 5, "too many shadow cells read clear: $misShadow")
        assertTrue(misClear <= residuals.count { !it.third } / 5, "too many clear cells read shadowed: $misClear")
    }

    // ── the predicted-field gradient ─────────────────────────────────────────
    @Test
    fun predictedGradientFallsOffMonotonically() {
        // The model's expected strength must decrease with distance — that's the
        // warm→cold wash the UI paints around the estimate.
        val tuning = SpatialTuning()
        val model = PathLossModel.from(tuning)
        val strengths = (0..8).map { tuning.strength01(model.expectedRssi(it * 4.0)) }
        assertTrue(strengths.first() > strengths.last(), "field should cool with distance")
        for (i in 1 until strengths.size) {
            assertTrue(strengths[i] <= strengths[i - 1], "strength rose at stop $i: $strengths")
        }
    }

    // ── recency + hits fold into confidence via the tracker ─────────────────
    @Test
    fun fieldCellsCarryConfidenceAndFadeWithAge() {
        val grid = SignalGrid(cellM = 1.0)
        grid.update(Vec2(0.5, 0.5), 0.0, -60.0, nowMs = 0L)
        repeat(5) { grid.update(Vec2(5.5, 0.5), 0.0, -70.0, nowMs = 100_000L) }
        val cells = grid.cellsOnFloor(0.0).sortedBy { it.x }
        assertTrue(cells[0].hits == 1 && cells[1].hits == 5)
        assertTrue(cells[1].lastMs > cells[0].lastMs)
    }
}
