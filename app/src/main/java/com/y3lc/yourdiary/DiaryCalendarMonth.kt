package com.y3lc.yourdiary

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

fun createCalendarMonthDays(month: YearMonth): List<LocalDate> {
  val firstDay = month.atDay(1)
  val leadingDays = firstDay.dayOfWeek.value - 1
  val firstVisibleDay = firstDay.minusDays(leadingDays.toLong())
  val dayCount = leadingDays + month.lengthOfMonth()
  val visibleDayCount = ((dayCount + 6) / 7) * 7
  return List(visibleDayCount) { index -> firstVisibleDay.plus(index.toLong(), ChronoUnit.DAYS) }
}

fun getCalendarDayLabel(date: LocalDate, month: YearMonth): String =
  if (YearMonth.from(date) == month) date.dayOfMonth.toString() else ""
