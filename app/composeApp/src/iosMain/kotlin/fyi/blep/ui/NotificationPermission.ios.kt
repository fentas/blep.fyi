package fyi.blep.ui

import androidx.compose.runtime.Composable

@Composable
actual fun rememberNotificationPermissionRequest(): () -> Unit = {}

@Composable
actual fun rememberLocationPermissionRequest(): () -> Unit = {}

// iOS notification authorisation is async to query; assume enabled (don't show a
// false hint). The system handles its own prompt on first request.
@Composable
actual fun rememberNotificationsEnabled(): Boolean = true

@Composable
actual fun rememberBlePermissionRequest(): () -> Unit = {} // CoreBluetooth prompts on first scan
