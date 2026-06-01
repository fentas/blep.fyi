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

        val sway = sin(phase) * 3.5f                       // gentle left/right lean
        val bob = sin(phase + 0.6f) * (size.minDimension * 0.018f)
        val bend = sin(phase * 1.3f) * (size.minDimension * 0.045f) // body flex
        val breathe = 1f + sin(phase + 1.2f) * 0.03f

        val cx = size.width / 2f
        val cy = size.height / 2f
        val unit = (size.minDimension / 2f) * settledScale * breathe
        val stroke = (unit * 0.2f).coerceAtLeast(2f)

        translate(left = 0f, top = bob) {
            rotate(degrees = heading + sway, pivot = Offset(cx, cy)) {
                // Bowed shaft (quadratic) so the body flexes rather than staying rigid.
                val shaft = Path().apply {
                    moveTo(cx, cy + unit * 0.9f)
                    quadraticTo(cx + bend, cy + unit * 0.1f, cx, cy - unit * 0.5f)
                }
                drawPath(shaft, tint, style = Stroke(width = stroke, cap = StrokeCap.Round))

                // Curved chevron head (a smooth arc, not two straight lines).
                val head = Path().apply {
                    moveTo(cx - unit * 0.62f, cy - unit * 0.08f)
                    quadraticTo(cx + bend * 0.5f, cy - unit * 0.92f, cx + unit * 0.62f, cy - unit * 0.08f)
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
