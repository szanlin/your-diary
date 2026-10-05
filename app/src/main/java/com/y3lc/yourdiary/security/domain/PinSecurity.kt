package com.y3lc.yourdiary.security.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PinCredential internal constructor(
  internal val saltBytes: ByteArray,
  internal val hashBytes: ByteArray,
  internal val iterations: Int,
)

class PinHasher(
  private val secureRandom: SecureRandom = SecureRandom(),
) {
  fun createCredential(pin: String): PinCredential {
    val saltBytes = ByteArray(saltByteCount)
    secureRandom.nextBytes(saltBytes)
    return PinCredential(
      saltBytes = saltBytes,
      hashBytes = deriveHash(pin, saltBytes),
      iterations = iterationCount,
    )
  }

  fun matches(credential: PinCredential, pin: String): Boolean =
    MessageDigest.isEqual(
      credential.hashBytes,
      deriveHash(pin, credential.saltBytes, credential.iterations),
    )

  internal fun deriveKey(
    pin: String,
    saltBytes: ByteArray,
    iterations: Int,
  ): ByteArray = deriveHash(pin, saltBytes, iterations)

  private fun deriveHash(
    pin: String,
    saltBytes: ByteArray,
    iterations: Int = iterationCount,
  ): ByteArray {
    require(pin.isNotBlank()) { "PIN 不能为空" }
    val pinCharacters = pin.toCharArray()
    val keySpec = PBEKeySpec(pinCharacters, saltBytes, iterations, hashBitCount)
    return try {
      SecretKeyFactory.getInstance(hashAlgorithm).generateSecret(keySpec).encoded
    } finally {
      pinCharacters.fill('\u0000')
      keySpec.clearPassword()
    }
  }

  private companion object {
    const val hashAlgorithm = "PBKDF2WithHmacSHA256"
    const val iterationCount = 210_000
    const val saltByteCount = 16
    const val hashBitCount = 256
  }
}

data class UnlockState(
  val isLocked: Boolean = true,
  val failedAttempts: Int = 0,
  val cooldownUntil: Instant? = null,
)

sealed interface PinVerificationResult {
  data object Unlocked : PinVerificationResult

  data class InvalidPin(
    val failedAttempts: Int,
  ) : PinVerificationResult

  data class Cooldown(
    val until: Instant,
  ) : PinVerificationResult
}

class PinAttemptLimiter {
  var failedAttempts: Int = 0
    private set
  private var cooldownUntil: Instant? = null

  fun canAttempt(now: Instant): Boolean {
    val until = cooldownUntil ?: return true
    if (now.isBefore(until)) return false
    failedAttempts = 0
    cooldownUntil = null
    return true
  }

  fun recordFailure(now: Instant) {
    failedAttempts += 1
    if (failedAttempts >= maximumFailedAttempts) {
      cooldownUntil = now.plusSeconds(cooldownDurationSeconds)
    }
  }

  fun recordSuccess() {
    failedAttempts = 0
    cooldownUntil = null
  }

  fun getCooldownUntil(now: Instant): Instant? = cooldownUntil?.takeIf { now.isBefore(it) }

  private companion object {
    const val maximumFailedAttempts = 5
    const val cooldownDurationSeconds = 30L
  }
}

enum class LockEvent {
  AppBackgrounded,
  ScreenTurnedOff,
}

class UnlockStateMachine(
  private val pinHasher: PinHasher,
  private val credential: PinCredential,
) {
  var state: UnlockState = UnlockState()
    private set

  fun verifyPin(pin: String, now: Instant): PinVerificationResult {
    val cooldownUntil = state.cooldownUntil
    if (cooldownUntil != null && now.isBefore(cooldownUntil)) {
      return PinVerificationResult.Cooldown(cooldownUntil)
    }
    if (cooldownUntil != null) {
      state = state.copy(failedAttempts = 0, cooldownUntil = null)
    }
    if (pinHasher.matches(credential, pin)) {
      state = UnlockState(isLocked = false)
      return PinVerificationResult.Unlocked
    }

    val failedAttempts = state.failedAttempts + 1
    return if (failedAttempts >= maximumFailedAttempts) {
      val nextCooldown = now.plusSeconds(cooldownDurationSeconds)
      state = UnlockState(
        isLocked = true,
        failedAttempts = maximumFailedAttempts,
        cooldownUntil = nextCooldown,
      )
      PinVerificationResult.Cooldown(nextCooldown)
    } else {
      state = UnlockState(isLocked = true, failedAttempts = failedAttempts)
      PinVerificationResult.InvalidPin(failedAttempts)
    }
  }

  fun lock(event: LockEvent) {
    when (event) {
      LockEvent.AppBackgrounded,
      LockEvent.ScreenTurnedOff,
      -> state = state.copy(isLocked = true)
    }
  }

  private companion object {
    const val maximumFailedAttempts = 5
    const val cooldownDurationSeconds = 30L
  }
}
