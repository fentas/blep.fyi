package fyi.blep.ui

import androidx.compose.runtime.Composable

/** Lets the "allow Bluetooth" banner try the OS permission prompt first, and only
 *  fall back to app settings once the system won't prompt anymore.
 *
 *  [blocked] is true when the permission is permanently denied (the user dismissed
 *  the prompt past the point the OS will re-ask) — the banner then switches to an
 *  "open settings" message. [request] shows the runtime prompt, or opens this app's
 *  settings page when [blocked]. Inert on iOS (the system prompts on first scan):
 *  blocked = false, request = no-op. */
class BlePermissionRecovery(
    val blocked: Boolean,
    val request: () -> Unit,
)

@Composable
expect fun rememberBlePermissionRecovery(): BlePermissionRecovery
