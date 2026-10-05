package com.y3lc.yourdiary.security.data

import com.y3lc.yourdiary.security.domain.PinCredential
import com.y3lc.yourdiary.security.domain.PinHasher
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class PinKeyProtector(
  private val pinHasher: PinHasher = PinHasher(),
  private val secureRandom: SecureRandom = SecureRandom(),
) {
  fun create(pin: String): CreatedDiaryKey {
    val credential = pinHasher.createCredential(pin)
    val diaryKey = ByteArray(diaryKeyByteCount).also(secureRandom::nextBytes)
    val nonceBytes = ByteArray(nonceByteCount).also(secureRandom::nextBytes)
    val encryptedKey = encrypt(
      keyBytes = diaryKey,
      derivedKeyBytes = pinHasher.deriveKey(pin, credential.saltBytes, credential.iterations),
      nonceBytes = nonceBytes,
    )
    return CreatedDiaryKey(
      material = PinKeyMaterial(
        saltBytes = credential.saltBytes.copyOf(),
        verifierBytes = credential.hashBytes.copyOf(),
        iterations = credential.iterations,
        nonceBytes = nonceBytes,
        ciphertextBytes = encryptedKey,
      ),
      diaryKey = diaryKey,
    )
  }

  fun unwrap(pin: String, material: PinKeyMaterial): ByteArray? {
    val credential = PinCredential(
      saltBytes = material.saltBytes,
      hashBytes = material.verifierBytes,
      iterations = material.iterations,
    )
    if (!pinHasher.matches(credential, pin)) {
      return null
    }
    return try {
      decrypt(
        ciphertextBytes = material.ciphertextBytes,
        derivedKeyBytes = pinHasher.deriveKey(pin, material.saltBytes, material.iterations),
        nonceBytes = material.nonceBytes,
      )
    } catch (_: GeneralSecurityException) {
      null
    }
  }

  private fun encrypt(
    keyBytes: ByteArray,
    derivedKeyBytes: ByteArray,
    nonceBytes: ByteArray,
  ): ByteArray = runCipher(Cipher.ENCRYPT_MODE, keyBytes, derivedKeyBytes, nonceBytes)

  private fun decrypt(
    ciphertextBytes: ByteArray,
    derivedKeyBytes: ByteArray,
    nonceBytes: ByteArray,
  ): ByteArray = runCipher(Cipher.DECRYPT_MODE, ciphertextBytes, derivedKeyBytes, nonceBytes)

  private fun runCipher(
    mode: Int,
    inputBytes: ByteArray,
    derivedKeyBytes: ByteArray,
    nonceBytes: ByteArray,
  ): ByteArray {
    val keySpec = SecretKeySpec(derivedKeyBytes, aesAlgorithm)
    return try {
      Cipher.getInstance(cipherTransformation).apply {
        init(mode, keySpec, GCMParameterSpec(authenticationTagBitCount, nonceBytes))
      }.doFinal(inputBytes)
    } finally {
      derivedKeyBytes.fill(0)
    }
  }

  class CreatedDiaryKey internal constructor(
    val material: PinKeyMaterial,
    internal val diaryKey: ByteArray,
  )

  private companion object {
    const val aesAlgorithm = "AES"
    const val cipherTransformation = "AES/GCM/NoPadding"
    const val authenticationTagBitCount = 128
    const val diaryKeyByteCount = 32
    const val nonceByteCount = 12
  }
}
