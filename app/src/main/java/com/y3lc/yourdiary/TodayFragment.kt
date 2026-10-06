package com.y3lc.yourdiary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.y3lc.yourdiary.databinding.FragmentTodayBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

class TodayFragment : Fragment() {

  private var bindingReference: FragmentTodayBinding? = null
  private val binding: FragmentTodayBinding
    get() = requireNotNull(bindingReference)

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    bindingReference = FragmentTodayBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onDestroyView() {
    bindingReference = null
    super.onDestroyView()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    loadEntries()
  }

  override fun onResume() {
    super.onResume()
    if (bindingReference != null) loadEntries()
  }

  private fun loadEntries() {
    launchDiaryTask {
      val (entries, historyEntries) = withContext(Dispatchers.IO) {
        val useCases = getDiaryEntryUseCases()
        useCases?.listToday().orEmpty() to useCases?.searchHistory("", null).orEmpty()
      }
      if (bindingReference == null || !isDiarySessionUnlocked()) return@launchDiaryTask
      val today = LocalDate.now(ZoneId.systemDefault())
      binding.todayDateText.text = today.dayOfMonth.toString().padStart(2, '0')
      binding.todayMonthText.text = today.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
      binding.todayEmptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
      binding.todayEntriesContainer.removeAllViews()
      entries.forEach { entry ->
        addTodayEntryCard(binding.todayEntriesContainer, entry) {
          findNavController().navigate(R.id.readerFragment, Bundle().apply { putString("entryId", entry.id.value) })
        }
      }
      binding.todayCalendarContainer.removeAllViews()
      buildRecentDiaryDays(historyEntries, today, ZoneId.systemDefault()).forEach { day ->
        addCalendarDay(day)
      }
    }
  }

  private fun addCalendarDay(day: RecentDiaryDay) {
    val dayView = layoutInflater.inflate(R.layout.item_today_calendar_day, binding.todayCalendarContainer, false)
    val isToday = day.date == LocalDate.now(ZoneId.systemDefault())
    val calendarCard = dayView as MaterialCardView
    if (isToday) {
      calendarCard.setCardBackgroundColor(
        MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimaryContainer, 0),
      )
      calendarCard.strokeWidth = (1 * resources.displayMetrics.density).toInt()
    }
    dayView.findViewById<android.widget.TextView>(R.id.calendarWeekdayText).text =
      day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
    dayView.findViewById<android.widget.TextView>(R.id.calendarDayText).text = day.date.dayOfMonth.toString()
    dayView.findViewById<View>(R.id.calendarEntryMarker).visibility = if (day.hasEntries) View.VISIBLE else View.GONE
    dayView.isActivated = isToday
    dayView.contentDescription = getString(
      when {
        isToday && day.hasEntries -> R.string.today_calendar_today_has_entries
        isToday -> R.string.today_calendar_today_no_entries
        day.hasEntries -> R.string.today_calendar_has_entries
        else -> R.string.today_calendar_no_entries
      },
      day.date,
    )
    dayView.setOnClickListener {
      findNavController().navigate(R.id.historyFragment, Bundle().apply { putString("selectedDate", day.date.toString()) })
    }
    binding.todayCalendarContainer.addView(dayView)
  }
}
