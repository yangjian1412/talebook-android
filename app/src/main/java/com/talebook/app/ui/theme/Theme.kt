package com.talebook.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
    palette: ReaderThemePalette? = null,
    primaryColor: Color? = null,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val baseScheme = if (palette != null) {
        if (darkTheme) darkColorScheme(
            primary = palette.primary.toColor(),
            onPrimary = androidx.compose.ui.graphics.Color(0xFF18202A),
            secondary = palette.primary.toColor(),
            background = palette.background.toColor(),
            onBackground = palette.text.toColor(),
            surface = palette.surface.toColor(),
            onSurface = palette.text.toColor(),
            surfaceVariant = palette.surface.toColor(),
            onSurfaceVariant = palette.text.toColor().copy(alpha = 0.72f)
        ) else lightColorScheme(
            primary = palette.primary.toColor(),
            onPrimary = androidx.compose.ui.graphics.Color.White,
            secondary = palette.primary.toColor(),
            background = palette.background.toColor(),
            onBackground = palette.text.toColor(),
            surface = palette.surface.toColor(),
            onSurface = palette.text.toColor(),
            surfaceVariant = palette.surface.toColor(),
            onSurfaceVariant = palette.text.toColor().copy(alpha = 0.72f)
        )
    } else when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val colorScheme = primaryColor?.let { baseScheme.copy(primary = it, secondary = it) } ?: baseScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
