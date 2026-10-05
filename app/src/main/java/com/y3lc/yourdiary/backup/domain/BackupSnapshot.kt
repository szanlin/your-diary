package com.y3lc.yourdiary.backup.domain

import com.y3lc.yourdiary.diary.domain.DiaryEntry
import com.y3lc.yourdiary.diary.domain.PhotoMetadata
import com.y3lc.yourdiary.diary.domain.Tag

data class BackupSnapshot(
  val entries: List<DiaryEntry>,
  val tags: List<Tag>,
  val photos: List<BackupPhoto>,
)

class BackupPhoto(
  val metadata: PhotoMetadata,
  bytes: ByteArray,
) {
  val bytes: ByteArray = bytes.copyOf()

  init {
    require(metadata.byteSize == bytes.size.toLong()) { "照片元数据大小与备份内容不一致" }
  }
}

object BackupExport {
  fun create(
    entries: List<DiaryEntry>,
    tags: List<Tag>,
    photos: List<BackupPhoto>,
  ): BackupSnapshot {
    val activeEntries = entries.filterNot { it.isDeleted }
    val activePhotoIds = activeEntries
      .flatMap { it.photoIds }
      .toSet()
    return BackupSnapshot(
      entries = activeEntries,
      tags = tags,
      photos = photos.filter { it.metadata.id in activePhotoIds },
    )
  }
}

object BackupMerger {
  fun merge(local: BackupSnapshot, restored: BackupSnapshot): BackupSnapshot = BackupSnapshot(
    entries = mergeEntries(local.entries, restored.entries),
    tags = mergeById(local.tags, restored.tags) { tag -> tag.id },
    photos = mergeById(local.photos, restored.photos) { photo -> photo.metadata.id },
  )

  private fun mergeEntries(local: List<DiaryEntry>, restored: List<DiaryEntry>): List<DiaryEntry> {
    val entriesById = linkedMapOf<com.y3lc.yourdiary.diary.domain.EntryId, DiaryEntry>()
    local.forEach { entry -> entriesById.putIfAbsent(entry.id, entry.normalizeRelations()) }
    restored.forEach { restoredEntry ->
      val localEntry = entriesById[restoredEntry.id]
      if (localEntry == null || restoredEntry.updatedAt.isAfter(localEntry.updatedAt)) {
        entriesById[restoredEntry.id] = restoredEntry.normalizeRelations()
      }
    }
    return entriesById.values.toList()
  }

  private fun DiaryEntry.normalizeRelations(): DiaryEntry = copy(
    photoIds = photoIds.distinct(),
    tagIds = tagIds.distinct(),
  )

  private fun <T, Id> mergeById(
    local: List<T>,
    restored: List<T>,
    getId: (T) -> Id,
  ): List<T> {
    val valuesById = linkedMapOf<Id, T>()
    local.forEach { value -> valuesById.putIfAbsent(getId(value), value) }
    restored.forEach { value -> valuesById.putIfAbsent(getId(value), value) }
    return valuesById.values.toList()
  }
}
