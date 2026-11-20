package com.myapp.app.ui.popup

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.myapp.app.data.AppStore
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.CategoryIcon
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.appTextStyle
import androidx.compose.foundation.text.BasicText
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.CategoryUi
import com.myapp.app.util.QUICK_CATEGORIES
import com.myapp.app.util.categoryDisplayName
import com.myapp.app.util.resolveCanonicalCategory
import com.myapp.app.util.syncCustomCategories
import com.myapp.app.util.tr
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the category chips look like right now. The old code re-drew the row after every action
 * and then highlighted a chip, which gave these exact looks:
 *  - just opened (cleared): every circle 48 px, grey
 *  - after a pick / while typing: circles 40 px, only the matching one 48 px and red
 */
class CategoryPickerState {
    var allLarge by mutableStateOf(true)
    var highlighted by mutableStateOf<String?>(null)
    var filterText by mutableStateOf("")
    var resetScrollTick by mutableIntStateOf(0)
    var revealTick by mutableIntStateOf(0)

    /** Modal just opened (openCustomSpendModal): render, clear all, highlight the preset. */
    fun onOpen(presetCategory: String?) {
        allLarge = true
        filterText = ""
        highlighted = null
        resetScrollTick++
        val canonical = if (presetCategory != null) resolveCanonicalCategory(presetCategory) else null
        if (canonical != null) {
            highlighted = canonical
            revealTick++
        }
    }

    /** A chip was tapped (selectQuickCategory). */
    fun onSelect(name: String) {
        allLarge = false
        filterText = ""
        highlighted = name
        revealTick++   // keep the row where it is, only bring the picked chip into view
    }

    /** The category text box changed (handleCategoryTyped). */
    fun onTyped(typedRaw: String) {
        val typed = typedRaw.trim()
        if (typed.isEmpty()) {
            allLarge = false
            filterText = ""
            highlighted = null
            resetScrollTick++
            return
        }
        allLarge = false
        filterText = typed
        highlighted = resolveCanonicalCategory(typed)
    }
}

@Composable
private fun Chip(
    name: String,
    big: Boolean,
    selected: Boolean,
    highlightLen: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    val display = categoryDisplayName(name)
    val labelSize = if (name == "Fun") 8f else 10f
    // The circle grows / shrinks and changes colour smoothly. The slot is always 48 dp tall, so
    // the row never jumps when the selection moves from one chip to another.
    val circle by animateDpAsState(if (big) 48.dp else 40.dp, tween(200, easing = FastOutSlowInEasing), label = "chipSize")
    val circleBg by animateColorAsState(if (selected) Brand.green else c.bgGray200a70, tween(220), label = "chipBg")
    val iconColor by animateColorAsState(if (selected) Color.White else c.textGray500, tween(220), label = "chipIcon")
    val baseColor by animateColorAsState(if (selected) Brand.green else c.textGray700, tween(220), label = "chipLabel")
    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(circle)
                    .clip(CircleShape)
                    .background(circleBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CategoryIcon(name, 12.dp, iconColor)
            }
        }
        if (highlightLen > 0) {
            val matchLen = minOf(highlightLen, display.length)
            val text = buildAnnotatedString {
                withStyle(SpanStyle(color = Brand.green)) { append(display.substring(0, matchLen)) }
                append(display.substring(matchLen))
            }
            BasicText(
                text = text,
                style = appTextStyle(labelSize, baseColor, FontWeight.Bold, align = TextAlign.Center),
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            AppText(
                text = display,
                size = labelSize,
                weight = FontWeight.Bold,
                color = baseColor,
                align = TextAlign.Center,
                singleLineEllipsis = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The dashed "+ New" chip that always ends the row. */
@Composable
private fun PlusChip(big: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    // same circle size as the category chips (48 dp when they are enlarged, else 40 dp) so the row stays aligned
    val circle by animateDpAsState(if (big) 48.dp else 40.dp, tween(200, easing = FastOutSlowInEasing), label = "plusSize")
    val dash = Color(0xFF9CA3AF).copy(alpha = 0.6f)
    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(circle)
                    .drawBehind {
                        drawCircle(
                            color = dash,
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                            ),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                FaIcon("fa-plus", 12.dp, c.textGray500)
            }
        }
        AppText(
            text = tr("cat_add_short", "New"),
            size = 10f,
            weight = FontWeight.Bold,
            color = c.textGray500,
            align = TextAlign.Center,
            singleLineEllipsis = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The category chip area of Add / Edit Transaction.
 *  - normal: one row, exactly 5 chips fit the width, swipe for the rest, press-and-hold a chip
 *    for 0.4 s then drag sideways to re-order (saved), "+ New" always last
 *  - typing: only the categories that start with the typed text, matched part in red
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChips(
    picker: CategoryPickerState,
    onPick: (String) -> Unit,
    onAddNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val revision = CategoryUi.revision
    val filter = picker.filterText

    if (filter.isNotEmpty()) {
        val lower = filter.lowercase()
        val matches = QUICK_CATEGORIES.filter { categoryDisplayName(it).lowercase().startsWith(lower) }
        if (matches.isEmpty()) {
            AppText(
                text = tr("modal_no_category_match", "No matching category"),
                size = 10f,
                weight = FontWeight.Bold,
                color = AppTheme.colors.textGray400,
                align = TextAlign.Center,
                modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        } else {
            FlowRow(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (name in matches) {
                    val sel = picker.highlighted == name
                    Chip(
                        name = name,
                        big = sel,
                        selected = sel,
                        highlightLen = filter.length,
                        modifier = Modifier,
                    ) { onPick(name) }
                }
            }
        }
        return
    }

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scroll = rememberScrollState()
    var order by remember(revision) { mutableStateOf(QUICK_CATEGORIES.toList()) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var fingerX by remember { mutableFloatStateOf(0f) }
    var viewportW by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val chipW = (maxWidth - 40.dp) / 5f
        val gap = 10.dp
        val chipWPx = with(density) { chipW.toPx() }
        val gapPx = with(density) { gap.toPx() }
        val stepPx = chipWPx + gapPx
        viewportW = with(density) { maxWidth.toPx() }
        val count = order.size + 1   // + the "New" chip

        // back to the first chip after every re-draw (renderCategorySlider sets scrollLeft = 0)
        LaunchedEffect(picker.resetScrollTick) { scroll.scrollTo(0) }
        // bring a preset category into view (scrollIntoView nearest)
        LaunchedEffect(picker.revealTick) {
            val h = picker.highlighted ?: return@LaunchedEffect
            val idx = order.indexOf(h)
            if (idx >= 0) {
                val left = idx * stepPx
                val right = left + chipWPx
                if (left < scroll.value) scroll.animateScrollTo(left.roundToInt())
                else if (right > scroll.value + viewportW) scroll.animateScrollTo((right - viewportW).roundToInt())
            }
        }
        // settle on a whole chip after every scroll (scroll-snap)
        LaunchedEffect(count) {
            snapshotFlow { scroll.isScrollInProgress }.collect { moving ->
                if (!moving && dragging == null) {
                    val n = (scroll.value / stepPx).roundToInt()
                    val target = minOf((n * stepPx).roundToInt(), scroll.maxValue)
                    if (abs(target - scroll.value) > 1) scroll.animateScrollTo(target)
                }
            }
        }

        fun fingerContentX(): Float = fingerX + scroll.value

        fun swapFor(nameDragged: String) {
            val fx = fingerContentX()
            val di = order.indexOf(nameDragged)
            for ((i, nm) in order.withIndex()) {
                if (nm == nameDragged) continue
                val left = i * stepPx
                if (fx >= left && fx <= left + chipWPx) {
                    val rest = order.filter { it != nameDragged }.toMutableList()
                    rest.add(i, nameDragged)
                    if (di != i) order = rest
                    break
                }
            }
        }

        fun finishDrag() {
            dragging = null
            val names = order
            if (names.isNotEmpty()) {
                AppStore.state.categoryOrder = names.toMutableList()
                AppStore.saveState()
                syncCustomCategories()
            }
        }

        LaunchedEffect(dragging) {
            if (dragging == null) return@LaunchedEffect
            val edge = with(density) { 36.dp.toPx() }
            val step = with(density) { 7.dp.toPx() }
            while (dragging != null) {
                if (fingerX < edge) scroll.scrollBy(-step)
                else if (fingerX > viewportW - edge) scroll.scrollBy(step)
                val d = dragging
                if (d != null) swapFor(d)
                delay(16)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val tol = 8.dp.toPx()
                        val contentX = down.position.x + scroll.value
                        val hit = (contentX / stepPx).toInt()
                        val inChip = contentX - hit * stepPx <= chipWPx
                        if (!inChip || hit < 0 || hit >= order.size) return@awaitEachGesture

                        var cancelled = false
                        val finished = withTimeoutOrNull(380L) {
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

                        dragging = order[hit]
                        fingerX = down.position.x
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        down.consume()
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            ch.consume()
                            if (!ch.pressed) break
                            fingerX = ch.position.x
                        }
                        finishDrag()
                    }
                }
                .horizontalScroll(scroll)
                .padding(vertical = 8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (name in order) {
                    val sel = picker.highlighted == name
                    val isDragged = dragging == name
                    Chip(
                        name = name,
                        big = sel || picker.allLarge,
                        selected = sel,
                        highlightLen = 0,
                        modifier = Modifier
                            .width(chipW)
                            .zIndex(if (isDragged) 5f else 0f)
                            .graphicsLayer {
                                if (isDragged) {
                                    val naturalCenter = order.indexOf(name) * stepPx + chipWPx / 2f
                                    translationX = fingerContentX() - naturalCenter
                                    scaleX = 1.08f
                                    scaleY = 1.08f
                                    alpha = 0.95f
                                }
                            },
                    ) { onPick(name) }
                }
                PlusChip(big = picker.allLarge, modifier = Modifier.width(chipW)) { onAddNew() }
            }
        }
    }
}
