package com.andy.todo

import android.content.Context
import android.content.SharedPreferences

/** Small user settings and UI state. */
class Prefs(context: Context) {
    private val p: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var summaryEnabled: Boolean
        get() = p.getBoolean("summary_enabled", true)
        set(v) = p.edit().putBoolean("summary_enabled", v).apply()

    /** Minutes after midnight for the daily summary. */
    var summaryMinutes: Int
        get() = p.getInt("summary_minutes", 21 * 60)
        set(v) = p.edit().putInt("summary_minutes", v).apply()

    /** "all", "today" or "list:<id>". */
    var filter: String
        get() = p.getString("filter", "all") ?: "all"
        set(v) = p.edit().putString("filter", v).apply()

    var showCompleted: Boolean
        get() = p.getBoolean("show_completed", false)
        set(v) = p.edit().putBoolean("show_completed", v).apply()

    var askedNotifications: Boolean
        get() = p.getBoolean("asked_notifications", false)
        set(v) = p.edit().putBoolean("asked_notifications", v).apply()

    var askedExactAlarms: Boolean
        get() = p.getBoolean("asked_exact", false)
        set(v) = p.edit().putBoolean("asked_exact", v).apply()
}
