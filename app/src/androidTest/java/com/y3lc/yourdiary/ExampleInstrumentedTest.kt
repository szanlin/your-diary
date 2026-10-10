package com.y3lc.yourdiary

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matcher
import androidx.navigation.findNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.color.MaterialColors
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

  @Test
  fun lockedScreenShowsDiaryBrandingOverWallpaper() {
    ActivityScenario.launch(MainActivity::class.java).use {
      onView(withId(R.id.lockWallpaper)).check(matches(isDisplayed()))
      onView(withText(R.string.lock_brand_title)).check(matches(isDisplayed()))
      onView(withText(R.string.lock_brand_motto)).check(matches(isDisplayed()))
    }
  }

  @Test
  fun initialPinSetupNavigatesToTodayAndShowsTheOnlyCreateEntryAction() {
    ActivityScenario.launch(MainActivity::class.java).use {
      onView(withId(R.id.pinInput)).perform(replaceText("2468"))
      onView(withId(R.id.unlockWithPinButton)).perform(click())
      onView(withId(R.id.fab)).check(matches(isDisplayed()))
      onView(withId(R.id.tagFilterGroup)).check(doesNotExist())
    }
  }

  @Test
  fun navigationDrawerContainsHistoryAndSettingsButKeepsCreateOnTheHomeScreen() {
    ActivityScenario.launch(MainActivity::class.java).use {
      onView(withId(R.id.pinInput)).perform(replaceText("2468"))
      onView(withId(R.id.unlockWithPinButton)).perform(click())

      onView(withContentDescription(R.string.open_navigation)).perform(click())
      onView(withText(R.string.history_title)).check(matches(isDisplayed()))
      onView(withText(R.string.settings_title)).check(matches(isDisplayed()))
      onView(withId(R.id.fab)).check(matches(isDisplayed()))
    }
  }

  @Test
  fun tagDrawerItemOpensHistoryWithTagFiltersFocused() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      onView(withId(R.id.pinInput)).perform(replaceText("2468"))
      onView(withId(R.id.unlockWithPinButton)).perform(click())

      onView(withContentDescription(R.string.open_navigation)).perform(click())
      onView(withText(R.string.manage_tags)).perform(click())

      scenario.onActivity { activity ->
        val arguments = activity.findNavController(R.id.nav_host_fragment_content_main).currentBackStackEntry?.arguments
        check(arguments?.getBoolean("focusTagFilters") == true)
      }
    }
  }

  @Test
  fun drawerDestinationShowsUpNavigationAndReturnsToToday() {
    ActivityScenario.launch(MainActivity::class.java).use {
      onView(withId(R.id.pinInput)).perform(replaceText("2468"))
      onView(withId(R.id.unlockWithPinButton)).perform(click())

      onView(withContentDescription(R.string.open_navigation)).perform(click())
      onView(withId(R.id.action_history)).perform(click())
      onView(withContentDescription(R.string.navigate_up)).check(matches(isDisplayed()))

      pressBack()
      onView(withContentDescription(R.string.open_navigation)).check(matches(isDisplayed()))
      onView(withId(R.id.fab)).check(matches(isDisplayed()))
    }
  }

  @Test
  fun editorUsesWritingPromptWithoutFormatInstructions() {
    ActivityScenario.launch(MainActivity::class.java).use {
      onView(withId(R.id.pinInput)).perform(replaceText("2468"))
      onView(withId(R.id.unlockWithPinButton)).perform(click())
      onView(withId(R.id.fab)).perform(click())

      onView(withHint("写下此刻")).check(matches(isDisplayed()))
      onView(withText("内容会自动保存；创建时间不可编辑。"))
        .check(doesNotExist())
    }
  }

  @Test
  fun userFacingEmptyStatesUseConciseLanguage() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext

    assertEquals("正在加载…", context.getString(R.string.entry_created_time_placeholder))
    assertEquals("还没有内容", context.getString(R.string.reader_empty_content))
  }

  @Test
  fun editorOpensAsAFullScreenWritingCanvas() {
    ActivityScenario.launch(MainActivity::class.java).use {
      onView(withId(R.id.pinInput)).perform(replaceText("2468"))
      onView(withId(R.id.unlockWithPinButton)).perform(click())
      onView(withId(R.id.fab)).perform(click())

      onView(withId(R.id.appBar)).check(matches(not(isDisplayed())))
      onView(withText("完成")).check(matches(isDisplayed()))
      onView(withId(R.id.entryCreatedAtText)).check(matches(isDisplayed()))
    }
  }

  @Test
  fun homeCentersCreateActionAndShowsCalendarShortcutOnItsLeft() {
    ActivityScenario.launch(MainActivity::class.java).use {
      onView(withId(R.id.pinInput)).perform(replaceText("2468"))
      onView(withId(R.id.unlockWithPinButton)).perform(click())

      onView(withId(R.id.fab)).check { fab, _ ->
        val parent = requireNotNull(fab.parent as? View)
        check(abs(fab.left + fab.width / 2 - parent.width / 2) <= 1)
        check(fab.width >= (72 * fab.resources.displayMetrics.density).toInt())
      }
      onView(withId(R.id.calendarFab)).check { calendar, _ ->
        val parent = requireNotNull(calendar.parent as? View)
        check(calendar.right <= parent.width / 3)
      }
    }
  }

  @Test
  fun calendarShortcutOpensMonthGrid() {
    ActivityScenario.launch(MainActivity::class.java).use {
      onView(withId(R.id.pinInput)).perform(replaceText("2468"))
      onView(withId(R.id.unlockWithPinButton)).perform(click())

      onView(withContentDescription(R.string.drawer_history_description)).perform(click())
      onView(withId(R.id.calendarDaysContainer)).check(matches(isDisplayed()))
      onView(withContentDescription(R.string.previous_month)).check(matches(isDisplayed()))
      onView(withContentDescription(R.string.next_month)).check(matches(isDisplayed()))
    }
  }

  @Test
  fun calendarUsesDarkDateTextOnTheTransparentCalendarSurface() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      onView(withId(R.id.pinInput)).perform(replaceText("2468"))
      onView(withId(R.id.unlockWithPinButton)).perform(click())
      onView(withContentDescription(R.string.drawer_history_description)).perform(click())

      scenario.onActivity { activity ->
        val dayContainer = activity.findViewById<LinearLayout>(R.id.calendarDaysContainer)
        val dateButton = (0 until dayContainer.childCount)
          .flatMap { rowIndex ->
            val row = dayContainer.getChildAt(rowIndex) as LinearLayout
            (0 until row.childCount).map { row.getChildAt(it) as TextView }
          }
          .first { it.isEnabled && it.text.isNotBlank() }
        val expectedColor = MaterialColors.getColor(dateButton, com.google.android.material.R.attr.colorOnSurface)

        check(dateButton.currentTextColor == expectedColor)
      }
    }
  }

  @Test
  fun calendarDateCellsUseTheirFullWidthForDayNumbers() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      onView(withId(R.id.pinInput)).perform(replaceText("2468"))
      onView(withId(R.id.unlockWithPinButton)).perform(click())
      onView(withContentDescription(R.string.drawer_history_description)).perform(click())

      scenario.onActivity { activity ->
        val dayContainer = activity.findViewById<LinearLayout>(R.id.calendarDaysContainer)
        val dateCell = (0 until dayContainer.childCount)
          .flatMap { rowIndex ->
            val row = dayContainer.getChildAt(rowIndex) as LinearLayout
            (0 until row.childCount).map { row.getChildAt(it) as TextView }
          }
          .first { it.text.toString() == "10" }

        check(dateCell.paddingStart == 0)
        check(dateCell.paddingEnd == 0)
        check(dateCell.maxLines == 1)
      }
    }
  }

  @Test
  fun editorShowsLargeChineseDayWithMonthAndYear() {
    ActivityScenario.launch(MainActivity::class.java).use {
      onView(withId(R.id.pinInput)).perform(replaceText("2468"))
      onView(withId(R.id.unlockWithPinButton)).perform(click())
      onView(withId(R.id.fab)).perform(click())

      onView(withId(R.id.entryCreatedAtText))
        .perform(waitForTextChange(R.string.entry_created_time_placeholder))
        .check { date, _ ->
        check((date as TextView).text.toString().matches(Regex("\\d{2}")))
      }
      onView(withText(containsString("月"))).check(matches(isDisplayed()))
    }
  }

  private fun waitForTextChange(placeholderRes: Int): ViewAction = object : ViewAction {
    override fun getConstraints(): Matcher<View> = org.hamcrest.Matchers.instanceOf(TextView::class.java)

    override fun getDescription(): String = "等待创建日期加载"

    override fun perform(uiController: androidx.test.espresso.UiController, view: View) {
      val placeholder = view.context.getString(placeholderRes)
      var remainingMillis = 5_000L
      while ((view as TextView).text.toString() == placeholder && remainingMillis > 0L) {
        uiController.loopMainThreadForAtLeast(50)
        remainingMillis -= 50
      }
      check(view.text.toString() != placeholder) { "创建日期未在 5 秒内加载" }
    }
  }

}
