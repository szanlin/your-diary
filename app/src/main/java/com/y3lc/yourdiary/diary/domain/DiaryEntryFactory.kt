package com.y3lc.yourdiary.diary.domain

import java.time.Instant

class DiaryEntryFactory(
  private val entryIdGenerator: EntryIdGenerator,
  private val now: () -> Instant,
) {
  fun createEntry(): DiaryEntry {
    val createdAt = now()
    return DiaryEntry(
      id = entryIdGenerator.generateId(),
      createdAt = createdAt,
      updatedAt = createdAt,
      markdown = "",
      photoIds = emptyList(),
      tagIds = emptyList(),
      deletedAt = null,
    )
  }
}

class DiaryEntryEditor(
  private val now: () -> Instant,
) {
  fun updateEntry(
    entry: DiaryEntry,
    markdown: String,
    photoIds: List<PhotoId>,
    tagIds: List<TagId>,
  ): DiaryEntry = entry.copy(
    updatedAt = now(),
    markdown = markdown,
    photoIds = photoIds.distinct(),
    tagIds = tagIds.distinct(),
  )

  fun deleteEntry(entry: DiaryEntry): DiaryEntry {
    val deletedAt = now()
    return entry.copy(
      updatedAt = deletedAt,
      deletedAt = deletedAt,
    )
  }

  fun restoreEntry(entry: DiaryEntry): DiaryEntry = entry.copy(
    updatedAt = now(),
    deletedAt = null,
  )
}
