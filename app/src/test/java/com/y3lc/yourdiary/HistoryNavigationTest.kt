package com.y3lc.yourdiary

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryNavigationTest {
  @Test
  fun parseRequestedHistoryDateRejectsBlankAndMalformedNavigationArguments() {
    assertNull(parseRequestedHistoryDate(""))
    assertNull(parseRequestedHistoryDate("not-a-date"))
    assertEquals(LocalDate.of(2026, 10, 6), parseRequestedHistoryDate("2026-10-06"))
  }

  @Test
  fun getHistoryTargetScrollYIncludesTheDateListOffsetInsideTheScrollContent() {
    assertEquals(472, getHistoryTargetScrollY(entriesContainerTop = 168, targetDateTop = 304))
  }
}
