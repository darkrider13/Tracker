package com.myapp.app.ui.popup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import com.myapp.app.data.AppStore
import com.myapp.app.ui.AppActions
import com.myapp.app.ui.AppUi
import com.myapp.app.ui.Tab
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.CategoryIcon
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.FaStyle
import com.myapp.app.ui.components.PagedViewport
import com.myapp.app.ui.components.WheelItem
import com.myapp.app.ui.components.WheelTarget
import com.myapp.app.ui.components.WheelPicker
import com.myapp.app.ui.components.pagedItem
import com.myapp.app.ui.components.rememberPagedState
import com.myapp.app.ui.screens.StatsMonth
import com.myapp.app.ui.screens.allCatMonthLabel
import com.myapp.app.ui.screens.statsMonthSpend
import com.myapp.app.ui.screens.statsSpendMonthScope
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.BN_MONTHS
import com.myapp.app.util.fmtMoney
import com.myapp.app.util.getLang
import com.myapp.app.util.groupByCategory
import com.myapp.app.util.localizeDigits
import com.myapp.app.util.tr
import com.myapp.app.util.trf
import java.util.Calendar

private val MONTHS_SHORT = listOf("Jan.", "Feb.", "Mar.", "Apr.", "May", "Jun.", "Jul.", "Aug.", "Sep.", "Oct.", "Nov.", "Dec.")

// =============================================================================================
// All Categories Summary (bottom sheet)
// =============================================================================================

object AllCategoriesSheet {
    /** true = the sheet vanishes in the same frame (cross icon); false = it glides down (back / tap outside). */
    internal var skipCloseAnim by mutableStateOf(false)

    fun open() {
        skipCloseAnim = false
        Popups.allCategories = true
    }

    fun close(instant: Boolean = false) {
        AllCatMonthPicker.close()
        skipCloseAnim = instant
        Popups.allCategories = false
    }
}

/** Height of one category row (12 + 36 + 8 + 6 + 12 dp): the placeholder must match it, or the sheet jumps when the rows arrive. */
private const val ALL_CAT_ROW_DP = 74

@Composable
fun AllCategoriesSheetOverlay() {
    val open = Popups.allCategories
    // The sheet glides up together with the dim + blur behind it (same curve), and glides down on close.
    // One Animatable read only in the draw / layer phase: the sheet is not recomposed on every frame of the slide.
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    var mounted by remember { mutableStateOf(false) }
    // the rows are built only once the slide-in has fully finished, so the slide runs on light frames
    var rowsReady by remember { mutableStateOf(false) }
    val closeNow = !open && AllCategoriesSheet.skipCloseAnim
    LaunchedEffect(open) {
        if (open) {
            mounted = true
            // let the sheet be composed + measured at progress 0 first (the heaviest frame), so that frame
            // is not counted as animation time and the slide does not start with a jump
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            progress.animateTo(1f, androidx.compose.animation.core.tween(SHEET_OPEN_MS, easing = SheetEase))
            rowsReady = true
        } else if (mounted) {
            if (AllCategoriesSheet.skipCloseAnim) progress.snapTo(0f)
            else progress.animateTo(0f, androidx.compose.animation.core.tween(SHEET_CLOSE_MS, easing = SheetCloseEase))
            mounted = false
            rowsReady = false
            AllCategoriesSheet.skipCloseAnim = false
        }
    }
    // cross icon: leave composition in the very frame of the tap, no slide-out
    if (!mounted || closeNow) return
    val c = AppTheme.colors
    val state = AppStore.observe()
    BackHandler(enabled = open && !Popups.allCatMonthPicker) { AllCategoriesSheet.close() }

    val scope = statsSpendMonthScope(state)
    val filtered = statsMonthSpend(state, scope)
    val groups = groupByCategory(filtered)
    val totalSpent = filtered.sumOf { it.amount }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.45f * progress.value)) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { AllCategoriesSheet.close() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.88f)
                .graphicsLayer { translationY = (1f - progress.value) * size.height }
                .clip(sheetShape)
                .background(if (c.isLight) Color(0xFFF3F4F6) else Color(0xFF131315), sheetShape)
                // taps on the sheet itself must not close it
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 28.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    AppText(
                        text = tr("stats_all_categories", "All Categories Summary"), size = 16f, lineHeight = 24f,
                        weight = FontWeight.ExtraBold, color = c.textGray900,
                    )
                    AppText(
                        text = tr("stats_total_spent", "Total Spent") + ": ৳" + fmtMoney(Math.round(totalSpent).toDouble(), 0) +
                            " · " + localizeDigits(groups.size.toString()) + " " + tr("stats_categories_used", "Categories Used"),
                        size = 10f, lineHeight = 15f, weight = FontWeight.Bold, color = c.textGray400,
                    )
                    AppText(
                        text = allCatMonthLabel(scope), size = 10f, lineHeight = 15f, weight = FontWeight.ExtraBold,
                        color = Brand.red, modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(c.bgWhite80, CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { AllCategoriesSheet.close(instant = true) },
                    contentAlignment = Alignment.Center,
                ) { FaIcon("fa-xmark", 14.dp, c.textGray500) }
            }

            if (groups.isEmpty()) {
                val shape = RoundedCornerShape(16.dp)
                Box(Modifier.fillMaxWidth().clip(shape).background(c.bgWhite70, shape).padding(16.dp), contentAlignment = Alignment.Center) {
                    AppText(
                        text = tr("stats_no_spending_data", "No spending statistics available for this asset."),
                        size = 11f, lineHeight = 16.5f, weight = FontWeight.Bold, color = c.textGray500, align = TextAlign.Center,
                    )
                }
            } else if (!rowsReady) {
                // about the final size while the sheet slides in; the rows follow one after another
                val n = minOf(groups.size, 3)
                Spacer(Modifier.fillMaxWidth().height(ALL_CAT_ROW_DP.dp * n + 8.dp * (n - 1)))
            } else {
                // exactly 3 categories visible; one swipe moves one page
                val paged = rememberPagedState(visible = 3, gap = 8.dp)
                PagedViewport(state = paged, count = groups.size, animateSize = false, defaultItemDp = ALL_CAT_ROW_DP.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        groups.forEachIndexed { i, g ->
                            key(g.label) {
                                val pct = if (totalSpent > 0) g.total / totalSpent * 100 else 0.0
                                val rowShape = RoundedCornerShape(16.dp)
                                // staggered entrance (same motion as the fund list): each row rises, fades in and settles in turn
                                val enter = remember { androidx.compose.animation.core.Animatable(0f) }
                                LaunchedEffect(Unit) {
                                    kotlinx.coroutines.delay(20L + i.coerceAtMost(6) * 60L)
                                    enter.animateTo(1f, androidx.compose.animation.core.tween(460, easing = CatEnterEase))
                                }
                                Column(
                                    Modifier.fillMaxWidth().pagedItem(paged, i)
                                        .graphicsLayer {
                                            val p = enter.value
                                            alpha = p
                                            translationY = (1f - p) * 18.dp.toPx()
                                            val sc = 0.94f + 0.06f * p
                                            scaleX = sc
                                            scaleY = sc
                                        }
                                        .clip(rowShape).background(c.bgWhite70, rowShape).padding(12.dp),
                                ) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            val iconShape = RoundedCornerShape(12.dp)
                                            Box(Modifier.size(36.dp).clip(iconShape).background(c.bgGray100, iconShape), contentAlignment = Alignment.Center) {
                                                CategoryIcon(g.label, 14.dp, Brand.red)
                                            }
                                            Column(Modifier.weight(1f)) {
                                                AppText(text = g.label, size = 12f, lineHeight = 16f, weight = FontWeight.Bold, color = c.textGray900, singleLineEllipsis = true)
                                                AppText(
                                                    text = trf("stats_tx_count_suffix", "${g.count} transactions", "count" to localizeDigits(g.count.toString())) +
                                                        " · " + localizeDigits("%.1f".format(pct)) + "%",
                                                    size = 10f, lineHeight = 15f, weight = FontWeight.Bold, color = c.textGray400,
                                                )
                                            }
                                        }
                                        AppText(
                                            text = "৳" + fmtMoney(Math.round(g.total).toDouble(), 0), size = 14f, lineHeight = 20f,
                                            weight = FontWeight.ExtraBold, color = if (i == 0) Brand.red else c.textGray900,
                                            noWrap = true, modifier = Modifier.padding(start = 8.dp),
                                        )
                                    }
                                    Box(
                                        Modifier.padding(top = 8.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(c.bgGray300a60, CircleShape),
                                    ) {
                                        Box(
                                            Modifier.fillMaxHeight().fillMaxWidth(fraction = (pct / 100.0).toFloat().coerceIn(0f, 1f))
                                                .clip(CircleShape).background(Brand.red, CircleShape),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================================
// Month picker of the Stats header (Month + Year wheels)
// =============================================================================================

object AllCatMonthPicker {
    var month by mutableIntStateOf(1)
    var year by mutableIntStateOf(2026)
    var openCount by mutableIntStateOf(0)
    var monthTarget by mutableStateOf<WheelTarget?>(null)
    var yearTarget by mutableStateOf<WheelTarget?>(null)
    private var resetNonce = 0

    fun open() {
        monthTarget = null
        yearTarget = null
        val cur = statsSpendMonthScope(AppStore.state)
        month = cur.monthIdx + 1
        year = cur.year
        openCount++
        Popups.allCatMonthPicker = true
    }

    fun close() { Popups.allCatMonthPicker = false }

    /** resetAllCatMonthPicker(): drop the picked month, back to the default scope. */
    fun reset() {
        // the popup stays open: the filter is dropped and both wheels glide to the present month / year
        AppUi.statsMonthOverride = null
        val cur = statsSpendMonthScope(AppStore.state)
        month = cur.monthIdx + 1
        year = cur.year
        resetNonce++
        monthTarget = WheelTarget(month.toString(), resetNonce)
        yearTarget = WheelTarget(year.toString(), resetNonce)
    }

    fun confirm() {
        close()
        AppUi.statsMonthOverride = StatsMonth(year, month - 1)
    }
}

@Composable
fun AllCatMonthPickerPopup() {
    val c = AppTheme.colors
    PopupLayer(visible = Popups.allCatMonthPicker, scrim = 0.6f) {
        val p = AllCatMonthPicker
        val now = Calendar.getInstance()
        val cur = statsSpendMonthScope(AppStore.state)
        val startYear = minOf(pickerMinYear("transactions"), cur.year)
        val endYear = maxOf(now.get(Calendar.YEAR), cur.year)
        val monthItems = remember(p.openCount) {
            (1..12).map { WheelItem(it.toString(), if (getLang() == "bn") BN_MONTHS[it - 1] else MONTHS_SHORT[it - 1]) }
        }
        val yearItems = remember(p.openCount) {
            (startYear..endYear).map { WheelItem(it.toString(), localizeDigits(it.toString())) }
        }
        GlassCard(radius = 28.dp, padding = 24.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val boxShape = RoundedCornerShape(12.dp)
                    Box(Modifier.size(32.dp).clip(boxShape).background(c.bgGray200a50, boxShape), contentAlignment = Alignment.Center) {
                        FaIcon("fa-calendar-alt", 12.dp, Brand.red, style = FaStyle.Regular)
                    }
                    AppText(
                        text = tr("fund_history_select_month", "Select a Month"), size = 18f, lineHeight = 28f,
                        weight = FontWeight.ExtraBold, letterSpacingEm = -0.025f, color = c.textGray900,
                    )
                }
                Column {
                    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.Center) {
                        Box(Modifier.width(78.dp), contentAlignment = Alignment.Center) {
                            AppText(
                                text = tr("fund_history_month_label", "Month"), size = 12f, lineHeight = 16f,
                                weight = FontWeight.Bold, color = c.textGray400, uppercase = true, align = TextAlign.Center,
                            )
                        }
                        Box(Modifier.padding(start = 4.dp).width(72.dp), contentAlignment = Alignment.Center) {
                            AppText(
                                text = tr("fund_history_year_label", "Year"), size = 12f, lineHeight = 16f,
                                weight = FontWeight.Bold, color = c.textGray400, uppercase = true, align = TextAlign.Center,
                            )
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        val bandShape = RoundedCornerShape(16.dp)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .background(Color(0xFF991B1B).copy(alpha = 0.10f), bandShape)
                                .border(1.dp, Color(0xFF991B1B).copy(alpha = 0.30f), bandShape),
                        )
                        Row(horizontalArrangement = Arrangement.Center) {
                            WheelPicker(
                                items = monthItems, initialValue = p.month.toString(), loop = true, width = 78.dp,
                                resetKey = p.openCount, animateTo = p.monthTarget,
                            ) { v -> p.month = v.toInt() }
                            Box(Modifier.width(4.dp))
                            WheelPicker(
                                items = yearItems, initialValue = p.year.toString(), loop = false, width = 72.dp,
                                resetKey = p.openCount, animateTo = p.yearTarget,
                            ) { v -> p.year = v.toInt() }
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(visible = AppUi.statsMonthOverride != null) {
                    ModalButton(
                        tr("filter_reset_btn", "Reset Filter"), ModalButtonKind.Secondary, Modifier.fillMaxWidth(),
                        textColor = Brand.red,
                    ) { AllCatMonthPicker.reset() }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModalButton(tr("common_cancel", "Cancel"), ModalButtonKind.Secondary, Modifier.weight(1f)) { AllCatMonthPicker.close() }
                    ModalButton(tr("modal_set_btn", "Set"), ModalButtonKind.Green, Modifier.weight(1f)) { AllCatMonthPicker.confirm() }
                }
            }
        }
    }
}

/** openHomeCategoryDetails(): Home > Category Breakdown row -> the "See all" sheet, opened on top of Home (closing it leaves you on Home). */
fun installStatsHooks() {
    AppActions.openHomeCategoryDetails = {
        // the Home graph is always the current month, so drop any month picked with the Stats calendar
        AppUi.statsMonthOverride = null
        val state = AppStore.state
        if (statsMonthSpend(state, statsSpendMonthScope(state)).isNotEmpty()) AllCategoriesSheet.open()
    }
}

private val CatEnterEase = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
