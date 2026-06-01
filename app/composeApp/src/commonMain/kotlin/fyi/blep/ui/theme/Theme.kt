package fyi.blep.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val BlepColorScheme = lightColorScheme(
    primary = BlepColors.Blue,
    onPrimary = BlepColors.Cream,
    secondary = BlepColors.Pink,
    onSecondary = BlepColors.Ink,
    background = BlepColors.Mist,
    onBackground = BlepColors.Ink,
    surface = BlepColors.Cream,
    onSurface = BlepColors.Ink,
)

private val BlepShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

/** Root theme: minimalist, pastel, geometric. */
@Composable
fun BlepTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BlepColorScheme,
        typography = BlepTypography,
        shapes = BlepShapes,
        content = content,
    )
}
