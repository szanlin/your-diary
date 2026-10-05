package com.y3lc.yourdiary.diary.ui

/** 编辑页离开后不再接受异步写入；若新条目随后才创建完成，应立即按空条目规则清理。 */
class EditorSessionState {
  private var closed = false

  @Synchronized
  fun close() {
    closed = true
  }

  @Synchronized
  fun canWrite(): Boolean = !closed

  @Synchronized
  fun shouldDiscardNewEntryAfterCreation(): Boolean = closed
}
