package com.y3lc.yourdiary.security.data

import android.content.SharedPreferences
import android.util.Base64

class SharedPreferencesPinKeyMaterialStore(
  private val preferences: SharedPreferences,
) : PinKeyMaterialStore {
  override fun read(): PinKeyMaterial? {
    val material = try {
      PinKeyMaterial(
        saltBytes = preferences.getString(saltKey, null)?.decode() ?: return null,
        verifierBytes = preferences.getString(verifierKey, null)?.decode() ?: return null,
        iterations = preferences.getInt(iterationsKey, missingIterations),
        nonceBytes = preferences.getString(nonceKey, null)?.decode() ?: return null,
        ciphertextBytes = preferences.getString(ciphertextKey, null)?.decode() ?: return null,
      )
    } catch (_: IllegalArgumentException) {
      return null
    }
    return material.takeIf {
      it.iterations > 0 &&
        it.saltBytes.size == saltByteCount &&
        it.verifierBytes.size == verifierByteCount &&
        it.nonceBytes.size == nonceByteCount &&
        it.ciphertextBytes.size >= encryptedDiaryKeyByteCount
    }
  }

  override fun write(material: PinKeyMaterial) {
    check(
      preferences.edit()
        .putString(saltKey, material.saltBytes.encode())
        .putString(verifierKey, material.verifierBytes.encode())
        .putInt(iterationsKey, material.iterations)
        .putString(nonceKey, material.nonceBytes.encode())
        .putString(ciphertextKey, material.ciphertextBytes.encode())
        .commit(),
    ) { "无法保存 PIN 密钥材料" }
  }

  private fun String.decode(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

  private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)

  private companion object {
    const val saltKey = "pin_salt"
    const val verifierKey = "pin_verifier"
    const val iterationsKey = "pin_iterations"
    const val nonceKey = "diary_key_nonce"
    const val ciphertextKey = "diary_key_ciphertext"
    const val missingIterations = -1
    const val saltByteCount = 16
    const val verifierByteCount = 32
    const val nonceByteCount = 12
    const val encryptedDiaryKeyByteCount = 48
  }
}
