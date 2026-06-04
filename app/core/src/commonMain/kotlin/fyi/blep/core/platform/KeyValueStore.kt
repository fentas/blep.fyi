package fyi.blep.core.platform

/**
 * A tiny persistent key/value store — SharedPreferences on Android, NSUserDefaults
 * on Apple, an in-memory map for tests. Used by the safety feature to remember
 * tracker encounters across app sessions (the "is it following me across hours?"
 * signal that a single live scan can't see).
 */
expect class KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}

expect fun createKeyValueStore(): KeyValueStore

/**
 * Wall-clock epoch millis. Distinct from the monotonic clock the live tracker uses:
 * cross-session correlation needs real time (a monotonic clock resets on reboot and
 * can't compare "9am vs 3pm" across separate app launches).
 */
expect fun epochMillis(): Long
