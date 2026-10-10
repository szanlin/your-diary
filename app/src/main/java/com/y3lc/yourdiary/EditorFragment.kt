package com.y3lc.yourdiary

import android.os.Bundle
import android.net.Uri
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import com.y3lc.yourdiary.databinding.FragmentEditorBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.y3lc.yourdiary.diary.domain.EntryId
import com.y3lc.yourdiary.diary.domain.DiaryEntry
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class EditorFragment : Fragment() {

  private var bindingReference: FragmentEditorBinding? = null
  private val binding: FragmentEditorBinding
    get() = requireNotNull(bindingReference)
  private var entryId: EntryId? = null
  private var wasNewEntry = false
  private var selectedTagIds: List<TagId> = emptyList()
  private var selectedPhotoIds: List<PhotoId> = emptyList()
  private var loadingEntry = true
  private var isFinishing = false
  private var useCases: DiaryEntryUseCases? = null
  private var saveJob: Job? = null
  private var photoImportJob: Job? = null
  private var entryCreationJob: Job? = null
  private val writeMutex = Mutex()
  private val sessionState = EditorSessionState()
  private val backPressCallback = object : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() {
      finishAndExit()
    }
  }
  private val pickPhotos = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
    (activity as? MainActivity)?.endPhotoPickerSession()
    if (bindingReference == null || !isDiarySessionUnlocked()) return@registerForActivityResult
    if (uris.isNotEmpty()) importSelectedPhotos(uris)
    else binding.selectPhotosButton.isEnabled = true
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
    binding.entryMarkdownInput.setOnFocusChangeListener { _, hasFocus ->
      if (!hasFocus) showMarkdownPreview()
    }
    binding.entryMarkdownPreview.setOnClickListener { showMarkdownInput() }
    binding.entryMarkdownInput.isEnabled = false
    binding.selectPhotosButton.isEnabled = false
    binding.insertEmojiButton.isEnabled = false
    binding.manageTagsButton.isEnabled = false
    requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressCallback)
    binding.exitEditorButton.setOnClickListener { finishAndExit() }
    binding.finishEditingButton.setOnClickListener { finishAndExit() }
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
      (activity as? MainActivity)?.beginPhotoPickerSession()
      try {
        pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
      } catch (_: IllegalStateException) {
        (activity as? MainActivity)?.endPhotoPickerSession()
        binding.selectPhotosButton.isEnabled = true
      }
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
      renderCreatedAt(entry)
      binding.entryMarkdownInput.setText(entry.markdown)
      binding.entryMarkdownInput.isEnabled = true
      if (entry.markdown.isBlank()) showMarkdownInput() else showMarkdownPreview()
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
    binding.entryMarkdownPreview.text = MarkdownRenderer.render(markdown)
  }

  private fun showMarkdownPreview() {
    binding.entryMarkdownPreview.text = MarkdownRenderer.render(binding.entryMarkdownInput.text?.toString().orEmpty())
    binding.entryMarkdownPreview.visibility = View.VISIBLE
    binding.entryMarkdownInput.visibility = View.GONE
  }

  private fun showMarkdownInput() {
    binding.entryMarkdownPreview.visibility = View.GONE
    binding.entryMarkdownInput.visibility = View.VISIBLE
    binding.entryMarkdownInput.requestFocus()
  }

  private fun renderCreatedAt(entry: DiaryEntry) {
    val createdAt = entry.createdAt.atZone(ZoneId.systemDefault())
    binding.entryCreatedAtText.text = String.format(Locale.CHINA, "%02d", createdAt.dayOfMonth)
    binding.entryCreatedMonthYearText.text = "${createdAt.monthValue}月 ${createdAt.year}年"
    binding.entryCreatedTimeText.text = createdAt.format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA))
  }

  private fun finishAndExit() {
    if (isFinishing) return
    isFinishing = true
    val id = entryId
    val entryUseCases = useCases
    if (id == null || entryUseCases == null || loadingEntry) {
      findNavController().navigateUp()
      return
    }
    val markdown = binding.entryMarkdownInput.text?.toString().orEmpty()
    val tagIds = selectedTagIds
    binding.exitEditorButton.isEnabled = false
    binding.finishEditingButton.isEnabled = false
    viewLifecycleOwner.lifecycleScope.launch {
      photoImportJob?.join()
      saveJob?.join()
      withContext(Dispatchers.IO) {
        writeMutex.withLock {
          if (sessionState.canWrite() && isDiarySessionUnlocked()) {
            entryUseCases.updateEntry(id, markdown, tagIds)
            entryUseCases.finishEditing(id, wasNewEntry)
          }
        }
      }
      if (isAdded) findNavController().navigateUp()
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
    if (bindingReference == null || !isDiarySessionUnlocked()) return
    val id = entryId ?: return
    val entryUseCases = useCases ?: return
    binding.selectPhotosButton.isEnabled = false
    photoImportJob = viewLifecycleOwner.lifecycleScope.launch {
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
    val photoIds = selectedPhotoIds
    val entryUseCases = useCases ?: return
    viewLifecycleOwner.lifecycleScope.launch {
      val previews = withContext(Dispatchers.IO) {
        photoIds.map { photoId ->
          PhotoPreview(photoId, PhotoPreviewDecoder.decode(entryUseCases.getPhotoForDisplay(photoId), editorPreviewMaxSide))
        }
      }
      if (!sessionState.canWrite() || !isDiarySessionUnlocked() || bindingReference == null || photoIds != selectedPhotoIds) return@launch
      previews.forEachIndexed { index, preview -> addPhotoPreview(preview, index) }
      if (previews.any { it.bitmap == null }) {
        Snackbar.make(binding.root, R.string.reader_photo_failure, Snackbar.LENGTH_LONG).show()
      }
    }
  }

  private fun addPhotoPreview(preview: PhotoPreview, index: Int) {
    val context = requireContext()
    binding.selectedPhotosList.addView(MaterialCardView(context).apply {
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
      ).apply { bottomMargin = resources.getDimensionPixelSize(R.dimen.reader_photo_spacing) }
      radius = 16 * resources.displayMetrics.density
      addView(LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        if (preview.bitmap != null) {
          addView(ImageView(context).apply {
            adjustViewBounds = true
            maxHeight = resources.getDimensionPixelSize(R.dimen.editor_photo_max_height)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setImageBitmap(preview.bitmap)
            contentDescription = getString(R.string.reader_photo_description, index + 1)
          })
        }
        addView(MaterialButton(context).apply {
          text = getString(R.string.remove_photo_description, index + 1)
          contentDescription = getString(R.string.remove_photo_description, index + 1)
          setOnClickListener { removeSelectedPhoto(preview.id) }
        })
      })
    })
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
    sessionState.close()
    saveJob?.cancel()
    entryCreationJob?.cancel()
    photoImportJob?.cancel()
    saveJob = null
    entryCreationJob = null
    photoImportJob = null
    bindingReference = null
    super.onDestroyView()
  }

  private data class ImportedPhotos(
    val savedPhotoIds: List<PhotoId>,
    val failedNames: List<String>,
  )

  private data class PhotoPreview(
    val id: PhotoId,
    val bitmap: Bitmap?,
  )

  private companion object {
    const val editorPreviewMaxSide = 720
  }
}
