package com.y3lc.yourdiary.diary.data

import android.content.Context
import com.y3lc.yourdiary.backup.domain.BackupPhoto
import com.y3lc.yourdiary.backup.domain.BackupSnapshot
import com.y3lc.yourdiary.diary.domain.DiaryEntry
import com.y3lc.yourdiary.diary.domain.DiaryRepository
import com.y3lc.yourdiary.diary.domain.EntryId
import com.y3lc.yourdiary.diary.domain.PhotoId
import com.y3lc.yourdiary.diary.domain.PhotoMetadata
import com.y3lc.yourdiary.diary.domain.Tag
import com.y3lc.yourdiary.diary.domain.TagId
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale

class EncryptedDiaryRepository(
  private val storageDirectory: File,
  private val unlockedKey: UnlockedDiaryKey,
) : DiaryRepository {
  private val stateFile = File(storageDirectory, STATE_FILE_NAME)
  private val photosDirectory = File(storageDirectory, PHOTOS_DIRECTORY_NAME)
  private val entriesById: MutableMap<EntryId, DiaryEntry>
  private val tagsById: MutableMap<TagId, Tag>
  private val photosById: MutableMap<PhotoId, PhotoMetadata>
  private var isClosed = false

  constructor(context: Context, unlockedKey: UnlockedDiaryKey) : this(
    storageDirectory = File(context.filesDir, STORAGE_DIRECTORY_NAME),
    unlockedKey = unlockedKey,
  )

  init {
    require(storageDirectory.exists() || storageDirectory.mkdirs()) { "无法创建日记私有存储目录" }
    val snapshot = readSnapshot()
    entriesById = snapshot.entries.associateByTo(linkedMapOf()) { entry -> entry.id }
    tagsById = snapshot.tags.associateByTo(linkedMapOf()) { tag -> tag.id }
    photosById = snapshot.photos.associateByTo(linkedMapOf()) { photo -> photo.id }
  }

  @Synchronized
  override fun getEntry(entryId: EntryId): DiaryEntry? {
    requireOpen()
    return entriesById[entryId]
  }

  @Synchronized
  override fun listEntries(): List<DiaryEntry> {
    requireOpen()
    return entriesById.values.toList()
  }

  @Synchronized
  override fun saveEntry(entry: DiaryEntry) {
    requireOpen()
    val previousEntry = entriesById.put(entry.id, entry)
    persistOrRestore { restoreEntry(entry.id, previousEntry) }
  }

  @Synchronized
  override fun deleteEntry(entryId: EntryId) {
    requireOpen()
    val previousEntry = entriesById.remove(entryId) ?: return
    persistOrRestore { entriesById[entryId] = previousEntry }
  }

  @Synchronized
  override fun getTag(tagId: TagId): Tag? {
    requireOpen()
    return tagsById[tagId]
  }

  @Synchronized
  override fun listTags(): List<Tag> {
    requireOpen()
    return tagsById.values.toList()
  }

  @Synchronized
  override fun saveTag(tag: Tag) {
    requireOpen()
    val previousTag = tagsById.put(tag.id, tag)
    persistOrRestore { restoreTag(tag.id, previousTag) }
  }

  @Synchronized
  override fun getPhotoMetadata(photoId: PhotoId): PhotoMetadata? {
    requireOpen()
    return photosById[photoId]
  }

  @Synchronized
  override fun savePhoto(photoMetadata: PhotoMetadata, bytes: ByteArray) {
    requireOpen()
    require(photoMetadata.byteSize == bytes.size.toLong()) { "照片元数据大小与内容不一致" }
    val photoFile = getPhotoFile(photoMetadata.id)
    val previousEncryptedBytes = photoFile.takeIf { file -> file.exists() }?.readBytes()
    val previousPhoto = photosById.put(photoMetadata.id, photoMetadata)
    writeEncryptedFile(photoFile, bytes, createPhotoAad(photoMetadata.id))
    try {
      persistSnapshot()
    } catch (exception: IOException) {
      restorePhotoMetadata(photoMetadata.id, previousPhoto)
      restorePhotoFile(photoFile, previousEncryptedBytes)
      throw exception
    } catch (exception: RuntimeException) {
      restorePhotoMetadata(photoMetadata.id, previousPhoto)
      restorePhotoFile(photoFile, previousEncryptedBytes)
      throw exception
    }
  }

  @Synchronized
  override fun readPhoto(photoId: PhotoId): ByteArray? {
    requireOpen()
    if (photosById[photoId] == null) return null
    val photoFile = getPhotoFile(photoId)
    if (!photoFile.exists()) return null
    return EncryptedContainer.decrypt(
      encrypted = photoFile.readBytes(),
      key = unlockedKey,
      additionalAuthenticatedData = createPhotoAad(photoId),
    )
  }

  @Synchronized
  override fun deletePhoto(photoId: PhotoId) {
    requireOpen()
    val previousMetadata = photosById.remove(photoId) ?: return
    persistOrRestore { photosById[photoId] = previousMetadata }
    getPhotoFile(photoId).delete()
  }

  @Synchronized
  override fun searchActiveEntries(query: String): List<DiaryEntry> {
    requireOpen()
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    return entriesById.values.filter { entry ->
      !entry.isDeleted && (
        normalizedQuery.isEmpty() ||
          entry.markdown.lowercase(Locale.ROOT).contains(normalizedQuery) ||
          entry.tagIds.any { tagId ->
            tagsById[tagId]?.displayName?.lowercase(Locale.ROOT)?.contains(normalizedQuery) == true
          }
        )
    }
  }

  @Synchronized
  fun createBackupSnapshot(): BackupSnapshot {
    requireOpen()
    return BackupSnapshot(
      entries = entriesById.values.toList(),
      tags = tagsById.values.toList(),
      photos = photosById.values.map { photoMetadata ->
        val bytes = requireNotNull(readPhoto(photoMetadata.id)) { "无法读取备份照片" }
        BackupPhoto(photoMetadata, bytes)
      },
    )
  }

  /**
   * 所有备份内容均已在写入前完成解密与合并。若持久化失败，恢复前的内存和文件状态。
   */
  @Synchronized
  fun replaceBackupSnapshot(snapshot: BackupSnapshot) {
    requireOpen()
    validateBackupSnapshot(snapshot)
    val previousEntries = entriesById.toMap()
    val previousTags = tagsById.toMap()
    val previousPhotos = photosById.toMap()
    val newPhotos = snapshot.photos.filter { photo -> photo.metadata.id !in photosById }
    val writtenPhotoFiles = mutableListOf<File>()
    try {
      newPhotos.forEach { photo ->
        val photoFile = getPhotoFile(photo.metadata.id)
        writeEncryptedFile(photoFile, photo.bytes, createPhotoAad(photo.metadata.id))
        writtenPhotoFiles += photoFile
      }
      entriesById.clear()
      entriesById.putAll(snapshot.entries.associateBy { entry -> entry.id })
      tagsById.clear()
      tagsById.putAll(snapshot.tags.associateBy { tag -> tag.id })
      photosById.clear()
      photosById.putAll(snapshot.photos.associate { photo -> photo.metadata.id to photo.metadata })
      persistSnapshot()
    } catch (exception: IOException) {
      restoreBackupState(previousEntries, previousTags, previousPhotos, writtenPhotoFiles)
      throw exception
    } catch (exception: RuntimeException) {
      restoreBackupState(previousEntries, previousTags, previousPhotos, writtenPhotoFiles)
      throw exception
    }
  }

  private fun readSnapshot(): DiarySnapshot {
    if (!stateFile.exists()) return DiarySnapshot(emptyList(), emptyList(), emptyList())
    val encrypted = stateFile.readBytes()
    val plainText = EncryptedContainer.decrypt(encrypted, unlockedKey, STATE_AAD)
    return DiarySnapshotCodec.decode(plainText)
  }

  @Synchronized
  fun close() {
    if (isClosed) return
    entriesById.clear()
    tagsById.clear()
    photosById.clear()
    unlockedKey.close()
    isClosed = true
  }

  private fun requireOpen() {
    check(!isClosed) { "日记会话已锁定" }
  }

  private fun persistSnapshot() {
    val snapshot = DiarySnapshot(
      entries = entriesById.values.toList(),
      tags = tagsById.values.toList(),
      photos = photosById.values.toList(),
    )
    writeEncryptedFile(stateFile, DiarySnapshotCodec.encode(snapshot), STATE_AAD)
  }

  private fun persistOrRestore(restore: () -> Unit) {
    try {
      persistSnapshot()
    } catch (exception: IOException) {
      restore()
      throw exception
    } catch (exception: RuntimeException) {
      restore()
      throw exception
    }
  }

  private fun validateBackupSnapshot(snapshot: BackupSnapshot) {
    require(snapshot.entries.map { entry -> entry.id }.distinct().size == snapshot.entries.size) {
      "备份包含重复日记条目"
    }
    require(snapshot.tags.map { tag -> tag.id }.distinct().size == snapshot.tags.size) {
      "备份包含重复 Tag"
    }
    require(snapshot.photos.map { photo -> photo.metadata.id }.distinct().size == snapshot.photos.size) {
      "备份包含重复照片"
    }
  }

  private fun restoreBackupState(
    previousEntries: Map<EntryId, DiaryEntry>,
    previousTags: Map<TagId, Tag>,
    previousPhotos: Map<PhotoId, PhotoMetadata>,
    writtenPhotoFiles: List<File>,
  ) {
    entriesById.clear()
    entriesById.putAll(previousEntries)
    tagsById.clear()
    tagsById.putAll(previousTags)
    photosById.clear()
    photosById.putAll(previousPhotos)
    writtenPhotoFiles.forEach { photoFile -> photoFile.delete() }
  }

  private fun writeEncryptedFile(file: File, plainText: ByteArray, additionalAuthenticatedData: ByteArray) {
    val encrypted = EncryptedContainer.encrypt(plainText, unlockedKey, additionalAuthenticatedData)
    writeFileAtomically(file, encrypted)
  }

  private fun restorePhotoFile(file: File, encryptedBytes: ByteArray?) {
    if (encryptedBytes == null) {
      file.delete()
    } else {
      writeFileAtomically(file, encryptedBytes)
    }
  }

  private fun writeFileAtomically(file: File, bytes: ByteArray) {
    val parent = file.parentFile ?: throw IOException("加密文件缺少父目录")
    require(parent.exists() || parent.mkdirs()) { "无法创建加密文件目录" }
    val temporaryFile = File.createTempFile(".writing-", ".tmp", parent)
    try {
      FileOutputStream(temporaryFile).use { output ->
        output.write(bytes)
        output.fd.sync()
      }
      if (!temporaryFile.renameTo(file)) {
        throw IOException("无法原子替换加密文件")
      }
    } finally {
      if (temporaryFile.exists()) temporaryFile.delete()
    }
  }

  private fun restoreEntry(entryId: EntryId, previousEntry: DiaryEntry?) {
    if (previousEntry == null) entriesById.remove(entryId) else entriesById[entryId] = previousEntry
  }

  private fun restoreTag(tagId: TagId, previousTag: Tag?) {
    if (previousTag == null) tagsById.remove(tagId) else tagsById[tagId] = previousTag
  }

  private fun restorePhotoMetadata(photoId: PhotoId, previousMetadata: PhotoMetadata?) {
    if (previousMetadata == null) photosById.remove(photoId) else photosById[photoId] = previousMetadata
  }

  private fun getPhotoFile(photoId: PhotoId): File = File(photosDirectory, "${photoId.value}.enc")

  private fun createPhotoAad(photoId: PhotoId): ByteArray =
    "photo:${photoId.value}".toByteArray(StandardCharsets.UTF_8)

  companion object {
    private const val STORAGE_DIRECTORY_NAME = "diary"
    private const val PHOTOS_DIRECTORY_NAME = "photos"
    private const val STATE_FILE_NAME = "diary-state.enc"
    private val STATE_AAD = "diary-state-v1".toByteArray(StandardCharsets.UTF_8)
  }
}
