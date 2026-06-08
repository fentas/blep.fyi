package fyi.blep.ui

import androidx.compose.runtime.Composable

/** iOS prompts for Bluetooth on first scan and has no per-permission re-prompt, so
 *  there's nothing to recover here — the availability banner falls back to its
 *  open-Settings action via [rememberAvailabilityAction]. */
@Composable
actual fun rememberBlePermissionRecovery(): BlePermissionRecovery =
    BlePermissionRecovery(blocked = false, request = {})
