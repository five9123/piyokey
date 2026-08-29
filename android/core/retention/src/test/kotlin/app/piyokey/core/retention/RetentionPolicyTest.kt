package app.piyokey.core.retention

import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckAuthor
import app.piyokey.core.deckkit.DeckType
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RetentionPolicyTest {
  @Test
  fun dailyChallengeIsStablePerGoalButCanReorderForAnotherGoal() {
    val day = JstDay("2026-08-25")
    assertEquals(DailyChallengePolicy.items(day, goalSalt = 2), DailyChallengePolicy.items(day, goalSalt = 2))
    assertNotEquals(DailyChallengePolicy.items(day, goalSalt = 0), DailyChallengePolicy.items(day, goalSalt = 1))
  }

  @Test fun quickPracticePrefersGoalTagsAvoidsRecentAndKeepsSourceDecks() {
    val preferred = deck("preferred", listOf("TOPIK"), listOf("가", "나", "다", "라", "마", "바"))
    val other = deck("other", listOf("日常"), listOf("사", "아", "자", "차", "카"))
    val session = requireNotNull(
      QuickPracticePolicy.makeSession(
        installedDecks = listOf(other, preferred),
        fallbackDecks = emptyList(),
        preferredTags = setOf("TOPIK"),
        recentWordKeys = listOf("가"),
        random = Random(7),
      ),
    )
    assertEquals(5, session.sources.size)
    assertTrue(session.sources.all { it.sourceDeckId == "preferred" })
    assertFalse("가" in session.wordKeys)
  }

  @Test fun quickPracticeFallsBackFiltersPhrasesAndCanonicalDuplicates() {
    val installed = deck("installed", emptyList(), listOf("가", "같이 가요"))
    val fallback = deck("fallback", emptyList(), listOf("가", "나", "다", "라", "마", "바"))
    val session = requireNotNull(
      QuickPracticePolicy.makeSession(
        installedDecks = listOf(installed),
        fallbackDecks = listOf(fallback),
        preferredTags = emptySet(),
        recentWordKeys = emptyList(),
        random = Random(3),
      ),
    )
    assertEquals(5, session.wordKeys.toSet().size)
    assertFalse("같이 가요" in session.wordKeys)
    assertEquals("installed", session.sources.first { it.wordKey == "가" }.sourceDeckId)
  }

  @Test fun quickPracticeHistoryKeepsLatestTwentyUniqueWords() {
    val first = QuickPracticeSession((1..20).map { source("단어$it", "first") })
    val second = QuickPracticeSession(listOf(source("단어1", "second"), source("새단어", "second")))
    val updated = QuickPracticePolicy.updatedHistory(
      QuickPracticePolicy.updatedHistory(emptyList(), first),
      second,
    )
    assertEquals(20, updated.size)
    assertEquals(listOf("단어1", "새단어"), updated.takeLast(2))
    assertFalse("단어2" in updated)
  }
  @Test fun curriculumHasSixChaptersAndTenToFifteenItemsPerStage() {
    assertEquals(6, CurriculumCatalog.chapters.size)
    assertTrue(CurriculumCatalog.stages.all { it.items.size in 10..15 })
    assertEquals(12, CurriculumCatalog.stages.size)
    assertEquals(
      listOf("chapter_5_words", "chapter_5_travel_words", "chapter_5_study_work_words"),
      CurriculumCatalog.chapters[4].stages.map(CurriculumStage::id),
    )
    assertEquals(
      listOf(
        "chapter_6_spacing", "chapter_6_sentences", "chapter_6_travel_phrases",
        "chapter_6_daily_conversation", "chapter_6_fan_support",
      ),
      CurriculumCatalog.chapters[5].stages.map(CurriculumStage::id),
    )
  }

  @Test fun sequentialCoreUnlocksAndChapterFiveIsFree() {
    val stages = CurriculumCatalog.stages
    assertTrue(CurriculumPolicy.isUnlocked(stages[0], emptySet()))
    assertFalse(CurriculumPolicy.isUnlocked(stages[1], emptySet()))
    assertTrue(CurriculumPolicy.isUnlocked(stages[1], setOf(stages[0].id)))
    assertTrue(CurriculumPolicy.isUnlocked(stages.first { it.chapterNumber == 5 }, emptySet()))
  }

  @Test fun starsUseIosParityThresholds() {
    assertEquals(0, CurriculumPolicy.stars(79.99, 100.0))
    assertEquals(1, CurriculumPolicy.stars(80.0, 0.0))
    assertEquals(2, CurriculumPolicy.stars(90.0, 40.0))
    assertEquals(3, CurriculumPolicy.stars(97.0, 60.0))
  }

  @Test fun jstBoundaryUsesTokyoDay() {
    assertEquals(JstDay("2026-01-02"), JstDay.fromEpochMillis(1767281400000)) // 00:30 JST
    assertEquals(JstDay("2026-01-01"), JstDay.fromEpochMillis(1767223800000)) // 08:30 JST
  }

  @Test fun weekIsMondayToSundayWithDistinctStates() {
    val today = JstDay("2026-08-25")
    val week = RetentionPolicy.week(today, setOf(JstDay("2026-08-24")))
    assertEquals("2026-08-24", week.first().day.value)
    assertEquals(StampState.COMPLETED, week[0].state)
    assertEquals(StampState.TODAY_PENDING, week[1].state)
    assertEquals(StampState.UPCOMING, week[2].state)
  }

  @Test fun currentStreakMayAnchorOnYesterdayAndLongestIsStable() {
    val completed = setOf(JstDay("2026-08-20"), JstDay("2026-08-21"), JstDay("2026-08-23"), JstDay("2026-08-24"))
    assertEquals(Streak(2, 2), RetentionPolicy.streak(completed, JstDay("2026-08-25")))
    assertEquals(Streak(0, 2), RetentionPolicy.streak(completed, JstDay("2026-08-26")))
  }

  @Test fun dailySelectionIsDeterministicAndChangesByDay() {
    val first = DailyChallengePolicy.items(JstDay("2026-08-25"))
    assertEquals(first, DailyChallengePolicy.items(JstDay("2026-08-25")))
    assertEquals(5, first.size)
    assertTrue(first != DailyChallengePolicy.items(JstDay("2026-08-26")))
  }

  @Test fun encouragementIsDeterministicAndUsesAllFourContexts() {
    val today = JstDay("2026-08-25")
    assertTrue(RetentionPolicy.encouragementIndex(today, emptySet()) in setOf(0, 1, 2, 4))
    assertTrue(RetentionPolicy.encouragementIndex(today, setOf(today)) in setOf(5, 6))
    assertTrue(RetentionPolicy.encouragementIndex(today, setOf(today.plusDays(-1))) in setOf(3, 7))
    assertTrue(RetentionPolicy.encouragementIndex(today, setOf(today.plusDays(-3))) in setOf(8, 9))
  }

  @Test fun reviewGraduatesAfterThreeConsecutivePerfectRunsAndMistakeResets() {
    val item = DeckItem("item", "사랑", "サラン", "love", null, null)
    var review = ReviewPolicy.recordMistake(null, item, "deck", 1).item
    review = ReviewPolicy.recordPerfect(review, 2).item
    review = ReviewPolicy.recordPerfect(review, 3).item
    assertTrue(requireNotNull(review).isActive)
    review = ReviewPolicy.recordMistake(review, item, "deck", 4).item
    assertEquals(0, requireNotNull(review).consecutivePerfect)
    repeat(3) { review = ReviewPolicy.recordPerfect(review, (5 + it).toLong()).item }
    assertFalse(requireNotNull(review).isActive)
  }

  private fun deck(id: String, tags: List<String>, words: List<String>) = Deck(
    deckId = id,
    version = 1,
    name = id,
    author = DeckAuthor("official", "Piyokey"),
    official = true,
    type = DeckType.WORD,
    level = 1,
    tags = tags,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
    items = words.mapIndexed { index, word ->
      DeckItem("$id-$index", word, "reading", "meaning", "audio/$index.mp3")
    },
  )

  private fun source(word: String, deckId: String) = QuickPracticeSource(
    DeckItem("$deckId-$word", word, "reading", "meaning", "audio/$word.mp3"),
    deckId,
    emptyList(),
  )
}
