package com.y3lc.yourdiary

import com.y3lc.yourdiary.diary.domain.DiaryEntry
import java.time.LocalDate
import java.time.ZoneId

data class HistoryDateGroup(
  val date: LocalDate,
  val entries: List<DiaryEntry>,
)

fun groupEntriesByDate(entries: List<DiaryEntry>, zoneId: ZoneId): List<HistoryDateGroup> = entries
  .groupBy { entry -> entry.createdAt.atZone(zoneId).toLocalDate() }
  .toSortedMap(compareByDescending { date -> date })
  .map { (date, groupedEntries) ->
    HistoryDateGroup(
      date = date,
      entries = groupedEntries.sortedByDescending { entry -> entry.createdAt },
    )
  }
