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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.FaStyle
import com.myapp.app.ui.components.WheelItem
import com.myapp.app.ui.components.WheelPicker
import com.myapp.app.ui.screens.to12Hour
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.BN_MONTHS
import com.myapp.app.util.getLang
import com.myapp.app.util.localizeDigits
import com.myapp.app.util.toBnDigits
import com.myapp.app.util.tr
import java.util.Calendar

// =============================================================================================
// Time picker (popup-time-picker): Hour / Minute wheels (looped) + a tap-to-toggle AM/PM button
// =============================================================================================

object TimePicker {
    private var onConfirm: ((String) -> Unit)? = null
    var title by mutableStateOf("")
    var hour by mutableIntStateOf(10)
    var minute by mutableIntStateOf(0)
    var ampm by mutableStateOf("AM")
    var openCount by mutableIntStateOf(0)

    /**
     * openTimePicker(): [time24] is "HH:mm" (null = 10:00); [onSet] gets the chosen time as "HH:mm".
     * [titleText] is the heading ("Select a Time" unless a caller names the thing being set).
     */
    fun open(time24: String?, titleText: String? = null, onSet: (String) -> Unit) {
        val (h12, m, ap) = to12Hour(time24 ?: "10:00")
        hour = h12; minute = m; ampm = ap
        title = titleText ?: tr("modal_select_time", "Select a Time")
        onConfirm = onSet
        openCount++
        Popups.timePicker = true
    }

    fun close() {
        Popups.timePicker = false
        onConfirm = null
    }

    /** to24Hour(h12, minute, ampm) */
    fun to24Hour(h12: Int, minute: Int, ampm: String): String {
        var h = h12 % 12
        if (ampm == "PM") h += 12
        return h.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
    }

    fun confirm() {
        val cb = onConfirm ?: return
        cb(to24Hour(hour, minute, ampm))
        close()
    }
}

@Composable
fun TimePickerPopup() {
    val c = AppTheme.colors
    PopupLayer(visible = Popups.timePicker, scrim = 0.6f) {
        val t = TimePicker
        val hourItems = remember(t.openCount) {
            (1..12).map { WheelItem(it.toString(), localizeDigits(it.toString().padStart(2, '0'))) }
        }
        val minuteItems = remember(t.openCount) {
            (0..59).map { WheelItem(it.toString(), localizeDigits(it.toString().padStart(2, '0'))) }
        }
        GlassCard(radius = 28.dp, padding = 24.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PopupHeaderIcon("fa-clock", 32.dp, 12.dp, Brand.red)
                    AppText(
                        text = t.title, size = 18f, lineHeight = 28f, weight = FontWeight.ExtraBold,
                        letterSpacingEm = -0.025f, color = c.textGray900,
                    )
                }
                Column {
                    // column titles
                    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.Center) {
                        Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
                            AppText(
                                text = tr("modal_time_hour", "Hour"), size = 14f, lineHeight = 20f,
                                weight = FontWeight.Bold, color = c.textGray400, uppercase = true, align = TextAlign.Center,
                            )
                        }
                        Box(Modifier.width(16.dp))
                        Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
                            AppText(
                                text = tr("modal_time_minute", "Minute"), size = 12f, lineHeight = 16f,
                                weight = FontWeight.Bold, color = c.textGray400, uppercase = true, align = TextAlign.Center,
                            )
                        }
                        Box(Modifier.width(24.dp + 58.dp))
                    }
                    // wheels with the highlight band behind the centre row
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            val bandShape = RoundedCornerShape(16.dp)
                            // the band spans exactly the width of the wheel row (left-0 right-0)
                            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .background(Color(0xFF991B1B).copy(alpha = 0.10f), bandShape)
                                        .border(1.dp, Color(0xFF991B1B).copy(alpha = 0.30f), bandShape),
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                WheelPicker(
                                    items = hourItems, initialValue = t.hour.toString(), loop = true,
                                    width = 56.dp, resetKey = t.openCount,
                                ) { v -> t.hour = v.toInt() }
                                Box(Modifier.padding(horizontal = 12.dp)) {
                                    AppText(
                                        text = ":", size = 18f, lineHeight = 28f,
                                        weight = FontWeight.ExtraBold, color = c.textGray900,
                                    )
                                }
                                WheelPicker(
                                    items = minuteItems, initialValue = t.minute.toString(), loop = true,
                                    width = 56.dp, resetKey = t.openCount,
                                ) { v -> t.minute = v.toInt() }
                                Box(
                                    Modifier.padding(start = 24.dp).width(58.dp).height(132.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                                t.ampm = if (t.ampm == "AM") "PM" else "AM"
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AppText(
                                            text = if (t.ampm == "PM") tr("reminder_pm", "PM") else tr("reminder_am", "AM"),
                                            size = 18f, lineHeight = 28f, weight = FontWeight.ExtraBold, color = c.textGray900,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModalButton(tr("common_cancel", "Cancel"), ModalButtonKind.Secondary, Modifier.weight(1f)) { TimePicker.close() }
                    ModalButton(tr("modal_set_btn", "Set"), ModalButtonKind.Green, Modifier.weight(1f)) { TimePicker.confirm() }
                }
            }
        }
    }
}

// =============================================================================================
// Due Date calendar (popup-due-date-calendar) - like the filter calendar, but no past days
// =============================================================================================

private val DUE_EN_MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

object DueDateCalendar {
    private var onPick: ((String) -> Unit)? = null
    var selected by mutableStateOf<String?>(null)
    var year by mutableIntStateOf(2026)
    var month by mutableIntStateOf(1)   // 1..12

    fun open(current: String?, onSelect: (String) -> Unit) {
        val now = Calendar.getInstance()
        val ms = com.myapp.app.data.JsDate.parseMillis(current)
        val c = if (!current.isNullOrEmpty() && ms != null) Calendar.getInstance().apply { timeInMillis = ms } else now
        year = c.get(Calendar.YEAR)
        month = c.get(Calendar.MONTH) + 1
        selected = current?.takeIf { it.isNotEmpty() }
        onPick = onSelect
        Popups.dueDateCalendar = true
    }

    fun close() {
        Popups.dueDateCalendar = false
        onPick = null
    }

    fun navigate(delta: Int) {
        var y = year
        var m = month + delta
        while (m < 1) { m += 12; y-- }
        while (m > 12) { m -= 12; y++ }
        year = y; month = m
    }

    fun select(iso: String) {
        onPick?.invoke(iso)
        close()
    }
}

@Composable
fun DueDateCalendarPopup() {
    val c = AppTheme.colors
    PopupLayer(visible = Popups.dueDateCalendar, scrim = 0.6f) {
        val d = DueDateCalendar
        val year = d.year
        val month = d.month
        val first = Calendar.getInstance().apply { set(year, month - 1, 1) }
        val firstWeekday = first.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val selMs = com.myapp.app.data.JsDate.parseMillis(d.selected)
        val sel = selMs?.let { Calendar.getInstance().apply { timeInMillis = it } }

        GlassCard(radius = 28.dp, padding = 24.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PopupHeaderIcon("fa-calendar-alt", 32.dp, 12.dp, Brand.red)
                    AppText(
                        text = tr("filter_select_date", "Select a Date"), size = 18f, lineHeight = 28f,
                        weight = FontWeight.ExtraBold, letterSpacingEm = -0.025f, color = c.textGray900,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    DueNavCircle("fa-chevron-left") { DueDateCalendar.navigate(-1) }
                    AppText(
                        text = if (getLang() == "bn") "${BN_MONTHS[month - 1]} ${toBnDigits(year)}" else "${DUE_EN_MONTHS[month - 1]} $year",
                        size = 14f, lineHeight = 20f, weight = FontWeight.ExtraBold, color = c.textGray900,
                    )
                    DueNavCircle("fa-chevron-right") { DueDateCalendar.navigate(1) }
                }
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
                val rows = (firstWeekday + daysInMonth + 6) / 7
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (r in 0 until rows) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (col in 0 until 7) {
                                val day = r * 7 + col - firstWeekday + 1
                                if (day < 1 || day > daysInMonth) {
                                    Box(Modifier.weight(1f))
                                } else {
                                    val cell = Calendar.getInstance().apply {
                                        set(year, month - 1, day, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                                    }
                                    val isPast = cell.before(today)
                                    val isToday = cell.timeInMillis == today.timeInMillis
                                    val isSelected = sel != null &&
                                        sel.get(Calendar.YEAR) == year && sel.get(Calendar.MONTH) + 1 == month &&
                                        sel.get(Calendar.DAY_OF_MONTH) == day
                                    val isFriday = col == 5
                                    val shape = RoundedCornerShape(12.dp)
                                    val bg: Color
                                    val fg: Color
                                    when {
                                        isPast -> { bg = Color.Transparent; fg = c.textGray300 }
                                        isSelected -> { bg = Brand.red; fg = Color.White }
                                        isToday -> { bg = Color(0xFF991B1B).copy(alpha = 0.16f); fg = Brand.red }
                                        isFriday -> { bg = c.bgWhite60; fg = Brand.red }
                                        else -> { bg = c.bgWhite60; fg = c.textGray700 }
                                    }
                                    val iso = "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(shape)
                                            .background(bg, shape)
                                            .then(
                                                if (isPast) Modifier
                                                else Modifier.clickable(
                                                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                                                ) { DueDateCalendar.select(iso) },
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AppText(
                                            text = localizeDigits(day.toString()), size = 12f, lineHeight = 16f,
                                            weight = FontWeight.Bold, color = fg,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    ModalButton(tr("common_close", "Close"), ModalButtonKind.Red, Modifier.fillMaxWidth()) { DueDateCalendar.close() }
                }
            }
        }
    }
}

@Composable
private fun DueNavCircle(icon: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(c.bgWhite60, CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { FaIcon(icon, 12.dp, c.textGray600) }
}
