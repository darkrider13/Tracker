package com.myapp.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.hypot
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand

/**
 * The full-screen page background that sat behind every screen in the old app (body::before):
 *  - dark : pure black with a soft white radial glow in the middle
 *  - light: #E6EEF5 with three soft glows (teal / violet / rose), same as TaskFlow
 */
@Composable
fun AppBackdrop(modifier: Modifier = Modifier) {
    val isLight = AppTheme.colors.isLight
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (!isLight) {
            drawRect(color = Color(0xFF000000))
            // softer + fainter glow: ellipse 110% x 48%, .065 -> .04 (22%) -> .02 (50%) -> .006 (78%) -> 0
            val rx = w * 1.10f
            val ry = h * 0.48f
            val center = Offset(w / 2f, h / 2f)
            scale(scaleX = 1f, scaleY = ry / rx, pivot = center) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to Color.White.copy(alpha = 0.065f),
                            0.22f to Color.White.copy(alpha = 0.040f),
                            0.50f to Color.White.copy(alpha = 0.020f),
                            0.78f to Color.White.copy(alpha = 0.006f),
                            1.00f to Color.White.copy(alpha = 0.0f),
                        ),
                        center = center,
                        radius = rx,
                    ),
                    radius = rx,
                    center = center,
                )
            }
        } else {
            // Same day-mode background as TaskFlow: #E6EEF5 base + three soft glows
            // (teal top-left, violet right, rose bottom-left).
            drawRect(color = Color(0xFFE6EEF5))
            fun glow(cx: Float, cy: Float, c: Color, frac: Float) {
                val far = maxOf(
                    maxOf(hypot(cx, cy), hypot(w - cx, cy)),
                    maxOf(hypot(cx, h - cy), hypot(w - cx, h - cy)),
                )
                val r = far * frac
                drawCircle(
                    brush = Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), center = Offset(cx, cy), radius = r),
                    radius = r,
                    center = Offset(cx, cy),
                )
            }
            glow(w * 0.12f, h * 0.06f, Color(94 / 255f, 234 / 255f, 212 / 255f, 0.55f), 0.42f)
            glow(w * 0.92f, h * 0.28f, Color(167 / 255f, 139 / 255f, 250 / 255f, 0.45f), 0.45f)
            glow(w * 0.18f, h * 0.96f, Color(251 / 255f, 146 / 255f, 160 / 255f, 0.40f), 0.45f)
        }
    }
}
