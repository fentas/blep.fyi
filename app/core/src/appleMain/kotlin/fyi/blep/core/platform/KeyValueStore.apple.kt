package fyi.blep.core.platform

import platform.Foundation.NSDate
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970

actual class KeyValueStore(private val defaults: NSUserDefaults) {
    actual fun getString(key: String): String? = defaults.stringForKey(key)
    actual fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }
    actual fun getBoolean(key: String, default: Boolean): Boolean =
        if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key)
    actual fun putBoolean(key: String, value: Boolean) {
        defaults.setBool(value, forKey = key)
    }
}

actual fun createKeyValueStore(): KeyValueStore =
    KeyValueStore(NSUserDefaults.standardUserDefaults)

actual fun epochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
