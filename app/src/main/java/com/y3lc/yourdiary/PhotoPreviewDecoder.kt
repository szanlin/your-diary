package com.y3lc.yourdiary

import android.graphics.Bitmap
import android.graphics.BitmapFactory

object PhotoPreviewDecoder {
  fun decode(bytes: ByteArray?, maxSide: Int = defaultMaxPhotoSide): Bitmap? {
    if (bytes == null) return null
    return try {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
      val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
      BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
      )
    } catch (_: Exception) {
      null
    }
  }

  private fun calculateSampleSize(width: Int, height: Int, maxSide: Int): Int {
    var sampleSize = 1
    while (width / sampleSize > maxSide || height / sampleSize > maxSide) sampleSize *= 2
    return sampleSize
  }

  private const val defaultMaxPhotoSide = 2_048
}
