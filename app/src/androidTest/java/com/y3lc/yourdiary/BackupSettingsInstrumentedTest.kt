package com.y3lc.yourdiary

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.navigation.findNavController
import org.junit.Test

class BackupSettingsInstrumentedTest {
  @Test
  fun exportBackupRequiresSeparatePasswordConfirmationAndShowsRecoveryWarning() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      onView(withId(R.id.pinInput)).perform(replaceText("2468"))
      onView(withId(R.id.unlockWithPinButton)).perform(click())
      scenario.onActivity { activity ->
        activity.findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.settingsFragment)
      }
      onView(withId(R.id.exportBackupButton)).perform(click())

      onView(withText(R.string.backup_password_unrecoverable)).check(matches(isDisplayed()))
      onView(withContentDescription(R.string.backup_password_confirm)).check(matches(isDisplayed()))
    }
  }
}
