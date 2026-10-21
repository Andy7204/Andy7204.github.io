package com.andy.todo

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Pulls a deadline, repeat rule, priority and list out of free text such as
 * "Pay rent every month on 1 nov 9am !" or "Call mom tomorrow at 6pm #family".
 */
object QuickAddParser {

    data class Result(
        val title: String,
        val date: LocalDate? = null,
        val time: LocalTime? = null,
        val repeat: Repeat? = null,
        val priority: Boolean = false,
        val listName: String? = null,
    ) {
        val found: Boolean get() = date != null || time != null || repeat != null || priority || listName != null
    }

    private const val B = "(?<![\\p{L}\\d])"
    private const val E = "(?![\\p{L}\\d])"
    private val OPT = RegexOption.IGNORE_CASE

    private val DAYS = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY,
        "thurs" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
    )
    private const val FULL_DAYS = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"
    private const val ANY_DAY = "$FULL_DAYS|mon|tues|tue|wed|thurs|thur|thu|fri|sat|sun"
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private const val MONTH = "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"

    private val everyDay = Regex("$B(?:every\\s*day|daily)$E", OPT)
    private val everyWeekday = Regex("$B(?:every\\s+weekdays?|on\\s+weekdays|weekdays)$E", OPT)
    private val everyNamedDay = Regex("${B}every\\s+($ANY_DAY)$E", OPT)
    private val everyWeek = Regex("$B(?:every\\s+week|weekly)$E", OPT)
    private val everyMonth = Regex("$B(?:every\\s+month|monthly)$E", OPT)

    private val inSpan = Regex("${B}in\\s+(\\d{1,3})\\s*(minutes?|mins?|m|hours?|hrs?|h|days?|d|weeks?|w)$E", OPT)
    private val partOfDay = Regex("$B(this|tomorrow|in\\s+the)\\s+(morning|afternoon|evening|night)$E", OPT)
    private val tonight = Regex("${B}tonight$E", OPT)
    private val dayAfter = Regex("${B}(?:the\\s+)?day\\s+after\\s+tomorrow$E", OPT)
    private val dayWord = Regex("$B(today|tomorrow|tmrw|tmr)$E", OPT)
    private val namedDay = Regex("$B(?:(?:on|next|this|by)\\s+($ANY_DAY)|($FULL_DAYS))$E", OPT)
    private val dayMonth = Regex("$B(?:on\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?$MONTH$E", OPT)
    private val monthDay = Regex("$B(?:on\\s+)?$MONTH\\s+(\\d{1,2})(?:st|nd|rd|th)?$E", OPT)
    private val time12 = Regex("$B(?:at\\s+|@\\s*)?(\\d{1,2})(?:[:.](\\d{2}))?\\s*([ap])\\.?m\\.?$E", OPT)
    private val time24 = Regex("$B(?:at\\s+|@\\s*)?([01]?\\d|2[0-3]):([0-5]\\d)$E", OPT)
    private val atHour = Regex("$B(?:at\\s+|@\\s*)(\\d{1,2})$E", OPT)
    private val namedTime = Regex("$B(?:at\\s+)?(noon|midday|midnight)$E", OPT)
    private val bang = Regex("(?<=^|\\s)(?:!{1,3}|!high|!important|!urgent)(?=\\s|$)", OPT)
    private val tag = Regex("(?<=^|\\s)#([\\p{L}\\d_-]+)")
    private val danglingEnd = Regex("(?:\\s+(?:at|on|by|due|for|in|every|from|-|,))+\\s*$", OPT)

    fun parse(text: String, now: LocalDateTime, lists: List<String> = emptyList()): Result {
        var s = " $text "
        var date: LocalDate? = null
        var time: LocalTime? = null
        var repeat: Repeat? = null
        var weekday: DayOfWeek? = null
        val today = now.toLocalDate()

        fun take(re: Regex): MatchResult? {
            val m = re.find(s) ?: return null
            s = s.removeRange(m.range).let { it.substring(0, m.range.first) + " " + it.substring(m.range.first) }
            return m
        }

        // Repeat rules
        take(everyDay)?.let { repeat = Repeat.DAILY }
        if (repeat == null) take(everyWeekday)?.let { repeat = Repeat.WEEKDAYS }
        if (repeat == null) take(everyNamedDay)?.let {
            repeat = Repeat.WEEKLY
            weekday = DAYS[it.groupValues[1].lowercase()]
        }
        if (repeat == null) take(everyWeek)?.let { repeat = Repeat.WEEKLY }
        if (repeat == null) take(everyMonth)?.let { repeat = Repeat.MONTHLY }

        // Relative spans set both date and time
        take(inSpan)?.let {
            val n = it.groupValues[1].toLong()
            val unit = it.groupValues[2].lowercase()
            val dt = when {
                unit.startsWith("m") -> now.plusMinutes(n)
                unit.startsWith("h") -> now.plusHours(n)
                unit.startsWith("d") -> now.plusDays(n)
                else -> now.plusWeeks(n)
            }
            date = dt.toLocalDate()
            time = dt.toLocalTime().withSecond(0).withNano(0)
        }

        take(partOfDay)?.let {
            if (it.groupValues[1].equals("tomorrow", true)) date = today.plusDays(1)
            time = when (it.groupValues[2].lowercase()) {
                "morning" -> LocalTime.of(9, 0)
                "afternoon" -> LocalTime.of(14, 0)
                "evening" -> LocalTime.of(18, 0)
                else -> LocalTime.of(21, 0)
            }
        }
        take(tonight)?.let {
            if (date == null) date = today
            if (time == null) time = LocalTime.of(20, 0)
        }

        // Dates
        if (date == null) take(dayAfter)?.let { date = today.plusDays(2) }
        if (date == null) take(dayWord)?.let {
            date = if (it.groupValues[1].equals("today", true)) today else today.plusDays(1)
        }
        if (date == null && weekday == null) take(namedDay)?.let {
            weekday = DAYS[(it.groupValues[1].ifEmpty { it.groupValues[2] }).lowercase()]
        }
        if (date == null) take(dayMonth)?.let { date = monthDate(it.groupValues[2], it.groupValues[1], today) }
        if (date == null) take(monthDay)?.let { date = monthDate(it.groupValues[1], it.groupValues[2], today) }

        // Times
        if (time == null) take(time12)?.let {
            var h = it.groupValues[1].toInt()
            val min = it.groupValues[2].ifEmpty { "0" }.toInt()
            if (h in 1..12 && min < 60) {
                val pm = it.groupValues[3].equals("p", true)
                if (h == 12) h = 0
                time = LocalTime.of(h + if (pm) 12 else 0, min)
            }
        }
        if (time == null) take(time24)?.let { time = LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
        if (time == null) take(namedTime)?.let {
            time = if (it.groupValues[1].equals("midnight", true)) LocalTime.of(23, 59) else LocalTime.NOON
        }
        if (time == null) take(atHour)?.let {
            val h = it.groupValues[1].toInt()
            // "at 6" almost always means the evening; "at 9" the morning.
            time = when (h) {
                in 1..7 -> LocalTime.of(h + 12, 0)
                in 8..12 -> LocalTime.of(h % 24, 0)
                in 13..23 -> LocalTime.of(h, 0)
                else -> null
            }
        }

        weekday?.let { dow ->
            val t = time ?: LocalTime.of(9, 0)
            date = if (today.dayOfWeek == dow && t.isAfter(now.toLocalTime())) today
            else today.with(TemporalAdjusters.next(dow))
        }
        // A time on its own means the next time the clock shows it.
        if (date == null && time != null) {
            date = if (time!!.isAfter(now.toLocalTime())) today else today.plusDays(1)
        }
        if (date == null && repeat != null) date = today

        val priority = take(bang) != null

        var listName: String? = null
        tag.find(s)?.let { m ->
            val match = lists.firstOrNull { it.equals(m.groupValues[1], true) }
                ?: lists.firstOrNull { it.replace(" ", "").equals(m.groupValues[1], true) }
            if (match != null) {
                listName = match
                take(tag)
            }
        }

        val title = s.replace(Regex("\\s+"), " ").trim().replace(danglingEnd, "").trim()
            .trimEnd(',', '-', ':').trim()
        return Result(title.ifEmpty { text.trim() }, date, time, repeat, priority, listName)
    }

    private fun monthDate(month: String, day: String, today: LocalDate): LocalDate? {
        val m = MONTHS.indexOf(month.lowercase().take(3)) + 1
        val d = day.toIntOrNull() ?: return null
        if (m == 0 || d !in 1..31) return null
        return try {
            val date = LocalDate.of(today.year, m, d)
            if (date.isBefore(today)) date.plusYears(1) else date
        } catch (e: java.time.DateTimeException) {
            null
        }
    }
}
