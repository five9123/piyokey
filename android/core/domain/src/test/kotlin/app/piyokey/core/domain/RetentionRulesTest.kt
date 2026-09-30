package app.piyokey.core.domain

import app.piyokey.core.domain.TestSupport.day
import app.piyokey.core.hangul.JamoDecomposer
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class RetentionRulesTest {
  @Test
  fun jstDayChangesExactlyAtTokyoMidnight() {
    assertEquals("2026-07-19", JstDay.of(Instant.parse("2026-07-19T14:59:59Z")).rawValue)
    assertEquals("2026-07-20", JstDay.of(Instant.parse("2026-07-19T15:00:00Z")).rawValue)
    assertNull(JstDay.parse("2026-02-30"))
    assertNull(JstDay.parse("2026-7-19"))
    assertEquals(day("2026-07-20"), day("2026-07-19").adding(1))
  }

  @Test
  fun jstDaySerializesAsRawStringAndRejectsInvalidDays() {
    val json = Json.encodeToString(JstDaySerializer, day("2026-07-19"))
    assertEquals("\"2026-07-19\"", json)
    assertFailsWith<Exception> { Json.decodeFromString(JstDaySerializer, "\"2026-13-01\"") }
  }

  @Test
  fun activitiesAreIdempotentAndSortedInOneDailyStamp() {
    val today = day("2026-07-19")
    var snapshot = RetentionSnapshot.EMPTY
    val (s1, m1) = RetentionRules.record(snapshot, RetentionActivityKind.GAME, today)
    snapshot = s1
    val (s2, m2) = RetentionRules.record(snapshot, RetentionActivityKind.GAME, today)
    val (s3, m3) = RetentionRules.record(s2, RetentionActivityKind.CURRICULUM, today)

    assertEquals(RetentionMutation.INSERTED, m1)
    assertEquals(RetentionMutation.UNCHANGED, m2)
    assertEquals(RetentionMutation.INSERTED, m3)
    val record = assertNotNull(s3.records[today])
    assertEquals(listOf(RetentionActivityKind.CURRICULUM, RetentionActivityKind.GAME), record.activities)
    assertTrue(record.isStamped)
    assertFalse(record.completedDailyChallenge)
  }

  @Test
  fun quickPracticeStampsWithoutCompletingDailyChallenge() {
    val today = day("2026-07-19")
    val (snapshot, _) = RetentionRules.record(RetentionSnapshot.EMPTY, RetentionActivityKind.QUICK_PRACTICE, today)
    val record = assertNotNull(snapshot.records[today])
    assertTrue(record.isStamped)
    assertFalse(record.completedDailyChallenge)
  }

  @Test
  fun validationRejectsDuplicateDaysAndEmptyActivities() {
    val today = day("2026-07-19")
    assertFailsWith<RetentionStoreException.InvalidRecord> {
      RetentionRules.validated(
        listOf(
          RetentionDayRecord(today, listOf(RetentionActivityKind.GAME)),
          RetentionDayRecord(today, listOf(RetentionActivityKind.CURRICULUM)),
        ),
      )
    }
    assertFailsWith<RetentionStoreException.InvalidRecord> { RetentionRules.validated(listOf(RetentionDayRecord(today, emptyList()))) }
    val records = Json.decodeFromString(
      ListSerializer(RetentionDayRecord.serializer()),
      """[{"day":"2026-07-19","activities":["daily_challenge","game"]}]""",
    )
    assertTrue(RetentionRules.validated(records).records.getValue(today).completedDailyChallenge)
  }

  @Test
  fun currentStreakKeepsYesterdayUntilTodayEndsAndFindsLongestRun() {
    val today = day("2026-07-20")
    val days = setOf(today.adding(-5), today.adding(-4), today.adding(-3), today.adding(-1))
    assertEquals(RetentionStreak(1, 3), RetentionStreak.calculate(days, today))
    assertEquals(RetentionStreak(2, 3), RetentionStreak.calculate(days + today, today))
    assertEquals(0, RetentionStreak.calculate(days, today.adding(1)).current)
  }

  @Test
  fun stampCardAlwaysRunsFromMondayThroughSunday() {
    val expected = listOf("2026-07-20", "2026-07-21", "2026-07-22", "2026-07-23", "2026-07-24", "2026-07-25", "2026-07-26")
    for (raw in listOf("2026-07-20", "2026-07-23", "2026-07-26")) {
      assertEquals(expected, RetentionCalendar.stampCardDays(day(raw)).map { it.rawValue })
    }
    assertEquals(
      listOf("2025-12-29", "2025-12-30", "2025-12-31", "2026-01-01", "2026-01-02", "2026-01-03", "2026-01-04"),
      RetentionCalendar.stampCardDays(day("2026-01-01")).map { it.rawValue },
    )
  }

  @Test
  fun stampDayStateSeparatesMissedTodayAndUpcomingDays() {
    val today = day("2026-07-23")
    assertEquals(RetentionStampDayState.COMPLETED, RetentionStampDayState.of(today.adding(-3), today, true))
    assertEquals(RetentionStampDayState.MISSED, RetentionStampDayState.of(today.adding(-1), today, false))
    assertEquals(RetentionStampDayState.TODAY_PENDING, RetentionStampDayState.of(today, today, false))
    assertEquals(RetentionStampDayState.UPCOMING, RetentionStampDayState.of(today.adding(1), today, false))
  }

  @Test
  fun stampRewardsUnlockAtThreeFiveAndSevenDaysOfTheWeek() {
    assertEquals(listOf(3, 5, 7), StampReward.entries.map { it.requiredDays })
    assertEquals(listOf("lightstick", "ribbon", "headphones"), StampReward.entries.map { it.propRaw })
    val today = day("2026-07-23")
    val stamped = setOf(day("2026-07-20"), day("2026-07-21"), day("2026-07-23"), day("2026-07-19"))
    val count = StampReward.stampedDaysThisWeek(today) { it in stamped }
    assertEquals(3, count)
    assertTrue(StampReward.THREE.isEarned(count))
    assertFalse(StampReward.FIVE.isEarned(count))
    assertEquals("retention.rewards.5.condition", StampReward.FIVE.conditionKey)
  }

  @Test
  fun dailyChallengeIsDeterministicUniqueAndChangesNextDay() {
    val today = day("2026-07-19")
    val challenge = DailyChallengeCatalog.challenge(today)
    assertEquals(challenge, DailyChallengeCatalog.challenge(today))
    assertEquals(5, challenge.items.size)
    assertEquals(5, challenge.items.map { it.id }.toSet().size)
    assertNotEquals(challenge.items.map { it.id }, DailyChallengeCatalog.challenge(today.adding(1)).items.map { it.id })
    for (item in challenge.items) assertTrue(JamoDecomposer.keySequence(item.ko).isNotEmpty())
  }

  @Test
  fun dailyChallengeUsesStrideThreeFromOrdinal() {
    val today = day("2026-07-19")
    val pool = CurriculumCatalog.stage("chapter_5_words")!!.items
    val start = Math.floorMod(today.ordinal, pool.size)
    assertEquals(
      (0 until 5).map { pool[(start + it * 3) % pool.size].id },
      DailyChallengeCatalog.challenge(today).items.map { it.id },
    )
  }

  @Test
  fun dailyChallengeUsesTheGoalSpecificCurriculumPool() {
    val today = day("2026-07-19")
    val expectations = mapOf(
      OnboardingGoal.KEYBOARD to "chapter_3_syllable_building",
      OnboardingGoal.TRAVEL to "chapter_6_sentences",
      OnboardingGoal.TOPIK to "chapter_5_words",
      OnboardingGoal.TRENDS to "chapter_6_sentences",
    )
    for ((goal, stageId) in expectations) {
      val allowed = CurriculumCatalog.stage(stageId)!!.items.map { it.id }.toSet()
      val challenge = DailyChallengeCatalog.challenge(today, goal)
      assertEquals(5, challenge.items.size)
      assertTrue(challenge.items.all { it.id in allowed }, "$goal must use $stageId")
    }
  }

  @Test
  fun randomWordPracticePrefersDownloadedGoalTagsAndAvoidsRecentWords() {
    val preferred = TestSupport.wordDeck("preferred", listOf("TOPIK"), listOf("가방", "학교", "친구", "공부", "시험", "교실"))
    val other = TestSupport.wordDeck("other", listOf("日常"), listOf("날씨", "주말", "가족", "회사", "식사", "사진"))
    repeat(20) { seed ->
      val session = assertNotNull(
        RandomWordPracticeCatalog.makeSession(
          installedDecks = listOf(other, preferred),
          fallbackDecks = emptyList(),
          preferredTags = listOf("TOPIK"),
          recentWordKeys = listOf("가방"),
          random = Random(seed),
        ),
      )
      assertEquals(5, session.sources.size)
      assertEquals(5, session.wordKeys.toSet().size)
      assertTrue(session.sources.all { it.sourceDeckId == preferred.deckId })
      assertFalse("가방" in session.wordKeys)
    }
  }

  @Test
  fun randomWordPracticeFillsFromFallbackAndFiltersPhrasesAndDuplicates() {
    val installed = TestSupport.wordDeck("installed", listOf("日常"), listOf("학교", "학교", "두 단어"))
    val fallback = TestSupport.wordDeck("fallback", listOf("TOPIK"), listOf("가방", "친구", "공부", "시험", "교실", "선생님"))
    val session = assertNotNull(
      RandomWordPracticeCatalog.makeSession(listOf(installed), listOf(fallback), emptyList(), emptyList(), random = Random(11)),
    )
    assertEquals(5, session.wordKeys.toSet().size)
    assertEquals(1, session.wordKeys.count { it == "학교" })
    assertFalse("두 단어" in session.wordKeys)
    assertTrue(session.sources.any { it.sourceDeckId == fallback.deckId })
  }

  @Test
  fun randomWordPracticeUsesRecentWordsOnlyWhenNeededAndSkipsUnofficialDecks() {
    val deck = TestSupport.wordDeck("deck", emptyList(), listOf("가방", "학교", "친구", "공부", "시험"))
    val session = assertNotNull(
      RandomWordPracticeCatalog.makeSession(listOf(deck), emptyList(), emptyList(), listOf("가방", "학교"), random = Random(3)),
    )
    assertEquals(setOf("가방", "학교", "친구", "공부", "시험"), session.wordKeys.toSet())
    assertEquals(setOf("친구", "공부", "시험"), session.wordKeys.take(3).toSet())

    val unofficial = TestSupport.wordDeck("mine", emptyList(), listOf("가방", "학교", "친구", "공부", "시험"), official = false)
    assertNull(RandomWordPracticeCatalog.makeSession(listOf(unofficial), emptyList(), emptyList(), emptyList()))
    assertEquals("official_topik_one", RandomWordPracticeCatalog.fallbackDeckId(null))
    assertEquals("official_trending_korean", RandomWordPracticeCatalog.fallbackDeckId(OnboardingGoal.TRENDS))
  }

  @Test
  fun randomWordPracticeHistoryKeepsLatestTwentyUniqueWords() {
    var recent = emptyList<String>()
    for (index in 0 until 5) {
      val sources = (0 until 5).map { offset ->
        RandomWordPracticeSource(TestSupport.item("$index-$offset", "단어${index * 5 + offset}"), "deck", emptyList())
      }
      recent = RandomWordPracticeHistoryRules.recorded(recent, RandomWordPracticeSession(sources))
    }
    assertEquals(20, recent.size)
    assertFalse("단어0" in recent)
    assertEquals("단어24", recent.last())
  }

  @Test
  fun dailyMascotEncouragementUsesEveryMessageAcrossContexts() {
    val today = day("2026-07-24")
    val indices = MascotDailyEncouragement.Context.entries.flatMap(MascotDailyEncouragement::messageIndices)
    assertEquals((0 until MascotDailyEncouragement.MESSAGE_COUNT).toList(), indices.sorted())
    assertEquals(
      MascotDailyEncouragement.localizationKey(today, emptySet()),
      MascotDailyEncouragement.localizationKey(today.adding(4), emptySet()),
    )
    assertTrue(MascotDailyEncouragement.localizationKey(today, emptySet()).startsWith("mascot.daily_encouragement."))
  }

  @Test
  fun dailyMascotEncouragementRespondsToLearningContext() {
    val today = day("2026-07-24")
    assertEquals(MascotDailyEncouragement.Context.BEFORE_STUDY, MascotDailyEncouragement.context(today, emptySet()))
    assertEquals(MascotDailyEncouragement.Context.COMPLETED_TODAY, MascotDailyEncouragement.context(today, setOf(today)))
    assertEquals(
      MascotDailyEncouragement.Context.ACTIVE_STREAK,
      MascotDailyEncouragement.context(today, setOf(today.adding(-1), today.adding(-2))),
    )
    assertEquals(MascotDailyEncouragement.Context.RETURNING, MascotDailyEncouragement.context(today, setOf(today.adding(-2))))
  }
}
