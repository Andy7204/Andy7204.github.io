package com.andy.todo;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Schedules and cancels reminder alarms. */
public final class Reminders {
    public static final String CHANNEL = "reminders";

    private Reminders() {}

    public static void ensureChannel(Context c) {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL, "Task reminders", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Alerts before a task's deadline");
        ch.enableVibration(true);
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    /** Schedules (or cancels) the reminder for a task based on its current state. */
    public static void sync(Context c, Task t) {
        cancel(c, t.id);
        long when = t.reminderTime();
        if (t.done || when < 0) return;
        if (when <= System.currentTimeMillis()) {
            // The reminder time already passed but the deadline hasn't: remind at the deadline.
            if (t.deadline <= System.currentTimeMillis()) return;
            when = t.deadline;
        }
        scheduleAt(c, t.id, when);
    }

    public static void scheduleAt(Context c, int id, long when) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = pending(c, id);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }

    public static void cancel(Context c, int id) {
        c.getSystemService(AlarmManager.class).cancel(pending(c, id));
    }

    public static void syncAll(Context c) {
        for (Task t : TaskStore.all(c)) sync(c, t);
    }

    private static PendingIntent pending(Context c, int id) {
        Intent i = new Intent(c, ReminderReceiver.class)
                .setAction(ReminderReceiver.ACTION_FIRE)
                .putExtra(ReminderReceiver.EXTRA_ID, id);
        return PendingIntent.getBroadcast(c, id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
