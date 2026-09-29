package app.piyokey.android.feature.library

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.piyokey.android.data.AppData
import app.piyokey.android.platform.billing.ProStore
import app.piyokey.android.ui.nav.AppTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** iOS 1.1.2 (#195): a draft the user closed is kept but never re-presented automatically. */
@RunWith(AndroidJUnit4::class)
class DeckDraftDismissalInstrumentedTest {
  @get:Rule val rule = createComposeRule()
  private var previousAccess = false

  @Before
  fun setUp() {
    previousAccess = ProStore.hasAccess.value
    runBlocking(Dispatchers.Main) {
      ProStore.setAccessForTesting(true)
      runCatching { AppData.drafts.clear() }
    }
    AppData.onboarding.appTourCompleted = true
    DeckMakerPrefs.dismissedDraftId.value = ""
  }

  @After
  fun tearDown() {
    runBlocking(Dispatchers.Main) {
      runCatching { AppData.drafts.clear() }
      ProStore.setAccessForTesting(previousAccess)
    }
    DeckMakerPrefs.dismissedDraftId.value = ""
  }

  private fun editorVisible() = rule.onAllNodes(hasTestTag("deck_editor.screen")).fetchSemanticsNodes().isNotEmpty()

  @Test
  fun closedDraftIsKeptButNotReopenedUntilUserResumesIt() {
    rule.setContent { LibraryTestHost(AppTab.MY_PAGE) { MyPageTabRoot() } }

    rule.onNodeWithTag("my_decks.create").performScrollTo().performClick()
    rule.waitUntil(5_000) { editorVisible() }

    rule.onNodeWithTag("deck_editor.cancel").performClick()
    rule.waitUntil(5_000) { !editorVisible() }
    val active = runBlocking(Dispatchers.Main) { AppData.drafts.load() }
    assertNotNull("Closing the editor must keep the draft", active)
    assertEquals(active!!.draftId, DeckMakerPrefs.dismissedDraftId.value)

    // Returning to My Page (restore effect re-runs) must not pop the editor again.
    rule.waitForIdle()
    Thread.sleep(1_500)
    rule.waitForIdle()
    assertEquals(false, editorVisible())

    // An explicit create action resumes the same draft and re-enables recovery.
    rule.onNodeWithTag("my_decks.create").performScrollTo().performClick()
    rule.waitUntil(5_000) { editorVisible() }
    assertEquals("", DeckMakerPrefs.dismissedDraftId.value)
  }
}
