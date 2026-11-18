package com.myapp.app.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.app.ui.AppUi
import com.myapp.app.ui.Tab
import com.myapp.app.ui.theme.AppFont
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.util.tr

private val NavEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

private fun navStyle(sizeSp: Float, weight: FontWeight, lineHeightSp: Float) = TextStyle(
    fontFamily = AppFont,
    fontSize = sizeSp.sp,
    fontWeight = weight,
    color = Color.White,
    lineHeight = lineHeightSp.sp,
    textAlign = TextAlign.Center,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

@Composable
private fun NavPill(
    icon: String,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .clip(shape)
            .then(if (active) Modifier.background(Color.White.copy(alpha = 0.12f), shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaIcon(name = icon, size = 16.dp, color = Color.White)
        if (active) {
            Spacer(Modifier.width(4.dp))
            BasicText(
                text = label,
                style = navStyle(11f, FontWeight.Bold, 16f),
                softWrap = false,
                maxLines = 1,
            )
        }
    }
}

/**
 * The floating bottom bar: 4 tabs (active one shows its name) and the round + button.
 * Draws the dark-mode bottom fade too. Hidden while the funds panel or keyboard is open.
 * Place it inside a full-screen Box and align it to the bottom centre.
 */
@Composable
fun BottomNavBar(modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val hidden = AppUi.bottomNavHidden
    if (hidden) return

    Box(modifier = modifier.fillMaxWidth()) {
        // dark-mode fade so content sinks into the bottom edge (height 2.25rem)
        if (!c.isLight) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Black.copy(alpha = 0f),
                                0.55f to Color.Black.copy(alpha = 0.55f),
                                1.00f to Color.Black,
                            ),
                        ),
                    ),
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val screenW = maxWidth
            val navW = minOf(screenW * 0.92f, 384.dp)
            val gap = (screenW * 0.03f).coerceIn(8.dp, 12.dp)
            Row(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .width(navW)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // the pill bar
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .background(c.navBarBg, CircleShape)
                        .border(1.dp, c.navBarBorder, CircleShape)
                        .padding(horizontal = 3.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val current = AppUi.tab
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        NavPill("fa-home", tr("nav_home", "Home"), current == Tab.Home) { AppUi.switchTab(Tab.Home) }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        NavPill("fa-list", tr("nav_logs", "Logs"), current == Tab.Logs) { AppUi.switchTab(Tab.Logs) }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        NavPill("fa-chart-bar", tr("nav_stats", "Stats"), current == Tab.Stats) { AppUi.switchTab(Tab.Stats) }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        NavPill("fa-user", tr("nav_profile", "Profile"), current == Tab.Profile) { AppUi.switchTab(Tab.Profile) }
                    }
                }

                Spacer(Modifier.width(gap))

                // the round + button (shrinks away on Stats / Profile, like the old scale-0 opacity-0)
                val hide = AppUi.addButtonHidden
                val s = animateFloatAsState(
                    targetValue = if (hide) 0f else 1f,
                    animationSpec = tween(durationMillis = 300, easing = NavEase),
                    label = "addButtonScale",
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = s.value
                            scaleY = s.value
                            alpha = s.value
                        }
                        .clip(CircleShape)
                        .background(Color.Black, CircleShape)
                        .then(
                            if (hide) Modifier else Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { AppUi.handleGlobalAddButton() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = "+",
                        style = navStyle(24f, FontWeight.Bold, 32f),
                    )
                }
            }
        }
    }
}
