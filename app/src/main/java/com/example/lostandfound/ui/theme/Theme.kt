package com.example.lostandfound.ui.theme


import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PoliceNavy, // Navy standard
    onPrimary = Color.White,
    secondary = PoliceSteel,
    tertiary = PoliceDarkGold, // Darker gold for readability on light backgrounds
    background = PoliceWhite,
    surface = Color.White,
    onSurface = PoliceBlack
)

@Composable
fun LostandfoundTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}