package fyi.blep.ui

import androidx.compose.runtime.Composable

@Composable
actual fun rememberNotificationPermissionRequest(): () -> Unit = {}

@Composable
actual fun rememberLocationPermissionRequest(): () -> Unit = {}
