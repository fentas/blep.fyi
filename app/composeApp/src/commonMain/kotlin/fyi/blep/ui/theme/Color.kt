package fyi.blep.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Brand palette, derived from the "blep" mascot logo. See `design/tokens.md`
 * for the single source of truth shared with the website.
 */
object BlepColors {
    val Blue = Color(0xFF5F90C3)
    val Pink = Color(0xFFFAB1B7)
    val Cream = Color(0xFFF4F5F0)
    val Ink = Color(0xFF27313B)
    val Mist = Color(0xFFEEF2F6)
    val Gold = Color(0xFFE0A93B) // favourite star — warm accent off the proximity amber

    // Proximity gradient stops: far → close ends on a soft pastel green.
    private val proximityStops = listOf(
        0.00f to Color(0xFF7FA8D4), // far: cool pastel blue
        0.45f to Color(0xFF9FD6C6), // calm teal
        0.75f to Color(0xFFC4E7B6), // light pastel green
        1.00f to Color(0xFFAEDD98), // close: pastel green
    )

    // Darker, muted twin of the ramp for dark surfaces (e.g. the signal bars on
    // the dark discovery screen). Same blue → green progression, lower lightness
    // so the bright pastels don't glare against the dark background.
    private val proximityStopsDark = listOf(
        0.00f to Color(0xFF577CA3), // far: muted blue
        0.45f to Color(0xFF5F9385), // muted teal
        0.75f to Color(0xFF7FA06B), // muted sage
        1.00f to Color(0xFF74A05C), // close: muted green
    )

    /**
     * Colour for a proximity in [0f, 1f], linearly interpolated between stops.
     * Drives the tracking screen's background + arrow tint ("getting warmer").
     * Pass [dark] for the muted ramp used on dark surfaces.
     */
    fun proximity(fraction: Float, dark: Boolean = false): Color {
        val stops = if (dark) proximityStopsDark else proximityStops
        val f = fraction.coerceIn(0f, 1f)
        for (i in 0 until stops.lastIndex) {
            val (p0, c0) = stops[i]
            val (p1, c1) = stops[i + 1]
            if (f <= p1) {
                val t = if (p1 == p0) 0f else (f - p0) / (p1 - p0)
                return lerp(c0, c1, t)
            }
        }
        return stops.last().second
    }
}
