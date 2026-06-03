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
import fyi.blep.core.spatial.SpatialSnapshot
import fyi.blep.core.spatial.Vec2
import fyi.blep.ui.theme.BlepColors
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

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
    modifier: Modifier = Modifier,
) {
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

        // ── range rings (scale reference, no map) ────────────────────────────
        val ring = BlepColors.Ink.copy(alpha = 0.08f)
        listOf(span / 6.0, span / 3.0).forEach { r ->
            drawCircle(ring, radius = (r * scale).toFloat(), center = toScreen(here), style = Stroke(width = 1.5f))
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
                strokeWidth = 5f, cap = StrokeCap.Round,
            )
        }

        // ── start marker (a small four-point star) ───────────────────────────
        drawStar(toScreen(Vec2.ZERO), r = 9f, color = BlepColors.Ink.copy(alpha = 0.55f))

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
            drawCircle(BlepColors.Pink.copy(alpha = 0.9f), radius = 5f, center = tc)
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
        } else if (snapshot?.signalBearingRad != null && snapshot.signalBearingConfidence > 0.3f) {
            // No fix yet, but turning in place has revealed a direction — point to it.
            val b = snapshot.signalBearingRad!!
            val dir = Offset(sin(b).toFloat(), -cos(b).toFloat())
            val hp = toScreen(here)
            val tip = hp + dir * (size.minDimension * 0.34f)
            val col = BlepColors.Pink.copy(alpha = 0.25f + 0.5f * snapshot.signalBearingConfidence)
            drawLine(col, hp, tip, strokeWidth = 4f, cap = StrokeCap.Round)
            val perp = Offset(-dir.y, dir.x)
            val back = tip - dir * 12f
            drawPath(
                Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo((back + perp * 7f).x, (back + perp * 7f).y)
                    lineTo((back - perp * 7f).x, (back - perp * 7f).y)
                    close()
                },
                col,
            )
        }

        // ── you: position dot + heading wedge ────────────────────────────────
        if (snapshot != null) {
            val hp = toScreen(here)
            val heading = snapshot.headingRad
            // On-course feedback: green toward target, red away, neutral if unknown.
            val wedgeColor = when {
                snapshot.target.bearingRad == null -> BlepColors.Blue
                snapshot.onCourse > 0.25f -> Color(0xFF8FCB7A)
                snapshot.onCourse < -0.25f -> Color(0xFFE0907F)
                else -> BlepColors.Blue
            }
            val dir = Offset(sin(heading).toFloat(), -cos(heading).toFloat())
            val perp = Offset(-dir.y, dir.x)
            val tip = hp + dir * 22f
            val base = hp - dir * 4f
            val wedge = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo((base + perp * 9f).x, (base + perp * 9f).y)
                lineTo((base - perp * 9f).x, (base - perp * 9f).y)
                close()
            }
            drawPath(wedge, wedgeColor.copy(alpha = 0.9f))
            drawCircle(BlepColors.Ink, radius = 6f, center = hp)
            drawCircle(BlepColors.Cream, radius = 3f, center = hp)
        } else {
            // No motion yet: just the start marker, centred.
            drawCircle(BlepColors.Ink, radius = 6f, center = Offset(cx, cy))
        }
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
