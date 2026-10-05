package com.y3lc.yourdiary.security.data

data class PinKeyMaterial(
  val saltBytes: ByteArray,
  val verifierBytes: ByteArray,
  val iterations: Int,
  val nonceBytes: ByteArray,
  val ciphertextBytes: ByteArray,
)

interface PinKeyMaterialStore {
  fun read(): PinKeyMaterial?

  fun write(material: PinKeyMaterial)
}
