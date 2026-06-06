package fyi.blep.core.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import fyi.blep.core.ble.BlepContext
import kotlin.math.roundToLong

// ~2 km grid: coarse enough to tell "a different place" apart while staying vague.
private const val CELL_DEG = 0.02

actual fun coarsePlaceCell(): String? {
    val ctx = BlepContext.app ?: return null
    if (ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        return null
    }
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val loc = runCatching {
        lm.getProviders(/* enabledOnly = */ true).asSequence()
            .mapNotNull { p -> lm.getLastKnownLocation(p) }
            .maxByOrNull { it.time }
    }.getOrNull() ?: return null
    return "${(loc.latitude / CELL_DEG).roundToLong()},${(loc.longitude / CELL_DEG).roundToLong()}"
}
