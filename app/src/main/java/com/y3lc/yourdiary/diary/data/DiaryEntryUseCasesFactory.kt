package com.y3lc.yourdiary.diary.data

import android.content.Context
import com.y3lc.yourdiary.diary.domain.DiaryEntryUseCases
import java.io.File

object DiaryEntryUseCasesFactory {
  private var activeSession: DiarySession? = null

  @Synchronized
  fun create(context: Context, unlockedKey: UnlockedDiaryKey): DiaryEntryUseCases =
    getSession(getStorageDirectory(context), unlockedKey).useCases

  @Synchronized
  fun getRepository(context: Context, unlockedKey: UnlockedDiaryKey): EncryptedDiaryRepository =
    getSession(getStorageDirectory(context), unlockedKey).repository

  /**
   * 锁定时由宿主调用，使内存中的加密仓储和用例不再可复用。
   */
  @Synchronized
  fun clearSession() {
    activeSession?.close()
    activeSession = null
  }

  @Synchronized
  internal fun create(storageDirectory: File, unlockedKey: UnlockedDiaryKey): DiaryEntryUseCases =
    getSession(storageDirectory, unlockedKey).useCases

  private fun getSession(storageDirectory: File, unlockedKey: UnlockedDiaryKey): DiarySession {
    val canonicalDirectory = storageDirectory.absoluteFile
    val session = activeSession
    if (session != null &&
      session.storageDirectory == canonicalDirectory &&
      session.unlockedKey.hasSameMaterial(unlockedKey)
    ) {
      unlockedKey.close()
      return session
    }
    session?.close()
    val repository = EncryptedDiaryRepository(canonicalDirectory, unlockedKey)
    return DiarySession(
      storageDirectory = canonicalDirectory,
      unlockedKey = unlockedKey,
      repository = repository,
      useCases = DiaryEntryUseCases(repository),
    ).also { activeSession = it }
  }

  private fun getStorageDirectory(context: Context): File =
    File(context.applicationContext.filesDir, "diary").absoluteFile

  private class DiarySession(
    val storageDirectory: File,
    val unlockedKey: UnlockedDiaryKey,
    val repository: EncryptedDiaryRepository,
    val useCases: DiaryEntryUseCases,
  ) {
    fun close() {
      repository.close()
      unlockedKey.close()
    }
  }
}
