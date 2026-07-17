package com.rhymo.music.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val DarkInk = Color(0xFF0B0C0F)
val DarkInkSoft = Color(0xFF15171C)
val DarkPaper = Color(0xFFF4F1E8)
val DarkMuted = Color(0xFFA7A8AD)

val LightCanvas = Color(0xFFF7F5FB)
val LightSurface = Color(0xFFFFFFFF)
val LightText = Color(0xFF18151D)
val LightMuted = Color(0xFF696370)

val Lime = Color(0xFFD8FF5F)
val Coral = Color(0xFFFF6B5E)
val Violet = Color(0xFF8E78FF)
val NeonBlue = Color(0xFF43E7FF)
val HotPink = Color(0xFFFF4FD8)
val Tangerine = Color(0xFFFFA14A)

data class RhymoSemanticColors(
    val canvas: Color,
    val surface: Color,
    val text: Color,
    val muted: Color
)

val LocalRhymoColors = staticCompositionLocalOf {
    RhymoSemanticColors(DarkInk, DarkInkSoft, DarkPaper, DarkMuted)
}

val Ink: Color @Composable get() = LocalRhymoColors.current.canvas
val InkSoft: Color @Composable get() = LocalRhymoColors.current.surface
val Paper: Color @Composable get() = LocalRhymoColors.current.text
val Muted: Color @Composable get() = LocalRhymoColors.current.muted
