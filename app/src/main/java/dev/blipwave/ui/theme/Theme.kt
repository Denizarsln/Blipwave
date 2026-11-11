package dev.blipwave.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = Color.White,
    primaryContainer = DeepViolet,
    onPrimaryContainer = Cloud,
    secondary = Coral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF5A1F3A),
    onSecondaryContainer = Color(0xFFFFD9D9),
    tertiary = NeonCyan,
    onTertiary = Night,
    background = Night,
    onBackground = Cloud,
    surface = Night,
    onSurface = Cloud,
    surfaceVariant = NightHigh,
    onSurfaceVariant = CloudMuted,
    surfaceContainerLowest = Color(0xFF14081F),
    surfaceContainerLow = Color(0xFF1F0F37),
    surfaceContainer = NightRaised,
    surfaceContainerHigh = NightHigh,
    surfaceContainerHighest = Color(0xFF3A2361),
    outline = NightOutline,
    outlineVariant = Color(0xFF3A2561),
    error = Coral,
    onError = Color.White,
)

private val LightScheme = lightColorScheme(
    primary = DeepViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF2E0F6B),
    secondary = Color(0xFFE5484D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0E0),
    onSecondaryContainer = Color(0xFF5C1013),
    tertiary = DeepCyan,
    onTertiary = Color.White,
    background = Mist,
    onBackground = Ink,
    surface = Mist,
    onSurface = Ink,
    surfaceVariant = MistHigh,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFDFAFF),
    surfaceContainer = MistRaised,
    surfaceContainerHigh = MistHigh,
    surfaceContainerHighest = Color(0xFFE6DAFB),
    outline = MistOutline,
    outlineVariant = Color(0xFFE8DEF8),
    error = Color(0xFFE5484D),
    onError = Color.White,
)

/** Material şemasında olmayan marka renkleri ve gradyanlar. */
@Immutable
data class BlipColors(
    val isDark: Boolean,
    val brandGradient: Brush,
    val warmGradient: Brush,
    val coolGradient: Brush,
    val heroGradient: Brush,
    val cardGradient: Brush,
    val glow: Color,
    val signalExcellent: Color,
    val signalGood: Color,
    val signalFair: Color,
    val signalWeak: Color,
    val bubbleRx: Color,
    val onBubbleRx: Color,
)

private fun blipColors(dark: Boolean): BlipColors = BlipColors(
    isDark = dark,
    brandGradient = Brush.linearGradient(listOf(ElectricViolet, HotPink, Coral)),
    warmGradient = Brush.linearGradient(listOf(Coral, SunsetOrange)),
    coolGradient = Brush.linearGradient(listOf(ElectricViolet, NeonCyan)),
    heroGradient = if (dark) {
        Brush.linearGradient(listOf(Color(0xFF3B1A6E), Color(0xFF5B1F63), Color(0xFF6E2A4A)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFEDE3FF), Color(0xFFFFE3F1), Color(0xFFFFE9DC)))
    },
    cardGradient = if (dark) {
        Brush.linearGradient(listOf(Color(0xFF2A1549), Color(0xFF23123D)))
    } else {
        Brush.linearGradient(listOf(Color.White, Color(0xFFFAF5FF)))
    },
    glow = if (dark) ElectricViolet.copy(alpha = 0.45f) else ElectricViolet.copy(alpha = 0.25f),
    signalExcellent = if (dark) NeonCyan else DeepCyan,
    signalGood = ElectricViolet,
    signalFair = SunsetOrange,
    signalWeak = Coral,
    bubbleRx = if (dark) NightHigh else Color.White,
    onBubbleRx = if (dark) Cloud else Ink,
)

val LocalBlipColors = staticCompositionLocalOf { blipColors(dark = true) }

private val BlipShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun BlipwaveTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBlipColors provides blipColors(darkTheme)) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = BlipTypography,
            shapes = BlipShapes,
            content = content,
        )
    }
}

/** Tema dışından kısa erişim: `BlipTheme.colors.brandGradient`. */
object BlipTheme {
    val colors: BlipColors
        @Composable get() = LocalBlipColors.current
}
