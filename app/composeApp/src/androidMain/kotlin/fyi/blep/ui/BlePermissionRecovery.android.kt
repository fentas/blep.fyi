package fyi.blep.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private const val PREFS = "blep_perms"
private const val KEY_BLOCKED = "ble_blocked"

/** The runtime permissions a scan needs: BLUETOOTH_SCAN/CONNECT on Android 12+, the
 *  legacy fine-location grant below that. */
private fun blePerms(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
actual fun rememberBlePermissionRecovery(): BlePermissionRecovery {
    val ctx = LocalContext.current
    val activity = ctx.findActivity()
    val perms = blePerms()
    val primary = perms[0]
    val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // "The OS won't prompt again" — persisted so it survives a restart, and set ONLY
    // from a real denial result below. Crucially NOT inferred from shouldShowRationale
    // at composition: that's also false in the window after asking but before a
    // denial, which would flash the banner to "blocked" while the prompt is still up.
    var permanentlyDenied by remember { mutableStateOf(prefs.getBoolean(KEY_BLOCKED, false)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.isNotEmpty() && result.values.all { it }
        // After a denial, rationale==true means we may ask again; false means the user
        // chose "don't allow" past the re-prompt point → permanently denied.
        val canAskAgain = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, primary)
        val blocked = !granted && !canAskAgain
        permanentlyDenied = blocked
        prefs.edit().putBoolean(KEY_BLOCKED, blocked).apply()
    }

    val granted = ContextCompat.checkSelfPermission(ctx, primary) == PackageManager.PERMISSION_GRANTED
    val blocked = !granted && permanentlyDenied

    return BlePermissionRecovery(blocked = blocked) {
        if (blocked) {
            // No prompt left to show — send them to the app's settings page to flip it.
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        } else {
            // Try first: show the OS prompt (this is the skipped-onboarding path).
            launcher.launch(perms)
        }
    }
}
