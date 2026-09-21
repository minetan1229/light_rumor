package com.lightrumor

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * LightRumorColors: Color palette inspired by Sony CineAlta, Sigma fp, Canon Cinema EOS, and Minolta alpha.
 * Completely anti-AI: pure matte chassis, zero neon glows, zero glassmorphism, zero floating blobs.
 */
@Immutable
data class LightRumorColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfacePressed: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accentAmber: Color,   // Sony Cine Amber (#FF7900)
    val accentRed: Color,     // Canon Cinema EOS Red (#E53935)
    val accentCyan: Color,    // Minolta Tech Cyan (#00A3E0)
    val sliderTrack: Color,
    val sliderFill: Color,
    val sliderZeroTick: Color,
    val graticule: Color
)

val ObsidianBlackColors = LightRumorColors(
    isDark = true,
    background = Color(0xFF0A0A0C),       // Sony/Sigma matte obsidian black
    surface = Color(0xFF121316),          // Knurled instrument panel
    surfaceElevated = Color(0xFF1B1D22),  // Elevated thumb dial housing
    surfacePressed = Color(0xFF24262C),
    borderSubtle = Color(0xFF2D2F33),     // Hairline titanium border
    borderStrong = Color(0xFF48484A),     // Structural frame divider
    textPrimary = Color(0xFFF5F5F7),      // Crisp high-contrast readout
    textSecondary = Color(0xFF8E8E93),    // Technical annotation
    textTertiary = Color(0xFF55565B),     // Unit label
    accentAmber = Color(0xFFFF7900),      // Sony Cine Amber active marker
    accentRed = Color(0xFFE53935),        // Canon Cinema Red indicator
    accentCyan = Color(0xFF00A3E0),       // Minolta Tech Cyan reticle
    sliderTrack = Color(0xFF202227),
    sliderFill = Color(0xFFFF7900),
    sliderZeroTick = Color(0xFFE5E5EA),
    graticule = Color(0xFF2D2F33)
)

val TechnicalArcticColors = LightRumorColors(
    isDark = false,
    background = Color(0xFFF4F4F6),       // High-contrast outdoor snow/desert white
    surface = Color(0xFFEAEAEF),          // Technical instrument plate
    surfaceElevated = Color(0xFFDFDFE6),  // Raised dial panel
    surfacePressed = Color(0xFFD4D4DC),
    borderSubtle = Color(0xFFC7C7CC),     // Precision ground aluminum edge
    borderStrong = Color(0xFF8E8E93),
    textPrimary = Color(0xFF1C1D21),      // Heavy black legend
    textSecondary = Color(0xFF55565B),    // Slate technical text
    textTertiary = Color(0xFF8E8E93),
    accentAmber = Color(0xFFE66700),      // Outdoor high-visibility amber
    accentRed = Color(0xFFD32F2F),        // Tally red
    accentCyan = Color(0xFF0082B4),       // Technical cyan
    sliderTrack = Color(0xFFD8D8DE),
    sliderFill = Color(0xFFE66700),
    sliderZeroTick = Color(0xFF1C1D21),
    graticule = Color(0xFFC7C7CC)
)

/**
 * Strict 2px-4px sharp corners for industrial photographic instrument feel.
 * Pill shapes and balloon curves are strictly forbidden.
 */
object LightRumorShapes {
    val SharpSquare = RoundedCornerShape(2.dp)
    val Panel = RoundedCornerShape(3.dp)
    val Button = RoundedCornerShape(4.dp)
    val Tile = RoundedCornerShape(4.dp)
}

/**
 * Optical Instrument Typography:
 * Monospace figures for all numeric readouts (+0.75 EV, 5600 K, 1/250s, f/2.8).
 */
object LightRumorTypography {
    val Header = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 1.0.sp
    )

    val Label = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.8.sp
    )

    val ValueReadout = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp
    )

    val MicroIndex = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.4.sp
    )

    val Tab = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.8.sp
    )
}

val LocalLightRumorColors = compositionLocalOf { ObsidianBlackColors }

@Composable
fun LightRumorTheme(
    isDark: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = if (isDark) ObsidianBlackColors else TechnicalArcticColors
    CompositionLocalProvider(
        LocalLightRumorColors provides colors,
        content = content
    )
}

object LightRumorTheme {
    val colors: LightRumorColors
        @Composable
        get() = LocalLightRumorColors.current
    val shapes = LightRumorShapes
    val typography = LightRumorTypography
}
