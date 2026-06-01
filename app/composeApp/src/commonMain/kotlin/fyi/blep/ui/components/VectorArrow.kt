package fyi.blep.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * The guidance arrow: a soft, *flexible* chevron that springs toward its target
 * heading and breathes while it guides. It isn't a rigid rotation — the body
 * bends, sways and bobs slightly so it feels alive, and the curves ease back
 * into place with a spring.
 *
 * @param rotationDeg symbolic heading (0 = forward, 180 = turn around).
 * @param scale target scale (0 collapses the arrow on completion).
 * @param tint proximity colour.
 */
@Composable
fun VectorArrow(
    rotationDeg: Float,
    scale: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    // Springy (not linear) settle toward the target heading/scale.
    val heading by animateFloatAsState(
        targetValue = rotationDeg,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "arrowHeading",
    )
    val settledScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "arrowScale",
    )

    // One continuous phase drives organic sway / bob / bend / breathing.
    val osc = rememberInfiniteTransition(label = "arrowLife")
    val phase by osc.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Restart),
        label = "phase",
    )

    Canvas(modifier = modifier.size(220.dp)) {
        if (settledScale <= 0.001f) return@Canvas

        val sway = sin(phase) * 3.5f                                  // gentle lean
        val bob = sin(phase + 0.6f) * (size.minDimension * 0.018f)
        val flex = sin(phase * 1.25f) * (size.minDimension * 0.05f)   // hand-drawn body flex
        val breathe = 1f + sin(phase + 1.2f) * 0.03f

        val cx = size.width / 2f
        val cy = size.height / 2f
        val u = (size.minDimension / 2f) * settledScale * breathe
        val stroke = (u * 0.16f).coerceAtLeast(2f)

        val tipX = cx
        val tipY = cy - u * 0.86f

        translate(left = 0f, top = bob) {
            rotate(degrees = heading + sway, pivot = Offset(cx, cy)) {
                // Sweeping, slightly off-axis shaft (cubic) — a hand-drawn stroke,
                // not a ruler-straight line. The flex makes it bow back and forth.
                val shaft = Path().apply {
                    moveTo(cx + u * 0.05f, cy + u * 0.96f)
                    cubicTo(
                        cx + flex, cy + u * 0.30f,
                        cx - flex * 0.85f, cy - u * 0.28f,
                        tipX, tipY,
                    )
                }
                drawPath(shaft, tint, style = Stroke(width = stroke, cap = StrokeCap.Round))

                // Hand-drawn chevron head: two slightly curved, asymmetric barbs
                // sweeping out of the tip (like a marker stroke).
                val head = Path().apply {
                    moveTo(cx - u * 0.50f, tipY + u * 0.44f)
                    quadraticTo(cx - u * 0.15f, tipY + u * 0.06f, tipX, tipY)
                    quadraticTo(cx + u * 0.19f, tipY + u * 0.05f, cx + u * 0.47f, tipY + u * 0.40f)
                }
                drawPath(
                    head,
                    tint,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}
