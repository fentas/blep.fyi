package fyi.blep.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fyi.blep.ui.theme.BlepColors
import fyi.blep.ui.theme.BlepLogo

@Composable
fun CompletionScreen(
    deviceName: String,
    onDone: () -> Unit,
    onDonate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Gentle spring-in of the celebration, mirroring the arrow shrinking away.
    var shown by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "completionScale",
    )
    androidx.compose.runtime.LaunchedEffect(Unit) { shown = true }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BlepColors.proximity(1f))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = rememberVectorPainter(BlepLogo),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(140.dp).scale(scale),
        )
        Spacer(Modifier.height(28.dp))
        Text(
            "Finished",
            style = MaterialTheme.typography.displayLarge,
            color = BlepColors.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Congratulations — you found $deviceName!",
            style = MaterialTheme.typography.bodyLarge,
            color = BlepColors.Ink.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = BlepColors.Blue, contentColor = BlepColors.Cream),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text("Track another", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onDonate,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text("♥  Help & donate", style = MaterialTheme.typography.titleMedium, color = BlepColors.Ink) }
    }
}
