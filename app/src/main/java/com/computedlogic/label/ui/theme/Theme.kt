package com.computedlogic.label.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary            = Blue500,
    onPrimary          = Color.White,
    primaryContainer   = Blue50,
    onPrimaryContainer = Blue700,
    secondary          = Gray500,
    onSecondary        = Color.White,
    secondaryContainer = Gray100,
    onSecondaryContainer = Gray800,
    tertiary           = Green500,
    surface            = Color.White,
    onSurface          = Gray800,
    surfaceVariant     = Gray100,
    onSurfaceVariant   = Gray500,
    background         = Gray100,
    onBackground       = Gray800,
    outline            = Gray200,
    outlineVariant     = Gray200,
    error              = Red500,
    onError            = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary            = DarkBlue300,
    onPrimary          = Blue700,
    primaryContainer   = Blue700,
    onPrimaryContainer = DarkBlue200,
    secondary          = Gray400,
    onSecondary        = Gray900,
    secondaryContainer = DarkSurface,
    onSecondaryContainer = Gray200,
    tertiary           = Green500,
    surface            = DarkSurface,
    onSurface          = Gray50,
    surfaceVariant     = Color(0xFF2A2A3C),
    onSurfaceVariant   = Gray400,
    background         = DarkBg,
    onBackground       = Gray50,
    outline            = Color(0xFF3A3A4E),
    outlineVariant     = Color(0xFF2E2E40),
    error              = Red500,
    onError            = Color.White,
)

@Composable
fun LabelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Tint the system bars to match the theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}