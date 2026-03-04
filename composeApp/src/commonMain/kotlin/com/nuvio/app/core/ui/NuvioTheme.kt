package com.nuvio.app.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

private val NuvioDarkColors = darkColorScheme(
    primary = Color(0xFF2E86B8),
    onPrimary = Color(0xFFD2E8F7),
    primaryContainer = Color(0xFF102531),
    onPrimaryContainer = Color(0xFFE2F1FA),
    secondary = Color(0xFF8A929C),
    onSecondary = Color(0xFFEEF1F3),
    background = Color(0xFF020404),
    onBackground = Color(0xFFF5F7F8),
    surface = Color(0xFF0A0D0D),
    onSurface = Color(0xFFF5F7F8),
    surfaceVariant = Color(0xFF121616),
    onSurfaceVariant = Color(0xFF969CA3),
    outline = Color(0xFF252A2A),
    error = Color(0xFFE36A8A),
    onError = Color(0xFFFCE5EC),
)

private val NuvioTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 42.sp,
        lineHeight = 46.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1.2).sp,
    ),
    headlineLarge = TextStyle(
        fontSize = 30.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.8).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleMedium = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp,
    ),
)

@Composable
fun NuvioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = 1f,
        ),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) NuvioDarkColors else NuvioDarkColors,
            typography = NuvioTypography,
            content = content,
        )
    }
}
