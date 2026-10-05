package com.y3lc.yourdiary.security.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentPinSessionTest {
  @Test
  fun setupPinPersistsOnlyProtectedMaterialAndUnlocksTheSession() {
    val store = InMemoryPinKeyMaterialStore()
    val session = PersistentPinSession(store)

    session.setupPin("4829")

    val material = store.read()!!
    assertTrue(session.isInitialized)
    assertEquals(16, material.saltBytes.size)
    assertEquals(32, material.verifierBytes.size)
    assertEquals(12, material.nonceBytes.size)
    assertTrue(material.ciphertextBytes.isNotEmpty())
    assertTrue(material.ciphertextBytes.size > 32)
    assertFalse(material.javaClass.declaredFields.any { it.name.contains("pin", ignoreCase = true) })
    assertFalse(material.javaClass.declaredFields.any { it.name.contains("diaryKey", ignoreCase = true) })
    assertEquals(32, session.currentKey()!!.size)
  }

  @Test
  fun lockedSessionCanRestoreTheSameDiaryKeyWithCorrectPin() {
    val store = InMemoryPinKeyMaterialStore()
    val setupSession = PersistentPinSession(store)
    setupSession.setupPin("4829")
    val expectedKey = setupSession.currentKey()!!
    setupSession.lock()

    val reopenedSession = PersistentPinSession(store)

    assertTrue(reopenedSession.unlock("4829"))
    assertArrayEquals(expectedKey, reopenedSession.currentKey())
  }

  @Test
  fun incorrectPinDoesNotExposeDiaryKey() {
    val store = InMemoryPinKeyMaterialStore()
    PersistentPinSession(store).apply {
      setupPin("4829")
      lock()
    }
    val reopenedSession = PersistentPinSession(store)

    assertFalse(reopenedSession.unlock("0000"))
    assertNull(reopenedSession.currentKey())
  }

  @Test
  fun lockClearsTheInMemoryDiaryKey() {
    val session = PersistentPinSession(InMemoryPinKeyMaterialStore())
    session.setupPin("4829")

    session.lock()

    assertNull(session.currentKey())
  }

  private class InMemoryPinKeyMaterialStore : PinKeyMaterialStore {
    private var material: PinKeyMaterial? = null

    override fun read(): PinKeyMaterial? = material

    override fun write(material: PinKeyMaterial) {
      this.material = material
    }
  }
}
