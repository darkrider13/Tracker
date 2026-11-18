package com.myapp.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand

/**
 * The full-screen page background that sat behind every screen in the old app (body::before):
 *  - dark : pure black with a soft white radial glow in the middle
 *  - light: #f9fafb fading to a faint brand-red tint at the bottom, with a soft white glow
 */
@Composable
fun AppBackdrop(modifier: Modifier = Modifier) {
    val isLight = AppTheme.colors.isLight
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (!isLight) {
            drawRect(color = Color(0xFF000000))
            // radial-gradient(ellipse 110% 42% at 50% 50%, .11 -> .04 (50%) -> 0)
            val rx = w * 1.10f
            val ry = h * 0.42f
            val center = Offset(w / 2f, h / 2f)
            scale(scaleX = 1f, scaleY = ry / rx, pivot = center) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color.White.copy(alpha = 0.11f),
                            0.5f to Color.White.copy(alpha = 0.04f),
                            1.0f to Color.White.copy(alpha = 0.0f),
                        ),
                        center = center,
                        radius = rx,
                    ),
                    radius = rx,
                    center = center,
                )
            }
        } else {
            drawRect(color = Color(0xFFF9FAFB))
            // linear-gradient(to bottom, #f9fafb 0%, #f9fafb 30%, brand-red .12 100%)
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFFF9FAFB),
                        0.3f to Color(0xFFF9FAFB),
                        1.0f to Brand.red.copy(alpha = 0.12f),
                    ),
                ),
            )
            // radial-gradient(ellipse 130% 55% at 50% 100%, white .5 -> .18 (45%) -> 0 (75%))
            val rx = w * 1.30f
            val ry = h * 0.55f
            val center = Offset(w / 2f, h)
            scale(scaleX = 1f, scaleY = ry / rx, pivot = center) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to Color.White.copy(alpha = 0.50f),
                            0.45f to Color.White.copy(alpha = 0.18f),
                            0.75f to Color.White.copy(alpha = 0.0f),
                            1.00f to Color.White.copy(alpha = 0.0f),
                        ),
                        center = center,
                        radius = rx,
                    ),
                    radius = rx,
                    center = center,
                )
            }
        }
    }
}
