package fyi.blep.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fyi.blep.core.spatial.CueKind
import fyi.blep.core.spatial.GuidanceLine
import fyi.blep.core.spatial.SpatialSnapshot
import fyi.blep.core.spatial.Vec2
import fyi.blep.ui.theme.BlepColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import fyi.blep.resources.Res
import fyi.blep.resources.radar_desc
import fyi.blep.resources.radar_warmer
import org.jetbrains.compose.resources.stringResource

/**
 * Abstract, map-less "radar" of the hunt, drawn **heads-up**: the direction you
 * face is always screen-up, so "turn 25° right" in the headline is literally 25°
 * right of the fixed you-chevron. The world (trail, signal fog, start star,
 * predicted target) rotates around you; a small "N" tick on the outer ring keeps
 * north recoverable.
 *
 * One instruction at a time: a single cue ray — driven by the same stabilized
 * [GuidanceLine] as the headline text, so picture and words can't disagree —
 * points where to go (rose = signal/target, amber = back to the warmest spot,
 * labelled "warmer"). The chevron itself never points anywhere but up; its tint
 * is the on-course feedback (green walking toward, red away).
 */
@Composable
fun RadarView(
    snapshot: SpatialSnapshot?,
    pulse: Float,          // 0..1 looping, for the target glow
    line: GuidanceLine? = null,  // the stabilized cue the headline shows — drives the ray
    pointBlank: Boolean = false, // headline says "right here": no ray, no stale metres
    signalLost: Boolean = false, // no fresh RSSI from the target right now
    ink: Color = BlepColors.Ink,    // foreground (rings, labels, "you") — flips for dark
    halo: Color = BlepColors.Cream, // contrast outline behind ink marks
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val warmerLabel = stringResource(Res.string.radar_warmer)
    val radarDesc = stringResource(Res.string.radar_desc)
    val labelStyle = TextStyle(color = ink.copy(alpha = 0.65f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
    Canvas(modifier = modifier.fillMaxSize().semantics { contentDescription = radarDesc }) {
        // The hub ("you") sits a touch below centre so the space ahead — where
        // you're walking — gets most of the canvas.
        val hub = Offset(size.width / 2f, size.height / 2f + size.minDimension * 0.06f)

        // ── world → screen: heads-up (your heading = up), centred on you ─────
        val pts = snapshot?.path ?: emptyList()
        val here = snapshot?.here ?: Vec2.ZERO
        val heading = snapshot?.headingRad ?: 0.0
        val target = snapshot?.target?.position
        var maxR = 3.0 // metres of world fitted around you, min 3 m
        fun include(v: Vec2) {
            val d = sqrt((v.x - here.x) * (v.x - here.x) + (v.y - here.y) * (v.y - here.y))
            if (d > maxR) maxR = d
        }
        pts.forEach { include(it.pos) }
        include(Vec2.ZERO); target?.let(::include)
        val scale = (size.minDimension * 0.40f) / maxR.toFloat()
        val ch = cos(heading).toFloat(); val sh = sin(heading).toFloat()
        // East/north metres → heads-up screen px (rotate the north-up frame by -heading).
        fun toScreen(v: Vec2): Offset {
            val x = ((v.x - here.x) * scale).toFloat()   // east px
            val y = (-(v.y - here.y) * scale).toFloat()  // north px, screen-y down
            return Offset(hub.x + x * ch + y * sh, hub.y - x * sh + y * ch)
        }
        // Screen direction of a world bearing (rad clockwise from north).
        fun dirFor(bearingRad: Double): Offset {
            val a = bearingRad - heading
            return Offset(sin(a).toFloat(), -cos(a).toFloat())
        }

        // ── predicted field: the map triangulation believes in ───────────────
        // Once the target is localised, the calibrated path-loss model predicts
        // the signal everywhere — painted as a radial warm→cold wash centred on
        // the estimate, strengthening with confidence. The measured fog cells
        // (and their shadows) layer on top as ground truth.
        val est0 = snapshot?.target
        val stops = snapshot?.fieldGradient ?: emptyList()
        if (est0?.position != null && stops.size > 1) {
            val tc = toScreen(est0.position!!)
            val spanM = stops.last().distanceM
            val rPx = (spanM * scale).toFloat()
            val conf = est0.confidence
            val colorStops = stops.map { s ->
                (s.distanceM / spanM).toFloat() to
                    signalColor(s.strength01).copy(alpha = (0.10f + 0.22f * conf) * (0.25f + 0.75f * s.strength01))
            }.toTypedArray()
            drawCircle(
                brush = Brush.radialGradient(colorStops = colorStops, center = tc, radius = rPx),
                radius = rPx,
                center = tc,
            )
        }

        // ── fog of war: directional reveals ──────────────────────────────────
        // Every place you've stood + faced paints a wedge ahead of you, tinted by
        // the dBm read there (body shielding makes a reading speak for the cone
        // you face). Walking sweeps a corridor open; turning in place reveals a
        // disc around you — VTT-style. Unexplored space stays plain background,
        // which is the honest amount of knowledge. Drawn under the rings so the
        // instruments stay legible on top of the paint.
        val reveals = snapshot?.reveals ?: emptyList()
        if (reveals.isNotEmpty()) {
            val rPx = (REVEAL_RADIUS_M * scale).toFloat().coerceIn(30f, size.minDimension * 0.45f)
            for (r in reveals) {
                val p = toScreen(r.pos)
                val angleDeg = ((r.bearingRad - heading) * 180.0 / PI).toFloat() - 90f
                val col = signalColor(r.strength01).copy(alpha = 0.65f)
                drawArc(
                    brush = Brush.radialGradient(listOf(col, col.copy(alpha = 0f)), center = p, radius = rPx),
                    startAngle = angleDeg - WEDGE_HALF_DEG,
                    sweepAngle = WEDGE_HALF_DEG * 2f,
                    useCenter = true,
                    topLeft = Offset(p.x - rPx, p.y - rPx),
                    size = Size(rPx * 2f, rPx * 2f),
                )
            }
        }
        // Measured shadow patches on top: a visited cell reading far below the
        // path-loss expectation has something (a wall) blocking it toward the
        // target — darken it so the obstruction shows through the reveal tint.
        val field = snapshot?.field ?: emptyList()
        if (field.isNotEmpty()) {
            val cellR = ((snapshot!!.fieldCellM * scale).toFloat() * 0.9f).coerceIn(8f, 44f)
            for (c in field) {
                if (c.confidence <= 0.05f || (c.residualDb ?: 0.0) >= SHADOW_DB) continue
                val p = toScreen(c.pos)
                val col = ink.copy(alpha = 0.30f + 0.25f * c.confidence)
                drawCircle(
                    brush = Brush.radialGradient(listOf(col, col.copy(alpha = 0f)), center = p, radius = cellR * 1.5f),
                    radius = cellR * 1.5f,
                    center = p,
                )
            }
        }

        // ── range rings: distance from you, snapped to round metres + labelled ──
        val ringColor = ink.copy(alpha = if (signalLost) 0.14f else 0.32f)
        val r1 = niceMeters(maxR / 2.0)
        val r2 = niceMeters(maxR).let { if (it <= r1) r1 * 2.0 else it }
        listOf(r1, r2).forEach { rm ->
            val rPx = (rm * scale).toFloat()
            drawCircle(ringColor, radius = rPx, center = hub, style = Stroke(width = 4f))
            val label = if (rm < 1.0) "${(rm * 100).roundToInt()} cm" else "${rm.roundToInt()} m"
            val layout = measurer.measure(label, labelStyle)
            // sit the label just above the top of its ring
            drawText(layout, topLeft = Offset(hub.x - layout.size.width / 2f, hub.y - rPx - layout.size.height - 1f))
        }
        // North tick on the outer ring, so orientation stays recoverable. Skipped when
        // north is nearly straight up — it would collide with the ring labels there,
        // and "north = forward" carries no information anyway.
        val northDeg = normalizeDeg((-heading * 180.0 / PI).toFloat())
        if (kotlin.math.abs(northDeg) > 14f) {
            val n = hub + dirFor(0.0) * (r2 * scale).toFloat()
            val layout = measurer.measure("N", TextStyle(color = ink.copy(alpha = 0.45f), fontSize = 11.sp, fontWeight = FontWeight.Bold))
            drawText(layout, topLeft = Offset(n.x - layout.size.width / 2f, n.y - layout.size.height / 2f))
        }

        // ── the trail, coloured per segment by signal ────────────────────────
        for (k in 1 until pts.size) {
            val a = pts[k - 1]; val b = pts[k]
            drawLine(
                color = signalColor((a.strength01 + b.strength01) / 2f),
                start = toScreen(a.pos), end = toScreen(b.pos),
                strokeWidth = 8f, cap = StrokeCap.Round,
            )
        }

        // ── start marker (a four-point star) ─────────────────────────────────
        drawStar(toScreen(Vec2.ZERO), r = 13f, color = ink.copy(alpha = 0.55f))

        // ── predicted target: glow + confidence ring + distance ──────────────
        val est = snapshot?.target
        if (est != null && est.position != null && est.confidence > 0.05f) {
            val tc = toScreen(est.position!!)
            val glow = (18f + 10f * pulse) * (0.6f + est.confidence)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(BlepColors.Pink.copy(alpha = 0.55f * est.confidence), Color.Transparent),
                    center = tc, radius = glow,
                ),
                radius = glow, center = tc,
            )
            drawCircle(BlepColors.Pink.copy(alpha = 0.9f), radius = 7f, center = tc)
            // Uncertainty ellipse from the particle-filter covariance (tighter =
            // more confident); falls back to a circle if axes aren't present. The
            // world rotates with the heads-up frame, so the ellipse does too.
            val maj = est.semiMajorM
            val min = est.semiMinorM
            if (maj != null && min != null && est.ellipseRad != null) {
                val majPx = (maj * scale).toFloat().coerceIn(8f, size.minDimension)
                val minPx = (min * scale).toFloat().coerceIn(6f, size.minDimension)
                val deg = (-(est.ellipseRad!! - heading) * 180.0 / PI).toFloat()
                rotate(degrees = deg, pivot = tc) {
                    drawOval(
                        color = BlepColors.Pink.copy(alpha = 0.35f),
                        topLeft = Offset(tc.x - majPx, tc.y - minPx),
                        size = Size(majPx * 2, minPx * 2),
                        style = Stroke(2f),
                    )
                }
            } else {
                val ringR = (maxR.toFloat() * 2f * scale) * 0.25f * (1.1f - est.confidence)
                drawCircle(BlepColors.Pink.copy(alpha = 0.35f), radius = ringR.coerceAtLeast(8f), center = tc, style = Stroke(2f))
            }
            // Anchor the estimated metres to the dot itself (the footer dB is global) —
            // but not at point-blank, where the headline's "right here" overrules a
            // spatial estimate that can lag stuck at a few metres.
            val dM = est.distanceM
            if (dM != null && est.confidence >= 0.35f && !pointBlank) {
                val layout = measurer.measure("≈${dM.roundToInt()} m", labelStyle)
                drawText(layout, topLeft = Offset(tc.x + 12f, tc.y + 8f))
            }
        }

        // ── ONE instruction ray, the same cue the headline phrases ───────────
        // Rose = toward the signal/target; amber = back to the warmest spot you
        // stood in ("warmer"), which also covers a lost signal. Never two arrows.
        val warm = snapshot?.warmestBearingRad
        val cueTurnDeg: Float?; val recover: Boolean
        if (pointBlank) {
            cueTurnDeg = null; recover = false
        } else if (signalLost && warm != null) {
            cueTurnDeg = normalizeDeg(((warm - heading) * 180.0 / PI).toFloat()); recover = true
        } else if (line != null && !signalLost) {
            cueTurnDeg = if (line.ahead) 0f else line.turnDeg.toFloat(); recover = line.kind == CueKind.RECOVER
        } else {
            cueTurnDeg = null; recover = false
        }
        if (cueTurnDeg != null && snapshot != null) {
            val a = (cueTurnDeg - 90f) * (PI.toFloat() / 180f)   // screen angle: up + signed turn
            val dir = Offset(cos(a), sin(a))
            val from = hub + dir * 34f                            // clear the chevron
            val tip = hub + dir * (size.minDimension * 0.31f)
            val col = if (recover) Amber else Rose
            drawLine(halo, from, tip, strokeWidth = 13f, cap = StrokeCap.Round)
            drawLine(col, from, tip, strokeWidth = 8f, cap = StrokeCap.Round)
            val perp = Offset(-dir.y, dir.x)
            val back = tip - dir * 20f
            val head = Path().apply {
                moveTo((tip + dir * 12f).x, (tip + dir * 12f).y)
                lineTo((back + perp * 13f).x, (back + perp * 13f).y)
                lineTo((back - perp * 13f).x, (back - perp * 13f).y)
                close()
            }
            drawPath(head, halo, style = Stroke(width = 6f))
            drawPath(head, col)
            if (recover) {
                val layout = measurer.measure(warmerLabel, TextStyle(color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                drawText(layout, topLeft = Offset(tip.x - layout.size.width / 2f, tip.y - layout.size.height - 18f))
            }
        }

        // ── you: a fixed chevron, always pointing up ─────────────────────────
        // Heads-up means it never rotates; its tint is the on-course feedback
        // (green walking toward the target, red away, ink when unknown), with a
        // halo outline so it stays crisp on any proximity colour.
        val arrowColor = when {
            snapshot == null || snapshot.target.bearingRad == null -> ink
            snapshot.onCourse > 0.25f -> OnCourseGreen
            snapshot.onCourse < -0.25f -> OffCourseRed
            else -> ink
        }
        val up = Offset(0f, -1f); val right = Offset(1f, 0f)
        val tip = hub + up * 54f
        val base = hub - up * 12f
        val wedge = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo((base + right * 25f).x, (base + right * 25f).y)
            lineTo(hub.x, hub.y)                                   // notched tail = a chevron
            lineTo((base - right * 25f).x, (base - right * 25f).y)
            close()
        }
        drawPath(wedge, halo, style = Stroke(width = 7f)) // outline for contrast
        drawPath(wedge, arrowColor)
        // pivot hub
        drawCircle(halo, radius = 13f, center = hub)
        drawCircle(arrowColor, radius = 8f, center = hub)
    }
}

// Residual this far below the path-loss expectation = the cell is shadowed
// (matches the wall-vs-noise margin proven in core's SignalFieldTest).
private const val SHADOW_DB = -8.0

// How far one reveal wedge reaches (m) and its half-width — roughly the cone the
// body-shielded reading actually speaks for.
private const val REVEAL_RADIUS_M = 4.0
private const val WEDGE_HALF_DEG = 38f

private val Rose = Color(0xFFD94F70)        // cue ray toward signal/target
private val Amber = Color(0xFFE8A33D)       // cue ray back to the warmest spot
private val OnCourseGreen = Color(0xFF3E8F4E)
private val OffCourseRed = Color(0xFFC85A41)

/** Wraps a signed angle to (−180°, 180°]. */
private fun normalizeDeg(d: Float): Float {
    var x = d % 360f
    if (x > 180f) x -= 360f
    if (x <= -180f) x += 360f
    return x
}

/** Snaps a distance to a friendly round value (1, 2, 5, 10 … m) for ring labels. */
private fun niceMeters(x: Double): Double {
    if (x <= 0.0) return 1.0
    val p = 10.0.pow(floor(log10(x)))
    val n = x / p
    return p * when {
        n < 1.5 -> 1.0
        n < 3.5 -> 2.0
        n < 7.5 -> 5.0
        else -> 10.0
    }
}

/** Maps a [0,1] signal strength to the brand far→near proximity gradient. */
private fun signalColor(strength01: Float): Color = BlepColors.proximity(strength01)

private operator fun Offset.times(s: Float) = Offset(x * s, y * s)

private fun DrawScope.drawStar(center: Offset, r: Float, color: Color) {
    val p = Path()
    val inner = r * 0.42f
    for (k in 0 until 8) {
        val ang = (k * 45.0) * (PI / 180.0)
        val rad = if (k % 2 == 0) r else inner
        val o = Offset(center.x + (rad * sin(ang)).toFloat(), center.y - (rad * cos(ang)).toFloat())
        if (k == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
    }
    p.close()
    drawPath(p, color)
}
