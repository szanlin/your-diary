package com.y3lc.yourdiary.diary.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object DiaryEntryRules {
  private const val recycleBinRetentionDays = 30L

  fun discardIfEmptyNewEntry(entry: DiaryEntry): DiaryEntry? =
    entry.takeUnless { it.markdown.isBlank() && it.photoIds.isEmpty() && it.tagIds.isEmpty() }

  fun listTodayEntries(
    entries: List<DiaryEntry>,
    today: LocalDate,
    zoneId: ZoneId,
  ): List<DiaryEntry> = entries
    .asSequence()
    .filter { !it.isDeleted }
    .filter { it.createdAt.atZone(zoneId).toLocalDate() == today }
    .sortedByDescending { it.createdAt }
    .toList()

  fun removeExpiredDeletedEntries(
    entries: List<DiaryEntry>,
    now: Instant,
  ): List<DiaryEntry> = entries.filter { entry ->
    val deletedAt = entry.deletedAt
    deletedAt == null || deletedAt.plus(recycleBinRetentionDays, ChronoUnit.DAYS).isAfter(now)
  }

  fun mergeEntry(local: DiaryEntry, restored: DiaryEntry): DiaryEntry {
    require(local.id == restored.id) { "只能合并相同 ID 的日记条目" }
    return if (restored.updatedAt.isAfter(local.updatedAt)) restored else local
  }
}

object TagRules {
  fun deduplicateById(tags: List<Tag>): List<Tag> {
    val tagsById = linkedMapOf<TagId, Tag>()
    tags.forEach { tag -> tagsById.putIfAbsent(tag.id, tag) }
    return tagsById.values.toList()
  }
}
