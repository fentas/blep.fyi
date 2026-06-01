package fyi.blep.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import fyi.blep.core.ble.ScanAvailability
import fyi.blep.core.model.BleDevice
import fyi.blep.ui.theme.BlepColors
import fyi.blep.ui.theme.BlepLogo

@Composable
fun DiscoveryScreen(
    devices: List<BleDevice>,
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
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = rememberVectorPainter(BlepLogo),
                contentDescription = "blep",
                tint = androidx.compose.ui.graphics.Color.Unspecified,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("blep", style = MaterialTheme.typography.displayLarge)
                Text(
                    "Tap a device to track it",
                    style = MaterialTheme.typography.bodyLarge,
                    color = BlepColors.Ink.copy(alpha = 0.6f),
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        AnimatedVisibility(availability != ScanAvailability.READY) {
            AvailabilityBanner(availability)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(devices, key = { it.id }) { device ->
                DeviceRow(
                    device = device,
                    onClick = { onSelect(device) },
                    onRename = { renaming = device },
                )
            }
        }

        RevealUnnamedToggle(includeUnnamed, onToggleUnnamed)
        Spacer(Modifier.height(20.dp))
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
private fun DeviceRow(
    device: BleDevice,
    onClick: () -> Unit,
    onRename: () -> Unit,
) {
    val border = if (device.isConnected) BlepColors.Blue else BlepColors.Ink.copy(alpha = 0.06f)
    Surface(
        color = BlepColors.Cream,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, border, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.displayName, style = MaterialTheme.typography.titleMedium)
                if (device.isConnected) {
                    Text(
                        "Connected",
                        style = MaterialTheme.typography.labelLarge,
                        color = BlepColors.Blue,
                    )
                }
            }
            SignalDots(rssi = device.rssi)
            Spacer(Modifier.width(12.dp))
            // Tap to rename.
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRename),
                contentAlignment = Alignment.Center,
            ) {
                Text("✎", style = MaterialTheme.typography.titleMedium, color = BlepColors.Ink.copy(alpha = 0.4f))
            }
        }
    }
}

/** Four dots that fill based on RSSI strength. */
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
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (i <= strength) BlepColors.proximity(strength / 4f)
                        else BlepColors.Ink.copy(alpha = 0.12f),
                    ),
            )
        }
    }
}

@Composable
private fun RevealUnnamedToggle(includeUnnamed: Boolean, onToggle: () -> Unit) {
    Text(
        text = if (includeUnnamed) "Hide unnamed devices" else "Show unnamed devices",
        style = MaterialTheme.typography.labelLarge,
        color = BlepColors.Blue,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 12.dp),
    )
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
        confirmButton = {
            TextButton(onClick = { onConfirm(text.text) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(null) }) { Text("Clear") }
        },
    )
}
