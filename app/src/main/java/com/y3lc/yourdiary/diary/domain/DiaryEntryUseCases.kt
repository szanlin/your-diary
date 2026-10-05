package com.y3lc.yourdiary.diary.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class DiaryEntryUseCases(
  private val repository: DiaryRepository,
  private val entryIdGenerator: EntryIdGenerator = EntryIdGenerator { EntryId(UUID.randomUUID().toString()) },
  private val now: () -> Instant = { Instant.now() },
  private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
  private val entryFactory = DiaryEntryFactory(entryIdGenerator, now)
  private val entryEditor = DiaryEntryEditor(now)

  fun createEntry(): DiaryEntry = entryFactory.createEntry().also(repository::saveEntry)

  fun getEntry(entryId: EntryId): DiaryEntry? = repository.getEntry(entryId)

  fun getPhotoForDisplay(photoId: PhotoId): ByteArray? = try {
    repository.readPhoto(photoId)
  } catch (_: Exception) {
    null
  }

  fun updateEntry(entryId: EntryId, markdown: String, tagIds: List<TagId>): DiaryEntry? {
    val entry = repository.getEntry(entryId) ?: return null
    val updated = entryEditor.updateEntry(entry, markdown, entry.photoIds, tagIds)
    repository.saveEntry(updated)
    return updated
  }

  fun addPhotos(
    entryId: EntryId,
    photos: List<NewPhoto>,
    photoIdGenerator: PhotoIdGenerator = PhotoIdGenerator { PhotoId(UUID.randomUUID().toString()) },
  ): PhotoSaveResult {
    val entry = repository.getEntry(entryId) ?: return PhotoSaveResult(emptyList(), photos.map { it.displayName })
    val savedPhotoIds = mutableListOf<PhotoId>()
    val failedPhotoNames = mutableListOf<String>()
    photos.forEach { photo ->
      try {
        val photoId = photoIdGenerator.generateId()
        repository.savePhoto(
          PhotoMetadata(photoId, photo.mimeType, photo.bytes.size.toLong()),
          photo.bytes,
        )
        savedPhotoIds += photoId
      } catch (_: Exception) {
        failedPhotoNames += photo.displayName
      }
    }
    if (savedPhotoIds.isNotEmpty()) {
      val updated = entryEditor.updateEntry(
        entry = entry,
        markdown = entry.markdown,
        photoIds = (entry.photoIds + savedPhotoIds).distinct(),
        tagIds = entry.tagIds,
      )
      repository.saveEntry(updated)
    }
    return PhotoSaveResult(savedPhotoIds, failedPhotoNames)
  }

  fun removePhoto(entryId: EntryId, photoId: PhotoId): DiaryEntry? {
    val entry = repository.getEntry(entryId) ?: return null
    if (photoId !in entry.photoIds) return entry
    val updated = entryEditor.updateEntry(
      entry = entry,
      markdown = entry.markdown,
      photoIds = entry.photoIds.filterNot { it == photoId },
      tagIds = entry.tagIds,
    )
    repository.saveEntry(updated)
    repository.deletePhoto(photoId)
    return updated
  }

  fun finishEditing(entryId: EntryId, wasNew: Boolean) {
    if (!wasNew) return
    val entry = repository.getEntry(entryId) ?: return
    if (DiaryEntryRules.discardIfEmptyNewEntry(entry) == null) repository.deleteEntry(entryId)
  }

  fun listToday(today: LocalDate = LocalDate.now(zoneId)): List<DiaryEntry> =
    DiaryEntryRules.listTodayEntries(repository.listEntries(), today, zoneId)

  fun searchHistory(query: String, tagId: TagId?): List<DiaryEntry> = repository.searchActiveEntries(query)
    .asSequence()
    .filter { entry -> tagId == null || tagId in entry.tagIds }
    .sortedByDescending { entry -> entry.createdAt }
    .toList()

  fun listTags(): List<Tag> = repository.listTags().sortedBy { tag -> tag.displayName }

  fun createTag(displayName: String): Tag? {
    val normalizedName = displayName.trim()
    if (normalizedName.isEmpty()) return null
    val existing = repository.listTags().firstOrNull { tag -> tag.displayName == normalizedName }
    if (existing != null) return existing
    return Tag(TagId(UUID.randomUUID().toString()), normalizedName).also(repository::saveTag)
  }

  fun moveToTrash(entryId: EntryId) {
    repository.getEntry(entryId)?.let { entry -> repository.saveEntry(entryEditor.deleteEntry(entry)) }
  }

  fun listTrash(): List<DiaryEntry> {
    removeExpiredDeletedEntries()
    return repository.listEntries().filter { entry -> entry.isDeleted }.sortedByDescending { entry -> entry.deletedAt }
  }

  fun restoreEntry(entryId: EntryId) {
    repository.getEntry(entryId)?.takeIf { entry -> entry.isDeleted }?.let { entry ->
      repository.saveEntry(entryEditor.restoreEntry(entry))
    }
  }

  fun permanentlyDeleteEntry(entryId: EntryId) {
    repository.deleteEntry(entryId)
  }

  private fun removeExpiredDeletedEntries() {
    val allEntries = repository.listEntries()
    val retainedEntries = DiaryEntryRules.removeExpiredDeletedEntries(allEntries, now())
    val retainedIds = retainedEntries.mapTo(hashSetOf()) { entry -> entry.id }
    allEntries.filter { entry -> entry.id !in retainedIds }.forEach { entry -> repository.deleteEntry(entry.id) }
  }
}
