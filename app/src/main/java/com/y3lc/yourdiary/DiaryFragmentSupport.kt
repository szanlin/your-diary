package com.y3lc.yourdiary

import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.y3lc.yourdiary.diary.domain.DiaryEntry
import com.y3lc.yourdiary.diary.domain.DiaryEntryUseCases
import kotlinx.coroutines.launch
import java.util.Date
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

fun Fragment.getDiaryEntryUseCases(): DiaryEntryUseCases? {
  val activity = requireActivity() as MainActivity
  return activity.takeIf { it.isDiaryUnlocked() }?.getDiaryEntryUseCases()
}

fun Fragment.isDiarySessionUnlocked(): Boolean =
  (activity as? MainActivity)?.isDiaryUnlocked() == true

fun Fragment.launchDiaryTask(task: suspend () -> Unit) {
  viewLifecycleOwner.lifecycleScope.launch {
    val activity = activity as? MainActivity ?: return@launch
    if (!activity.isDiaryUnlocked()) return@launch
    task()
  }
}

fun Fragment.addEntryCard(
  container: LinearLayout,
  entry: DiaryEntry,
  onClick: () -> Unit,
) {
  val context = container.context
  val card = MaterialCardView(context).apply {
    layoutParams = LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = (12 * resources.displayMetrics.density).toInt() }
    isClickable = true
    isFocusable = true
    setCardBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
    setOnClickListener { onClick() }
    contentDescription = formatEntryTime(entry)
  }
  val content = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(20, 16, 20, 16)
  }
  content.addView(TextView(context).apply {
    text = formatEntryTime(entry)
    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
  })
  content.addView(TextView(context).apply {
    text = entry.markdown.ifBlank { "（无正文）" }
    maxLines = 3
    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
  })
  card.addView(content)
  container.addView(card)
}

fun Fragment.addTodayEntryCard(
  container: LinearLayout,
  entry: DiaryEntry,
  onClick: () -> Unit,
) {
  val context = container.context
  val dateTime = entry.createdAt.atZone(java.time.ZoneId.systemDefault())
  val card = MaterialCardView(context).apply {
    layoutParams = LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = (12 * resources.displayMetrics.density).toInt() }
    radius = 24 * resources.displayMetrics.density
    cardElevation = 0f
    strokeWidth = 0
    setCardBackgroundColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerLow, 0))
    isClickable = true
    isFocusable = true
    contentDescription = formatEntryTime(entry)
    setOnClickListener { onClick() }
  }
  val row = LinearLayout(context).apply {
    orientation = LinearLayout.HORIZONTAL
    setPadding(20, 18, 20, 18)
  }
  val dateColumn = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    layoutParams = LinearLayout.LayoutParams((76 * resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
  }
  dateColumn.addView(TextView(context).apply {
    text = dateTime.dayOfMonth.toString().padStart(2, '0')
    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_DisplaySmall)
  })
  dateColumn.addView(TextView(context).apply {
    text = dateTime.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
    setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0))
  })
  val textColumn = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
  }
  textColumn.addView(TextView(context).apply {
    text = DateFormat.getTimeFormat(context).format(Date.from(entry.createdAt))
    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
    setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0))
  })
  textColumn.addView(TextView(context).apply {
    text = entry.markdown.ifBlank { "（无正文）" }
    maxLines = 3
    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
  })
  row.addView(dateColumn)
  row.addView(textColumn)
  card.addView(row)
  container.addView(card)
}

fun Fragment.addTimelineDateGroup(
  container: LinearLayout,
  date: LocalDate,
  entries: List<DiaryEntry>,
  onEntryClick: (DiaryEntry) -> Unit,
) {
  val context = container.context
  val row = LinearLayout(context).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.TOP
    layoutParams = LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = (12 * resources.displayMetrics.density).toInt() }
  }
  val dateColumn = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER_HORIZONTAL
    layoutParams = LinearLayout.LayoutParams(
      (92 * resources.displayMetrics.density).toInt(),
      ViewGroup.LayoutParams.MATCH_PARENT,
    )
  }
  dateColumn.addView(TextView(context).apply {
    text = date.toString()
    textAlignment = View.TEXT_ALIGNMENT_CENTER
    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
  })
  dateColumn.addView(View(context).apply {
    layoutParams = LinearLayout.LayoutParams(
      (2 * resources.displayMetrics.density).toInt(),
      0,
      1f,
    ).apply { topMargin = (8 * resources.displayMetrics.density).toInt() }
    setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
  })
  val entryColumn = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
  }
  entries.forEach { entry -> addEntryCard(entryColumn, entry) { onEntryClick(entry) } }
  row.addView(dateColumn)
  row.addView(entryColumn)
  container.addView(row)
}

fun Fragment.formatEntryTime(entry: DiaryEntry): String = DateFormat.getMediumDateFormat(requireContext())
  .format(Date.from(entry.createdAt)) + " " + DateFormat.getTimeFormat(requireContext()).format(Date.from(entry.createdAt))
