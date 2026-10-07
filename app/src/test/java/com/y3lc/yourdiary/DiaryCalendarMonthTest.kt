package com.y3lc.yourdiary

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class DiaryCalendarMonthTest {

  @Test
  fun createsMondayFirstGridForTheWholeMonth() {
    val days = createCalendarMonthDays(YearMonth.of(2026, 10))

    assertEquals(35, days.size)
    assertEquals(LocalDate.of(2026, 9, 28), days.first())
    assertEquals(LocalDate.of(2026, 11, 1), days.last())
  }

  @Test
  fun hidesAdjacentMonthDatesWhileKeepingTheirGridSlots() {
    val month = YearMonth.of(2026, 10)

    assertEquals("", getCalendarDayLabel(LocalDate.of(2026, 9, 28), month))
    assertEquals("1", getCalendarDayLabel(LocalDate.of(2026, 10, 1), month))
    assertEquals("", getCalendarDayLabel(LocalDate.of(2026, 11, 1), month))
  }
}
