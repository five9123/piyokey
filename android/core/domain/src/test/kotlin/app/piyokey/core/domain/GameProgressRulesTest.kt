package app.piyokey.core.domain

import app.piyokey.core.domain.TestSupport.at
import app.piyokey.core.domain.TestSupport.jstNoon
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class GameProgressRulesTest {
  private fun record(
    score: Int,
    accuracy: Double,
    playedAt: Instant,
    inputMode: SessionInputMode = SessionInputMode.BUILT_IN,
    course: String = "word",
    cpm: Double = 84.0,
    duration: Double = 60.0,
    completed: Int = 8,
    deckVersion: Int? = null,
    competition: GameCompetition? = null,
    mode: SessionMode = SessionMode.GAME,
    deckId: String = "official_daily_words",
    maxCombo: Int = 4,
  ) = GameRecord(
    id = GameRecord.newId(), mode = mode, deckId = deckId, deckVersion = deckVersion, competition = competition,
    course = course, score = score, maxCombo = maxCombo, accuracy = accuracy, charactersPerMinute = cpm,
    activeDuration = duration, completedItemCount = completed, missedItemCount = 2, inputMode = inputMode, playedAt = playedAt,
  )

  private fun GameProgressSnapshot.add(record: GameRecord) = GameProgressRules.applyGameRecord(record, this)

  @Test
  fun firstRecordIsNewBestAndLowerScoreKeepsBest() {
    val (s1, first) = GameProgressSnapshot.EMPTY.add(record(700, 80.0, at(1)))
    assertTrue(first.isNewBest)
    assertNull(first.previousBestScore)
    assertEquals(1, first.deckProgress.plays)

    val (s2, lower) = s1.add(record(500, 95.0, at(2)))
    assertFalse(lower.isNewBest)
    assertEquals(700, lower.previousBestScore)
    assertEquals(2, lower.deckProgress.plays)
    assertEquals(700, lower.deckProgress.bestScore)
    assertEquals(95.0, lower.deckProgress.bestAccuracy)
    assertEquals(at(2), lower.deckProgress.lastPlayedAt)

    val (_, equal) = s2.add(record(700, 90.0, at(3)))
    assertFalse(equal.isNewBest)
    val (_, higher) = s2.add(record(900, 88.0, at(3)))
    assertTrue(higher.isNewBest)
  }

  @Test
  fun bestScoresAreSeparatedByInputModeAndGameKind() {
    var snapshot = GameProgressSnapshot.EMPTY
    snapshot = snapshot.add(record(700, 90.0, at(1))).first
    snapshot = snapshot.add(record(900, 95.0, at(2), inputMode = SessionInputMode.OS_IME)).first
    snapshot = snapshot.add(record(800, 92.0, at(3), inputMode = SessionInputMode.BUILT_IN_KOREAN_10KEY)).first
    snapshot = snapshot.add(record(1_100, 100.0, at(4), course = "choseong")).first
    snapshot = snapshot.add(record(1_250, 98.0, at(5), course = "dictation")).first
    fun best(kind: GameKind = GameKind.FLOW, mode: SessionInputMode = SessionInputMode.BUILT_IN) =
      snapshot.deckProgress[GameProgressRules.progressKey("official_daily_words", kind, mode)]?.bestScore
    assertEquals(700, best())
    assertEquals(900, best(mode = SessionInputMode.OS_IME))
    assertEquals(800, best(mode = SessionInputMode.BUILT_IN_KOREAN_10KEY))
    assertEquals(1_100, best(GameKind.CHOSEONG))
    assertEquals(1_250, best(GameKind.DICTATION))
  }

  @Test
  fun lessonRecordIsLoggedWithoutChangingGameBest() {
    val (s1, _) = GameProgressSnapshot.EMPTY.add(record(700, 90.0, at(1)))
    val lesson = record(12, 95.0, at(2), course = "practice", mode = SessionMode.LESSON)
    val s2 = GameProgressRules.applyLessonRecord(lesson, s1)
    assertEquals(2, s2.records.size)
    assertEquals(1, s2.deckProgress.size)
    assertFailsWith<GameProgressStoreException.InvalidProgress> {
      GameProgressRules.applyLessonRecord(record(1, 90.0, at(3)), s1)
    }
  }

  @Test
  fun nonFiniteMetricsAreRejected() {
    assertFailsWith<GameProgressStoreException.InvalidProgress> {
      GameProgressSnapshot.EMPTY.add(record(10, 90.0, at(1), cpm = Double.POSITIVE_INFINITY))
    }
    assertFailsWith<GameProgressStoreException.InvalidProgress> {
      GameProgressSnapshot.EMPTY.add(record(10, 101.0, at(1)))
    }
  }

  @Test
  fun cupAndOrdinaryFlowHaveIndependentBests() {
    val deck = GameProgressRules.PIYO_CUP_DECK_ID
    var s = GameProgressSnapshot.EMPTY
    s = s.add(record(700, 90.0, at(1), competition = GameCompetition.OFFICIAL_DECK, deckId = deck)).first
    val (s2, cup) = s.add(record(900, 99.0, at(2), competition = GameCompetition.WEEKLY_PIYO_CUP, deckId = deck))
    val (s3, flow) = s2.add(record(800, 91.0, at(3), deckId = deck))
    val (s4, lowerCup) = s3.add(record(850, 95.0, at(4), competition = GameCompetition.WEEKLY_PIYO_CUP, deckId = deck))

    assertTrue(cup.isNewBest)
    assertNull(cup.previousBestScore)
    assertTrue(flow.isNewBest)
    assertEquals(700, flow.previousBestScore)
    assertEquals(2, flow.deckProgress.plays)
    assertFalse(lowerCup.isNewBest)
    assertEquals(900, lowerCup.previousBestScore)
    assertEquals(99.0, lowerCup.deckProgress.bestAccuracy)
    assertEquals(800, s4.deckProgress[GameProgressRules.progressKey(deck, inputMode = SessionInputMode.BUILT_IN)]?.bestScore)
    assertEquals(
      900,
      s4.deckProgress[GameProgressRules.progressKey(deck, inputMode = SessionInputMode.BUILT_IN, competition = GameCompetition.WEEKLY_PIYO_CUP)]?.bestScore,
    )
    assertNull(flow.deckProgress.competition)
  }

  @Test
  fun bestComboIsScopedByCompetitionInputAndMode() {
    val deck = GameProgressRules.PIYO_CUP_DECK_ID
    val records = listOf(
      record(700, 90.0, at(1), deckId = deck, maxCombo = 7, competition = GameCompetition.OFFICIAL_DECK),
      record(900, 90.0, at(2), deckId = deck, maxCombo = 9, competition = GameCompetition.WEEKLY_PIYO_CUP),
      record(1_100, 90.0, at(3), deckId = deck, maxCombo = 11, inputMode = SessionInputMode.OS_IME),
      record(9_999, 100.0, at(4), deckId = deck, maxCombo = 999, mode = SessionMode.LESSON),
    )
    assertEquals(7, GameProgressRules.bestCombo(records, deck))
    assertEquals(9, GameProgressRules.bestCombo(records, deck, competition = GameCompetition.WEEKLY_PIYO_CUP))
    assertEquals(11, GameProgressRules.bestCombo(records, deck, inputMode = SessionInputMode.OS_IME))
  }

  @Test
  fun compactionKeepsLeaderboardRepresentativesAndSummaries() {
    val reference = jstNoon(2026, 8, 11)
    val classicBest = record(
      9_999, 100.0, reference.minusSeconds(86_400), deckId = "flow_topik_beginner", deckVersion = 3,
      competition = GameCompetition.OFFICIAL_DECK,
    )
    val weeklyBest = record(
      18_888, 99.0, reference, deckId = "flow_topik_beginner", deckVersion = 3, competition = GameCompetition.WEEKLY_PIYO_CUP,
    )
    val oldWeekly = record(
      20_000, 99.0, reference.minusSeconds(86_400 * 14), deckId = "flow_topik_beginner", deckVersion = 3,
      competition = GameCompetition.WEEKLY_PIYO_CUP,
    )
    val progress = DeckProgress(deckId = "downloaded-deck", plays = 300, bestScore = 7_777, bestAccuracy = 98.0, lastPlayedAt = reference)
    val recent = (0 until 300).map { record(it, 90.0, reference.plusSeconds(it + 1L), deckId = "downloaded-deck") }
    val snapshot = GameProgressSnapshot(listOf(oldWeekly, classicBest, weeklyBest) + recent, mapOf(progress.id to progress))
    val boards: (GameRecord) -> List<LeaderboardRef> = { r ->
      when {
        r.deckId != "flow_topik_beginner" || r.deckVersion != 3 -> emptyList()
        r.competition == GameCompetition.WEEKLY_PIYO_CUP -> listOf(LeaderboardRef("cup", true))
        else -> listOf(LeaderboardRef("flow_beginner", false))
      }
    }

    val compacted = GameProgressRules.compacted(snapshot, reference, limit = 128, leaderboards = boards)

    assertEquals(128, compacted.records.size)
    assertTrue(compacted.records.any { it.id == classicBest.id })
    assertTrue(compacted.records.any { it.id == weeklyBest.id })
    assertFalse(compacted.records.any { it.id == oldWeekly.id })
    assertEquals(progress, compacted.deckProgress[progress.id])
    assertEquals(snapshot, GameProgressRules.compacted(snapshot, reference, limit = 1_000, leaderboards = boards))
  }

  @Test
  fun legacyInputModesDecodeAndUnscopedProgressDefaultsToBuiltInFlow() {
    val json = Json { ignoreUnknownKeys = true }
    val decoded = json.decodeFromString(
      GameRecord.serializer(),
      """{"id":"00000000-0000-0000-0000-000000000001","mode":"game","deck_id":"d","course":"word","score":1,
        |"max_combo":1,"accuracy":90,"characters_per_minute":84,"active_duration":60,"completed_item_count":1,
        |"missed_item_count":0,"input_mode":"built_in","played_at":"1970-01-01T00:00:01Z"}""".trimMargin(),
    )
    assertEquals(SessionInputMode.BUILT_IN, decoded.inputMode)
    assertNull(decoded.deckVersion)
    assertEquals(SessionInputMode.OS_IME, json.decodeFromString(SessionInputModeSerializer, "\"os_keyboard\""))
    val progress = json.decodeFromString(
      DeckProgress.serializer(),
      """{"deck_id":"d","plays":1,"best_score":700,"best_accuracy":92.5,"last_played_at":"1970-01-01T00:00:01Z"}""",
    )
    assertEquals("d::flow::builtin", progress.id)
    val official = json.decodeFromString(
      DeckProgress.serializer(),
      """{"deck_id":"d","competition":"official_deck_v1","plays":1,"best_score":700,"best_accuracy":92.5,"last_played_at":"1970-01-01T00:00:01Z"}""",
    )
    assertNull(GameProgressRules.validatedProgress(listOf(official)).values.single().competition)
  }

  @Test
  fun legacyMixedSummariesArePreservedAndRebuiltFromAttributedRecords() {
    val deck = GameProgressRules.PIYO_CUP_DECK_ID
    val flow = record(400, 90.0, at(1), competition = GameCompetition.OFFICIAL_DECK, deckId = deck)
    val cup = record(900, 99.0, at(2), competition = GameCompetition.WEEKLY_PIYO_CUP, deckId = deck)
    val mixed = DeckProgress(deckId = deck, plays = 200, bestScore = 9_999, bestAccuracy = 100.0, lastPlayedAt = at(2))

    val migrated = GameProgressRules.validatedSnapshot(2, listOf(cup, flow), listOf(mixed), null)
    val cupKey = GameProgressRules.progressKey(deck, inputMode = SessionInputMode.BUILT_IN, competition = GameCompetition.WEEKLY_PIYO_CUP)

    assertEquals(listOf(cup, flow), migrated.records)
    assertEquals(mixed, migrated.legacyMixedProgress[mixed.id])
    assertEquals(400, migrated.deckProgress[mixed.id]?.bestScore)
    assertEquals(1, migrated.deckProgress[mixed.id]?.plays)
    assertEquals(900, migrated.deckProgress[cupKey]?.bestScore)
    assertEquals(at(2), migrated.deckProgress[cupKey]?.lastPlayedAt)
    // Schema 3 is taken as-is.
    assertEquals(mixed, GameProgressRules.validatedSnapshot(3, emptyList(), listOf(mixed), null).deckProgress[mixed.id])
  }

  @Test
  fun compactedLegacyCupCannotBeMisattributedAndUnaffectedModesSurvive() {
    val deck = GameProgressRules.PIYO_CUP_DECK_ID
    val mixed = DeckProgress(deckId = deck, plays = 200, bestScore = 9_999, bestAccuracy = 100.0, lastPlayedAt = at(2))
    val migrated = GameProgressRules.validatedSnapshot(1, emptyList(), listOf(mixed), null)
    assertTrue(migrated.deckProgress.isEmpty())
    assertEquals(mixed, migrated.legacyMixedProgress[mixed.id])

    val tenKey = DeckProgress(deckId = deck, inputMode = SessionInputMode.BUILT_IN_KOREAN_10KEY, plays = 10, bestScore = 800, bestAccuracy = 95.0, lastPlayedAt = at(2))
    val other = DeckProgress(deckId = "flow_topik_intermediate", plays = 10, bestScore = 900, bestAccuracy = 96.0, lastPlayedAt = at(2))
    val osMixed = DeckProgress(deckId = deck, inputMode = SessionInputMode.OS_IME, plays = 20, bestScore = 1_000, bestAccuracy = 98.0, lastPlayedAt = at(2))
    val result = GameProgressRules.validatedSnapshot(
      2,
      listOf(
        record(500, 90.0, at(1), inputMode = SessionInputMode.OS_IME, deckId = deck),
        record(1_000, 98.0, at(2), inputMode = SessionInputMode.OS_IME, competition = GameCompetition.WEEKLY_PIYO_CUP, deckId = deck),
      ),
      listOf(tenKey, other, osMixed),
      null,
    )
    assertEquals(tenKey, result.deckProgress[tenKey.id])
    assertEquals(other, result.deckProgress[other.id])
    assertEquals(mapOf(osMixed.id to osMixed), result.legacyMixedProgress)
    assertEquals(500, result.deckProgress[osMixed.id]?.bestScore)
  }

  @Test
  fun unsupportedSchemaAndDuplicateRecordsAreRejected() {
    assertEquals(
      GameProgressStoreException.UnsupportedSchema(999),
      assertFailsWith<GameProgressStoreException.UnsupportedSchema> {
        GameProgressRules.validatedSnapshot(999, emptyList(), emptyList(), null)
      },
    )
    val r = record(1, 90.0, at(1))
    assertFailsWith<GameProgressStoreException.InvalidProgress> {
      GameProgressRules.validatedSnapshot(3, listOf(r, r), emptyList(), null)
    }
  }

  @Test
  fun learningInsightsAggregatePeriodTrendReviewAndWeakJamo() {
    val today = jstNoon(2026, 7, 20)
    val records = listOf(
      record(50, 90.0, today, cpm = 60.0, duration = 60.0, completed = 5),
      record(80, 100.0, jstNoon(2026, 7, 19), cpm = 120.0, duration = 120.0, completed = 7, mode = SessionMode.LESSON),
      record(30, 80.0, jstNoon(2026, 7, 10), cpm = 50.0, duration = 60.0, completed = 3),
    )
    val activityDay = JstDay.of(jstNoon(2026, 7, 18))
    val retention = mapOf(activityDay to RetentionDayRecord(activityDay, listOf(RetentionActivityKind.CURRICULUM)))
    val item = TestSupport.item("school", "학교", "ハッキョ", "学校")
    val active = ReviewDeckItem.of(item, "deck-a", today, 2)
    val graduated = ReviewDeckItem.of(item, "deck-b", jstNoon(2026, 7, 1), 1).copy(graduatedAt = jstNoon(2026, 7, 19))

    val insights = LearningInsights.make(
      LearningInsightPeriod.WEEK, today, records, retention, listOf(active, graduated),
      mapOf("ㄱ" to 3, "ㅏ" to 5, "ㅂ" to 1, "ㅈ" to 0),
    )

    assertEquals(7, insights.dailyActivities.size)
    assertEquals(3, insights.activeDays)
    assertEquals(2, insights.sessionCount)
    assertEquals(180.0, insights.totalActiveDuration, 0.001)
    assertEquals(95.0, assertNotNull(insights.averageAccuracy), 0.001)
    assertEquals(100.0, assertNotNull(insights.averageCharactersPerMinute), 0.001)
    assertEquals(15.0, assertNotNull(insights.accuracyChange), 0.001)
    assertEquals(50.0, assertNotNull(insights.speedChange), 0.001)
    assertEquals(12, insights.completedItemCount)
    assertEquals(1, insights.activeReviewCount)
    assertEquals(1, insights.addedReviewCount)
    assertEquals(1, insights.graduatedReviewCount)
    assertEquals(listOf("ㅏ", "ㄱ", "ㅂ"), insights.weakJamo.map { it.jamo })
  }

  @Test
  fun learningInsightsLargeFixtureBucketsMonth() {
    val today = jstNoon(2026, 8, 11)
    val records = (0 until 10_000).map { record(it, 90.0, today.minusSeconds((it % 60) * 86_400L), cpm = 80.0, completed = 1) }
    val insights = LearningInsights.make(LearningInsightPeriod.MONTH, today, records, emptyMap(), emptyList(), emptyMap())
    assertEquals(30, insights.dailyActivities.size)
    assertTrue(insights.sessionCount > 0)
    assertEquals(null, LearningInsights.make(LearningInsightPeriod.WEEK, today, emptyList(), emptyMap(), emptyList(), emptyMap()).averageAccuracy)
  }

  @Test
  fun rankTuningRanksAndValidates() {
    val tuning = FlowGameRankTuningLoader.decode(java.io.File(TestSupport.sharedRoot, "tuning/game_rank_tuning.json").readBytes())
    assertEquals(FlowGameRankTuning(), tuning)
    assertEquals("S", tuning.rank(100.0, 120.0))
    assertEquals("A", tuning.rank(100.0, 60.0))
    assertEquals("B", tuning.rank(80.0, 30.0))
    assertEquals("C", tuning.rank(40.0, 0.0))
    assertEquals("S", tuning.rank(150.0, 1_000.0))

    fun data(aw: Double = 0.6, sw: Double = 0.4, s: Double = 90.0, a: Double = 75.0, b: Double = 55.0) =
      """{"schema_version":1,"accuracy_weight":$aw,"speed_weight":$sw,"speed_cap_characters_per_minute":120,
        |"s_threshold":$s,"a_threshold":$a,"b_threshold":$b}""".trimMargin().toByteArray()
    assertFailsWith<FlowGameRankTuningException.InvalidWeights> { FlowGameRankTuningLoader.decode(data(sw = 0.5)) }
    assertFailsWith<FlowGameRankTuningException.InvalidThresholds> { FlowGameRankTuningLoader.decode(data(s = 75.0, a = 90.0)) }
    assertFailsWith<FlowGameRankTuningException.UnsupportedSchema> {
      FlowGameRankTuningLoader.decode(String(data()).replace("\"schema_version\":1", "\"schema_version\":2").toByteArray())
    }
  }
}
