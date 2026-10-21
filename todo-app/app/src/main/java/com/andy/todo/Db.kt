package com.andy.todo

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

/** SQLite storage for tasks and lists. */
class Db private constructor(private val context: Context) :
    SQLiteOpenHelper(context, "todo.db", null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE lists (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                color INTEGER NOT NULL,
                position INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                deadline INTEGER NOT NULL,
                remind_before INTEGER NOT NULL,
                done INTEGER NOT NULL DEFAULT 0,
                done_at INTEGER NOT NULL DEFAULT 0,
                repeat TEXT NOT NULL DEFAULT 'NONE',
                list_id INTEGER NOT NULL DEFAULT 0,
                priority INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX tasks_deadline ON tasks(deadline)")
        db.insert("lists", null, listValues(TaskList(name = "Personal", color = Palette.LIST_COLORS[0])))
        db.insert("lists", null, listValues(TaskList(name = "Work", color = Palette.LIST_COLORS[1])))
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // Tasks

    fun tasks(): List<Task> = query("SELECT * FROM tasks ORDER BY deadline") { it.toTask() }

    fun task(id: Int): Task? =
        query("SELECT * FROM tasks WHERE id = ?", arrayOf(id.toString())) { it.toTask() }.firstOrNull()

    /** Inserts the task when its id is 0 (assigning one), otherwise inserts or replaces it. */
    fun save(task: Task): Int {
        val values = taskValues(task)
        if (task.id == 0) {
            task.id = writableDatabase.insertOrThrow("tasks", null, values).toInt()
        } else {
            values.put("id", task.id)
            writableDatabase.insertWithOnConflict("tasks", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
        return task.id
    }

    fun delete(id: Int) {
        writableDatabase.delete("tasks", "id = ?", arrayOf(id.toString()))
    }

    // Lists

    fun lists(): List<TaskList> = query("SELECT * FROM lists ORDER BY position, id") {
        TaskList(it.int("id"), it.str("name"), it.int("color"))
    }

    fun saveList(list: TaskList): Int {
        val values = listValues(list)
        if (list.id == 0) {
            values.put("position", (lists().size + 1) * 10)
            list.id = writableDatabase.insertOrThrow("lists", null, values).toInt()
        } else {
            writableDatabase.update("lists", values, "id = ?", arrayOf(list.id.toString()))
        }
        return list.id
    }

    /** Deletes a list; its tasks are kept and moved out of any list. */
    fun deleteList(id: Int) = writableDatabase.transaction {
        update("tasks", ContentValues().apply { put("list_id", 0) }, "list_id = ?", arrayOf(id.toString()))
        delete("lists", "id = ?", arrayOf(id.toString()))
    }

    /** Replaces everything, used when restoring a backup. */
    fun replaceAll(lists: List<TaskList>, tasks: List<Task>) = writableDatabase.transaction {
        delete("tasks", null, null)
        delete("lists", null, null)
        lists.forEachIndexed { i, l ->
            insert("lists", null, listValues(l).apply {
                put("id", l.id)
                put("position", i * 10)
            })
        }
        tasks.forEach { t -> insert("tasks", null, taskValues(t).apply { put("id", t.id) }) }
    }

    // Helpers

    private fun <T> query(sql: String, args: Array<String>? = null, map: (Cursor) -> T): List<T> =
        readableDatabase.rawQuery(sql, args).use { c ->
            val out = ArrayList<T>(c.count)
            while (c.moveToNext()) out.add(map(c))
            out
        }

    private fun Cursor.int(col: String) = getInt(getColumnIndexOrThrow(col))
    private fun Cursor.long(col: String) = getLong(getColumnIndexOrThrow(col))
    private fun Cursor.str(col: String): String = getString(getColumnIndexOrThrow(col))

    private fun Cursor.toTask() = Task(
        id = int("id"),
        title = str("title"),
        deadline = long("deadline"),
        remindBefore = int("remind_before"),
        done = int("done") != 0,
        doneAt = long("done_at"),
        repeat = Repeat.of(str("repeat")),
        listId = int("list_id"),
        priority = int("priority") != 0,
        createdAt = long("created_at"),
    )

    private fun taskValues(t: Task) = ContentValues().apply {
        put("title", t.title)
        put("deadline", t.deadline)
        put("remind_before", t.remindBefore)
        put("done", if (t.done) 1 else 0)
        put("done_at", t.doneAt)
        put("repeat", t.repeat.name)
        put("list_id", t.listId)
        put("priority", if (t.priority) 1 else 0)
        put("created_at", t.createdAt)
    }

    private fun listValues(l: TaskList) = ContentValues().apply {
        put("name", l.name)
        put("color", l.color)
    }

    private inline fun <T> SQLiteDatabase.transaction(block: SQLiteDatabase.() -> T): T {
        beginTransaction()
        try {
            val r = block()
            setTransactionSuccessful()
            return r
        } finally {
            endTransaction()
        }
    }

    /** Moves tasks saved by v1.0 (a JSON array in SharedPreferences) into the database. */
    private fun migrateLegacy() {
        val prefs = context.getSharedPreferences("tasks", Context.MODE_PRIVATE)
        val json = prefs.getString("tasks", null) ?: return
        try {
            val arr = JSONArray(json)
            writableDatabase.transaction {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val t = Task(
                        id = o.optInt("id"),
                        title = o.optString("title"),
                        deadline = o.optLong("deadline"),
                        remindBefore = o.optInt("remindBefore", Task.NO_REMINDER),
                        done = o.optBoolean("done"),
                        doneAt = if (o.optBoolean("done")) o.optLong("deadline") else 0,
                    )
                    insertWithOnConflict("tasks", null, taskValues(t).apply { put("id", t.id) },
                        SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
        } catch (e: org.json.JSONException) {
            // Unreadable legacy data: nothing we can recover.
        }
        prefs.edit().clear().apply()
    }

    companion object {
        private const val VERSION = 1

        @Volatile private var instance: Db? = null

        fun get(context: Context): Db = instance ?: synchronized(this) {
            instance ?: Db(context.applicationContext).also {
                it.migrateLegacy()
                instance = it
            }
        }
    }
}
