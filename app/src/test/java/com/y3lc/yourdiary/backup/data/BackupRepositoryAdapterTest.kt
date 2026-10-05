package com.y3lc.yourdiary.backup.data

import com.y3lc.yourdiary.diary.data.EncryptedDiaryRepository
import com.y3lc.yourdiary.diary.data.UnlockedDiaryKey
import com.y3lc.yourdiary.diary.domain.DiaryEntry
import com.y3lc.yourdiary.diary.domain.EntryId
import com.y3lc.yourdiary.diary.domain.PhotoId
import com.y3lc.yourdiary.diary.domain.PhotoMetadata
import com.y3lc.yourdiary.diary.domain.Tag
import com.y3lc.yourdiary.diary.domain.TagId
import java.time.Instant
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupRepositoryAdapterTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private val key = UnlockedDiaryKey(ByteArray(32) { index -> (index + 1).toByte() })

  @Test
  fun exportThenRestoreMergesActiveEntriesTagsAndPhotosButExcludesTrash() {
    val source = EncryptedDiaryRepository(temporaryFolder.newFolder("source"), key)
    source.saveTag(Tag(TagId("work"), "工作"))
    source.savePhoto(PhotoMetadata(PhotoId("active-photo"), "image/jpeg", 3), byteArrayOf(1, 2, 3))
    source.savePhoto(PhotoMetadata(PhotoId("deleted-photo"), "image/jpeg", 2), byteArrayOf(4, 5))
    source.saveEntry(createEntry("active", "正在备份", listOf(PhotoId("active-photo"))))
    source.saveEntry(createEntry("deleted", "不应导出", listOf(PhotoId("deleted-photo")), deleted = true))

    val encrypted = BackupRepositoryAdapter(source).createEncryptedBackup("独立密码".toCharArray())
    val target = EncryptedDiaryRepository(temporaryFolder.newFolder("target"), key)

    BackupRepositoryAdapter(target).restoreEncryptedBackup(encrypted, "独立密码".toCharArray())

    assertEquals("正在备份", target.getEntry(EntryId("active"))?.markdown)
    assertNull(target.getEntry(EntryId("deleted")))
    assertEquals("工作", target.getTag(TagId("work"))?.displayName)
    assertArrayEquals(byteArrayOf(1, 2, 3), target.readPhoto(PhotoId("active-photo")))
    assertNull(target.readPhoto(PhotoId("deleted-photo")))
  }

  @Test
  fun restoreWithWrongPasswordDoesNotChangeCurrentRepository() {
    val source = EncryptedDiaryRepository(temporaryFolder.newFolder("source"), key)
    source.saveEntry(createEntry("source", "备份内容", emptyList()))
    val encrypted = BackupRepositoryAdapter(source).createEncryptedBackup("正确密码".toCharArray())
    val target = EncryptedDiaryRepository(temporaryFolder.newFolder("target"), key)
    val local = createEntry("local", "本机内容", emptyList())
    target.saveEntry(local)

    val restoreResult = runCatching {
      BackupRepositoryAdapter(target).restoreEncryptedBackup(encrypted, "错误密码".toCharArray())
    }

    assertTrue(restoreResult.isFailure)
    assertEquals(local, target.getEntry(local.id))
    assertNull(target.getEntry(EntryId("source")))
  }

  private fun createEntry(
    id: String,
    markdown: String,
    photoIds: List<PhotoId>,
    deleted: Boolean = false,
  ): DiaryEntry = DiaryEntry(
    id = EntryId(id),
    createdAt = Instant.parse("2026-10-04T01:00:00Z"),
    updatedAt = Instant.parse("2026-10-04T02:00:00Z"),
    markdown = markdown,
    photoIds = photoIds,
    tagIds = listOf(TagId("work")),
    deletedAt = if (deleted) Instant.parse("2026-10-04T03:00:00Z") else null,
  )
}
