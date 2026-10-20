package com.andy.todo;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

/** Bottom sheet for adding or editing a task. */
public class TaskSheet {
    private static final int[] REMINDERS = {Task.NONE, 0, 15, 60, 1440};

    public interface Callback {
        void onSaved(Task task);
        void onDeleted(Task task);
    }

    private final Activity activity;
    private final Task task;
    private final boolean isNew;
    private final Callback callback;
    private final Calendar cal = Calendar.getInstance();
    private int remindBefore;
    private Dialog dialog;
    private TextView dateView, timeView;
    private LinearLayout chips;

    public TaskSheet(Activity activity, Task existing, Callback callback) {
        this.activity = activity;
        this.callback = callback;
        this.isNew = existing == null;
        if (isNew) {
            task = new Task();
            // Default deadline: the top of the hour, at least an hour from now.
            cal.add(Calendar.HOUR_OF_DAY, 2);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            remindBefore = 15;
        } else {
            task = existing;
            cal.setTimeInMillis(existing.deadline);
            remindBefore = existing.remindBefore;
        }
    }

    public void show() {
        dialog = new Dialog(activity, R.style.Sheet);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.sheet_task);
        Window w = dialog.getWindow();
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        w.setGravity(Gravity.BOTTOM);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | (isNew ? WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE : 0));

        TextView title = dialog.findViewById(R.id.sheetTitle);
        EditText input = dialog.findViewById(R.id.input);
        dateView = dialog.findViewById(R.id.pickDate);
        timeView = dialog.findViewById(R.id.pickTime);
        chips = dialog.findViewById(R.id.chips);
        View delete = dialog.findViewById(R.id.delete);
        View save = dialog.findViewById(R.id.save);

        title.setText(isNew ? "New task" : "Edit task");
        delete.setVisibility(isNew ? View.INVISIBLE : View.VISIBLE);
        if (!isNew) {
            input.setText(task.title);
            input.setSelection(input.length());
        }
        input.requestFocus();

        dateView.setOnClickListener(v -> new DatePickerDialog(activity, (p, y, m, d) -> {
            cal.set(y, m, d);
            refresh();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show());

        timeView.setOnClickListener(v -> new TimePickerDialog(activity, (p, h, min) -> {
            cal.set(Calendar.HOUR_OF_DAY, h);
            cal.set(Calendar.MINUTE, min);
            cal.set(Calendar.SECOND, 0);
            refresh();
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE),
                DateFormat.is24HourFormat(activity)).show());

        Runnable doSave = () -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) {
                input.setError("Give your task a name");
                return;
            }
            task.title = text;
            task.deadline = cal.getTimeInMillis();
            task.remindBefore = remindBefore;
            dialog.dismiss();
            callback.onSaved(task);
        };
        save.setOnClickListener(v -> doSave.run());
        input.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                doSave.run();
                return true;
            }
            return false;
        });
        delete.setOnClickListener(v -> {
            dialog.dismiss();
            callback.onDeleted(task);
        });

        refresh();
        dialog.show();
    }

    private void refresh() {
        dateView.setText(Format.day(cal.getTimeInMillis()));
        timeView.setText(Format.time(activity, cal.getTimeInMillis()));

        chips.removeAllViews();
        float dp = activity.getResources().getDisplayMetrics().density;
        for (int minutes : REMINDERS) {
            TextView chip = new TextView(activity);
            boolean selected = minutes == remindBefore;
            chip.setText(minutes == 0 ? "At deadline"
                    : minutes == Task.NONE ? "None"
                    : minutes == 15 ? "15 min" : minutes == 60 ? "1 hour" : "1 day");
            chip.setTextSize(14);
            chip.setPadding((int) (16 * dp), (int) (10 * dp), (int) (16 * dp), (int) (10 * dp));
            chip.setTextColor(selected ? 0xFFFFFFFF : activity.getColor(R.color.text));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(20 * dp);
            bg.setColor(activity.getColor(selected ? R.color.primary : R.color.field));
            chip.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd((int) (8 * dp));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                remindBefore = minutes;
                refresh();
            });
            chips.addView(chip);
        }
    }
}
