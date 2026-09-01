package com.faforever.mobile.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = FafAccent,
    secondary = FafGold,
    background = FafBlueDark,
    surface = FafSurface,
    surfaceVariant = FafSurfaceLight,
    onPrimary = FafBlueDark,
    onSecondary = FafBlueDark,
    onBackground = FafOnSurface,
    onSurface = FafOnSurface,
    error = FafError,
)

private val LightColorScheme = lightColorScheme(
    primary = FafLightPrimary,
    secondary = FafGold,
    background = FafLightSurface,
    surface = FafLightSurface,
    onPrimary = FafLightSurface,
    onBackground = FafLightOnSurface,
    onSurface = FafLightOnSurface,
)

@Composable
fun FafTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
