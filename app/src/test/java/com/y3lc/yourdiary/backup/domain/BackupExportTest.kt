package com.y3lc.yourdiary.backup.domain

import com.y3lc.yourdiary.diary.domain.DiaryEntry
import com.y3lc.yourdiary.diary.domain.EntryId
import com.y3lc.yourdiary.diary.domain.PhotoId
import com.y3lc.yourdiary.diary.domain.PhotoMetadata
import com.y3lc.yourdiary.diary.domain.Tag
import com.y3lc.yourdiary.diary.domain.TagId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupExportTest {
  @Test
  fun createExcludesDeletedEntriesAndTheirPhotosButKeepsAllTags() {
    val active = createEntry("active", listOf(PhotoId("active-photo")))
    val deleted = createEntry(
      id = "deleted",
      photoIds = listOf(PhotoId("deleted-photo")),
      deletedAt = Instant.parse("2026-10-04T02:00:00Z"),
    )
    val activePhoto = BackupPhoto(PhotoMetadata(PhotoId("active-photo"), "image/jpeg", 1), byteArrayOf(1))
    val deletedPhoto = BackupPhoto(PhotoMetadata(PhotoId("deleted-photo"), "image/jpeg", 1), byteArrayOf(2))
    val unusedTag = Tag(TagId("unused"), "未使用")

    val backup = BackupExport.create(
      entries = listOf(active, deleted),
      tags = listOf(unusedTag),
      photos = listOf(activePhoto, deletedPhoto),
    )

    assertEquals(listOf(active), backup.entries)
    assertEquals(listOf(unusedTag), backup.tags)
    assertEquals(listOf(PhotoId("active-photo")), backup.photos.map { it.metadata.id })
  }

  private fun createEntry(
    id: String,
    photoIds: List<PhotoId>,
    deletedAt: Instant? = null,
  ) = DiaryEntry(
    id = EntryId(id),
    createdAt = Instant.parse("2026-10-04T00:00:00Z"),
    updatedAt = Instant.parse("2026-10-04T01:00:00Z"),
    markdown = "内容",
    photoIds = photoIds,
    tagIds = emptyList(),
    deletedAt = deletedAt,
  )
}
