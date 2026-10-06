package com.y3lc.yourdiary

import com.y3lc.yourdiary.diary.domain.DiaryEntry
import com.y3lc.yourdiary.diary.domain.EntryId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayCalendarTest {
  @Test
  fun buildRecentDiaryDaysMarksOnlyDaysWithActiveEntriesInTheSevenDayWindow() {
    val zoneId = ZoneId.of("Asia/Shanghai")
    val entries = listOf(
      createEntry("today", "2026-10-06T01:00:00Z"),
      createEntry("three-days-ago", "2026-10-02T16:00:00Z"),
      createEntry("outside-window", "2026-09-29T15:59:00Z"),
    )

    val days = buildRecentDiaryDays(entries, LocalDate.of(2026, 10, 6), zoneId)

    assertEquals(
      listOf(
        LocalDate.of(2026, 9, 30),
        LocalDate.of(2026, 10, 1),
        LocalDate.of(2026, 10, 2),
        LocalDate.of(2026, 10, 3),
        LocalDate.of(2026, 10, 4),
        LocalDate.of(2026, 10, 5),
        LocalDate.of(2026, 10, 6),
      ),
      days.map { it.date },
    )
    assertEquals(listOf(false, false, false, true, false, false, true), days.map { it.hasEntries })
  }

  private fun createEntry(id: String, createdAt: String): DiaryEntry = DiaryEntry(
    id = EntryId(id),
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(createdAt),
    markdown = id,
    photoIds = emptyList(),
    tagIds = emptyList(),
    deletedAt = null,
  )
}
