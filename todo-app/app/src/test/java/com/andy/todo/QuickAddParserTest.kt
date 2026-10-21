package com.andy.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class QuickAddParserTest {
    // Wednesday, 21 Oct 2026, 10:00
    private val now = LocalDateTime.of(2026, 10, 21, 10, 0)
    private fun parse(s: String, lists: List<String> = emptyList()) = QuickAddParser.parse(s, now, lists)

    @Test fun plainTitleIsUntouched() {
        val r = parse("Buy milk")
        assertEquals("Buy milk", r.title)
        assertFalse(r.found)
    }

    @Test fun tomorrowAtTime() {
        val r = parse("Call mom tomorrow at 6pm")
        assertEquals("Call mom", r.title)
        assertEquals(LocalDate.of(2026, 10, 22), r.date)
        assertEquals(LocalTime.of(18, 0), r.time)
    }

    @Test fun timeWithMinutesAndNoDateLaterToday() {
        val r = parse("Standup 10:30am")
        assertEquals("Standup", r.title)
        assertEquals(LocalDate.of(2026, 10, 21), r.date)
        assertEquals(LocalTime.of(10, 30), r.time)
    }

    @Test fun pastTimeRollsToTomorrow() {
        val r = parse("Gym 7am")
        assertEquals(LocalDate.of(2026, 10, 22), r.date)
        assertEquals(LocalTime.of(7, 0), r.time)
    }

    @Test fun twentyFourHourTime() {
        val r = parse("Train 18:45")
        assertEquals("Train", r.title)
        assertEquals(LocalTime.of(18, 45), r.time)
    }

    @Test fun bareAtHourAssumesEvening() {
        assertEquals(LocalTime.of(18, 0), parse("Dinner at 6").time)
        assertEquals(LocalTime.of(9, 0), parse("Meeting at 9").time)
    }

    @Test fun namedWeekdayAndDanglingBy() {
        val r = parse("Submit report by friday 5pm")
        assertEquals("Submit report", r.title)
        assertEquals(LocalDate.of(2026, 10, 23), r.date)
        assertEquals(LocalTime.of(17, 0), r.time)
    }

    @Test fun sameWeekdayLaterTodayIsToday() {
        val r = parse("Review on wednesday 3pm")
        assertEquals(LocalDate.of(2026, 10, 21), r.date)
    }

    @Test fun abbreviationsNeedAPreposition() {
        val r = parse("Buy sun cream")
        assertEquals("Buy sun cream", r.title)
        assertNull(r.date)
    }

    @Test fun inRelativeSpan() {
        val r = parse("Take pizza out in 20 min")
        assertEquals("Take pizza out", r.title)
        assertEquals(LocalTime.of(10, 20), r.time)
        assertEquals(LocalDate.of(2026, 10, 21), r.date)
        assertEquals(LocalDate.of(2026, 10, 24), parse("Return book in 3 days").date)
    }

    @Test fun dayAndMonth() {
        assertEquals(LocalDate.of(2026, 11, 5), parse("Dentist 5 nov").date)
        assertEquals(LocalDate.of(2026, 12, 25), parse("Gifts on December 25th").date)
        // Already past this year
        assertEquals(LocalDate.of(2027, 3, 1), parse("Taxes march 1").date)
    }

    @Test fun repeatRules() {
        val daily = parse("Take vitamins every day at 9am")
        assertEquals("Take vitamins", daily.title)
        assertEquals(Repeat.DAILY, daily.repeat)
        assertEquals(LocalDate.of(2026, 10, 22), daily.date)

        val weekly = parse("Team sync every monday 11am")
        assertEquals("Team sync", weekly.title)
        assertEquals(Repeat.WEEKLY, weekly.repeat)
        assertEquals(LocalDate.of(2026, 10, 26), weekly.date)

        assertEquals(Repeat.MONTHLY, parse("Pay rent monthly").repeat)
        assertEquals(Repeat.WEEKDAYS, parse("Stretch every weekday").repeat)
    }

    @Test fun partOfDay() {
        val r = parse("Run tomorrow morning")
        assertEquals("Run", r.title)
        assertEquals(LocalDate.of(2026, 10, 22), r.date)
        assertEquals(LocalTime.of(9, 0), r.time)
        assertEquals(LocalTime.of(20, 0), parse("Call dad tonight").time)
    }

    @Test fun priorityAndList() {
        val r = parse("Fix bug !! #Work", listOf("Personal", "Work"))
        assertEquals("Fix bug", r.title)
        assertTrue(r.priority)
        assertEquals("Work", r.listName)
    }

    @Test fun unknownTagStaysInTitle() {
        val r = parse("Read #book", listOf("Work"))
        assertEquals("Read #book", r.title)
        assertNull(r.listName)
    }

    @Test fun titleThatIsOnlyADateKeepsText() {
        assertEquals("tomorrow", parse("tomorrow").title)
    }
}
