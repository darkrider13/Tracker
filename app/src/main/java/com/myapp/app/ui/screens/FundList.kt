package com.myapp.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.myapp.app.data.AppState
import com.myapp.app.data.AppStore
import com.myapp.app.ui.AppActions
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.FundTypeIcon
import com.myapp.app.ui.components.NoteGlyph
import com.myapp.app.ui.components.PagedViewport
import com.myapp.app.ui.components.pagedItem
import com.myapp.app.ui.components.rememberPagedState
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.ensureFundMeta
import com.myapp.app.util.fmtMoney
import com.myapp.app.util.fundDisplayName
import com.myapp.app.util.fundTint
import com.myapp.app.util.getFundType
import com.myapp.app.util.parseCssColor
import com.myapp.app.util.tr
import kotlinx.coroutines.delay
import kotlin.math.abs
import com.myapp.app.ui.components.SoftGlass

private const val FUNDS_VISIBLE_COUNT = 4
private const val HOLD_MS = 400L

/** Cards are tapped to switch the active fund (the old shiftActiveAssetDirectly). */
private fun switchActiveFund(name: String) {
    AppStore.state.activeFund = name
    AppStore.commit()
}

/**
 * The list of funds inside the expanded Home panel:
 *  - tap a card: make that fund active
 *  - tap the arrow: open its details
 *  - press and hold (0.4 s) then drag up/down: reorder (saved in the order of appState.funds)
 *  - more than 4 funds: scrolls a whole page of 4 at a time
 */
@Composable
fun FundList(state: AppState, open: Boolean = true, clock: State<Float> = remember { mutableStateOf(1f) }) {
    // Same AppState object is passed every time, so Compose would skip this list; reading the revision here makes it redraw right after a commit.
    AppStore.observe()
    val names = state.funds.keys.toList()
    val density = LocalDensity.current
    val paged = rememberPagedState(visible = FUNDS_VISIBLE_COUNT, gap = 8.dp)
    val gapPx = with(density) { 8.dp.toPx() }

    // read lazily by the cards (a State, not a Boolean): opening the panel must not recompose every card
    val liveState = rememberUpdatedState(open)
    var order by remember(names) { mutableStateOf(names) }
    val heightsByName = remember { mutableStateMapOf<String, Int>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var grabOffsetY by remember { mutableFloatStateOf(0f) }   // finger offset inside the card (px)
    var fingerLocalY by remember { mutableFloatStateOf(0f) }  // finger y inside the list viewport (px)
    var viewportH by remember { mutableFloatStateOf(0f) }

    fun heightOf(n: String): Float = (heightsByName[n] ?: 0).toFloat()

    /** Top (px, in scrolled content) of slot [idx] for the current order. */
    fun naturalTop(idx: Int): Float {
        var t = 0f
        for (k in 0 until idx) t += heightOf(order[k]) + gapPx
        return t
    }

    fun desiredTop(): Float = fingerLocalY + paged.scroll.value - grabOffsetY

    /** Moves the dragged card to where its centre now sits among the others (like the old insertBefore). */
    fun reorderForDrag() {
        val dragged = dragging ?: return
        val center = desiredTop() + heightOf(dragged) / 2f
        var insertBefore: String? = null
        for ((k, nm) in order.withIndex()) {
            if (nm == dragged) continue
            val top = naturalTop(k)
            if (center < top + heightOf(nm) / 2f) {
                insertBefore = nm
                break
            }
        }
        val rest = order.filter { it != dragged }
        val newOrder = ArrayList<String>(order.size)
        if (insertBefore == null) {
            newOrder.addAll(rest)
            newOrder.add(dragged)
        } else {
            for (nm in rest) {
                if (nm == insertBefore) newOrder.add(dragged)
                newOrder.add(nm)
            }
        }
        if (newOrder != order) order = newOrder
    }

    fun commitOrder() {
        val oldNames = state.funds.keys.toList()
        if (order.size == oldNames.size && order != oldNames) {
            val copy = LinkedHashMap(state.funds)
            state.funds.clear()
            for (n in order) {
                val v = copy[n]
                if (v != null) state.funds[n] = v
            }
            AppStore.saveState()
        }
        AppStore.refresh()
    }

    // Jump to the page that holds the ACTIVE fund (instantly) when the list opens / the fund changes.
    val ready = names.size <= FUNDS_VISIBLE_COUNT || paged.viewportMax(names.size) != null
    LaunchedEffect(state.activeFund, ready, open) {
        if (ready) {
            val idx = names.indexOf(state.activeFund)
            paged.scroll.scrollTo(if (idx > 0) paged.pageOffsetOfItem(idx) else 0)
        }
    }

    // Auto-scroll while dragging near the top / bottom edge of the list.
    LaunchedEffect(dragging) {
        if (dragging == null) return@LaunchedEffect
        val edge = with(density) { 36.dp.toPx() }
        val step = with(density) { 8.dp.toPx() }
        while (dragging != null) {
            if (paged.scroll.maxValue > 0) {
                if (fingerLocalY < edge) {
                    paged.scroll.scrollBy(-step)
                    reorderForDrag()
                } else if (fingerLocalY > viewportH - edge) {
                    paged.scroll.scrollBy(step)
                    reorderForDrag()
                }
            }
            delay(16)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { viewportH = it.height.toFloat() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val tol = 10.dp.toPx()
                    // which card is under the finger?
                    val contentY = down.position.y + paged.scroll.value
                    var hit = -1
                    var acc = 0f
                    for ((k, nm) in order.withIndex()) {
                        val h = heightOf(nm)
                        if (contentY >= acc && contentY < acc + h) {
                            hit = k
                            break
                        }
                        acc += h + gapPx
                    }
                    if (hit < 0) return@awaitEachGesture

                    // Wait: finger lifts / moves too far = normal tap or scroll; 0.4 s still = start drag.
                    var cancelled = false
                    val finished = withTimeoutOrNull(HOLD_MS) {
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id }
                            if (ch == null || ch.changedToUp() || ch.isConsumed ||
                                abs(ch.position.x - down.position.x) > tol ||
                                abs(ch.position.y - down.position.y) > tol
                            ) {
                                cancelled = true
                                break
                            }
                        }
                        true
                    }
                    if (cancelled || finished != null) return@awaitEachGesture

                    // ---- long press: drag ----
                    val name = order[hit]
                    dragging = name
                    grabOffsetY = contentY - acc
                    fingerLocalY = down.position.y
                    down.consume()
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        ch.consume()
                        if (!ch.pressed) break
                        fingerLocalY = ch.position.y
                        reorderForDrag()
                    }
                    dragging = null
                    commitOrder()
                }
            },
    ) {
        PagedViewport(
            state = paged,
            count = names.size,
            snap = dragging == null,
            animateSize = false,   // opening the funds panel: no height animation, it just appears
            smoothSnap = true,     // same glide-onto-a-whole-card scrolling as the fund history list
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for ((index, name) in order.withIndex()) key(name) {
                    val isDragged = dragging == name
                    // Staggered entrance: every card plays its part of ONE shared clock (see staggeredReveal), so the cards,
                    // the buttons below them and the panel height all finish together. No per-card timers.
                    val cardStart = fundCardStart(index)
                    val cardEnd = cardStart + FUND_CARD_SPAN
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pagedItem(paged, index)
                            .onSizeChanged { heightsByName[name] = it.height }
                            .zIndex(if (isDragged) 50f else 0f)
                            .graphicsLayer {
                                // gentle: a soft fade + a tiny rise (no scaling), one card after another
                                val p = EnterEase.transform(((clock.value - cardStart) / (cardEnd - cardStart)).coerceIn(0f, 1f))
                                alpha = p
                                translationY = (1f - p) * 8.dp.toPx()
                                // one cheap layer: no off-screen buffer per card while it fades
                                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                                if (isDragged) {
                                    translationY += desiredTop() - naturalTop(order.indexOf(name))
                                    alpha = 0.95f * p
                                }
                            },
                    ) {
                        FundCard(
                            state = state,
                            name = name,
                            isActive = state.activeFund == name,
                            live = liveState,
                        )
                    }
                }
            }
        }
    }
}

private val EnterEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** Where on the shared panel clock (0..1) card [i] starts its entrance; the buttons under the cards use the same scale. */
internal fun fundCardStart(i: Int): Float = 0.02f + i.coerceIn(0, 4) * 0.08f
internal const val FUND_CARD_SPAN = 0.5f
private val PulseEase = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)

@Composable
private fun FundCard(state: AppState, name: String, isActive: Boolean, live: State<Boolean>) {
    AppStore.observe()
    val c = AppTheme.colors
    val meta = ensureFundMeta(name)
    val metaColor = parseCssColor(meta.color) ?: Brand.red
    val typeLabel = tr("fund_type_" + meta.type, getFundType(meta.type).label)
    val hasNote = state.fundNotes[name]?.trim()?.isNotEmpty() == true
    val shape = RoundedCornerShape(16.dp)

    val cardModifier = if (isActive) {
        // border-2 in the fund colour + a 5% tint (the tint only exists for real #rrggbb colours)
        val tint = fundTint(meta.color, 0x0D)
        Modifier
            .clip(shape)
            .then(if (tint != null) Modifier.background(tint, shape) else Modifier)
            .border(BorderStroke(2.dp, metaColor), shape)
    } else {
        Modifier
            .clip(shape)
            .background(if (c.isLight) c.bgWhite else SoftGlass.bg, shape)
            .border(BorderStroke(1.dp, if (c.isLight) c.homeCardBorder else SoftGlass.border), shape)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(cardModifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // main area: tap = switch fund
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { switchActiveFund(name) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val iconTint = fundTint(meta.color, 0x22)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(if (iconTint != null) Modifier.background(iconTint, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                FundTypeIcon(meta.type, meta.icon, 14.dp, metaColor)
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AppText(
                        text = fundDisplayName(name),
                        size = 12f,
                        lineHeight = 16f,
                        weight = FontWeight.Bold,
                        color = c.textGray900,
                        singleLineEllipsis = true,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (hasNote) NoteGlyph(8.dp, metaColor)
                }
                AppText(
                    text = typeLabel + " • ৳" + fmtMoney(state.funds[name] ?: 0.0),
                    size = 12f,
                    lineHeight = 16f,
                    weight = FontWeight.Bold,
                    color = metaColor,
                    singleLineEllipsis = true,
                )
            }
            if (isActive) {
                // The dot pulses only while the panel is open, and only after it has finished opening (nothing extra animates
                // during the open motion). Driven by one Animatable read in the draw phase: no recomposition per frame.
                val pulse = remember { Animatable(1f) }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer { alpha = pulse.value }
                        .background(metaColor, CircleShape),
                )
            }
        }

        // arrow: open the fund details
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(c.borderGray100),
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { AppActions.openAccountDetails(name) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            FaIcon("fa-chevron-right", 11.dp, c.textGray400)
        }
    }
}
