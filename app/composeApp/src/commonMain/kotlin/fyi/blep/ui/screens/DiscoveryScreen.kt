package fyi.blep.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import fyi.blep.core.ble.ScanAvailability
import fyi.blep.core.model.BleDevice
import fyi.blep.resources.Res
import fyi.blep.resources.app_tagline
import fyi.blep.resources.action_cancel
import fyi.blep.resources.action_clear
import fyi.blep.resources.action_save
import fyi.blep.resources.done_donate_blurb
import fyi.blep.resources.done_donate_button
import fyi.blep.resources.avail_bluetooth_off
import fyi.blep.resources.avail_location_off
import fyi.blep.resources.avail_permission
import fyi.blep.resources.avail_unsupported
import fyi.blep.resources.dbm
import fyi.blep.resources.discovery_empty_title
import fyi.blep.resources.discovery_hint
import fyi.blep.resources.hide_unnamed
import fyi.blep.resources.nearby_count
import fyi.blep.resources.paired_button
import fyi.blep.resources.paired_sheet_empty
import fyi.blep.resources.paired_sheet_hint
import fyi.blep.resources.paired_sheet_title
import fyi.blep.resources.rename_label
import fyi.blep.resources.rename_title
import fyi.blep.resources.a11y_donate
import fyi.blep.resources.a11y_favorite
import fyi.blep.resources.safety_entry_subtitle
import fyi.blep.resources.safety_entry_title
import fyi.blep.resources.settings_title
import fyi.blep.resources.section_nearby
import fyi.blep.resources.show_unnamed_many
import fyi.blep.resources.show_unnamed_one
import fyi.blep.resources.status_connected
import fyi.blep.resources.status_paired
import fyi.blep.ui.theme.BlepColors
import fyi.blep.ui.theme.BlepLogo
import fyi.blep.ui.theme.HeartIcon
import org.jetbrains.compose.resources.stringResource

@Composable
fun DiscoveryScreen(
    devices: List<BleDevice>,
    pairedDevices: List<BleDevice>,
    nearbyCount: Int,
    unnamedCount: Int,
    availability: ScanAvailability,
    includeUnnamed: Boolean,
    onToggleUnnamed: () -> Unit,
    onSelect: (BleDevice) -> Unit,
    onRename: (BleDevice, String?) -> Unit,
    onToggleFavorite: (BleDevice) -> Unit,
    onSafetyScan: () -> Unit,
    onSettings: () -> Unit,
    onDonate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var renaming by remember { mutableStateOf<BleDevice?>(null) }
    var showPaired by remember { mutableStateOf(false) }
    var showDonate by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize().background(BlepColors.Mist)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Header(deviceCount = nearbyCount)
        Spacer(Modifier.height(14.dp))
        SafetyEntry(onSafetyScan)
        Spacer(Modifier.height(16.dp))

        AnimatedVisibility(availability != ScanAvailability.READY) {
            AvailabilityBanner(availability)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(Res.string.section_nearby), Modifier.weight(1f))
            if (pairedDevices.isNotEmpty()) {
                PairedPill(count = pairedDevices.size, onClick = { showPaired = true })
                Spacer(Modifier.width(8.dp))
            }
            SettingsButton(onClick = onSettings)
        }
        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (devices.isEmpty() && availability == ScanAvailability.READY) {
                item { EmptyState() }
            }
            items(devices, key = { it.id }) { device ->
                DeviceCard(
                    device = device,
                    onClick = { onSelect(device) },
                    onRename = { renaming = device },
                    onToggleFavorite = { onToggleFavorite(device) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (unnamedCount > 0 || includeUnnamed) {
                item(key = "show-more") {
                    ShowMoreRow(
                        unnamedCount = unnamedCount,
                        expanded = includeUnnamed,
                        onClick = onToggleUnnamed,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

        DonateHeart(
            onClick = { showDonate = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
        )
    }

    renaming?.let { device ->
        RenameDialog(
            device = device,
            onDismiss = { renaming = null },
            onConfirm = { alias -> onRename(device, alias); renaming = null },
        )
    }

    if (showPaired) {
        PairedSheet(
            devices = pairedDevices,
            onDismiss = { showPaired = false },
            onSelect = { showPaired = false; onSelect(it) },
            onToggleFavorite = onToggleFavorite,
        )
    }

    if (showDonate) {
        AlertDialog(
            onDismissRequest = { showDonate = false },
            icon = { Text("♥", style = MaterialTheme.typography.headlineMedium, color = BlepColors.Pink) },
            text = {
                Text(
                    stringResource(Res.string.done_donate_blurb),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BlepColors.Ink,
                )
            },
            confirmButton = {
                TextButton(onClick = { showDonate = false; onDonate() }) {
                    Text(stringResource(Res.string.done_donate_button), color = BlepColors.Blue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDonate = false }) {
                    Text(stringResource(Res.string.action_cancel), color = BlepColors.Ink.copy(alpha = 0.6f))
                }
            },
        )
    }
}

/** Flat 2-D floating heart (no shadow/elevation) that invites a donation. */
@Composable
private fun DonateHeart(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color(0xFFE6E7EA)) // light gray
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = rememberVectorPainter(HeartIcon),
            contentDescription = stringResource(Res.string.a11y_donate),
            tint = BlepColors.Pink, // mild pastel
            modifier = Modifier.size(26.dp),
        )
    }
}

/** The "all paired" manager: every bonded device, where favourites are curated.
 *  This is the only place silent (non-advertising) paired devices appear, so the
 *  main list can stay genuinely nearby. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairedSheet(
    devices: List<BleDevice>,
    onDismiss: () -> Unit,
    onSelect: (BleDevice) -> Unit,
    onToggleFavorite: (BleDevice) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BlepColors.Mist,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                stringResource(Res.string.paired_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                color = BlepColors.Ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(Res.string.paired_sheet_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = BlepColors.Ink.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(14.dp))
            if (devices.isEmpty()) {
                Text(
                    stringResource(Res.string.paired_sheet_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = BlepColors.Ink.copy(alpha = 0.45f),
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(devices, key = { it.id }) { device ->
                        DeviceCard(
                            device = device,
                            onClick = { onSelect(device) },
                            onToggleFavorite = { onToggleFavorite(device) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(deviceCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = rememberVectorPainter(BlepLogo),
            contentDescription = "blep",
            tint = Color.Unspecified,
            modifier = Modifier.size(52.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("blep", style = MaterialTheme.typography.displayLarge)
            Text(
                stringResource(Res.string.app_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = BlepColors.Ink.copy(alpha = 0.55f),
            )
        }
        if (deviceCount > 0) {
            Surface(color = BlepColors.Ink.copy(alpha = 0.06f), shape = RoundedCornerShape(999.dp)) {
                Text(
                    stringResource(Res.string.nearby_count, deviceCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = BlepColors.Ink.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Gear that opens Settings — sits in the section row, right of the Paired pill. */
@Composable
private fun SettingsButton(onClick: () -> Unit) {
    val label = stringResource(Res.string.settings_title)
    Text(
        "⚙",
        style = MaterialTheme.typography.titleLarge,
        color = BlepColors.Ink.copy(alpha = 0.5f),
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(6.dp)
            .semantics { contentDescription = label },
    )
}

@Composable
private fun SafetyEntry(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = BlepColors.Blue.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🛡️", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.safety_entry_title), style = MaterialTheme.typography.titleSmall, color = BlepColors.Ink, fontWeight = FontWeight.SemiBold)
                Text(stringResource(Res.string.safety_entry_subtitle), style = MaterialTheme.typography.bodySmall, color = BlepColors.Ink.copy(alpha = 0.55f))
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = BlepColors.Blue)
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = BlepColors.Ink.copy(alpha = 0.4f),
        modifier = modifier.padding(start = 4.dp),
    )
}

/** Touchable pill beside the NEARBY label that opens the all-paired manager. */
@Composable
private fun PairedPill(count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = BlepColors.Blue.copy(alpha = 0.10f),
    ) {
        Text(
            stringResource(Res.string.paired_button, count),
            style = MaterialTheme.typography.labelLarge,
            color = BlepColors.Blue,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun DeviceCard(
    device: BleDevice,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    onRename: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(device)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (device.isNamed) BlepColors.Ink else BlepColors.Ink.copy(alpha = 0.55f),
                )
                Spacer(Modifier.height(2.dp))
                // Show the live signal whenever we have it (even when connected or
                // paired — the avatar already marks connection). The chip only
                // stands in when a bonded device isn't advertising any signal.
                when {
                    !device.rssiUnknown -> Row(verticalAlignment = Alignment.CenterVertically) {
                        if (device.isConnected) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(BlepColors.Blue))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            stringResource(Res.string.dbm, device.rssi),
                            style = MaterialTheme.typography.labelLarge,
                            color = BlepColors.Ink.copy(alpha = 0.45f),
                        )
                    }
                    device.isConnected -> StatusChip(stringResource(Res.string.status_connected), showDot = true)
                    else -> StatusChip(stringResource(Res.string.status_paired), showDot = false)
                }
            }
            if (!device.rssiUnknown) {
                SignalDots(rssi = device.rssi)
                Spacer(Modifier.width(10.dp))
            }
            FavoriteButton(isFavorite = device.isFavorite, onClick = onToggleFavorite)
            if (onRename != null) RenameButton(onRename)
        }
    }
}

/** Star toggle: filled gold when starred, hollow otherwise. Starred devices pin to
 *  the top of the main list even when not advertising. */
// Centre a single glyph within its line box so ★/☆/✎ sit at the same height
// (their default font ascent/descent differ).
private val CenteredGlyph = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

@Composable
private fun FavoriteButton(isFavorite: Boolean, onClick: () -> Unit) {
    val label = stringResource(Res.string.a11y_favorite)
    Box(
        Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (isFavorite) "★" else "☆",
            style = MaterialTheme.typography.titleMedium.copy(lineHeightStyle = CenteredGlyph),
            color = if (isFavorite) BlepColors.Gold else BlepColors.Ink.copy(alpha = 0.35f),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun Avatar(device: BleDevice) {
    val bg = when {
        device.isConnected -> BlepColors.Blue
        device.isNamed -> BlepColors.Blue.copy(alpha = 0.12f)
        else -> BlepColors.Ink.copy(alpha = 0.06f)
    }
    val fg = if (device.isConnected) BlepColors.Cream else BlepColors.Blue
    val initial = if (device.isNamed) device.displayName.first().uppercaseChar().toString() else "?"
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial,
            style = MaterialTheme.typography.titleMedium,
            color = if (device.isNamed || device.isConnected) fg else BlepColors.Ink.copy(alpha = 0.4f),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StatusChip(label: String, showDot: Boolean) {
    Surface(color = BlepColors.Blue.copy(alpha = 0.14f), shape = RoundedCornerShape(999.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            if (showDot) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(BlepColors.Blue))
                Spacer(Modifier.width(5.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = BlepColors.Blue)
        }
    }
}

@Composable
private fun RenameButton(onRename: () -> Unit) {
    val label = stringResource(Res.string.rename_title)
    Box(
        Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onRename),
        contentAlignment = Alignment.Center,
    ) {
        Text("✎", style = MaterialTheme.typography.titleMedium.copy(lineHeightStyle = CenteredGlyph), color = BlepColors.Ink.copy(alpha = 0.35f), modifier = Modifier.semantics { contentDescription = label })
    }
}

/** Four bars that fill based on RSSI strength, coloured by proximity. */
@Composable
private fun SignalDots(rssi: Int) {
    val strength = when {
        rssi >= -55 -> 4
        rssi >= -67 -> 3
        rssi >= -80 -> 2
        rssi >= -92 -> 1
        else -> 0
    }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 1..4) {
            Box(
                Modifier
                    .width(5.dp)
                    .height((6 + i * 3).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (i <= strength) BlepColors.proximity(strength / 4f)
                        else BlepColors.Ink.copy(alpha = 0.10f),
                    ),
            )
        }
    }
}

@Composable
private fun ShowMoreRow(
    unnamedCount: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (expanded) {
        stringResource(Res.string.hide_unnamed)
    } else if (unnamedCount == 1) {
        stringResource(Res.string.show_unnamed_one, unnamedCount)
    } else {
        stringResource(Res.string.show_unnamed_many, unnamedCount)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = BlepColors.Blue.copy(alpha = 0.10f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (expanded) "–" else "+", style = MaterialTheme.typography.titleMedium, color = BlepColors.Blue)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = BlepColors.Blue)
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.discovery_empty_title), style = MaterialTheme.typography.titleMedium, color = BlepColors.Ink.copy(alpha = 0.6f))
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(Res.string.discovery_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = BlepColors.Ink.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun AvailabilityBanner(availability: ScanAvailability) {
    val message = when (availability) {
        ScanAvailability.BLUETOOTH_OFF -> stringResource(Res.string.avail_bluetooth_off)
        ScanAvailability.PERMISSION_REQUIRED -> stringResource(Res.string.avail_permission)
        ScanAvailability.LOCATION_OFF -> stringResource(Res.string.avail_location_off)
        ScanAvailability.UNSUPPORTED -> stringResource(Res.string.avail_unsupported)
        ScanAvailability.READY -> return
    }
    Surface(
        color = BlepColors.Pink.copy(alpha = 0.35f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = BlepColors.Ink,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun RenameDialog(
    device: BleDevice,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var text by remember {
        mutableStateOf(TextFieldValue(device.alias ?: device.name ?: ""))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(Res.string.rename_label)) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.text) }) { Text(stringResource(Res.string.action_save)) } },
        dismissButton = { TextButton(onClick = { onConfirm(null) }) { Text(stringResource(Res.string.action_clear)) } },
    )
}
