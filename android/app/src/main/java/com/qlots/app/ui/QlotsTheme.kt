package com.qlots.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object QlotsColors {
    val Ink = Color(0xFF17251F)
    val Muted = Color(0xFF728078)
    val Canvas = Color(0xFFF5F6F0)
    val Card = Color(0xFFFFFFFF)
    val Green = Color(0xFF176B4D)
    val Deep = Color(0xFF123D2E)
    val Mint = Color(0xFFDFF1E8)
    val Lime = Color(0xFFC8E8A9)
    val Gold = Color(0xFFF2C66D)
    val Border = Color(0xFFE4E9E2)
    val Red = Color(0xFFA6473C)
    val RedBg = Color(0xFFFCEDE9)
    val Blue = Color(0xFF416F8B)
    val Purple = Color(0xFF9B6B9E)
}

private val QlotsLightScheme = lightColorScheme(
    primary = QlotsColors.Green,
    onPrimary = Color.White,
    secondary = QlotsColors.Mint,
    onSecondary = QlotsColors.Deep,
    background = QlotsColors.Canvas,
    onBackground = QlotsColors.Ink,
    surface = QlotsColors.Card,
    onSurface = QlotsColors.Ink,
    surfaceVariant = Color(0xFFEAF0E9),
    onSurfaceVariant = QlotsColors.Muted,
    outline = QlotsColors.Border,
    error = QlotsColors.Red,
)

@Composable
fun QlotsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = QlotsLightScheme,
        typography = Typography(),
        content = content,
    )
}
