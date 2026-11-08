package com.example.helmchatreborn.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val MotorcycleDarkColorScheme = darkColorScheme(
    primary = MotorcyclePrimary,
    onPrimary = MotorcycleOnPrimary,
    secondary = MotorcycleSecondary,
    onSecondary = MotorcycleOnSecondary,
    tertiary = MotorcycleTertiary,
    onTertiary = MotorcycleOnTertiary,
    background = MotorcycleBackground,
    onBackground = HighContrastTextPrimary,
    surface = MotorcycleSurface,
    onSurface = HighContrastTextPrimary,
    surfaceVariant = MotorcycleSurfaceVariant,
    onSurfaceVariant = HighContrastTextSecondary,
    error = MotorcycleError,
    onError = MotorcycleOnError,
    outline = MotorcycleSurfaceBorder
)

private val MotorcycleLightColorScheme = lightColorScheme(
    primary = MotorcyclePrimary,
    onPrimary = MotorcycleOnPrimary,
    secondary = MotorcycleSecondary,
    onSecondary = MotorcycleOnSecondary,
    tertiary = MotorcycleTertiary,
    onTertiary = MotorcycleOnTertiary,
    background = MotorcycleBackground,
    onBackground = HighContrastTextPrimary,
    surface = MotorcycleSurface,
    onSurface = HighContrastTextPrimary,
    surfaceVariant = MotorcycleSurfaceVariant,
    onSurfaceVariant = HighContrastTextSecondary,
    error = MotorcycleError,
    onError = MotorcycleOnError,
    outline = MotorcycleSurfaceBorder
)

@Composable
fun HelmChatRebornTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Allow option for forced high-contrast motorcycle dark theme (ideal for sunlight readability)
    forceHighContrastDark: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        forceHighContrastDark -> MotorcycleDarkColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> MotorcycleDarkColorScheme
        else -> MotorcycleLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
