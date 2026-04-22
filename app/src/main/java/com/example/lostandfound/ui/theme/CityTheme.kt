package com.example.lostandfound.ui.theme

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── City Color Palette ──────────────────────────────────────────────────────
// These are shared across all screens for a consistent city-themed look.

/**
 * Core color palette and styling definitions for the Balik-Calasiao application.
 * Defines the central "CityTheme" object holding consistent UI colors used globally.
 */
object CityTheme {
    val Green      = Color(0xFF2D6A4F)   // Deep forest green  – primary actions
    val GreenLight = Color(0xFF40916C)   // Lighter green      – gradients
    val Gold       = Color(0xFFD4A017)   // Golden yellow      – accents / badges
    val GoldLight  = Color(0xFFF4C430)   // Bright gold        – highlights
    val Ube        = Color(0xFF8D5BA1)   // Ube violet         – heritage/accent
    val UbeLight   = Color(0xFFA67BB8)   // Light ube          – gradients/softness
    val Brown      = Color(0xFF5C3D1E)   // Warm brown         – text / outlines
    val Cream      = Color(0xFFFDF8F0)   // Off-white cream    – backgrounds
    val White      = Color(0xFFFFFFFF)   // Pure white         – card surfaces
    val Error      = Color(0xFFB00020)   // Error red
}

/**
 * Returns a standardized set of colors for OutlinedTextField components
 * to maintain visual consistency across all forms and text inputs in the application.
 */
@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CityTheme.Green,
    unfocusedBorderColor = CityTheme.Brown.copy(alpha = 0.25f),
    focusedLabelColor = CityTheme.Green,
    cursorColor = CityTheme.Green
)
