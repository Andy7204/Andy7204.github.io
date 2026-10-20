package com.andy.todo;

import org.json.JSONException;
import org.json.JSONObject;

public class Task {
    /** Reminder offsets in minutes before the deadline; NONE disables the reminder. */
    public static final int NONE = -1;

    public int id;
    public String title;
    public long deadline;
    public int remindBefore = 15;
    public boolean done;

    public long reminderTime() {
        return remindBefore == NONE ? -1 : deadline - remindBefore * 60_000L;
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("id", id)
                .put("title", title)
                .put("deadline", deadline)
                .put("remindBefore", remindBefore)
                .put("done", done);
    }

    static Task fromJson(JSONObject o) {
        Task t = new Task();
        t.id = o.optInt("id");
        t.title = o.optString("title");
        t.deadline = o.optLong("deadline");
        t.remindBefore = o.optInt("remindBefore", NONE);
        t.done = o.optBoolean("done");
        return t;
    }
}
