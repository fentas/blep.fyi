package fyi.blep.core.platform

import android.content.Context
import android.content.SharedPreferences
import fyi.blep.core.ble.BlepContext

actual class KeyValueStore(private val prefs: SharedPreferences) {
    actual fun getString(key: String): String? = prefs.getString(key, null)
    actual fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
    actual fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    actual fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
}

actual fun createKeyValueStore(): KeyValueStore {
    val ctx = BlepContext.app ?: error("BlepContext not initialised")
    return KeyValueStore(ctx.getSharedPreferences("blep_safety", Context.MODE_PRIVATE))
}

actual fun epochMillis(): Long = System.currentTimeMillis()
