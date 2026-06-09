package fyi.blep.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fyi.blep.resources.Res
import fyi.blep.resources.onboard_body_1
import fyi.blep.resources.onboard_body_2
import fyi.blep.resources.onboard_body_3
import fyi.blep.resources.onboard_done
import fyi.blep.resources.onboard_next
import fyi.blep.resources.onboard_skip
import fyi.blep.resources.onboard_title_1
import fyi.blep.resources.onboard_title_2
import fyi.blep.resources.onboard_title_3
import fyi.blep.ui.theme.BlepColors
import fyi.blep.ui.theme.BlepLogo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class OnboardPage(val title: StringResource, val body: StringResource)

/**
 * Skippable first-run intro: what blep does, the body-shielding finding technique
 * (with a drawn diagram), and a privacy/permission primer whose final button
 * triggers the OS Bluetooth prompt. [onDone] = finished (enable Bluetooth), [onSkip]
 * = dismissed early; both mark onboarding complete.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val pages = listOf(
        OnboardPage(Res.string.onboard_title_1, Res.string.onboard_body_1),
        OnboardPage(Res.string.onboard_title_2, Res.string.onboard_body_2),
        OnboardPage(Res.string.onboard_title_3, Res.string.onboard_body_3),
    )
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val onLast = pager.currentPage == pages.lastIndex

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterEnd)) {
                Text(stringResource(Res.string.onboard_skip), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
            }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            // Fixed-height bands (artwork / title / body) so the heading lands at the
            // same vertical position on every page — independent of artwork size or how
            // many lines the title/body wrap to.
            Column(
                Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.height(200.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when (page) {
                        1 -> BodyShieldDiagram(Modifier.size(196.dp))
                        2 -> ShieldIcon(Modifier.size(150.dp))
                        else -> Image(rememberVectorPainter(BlepLogo), contentDescription = null, modifier = Modifier.size(150.dp))
                    }
                }
                Spacer(Modifier.height(28.dp))
                Box(Modifier.height(76.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Text(
                        stringResource(pages[page].title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.height(132.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Text(
                        stringResource(pages[page].body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                val selected = i == pager.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (selected) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(if (selected) BlepColors.Blue else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f)),
                )
            }
        }

        Button(
            onClick = { if (onLast) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BlepColors.Blue, contentColor = BlepColors.Cream),
        ) {
            Text(stringResource(if (onLast) Res.string.onboard_done else Res.string.onboard_next))
        }
    }
}

/** A shield with a check — the privacy/permission card's icon. */
@Composable
private fun ShieldIcon(modifier: Modifier) {
    val blue = BlepColors.Blue
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val shield = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.84f, h * 0.22f)
            lineTo(w * 0.84f, h * 0.5f)
            cubicTo(w * 0.84f, h * 0.78f, w * 0.68f, h * 0.9f, w * 0.5f, h * 0.95f)
            cubicTo(w * 0.32f, h * 0.9f, w * 0.16f, h * 0.78f, w * 0.16f, h * 0.5f)
            lineTo(w * 0.16f, h * 0.22f)
            close()
        }
        drawPath(shield, blue.copy(alpha = 0.12f))
        drawPath(shield, blue, style = Stroke(width = w * 0.03f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val check = Path().apply {
            moveTo(w * 0.36f, h * 0.5f)
            lineTo(w * 0.46f, h * 0.62f)
            lineTo(w * 0.66f, h * 0.4f)
        }
        drawPath(check, blue, style = Stroke(width = w * 0.05f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** "Hold it to your chest, turn slowly": a filled person-bust silhouette holding the
 *  phone flat to the chest, ringed by a rotation arrow. Your body shields the signal
 *  from behind, so the reading points the way as you turn. Drawn — no image asset. */
@Composable
private fun BodyShieldDiagram(modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground
    val blue = BlepColors.Blue
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Soft badge halo behind the figure.
        drawCircle(blue.copy(alpha = 0.08f), radius = w * 0.46f, center = Offset(cx, cy))

        // Person bust — filled head + shoulders silhouette.
        drawCircle(ink.copy(alpha = 0.88f), radius = w * 0.135f, center = Offset(cx, cy - h * 0.17f))
        val bust = Path().apply {
            moveTo(cx - w * 0.28f, cy + h * 0.31f)
            cubicTo(cx - w * 0.28f, cy + h * 0.04f, cx - w * 0.12f, cy - h * 0.03f, cx, cy - h * 0.03f)
            cubicTo(cx + w * 0.12f, cy - h * 0.03f, cx + w * 0.28f, cy + h * 0.04f, cx + w * 0.28f, cy + h * 0.31f)
            close()
        }
        drawPath(bust, ink.copy(alpha = 0.88f))

        // Phone held flat to the chest, with a lighter screen.
        val pw = w * 0.17f
        val ph = w * 0.25f
        val ptl = Offset(cx - pw / 2f, cy + h * 0.04f)
        drawRoundRect(blue, topLeft = ptl, size = Size(pw, ph), cornerRadius = CornerRadius(w * 0.022f, w * 0.022f))
        drawRoundRect(
            BlepColors.Cream.copy(alpha = 0.9f),
            topLeft = Offset(ptl.x + pw * 0.17f, ptl.y + ph * 0.15f),
            size = Size(pw * 0.66f, ph * 0.6f),
            cornerRadius = CornerRadius(w * 0.012f, w * 0.012f),
        )

        // "Turn" — a near-full ring with an arrowhead at its clockwise end.
        val rr = w * 0.43f
        drawArc(
            color = blue,
            startAngle = 128f, sweepAngle = 274f, useCenter = false,
            topLeft = Offset(cx - rr, cy - rr), size = Size(rr * 2f, rr * 2f),
            style = Stroke(width = w * 0.028f, cap = StrokeCap.Round),
        )
        val endA = (128f + 274f) * (PI.toFloat() / 180f)
        val p = Offset(cx + rr * cos(endA), cy + rr * sin(endA))
        val tang = Offset(-sin(endA), cos(endA)) // clockwise tangent
        val rad = Offset(cos(endA), sin(endA))
        val ah = w * 0.062f
        val arrow = Path().apply {
            moveTo(p.x + tang.x * ah, p.y + tang.y * ah) // tip, ahead along the turn
            lineTo(p.x - tang.x * ah * 0.2f + rad.x * ah * 0.62f, p.y - tang.y * ah * 0.2f + rad.y * ah * 0.62f)
            lineTo(p.x - tang.x * ah * 0.2f - rad.x * ah * 0.62f, p.y - tang.y * ah * 0.2f - rad.y * ah * 0.62f)
            close()
        }
        drawPath(arrow, blue)
    }
}
