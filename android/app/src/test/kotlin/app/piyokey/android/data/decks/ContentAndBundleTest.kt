package app.piyokey.android.data.decks

import app.piyokey.android.data.DataTestSupport
import app.piyokey.core.domain.FlowGameRankTuning
import app.piyokey.core.domain.OnboardingGoal
import app.piyokey.core.domain.CurriculumCatalog
import app.piyokey.core.domain.JstDay
import app.piyokey.android.data.persistence.InMemoryKeyValueStore
import app.piyokey.android.data.retention.QuickPracticeContent
import app.piyokey.android.data.retention.RandomWordPracticeHistory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentAndBundleTest {
  private val bundled = BundledContent(DataTestSupport.assets)
  private val catalog = DataTestSupport.bundledRepository.loadCatalog()

  @Test
  fun contentLocaleFallsBackLikeIos() {
    val entry = catalog.decks.first()
    val deck = bundled.officialDeck(entry)!!
    assertEquals(deck.localizedName("en") ?: "N/A", ContentLocale.name(deck, "en", "N/A"))
    assertEquals(deck.name, ContentLocale.name(deck, "ja", "N/A"))
    val unlocalized = deck.copy(localizations = null, defaultLocale = null)
    assertEquals("N/A", ContentLocale.name(unlocalized, "fr", "N/A"))
    assertEquals("Official", ContentLocale.authorNickname(unlocalized, "fr", "Official", "Unknown"))
    assertEquals("Unknown", ContentLocale.authorNickname(unlocalized.copy(official = false), "fr", "Official", "Unknown"))
    assertTrue(ContentLocale.tags(unlocalized, "fr").isEmpty())
    assertEquals(entry.localizedName("ja"), ContentLocale.name(entry, "ja", "N/A"))
  }

  @Test
  fun bundledDecksSchemaTuningAndFallbacks() {
    assertEquals(FlowGameRankTuning(), bundled.rankTuning)
    assertTrue(bundled.deckSchema.isNotEmpty())
    val cup = assertNotNull(bundled.piyoCupDeck())
    assertEquals("flow_topik_beginner", cup.deckId)
    assertNull(bundled.deck("../catalog.json"))
    assertNull(bundled.deck("decks/missing.json"))
    val fallbacks = bundled.randomWordFallbackDecks(OnboardingGoal.TRAVEL, catalog)
    assertEquals(listOf("official_daily_words", "flow_topik_beginner"), fallbacks.map { it.deckId })
    assertEquals(listOf("flow_topik_beginner"), bundled.randomWordFallbackDecks(OnboardingGoal.TOPIK, null).map { it.deckId })
  }

  @Test
  fun quickPracticeBuildsSessionsFromFallbacksAndRecordsHistory() {
    val history = RandomWordPracticeHistory(InMemoryKeyValueStore())
    val quick = QuickPracticeContent(bundled, history)
    val session = assertNotNull(quick.nextRandomWordSession(emptyList(), OnboardingGoal.TOPIK, catalog, Random(1)))
    assertEquals(5, session.wordKeys.toSet().size)
    assertEquals(session.wordKeys, history.recentWordKeys)
    val next = assertNotNull(quick.nextRandomWordSession(emptyList(), OnboardingGoal.TOPIK, catalog, Random(2)))
    assertTrue(next.wordKeys.none { it in session.wordKeys })
    assertEquals(10, history.recentWordKeys.size)
    val challenge = quick.dailyChallenge(JstDay.parse("2026-07-19")!!, OnboardingGoal.KEYBOARD)
    val pool = CurriculumCatalog.stage("chapter_3_syllable_building")!!.items.map { it.id }.toSet()
    assertTrue(challenge.items.all { it.id in pool })
  }
}
