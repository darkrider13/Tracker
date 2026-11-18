package com.myapp.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One daily reminder (Morning / Afternoon / Evening / Night). */
class ReminderItem(
    val id: String,
    var time: String,          // 24h "HH:mm"
    val label: String,
    val message: String,
    var enabled: Boolean,
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("time", time)
        o.put("label", label)
        o.put("message", message)
        o.put("enabled", enabled)
        return o
    }
}

/** Same data as the old `reminderSettings` (stored in its own key, like before). */
class ReminderSettings(
    var enabled: Boolean,
    var items: MutableList<ReminderItem>,
    var lastFired: LinkedHashMap<String, String>,
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("enabled", enabled)
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        o.put("items", arr)
        val lf = JSONObject()
        lastFired.forEach { (k, v) -> lf.put(k, v) }
        o.put("lastFired", lf)
        return o
    }

    companion object {
        const val KEY = "smart_saver_reminders"
        private const val PREFS = "tracker_prefs"

        fun defaults(): ReminderSettings = ReminderSettings(
            enabled = false,
            items = mutableListOf(
                ReminderItem("morning", "10:00", "Morning", "Have you told me your morning expenses?", true),
                ReminderItem("afternoon", "14:00", "Afternoon", "Have you told me your afternoon expenses?", true),
                ReminderItem("evening", "20:00", "Evening", "Have you told me your evening expenses?", true),
                ReminderItem("night", "23:30", "Night", "Have you told me your night expenses?", true),
            ),
            lastFired = LinkedHashMap(),
        )

        /**
         * Merges saved JSON into the defaults the same way the old loadReminderSettings() did:
         * only `enabled`, `lastFired` and each item's `time` / `enabled` are taken from saved data;
         * labels and messages always come from the defaults.
         */
        fun fromJson(saved: JSONObject?): ReminderSettings {
            val d = defaults()
            if (saved == null) return d
            try {
                val savedEnabled = saved.opt("enabled")
                if (savedEnabled is Boolean) d.enabled = savedEnabled

                val lf = saved.optJSONObject("lastFired")
                if (lf != null) {
                    val it = lf.keys()
                    while (it.hasNext()) {
                        val k = it.next()
                        d.lastFired[k] = lf.str(k) ?: ""
                    }
                }

                val savedItems = saved.optJSONArray("items")
                if (savedItems != null) {
                    for (item in d.items) {
                        var match: JSONObject? = null
                        for (i in 0 until savedItems.length()) {
                            val s = savedItems.optJSONObject(i)
                            if (s != null && s.str("id") == item.id) {
                                match = s
                                break
                            }
                        }
                        if (match != null) {
                            val t = match.str("time")
                            if (!t.isNullOrEmpty()) item.time = t
                            val en = match.opt("enabled")
                            if (en is Boolean) item.enabled = en
                        }
                    }
                }
                return d
            } catch (e: Exception) {
                return defaults()
            }
        }

        fun load(context: Context): ReminderSettings {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
                ?: return defaults()
            return try {
                fromJson(JSONObject(raw))
            } catch (e: Exception) {
                defaults()
            }
        }
    }

    fun persist(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, toJson().toString()).apply()
    }
}
