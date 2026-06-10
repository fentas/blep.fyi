package fyi.blep

import fyi.blep.core.platform.KeyValueStore
import fyi.blep.core.platform.createKeyValueStore
import fyi.blep.core.safety.ScanSensitivity
import fyi.blep.core.tether.TetherAlertDirection

/** App theme preference: follow the OS, or force light/dark. */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * How hard blep looks for trackers while you're not in the app — one tri-state instead of
 * two overlapping toggles. [CONTINUOUS] supersedes [INTERVAL] (you'd never run both).
 * Left-behind/flag watching is separate and always-auto; it can keep the foreground service
 * alive on its own (in watch-only mode) regardless of this.
 */
enum class ScanMode {
    /** No background tracker scanning. */
    OFF,

    /** Periodic WorkManager checks while blep is closed — lighter on battery, can miss things. */
    INTERVAL,

    /** A continuous foreground-service scan — best detection, more battery. */
    CONTINUOUS;

    companion object {
        fun fromName(name: String?): ScanMode? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Persisted app preferences shown on the Settings screen. Thin typed wrapper over
 * [KeyValueStore]; the controller reads these as the live values (the inline
 * toggles on Discovery/Tracking write through to here too). "Remember trackers"
 * isn't here — it already persists via the safety history, and the controller
 * bridges it so the Settings screen and the Safety screen stay in sync.
 */
class AppSettings(private val store: KeyValueStore = createKeyValueStore()) {
    fun measureConnectedSignal(): Boolean = store.getBoolean(KEY_CONN_SIGNAL, true)
    fun setMeasureConnectedSignal(on: Boolean) = store.putBoolean(KEY_CONN_SIGNAL, on)

    fun trackingSound(): Boolean = store.getBoolean(KEY_SOUND, false)
    fun setTrackingSound(on: Boolean) = store.putBoolean(KEY_SOUND, on)

    /** Vibration feedback during tracking (the proximity "Geiger" pulse + success). */
    fun haptics(): Boolean = store.getBoolean(KEY_HAPTICS, true)
    fun setHaptics(on: Boolean) = store.putBoolean(KEY_HAPTICS, on)

    fun showUnnamed(): Boolean = store.getBoolean(KEY_UNNAMED, false)
    fun setShowUnnamed(on: Boolean) = store.putBoolean(KEY_UNNAMED, on)

    /** Whether the left-behind explainer modal has been dismissed for good. */
    fun watchExplainerDismissed(): Boolean = store.getBoolean(KEY_WATCH_EXPLAINED, false)
    fun setWatchExplainerDismissed(on: Boolean) = store.putBoolean(KEY_WATCH_EXPLAINED, on)

    /** Whether the "Watch this device" (flag) explainer modal has been dismissed for good. */
    fun flagExplainerDismissed(): Boolean = store.getBoolean(KEY_FLAG_EXPLAINED, false)
    fun setFlagExplainerDismissed(on: Boolean) = store.putBoolean(KEY_FLAG_EXPLAINED, on)

    // ── Background tracker scanning (default off; asks permission on activate) ──
    /** The tri-state scan mode. Migrates the old two booleans (foreground → CONTINUOUS,
     *  background → INTERVAL) the first time, until [setScanMode] writes the new key. */
    fun scanMode(): ScanMode {
        ScanMode.fromName(store.getString(KEY_SCAN_MODE))?.let { return it }
        return when {
            store.getBoolean(KEY_FG, false) -> ScanMode.CONTINUOUS
            store.getBoolean(KEY_BG, false) -> ScanMode.INTERVAL
            else -> ScanMode.OFF
        }
    }
    fun setScanMode(mode: ScanMode) = store.putString(KEY_SCAN_MODE, mode.name)

    /** Continuous foreground tracker scan is on (the "tracker scan" the service runs). */
    fun foregroundScan(): Boolean = scanMode() == ScanMode.CONTINUOUS

    /** Periodic interval tracker scan is on. */
    fun backgroundScan(): Boolean = scanMode() == ScanMode.INTERVAL

    /** Periodic interval in minutes, clamped to [INTERVAL_MIN]..[INTERVAL_MAX]
     *  ([INTERVAL_MIN] is WorkManager's hard floor for periodic work). */
    fun scanIntervalMinutes(): Int =
        (store.getString(KEY_INTERVAL)?.toIntOrNull() ?: 30).coerceIn(INTERVAL_MIN, INTERVAL_MAX)
    fun setScanIntervalMinutes(min: Int) =
        store.putString(KEY_INTERVAL, min.coerceIn(INTERVAL_MIN, INTERVAL_MAX).toString())

    /** Detection sensitivity preset for the safety scan. */
    fun scanSensitivity(): ScanSensitivity = ScanSensitivity.fromName(store.getString(KEY_SENSITIVITY))
    fun setScanSensitivity(s: ScanSensitivity) = store.putString(KEY_SENSITIVITY, s.name)

    /** Location-aware detection: sample a coarse on-device place on a suspect sighting
     *  to tell a follower (seen across places) from your daily crowd. Opt-in. */
    fun locationAware(): Boolean = store.getBoolean(KEY_LOCATION, false)
    fun setLocationAware(on: Boolean) = store.putBoolean(KEY_LOCATION, on)

    /** Whether the first-run onboarding has been shown (skipped or completed). */
    fun onboarded(): Boolean = store.getBoolean(KEY_ONBOARDED, false)
    fun setOnboarded(on: Boolean) = store.putBoolean(KEY_ONBOARDED, on)

    /** Light/dark/system theme preference. */
    fun themeMode(): ThemeMode = ThemeMode.fromName(store.getString(KEY_THEME))
    fun setThemeMode(mode: ThemeMode) = store.putString(KEY_THEME, mode.name)

    /** How long a device's identity (rename / flag / first-seen) is remembered after it
     *  was last seen, in days. Clamped to [IDENTITY_TTL_MIN_DAYS]..[IDENTITY_TTL_MAX_DAYS]. */
    fun identityTtlDays(): Int =
        (store.getString(KEY_IDENTITY_TTL)?.toIntOrNull() ?: 14).coerceIn(IDENTITY_TTL_MIN_DAYS, IDENTITY_TTL_MAX_DAYS)
    fun setIdentityTtlDays(days: Int) =
        store.putString(KEY_IDENTITY_TTL, days.coerceIn(IDENTITY_TTL_MIN_DAYS, IDENTITY_TTL_MAX_DAYS).toString())

    /** Actively probe a device (one short GATT connection) to learn its name/identity
     *  once it has lingered. On by default; the connection is one-shot and cached. */
    fun probeEnabled(): Boolean = store.getBoolean(KEY_PROBE, true)
    fun setProbeEnabled(on: Boolean) = store.putBoolean(KEY_PROBE, on)

    /** How long a device must have been around before it's worth a probe (minutes) —
     *  so passers-by are ignored. Clamped to [PROBE_MIN_MINUTES]..[PROBE_MAX_MINUTES]. */
    fun probeThresholdMinutes(): Int =
        (store.getString(KEY_PROBE_MINS)?.toIntOrNull() ?: 2).coerceIn(PROBE_MIN_MINUTES, PROBE_MAX_MINUTES)
    fun setProbeThresholdMinutes(min: Int) =
        store.putString(KEY_PROBE_MINS, min.coerceIn(PROBE_MIN_MINUTES, PROBE_MAX_MINUTES).toString())

    /** Which presence transitions of a tethered device raise an alert (default BOTH). */
    fun tetherAlert(): TetherAlertDirection = TetherAlertDirection.fromName(store.getString(KEY_TETHER_ALERT))
    fun setTetherAlert(d: TetherAlertDirection) = store.putString(KEY_TETHER_ALERT, d.name)

    companion object {
        const val INTERVAL_MIN = 15   // WorkManager periodic floor
        const val INTERVAL_MAX = 240  // 4 hours
        const val IDENTITY_TTL_MIN_DAYS = 1
        const val IDENTITY_TTL_MAX_DAYS = 90
        const val PROBE_MIN_MINUTES = 1
        // Past ~15 min a private address would normally have rotated, so a device still on
        // the same id is effectively stable — no point waiting longer before probing it.
        const val PROBE_MAX_MINUTES = 15
        const val DAY_MS = 24L * 60 * 60 * 1000

        private const val KEY_CONN_SIGNAL = "settings.connectedSignal"
        private const val KEY_SOUND = "settings.trackingSound"
        private const val KEY_HAPTICS = "settings.haptics"
        private const val KEY_UNNAMED = "settings.showUnnamed"
        private const val KEY_FG = "settings.foregroundScan"
        private const val KEY_SCAN_MODE = "settings.scanMode"
        private const val KEY_WATCH_EXPLAINED = "settings.watchExplainerDismissed"
        private const val KEY_FLAG_EXPLAINED = "settings.flagExplainerDismissed"
        private const val KEY_BG = "settings.backgroundScan"
        private const val KEY_INTERVAL = "settings.scanIntervalMin"
        private const val KEY_SENSITIVITY = "settings.scanSensitivity"
        private const val KEY_LOCATION = "settings.locationAware"
        private const val KEY_ONBOARDED = "settings.onboarded"
        private const val KEY_THEME = "settings.themeMode"
        private const val KEY_IDENTITY_TTL = "settings.identityTtlDays"
        private const val KEY_PROBE = "settings.probeEnabled"
        private const val KEY_PROBE_MINS = "settings.probeThresholdMinutes"
        private const val KEY_TETHER_ALERT = "settings.tetherAlert"
    }
}
