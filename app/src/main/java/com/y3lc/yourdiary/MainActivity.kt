package com.y3lc.yourdiary

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.View
import android.view.MenuItem
import com.y3lc.yourdiary.databinding.ActivityMainBinding
import com.y3lc.yourdiary.diary.data.UnlockedDiaryKey
import com.y3lc.yourdiary.diary.data.DiaryEntryUseCasesFactory
import com.y3lc.yourdiary.diary.data.EncryptedDiaryRepository
import com.y3lc.yourdiary.diary.domain.DiaryEntryUseCases
import com.y3lc.yourdiary.security.data.PersistentPinSession
import com.y3lc.yourdiary.security.data.SharedPreferencesPinKeyMaterialStore
import com.y3lc.yourdiary.security.data.BiometricKeyStore
import com.y3lc.yourdiary.security.domain.PinAttemptLimiter
import java.time.Instant
import java.util.concurrent.Executor
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var isLockedScreen = true
    private val pinAttemptLimiter = PinAttemptLimiter()
    private lateinit var mainExecutor: Executor
    private val pinSession by lazy {
        PersistentPinSession(
            SharedPreferencesPinKeyMaterialStore(getSharedPreferences("security", MODE_PRIVATE)),
        )
    }
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Intent.ACTION_SCREEN_OFF == intent.action) lockDiary(navigateToLock = true)
        }
    }
    private val biometricKeyStore by lazy { BiometricKeyStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        mainExecutor = ContextCompat.getMainExecutor(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setSupportActionBar(binding.toolbar)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.todayFragment,
                R.id.historyFragment,
                R.id.trashFragment,
                R.id.settingsFragment,
                R.id.lockFragment
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener {
            navController.navigate(R.id.editorFragment)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            isLockedScreen = destination.id == R.id.lockFragment
            binding.appBar.visibility = if (isLockedScreen) View.GONE else View.VISIBLE
            binding.fab.visibility = if (destination.id == R.id.todayFragment) {
                View.VISIBLE
            } else {
                View.GONE
            }
            invalidateOptionsMenu()
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onStop() {
        lockDiary(navigateToLock = false)
        unregisterReceiver(screenOffReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (!pinSession.isUnlocked()) lockDiary(navigateToLock = true)
    }

    fun isPinInitialized(): Boolean = pinSession.isInitialized

    fun unlockOrSetup(pin: String): PinUnlockResult {
        if (pin.isBlank()) return PinUnlockResult.Invalid
        if (!pinSession.isInitialized) {
            pinSession.setupPin(pin)
            pinAttemptLimiter.recordSuccess()
            return PinUnlockResult.Unlocked
        }
        val now = Instant.now()
        if (!pinAttemptLimiter.canAttempt(now)) {
            return PinUnlockResult.Cooldown(requireNotNull(pinAttemptLimiter.getCooldownUntil(now)))
        }
        return if (pinSession.unlock(pin)) {
            pinAttemptLimiter.recordSuccess()
            PinUnlockResult.Unlocked
        } else {
            pinAttemptLimiter.recordFailure(now)
            val cooldownUntil = pinAttemptLimiter.getCooldownUntil(now)
            if (cooldownUntil == null) PinUnlockResult.Invalid else PinUnlockResult.Cooldown(cooldownUntil)
        }
    }

    fun canConfigureBiometricUnlock(): Boolean =
        biometricKeyStore.getAuthenticationAvailability() == BiometricManager.BIOMETRIC_SUCCESS

    fun isBiometricUnlockEnabled(): Boolean =
        pinSession.isInitialized && biometricKeyStore.isEnabled && canConfigureBiometricUnlock()

    fun getBiometricStatusMessageResource(): Int = when (biometricKeyStore.getAuthenticationAvailability()) {
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> R.string.biometric_no_hardware
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> R.string.biometric_not_enrolled
        else -> R.string.biometric_unavailable
    }

    fun enableBiometricUnlock(onFinished: (Boolean) -> Unit) {
        val diaryKey = pinSession.currentKey() ?: run {
            onFinished(false)
            return
        }
        val cipher = try {
            biometricKeyStore.createEncryptionCipher()
        } catch (_: Exception) {
            diaryKey.fill(0)
            onFinished(false)
            return
        }
        BiometricPrompt(this, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val resultCipher = result.cryptoObject?.cipher
                if (resultCipher == null) {
                    diaryKey.fill(0)
                    onFinished(false)
                    return
                }
                try {
                    biometricKeyStore.saveEncryptedDiaryKey(resultCipher, diaryKey)
                    onFinished(true)
                } catch (_: Exception) {
                    onFinished(false)
                } finally {
                    diaryKey.fill(0)
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                diaryKey.fill(0)
                onFinished(false)
            }
        }).authenticate(createBiometricPromptInfo(R.string.biometric_enable_title), BiometricPrompt.CryptoObject(cipher))
    }

    fun unlockWithBiometric(onFinished: (Boolean) -> Unit) {
        val cipher = biometricKeyStore.createDecryptionCipher() ?: run {
            onFinished(false)
            return
        }
        BiometricPrompt(this, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val diaryKey = result.cryptoObject?.cipher?.let(biometricKeyStore::decryptDiaryKey)
                if (diaryKey == null) {
                    onFinished(false)
                    return
                }
                try {
                    pinSession.restoreDiaryKey(diaryKey)
                    pinAttemptLimiter.recordSuccess()
                    onFinished(true)
                } finally {
                    diaryKey.fill(0)
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFinished(false)
            }
        }).authenticate(createBiometricPromptInfo(R.string.biometric_unlock_title), BiometricPrompt.CryptoObject(cipher))
    }

    fun isDiaryUnlocked(): Boolean = pinSession.isUnlocked()

    fun getUnlockedDiaryKey(): UnlockedDiaryKey? {
        val keyBytes = pinSession.currentKey() ?: return null
        return try {
            UnlockedDiaryKey(keyBytes)
        } finally {
            keyBytes.fill(0)
        }
    }

    fun getDiaryEntryUseCases(): DiaryEntryUseCases? =
        getUnlockedDiaryKey()?.let { unlockedKey -> DiaryEntryUseCasesFactory.create(this, unlockedKey) }

    fun getDiaryRepository(): EncryptedDiaryRepository? =
        getUnlockedDiaryKey()?.let { unlockedKey -> DiaryEntryUseCasesFactory.getRepository(this, unlockedKey) }

    private fun lockDiary(navigateToLock: Boolean) {
        DiaryEntryUseCasesFactory.clearSession()
        pinSession.lock()
        if (!navigateToLock) return
        val controller = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main)
            ?.let { fragment -> (fragment as? NavHostFragment)?.navController }
        if (controller != null && controller.currentDestination?.id != R.id.lockFragment) {
            if (!controller.popBackStack(R.id.lockFragment, false)) {
                controller.navigate(R.id.lockFragment)
            }
        }
    }

    private fun createBiometricPromptInfo(titleResourceId: Int): BiometricPrompt.PromptInfo =
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(titleResourceId))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(getString(R.string.biometric_use_pin))
            .build()

    sealed interface PinUnlockResult {
        data object Unlocked : PinUnlockResult
        data object Invalid : PinUnlockResult
        data class Cooldown(val until: Instant) : PinUnlockResult
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_history -> navigateTo(R.id.historyFragment)
            R.id.action_trash -> navigateTo(R.id.trashFragment)
            R.id.action_settings -> navigateTo(R.id.settingsFragment)
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPrepareOptionsMenu(menu: android.view.Menu): Boolean {
        menu.setGroupVisible(0, !isLockedScreen)
        return super.onPrepareOptionsMenu(menu)
    }

    private fun navigateTo(destinationId: Int): Boolean {
        findNavController(R.id.nav_host_fragment_content_main).navigate(destinationId)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}
