package com.andy.todo

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Human-friendly deadline text. */
object Format {
    fun time(c: Context, ms: Long): String =
        DateFormat.format(if (DateFormat.is24HourFormat(c)) "HH:mm" else "h:mm a", ms).toString()

    fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    fun daysFromToday(ms: Long): Long = ChronoUnit.DAYS.between(LocalDate.now(), localDate(ms))

    fun day(ms: Long): String {
        val d = localDate(ms)
        val diff = daysFromToday(ms)
        return when {
            diff == 0L -> "Today"
            diff == 1L -> "Tomorrow"
            diff == -1L -> "Yesterday"
            d.year == LocalDate.now().year -> DateFormat.format("EEE, MMM d", ms).toString()
            else -> DateFormat.format("MMM d, yyyy", ms).toString()
        }
    }

    fun dayTime(c: Context, ms: Long) = "${day(ms)}, ${time(c, ms)}"

    /** e.g. "Today, 5:00 PM · in 2h" or "Overdue · Yesterday, 9:00 AM". */
    fun due(c: Context, ms: Long): String {
        val diff = ms - System.currentTimeMillis()
        return if (diff < 0) "Overdue · ${dayTime(c, ms)}" else "${dayTime(c, ms)} · in ${span(diff)}"
    }

    fun span(ms: Long): String {
        val min = ms / 60_000L
        if (min < 1) return "<1m"
        if (min < 60) return "${min}m"
        val h = min / 60
        if (h < 24) return if (h < 3 && min % 60 > 0) "${h}h ${min % 60}m" else "${h}h"
        return "${h / 24}d"
    }

    fun reminderChip(minutes: Int) = when (minutes) {
        Task.NO_REMINDER -> "None"
        0 -> "At deadline"
        15 -> "15 min"
        60 -> "1 hour"
        1440 -> "1 day"
        else -> "$minutes min"
    }
}
