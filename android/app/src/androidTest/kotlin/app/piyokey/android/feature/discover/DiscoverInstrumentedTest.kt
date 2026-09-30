package app.piyokey.android.feature.discover

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.piyokey.android.data.AppData
import app.piyokey.android.feature.library.LibraryTestHost
import app.piyokey.android.feature.library.MyPageTabRoot
import app.piyokey.android.ui.nav.AppTab
import app.piyokey.core.domain.library.DiscoverFilters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiscoverInstrumentedTest {
  @get:Rule val rule = createComposeRule()

  private val deckId = "official_daily_words"

  @Before
  fun setUp() {
    DiscoverModel.filters = DiscoverFilters()
    AppData.onboarding.appTourCompleted = true
    runBlocking(Dispatchers.Main) { if (AppData.deckLibrary.isInstalled(deckId)) AppData.deckLibrary.removeAndWait(deckId) }
  }

  @After
  fun tearDown() {
    DiscoverModel.filters = DiscoverFilters()
    runBlocking(Dispatchers.Main) { if (AppData.deckLibrary.isInstalled(deckId)) AppData.deckLibrary.removeAndWait(deckId) }
  }

  private fun waitForCatalog() {
    rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("discover.catalog")).fetchSemanticsNodes().isNotEmpty() }
  }

  @Test
  fun discoverListsDecksAndFiltersByTagAndSearch() {
    rule.setContent { LibraryTestHost(AppTab.DISCOVER) { DiscoverTabRoot() } }
    waitForCatalog()
    rule.onNodeWithTag("discover.search").assertExists()
    rule.onAllNodesWithTag("discover.deck.official_keyboard_start").fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }

    rule.onNodeWithTag("discover.shortcuts").performScrollToKey("TOPIK")
    rule.onNodeWithTag("discover.tag.TOPIK").performClick()
    rule.onNodeWithTag("discover.results.count").assertExists()
    rule.onAllNodesWithTag("discover.deck.official_topik_one").assertCountEquals(1)
    rule.onAllNodesWithTag("discover.deck.official_keyboard_start").assertCountEquals(0)
    rule.onNodeWithTag("discover.shortcuts").performScrollToKey("TOPIK")
    rule.onNodeWithTag("discover.tag.TOPIK").performClick()

    rule.onNodeWithTag("discover.search").performTextInput("zzzz-no-such-deck")
    rule.onNodeWithTag("discover.results.empty").assertExists()
    rule.onNodeWithTag("discover.filter").performClick()
    rule.onNodeWithTag("discover.filter.reset").performClick()
    rule.onAllNodesWithTag("discover.results.empty").assertCountEquals(0)
  }

  @Test
  fun detailInstallThenDeckShowsInMyDecks() {
    rule.setContent { LibraryTestHost(AppTab.DISCOVER) { DeckDetailRoute(deckId).Content() } }
    rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("deck.detail.download")).fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithTag("deck.preview.0", useUnmergedTree = true).assertExists()
    rule.onNodeWithTag("deck.detail.download").performClick()
    rule.waitUntil(10_000) { AppData.deckLibrary.isInstalled(deckId) }
    rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("deck.detail.play")).fetchSemanticsNodes().isNotEmpty() }
  }

  @Test
  fun installedDeckIsListedOnMyPage() {
    val entry = AppData.catalog.let { it.loadIfNeeded(); it.catalog.value }?.decks?.first { it.deckId == deckId }!!
    runBlocking(Dispatchers.Main) { AppData.deckLibrary.install(entry) }
    rule.setContent { LibraryTestHost(AppTab.MY_PAGE) { MyPageTabRoot() } }
    rule.onNodeWithTag("my_decks.deck.$deckId").performScrollTo().assertExists()
  }
}
