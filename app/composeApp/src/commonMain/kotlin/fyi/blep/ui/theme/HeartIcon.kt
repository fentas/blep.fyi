package fyi.blep.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * A filled heart (the Material "favorite" path) as a multiplatform [ImageVector],
 * so it renders **tintable** on every platform — unlike the `♥` emoji, which the
 * OS draws as a fixed-colour glyph that ignores the text colour. Tint at the call
 * site via `Icon(..., tint = …)`.
 */
val HeartIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Heart",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        val nodes = PathParser().parsePathString(
            "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3" +
                "c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5" +
                "c0 3.78-3.4 6.86-8.55 11.54L12 21.35z",
        ).toNodes()
        addPath(pathData = nodes, fill = SolidColor(Color.Black))
    }.build()
}
