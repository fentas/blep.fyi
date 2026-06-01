package fyi.blep.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/**
 * The guidance arrow: a soft, rounded chevron that smoothly rotates and scales
 * toward its target, tinted by proximity, with a subtle "breathing" pulse.
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
    val animatedRotation by animateFloatAsState(
        targetValue = rotationDeg,
        animationSpec = tween(durationMillis = 600),
        label = "arrowRotation",
    )
    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = tween(durationMillis = 600),
        label = "arrowScale",
    )

    // Gentle, continuous pulse so the arrow feels alive while guiding.
    val pulse = rememberInfiniteTransition(label = "arrowPulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Canvas(modifier = modifier.size(220.dp)) {
        val effective = animatedScale * pulse(pulseScale, animatedScale)
        if (effective <= 0.001f) return@Canvas

        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val unit = (minOf(w, h) / 2f) * effective
        val stroke = (unit * 0.22f).coerceAtLeast(2f)

        rotate(degrees = animatedRotation, pivot = Offset(cx, cy)) {
            // Shaft
            drawLine(
                color = tint,
                start = Offset(cx, cy + unit * 0.85f),
                end = Offset(cx, cy - unit * 0.55f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            // Chevron head
            val head = Path().apply {
                moveTo(cx - unit * 0.6f, cy - unit * 0.15f)
                lineTo(cx, cy - unit * 0.85f)
                lineTo(cx + unit * 0.6f, cy - unit * 0.15f)
            }
            drawPath(
                path = head,
                color = tint,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** Pulse only matters once the arrow has materialised; suppress it near zero. */
private fun pulse(pulseScale: Float, animatedScale: Float): Float =
    if (animatedScale < 0.05f) 1f else pulseScale
