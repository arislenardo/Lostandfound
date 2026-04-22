package com.example.lostandfound.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Defines custom SVG vector graphics mapped to Compose ImageVectors.
 * Provides unique icons (like Glasses and Hat) that are not available in the default Material Icons set.
 */
object CustomIcons {
    val Glasses: ImageVector
        get() = ImageVector.Builder(
            name = "Glasses",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            // Left lens
            moveTo(7f, 9f)
            curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f)
            reflectiveCurveToRelative(1.34f, 3f, 3f, 3f)
            reflectiveCurveToRelative(3f, -1.34f, 3f, -3f)
            reflectiveCurveToRelative(-1.34f, -3f, -3f, -3f)
            close()
            // Right lens
            moveTo(17f, 9f)
            curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f)
            reflectiveCurveToRelative(1.34f, 3f, 3f, 3f)
            reflectiveCurveToRelative(3f, -1.34f, 3f, -3f)
            reflectiveCurveToRelative(-1.34f, -3f, -3f, -3f)
            close()
            // Bridge
            moveTo(10f, 12f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(-1f)
            horizontalLineToRelative(-4f)
            verticalLineToRelative(1f)
            close()
            // Ear pieces
            moveTo(4f, 12f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-1f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(1f)
            close()
            moveTo(22f, 12f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-1f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(1f)
            close()
        }.build()

    val Hat: ImageVector
        get() = ImageVector.Builder(
            name = "Hat",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            // Brim
            moveTo(21f, 17f)
            horizontalLineToRelative(-2.2f)
            lineToRelative(-1f, -4f)
            horizontalLineTo(6.2f)
            lineToRelative(-1f, 4f)
            horizontalLineTo(3f)
            curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
            reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
            horizontalLineToRelative(18f)
            curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
            reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
            close()
            // Crown
            moveTo(6.5f, 12f)
            horizontalLineToRelative(11f)
            lineToRelative(-1f, -5f)
            curveToRelative(-0.5f, -2f, -2f, -3.5f, -4f, -3.5f)
            reflectiveCurveToRelative(-3.5f, 1.5f, -4f, 3.5f)
            lineToRelative(-1f, 5f)
            close()
            // Band
            moveTo(6.2f, 13f)
            horizontalLineToRelative(11.6f)
            verticalLineToRelative(1f)
            horizontalLineTo(6.2f)
            verticalLineToRelative(-1f)
            close()
        }.build()
}
