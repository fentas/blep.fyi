package fyi.blep.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import fyi.blep.resources.alert_persistent_title
import fyi.blep.resources.alert_persistent_named
import fyi.blep.resources.alert_persistent_detail
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
import fyi.blep.resources.safety_need_help
import fyi.blep.core.safety.ScanSensitivity
import fyi.blep.ScanMode
import fyi.blep.resources.alert_cross_places
import fyi.blep.resources.settings_background_desc
import fyi.blep.resources.settings_background_title
import fyi.blep.resources.settings_scan_mode_title
import fyi.blep.resources.settings_scan_off
import fyi.blep.resources.settings_scan_interval
import fyi.blep.resources.settings_scan_continuous
import fyi.blep.ui.components.SensitivitySelector
import fyi.blep.ui.rememberNotificationPermissionRequest
import fyi.blep.ui.rememberNotificationsEnabled
import fyi.blep.resources.settings_notifications_off
import fyi.blep.resources.safety_scanning
import fyi.blep.resources.safety_title
import fyi.blep.resources.severity_alert
import fyi.blep.resources.severity_info
import fyi.blep.resources.severity_warn
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
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
    scanSensitivity: ScanSensitivity,
    onSelectSensitivity: (ScanSensitivity) -> Unit,
    scanMode: ScanMode,
    onSetScanMode: (ScanMode) -> Unit,
    onFind: (TrackerAlert, String) -> Unit,
    onMine: (TrackerAlert) -> Unit,
    lastMuted: TrackerAlert?,
    onUndoMute: () -> Unit,
    onMuteUndoShown: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
            Text(stringResource(Res.string.safety_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScanningDot()
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.safety_scanning),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
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

            SensitivitySelector(scanSensitivity, onSelectSensitivity, Modifier.fillMaxWidth())
            BackgroundModeSelector(scanMode, onSetScanMode)

            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(stringResource(Res.string.action_done), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
        }

        SnackbarHost(
            snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing),
        )
    }
}

@Composable
private fun BackgroundModeSelector(mode: ScanMode, onSet: (ScanMode) -> Unit) {
    val requestNotifications = rememberNotificationPermissionRequest()
    val notificationsEnabled = rememberNotificationsEnabled()
    // Reflects (and sets) whether blep keeps scanning for trackers off-screen — so the
    // safety panel shows the background scan state, not just the in-app one.
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(stringResource(Res.string.settings_scan_mode_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                    .padding(2.dp),
            ) {
                ModeChip(stringResource(Res.string.settings_scan_off), mode == ScanMode.OFF) { onSet(ScanMode.OFF) }
                ModeChip(stringResource(Res.string.settings_scan_interval), mode == ScanMode.INTERVAL) { if (!notificationsEnabled) requestNotifications(); onSet(ScanMode.INTERVAL) }
                ModeChip(stringResource(Res.string.settings_scan_continuous), mode == ScanMode.CONTINUOUS) { if (!notificationsEnabled) requestNotifications(); onSet(ScanMode.CONTINUOUS) }
            }
            if (mode != ScanMode.OFF && !notificationsEnabled) {
                Text(
                    stringResource(Res.string.settings_notifications_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = AMBER,
                    modifier = Modifier.padding(top = 8.dp).clickable { requestNotifications() },
                )
            }
        }
    }
}

/** Localized title for a structured [TrackerAlert]. */
@Composable
private fun alertTitle(alert: TrackerAlert): String = when (alert.reason) {
    AlertReason.FOLLOWING -> stringResource(Res.string.alert_following_title, kindLabel(alert.kind))
    AlertReason.SEPARATED_NEARBY -> stringResource(Res.string.alert_nearby_title, kindLabel(alert.kind))
    AlertReason.ROTATION -> stringResource(Res.string.alert_rotation_title)
    AlertReason.PERSISTENT -> alert.label?.takeIf { it.isNotBlank() }
        ?.let { stringResource(Res.string.alert_persistent_named, it) }
        ?: stringResource(Res.string.alert_persistent_title)
}

/** Localized detail for a structured [TrackerAlert], incl. the cross-session note. */
@Composable
private fun alertDetail(alert: TrackerAlert): String {
    val base = when (alert.reason) {
        AlertReason.FOLLOWING -> stringResource(Res.string.alert_following_detail, durLabel(alert.durationMs))
        AlertReason.SEPARATED_NEARBY -> stringResource(Res.string.alert_nearby_detail)
        AlertReason.ROTATION -> stringResource(Res.string.alert_rotation_detail, alert.distinctCount)
        AlertReason.PERSISTENT -> stringResource(Res.string.alert_persistent_detail, durLabel(alert.durationMs))
    }
    var out = base
    if (alert.crossSessionPlaces >= 2) {
        out += " " + stringResource(Res.string.alert_cross_places, alert.crossSessionPlaces)
    }
    if (alert.crossSessionHours >= 3) {
        out += " " + stringResource(Res.string.alert_cross_session, alert.crossSessionHours)
    }
    return out
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
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
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
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(2.dp))
            Text(alertDetail(alert), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.dbm, alert.rssi),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                    modifier = Modifier.weight(1f),
                )
                if (alert.trackingAddress != null) {
                    TextButton(onClick = { onMine(alert) }) {
                        Text(stringResource(Res.string.safety_its_mine), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
                    }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = { onFind(alert, title) },
                        colors = ButtonDefaults.buttonColors(containerColor = BlepColors.Blue, contentColor = BlepColors.Cream),
                    ) { Text(stringResource(Res.string.safety_find_it)) }
                }
            }
            // Calm, opt-in guidance — the detailed "what to do" lives on the website.
            val uriHandler = LocalUriHandler.current
            TextButton(onClick = { uriHandler.openUri(FOUND_HELP_URL) }, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(Res.string.safety_need_help), style = MaterialTheme.typography.labelLarge, color = BlepColors.Blue)
            }
        }
    }
}

private const val FOUND_HELP_URL = "https://blep.fyi/guide.html#found"

@Composable
private fun AllClear(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Text("🛡️", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.safety_all_clear), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(Res.string.safety_all_clear_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
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
