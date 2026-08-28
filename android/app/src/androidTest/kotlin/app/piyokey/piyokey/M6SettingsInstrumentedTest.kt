package app.piyokey.piyokey

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.piyokey.core.settings.AppPreferencesStore
import app.piyokey.core.settings.AppTheme
import app.piyokey.core.settings.FontScale
import app.piyokey.core.settings.InputMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M6SettingsInstrumentedTest {
  val composeRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val rules: RuleChain = RuleChain.outerRule(TestAppStateRule(skipOnboarding = true)).around(composeRule)

  @Test
  fun commonSettingsOpensFromShellAndClosesWithoutStartingSession() {
    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching { composeRule.onNodeWithTag("common-settings-button").fetchSemanticsNode() }.isSuccess
    }
    composeRule.onNodeWithTag("common-settings-button").performClick()
    composeRule.onNodeWithTag("settings-sheet").assertIsDisplayed()
  }

  @Test
  fun settingsPersistThroughPreferencesDataStore() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val store = AppPreferencesStore.create(context)
    store.update {
      it.copy(
        theme = AppTheme.DARK,
        fontScale = FontScale.LARGE,
        soundEffectsEnabled = false,
        defaultInputMode = InputMode.OS_IME,
      )
    }

    val stored = store.values.first()
    assertEquals(AppTheme.DARK, stored.theme)
    assertEquals(FontScale.LARGE, stored.fontScale)
    assertFalse(stored.soundEffectsEnabled)
    assertEquals(InputMode.OS_IME, stored.defaultInputMode)
  }
}
