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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fyi.blep.core.spatial.SpatialGuidance
import fyi.blep.core.spatial.SpatialSnapshot
import fyi.blep.core.tracking.TrackingStatus
import fyi.blep.ui.components.RadarView
import fyi.blep.ui.components.VectorArrow
import fyi.blep.ui.theme.BlepColors
import kotlin.math.roundToInt

@Composable
fun TrackingScreen(
    deviceName: String,
    status: TrackingStatus,
    rssi: Int?,
    spatial: SpatialSnapshot?,
    guidanceLine: String?,
    soundOn: Boolean,
    onToggleSound: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = BlepColors.proximity(status.proximity),
        animationSpec = tween(durationMillis = 800),
        label = "trackingBackground",
    )
    val arrowTint = BlepColors.Ink.copy(alpha = 0.82f)
    val pulse by rememberInfiniteTransition(label = "radar").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "pulse",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(background)                       // full-bleed colour
            .windowInsetsPadding(WindowInsets.safeDrawing) // keep content off the bars
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(
                text = deviceName,
                style = MaterialTheme.typography.titleMedium,
                color = BlepColors.Ink.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center),
            )
            MuteToggle(
                soundOn = soundOn,
                onToggle = onToggleSound,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        // The spatial map is the hero; a compact arrow keeps the immediate cue.
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            RadarView(snapshot = spatial, pulse = pulse, modifier = Modifier.fillMaxSize())
            if (spatial == null) {
                VectorArrow(curl = status.arrow.curl, scale = status.arrow.scale, tint = arrowTint)
            }
        }

        // ONE authoritative cue, never two that disagree: the precise compass /
        // turn-by-turn instruction when we have it (it already folds in warmer /
        // distance), otherwise the coarser RSSI phase guidance.
        val instruction = guidanceLine
            ?: spatial?.target?.takeIf { it.confidence >= 0.35f && it.distanceM != null }
                ?.let { distanceLabel(it.distanceM!!) }
        val headline = instruction ?: status.guidance.title
        val detail = if (instruction != null) null else status.guidance.detail
        AnimatedContent(
            targetState = headline,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "guidance",
        ) { text ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text, style = MaterialTheme.typography.displaySmall, color = BlepColors.Ink, textAlign = TextAlign.Center)
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodyLarge, color = BlepColors.Ink.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                }
            }
        }

        val floor = spatial?.floorDelta?.let { SpatialGuidance.floorHint(it) }
        if (floor != null) {
            Text(
                text = floor,
                style = MaterialTheme.typography.bodyLarge,
                color = BlepColors.Ink.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // RSSI + which environment the tracker thinks it's in (from signal jitter).
        Text(
            text = buildString {
                append(rssi?.let { "$it dBm" } ?: "scanning…")
                spatial?.signalVolatilityDb?.takeIf { it > 0.0 }?.let { v ->
                    val tag = if (v >= NOISY_FIELD_DB) "noisy" else "clean"
                    append("  ·  $tag field ${(v * 10).roundToInt() / 10.0} dB")
                }
            },
            style = MaterialTheme.typography.labelLarge,
            color = BlepColors.Ink.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
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

/** Human label for an estimated target distance. */
internal fun distanceLabel(meters: Double): String = when {
    meters < 1.5 -> "almost on it"
    else -> "~${meters.roundToInt()} m away"
}

// Above this much signal jitter (dB) the field reads as "noisy" — mirrors
// GuidanceStabilizer.noisyVolatilityDb, which gates directional commitment.
private const val NOISY_FIELD_DB = 2.2

/** A flat 2-D speaker glyph (no system emoji) that toggles the tracking tone:
 *  sound-wave arcs when on, a slash when muted. */
@Composable
private fun MuteToggle(soundOn: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val tint = BlepColors.Ink.copy(alpha = 0.72f)
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onToggle)
            .padding(10.dp)
            .size(26.dp),
    ) {
        val w = size.width; val h = size.height
        // Speaker = back block + cone, one filled polygon.
        drawPath(
            Path().apply {
                moveTo(w * 0.08f, h * 0.38f)
                lineTo(w * 0.28f, h * 0.38f)
                lineTo(w * 0.50f, h * 0.16f)
                lineTo(w * 0.50f, h * 0.84f)
                lineTo(w * 0.28f, h * 0.62f)
                lineTo(w * 0.08f, h * 0.62f)
                close()
            },
            tint,
        )
        if (soundOn) {
            val cx = w * 0.45f; val cy = h * 0.5f
            listOf(w * 0.22f, w * 0.34f).forEach { r ->
                drawArc(
                    color = tint,
                    startAngle = -55f, sweepAngle = 110f, useCenter = false,
                    topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2),
                    style = Stroke(width = w * 0.07f, cap = StrokeCap.Round),
                )
            }
        } else {
            drawLine(tint, Offset(w * 0.58f, h * 0.22f), Offset(w * 0.96f, h * 0.78f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
        }
    }
}
