package fyi.blep.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Device-row action glyphs as tintable [ImageVector]s (Material star / star-border
// / edit paths). Vectors have a known 24×24 viewbox, so they centre exactly in a
// Box — unlike the ★/☆/✎ font glyphs, whose per-glyph metrics differ and never
// quite line up. Tint at the call site via `Icon(..., tint = …)`.

private fun icon(name: String, path: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
    }.build()

/** Door with an arrow leaving it — "left behind" (a device that walked out of range). */
val ExitIcon: ImageVector by lazy {
    icon(
        "Exit",
        "M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2" +
            "v14c0 1.1.9 2 2 2h8v-2H4V5z",
    )
}

/** Filled star — favourited. */
val StarFilledIcon: ImageVector by lazy {
    icon(
        "StarFilled",
        "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24" +
            "l5.46 4.73L5.82 21z",
    )
}

/** Hollow star — not favourited. */
val StarOutlineIcon: ImageVector by lazy {
    icon(
        "StarOutline",
        "M22 9.24l-7.19-.62L12 2 9.19 8.62 2 9.24l5.45 4.73L5.82 21 12 17.27 18.18 21" +
            "l-1.64-7.03L22 9.24zM12 15.4l-3.76 2.27 1-4.28-3.32-2.88 4.38-.38L12 6.1" +
            "l1.71 4.04 4.38.38-3.32 2.88 1 4.28L12 15.4z",
    )
}

/** Pencil — rename. */
val PencilIcon: ImageVector by lazy {
    icon(
        "Pencil",
        "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41" +
            "l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z",
    )
}
