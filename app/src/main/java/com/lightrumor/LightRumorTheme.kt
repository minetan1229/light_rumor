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
    val statusPick: Color = Color(0xFF388E3C),    // Professional Green pick indicator
    val statusReject: Color = Color(0xFFE53935),  // Professional Red reject indicator
    val sliderTrack: Color,
    val sliderFill: Color,
    val sliderZeroTick: Color,
    val graticule: Color
)

val ObsidianBlackColors = LightRumorColors(
    isDark = true,
    background = Color(0xFF0B0B0D),       // Neutral obsidian studio black
    surface = Color(0xFF131417),          // Knurled instrument panel chassis
    surfaceElevated = Color(0xFF1C1D22),  // Elevated dial/fader housing
    surfacePressed = Color(0xFF26282F),
    borderSubtle = Color(0xFF2B2D35),     // Hairline titanium border
    borderStrong = Color(0xFF424550),     // Structural frame divider
    textPrimary = Color(0xFFF2F3F5),      // Crisp high-contrast readout
    textSecondary = Color(0xFF989AA2),    // High-visibility technical annotation
    textTertiary = Color(0xFF686A73),     // Unit label
    accentAmber = Color(0xFFFF8800),      // Sony Cine Amber / Studio Gold active marker
    accentRed = Color(0xFFE53935),        // Canon Cinema Red indicator / Reject flag
    accentCyan = Color(0xFF00B4F0),       // Minolta Tech Cyan reticle
    statusPick = Color(0xFF388E3C),
    statusReject = Color(0xFFE53935),
    sliderTrack = Color(0xFF202229),
    sliderFill = Color(0xFFFF8800),
    sliderZeroTick = Color(0xFFF2F3F5),
    graticule = Color(0xFF2B2D35)
)

val TechnicalArcticColors = LightRumorColors(
    isDark = false,
    background = Color(0xFFF0F1F4),       // High-contrast outdoor snow/desert white
    surface = Color(0xFFE4E5EA),          // Technical instrument plate
    surfaceElevated = Color(0xFFD8D9E0),  // Raised dial panel
    surfacePressed = Color(0xFFCBCDD6),
    borderSubtle = Color(0xFFB4B6C2),     // Precision ground aluminum edge
    borderStrong = Color(0xFF767884),
    textPrimary = Color(0xFF111215),      // Heavy black legend
    textSecondary = Color(0xFF484A54),    // Slate technical text
    textTertiary = Color(0xFF727480),
    accentAmber = Color(0xFFE66700),      // Outdoor high-visibility amber
    accentRed = Color(0xFFD32F2F),        // Tally red
    accentCyan = Color(0xFF007AA8),       // Technical cyan
    statusPick = Color(0xFF2E7D32),
    statusReject = Color(0xFFD32F2F),
    sliderTrack = Color(0xFFC8CAD4),
    sliderFill = Color(0xFFE66700),
    sliderZeroTick = Color(0xFF111215),
    graticule = Color(0xFFB4B6C2)
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
 * High legibility across all screen densities.
 * Monospace figures for all numeric readouts (+0.75 EV, 5600 K, 1/250s, f/2.8).
 * Crisp SansSerif with high tracking for labels and UI headers.
 */
object LightRumorTypography {
    val TitleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        letterSpacing = 1.0.sp
    )

    val Header = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.8.sp
    )

    val TitleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp
    )

    val Label = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.6.sp
    )

    val Body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = 0.2.sp
    )

    val ValueReadout = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 0.4.sp
    )

    val Tab = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.6.sp
    )

    val Button = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp
    )

    val Caption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.2.sp
    )

    val Badge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 0.4.sp
    )

    val MicroIndex = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.2.sp
    )
}

val LocalLightRumorColors = compositionLocalOf { ObsidianBlackColors }
val LocalUiFontScale = compositionLocalOf { 1.0f }

@Composable
fun LightRumorTheme(
    isDark: Boolean = true,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val colors = if (isDark) ObsidianBlackColors else TechnicalArcticColors
    CompositionLocalProvider(
        LocalLightRumorColors provides colors,
        LocalUiFontScale provides fontScale,
        content = content
    )
}

object LightRumorTheme {
    val colors: LightRumorColors
        @Composable
        get() = LocalLightRumorColors.current
    val shapes = LightRumorShapes
    val typography = LightRumorTypography
    val fontScale: Float
        @Composable
        get() = LocalUiFontScale.current
}
