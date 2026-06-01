package fyi.blep

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Entry point consumed by the SwiftUI app shell (see `iosApp/`). */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
