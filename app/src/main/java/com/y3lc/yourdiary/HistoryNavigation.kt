package com.y3lc.yourdiary

import java.time.LocalDate

fun parseRequestedHistoryDate(value: String?): LocalDate? = value
  ?.takeIf { it.isNotBlank() }
  ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

fun getHistoryTargetScrollY(entriesContainerTop: Int, targetDateTop: Int): Int =
  entriesContainerTop + targetDateTop
