package com.journai.journai.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.tooling.preview.Preview

@Immutable
data class JournAIColors(
    val canvas: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val accent: Color,
    val highlight: Color,
)

val LocalJournAIColors = staticCompositionLocalOf {
    JournAIColors(
        canvas = EggshellWhite,
        textPrimary = MutedSage,
        textSecondary = MistyMoss,
        border = PaleSage,
        accent = PastelFern,
        highlight = SoftMint,
    )
}

private val LightBrandColors = JournAIColors(
    canvas = EggshellWhite,
    textPrimary = MutedSage,
    textSecondary = MistyMoss,
    border = PaleSage,
    accent = PastelFern,
    highlight = SoftMint,
)

// Basic dark palette derived from brand (can be tuned later)
private val DarkBrandColors = JournAIColors(
    canvas = Color(0xFF111311),
    textPrimary = Color(0xFFD6E5DB),
    textSecondary = Color(0xFFADC8B4),
    border = Color(0xFF31463A),
    accent = Color(0xFF86B59B),
    highlight = Color(0xFF2A3F32),
)

private fun lightSchemeFromBrand(c: JournAIColors) = lightColorScheme(
    background = c.canvas,
    surface = c.canvas,
    surfaceVariant = c.highlight,
    onSurface = c.textPrimary,
    onSurfaceVariant = c.textSecondary,
    outline = c.border,
    outlineVariant = c.border,
    primary = c.accent,
    onPrimary = Color(0xFF0E1612),
    secondary = c.accent,
    onSecondary = Color(0xFF0E1612),
    primaryContainer = c.highlight,
    onPrimaryContainer = c.textPrimary,
    secondaryContainer = c.highlight,
    onSecondaryContainer = c.textPrimary,
)

private fun darkSchemeFromBrand(c: JournAIColors) = darkColorScheme(
    background = c.canvas,
    surface = c.canvas,
    surfaceVariant = c.highlight,
    onSurface = c.textPrimary,
    onSurfaceVariant = c.textSecondary,
    outline = c.border,
    outlineVariant = c.border,
    primary = c.accent,
    onPrimary = Color(0xFF0A0C0B),
    secondary = c.accent,
    onSecondary = Color(0xFF0A0C0B),
    primaryContainer = c.highlight,
    onPrimaryContainer = c.textPrimary,
    secondaryContainer = c.highlight,
    onSecondaryContainer = c.textPrimary,
)

@Composable
fun JournAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkSchemeFromBrand(DarkBrandColors)
        else -> lightSchemeFromBrand(LightBrandColors)
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalJournAIColors provides if (darkTheme) DarkBrandColors else LightBrandColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// Previews for quick visual validation
@Preview
@Composable
private fun LightThemePreview() {
    JournAITheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {}
    }
}

@Preview
@Composable
private fun DarkThemePreview() {
    JournAITheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {}
    }
}
