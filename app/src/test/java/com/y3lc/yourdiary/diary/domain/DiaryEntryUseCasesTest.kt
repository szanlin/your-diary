package com.y3lc.yourdiary.diary.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryEntryUseCasesTest {
  private val now = Instant.parse("2026-10-04T01:02:03Z")
  private val repository = InMemoryDiaryRepository()
  private val useCases = DiaryEntryUseCases(
    repository = repository,
    entryIdGenerator = EntryIdGenerator { EntryId("created-entry") },
    now = { now },
    zoneId = ZoneId.of("Asia/Shanghai"),
  )

  @Test
  fun createEntryPersistsEntryImmediately() {
    val entry = useCases.createEntry()

    assertEquals(entry, repository.getEntry(entry.id))
    assertEquals(now, entry.createdAt)
  }

  @Test
  fun finishNewEntryRemovesAnEmptyEntry() {
    val entry = useCases.createEntry()

    useCases.finishEditing(entry.id, wasNew = true)

    assertNull(repository.getEntry(entry.id))
  }

  @Test
  fun addPhotosEncryptsThroughRepositoryAndAssociatesOnlySavedPhotosWithEntry() {
    val entry = useCases.createEntry()
    val firstBytes = byteArrayOf(1, 2, 3)
    val secondBytes = byteArrayOf(4, 5)

    val result = useCases.addPhotos(
      entryId = entry.id,
      photos = listOf(
        NewPhoto(displayName = "第一张", mimeType = "image/jpeg", bytes = firstBytes),
        NewPhoto(displayName = "第二张", mimeType = "image/png", bytes = secondBytes),
      ),
      photoIdGenerator = PhotoIdGenerator { PhotoId("photo-${nextPhotoId++}") },
    )

    assertEquals(listOf(PhotoId("photo-0"), PhotoId("photo-1")), result.savedPhotoIds)
    assertTrue(result.failedPhotoNames.isEmpty())
    assertEquals(result.savedPhotoIds, requireNotNull(repository.getEntry(entry.id)).photoIds)
    assertArrayEquals(firstBytes, repository.readPhoto(PhotoId("photo-0")))
    assertArrayEquals(secondBytes, repository.readPhoto(PhotoId("photo-1")))
  }

  @Test
  fun removePhotoDetachesItFromEntryAndDeletesEncryptedPhoto() {
    val entry = useCases.createEntry()
    useCases.addPhotos(
      entryId = entry.id,
      photos = listOf(NewPhoto("待移除", "image/jpeg", byteArrayOf(8))),
      photoIdGenerator = PhotoIdGenerator { PhotoId("remove-me") },
    )

    val updated = useCases.removePhoto(entry.id, PhotoId("remove-me"))

    assertTrue(requireNotNull(updated).photoIds.isEmpty())
    assertNull(repository.readPhoto(PhotoId("remove-me")))
  }

  @Test
  fun getPhotoForDisplayReturnsTheDecryptedBytesForAnAttachedPhoto() {
    val entry = useCases.createEntry()
    val photoBytes = byteArrayOf(9, 7, 5)
    useCases.addPhotos(
      entryId = entry.id,
      photos = listOf(NewPhoto("阅读照片", "image/jpeg", photoBytes)),
      photoIdGenerator = PhotoIdGenerator { PhotoId("reader-photo") },
    )

    val result = useCases.getPhotoForDisplay(PhotoId("reader-photo"))

    assertArrayEquals(photoBytes, result)
  }

  @Test
  fun getPhotoForDisplayReturnsNullWhenThePhotoCannotBeRead() {
    assertNull(useCases.getPhotoForDisplay(PhotoId("missing-photo")))
  }

  @Test
  fun getPhotoForDisplayReturnsNullWhenEncryptedPhotoReadFails() {
    repository.readFailureId = PhotoId("corrupted-photo")

    assertNull(useCases.getPhotoForDisplay(PhotoId("corrupted-photo")))
  }

  @Test
  fun listTodayReturnsNewestActiveEntryFirst() {
    val earlier = createEntry("early", "2026-10-03T16:01:00Z")
    val later = createEntry("later", "2026-10-03T16:02:00Z")
    repository.saveEntry(earlier)
    repository.saveEntry(later)

    val entries = useCases.listToday(LocalDate.of(2026, 10, 4))

    assertEquals(listOf(later, earlier), entries)
  }

  @Test
  fun moveToTrashMakesEntryInvisibleToHistoryAndRestorable() {
    val entry = useCases.createEntry()
    useCases.updateEntry(entry.id, "可找回", emptyList())

    useCases.moveToTrash(entry.id)

    assertFalse(useCases.searchHistory("可找回", null).any { it.id == entry.id })
    assertEquals(listOf(entry.id), useCases.listTrash().map { it.id })

    useCases.restoreEntry(entry.id)

    assertFalse(requireNotNull(repository.getEntry(entry.id)).isDeleted)
  }

  private fun createEntry(id: String, createdAt: String): DiaryEntry = DiaryEntry(
    id = EntryId(id),
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(createdAt),
    markdown = id,
    photoIds = emptyList(),
    tagIds = emptyList(),
    deletedAt = null,
  )

  private var nextPhotoId = 0
}

private class InMemoryDiaryRepository : DiaryRepository {
  private val entries = linkedMapOf<EntryId, DiaryEntry>()
  private val tags = linkedMapOf<TagId, Tag>()
  private val photos = linkedMapOf<PhotoId, Pair<PhotoMetadata, ByteArray>>()
  var readFailureId: PhotoId? = null
  override fun getEntry(entryId: EntryId): DiaryEntry? = entries[entryId]
  override fun listEntries(): List<DiaryEntry> = entries.values.toList()
  override fun saveEntry(entry: DiaryEntry) { entries[entry.id] = entry }
  override fun deleteEntry(entryId: EntryId) { entries.remove(entryId) }
  override fun getTag(tagId: TagId): Tag? = tags[tagId]
  override fun listTags(): List<Tag> = tags.values.toList()
  override fun saveTag(tag: Tag) { tags[tag.id] = tag }
  override fun getPhotoMetadata(photoId: PhotoId): PhotoMetadata? = photos[photoId]?.first
  override fun savePhoto(photoMetadata: PhotoMetadata, bytes: ByteArray) { photos[photoMetadata.id] = photoMetadata to bytes }
  override fun readPhoto(photoId: PhotoId): ByteArray? {
    if (photoId == readFailureId) throw IllegalStateException("解密失败")
    return photos[photoId]?.second
  }
  override fun deletePhoto(photoId: PhotoId) { photos.remove(photoId) }
  override fun searchActiveEntries(query: String): List<DiaryEntry> = entries.values.filter { !it.isDeleted && it.markdown.contains(query) }
}
