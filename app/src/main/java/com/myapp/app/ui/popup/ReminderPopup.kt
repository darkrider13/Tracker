package com.myapp.app.ui.popup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.myapp.app.data.ReminderSettings
import com.myapp.app.reminder.Reminders
import com.myapp.app.ui.AppActions
import com.myapp.app.ui.components.AppSwitch
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.screens.to12Hour
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.localizeDigits
import com.myapp.app.util.tr
import com.myapp.app.util.trf

object ReminderPanel {
    private var appContext: Context? = null
    private var launcher: ActivityResultLauncher<String>? = null

    /** The editable copy shown in the panel (items are plain objects, so [tick] drives redraws). */
    var settings by mutableStateOf<ReminderSettings?>(null)
    var tick by mutableIntStateOf(0)
    /** Bumped when a permission may have changed (answer to the dialog, coming back from Settings). */
    var permissionTick by mutableIntStateOf(0)

    /** Call once from MainActivity.onCreate (before the activity starts). */
    fun attach(activity: ComponentActivity) {
        appContext = activity.applicationContext
        launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            permissionTick++
            appContext?.let { Reminders.scheduleAll(it) }
        }
    }

    /** The old app asked for notification permission on every launch (requestPermissions); the system only shows it while allowed. */
    fun requestAtLaunch() {
        val ctx = appContext ?: return
        if (Build.VERSION.SDK_INT >= 33 && !Reminders.hasNotificationPermission(ctx)) {
            try { launcher?.launch(Manifest.permission.POST_NOTIFICATIONS) } catch (_: Exception) {}
        }
    }

    /** requestReminderPermission(): the "Allow" button. */
    fun requestPermission() {
        val ctx = appContext ?: return
        if (Build.VERSION.SDK_INT >= 33) {
            try { launcher?.launch(Manifest.permission.POST_NOTIFICATIONS) } catch (_: Exception) {}
        } else {
            openAppNotificationSettings(ctx)
        }
    }

    private fun openAppNotificationSettings(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {}
    }

    /** openNotificationsPanel() */
    fun open() {
        val ctx = appContext ?: return
        settings = ReminderSettings.load(ctx)
        tick++
        permissionTick++
        Popups.reminders = true
    }

    fun close() { Popups.reminders = false }

    fun remindersActive(): Boolean = settings?.items?.any { it.enabled } == true

    fun toggle(id: String, checked: Boolean) {
        settings?.items?.firstOrNull { it.id == id }?.enabled = checked
        tick++
        val ctx = appContext ?: return
        if (checked && !Reminders.hasNotificationPermission(ctx)) requestPermission()
    }

    fun setTime(id: String, time24: String) {
        settings?.items?.firstOrNull { it.id == id }?.time = time24
        tick++
    }

    /** saveReminderSettings(): store, re-register the alarms, close. */
    fun save() {
        val ctx = appContext
        val s = settings
        if (ctx != null && s != null) {
            s.persist(ctx)
            Reminders.scheduleAll(ctx)
        }
        close()
    }
}

@Composable
fun ReminderPopup() {
    val c = AppTheme.colors
    val context = LocalContext.current
    PopupLayer(visible = Popups.reminders, scrim = 0.6f) {
        // coming back from the system Settings screens: re-check the permissions
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) ReminderPanel.permissionTick++ }
            owner.lifecycle.addObserver(obs)
            onDispose { owner.lifecycle.removeObserver(obs) }
        }
        @Suppress("UNUSED_VARIABLE") val t = ReminderPanel.tick
        @Suppress("UNUSED_VARIABLE") val pt = ReminderPanel.permissionTick
        val s = ReminderPanel.settings
        val active = ReminderPanel.remindersActive()

        GlassCard(radius = 28.dp, padding = 24.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PopupHeaderIcon("fa-bell", 32.dp, 12.dp, Brand.red)
                    AppText(
                        text = tr("modal_expense_reminders_title", "Expense Reminders"), size = 18f, lineHeight = 28f,
                        weight = FontWeight.ExtraBold, letterSpacingEm = -0.025f, color = c.textGray900,
                    )
                }

                if (active && !Reminders.hasNotificationPermission(context)) {
                    val shape = RoundedCornerShape(12.dp)
                    Row(
                        Modifier.fillMaxWidth().clip(shape).background(Brand.red, shape).border(1.dp, Color(0xFFFEE2E2), shape).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppText(
                            text = tr("modal_allow_notif_banner", "Allow notifications to receive reminders."),
                            size = 11f, lineHeight = 16.5f, weight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f),
                        )
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(Brand.red, CircleShape)
                                .border(1.dp, Color(0xFFE5E7EB), CircleShape)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    ReminderPanel.requestPermission()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            AppText(text = tr("common_allow", "Allow"), size = 10f, lineHeight = 15f, weight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s?.items?.forEach { item ->
                        val rowShape = RoundedCornerShape(16.dp)
                        val (h12, minute, ampm) = to12Hour(item.time)
                        val ampmLabel = if (ampm == "PM") tr("reminder_pm", "PM") else tr("reminder_am", "AM")
                        val label12 = localizeDigits(h12.toString()) + ":" + localizeDigits(minute.toString().padStart(2, '0')) + " " + ampmLabel
                        val periodName = tr("reminder_label_${item.id}", item.label)
                        Row(
                            Modifier.fillMaxWidth().clip(rowShape).background(c.bgGray100a70, rowShape).padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppText(
                                text = periodName, size = 12f, lineHeight = 16f, weight = FontWeight.Bold,
                                color = c.textGray800, modifier = Modifier.weight(1f),
                            )
                            val timeShape = RoundedCornerShape(8.dp)
                            Box(
                                Modifier
                                    .width(96.dp)
                                    .clip(timeShape)
                                    .background(c.bgWhite80, timeShape)
                                    .border(1.dp, c.borderGray200, timeShape)
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        TimePicker.open(
                                            item.time,
                                            trf("modal_select_time_for", "Select $periodName Time", "period" to periodName),
                                        ) { picked -> ReminderPanel.setTime(item.id, picked) }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppText(text = label12, size = 12f, lineHeight = 16f, weight = FontWeight.Bold, color = c.textDefault, align = TextAlign.Center)
                            }
                            AppSwitch(checked = item.enabled, onCheckedChange = { ReminderPanel.toggle(item.id, it) })
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModalButton(tr("common_close", "Close"), ModalButtonKind.Secondary, Modifier.weight(1f)) { ReminderPanel.close() }
                    ModalButton(tr("common_save", "Save"), ModalButtonKind.Green, Modifier.weight(1f)) { ReminderPanel.save() }
                }
            }
        }
    }
}

fun installReminderHooks() {
    AppActions.openNotificationsPanel = { ReminderPanel.open() }
}
