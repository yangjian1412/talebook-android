package com.talebook.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = BlueGrey40,
    tertiary = Teal40,
    background = androidx.compose.ui.graphics.Color(0xFFF5F5F5),
    surface = androidx.compose.ui.graphics.Color.White,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE8EAF6)
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue40,
    secondary = BlueGrey80,
    tertiary = Teal80,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF3C4043)
)

@Composable
fun TaleReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
