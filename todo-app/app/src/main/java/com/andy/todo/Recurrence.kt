package com.andy.todo

import java.time.DayOfWeek
import java.time.LocalDateTime

object Recurrence {
    /** The first occurrence after [from] that is also after [now]. */
    fun next(from: LocalDateTime, repeat: Repeat, now: LocalDateTime): LocalDateTime? {
        if (repeat == Repeat.NONE) return null
        var n = 1L
        while (true) {
            val candidate = when (repeat) {
                Repeat.DAILY -> from.plusDays(n)
                Repeat.WEEKLY -> from.plusWeeks(n)
                // Count from the original date each time so day 31 doesn't drift to 28.
                Repeat.MONTHLY -> from.plusMonths(n)
                Repeat.WEEKDAYS -> nextWeekday(if (n == 1L) from else from.plusDays(n - 1))
                Repeat.NONE -> return null
            }
            if (candidate.isAfter(now)) return candidate
            n++
        }
    }

    private fun nextWeekday(from: LocalDateTime): LocalDateTime {
        var d = from.plusDays(1)
        while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.plusDays(1)
        return d
    }
}
