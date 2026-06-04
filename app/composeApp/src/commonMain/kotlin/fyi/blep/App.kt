package fyi.blep

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import fyi.blep.core.ble.createBleScanner
import fyi.blep.core.safety.TrackerTuning
import fyi.blep.demo.DemoBleScanner
import fyi.blep.demo.DemoMotionProvider
import fyi.blep.ui.screens.CompletionScreen
import fyi.blep.ui.screens.DiscoveryScreen
import fyi.blep.ui.screens.SafetyScreen
import fyi.blep.ui.screens.TrackingScreen
import fyi.blep.ui.theme.BlepTheme

/** Where the in-app "Help & donate" button sends people (hosted by the website). */
const val DONATE_URL: String = "https://blep.fyi/donate.html"

/** Root composable shared by the Android and iOS phone apps. [demo] swaps in
 *  scripted data sources (no Bluetooth/sensors needed) for screenshots/previews. */
@Composable
fun App(demo: Boolean = false) {
    BlepTheme {
        val scope = rememberCoroutineScope()
        val controller = remember(scope) {
            if (demo) BlepController(
                DemoBleScanner(), scope, DemoMotionProvider(),
                // fast thresholds so the demo safety scan escalates within seconds
                safetyTuning = TrackerTuning(nearbyMs = 1_000, followingMs = 6_000, rotationMinDistinct = 3, bucketMs = 4_000),
            )
            else BlepController(createBleScanner(), scope)
        }
        val uriHandler = LocalUriHandler.current

        AnimatedContent(
            targetState = controller.screen,
            transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(250)) },
            label = "screen",
        ) { screen ->
            when (screen) {
                is Screen.Discovery -> DiscoveryScreen(
                    devices = controller.visibleDevices,
                    unnamedCount = controller.unnamedCount,
                    availability = controller.availability,
                    includeUnnamed = controller.includeUnnamed,
                    onToggleUnnamed = controller::toggleUnnamed,
                    onSelect = controller::track,
                    onRename = controller::rename,
                    onSafetyScan = controller::openSafetyScan,
                )

                is Screen.Safety -> SafetyScreen(
                    alerts = controller.safetyAlerts,
                    onFind = controller::findTracker,
                    onBack = controller::startDiscovery,
                )

                is Screen.Tracking -> controller.status?.let { status ->
                    TrackingScreen(
                        deviceName = screen.device.displayName,
                        status = status,
                        rssi = controller.lastRssi,
                        spatial = controller.spatial,
                        guidanceLine = controller.guidance,
                        signalLost = controller.signalLost,
                        signalAgeSec = controller.signalAgeSec,
                        soundOn = controller.soundOn,
                        onToggleSound = controller::toggleSound,
                        onCancel = controller::startDiscovery,
                    )
                }

                is Screen.Done -> CompletionScreen(
                    deviceName = screen.device.displayName,
                    onDone = controller::startDiscovery,
                    onDonate = { uriHandler.openUri(DONATE_URL) },
                )
            }
        }
    }
}
