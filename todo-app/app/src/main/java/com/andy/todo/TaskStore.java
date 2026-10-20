package com.andy.todo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** Persists tasks as a JSON array in SharedPreferences. */
public final class TaskStore {
    private static final String PREFS = "tasks";
    private static final String KEY_TASKS = "tasks";
    private static final String KEY_NEXT_ID = "next_id";

    private TaskStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized List<Task> all(Context c) {
        List<Task> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY_TASKS, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(Task.fromJson(arr.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
        return out;
    }

    public static synchronized Task get(Context c, int id) {
        for (Task t : all(c)) if (t.id == id) return t;
        return null;
    }

    public static synchronized void save(Context c, Task task) {
        List<Task> list = all(c);
        if (task.id == 0) {
            int next = prefs(c).getInt(KEY_NEXT_ID, 1);
            task.id = next;
            prefs(c).edit().putInt(KEY_NEXT_ID, next + 1).apply();
            list.add(task);
        } else {
            boolean found = false;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id == task.id) {
                    list.set(i, task);
                    found = true;
                }
            }
            if (!found) list.add(task);
        }
        write(c, list);
    }

    public static synchronized void delete(Context c, int id) {
        List<Task> list = all(c);
        list.removeIf(t -> t.id == id);
        write(c, list);
    }

    private static void write(Context c, List<Task> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Task t : list) arr.put(t.toJson());
        } catch (JSONException ignored) {
        }
        prefs(c).edit().putString(KEY_TASKS, arr.toString()).apply();
    }
}
