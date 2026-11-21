package com.myapp.app.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.myapp.app.MainActivity
import com.myapp.app.R
import com.myapp.app.data.AppSession
import com.myapp.app.data.AppStore
import com.myapp.app.data.Debt
import com.myapp.app.data.ReminderSettings
import com.myapp.app.ui.screens.debtRemaining
import com.myapp.app.util.I18n
import com.myapp.app.util.fmtMoney
import com.myapp.app.util.tr
import com.myapp.app.util.trf
import java.util.Calendar

/**
 * Local reminders - the native twin of scheduleNativeReminders() / scheduleNativeDebtReminders():
 *  - four daily expense reminders (Morning / Afternoon / Evening / Night) at the times picked in the panel
 *  - one reminder per pending debt / receivable at its due date + chosen time ("pay / collect from ...")
 * Alarms are exact when the device allows it, so they arrive on time even in Doze.
 */
object Reminders {
    const val CHANNEL_ID = "tracker_reminders"
    private const val PREFS = "tracker_prefs"
    private const val KEY_DEBT_IDS = "smart_saver_debt_reminder_ids"
    private const val KEY_FIRED_DEBTS = "smart_saver_debt_reminders_fired"

    private val DAILY_IDS = mapOf("morning" to 9101, "afternoon" to 9102, "evening" to 9103, "night" to 9104)

    // ------------------------------------------------------------------ permissions

    fun hasNotificationPermission(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // ------------------------------------------------------------------ notification

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }

    fun notifyNow(context: Context, id: Int, body: String) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(0xFF4F46E5.toInt())
            .setContentTitle("Tracker")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    // ------------------------------------------------------------------ alarms

    private fun alarmIntent(context: Context, id: Int, kind: String, body: String, hour: Int = -1, minute: Int = -1): Intent =
        Intent(context, ReminderReceiver::class.java)
            .setAction("com.myapp.app.REMINDER.$id")
            .putExtra("id", id)
            .putExtra("kind", kind)
            .putExtra("body", body)
            .putExtra("hour", hour)
            .putExtra("minute", minute)

    private fun pending(context: Context, id: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun setAlarm(context: Context, id: Int, triggerAt: Long, intent: Intent) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context, id, intent)
        // Inexact + works in Doze: no special permission, allowed by Google Play (a few minutes of drift is fine for reminders)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    private fun cancelAlarm(context: Context, id: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context, id, alarmIntent(context, id, "", "")))
    }

    /** Next moment (today if still ahead, else tomorrow) at [hour]:[minute]. */
    fun nextOccurrence(hour: Int, minute: Int, after: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = after
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= after) c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    // ------------------------------------------------------------------ the two schedules

    /** Re-registers everything (app start, Save in the panel, after a reboot). */
    fun scheduleAll(context: Context) {
        val app = context.applicationContext
        scheduleDaily(app)
        scheduleDebts(app)
    }

    fun scheduleDaily(context: Context) {
        val settings = ReminderSettings.load(context)
        // cancel first so a changed time / switched-off item really disappears
        DAILY_IDS.values.forEach { cancelAlarm(context, it) }
        for (item in settings.items) {
            val id = DAILY_IDS[item.id] ?: continue
            if (!item.enabled || item.time.isEmpty()) continue
            val (h, m) = item.time.split(":").map { it.toInt() }
            val body = tr("reminder_msg_${item.id}", item.message)
            setAlarm(context, id, nextOccurrence(h, m), alarmIntent(context, id, "daily", body, h, m))
        }
    }

    private fun debtNativeId(d: Debt): Int = 40000 + (d.id % 500000).toInt()

    private fun debtDueMessage(d: Debt): String {
        val remaining = fmtMoney(debtRemaining(d))
        return if (d.type == "receivable") {
            trf(
                "debt_due_notif_receivable", "Reminder: ${d.person} still owes you ৳$remaining. Time to collect?",
                "person" to d.person, "amt" to remaining,
            )
        } else {
            trf(
                "debt_due_notif_payable", "Reminder: You need to pay ৳$remaining to ${d.person}. Don't forget!",
                "person" to d.person, "amt" to remaining,
            )
        }
    }

    private fun firedSet(context: Context): MutableSet<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_FIRED_DEBTS, emptySet())!!.toMutableSet()

    fun markDebtFired(context: Context, id: Int) {
        val set = firedSet(context)
        set.add(id.toString())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY_FIRED_DEBTS, set).apply()
    }

    /**
     * One reminder per pending entry with a due date + time, at that moment. A moment that already
     * passed less than a day ago is announced right away - once (the old app's "don't bother with
     * far-past due dates" rule); anything older is skipped.
     */
    fun scheduleDebts(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // whatever was scheduled before (settled / deleted entries) goes first
        prefs.getStringSet(KEY_DEBT_IDS, emptySet())!!.forEach { cancelAlarm(context, it.toInt()) }

        val now = System.currentTimeMillis()
        val dayAgo = now - 24L * 60 * 60 * 1000
        val fired = firedSet(context)
        val ids = mutableSetOf<String>()
        var stateChanged = false

        for (d in AppStore.state.debts) {
            val due = d.dueDate
            val time = d.reminderTime
            if (d.settled || due.isNullOrEmpty() || time.isNullOrEmpty()) continue
            val dp = due.split("-")
            val tp = time.split(":")
            if (dp.size < 3 || tp.size < 2) continue
            val at = Calendar.getInstance().apply {
                set(dp[0].toInt(), dp[1].toInt() - 1, dp[2].toInt(), tp[0].toInt(), tp[1].toInt(), 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val id = debtNativeId(d)

            if (at > now) {
                // a new due date for an entry that already fired gets a fresh chance
                fired.remove(id.toString())
                if (d.dueReminderFired) { d.dueReminderFired = false; stateChanged = true }
                setAlarm(context, id, at, alarmIntent(context, id, "debt", debtDueMessage(d)))
                ids.add(id.toString())
            } else if (at >= dayAgo) {
                val alreadyDone = d.dueReminderFired || id.toString() in fired
                if (!alreadyDone) {
                    notifyNow(context, id, debtDueMessage(d))
                    fired.add(id.toString())
                }
                if (!d.dueReminderFired) { d.dueReminderFired = true; stateChanged = true }
            }
        }
        prefs.edit().putStringSet(KEY_DEBT_IDS, ids).putStringSet(KEY_FIRED_DEBTS, fired).apply()
        if (stateChanged) AppStore.commitQuietly()
    }
}

/** Fires the notification; a daily reminder immediately books itself for the next day. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        if (id < 0) return
        val body = intent.getStringExtra("body") ?: return
        Reminders.notifyNow(context, id, body)
        when (intent.getStringExtra("kind")) {
            "daily" -> {
                val h = intent.getIntExtra("hour", -1)
                val m = intent.getIntExtra("minute", -1)
                if (h >= 0 && m >= 0) {
                    // at least a minute ahead so an early-delivered alarm can't book itself for "now" again
                    val next = Reminders.nextOccurrence(h, m, System.currentTimeMillis() + 60_000L)
                    val again = Intent(context, ReminderReceiver::class.java)
                        .setAction("com.myapp.app.REMINDER.$id")
                        .putExtra("id", id).putExtra("kind", "daily").putExtra("body", body)
                        .putExtra("hour", h).putExtra("minute", m)
                    val pi = PendingIntent.getBroadcast(context, id, again, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
                }
            }
            "debt" -> Reminders.markDebtFired(context, id)
        }
    }
}

/** After a reboot (or an app update) the alarms are gone - put them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action
        if (a != Intent.ACTION_BOOT_COMPLETED && a != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext
        AppSession.init(app)
        I18n.init(app)
        AppStore.init(app)
        Reminders.scheduleAll(app)
    }
}
