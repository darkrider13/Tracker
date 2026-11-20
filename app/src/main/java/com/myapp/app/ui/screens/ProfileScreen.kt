package com.myapp.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.myapp.app.ui.components.pageScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate as rotateDraw
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myapp.app.data.AppSession
import com.myapp.app.data.AppStore
import com.myapp.app.ui.AppActions
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.AppSwitch
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.FaStyle
import com.myapp.app.ui.components.appTextStyle
import com.myapp.app.ui.popup.AvatarPicker
import com.myapp.app.ui.popup.BackupUi
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.getAvatarOption
import com.myapp.app.util.tr
import com.myapp.app.util.localizeDigits
import com.myapp.app.util.trf
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import com.myapp.app.ui.popup.AppDialogs
import com.myapp.app.ui.theme.Tw
import com.myapp.app.util.UpdateChecker
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import com.myapp.app.ui.components.SoftGlass

/** updateLockTypeRow(): icon + label of the current screen lock. */
private fun lockTypeIcon(type: String) = when (type) {
    "biometric" -> "fa-fingerprint"
    "pin" -> "fa-hashtag"
    "pattern" -> "fa-draw-polygon"
    else -> "fa-lock-open"
}

private fun lockTypeLabel(type: String) = when (type) {
    "biometric" -> tr("lock_type_fingerprint", "Fingerprint")
    "pin" -> tr("lock_type_pin", "PIN")
    "pattern" -> tr("lock_type_pattern", "Pattern")
    else -> tr("profile_lock_off", "Off")
}

fun openExternalLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

@Composable
fun ProfileScreen() {
    val state = AppStore.observe()
    @Suppress("UNUSED_VARIABLE")
    val lang = AppSession.lang
    val c = AppTheme.colors
    val context = LocalContext.current

    val pageScrollState = androidx.compose.foundation.rememberScrollState()
    Box(Modifier.fillMaxSize().pageScroll(pageScrollState), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 448.dp)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    AppText(
                        text = tr("profile_title", "Personal Profile"), size = 24f, lineHeight = 32f,
                        weight = FontWeight.Bold, letterSpacingEm = -0.025f, color = c.textGray900,
                    )
                    AppText(
                        text = tr("profile_subtitle", "Customize settings, security, and developer details."),
                        size = 12f, lineHeight = 16f, color = c.textGray500, modifier = Modifier.padding(top = 2.dp),
                    )
                }
                UpdateButton()
            }

            NameBox()

            // ---- Day Mode
            SettingRow(
                icon = { SunIcon() },
                title = tr("profile_lightmode", "Day Mode"),
                desc = tr("profile_lightmode_desc", "Enable day mode."),
            ) {
                AppSwitch(checked = AppSession.isLight, onCheckedChange = { AppSession.setLight(context, it) })
            }

            // ---- Language
            SettingRow(
                icon = { FaIcon("fa-language", 18.dp, Brand.red) },
                title = tr("profile_language", "Language"),
                desc = tr("profile_language_desc", "Change the app language En → বাং"),
            ) {
                AppSwitch(checked = AppSession.lang == "bn", onCheckedChange = { AppSession.setLang(context, if (it) "bn" else "en") })
            }

            // ---- Screen lock type
            val shape = RoundedCornerShape(if (c.isLight) 24.dp else SoftGlass.radius)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (c.isLight) c.bgGray200a60 else SoftGlass.bg, shape)
                    .border(1.dp, if (c.isLight) c.homeCardBorder else SoftGlass.border, shape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        AppActions.openLockTypeChooser()
                    }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WhiteCircle { FaIcon(lockTypeIcon(state.lockType), 18.dp, Brand.red) }
                    Column {
                        AppText(text = tr("profile_lockscreen", "Screen Lock Type"), size = 14f, lineHeight = 20f, weight = FontWeight.Bold, color = c.textGray800)
                        AppText(text = tr("profile_lockscreen_desc", "Protect your app access"), size = 10f, lineHeight = 15f, weight = FontWeight.Medium, color = c.textGray400)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(text = lockTypeLabel(state.lockType), size = 12f, lineHeight = 16f, weight = FontWeight.Bold, color = c.textGray500)
                    FaIcon("fa-chevron-right", 12.dp, c.textGray400)
                }
            }

            // ---- Backup & Restore
            Column(
                Modifier.fillMaxWidth().clip(shape).background(if (c.isLight) c.bgGray200a60 else SoftGlass.bg, shape)
                    .border(1.dp, if (c.isLight) c.homeCardBorder else SoftGlass.border, shape).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppText(text = tr("profile_backup_title", "Backup & Restore"), size = 14f, lineHeight = 20f, weight = FontWeight.Bold, color = c.textGray800)
                AppText(
                    text = tr("profile_backup_desc", "Backup and restore your full transaction history and settings."),
                    size = 10f, lineHeight = 15f, weight = FontWeight.Medium, color = c.textGray400,
                    modifier = Modifier.pullUp(6.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BackupButton("fa-file-export", tr("profile_export", "Export"), Modifier.weight(1f)) { BackupUi.export(context) }
                    BackupButton("fa-file-import", tr("profile_import", "Import"), Modifier.weight(1f)) { BackupUi.pickFile() }
                }
            }

            // ---- Developer credit
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppText(
                    text = tr("profile_developed_by", "Developed by MD.INSANUL ISLAM ABIR"), size = 11.5f, lineHeight = 17f,
                    weight = FontWeight.Bold, color = c.textGray400, align = TextAlign.Center,
                    modifier = Modifier.pullUp(8.dp),
                )
                Row(Modifier.pullUp(8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    SocialIcon("fa-facebook-f", FaStyle.Brands, 23.dp) { openExternalLink(context, "https://m.facebook.com/insanul.islam.abirr/") }
                    SocialIcon("fa-whatsapp", FaStyle.Brands, 23.dp) { openExternalLink(context, "https://wa.me/8801516531459") }
                    SocialIcon("fa-instagram", FaStyle.Brands, 23.dp) { openExternalLink(context, "https://instagram.com/insaanulislaamaabiir") }
                    SocialIcon("fa-paper-plane", FaStyle.Solid, 21.dp) { openExternalLink(context, "https://t.me/insanul13") }
                }
                AppText(
                    text = tr("profile_copyright", "© Copyright MD.INSANUL ISLAM ABIR. All rights reserved."), size = 6.5f, lineHeight = 10f,
                    weight = FontWeight.Bold, color = c.textGray400, align = TextAlign.Center,
                    modifier = Modifier.pullUp(12.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ pieces

/** "Check for update" icon in the page header (no dot: tapping it tells you whether a newer version exists). */
@Composable
private fun UpdateButton() {
    val c = AppTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // icon rotation (degrees); every spin ends on a full turn, so resetting to 0 afterwards is invisible
    val angle = remember { Animatable(0f) }
    Box(
        Modifier
            .padding(start = 12.dp)
            // same height as the headline's line (32dp) so both share one centre line; the small lift
            // matches where the headline's glyphs visually sit inside that line
            .width(36.dp)
            .height(32.dp)
            .offset(y = (-1.5).dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (UpdateChecker.checking) return@clickable
                UpdateChecker.checking = true
                scope.launch {
                    // check in the background while the icon spins smoothly 3 times, then show the popup
                    val check = async { UpdateChecker.check(context) }
                    angle.snapTo(0f)
                    angle.animateTo(1080f, tween(1800, easing = FastOutSlowInEasing))
                    // slow connection: keep turning (whole turns only) until the result is in
                    while (!check.isCompleted) {
                        angle.snapTo(0f)
                        angle.animateTo(360f, tween(700, easing = LinearEasing))
                    }
                    val result = check.await()
                    angle.snapTo(0f)
                    UpdateChecker.checking = false
                    val current = UpdateChecker.installedName(context)
                    when (result) {
                        is UpdateChecker.Result.Available -> AppDialogs.confirm(
                            message = tr("update_found_msg", "A new version is available.") +
                                "\n" + tr("update_current_label", "Current version") + ": " + current +
                                (result.versionName?.let { "\n" + tr("update_new_label", "New version") + ": " + it } ?: ""),
                            title = tr("update_found_title", "Update available"),
                            okLabel = tr("update_download", "Download"),
                            cancelLabel = tr("update_later", "Later"),
                            icon = "fa-circle-check",
                        ) { ok -> if (ok == true) UpdateChecker.startUpdate(context, result.url) }
                        is UpdateChecker.Result.UpToDate -> AppDialogs.alert(
                            message = tr("update_latest_msg", "You are using version %s.").replace("%s", current),
                            title = tr("update_latest_title", "You have the latest version"),
                            icon = "fa-circle-check",
                        )
                        is UpdateChecker.Result.Failed -> AppDialogs.alert(
                            message = tr("update_check_retry_msg", "Please try again after some time."),
                            title = tr("update_failed_title", "Could not check for updates"),
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        FaIcon(
            "fa-arrows-rotate", 16.dp, c.textGray900,
            modifier = Modifier.rotate(angle.value),
        )
    }
}

@Composable
private fun WhiteCircle(content: @Composable () -> Unit) {
    val c = AppTheme.colors
    Box(Modifier.size(40.dp).clip(CircleShape).background(c.bgWhite, CircleShape), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun SettingRow(icon: @Composable () -> Unit, title: String, desc: String, trailing: @Composable () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(if (c.isLight) 24.dp else SoftGlass.radius)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (c.isLight) c.bgGray200a60 else SoftGlass.bg, shape)
            .border(1.dp, if (c.isLight) c.homeCardBorder else SoftGlass.border, shape).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WhiteCircle { icon() }
            Column {
                AppText(text = title, size = 14f, lineHeight = 20f, weight = FontWeight.Bold, color = c.textGray800)
                AppText(text = desc, size = 10f, lineHeight = 15f, weight = FontWeight.Medium, color = c.textGray400)
            }
        }
        trailing()
    }
}

/** The orange / yellow sun of the Day Mode row (22 dp, drawn from the old 48x48 SVG). */
@Composable
private fun SunIcon() {
    Canvas(Modifier.size(22.dp)) {
        val k = size.width / 48f
        val orange = Color(0xFFFF9800)
        // square 1: x 11 y 11, 26 x 26
        drawRect(orange, topLeft = Offset(11f * k, 11f * k), size = Size(26f * k, 26f * k))
        // square 2: the same square turned 45 degrees around the centre
        rotateDraw(degrees = 45f, pivot = Offset(24f * k, 24f * k)) {
            drawRect(orange, topLeft = Offset(11f * k, 11f * k), size = Size(26f * k, 26f * k))
        }
        drawCircle(Color(0xFFFFEB3B), radius = 11f * k, center = Offset(24f * k, 24f * k))
    }
}

@Composable
private fun BackupButton(icon: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .clip(shape)
            .background(c.bgWhite, shape)
            .border(1.dp, if (c.isLight) c.homeCardBorder else c.borderGray300, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaIcon(icon, 12.dp, c.textGray700)
        AppText(text = label, size = 12f, lineHeight = 16f, weight = FontWeight.Bold, color = c.textGray700)
    }
}

@Composable
private fun SocialIcon(icon: String, style: FaStyle, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { FaIcon(icon, size, Brand.red, style = style) }
}

// ------------------------------------------------------------------ name box

@Composable
private fun NameBox() {
    val state = AppStore.observe()
    val c = AppTheme.colors
    val shape = RoundedCornerShape(if (c.isLight) 24.dp else SoftGlass.radius)
    val avatar = getAvatarOption(state.profileAvatar)

    Box(
        Modifier.fillMaxWidth().clip(shape).background(if (c.isLight) c.bgGray200a60 else SoftGlass.bg, shape)
            .border(1.dp, if (c.isLight) c.homeCardBorder else SoftGlass.border, shape),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // avatar with a small pencil badge (tap = choose avatar)
            Box(Modifier.size(56.dp)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(c.bgGray100, CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { AvatarPicker.open() },
                    contentAlignment = Alignment.Center,
                ) { FaIcon(avatar.icon, 22.dp, avatar.color) }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 3.dp, y = 3.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(avatar.color.copy(alpha = 0.22f), CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { AvatarPicker.open() },
                    contentAlignment = Alignment.Center,
                ) { FaIcon("fa-pen", 9.dp, avatar.color) }
            }

            Column(Modifier.weight(1f)) {
                var field by remember { mutableStateOf(TextFieldValue(state.username)) }
                var focused by remember { mutableStateOf(false) }
                // keep in sync when the name changes elsewhere (restore, onboarding)
                if (!focused && field.text != state.username) field = TextFieldValue(state.username)

                fun commit() {
                    val v = field.text.trim().ifEmpty { "User" }
                    AppStore.state.username = v
                    AppStore.commit()
                    field = TextFieldValue(v)
                }
                AppText(
                    text = tr("home_welcome", "Welcome Back"),
                    size = 12f, lineHeight = 16f, color = c.textGray500,
                )
                // the underline is only as wide as the text (autoSizeUsernameInput)
                Column(Modifier.width(IntrinsicSize.Max).widthIn(min = 64.dp)) {
                    BasicTextField(
                        value = field,
                        onValueChange = { field = it },
                        singleLine = true,
                        textStyle = appTextStyle(18f, c.textGray900, FontWeight.Bold, lineHeight = 26f),
                        cursorBrush = SolidColor(c.textGray900),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commit() }),
                        modifier = Modifier.onFocusChanged {
                            if (focused && !it.isFocused) commit()
                            focused = it.isFocused
                        },
                    )
                    Box(Modifier.fillMaxWidth().height(1.dp).background(if (focused) Brand.green else c.borderGray300))
                }
            }

            // two small chips stacked on the right: they grow towards the left as the numbers get bigger
            Column(Modifier.width(IntrinsicSize.Max), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val txN = localizeDigits(state.transactions.size.toString())
                val fundN = localizeDigits(state.funds.size.toString())
                ProfileChip(trf("stats_tx_count_suffix", "${state.transactions.size} transactions", "count" to txN), Brand.red)
                ProfileChip(trf("profile_chip_funds", "${state.funds.size} funds", "count" to fundN), Brand.green)
            }
        }
    }
}

@Composable
private fun ProfileChip(text: String, color: Color) {
    // both chips share one width (the wider one decides) and one fixed height, so their edges line up
    Box(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(text = text, size = 10f, lineHeight = 15f, weight = FontWeight.Bold, color = color, noWrap = true, align = TextAlign.Center)
    }
}

/** Where the name box and other screens read the avatar of the current profile. */
@Suppress("unused")
private val ProfileFontFamily = FontFamily.Default
