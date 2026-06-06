package fyi.blep.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fyi.blep.core.safety.ScanSensitivity
import fyi.blep.resources.Res
import fyi.blep.resources.sensitivity_balanced
import fyi.blep.resources.sensitivity_balanced_hint
import fyi.blep.resources.sensitivity_relaxed
import fyi.blep.resources.sensitivity_relaxed_hint
import fyi.blep.resources.sensitivity_strict
import fyi.blep.resources.sensitivity_strict_hint
import fyi.blep.resources.sensitivity_title
import fyi.blep.ui.theme.BlepColors
import org.jetbrains.compose.resources.stringResource

/**
 * Tap-to-expand detection-profile picker: a header row (label + current value +
 * chevron) that animates open to reveal the options in place. The options pane is
 * height-capped and scrolls, so it stays tidy if more presets (or custom profiles)
 * are added later — built off [ScanSensitivity.entries].
 */
@Composable
fun SensitivitySelector(
    current: ScanSensitivity,
    onSelect: (ScanSensitivity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 4.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.sensitivity_title),
                style = MaterialTheme.typography.titleSmall,
                color = BlepColors.Ink,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                sensitivityLabel(current),
                style = MaterialTheme.typography.labelLarge,
                color = BlepColors.Blue,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                " ▾",
                style = MaterialTheme.typography.labelLarge,
                color = BlepColors.Blue,
                modifier = Modifier.rotate(chevron),
            )
        }
        AnimatedVisibility(expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp) // caps height; scrolls if more profiles arrive
                    .verticalScroll(rememberScrollState()),
            ) {
                ScanSensitivity.entries.forEach { s ->
                    val selected = s == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(s); expanded = false }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                sensitivityLabel(s),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (selected) BlepColors.Blue else BlepColors.Ink,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                sensitivityHint(s),
                                style = MaterialTheme.typography.labelMedium,
                                color = BlepColors.Ink.copy(alpha = 0.55f),
                            )
                        }
                        if (selected) Text("✓", style = MaterialTheme.typography.titleSmall, color = BlepColors.Blue)
                    }
                }
            }
        }
    }
}

@Composable
private fun sensitivityLabel(s: ScanSensitivity): String = when (s) {
    ScanSensitivity.RELAXED -> stringResource(Res.string.sensitivity_relaxed)
    ScanSensitivity.BALANCED -> stringResource(Res.string.sensitivity_balanced)
    ScanSensitivity.STRICT -> stringResource(Res.string.sensitivity_strict)
}

@Composable
private fun sensitivityHint(s: ScanSensitivity): String = when (s) {
    ScanSensitivity.RELAXED -> stringResource(Res.string.sensitivity_relaxed_hint)
    ScanSensitivity.BALANCED -> stringResource(Res.string.sensitivity_balanced_hint)
    ScanSensitivity.STRICT -> stringResource(Res.string.sensitivity_strict_hint)
}
