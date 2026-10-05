package com.y3lc.yourdiary.backup.data

import com.y3lc.yourdiary.backup.domain.BackupPhoto
import com.y3lc.yourdiary.backup.domain.BackupSnapshot
import com.y3lc.yourdiary.diary.domain.DiaryEntry
import com.y3lc.yourdiary.diary.domain.EntryId
import com.y3lc.yourdiary.diary.domain.PhotoId
import com.y3lc.yourdiary.diary.domain.PhotoMetadata
import com.y3lc.yourdiary.diary.domain.Tag
import com.y3lc.yourdiary.diary.domain.TagId
import java.time.Instant
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupContainerTest {
  @Test
  fun encryptThenDecryptRestoresSnapshotWithPhotoBytesUsingIndependentPassword() {
    val snapshot = createSnapshot()

    val encrypted = BackupContainer.encrypt(snapshot, "独立备份密码".toCharArray())
    val decrypted = BackupContainer.decrypt(encrypted, "独立备份密码".toCharArray())

    assertEquals(snapshot.entries, decrypted.entries)
    assertEquals(snapshot.tags, decrypted.tags)
    assertEquals(snapshot.photos.single().metadata, decrypted.photos.single().metadata)
    assertArrayEquals(snapshot.photos.single().bytes, decrypted.photos.single().bytes)
  }

  @Test(expected = SecurityException::class)
  fun decryptRejectsWrongBackupPassword() {
    val encrypted = BackupContainer.encrypt(createSnapshot(), "正确密码".toCharArray())

    BackupContainer.decrypt(encrypted, "错误密码".toCharArray())
  }

  @Test(expected = IllegalArgumentException::class)
  fun decryptRejectsUnsupportedFormatVersionBeforeParsingCiphertext() {
    val encrypted = BackupContainer.encrypt(createSnapshot(), "备份密码".toCharArray())
    encrypted[4] = 0
    encrypted[5] = 0
    encrypted[6] = 0
    encrypted[7] = 2

    BackupContainer.decrypt(encrypted, "备份密码".toCharArray())
  }

  private fun createSnapshot(): BackupSnapshot = BackupSnapshot(
    entries = listOf(
      DiaryEntry(
        id = EntryId("entry-1"),
        createdAt = Instant.parse("2026-10-04T00:00:00Z"),
        updatedAt = Instant.parse("2026-10-04T01:00:00Z"),
        markdown = "# 备份内容",
        photoIds = listOf(PhotoId("photo-1")),
        tagIds = listOf(TagId("tag-1")),
        deletedAt = null,
      ),
    ),
    tags = listOf(Tag(TagId("tag-1"), "记录")),
    photos = listOf(
      BackupPhoto(
        metadata = PhotoMetadata(PhotoId("photo-1"), "image/jpeg", 3),
        bytes = byteArrayOf(1, 2, 3),
      ),
    ),
  )
}
