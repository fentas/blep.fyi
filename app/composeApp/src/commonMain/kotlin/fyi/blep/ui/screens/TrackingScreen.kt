package fyi.blep.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fyi.blep.core.tracking.TrackingStatus
import fyi.blep.ui.components.VectorArrow
import fyi.blep.ui.theme.BlepColors

@Composable
fun TrackingScreen(
    deviceName: String,
    status: TrackingStatus,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Background crossfades along the proximity gradient ("getting warmer").
    val background by animateColorAsState(
        targetValue = BlepColors.proximity(status.proximity),
        animationSpec = tween(durationMillis = 800),
        label = "trackingBackground",
    )
    // Arrow stays readable on any background.
    val arrowTint = BlepColors.Ink.copy(alpha = 0.82f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = deviceName,
            style = MaterialTheme.typography.titleMedium,
            color = BlepColors.Ink.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 16.dp),
        )

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            VectorArrow(
                curl = status.arrow.curl,
                scale = status.arrow.scale,
                tint = arrowTint,
            )
        }

        // Instruction copy crossfades softly between phases.
        AnimatedContent(
            targetState = status.guidance,
            transitionSpec = {
                (fadeIn(tween(300)) togetherWith fadeOut(tween(200)))
            },
            label = "guidance",
        ) { guidance ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = guidance.title,
                    style = MaterialTheme.typography.displayLarge,
                    color = BlepColors.Ink,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = guidance.detail,
                    style = MaterialTheme.typography.bodyLarge,
                    color = BlepColors.Ink.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        Text(
            text = "Cancel",
            style = MaterialTheme.typography.labelLarge,
            color = BlepColors.Ink.copy(alpha = 0.55f),
            modifier = Modifier
                .padding(top = 28.dp, bottom = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 24.dp, vertical = 10.dp),
        )
    }
}
