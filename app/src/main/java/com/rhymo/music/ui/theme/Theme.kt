package com.rhymo.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val ColorLightPrimary = androidx.compose.ui.graphics.Color(0xFF6852D9)
private val ColorLightSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFECE8F2)
private val ColorLightOutline = androidx.compose.ui.graphics.Color(0xFF817A88)

private val DarkColorScheme = darkColorScheme(
    primary = Lime, onPrimary = DarkInk,
    secondary = Coral, tertiary = Violet,
    background = DarkInk, onBackground = DarkPaper,
    surface = DarkInkSoft, onSurface = DarkPaper,
    onSurfaceVariant = DarkMuted
)

private val LightColorScheme = lightColorScheme(
    primary = ColorLightPrimary, onPrimary = DarkPaper,
    secondary = Coral, tertiary = Violet,
    background = LightCanvas, onBackground = LightText,
    surface = LightSurface, onSurface = LightText,
    surfaceVariant = ColorLightSurfaceVariant,
    onSurfaceVariant = LightMuted,
    outline = ColorLightOutline
)

@Composable
fun RhymoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val semantic = if (dark) {
        RhymoSemanticColors(DarkInk, DarkInkSoft, DarkPaper, DarkMuted)
    } else {
        RhymoSemanticColors(LightCanvas, LightSurface, LightText, LightMuted)
    }
    CompositionLocalProvider(LocalRhymoColors provides semantic) {
        MaterialTheme(
            colorScheme = if (dark) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content
        )
    }
}
