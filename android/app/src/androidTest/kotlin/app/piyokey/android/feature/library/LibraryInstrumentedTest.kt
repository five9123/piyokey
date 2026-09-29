package app.piyokey.android.feature.library

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.piyokey.android.data.AppData
import app.piyokey.android.platform.billing.ProStore
import app.piyokey.android.ui.nav.AppTab
import app.piyokey.core.domain.PiyoDeckDocumentNotice
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryInstrumentedTest {
  @get:Rule val rule = createComposeRule()

  private val fixtureDeckId: String by lazy {
    val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("valid/basic.typedeck").use { it.readBytes() }
    app.piyokey.core.deckkit.PiyoDeckPackageReader.read(bytes, AppData.bundled.deckSchema).deck.deckId
  }

  private fun removeUserDecks() {
    runBlocking {
    withContext(Dispatchers.Main) {
      AppData.deckLibrary.records.value.values.filter { it.source.isUserDeck }.forEach { AppData.deckLibrary.removeAndWait(it.deckId) }
      runCatching { AppData.drafts.clear() }
    }
    }
  }

  @Before
  fun setUp() {
    AppData.onboarding.appTourCompleted = true
    removeUserDecks()
  }

  @After
  fun tearDown() {
    removeUserDecks()
  }

  @Test
  fun importFixtureThroughPipelineShowsPreviewAndCommits() {
    val coordinator = LibraryLauncher.documents
    runBlocking(Dispatchers.Main) {
      coordinator.candidate.value?.let { coordinator.dismissCandidate(showNext = false) }
      if (coordinator.notice.value != null) coordinator.dismissNotice()
      coordinator.receive { InstrumentationRegistry.getInstrumentation().context.assets.open("valid/basic.typedeck") }
    }
    val candidate = coordinator.candidate.value
    assertNotNull(candidate)
    rule.setContent { LibraryTestHost(AppTab.MY_PAGE) { ImportPreview(candidate!!, coordinator) } }
    rule.onNodeWithTag("piyodeck.import.preview").assertExists()
    rule.onNodeWithTag("piyodeck.import.status.new").assertExists()
    rule.onNodeWithTag("piyodeck.import.preview.item.0").assertExists()
    rule.onNodeWithTag("piyodeck.import.action.import").performClick()
    rule.waitUntil(10_000) { AppData.deckLibrary.isInstalled(fixtureDeckId) }
    rule.waitUntil(5_000) { coordinator.notice.value == PiyoDeckDocumentNotice.IMPORTED }
    assertEquals(null, coordinator.candidate.value)
    runBlocking(Dispatchers.Main) { coordinator.dismissNotice() }
  }

  @Test
  fun importedFixtureAppearsInMyDecksAndIdenticalReimportIsNoOp() {
    val coordinator = LibraryLauncher.documents
    runBlocking(Dispatchers.Main) {
      coordinator.receive { InstrumentationRegistry.getInstrumentation().context.assets.open("valid/basic.typedeck") }
      val candidate = coordinator.candidate.value!!
      ImportActions.install(candidate, coordinator, replacing = false)
      coordinator.dismissNotice()
      coordinator.receive { InstrumentationRegistry.getInstrumentation().context.assets.open("valid/basic.typedeck") }
    }
    val again = coordinator.candidate.value!!
    rule.setContent { LibraryTestHost(AppTab.MY_PAGE) { ImportPreview(again, coordinator) } }
    rule.onNodeWithTag("piyodeck.import.status.identical").assertExists()
    rule.onNodeWithTag("piyodeck.import.action.done").performClick()
    rule.waitUntil(5_000) { coordinator.notice.value == PiyoDeckDocumentNotice.ALREADY_IMPORTED }
    runBlocking(Dispatchers.Main) { coordinator.dismissNotice() }
  }

  @Test
  fun myDecksListsImportedDeckAndCreateShowsPaywallWithoutPro() {
    assumeFalse("Paywall only appears without Deck Maker access", ProStore.hasAccess.value)
    val coordinator = LibraryLauncher.documents
    runBlocking(Dispatchers.Main) {
      coordinator.receive { InstrumentationRegistry.getInstrumentation().context.assets.open("valid/basic.typedeck") }
      ImportActions.install(coordinator.candidate.value!!, coordinator, replacing = false)
      coordinator.dismissNotice()
    }
    rule.setContent { LibraryTestHost(AppTab.MY_PAGE) { MyPageTabRoot() } }
    rule.onNodeWithTag("my_page.profile", useUnmergedTree = true).assertExists()
    rule.onNodeWithTag("my_decks.deck.$fixtureDeckId").performScrollTo().assertExists()
    rule.onNodeWithTag("my_decks.create").performScrollTo().performClick()
    rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("deck_maker.paywall.screen")).fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithTag("deck_maker.paywall.purchase").assertExists()
    rule.onNodeWithTag("deck_maker.paywall.close").performClick()
  }

  @Test
  fun reviewDeckShowsEmptyStateWithoutItems() {
    assumeFalse(AppData.review.activeItems.value.isNotEmpty())
    rule.setContent { LibraryTestHost(AppTab.MY_PAGE) { ReviewDeckRoute().Content() } }
    rule.onNodeWithTag("review.deck.empty").assertExists()
    assertTrue(true)
  }
}
