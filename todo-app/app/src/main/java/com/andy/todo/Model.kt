package com.andy.todo

enum class Repeat(val label: String) {
    NONE("Never"), DAILY("Daily"), WEEKDAYS("Weekdays"), WEEKLY("Weekly"), MONTHLY("Monthly");

    companion object {
        fun of(name: String?): Repeat = entries.firstOrNull { it.name == name } ?: NONE
    }
}

data class Task(
    var id: Int = 0,
    var title: String = "",
    var deadline: Long = 0,
    /** Minutes before the deadline to remind, or [NO_REMINDER]. */
    var remindBefore: Int = 15,
    var done: Boolean = false,
    var doneAt: Long = 0,
    var repeat: Repeat = Repeat.NONE,
    /** 0 means the task is not in any list. */
    var listId: Int = 0,
    var priority: Boolean = false,
    var createdAt: Long = System.currentTimeMillis(),
) {
    val reminderTime: Long get() = if (remindBefore == NO_REMINDER) -1 else deadline - remindBefore * 60_000L

    companion object {
        const val NO_REMINDER = -1
    }
}

data class TaskList(var id: Int = 0, var name: String = "", var color: Int = 0)
