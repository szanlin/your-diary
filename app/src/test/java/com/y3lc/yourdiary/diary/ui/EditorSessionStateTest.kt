package com.y3lc.yourdiary.diary.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSessionStateTest {
  @Test
  fun closeBeforeNewEntryCreationMarksTheCreatedEntryForImmediateCleanup() {
    val state = EditorSessionState()

    state.close()

    assertTrue(state.shouldDiscardNewEntryAfterCreation())
  }

  @Test
  fun closePreventsQueuedWritesFromStartingAfterTheEditorIsNoLongerActive() {
    val state = EditorSessionState()
    state.close()

    assertFalse(state.canWrite())
  }
}
