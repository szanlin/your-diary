package com.y3lc.yourdiary.diary.domain

data class PhotoMetadata(
  val id: PhotoId,
  val mimeType: String,
  val byteSize: Long,
) {
  init {
    require(mimeType.isNotBlank()) { "照片 MIME 类型不得为空" }
    require(byteSize >= 0) { "照片大小不得为负数" }
  }
}

interface DiaryRepository {
  fun getEntry(entryId: EntryId): DiaryEntry?

  fun listEntries(): List<DiaryEntry>

  fun saveEntry(entry: DiaryEntry)

  fun deleteEntry(entryId: EntryId)

  fun getTag(tagId: TagId): Tag?

  fun listTags(): List<Tag>

  fun saveTag(tag: Tag)

  fun getPhotoMetadata(photoId: PhotoId): PhotoMetadata?

  fun savePhoto(photoMetadata: PhotoMetadata, bytes: ByteArray)

  fun readPhoto(photoId: PhotoId): ByteArray?

  fun deletePhoto(photoId: PhotoId)

  fun searchActiveEntries(query: String): List<DiaryEntry>
}
