package fyi.blep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fyi.blep.resources.Res
import fyi.blep.resources.settings_connected_signal_desc
import fyi.blep.resources.settings_connected_signal_title
import fyi.blep.resources.settings_remember_desc
import fyi.blep.resources.settings_remember_title
import fyi.blep.resources.settings_sound_desc
import fyi.blep.resources.settings_sound_title
import fyi.blep.resources.settings_title
import fyi.blep.resources.settings_unnamed_desc
import fyi.blep.resources.settings_unnamed_title
import fyi.blep.ui.theme.BlepColors
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    measureConnectedSignal: Boolean,
    onToggleConnectedSignal: (Boolean) -> Unit,
    soundOn: Boolean,
    onToggleSound: (Boolean) -> Unit,
    showUnnamed: Boolean,
    onToggleUnnamed: (Boolean) -> Unit,
    rememberTrackers: Boolean,
    onToggleRemember: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BlepColors.Mist)
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
                    .padding(end = 12.dp),
            )
            Text(
                stringResource(Res.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                color = BlepColors.Ink,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        }
    }
}

@Composable
private fun SettingRow(title: String, desc: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(
        onClick = { onToggle(!checked) },
        shape = RoundedCornerShape(18.dp),
        color = androidx.compose.ui.graphics.Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = BlepColors.Ink)
                Spacer(Modifier.height(2.dp))
                Text(desc, style = MaterialTheme.typography.bodySmall, color = BlepColors.Ink.copy(alpha = 0.55f))
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
