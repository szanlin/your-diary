package com.y3lc.yourdiary

import android.os.Bundle
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import com.y3lc.yourdiary.databinding.FragmentEditorBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.y3lc.yourdiary.diary.domain.EntryId
import com.y3lc.yourdiary.diary.domain.DiaryEntryUseCases
import com.y3lc.yourdiary.diary.domain.NewPhoto
import com.y3lc.yourdiary.diary.domain.PhotoId
import com.y3lc.yourdiary.diary.domain.PhotoSaveResult
import com.y3lc.yourdiary.diary.domain.TagId
import com.y3lc.yourdiary.diary.ui.EditorSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EditorFragment : Fragment() {

  private var bindingReference: FragmentEditorBinding? = null
  private val binding: FragmentEditorBinding
    get() = requireNotNull(bindingReference)
  private var entryId: EntryId? = null
  private var wasNewEntry = false
  private var selectedTagIds: List<TagId> = emptyList()
  private var selectedPhotoIds: List<PhotoId> = emptyList()
  private var loadingEntry = true
  private var useCases: DiaryEntryUseCases? = null
  private var saveJob: Job? = null
  private var entryCreationJob: Job? = null
  private val writeMutex = Mutex()
  private val sessionState = EditorSessionState()
  private val pickPhotos = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
    if (uris.isNotEmpty()) importSelectedPhotos(uris)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    bindingReference = FragmentEditorBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    entryId = savedInstanceState?.getString("entryId")?.takeIf { it.isNotBlank() }?.let(::EntryId)
      ?: arguments?.getString("entryId")?.takeIf { it.isNotBlank() }?.let(::EntryId)
    wasNewEntry = savedInstanceState?.getBoolean("wasNew") ?: arguments?.getBoolean("wasNew", false) ?: false
    binding.entryMarkdownInput.doAfterTextChanged { markdown -> saveMarkdown(markdown?.toString().orEmpty()) }
    binding.selectPhotosButton.isEnabled = false
    binding.insertEmojiButton.isEnabled = false
    binding.manageTagsButton.isEnabled = false
    val baseToolbarMargin = (16 * resources.displayMetrics.density).toInt()
    ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
      val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
      val systemBarsBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
      val toolbarParams = binding.editorToolbar.layoutParams as ViewGroup.MarginLayoutParams
      toolbarParams.bottomMargin = baseToolbarMargin + (imeBottom - systemBarsBottom).coerceAtLeast(0)
      binding.editorToolbar.layoutParams = toolbarParams
      insets
    }
    ViewCompat.requestApplyInsets(binding.root)
    binding.selectPhotosButton.setOnClickListener {
      pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    binding.insertEmojiButton.setOnClickListener { showEmojiDialog() }
    binding.manageTagsButton.setOnClickListener { showTagDialog() }
    loadOrCreateEntry()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putString("entryId", entryId?.value)
    outState.putBoolean("wasNew", wasNewEntry)
  }

  override fun onStop() {
    sessionState.close()
    saveJob?.cancel()
    super.onStop()
  }

  private fun loadOrCreateEntry() {
    entryCreationJob = viewLifecycleOwner.lifecycleScope.launch {
      val loadedUseCases = withContext(Dispatchers.IO) {
        getDiaryEntryUseCases()
      }
      if (loadedUseCases == null) return@launch
      val entry = withContext(Dispatchers.IO) {
        val loadedEntry = entryId?.let(loadedUseCases::getEntry)
          ?: loadedUseCases.createEntry().also { wasNewEntry = true }
        if (wasNewEntry && sessionState.shouldDiscardNewEntryAfterCreation()) {
          loadedUseCases.finishEditing(loadedEntry.id, wasNew = true)
          null
        } else {
          loadedEntry
        }
      } ?: return@launch
      if (!sessionState.canWrite() || !isDiarySessionUnlocked()) return@launch
      useCases = loadedUseCases
      entryId = entry.id
      selectedTagIds = entry.tagIds
      selectedPhotoIds = entry.photoIds
      binding.entryCreatedAtText.text = formatEntryTime(entry)
      binding.entryMarkdownInput.setText(entry.markdown)
      renderSelectedPhotos()
      binding.selectPhotosButton.isEnabled = true
      binding.insertEmojiButton.isEnabled = true
      binding.manageTagsButton.isEnabled = true
      loadingEntry = false
    }
  }

  private fun saveMarkdown(markdown: String) {
    val id = entryId ?: return
    if (loadingEntry) return
    val entryUseCases = useCases ?: return
    saveJob = viewLifecycleOwner.lifecycleScope.launch {
      writeMutex.withLock {
        if (!sessionState.canWrite() || !isDiarySessionUnlocked()) return@withLock
        withContext(Dispatchers.IO) {
          if (sessionState.canWrite() && isDiarySessionUnlocked()) {
            entryUseCases.updateEntry(id, markdown, selectedTagIds)
          }
        }
      }
    }
  }

  private fun showTagDialog() {
    launchDiaryTask {
      val entryUseCases = useCases ?: return@launchDiaryTask
      val tags = withContext(Dispatchers.IO) { entryUseCases.listTags() }
      val checked = tags.map { tag -> tag.id in selectedTagIds }.toBooleanArray()
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(getString(R.string.manage_tags))
        .setMultiChoiceItems(tags.map { tag -> tag.displayName }.toTypedArray(), checked) { _, index, selected -> checked[index] = selected }
        .setNegativeButton(android.R.string.cancel, null)
        .setNeutralButton(R.string.create_tag) { _, _ -> showCreateTagDialog() }
        .setPositiveButton(android.R.string.ok) { _, _ ->
          selectedTagIds = tags.filterIndexed { index, _ -> checked[index] }.map { tag -> tag.id }
          saveMarkdown(binding.entryMarkdownInput.text?.toString().orEmpty())
        }
        .show()
    }
  }

  private fun showEmojiDialog() {
    val emojis = arrayOf("😊", "😂", "🥰", "😌", "😢", "😡", "🎉", "❤️")
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.insert_emoji)
      .setItems(emojis) { _, selectedIndex ->
        val emoji = emojis[selectedIndex]
        val input = binding.entryMarkdownInput
        val insertionIndex = input.selectionStart.coerceIn(0, input.text?.length ?: 0)
        input.text?.insert(insertionIndex, emoji)
        input.setSelection(insertionIndex + emoji.length)
        input.requestFocus()
      }
      .show()
  }

  private fun importSelectedPhotos(uris: List<Uri>) {
    val id = entryId ?: return
    val entryUseCases = useCases ?: return
    binding.selectPhotosButton.isEnabled = false
    viewLifecycleOwner.lifecycleScope.launch {
      if (!isDiarySessionUnlocked()) return@launch
      val imported = withContext(Dispatchers.IO) {
        if (!sessionState.canWrite() || !isDiarySessionUnlocked()) {
          return@withContext ImportedPhotos(emptyList(), emptyList())
        }
        val contentResolver = requireContext().applicationContext.contentResolver
        val readablePhotos = mutableListOf<NewPhoto>()
        val unreadableNames = mutableListOf<String>()
        uris.forEachIndexed { index, uri ->
          val displayName = getString(R.string.photo_default_name, index + 1)
          val mimeType = contentResolver.getType(uri)
          val bytes = try {
            contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
          } catch (_: Exception) {
            null
          }
          if (mimeType == null || bytes == null) {
            unreadableNames += displayName
          } else {
            readablePhotos += NewPhoto(displayName, mimeType, bytes)
          }
        }
        val saveResult = if (sessionState.canWrite() && isDiarySessionUnlocked()) {
          writeMutex.withLock {
            if (sessionState.canWrite() && isDiarySessionUnlocked()) entryUseCases.addPhotos(id, readablePhotos)
            else PhotoSaveResult(emptyList(), emptyList())
          }
        } else {
          PhotoSaveResult(emptyList(), emptyList())
        }
        ImportedPhotos(
          savedPhotoIds = saveResult.savedPhotoIds,
          failedNames = unreadableNames + saveResult.failedPhotoNames,
        )
      }
      if (!sessionState.canWrite() || !isDiarySessionUnlocked()) return@launch
      selectedPhotoIds = (selectedPhotoIds + imported.savedPhotoIds).distinct()
      renderSelectedPhotos()
      binding.selectPhotosButton.isEnabled = true
      if (imported.failedNames.isNotEmpty()) {
        Snackbar.make(
          binding.root,
          getString(R.string.photo_import_failure, imported.failedNames.joinToString("、")),
          Snackbar.LENGTH_LONG,
        ).show()
      }
    }
  }

  private fun renderSelectedPhotos() {
    binding.selectedPhotosText.text = getString(R.string.selected_photo_count, selectedPhotoIds.size)
    binding.selectedPhotosList.removeAllViews()
    selectedPhotoIds.forEachIndexed { index, photoId ->
      binding.selectedPhotosList.addView(Chip(requireContext()).apply {
        text = getString(R.string.photo_default_name, index + 1)
        isCloseIconVisible = true
        contentDescription = getString(R.string.remove_photo_description, index + 1)
        setOnCloseIconClickListener { removeSelectedPhoto(photoId) }
      })
    }
  }

  private fun removeSelectedPhoto(photoId: PhotoId) {
    val id = entryId ?: return
    val entryUseCases = useCases ?: return
    viewLifecycleOwner.lifecycleScope.launch {
      if (!isDiarySessionUnlocked()) return@launch
      val updated = withContext(Dispatchers.IO) {
        writeMutex.withLock {
          if (sessionState.canWrite() && isDiarySessionUnlocked()) entryUseCases.removePhoto(id, photoId) else null
        }
      }
      if (updated != null) {
        selectedPhotoIds = updated.photoIds
        renderSelectedPhotos()
      }
    }
  }

  private fun showCreateTagDialog() {
    val input = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
      hint = getString(R.string.tag_name_hint)
      setPadding(48, 0, 48, 0)
    }
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.create_tag)
      .setView(input)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(android.R.string.ok) { _, _ ->
        val name = input.text?.toString().orEmpty()
        launchDiaryTask {
          val tag = withContext(Dispatchers.IO) { useCases?.createTag(name) }
          if (tag != null) {
            selectedTagIds = (selectedTagIds + tag.id).distinct()
            saveMarkdown(binding.entryMarkdownInput.text?.toString().orEmpty())
          }
        }
      }
      .show()
  }

  override fun onDestroyView() {
    saveJob?.cancel()
    entryCreationJob?.cancel()
    saveJob = null
    entryCreationJob = null
    bindingReference = null
    super.onDestroyView()
  }

  private data class ImportedPhotos(
    val savedPhotoIds: List<PhotoId>,
    val failedNames: List<String>,
  )
}
