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
import fyi.blep.ui.theme.BlepColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val CELEBRATIONS = listOf(
    "Found it! 🎉", "Gotcha! 🎯", "There you are!", "Reunited! 🎉",
    "Nailed it! ✨", "Hurray! 🎉", "Got it! 🙌", "Tracked down!",
    "Mission complete 🥳", "Bingo! 🎉", "Recovered! ✨", "Sweet success!",
)

private val SUBLINES = listOf(
    "That's one less thing to worry about.",
    "Back where it belongs. ✨",
    "Phew — saved you a hunt.",
    "Crisis averted. 🙌",
    "Nice teamwork. 🐾",
    "Right where blep said it'd be.",
    "Hope it wasn't hiding too long.",
    "Another one found.",
)

private val BURST_EMOJI = listOf("🎉", "✨", "🎈", "🥳", "⭐", "🙌", "💫", "🐾", "🎊")

@Composable
fun CompletionScreen(
    deviceName: String,
    onDone: () -> Unit,
    onDonate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var celebrated by remember { mutableStateOf(false) }
    // Each visit picks a fresh headline, sub-line, and animation flavour.
    val celebration = remember { CELEBRATIONS[Random.nextInt(CELEBRATIONS.size)] }
    val subline = remember { SUBLINES[Random.nextInt(SUBLINES.size)] }
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
            "Right here?",
            style = MaterialTheme.typography.displayLarge,
            color = BlepColors.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "You're right on top of it.",
            style = MaterialTheme.typography.bodyLarge,
            color = BlepColors.Ink.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onGotIt,
            colors = ButtonDefaults.buttonColors(containerColor = BlepColors.Blue, contentColor = BlepColors.Cream),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text("Got it 👍", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onKeepLooking, modifier = Modifier.fillMaxWidth()) {
            Text("Keep looking", color = BlepColors.Ink.copy(alpha = 0.6f))
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
            "If blep saved you some time, a small tip keeps it going. ♥",
            style = MaterialTheme.typography.bodyLarge,
            color = BlepColors.Ink.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onDonate,
            colors = ButtonDefaults.buttonColors(containerColor = BlepColors.Blue, contentColor = BlepColors.Cream),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text("♥  Help & donate", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onAnother, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("Track another", style = MaterialTheme.typography.titleMedium, color = BlepColors.Ink)
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
