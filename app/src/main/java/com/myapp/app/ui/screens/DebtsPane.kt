package com.myapp.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myapp.app.data.AppState
import com.myapp.app.data.AppStore
import com.myapp.app.data.Debt
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.FaStyle
import com.myapp.app.ui.components.NoteGlyph
import com.myapp.app.ui.components.PulseDot
import com.myapp.app.ui.components.ScrollFadePaged
import com.myapp.app.ui.components.appTextStyle
import com.myapp.app.ui.components.pagedItem
import com.myapp.app.ui.components.rememberPagedState
import com.myapp.app.ui.popup.Popups
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.ui.theme.Tw
import com.myapp.app.ui.AppActions
import com.myapp.app.util.DateOpts
import com.myapp.app.util.amountTextExactMatchesQuery
import com.myapp.app.util.fmtDate
import com.myapp.app.util.fmtMoney
import com.myapp.app.util.fmtTime
import com.myapp.app.util.formatAmountTyping
import com.myapp.app.util.fundDisplayName
import com.myapp.app.util.isDateFilterToday
import com.myapp.app.util.localizeDigits
import com.myapp.app.util.normalizeHyphenForSearch
import com.myapp.app.util.tr
import com.myapp.app.util.trf

private val Green500 = Tw.green500
private val Green600 = Tw.green600
private val Yellow500 = Tw.yellow500
private val Rose100 = Tw.rose100
private val Emerald100 = Tw.emerald100

/** The Debts ("you owe") or Receivables ("owed to you") sub-tab of Logs. [category] = debts | receivables. */
@Composable
fun DebtsPane(category: String) {
    val state = AppStore.observe()
    val cs = DebtUi.of(category)
    val accent = if (category == "receivables") Brand.green else Brand.red

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SummaryCard(state, category, cs)
        DebtSearchRow(category, cs)
        DebtFilterRow(category, cs)

        val query = normalizedLogsQuery(cs.search.text)
        val entries = filteredDebtEntries(state, category)
        DebtList(entries, category, query, accent)
    }
}

// ------------------------------------------------------------------ black summary card

@Composable
private fun SummaryCard(state: AppState, category: String, cs: DebtCategoryState) {
    val c = AppTheme.colors
    val isReceivable = category == "receivables"
    val total = debtTotalPending(state, category)
    val shape = RoundedCornerShape(24.dp)
    val labelColor = if (c.isLight) Color(0xFF6B7280) else c.textGray400
    val dateStr = cs.dateFilter
    val showDot = dateStr != "month" && !isDateFilterToday(dateStr)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.categoryCardBg, shape)
            .then(if (c.categoryCardBorder != null) Modifier.border(1.dp, c.categoryCardBorder!!, shape) else Modifier),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val label = (if (isReceivable) tr("logs_total_collectible", "Total Collectible") else tr("logs_total_owed", "Total Owed")) +
                (if (total > 0) tr("logs_pending_suffix", " (Pending)") else "")
            AppText(
                text = label.uppercase(), size = 10f, lineHeight = 15f, weight = FontWeight.Bold,
                letterSpacingEm = 0.05f, color = labelColor, modifier = Modifier.padding(end = 56.dp),
            )
            AppText(
                text = "৳" + fmtMoney(total), size = 30f, lineHeight = 36f, weight = FontWeight.ExtraBold,
                letterSpacingEm = -0.025f, color = if (isReceivable) Green500 else Brand.red,
            )
        }
        // the calendar button only exists on the Settled tab
        if (cs.status == "settled") {
            val btnShape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
                    .size(44.dp)
                    .clip(btnShape)
                    .background(c.bgGray200a50, btnShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        com.myapp.app.ui.popup.TimeCalendar.open(category)
                    },
                contentAlignment = Alignment.Center,
            ) {
                FaIcon("fa-calendar-alt", 16.dp, Brand.red, style = FaStyle.Regular)
                if (showDot) PulseDot(4.dp, Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ search row

@Composable
private fun DebtSearchRow(category: String, cs: DebtCategoryState) {
    val c = AppTheme.colors
    val fieldShape = RoundedCornerShape(16.dp)
    val placeholder = when (cs.searchField) {
        "note" -> tr("logs_search_by_note", "Search by Note...")
        "amount" -> tr("logs_search_by_amount", "Search by Amount...")
        else -> tr("logs_search_by_person", "Search by Person Name...")
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = cs.search,
                onValueChange = { nv ->
                    var v = nv
                    if (cs.searchField == "amount") {
                        val fromEnd = v.text.length - v.selection.end
                        val formatted = formatAmountTyping(v.text)
                        val pos = (formatted.length - fromEnd).coerceIn(0, formatted.length)
                        v = TextFieldValue(formatted, TextRange(pos))
                    }
                    cs.search = v
                },
                singleLine = true,
                textStyle = appTextStyle(12f, c.textDefault, FontWeight.Bold, lineHeight = 16f),
                cursorBrush = SolidColor(c.textDefault),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(fieldShape)
                            .background(c.bgGray200a50, fieldShape)
                            .padding(start = 40.dp, end = 36.dp, top = 12.dp, bottom = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (cs.search.text.isEmpty()) {
                            AppText(
                                text = placeholder, size = 12f, lineHeight = 16f,
                                weight = FontWeight.Bold, color = c.placeholder, singleLineEllipsis = true,
                            )
                        }
                        inner()
                    }
                },
            )
            FaIcon("fa-search", 14.dp, c.textGray400, Modifier.align(Alignment.CenterStart).padding(start = 16.dp))
            if (cs.search.text.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .size(20.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            cs.search = TextFieldValue("")
                        },
                    contentAlignment = Alignment.Center,
                ) { FaIcon("fa-xmark", 10.dp, c.textGray400) }
            }
        }
        SmallSquareButton(onClick = { openSearchFieldPicker(category) }, dot = cs.searchField != "person") {
            FaIcon("fa-search", 14.dp, Brand.red)
        }
        SmallSquareButton(
            onClick = { AppActions.openSectionNote(category) },
            dot = AppStore.state.logSectionNotes[category]?.isNotBlank() == true,
        ) {
            NoteGlyph(14.dp, Brand.red)
        }
    }
}

@Composable
private fun SmallSquareButton(onClick: () -> Unit, dot: Boolean, icon: @Composable () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(c.bgGray200a50, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
        if (dot) PulseDot(4.dp, Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp))
    }
}

// ------------------------------------------------------------------ Pending / Settled / Priority / Amount

@Composable
private fun DebtFilterRow(category: String, cs: DebtCategoryState) {
    val c = AppTheme.colors
    val sort = cs.sort
    val amountLabel = when (sort.amount) {
        "high" -> tr("logs_high_to_low", "High to Low")
        "low" -> tr("logs_low_to_high", "Low to High")
        else -> tr("logs_all_prices", "All Amounts")
    }
    val amountSorted = sort.amount == "high" || sort.amount == "low"

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusButton(
            Modifier.weight(1f), active = cs.status == "pending", activeBg = Brand.red,
            icon = "fa-clock", style = FaStyle.Regular, label = tr("logs_pending", "Pending"),
        ) { cs.status = "pending" }
        StatusButton(
            Modifier.weight(1f), active = cs.status == "settled", activeBg = Green500,
            icon = "fa-handshake", style = FaStyle.Solid, label = tr("logs_settled", "Settled"),
        ) { cs.status = "settled" }
        SortChip(
            Modifier.weight(1f), icon = "fa-flag", label = tr("logs_sort_due_soon", "Priority"), labelSize = 7f,
            dot = sort.priority == "due" || sort.priority == "keyperson",
        ) { openPriorityPicker(category) }
        SortChip(
            Modifier.weight(1f), icon = "fa-money-bill-wave", label = amountLabel,
            labelSize = if (amountSorted) 6f else 7f, dot = sort.amount != "all",
        ) { openDebtAmountPicker(category) }
    }
}

@Composable
private fun StatusButton(
    modifier: Modifier,
    active: Boolean,
    activeBg: Color,
    icon: String,
    style: FaStyle,
    label: String,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .background(if (active) activeBg else c.bgWhite, shape)
            .then(if (!active) Modifier.border(1.dp, c.borderGray200, shape) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
    ) {
        FaIcon(icon, 9.dp, if (active) Color.White else if (icon == "fa-clock") c.textGray600 else Brand.red, style = style)
        AppText(
            text = label, size = 9f, lineHeight = 13.5f, weight = FontWeight.Bold,
            color = if (active) Color.White else c.textGray600, noWrap = true,
        )
    }
}

@Composable
private fun SortChip(
    modifier: Modifier,
    icon: String,
    label: String,
    labelSize: Float,
    dot: Boolean,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .background(c.bgWhite, shape)
            .border(1.dp, c.borderGray200.copy(alpha = 0.8f), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
    ) {
        FaIcon(icon, 7.dp, Tw.red500)
        AppText(
            text = label, size = labelSize, lineHeight = labelSize * 1.5f, weight = FontWeight.Bold,
            color = c.textGray700, noWrap = true,
        )
        if (dot) PulseDot(4.dp)
        FaIcon("fa-caret-down", 9.dp, c.textGray400)
    }
}

// ------------------------------------------------------------------ list

@Composable
private fun DebtList(entries: List<Debt>, category: String, query: String, accent: Color) {
    val c = AppTheme.colors
    if (entries.isEmpty()) {
        val shape = RoundedCornerShape(24.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.bgGray200a50, shape)
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            AppText(
                text = tr("debt_no_records_found", "No records found."),
                size = 12f, lineHeight = 16f, weight = FontWeight.Bold,
                letterSpacingEm = 0.025f, color = c.textGray500, align = TextAlign.Center,
            )
        }
        return
    }
    // exactly 2 cards at a time; snaps by page when there are more
    val paged = rememberPagedState(visible = 2, gap = 8.dp)
    ScrollFadePaged(
        state = paged, count = entries.size,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 2.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            entries.forEachIndexed { index, d ->
                key(d.id) {
                    Box(Modifier.pagedItem(paged, index)) { DebtCard(d, query, category, accent) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ one card

@Composable
private fun DebtCard(d: Debt, query: String, category: String, accent: Color) {
    val c = AppTheme.colors
    val cs = DebtUi.of(category)
    val isReceivable = d.type == "receivable"
    val hasQuery = query.isNotEmpty()
    val mode = cs.searchField

    val paid = debtPaid(d)
    val remaining = debtRemaining(d)
    val pct = if (d.amount > 0) minOf(100.0, paid / d.amount * 100.0) else 0.0
    val hasPayments = paid > 0
    val displayedAmountText = fmtMoney(if (d.settled) d.amount else remaining)

    val personIsMatch = hasQuery && mode == "person" && normalizeHyphenForSearch(d.person).lowercase().startsWith(query)
    val noteIsMatch = hasQuery && mode == "note" && normalizeHyphenForSearch(d.note).lowercase().startsWith(query)
    val isFullMatch = hasQuery && (
        (mode == "person" && normalizeHyphenForSearch(d.person).trim().lowercase() == query) ||
            (mode == "note" && d.note.isNotEmpty() && normalizeHyphenForSearch(d.note).trim().lowercase() == query) ||
            (mode == "amount" && amountTextExactMatchesQuery(displayedAmountText, query))
        )

    val dateLabel = fmtDate(d.createdDate, DateOpts(month = "short", day = "2-digit", year = "numeric")) + " " + fmtTime(d.createdDate)
    val verb = if (isReceivable) tr("debt_lent", "Lent") else tr("debt_borrowed", "Borrowed")
    val iconName = if (isReceivable) "fa-arrow-down-left" else "fa-arrow-up-right"
    val iconBg = if (isReceivable) Emerald100 else Rose100
    val iconColor = if (isReceivable) Brand.green else Brand.red

    val detail: AnnotatedString = detailLine(d, noteIsMatch, query, accent)
    val hasDetail = detail.text.isNotEmpty()

    val tint = if (c.isLight) 0.10f else 0.16f
    val cardBg = if (isFullMatch) accent.copy(alpha = tint) else c.bgGray100a70
    val shape = RoundedCornerShape(16.dp)
    val cardModifier = Modifier
        .fillMaxWidth()
        .height(160.dp)
        .clip(shape)
        .background(cardBg, shape)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { DebtActions.openHistory(d.id) }
    val padding = if (d.settled) Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp)
    else Modifier.padding(16.dp)

    val subColor = if (c.isLight) c.textGray400 else Color(0xFFD6D7DE)
    val subStrong = if (c.isLight) c.textGray400 else Color(0xFFF2F2F6)
    val dividerColor = if (c.isLight) Color(0x666B7280) else Color(0x38FFFFFF)

    Column(
        modifier = cardModifier.then(padding),
        verticalArrangement = Arrangement.spacedBy(if (d.settled) 10.dp else 6.dp),
    ) {
        // header
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(iconBg, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) { FaIcon(iconName, 14.dp, iconColor) }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    val personText = if (personIsMatch) highlightMatch(d.person, query, accent) else AnnotatedString(d.person)
                    RichLine(personText, 14f, 20f, c.textGray900, FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppText(
                            text = "$verb: $dateLabel", size = 8f, lineHeight = 12f,
                            weight = FontWeight.Medium, color = subColor, noWrap = true,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (!d.settled) PulseDot(8.dp, color = if (isReceivable) Brand.green else Brand.red)
                    }
                    if (!d.settled && !d.dueDate.isNullOrEmpty()) DueLine(d, subColor)
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(max = 150.dp).padding(start = 8.dp),
            ) {
                AppText(
                    text = "৳$displayedAmountText", size = 16f, lineHeight = 24f, weight = FontWeight.Black,
                    color = accent, singleLineEllipsis = true,
                )
                if (!d.settled && hasPayments) {
                    AppText(
                        text = trf("debt_of_amount", "of ৳${fmtMoney(d.amount)}", "amt" to "৳${fmtMoney(d.amount)}"),
                        size = 8f, lineHeight = 12f, weight = FontWeight.Bold, color = subColor, singleLineEllipsis = true,
                    )
                }
                if (d.settled) {
                    Box(
                        Modifier.clip(CircleShape).background(Green500, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        AppText(
                            text = tr("debt_settled", "Settled"), size = 9f, lineHeight = 13.5f,
                            weight = FontWeight.Bold, color = Color.White, noWrap = true,
                        )
                    }
                }
            }
        }

        // settled: note line with a divider above it
        if (hasDetail && d.settled) {
            Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(dividerColor))
                Spacer(Modifier.height(8.dp))
                RichLine(detail, 12f, 16f, c.textGray500, FontWeight.Medium, ellipsis = false)
            }
        }

        // pending: progress bar + "paid • % • left"
        if (!d.settled) {
            Column(Modifier.padding(top = 2.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(c.bgGray300a60.copy(alpha = 0.5f), CircleShape),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = (pct / 100.0).toFloat().coerceIn(0f, 1f))
                            .background(debtProgressColor(pct)),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasDetail) {
                        Box(Modifier.weight(1f).padding(end = 8.dp)) { RichLine(detail, 8f, 12f, subStrong, FontWeight.Bold) }
                    } else Spacer(Modifier.weight(1f))
                    AppText(
                        text = trf(
                            "debt_paid_pct_left", "৳${fmtMoney(paid)} paid • ${"%.0f".format(pct)}% • ৳${fmtMoney(remaining)} left",
                            "paid" to "৳${fmtMoney(paid)}",
                            "pct" to localizeDigits("%.0f".format(pct)),
                            "remaining" to "৳${fmtMoney(remaining)}",
                        ),
                        size = 8f, lineHeight = 12f, weight = FontWeight.Bold, color = subStrong, noWrap = true,
                    )
                }
            }
        }

        // pending cards with a note-less settled card keep the buttons pinned to the bottom
        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (!d.settled) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            DebtActions.openEdit(d.id)
                        }.padding(4.dp),
                    ) { FaIcon("fa-pen", 14.dp, c.textGray400) }
                    Box(
                        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            toggleDebtKeyPerson(d.id)
                        }.padding(4.dp),
                    ) {
                        FaIcon(
                            "fa-star", 16.dp, if (d.isKey) Yellow500 else c.textGray300,
                            style = if (d.isKey) FaStyle.Solid else FaStyle.Regular,
                        )
                    }
                }
            } else if (d.isKey) {
                FaIcon("fa-star", 16.dp, Yellow500)
            } else Spacer(Modifier.size(0.dp))

            if (!d.settled) {
                val pillShape = CircleShape
                Box(
                    Modifier
                        .clip(pillShape)
                        .background(if (isReceivable) Green600 else Brand.red, pillShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            DebtActions.openPayment(d.id)
                        }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    AppText(
                        text = if (isReceivable) tr("debt_receive_partially", "Receive Partially") else tr("debt_pay_partially", "Pay Partially"),
                        size = 12f, lineHeight = 16f, weight = FontWeight.Bold, color = Color.White, noWrap = true,
                    )
                }
            } else {
                Box(
                    Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        DebtActions.delete(d.id)
                    }.padding(4.dp),
                ) { FaIcon("fa-trash-alt", 14.dp, c.textGray400) }
            }
        }
    }
}

/** "Fund (note)" - or "Fund - note" when the note already has brackets; just the note without a fund. */
private fun detailLine(d: Debt, noteIsMatch: Boolean, query: String, accent: Color): AnnotatedString {
    val noteAnn = if (d.note.isNotEmpty()) {
        if (noteIsMatch) highlightMatch(d.note, query, accent) else AnnotatedString(d.note)
    } else AnnotatedString("")
    val fund = d.fund
    if (fund.isNullOrEmpty()) return noteAnn
    return androidx.compose.ui.text.buildAnnotatedString {
        append(fundDisplayName(fund))
        if (noteAnn.text.isNotEmpty()) {
            val hasBrackets = d.note.any { it == '(' || it == ')' || it == '（' || it == '）' }
            append(if (hasBrackets) " - " else " (")
            append(noteAnn)
            if (!hasBrackets) append(")")
        }
    }
}

@Composable
private fun DueLine(d: Debt, color: Color) {
    val due = fmtDate(d.dueDate, DateOpts(month = "short", day = "2-digit", year = "numeric"))
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppText(
            text = tr("debt_due", "Due") + ": " + due, size = 8f, lineHeight = 12f,
            weight = FontWeight.Medium, color = color, noWrap = true,
        )
        if (!d.reminderTime.isNullOrEmpty()) {
            val (h12, minute, ampm) = to12Hour(d.reminderTime)
            val ampmLabel = if (ampm == "PM") tr("reminder_pm", "PM") else tr("reminder_am", "AM")
            AppText(text = " • ", size = 8f, lineHeight = 12f, weight = FontWeight.Medium, color = color)
            FaIcon("fa-bell", 9.dp, color)
            AppText(
                text = " " + localizeDigits(h12.toString()) + ":" + localizeDigits(minute.toString().padStart(2, '0')) + " " + ampmLabel,
                size = 8f, lineHeight = 12f, weight = FontWeight.Medium, color = color, noWrap = true,
            )
        }
    }
}

/** One-line rich text with an ellipsis, like `truncate`. */
@Composable
private fun RichLine(
    text: AnnotatedString,
    size: Float,
    lineHeight: Float,
    color: Color,
    weight: FontWeight,
    ellipsis: Boolean = true,
) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        style = appTextStyle(size, color, weight, lineHeight = lineHeight),
        softWrap = !ellipsis,
        maxLines = if (ellipsis) 1 else 2,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}
