package com.y3lc.yourdiary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.y3lc.yourdiary.databinding.FragmentTrashBinding
import com.google.android.material.button.MaterialButton
import com.y3lc.yourdiary.diary.domain.EntryId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TrashFragment : Fragment() {

  private var bindingReference: FragmentTrashBinding? = null
  private val binding: FragmentTrashBinding
    get() = requireNotNull(bindingReference)

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    bindingReference = FragmentTrashBinding.inflate(inflater, container, false)
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
      val entries = withContext(Dispatchers.IO) { getDiaryEntryUseCases()?.listTrash().orEmpty() }
      if (bindingReference == null || !isDiarySessionUnlocked()) return@launchDiaryTask
      binding.trashContentContainer.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
      binding.trashEntriesContainer.removeAllViews()
      entries.forEach { entry -> addTrashEntry(entry.id, entry.markdown) }
    }
  }

  private fun addTrashEntry(entryId: EntryId, markdown: String) {
    val entryRow = android.widget.LinearLayout(requireContext()).apply {
      orientation = android.widget.LinearLayout.VERTICAL
      setPadding(20, 16, 20, 16)
    }
    entryRow.addView(android.widget.TextView(requireContext()).apply { text = markdown.ifBlank { "（无正文）" } })
    entryRow.addView(MaterialButton(requireContext()).apply {
      text = getString(R.string.restore_entry)
      setOnClickListener { changeEntry(entryId, restore = true) }
    })
    entryRow.addView(MaterialButton(requireContext()).apply {
      text = getString(R.string.delete_permanently)
      setOnClickListener { changeEntry(entryId, restore = false) }
    })
    binding.trashEntriesContainer.addView(entryRow)
  }

  private fun changeEntry(entryId: EntryId, restore: Boolean) {
    launchDiaryTask {
      withContext(Dispatchers.IO) {
        val useCases = getDiaryEntryUseCases() ?: return@withContext
        if (restore) useCases.restoreEntry(entryId) else useCases.permanentlyDeleteEntry(entryId)
      }
      loadEntries()
    }
  }
}
