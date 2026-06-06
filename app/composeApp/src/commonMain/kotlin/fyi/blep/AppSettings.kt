package fyi.blep

import fyi.blep.core.platform.KeyValueStore
import fyi.blep.core.platform.createKeyValueStore

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

    private companion object {
        const val KEY_CONN_SIGNAL = "settings.connectedSignal"
        const val KEY_SOUND = "settings.trackingSound"
        const val KEY_UNNAMED = "settings.showUnnamed"
    }
}
