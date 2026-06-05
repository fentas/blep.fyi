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

    /**
     * Colour for a proximity in [0f, 1f], linearly interpolated between stops.
     * Drives the tracking screen's background + arrow tint ("getting warmer").
     */
    fun proximity(fraction: Float): Color {
        val f = fraction.coerceIn(0f, 1f)
        for (i in 0 until proximityStops.lastIndex) {
            val (p0, c0) = proximityStops[i]
            val (p1, c1) = proximityStops[i + 1]
            if (f <= p1) {
                val t = if (p1 == p0) 0f else (f - p0) / (p1 - p0)
                return lerp(c0, c1, t)
            }
        }
        return proximityStops.last().second
    }
}
