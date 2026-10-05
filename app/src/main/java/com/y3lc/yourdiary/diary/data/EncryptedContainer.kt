package com.y3lc.yourdiary.diary.data

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class UnlockedDiaryKey(keyBytes: ByteArray) : AutoCloseable {
  private val keyBytes = keyBytes.copyOf()
  private var isClosed = false

  init {
    require(this.keyBytes.size == AES_KEY_SIZE_BYTES) { "日记密钥必须为 256 位" }
  }

  internal fun createSecretKey(): SecretKey {
    requireOpen()
    return SecretKeySpec(keyBytes, "AES")
  }

  /**
   * 仅供会话生命周期判断使用，避免将密钥内容暴露到 UI 或持久化层。
   */
  internal fun hasSameMaterial(other: UnlockedDiaryKey): Boolean {
    requireOpen()
    other.requireOpen()
    return keyBytes.contentEquals(other.keyBytes)
  }

  override fun close() {
    if (isClosed) return
    keyBytes.fill(0)
    isClosed = true
  }

  private fun requireOpen() {
    check(!isClosed) { "日记会话已锁定" }
  }

  companion object {
    private const val AES_KEY_SIZE_BYTES = 32
  }
}

object EncryptedContainer {
  private const val AES_ALGORITHM = "AES"
  private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
  private const val GCM_TAG_LENGTH_BITS = 128
  private const val NONCE_SIZE_BYTES = 12

  fun encrypt(
    plainText: ByteArray,
    key: UnlockedDiaryKey,
    additionalAuthenticatedData: ByteArray,
  ): ByteArray {
    val nonce = ByteArray(NONCE_SIZE_BYTES)
    SecureRandom().nextBytes(nonce)
    val cipherText = createCipher(Cipher.ENCRYPT_MODE, key, nonce, additionalAuthenticatedData)
      .doFinal(plainText)
    return nonce + cipherText
  }

  fun decrypt(
    encrypted: ByteArray,
    key: UnlockedDiaryKey,
    additionalAuthenticatedData: ByteArray,
  ): ByteArray {
    require(encrypted.size > NONCE_SIZE_BYTES) { "加密容器格式无效" }
    val nonce = encrypted.copyOfRange(0, NONCE_SIZE_BYTES)
    val cipherText = encrypted.copyOfRange(NONCE_SIZE_BYTES, encrypted.size)
    return try {
      createCipher(Cipher.DECRYPT_MODE, key, nonce, additionalAuthenticatedData).doFinal(cipherText)
    } catch (exception: GeneralSecurityException) {
      throw SecurityException("加密数据无法通过完整性校验", exception)
    }
  }

  private fun createCipher(
    mode: Int,
    key: UnlockedDiaryKey,
    nonce: ByteArray,
    additionalAuthenticatedData: ByteArray,
  ): Cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION).apply {
    init(mode, key.createSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce))
    updateAAD(additionalAuthenticatedData)
  }
}
