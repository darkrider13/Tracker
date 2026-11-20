package com.myapp.app.ui.popup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapp.app.data.AppStore
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.FundTypeIcon
import com.myapp.app.ui.components.PagedViewport
import com.myapp.app.ui.components.pagedItem
import com.myapp.app.ui.components.rememberPagedState
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.ensureFundMeta
import com.myapp.app.util.fundDisplayName
import com.myapp.app.util.parseCssColor
import com.myapp.app.util.tr

/** Which fund dropdown is open ("modal", "edit-tx", "transfer-from", ...). Only one at a time. */
object FundDropdownState {
    var openContext by mutableStateOf<String?>(null)

    fun toggle(context: String) {
        openContext = if (openContext == context) null else context
    }

    fun close() {
        openContext = null
    }
}

private class ButtonDims(
    val pad: androidx.compose.ui.unit.Dp,
    val corner: androidx.compose.ui.unit.Dp,
    val badge: androidx.compose.ui.unit.Dp,
    val badgeCorner: androidx.compose.ui.unit.Dp,
    val badgeIcon: androidx.compose.ui.unit.Dp,
    val chevron: androidx.compose.ui.unit.Dp,
    val textSize: Float,
    val textLine: Float,
    val gap: androidx.compose.ui.unit.Dp,
)

/** Size / look of the closed fund button in each popup. */
enum class FundButtonVariant { AddTx, EditTx, Transfer, Debt }

/** The closed-state fund button: coloured icon badge, fund name, chevron. */
@Composable
fun FundSelectButton(
    context: String,
    selectedFund: String,
    modifier: Modifier = Modifier,
    variant: FundButtonVariant = FundButtonVariant.AddTx,
    textColor: Color? = null,
    enabled: Boolean = true,
) {
    val c = AppTheme.colors
    val meta = ensureFundMeta(selectedFund)
    val open = FundDropdownState.openContext == context
    val rotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = tween(150),
        label = "fundChevron",
    )
    val d = when (variant) {
        FundButtonVariant.AddTx -> ButtonDims(10.dp, 12.dp, 20.dp, 8.dp, 9.dp, 10.dp, 12f, 16f, 8.dp)
        FundButtonVariant.EditTx -> ButtonDims(12.dp, 12.dp, 20.dp, 6.dp, 10.dp, 10.dp, 14f, 20f, 8.dp)
        FundButtonVariant.Transfer -> ButtonDims(8.dp, 8.dp, 18.dp, 6.dp, 9.dp, 9.dp, 12f, 16f, 6.dp)
        FundButtonVariant.Debt -> ButtonDims(12.dp, 12.dp, 20.dp, 6.dp, 9.dp, 10.dp, 12f, 16f, 8.dp)
    }
    val pad = d.pad
    val corner = d.corner
    val badge = d.badge
    val badgeCorner = d.badgeCorner
    val badgeIcon = d.badgeIcon
    val chevron = d.chevron
    val textSize = d.textSize
    val textLine = d.textLine
    val gap = d.gap
    val shape = RoundedCornerShape(corner)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .clip(shape)
            .background(c.bgWhite80, shape)
            .border(1.dp, c.borderGray200, shape)
            .then(
                if (enabled) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { FundDropdownState.toggle(context) } else Modifier,
            )
            .padding(pad),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            Box(
                modifier = Modifier
                    .size(badge)
                    .clip(RoundedCornerShape(badgeCorner))
                    .background(parseCssColor(meta.color) ?: Brand.red, RoundedCornerShape(badgeCorner)),
                contentAlignment = Alignment.Center,
            ) {
                FundTypeIcon(meta.type, meta.icon, badgeIcon, Color.White)
            }
            AppText(
                text = fundDisplayName(selectedFund),
                size = textSize,
                lineHeight = textLine,
                weight = FontWeight.Bold,
                color = textColor ?: c.textGray800,
                singleLineEllipsis = true,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        FaIcon(
            "fa-chevron-down", chevron, c.textGray400,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
    }
}

/**
 * The floating fund list that opens over the popup card (blurred card behind it, list centred,
 * 24 px from both card edges). Three fund rows show at a time; scrolling moves one row at a time.
 * Put it in the `overlay` slot of [GlassCard].
 */
@Composable
fun BoxScope.FundDropdownOverlay(
    context: String,
    selectedFund: String,
    excludeFund: String? = null,
    compact: Boolean = false,
    tight: Boolean = false,
    onPick: (String) -> Unit,
) {
    val isOpen = FundDropdownState.openContext == context
    // open: eased glide in (300 ms); close: quicker fade out (180 ms). Kept composed while it animates out.
    val progress by animateFloatAsState(
        targetValue = if (isOpen) 1f else 0f,
        animationSpec = tween(if (isOpen) 300 else 180, easing = DropdownEase),
        label = "fundDropdownProgress",
    )
    if (!isOpen && progress <= 0.001f) return
    val c = AppTheme.colors
    val state = AppStore.observe()
    val funds = state.funds.keys.filter { it != excludeFund }
    val shape = RoundedCornerShape(8.dp)
    val iconSize = if (compact) 15.dp else 19.dp
    val rowPad = if (compact) 4.dp else 6.dp
    val nameSize = if (compact) 9f else 11f
    val activeIconSize = if (compact) 8.dp else 9.dp
    val inactiveIconSize = if (compact) 9.dp else 11.dp
    val checkSize = if (compact) 8.dp else 10.dp

    // dim + blur the card behind the list; tapping it closes the list
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer { alpha = progress }
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { FundDropdownState.close() },
    )

    val rowGap = if (tight) 4.dp else 8.dp
    val shellPad = if (tight) 8.dp else 10.dp
    val paged = rememberPagedState(visible = 3, gap = rowGap, snapStep = 1)
    val selectedIndex = funds.indexOf(selectedFund).coerceAtLeast(0)
    val ready = funds.size <= 3 || paged.viewportMax(funds.size) != null
    // The list stays invisible until every row is measured, the viewport is clamped to 3 rows and the
    // selected page is in place; then it fades in. So it never jumps / half-cuts a row while opening.
    var shown by remember { mutableStateOf(funds.size <= 3) }
    LaunchedEffect(ready, funds.size) {
        if (funds.size <= 3) {
            paged.scroll.scrollTo(0)
            shown = true
        } else if (ready) {
            // wait for the clamped layout (scroll range appears), then jump to the selected page
            kotlinx.coroutines.withTimeoutOrNull(500L) {
                snapshotFlow { paged.scroll.maxValue }.first { it > 0 }
            }
            paged.scroll.scrollTo(paged.offsetOf(selectedIndex))
            shown = true
        }
    }
    val shownAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(120),
        label = "fundListShown",
    )

    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .graphicsLayer {
                alpha = shownAlpha * progress
                val sc = 0.94f + 0.06f * progress
                scaleX = sc
                scaleY = sc
                translationY = (1f - progress) * 14.dp.toPx()
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.35f)
            }
            .clip(shape)
            .background(if (c.isLight) Color(0xFFD4D4D8) else Color(0xFF141414), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { }
            .padding(shellPad),
    ) {
        AppText(
            text = tr("modal_select_fund", "Select Fund"),
            size = 10f,
            weight = FontWeight.Bold,
            letterSpacingEm = 0.05f,
            color = c.textGray400,
            uppercase = true,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
        )
        PagedViewport(state = paged, count = funds.size, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(rowGap),
            ) {
                for ((i, fund) in funds.withIndex()) {
                    val isSelected = fund == selectedFund
                    val meta = ensureFundMeta(fund)
                    val rowShape = RoundedCornerShape(12.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pagedItem(paged, i)
                            .clip(rowShape)
                            .background(
                                if (isSelected) Color(0xFF991B1B).copy(alpha = 0.10f) else c.bgGray200a70,
                                rowShape,
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                onPick(fund)
                                FundDropdownState.close()
                            }
                            .padding(rowPad),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(iconSize)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(parseCssColor(meta.color) ?: Brand.red, RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center,
                                ) { FundTypeIcon(meta.type, meta.icon, activeIconSize, Color.White) }
                            } else {
                                Box(modifier = Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                                    FundTypeIcon(meta.type, meta.icon, inactiveIconSize, c.textGray400)
                                }
                            }
                            AppText(
                                text = fundDisplayName(fund),
                                size = nameSize,
                                weight = FontWeight.Bold,
                                color = if (isSelected) Brand.red else c.textGray800,
                                singleLineEllipsis = true,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        if (isSelected) FaIcon("fa-check", checkSize, Brand.red)
                    }
                }
            }
        }
    }
}
