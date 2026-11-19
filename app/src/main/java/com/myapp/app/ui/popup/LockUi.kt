package com.myapp.app.ui.popup

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import com.myapp.app.data.AppStore
import com.myapp.app.ui.AppActions
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.FinanceDecor
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.math.hypot

private val ShakeEase = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val RedError = Color(0xFFF87171)   // red-400

suspend fun sha256Hex(text: String): String = withContext(Dispatchers.Default) {
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

// =============================================================================================
// Biometric (BiometricPrompt)
// =============================================================================================

object BiometricHelper {
    var activity: FragmentActivity? = null

    fun isSupported(): Boolean {
        val a = activity ?: return false
        return BiometricManager.from(a).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    /** Shows the system fingerprint / face prompt; [onResult] gets true only on a successful scan. */
    fun authenticate(title: String, subtitle: String, description: String, onResult: (Boolean) -> Unit) {
        val a = activity
        if (a == null) { onResult(false); return }
        var done = false
        val prompt = BiometricPrompt(
            a, ContextCompat.getMainExecutor(a),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (!done) { done = true; onResult(true) }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!done) { done = true; onResult(false) }
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle.isNotEmpty()) setSubtitle(subtitle) }
            .setDescription(description)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText(tr("common_cancel", "Cancel"))
            .build()
        try {
            prompt.authenticate(info)
        } catch (_: Throwable) {
            if (!done) { done = true; onResult(false) }
        }
    }
}

// =============================================================================================
// State of the three full-screen lock UIs
// =============================================================================================

object LockState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ---------------------------------------------------------------- PIN pad
    var pinVisible by mutableStateOf(false)
    private var pinMode: String? = null                 // setup1 | setup2 | verify
    var pinBuffer by mutableStateOf("")
    private var pinFirst = ""
    private var pinOnSuccess: (suspend (String) -> Unit)? = null
    private var pinOnCancel: (() -> Unit)? = null
    private var pinCustomTitle = ""
    private var pinCustomSubtitle = ""
    var pinAllowCancel by mutableStateOf(true)
    var pinTitle by mutableStateOf("")
    var pinSubtitle by mutableStateOf("")
    var pinError by mutableStateOf("")
    var pinErrorState by mutableStateOf(false)         // the 4 dots held solid red
    var pinShake by mutableIntStateOf(0)

    fun openPinPad(
        mode: String,
        allowCancel: Boolean = true,
        title: String = "",
        subtitle: String = "",
        onCancel: (() -> Unit)? = null,
        onSuccess: (suspend (String) -> Unit)? = null,
    ) {
        pinMode = mode
        pinBuffer = ""
        pinFirst = ""
        pinOnSuccess = onSuccess
        pinOnCancel = onCancel
        pinCustomTitle = title
        pinCustomSubtitle = subtitle
        pinAllowCancel = allowCancel
        pinError = ""
        pinErrorState = false
        updatePinTitle()
        pinVisible = true
    }

    private fun updatePinTitle() {
        when (pinMode) {
            "setup1" -> {
                pinTitle = tr("pin_setup_title", "Set a 4-digit PIN")
                pinSubtitle = tr("pin_setup_subtitle", "You'll need this PIN to unlock the app - don't forget it.")
            }
            "setup2" -> {
                pinTitle = tr("pin_confirm_title", "Confirm your PIN")
                pinSubtitle = tr("pin_confirm_subtitle", "Enter the same PIN again.")
            }
            else -> {
                pinTitle = pinCustomTitle.ifEmpty { tr("pin_enter_title", "Enter PIN") }
                pinSubtitle = pinCustomSubtitle.ifEmpty { tr("biometric_verify_continue_desc", "Verify your identity to continue") }
            }
        }
    }

    fun pinPress(d: Int) {
        if (pinBuffer.length >= 4) return
        pinError = ""
        pinBuffer += d.toString()
        if (pinBuffer.length == 4) scope.launch { delay(150); processPin() }
    }

    fun pinBackspace() {
        if (pinBuffer.isEmpty()) return
        pinBuffer = pinBuffer.dropLast(1)
    }

    private suspend fun processPin() {
        when (pinMode) {
            "setup1" -> {
                pinFirst = pinBuffer
                pinBuffer = ""
                pinMode = "setup2"
                updatePinTitle()
            }
            "setup2" -> {
                if (pinBuffer != pinFirst) {
                    pinError = tr("pin_mismatch_error", "PIN didn't match. Let's start over.")
                    pinErrorState = true
                    pinShake++
                    pinFirst = ""
                    pinMode = "setup1"
                    delay(900)
                    pinBuffer = ""
                    pinErrorState = false
                    updatePinTitle()
                    return
                }
                val pin = pinBuffer
                val cb = pinOnSuccess
                closePin(false)
                cb?.invoke(pin)
            }
            "verify" -> {
                val hash = sha256Hex(pinBuffer)
                if (hash == AppStore.state.lockPinHash) {
                    val cb = pinOnSuccess
                    closePin(false)
                    cb?.invoke("")
                } else {
                    pinError = tr("pin_incorrect_error", "Incorrect PIN. Try again.")
                    pinErrorState = true
                    pinShake++
                    delay(450)
                    pinBuffer = ""
                    pinErrorState = false
                }
            }
        }
    }

    fun closePin(cancelled: Boolean) {
        pinVisible = false
        pinMode = null
        if (cancelled) pinOnCancel?.invoke()
    }

    // ---------------------------------------------------------------- pattern pad
    var patternVisible by mutableStateOf(false)
    private var patternMode: String? = null
    var patternBuffer by mutableStateOf<List<Int>>(emptyList())
    private var patternFirst: List<Int> = emptyList()
    private var patternOnSuccess: (suspend (List<Int>) -> Unit)? = null
    private var patternOnCancel: (() -> Unit)? = null
    private var patternCustomTitle = ""
    private var patternCustomSubtitle = ""
    var patternAllowCancel by mutableStateOf(true)
    var patternTitle by mutableStateOf("")
    var patternSubtitle by mutableStateOf("")
    var patternError by mutableStateOf("")
    var patternShake by mutableIntStateOf(0)
    var patternLocked by mutableStateOf(false)         // blocks a new touch during the error shake
    var patternDrawing by mutableStateOf(false)
    var patternLive by mutableStateOf<Offset?>(null)   // finger position in 0..300 units

    fun openPatternPad(
        mode: String,
        allowCancel: Boolean = true,
        title: String = "",
        subtitle: String = "",
        onCancel: (() -> Unit)? = null,
        onSuccess: (suspend (List<Int>) -> Unit)? = null,
    ) {
        patternMode = mode
        patternBuffer = emptyList()
        patternFirst = emptyList()
        patternLocked = false
        patternOnSuccess = onSuccess
        patternOnCancel = onCancel
        patternCustomTitle = title
        patternCustomSubtitle = subtitle
        patternAllowCancel = allowCancel
        patternError = ""
        patternLive = null
        updatePatternTitle()
        patternVisible = true
    }

    private fun updatePatternTitle() {
        when (patternMode) {
            "setup1" -> {
                patternTitle = tr("pattern_setup_title", "Draw a pattern")
                patternSubtitle = tr("pattern_setup_subtitle", "Connect at least 4 dots. You'll need this to unlock the app.")
            }
            "setup2" -> {
                patternTitle = tr("pattern_confirm_title", "Confirm your pattern")
                patternSubtitle = tr("pattern_confirm_subtitle", "Draw the same pattern again.")
            }
            else -> {
                patternTitle = patternCustomTitle.ifEmpty { tr("pattern_draw_title", "Draw Pattern") }
                patternSubtitle = patternCustomSubtitle.ifEmpty { tr("biometric_verify_continue_desc", "Verify your identity to continue") }
            }
        }
    }

    val patternPoints: List<Offset> = listOf(
        Offset(50f, 50f), Offset(150f, 50f), Offset(250f, 50f),
        Offset(50f, 150f), Offset(150f, 150f), Offset(250f, 150f),
        Offset(50f, 250f), Offset(150f, 250f), Offset(250f, 250f),
    )

    private fun nearestDot(pt: Offset): Int {
        var best = -1
        var bestDist = 30f
        patternPoints.forEachIndexed { i, p ->
            val d = hypot(p.x - pt.x, p.y - pt.y)
            if (d < bestDist) { bestDist = d; best = i }
        }
        return best
    }

    fun patternDown(pt: Offset) {
        if (patternLocked) return
        patternDrawing = true
        patternError = ""
        val idx = nearestDot(pt)
        patternBuffer = if (idx >= 0) listOf(idx) else emptyList()
        patternLive = pt
    }

    fun patternMove(pt: Offset) {
        if (!patternDrawing) return
        val idx = nearestDot(pt)
        if (idx >= 0 && idx !in patternBuffer) patternBuffer = patternBuffer + idx
        patternLive = pt
    }

    fun patternUp() {
        if (!patternDrawing) return
        patternDrawing = false
        patternLive = null
        scope.launch { processPattern() }
    }

    private suspend fun processPattern() {
        if (patternBuffer.size < 4) {
            patternError = tr("pattern_min_dots_error", "Connect at least 4 dots.")
            patternBuffer = emptyList()
            return
        }
        when (patternMode) {
            "setup1" -> {
                patternFirst = patternBuffer
                patternBuffer = emptyList()
                patternMode = "setup2"
                updatePatternTitle()
            }
            "setup2" -> {
                if (patternBuffer != patternFirst) {
                    patternError = tr("pattern_mismatch_error", "Pattern didn't match. Let's start over.")
                    patternShake++
                    patternLocked = true
                    patternFirst = emptyList()
                    patternMode = "setup1"
                    delay(900)
                    patternBuffer = emptyList()
                    patternLocked = false
                    updatePatternTitle()
                    return
                }
                val pattern = patternBuffer
                val cb = patternOnSuccess
                closePattern(false)
                cb?.invoke(pattern)
            }
            "verify" -> {
                val hash = sha256Hex(patternBuffer.joinToString("-"))
                if (hash == AppStore.state.lockPatternHash) {
                    val cb = patternOnSuccess
                    closePattern(false)
                    cb?.invoke(emptyList())
                } else {
                    patternError = tr("pattern_incorrect_error", "Pattern did not match. Try again.")
                    patternShake++
                    patternLocked = true
                    delay(450)
                    patternBuffer = emptyList()
                    patternLocked = false
                }
            }
        }
    }

    fun closePattern(cancelled: Boolean) {
        patternVisible = false
        patternMode = null
        patternLive = null
        patternDrawing = false
        if (cancelled) patternOnCancel?.invoke()
    }

    // ---------------------------------------------------------------- biometric lock screen
    var biometricVisible by mutableStateOf(false)
    var biometricError by mutableStateOf(false)
    private var biometricAttempt = 0

    fun showBiometricLock() {
        biometricVisible = true
        biometricError = false
        biometricAttempt++
    }

    /** The big "Unlock" button / the automatic first prompt. */
    fun verifyBiometricUnlock() {
        biometricError = false
        BiometricHelper.authenticate(
            title = tr("biometric_unlock_title", "Unlock Tracker"), subtitle = "",
            description = tr("biometric_verify_continue_desc", "Verify your identity to continue"),
        ) { ok -> if (ok) biometricVisible = false else biometricError = true }
    }

    val biometricAutoPromptKey: Int get() = biometricAttempt

    // ---------------------------------------------------------------- start of the app
    /** Called once when the splash is going away on a normal launch (not during onboarding). */
    fun showStartupLock() {
        when (AppStore.state.lockType) {
            "biometric" -> showBiometricLock()
            "pin" -> openPinPad(
                "verify", allowCancel = false,
                title = tr("lock_title", "Tracker is Locked"), subtitle = tr("lock_pin_subtitle", "Enter your PIN to continue"),
                onSuccess = {},
            )
            "pattern" -> openPatternPad(
                "verify", allowCancel = false,
                title = tr("lock_title", "Tracker is Locked"), subtitle = tr("lock_pattern_subtitle", "Draw your pattern to continue"),
                onSuccess = {},
            )
        }
    }

    // ---------------------------------------------------------------- verify the CURRENT lock (before changing it)
    /** verifyCurrentLock(reason): runs whichever check the active lock type needs, then [onResult]. */
    fun verifyCurrentLock(reason: String, onResult: (Boolean) -> Unit) {
        when (AppStore.state.lockType) {
            "biometric" -> BiometricHelper.authenticate(
                title = tr("lock_verify_identity_title", "Verify Identity"), subtitle = "", description = reason, onResult = onResult,
            )
            "pin" -> openPinPad(
                "verify", allowCancel = true, title = tr("pin_enter_title", "Enter PIN"), subtitle = reason,
                onCancel = { onResult(false) }, onSuccess = { onResult(true) },
            )
            "pattern" -> openPatternPad(
                "verify", allowCancel = true, title = tr("pattern_draw_title", "Draw Pattern"), subtitle = reason,
                onCancel = { onResult(false) }, onSuccess = { onResult(true) },
            )
            else -> onResult(true)
        }
    }

    // ---------------------------------------------------------------- lock type chooser
    fun openChooser() { Popups.lockType = true }
    fun closeChooser() { Popups.lockType = false }

    /** handleLockTypeChoice(target) */
    fun chooseLockType(target: String) {
        val state = AppStore.state
        val current = state.lockType
        if (target == current) { closeChooser(); return }
        closeChooser()
        if (current != "none") {
            verifyCurrentLock(tr("lock_confirm_change_reason", "Confirm to change screen lock")) { ok ->
                if (!ok) openChooser() else applyChoice(target)
            }
        } else applyChoice(target)
    }

    private fun applyChoice(target: String) {
        val state = AppStore.state
        when (target) {
            "none" -> {
                state.lockType = "none"
                state.biometricLockEnabled = false
                state.lockPinHash = null
                state.lockPatternHash = null
                AppStore.commit()
            }
            "biometric" -> {
                if (!BiometricHelper.isSupported()) {
                    AppDialogs.alert(tr("alert_biometric_not_supported", "Your device doesn't support biometric authentication."))
                    return
                }
                // make sure the person can really authenticate BEFORE the lock is switched on
                BiometricHelper.authenticate(
                    title = tr("biometric_enable_title", "Enable Biometric Lock"),
                    subtitle = tr("onboard_app_name", "Tracker"),
                    description = tr("biometric_enable_desc", "Verify your identity to protect app access"),
                ) { ok ->
                    if (ok) {
                        state.lockType = "biometric"
                        state.biometricLockEnabled = true
                        state.lockPinHash = null
                        state.lockPatternHash = null
                        AppStore.commit()
                    } else {
                        AppDialogs.alert(tr("alert_biometric_setup_failed", "Couldn't set up biometric lock. Please try again."))
                    }
                }
            }
            "pin" -> openPinPad("setup1", allowCancel = true) { pin ->
                state.lockType = "pin"
                state.biometricLockEnabled = false
                state.lockPinHash = sha256Hex(pin)
                state.lockPatternHash = null
                AppStore.commit()
                AppDialogs.alert(
                    tr("alert_pin_set", "Your PIN is set. Remember it - there is no way to recover a forgotten PIN except resetting the app data."),
                    icon = "fa-circle-check",
                )
            }
            "pattern" -> openPatternPad("setup1", allowCancel = true) { pattern ->
                state.lockType = "pattern"
                state.biometricLockEnabled = false
                state.lockPatternHash = sha256Hex(pattern.joinToString("-"))
                state.lockPinHash = null
                AppStore.commit()
                AppDialogs.alert(
                    tr("alert_pattern_set", "Your pattern is set. Remember it - there is no way to recover a forgotten pattern except resetting the app data."),
                    icon = "fa-circle-check",
                )
            }
        }
    }
}

// =============================================================================================
// Screens
// =============================================================================================

/** The full-screen background of the lock screens: gradient in day mode, pure black in dark mode. */
@Composable
private fun LockBackground(content: @Composable () -> Unit) {
    val c = AppTheme.colors
    val bg = if (c.isLight) Modifier.background(Brush.verticalGradient(listOf(Color(0xFFF9FAFB), Color(0xFFF9FAFB), Color(0xFFFEF2F2))))
    else Modifier.background(Color.Black)
    Box(
        Modifier
            .fillMaxSize()
            .then(bg)
            // everything below is blocked while a lock screen is up
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
    ) {
        FinanceDecor()
        content()
    }
}

@Composable
private fun CancelButton(onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier
            .padding(start = 24.dp, top = 24.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(if (c.isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.10f), CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { FaIcon("fa-arrow-left", 14.dp, if (c.isLight) Color(0xFF3F3F46) else Color.White) }
}

@Composable
private fun LockTitleColor(): Color = if (AppTheme.colors.isLight) Color(0xFF18181B) else Color.White

@Composable
private fun LockHeader(icon: String, circle: Int, iconSize: Int, title: String, titleSize: Float, titleLine: Float, subtitle: String, subSize: Float, subLine: Float) {
    val c = AppTheme.colors
    Box(
        Modifier.size(circle.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { FaIcon(icon, iconSize.dp, Brand.red) }
    AppText(
        text = title, size = titleSize, lineHeight = titleLine, weight = FontWeight.Bold, color = LockTitleColor(),
        align = TextAlign.Center, modifier = Modifier.padding(top = 20.dp),
    )
    AppText(
        text = subtitle, size = subSize, lineHeight = subLine, weight = FontWeight.Medium, color = c.textGray400,
        align = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, start = 16.dp, end = 16.dp),
    )
}

@Composable
fun PinPadScreen() {
    if (!LockState.pinVisible) return
    val s = LockState
    BackHandler(enabled = true) { if (s.pinAllowCancel) s.closePin(true) }
    LockBackground {
        if (s.pinAllowCancel) {
            Box(Modifier.align(Alignment.TopStart)) { CancelButton { s.closePin(true) } }
        }
        Column(
            Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(Modifier.widthIn(max = 320.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                LockHeader("fa-lock", 64, 24, s.pinTitle, 18f, 28f, s.pinSubtitle, 12f, 16f)

                // 4 dots (the whole row shakes on a wrong PIN)
                val shakeX = remember { Animatable(0f) }
                LaunchedEffect(s.pinShake) {
                    if (s.pinShake > 0) shake(shakeX)
                }
                Row(
                    Modifier.padding(top = 28.dp, bottom = 4.dp).offset(x = shakeX.value.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (i in 0 until 4) {
                        val showRed = i < s.pinBuffer.length || s.pinErrorState
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (showRed) Brand.red else Color.Transparent, CircleShape)
                                .border(2.dp, if (s.pinErrorState) Brand.red else Color(0xFF991B1B).copy(alpha = 0.40f), CircleShape),
                        )
                    }
                }
                AppText(
                    text = s.pinError, size = 11f, lineHeight = 16.5f, weight = FontWeight.Bold, color = RedError,
                    align = TextAlign.Center, modifier = Modifier.padding(top = 8.dp).height(16.dp),
                )

                // keypad
                Column(
                    Modifier.widthIn(max = 260.dp).fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { d -> Box(Modifier.weight(1f)) { PinKey(d.toString()) { s.pinPress(d) } } }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f))
                        Box(Modifier.weight(1f)) { PinKey("0") { s.pinPress(0) } }
                        Box(Modifier.weight(1f)) { PinKey(null) { s.pinBackspace() } }
                    }
                }
            }
        }
    }
}

/** One round key: black 45 %, turns red 55 % while pressed. */
@Composable
private fun PinKey(label: String?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .widthIn(max = 68.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (pressed) Brand.red.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.45f), CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            AppText(text = label, size = 16.8f, lineHeight = 25f, weight = FontWeight.Bold, color = Color.White)
        } else {
            FaIcon("fa-delete-left", 16.dp, Color.White)
        }
    }
}

/** lockEntryShake: 0 -> -8 -> 7 -> -5 -> 4 -> 0 over 450 ms. */
private suspend fun shake(x: Animatable<Float, *>) {
    for (v in listOf(-8f, 7f, -5f, 4f, 0f)) x.animateTo(v, tween(90, easing = ShakeEase))
}

@Composable
fun PatternPadScreen() {
    if (!LockState.patternVisible) return
    val s = LockState
    BackHandler(enabled = true) { if (s.patternAllowCancel) s.closePattern(true) }
    LockBackground {
        if (s.patternAllowCancel) {
            Box(Modifier.align(Alignment.TopStart)) { CancelButton { s.closePattern(true) } }
        }
        Column(
            Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(Modifier.widthIn(max = 320.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                LockHeader("fa-draw-polygon", 64, 24, s.patternTitle, 18f, 28f, s.patternSubtitle, 12f, 16f)
                AppText(
                    text = s.patternError, size = 11f, lineHeight = 16.5f, weight = FontWeight.Bold, color = RedError,
                    align = TextAlign.Center, modifier = Modifier.padding(top = 8.dp).height(16.dp),
                )

                val shakeX = remember { Animatable(0f) }
                LaunchedEffect(s.patternShake) {
                    if (s.patternShake > 0) shake(shakeX)
                }
                BoxWithConstraints(Modifier.padding(top = 12.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, 300.dp)
                    Canvas(
                        Modifier
                            .size(side)
                            .offset(x = shakeX.value.dp)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(pass = PointerEventPass.Main)
                                    val k = 300f / size.width
                                    down.consume()
                                    LockState.patternDown(Offset(down.position.x * k, down.position.y * k))
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                        val ch = event.changes.firstOrNull() ?: break
                                        if (!ch.pressed) { ch.consume(); break }
                                        LockState.patternMove(Offset(ch.position.x * k, ch.position.y * k))
                                        ch.consume()
                                    }
                                    LockState.patternUp()
                                },
                            },
                    ) {
                        val k = size.width / 300f
                        val pts = s.patternPoints
                        val lineStroke = Stroke(width = 8f * k, cap = StrokeCap.Round)
                        val buf = s.patternBuffer
                        for (i in 0 until buf.size - 1) {
                            val a = pts[buf[i]]; val b = pts[buf[i + 1]]
                            drawLine(Brand.red, Offset(a.x * k, a.y * k), Offset(b.x * k, b.y * k), strokeWidth = lineStroke.width, cap = StrokeCap.Round)
                        }
                        val live = s.patternLive
                        if (live != null && buf.isNotEmpty()) {
                            val last = pts[buf.last()]
                            drawLine(Brand.red, Offset(last.x * k, last.y * k), Offset(live.x * k, live.y * k), strokeWidth = lineStroke.width, cap = StrokeCap.Round)
                        }
                        pts.forEachIndexed { i, p ->
                            val active = i in buf
                            drawCircle(if (active) Brand.red else Color.Black.copy(alpha = 0.45f), radius = 11f * k, center = Offset(p.x * k, p.y * k))
                            drawCircle(
                                if (active) Brand.red else Color.White.copy(alpha = 0.2f), radius = 11f * k,
                                center = Offset(p.x * k, p.y * k), style = Stroke(width = 2f * k),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BiometricLockScreen() {
    if (!LockState.biometricVisible) return
    val s = LockState
    BackHandler(enabled = true) { }
    // the system prompt opens by itself shortly after this screen appears
    LaunchedEffect(s.biometricAutoPromptKey) {
        delay(450)
        if (LockState.biometricVisible) LockState.verifyBiometricUnlock()
    }
    LockBackground {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val c = AppTheme.colors
            Box(
                Modifier.size(80.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { FaIcon("fa-fingerprint", 30.dp, Brand.red) }
            AppText(
                text = tr("lock_title", "Tracker is Locked"), size = 20f, lineHeight = 28f, weight = FontWeight.Bold,
                color = LockTitleColor(), align = TextAlign.Center, modifier = Modifier.padding(top = 24.dp),
            )
            AppText(
                text = tr("biometric_verify_continue_desc", "Verify your identity to continue"), size = 14f, lineHeight = 20f,
                weight = FontWeight.Medium, color = c.textGray400, align = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 32.dp),
            )
            val btnShape = RoundedCornerShape(16.dp)
            Row(
                Modifier
                    .shadow(8.dp, btnShape)
                    .clip(btnShape)
                    .background(Brand.red, btnShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { LockState.verifyBiometricUnlock() }
                    .padding(horizontal = 32.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FaIcon("fa-fingerprint", 14.dp, Color.White)
                AppText(text = tr("biometric_unlock_btn", "Unlock"), size = 14f, lineHeight = 20f, weight = FontWeight.Bold, color = Color.White)
            }
            if (s.biometricError) {
                AppText(
                    text = tr("biometric_verify_failed", "Verification failed. Try again."), size = 11f, lineHeight = 16.5f,
                    weight = FontWeight.Medium, color = RedError, align = TextAlign.Center, modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

/** All three lock UIs, drawn above everything (the PIN / pattern pads sit above the biometric screen). */
@Composable
fun LockHost() {
    BiometricLockScreen()
    PinPadScreen()
    PatternPadScreen()
}

// =============================================================================================
// Lock type chooser (popup-lock-type)
// =============================================================================================

@Composable
fun LockTypeChooserPopup() {
    val c = AppTheme.colors
    PopupLayer(visible = Popups.lockType, scrim = 0.6f) {
        val state = AppStore.observe()
        GlassCard(radius = 28.dp, padding = 24.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PopupHeaderIcon("fa-lock", 32.dp, 14.dp, Brand.red)
                    AppText(
                        text = tr("modal_lock_type_title", "Screen Lock Type"), size = 18f, lineHeight = 28f,
                        weight = FontWeight.ExtraBold, letterSpacingEm = -0.025f, color = c.textGray900,
                    )
                }
                AppText(
                    text = tr("modal_lock_type_subtitle", "Choose how to protect your app"), size = 10f, lineHeight = 15f,
                    weight = FontWeight.Bold, letterSpacingEm = 0.05f, color = c.textGray400, uppercase = true,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val types = listOf(
                        Triple("none", "fa-lock-open", tr("profile_lock_off", "Off")),
                        Triple("biometric", "fa-fingerprint", tr("lock_type_fingerprint", "Fingerprint")),
                        Triple("pin", "fa-hashtag", tr("lock_type_pin", "PIN")),
                        Triple("pattern", "fa-draw-polygon", tr("lock_type_pattern", "Pattern")),
                    )
                    types.forEach { (key, icon, label) ->
                        val selected = key == state.lockType
                        val shape = RoundedCornerShape(16.dp)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(if (selected) Brand.red.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.03f), shape)
                                .border(2.dp, if (selected) Brand.red else Color.Transparent, shape)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    LockState.chooseLockType(key)
                                }
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    Modifier.size(36.dp).shadow(1.dp, CircleShape).background(c.bgWhite, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { FaIcon(icon, 14.dp, Brand.red) }
                                AppText(text = label, size = 14f, lineHeight = 20f, weight = FontWeight.Bold, color = c.textGray800)
                            }
                            if (selected) FaIcon("fa-check", 14.dp, Brand.red)
                        }
                    }
                }
                ModalButton(tr("common_cancel", "Cancel"), ModalButtonKind.Secondary, Modifier.fillMaxWidth()) { LockState.closeChooser() }
            }
        }
    }
}

fun installLockHooks() {
    AppActions.openLockTypeChooser = { LockState.openChooser() }
}
