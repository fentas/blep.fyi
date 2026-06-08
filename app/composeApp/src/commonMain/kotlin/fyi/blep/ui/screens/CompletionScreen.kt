package fyi.blep.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import fyi.blep.resources.dbm
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
import fyi.blep.resources.tracking_db_hint
import fyi.blep.ui.theme.BlepColors
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Motion curves (matching the SVG-animation craft: ease-out for things flying
// out, a slight overshoot/bounce for things popping in).
private val EaseOutCubic = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val Overshoot = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** Festive palette shared by every celebration flavour. */
private val PARTY = listOf(
    BlepColors.Blue, BlepColors.Pink, BlepColors.Gold,
    BlepColors.proximity(0.55f), BlepColors.proximity(0.9f),
)

/** One-shot celebration overlays. Each visit picks one at random. */
private enum class CelebrationKind { FIREWORKS, RIBBONS, SPARKLES }

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
    rssi: Int? = null,      // live signal, so the found panel can pinpoint the spot
    modifier: Modifier = Modifier,
) {
    var celebrated by remember { mutableStateOf(false) }
    // Each visit picks a fresh headline, sub-line, and animation flavour.
    val celebs = celebrations()
    val subs = sublines()
    val celebration = remember { celebs[Random.nextInt(celebs.size)] }
    val subline = remember { subs[Random.nextInt(subs.size)] }
    val kind = remember { CelebrationKind.entries[Random.nextInt(CelebrationKind.entries.size)] }

    // Dark theme: a dark background tinted by the close-range "success" green
    // (still celebratory, not black) with light ink; light theme keeps the bright
    // pastel green it always had.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ink = if (dark) Color(0xFFE7EBEF) else BlepColors.Ink
    val bg = if (dark) lerp(BlepColors.proximity(1f, dark = true), Color(0xFF14181D), 0.45f) else BlepColors.proximity(1f)

    Box(
        modifier = modifier.fillMaxSize().background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (celebrated) {
            when (kind) {
                CelebrationKind.FIREWORKS -> Fireworks(Modifier.fillMaxSize())
                CelebrationKind.RIBBONS -> Ribbons(Modifier.fillMaxSize())
                CelebrationKind.SPARKLES -> Sparkles(Modifier.fillMaxSize())
            }
        }

        AnimatedContent(
            targetState = celebrated,
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
            label = "celebrate",
        ) { done ->
            if (!done) {
                FoundPanel(deviceName = deviceName, ink = ink, rssi = rssi, onGotIt = { celebrated = true }, onKeepLooking = onDone)
            } else {
                CelebratePanel(headline = celebration, subline = subline, ink = ink, onDonate = onDonate, onAnother = onDone)
            }
        }
    }
}

@Composable
private fun FoundPanel(deviceName: String, ink: Color, rssi: Int?, onGotIt: () -> Unit, onKeepLooking: () -> Unit) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Text block centred in the space above the bottom-anchored buttons.
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(deviceName, style = MaterialTheme.typography.titleMedium, color = ink.copy(alpha = 0.6f))
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(Res.string.done_here),
                style = MaterialTheme.typography.displayLarge,
                color = ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(Res.string.done_on_top),
                style = MaterialTheme.typography.bodyLarge,
                color = ink.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            // Live signal so this panel is still a tool: sweep the phone to the
            // exact spot and watch it peak.
            if (rssi != null) {
                Spacer(Modifier.height(22.dp))
                Text(stringResource(Res.string.dbm, rssi), style = MaterialTheme.typography.displaySmall, color = ink)
                Text(
                    stringResource(Res.string.tracking_db_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = ink.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        Button(
            onClick = onGotIt,
            colors = ButtonDefaults.buttonColors(containerColor = BlepColors.Blue, contentColor = BlepColors.Cream),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text(stringResource(Res.string.done_got_it), style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onKeepLooking, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.done_keep_looking), color = ink.copy(alpha = 0.6f))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CelebratePanel(headline: String, subline: String, ink: Color, onDonate: () -> Unit, onAnother: () -> Unit) {
    val pop by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pop",
    )
    // Text + seal centred in the space above the bottom-anchored buttons, so the
    // buttons line up with the FoundPanel's; particles fill the space overhead.
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Hero: a ring + checkmark that draw themselves on, for the satisfying beat.
            SuccessSeal(Modifier.size(96.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                headline,
                style = MaterialTheme.typography.displayLarge,
                color = ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { scaleX = pop; scaleY = pop },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                subline,
                style = MaterialTheme.typography.titleMedium,
                color = ink.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(Res.string.done_donate_blurb),
                style = MaterialTheme.typography.bodyLarge,
                color = ink.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
        Button(
            onClick = onDonate,
            colors = ButtonDefaults.buttonColors(containerColor = BlepColors.Blue, contentColor = BlepColors.Cream),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text(stringResource(Res.string.done_donate_button), style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onAnother, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text(stringResource(Res.string.done_track_another), style = MaterialTheme.typography.titleMedium, color = ink)
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** A drawn-on success ring with a checkmark that strokes in after it
 *  (stroke-dashoffset, the SVG way) — the hero beat of the celebration. */
@Composable
private fun SuccessSeal(modifier: Modifier = Modifier) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val ring by animateFloatAsState(if (go) 1f else 0f, tween(560, easing = EaseOutCubic), label = "ring")
    val check by animateFloatAsState(if (go) 1f else 0f, tween(440, delayMillis = 440, easing = EaseInOut), label = "check")

    Canvas(modifier) {
        val s = size.minDimension
        val stroke = s * 0.08f
        val r = s * 0.40f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawArc(
            color = BlepColors.Blue,
            startAngle = -90f,
            sweepAngle = 360f * ring,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // Checkmark: two strokes A→B→C drawn in sequence.
        val a = Offset(c.x - r * 0.42f, c.y + r * 0.04f)
        val b = Offset(c.x - r * 0.08f, c.y + r * 0.36f)
        val d = Offset(c.x + r * 0.46f, c.y - r * 0.30f)
        val seg1 = (check / 0.42f).coerceIn(0f, 1f)
        val seg2 = ((check - 0.42f) / 0.58f).coerceIn(0f, 1f)
        if (seg1 > 0f) drawLine(BlepColors.Blue, a, lerp(a, b, seg1), strokeWidth = stroke, cap = StrokeCap.Round)
        if (seg2 > 0f) drawLine(BlepColors.Blue, b, lerp(b, d, seg2), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** Staggered radial spark bursts with ease-out reach, gravity sag and a trailing
 *  streak — reads like small fireworks rather than flat dots. */
@Composable
private fun Fireworks(modifier: Modifier = Modifier) {
    // Bursts sit in the open upper third so they read clearly above the centred text.
    val bursts = remember {
        List(4) { i ->
            val n = 18 + Random.nextInt(10)
            Burst(
                cx = 0.15f + Random.nextFloat() * 0.70f,
                cy = 0.12f + Random.nextFloat() * 0.30f,
                delay = i * 0.15f + Random.nextFloat() * 0.05f,
                sparks = List(n) { j ->
                    val base = j.toFloat() / n * 2f * PI.toFloat()
                    Spark(
                        angle = base + (Random.nextFloat() - 0.5f) * 0.22f,
                        speed = 0.75f + Random.nextFloat() * 0.5f,
                        radius = 5f + Random.nextFloat() * 4f,
                        color = PARTY[Random.nextInt(PARTY.size)],
                    )
                },
            )
        }
    }
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val t by animateFloatAsState(if (go) 1f else 0f, tween(2100), label = "fireworks")

    Canvas(modifier) {
        val maxReach = size.minDimension * 0.42f
        bursts.forEach { burst ->
            val lp = ((t - burst.delay) / 0.6f).coerceIn(0f, 1f)
            if (lp <= 0f) return@forEach
            val e = EaseOutCubic.transform(lp)
            val ox = burst.cx * size.width
            val oy = burst.cy * size.height
            val sag = 140f * lp * lp
            // Stays bright through the first half, then fades — fireworks, not a dribble.
            val alpha = (1f - (lp * lp)).coerceIn(0f, 1f)
            burst.sparks.forEach { sp ->
                val reach = e * maxReach * sp.speed
                val gx = ox + cos(sp.angle) * reach
                val gy = oy + sin(sp.angle) * reach + sag
                val tx = ox + cos(sp.angle) * reach * 0.7f
                val ty = oy + sin(sp.angle) * reach * 0.7f + sag * 0.7f
                drawLine(sp.color.copy(alpha = alpha * 0.5f), Offset(tx, ty), Offset(gx, gy), strokeWidth = sp.radius * 0.8f, cap = StrokeCap.Round)
                drawCircle(sp.color.copy(alpha = alpha), radius = sp.radius * (1f - 0.25f * lp), center = Offset(gx, gy))
            }
        }
    }
}

/** Spinning, swaying ribbon confetti falling under ease-in gravity. */
@Composable
private fun Ribbons(modifier: Modifier = Modifier) {
    val ribbons = remember {
        List(38) {
            Ribbon(
                xFrac = Random.nextFloat(),
                delay = Random.nextFloat() * 0.4f,
                sway = 0.04f + Random.nextFloat() * 0.08f,
                swayFreq = 4f + Random.nextFloat() * 4f,
                phase = Random.nextFloat() * 2f * PI.toFloat(),
                spin = (if (Random.nextBoolean()) 1f else -1f) * (1.5f + Random.nextFloat() * 2f),
                w = 11f + Random.nextFloat() * 10f,
                h = 18f + Random.nextFloat() * 16f,
                color = PARTY[Random.nextInt(PARTY.size)],
            )
        }
    }
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val t by animateFloatAsState(if (go) 1f else 0f, tween(2200), label = "ribbons")

    Canvas(modifier) {
        ribbons.forEach { rb ->
            val lp = ((t - rb.delay) / (1f - rb.delay)).coerceIn(0f, 1f)
            if (lp <= 0f) return@forEach
            val y = (-0.1f + lp * 1.2f) * size.height
            val x = (rb.xFrac + sin(lp * rb.swayFreq + rb.phase) * rb.sway) * size.width
            val alpha = if (lp < 0.82f) 1f else ((1f - lp) / 0.18f).coerceIn(0f, 1f)
            rotate(rb.spin * lp * 360f, Offset(x, y)) {
                drawRoundRect(
                    color = rb.color.copy(alpha = alpha),
                    topLeft = Offset(x - rb.w / 2f, y - rb.h / 2f),
                    size = Size(rb.w, rb.h),
                    cornerRadius = CornerRadius(rb.w * 0.35f, rb.w * 0.35f),
                )
            }
        }
    }
}

/** Four-point sparkles that twinkle in (overshoot) and fade out, staggered. */
@Composable
private fun Sparkles(modifier: Modifier = Modifier) {
    val stars = remember {
        List(22) {
            Star(
                xFrac = 0.06f + Random.nextFloat() * 0.88f,
                yFrac = 0.08f + Random.nextFloat() * 0.74f,
                delay = Random.nextFloat() * 0.6f,
                peak = 16f + Random.nextFloat() * 20f,
                color = PARTY[Random.nextInt(PARTY.size)],
            )
        }
    }
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val t by animateFloatAsState(if (go) 1f else 0f, tween(2000), label = "sparkles")

    Canvas(modifier) {
        stars.forEach { st ->
            val lp = ((t - st.delay) / (1f - st.delay)).coerceIn(0f, 1f)
            if (lp <= 0f) return@forEach
            // twinkle: overshoot up over the first 40%, ease down the rest.
            val scale = if (lp < 0.4f) Overshoot.transform(lp / 0.4f) else 1f - (lp - 0.4f) / 0.6f
            if (scale <= 0f) return@forEach
            drawSparkle(
                center = Offset(st.xFrac * size.width, st.yFrac * size.height),
                radius = st.peak * scale,
                color = st.color.copy(alpha = scale.coerceIn(0f, 1f)),
            )
        }
    }
}

/** A 4-pointed sparkle (outer points on the axes, concave between). */
private fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color) {
    val inner = radius * 0.32f
    val path = Path()
    for (i in 0 until 8) {
        val ang = i * (PI / 4.0)
        val rad = if (i % 2 == 0) radius else inner
        val x = center.x + (cos(ang) * rad).toFloat()
        val y = center.y + (sin(ang) * rad).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

private data class Spark(val angle: Float, val speed: Float, val radius: Float, val color: Color)
private data class Burst(val cx: Float, val cy: Float, val delay: Float, val sparks: List<Spark>)
private data class Ribbon(
    val xFrac: Float, val delay: Float, val sway: Float, val swayFreq: Float,
    val phase: Float, val spin: Float, val w: Float, val h: Float, val color: Color,
)
private data class Star(val xFrac: Float, val yFrac: Float, val delay: Float, val peak: Float, val color: Color)
