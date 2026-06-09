package fyi.blep

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import fyi.blep.core.ble.createBleScanner
import fyi.blep.core.safety.TrackerTuning
import fyi.blep.demo.DemoBleScanner
import fyi.blep.demo.DemoMotionProvider
import fyi.blep.ui.screens.CompletionScreen
import androidx.compose.foundation.isSystemInDarkTheme
import fyi.blep.ui.BuildInfo
import fyi.blep.ui.rememberBlePermissionRequest
import fyi.blep.ui.screens.DeviceDetailScreen
import fyi.blep.ui.screens.DiscoveryScreen
import fyi.blep.ui.screens.OnboardingScreen
import fyi.blep.ui.screens.SafetyScreen
import fyi.blep.ui.screens.SettingsScreen
import fyi.blep.ui.AppBackHandler
import fyi.blep.ui.screens.TrackingScreen
import fyi.blep.ui.theme.BlepTheme

/** Where the in-app "Help & donate" button sends people (hosted by the website). */
const val DONATE_URL: String = "https://blep.fyi/donate.html"

/** Root composable shared by the Android and iOS phone apps. [demo] swaps in
 *  scripted data sources (no Bluetooth/sensors needed) for screenshots/previews.
 *  [buildInfo] is the running build's identity for the on-screen version stamp;
 *  null (iOS/Wear, or demo) hides it. */
@Composable
fun App(demo: Boolean = false, buildInfo: BuildInfo? = null) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) {
        if (demo) BlepController(
            DemoBleScanner(), scope, DemoMotionProvider(),
            // fast thresholds so the demo safety scan escalates within seconds
            safetyTuning = TrackerTuning(nearbyMs = 1_000, followingMs = 6_000, rotationMinDistinct = 3, bucketMs = 4_000),
            skipOnboarding = true, // screenshots/demo jump straight to discovery
        ).also { it.seedDemoIdentity() }
        else BlepController(createBleScanner(), scope)
    }
    val dark = when (controller.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    BlepTheme(darkTheme = dark) {
        val uriHandler = LocalUriHandler.current
        val requestBlePermission = rememberBlePermissionRequest()

        // System back on any sub-screen returns to discovery; on discovery (and the
        // first-run onboarding) it's disabled so the OS handles it (exit). The paired
        // sheet handles its own.
        AppBackHandler(enabled = controller.screen !is Screen.Discovery && controller.screen !is Screen.Onboarding) {
            controller.startDiscovery()
        }

        AnimatedContent(
            targetState = controller.screen,
            transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(250)) },
            label = "screen",
        ) { screen ->
            when (screen) {
                is Screen.Onboarding -> OnboardingScreen(
                    onDone = { requestBlePermission(); controller.finishOnboarding() },
                    onSkip = controller::finishOnboarding,
                )

                is Screen.Discovery -> DiscoveryScreen(
                    devices = controller.visibleDevices,
                    pairedDevices = controller.pairedDevices,
                    nearbyCount = controller.nearbyCount,
                    unnamedCount = controller.unnamedCount,
                    availability = controller.availability,
                    includeUnnamed = controller.includeUnnamed,
                    onToggleUnnamed = controller::toggleUnnamed,
                    onSelect = controller::track,
                    onDetails = controller::openDeviceDetail,
                    onToggleFavorite = controller::toggleFavorite,
                    onSafetyScan = controller::openSafetyScan,
                    onSettings = controller::openSettings,
                    onDonate = { uriHandler.openUri(DONATE_URL) },
                    rotationOf = controller::rotationStats,
                )

                is Screen.Settings -> SettingsScreen(
                    measureConnectedSignal = controller.measureConnectedSignal,
                    onToggleConnectedSignal = controller::toggleConnectedSignal,
                    soundOn = controller.soundOn,
                    onToggleSound = { controller.toggleSound() },
                    hapticsOn = controller.hapticsOn,
                    onToggleHaptics = { controller.toggleHaptics() },
                    showUnnamed = controller.includeUnnamed,
                    onToggleUnnamed = { controller.toggleUnnamed() },
                    rememberTrackers = controller.rememberEncounters,
                    onToggleRemember = { controller.toggleRememberEncounters() },
                    scanSensitivity = controller.scanSensitivity,
                    onSelectSensitivity = controller::selectScanSensitivity,
                    locationAware = controller.locationAware,
                    onToggleLocation = controller::toggleLocationAware,
                    rememberDeviceDays = controller.identityTtlDays,
                    onRememberDeviceDaysChange = controller::setIdentityTtl,
                    foregroundScan = controller.foregroundScanEnabled,
                    onToggleForeground = controller::setForegroundScanning,
                    backgroundScan = controller.backgroundScanEnabled,
                    onToggleBackground = controller::setBackgroundScanning,
                    intervalMinutes = controller.scanIntervalMinutes,
                    onIntervalChange = controller::setScanInterval,
                    themeMode = controller.themeMode,
                    onSelectTheme = controller::selectTheme,
                    onBack = controller::startDiscovery,
                    buildInfo = buildInfo,
                )

                is Screen.Safety -> SafetyScreen(
                    alerts = controller.safetyAlerts,
                    scanSensitivity = controller.scanSensitivity,
                    onSelectSensitivity = controller::selectScanSensitivity,
                    backgroundOn = controller.backgroundScanEnabled,
                    onToggleBackground = controller::setBackgroundScanning,
                    onFind = controller::findTracker,
                    onMine = controller::muteTracker,
                    lastMuted = controller.lastMuted,
                    onUndoMute = controller::undoMute,
                    onMuteUndoShown = controller::clearMuteUndo,
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
                    rssi = if (controller.signalLost) null else controller.lastRssi,
                )

                is Screen.DeviceDetail -> {
                    // Follow the device across any id rotation since the page opened, so a
                    // watched device tracks its live lineage instead of dying on a stale id.
                    val liveId = controller.currentAddressFor(screen.device.id) ?: screen.device.id
                    val live = controller.devices.firstOrNull { it.id == liveId } ?: screen.device
                    // Drive the dedicated fast signal for whatever id the device wears now;
                    // re-keyed on liveId so it re-subscribes when the device rotates.
                    DisposableEffect(liveId) {
                        controller.startDetailSignal(liveId)
                        onDispose { controller.stopDetailSignal() }
                    }
                    DeviceDetailScreen(
                        device = live,
                        liveRssi = controller.detailRssi,
                        rotation = controller.rotationStats(live.id),
                        firstSeenAgoMs = controller.rotationFirstSeenAgoMs(live.id),
                        wornIds = controller.deviceHistory(live.id),
                        onRename = { controller.rename(live, it) },
                        onToggleFavorite = { controller.toggleFavorite(live) },
                        onToggleFlag = { controller.toggleFlag(live) },
                        onTrack = { controller.track(live) },
                        onBack = controller::startDiscovery,
                    )
                }
            }
        }
    }
}
