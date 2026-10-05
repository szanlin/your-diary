package com.y3lc.yourdiary.security.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class BiometricKeyStore(context: Context) {
  private val applicationContext = context.applicationContext
  private val preferences = applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

  val isEnabled: Boolean
    get() = preferences.contains(initializationVectorKey) && preferences.contains(ciphertextKey)

  fun getAuthenticationAvailability(): Int =
    BiometricManager.from(applicationContext).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

  fun createEncryptionCipher(): Cipher {
    ensureSecretKey()
    return Cipher.getInstance(cipherTransformation).apply {
      init(Cipher.ENCRYPT_MODE, requireSecretKey())
    }
  }

  fun createDecryptionCipher(): Cipher? {
    val initializationVector = preferences.getString(initializationVectorKey, null)?.decode() ?: return null
    return try {
      Cipher.getInstance(cipherTransformation).apply {
        init(Cipher.DECRYPT_MODE, requireSecretKey(), GCMParameterSpec(authenticationTagBitCount, initializationVector))
      }
    } catch (_: Exception) {
      disable()
      null
    }
  }

  fun saveEncryptedDiaryKey(cipher: Cipher, diaryKey: ByteArray) {
    val ciphertext = cipher.doFinal(diaryKey)
    check(
      preferences.edit()
        .putString(initializationVectorKey, cipher.iv.encode())
        .putString(ciphertextKey, ciphertext.encode())
        .commit(),
    ) { "无法保存生物识别解锁材料" }
  }

  fun decryptDiaryKey(cipher: Cipher): ByteArray? {
    val ciphertext = preferences.getString(ciphertextKey, null)?.decode() ?: return null
    return try {
      cipher.doFinal(ciphertext).takeIf { it.size == diaryKeyByteCount }
    } catch (_: Exception) {
      null
    }
  }

  fun disable() {
    preferences.edit().remove(initializationVectorKey).remove(ciphertextKey).apply()
  }

  private fun ensureSecretKey() {
    if (loadSecretKey() != null) return
    KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, androidKeyStore).apply {
      init(
        KeyGenParameterSpec.Builder(
          keyAlias,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .setUserAuthenticationRequired(true)
          .setInvalidatedByBiometricEnrollment(true)
          .build(),
      )
      generateKey()
    }
  }

  private fun requireSecretKey(): SecretKey = requireNotNull(loadSecretKey()) { "生物识别密钥不可用" }

  private fun loadSecretKey(): SecretKey? = (KeyStore.getInstance(androidKeyStore).apply { load(null) }
    .getKey(keyAlias, null) as? SecretKey)

  private fun String.decode(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

  private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)

  private companion object {
    const val androidKeyStore = "AndroidKeyStore"
    const val keyAlias = "your_diary_biometric_key"
    const val preferencesName = "biometric_unlock"
    const val initializationVectorKey = "iv"
    const val ciphertextKey = "ciphertext"
    const val cipherTransformation = "AES/GCM/NoPadding"
    const val authenticationTagBitCount = 128
    const val diaryKeyByteCount = 32
  }
}
