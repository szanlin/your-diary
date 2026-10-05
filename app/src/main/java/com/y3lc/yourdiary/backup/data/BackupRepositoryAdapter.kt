package com.y3lc.yourdiary.backup.data

import com.y3lc.yourdiary.backup.domain.BackupExport
import com.y3lc.yourdiary.backup.domain.BackupMerger
import com.y3lc.yourdiary.backup.domain.BackupPhoto
import com.y3lc.yourdiary.backup.domain.BackupSnapshot
import com.y3lc.yourdiary.diary.data.EncryptedDiaryRepository

/** 将已解锁的私有日记存储适配为备份读写操作。 */
class BackupRepositoryAdapter(
  private val repository: EncryptedDiaryRepository,
) {
  fun createEncryptedBackup(password: CharArray): ByteArray = try {
    val localSnapshot = repository.createBackupSnapshot()
    val exportSnapshot = BackupExport.create(
      entries = localSnapshot.entries,
      tags = localSnapshot.tags,
      photos = localSnapshot.photos,
    )
    BackupContainer.encrypt(exportSnapshot, password)
  } finally {
    password.fill('\u0000')
  }

  fun restoreEncryptedBackup(encryptedBackup: ByteArray, password: CharArray) {
    val restoredSnapshot = try {
      BackupContainer.decrypt(encryptedBackup, password)
    } finally {
      password.fill('\u0000')
    }
    val localSnapshot = repository.createBackupSnapshot()
    val mergedSnapshot = BackupMerger.merge(localSnapshot, restoredSnapshot)
    repository.replaceBackupSnapshot(mergedSnapshot)
  }
}
