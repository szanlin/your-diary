package com.y3lc.yourdiary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.y3lc.yourdiary.databinding.FragmentTodayBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
      val entries = withContext(Dispatchers.IO) { getDiaryEntryUseCases()?.listToday().orEmpty() }
      if (bindingReference == null || !isDiarySessionUnlocked()) return@launchDiaryTask
      binding.todayEmptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
      binding.todayEntriesContainer.removeAllViews()
      entries.forEach { entry ->
        addEntryCard(binding.todayEntriesContainer, entry) {
          findNavController().navigate(R.id.readerFragment, Bundle().apply { putString("entryId", entry.id.value) })
        }
      }
    }
  }
}
