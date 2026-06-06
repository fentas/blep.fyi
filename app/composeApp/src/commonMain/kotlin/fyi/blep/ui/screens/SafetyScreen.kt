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
import fyi.blep.resources.Res
import fyi.blep.resources.action_done
import fyi.blep.resources.action_undo
import fyi.blep.resources.alert_cross_session
import fyi.blep.resources.alert_following_detail
import fyi.blep.resources.alert_following_title
import fyi.blep.resources.alert_nearby_detail
import fyi.blep.resources.alert_nearby_title
import fyi.blep.resources.alert_rotation_detail
import fyi.blep.resources.alert_rotation_title
import fyi.blep.resources.dbm
import fyi.blep.resources.dur_a_little_while
import fyi.blep.resources.dur_minutes
import fyi.blep.resources.kind_dult
import fyi.blep.resources.kind_find_my
import fyi.blep.resources.kind_google_find_my
import fyi.blep.resources.kind_smarttag
import fyi.blep.resources.kind_tile
import fyi.blep.resources.kind_unknown
import fyi.blep.resources.safety_all_clear
import fyi.blep.resources.safety_all_clear_body
import fyi.blep.resources.safety_find_it
import fyi.blep.resources.safety_its_mine
import fyi.blep.resources.safety_muted_undo
import fyi.blep.resources.settings_background_desc
import fyi.blep.resources.settings_background_title
import fyi.blep.ui.rememberNotificationPermissionRequest
import fyi.blep.resources.safety_scanning
import fyi.blep.resources.safety_title
import fyi.blep.resources.severity_alert
import fyi.blep.resources.severity_info
import fyi.blep.resources.severity_warn
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fyi.blep.core.safety.AlertReason
import fyi.blep.core.safety.Severity
import fyi.blep.core.safety.TrackerAlert
import fyi.blep.core.safety.TrackerKind
import fyi.blep.ui.theme.BlepColors

private val AMBER = Color(0xFFE8A33D)

@Composable
fun SafetyScreen(
    alerts: List<TrackerAlert>,
    backgroundOn: Boolean,
    onToggleBackground: (Boolean) -> Unit,
    onFind: (TrackerAlert, String) -> Unit,
    onMine: (TrackerAlert) -> Unit,
    lastMuted: TrackerAlert?,
    onUndoMute: () -> Unit,
    onMuteUndoShown: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(BlepColors.Mist)) {
        val snackbarHostState = remember { SnackbarHostState() }
        // Snackbar runs in a coroutine, so resolve its strings in composition first.
        val mutedMessage = stringResource(Res.string.safety_muted_undo)
        val undoLabel = stringResource(Res.string.action_undo)
        // Show a brief "Undo" when a tracker is marked mine — so an accidental tap (or
        // a change of mind) is reversible instead of a permanent, invisible mute.
        LaunchedEffect(lastMuted) {
            if (lastMuted == null) return@LaunchedEffect
            val result = snackbarHostState.showSnackbar(
                message = mutedMessage,
                actionLabel = undoLabel,
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
            Text(stringResource(Res.string.safety_title), style = MaterialTheme.typography.headlineMedium, color = BlepColors.Ink)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScanningDot()
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.safety_scanning),
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

            BackgroundToggle(backgroundOn, onToggleBackground)

            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(stringResource(Res.string.action_done), color = BlepColors.Ink.copy(alpha = 0.6f))
            }
        }

        SnackbarHost(
            snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing),
        )
    }
}

@Composable
private fun BackgroundToggle(on: Boolean, onToggle: (Boolean) -> Unit) {
    val requestNotifications = rememberNotificationPermissionRequest()
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
                Text(stringResource(Res.string.settings_background_title), style = MaterialTheme.typography.titleSmall, color = BlepColors.Ink)
                Text(
                    stringResource(Res.string.settings_background_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = BlepColors.Ink.copy(alpha = 0.55f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = on,
                onCheckedChange = { v -> if (v) requestNotifications(); onToggle(v) },
                colors = SwitchDefaults.colors(checkedTrackColor = BlepColors.Blue),
            )
        }
    }
}

/** Localized title for a structured [TrackerAlert]. */
@Composable
private fun alertTitle(alert: TrackerAlert): String = when (alert.reason) {
    AlertReason.FOLLOWING -> stringResource(Res.string.alert_following_title, kindLabel(alert.kind))
    AlertReason.SEPARATED_NEARBY -> stringResource(Res.string.alert_nearby_title, kindLabel(alert.kind))
    AlertReason.ROTATION -> stringResource(Res.string.alert_rotation_title)
}

/** Localized detail for a structured [TrackerAlert], incl. the cross-session note. */
@Composable
private fun alertDetail(alert: TrackerAlert): String {
    val base = when (alert.reason) {
        AlertReason.FOLLOWING -> stringResource(Res.string.alert_following_detail, durLabel(alert.durationMs))
        AlertReason.SEPARATED_NEARBY -> stringResource(Res.string.alert_nearby_detail)
        AlertReason.ROTATION -> stringResource(Res.string.alert_rotation_detail, alert.distinctCount)
    }
    return if (alert.crossSessionHours >= 3) {
        base + " " + stringResource(Res.string.alert_cross_session, alert.crossSessionHours)
    } else {
        base
    }
}

@Composable
private fun kindLabel(kind: TrackerKind): String = stringResource(
    when (kind) {
        TrackerKind.FIND_MY -> Res.string.kind_find_my
        TrackerKind.GOOGLE_FIND_MY -> Res.string.kind_google_find_my
        TrackerKind.TILE -> Res.string.kind_tile
        TrackerKind.SMARTTAG -> Res.string.kind_smarttag
        TrackerKind.DULT -> Res.string.kind_dult
        TrackerKind.UNKNOWN -> Res.string.kind_unknown
    },
)

@Composable
private fun durLabel(ms: Long): String =
    if (ms < 60_000L) stringResource(Res.string.dur_a_little_while)
    else stringResource(Res.string.dur_minutes, (ms / 60_000L).toInt())

@Composable
private fun AlertCard(alert: TrackerAlert, onFind: (TrackerAlert, String) -> Unit, onMine: (TrackerAlert) -> Unit) {
    val accent = when (alert.severity) {
        Severity.ALERT -> BlepColors.Pink
        Severity.WARN -> AMBER
        Severity.INFO -> BlepColors.Blue
    }
    val severityLabel = stringResource(
        when (alert.severity) {
            Severity.ALERT -> Res.string.severity_alert
            Severity.WARN -> Res.string.severity_warn
            Severity.INFO -> Res.string.severity_info
        },
    )
    val title = alertTitle(alert)
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(8.dp))
                Text(
                    severityLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = BlepColors.Ink)
            Spacer(Modifier.height(2.dp))
            Text(alertDetail(alert), style = MaterialTheme.typography.bodyMedium, color = BlepColors.Ink.copy(alpha = 0.65f))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.dbm, alert.rssi),
                    style = MaterialTheme.typography.labelLarge,
                    color = BlepColors.Ink.copy(alpha = 0.45f),
                    modifier = Modifier.weight(1f),
                )
                if (alert.trackingAddress != null) {
                    TextButton(onClick = { onMine(alert) }) {
                        Text(stringResource(Res.string.safety_its_mine), color = BlepColors.Ink.copy(alpha = 0.55f))
                    }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = { onFind(alert, title) },
                        colors = ButtonDefaults.buttonColors(containerColor = BlepColors.Blue, contentColor = BlepColors.Cream),
                    ) { Text(stringResource(Res.string.safety_find_it)) }
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
        Text(stringResource(Res.string.safety_all_clear), style = MaterialTheme.typography.titleLarge, color = BlepColors.Ink.copy(alpha = 0.7f))
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(Res.string.safety_all_clear_body),
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
