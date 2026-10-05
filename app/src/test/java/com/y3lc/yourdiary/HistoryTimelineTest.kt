package com.y3lc.yourdiary

import com.y3lc.yourdiary.diary.domain.DiaryEntry
import com.y3lc.yourdiary.diary.domain.EntryId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryTimelineTest {
  @Test
  fun groupEntriesByDateKeepsDescendingDatesAndEntriesTogether() {
    val zoneId = ZoneId.of("Asia/Shanghai")
    val first = createEntry("first", "2026-10-03T16:30:00Z")
    val second = createEntry("second", "2026-10-03T16:00:00Z")
    val previousDay = createEntry("previous", "2026-10-02T15:59:00Z")

    val groups = groupEntriesByDate(listOf(first, second, previousDay), zoneId)

    assertEquals(listOf(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 2)), groups.map { it.date })
    assertEquals(listOf(first, second), groups[0].entries)
    assertEquals(listOf(previousDay), groups[1].entries)
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
