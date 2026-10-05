package com.y3lc.yourdiary.diary.data

import com.y3lc.yourdiary.diary.domain.DiaryEntry
import com.y3lc.yourdiary.diary.domain.EntryId
import com.y3lc.yourdiary.diary.domain.PhotoId
import com.y3lc.yourdiary.diary.domain.PhotoMetadata
import com.y3lc.yourdiary.diary.domain.Tag
import com.y3lc.yourdiary.diary.domain.TagId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class DiarySnapshotCodecTest {
  @Test
  fun encodeThenDecodePreservesEntriesTagsAndPhotoMetadata() {
    val snapshot = DiarySnapshot(
      entries = listOf(
        DiaryEntry(
          id = EntryId("entry-1"),
          createdAt = Instant.parse("2026-10-04T01:02:03Z"),
          updatedAt = Instant.parse("2026-10-04T02:03:04Z"),
          markdown = "# 会见\n记录内容",
          photoIds = listOf(PhotoId("photo-1")),
          tagIds = listOf(TagId("meeting")),
          deletedAt = null,
        ),
      ),
      tags = listOf(Tag(TagId("meeting"), "会见")),
      photos = listOf(PhotoMetadata(PhotoId("photo-1"), "image/jpeg", 1024L)),
    )

    val decoded = DiarySnapshotCodec.decode(DiarySnapshotCodec.encode(snapshot))

    assertEquals(snapshot, decoded)
  }
}
