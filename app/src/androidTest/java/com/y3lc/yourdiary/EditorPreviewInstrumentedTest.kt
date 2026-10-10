package com.y3lc.yourdiary

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.y3lc.yourdiary.diary.data.DiaryEntryUseCasesFactory
import org.hamcrest.Matchers.containsString
import org.junit.Before
import org.junit.Test

class EditorPreviewInstrumentedTest {

  @Before
  fun clearDiaryData() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    DiaryEntryUseCasesFactory.clearSession()
    context.filesDir.deleteRecursively()
    context.getSharedPreferences("security", Context.MODE_PRIVATE).edit().clear().commit()
  }

  @Test
  fun previewRendersMarkdownAndReturnsToEditableSource() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      unlockWithPin()
      onView(withId(R.id.fab)).perform(click())
      onView(withId(R.id.entryMarkdownInput)).perform(replaceText("# 标题\n**重点**"))
      closeSoftKeyboard()
      scenario.onActivity { activity ->
        activity.findViewById<android.view.View>(R.id.entryMarkdownInput).clearFocus()
      }

      onView(withId(R.id.entryMarkdownPreview)).check(matches(withText(containsString("标题"))))
      onView(withId(R.id.entryMarkdownPreview)).perform(click())
      onView(withId(R.id.entryMarkdownInput)).check(matches(withText("# 标题\n**重点**")))
    }
  }

  private fun unlockWithPin() {
    onView(withId(R.id.pinInput)).perform(replaceText("2468"))
    closeSoftKeyboard()
    onView(withId(R.id.unlockWithPinButton)).perform(click())
  }
}
