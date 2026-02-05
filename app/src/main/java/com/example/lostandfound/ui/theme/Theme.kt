package com.example.lostandfound.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.compositionLocalOf

data class ThemeConfig(
    val isDark: Boolean,
    val toggleDark: () -> Unit
)

val LocalThemeConfig = compositionLocalOf {
    ThemeConfig(isDark = false, toggleDark = {})
}

private val DarkColorScheme = darkColorScheme(
    primary = PoliceGold, // Gold pops on dark
    onPrimary = PoliceNavy,
    secondary = PoliceSteel,
    tertiary = PoliceNavy,
    background = PoliceBlack,
    surface = PoliceNavy,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PoliceNavy, // Navy standard
    onPrimary = Color.White,
    secondary = PoliceSteel,
    tertiary = PoliceGold,
    background = PoliceWhite,
    surface = Color.White,
    onSurface = PoliceBlack
)

@Composable
fun LostandfoundTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}