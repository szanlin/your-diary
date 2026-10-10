package com.y3lc.yourdiary

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownRendererInstrumentedTest {

  @Test
  fun renderStripsHeadingAndBoldMarkdownAndAppliesBoldSpan() {
    val rendered = MarkdownRenderer.render("# 标题\n**重点**")

    assertEquals("标题\n重点", rendered.toString())
    val spans = (rendered as Spanned).getSpans(0, rendered.length, StyleSpan::class.java)
    assertTrue(spans.any { it.style == Typeface.BOLD })
  }
}
