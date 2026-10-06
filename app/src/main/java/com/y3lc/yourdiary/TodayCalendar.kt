package com.y3lc.yourdiary

import com.y3lc.yourdiary.diary.domain.DiaryEntry
import java.time.LocalDate
import java.time.ZoneId

data class RecentDiaryDay(
  val date: LocalDate,
  val hasEntries: Boolean,
)

fun buildRecentDiaryDays(
  entries: List<DiaryEntry>,
  today: LocalDate,
  zoneId: ZoneId,
  dayCount: Long = 7,
): List<RecentDiaryDay> {
  require(dayCount > 0) { "日期数量必须大于零" }
  val entryDates = entries.mapTo(hashSetOf()) { entry -> entry.createdAt.atZone(zoneId).toLocalDate() }
  return (dayCount - 1 downTo 0).map { offset ->
    val date = today.minusDays(offset)
    RecentDiaryDay(date = date, hasEntries = date in entryDates)
  }
}
