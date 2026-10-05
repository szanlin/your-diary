package com.y3lc.yourdiary.security.domain

/** 已解锁日记主密钥的会话边界。 */
interface DiaryKeySession {
  val isInitialized: Boolean

  fun setupPin(pin: String)

  fun unlock(pin: String): Boolean

  fun lock()

  fun currentKey(): ByteArray?
}
