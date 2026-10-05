package com.y3lc.yourdiary.diary.domain

import java.time.Instant

@JvmInline
value class EntryId(val value: String) {
  init {
    require(value.isNotBlank()) { "日记条目 ID 不得为空" }
  }
}

@JvmInline
value class PhotoId(val value: String) {
  init {
    require(value.isNotBlank()) { "照片 ID 不得为空" }
  }
}

@JvmInline
value class TagId(val value: String) {
  init {
    require(value.isNotBlank()) { "Tag ID 不得为空" }
  }
}

data class DiaryEntry(
  val id: EntryId,
  val createdAt: Instant,
  val updatedAt: Instant,
  val markdown: String,
  val photoIds: List<PhotoId>,
  val tagIds: List<TagId>,
  val deletedAt: Instant?,
) {
  val isDeleted: Boolean
    get() = deletedAt != null
}

data class Tag(
  val id: TagId,
  val displayName: String,
)

fun interface EntryIdGenerator {
  fun generateId(): EntryId
}

fun interface PhotoIdGenerator {
  fun generateId(): PhotoId
}

data class NewPhoto(
  val displayName: String,
  val mimeType: String,
  val bytes: ByteArray,
) {
  init {
    require(displayName.isNotBlank()) { "照片名称不得为空" }
    require(mimeType.isNotBlank()) { "照片 MIME 类型不得为空" }
  }
}

data class PhotoSaveResult(
  val savedPhotoIds: List<PhotoId>,
  val failedPhotoNames: List<String>,
)
