package com.y3lc.yourdiary

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.y3lc.yourdiary.diary.data.DiaryEntryUseCasesFactory
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Test
import org.junit.Assert.fail

class DiaryAcceptanceInstrumentedTest {

  @Before
  fun clearDiaryData() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    DiaryEntryUseCasesFactory.clearSession()
    context.filesDir.deleteRecursively()
    context.getSharedPreferences("security", Context.MODE_PRIVATE).edit().clear().commit()
  }

  @Test
  fun movingAppToBackgroundRequiresPinBeforeDiaryIsVisibleAgain() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      unlockWithPin("2468")
      onView(withId(R.id.fab)).check(matches(isDisplayed()))

      scenario.moveToState(Lifecycle.State.CREATED)
      scenario.moveToState(Lifecycle.State.RESUMED)

      onView(withId(R.id.pinInput)).check(matches(isDisplayed()))
      onView(withId(R.id.fab)).check(matches(not(isDisplayed())))
      unlockWithPin("2468")
      scenario.onActivity { activity ->
        check(activity.isDiaryUnlocked()) { "正确 PIN 解锁后密钥会话应保持已解锁" }
        check(
          activity.findNavController(R.id.nav_host_fragment_content_main).currentDestination?.id == R.id.todayFragment
        ) { "PIN 解锁后应立即进入今天首页" }
      }
      onView(withId(R.id.fab)).perform(waitForDisplayed())
      scenario.onActivity { activity ->
        val navController = activity.findNavController(R.id.nav_host_fragment_content_main)
        check(navController.currentDestination?.id == R.id.todayFragment)
        try {
          navController.getBackStackEntry(R.id.lockFragment)
          fail("解锁后锁屏页面不应保留在返回栈中")
        } catch (_: IllegalArgumentException) {
          // 锁屏已被从返回栈清除。
        }
      }
    }
  }

  @Test
  fun entryAutoSavesSupportsTagSearchAndTrashRestoreAndPermanentDelete() {
    val entryText = "设备验收日记内容"
    val tagName = "设备验收标签"
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      unlockWithPin("2468")
      onView(withContentDescription(R.string.create_entry)).perform(click())
      onView(withId(R.id.entryCreatedAtText)).perform(waitForTextChange(R.string.entry_created_time_placeholder))
      onView(withId(R.id.entryMarkdownInput)).perform(replaceText(entryText))
      onView(withId(R.id.manageTagsButton)).perform(click())
      onView(withId(android.R.id.button3)).perform(click())
      onView(withHint(R.string.tag_name_hint)).perform(replaceText(tagName))
      onView(withId(android.R.id.button1)).perform(click())
      onView(withId(R.id.manageTagsButton)).perform(click())
      onView(withText(tagName)).check(matches(isDisplayed()))
      onView(withId(android.R.id.button1)).perform(click())

      pressBack()
      onView(withText(entryText)).perform(waitForDisplayed())
      onView(withText(entryText)).perform(click())
      onView(withText(entryText)).check(matches(isDisplayed()))
      onView(withText(tagName)).check(matches(isDisplayed()))

      scenario.onActivity { activity ->
        activity.findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.historyFragment)
      }
      onView(withId(R.id.historySearchInput)).perform(replaceText(entryText))
      onView(entryTextOutsideSearch(entryText)).perform(waitForDisplayed())
      onView(withText(tagName)).perform(click())
      onView(entryTextOutsideSearch(entryText)).check(matches(isDisplayed()))

      scenario.onActivity { activity ->
        activity.findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.readerFragment, android.os.Bundle().apply {
          putString("entryId", getOnlyEntryId(activity))
        })
      }
      onView(withId(R.id.deleteEntryButton)).perform(waitForDisplayed(), click())
      scenario.onActivity { activity ->
        activity.findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.trashFragment)
      }
      onView(withText(entryText)).perform(waitForDisplayed())
      onView(withText(R.string.restore_entry)).perform(click())
      onView(withText(R.string.trash_empty_title)).perform(waitForDisplayed())

      scenario.onActivity { activity ->
        activity.findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.readerFragment, android.os.Bundle().apply {
          putString("entryId", getOnlyEntryId(activity))
        })
      }
      onView(withId(R.id.deleteEntryButton)).perform(waitForDisplayed(), click())
      scenario.onActivity { activity ->
        activity.findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.trashFragment)
      }
      onView(withText(R.string.delete_permanently)).perform(waitForDisplayed(), click())
      onView(withText(R.string.trash_empty_title)).perform(waitForDisplayed())
    }
  }

  @Test
  fun leavingAnEmptyNewEntryDoesNotLeaveItOnTodayScreen() {
    ActivityScenario.launch(MainActivity::class.java).use {
      unlockWithPin("2468")
      onView(withContentDescription(R.string.create_entry)).perform(click())
      onView(withId(R.id.entryCreatedAtText)).perform(waitForTextChange(R.string.entry_created_time_placeholder))
      pressBack()
      onView(withText(R.string.today_empty_title)).perform(waitForDisplayed())
    }
  }

  @Test
  fun editorToolbarInsertsEmojiIntoMarkdownAndAutoSavesIt() {
    ActivityScenario.launch(MainActivity::class.java).use {
      unlockWithPin("2468")
      onView(withContentDescription(R.string.create_entry)).perform(click())
      onView(withId(R.id.entryCreatedAtText)).perform(waitForTextChange(R.string.entry_created_time_placeholder))

      onView(withContentDescription("插入表情")).perform(click())
      onView(withText("😊")).perform(click())
      onView(withId(R.id.entryMarkdownInput)).check(matches(withText("😊")))

      pressBack()
      onView(withText("😊")).perform(waitForDisplayed())
    }
  }

  @Test
  fun editorToolbarUsesFloatingHorizontalInsets() {
    ActivityScenario.launch(MainActivity::class.java).use {
      unlockWithPin("2468")
      onView(withContentDescription(R.string.create_entry)).perform(click())
      onView(withId(R.id.entryCreatedAtText)).perform(waitForTextChange(R.string.entry_created_time_placeholder))
      onView(withId(R.id.editorToolbar)).check { toolbar, _ ->
        val parentWidth = requireNotNull(toolbar.parent as? View).width
        check(toolbar.width < parentWidth) { "编辑工具栏应作为悬浮容器保留左右留白" }
        check(toolbar.left > 0 && toolbar.right < parentWidth) { "编辑工具栏应在页面内水平居中" }
      }
    }
  }

  @Test
  fun editorToolbarMovesAboveVisibleKeyboard() {
    ActivityScenario.launch(MainActivity::class.java).use {
      unlockWithPin("2468")
      onView(withContentDescription(R.string.create_entry)).perform(click())
      onView(withId(R.id.entryCreatedAtText)).perform(waitForTextChange(R.string.entry_created_time_placeholder))
      onView(withId(R.id.entryMarkdownInput)).perform(click(), typeText("ime"))
      onView(withId(R.id.editorToolbar)).check { toolbar, _ ->
        val layoutParams = toolbar.layoutParams as ViewGroup.MarginLayoutParams
        val baseMargin = (16 * toolbar.resources.displayMetrics.density).toInt()
        check(layoutParams.bottomMargin > baseMargin) { "键盘显示时工具栏应上移" }
      }
    }
  }

  private fun unlockWithPin(pin: String) {
    onView(withId(R.id.pinInput)).perform(replaceText(pin))
    closeSoftKeyboard()
    onView(withId(R.id.unlockWithPinButton)).perform(waitForDisplayed(), click())
  }

  private fun getOnlyEntryId(activity: MainActivity): String = requireNotNull(activity.getDiaryEntryUseCases())
    .searchHistory("", null)
    .single()
    .id
    .value

  private fun entryTextOutsideSearch(entryText: String): Matcher<View> = allOf(
    withText(entryText),
    not(withId(R.id.historySearchInput)),
  )

  private fun waitForDisplayed(): ViewAction = object : ViewAction {
    override fun getConstraints(): Matcher<View> = org.hamcrest.Matchers.any(View::class.java)

    override fun getDescription(): String = "等待视图显示"

    override fun perform(uiController: androidx.test.espresso.UiController, view: View) {
      var remainingMillis = 5_000L
      while (view.visibility != View.VISIBLE && remainingMillis > 0L) {
        uiController.loopMainThreadForAtLeast(50)
        remainingMillis -= 50
      }
      check(view.visibility == View.VISIBLE) { "视图未在 5 秒内显示" }
    }
  }

  private fun waitForTextChange(placeholderRes: Int): ViewAction = object : ViewAction {
    override fun getConstraints(): Matcher<View> = org.hamcrest.Matchers.instanceOf(TextView::class.java)

    override fun getDescription(): String = "等待异步加载的创建时间"

    override fun perform(uiController: androidx.test.espresso.UiController, view: View) {
      val placeholder = view.context.getString(placeholderRes)
      var remainingMillis = 5_000L
      while ((view as TextView).text.toString() == placeholder && remainingMillis > 0L) {
        uiController.loopMainThreadForAtLeast(50)
        remainingMillis -= 50
      }
      check(view.text.toString() != placeholder) { "创建时间未在 5 秒内加载" }
    }
  }
}
