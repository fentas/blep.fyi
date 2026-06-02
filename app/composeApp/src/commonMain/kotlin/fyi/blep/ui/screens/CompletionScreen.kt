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
import androidx.compose.foundation.layout.size
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
import fyi.blep.ui.components.VectorArrow
import fyi.blep.ui.theme.BlepColors
import kotlin.random.Random

private val CELEBRATIONS = listOf(
    "Hurray! 🎉",
    "Awesome!",
    "Juhu! 🎉",
    "Glad it worked out!",
    "Time saved!",
    "Found at last!",
    "Nice — reunited!",
)

@Composable
fun CompletionScreen(
    deviceName: String,
    onDone: () -> Unit,
    onDonate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var celebrated by remember { mutableStateOf(false) }
    val celebration = remember { CELEBRATIONS[Random.nextInt(CELEBRATIONS.size)] }

    // The guidance arrow lands: it shrinks, floats up and fades into a faint
    // background watermark as the screen settles.
    var landed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { landed = true }
    val arrowScale by animateFloatAsState(if (landed) 0.4f else 1.1f, tween(900), label = "landScale")
    val arrowShift by animateFloatAsState(if (landed) -160f else 0f, tween(900), label = "landShift")
    val arrowAlpha by animateFloatAsState(if (landed) 0.14f else 0.85f, tween(900), label = "landAlpha")

    Box(
        modifier = modifier.fillMaxSize().background(BlepColors.proximity(1f)),
        contentAlignment = Alignment.Center,
    ) {
        // Faint floating arrow (settles near the top).
        VectorArrow(
            curl = 0f,
            scale = arrowScale,
            tint = BlepColors.Ink.copy(alpha = arrowAlpha),
            modifier = Modifier.graphicsLayer { translationY = arrowShift },
        )

        if (celebrated) Confetti(Modifier.fillMaxSize())

        AnimatedContent(
            targetState = celebrated,
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
            label = "celebrate",
        ) { done ->
            if (!done) {
                FoundPanel(deviceName = deviceName, onGotIt = { celebrated = true }, onKeepLooking = onDone)
            } else {
                CelebratePanel(headline = celebration, onDonate = onDonate, onAnother = onDone)
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
private fun CelebratePanel(headline: String, onDonate: () -> Unit, onAnother: () -> Unit) {
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
        Spacer(Modifier.height(12.dp))
        Text(
            "Glad it helped. If blep saved you some time, maybe help me out?",
            style = MaterialTheme.typography.bodyLarge,
            color = BlepColors.Ink.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
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
        List(18) {
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

private data class ConfettiBit(
    val xFrac: Float,
    val delay: Float,
    val drift: Float,
    val radius: Float,
    val color: androidx.compose.ui.graphics.Color,
)
