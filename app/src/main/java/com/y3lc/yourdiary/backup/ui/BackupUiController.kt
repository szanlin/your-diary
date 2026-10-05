package com.y3lc.yourdiary.backup.ui

import com.y3lc.yourdiary.backup.data.BackupRepositoryAdapter
import com.y3lc.yourdiary.diary.data.EncryptedDiaryRepository
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupUiController(
  repository: EncryptedDiaryRepository,
  private val inputReader: BackupInputReader = BackupInputReader(),
) {
  private val backupRepository = BackupRepositoryAdapter(
    repository,
  )

  suspend fun exportBackup(outputStream: OutputStream, password: CharArray) = withContext(Dispatchers.IO) {
    val encryptedBackup = backupRepository.createEncryptedBackup(password)
    outputStream.use { output -> output.write(encryptedBackup) }
  }

  suspend fun restoreBackup(inputStream: InputStream, password: CharArray) = withContext(Dispatchers.IO) {
    inputStream.use { input -> backupRepository.restoreEncryptedBackup(inputReader.readAll(input), password) }
  }
}
