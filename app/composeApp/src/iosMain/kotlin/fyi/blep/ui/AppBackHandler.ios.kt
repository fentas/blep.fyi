package fyi.blep.ui

import androidx.compose.runtime.Composable

// iOS has no hardware back button — navigation is by swipe/gesture, so there's
// nothing to intercept here.
@Composable
actual fun AppBackHandler(enabled: Boolean, onBack: () -> Unit) {
}
