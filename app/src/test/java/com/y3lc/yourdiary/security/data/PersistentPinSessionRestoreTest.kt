package com.y3lc.yourdiary.security.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PersistentPinSessionRestoreTest {
  @Test
  fun restoreDiaryKeyCreatesAnUnlockedCopyWithoutRetainingTheCallersArray() {
    val session = PersistentPinSession(InMemoryPinKeyMaterialStore())
    val key = ByteArray(32) { it.toByte() }

    session.restoreDiaryKey(key)
    key.fill(0)

    assertArrayEquals(ByteArray(32) { it.toByte() }, requireNotNull(session.currentKey()))
    session.lock()
    assertFalse(session.currentKey() != null)
  }

  private class InMemoryPinKeyMaterialStore : PinKeyMaterialStore {
    override fun read(): PinKeyMaterial? = null

    override fun write(material: PinKeyMaterial) = Unit
  }
}
