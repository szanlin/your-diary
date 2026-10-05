package com.y3lc.yourdiary.diary.data

import com.y3lc.yourdiary.diary.domain.DiaryEntry
import com.y3lc.yourdiary.diary.domain.EntryId
import com.y3lc.yourdiary.diary.domain.PhotoId
import com.y3lc.yourdiary.diary.domain.PhotoMetadata
import com.y3lc.yourdiary.diary.domain.Tag
import com.y3lc.yourdiary.diary.domain.TagId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Instant

data class DiarySnapshot(
  val entries: List<DiaryEntry>,
  val tags: List<Tag>,
  val photos: List<PhotoMetadata>,
)

object DiarySnapshotCodec {
  private const val FORMAT_VERSION = 1
  private const val MAX_COLLECTION_SIZE = 100_000
  private const val MAX_STRING_SIZE_BYTES = 16 * 1024 * 1024

  fun encode(snapshot: DiarySnapshot): ByteArray = ByteArrayOutputStream().use { byteStream ->
    DataOutputStream(byteStream).use { output ->
      output.writeInt(FORMAT_VERSION)
      output.writeCollection(snapshot.entries) { entry -> writeEntry(entry) }
      output.writeCollection(snapshot.tags) { tag -> writeTag(tag) }
      output.writeCollection(snapshot.photos) { photo -> writePhotoMetadata(photo) }
    }
    byteStream.toByteArray()
  }

  fun decode(bytes: ByteArray): DiarySnapshot = try {
    DataInputStream(ByteArrayInputStream(bytes)).use { input ->
      require(input.readInt() == FORMAT_VERSION) { "不支持的日记快照版本" }
      val entries = input.readCollection { readEntry() }
      val tags = input.readCollection { readTag() }
      val photos = input.readCollection { readPhotoMetadata() }
      require(input.available() == 0) { "日记快照包含多余数据" }
      DiarySnapshot(entries = entries, tags = tags, photos = photos)
    }
  } catch (exception: EOFException) {
    throw IllegalArgumentException("日记快照不完整", exception)
  } catch (exception: IOException) {
    throw IllegalArgumentException("日记快照无法读取", exception)
  }

  private fun DataOutputStream.writeEntry(entry: DiaryEntry) {
    writeString(entry.id.value)
    writeLong(entry.createdAt.toEpochMilli())
    writeLong(entry.updatedAt.toEpochMilli())
    writeString(entry.markdown)
    writeCollection(entry.photoIds) { photoId -> writeString(photoId.value) }
    writeCollection(entry.tagIds) { tagId -> writeString(tagId.value) }
    writeBoolean(entry.deletedAt != null)
    entry.deletedAt?.let { writeLong(it.toEpochMilli()) }
  }

  private fun DataInputStream.readEntry(): DiaryEntry {
    val id = EntryId(readString())
    val createdAt = Instant.ofEpochMilli(readLong())
    val updatedAt = Instant.ofEpochMilli(readLong())
    val markdown = readString()
    val photoIds = readCollection { PhotoId(readString()) }
    val tagIds = readCollection { TagId(readString()) }
    val deletedAt = if (readBoolean()) Instant.ofEpochMilli(readLong()) else null
    return DiaryEntry(id, createdAt, updatedAt, markdown, photoIds, tagIds, deletedAt)
  }

  private fun DataOutputStream.writeTag(tag: Tag) {
    writeString(tag.id.value)
    writeString(tag.displayName)
  }

  private fun DataInputStream.readTag(): Tag = Tag(TagId(readString()), readString())

  private fun DataOutputStream.writePhotoMetadata(photo: PhotoMetadata) {
    writeString(photo.id.value)
    writeString(photo.mimeType)
    writeLong(photo.byteSize)
  }

  private fun DataInputStream.readPhotoMetadata(): PhotoMetadata = PhotoMetadata(
    id = PhotoId(readString()),
    mimeType = readString(),
    byteSize = readLong(),
  )

  private fun DataOutputStream.writeString(value: String) {
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    require(bytes.size <= MAX_STRING_SIZE_BYTES) { "字符串过长" }
    writeInt(bytes.size)
    write(bytes)
  }

  private fun DataInputStream.readString(): String {
    val size = readInt()
    require(size in 0..MAX_STRING_SIZE_BYTES) { "字符串大小无效" }
    return String(ByteArray(size).also { bytes -> readFully(bytes) }, StandardCharsets.UTF_8)
  }

  private fun <T> DataOutputStream.writeCollection(values: List<T>, writeValue: DataOutputStream.(T) -> Unit) {
    require(values.size <= MAX_COLLECTION_SIZE) { "集合元素过多" }
    writeInt(values.size)
    values.forEach { value -> writeValue(value) }
  }

  private fun <T> DataInputStream.readCollection(readValue: DataInputStream.() -> T): List<T> {
    val size = readInt()
    require(size in 0..MAX_COLLECTION_SIZE) { "集合大小无效" }
    return List(size) { readValue() }
  }
}
