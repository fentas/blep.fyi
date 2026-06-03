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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
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
        var tx: Double, var ty: Double, val tz: Double = 0.0,
        val wall: Wall? = null,
        val slope: Double = 0.0,        // ground rises this much per metre walked north
        val vx: Double = 0.0, val vy: Double = 0.0, // the device itself moving (m/s)
        val canopyDb: Double = 0.0,     // forest: position-dependent shadowing/fading
        val txAt1m: Double = -59.0,
        val pathLossN: Double = 2.5,
        val shieldDb: Double = 10.0,    // front/back attenuation from your body
        val noiseDb: Double = 1.2,
    ) {
        var x = 0.0; var y = 0.0; var heading = 0.0
        private var i = 0
        val z get() = slope * y         // your altitude follows the ground

        fun distance() = sqrt((x - tx) * (x - tx) + (y - ty) * (y - ty) + (z - tz) * (z - tz))

        fun advanceTarget(dtSec: Double) { tx += vx * dtSec; ty += vy * dtSec }

        /** Integer dBm: path loss + directional body shielding + wall + canopy + noise. */
        fun rssi(): Int {
            val d = max(distance(), 0.4)
            val pathLoss = txAt1m - 10.0 * pathLossN * log10(d)
            val toTarget = bearingOf(Vec2(tx - x, ty - y))
            val shield = -shieldDb * (1 - cos(angleDelta(toTarget, heading))) / 2.0
            val blocked = if (wall != null && crosses(x, y, tx, ty, wall.x1, wall.y1, wall.x2, wall.y2)) -wall.db else 0.0
            // Canopy: trees shadow the signal in a position-dependent fading pattern.
            val canopy = if (canopyDb > 0.0) canopyDb * sin(x * 0.8 + 1.3) * sin(y * 0.9) else 0.0
            val noise = noiseDb * sin(i * 1.3) + 0.5 * sin(i * 0.37)
            i++
            return (pathLoss + shield + blocked + canopy + noise).roundToInt()
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
        /** A real success: reached it AND didn't wander to get there. Walking 77 m
         *  for an 8 m target counts as a fail even though you technically arrived. */
        val clean get() = solved && efficiency < CLEAN_EFF
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
            val instruction = SpatialGuidance.instruction(snap, spatialTuning)

            val d = world.distance()
            if (d < minD) minD = d
            if (reachTick < 0 && d <= REACH_M) { reachTick = tick; walkedToReach = walked }
            if (status.phase == TrackingPhase.COMPLETE) break

            if (DEBUG && name.startsWith("diagonal") && tick in 38..70 && tick % 2 == 0) {
                println("t=%2d hd=%4.0f° pos=(%4.1f,%4.1f) %-26s tgtC=%.2f sigC=%.2f d=%4.1f".format(
                    tick, (world.heading * 180 / PI) % 360, world.x, world.y,
                    instruction ?: "(${status.guidance.title})", snap.target.confidence, snap.signalBearingConfidence, d))
            }
            val (turn, step) = act(status.phase, instruction)
            world.heading += turn
            world.x += sin(world.heading) * step
            world.y += cos(world.heading) * step
            world.advanceTarget(DT_MS / 1000.0) // the device may be moving too
            walked += step; lastStep = step
            t += DT_MS
        }
        return Result(name, initial, minD, reachTick, walkedToReach, walked)
    }

    /** The scenario spread — fresh (mutable) worlds each call, so a trial can't
     *  pollute the next. Seeded so "randomized" is reproducible across trials. */
    private fun scenarios(): List<Pair<String, World>> {
        val rnd = Random(7)
        val rAng = rnd.nextDouble(0.0, 2 * PI)
        val rDist = rnd.nextDouble(7.0, 18.0)
        val rNoise = rnd.nextDouble(1.0, 3.0)
        return listOf(
            "ahead 5 m" to World(0.0, 5.0),
            "behind 8 m" to World(0.0, -8.0),
            "to the side 6 m" to World(6.0, 0.0),
            "diagonal 14 m" to World(10.0, 10.0),
            "far 20 m" to World(0.0, 20.0),
            "extra-far 35 m" to World(0.0, 35.0),
            "through a wall" to World(0.0, 9.0, wall = Wall(-4.0, 4.5, 4.0, 4.5, db = 16.0)),
            "up a slope" to World(0.0, 12.0, tz = 1.8, slope = 0.15),
            "forest 12 m" to World(0.0, 12.0, canopyDb = 6.0, noiseDb = 2.5),
            "noisy room" to World(0.0, 8.0, noiseDb = 4.0),
            "randomized" to World(sin(rAng) * rDist, cos(rAng) * rDist, noiseDb = rNoise),
            "one floor up" to World(4.0, 0.0, tz = 3.0),       // needs stairs — expected fail
            "moving device" to World(0.0, 8.0, vx = 0.25),     // edge case — solve last
        )
    }

    private fun runSuite(tuning: TrackingTuning, spatialTuning: SpatialTuning): List<Result> =
        scenarios().map { (n, w) -> run(n, w, tuning, spatialTuning) }

    @Test
    fun simulation_suite() {
        val results = runSuite(TrackingTuning(), SpatialTuning())

        fun secs(v: Int) = if (v < 0) "timeout" else "${"%.0f".format(v * DT_MS / 1000.0)}s"
        println("\n── tracking simulation (timeout ${MAX_TICKS * DT_MS / 1000}s) ──────────────────────────")
        println("scenario           straight   closest   found     walked   path-eff   result")
        results.forEach {
            val mark = if (it.clean) "★ clean" else if (it.solved) "~ wander" else "✗ fail"
            println(
                "%-16s   %5.1f m   %5.1f m   %7s   %5.1f m   %5.1f×    %s".format(
                    it.name, it.initialM, it.minDistanceM, secs(it.reachTick), it.walkedM, it.efficiency, mark,
                ),
            )
        }
        val clean = results.count { it.clean }
        val reached = results.count { it.solved }
        val eff = results.filter { it.solved }.map { it.efficiency }
        println("──────────────────  clean %d/%d  ·  reached %d/%d  ·  avg path-eff %.1f×  ──".format(
            clean, results.size, reached, results.size, if (eff.isEmpty()) 0.0 else eff.average()))
        println()

        // Report-first: only assert the simplest case is a CLEAN solve, so a gross
        // regression fails the build. "Reached by wandering" doesn't count.
        val ahead = results.first { it.name.startsWith("ahead") }
        assertTrue(ahead.clean, "straight-ahead target not cleanly found (closest ${"%.1f".format(ahead.minDistanceM)} m, ${"%.1f".format(ahead.efficiency)}× path)")
    }

    /**
     * Held-out generalisation: many *randomly generated* worlds (bearing, distance,
     * noise, sometimes a wall or slope), none of them the hand-picked 13. Tells us
     * whether the tuning actually generalises or just overfits the fixed suite.
     * Defaults to 40 worlds; override with ROBUST_N. All same-floor (vertical is
     * the known-unsolvable case), so the bar is a high clean rate.
     */
    @Test
    fun robustness_suite() {
        val n = (System.getenv("ROBUST_N") ?: "40").toIntOrNull() ?: 40
        val rnd = Random(System.getenv("ROBUST_SEED")?.toLongOrNull() ?: 1234L)
        val results = (0 until n).map { k ->
            val ang = rnd.nextDouble(0.0, 2 * PI)
            val dist = rnd.nextDouble(4.0, 28.0)
            val tx = sin(ang) * dist; val ty = cos(ang) * dist
            val noise = rnd.nextDouble(1.0, 4.0)
            // ~30% have an attenuating wall roughly between you and the target.
            val wall = if (rnd.nextDouble() < 0.3) {
                val mx = tx / 2; val my = ty / 2; val s = 4.0
                Wall(mx - ty / dist * s, my + tx / dist * s, mx + ty / dist * s, my - tx / dist * s, db = rnd.nextDouble(8.0, 18.0))
            } else null
            val slope = if (rnd.nextDouble() < 0.2) rnd.nextDouble(0.05, 0.18) else 0.0
            run("rnd#$k", World(tx, ty, slope = slope, canopyDb = if (noise > 3.0) 5.0 else 0.0, wall = wall, noiseDb = noise))
        }
        val clean = results.count { it.clean }
        val reached = results.count { it.solved }
        val eff = results.filter { it.solved }.map { it.efficiency }
        println("\n── robustness ($n random worlds) ──")
        println("clean %d/%d (%.0f%%) · reached %d/%d · avg path-eff %.1f×".format(
            clean, n, 100.0 * clean / n, reached, n, if (eff.isEmpty()) 0.0 else eff.average()))
        // The worst few, to see what geometry still trips it up.
        results.filter { !it.clean }.sortedByDescending { it.efficiency }.take(6).forEach {
            println("  %-7s straight %.1f m  closest %.1f m  eff %.1f×  %s".format(
                it.name, it.initialM, it.minDistanceM, it.efficiency, if (it.solved) "wander" else "fail"))
        }
        // Soft floor: a gross regression (tuning that doesn't generalise) fails here.
        assertTrue(clean >= n * 0.65, "random-world clean rate regressed: $clean/$n")
    }

    /** A single dial we can turn: a [name]d tuning field, its [lo]..[hi] search
     *  range and current [def]ault. Trial 0 pins every dial to its default. */
    private class Dial(val name: String, val lo: Double, val hi: Double, val def: Double)

    private data class Trial(val score: Double, val clean: Int, val reached: Int, val eff: Double, val knobs: Map<String, Double>)

    /**
     * Chaos mode: turn every knob to a random value, run the whole suite, score it,
     * and after N trials report which dials actually move the needle (top-third vs
     * bottom-third mean) so we know where to look. Opt-in (it's slow):
     *
     *     CHAOS_N=60 ./gradlew :core:jvmTest --tests '*TrackingSimulationTest.chaos*'
     *     CHAOS_N=60 CHAOS_SEED=7 ./gradlew ...   # different random draw
     */
    @Test
    fun chaos_search() {
        val trials = (System.getenv("CHAOS_N") ?: "0").toIntOrNull() ?: 0
        if (trials <= 0) return // off by default — doesn't bloat CI
        val rnd = Random(System.getenv("CHAOS_SEED")?.toLongOrNull() ?: 42L)

        val out = ArrayList<Trial>(trials)
        repeat(trials) { k ->
            val v = DIALS.associate { it.name to if (k == 0) it.def else rnd.nextDouble(it.lo, it.hi) }
            val st = SpatialTuning(
                pathLossExponent = v.getValue("pathLossExponent"),
                minTriangulationStepM = v.getValue("minTriangulationStepM"),
                measurementSigmaDb = v.getValue("measurementSigmaDb"),
                particleJitterM = v.getValue("particleJitterM"),
                minSpreadM = v.getValue("minSpreadM"),
                reportConfidence = v.getValue("reportConfidence").toFloat(),
                warmestMinOffsetM = v.getValue("warmestMinOffsetM"),
                recoverDb = v.getValue("recoverDb"),
                angularBinEma = v.getValue("angularBinEma"),
                angularCoverageFraction = v.getValue("angularCoverageFraction"),
                angularPeakednessDb = v.getValue("angularPeakednessDb"),
                aheadDeg = v.getValue("aheadDeg"),
                signalMinConfidence = v.getValue("signalMinConfidence").toFloat(),
                guidanceMinConfidence = v.getValue("guidanceMinConfidence").toFloat(),
            )
            val tt = TrackingTuning(
                emaAlpha = v.getValue("emaAlpha"),
                rssiFar = v.getValue("rssiFar"),
                rssiNear = v.getValue("rssiNear"),
            )
            val res = runSuite(tt, st)
            val clean = res.count { it.clean }
            val reached = res.count { it.solved }
            val effs = res.filter { it.solved }.map { it.efficiency }
            val avgEff = if (effs.isEmpty()) 9.9 else effs.average()
            // clean solves dominate; path-eff breaks ties (lower = better).
            out += Trial(clean - 0.05 * avgEff, clean, reached, avgEff, v)
        }
        val baseline = out.first() // trial 0 pinned every dial to its default
        out.sortByDescending { it.score }

        println("\n══ chaos search · $trials trials (seed ${System.getenv("CHAOS_SEED") ?: "42"}) ══")
        println("baseline (defaults): clean ${baseline.clean}/13 · reached ${baseline.reached}/13 · eff %.2f×".format(baseline.eff))
        println("\ntop 5 configs:")
        out.take(5).forEach { t ->
            println("  clean ${t.clean}/13 · reached ${t.reached}/13 · eff %.2f×".format(t.eff))
        }
        val best = out.first()
        if (best.clean > baseline.clean || (best.clean == baseline.clean && best.eff < baseline.eff - 0.05)) {
            println("\nbest beat baseline — its dials:")
            DIALS.forEach { d -> println("  %-24s %.3f  (default %.3f)".format(d.name, best.knobs.getValue(d.name), d.def)) }
        }

        // Which dials separate good from bad? Compare top-third vs bottom-third mean,
        // normalised by the dial's range so they're comparable.
        val third = (out.size / 3).coerceAtLeast(1)
        val good = out.take(third); val bad = out.takeLast(third)
        println("\ndials that move the score most (top-third vs bottom-third mean):")
        DIALS.map { d ->
            val g = good.map { it.knobs.getValue(d.name) }.average()
            val b = bad.map { it.knobs.getValue(d.name) }.average()
            Triple(d, g, b)
        }.sortedByDescending { (d, g, b) -> abs(g - b) / (d.hi - d.lo) }
            .take(8)
            .forEach { (d, g, b) ->
                val pull = if (g > b) "↑ higher" else "↓ lower"
                println("  %-24s good≈%.3f bad≈%.3f  → %s helps  (range %.2f..%.2f)".format(
                    d.name, g, b, pull, d.lo, d.hi))
            }
        println("══════════════════════════════════════════════════════════════")
    }

    private companion object {
        const val REACH_M = 2.0   // within arm's reach counts as found
        const val CLEAN_EFF = 3.5 // ≤ this × the straight line = a clean solve (not wandering)
        const val STEP = 0.6
        const val MAX_TURN_DEG = 30.0
        const val SWEEP_DEG = 18.0
        const val MAX_TICKS = 450 // ≈ 3 min timeout per scenario
        const val DT_MS = 400L
        const val DEBUG = false

        /** Every knob the chaos search turns, with its search range and default. */
        val DIALS = listOf(
            // spatial: range model + particle filter
            Dial("pathLossExponent", 2.0, 3.4, 2.5),
            Dial("minTriangulationStepM", 0.4, 1.2, 0.7),
            Dial("measurementSigmaDb", 2.0, 6.0, 3.5),
            Dial("particleJitterM", 0.15, 0.6, 0.3),
            // spatial: reporting gates
            Dial("minSpreadM", 1.0, 2.5, 1.5),
            Dial("reportConfidence", 0.2, 0.45, 0.30),
            // spatial: warmest-spot recovery
            Dial("warmestMinOffsetM", 1.0, 2.5, 1.5),
            Dial("recoverDb", 4.0, 9.0, 6.0),
            // spatial: angular direction-finding
            Dial("angularBinEma", 0.3, 0.7, 0.5),
            Dial("angularCoverageFraction", 0.6, 0.95, 0.8),
            Dial("angularPeakednessDb", 3.0, 8.0, 5.0),
            // guidance thresholds
            Dial("aheadDeg", 15.0, 30.0, 22.0),
            Dial("signalMinConfidence", 0.25, 0.5, 0.35),
            Dial("guidanceMinConfidence", 0.3, 0.5, 0.4),
            // RSSI phase machine (affects PINPOINT small-step + strength mapping)
            Dial("emaAlpha", 0.3, 0.6, 0.45),
            Dial("rssiFar", -95.0, -85.0, -90.0),
            Dial("rssiNear", -64.0, -52.0, -58.0),
        )
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
