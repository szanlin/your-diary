package com.y3lc.yourdiary

import android.text.TextUtils
import androidx.core.text.HtmlCompat

object MarkdownRenderer {
  fun render(markdown: String): CharSequence {
    val escaped = TextUtils.htmlEncode(markdown)
    val html = escaped
      .replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
      .replace(Regex("(?m)^# (.+)$"), "<h2>$1</h2>")
      .replace("\n", "<br>")
    return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
  }
}
