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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fyi.blep.core.spatial.SpatialSnapshot
import fyi.blep.core.spatial.Vec2
import fyi.blep.ui.theme.BlepColors
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import fyi.blep.resources.Res
import fyi.blep.resources.radar_warmer
import org.jetbrains.compose.resources.stringResource

/**
 * Abstract, map-less "radar" of the hunt. Draws the start point, the trail you've
 * walked (coloured by signal strength, with a soft warm fog where it's strong),
 * your live position + heading wedge (green when you're heading toward the target,
 * red when away), and the predicted target as a pulsing glow with a confidence
 * ring. Auto-scales to fit. Shows just the start marker until motion data arrives.
 */
@Composable
fun RadarView(
    snapshot: SpatialSnapshot?,
    pulse: Float,          // 0..1 looping, for the target glow
    signalLost: Boolean = false, // no fresh RSSI from the target right now
    ink: Color = BlepColors.Ink,    // foreground (rings, labels, "you") — flips for dark
    halo: Color = BlepColors.Cream, // contrast outline behind ink marks
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val warmerLabel = stringResource(Res.string.radar_warmer)
    val labelStyle = TextStyle(color = ink.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f

        // ── world → screen fit (north = up) ──────────────────────────────────
        val pts = snapshot?.path ?: emptyList()
        val here = snapshot?.here ?: Vec2.ZERO
        val target = snapshot?.target?.position
        var minX = -3.0; var maxX = 3.0; var minY = -3.0; var maxY = 3.0
        fun include(v: Vec2) {
            if (v.x < minX) minX = v.x; if (v.x > maxX) maxX = v.x
            if (v.y < minY) minY = v.y; if (v.y > maxY) maxY = v.y
        }
        pts.forEach { include(it.pos) }
        include(here); target?.let(::include)
        val spanX = (maxX - minX); val spanY = (maxY - minY)
        val span = max(max(spanX, spanY), 6.0)            // metres, min 6 m
        val midX = (minX + maxX) / 2.0; val midY = (minY + maxY) / 2.0
        val scale = (minOf(size.width, size.height) * 0.84f) / span.toFloat()
        fun toScreen(v: Vec2) = Offset(
            x = cx + ((v.x - midX) * scale).toFloat(),
            y = cy - ((v.y - midY) * scale).toFloat(),   // invert: +north is up
        )

        // ── range rings: distance from you, snapped to round metres + labelled ──
        val ringColor = ink.copy(alpha = if (signalLost) 0.10f else 0.20f)
        val r1 = niceMeters(span / 6.0)
        val r2 = niceMeters(span / 3.0).let { if (it <= r1) r1 * 2.0 else it }
        listOf(r1, r2).forEach { rm ->
            val rPx = (rm * scale).toFloat()
            val c = toScreen(here)
            drawCircle(ringColor, radius = rPx, center = c, style = Stroke(width = 3.5f))
            val label = if (rm < 1.0) "${(rm * 100).roundToInt()} cm" else "${rm.roundToInt()} m"
            val layout = measurer.measure(label, labelStyle)
            // sit the label just above the top of its ring
            drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - rPx - layout.size.height - 1f))
        }

        // ── signal fog: soft warm discs at strong samples ────────────────────
        if (pts.size > 1) {
            val fogStep = max(1, pts.size / 90)
            var i = 0
            while (i < pts.size) {
                val p = pts[i]
                if (p.strength01 > 0.15f) {
                    val c = signalColor(p.strength01).copy(alpha = 0.10f * p.strength01)
                    val rad = (span.toFloat() * scale) * 0.05f * (0.5f + p.strength01)
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(c, Color.Transparent),
                            center = toScreen(p.pos),
                            radius = rad.coerceAtLeast(8f),
                        ),
                        radius = rad.coerceAtLeast(8f),
                        center = toScreen(p.pos),
                    )
                }
                i += fogStep
            }
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

        // ── predicted target: glow + confidence ring ─────────────────────────
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
            // more confident); falls back to a circle if axes aren't present.
            val maj = est.semiMajorM
            val min = est.semiMinorM
            if (maj != null && min != null && est.ellipseRad != null) {
                val majPx = (maj * scale).toFloat().coerceIn(8f, size.minDimension)
                val minPx = (min * scale).toFloat().coerceIn(6f, size.minDimension)
                rotate(degrees = (-est.ellipseRad!! * 180.0 / kotlin.math.PI).toFloat(), pivot = tc) {
                    drawOval(
                        color = BlepColors.Pink.copy(alpha = 0.35f),
                        topLeft = Offset(tc.x - majPx, tc.y - minPx),
                        size = Size(majPx * 2, minPx * 2),
                        style = Stroke(2f),
                    )
                }
            } else {
                val ringR = (span.toFloat() * scale) * 0.25f * (1.1f - est.confidence)
                drawCircle(BlepColors.Pink.copy(alpha = 0.35f), radius = ringR.coerceAtLeast(8f), center = tc, style = Stroke(2f))
            }
            // Matches SpatialTuning.signalMinConfidence so the arrow and the text
            // cue appear together (the alpha below still fades it in near threshold).
        } else if (snapshot?.signalBearingRad != null && snapshot.signalBearingConfidence >= 0.35f) {
            // No fix yet, but turning in place has revealed a direction — point to it.
            val b = snapshot.signalBearingRad!!
            val dir = Offset(sin(b).toFloat(), -cos(b).toFloat())
            val hp = toScreen(here)
            val tip = hp + dir * (size.minDimension * 0.40f)
            val col = BlepColors.Pink.copy(alpha = 0.25f + 0.5f * snapshot.signalBearingConfidence)
            drawLine(col, hp, tip, strokeWidth = 6f, cap = StrokeCap.Round)
            val perp = Offset(-dir.y, dir.x)
            val back = tip - dir * 16f
            drawPath(
                Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo((back + perp * 10f).x, (back + perp * 10f).y)
                    lineTo((back - perp * 10f).x, (back - perp * 10f).y)
                    close()
                },
                col,
            )
        }

        // ── "warmer this way": point back to the strongest spot you stood in,
        //    whenever you've strayed off it (recovering) or lost the signal ───────
        val warmBearing = snapshot?.warmestBearingRad
        if (snapshot != null && warmBearing != null && (snapshot.recovering || signalLost)) {
            val hp = toScreen(here)
            val wdir = Offset(sin(warmBearing).toFloat(), -cos(warmBearing).toFloat())
            val wtip = hp + wdir * (size.minDimension * 0.30f)
            val amber = Color(0xFFE8A33D)
            drawLine(amber, hp, wtip, strokeWidth = 7f, cap = StrokeCap.Round)
            val wperp = Offset(-wdir.y, wdir.x)
            val wback = wtip - wdir * 18f
            drawPath(
                Path().apply {
                    moveTo(wtip.x, wtip.y)
                    lineTo((wback + wperp * 11f).x, (wback + wperp * 11f).y)
                    lineTo((wback - wperp * 11f).x, (wback - wperp * 11f).y)
                    close()
                },
                amber,
            )
            val warmLabel = measurer.measure(warmerLabel, TextStyle(color = amber, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            drawText(warmLabel, topLeft = Offset(wtip.x - warmLabel.size.width / 2f, wtip.y - warmLabel.size.height - 4f))
        }

        // ── you: a big, high-contrast heading arrow ──────────────────────────
        // On-course feedback: green toward target, red away. When the bearing is
        // unknown it's drawn in Ink (dark) — NOT brand blue, which vanished against
        // the blue background. A cream outline keeps it crisp on any colour.
        if (snapshot != null) {
            val hp = toScreen(here)
            val heading = snapshot.headingRad
            val arrowColor = when {
                snapshot.target.bearingRad == null -> ink
                snapshot.onCourse > 0.25f -> Color(0xFF4FA85E)
                snapshot.onCourse < -0.25f -> Color(0xFFD4694F)
                else -> ink
            }
            val dir = Offset(sin(heading).toFloat(), -cos(heading).toFloat())
            val perp = Offset(-dir.y, dir.x)
            val tip = hp + dir * 48f
            val base = hp - dir * 10f
            val wedge = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo((base + perp * 22f).x, (base + perp * 22f).y)
                lineTo(hp.x, hp.y)                                   // notched tail = a chevron
                lineTo((base - perp * 22f).x, (base - perp * 22f).y)
                close()
            }
            drawPath(wedge, halo, style = Stroke(width = 6f)) // outline for contrast
            drawPath(wedge, arrowColor)
            // pivot hub
            drawCircle(halo, radius = 12f, center = hp)
            drawCircle(arrowColor, radius = 7f, center = hp)
        } else {
            drawCircle(halo, radius = 12f, center = Offset(cx, cy))
            drawCircle(ink, radius = 7f, center = Offset(cx, cy))
        }
    }
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
        val ang = (k * 45.0) * (kotlin.math.PI / 180.0)
        val rad = if (k % 2 == 0) r else inner
        val o = Offset(center.x + (rad * sin(ang)).toFloat(), center.y - (rad * cos(ang)).toFloat())
        if (k == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
    }
    p.close()
    drawPath(p, color)
}
