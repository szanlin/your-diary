package com.y3lc.yourdiary.backup.ui

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * 在校验和解密前限制备份文件大小，避免恶意或误选的大文件耗尽应用内存。
 */
class BackupInputReader(
  private val maxBytes: Int = MAX_BACKUP_BYTES,
) {
  init {
    require(maxBytes > 0) { "备份大小上限必须大于零" }
  }

  fun readAll(inputStream: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_SIZE_BYTES)
    var totalBytes = 0
    while (true) {
      val count = inputStream.read(buffer)
      if (count == -1) break
      if (count == 0) continue
      if (count > maxBytes - totalBytes) throw BackupInputTooLargeException(maxBytes)
      output.write(buffer, 0, count)
      totalBytes += count
    }
    return output.toByteArray()
  }

  private companion object {
    const val BUFFER_SIZE_BYTES = 8 * 1024
    const val MAX_BACKUP_BYTES = 64 * 1024 * 1024
  }
}

class BackupInputTooLargeException(maxBytes: Int) : IllegalArgumentException(
  "备份文件超过允许的 ${maxBytes / (1024 * 1024)} MB 上限",
)
