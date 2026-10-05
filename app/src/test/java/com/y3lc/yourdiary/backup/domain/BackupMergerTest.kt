package com.y3lc.yourdiary.backup.domain

import com.y3lc.yourdiary.diary.domain.DiaryEntry
import com.y3lc.yourdiary.diary.domain.EntryId
import com.y3lc.yourdiary.diary.domain.PhotoId
import com.y3lc.yourdiary.diary.domain.PhotoMetadata
import com.y3lc.yourdiary.diary.domain.Tag
import com.y3lc.yourdiary.diary.domain.TagId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMergerTest {
  @Test
  fun mergeKeepsLocalEntryWhenVersionsHaveSameTimestamp() {
    val timestamp = Instant.parse("2026-10-04T01:00:00Z")
    val local = createEntry(markdown = "本机内容", updatedAt = timestamp)
    val restored = createEntry(markdown = "备份内容", updatedAt = timestamp)

    val merged = BackupMerger.merge(
      local = BackupSnapshot(entries = listOf(local), tags = emptyList(), photos = emptyList()),
      restored = BackupSnapshot(entries = listOf(restored), tags = emptyList(), photos = emptyList()),
    )

    assertEquals(listOf(local), merged.entries)
  }

  @Test
  fun mergeUsesNewerRestoredEntryAsWholeVersionIncludingDeletion() {
    val local = createEntry(markdown = "仍在编辑", updatedAt = Instant.parse("2026-10-04T01:00:00Z"))
    val restored = createEntry(
      markdown = "删除前内容",
      updatedAt = Instant.parse("2026-10-04T02:00:00Z"),
      deletedAt = Instant.parse("2026-10-04T02:00:00Z"),
    )

    val merged = BackupMerger.merge(
      local = BackupSnapshot(entries = listOf(local), tags = emptyList(), photos = emptyList()),
      restored = BackupSnapshot(entries = listOf(restored), tags = emptyList(), photos = emptyList()),
    )

    assertEquals(listOf(restored), merged.entries)
  }

  @Test
  fun mergeDoesNotChangeLocalTrashMissingFromBackup() {
    val localTrash = createEntry(
      id = "trash-entry",
      markdown = "本机回收站",
      updatedAt = Instant.parse("2026-10-04T01:00:00Z"),
      deletedAt = Instant.parse("2026-10-04T01:00:00Z"),
    )

    val merged = BackupMerger.merge(
      local = BackupSnapshot(entries = listOf(localTrash), tags = emptyList(), photos = emptyList()),
      restored = BackupSnapshot(entries = emptyList(), tags = emptyList(), photos = emptyList()),
    )

    assertEquals(listOf(localTrash), merged.entries)
  }

  @Test
  fun mergeDeduplicatesTagsPhotosAndEntryRelationsById() {
    val entry = createEntry(
      photoIds = listOf(PhotoId("photo-1"), PhotoId("photo-1")),
      tagIds = listOf(TagId("tag-1"), TagId("tag-1")),
    )
    val photo = BackupPhoto(PhotoMetadata(PhotoId("photo-1"), "image/jpeg", 3), byteArrayOf(1, 2, 3))
    val tag = Tag(TagId("tag-1"), "工作")

    val merged = BackupMerger.merge(
      local = BackupSnapshot(entries = listOf(entry), tags = listOf(tag), photos = listOf(photo)),
      restored = BackupSnapshot(entries = listOf(entry), tags = listOf(tag), photos = listOf(photo)),
    )

    assertEquals(1, merged.tags.size)
    assertEquals(1, merged.photos.size)
    assertEquals(listOf(PhotoId("photo-1")), merged.entries.single().photoIds)
    assertEquals(listOf(TagId("tag-1")), merged.entries.single().tagIds)
    assertTrue(merged.photos.single().bytes.contentEquals(byteArrayOf(1, 2, 3)))
  }

  private fun createEntry(
    id: String = "entry-1",
    markdown: String = "内容",
    updatedAt: Instant = Instant.parse("2026-10-04T01:00:00Z"),
    photoIds: List<PhotoId> = emptyList(),
    tagIds: List<TagId> = emptyList(),
    deletedAt: Instant? = null,
  ) = DiaryEntry(
    id = EntryId(id),
    createdAt = Instant.parse("2026-10-04T00:00:00Z"),
    updatedAt = updatedAt,
    markdown = markdown,
    photoIds = photoIds,
    tagIds = tagIds,
    deletedAt = deletedAt,
  )
}
