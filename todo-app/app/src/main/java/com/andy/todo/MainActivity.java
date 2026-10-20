package com.andy.todo;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity implements ReminderReceiver.Listener {
    private static final long TICK_MS = 30_000L;
    private static final long BANNER_MS = 10_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, TICK_MS);
        }
    };
    private final Runnable hideBanner = this::hideBanner;

    private LinearLayout list;
    private View empty, banner;
    private TextView today, progressTitle, progressSub, bannerTitle, bannerSub;
    private ProgressBar progress;
    private int bannerTaskId;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        today = findViewById(R.id.today);
        progressTitle = findViewById(R.id.progressTitle);
        progressSub = findViewById(R.id.progressSub);
        progress = findViewById(R.id.progress);
        banner = findViewById(R.id.banner);
        bannerTitle = findViewById(R.id.bannerTitle);
        bannerSub = findViewById(R.id.bannerSub);

        findViewById(R.id.fab).setOnClickListener(v -> openSheet(null));
        findViewById(R.id.bannerClose).setOnClickListener(v -> hideBanner());
        findViewById(R.id.bannerDone).setOnClickListener(v -> {
            Task t = TaskStore.get(this, bannerTaskId);
            if (t != null) setDone(t, true);
            hideBanner();
        });

        Reminders.ensureChannel(this);
        Reminders.syncAll(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ReminderReceiver.listener = this;
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ReminderReceiver.listener = null;
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        render();
    }

    // ReminderReceiver.Listener

    @Override
    public void onReminder(Task t) {
        bannerTaskId = t.id;
        bannerTitle.setText(t.title);
        bannerSub.setText(Format.due(this, t.deadline));
        handler.removeCallbacks(hideBanner);
        if (banner.getVisibility() != View.VISIBLE) {
            banner.setVisibility(View.VISIBLE);
            banner.setTranslationY(-300f);
            banner.setAlpha(0f);
            banner.animate().translationY(0f).alpha(1f).setDuration(280).start();
        }
        handler.postDelayed(hideBanner, BANNER_MS);
        render();
    }

    @Override
    public void onTasksChanged() {
        render();
    }

    private void hideBanner() {
        handler.removeCallbacks(hideBanner);
        if (banner.getVisibility() != View.VISIBLE) return;
        banner.animate().translationY(-300f).alpha(0f).setDuration(220)
                .withEndAction(() -> banner.setVisibility(View.GONE)).start();
    }

    // Rendering

    private void render() {
        today.setText(DateFormat.format("EEEE, MMMM d", System.currentTimeMillis()));

        List<Task> tasks = TaskStore.all(this);
        tasks.sort(Comparator.comparingLong(t -> t.deadline));
        long now = System.currentTimeMillis();
        List<Task> overdue = new ArrayList<>(), upcoming = new ArrayList<>(), done = new ArrayList<>();
        for (Task t : tasks) {
            if (t.done) done.add(t);
            else if (t.deadline < now) overdue.add(t);
            else upcoming.add(t);
        }

        int total = tasks.size();
        progress.setProgress(total == 0 ? 0 : done.size() * 100 / total);
        if (total == 0) {
            progressTitle.setText("Nothing planned");
            progressSub.setText("Add a task to get started");
        } else if (done.size() == total) {
            progressTitle.setText("All done! 🎉");
            progressSub.setText(total + (total == 1 ? " task" : " tasks") + " completed");
        } else {
            progressTitle.setText(done.size() + " of " + total + " done");
            int dueToday = 0;
            for (Task t : upcoming) if (Format.day(t.deadline).equals("Today")) dueToday++;
            progressSub.setText(overdue.size() > 0 ? overdue.size() + " overdue"
                    + (dueToday > 0 ? " · " + dueToday + " due today" : "")
                    : dueToday > 0 ? dueToday + " due today" : "You're on track");
        }

        list.removeAllViews();
        empty.setVisibility(total == 0 ? View.VISIBLE : View.GONE);
        section("Overdue", overdue);
        section("Upcoming", upcoming);
        section("Completed", done);
    }

    private void section(String name, List<Task> tasks) {
        if (tasks.isEmpty()) return;
        LayoutInflater inf = getLayoutInflater();
        TextView header = (TextView) inf.inflate(R.layout.item_section, list, false);
        header.setText(name + "  ·  " + tasks.size());
        list.addView(header);
        for (Task t : tasks) list.addView(bind(inf.inflate(R.layout.item_task, list, false), t));
    }

    private View bind(View v, Task t) {
        TextView title = v.findViewById(R.id.title);
        TextView due = v.findViewById(R.id.due);
        ImageView check = v.findViewById(R.id.check);
        ImageView bell = v.findViewById(R.id.bell);

        title.setText(t.title);
        long left = t.deadline - System.currentTimeMillis();
        int fg, bg;
        if (t.done) {
            fg = getColor(R.color.muted);
            bg = getColor(R.color.chip);
        } else if (left < 0) {
            fg = getColor(R.color.danger);
            bg = getColor(R.color.danger_soft);
        } else if (left < 24 * 3_600_000L) {
            fg = getColor(R.color.warn);
            bg = getColor(R.color.warn_soft);
        } else {
            fg = getColor(R.color.primary);
            bg = getColor(R.color.primary_soft);
        }
        due.setText(t.done ? Format.day(t.deadline) + ", " + Format.time(this, t.deadline)
                : Format.due(this, t.deadline));
        due.setTextColor(fg);
        GradientDrawable chip = new GradientDrawable();
        chip.setCornerRadius(100f);
        chip.setColor(bg);
        due.setBackground(chip);
        Drawable clock = getDrawable(R.drawable.ic_clock).mutate();
        clock.setTint(fg);
        clock.setBounds(0, 0, dp(13), dp(13));
        due.setCompoundDrawablesRelative(clock, null, null, null);

        if (t.done) {
            check.setBackgroundResource(R.drawable.bg_check_done);
            check.setImageResource(R.drawable.ic_check);
            title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            title.setAlpha(0.5f);
        } else {
            check.setBackgroundResource(R.drawable.bg_check);
            check.setImageDrawable(null);
            title.setPaintFlags(title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            title.setAlpha(1f);
        }
        bell.setVisibility(!t.done && t.remindBefore != Task.NONE ? View.VISIBLE : View.GONE);

        v.findViewById(R.id.checkTouch).setOnClickListener(x ->
                check.animate().scaleX(0.6f).scaleY(0.6f).setDuration(90).withEndAction(() -> {
                    check.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    setDone(t, !t.done);
                }).start());
        v.setOnClickListener(x -> openSheet(t));
        return v;
    }

    // Actions

    private void setDone(Task t, boolean done) {
        t.done = done;
        TaskStore.save(this, t);
        Reminders.sync(this, t);
        if (done) getSystemService(NotificationManager.class).cancel(t.id);
        render();
    }

    private void openSheet(Task existing) {
        new TaskSheet(this, existing, new TaskSheet.Callback() {
            @Override
            public void onSaved(Task task) {
                TaskStore.save(MainActivity.this, task);
                Reminders.sync(MainActivity.this, task);
                render();
            }

            @Override
            public void onDeleted(Task task) {
                Reminders.cancel(MainActivity.this, task.id);
                getSystemService(NotificationManager.class).cancel(task.id);
                TaskStore.delete(MainActivity.this, task.id);
                render();
                Toast.makeText(MainActivity.this, "Task deleted", Toast.LENGTH_SHORT).show();
            }
        }).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
