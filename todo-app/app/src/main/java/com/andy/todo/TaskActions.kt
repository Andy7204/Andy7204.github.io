package com.andy.todo

import android.app.NotificationManager
import android.content.Context
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Every change to a task goes through here so that alarms, notifications, the widget and
 * an open MainActivity all stay in sync.
 */
object TaskActions {

    /** Remembers what completing a task did, so it can be undone. */
    data class Completion(val original: Task, val nextId: Int)

    /** Set by MainActivity while it is in the foreground. */
    interface Listener {
        fun onTasksChanged()
        fun onReminder(task: Task)
    }

    var listener: Listener? = null

    fun save(c: Context, t: Task) {
        Db.get(c).save(t)
        Reminders.sync(c, t)
        changed(c)
    }

    fun complete(c: Context, t: Task): Completion {
        val original = t.copy()
        val db = Db.get(c)
        t.done = true
        t.doneAt = System.currentTimeMillis()
        db.save(t)
        Reminders.cancel(c, t.id)
        dismissNotification(c, t.id)

        var nextId = 0
        if (t.repeat != Repeat.NONE) {
            val zone = ZoneId.systemDefault()
            val from = LocalDateTime.ofInstant(Instant.ofEpochMilli(t.deadline), zone)
            Recurrence.next(from, t.repeat, LocalDateTime.now())?.let { next ->
                val n = t.copy(
                    id = 0, done = false, doneAt = 0, createdAt = System.currentTimeMillis(),
                    deadline = next.atZone(zone).toInstant().toEpochMilli(),
                )
                nextId = db.save(n)
                Reminders.sync(c, n)
            }
        }
        changed(c)
        return Completion(original, nextId)
    }

    fun undoComplete(c: Context, completion: Completion) {
        if (completion.nextId != 0) delete(c, completion.nextId, notify = false)
        save(c, completion.original)
    }

    fun reopen(c: Context, t: Task) {
        t.done = false
        t.doneAt = 0
        save(c, t)
    }

    fun delete(c: Context, id: Int, notify: Boolean = true) {
        Reminders.cancel(c, id)
        dismissNotification(c, id)
        Db.get(c).delete(id)
        if (notify) changed(c)
    }

    fun deleteAll(c: Context, tasks: List<Task>) {
        tasks.forEach { delete(c, it.id, notify = false) }
        changed(c)
    }

    fun restore(c: Context, tasks: List<Task>) {
        val db = Db.get(c)
        tasks.forEach {
            db.save(it)
            Reminders.sync(c, it)
        }
        changed(c)
    }

    fun changed(c: Context) {
        TodoWidget.refresh(c)
        listener?.onTasksChanged()
    }

    private fun dismissNotification(c: Context, id: Int) =
        c.getSystemService(NotificationManager::class.java).cancel(id)
}
