package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun buildExpressiveDarkColorScheme(
    accent: AccentPalette,
    isAmoled: Boolean
): ColorScheme {
    val bg = if (isAmoled) AmoledDarkBackground else PixelDarkBackground
    val surf = if (isAmoled) AmoledDarkSurface else PixelDarkSurface
    val surfVar = if (isAmoled) AmoledDarkSurfaceVariant else PixelDarkSurfaceVariant
    val surfCard = if (isAmoled) AmoledDarkSurfaceCard else PixelDarkSurfaceCard

    return darkColorScheme(
        primary = accent.primaryDark,
        onPrimary = Color(0xFF022C22),
        primaryContainer = surfVar,
        onPrimaryContainer = accent.primaryDark,
        secondary = accent.secondary,
        onSecondary = Color(0xFF0F172A),
        secondaryContainer = surfCard,
        onSecondaryContainer = accent.secondary,
        tertiary = accent.tertiary,
        onTertiary = Color.White,
        background = bg,
        onBackground = PixelTextPrimary,
        surface = surf,
        onSurface = PixelTextPrimary,
        surfaceVariant = surfVar,
        onSurfaceVariant = PixelTextSecondary,
        outline = Color(0xFF2E384D),
        error = Color(0xFFF87171),
        onError = Color.White
    )
}

@Composable
fun buildExpressiveLightColorScheme(
    accent: AccentPalette
): ColorScheme {
    return lightColorScheme(
        primary = accent.primaryLight,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE0F2FE),
        onPrimaryContainer = Color(0xFF0369A1),
        secondary = accent.secondary,
        onSecondary = Color.White,
        secondaryContainer = PixelLightSurfaceCard,
        onSecondaryContainer = Color(0xFF1E293B),
        tertiary = accent.tertiary,
        onTertiary = Color.White,
        background = PixelLightBackground,
        onBackground = PixelLightTextPrimary,
        surface = PixelLightSurface,
        onSurface = PixelLightTextPrimary,
        surfaceVariant = PixelLightSurfaceVariant,
        onSurfaceVariant = PixelLightTextSecondary,
        outline = Color(0xFFCBD5E1),
        error = Color(0xFFDC2626),
        onError = Color.White
    )
}

@Composable
fun SMusicTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = true,
    isAmoledBlack: Boolean = false,
    accentPalette: AccentPalette = AccentPalette.EMERALD,
    fontOption: FontOption = FontOption.SF_PRO_DISPLAY,
    content: @Composable () -> Unit,
) {
    val isDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (context as? android.app.Activity)?.window
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDarkTheme
                insetsController.isAppearanceLightNavigationBars = !isDarkTheme
            }
        }
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val baseScheme = if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (isDarkTheme && isAmoledBlack) {
                baseScheme.copy(
                    background = AmoledDarkBackground,
                    surface = AmoledDarkSurface,
                    surfaceVariant = AmoledDarkSurfaceVariant
                )
            } else {
                baseScheme
            }
        }
        isDarkTheme -> buildExpressiveDarkColorScheme(accentPalette, isAmoledBlack)
        else -> buildExpressiveLightColorScheme(accentPalette)
    }

    val typography = getAppTypography(fontOption)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
