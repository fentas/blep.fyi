package fyi.blep.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import fyi.blep.core.tracking.TrackingPhase
import fyi.blep.core.tracking.TrackingStatus
import fyi.blep.resources.Res
import fyi.blep.resources.dog_sheet
import fyi.blep.ui.components.VectorArrow
import fyi.blep.ui.theme.BlepColors
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.imageResource

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

        // The blep pup reacts to what you should do, with the live signal.
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

// Sprite sheet layout: up to 24 frames per clip, 4 clip rows; cell 400x264 px.
// Stitched + aligned from AI sources (web/scripts/stitch-dog-sprites.mjs).
private const val CELL_W = 400
private const val CELL_H = 264

/** Clip rows in dog_sheet.png, with frame count + playback rate. */
private enum class DogClip(val row: Int, val frames: Int, val fps: Int, val moving: Boolean) {
    WALK(0, 24, 24, true),     // trot
    LOOK(1, 24, 24, false),    // look around
    IDLE(2, 24, 24, false),    // idle
    FOUND(3, 12, 12, false);   // with a bone (close / found)

    val periodMs: Int get() = frames * 1000 / fps
}

/**
 * The blep pup at the bottom + the live RSSI. Plays each clip from the sprite
 * sheet at its own fps and **crossfades between clips**, entering the new clip
 * at the pose that best matches the outgoing one (precomputed in [DogFrames]),
 * so switches are coherent rather than abrupt.
 */
@Composable
private fun DogTrack(phase: TrackingPhase, proximity: Float, rssi: Int?, modifier: Modifier = Modifier) {
    val sheet = imageResource(Res.drawable.dog_sheet)
    val clip = when {
        proximity >= 0.82f || phase == TrackingPhase.PINPOINT -> DogClip.FOUND
        phase == TrackingPhase.VECTOR_WALK -> DogClip.WALK
        phase == TrackingPhase.AXIS_SWEEP || phase == TrackingPhase.REORIENT -> DogClip.LOOK
        else -> DogClip.IDLE
    }

    var shown by remember { mutableStateOf(clip) }
    var startFrame by remember { mutableIntStateOf(0) }
    var fromClip by remember { mutableStateOf<DogClip?>(null) }
    var fromFrame by remember { mutableIntStateOf(0) }
    val fade = remember { Animatable(1f) }

    val anim = rememberInfiniteTransition(label = "dog")
    val freeF by anim.animateFloat(
        0f, shown.frames.toFloat(), infiniteRepeatable(tween(shown.periodMs, easing = LinearEasing), RepeatMode.Restart), label = "frame",
    )
    val walkP by anim.animateFloat(
        0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "walkX",
    )
    val curFrame = (startFrame + freeF.toInt()) % shown.frames

    LaunchedEffect(clip) {
        if (clip != shown) {
            fromClip = shown; fromFrame = curFrame
            startFrame = DogFrames.transition[shown.ordinal][clip.ordinal]
                .getOrElse(curFrame) { 0 }.coerceIn(0, clip.frames - 1)
            shown = clip
            fade.snapTo(0f); fade.animateTo(1f, tween(durationMillis = 300))
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = rssi?.let { "$it dBm" } ?: "scanning…",
            style = MaterialTheme.typography.labelLarge,
            color = BlepColors.Ink.copy(alpha = 0.55f),
        )
        Canvas(modifier = Modifier.fillMaxWidth().height(108.dp).clipToBounds()) {
            fromClip?.let { fc ->
                if (fade.value < 0.999f) drawDogFrame(sheet, fc, fromFrame, walkP, alpha = 1f - fade.value)
            }
            drawDogFrame(sheet, shown, curFrame, walkP, alpha = fade.value)
        }
    }
}

private fun DrawScope.drawDogFrame(sheet: ImageBitmap, clip: DogClip, frame: Int, walkP: Float, alpha: Float) {
    val scale = size.height / CELL_H
    val dstW = CELL_W * scale
    val x = if (clip.moving) -dstW + (size.width + dstW) * walkP else (size.width - dstW) / 2f
    drawImage(
        image = sheet,
        srcOffset = IntOffset(frame.coerceIn(0, clip.frames - 1) * CELL_W, clip.row * CELL_H),
        srcSize = IntSize(CELL_W, CELL_H),
        dstOffset = IntOffset(x.roundToInt(), 0),
        dstSize = IntSize(dstW.roundToInt(), size.height.roundToInt()),
        alpha = alpha.coerceIn(0f, 1f),
    )
}
