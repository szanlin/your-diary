package com.y3lc.yourdiary

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.color.MaterialColors
import com.y3lc.yourdiary.databinding.FragmentCalendarBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class CalendarFragment : Fragment() {

  private var bindingReference: FragmentCalendarBinding? = null
  private val binding: FragmentCalendarBinding
    get() = requireNotNull(bindingReference)
  private var currentMonth = YearMonth.now()
  private var entryDates: Set<LocalDate> = emptySet()

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    bindingReference = FragmentCalendarBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.previousMonthButton.setOnClickListener { currentMonth = currentMonth.minusMonths(1); renderMonth() }
    binding.nextMonthButton.setOnClickListener { currentMonth = currentMonth.plusMonths(1); renderMonth() }
    loadEntryDates()
  }

  private fun loadEntryDates() {
    launchDiaryTask {
      val entries = withContext(Dispatchers.IO) { getDiaryEntryUseCases()?.searchHistory("", null).orEmpty() }
      if (bindingReference == null || !isDiarySessionUnlocked()) return@launchDiaryTask
      entryDates = entries.mapTo(linkedSetOf()) { entry -> entry.createdAt.atZone(ZoneId.systemDefault()).toLocalDate() }
      renderMonth()
    }
  }

  private fun renderMonth() {
    binding.calendarMonthText.text = currentMonth.format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA))
    binding.calendarDaysContainer.removeAllViews()
    createCalendarMonthDays(currentMonth).chunked(7).forEach { week ->
      val weekRow = LinearLayout(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp)
        orientation = LinearLayout.HORIZONTAL
      }
      week.forEach { date -> weekRow.addView(createDayView(date)) }
      binding.calendarDaysContainer.addView(weekRow)
    }
  }

  private fun createDayView(date: LocalDate): TextView = TextView(requireContext()).apply {
    val isCurrentMonth = YearMonth.from(date) == currentMonth
    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
    gravity = Gravity.CENTER
    maxLines = 1
    text = getCalendarDayLabel(date, currentMonth)
    isEnabled = isCurrentMonth
    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
    if (isCurrentMonth) {
      contentDescription = getString(
        if (date in entryDates) R.string.calendar_day_with_entries else R.string.calendar_day_without_entries,
        date.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)),
      )
    } else {
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    if (date in entryDates) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    if (isCurrentMonth) {
      setOnClickListener {
        findNavController().navigate(R.id.historyFragment, Bundle().apply { putString("selectedDate", date.toString()) })
      }
    }
  }

  override fun onDestroyView() {
    bindingReference = null
    super.onDestroyView()
  }

  private val Int.dp: Int
    get() = (this * resources.displayMetrics.density).toInt()
}
