package com.andy.todo;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires a reminder notification and handles its "Done" / "Snooze" actions. */
public class ReminderReceiver extends BroadcastReceiver {
    public static final String ACTION_FIRE = "com.andy.todo.FIRE";
    public static final String ACTION_DONE = "com.andy.todo.DONE";
    public static final String ACTION_SNOOZE = "com.andy.todo.SNOOZE";
    public static final String EXTRA_ID = "id";

    private static final long SNOOZE_MS = 10 * 60_000L;

    /** Set by MainActivity while it is in the foreground to show an in-app banner. */
    public interface Listener {
        void onReminder(Task task);
        void onTasksChanged();
    }

    static Listener listener;

    @Override
    public void onReceive(Context c, Intent intent) {
        int id = intent.getIntExtra(EXTRA_ID, 0);
        Task t = TaskStore.get(c, id);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        String action = intent.getAction();

        if (ACTION_DONE.equals(action)) {
            nm.cancel(id);
            if (t != null) {
                t.done = true;
                TaskStore.save(c, t);
                Reminders.cancel(c, id);
            }
            if (listener != null) listener.onTasksChanged();
            return;
        }
        if (ACTION_SNOOZE.equals(action)) {
            nm.cancel(id);
            Reminders.scheduleAt(c, id, System.currentTimeMillis() + SNOOZE_MS);
            return;
        }

        if (t == null || t.done) return;
        notify(c, nm, t);
        if (listener != null) listener.onReminder(t);
    }

    private static void notify(Context c, NotificationManager nm, Task t) {
        Reminders.ensureChannel(c);
        PendingIntent open = PendingIntent.getActivity(c, t.id,
                new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(c, Reminders.CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(c.getColor(R.color.primary))
                .setContentTitle(t.title)
                .setContentText(Format.due(c, t.deadline))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(null, "Mark done",
                        action(c, ACTION_DONE, t.id)).build())
                .addAction(new Notification.Action.Builder(null, "Snooze 10 min",
                        action(c, ACTION_SNOOZE, t.id)).build())
                .build();
        try {
            nm.notify(t.id, n);
        } catch (SecurityException ignored) {
            // Notification permission denied; the in-app banner still shows if the app is open.
        }
    }

    private static PendingIntent action(Context c, String action, int id) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction(action).putExtra(EXTRA_ID, id);
        int code = id * 3 + (ACTION_DONE.equals(action) ? 1 : 2);
        return PendingIntent.getBroadcast(c, code, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
