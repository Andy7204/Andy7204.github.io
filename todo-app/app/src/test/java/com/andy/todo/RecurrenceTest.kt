package com.andy.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class RecurrenceTest {
    private val now = LocalDateTime.of(2026, 10, 21, 10, 0) // Wednesday

    @Test fun noneHasNoNext() = assertNull(Recurrence.next(now, Repeat.NONE, now))

    @Test fun dailyAdvancesOneDay() {
        val from = LocalDateTime.of(2026, 10, 21, 18, 0)
        assertEquals(LocalDateTime.of(2026, 10, 22, 18, 0), Recurrence.next(from, Repeat.DAILY, now))
    }

    @Test fun overdueDailySkipsToFuture() {
        val from = LocalDateTime.of(2026, 10, 15, 9, 0)
        assertEquals(LocalDateTime.of(2026, 10, 22, 9, 0), Recurrence.next(from, Repeat.DAILY, now))
    }

    @Test fun weekdaysSkipWeekend() {
        val friday = LocalDateTime.of(2026, 10, 23, 9, 0)
        assertEquals(LocalDateTime.of(2026, 10, 26, 9, 0), Recurrence.next(friday, Repeat.WEEKDAYS, now))
    }

    @Test fun weeklyAndMonthly() {
        val from = LocalDateTime.of(2026, 10, 21, 18, 0)
        assertEquals(LocalDateTime.of(2026, 10, 28, 18, 0), Recurrence.next(from, Repeat.WEEKLY, now))
        val jan31 = LocalDateTime.of(2027, 1, 31, 9, 0)
        assertEquals(LocalDateTime.of(2027, 2, 28, 9, 0), Recurrence.next(jan31, Repeat.MONTHLY, now))
    }
}
