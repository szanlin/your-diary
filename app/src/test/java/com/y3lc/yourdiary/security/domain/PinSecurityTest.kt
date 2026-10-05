package com.y3lc.yourdiary.security.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinSecurityTest {
  private val start = Instant.parse("2026-10-04T01:00:00Z")

  @Test
  fun createCredentialDoesNotRetainPlaintextPinAndVerifiesTheSamePin() {
    val hasher = PinHasher()

    val credential = hasher.createCredential("4829")

    assertTrue(hasher.matches(credential, "4829"))
    assertFalse(hasher.matches(credential, "4828"))
    assertFalse(credential.javaClass.declaredFields.any { it.name.contains("pin", ignoreCase = true) })
  }

  @Test
  fun fifthConsecutiveFailureStartsThirtySecondCooldown() {
    val machine = UnlockStateMachine(PinHasher(), hasherCredential())

    repeat(4) { machine.verifyPin("0000", start) }
    val result = machine.verifyPin("0000", start)

    assertEquals(PinVerificationResult.Cooldown(start.plusSeconds(30)), result)
    assertEquals(start.plusSeconds(30), machine.state.cooldownUntil)
  }

  @Test
  fun pinAttemptsDuringCooldownRemainLockedWithoutIncreasingFailureCount() {
    val machine = UnlockStateMachine(PinHasher(), hasherCredential())
    repeat(5) { machine.verifyPin("0000", start) }

    val result = machine.verifyPin("4829", start.plusSeconds(10))

    assertEquals(PinVerificationResult.Cooldown(start.plusSeconds(30)), result)
    assertEquals(5, machine.state.failedAttempts)
    assertTrue(machine.state.isLocked)
  }

  @Test
  fun successfulVerificationResetsFailuresAndUnlocks() {
    val machine = UnlockStateMachine(PinHasher(), hasherCredential())
    repeat(3) { machine.verifyPin("0000", start) }

    val result = machine.verifyPin("4829", start)

    assertEquals(PinVerificationResult.Unlocked, result)
    assertFalse(machine.state.isLocked)
    assertEquals(0, machine.state.failedAttempts)
    assertEquals(null, machine.state.cooldownUntil)
  }

  @Test
  fun appBackgroundEventImmediatelyLocksAnUnlockedSession() {
    val machine = UnlockStateMachine(PinHasher(), hasherCredential())
    machine.verifyPin("4829", start)

    machine.lock(LockEvent.AppBackgrounded)

    assertTrue(machine.state.isLocked)
  }

  @Test
  fun screenOffEventImmediatelyLocksAnUnlockedSession() {
    val machine = UnlockStateMachine(PinHasher(), hasherCredential())
    machine.verifyPin("4829", start)

    machine.lock(LockEvent.ScreenTurnedOff)

    assertTrue(machine.state.isLocked)
  }

  private fun hasherCredential(): PinCredential = PinHasher().createCredential("4829")
}
