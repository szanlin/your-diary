package com.y3lc.yourdiary

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.y3lc.yourdiary.backup.ui.BackupUiController
import com.y3lc.yourdiary.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

  private var bindingReference: FragmentSettingsBinding? = null
  private val binding: FragmentSettingsBinding
    get() = requireNotNull(bindingReference)
  private var pendingExportPassword: CharArray? = null

  private val createBackupDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
    val password = pendingExportPassword
    pendingExportPassword = null
    if (uri != null && password != null) exportBackup(uri, password) else password?.fill('\u0000')
  }

  private val openBackupDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) showRestorePasswordDialog(uri)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    bindingReference = FragmentSettingsBinding.inflate(inflater, container, false)
    binding.exportBackupButton.setOnClickListener { showExportPasswordDialog() }
    binding.restoreBackupButton.setOnClickListener { openBackupDocument.launch(arrayOf("application/octet-stream")) }
    return binding.root
  }

  override fun onDestroyView() {
    pendingExportPassword?.fill('\u0000')
    pendingExportPassword = null
    bindingReference = null
    super.onDestroyView()
  }

  private fun showExportPasswordDialog() {
    val passwordInput = createPasswordInput(getString(R.string.backup_password))
    val confirmInput = createPasswordInput(getString(R.string.backup_password_confirm))
    val content = createDialogContent(passwordInput, confirmInput)
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.export_backup)
      .setMessage(R.string.backup_password_unrecoverable)
      .setView(content)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.continue_action, null)
      .show()
      .also { dialog ->
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
          val password = passwordInput.editText?.text?.toString().orEmpty()
          val confirmation = confirmInput.editText?.text?.toString().orEmpty()
          if (password.isBlank()) {
            passwordInput.error = getString(R.string.backup_password_required)
          } else if (password != confirmation) {
            confirmInput.error = getString(R.string.backup_password_not_match)
          } else {
            passwordInput.error = null
            confirmInput.error = null
            pendingExportPassword = password.toCharArray()
            passwordInput.editText?.text?.clear()
            confirmInput.editText?.text?.clear()
            dialog.dismiss()
            createBackupDocument.launch(getString(R.string.backup_default_file_name))
          }
        }
      }
  }

  private fun showRestorePasswordDialog(uri: Uri) {
    val passwordInput = createPasswordInput(getString(R.string.backup_password))
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.restore_backup)
      .setMessage(R.string.backup_password_unrecoverable)
      .setView(passwordInput)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.restore_and_merge, null)
      .show()
      .also { dialog ->
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
          val password = passwordInput.editText?.text?.toString().orEmpty()
          if (password.isBlank()) {
            passwordInput.error = getString(R.string.backup_password_required)
            return@setOnClickListener
          }
          passwordInput.editText?.text?.clear()
          dialog.dismiss()
          restoreBackup(uri, password.toCharArray())
        }
      }
  }

  private fun createPasswordInput(hint: String): TextInputLayout = TextInputLayout(requireContext()).apply {
    this.hint = hint
    addView(EditText(context).apply {
      contentDescription = hint
      inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
      setSingleLine()
    })
  }

  private fun createDialogContent(firstInput: TextInputLayout, secondInput: TextInputLayout): View =
    android.widget.LinearLayout(requireContext()).apply {
      orientation = android.widget.LinearLayout.VERTICAL
      val margin = resources.getDimensionPixelSize(R.dimen.dialog_content_margin)
      setPadding(margin, 0, margin, 0)
      addView(firstInput)
      addView(secondInput)
    }

  private fun exportBackup(uri: Uri, password: CharArray) {
    val controller = createBackupController() ?: run {
      password.fill('\u0000')
      showMessage(R.string.backup_requires_unlock)
      return
    }
    viewLifecycleOwner.lifecycleScope.launch {
      val result = runCatching {
        val output = requireContext().contentResolver.openOutputStream(uri)
          ?: error(getString(R.string.backup_file_unavailable))
        controller.exportBackup(output, password)
      }
      password.fill('\u0000')
      result.onSuccess {
        showMessage(R.string.backup_export_success)
      }.onFailure {
        showMessage(R.string.backup_export_failure)
      }
    }
  }

  private fun restoreBackup(uri: Uri, password: CharArray) {
    val controller = createBackupController() ?: run {
      password.fill('\u0000')
      showMessage(R.string.backup_requires_unlock)
      return
    }
    viewLifecycleOwner.lifecycleScope.launch {
      val result = runCatching {
        val input = requireContext().contentResolver.openInputStream(uri)
          ?: error(getString(R.string.backup_file_unavailable))
        controller.restoreBackup(input, password)
      }
      password.fill('\u0000')
      result.onSuccess {
        showMessage(R.string.backup_restore_success)
      }.onFailure {
        showMessage(R.string.backup_restore_failure)
      }
    }
  }

  private fun showMessage(messageRes: Int) {
    bindingReference?.root?.let { root -> Snackbar.make(root, messageRes, Snackbar.LENGTH_LONG).show() }
  }

  private fun createBackupController(): BackupUiController? =
    (activity as? MainActivity)?.getDiaryRepository()?.let(::BackupUiController)
}
