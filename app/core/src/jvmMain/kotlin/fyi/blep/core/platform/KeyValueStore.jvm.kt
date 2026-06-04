package fyi.blep.core.platform

/** In-memory store for JVM unit tests — no persistence needed off-device. */
actual class KeyValueStore {
    private val strings = HashMap<String, String>()
    private val bools = HashMap<String, Boolean>()
    actual fun getString(key: String): String? = strings[key]
    actual fun putString(key: String, value: String) { strings[key] = value }
    actual fun getBoolean(key: String, default: Boolean): Boolean = bools[key] ?: default
    actual fun putBoolean(key: String, value: Boolean) { bools[key] = value }
}

actual fun createKeyValueStore(): KeyValueStore = KeyValueStore()

private var clock = 0L
actual fun epochMillis(): Long = ++clock
