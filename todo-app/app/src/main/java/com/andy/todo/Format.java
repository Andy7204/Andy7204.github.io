package com.andy.todo;

import android.content.Context;
import android.text.format.DateFormat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Human-friendly deadline text. */
public final class Format {
    private Format() {}

    public static String time(Context c, long when) {
        String pattern = DateFormat.is24HourFormat(c) ? "HH:mm" : "h:mm a";
        return DateFormat.format(pattern, when).toString();
    }

    public static String day(long when) {
        LocalDate today = LocalDate.now();
        LocalDate d = Instant.ofEpochMilli(when).atZone(ZoneId.systemDefault()).toLocalDate();
        long diff = ChronoUnit.DAYS.between(today, d);
        if (today.getYear() == d.getYear() || Math.abs(diff) <= 1) {
            if (diff == 0) return "Today";
            if (diff == 1) return "Tomorrow";
            if (diff == -1) return "Yesterday";
            return DateFormat.format("EEE, MMM d", when).toString();
        }
        return DateFormat.format("MMM d, yyyy", when).toString();
    }

    /** e.g. "Today, 5:00 PM · in 2h" or "Overdue · Yesterday, 9:00 AM". */
    public static String due(Context c, long when) {
        String abs = day(when) + ", " + time(c, when);
        long diff = when - System.currentTimeMillis();
        if (diff < 0) return "Overdue · " + abs;
        return abs + " · in " + span(diff);
    }

    private static String span(long ms) {
        long min = ms / 60_000L;
        if (min < 1) return "<1m";
        if (min < 60) return min + "m";
        long h = min / 60;
        if (h < 24) return h + "h" + (min % 60 >= 1 && h < 3 ? " " + (min % 60) + "m" : "");
        return String.format(Locale.getDefault(), "%dd", h / 24);
    }

}
