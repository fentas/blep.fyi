package fyi.blep.core.ble

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri

/** Holds the application [Context] for the BLE layer. */
internal object BlepContext {
    @Volatile
    var app: Context? = null
}

/**
 * No-op [ContentProvider] that captures the application context at startup, so
 * the core library can reach Bluetooth system services without the app having
 * to pass a context in. Registered in the core Android manifest.
 */
class BlepContextProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        BlepContext.app = context?.applicationContext
        return true
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
