package com.y3lc.yourdiary.diary.data

import com.y3lc.yourdiary.diary.domain.DiaryEntry
import com.y3lc.yourdiary.diary.domain.EntryId
import com.y3lc.yourdiary.diary.domain.PhotoId
import com.y3lc.yourdiary.diary.domain.PhotoMetadata
import com.y3lc.yourdiary.diary.domain.Tag
import com.y3lc.yourdiary.diary.domain.TagId
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncryptedDiaryRepositoryTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private val key = UnlockedDiaryKey(ByteArray(32) { (it + 1).toByte() })

  @Test
  fun saveThenReopenKeepsAllMetadataEncryptedAndSearchesOnlyInMemory() {
    val storageDirectory = temporaryFolder.newFolder("diary")
    val repository = EncryptedDiaryRepository(storageDirectory, key)
    val entry = createEntry()
    val photoBytes = "photo bytes".toByteArray(StandardCharsets.UTF_8)

    repository.saveTag(Tag(TagId("meeting"), "会见"))
    repository.savePhoto(PhotoMetadata(PhotoId("photo-1"), "image/jpeg", photoBytes.size.toLong()), photoBytes)
    repository.saveEntry(entry)

    val reopened = EncryptedDiaryRepository(storageDirectory, key)
    assertEquals(entry, reopened.getEntry(entry.id))
    assertEquals(listOf(Tag(TagId("meeting"), "会见")), reopened.listTags())
    assertArrayEquals(photoBytes, reopened.readPhoto(PhotoId("photo-1")))
    assertEquals(listOf(entry), reopened.searchActiveEntries("会议记录"))

    val stateBytes = File(storageDirectory, "diary-state.enc").readBytes()
    assertFalse(String(stateBytes, StandardCharsets.UTF_8).contains("会议记录"))
    assertFalse(File(storageDirectory, "photos/photo-1.enc").readText().contains("photo bytes"))
  }

  @Test
  fun searchExcludesDeletedEntries() {
    val repository = EncryptedDiaryRepository(temporaryFolder.newFolder("diary"), key)
    val active = createEntry(id = "active", markdown = "需要检索")
    val deleted = createEntry(
      id = "deleted",
      markdown = "需要检索",
      deletedAt = Instant.parse("2026-10-04T03:00:00Z"),
    )

    repository.saveEntry(active)
    repository.saveEntry(deleted)

    assertEquals(listOf(active), repository.searchActiveEntries("检索"))
    assertTrue(repository.searchActiveEntries("不存在").isEmpty())
  }

  @Test
  fun closedRepositoryRejectsBackupAccess() {
    val repository = EncryptedDiaryRepository(temporaryFolder.newFolder("diary"), key)

    repository.close()

    assertThrows(IllegalStateException::class.java) {
      repository.createBackupSnapshot()
    }
  }

  private fun createEntry(
    id: String = "entry-1",
    markdown: String = "会议记录",
    deletedAt: Instant? = null,
  ): DiaryEntry = DiaryEntry(
    id = EntryId(id),
    createdAt = Instant.parse("2026-10-04T01:02:03Z"),
    updatedAt = Instant.parse("2026-10-04T02:03:04Z"),
    markdown = markdown,
    photoIds = listOf(PhotoId("photo-1")),
    tagIds = listOf(TagId("meeting")),
    deletedAt = deletedAt,
  )
}
