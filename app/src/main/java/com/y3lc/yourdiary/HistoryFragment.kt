package com.y3lc.yourdiary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.fragment.findNavController
import com.y3lc.yourdiary.databinding.FragmentHistoryBinding
import com.google.android.material.chip.Chip
import com.y3lc.yourdiary.diary.domain.TagId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.LocalDate

class HistoryFragment : Fragment() {

  private var bindingReference: FragmentHistoryBinding? = null
  private val binding: FragmentHistoryBinding
    get() = requireNotNull(bindingReference)
  private var selectedTagId: TagId? = null
  private val shouldFocusTagFilters: Boolean
    get() = arguments?.getBoolean("focusTagFilters") == true
  private val selectedDate: LocalDate?
    get() = arguments?.getString("selectedDate")?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    bindingReference = FragmentHistoryBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.historySearchInput.doAfterTextChanged { loadEntries() }
    loadTags()
    loadEntries()
  }

  private fun loadTags() {
    launchDiaryTask {
      val tags = withContext(Dispatchers.IO) { getDiaryEntryUseCases()?.listTags().orEmpty() }
      if (bindingReference == null || !isDiarySessionUnlocked()) return@launchDiaryTask
      binding.tagFilterGroup.removeViews(1, (binding.tagFilterGroup.childCount - 1).coerceAtLeast(0))
      binding.allTagsChip.setOnClickListener { selectedTagId = null; loadEntries() }
      tags.forEach { tag ->
        binding.tagFilterGroup.addView(Chip(requireContext()).apply {
          id = View.generateViewId()
          text = tag.displayName
          isCheckable = true
          setOnClickListener {
            selectedTagId = if (isChecked) tag.id else null
            binding.allTagsChip.isChecked = selectedTagId == null
            loadEntries()
          }
        })
      }
      if (shouldFocusTagFilters) focusTagFilters()
    }
  }

  private fun focusTagFilters() {
    val scrollView = binding.root
    val tagFilters = binding.tagFilterGroup
    val targetScrollY = tagFilters.top
    scrollView.post {
      if (!scrollView.isAttachedToWindow) return@post
      scrollView.smoothScrollTo(0, targetScrollY)
      tagFilters.requestFocus()
      tagFilters.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
    }
  }

  private fun loadEntries() {
    launchDiaryTask {
      val query = binding.historySearchInput.text?.toString().orEmpty()
      val entries = withContext(Dispatchers.IO) {
        getDiaryEntryUseCases()?.searchHistory(query, selectedTagId).orEmpty()
      }
      if (bindingReference == null || !isDiarySessionUnlocked()) return@launchDiaryTask
      val visibleEntries = entries.filter { entry ->
        selectedDate == null || entry.createdAt.atZone(ZoneId.systemDefault()).toLocalDate() == selectedDate
      }
      binding.historyEmptyState.visibility = if (visibleEntries.isEmpty()) View.VISIBLE else View.GONE
      binding.historyEntriesContainer.removeAllViews()
      groupEntriesByDate(visibleEntries, ZoneId.systemDefault()).forEach { group ->
        addTimelineDateGroup(binding.historyEntriesContainer, group.date, group.entries) { entry ->
          findNavController().navigate(R.id.readerFragment, Bundle().apply { putString("entryId", entry.id.value) })
        }
      }
    }
  }

  override fun onDestroyView() {
    bindingReference = null
    super.onDestroyView()
  }
}
