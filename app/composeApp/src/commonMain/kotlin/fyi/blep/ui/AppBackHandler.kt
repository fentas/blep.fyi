package fyi.blep.ui

import androidx.compose.runtime.Composable

/**
 * Handle the system back gesture/button while [enabled]. Compose Multiplatform 1.7
 * has no common BackHandler, so this is expect/actual: Android wires the real
 * dispatcher; iOS is a no-op (it navigates by swipe, not a hardware back).
 */
@Composable
expect fun AppBackHandler(enabled: Boolean, onBack: () -> Unit)
