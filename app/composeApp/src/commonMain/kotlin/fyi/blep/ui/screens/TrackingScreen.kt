package fyi.blep.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fyi.blep.core.tracking.TrackingPhase
import fyi.blep.core.tracking.TrackingStatus
import fyi.blep.ui.components.VectorArrow
import fyi.blep.ui.theme.BlepColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TrackingScreen(
    deviceName: String,
    status: TrackingStatus,
    rssi: Int?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = BlepColors.proximity(status.proximity),
        animationSpec = tween(durationMillis = 800),
        label = "trackingBackground",
    )
    val arrowTint = BlepColors.Ink.copy(alpha = 0.82f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(background)                       // full-bleed colour
            .windowInsetsPadding(WindowInsets.safeDrawing) // keep content off the bars
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = deviceName,
            style = MaterialTheme.typography.titleMedium,
            color = BlepColors.Ink.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 8.dp),
        )

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            VectorArrow(curl = status.arrow.curl, scale = status.arrow.scale, tint = arrowTint)
        }

        AnimatedContent(
            targetState = status.guidance,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "guidance",
        ) { guidance ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(guidance.title, style = MaterialTheme.typography.displayLarge, color = BlepColors.Ink, textAlign = TextAlign.Center)
                Text(guidance.detail, style = MaterialTheme.typography.bodyLarge, color = BlepColors.Ink.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            }
        }

        // The blep pup reacts to what you should do, with the live signal —
        // and a ball rolls in as you close in.
        DogTrack(
            phase = status.phase,
            proximity = status.proximity,
            rssi = rssi,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        Text(
            text = "Cancel",
            style = MaterialTheme.typography.labelLarge,
            color = BlepColors.Ink.copy(alpha = 0.55f),
            modifier = Modifier
                .padding(top = 10.dp, bottom = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 24.dp, vertical = 10.dp),
        )
    }
}

/**
 * The blep pup at the bottom + the live RSSI. It mirrors the guidance:
 * **turning** → looks left and right; **walking** → trots across and off one
 * edge, reappearing from the other; **close** → stops, wags fast and a ball
 * rolls in from the side. Drawn as a bold ink silhouette.
 */
@Composable
private fun DogTrack(phase: TrackingPhase, proximity: Float, rssi: Int?, modifier: Modifier = Modifier) {
    val walking = phase == TrackingPhase.VECTOR_WALK
    val turning = phase == TrackingPhase.AXIS_SWEEP || phase == TrackingPhase.REORIENT
    val close = proximity >= 0.78f || phase == TrackingPhase.PINPOINT

    val anim = rememberInfiniteTransition(label = "dog")
    val walkP by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "walkP")
    val osc by anim.animateFloat(0f, (2f * PI).toFloat(), infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart), label = "osc")
    val fast by anim.animateFloat(0f, (2f * PI).toFloat(), infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Restart), label = "fast")

    // Ball slides in from the right as you get close.
    val ballIn by animateFloatAsState(
        targetValue = ((proximity - 0.5f) / 0.45f).coerceIn(0f, 1f),
        animationSpec = tween(700), label = "ballIn",
    )

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = rssi?.let { "$it dBm" } ?: "scanning…",
            style = MaterialTheme.typography.labelLarge,
            color = BlepColors.Ink.copy(alpha = 0.55f),
        )
        Canvas(modifier = Modifier.fillMaxWidth().height(88.dp).clipToBounds()) {
            val s = size.height / 82f
            val groundY = size.height * 0.95f
            val cy = groundY - 30f * s
            val dogHalf = 58f * s

            var cx = size.width / 2f
            var moving = false
            var headTurn = 0f
            var tailWag = 0f
            val twoPi = 2f * PI.toFloat()
            when {
                close -> { cx = size.width * 0.42f; headTurn = 6f; tailWag = sin(fast) * 26f }
                walking -> { cx = -dogHalf + (size.width + 2f * dogHalf) * walkP; moving = true; tailWag = sin(walkP * twoPi * 4f) * 14f }
                turning -> { cx = size.width / 2f + sin(osc) * 9f * s; headTurn = sin(osc) * 18f; tailWag = sin(osc) * 16f }
                else -> { tailWag = sin(osc) * 10f }
            }
            val bob = if (moving) -abs(sin(walkP * twoPi * 4f)) * 3f * s else sin(osc) * 1.5f * s
            if (ballIn > 0.01f) drawBall(cx, groundY, s, ballIn)
            drawDog(cx, cy + bob, s, gaitP = walkP, moving = moving, headTurnDeg = headTurn, tailWagDeg = tailWag)
        }
    }
}

/** Draws the pup silhouette (facing right) in dog-space mapped to the canvas. */
private fun DrawScope.drawDog(
    cx: Float, cy: Float, s: Float, gaitP: Float, moving: Boolean, headTurnDeg: Float, tailWagDeg: Float,
) {
    val ink = BlepColors.Ink
    fun x(v: Float) = cx + (v - 66f) * s
    fun y(v: Float) = cy + (v - 46f) * s
    fun w(v: Float) = v * s
    fun o(px: Float, py: Float) = Offset(x(px), y(py))

    fun leg(hx: Float, hy: Float, phase: Float, width: Float, alpha: Float) {
        val stride = if (moving) 6f else 0f
        val lift = if (moving) 9f else 0f
        val th = 2f * PI.toFloat() * (gaitP + phase)
        val pawX = hx + stride * cos(th)
        val pawY = hy + 18f - lift * maxOf(0f, sin(th))
        drawLine(ink.copy(alpha = alpha), o(hx, hy), o(pawX, pawY), w(width), StrokeCap.Round)
    }
    // far legs (behind), then body, then near legs
    leg(48f, 56f, 0.5f, 6f, 0.5f)
    leg(74f, 56f, 0.0f, 6f, 0.5f)

    // tail (wags)
    val tailEndX = 18f + tailWagDeg * 0.16f
    val tailEndY = 24f - abs(tailWagDeg) * 0.12f
    drawPath(
        Path().apply { moveTo(x(34f), y(40f)); quadraticTo(x(23f), y(28f), x(tailEndX), y(tailEndY)) },
        ink, style = Stroke(width = w(6f), cap = StrokeCap.Round),
    )

    // torso capsule
    drawLine(ink, o(36f, 44f), o(78f, 44f), w(28f), StrokeCap.Round)

    // near legs
    leg(52f, 56f, 0.0f, 7f, 1f)
    leg(78f, 56f, 0.5f, 7f, 1f)

    // head (turns)
    rotate(degrees = headTurnDeg, pivot = o(86f, 36f)) {
        drawCircle(ink, radius = w(14f), center = o(90f, 34f))
        drawLine(ink, o(98f, 40f), o(114f, 42f), w(13f), StrokeCap.Round) // muzzle
        val ear = Path().apply {
            moveTo(x(86f), y(24f))
            cubicTo(x(78f), y(26f), x(78f), y(48f), x(88f), y(50f))
            cubicTo(x(92f), y(44f), x(92f), y(30f), x(86f), y(24f))
            close()
        }
        drawPath(ear, ink)
        drawCircle(BlepColors.Cream, radius = w(2.1f), center = o(93f, 32f))   // eye
        drawCircle(ink, radius = w(2.6f), center = o(115f, 41f))               // nose
    }
}

/** A pink ball rolling in from the right edge toward the pup. */
private fun DrawScope.drawBall(cx: Float, groundY: Float, s: Float, amount: Float) {
    val r = 9f * s
    val startX = size.width + 30f
    val restX = cx + 80f * s
    val bx = startX + (restX - startX) * amount
    val by = groundY - r
    drawCircle(BlepColors.Pink, radius = r, center = Offset(bx, by))
    drawCircle(BlepColors.Ink, radius = r, center = Offset(bx, by), style = Stroke(width = 2.4f * s))
    drawCircle(BlepColors.Cream.copy(alpha = 0.8f), radius = r * 0.28f, center = Offset(bx - r * 0.3f, by - r * 0.3f))
}
