package dev.blipwave.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Display = FontFamily.SansSerif

val BlipTypography = Typography(
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.25).sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp),
)

/** MAC adresi, UUID ve HEX içerik için eş aralıklı yazı. */
val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, letterSpacing = 0.2.sp)
