package fyi.blep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import fyi.blep.core.ble.AddressKind
import fyi.blep.core.ble.ProbeResult
import fyi.blep.core.ble.RotationStats
import fyi.blep.core.ble.WornId
import fyi.blep.core.ble.addressKind
import fyi.blep.core.model.BleDevice
import fyi.blep.core.platform.epochMillis
import fyi.blep.resources.Res
import fyi.blep.resources.a11y_back
import fyi.blep.resources.action_cancel
import fyi.blep.resources.action_clear
import fyi.blep.resources.action_save
import fyi.blep.resources.dbm
import fyi.blep.resources.detail_addr_opaque
import fyi.blep.resources.detail_addr_rotating
import fyi.blep.resources.detail_addr_stable
import fyi.blep.resources.detail_addr_static
import fyi.blep.resources.detail_alt
import fyi.blep.resources.detail_find
import fyi.blep.resources.detail_flag
import fyi.blep.resources.detail_flagged
import fyi.blep.resources.flag_explainer_title
import fyi.blep.resources.flag_explainer_body
import fyi.blep.resources.detail_tether
import fyi.blep.resources.action_ok
import fyi.blep.resources.watch_explainer_title
import fyi.blep.resources.watch_explainer_body
import fyi.blep.resources.watch_explainer_dont_show
import fyi.blep.resources.detail_tethered
import fyi.blep.resources.detail_first_seen
import fyi.blep.resources.detail_history
import fyi.blep.resources.detail_identified
import fyi.blep.resources.detail_identifier
import fyi.blep.resources.detail_identify
import fyi.blep.resources.detail_identify_failed
import fyi.blep.resources.detail_identifying
import fyi.blep.resources.detail_lost_unlikely
import fyi.blep.resources.detail_no_signal_for
import fyi.blep.resources.dur_seconds
import fyi.blep.resources.dur_minutes
import fyi.blep.resources.dur_hours
import fyi.blep.resources.detail_signal
import fyi.blep.resources.detail_suspect_title
import fyi.blep.resources.detail_suspect_body
import fyi.blep.resources.action_dismiss
import fyi.blep.resources.detail_signal_lost
import fyi.blep.resources.detail_signal_scanning
import fyi.blep.resources.detail_history_rotations
import fyi.blep.resources.detail_confidence
import fyi.blep.resources.detail_correlating
import fyi.blep.resources.detail_contested
import fyi.blep.resources.detail_id_now
import fyi.blep.resources.detail_help_title
import fyi.blep.resources.detail_help_body
import fyi.blep.resources.a11y_help
import fyi.blep.resources.detail_info_title
import fyi.blep.resources.detail_info_maker
import fyi.blep.resources.detail_info_model
import fyi.blep.resources.detail_info_firmware
import fyi.blep.resources.detail_info_hardware
import fyi.blep.resources.detail_info_serial
import fyi.blep.resources.detail_info_battery
import fyi.blep.resources.detail_info_services
import fyi.blep.resources.detail_info_signature
import fyi.blep.resources.detail_info_locked
import fyi.blep.resources.rename_title
import fyi.blep.resources.status_connected
import fyi.blep.resources.status_paired
import fyi.blep.ui.theme.BlepColors
import kotlinx.coroutines.delay
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
    liveRssi: Int?,
    signalPresent: Boolean,
    rotation: RotationStats?,
    firstSeenAgoMs: Long?,
    correlating: Boolean,
    probing: Boolean,
    probed: Boolean,
    probeLabel: String?,
    probeInfo: ProbeResult?,
    onIdentify: () -> Unit,
    onRename: (String?) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleFlag: () -> Unit,
    tethered: Boolean,
    onToggleTether: () -> Unit,
    watchExplained: Boolean,
    onWatchExplainedDismiss: () -> Unit,
    flagExplained: Boolean,
    onFlagExplainedDismiss: () -> Unit,
    onTrack: () -> Unit,
    onBack: () -> Unit,
    suspect: Boolean = false,
    onDismissSuspect: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var renaming by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showWatchModal by remember { mutableStateOf(false) }
    var showFlagModal by remember { mutableStateOf(false) }
    val backLabel = stringResource(Res.string.a11y_back)
    val helpLabel = stringResource(Res.string.a11y_help)
    // Ticks so the "no signal for X" duration counts up while the page is open.
    val nowMs by produceState(epochMillis()) { while (true) { delay(1000); value = epochMillis() } }

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
            // Why this device is marked in the list — and the way to unmark it.
            if (suspect) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = BlepColors.Pink.copy(alpha = 0.16f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(Res.string.detail_suspect_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = BlepColors.Pink,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(Res.string.detail_suspect_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                        TextButton(onClick = onDismissSuspect, modifier = Modifier.align(Alignment.End)) {
                            Text(stringResource(Res.string.action_dismiss), color = BlepColors.Pink)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            // Signal + connection status (or a lost-signal hint while it's out of range).
            val signalLost = !signalPresent && !device.isConnected && !device.isPaired
            Section(
                stringResource(Res.string.detail_signal),
                // "Still scanning" sits top-right of the heading while we're looking for it.
                trailing = if (signalLost) {
                    { Text(stringResource(Res.string.detail_signal_scanning), style = MaterialTheme.typography.labelLarge, color = BlepColors.Blue) }
                } else null,
            ) {
                val rssi = liveRssi ?: device.rssi.takeUnless { device.rssiUnknown }
                if (signalLost) {
                    val gone = device.probablyGone(nowMs) // rotating id, gone >1h → won't be found
                    Text(
                        stringResource(if (gone) Res.string.detail_lost_unlikely else Res.string.detail_signal_lost),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (gone) BlepColors.Pink else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                    device.lostForMs(nowMs)?.let { lost ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(Res.string.detail_no_signal_for, lostDurationLabel(lost)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // The dedicated fast stream wins when present (it also gives bonded
                        // devices a live dBm, which the shared snapshot reports as unknown).
                        val signal = when {
                            rssi != null -> stringResource(Res.string.dbm, rssi)
                            device.isConnected -> stringResource(Res.string.status_connected)
                            else -> stringResource(Res.string.status_paired)
                        }
                        Text(signal, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                        val status = when {
                            device.isConnected -> stringResource(Res.string.status_connected)
                            device.isPaired -> stringResource(Res.string.status_paired)
                            else -> null
                        }
                        if (rssi != null && status != null) {
                            Spacer(Modifier.width(10.dp))
                            Text("· $status", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Identifier — the id itself + what it tells us. (No redundant "Identity" header.)
            Section(stringResource(Res.string.detail_identifier)) {
                Text(device.id, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                val addressNote = when {
                    // A bonded device has a stable identity address — never call it rotating.
                    device.isPaired -> stringResource(Res.string.detail_addr_stable)
                    else -> when (addressKind(device.id)) {
                        AddressKind.PUBLIC -> stringResource(Res.string.detail_addr_stable)
                        AddressKind.STATIC -> stringResource(Res.string.detail_addr_static)
                        AddressKind.RANDOM -> stringResource(Res.string.detail_addr_rotating)
                        AddressKind.OPAQUE -> stringResource(Res.string.detail_addr_opaque)
                    }
                }
                Text(addressNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(12.dp))

            // History — id changes the correlator stitched together (the count is in the
            // header), the cumulative confidence + a help affordance, first-seen, the
            // per-id lifetimes, and (if any) the contested fork: ids it might also be.
            val rotated = rotation != null && rotation.rotations > 0
            val historyTitle =
                if (rotated) stringResource(Res.string.detail_history_rotations, rotation!!.rotations)
                else stringResource(Res.string.detail_history)
            Section(historyTitle) {
                when {
                    rotation?.contested == true ->
                        Text(stringResource(Res.string.detail_contested), style = MaterialTheme.typography.bodyMedium, color = BlepColors.Pink)
                    rotated -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(Res.string.detail_confidence, "${(rotation!!.confidence * 100).roundToInt()}%"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "ⓘ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BlepColors.Blue,
                            modifier = Modifier
                                .clickable { showHelp = true }
                                .semantics { contentDescription = helpLabel },
                        )
                    }
                    // No rotation yet + not correlating → show nothing; "stable so far" is implicit.
                    correlating ->
                        Text(stringResource(Res.string.detail_correlating), style = MaterialTheme.typography.bodyMedium, color = BlepColors.Blue)
                    else -> Unit
                }
                if (firstSeenAgoMs != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(Res.string.detail_first_seen, formatAge(firstSeenAgoMs)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    )
                }
                val history = rotation?.history.orEmpty()
                if (history.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    history.forEach { WornIdRow(it) }
                }
                if (rotation?.contested == true && rotation.alternatives.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(Res.string.detail_alt), style = MaterialTheme.typography.labelLarge, color = BlepColors.Pink)
                    rotation.alternatives.forEach {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = BlepColors.Pink.copy(alpha = 0.8f))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Identify — one short GATT connection to learn the device's name/identity.
            IdentifyRow(probing, probed, probeLabel, onIdentify)

            // Device info — everything the GATT probe pulled (DIS fields, battery, the
            // structural fingerprint, pairing posture), not just a broadcast name.
            if (probeInfo != null && probeInfo.isInformative) {
                Spacer(Modifier.height(12.dp))
                Section(stringResource(Res.string.detail_info_title)) {
                    probeInfo.manufacturer?.let { InfoRow(stringResource(Res.string.detail_info_maker), it) }
                    probeInfo.model?.let { InfoRow(stringResource(Res.string.detail_info_model), it) }
                    probeInfo.firmware?.let { InfoRow(stringResource(Res.string.detail_info_firmware), it) }
                    probeInfo.hardware?.let { InfoRow(stringResource(Res.string.detail_info_hardware), it) }
                    probeInfo.serial?.let { InfoRow(stringResource(Res.string.detail_info_serial), it) }
                    probeInfo.batteryPct?.let { InfoRow(stringResource(Res.string.detail_info_battery), "$it%") }
                    if (probeInfo.serviceCount > 0) {
                        InfoRow(stringResource(Res.string.detail_info_services, probeInfo.serviceCount), "")
                    }
                    probeInfo.structure?.let { InfoRow(stringResource(Res.string.detail_info_signature), it) }
                    if (probeInfo.needsPairing) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(Res.string.detail_info_locked),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Pinned actions at the bottom. "Watch this device" (flag) is an anti-stalking tool —
        // hidden for paired/bonded devices (your own gear), where it makes no sense.
        if (!device.isPaired) {
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
                // First activation explains what it does (priority bump + foreground service).
                OutlinedButton(
                    onClick = { if (flagExplained) onToggleFlag() else showFlagModal = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(Res.string.detail_flag)) }
            }
            Spacer(Modifier.height(10.dp))
        }
        // Tether: alert me when this device leaves (and comes back into) Bluetooth range.
        if (tethered) {
            FilledTonalButton(
                onClick = onToggleTether,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = BlepColors.Blue.copy(alpha = 0.18f),
                    contentColor = BlepColors.Blue,
                ),
            ) { Text(stringResource(Res.string.detail_tethered)) }
        } else {
            // First time activating, explain what it does (incl. the foreground service)
            // unless the user has dismissed the explainer.
            OutlinedButton(
                onClick = { if (watchExplained) onToggleTether() else showWatchModal = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.detail_tether))
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = onTrack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.detail_find))
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(stringResource(Res.string.detail_help_title)) },
            text = { Text(stringResource(Res.string.detail_help_body)) },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }

    if (showWatchModal) {
        var dontShowAgain by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showWatchModal = false },
            title = { Text(stringResource(Res.string.watch_explainer_title)) },
            text = {
                Column {
                    Text(stringResource(Res.string.watch_explainer_body))
                    Spacer(Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { dontShowAgain = !dontShowAgain },
                    ) {
                        Checkbox(checked = dontShowAgain, onCheckedChange = { dontShowAgain = it })
                        Text(stringResource(Res.string.watch_explainer_dont_show))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dontShowAgain) onWatchExplainedDismiss()
                    showWatchModal = false
                    onToggleTether()
                }) { Text(stringResource(Res.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showWatchModal = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }

    if (showFlagModal) {
        var dontShowAgain by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showFlagModal = false },
            title = { Text(stringResource(Res.string.flag_explainer_title)) },
            text = {
                Column {
                    Text(stringResource(Res.string.flag_explainer_body))
                    Spacer(Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { dontShowAgain = !dontShowAgain },
                    ) {
                        Checkbox(checked = dontShowAgain, onCheckedChange = { dontShowAgain = it })
                        Text(stringResource(Res.string.watch_explainer_dont_show))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dontShowAgain) onFlagExplainedDismiss()
                    showFlagModal = false
                    onToggleFlag()
                }) { Text(stringResource(Res.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showFlagModal = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
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

/** The active-probe affordance: a button when untried, a spinner while connecting, and
 *  the learned label (or a graceful "couldn't connect") once done. */
@Composable
private fun IdentifyRow(probing: Boolean, probed: Boolean, probeLabel: String?, onIdentify: () -> Unit) {
    when {
        probing -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = BlepColors.Blue)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(Res.string.detail_identifying),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
        probed && !probeLabel.isNullOrBlank() -> Text(
            stringResource(Res.string.detail_identified, probeLabel),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        probed -> Text(
            stringResource(Res.string.detail_identify_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        else -> OutlinedButton(onClick = onIdentify, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.detail_identify))
        }
    }
}

/** A label → value row for the device-info card (value right-aligned, may be blank). */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
        if (value.isNotEmpty()) {
            Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
        }
    }
}

/** One row in the worn-id history: the id, how long it was seen, and the hop quality
 *  (or "now" for the live id). */
@Composable
private fun WornIdRow(w: WornId) {
    val meta = if (w.current) stringResource(Res.string.detail_id_now)
    else "${(w.quality * 100).roundToInt()}%"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(w.address, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f))
        Text(
            "${formatAge(w.durationMs)} · $meta",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (w.current) 0.55f else 0.4f),
        )
    }
}

@Composable
private fun Section(title: String, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Label(title) }
                trailing?.invoke()
            }
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
@Composable
private fun lostDurationLabel(ms: Long): String {
    val s = (ms / 1000).toInt()
    return when {
        s < 60 -> stringResource(Res.string.dur_seconds, s)
        s < 3600 -> stringResource(Res.string.dur_minutes, s / 60)
        else -> stringResource(Res.string.dur_hours, s / 3600)
    }
}

private fun formatAge(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m"
        s < 86_400 -> "${s / 3600}h"
        else -> "${s / 86_400}d"
    }
}
