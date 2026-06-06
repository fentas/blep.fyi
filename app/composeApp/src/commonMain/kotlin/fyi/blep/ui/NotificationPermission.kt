package fyi.blep.ui

import androidx.compose.runtime.Composable

/**
 * Returns a function that, when invoked, ensures the OS notification permission so
 * background-scan alerts can show (Android 13+ runtime prompt). No-op on platforms
 * that don't gate notifications this way.
 */
@Composable
expect fun rememberNotificationPermissionRequest(): () -> Unit

/** Returns a function that, when invoked, ensures coarse-location permission so
 *  location-aware detection can sample a place. No-op where not applicable. */
@Composable
expect fun rememberLocationPermissionRequest(): () -> Unit
