package com.y3lc.yourdiary.diary.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiaryEntryUseCasesFactoryTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @After
  fun clearSession() {
    DiaryEntryUseCasesFactory.clearSession()
  }

  @Test
  fun twoFactoryCallsInOneUnlockedSessionShareRepositoryAndKeepBothSaves() {
    val storageDirectory = temporaryFolder.newFolder("diary")
    val firstPageUseCases = DiaryEntryUseCasesFactory.create(storageDirectory, createKey())
    val secondPageUseCases = DiaryEntryUseCasesFactory.create(storageDirectory, createKey())

    assertSame(firstPageUseCases, secondPageUseCases)
    firstPageUseCases.createEntry()
    secondPageUseCases.createEntry()

    DiaryEntryUseCasesFactory.clearSession()
    val reopenedUseCases = DiaryEntryUseCasesFactory.create(storageDirectory, createKey())

    assertEquals(2, reopenedUseCases.listToday().size)
  }

  @Test
  fun clearSessionMakesPreviouslyReturnedUseCasesUnreadable() {
    val storageDirectory = temporaryFolder.newFolder("diary")
    val useCases = DiaryEntryUseCasesFactory.create(storageDirectory, createKey())
    useCases.createEntry()

    DiaryEntryUseCasesFactory.clearSession()

    assertThrows(IllegalStateException::class.java) {
      useCases.listToday()
    }
  }

  @Test
  fun clearSessionMakesPreviouslyReturnedUseCasesUnwritable() {
    val storageDirectory = temporaryFolder.newFolder("diary")
    val useCases = DiaryEntryUseCasesFactory.create(storageDirectory, createKey())

    DiaryEntryUseCasesFactory.clearSession()

    assertThrows(IllegalStateException::class.java) {
      useCases.createEntry()
    }
  }

  private fun createKey(): UnlockedDiaryKey = UnlockedDiaryKey(ByteArray(32) { index -> (index + 1).toByte() })
}
