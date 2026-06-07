package fyi.blep.ui

import androidx.compose.runtime.Composable
import fyi.blep.core.ble.ScanAvailability
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

@Composable
actual fun rememberAvailabilityAction(): (ScanAvailability) -> Unit = { state ->
    // iOS can't toggle the adapter or re-prompt directly; deep-link to the app's
    // Settings page where Bluetooth/Location permission can be granted.
    if (state != ScanAvailability.READY && state != ScanAvailability.UNSUPPORTED) {
        NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
            UIApplication.sharedApplication.openURL(it)
        }
    }
}
