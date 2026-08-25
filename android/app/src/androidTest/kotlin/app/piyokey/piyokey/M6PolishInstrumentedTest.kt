package app.piyokey.piyokey

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.piyokey.core.platform.PronunciationResolver
import app.piyokey.core.platform.ResultShareController
import app.piyokey.core.platform.ResultShareModel
import app.piyokey.core.platform.ResultShareRenderer
import app.piyokey.core.settings.AppLanguage
import app.piyokey.core.settings.AppPreferencesStore
import app.piyokey.core.settings.PiyoAccessory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M6PolishInstrumentedTest {
  val composeRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val rules: RuleChain = RuleChain.outerRule(TestAppStateRule(skipOnboarding = true)).around(composeRule)

  @Test
  fun resultCardIsExactSquareAndSharesOnlyContentUri() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val model = ResultShareModel(
      language = AppLanguage.JAPANESE,
      title = "フロー",
      levelOrDeck = "TOPIK I",
      score = 12_340,
      maxCombo = 42,
      streak = 7,
      scoreLabel = "スコア",
      comboLabel = "最大コンボ",
      streakLabel = "連続記録",
      downloadPrompt = "ピヨキーで練習しよう",
      caption = "結果 #ピヨキー",
    )
    val bitmap = ResultShareRenderer.render(context, model)
    assertEquals(1_200, bitmap.width)
    assertEquals(1_200, bitmap.height)
    assertNotEquals(bitmap.getPixel(0, 0), bitmap.getPixel(600, 700))

    val intent = ResultShareController(context).share(bitmap, model.caption)
    val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
    assertEquals("content", uri?.scheme)
    assertEquals("${context.packageName}.files", uri?.authority)
    assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
  }

  @Test
  fun wardrobeSelectionAndUnlocksPersist() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val store = AppPreferencesStore.create(context)
    store.update {
      it.copy(
        selectedPiyoAccessory = PiyoAccessory.TOPIK_GLASSES,
        unlockedPiyoAccessories = it.unlockedPiyoAccessories + PiyoAccessory.TOPIK_GLASSES,
      )
    }
    val stored = store.values.first()
    assertEquals(PiyoAccessory.TOPIK_GLASSES, stored.selectedPiyoAccessory)
    assertTrue(PiyoAccessory.TOPIK_GLASSES in stored.unlockedPiyoAccessories)
  }

  @Test
  fun profileExposesWardrobeAsAReachableNonSessionAction() {
    composeRule.waitUntil(timeoutMillis = 15_000) {
      composeRule.onAllNodesWithTag("nav-profile").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("nav-profile").performClick()
    composeRule.onNodeWithTag("piyo-wardrobe-open").assertIsDisplayed().performClick()
    composeRule.onNodeWithTag("piyo-accessory-auto").assertIsDisplayed()
    composeRule.onNodeWithTag("piyo-accessory-none").assertIsDisplayed()
  }

  @Test
  fun bundledCanonicalPronunciationAssetIsPresent() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val target = "안녕하세요"
    val path = PronunciationResolver.canonicalPath(target)
    context.assets.openFd(path).use { descriptor -> assertTrue(descriptor.length > 0) }
  }
}
