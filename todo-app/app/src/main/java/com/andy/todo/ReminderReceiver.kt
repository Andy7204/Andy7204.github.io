package com.andy.todo

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires reminder and summary notifications and handles their actions. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(c: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, 0)
        val nm = c.getSystemService(NotificationManager::class.java)
        when (intent.action) {
            ACTION_DONE -> {
                nm.cancel(id)
                Db.get(c).task(id)?.takeIf { !it.done }?.let { TaskActions.complete(c, it) }
            }
            ACTION_SNOOZE -> {
                nm.cancel(id)
                Reminders.scheduleAt(c, id, System.currentTimeMillis() + SNOOZE_MS)
            }
            ACTION_SUMMARY -> {
                postSummary(c, nm)
                Reminders.scheduleSummary(c)
            }
            ACTION_FIRE -> {
                val t = Db.get(c).task(id) ?: return
                if (t.done) return
                postReminder(c, nm, t)
                TaskActions.listener?.onReminder(t)
            }
        }
    }

    private fun postReminder(c: Context, nm: NotificationManager, t: Task) {
        Reminders.ensureChannels(c)
        val n = Notification.Builder(c, Reminders.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(c.getColor(R.color.primary))
            .setContentTitle(if (t.priority) "⚑ ${t.title}" else t.title)
            .setContentText(Format.due(c, t.deadline))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(openApp(c, t.id))
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "Mark done", action(c, ACTION_DONE, t.id)).build())
            .addAction(Notification.Action.Builder(null, "Snooze 10 min", action(c, ACTION_SNOOZE, t.id)).build())
            .build()
        post(nm, t.id, n)
    }

    private fun postSummary(c: Context, nm: NotificationManager) {
        val now = System.currentTimeMillis()
        val open = Db.get(c).tasks().filter { !it.done }
        val overdue = open.count { it.deadline < now }
        val today = open.count { it.deadline >= now && Format.daysFromToday(it.deadline) == 0L }
        if (overdue + today == 0) return
        val parts = listOfNotNull(
            if (today > 0) "$today left today" else null,
            if (overdue > 0) "$overdue overdue" else null,
        )
        Reminders.ensureChannels(c)
        val n = Notification.Builder(c, Reminders.CHANNEL_SUMMARY)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(c.getColor(R.color.primary))
            .setContentTitle(parts.joinToString(" · ").replaceFirstChar { it.uppercase() })
            .setContentText("Tap to review your tasks")
            .setContentIntent(openApp(c, Reminders.SUMMARY_NOTIFICATION_ID))
            .setAutoCancel(true)
            .build()
        post(nm, Reminders.SUMMARY_NOTIFICATION_ID, n)
    }

    private fun post(nm: NotificationManager, id: Int, n: Notification) {
        try {
            nm.notify(id, n)
        } catch (e: SecurityException) {
            // Notification permission denied; the in-app banner still shows if the app is open.
        }
    }

    private fun openApp(c: Context, code: Int): PendingIntent = PendingIntent.getActivity(
        c, code, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(c: Context, action: String, id: Int): PendingIntent {
        val i = Intent(c, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id)
        // Distinct request codes per task and action so the PendingIntents don't overwrite each other.
        val code = id * 3 + if (action == ACTION_DONE) 1 else 2
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        // These strings are kept from v1.0 so alarms scheduled by it still arrive.
        const val ACTION_FIRE = "com.andy.todo.FIRE"
        const val ACTION_DONE = "com.andy.todo.DONE"
        const val ACTION_SNOOZE = "com.andy.todo.SNOOZE"
        const val ACTION_SUMMARY = "com.andy.todo.SUMMARY"
        const val EXTRA_ID = "id"
        private const val SNOOZE_MS = 10 * 60_000L
    }
}
