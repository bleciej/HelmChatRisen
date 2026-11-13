package com.example.helmChatRisen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MotorcycleColorScheme = darkColorScheme(
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

/**
 * High-contrast motorcycle theme. The app is always dark, independent of the system setting.
 */
@Composable
fun HelmChatRisenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MotorcycleColorScheme,
        typography = Typography,
        content = content
    )
}
