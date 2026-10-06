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
import java.time.LocalDate
import java.time.ZoneId

class HistoryFragment : Fragment() {

  private var bindingReference: FragmentHistoryBinding? = null
  private val binding: FragmentHistoryBinding
    get() = requireNotNull(bindingReference)
  private var selectedTagId: TagId? = null
  private val selectedDate: LocalDate? by lazy { parseRequestedHistoryDate(arguments?.getString("selectedDate")) }

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
    }
  }

  private fun loadEntries() {
    launchDiaryTask {
      val query = binding.historySearchInput.text?.toString().orEmpty()
      val entries = withContext(Dispatchers.IO) {
        getDiaryEntryUseCases()?.searchHistory(query, selectedTagId).orEmpty()
      }
      if (bindingReference == null || !isDiarySessionUnlocked()) return@launchDiaryTask
      binding.historyEmptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
      binding.historyEntriesContainer.removeAllViews()
      var targetDateView: View? = null
      groupEntriesByDate(entries, ZoneId.systemDefault()).forEach { group ->
        addTimelineDateGroup(binding.historyEntriesContainer, group.date, group.entries) { entry ->
          findNavController().navigate(R.id.readerFragment, Bundle().apply { putString("entryId", entry.id.value) })
        }
        if (group.date == selectedDate) targetDateView = binding.historyEntriesContainer.getChildAt(binding.historyEntriesContainer.childCount - 1)
      }
      targetDateView?.let { target ->
        val scrollView = binding.root
        val targetScrollY = getHistoryTargetScrollY(binding.historyEntriesContainer.top, target.top)
        scrollView.post {
          if (!scrollView.isAttachedToWindow) return@post
          scrollView.smoothScrollTo(
            0,
            targetScrollY,
          )
        }
      }
    }
  }

  override fun onDestroyView() {
    bindingReference = null
    super.onDestroyView()
  }
}
