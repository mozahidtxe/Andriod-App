package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = BrandBlue80,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = BrandBlueGrey80,
    background = DarkReaderBackground,
    surface = DarkReaderSurface,
    onBackground = DarkReaderText,
    onSurface = DarkReaderText,
    surfaceVariant = DarkReaderCard,
    onSurfaceVariant = DarkReaderSecondaryText
)

private val LightColorScheme = lightColorScheme(
    primary = BrandBlueMedium,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = BrandBlueGrey40,
    background = LightReaderBackground,
    surface = LightReaderSurface,
    onBackground = LightReaderText,
    onSurface = LightReaderText,
    surfaceVariant = LightReaderCard,
    onSurfaceVariant = LightReaderSecondaryText
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent stylish blue/indigo palette
    content: @Composable () -> Unit,
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
        typography = Typography,
        content = content
    )
}
