package com.y3lc.yourdiary.diary.data

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EncryptedContainerTest {
  private val key = UnlockedDiaryKey(ByteArray(32) { it.toByte() })

  @Test
  fun encryptThenDecryptRestoresOriginalBytes() {
    val plainText = "细大必书".toByteArray(StandardCharsets.UTF_8)

    val encrypted = EncryptedContainer.encrypt(
      plainText = plainText,
      key = key,
      additionalAuthenticatedData = "diary-state-v1".toByteArray(StandardCharsets.UTF_8),
    )

    val decrypted = EncryptedContainer.decrypt(
      encrypted = encrypted,
      key = key,
      additionalAuthenticatedData = "diary-state-v1".toByteArray(StandardCharsets.UTF_8),
    )

    assertArrayEquals(plainText, decrypted)
    assertNotEquals(String(plainText, StandardCharsets.UTF_8), String(encrypted, StandardCharsets.UTF_8))
  }

  @Test(expected = SecurityException::class)
  fun decryptRejectsModifiedCiphertext() {
    val encrypted = EncryptedContainer.encrypt(
      plainText = "不可篡改".toByteArray(StandardCharsets.UTF_8),
      key = key,
      additionalAuthenticatedData = "diary-state-v1".toByteArray(StandardCharsets.UTF_8),
    )
    encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()

    EncryptedContainer.decrypt(
      encrypted = encrypted,
      key = key,
      additionalAuthenticatedData = "diary-state-v1".toByteArray(StandardCharsets.UTF_8),
    )
  }
}
