package fyi.blep.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Typography for the high-contrast, geometric look in `design/tokens.md`.
 *
 * Uses the platform geometric sans by default (SF Pro on Apple, Roboto/system
 * on Android). To match the website exactly, drop Inter into
 * `composeResources/font` and swap [brandFontFamily].
 */
private val brandFontFamily = FontFamily.Default

val BlepTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = brandFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.03).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = brandFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.02).em,
    ),
    titleMedium = TextStyle(
        fontFamily = brandFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.01).em,
    ),
    bodyLarge = TextStyle(
        fontFamily = brandFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = brandFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.01.em,
    ),
)
