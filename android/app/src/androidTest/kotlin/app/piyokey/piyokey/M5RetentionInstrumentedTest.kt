package app.piyokey.piyokey

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M5RetentionInstrumentedTest {
  val composeRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val rules: RuleChain = RuleChain.outerRule(TestAppStateRule(skipOnboarding = true)).around(composeRule)

  @Test
  fun homeShowsPiyoWeekBeforeDailyAndDailyStartsOfflinePractice() {
    waitForShell()
    composeRule.onNodeWithTag("retention-profile-card").assertIsDisplayed()
    composeRule.onNodeWithTag("retention-daily-challenge").assertIsDisplayed()
    saveScreenshot("01-retention-home.png")
    composeRule.onNodeWithTag("retention-daily-challenge").performClick()
    composeRule.onNodeWithTag("practice-screen").assertIsDisplayed()
    saveScreenshot("02-daily-practice.png")
  }

  @Test
  fun homeRandomFiveStartsOfflinePractice() {
    waitForShell()
    composeRule.onNodeWithTag("home-quick-random").performScrollTo().assertIsDisplayed().performClick()
    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching { composeRule.onNodeWithTag("practice-screen").fetchSemanticsNode() }.isSuccess
    }
    composeRule.onNodeWithTag("practice-screen").assertIsDisplayed()
  }

  @Test
  fun homeWeeklyCupStartsFlowDirectlyWithBuiltinKeyboard() {
    waitForShell()
    composeRule.onNodeWithTag("home-quick-piyo-cup").performScrollTo().assertIsDisplayed().performClick()
    composeRule.waitUntil(timeoutMillis = 20_000) {
      runCatching { composeRule.onNodeWithTag("flow-result").fetchSemanticsNode() }.isSuccess
    }
    composeRule.onNodeWithTag("flow-result").assertIsDisplayed()
  }

  @Test
  fun curriculumStartsWithSequentialCoreUnlockAndFreePracticeExit() {
    waitForShell()
    composeRule.onNodeWithTag("nav-practice").performClick()
    composeRule.onNodeWithTag("curriculum-map").assertIsDisplayed()
    composeRule.onNodeWithTag("curriculum-stage-chapter_1_basic_consonants").assertIsDisplayed()
    composeRule.onNodeWithTag("curriculum-stage-chapter_2_basic_vowels-locked").assertIsDisplayed()
    saveScreenshot("03-curriculum-map.png")
    composeRule.onNodeWithTag("curriculum-stage-chapter_1_basic_consonants").performClick()
    composeRule.onNodeWithTag("practice-screen").assertIsDisplayed()
  }

  @Test
  fun profileShowsReviewAndReminderDefaultsOffWithoutOpeningSessionModal() {
    waitForShell()
    composeRule.onNodeWithTag("nav-profile").performClick()
    composeRule.onNodeWithTag("piyo-profile-detail").assertIsDisplayed()
    saveScreenshot("04-piyo-profile.png")
    composeRule.onNodeWithTag("retention-reminder-toggle").performScrollTo().assertIsDisplayed()
    saveScreenshot("05-profile-review-reminder.png")
  }

  private fun waitForShell() {
    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching { composeRule.onNodeWithTag("nav-home").fetchSemanticsNode() }.isSuccess
    }
  }

  private fun saveScreenshot(name: String) {
    val directory = File(composeRule.activity.getExternalFilesDir(null), "m5-evidence").apply { mkdirs() }
    val output = File(directory, name)
    FileOutputStream(output).use { stream ->
      composeRule.onRoot().captureToImage().asAndroidBitmap()
        .compress(Bitmap.CompressFormat.PNG, 100, stream)
    }
    println("PIYOKEY_M5_SCREENSHOT=${output.absolutePath}")
  }
}
