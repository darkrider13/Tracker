package com.myapp.app.ui.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myapp.app.data.AppStore
import com.myapp.app.data.JsDate
import com.myapp.app.ui.AppActions
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.FaStyle
import com.myapp.app.ui.components.PulseDot
import com.myapp.app.ui.components.WheelItem
import com.myapp.app.ui.components.WheelPicker
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.BN_MONTHS
import com.myapp.app.util.BN_MONTHS_SHORT
import com.myapp.app.util.getLang
import com.myapp.app.util.localizeDigits
import com.myapp.app.util.toBnDigits
import com.myapp.app.util.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

private const val APP_LAUNCH_YEAR = 2026
private val EN_MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)
private val EN_MONTHS_SHORT = listOf(
    "Jan.", "Feb.", "Mar.", "Apr.", "May", "Jun.", "Jul.", "Aug.", "Sep.", "Oct.", "Nov.", "Dec.",
)

/** getPickerMinYear(target): 5 years back for Transactions / Fund History, 15 for Debts / Receivables. */
fun pickerMinYear(target: String): Int {
    val cur = Calendar.getInstance().get(Calendar.YEAR)
    val span = if (target == "debts" || target == "receivables") 15 else 5
    return maxOf(cur - span, APP_LAUNCH_YEAR)
}

/**
 * Places other screens plug into so ONE calendar / wheel serves Transactions, Debts,
 * Receivables and Fund History. Transactions works out of the box; Step 7 fills the debt hooks
 * and the Fund Details screen fills [fundHistory].
 */
object DatePickerSources {
    /** getDebtDateFilter(category): "month" | "YYYY-MM" | "YYYY-MM-DD" (null = nothing saved). */
    var getDebtDateFilter: (String) -> String? = { null }
    var setDebtDateFilter: (String, String) -> Unit = { _, _ -> }
    /** Dates (millis) of the entries that get a dot, for debts / receivables (status filter applied by the caller). */
    var debtEntryDates: (String) -> List<Long> = { emptyList() }
    /** Dates of this fund's transactions for the Fund Details history. */
    var fundHistoryDates: () -> List<Long> = { emptyList() }
    /** After a filter changed, the owning screen refreshes (compose normally does this by itself). */
    var onChanged: (String) -> Unit = {}
}

/** fundHistoryFilter from the old app: mode null | "date" | "month". */
object FundHistoryFilter {
    var mode by mutableStateOf<String?>(null)
    var value by mutableStateOf<String?>(null)
}

private fun pad2(n: Int) = n.toString().padStart(2, '0')
private fun ymd(y: Int, m: Int, d: Int) = "$y-${pad2(m)}-${pad2(d)}"

private fun localParts(millis: Long): Triple<Int, Int, Int> {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

private fun todayYMD(): String {
    val (y, m, d) = localParts(System.currentTimeMillis())
    return ymd(y, m, d)
}

private val YMD = Regex("^\\d{4}-\\d{2}-\\d{2}$")
private val YM = Regex("^\\d{4}-\\d{2}$")

private fun monthLabel(m: Int, short: Boolean): String =
    if (getLang() == "bn") (if (short) BN_MONTHS_SHORT[m - 1] else BN_MONTHS[m - 1])
    else (if (short) EN_MONTHS_SHORT[m - 1] else EN_MONTHS[m - 1])

/** The filter value currently saved for [target] as "YYYY-MM-DD", "YYYY-MM" or null (nothing pinned). */
private fun savedFilter(target: String): String? {
    val f = AppStore.state.filters
    return when (target) {
        "fundHistory" -> if (FundHistoryFilter.mode != null) FundHistoryFilter.value else null
        "debts", "receivables" -> DatePickerSources.getDebtDateFilter(target)?.takeIf { YMD.matches(it) || YM.matches(it) }
        else -> when {
            f.time == "custom" && f.customDate != null -> f.customDate
            f.time == "custommonth" && f.customMonth != null -> f.customMonth
            else -> null
        }
    }
}

/** Days with entries (local y/m/d) for the dots / red wheel rows. */
private fun entryDates(target: String): List<Long> {
    val s = AppStore.state
    return when (target) {
        "debts", "receivables" -> DatePickerSources.debtEntryDates(target)
        "fundHistory" -> DatePickerSources.fundHistoryDates()
        else -> s.transactions.filter { it.fund == s.activeFund }.mapNotNull { JsDate.parseMillis(it.date) }
    }
}

/** Apply a picked date ("YYYY-MM-DD") or whole month ("YYYY-MM") to [target]. */
private fun applyPick(target: String, value: String, isMonth: Boolean) {
    when (target) {
        "debts", "receivables" -> {
            DatePickerSources.setDebtDateFilter(target, value)
            DatePickerSources.onChanged(target)
        }
        "fundHistory" -> {
            FundHistoryFilter.mode = if (isMonth) "month" else "date"
            FundHistoryFilter.value = value
        }
        else -> {
            val f = AppStore.state.filters
            if (isMonth) {
                f.time = "custommonth"; f.customMonth = value; f.customDate = null
            } else {
                f.time = "custom"; f.customDate = value; f.customMonth = null
            }
            AppStore.commit()
        }
    }
}

/** Back to "no date filter" for [target] (Clear Filter). */
private fun clearPick(target: String) {
    when (target) {
        "debts", "receivables" -> {
            DatePickerSources.setDebtDateFilter(target, "month")
            DatePickerSources.onChanged(target)
        }
        "fundHistory" -> {
            FundHistoryFilter.mode = null
            FundHistoryFilter.value = null
        }
        else -> {
            val f = AppStore.state.filters
            f.time = "month"; f.customDate = null; f.customMonth = null
            AppStore.commit()
        }
    }
}

/** hasActiveFilter: a pinned day (not today) or month; picking today is not a filter worth clearing. */
private fun hasActiveFilter(target: String): Boolean {
    val saved = savedFilter(target) ?: return false
    if (target == "debts" || target == "receivables") return saved != "month" && saved != todayYMD()
    if (target == "fundHistory") return !(FundHistoryFilter.mode == "date" && saved == todayYMD())
    val f = AppStore.state.filters
    return (f.time == "custom" && f.customDate != null && f.customDate != todayYMD()) ||
        (f.time == "custommonth" && f.customMonth != null)
}

// =============================================================================================
// Grid calendar (popup-time-calendar)
// =============================================================================================

object TimeCalendar {
    var target by mutableStateOf("transactions")
    var year by mutableIntStateOf(2026)
    var month by mutableIntStateOf(1)   // 1..12

    /** openDateCalendarIcon(target) + openTimeCalendarModalUnified(target). */
    fun open(t: String) {
        target = t
        val saved = savedFilter(t)
        val now = Calendar.getInstance()
        when {
            saved != null && YMD.matches(saved) -> {
                val (y, m, _) = localParts(JsDate.parseMillis(saved) ?: now.timeInMillis)
                year = y; month = m
            }
            saved != null && YM.matches(saved) -> {
                val p = saved.split("-")
                year = p[0].toInt(); month = p[1].toInt()
            }
            else -> {
                year = now.get(Calendar.YEAR); month = now.get(Calendar.MONTH) + 1
            }
        }
        Popups.timeCalendar = true
    }

    fun close() { Popups.timeCalendar = false }

    /** navigateTimeCalendar(delta): backwards stops at the earliest year this screen can have data for. */
    fun navigate(delta: Int) {
        var y = year
        var m = month + delta
        while (m < 1) { m += 12; y-- }
        while (m > 12) { m -= 12; y++ }
        if (delta < 0 && y < pickerMinYear(target)) return
        year = y; month = m
    }

    fun select(dateStr: String) {
        applyPick(target, dateStr, isMonth = false)
        close()
    }

    fun clear() {
        clearPick(target)
        close()
    }

    /** Calendar label -> wheel picker (switchToWheelDatePicker): fade out, 247 ms pause, fade in. */
    fun switchToWheel(scope: CoroutineScope) {
        val t = target
        close()
        scope.launch(Dispatchers.Main) {
            delay(247)
            UnifiedDatePicker.open(t)
        }
    }
}

@Composable
fun TimeCalendarPopup() {
    val c = AppTheme.colors
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    PopupLayer(visible = Popups.timeCalendar, scrim = 0.6f) {
        // observe state so the grid redraws when a filter changes
        AppStore.observe()
        val target = TimeCalendar.target
        val year = TimeCalendar.year
        val month = TimeCalendar.month
        val minYear = pickerMinYear(target)
        val atMin = year <= minYear && month == 1

        val firstWeekday = Calendar.getInstance().apply { set(year, month - 1, 1) }.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = Calendar.getInstance().apply { set(year, month - 1, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)

        val dotDays = remember(target, year, month, AppStore.state.transactions.size) {
            entryDates(target).mapNotNull {
                val (y, m, d) = localParts(it)
                if (y == year && m == month) d else null
            }.toSet()
        }
        val saved = savedFilter(target)
        val selectedDay: Int? = if (saved != null && YMD.matches(saved) &&
            !(target == "fundHistory" && FundHistoryFilter.mode != "date")
        ) {
            val (y, m, d) = localParts(JsDate.parseMillis(saved) ?: 0L)
            if (y == year && m == month) d else null
        } else null
        val now = Calendar.getInstance()
        val todayDay = if (now.get(Calendar.YEAR) == year && now.get(Calendar.MONTH) + 1 == month) now.get(Calendar.DAY_OF_MONTH) else null

        GlassCard(radius = 28.dp, padding = 24.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PopupHeaderIcon("fa-calendar-alt", 32.dp, 12.dp, Brand.red)
                    AppText(
                        text = tr("filter_select_date", "Select a Date"), size = 18f, lineHeight = 28f,
                        weight = FontWeight.ExtraBold, letterSpacingEm = -0.025f, color = c.textGray900,
                    )
                }

                // prev / label / next
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    NavCircle("fa-chevron-left", Modifier.alpha(if (atMin) 0.3f else 1f), enabled = !atMin) { TimeCalendar.navigate(-1) }
                    AppText(
                        text = if (getLang() == "bn") "${BN_MONTHS[month - 1]} ${toBnDigits(year)}" else "${EN_MONTHS[month - 1]} $year",
                        size = 14f, lineHeight = 20f, weight = FontWeight.ExtraBold, color = c.textGray900,
                        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            TimeCalendar.switchToWheel(scope)
                        },
                    )
                    NavCircle("fa-chevron-right", Modifier, enabled = true) { TimeCalendar.navigate(1) }
                }

                // weekday header
                val wd = listOf(
                    tr("cal_wd_sun", "S"), tr("cal_wd_mon", "M"), tr("cal_wd_tue", "T"), tr("cal_wd_wed", "W"),
                    tr("cal_wd_thu", "T"), tr("cal_wd_fri", "F"), tr("cal_wd_sat", "S"),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    wd.forEachIndexed { i, label ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            AppText(
                                text = label, size = 10f, lineHeight = 15f, weight = FontWeight.Bold,
                                color = if (i == 5) Brand.red else c.textGray400, align = TextAlign.Center,
                            )
                        }
                    }
                }

                // the day grid
                val cells = firstWeekday + daysInMonth
                val rows = (cells + 6) / 7
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (r in 0 until rows) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (col in 0 until 7) {
                                val day = r * 7 + col - firstWeekday + 1
                                if (day < 1 || day > daysInMonth) {
                                    Box(Modifier.weight(1f))
                                } else {
                                    DayCell(
                                        day = day,
                                        isSelected = day == selectedDay,
                                        isToday = day == todayDay,
                                        isFriday = col == 5,
                                        hasSpend = day in dotDays,
                                        modifier = Modifier.weight(1f),
                                    ) { TimeCalendar.select(ymd(year, month, day)) }
                                }
                            }
                        }
                    }
                }

                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasActiveFilter(target)) {
                        ModalButton(tr("filter_clear_filter", "Clear Filter"), ModalButtonKind.Secondary, Modifier.fillMaxWidth()) {
                            TimeCalendar.clear()
                        }
                    }
                    ModalButton(tr("common_close", "Close"), ModalButtonKind.Red, Modifier.fillMaxWidth()) { TimeCalendar.close() }
                }
            }
        }
    }
}

@Composable
private fun NavCircle(icon: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(c.bgWhite60, CircleShape)
            .then(
                if (enabled) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) { FaIcon(icon, 12.dp, c.textGray600) }
}

@Composable
private fun DayCell(
    day: Int,
    isSelected: Boolean,
    isToday: Boolean,
    isFriday: Boolean,
    hasSpend: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val bg: Color
    val fg: Color
    when {
        isSelected -> { bg = Brand.red; fg = Color.White }
        isToday -> { bg = Color(0xFF991B1B).copy(alpha = 0.16f); fg = Brand.red }
        isFriday -> { bg = c.bgWhite60; fg = Brand.red }
        else -> { bg = c.bgWhite60; fg = c.textGray700 }
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(bg, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppText(text = localizeDigits(day.toString()), size = 12f, lineHeight = 16f, weight = FontWeight.Bold, color = fg)
        if (hasSpend && !isSelected) {
            PulseDot(6.dp, Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
        }
    }
}

// =============================================================================================
// Wheel date picker (popup-unified-date-picker)
// =============================================================================================

object UnifiedDatePicker {
    var target by mutableStateOf("transactions")
    /** "all" or the day number */
    var day by mutableStateOf("all")
    var month by mutableIntStateOf(1)
    var year by mutableIntStateOf(2026)
    /** bumps whenever the wheels must be rebuilt from scratch (open) */
    var openCount by mutableIntStateOf(0)

    fun open(t: String) {
        target = t
        val saved = savedFilter(t)
        val now = Calendar.getInstance()
        when {
            saved != null && YMD.matches(saved) -> {
                val (y, m, d) = localParts(JsDate.parseMillis(saved) ?: now.timeInMillis)
                day = d.toString(); month = m; year = y
            }
            saved != null && YM.matches(saved) -> {
                val p = saved.split("-")
                day = "all"; month = p[1].toInt(); year = p[0].toInt()
            }
            else -> {
                day = "all"; month = now.get(Calendar.MONTH) + 1; year = now.get(Calendar.YEAR)
            }
        }
        openCount++
        Popups.unifiedDatePicker = true
    }

    fun close() { Popups.unifiedDatePicker = false }

    fun confirm() {
        val monthStr = "$year-${pad2(month)}"
        if (day == "all") applyPick(target, monthStr, isMonth = true)
        else applyPick(target, "$monthStr-${pad2(day.toInt())}", isMonth = false)
        close()
    }

    /** The header calendar button: back to the grid calendar (switchToGridDatePicker). */
    fun switchToGrid(scope: CoroutineScope) {
        val t = target
        close()
        scope.launch(Dispatchers.Main) {
            delay(247)
            TimeCalendar.open(t)
        }
    }
}

@Composable
fun UnifiedDatePickerPopup() {
    val c = AppTheme.colors
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    PopupLayer(visible = Popups.unifiedDatePicker, scrim = 0.6f) {
        val u = UnifiedDatePicker
        val target = u.target
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val startYear = pickerMinYear(target)

        val yearItems = remember(u.openCount) {
            (startYear..currentYear).map { WheelItem(it.toString(), localizeDigits(it.toString())) }
        }
        val monthItems = remember(u.openCount) {
            (1..12).map { WheelItem(it.toString(), monthLabel(it, short = true)) }
        }
        val daysInMonth = Calendar.getInstance().apply { set(u.year, u.month - 1, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
        val txDays = remember(u.openCount, u.month, u.year) {
            entryDates(target).mapNotNull {
                val (y, m, d) = localParts(it)
                if (y == u.year && m == u.month) d else null
            }.toSet()
        }
        // keep a numeric day inside the new month ("All" is never clamped)
        val shownDay = if (u.day == "all") "all" else minOf(u.day.toIntOrNull() ?: 1, daysInMonth).toString()
        val dayItems = remember(u.openCount, u.month, u.year) {
            val l = (1..daysInMonth).map { WheelItem(it.toString(), localizeDigits(it.toString().padStart(2, '0')), it in txDays) }
            l + WheelItem("all", tr("filter_all_dates", "All"))
        }

        GlassCard(radius = 28.dp, padding = 24.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c.bgWhite, CircleShape)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                UnifiedDatePicker.switchToGrid(scope)
                            },
                        contentAlignment = Alignment.Center,
                    ) { FaIcon("fa-calendar-alt", 12.dp, Brand.red, style = FaStyle.Regular) }
                    AppText(
                        text = tr("filter_select_date", "Select a Date"), size = 18f, lineHeight = 28f,
                        weight = FontWeight.ExtraBold, letterSpacingEm = -0.025f, color = c.textGray900,
                    )
                }

                Column {
                    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.Center) {
                        ColLabel(tr("modal_time_date", "Date"), 56)
                        Box(Modifier.width(4.dp))
                        ColLabel(tr("fund_history_month_label", "Month"), 78)
                        Box(Modifier.width(4.dp))
                        ColLabel(tr("fund_history_year_label", "Year"), 72)
                    }
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        // the highlight band behind the centre row
                        val bandShape = RoundedCornerShape(16.dp)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .background(Color(0xFF991B1B).copy(alpha = 0.10f), bandShape)
                                .border(1.dp, Color(0xFF991B1B).copy(alpha = 0.30f), bandShape),
                        )
                        Row(horizontalArrangement = Arrangement.Center) {
                            // The day wheel is rebuilt when month / year settle, like rebuildUnifiedDayWheel
                            WheelPicker(
                                items = dayItems, initialValue = shownDay, loop = true, width = 56.dp,
                                resetKey = Triple(u.openCount, u.month, u.year),
                            ) { v -> u.day = v }
                            Box(Modifier.width(4.dp))
                            WheelPicker(
                                items = monthItems, initialValue = u.month.toString(), loop = true, width = 78.dp,
                                resetKey = u.openCount,
                            ) { v -> u.month = v.toInt() }
                            Box(Modifier.width(4.dp))
                            WheelPicker(
                                items = yearItems, initialValue = u.year.toString(), loop = false, width = 72.dp,
                                resetKey = u.openCount,
                            ) { v -> u.year = v.toInt() }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModalButton(tr("common_cancel", "Cancel"), ModalButtonKind.Secondary, Modifier.weight(1f), textColor = Brand.red) {
                        UnifiedDatePicker.close()
                    }
                    ModalButton(tr("modal_set_btn", "Set"), ModalButtonKind.Green, Modifier.weight(1f)) { UnifiedDatePicker.confirm() }
                }
            }
        }
    }
}

@Composable
private fun ColLabel(text: String, widthDp: Int) {
    val c = AppTheme.colors
    Box(Modifier.width(widthDp.dp), contentAlignment = Alignment.Center) {
        AppText(
            text = text.uppercase(), size = 12f, lineHeight = 16f, weight = FontWeight.Bold,
            color = c.textGray400, align = TextAlign.Center,
        )
    }
}

/** Installed from installPopupHooks(): the Logs calendar button opens the grid calendar directly. */
fun installDatePickerHooks() {
    AppActions.openLogsDateMenu = { TimeCalendar.open("transactions") }
    DatePickerSources.getDebtDateFilter = { cat -> com.myapp.app.ui.screens.DebtUi.of(cat).dateFilter }
    DatePickerSources.setDebtDateFilter = { cat, v -> com.myapp.app.ui.screens.DebtUi.of(cat).dateFilter = v }
    DatePickerSources.debtEntryDates = { cat -> com.myapp.app.ui.screens.debtEntryDatesFor(cat) }
}
