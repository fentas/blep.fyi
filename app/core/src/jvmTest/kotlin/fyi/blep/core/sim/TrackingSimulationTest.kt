package fyi.blep.core.sim

import fyi.blep.core.spatial.MotionSample
import fyi.blep.core.spatial.SpatialGuidance
import fyi.blep.core.spatial.SpatialTracker
import fyi.blep.core.spatial.SpatialTuning
import fyi.blep.core.spatial.Vec2
import fyi.blep.core.spatial.angleDelta
import fyi.blep.core.spatial.bearingOf
import fyi.blep.core.tracking.TrackingPhase
import fyi.blep.core.tracking.TrackingSession
import fyi.blep.core.tracking.TrackingTuning
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Closed-loop tracking simulator: a virtual user **reads the on-screen guidance
 * and does what it says** (turns / walks), while a body-shielding RSSI model +
 * compass + step odometry feed the real [TrackingSession] + [SpatialTracker].
 *
 * It runs a spread of scenarios — line-of-sight, behind you, far, through a wall,
 * up a slope, a different floor, noisy — and reports how efficiently (and whether)
 * the guidance leads you there. Some scenarios are *expected* to be unsolvable
 * (you can't walk through a ceiling), so it's report-first; a timeout bounds each.
 *
 *     ./gradlew :core:jvmTest --tests '*TrackingSimulationTest*'
 */
class TrackingSimulationTest {

    /** An attenuating wall segment between (x1,y1) and (x2,y2). */
    private class Wall(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val db: Double)

    // ── world + body-shielding signal model ──────────────────────────────────
    private class World(
        val tx: Double, val ty: Double, val tz: Double = 0.0,
        val wall: Wall? = null,
        val slope: Double = 0.0,        // ground rises this much per metre walked north
        val txAt1m: Double = -59.0,
        val pathLossN: Double = 2.5,
        val shieldDb: Double = 10.0,    // front/back attenuation from your body
        val noiseDb: Double = 1.2,
    ) {
        var x = 0.0; var y = 0.0; var heading = 0.0
        private var i = 0
        val z get() = slope * y         // your altitude follows the ground

        fun distance() = sqrt((x - tx) * (x - tx) + (y - ty) * (y - ty) + (z - tz) * (z - tz))

        /** Integer dBm: path loss + directional body shielding + wall + noise. */
        fun rssi(): Int {
            val d = max(distance(), 0.4)
            val pathLoss = txAt1m - 10.0 * pathLossN * log10(d)
            val toTarget = bearingOf(Vec2(tx - x, ty - y))
            val shield = -shieldDb * (1 - cos(angleDelta(toTarget, heading))) / 2.0
            val blocked = if (wall != null && crosses(x, y, tx, ty, wall.x1, wall.y1, wall.x2, wall.y2)) -wall.db else 0.0
            val noise = noiseDb * sin(i * 1.3) + 0.5 * sin(i * 0.37)
            i++
            return (pathLoss + shield + blocked + noise).roundToInt()
        }
    }

    // ── the virtual user: read the screen, act ───────────────────────────────
    private val degRx = Regex("(\\d+)")

    /** (turnRadians, stepMetres) from the guidance the screen shows + how close we feel. */
    private fun act(phase: TrackingPhase, instruction: String?): Pair<Double, Double> {
        if (phase == TrackingPhase.CALIBRATION || phase == TrackingPhase.COMPLETE) return 0.0 to 0.0
        val near = phase == TrackingPhase.PINPOINT // proximity says we're close → small steps
        val fwd = if (near) STEP * 0.35 else STEP

        if (instruction != null) {
            val s = instruction.lowercase()
            if (s.startsWith("facing") || s.startsWith("straight") || s.contains("walk")) return 0.0 to fwd
            if (s.startsWith("almost")) return 0.0 to STEP * 0.3
            val deg = degRx.find(instruction)?.value?.toDoubleOrNull()
            if (deg != null) {
                val dir = if (s.contains("right")) 1.0 else -1.0
                val turn = dir * min(deg, MAX_TURN_DEG) * PI / 180.0
                val step = if (deg <= MAX_TURN_DEG) fwd * 0.6 else 0.0 // ease forward while correcting
                return turn to step
            }
        }
        return (SWEEP_DEG * PI / 180.0) to 0.0 // no direction yet → keep turning to search
    }

    private data class Result(
        val name: String, val initialM: Double, val minDistanceM: Double, val reachTick: Int,
        val walkedToReachM: Double, val walkedM: Double,
    ) {
        val solved get() = reachTick >= 0
        val efficiency get() = (if (solved) walkedToReachM else walkedM) / max(initialM, 0.1)
    }

    private fun run(
        name: String, world: World,
        tuning: TrackingTuning = TrackingTuning(),
        spatialTuning: SpatialTuning = SpatialTuning(),
    ): Result {
        val session = TrackingSession(tuning)
        val spatial = SpatialTracker(spatialTuning)
        val initial = world.distance()
        var t = 0L; var walked = 0.0; var walkedToReach = 0.0; var minD = initial; var reachTick = -1; var lastStep = 0.0

        for (tick in 0 until MAX_TICKS) {
            val rssi = world.rssi()
            val motion = MotionSample(
                timeMs = t,
                headingRad = world.heading + 0.035 * sin(tick * 0.7), // ~2° compass jitter
                stepDistanceM = lastStep,
                moving = lastStep > 0.0,
                relativeAltitudeM = world.z,
            )
            val status = session.onSample(rssi, t, motion)
            val snap = spatial.update(rssi.toDouble(), motion)
            val instruction = SpatialGuidance.instruction(snap)

            val d = world.distance()
            if (d < minD) minD = d
            if (reachTick < 0 && d <= REACH_M) { reachTick = tick; walkedToReach = walked }
            if (status.phase == TrackingPhase.COMPLETE) break

            val (turn, step) = act(status.phase, instruction)
            world.heading += turn
            world.x += sin(world.heading) * step
            world.y += cos(world.heading) * step
            walked += step; lastStep = step
            t += DT_MS
        }
        return Result(name, initial, minD, reachTick, walkedToReach, walked)
    }

    @Test
    fun simulation_suite() {
        val scenarios = listOf(
            "ahead 5 m" to World(0.0, 5.0),
            "behind 8 m" to World(0.0, -8.0),
            "to the side 6 m" to World(6.0, 0.0),
            "diagonal 14 m" to World(10.0, 10.0),
            "far 20 m" to World(0.0, 20.0),
            "through a wall" to World(0.0, 9.0, wall = Wall(-4.0, 4.5, 4.0, 4.5, db = 16.0)),
            "up a slope" to World(0.0, 12.0, tz = 1.8, slope = 0.15),
            "one floor up" to World(4.0, 0.0, tz = 3.0),  // needs stairs — expected to fail
            "noisy room" to World(0.0, 8.0, noiseDb = 4.0),
        )
        val results = scenarios.map { (n, w) -> run(n, w) }

        fun secs(v: Int) = if (v < 0) "timeout" else "${"%.0f".format(v * DT_MS / 1000.0)}s"
        println("\n── tracking simulation (timeout ${MAX_TICKS * DT_MS / 1000}s) ──────────────────────────")
        println("scenario           straight   closest   found     walked   path-eff   solved")
        results.forEach {
            println(
                "%-16s   %5.1f m   %5.1f m   %7s   %5.1f m   %5.1f×    %s".format(
                    it.name, it.initialM, it.minDistanceM, secs(it.reachTick), it.walkedM,
                    it.efficiency, if (it.solved) "✓" else "✗",
                ),
            )
        }
        val solved = results.count { it.solved }
        val eff = results.filter { it.solved }.map { it.efficiency }
        println("───────────────────────────────  solved %d/%d   avg path-eff %.1f×  ──".format(solved, results.size, if (eff.isEmpty()) 0.0 else eff.average()))
        println()

        // Report-first: just make sure the simplest line-of-sight case works, so a
        // gross regression still fails the build. The rest is for tuning.
        val ahead = results.first { it.name.startsWith("ahead") }
        assertTrue(ahead.solved, "even a target straight ahead wasn't found (closest ${"%.1f".format(ahead.minDistanceM)} m)")
    }

    private companion object {
        const val REACH_M = 2.0   // within arm's reach counts as found
        const val STEP = 0.6
        const val MAX_TURN_DEG = 30.0
        const val SWEEP_DEG = 18.0
        const val MAX_TICKS = 450 // ≈ 3 min timeout per scenario
        const val DT_MS = 400L
    }
}

/** Do segments (ax,ay)-(bx,by) and (x1,y1)-(x2,y2) intersect? */
private fun crosses(
    ax: Double, ay: Double, bx: Double, by: Double,
    x1: Double, y1: Double, x2: Double, y2: Double,
): Boolean {
    fun ccw(px: Double, py: Double, qx: Double, qy: Double, rx: Double, ry: Double) =
        (ry - py) * (qx - px) > (qy - py) * (rx - px)
    return ccw(ax, ay, x1, y1, x2, y2) != ccw(bx, by, x1, y1, x2, y2) &&
        ccw(ax, ay, bx, by, x1, y1) != ccw(ax, ay, bx, by, x2, y2)
}
