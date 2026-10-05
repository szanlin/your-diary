package com.y3lc.yourdiary.backup.data

import com.y3lc.yourdiary.backup.domain.BackupPhoto
import com.y3lc.yourdiary.backup.domain.BackupSnapshot
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
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupContainer {
  private val magic = byteArrayOf('Y'.code.toByte(), 'D'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
  private const val formatVersion = 1
  private const val saltSizeBytes = 16
  private const val nonceSizeBytes = 12
  private const val keySizeBits = 256
  private const val keyDerivationIterations = 210_000
  private const val gcmTagLengthBits = 128
  private const val gcmTagSizeBytes = 16

  fun encrypt(snapshot: BackupSnapshot, password: CharArray): ByteArray {
    require(password.isNotEmpty()) { "备份密码不得为空" }
    val salt = ByteArray(saltSizeBytes).also { SecureRandom().nextBytes(it) }
    val nonce = ByteArray(nonceSizeBytes).also { SecureRandom().nextBytes(it) }
    val header = createHeader(salt, nonce)
    val keyBytes = deriveKey(password, salt)
    return try {
      val cipherText = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(gcmTagLengthBits, nonce))
        updateAAD(header)
      }.doFinal(BackupSnapshotCodec.encode(snapshot))
      header + cipherText
    } catch (exception: GeneralSecurityException) {
      throw IllegalStateException("无法加密备份", exception)
    } finally {
      keyBytes.fill(0)
    }
  }

  fun decrypt(encrypted: ByteArray, password: CharArray): BackupSnapshot {
    require(password.isNotEmpty()) { "备份密码不得为空" }
    val headerSize = magic.size + Int.SIZE_BYTES + saltSizeBytes + nonceSizeBytes
    require(encrypted.size >= headerSize + gcmTagSizeBytes) { "备份容器不完整" }
    val header = encrypted.copyOfRange(0, headerSize)
    val input = DataInputStream(ByteArrayInputStream(header))
    val receivedMagic = ByteArray(magic.size).also { input.readFully(it) }
    require(receivedMagic.contentEquals(magic)) { "不是 YourDiary 备份文件" }
    require(input.readInt() == formatVersion) { "不支持的备份格式版本" }
    val salt = ByteArray(saltSizeBytes).also { input.readFully(it) }
    val nonce = ByteArray(nonceSizeBytes).also { input.readFully(it) }
    val keyBytes = deriveKey(password, salt)
    return try {
      val plainText = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(gcmTagLengthBits, nonce))
        updateAAD(header)
      }.doFinal(encrypted.copyOfRange(headerSize, encrypted.size))
      BackupSnapshotCodec.decode(plainText)
    } catch (exception: GeneralSecurityException) {
      throw SecurityException("备份密码错误或备份已损坏", exception)
    } finally {
      keyBytes.fill(0)
    }
  }

  private fun createHeader(salt: ByteArray, nonce: ByteArray): ByteArray = ByteArrayOutputStream().use { byteStream ->
    DataOutputStream(byteStream).use { output ->
      output.write(magic)
      output.writeInt(formatVersion)
      output.write(salt)
      output.write(nonce)
    }
    byteStream.toByteArray()
  }

  private fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
    val passwordCopy = password.copyOf()
    val keySpec = PBEKeySpec(passwordCopy, salt, keyDerivationIterations, keySizeBits)
    passwordCopy.fill('\u0000')
    return try {
      SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).encoded
    } finally {
      keySpec.clearPassword()
    }
  }
}

private object BackupSnapshotCodec {
  private const val maxCollectionSize = 100_000
  private const val maxValueSizeBytes = 16 * 1024 * 1024

  fun encode(snapshot: BackupSnapshot): ByteArray = ByteArrayOutputStream().use { byteStream ->
    DataOutputStream(byteStream).use { output ->
      output.writeCollection(snapshot.entries) { entry -> writeEntry(entry) }
      output.writeCollection(snapshot.tags) { tag -> writeTag(tag) }
      output.writeCollection(snapshot.photos) { photo -> writePhoto(photo) }
    }
    byteStream.toByteArray()
  }

  fun decode(bytes: ByteArray): BackupSnapshot = try {
    DataInputStream(ByteArrayInputStream(bytes)).use { input ->
      val entries = input.readCollection { readEntry() }
      val tags = input.readCollection { readTag() }
      val photos = input.readCollection { readPhoto() }
      require(input.available() == 0) { "备份内容包含多余数据" }
      BackupSnapshot(entries, tags, photos)
    }
  } catch (exception: EOFException) {
    throw IllegalArgumentException("备份内容不完整", exception)
  } catch (exception: IOException) {
    throw IllegalArgumentException("备份内容无法读取", exception)
  }

  private fun DataOutputStream.writeEntry(entry: DiaryEntry) {
    writeString(entry.id.value)
    writeLong(entry.createdAt.toEpochMilli())
    writeLong(entry.updatedAt.toEpochMilli())
    writeString(entry.markdown)
    writeCollection(entry.photoIds) { photoId -> writeString(photoId.value) }
    writeCollection(entry.tagIds) { tagId -> writeString(tagId.value) }
    writeBoolean(entry.deletedAt != null)
    entry.deletedAt?.let { deletedAt -> writeLong(deletedAt.toEpochMilli()) }
  }

  private fun DataInputStream.readEntry(): DiaryEntry = DiaryEntry(
    id = EntryId(readString()),
    createdAt = Instant.ofEpochMilli(readLong()),
    updatedAt = Instant.ofEpochMilli(readLong()),
    markdown = readString(),
    photoIds = readCollection { PhotoId(readString()) },
    tagIds = readCollection { TagId(readString()) },
    deletedAt = if (readBoolean()) Instant.ofEpochMilli(readLong()) else null,
  )

  private fun DataOutputStream.writeTag(tag: Tag) {
    writeString(tag.id.value)
    writeString(tag.displayName)
  }

  private fun DataInputStream.readTag(): Tag = Tag(TagId(readString()), readString())

  private fun DataOutputStream.writePhoto(photo: BackupPhoto) {
    writeString(photo.metadata.id.value)
    writeString(photo.metadata.mimeType)
    writeLong(photo.metadata.byteSize)
    writeBytes(photo.bytes)
  }

  private fun DataInputStream.readPhoto(): BackupPhoto {
    val metadata = PhotoMetadata(PhotoId(readString()), readString(), readLong())
    return BackupPhoto(metadata, readBytes())
  }

  private fun DataOutputStream.writeString(value: String) = writeBytes(value.toByteArray(StandardCharsets.UTF_8))

  private fun DataInputStream.readString(): String = String(readBytes(), StandardCharsets.UTF_8)

  private fun DataOutputStream.writeBytes(value: ByteArray) {
    require(value.size <= maxValueSizeBytes) { "备份数据字段过大" }
    writeInt(value.size)
    write(value)
  }

  private fun DataInputStream.readBytes(): ByteArray {
    val size = readInt()
    require(size in 0..maxValueSizeBytes) { "备份数据字段大小无效" }
    return ByteArray(size).also { value -> readFully(value) }
  }

  private fun <T> DataOutputStream.writeCollection(values: List<T>, writeValue: DataOutputStream.(T) -> Unit) {
    require(values.size <= maxCollectionSize) { "备份集合元素过多" }
    writeInt(values.size)
    values.forEach { value -> writeValue(value) }
  }

  private fun <T> DataInputStream.readCollection(readValue: DataInputStream.() -> T): List<T> {
    val size = readInt()
    require(size in 0..maxCollectionSize) { "备份集合大小无效" }
    return List(size) { readValue() }
  }
}
