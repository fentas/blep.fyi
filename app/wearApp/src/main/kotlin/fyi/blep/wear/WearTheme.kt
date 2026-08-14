package fyi.blep.wear

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/**
 * The watch's colour system — the phone's brand palette expressed as Wear Material
 * colours, so rows, chips, buttons and the radar stop each inventing their own hex.
 *
 * Dark-first by deliberate choice: a watch screen is mostly off, and on OLED an almost
 * black background is what lets the accent and the proximity ramp read at a glance.
 * The one screen that stays light is the hunt itself — there the *background colour is
 * the information* (cold blue → warm amber as you close in), so it must not be dimmed
 * into the theme. [Ink] is the on-colour there.
 */
object BlepWear {
    /** Brand blue — the accent, and the fill for a device you're watching. */
    val Blue = Color(0xFF5F90C3)
    /** Near-black page. Deeper than a neutral grey so the accent carries on OLED. */
    val Background = Color(0xFF0B0F14)
    /** Raised row/chip fill, a step above [Background] rather than a border. */
    val Surface = Color(0xFF1B2430)
    /** Text on the light accent, and on the light proximity backgrounds of the hunt. */
    val Ink = Color(0xFF27313B)
    /** Text on dark surfaces. */
    val Cream = Color(0xFFF4F5F0)
    /** Alert / destination pin. */
    val Rose = Color(0xFFD94F70)
    /** Recovery cue. */
    val Amber = Color(0xFFE8A33D)
}

private val BlepWearColors = Colors(
    primary = BlepWear.Blue,
    primaryVariant = Color(0xFF3A6098),
    secondary = BlepWear.Surface,
    secondaryVariant = Color(0xFF2A3644),
    background = BlepWear.Background,
    surface = BlepWear.Surface,
    error = BlepWear.Rose,
    // Dark glyphs on the light accent, matching the Wear convention of a light filled
    // control carrying dark content.
    onPrimary = BlepWear.Ink,
    onSecondary = BlepWear.Cream,
    onBackground = BlepWear.Cream,
    onSurface = BlepWear.Cream,
    onSurfaceVariant = Color(0xFFA8B4C2),
    onError = BlepWear.Cream,
)

@Composable
fun BlepWearTheme(content: @Composable () -> Unit) =
    MaterialTheme(colors = BlepWearColors, content = content)
