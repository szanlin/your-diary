package com.y3lc.yourdiary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.y3lc.yourdiary.databinding.FragmentLockBinding
import java.time.Duration
import java.time.Instant

class LockFragment : Fragment() {

  private var bindingReference: FragmentLockBinding? = null
  private val binding: FragmentLockBinding
    get() = requireNotNull(bindingReference)

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    bindingReference = FragmentLockBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val activity = requireActivity() as MainActivity
    binding.unlockWithPinButton.text = getString(
      if (activity.isPinInitialized()) R.string.unlock_with_pin else R.string.pin_setup_action,
    )
    binding.lockDescription.text = getString(
      if (activity.isPinInitialized()) R.string.pin_unlock_description else R.string.pin_setup_description,
    )
    val canConfigureBiometric = activity.canConfigureBiometricUnlock()
    val isBiometricEnabled = activity.isBiometricUnlockEnabled()
    binding.biometricStatus.setText(activity.getBiometricStatusMessageResource())
    binding.biometricStatus.visibility = if (canConfigureBiometric) View.GONE else View.VISIBLE
    binding.enableBiometricCheckbox.visibility = if (canConfigureBiometric && !isBiometricEnabled) View.VISIBLE else View.GONE
    binding.unlockWithBiometricButton.visibility = if (isBiometricEnabled) View.VISIBLE else View.GONE
    binding.unlockWithPinButton.setOnClickListener {
      val pin = binding.pinInput.text?.toString().orEmpty()
      if (pin.isBlank()) {
        binding.pinInput.error = getString(R.string.pin_required)
        return@setOnClickListener
      }
      when (val result = activity.unlockOrSetup(pin)) {
        MainActivity.PinUnlockResult.Unlocked -> {
          binding.pinInput.text?.clear()
          if (binding.enableBiometricCheckbox.isChecked && !isBiometricEnabled) {
            activity.enableBiometricUnlock { enabled ->
              if (!enabled) showBiometricEnableFailed()
              navigateToToday()
            }
          } else {
            navigateToToday()
          }
        }
        MainActivity.PinUnlockResult.Invalid -> {
          Snackbar.make(binding.root, R.string.pin_invalid, Snackbar.LENGTH_LONG).show()
        }
        is MainActivity.PinUnlockResult.Cooldown -> {
          showCooldown(result.until)
        }
      }
    }
    binding.unlockWithBiometricButton.setOnClickListener {
      activity.unlockWithBiometric { unlocked ->
        if (unlocked) navigateToToday() else showBiometricUnlockFailed()
      }
    }
  }

  private fun navigateToToday() {
    if (isAdded) findNavController().navigate(R.id.action_lockFragment_to_todayFragment)
  }

  private fun showCooldown(until: Instant) {
    val seconds = Duration.between(Instant.now(), until).seconds.coerceAtLeast(1)
    Snackbar.make(binding.root, getString(R.string.pin_cooldown, seconds), Snackbar.LENGTH_LONG).show()
  }

  private fun showBiometricEnableFailed() {
    if (isAdded) Snackbar.make(binding.root, R.string.biometric_enable_failed, Snackbar.LENGTH_LONG).show()
  }

  private fun showBiometricUnlockFailed() {
    if (isAdded) Snackbar.make(binding.root, R.string.biometric_unlock_failed, Snackbar.LENGTH_LONG).show()
  }

  override fun onDestroyView() {
    bindingReference = null
    super.onDestroyView()
  }
}
