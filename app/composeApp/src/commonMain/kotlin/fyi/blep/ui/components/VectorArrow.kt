package fyi.blep.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The guidance arrow, drawn as a flexible hand-drawn stroke whose **shape**
 * carries the instruction — it doesn't just rotate.
 *
 * [curl] (signed, see [fyi.blep.core.tracking.ArrowDirective]) bends the arrow
 * body: `0` is a straight "go" arrow, small values lean it left/right, larger
 * values curl it into a "turn / rotate" or U-turn. The arrow morphs smoothly
 * (spring) between poses and keeps a gentle living wobble.
 */
@Composable
fun VectorArrow(
    curl: Float,
    scale: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val animCurl by animateFloatAsState(
        targetValue = curl,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "arrowCurl",
    )
    val animScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "arrowScale",
    )
    val osc = rememberInfiniteTransition(label = "arrowLife")
    val phase by osc.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Restart),
        label = "phase",
    )

    Canvas(modifier = modifier.size(220.dp)) {
        if (animScale <= 0.001f) return@Canvas
        val breathe = 1f + sin(phase + 1.2f) * 0.03f
        val wobble = sin(phase * 1.15f) * 0.05f          // tiny living flex of the body
        val bob = sin(phase + 0.6f) * (size.minDimension * 0.015f)
        val span = size.minDimension * 0.78f * animScale * breathe
        val stroke = (span * 0.08f).coerceAtLeast(2f)

        translate(left = 0f, top = bob) {
            val path = buildArrow(
                curl = animCurl + wobble,
                span = span,
                centerX = size.width / 2f,
                centerY = size.height / 2f,
            )
            drawPath(path, tint, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private const val SEGMENTS = 28
private const val SPINE_LEN = 1.95f       // arc length in unit space
private const val MAX_ANGLE = 4.2f        // radians at |curl| = 1
private const val HEAD = 0.66f
private const val SPREAD = 0.6f

/**
 * Builds the arrow as a constant-curvature "spine" (so it bends like a drawn
 * stroke) plus a chevron head that follows the tip's tangent. Normalised to fit
 * [span] and centred at ([centerX], [centerY]).
 */
private fun DrawScope.buildArrow(curl: Float, span: Float, centerX: Float, centerY: Float): Path {
    val arc = abs(curl) * MAX_ANGLE
    val sign = if (curl < 0f) -1f else 1f
    val dTheta = arc * sign / SEGMENTS
    val ds = SPINE_LEN / SEGMENTS

    val xs = FloatArray(SEGMENTS + 3)
    val ys = FloatArray(SEGMENTS + 3)
    var x = 0f
    var y = 0.9f
    var a = (-PI / 2).toFloat()
    xs[0] = x; ys[0] = y
    for (k in 1..SEGMENTS) {
        x += ds * cos(a); y += ds * sin(a); a += dTheta
        xs[k] = x; ys[k] = y
    }
    // Chevron head from the tip, opening backwards along the tip tangent.
    val tipX = xs[SEGMENTS]; val tipY = ys[SEGMENTS]
    val back = a + PI.toFloat()
    xs[SEGMENTS + 1] = tipX + HEAD * cos(back + SPREAD); ys[SEGMENTS + 1] = tipY + HEAD * sin(back + SPREAD)
    xs[SEGMENTS + 2] = tipX + HEAD * cos(back - SPREAD); ys[SEGMENTS + 2] = tipY + HEAD * sin(back - SPREAD)

    // Normalise (centre + scale to span) so curled poses don't shrink.
    var minX = xs[0]; var maxX = xs[0]; var minY = ys[0]; var maxY = ys[0]
    for (i in 0..SEGMENTS + 2) {
        if (xs[i] < minX) minX = xs[i]; if (xs[i] > maxX) maxX = xs[i]
        if (ys[i] < minY) minY = ys[i]; if (ys[i] > maxY) maxY = ys[i]
    }
    val cx = (minX + maxX) / 2f
    val cy = (minY + maxY) / 2f
    val dim = maxOf(maxX - minX, maxY - minY, 0.0001f)
    val s = span / dim
    fun px(i: Int) = (xs[i] - cx) * s + centerX
    fun py(i: Int) = (ys[i] - cy) * s + centerY

    return Path().apply {
        moveTo(px(0), py(0))
        for (k in 1..SEGMENTS) lineTo(px(k), py(k))
        // head: leftBarb -> tip -> rightBarb
        moveTo(px(SEGMENTS + 1), py(SEGMENTS + 1))
        lineTo(px(SEGMENTS), py(SEGMENTS))
        lineTo(px(SEGMENTS + 2), py(SEGMENTS + 2))
    }
}
