package com.tallyvox.ui.theme

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
    primary = DarkPrimary,
    onPrimary = DarkText,
    primaryContainer = DarkPrimaryHighlight,
    onPrimaryContainer = DarkPrimary,
    secondary = DarkGold,
    onSecondary = DarkText,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurface2,
    onSurfaceVariant = DarkTextMuted,
    error = DarkError,
    onError = DarkText,
    outline = DarkBorder,
    outlineVariant = DarkDivider
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightBg,
    primaryContainer = LightPrimaryHighlight,
    onPrimaryContainer = LightPrimary,
    secondary = LightGold,
    onSecondary = LightText,
    background = LightBg,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurface2,
    onSurfaceVariant = LightTextMuted,
    error = LightError,
    onError = LightBg,
    outline = LightBorder,
    outlineVariant = LightDivider
)

@Composable
fun TallyVoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
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
        content = content
    )
}
