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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import fyi.blep.core.ble.ScanAvailability
import fyi.blep.core.model.BleDevice
import fyi.blep.ui.theme.BlepColors
import fyi.blep.ui.theme.BlepLogo

@Composable
fun DiscoveryScreen(
    devices: List<BleDevice>,
    unnamedCount: Int,
    availability: ScanAvailability,
    includeUnnamed: Boolean,
    onToggleUnnamed: () -> Unit,
    onSelect: (BleDevice) -> Unit,
    onRename: (BleDevice, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renaming by remember { mutableStateOf<BleDevice?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BlepColors.Mist)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Header(deviceCount = devices.size)
        Spacer(Modifier.height(16.dp))

        AnimatedVisibility(availability != ScanAvailability.READY) {
            AvailabilityBanner(availability)
        }

        if (devices.isNotEmpty()) {
            SectionLabel("Nearby")
            Spacer(Modifier.height(10.dp))
        }

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

    renaming?.let { device ->
        RenameDialog(
            device = device,
            onDismiss = { renaming = null },
            onConfirm = { alias -> onRename(device, alias); renaming = null },
        )
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
                "Tap a device to track it",
                style = MaterialTheme.typography.bodyLarge,
                color = BlepColors.Ink.copy(alpha = 0.55f),
            )
        }
        if (deviceCount > 0) {
            Surface(color = BlepColors.Ink.copy(alpha = 0.06f), shape = RoundedCornerShape(999.dp)) {
                Text(
                    "$deviceCount nearby",
                    style = MaterialTheme.typography.labelLarge,
                    color = BlepColors.Ink.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = BlepColors.Ink.copy(alpha = 0.4f),
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun DeviceCard(
    device: BleDevice,
    onClick: () -> Unit,
    onRename: () -> Unit,
    modifier: Modifier = Modifier,
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
                if (device.isConnected) {
                    ConnectedChip()
                } else {
                    Text(
                        "${device.rssi} dBm",
                        style = MaterialTheme.typography.labelLarge,
                        color = BlepColors.Ink.copy(alpha = 0.45f),
                    )
                }
            }
            SignalDots(rssi = device.rssi)
            Spacer(Modifier.width(10.dp))
            RenameButton(onRename)
        }
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
private fun ConnectedChip() {
    Surface(color = BlepColors.Blue.copy(alpha = 0.14f), shape = RoundedCornerShape(999.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(BlepColors.Blue))
            Spacer(Modifier.width(5.dp))
            Text("Connected", style = MaterialTheme.typography.labelLarge, color = BlepColors.Blue)
        }
    }
}

@Composable
private fun RenameButton(onRename: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onRename),
        contentAlignment = Alignment.Center,
    ) {
        Text("✎", style = MaterialTheme.typography.titleMedium, color = BlepColors.Ink.copy(alpha = 0.35f))
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
    val label = if (expanded) "Hide unnamed devices"
    else "Show $unnamedCount unnamed device" + if (unnamedCount == 1) "" else "s"
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
        Text("Looking around…", style = MaterialTheme.typography.titleMedium, color = BlepColors.Ink.copy(alpha = 0.6f))
        Spacer(Modifier.height(6.dp))
        Text(
            "Make sure the device is powered on and nearby.",
            style = MaterialTheme.typography.bodyLarge,
            color = BlepColors.Ink.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun AvailabilityBanner(availability: ScanAvailability) {
    val message = when (availability) {
        ScanAvailability.BLUETOOTH_OFF -> "Turn on Bluetooth to scan"
        ScanAvailability.PERMISSION_REQUIRED -> "Allow Bluetooth access to scan"
        ScanAvailability.LOCATION_OFF -> "Turn on Location to scan (Android)"
        ScanAvailability.UNSUPPORTED -> "Bluetooth LE isn't supported on this device"
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
        title = { Text("Rename device") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { onConfirm(null) }) { Text("Clear") } },
    )
}
