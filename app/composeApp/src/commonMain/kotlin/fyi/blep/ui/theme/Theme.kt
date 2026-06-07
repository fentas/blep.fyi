package fyi.blep.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Neutral screens (Discovery / Settings / Safety) read background / onBackground /
// surface from the scheme, so they flip with dark mode. Brand accents (Blue, Pink,
// Gold, Cream) and the warm/cold proximity colours stay fixed — the Tracking,
// Radar and Completion screens sit on the always-light proximity background and
// keep their dark ink, so they're intentionally not theme-flipped.
private val LightScheme = lightColorScheme(
    primary = BlepColors.Blue, onPrimary = BlepColors.Cream,
    secondary = BlepColors.Pink, onSecondary = BlepColors.Ink,
    background = BlepColors.Mist, onBackground = BlepColors.Ink,
    surface = Color(0xFFFFFFFF), onSurface = BlepColors.Ink,
)

private val DarkScheme = darkColorScheme(
    primary = BlepColors.Blue, onPrimary = BlepColors.Cream,
    secondary = BlepColors.Pink, onSecondary = Color(0xFF14181D),
    background = Color(0xFF14181D), onBackground = Color(0xFFE7EBEF),
    surface = Color(0xFF222A32), onSurface = Color(0xFFE7EBEF),
)

private val BlepShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

/** Root theme: minimalist, pastel, geometric — follows the system light/dark setting. */
@Composable
fun BlepTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        typography = BlepTypography,
        shapes = BlepShapes,
        content = content,
    )
}
