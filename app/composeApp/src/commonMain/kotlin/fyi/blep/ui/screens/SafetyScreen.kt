package fyi.blep.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fyi.blep.core.safety.Severity
import fyi.blep.core.safety.TrackerAlert
import fyi.blep.ui.theme.BlepColors

private val AMBER = Color(0xFFE8A33D)

@Composable
fun SafetyScreen(
    alerts: List<TrackerAlert>,
    rememberOn: Boolean,
    onToggleRemember: () -> Unit,
    onFind: (TrackerAlert) -> Unit,
    onMine: (TrackerAlert) -> Unit,
    lastMuted: TrackerAlert?,
    onUndoMute: () -> Unit,
    onMuteUndoShown: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(modifier.fillMaxSize().background(BlepColors.Mist)) {
    val snackbarHostState = remember { SnackbarHostState() }
    // Show a brief "Undo" when a tracker is marked mine — so an accidental tap (or
    // a change of mind) is reversible instead of a permanent, invisible mute.
    LaunchedEffect(lastMuted) {
        val muted = lastMuted ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Marked as yours — won't flag it again",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) onUndoMute() else onMuteUndoShown()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Is anything tracking you?", style = MaterialTheme.typography.headlineMedium, color = BlepColors.Ink)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScanningDot()
            Spacer(Modifier.width(8.dp))
            Text(
                "Scanning nearby Bluetooth for unwanted trackers…",
                style = MaterialTheme.typography.bodyLarge,
                color = BlepColors.Ink.copy(alpha = 0.55f),
            )
        }
        Spacer(Modifier.height(16.dp))

        if (alerts.isEmpty()) {
            AllClear(Modifier.weight(1f))
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(alerts) { AlertCard(it, onFind, onMine) }
            }
        }

        RememberToggle(rememberOn, onToggleRemember)

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Done", color = BlepColors.Ink.copy(alpha = 0.6f))
        }
    }

    SnackbarHost(
        snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing),
    )
  }
}

@Composable
private fun RememberToggle(on: Boolean, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Remember across sessions", style = MaterialTheme.typography.titleSmall, color = BlepColors.Ink)
                Text(
                    "Logs only the tracker type + time — no identity, no location — so a tag that keeps reappearing over hours gets flagged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BlepColors.Ink.copy(alpha = 0.55f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = on,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedTrackColor = BlepColors.Blue),
            )
        }
    }
}

@Composable
private fun AlertCard(alert: TrackerAlert, onFind: (TrackerAlert) -> Unit, onMine: (TrackerAlert) -> Unit) {
    val accent = when (alert.severity) {
        Severity.ALERT -> BlepColors.Pink
        Severity.WARN -> AMBER
        Severity.INFO -> BlepColors.Blue
    }
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(8.dp))
                Text(
                    alert.severity.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(alert.title, style = MaterialTheme.typography.titleMedium, color = BlepColors.Ink)
            Spacer(Modifier.height(2.dp))
            Text(alert.detail, style = MaterialTheme.typography.bodyMedium, color = BlepColors.Ink.copy(alpha = 0.65f))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${alert.rssi} dBm",
                    style = MaterialTheme.typography.labelLarge,
                    color = BlepColors.Ink.copy(alpha = 0.45f),
                    modifier = Modifier.weight(1f),
                )
                if (alert.trackingAddress != null) {
                    TextButton(onClick = { onMine(alert) }) {
                        Text("It's mine", color = BlepColors.Ink.copy(alpha = 0.55f))
                    }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = { onFind(alert) },
                        colors = ButtonDefaults.buttonColors(containerColor = BlepColors.Blue, contentColor = BlepColors.Cream),
                    ) { Text("Find it") }
                }
            }
        }
    }
}

@Composable
private fun AllClear(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Text("🛡️", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        Text("All clear", style = MaterialTheme.typography.titleLarge, color = BlepColors.Ink.copy(alpha = 0.7f))
        Spacer(Modifier.height(6.dp))
        Text(
            "No unwanted trackers near you right now. Keep this open for a minute while you move — a tracker following you will show up.",
            style = MaterialTheme.typography.bodyLarge,
            color = BlepColors.Ink.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun ScanningDot() {
    val a by rememberInfiniteTransition(label = "scan").animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), label = "scanAlpha",
    )
    Box(Modifier.size(10.dp).graphicsLayer { alpha = a }.clip(CircleShape).background(BlepColors.Blue))
}
