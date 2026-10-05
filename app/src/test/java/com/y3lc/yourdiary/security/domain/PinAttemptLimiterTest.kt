package com.y3lc.yourdiary.security.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinAttemptLimiterTest {
  @Test
  fun startsCooldownAfterTheFifthFailedAttempt() {
    val limiter = PinAttemptLimiter()
    val start = Instant.parse("2026-10-04T00:00:00Z")

    repeat(4) { limiter.recordFailure(start) }
    assertTrue(limiter.canAttempt(start))

    limiter.recordFailure(start)

    assertFalse(limiter.canAttempt(start.plusSeconds(29)))
    assertTrue(limiter.canAttempt(start.plusSeconds(30)))
  }

  @Test
  fun successfulPinResetsTheFailureLimit() {
    val limiter = PinAttemptLimiter()
    val start = Instant.parse("2026-10-04T00:00:00Z")

    repeat(4) { limiter.recordFailure(start) }
    limiter.recordSuccess()

    assertEquals(0, limiter.failedAttempts)
    assertTrue(limiter.canAttempt(start))
  }
}
