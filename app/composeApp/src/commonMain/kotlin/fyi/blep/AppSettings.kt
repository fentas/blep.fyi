package fyi.blep

import fyi.blep.core.platform.KeyValueStore
import fyi.blep.core.platform.createKeyValueStore
import fyi.blep.core.safety.ScanSensitivity

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

    fun trackingSound(): Boolean = store.getBoolean(KEY_SOUND, true)
    fun setTrackingSound(on: Boolean) = store.putBoolean(KEY_SOUND, on)

    fun showUnnamed(): Boolean = store.getBoolean(KEY_UNNAMED, false)
    fun setShowUnnamed(on: Boolean) = store.putBoolean(KEY_UNNAMED, on)

    // ── Background safety scanning (default off; asks permission on activate) ──
    /** Keep the safety scan alive when you leave the app (foreground service). */
    fun foregroundScan(): Boolean = store.getBoolean(KEY_FG, false)
    fun setForegroundScan(on: Boolean) = store.putBoolean(KEY_FG, on)

    /** Periodic safety scan while the app is closed (WorkManager). */
    fun backgroundScan(): Boolean = store.getBoolean(KEY_BG, false)
    fun setBackgroundScan(on: Boolean) = store.putBoolean(KEY_BG, on)

    /** Periodic interval in minutes, clamped to [INTERVAL_MIN]..[INTERVAL_MAX]
     *  ([INTERVAL_MIN] is WorkManager's hard floor for periodic work). */
    fun scanIntervalMinutes(): Int =
        (store.getString(KEY_INTERVAL)?.toIntOrNull() ?: 30).coerceIn(INTERVAL_MIN, INTERVAL_MAX)
    fun setScanIntervalMinutes(min: Int) =
        store.putString(KEY_INTERVAL, min.coerceIn(INTERVAL_MIN, INTERVAL_MAX).toString())

    /** Detection sensitivity preset for the safety scan. */
    fun scanSensitivity(): ScanSensitivity = ScanSensitivity.fromName(store.getString(KEY_SENSITIVITY))
    fun setScanSensitivity(s: ScanSensitivity) = store.putString(KEY_SENSITIVITY, s.name)

    companion object {
        const val INTERVAL_MIN = 15   // WorkManager periodic floor
        const val INTERVAL_MAX = 240  // 4 hours

        private const val KEY_CONN_SIGNAL = "settings.connectedSignal"
        private const val KEY_SOUND = "settings.trackingSound"
        private const val KEY_UNNAMED = "settings.showUnnamed"
        private const val KEY_FG = "settings.foregroundScan"
        private const val KEY_BG = "settings.backgroundScan"
        private const val KEY_INTERVAL = "settings.scanIntervalMin"
        private const val KEY_SENSITIVITY = "settings.scanSensitivity"
    }
}
