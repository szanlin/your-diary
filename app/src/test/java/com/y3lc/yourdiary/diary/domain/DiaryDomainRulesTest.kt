package com.y3lc.yourdiary.diary.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DiaryDomainRulesTest {
  private val zoneId: ZoneId = ZoneId.of("Asia/Shanghai")

  @Test
  fun createEntryAssignsGeneratedIdAndCurrentTimestamps() {
    val createdAt = Instant.parse("2026-10-04T01:02:03Z")
    val factory = DiaryEntryFactory(
      entryIdGenerator = FixedEntryIdGenerator("entry-1"),
      now = { createdAt },
    )

    val entry = factory.createEntry()

    assertEquals(EntryId("entry-1"), entry.id)
    assertEquals(createdAt, entry.createdAt)
    assertEquals(createdAt, entry.updatedAt)
  }

  @Test
  fun updateEntryPreservesIdAndCreationTime() {
    val createdAt = Instant.parse("2026-10-04T01:02:03Z")
    val updatedAt = Instant.parse("2026-10-04T02:03:04Z")
    val entry = createEntry(createdAt = createdAt)

    val updated = DiaryEntryEditor(now = { updatedAt }).updateEntry(
      entry = entry,
      markdown = "修订后的正文",
      photoIds = emptyList(),
      tagIds = emptyList(),
    )

    assertEquals(entry.id, updated.id)
    assertEquals(entry.createdAt, updated.createdAt)
    assertEquals(updatedAt, updated.updatedAt)
    assertEquals("修订后的正文", updated.markdown)
  }

  @Test
  fun discardEmptyNewEntryReturnsNull() {
    val entry = createEntry()

    val result = DiaryEntryRules.discardIfEmptyNewEntry(entry)

    assertNull(result)
  }

  @Test
  fun keepNewEntryWithWhitespaceFreeContentOrAttachmentOrTag() {
    val editor = DiaryEntryEditor(now = { Instant.parse("2026-10-04T02:03:04Z") })
    val entry = editor.updateEntry(
      entry = createEntry(),
      markdown = "有内容",
      photoIds = emptyList(),
      tagIds = emptyList(),
    )

    val result = DiaryEntryRules.discardIfEmptyNewEntry(entry)

    assertSame(entry, result)
  }

  @Test
  fun listTodayEntriesSortsActiveEntriesByCreationTimeDescending() {
    val today = LocalDate.of(2026, 10, 4)
    val earliest = createEntry(id = "early", createdAt = Instant.parse("2026-10-03T16:10:00Z"))
    val latest = createEntry(id = "late", createdAt = Instant.parse("2026-10-03T16:30:00Z"))
    val deleted = createEntry(
      id = "deleted",
      createdAt = Instant.parse("2026-10-03T16:45:00Z"),
      deletedAt = Instant.parse("2026-10-03T17:00:00Z"),
    )
    val yesterday = createEntry(id = "yesterday", createdAt = Instant.parse("2026-10-03T15:59:59Z"))

    val entries = DiaryEntryRules.listTodayEntries(
      entries = listOf(earliest, latest, deleted, yesterday),
      today = today,
      zoneId = zoneId,
    )

    assertEquals(listOf(latest, earliest), entries)
  }

  @Test
  fun updateEntryDeduplicatesTagAssociationsByTagId() {
    val entry = DiaryEntryEditor(now = { Instant.parse("2026-10-04T02:03:04Z") }).updateEntry(
      entry = createEntry(),
      markdown = "",
      photoIds = emptyList(),
      tagIds = listOf(TagId("work"), TagId("reading"), TagId("work")),
    )

    assertEquals(listOf(TagId("work"), TagId("reading")), entry.tagIds)
  }

  @Test
  fun deduplicateTagsKeepsOneTagForEachId() {
    val tags = TagRules.deduplicateById(
      listOf(
        Tag(TagId("work"), "工作"),
        Tag(TagId("work"), "工作的新名称"),
        Tag(TagId("reading"), "阅读"),
      ),
    )

    assertEquals(listOf(Tag(TagId("work"), "工作"), Tag(TagId("reading"), "阅读")), tags)
  }

  @Test
  fun removeExpiredDeletedEntriesRemovesEntriesAtThirtyDayBoundary() {
    val now = Instant.parse("2026-11-03T00:00:00Z")
    val expiresNow = createEntry(id = "expires", deletedAt = now.minusSeconds(30L * 24L * 60L * 60L))
    val stillRetained = createEntry(id = "kept", deletedAt = now.minusSeconds(30L * 24L * 60L * 60L - 1L))
    val active = createEntry(id = "active")

    val retained = DiaryEntryRules.removeExpiredDeletedEntries(
      entries = listOf(expiresNow, stillRetained, active),
      now = now,
    )

    assertEquals(listOf(stillRetained, active), retained)
  }

  @Test
  fun mergeEntryChoosesEntireRemoteStateWhenRemoteIsNewer() {
    val local = createEntry(
      markdown = "本机正文",
      updatedAt = Instant.parse("2026-10-04T01:00:00Z"),
      tagIds = listOf(TagId("local")),
    )
    val remote = createEntry(
      markdown = "备份正文",
      updatedAt = Instant.parse("2026-10-04T02:00:00Z"),
      deletedAt = Instant.parse("2026-10-04T02:00:00Z"),
      tagIds = listOf(TagId("remote")),
    )

    val merged = DiaryEntryRules.mergeEntry(local = local, restored = remote)

    assertEquals(remote, merged)
  }

  @Test
  fun mergeEntryKeepsLocalStateWhenTimestampsAreEqual() {
    val time = Instant.parse("2026-10-04T01:00:00Z")
    val local = createEntry(markdown = "本机正文", updatedAt = time)
    val remote = createEntry(markdown = "备份正文", updatedAt = time, deletedAt = time)

    val merged = DiaryEntryRules.mergeEntry(local = local, restored = remote)

    assertEquals(local, merged)
    assertFalse(merged.isDeleted)
  }

  private fun createEntry(
    id: String = "entry-1",
    createdAt: Instant = Instant.parse("2026-10-04T01:02:03Z"),
    updatedAt: Instant = createdAt,
    markdown: String = "",
    tagIds: List<TagId> = emptyList(),
    deletedAt: Instant? = null,
  ): DiaryEntry = DiaryEntry(
    id = EntryId(id),
    createdAt = createdAt,
    updatedAt = updatedAt,
    markdown = markdown,
    photoIds = emptyList(),
    tagIds = tagIds,
    deletedAt = deletedAt,
  )
}

private class FixedEntryIdGenerator(
  private val value: String,
) : EntryIdGenerator {
  override fun generateId(): EntryId = EntryId(value)
}
