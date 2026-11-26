package com.myapp.app.ui.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.ui.theme.Tw
import com.myapp.app.util.tr

class AlertRequest(
    val title: String,
    val message: String,
    val okLabel: String,
    val icon: String,
    val onClose: () -> Unit,
    /** No blur and only a hint of dimming behind the popup (update popups). */
    val softBackdrop: Boolean = false,
)

class ConfirmRequest(
    val title: String,
    val message: String,
    val okLabel: String,
    val cancelLabel: String,
    val icon: String,
    val cancelGreen: Boolean,
    val showClose: Boolean,
    val onResult: (Boolean?) -> Unit,
    /** Very light dimming + blur behind the popup (the update popup: the bars must keep matching the page). */
    val softBackdrop: Boolean = false,
)

private val POSITIVE_MODAL_ICONS = listOf("fa-check-circle", "fa-circle-check")

/**
 * The old customAlert() / customConfirm() (they returned promises; here the answer comes
 * to a callback). Confirm answers true (OK), false (Cancel) or null (the X in the corner).
 */
object AppDialogs {
    internal var lastAlert: AlertRequest? by mutableStateOf(null)
    internal var lastConfirm: ConfirmRequest? by mutableStateOf(null)

    fun alert(
        message: String,
        title: String? = null,
        okLabel: String? = null,
        icon: String? = null,
        softBackdrop: Boolean = false,
        onClose: () -> Unit = {},
    ) {
        val req = AlertRequest(
            title = title ?: tr("alert_default_title", "Notice"),
            message = message,
            okLabel = okLabel ?: tr("common_ok", "OK"),
            icon = icon ?: "fa-circle-info",
            onClose = onClose,
            softBackdrop = softBackdrop,
        )
        lastAlert = req
        Popups.alert = req
    }

    fun confirm(
        message: String,
        title: String? = null,
        okLabel: String? = null,
        cancelLabel: String? = null,
        icon: String? = null,
        cancelGreen: Boolean = false,
        showClose: Boolean = false,
        softBackdrop: Boolean = false,
        onResult: (Boolean?) -> Unit,
    ) {
        val req = ConfirmRequest(
            title = title ?: tr("confirm_default_title", "Are you sure?"),
            message = message,
            okLabel = okLabel ?: tr("common_yes", "Yes"),
            cancelLabel = cancelLabel ?: tr("common_cancel", "Cancel"),
            icon = icon ?: "fa-exclamation-triangle",
            cancelGreen = cancelGreen,
            showClose = showClose,
            onResult = onResult,
            softBackdrop = softBackdrop,
        )
        lastConfirm = req
        Popups.confirm = req
    }

    internal fun resolveAlert() {
        val r = Popups.alert
        Popups.alert = null
        r?.onClose?.invoke()
    }

    internal fun resolveConfirm(result: Boolean?) {
        val r = Popups.confirm
        Popups.confirm = null
        r?.onResult?.invoke(result)
    }
}

@Composable
private fun DialogIcon(icon: String) {
    if (POSITIVE_MODAL_ICONS.contains(icon)) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(Tw.green500, CircleShape),
            contentAlignment = Alignment.Center,
        ) { FaIcon(icon, 24.dp, Color.White) }
    } else {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            FaIcon(icon, 30.dp, Brand.red)
        }
    }
}

@Composable
private fun DialogTexts(title: String, message: String) {
    val c = AppTheme.colors
    AppText(
        text = title,
        size = 18f,
        lineHeight = 28f,
        weight = FontWeight.ExtraBold,
        letterSpacingEm = -0.025f,
        color = c.textGray900,
        align = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    if (message.isNotEmpty()) {
        AppText(
            text = message,
            size = 12f,
            lineHeight = 16f,
            weight = FontWeight.Medium,
            color = c.textGray500,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 8.dp, end = 8.dp),
        )
    }
}

/** Draws the Alert and Confirm popups (always on top of every other popup). */
@Composable
fun DialogsHost() {
    PopupLayer(visible = Popups.confirm != null, scrim = if (AppDialogs.lastConfirm?.softBackdrop == true) 0.10f else 0.7f) {
        val req = AppDialogs.lastConfirm
        if (req != null) {
            GlassCard(radius = 28.dp, padding = 24.dp) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DialogIcon(req.icon)
                        Spacer(Modifier.height(16.dp))
                        DialogTexts(req.title, req.message)
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ModalButton(
                                text = req.cancelLabel,
                                kind = if (req.cancelGreen) ModalButtonKind.GreenLight else ModalButtonKind.Secondary,
                                modifier = Modifier.weight(1f),
                            ) { AppDialogs.resolveConfirm(false) }
                            ModalButton(
                                text = req.okLabel,
                                kind = ModalButtonKind.Red,
                                modifier = Modifier.weight(1f),
                            ) { AppDialogs.resolveConfirm(true) }
                        }
                    }
                    if (req.showClose) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 0.dp)
                                .size(32.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { AppDialogs.resolveConfirm(null) },
                            contentAlignment = Alignment.Center,
                        ) { FaIcon("fa-xmark", 18.dp, AppTheme.colors.textGray500) }
                    }
                }
            }
        }
    }

    PopupLayer(visible = Popups.alert != null, scrim = if (AppDialogs.lastAlert?.softBackdrop == true) 0.10f else 0.7f) {
        val req = AppDialogs.lastAlert
        if (req != null) {
            GlassCard(radius = 28.dp, padding = 24.dp) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DialogIcon(req.icon)
                    Spacer(Modifier.height(16.dp))
                    DialogTexts(req.title, req.message)
                    Spacer(Modifier.height(16.dp))
                    ModalButton(
                        text = req.okLabel,
                        kind = ModalButtonKind.Red,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { AppDialogs.resolveAlert() }
                }
            }
        }
    }
}
