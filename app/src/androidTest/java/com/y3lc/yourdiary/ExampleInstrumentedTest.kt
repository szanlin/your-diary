package com.y3lc.yourdiary

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

  @Test
  fun initialPinSetupNavigatesToTodayAndShowsTheOnlyCreateEntryAction() {
    ActivityScenario.launch(MainActivity::class.java).use {
      onView(withId(R.id.pinInput)).perform(replaceText("2468"))
      onView(withId(R.id.unlockWithPinButton)).perform(click())
      onView(withId(R.id.fab)).check(matches(isDisplayed()))
      onView(withId(R.id.tagFilterGroup)).check(doesNotExist())
    }
  }
}
