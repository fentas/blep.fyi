package fyi.blep.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fyi.blep.core.tracking.TrackingPhase
import fyi.blep.core.tracking.TrackingStatus
import fyi.blep.ui.components.VectorArrow
import fyi.blep.ui.theme.BlepColors
import fyi.blep.ui.theme.BlepLogo
import kotlin.math.PI
import kotlin.math.abs
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

        // The blep mascot reacts to what you should do, with the live signal.
        Mascot(phase = status.phase, rssi = rssi, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))

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
 * The mascot at the bottom + the live RSSI. It mirrors the guidance:
 * **turning** → it looks left and right; **walking** → it strolls across and
 * off one edge, reappearing from the other; otherwise it idles with a bob.
 */
@Composable
private fun Mascot(phase: TrackingPhase, rssi: Int?, modifier: Modifier = Modifier) {
    val walking = phase == TrackingPhase.VECTOR_WALK
    val turning = phase == TrackingPhase.AXIS_SWEEP || phase == TrackingPhase.REORIENT

    val anim = rememberInfiniteTransition(label = "mascot")
    val walkP by anim.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart), label = "walk",
    )
    val osc by anim.animateFloat(
        0f, (2f * PI).toFloat(), infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Restart), label = "osc",
    )

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = rssi?.let { "$it dBm" } ?: "scanning…",
            style = MaterialTheme.typography.labelLarge,
            color = BlepColors.Ink.copy(alpha = 0.55f),
        )
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(72.dp).clipToBounds(),
        ) {
            val density = LocalDensity.current
            val iconSize = 56.dp
            val travelPx = with(density) { (maxWidth / 2 + iconSize).toPx() }
            val bobPx = with(density) { 7.dp.toPx() }
            val lookPx = with(density) { 9.dp.toPx() }

            Icon(
                painter = rememberVectorPainter(BlepLogo),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier
                    .size(iconSize)
                    .align(Alignment.BottomCenter)
                    .graphicsLayerMascot(
                        walking = walking,
                        turning = turning,
                        walkP = walkP,
                        osc = osc,
                        travelPx = travelPx,
                        bobPx = bobPx,
                        lookPx = lookPx,
                    ),
            )
        }
    }
}

private fun Modifier.graphicsLayerMascot(
    walking: Boolean,
    turning: Boolean,
    walkP: Float,
    osc: Float,
    travelPx: Float,
    bobPx: Float,
    lookPx: Float,
) = this.graphicsLayer {
        when {
            walking -> {
                // stroll across; exit one edge, re-enter from the other
                translationX = -travelPx + 2f * travelPx * walkP
                val gait = walkP * 2f * PI.toFloat() * 4f
                translationY = -abs(sin(gait)) * bobPx
                rotationZ = sin(gait) * 4f
            }
            turning -> {
                // look left and right
                rotationZ = sin(osc) * 16f
                translationX = sin(osc) * lookPx
                translationY = -abs(sin(osc * 2f)) * (bobPx * 0.4f)
            }
            else -> {
                // idle breathing bob
                translationY = sin(osc) * (bobPx * 0.5f)
                val s = 1f + sin(osc) * 0.03f
                scaleX = s; scaleY = s
            }
        }
    }
