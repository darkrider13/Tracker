package com.myapp.app.ui.popup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myapp.app.data.AppStore
import com.myapp.app.ui.AppActions
import com.myapp.app.ui.AppUi
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.FinanceDecor
import com.myapp.app.ui.components.NoteGlyph
import com.myapp.app.ui.components.appTextStyle
import com.myapp.app.ui.components.entrance
import com.myapp.app.ui.screens.pullUp
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.fundDisplayName
import com.myapp.app.util.tr
import com.myapp.app.util.trf
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// =============================================================================================
// Welcome / onboarding (screen-onboarding)
// =============================================================================================

/**
 * Welcome screen. It comes in softly (background first, then the title, the profile card and the button one after
 * another) once the launch screen is gone, and when "Get Started" is tapped it dissolves like the lock screen does
 * (fade + slight zoom-out) so Home is revealed smoothly instead of the screen vanishing in one frame.
 */
@Composable
fun OnboardingScreen() {
    val done = AppStore.observe().onboardingComplete
    androidx.compose.animation.AnimatedVisibility(
        visible = !done,
        enter = androidx.compose.animation.EnterTransition.None,
        exit = androidx.compose.animation.fadeOut(tween(LOCK_EXIT_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
            androidx.compose.animation.scaleOut(targetScale = 1.06f, animationSpec = tween(LOCK_EXIT_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
    ) { OnboardingContent() }
}

@Composable
private fun OnboardingContent() {
    val c = AppTheme.colors
    val go = com.myapp.app.ui.components.rememberSplashGone()
    val haptic = LocalHapticFeedback.current

    var name by remember {
        val u = AppStore.state.username
        mutableStateOf(TextFieldValue(if (u.isNotEmpty() && u != "User") u else ""))
    }
    var error by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val focus = remember { FocusRequester() }
    var shakeKey by remember { mutableStateOf(0) }

    LaunchedEffect(shakeKey) {
        if (shakeKey > 0) {
            // onboardNameShake: 0 -> -10 -> 9 -> -7 -> 6 -> -3 -> 2 -> 0 over 500 ms
            val ease = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
            for (v in listOf(-10f, 9f, -7f, 6f, -3f, 2f, 0f)) shake.animateTo(v, tween(72, easing = ease))
        }
    }

    fun complete() {
        val n = name.text.trim()
        if (n.isEmpty()) {
            // a name is required: shake the box, red underline, stay here
            error = true
            shakeKey++
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            focus.requestFocus()
            return
        }
        AppStore.state.username = n
        AppStore.state.onboardingComplete = true
        AppStore.commit()
    }

    BackHandler(enabled = true) { }
    // day mode: exactly the Home backdrop (also under the bars); dark mode: pure black as before
    val bgModifier = if (c.isLight) Modifier else Modifier.background(Color.Black)

    Box(
        Modifier
            .fillMaxSize()
            .then(bgModifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
    ) {
        if (c.isLight) com.myapp.app.ui.components.AppBackdrop(extend = true)
        Box(Modifier.fillMaxSize().entrance(go, delayMs = 0, rise = 0.dp, durationMs = 700)) { FinanceDecor(accent = Brand.green) }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Column(
                    Modifier.fillMaxWidth().padding(top = 56.dp).entrance(go, delayMs = 60),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppText(
                        text = tr("onboard_welcome_to", "Get Started with"), size = 24f, lineHeight = 32f,
                        weight = FontWeight.Medium, color = c.textGray400, align = TextAlign.Center,
                    )
                    AppText(
                        text = tr("onboard_app_name", "Tracker"), size = 48f, lineHeight = 48f, weight = FontWeight.Black,
                        letterSpacingEm = -0.025f, color = c.textGray900, align = TextAlign.Center,
                    )
                    AppText(
                        text = tr("onboard_subtitle", "Let's personalize your dashboard"), size = 14f, lineHeight = 20f,
                        weight = FontWeight.Medium, color = c.textGray400, align = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                val cardShape = RoundedCornerShape(28.dp)
                Column(
                    Modifier
                        .padding(top = 40.dp)
                        .entrance(go, delayMs = 200)
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(c.bgGray200a60, cardShape)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    AppText(
                        text = tr("onboard_your_profile", "Your Profile"), size = 20f, lineHeight = 28f,
                        weight = FontWeight.Bold, color = c.textGray900,
                    )
                    Row(
                        Modifier.fillMaxWidth().offset(x = shake.value.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(c.bgGray300a60, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { FaIcon("fa-user", 14.dp, c.textGray500) }
                        Column(Modifier.weight(1f)) {
                            Box {
                                BasicTextField(
                                    value = name,
                                    onValueChange = { name = it; error = false },
                                    singleLine = true,
                                    textStyle = appTextStyle(16f, c.textGray700, FontWeight.Medium, lineHeight = 24f),
                                    cursorBrush = SolidColor(c.textGray700),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                        .focusRequester(focus)
                                        .onFocusChanged { focused = it.isFocused },
                                )
                                if (name.text.isEmpty()) {
                                    AppText(
                                        text = tr("onboard_name_placeholder", "What is your name?"), size = 16f, lineHeight = 24f,
                                        weight = FontWeight.Medium, color = if (error) Color(0xFFEF4444) else c.placeholder,
                                    )
                                }
                            }
                            Box(
                                Modifier.fillMaxWidth().height(1.dp)
                                    .background(if (error) Color(0xFFEF4444) else if (focused) Brand.green else c.borderGray300),
                            )
                        }
                    }
                }
            }

            val btnShape = RoundedCornerShape(16.dp)
            Row(
                Modifier
                    .padding(top = 32.dp)
                    .entrance(go, delayMs = 340)
                    .fillMaxWidth()
                    .shadow(8.dp, btnShape)
                    .clip(btnShape)
                    .background(Brand.green, btnShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { complete() }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FaIcon("fa-check-circle", 16.dp, Color.White)
                AppText(text = tr("onboard_get_started", "Get Started"), size = 16f, lineHeight = 24f, weight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// =============================================================================================
// Fund note / Logs section note (popup-fund-note)
// =============================================================================================

object NoteModal {
    var fund by mutableStateOf<String?>(null)
    var section by mutableStateOf<String?>(null)
    var text by mutableStateOf(TextFieldValue(""))
    var labelName by mutableStateOf("")
    var hasExisting by mutableStateOf(false)

    /** openFundNoteModal(fundName): the Fund Details note. */
    fun openFund(fundName: String?) {
        val f = fundName ?: AppUi.currentDetailsFund ?: return
        fund = f
        section = null
        val existing = AppStore.state.fundNotes[f] ?: ""
        populate(existing, fundDisplayName(f))
    }

    /** openSectionNoteModal(section): transactions | debts | receivables. */
    fun openSection(s: String) {
        section = s
        fund = null
        val existing = AppStore.state.logSectionNotes[s] ?: ""
        val title = when (s) {
            "debts" -> tr("logs_debts", "Debts")
            "receivables" -> tr("logs_receivables", "Receivables")
            else -> tr("logs_transactions", "Transactions")
        }
        populate(existing, title)
    }

    private fun populate(existing: String, label: String) {
        text = TextFieldValue(existing)
        labelName = label
        hasExisting = existing.isNotBlank()
        Popups.noteModal = true
    }

    fun close() { Popups.noteModal = false }

    /** saveFundNote() */
    fun save() {
        val t = text.text.trim()
        val state = AppStore.state
        val sec = section
        if (sec != null) {
            if (t.isNotEmpty()) state.logSectionNotes[sec] = t else state.logSectionNotes.remove(sec)
            close()
            AppStore.commit()
            return
        }
        val f = fund ?: return
        if (t.isNotEmpty()) state.fundNotes[f] = t else state.fundNotes.remove(f)
        close()
        AppStore.commit()
    }

    /** deleteFundNote(): confirm, then drop the note. */
    fun delete() {
        if (fund == null && section == null) return
        AppDialogs.confirm(
            message = tr("confirm_delete_fund_note_message", "This will permanently delete this note."),
            title = tr("confirm_delete_fund_note_title", "Delete This Note?"),
            okLabel = tr("common_yes_delete", "Yes, Delete"),
            cancelLabel = tr("common_cancel", "Cancel"),
            icon = "fa-eraser",
        ) { ok ->
            if (ok != true) return@confirm
            val state = AppStore.state
            val sec = section
            if (sec != null) state.logSectionNotes.remove(sec) else fund?.let { state.fundNotes.remove(it) }
            close()
            AppStore.commit()
        }
    }
}

@Composable
fun NoteModalPopup() {
    val c = AppTheme.colors
    PopupLayer(visible = Popups.noteModal, scrim = 0.6f) {
        val n = NoteModal
        GlassCard(radius = 24.dp, padding = 20.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.size(28.dp).background(c.bgWhite, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { NoteGlyph(11.dp, Brand.red) }
                        AppText(
                            text = tr("modal_fund_note_title", "Note"), size = 18f, lineHeight = 28f,
                            weight = FontWeight.ExtraBold, letterSpacingEm = -0.025f, color = c.textGray900,
                        )
                    }
                    if (n.hasExisting) {
                        val eraseShape = RoundedCornerShape(12.dp)
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(eraseShape)
                                .background(c.bgWhite, eraseShape)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { NoteModal.delete() },
                            contentAlignment = Alignment.Center,
                        ) { FaIcon("fa-eraser", 12.dp, Brand.red) }
                    }
                }
                AppText(
                    text = trf("modal_fund_note_related_label", "${n.labelName} related", "name" to n.labelName),
                    size = 10f, lineHeight = 15f, weight = FontWeight.Bold, letterSpacingEm = 0.05f,
                    color = c.textGray400, uppercase = true, modifier = Modifier.pullUp(4.dp),
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NoteBox(n)
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModalButton(tr("common_cancel", "Cancel"), ModalButtonKind.Secondary, Modifier.weight(1f)) { NoteModal.close() }
                        ModalButton(tr("common_save", "Save"), ModalButtonKind.Green, Modifier.weight(1f)) { NoteModal.save() }
                    }
                }
            }
        }
    }
}

/**
 * The note field: exactly 6 full lines tall (6 x 20 + padding + border), longer notes scroll inside it
 * and settle on a whole line; a soft fade shows at the top / bottom edge only WHILE scrolling.
 */
@Composable
private fun NoteBox(n: NoteModal) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val linePx = with(density) { 20.dp.toPx() }

    // snap to a line boundary 100 ms after the scrolling stops
    LaunchedEffect(scroll.isScrollInProgress) {
        if (!scroll.isScrollInProgress) {
            delay(100)
            val snapped = ((scroll.value / linePx).roundToInt() * linePx).roundToInt()
            if (kotlin.math.abs(snapped - scroll.value) > 1) scroll.animateScrollTo(snapped.coerceIn(0, scroll.maxValue))
        }
    }
    val atTop = scroll.value <= 2
    val atBottom = scroll.value >= scroll.maxValue - 2
    val topFade by animateFloatAsState(if (scroll.isScrollInProgress && !atTop) 1f else 0f, tween(300), label = "noteTopFade")
    val bottomFade by animateFloatAsState(if (scroll.isScrollInProgress && !atBottom) 1f else 0f, tween(300), label = "noteBottomFade")
    val fadeColor = if (c.isLight) Color(0xFFE4E4E7) else Color(0xFF1C1C1F)

    Box(
        Modifier
            .fillMaxWidth()
            .height(146.dp)
            .clip(shape)
            .background(c.bgWhite80, shape)
            .border(1.dp, if (focused) Brand.green else c.borderGray200, shape),
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp).verticalScroll(scroll)) {
            BasicTextField(
                value = n.text,
                onValueChange = { NoteModal.text = it },
                textStyle = appTextStyle(14f, c.textDefault, FontWeight.Medium, lineHeight = 20f),
                cursorBrush = SolidColor(c.textDefault),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            )
            if (n.text.text.isEmpty()) {
                AppText(
                    text = trf("modal_fund_note_placeholder_named", "Write a note about ${n.labelName}...", "name" to n.labelName),
                    size = 14f, lineHeight = 20f, weight = FontWeight.Medium, color = c.placeholder,
                )
            }
        }
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(32.dp).alpha(topFade)
                .background(Brush.verticalGradient(0.35f to fadeColor, 1f to fadeColor.copy(alpha = 0f))),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(32.dp).alpha(bottomFade)
                .background(Brush.verticalGradient(0f to fadeColor.copy(alpha = 0f), 0.65f to fadeColor)),
        )
    }
}

fun installNoteHooks() {
    AppActions.openFundNote = { NoteModal.openFund(null) }
    AppActions.openSectionNote = { s -> NoteModal.openSection(s) }
}
