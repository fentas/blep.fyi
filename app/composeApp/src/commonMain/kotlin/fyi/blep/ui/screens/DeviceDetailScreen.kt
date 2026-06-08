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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import fyi.blep.core.ble.AddressKind
import fyi.blep.core.ble.RotationStats
import fyi.blep.core.ble.addressKind
import fyi.blep.core.model.BleDevice
import fyi.blep.resources.Res
import fyi.blep.resources.a11y_back
import fyi.blep.resources.action_cancel
import fyi.blep.resources.action_clear
import fyi.blep.resources.action_save
import fyi.blep.resources.dbm
import fyi.blep.resources.detail_addr_opaque
import fyi.blep.resources.detail_addr_rotating
import fyi.blep.resources.detail_addr_stable
import fyi.blep.resources.detail_alt
import fyi.blep.resources.detail_find
import fyi.blep.resources.detail_flag
import fyi.blep.resources.detail_flagged
import fyi.blep.resources.detail_first_seen
import fyi.blep.resources.detail_history
import fyi.blep.resources.detail_identifier
import fyi.blep.resources.detail_identity
import fyi.blep.resources.detail_no_rotation
import fyi.blep.resources.detail_rotated
import fyi.blep.resources.detail_rotated_contested
import fyi.blep.resources.detail_signal
import fyi.blep.resources.rename_title
import fyi.blep.resources.status_connected
import fyi.blep.resources.status_paired
import fyi.blep.ui.theme.BlepColors
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Per-device page: rename (persisted), what its id tells us (stable vs rotating),
 * the live signal, and the rotation history the correlator built — how many times it
 * has changed id, how sure we are, and how long ago we first saw it. The contested
 * fork (an unresolved match with a nearby device) is shown plainly, not hidden.
 */
@Composable
fun DeviceDetailScreen(
    device: BleDevice,
    rotation: RotationStats?,
    firstSeenAgoMs: Long?,
    wornIds: List<String>,
    onRename: (String?) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleFlag: () -> Unit,
    onTrack: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var renaming by remember { mutableStateOf(false) }
    val backLabel = stringResource(Res.string.a11y_back)

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
                modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp).semantics { contentDescription = backLabel },
            )
            Text(
                device.displayName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { renaming = true }) { Text(stringResource(Res.string.rename_title)) }
        }
        Spacer(Modifier.height(14.dp))

        // Scrollable content; the actions stay pinned at the bottom.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Section(stringResource(Res.string.detail_signal)) {
                val signal = when {
                    !device.rssiUnknown -> stringResource(Res.string.dbm, device.rssi)
                    device.isConnected -> stringResource(Res.string.status_connected)
                    else -> stringResource(Res.string.status_paired)
                }
                Text(signal, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.height(12.dp))

            Section(stringResource(Res.string.detail_identity)) {
                Label(stringResource(Res.string.detail_identifier))
                Text(device.id, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                val addressNote = when (addressKind(device.id)) {
                    AddressKind.PUBLIC -> stringResource(Res.string.detail_addr_stable)
                    AddressKind.RANDOM -> stringResource(Res.string.detail_addr_rotating)
                    AddressKind.OPAQUE -> stringResource(Res.string.detail_addr_opaque)
                }
                Text(addressNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                if (firstSeenAgoMs != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(Res.string.detail_first_seen, formatAge(firstSeenAgoMs)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    )
                }
                if (rotation == null || rotation.rotations == 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(Res.string.detail_no_rotation),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    )
                }
            }

            // History — the id changes the correlator stitched together, with the
            // contested fork (if any) shown plainly: the ids this might also be.
            if (rotation != null && rotation.rotations > 0) {
                Spacer(Modifier.height(12.dp))
                Section(stringResource(Res.string.detail_history)) {
                    val summary = if (rotation.contested) {
                        stringResource(Res.string.detail_rotated_contested, rotation.rotations)
                    } else {
                        stringResource(Res.string.detail_rotated, rotation.rotations, (rotation.confidence * 100).roundToInt())
                    }
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = if (rotation.contested) BlepColors.Pink else MaterialTheme.colorScheme.onBackground)
                    if (wornIds.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        wornIds.forEach {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                    }
                    if (rotation.contested && rotation.alternatives.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(Res.string.detail_alt), style = MaterialTheme.typography.labelLarge, color = BlepColors.Pink)
                        rotation.alternatives.forEach {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = BlepColors.Pink.copy(alpha = 0.8f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Pinned actions at the bottom.
        if (device.isFlagged) {
            FilledTonalButton(
                onClick = onToggleFlag,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = BlepColors.Pink.copy(alpha = 0.18f),
                    contentColor = BlepColors.Pink,
                ),
            ) { Text(stringResource(Res.string.detail_flagged)) }
        } else {
            OutlinedButton(onClick = onToggleFlag, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.detail_flag))
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = onTrack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.detail_find))
        }
        Spacer(Modifier.height(16.dp))
    }

    if (renaming) {
        var text by remember { mutableStateOf(TextFieldValue(device.alias ?: device.name ?: "")) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(Res.string.rename_title)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardActions = KeyboardActions(onDone = { onRename(text.text); renaming = false }),
                )
            },
            confirmButton = { TextButton(onClick = { onRename(text.text); renaming = false }) { Text(stringResource(Res.string.action_save)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { onRename(null); renaming = false }) { Text(stringResource(Res.string.action_clear)) }
                    TextButton(onClick = { renaming = false }) { Text(stringResource(Res.string.action_cancel)) }
                }
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(title)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
    )
}

/** Compact relative duration: 5s / 3m / 2h / 1d (units are locale-neutral). */
private fun formatAge(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m"
        s < 86_400 -> "${s / 3600}h"
        else -> "${s / 86_400}d"
    }
}
