package com.y3lc.yourdiary

import android.os.Bundle
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.y3lc.yourdiary.databinding.FragmentReaderBinding
import com.y3lc.yourdiary.diary.domain.DiaryEntry
import com.y3lc.yourdiary.diary.domain.EntryId
import com.y3lc.yourdiary.diary.domain.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReaderFragment : Fragment() {

  private var bindingReference: FragmentReaderBinding? = null
  private val binding: FragmentReaderBinding
    get() = requireNotNull(bindingReference)

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    bindingReference = FragmentReaderBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val entryId = arguments?.getString("entryId")?.let(::EntryId) ?: return
    loadEntry(entryId)
    binding.editEntryButton.setOnClickListener {
      findNavController().navigate(R.id.editorFragment, Bundle().apply {
        putString("entryId", entryId.value)
        putBoolean("wasNew", false)
      })
    }
    binding.deleteEntryButton.setOnClickListener {
      launchDiaryTask {
        withContext(Dispatchers.IO) { getDiaryEntryUseCases()?.moveToTrash(entryId) }
        findNavController().navigateUp()
      }
    }
  }

  private fun loadEntry(entryId: EntryId) {
    launchDiaryTask {
      val content = withContext(Dispatchers.IO) {
        val useCases = getDiaryEntryUseCases() ?: return@withContext null
        val entry = useCases.getEntry(entryId) ?: return@withContext null
        ReaderContent(
          entry = entry,
          tags = useCases.listTags().filter { tag -> tag.id in entry.tagIds },
          photos = entry.photoIds.map { photoId -> PhotoPreviewDecoder.decode(useCases.getPhotoForDisplay(photoId)) },
        )
      } ?: return@launchDiaryTask
      if (bindingReference == null || !isDiarySessionUnlocked()) return@launchDiaryTask
      val entry = content.entry
      binding.entryCreatedAtText.text = formatEntryTime(entry)
      binding.entryMarkdownText.text = MarkdownRenderer.render(entry.markdown)
      renderTags(content.tags)
      renderPhotos(content.photos)
    }
  }

  private fun renderTags(tags: List<Tag>) {
    binding.readerTagGroup.removeAllViews()
    binding.readerTagGroup.visibility = if (tags.isEmpty()) View.GONE else View.VISIBLE
    tags.forEach { tag ->
      binding.readerTagGroup.addView(Chip(requireContext()).apply {
        text = tag.displayName
        isClickable = false
        isCheckable = false
      })
    }
  }

  private fun renderPhotos(photos: List<Bitmap?>) {
    binding.readerPhotosContainer.removeAllViews()
    photos.forEachIndexed { index, bitmap ->
      if (bitmap != null) {
        binding.readerPhotosContainer.addView(ImageView(requireContext()).apply {
          layoutParams = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
          ).apply { bottomMargin = resources.getDimensionPixelSize(R.dimen.reader_photo_spacing) }
          adjustViewBounds = true
          maxHeight = resources.getDimensionPixelSize(R.dimen.reader_photo_max_height)
          scaleType = ImageView.ScaleType.CENTER_INSIDE
          setImageBitmap(bitmap)
          contentDescription = getString(R.string.reader_photo_description, index + 1)
        })
      }
    }
    if (photos.any { bitmap -> bitmap == null }) {
      Snackbar.make(binding.root, R.string.reader_photo_failure, Snackbar.LENGTH_LONG).show()
    }
  }

  override fun onDestroyView() {
    bindingReference = null
    super.onDestroyView()
  }

  private data class ReaderContent(
    val entry: DiaryEntry,
    val tags: List<Tag>,
    val photos: List<Bitmap?>,
  )

}
