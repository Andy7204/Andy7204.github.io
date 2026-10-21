package com.andy.todo

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Schedules reminder alarms and the daily summary. */
object Reminders {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_SUMMARY = "summary"
    const val SUMMARY_NOTIFICATION_ID = Int.MAX_VALUE - 1
    private const val SUMMARY_REQUEST = Int.MAX_VALUE - 1

    fun ensureChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Task reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts before a task's deadline"
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SUMMARY, "Daily summary", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A once-a-day note about tasks left for today"
            }
        )
    }

    fun canScheduleExact(c: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Schedules (or cancels) the reminder for a task based on its current state. */
    fun sync(c: Context, t: Task) {
        cancel(c, t.id)
        var whenMs = t.reminderTime
        if (t.done || whenMs < 0) return
        val now = System.currentTimeMillis()
        if (whenMs <= now) {
            // The reminder time already passed but the deadline hasn't: remind at the deadline.
            if (t.deadline <= now) return
            whenMs = t.deadline
        }
        scheduleAt(c, t.id, whenMs)
    }

    fun scheduleAt(c: Context, id: Int, whenMs: Long) = setAlarm(c, whenMs, reminderIntent(c, id))

    fun cancel(c: Context, id: Int) {
        c.getSystemService(AlarmManager::class.java).cancel(reminderIntent(c, id))
    }

    fun syncAll(c: Context) {
        Db.get(c).tasks().forEach { sync(c, it) }
        scheduleSummary(c)
    }

    fun scheduleSummary(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = summaryIntent(c)
        am.cancel(pi)
        val prefs = Prefs(c)
        if (!prefs.summaryEnabled) return
        val now = LocalDateTime.now()
        val at = LocalTime.of(prefs.summaryMinutes / 60, prefs.summaryMinutes % 60)
        var next = LocalDate.now().atTime(at)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val ms = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // The summary doesn't need to be to-the-minute, so it never asks for an exact alarm.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
    }

    private fun setAlarm(c: Context, whenMs: Long, pi: PendingIntent) {
        val am = c.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
                return
            } catch (e: SecurityException) {
                // Access was revoked between the check and the call; fall through to inexact.
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
    }

    private fun reminderIntent(c: Context, id: Int): PendingIntent {
        val i = Intent(c, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .putExtra(ReminderReceiver.EXTRA_ID, id)
        return PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun summaryIntent(c: Context): PendingIntent {
        val i = Intent(c, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_SUMMARY)
        return PendingIntent.getBroadcast(c, SUMMARY_REQUEST, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
