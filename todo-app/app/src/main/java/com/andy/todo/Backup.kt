package com.andy.todo

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Exports and imports all tasks and lists as JSON. */
object Backup {
    private const val FORMAT = 2

    class Contents(val lists: List<TaskList>, val tasks: List<Task>)

    fun export(c: Context): String {
        val db = Db.get(c)
        val lists = JSONArray()
        db.lists().forEach { lists.put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color)) }
        val tasks = JSONArray()
        db.tasks().forEach {
            tasks.put(JSONObject()
                .put("id", it.id)
                .put("title", it.title)
                .put("deadline", it.deadline)
                .put("remindBefore", it.remindBefore)
                .put("done", it.done)
                .put("doneAt", it.doneAt)
                .put("repeat", it.repeat.name)
                .put("listId", it.listId)
                .put("priority", it.priority)
                .put("createdAt", it.createdAt))
        }
        return JSONObject()
            .put("app", "com.andy.todo")
            .put("format", FORMAT)
            .put("exportedAt", System.currentTimeMillis())
            .put("lists", lists)
            .put("tasks", tasks)
            .toString(2)
    }

    /** Parses a backup, or throws [IllegalArgumentException] with a readable message. */
    fun parse(json: String): Contents {
        try {
            val root = JSONObject(json)
            if (root.optString("app") != "com.andy.todo") throw IllegalArgumentException("This isn't a Todo backup file.")
            val lists = root.optJSONArray("lists") ?: JSONArray()
            val tasks = root.getJSONArray("tasks")
            val listIds = HashSet<Int>()
            val outLists = (0 until lists.length()).map {
                val o = lists.getJSONObject(it)
                TaskList(o.getInt("id"), o.getString("name"), o.optInt("color", Palette.LIST_COLORS[0]))
                    .also { l -> listIds.add(l.id) }
            }
            val outTasks = (0 until tasks.length()).map {
                val o = tasks.getJSONObject(it)
                Task(
                    id = o.getInt("id"),
                    title = o.getString("title"),
                    deadline = o.getLong("deadline"),
                    remindBefore = o.optInt("remindBefore", Task.NO_REMINDER),
                    done = o.optBoolean("done"),
                    doneAt = o.optLong("doneAt"),
                    repeat = Repeat.of(o.optString("repeat")),
                    listId = o.optInt("listId").takeIf { id -> id in listIds } ?: 0,
                    priority = o.optBoolean("priority"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                )
            }
            return Contents(outLists, outTasks)
        } catch (e: JSONException) {
            throw IllegalArgumentException("The backup file is damaged or incomplete.")
        }
    }

    fun restore(c: Context, contents: Contents) {
        Db.get(c).tasks().forEach { Reminders.cancel(c, it.id) }
        Db.get(c).replaceAll(contents.lists, contents.tasks)
        Reminders.syncAll(c)
        TaskActions.changed(c)
    }
}
