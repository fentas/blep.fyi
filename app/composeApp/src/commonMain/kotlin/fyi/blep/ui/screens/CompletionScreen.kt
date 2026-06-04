package fyi.blep.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fyi.blep.resources.Res
import fyi.blep.resources.celebrate_1
import fyi.blep.resources.celebrate_10
import fyi.blep.resources.celebrate_11
import fyi.blep.resources.celebrate_12
import fyi.blep.resources.celebrate_2
import fyi.blep.resources.celebrate_3
import fyi.blep.resources.celebrate_4
import fyi.blep.resources.celebrate_5
import fyi.blep.resources.celebrate_6
import fyi.blep.resources.celebrate_7
import fyi.blep.resources.celebrate_8
import fyi.blep.resources.celebrate_9
import fyi.blep.resources.done_donate_blurb
import fyi.blep.resources.done_donate_button
import fyi.blep.resources.done_got_it
import fyi.blep.resources.done_here
import fyi.blep.resources.done_keep_looking
import fyi.blep.resources.done_on_top
import fyi.blep.resources.done_track_another
import fyi.blep.resources.subline_1
import fyi.blep.resources.subline_2
import fyi.blep.resources.subline_3
import fyi.blep.resources.subline_4
import fyi.blep.resources.subline_5
import fyi.blep.resources.subline_6
import fyi.blep.resources.subline_7
import fyi.blep.resources.subline_8
import fyi.blep.ui.theme.BlepColors
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val BURST_EMOJI = listOf("🎉", "✨", "🎈", "🥳", "⭐", "🙌", "💫", "🐾", "🎊")

@Composable
private fun celebrations(): List<String> = listOf(
    stringResource(Res.string.celebrate_1), stringResource(Res.string.celebrate_2),
    stringResource(Res.string.celebrate_3), stringResource(Res.string.celebrate_4),
    stringResource(Res.string.celebrate_5), stringResource(Res.string.celebrate_6),
    stringResource(Res.string.celebrate_7), stringResource(Res.string.celebrate_8),
    stringResource(Res.string.celebrate_9), stringResource(Res.string.celebrate_10),
    stringResource(Res.string.celebrate_11), stringResource(Res.string.celebrate_12),
)

@Composable
private fun sublines(): List<String> = listOf(
    stringResource(Res.string.subline_1), stringResource(Res.string.subline_2),
    stringResource(Res.string.subline_3), stringResource(Res.string.subline_4),
    stringResource(Res.string.subline_5), stringResource(Res.string.subline_6),
    stringResource(Res.string.subline_7), stringResource(Res.string.subline_8),
)

@Composable
fun CompletionScreen(
    deviceName: String,
    onDone: () -> Unit,
    onDonate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var celebrated by remember { mutableStateOf(false) }
    // Each visit picks a fresh headline, sub-line, and animation flavour.
    val celebs = celebrations()
    val subs = sublines()
    val celebration = remember { celebs[Random.nextInt(celebs.size)] }
    val subline = remember { subs[Random.nextInt(subs.size)] }
    val flavour = remember { Random.nextInt(3) } // 0 confetti · 1 emoji · 2 both

    Box(
        modifier = modifier.fillMaxSize().background(BlepColors.proximity(1f)),
        contentAlignment = Alignment.Center,
    ) {
        if (celebrated) {
            if (flavour != 1) Confetti(Modifier.fillMaxSize())
            if (flavour != 0) EmojiBurst(Modifier.fillMaxSize())
        }

        AnimatedContent(
            targetState = celebrated,
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
            label = "celebrate",
        ) { done ->
            if (!done) {
                FoundPanel(deviceName = deviceName, onGotIt = { celebrated = true }, onKeepLooking = onDone)
            } else {
                CelebratePanel(headline = celebration, subline = subline, onDonate = onDonate, onAnother = onDone)
            }
        }
    }
}

@Composable
private fun FoundPanel(deviceName: String, onGotIt: () -> Unit, onKeepLooking: () -> Unit) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(deviceName, style = MaterialTheme.typography.titleMedium, color = BlepColors.Ink.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(Res.string.done_here),
            style = MaterialTheme.typography.displayLarge,
            color = BlepColors.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(Res.string.done_on_top),
            style = MaterialTheme.typography.bodyLarge,
            color = BlepColors.Ink.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onGotIt,
            colors = ButtonDefaults.buttonColors(containerColor = BlepColors.Blue, contentColor = BlepColors.Cream),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text(stringResource(Res.string.done_got_it), style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onKeepLooking, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.done_keep_looking), color = BlepColors.Ink.copy(alpha = 0.6f))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CelebratePanel(headline: String, subline: String, onDonate: () -> Unit, onAnother: () -> Unit) {
    val pop by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pop",
    )
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            headline,
            style = MaterialTheme.typography.displayLarge,
            color = BlepColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { scaleX = pop; scaleY = pop },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            subline,
            style = MaterialTheme.typography.titleMedium,
            color = BlepColors.Ink.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(Res.string.done_donate_blurb),
            style = MaterialTheme.typography.bodyLarge,
            color = BlepColors.Ink.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onDonate,
            colors = ButtonDefaults.buttonColors(containerColor = BlepColors.Blue, contentColor = BlepColors.Cream),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text(stringResource(Res.string.done_donate_button), style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onAnother, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text(stringResource(Res.string.done_track_another), style = MaterialTheme.typography.titleMedium, color = BlepColors.Ink)
        }
    }
}

/** A short, gentle one-shot confetti drift from the top. */
@Composable
private fun Confetti(modifier: Modifier = Modifier) {
    val palette = listOf(BlepColors.Blue, BlepColors.Pink, BlepColors.proximity(0.7f), BlepColors.proximity(1f))
    val bits = remember {
        List(22) {
            ConfettiBit(
                xFrac = Random.nextFloat(),
                delay = Random.nextFloat() * 0.3f,
                drift = (Random.nextFloat() - 0.5f) * 0.2f,
                radius = 4f + Random.nextFloat() * 5f,
                color = palette[Random.nextInt(palette.size)],
            )
        }
    }
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val progress by animateFloatAsState(if (go) 1f else 0f, tween(1400), label = "confetti")

    Canvas(modifier) {
        bits.forEach { b ->
            val p = ((progress - b.delay) / (1f - b.delay)).coerceIn(0f, 1f)
            if (p <= 0f) return@forEach
            val x = (b.xFrac + b.drift * p) * size.width
            val y = (-0.05f + p * 0.85f) * size.height
            drawCircle(b.color.copy(alpha = (1f - p) * 0.9f), radius = b.radius, center = androidx.compose.ui.geometry.Offset(x, y))
        }
    }
}

/** A radial pop of celebratory emoji from the centre, scaling out and fading. */
@Composable
private fun EmojiBurst(modifier: Modifier = Modifier) {
    val emoji = remember { BURST_EMOJI.shuffled().take(7) }
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val p by animateFloatAsState(if (go) 1f else 0f, tween(1100), label = "burst")

    Box(modifier, contentAlignment = Alignment.Center) {
        emoji.forEachIndexed { i, e ->
            val ang = (i.toFloat() / emoji.size) * 2f * PI.toFloat()
            Text(
                e,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.graphicsLayer {
                    val reach = 360f * p
                    translationX = cos(ang) * reach
                    translationY = sin(ang) * reach
                    val s = (0.4f + p * 1.1f).coerceAtMost(1.5f)
                    scaleX = s; scaleY = s
                    alpha = (1f - p) * 0.85f + 0.12f
                },
            )
        }
    }
}

private data class ConfettiBit(
    val xFrac: Float,
    val delay: Float,
    val drift: Float,
    val radius: Float,
    val color: androidx.compose.ui.graphics.Color,
)
