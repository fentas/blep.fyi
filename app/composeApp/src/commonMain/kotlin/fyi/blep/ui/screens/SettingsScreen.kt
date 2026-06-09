package fyi.blep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fyi.blep.resources.Res
import fyi.blep.resources.a11y_back
import fyi.blep.resources.a11y_more_info
import fyi.blep.AppSettings
import fyi.blep.ThemeMode
import fyi.blep.resources.settings_theme_title
import fyi.blep.resources.theme_dark
import fyi.blep.resources.theme_light
import fyi.blep.resources.theme_system
import fyi.blep.core.safety.ScanSensitivity
import fyi.blep.core.tether.TetherAlertDirection
import fyi.blep.resources.settings_tether_title
import fyi.blep.resources.settings_tether_desc
import fyi.blep.resources.settings_tether_leave
import fyi.blep.resources.settings_tether_return
import fyi.blep.resources.settings_tether_both
import fyi.blep.resources.settings_section_sync
import fyi.blep.resources.settings_sync_title
import fyi.blep.resources.settings_sync_desc
import fyi.blep.resources.settings_sync_favorites
import fyi.blep.resources.settings_sync_names
import fyi.blep.resources.settings_sync_tethered
import fyi.blep.resources.settings_sync_alerts
import fyi.blep.ui.components.SensitivitySelector
import fyi.blep.resources.action_done
import fyi.blep.resources.settings_background_desc
import fyi.blep.resources.settings_background_info
import fyi.blep.resources.settings_background_title
import fyi.blep.resources.settings_connected_signal_desc
import fyi.blep.resources.settings_connected_signal_title
import fyi.blep.resources.settings_foreground_desc
import fyi.blep.resources.settings_foreground_title
import fyi.blep.resources.settings_interval_h
import fyi.blep.resources.settings_interval_hm
import fyi.blep.resources.settings_interval_min
import fyi.blep.resources.settings_location_desc
import fyi.blep.resources.settings_location_title
import fyi.blep.resources.settings_section_background
import fyi.blep.resources.settings_remember_desc
import fyi.blep.resources.settings_remember_title
import fyi.blep.resources.settings_remember_devices_desc
import fyi.blep.resources.settings_remember_devices_title
import fyi.blep.resources.settings_days
import fyi.blep.resources.settings_probe_title
import fyi.blep.resources.settings_probe_desc
import fyi.blep.resources.settings_probe_after_title
import fyi.blep.resources.settings_probe_after_desc
import fyi.blep.resources.settings_minutes
import fyi.blep.resources.action_cancel
import fyi.blep.resources.settings_storage_title
import fyi.blep.resources.settings_storage_desc
import fyi.blep.resources.settings_storage_clear
import fyi.blep.resources.settings_storage_confirm_title
import fyi.blep.resources.settings_storage_confirm_body
import fyi.blep.resources.settings_haptics_desc
import fyi.blep.resources.settings_haptics_title
import fyi.blep.resources.settings_sound_desc
import fyi.blep.resources.settings_sound_title
import fyi.blep.resources.settings_title
import fyi.blep.resources.settings_unnamed_desc
import fyi.blep.resources.settings_unnamed_title
import fyi.blep.ui.BuildInfo
import fyi.blep.ui.VersionStamp
import fyi.blep.ui.rememberLocationPermissionRequest
import fyi.blep.ui.rememberNotificationPermissionRequest
import fyi.blep.ui.rememberNotificationsEnabled
import fyi.blep.resources.settings_notifications_off
import fyi.blep.ui.theme.BlepColors
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    measureConnectedSignal: Boolean,
    onToggleConnectedSignal: (Boolean) -> Unit,
    soundOn: Boolean,
    onToggleSound: (Boolean) -> Unit,
    hapticsOn: Boolean,
    onToggleHaptics: (Boolean) -> Unit,
    showUnnamed: Boolean,
    onToggleUnnamed: (Boolean) -> Unit,
    rememberTrackers: Boolean,
    onToggleRemember: (Boolean) -> Unit,
    scanSensitivity: ScanSensitivity,
    onSelectSensitivity: (ScanSensitivity) -> Unit,
    locationAware: Boolean,
    onToggleLocation: (Boolean) -> Unit,
    rememberDeviceDays: Int,
    onRememberDeviceDaysChange: (Int) -> Unit,
    probeEnabled: Boolean,
    onToggleProbe: (Boolean) -> Unit,
    probeThresholdMinutes: Int,
    onProbeThresholdChange: (Int) -> Unit,
    tetherAlert: TetherAlertDirection,
    onSelectTetherAlert: (TetherAlertDirection) -> Unit,
    storageBytes: Int,
    onClearStorage: () -> Unit,
    foregroundScan: Boolean,
    onToggleForeground: (Boolean) -> Unit,
    backgroundScan: Boolean,
    onToggleBackground: (Boolean) -> Unit,
    intervalMinutes: Int,
    onIntervalChange: (Int) -> Unit,
    syncEnabled: Boolean,
    onToggleSync: (Boolean) -> Unit,
    syncFavorites: Boolean,
    onToggleSyncFavorites: (Boolean) -> Unit,
    syncNames: Boolean,
    onToggleSyncNames: (Boolean) -> Unit,
    syncTethered: Boolean,
    onToggleSyncTethered: (Boolean) -> Unit,
    syncAlerts: Boolean,
    onToggleSyncAlerts: (Boolean) -> Unit,
    themeMode: ThemeMode,
    onSelectTheme: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    buildInfo: BuildInfo? = null,
) {
    // Ask for the notification permission when background scanning is switched on.
    val requestNotifications = rememberNotificationPermissionRequest()
    val requestLocation = rememberLocationPermissionRequest()
    var showBgInfo by remember { mutableStateOf(false) }
    val backLabel = stringResource(Res.string.a11y_back)
    val infoLabel = stringResource(Res.string.a11y_more_info)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹",
                style = MaterialTheme.typography.headlineMedium,
                color = BlepColors.Blue,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(end = 12.dp)
                    .semantics { contentDescription = backLabel },
            )
            Text(
                stringResource(Res.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThemeSelector(themeMode, onSelectTheme)
            SettingRow(
                title = stringResource(Res.string.settings_connected_signal_title),
                desc = stringResource(Res.string.settings_connected_signal_desc),
                checked = measureConnectedSignal,
                onToggle = onToggleConnectedSignal,
            )
            SettingRow(
                title = stringResource(Res.string.settings_sound_title),
                desc = stringResource(Res.string.settings_sound_desc),
                checked = soundOn,
                onToggle = onToggleSound,
            )
            SettingRow(
                title = stringResource(Res.string.settings_haptics_title),
                desc = stringResource(Res.string.settings_haptics_desc),
                checked = hapticsOn,
                onToggle = onToggleHaptics,
            )
            SettingRow(
                title = stringResource(Res.string.settings_unnamed_title),
                desc = stringResource(Res.string.settings_unnamed_desc),
                checked = showUnnamed,
                onToggle = onToggleUnnamed,
            )
            SettingRow(
                title = stringResource(Res.string.settings_remember_title),
                desc = stringResource(Res.string.settings_remember_desc),
                checked = rememberTrackers,
                onToggle = onToggleRemember,
            )
            RememberDevicesRow(rememberDeviceDays, onRememberDeviceDaysChange)

            SettingRow(
                title = stringResource(Res.string.settings_probe_title),
                desc = stringResource(Res.string.settings_probe_desc),
                checked = probeEnabled,
                onToggle = onToggleProbe,
            )
            if (probeEnabled) ProbeAfterRow(probeThresholdMinutes, onProbeThresholdChange)
            StorageRow(storageBytes, onClearStorage)

            SettingRow(
                title = stringResource(Res.string.settings_location_title),
                desc = stringResource(Res.string.settings_location_desc),
                checked = locationAware,
                onToggle = { on -> if (on) requestLocation(); onToggleLocation(on) },
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SensitivitySelector(
                    scanSensitivity, onSelectSensitivity,
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            TetherAlertSelector(tetherAlert, onSelectTetherAlert)

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.settings_section_background).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                Text(
                    "?",
                    style = MaterialTheme.typography.labelLarge,
                    color = BlepColors.Blue,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { showBgInfo = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .semantics { contentDescription = infoLabel },
                )
            }
            SettingRow(
                title = stringResource(Res.string.settings_foreground_title),
                desc = stringResource(Res.string.settings_foreground_desc),
                checked = foregroundScan,
                onToggle = { on -> if (on) requestNotifications(); onToggleForeground(on) },
            )
            BackgroundScanRow(
                checked = backgroundScan,
                onToggle = { on -> if (on) requestNotifications(); onToggleBackground(on) },
                intervalMinutes = intervalMinutes,
                onIntervalChange = onIntervalChange,
            )

            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(Res.string.settings_section_sync).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.padding(start = 4.dp),
            )
            SettingRow(
                title = stringResource(Res.string.settings_sync_title),
                desc = stringResource(Res.string.settings_sync_desc),
                checked = syncEnabled,
                onToggle = onToggleSync,
            )
            if (syncEnabled) {
                SettingRow(stringResource(Res.string.settings_sync_favorites), "", syncFavorites, onToggleSyncFavorites)
                SettingRow(stringResource(Res.string.settings_sync_names), "", syncNames, onToggleSyncNames)
                SettingRow(stringResource(Res.string.settings_sync_tethered), "", syncTethered, onToggleSyncTethered)
                SettingRow(stringResource(Res.string.settings_sync_alerts), "", syncAlerts, onToggleSyncAlerts)
            }

            Spacer(Modifier.height(20.dp))
            // Build identity, tap → prefilled GitHub issue. Hidden on iOS/Wear (null).
            buildInfo?.let {
                VersionStamp(it, Modifier.align(Alignment.CenterHorizontally))
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showBgInfo) {
        AlertDialog(
            onDismissRequest = { showBgInfo = false },
            title = { Text(stringResource(Res.string.settings_section_background)) },
            text = { Text(stringResource(Res.string.settings_background_info)) },
            confirmButton = {
                TextButton(onClick = { showBgInfo = false }) { Text(stringResource(Res.string.action_done)) }
            },
        )
    }
}

/** Slider for how long device identities (rename / flag / first-seen) are remembered
 *  after a device was last seen. */
@Composable
private fun RememberDevicesRow(days: Int, onChange: (Int) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.settings_remember_devices_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(Res.string.settings_remember_devices_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(4.dp))
            Slider(
                value = days.toFloat(),
                onValueChange = { onChange(it.roundToInt()) },
                valueRange = AppSettings.IDENTITY_TTL_MIN_DAYS.toFloat()..AppSettings.IDENTITY_TTL_MAX_DAYS.toFloat(),
                colors = SliderDefaults.colors(thumbColor = BlepColors.Blue, activeTrackColor = BlepColors.Blue),
            )
            Text(
                stringResource(Res.string.settings_days, days),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** Stored-data footprint + a Clear action (with a confirm dialog). */
@Composable
private fun StorageRow(bytes: Int, onClear: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.settings_storage_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(Res.string.settings_storage_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatBytes(bytes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
                TextButton(onClick = { confirm = true }) {
                    Text(stringResource(Res.string.settings_storage_clear), color = BlepColors.Pink)
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(Res.string.settings_storage_confirm_title)) },
            text = { Text(stringResource(Res.string.settings_storage_confirm_body)) },
            confirmButton = { TextButton(onClick = { onClear(); confirm = false }) { Text(stringResource(Res.string.settings_storage_clear), color = BlepColors.Pink) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

/** Bytes → a compact human size (B / KB / MB). Long math so it can't overflow. */
private fun formatBytes(b: Int): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    else -> "${(b.toLong() * 10 / (1024 * 1024)) / 10.0} MB"
}

/** Sub-option under "Identify devices": how long a device must linger before a probe. */
@Composable
private fun ProbeAfterRow(minutes: Int, onChange: (Int) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.settings_probe_after_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(Res.string.settings_probe_after_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(4.dp))
            Slider(
                value = minutes.toFloat(),
                onValueChange = { onChange(it.roundToInt()) },
                valueRange = AppSettings.PROBE_MIN_MINUTES.toFloat()..AppSettings.PROBE_MAX_MINUTES.toFloat(),
                colors = SliderDefaults.colors(thumbColor = BlepColors.Blue, activeTrackColor = BlepColors.Blue),
            )
            Text(
                stringResource(Res.string.settings_minutes, minutes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** "Scan in the background" toggle with the interval slider nested as a sub-option
 *  (slider on top, small centred label below) when it's on. */
@Composable
private fun BackgroundScanRow(
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    intervalMinutes: Int,
    onIntervalChange: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.settings_background_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(Res.string.settings_background_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = checked,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = BlepColors.Cream, checkedTrackColor = BlepColors.Blue),
                )
            }
            if (checked) {
                Spacer(Modifier.height(4.dp))
                val min = AppSettings.INTERVAL_MIN
                val max = AppSettings.INTERVAL_MAX
                Slider(
                    value = intervalMinutes.toFloat(),
                    onValueChange = { v -> onIntervalChange((v / 15f).roundToInt() * 15) },
                    valueRange = min.toFloat()..max.toFloat(),
                    steps = (max - min) / 15 - 1,
                    colors = SliderDefaults.colors(thumbColor = BlepColors.Blue, activeTrackColor = BlepColors.Blue),
                )
                Text(
                    intervalLabel(intervalMinutes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            if (checked && !rememberNotificationsEnabled()) {
                val requestNotifications = rememberNotificationPermissionRequest()
                Text(
                    stringResource(Res.string.settings_notifications_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE8A33D),
                    modifier = Modifier.padding(top = 8.dp).clickable { requestNotifications() },
                )
            }
        }
    }
}

@Composable
private fun intervalLabel(minutes: Int): String = when {
    minutes < 60 -> stringResource(Res.string.settings_interval_min, minutes)
    minutes % 60 == 0 -> stringResource(Res.string.settings_interval_h, minutes / 60)
    else -> stringResource(Res.string.settings_interval_hm, minutes / 60, minutes % 60)
}

@Composable
private fun ThemeSelector(mode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.settings_theme_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                    .padding(2.dp),
            ) {
                ThemeChip(stringResource(Res.string.theme_system), mode == ThemeMode.SYSTEM) { onSelect(ThemeMode.SYSTEM) }
                ThemeChip(stringResource(Res.string.theme_light), mode == ThemeMode.LIGHT) { onSelect(ThemeMode.LIGHT) }
                ThemeChip(stringResource(Res.string.theme_dark), mode == ThemeMode.DARK) { onSelect(ThemeMode.DARK) }
            }
        }
    }
}

/** "Left-behind alerts" direction: leave / return / both. Reuses the pill-chip style. */
@Composable
private fun TetherAlertSelector(dir: TetherAlertDirection, onSelect: (TetherAlertDirection) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.settings_tether_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(Res.string.settings_tether_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                    .padding(2.dp),
            ) {
                ThemeChip(stringResource(Res.string.settings_tether_both), dir == TetherAlertDirection.BOTH) { onSelect(TetherAlertDirection.BOTH) }
                ThemeChip(stringResource(Res.string.settings_tether_leave), dir == TetherAlertDirection.LEAVE) { onSelect(TetherAlertDirection.LEAVE) }
                ThemeChip(stringResource(Res.string.settings_tether_return), dir == TetherAlertDirection.RETURN) { onSelect(TetherAlertDirection.RETURN) }
            }
        }
    }
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) BlepColors.Blue else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) BlepColors.Cream else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun SettingRow(title: String, desc: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(
        onClick = { onToggle(!checked) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = BlepColors.Cream, checkedTrackColor = BlepColors.Blue),
            )
        }
    }
}
