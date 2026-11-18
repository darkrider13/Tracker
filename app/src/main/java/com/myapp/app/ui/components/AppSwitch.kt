package com.myapp.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.myapp.app.ui.theme.AppTheme

private val SpringyEase = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/**
 * The old `.switch` toggle: 40x24 pill, 18 dp knob, green gradient + glow when on,
 * springy knob slide (350 ms), knob stretches to 22 dp while pressed.
 */
@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val knobWidth by animateDpAsState(if (pressed) 22.dp else 18.dp, tween(200), label = "knobW")
    val travel = if (checked) (if (pressed) 12.dp else 16.dp) else 0.dp
    val knobX by animateDpAsState(travel, tween(350, easing = SpringyEase), label = "knobX")
    val offTrack by animateColorAsState(if (c.isLight) Color(0xFFD4D4D8) else Color(0xFF2C2C30), tween(300), label = "track")
    val knobColor = when {
        c.isLight -> Color.White
        checked -> Color.White
        else -> Color(0xFFE4E4E7)
    }
    val trackModifier = if (checked) {
        Modifier.background(
            Brush.linearGradient(listOf(Color(0xFF22C55E), Color(0xFF16A34A))),
            CircleShape,
        )
    } else Modifier.background(offTrack, CircleShape)

    Box(
        modifier = modifier
            .width(40.dp)
            .height(24.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(CircleShape)
            .then(trackModifier)
            .then(
                if (enabled) Modifier.clickable(interactionSource = interaction, indication = null) { onCheckedChange(!checked) }
                else Modifier,
            ),
    ) {
        Box(
            Modifier
                .offset(x = 3.dp + knobX, y = 3.dp)
                .width(knobWidth)
                .size(width = knobWidth, height = 18.dp)
                .shadow(2.dp, CircleShape)
                .background(knobColor, CircleShape),
        )
    }
}
