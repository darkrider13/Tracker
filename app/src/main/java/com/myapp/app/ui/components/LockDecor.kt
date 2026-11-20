package com.myapp.app.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand

private class DecorIcon(
    val icon: String,
    val size: Dp,
    val white: Boolean,
    /** distance from the top (true) or bottom (false) edge, as a fraction of the screen height */
    val fromTop: Boolean,
    val v: Float,
    /** distance from the left (true) or right (false) edge, as a fraction of the screen width */
    val fromLeft: Boolean,
    val h: Float,
    val rotation: Float,
)

private val DECOR = listOf(
    DecorIcon("fa-chart-pie", 60.dp, false, true, 0.16f, true, 0.18f, -15f),
    DecorIcon("fa-coins", 36.dp, true, true, 0.16f, false, 0.24f, 12f),
    DecorIcon("fa-wallet", 72.dp, false, true, 0.34f, true, -0.04f, -10f),
    DecorIcon("fa-university", 72.dp, true, true, 0.28f, false, -0.06f, 8f),
    DecorIcon("fa-percentage", 36.dp, false, true, 0.46f, true, 0.14f, -8f),
    DecorIcon("fa-file-invoice-dollar", 48.dp, true, true, 0.50f, false, 0.12f, 14f),
    DecorIcon("fa-hand-holding-usd", 48.dp, false, false, 0.34f, true, 0.30f, -6f),
    DecorIcon("fa-receipt", 48.dp, true, true, 0.62f, false, 0.06f, 16f),
    DecorIcon("fa-calculator", 60.dp, true, false, 0.08f, true, 0.06f, 10f),
    DecorIcon("fa-credit-card", 48.dp, false, false, 0.14f, false, -0.04f, -14f),
    DecorIcon("fa-money-bill-wave", 48.dp, false, false, 0.28f, true, -0.02f, -6f),
    DecorIcon("fa-chart-line", 60.dp, false, false, -0.02f, false, 0.28f, -8f),
    DecorIcon("fa-landmark", 36.dp, true, false, 0.02f, true, 0.40f, 6f),
)

/**
 * The scattered, very faint finance icons behind the Welcome / Lock / PIN / Pattern screens.
 * Dark theme: 20 % (16 % for the white ones). Day mode: 16 %, white ones turned grey.
 */
@Composable
fun FinanceDecor(modifier: Modifier = Modifier, accent: Color = Brand.red) {
    val light = AppTheme.colors.isLight
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val w = maxWidth
        val hh = maxHeight
        for (d in DECOR) {
            val color = when {
                d.white && light -> Color(0xFF9CA3AF)
                d.white -> Color.White
                else -> accent
            }
            val op = if (light) 0.16f else if (d.white) 0.16f else 0.20f
            val x = if (d.fromLeft) w * d.h else w * (1f - d.h) - d.size
            val y = if (d.fromTop) hh * d.v else hh * (1f - d.v) - d.size
            Box(Modifier.offset(x = x, y = y).rotate(d.rotation).alpha(op)) {
                FaIcon(d.icon, d.size, color)
            }
        }
    }
}
