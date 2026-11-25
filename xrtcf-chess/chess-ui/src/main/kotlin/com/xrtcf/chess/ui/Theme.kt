package com.xrtcf.chess.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Premium dark palette: black/charcoal + purple accent. */
object XrtcfColors {
    val Background = Color(0xFF0A0A0F)
    val Surface = Color(0xFF14141C)
    val SurfaceVariant = Color(0xFF1C1C28)
    val SurfaceElevated = Color(0xFF22222E)
    val Primary = Color(0xFFA855F7)
    val PrimaryDim = Color(0xFF7C3AED)
    val OnPrimary = Color(0xFFFFFFFF)
    val OnSurface = Color(0xFFECECF1)
    val OnSurfaceMuted = Color(0xFF9A9AB0)
    val Outline = Color(0xFF2E2E3E)

    val LightSquare = Color(0xFFE8D5B0)
    val DarkSquare = Color(0xFF6B4F3A)
    val LightSquareAlt = Color(0xFFEEE1C8)
    val DarkSquareAlt = Color(0xFF7A5C44)

    val Selected = Color(0x80A855F7)
    val LegalMove = Color(0x6622C55E)
    val LastMove = Color(0x66EAB308)
    val Check = Color(0x99EF4444)
    val Capture = Color(0x66EF4444)

    val EvalBarWhite = Color(0xFFF5F5F5)
    val EvalBarBlack = Color(0xFF1A1A1A)
}

private val DarkScheme = darkColorScheme(
    primary = XrtcfColors.Primary,
    onPrimary = XrtcfColors.OnPrimary,
    secondary = XrtcfColors.PrimaryDim,
    background = XrtcfColors.Background,
    onBackground = XrtcfColors.OnSurface,
    surface = XrtcfColors.Surface,
    onSurface = XrtcfColors.OnSurface,
    surfaceVariant = XrtcfColors.SurfaceVariant,
    onSurfaceVariant = XrtcfColors.OnSurfaceMuted,
    outline = XrtcfColors.Outline,
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp),
)

@Composable
fun XrtcfChessTheme(content: @Composable () -> Unit) {
    // Always dark premium theme per design spec
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = AppTypography,
        content = content,
    )
}
