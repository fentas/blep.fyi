package fyi.blep.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import fyi.blep.core.ble.ScanAvailability

@Composable
actual fun rememberAvailabilityAction(): (ScanAvailability) -> Unit {
    val ctx = LocalContext.current
    fun start(intent: Intent) = runCatching {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    return { state ->
        when (state) {
            // App settings always lets the user grant the permission, in any denial
            // state (a re-request silently no-ops once permanently denied).
            ScanAvailability.PERMISSION_REQUIRED -> start(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)),
            )
            ScanAvailability.BLUETOOTH_OFF -> start(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            ScanAvailability.LOCATION_OFF -> start(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            ScanAvailability.READY, ScanAvailability.UNSUPPORTED -> Unit
        }
    }
}
