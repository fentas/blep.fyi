package fyi.blep.ui

import androidx.compose.runtime.Composable
import fyi.blep.core.ble.ScanAvailability

/**
 * Returns an action that tries to *recover* from a non-ready scan state when the
 * user taps the availability banner: re-grant the permission (via app settings),
 * turn the Bluetooth adapter on, or open location settings. No-op for UNSUPPORTED /
 * READY. Platform-specific because the recovery intents differ.
 */
@Composable
expect fun rememberAvailabilityAction(): (ScanAvailability) -> Unit
