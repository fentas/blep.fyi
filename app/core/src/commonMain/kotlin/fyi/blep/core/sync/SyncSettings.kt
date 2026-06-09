package fyi.blep.core.sync

import fyi.blep.core.platform.KeyValueStore

/**
 * Per-category toggles for what syncs between phone and watch (the "Sync" settings menu).
 * A master switch plus one per category, so a user can, say, share favourites but keep
 * device names private. Read by [SyncManager] before publishing/applying each category.
 * Defaults: everything on except the heavier scan-fusion stream (opt-in).
 */
class SyncSettings(private val store: KeyValueStore) {
    fun enabled(): Boolean = store.getBoolean(KEY_ON, true)
    fun setEnabled(on: Boolean) = store.putBoolean(KEY_ON, on)

    fun favorites(): Boolean = on(KEY_FAV)
    fun setFavorites(on: Boolean) = store.putBoolean(KEY_FAV, on)

    fun names(): Boolean = on(KEY_NAMES)
    fun setNames(on: Boolean) = store.putBoolean(KEY_NAMES, on)

    fun tethered(): Boolean = on(KEY_TETHER)
    fun setTethered(on: Boolean) = store.putBoolean(KEY_TETHER, on)

    fun mutes(): Boolean = on(KEY_MUTES)
    fun setMutes(on: Boolean) = store.putBoolean(KEY_MUTES, on)

    fun settings(): Boolean = on(KEY_SETTINGS)
    fun setSettings(on: Boolean) = store.putBoolean(KEY_SETTINGS, on)

    fun alerts(): Boolean = on(KEY_ALERTS)
    fun setAlerts(on: Boolean) = store.putBoolean(KEY_ALERTS, on)

    /** Scan fusion (relaying live device sightings) — heavier, so opt-in (default off). */
    fun scans(): Boolean = store.getBoolean(KEY_SCANS, false)
    fun setScans(on: Boolean) = store.putBoolean(KEY_SCANS, on)

    // Raw per-category value (default on). The master switch is enforced separately by
    // SyncManager (it doesn't even start when disabled), so the UI can show each toggle
    // on its own merits.
    private fun on(key: String) = store.getBoolean(key, true)

    private companion object {
        const val KEY_ON = "sync.enabled"
        const val KEY_FAV = "sync.favorites"
        const val KEY_NAMES = "sync.names"
        const val KEY_TETHER = "sync.tethered"
        const val KEY_MUTES = "sync.mutes"
        const val KEY_SETTINGS = "sync.settings"
        const val KEY_ALERTS = "sync.alerts"
        const val KEY_SCANS = "sync.scans"
    }
}
