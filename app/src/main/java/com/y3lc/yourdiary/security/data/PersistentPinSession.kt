package com.y3lc.yourdiary.security.data

import com.y3lc.yourdiary.security.domain.DiaryKeySession

class PersistentPinSession(
  private val materialStore: PinKeyMaterialStore,
  private val keyProtector: PinKeyProtector = PinKeyProtector(),
) : DiaryKeySession {
  private var unlockedKey: ByteArray? = null

  override val isInitialized: Boolean
    get() = materialStore.read() != null

  override fun setupPin(pin: String) {
    lock()
    val createdKey = keyProtector.create(pin)
    materialStore.write(createdKey.material)
    unlockedKey = createdKey.diaryKey
  }

  override fun unlock(pin: String): Boolean {
    lock()
    val material = materialStore.read() ?: return false
    val diaryKey = keyProtector.unwrap(pin, material) ?: return false
    unlockedKey = diaryKey
    return true
  }

  fun restoreDiaryKey(diaryKey: ByteArray) {
    require(diaryKey.size == diaryKeyByteCount) { "日记密钥长度无效" }
    lock()
    unlockedKey = diaryKey.copyOf()
  }

  override fun lock() {
    unlockedKey?.fill(0)
    unlockedKey = null
  }

  override fun currentKey(): ByteArray? = unlockedKey?.copyOf()

  fun isUnlocked(): Boolean = unlockedKey != null

  private companion object {
    const val diaryKeyByteCount = 32
  }
}
