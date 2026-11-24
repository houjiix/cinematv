package com.cinematv.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object CinemaColors {
    val Obsidian = Color(0xFF090A0D)
    val Charcoal = Color(0xFF13161D)
    val SlateSurface = Color(0xFF1B1F29)
    val GoldAccent = Color(0xFFE5A93B)
    val CrimsonAction = Color(0xFFD9383A)
    val CrispWhite = Color(0xFFF4F5F7)
    val MutedSteel = Color(0xFF9BA3B4)
    val HairlineDark = Color(0xFF252A36)

    val LightCanvas = Color(0xFFF6F7F9)
    val LightSurface = Color(0xFFFFFFFF)
    val LightInk = Color(0xFF111319)
    val LightMuted = Color(0xFF5A6272)
}

object CinemaSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val tvOverscanHorizontal = 48.dp
    val tvOverscanVertical = 28.dp
}

private val DarkScheme = darkColorScheme(
    primary = CinemaColors.GoldAccent,
    onPrimary = CinemaColors.Obsidian,
    secondary = CinemaColors.CrimsonAction,
    onSecondary = Color.White,
    background = CinemaColors.Obsidian,
    onBackground = CinemaColors.CrispWhite,
    surface = CinemaColors.Charcoal,
    onSurface = CinemaColors.CrispWhite,
    surfaceVariant = CinemaColors.SlateSurface,
    onSurfaceVariant = CinemaColors.MutedSteel,
    outline = CinemaColors.HairlineDark
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFB37812),
    onPrimary = Color.White,
    secondary = CinemaColors.CrimsonAction,
    onSecondary = Color.White,
    background = CinemaColors.LightCanvas,
    onBackground = CinemaColors.LightInk,
    surface = CinemaColors.LightSurface,
    onSurface = CinemaColors.LightInk,
    surfaceVariant = Color(0xFFE9ECF2),
    onSurfaceVariant = CinemaColors.LightMuted,
    outline = Color(0xFFD5DAE4)
)

private val CinemaTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)
)

@Composable
fun CinemaTvTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = CinemaTypography,
        content = content
    )
}
