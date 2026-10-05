package com.y3lc.yourdiary.backup.ui

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class BackupInputReaderTest {
  @Test
  fun readAllReadsACompleteBackupWithinTheConfiguredLimit() {
    val reader = BackupInputReader(maxBytes = 4)

    val bytes = reader.readAll(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)))

    assertArrayEquals(byteArrayOf(1, 2, 3, 4), bytes)
  }

  @Test(expected = BackupInputTooLargeException::class)
  fun readAllRejectsInputLargerThanTheConfiguredLimitBeforeAccumulatingIt() {
    val reader = BackupInputReader(maxBytes = 3)

    reader.readAll(RepeatingInputStream())
  }

  private class RepeatingInputStream : InputStream() {
    override fun read(): Int = 1

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
      bytes.fill(1, offset, offset + length)
      return length
    }
  }
}
