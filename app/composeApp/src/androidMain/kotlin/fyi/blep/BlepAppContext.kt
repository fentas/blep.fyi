package fyi.blep

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri

/** Holds the application [Context] for the composeApp Android layer (WorkManager,
 *  notifications, foreground service), captured at startup by [BlepAppInitProvider]. */
internal object BlepApp {
    @Volatile
    var ctx: Context? = null
}

/** No-op [ContentProvider] that captures the app context at startup — same trick the
 *  core uses — so background-scan plumbing can reach system services without a
 *  custom Application class. Registered in the Android manifest. */
class BlepAppInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        BlepApp.ctx = context?.applicationContext
        return true
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
