package app.piyokey.android.data.progress

import app.piyokey.android.data.DataTestSupport
import app.piyokey.android.data.DataTestSupport.at
import app.piyokey.android.data.persistence.RecoverableJsonFile
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckAuthor
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.DeckType
import app.piyokey.core.domain.CurriculumProgressMutation
import app.piyokey.core.domain.CurriculumProgressStoreException
import app.piyokey.core.domain.DeckProgress
import app.piyokey.core.domain.GameCompetition
import app.piyokey.core.domain.GameKind
import app.piyokey.core.domain.GameProgressRules
import app.piyokey.core.domain.GameProgressStoreException
import app.piyokey.core.domain.GameRecord
import app.piyokey.core.domain.HatchOnboardingPolicy
import app.piyokey.core.domain.JstDay
import app.piyokey.core.domain.PracticeSessionCheckpoint
import app.piyokey.core.domain.RetentionActivityKind
import app.piyokey.core.domain.RetentionMutation
import app.piyokey.core.domain.RetentionStoreException
import app.piyokey.core.domain.ReviewDeckMutation
import app.piyokey.core.domain.ReviewDeckStoreException
import app.piyokey.core.domain.SessionInputMode
import app.piyokey.core.domain.SessionMode
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressStoresTest {
  private val base = DataTestSupport.tempDir()

  private fun TestScope.testScope() = CoroutineScope(StandardTestDispatcher(testScheduler))
  private fun TestScope.io() = StandardTestDispatcher(testScheduler)

  private fun record(
    score: Int,
    playedAt: Instant,
    deckId: String = "official_daily_words",
    inputMode: SessionInputMode = SessionInputMode.BUILT_IN,
    competition: GameCompetition? = null,
    mode: SessionMode = SessionMode.GAME,
    maxCombo: Int = 4,
  ) = GameRecord(
    id = GameRecord.newId(), mode = mode, deckId = deckId, competition = competition, course = "word", score = score,
    maxCombo = maxCombo, accuracy = 90.0, charactersPerMinute = 84.0, activeDuration = 60.0, completedItemCount = 8,
    missedItemCount = 2, inputMode = inputMode, playedAt = playedAt,
  )

  // Game progress

  private val gameStore = GameProgressStore(File(base, "Progress"))
  private val gameFile = File(base, "Progress/game-progress.json")

  @Test
  fun gameStoreRoundTripsSchemaThreeAndRecoversFromBackup() {
    val first = record(700, at(1))
    val outcome = gameStore.append(first)
    assertTrue(outcome.isNewBest)
    assertEquals(listOf(first), gameStore.loadSnapshot().records)
    assertEquals(3, Json.parseToJsonElement(gameFile.readText()).jsonObject["schema_version"]!!.jsonPrimitive.int)

    val corrupted = Json.parseToJsonElement(gameFile.readText()).jsonObject.toMutableMap()
    val records = corrupted.getValue("records").jsonArray.map { it.jsonObject.toMutableMap() }
    records[0]["accuracy"] = JsonPrimitive(200)
    corrupted["records"] = JsonArray(records.map { JsonObject(it) })
    gameFile.writeText(JsonObject(corrupted).toString())
    assertEquals(listOf(first), gameStore.loadSnapshot().records)
    assertTrue(RecoverableJsonFile.corruptFile(gameFile).exists())
  }

  @Test
  fun gameStoreRejectsFutureSchemaAndPreservesFutureBackup() {
    gameFile.parentFile.mkdirs()
    gameFile.writeText("""{"schema_version":999,"records":[],"deck_progress":[]}""")
    assertEquals(999, assertFailsWith<GameProgressStoreException.UnsupportedSchema> { gameStore.loadSnapshot() }.version)
    gameFile.delete()
    RecoverableJsonFile.backupFile(gameFile).writeText("""{"schema_version":999,"records":[],"deck_progress":[]}""")
    assertFailsWith<GameProgressStoreException.UnsupportedSchema> { gameStore.loadSnapshot() }
    assertTrue(RecoverableJsonFile.backupFile(gameFile).exists())
    assertFalse(RecoverableJsonFile.corruptFile(RecoverableJsonFile.backupFile(gameFile)).exists())
  }

  @Test
  fun gameStoreMigratesLegacySchemaOneFile() {
    gameFile.parentFile.mkdirs()
    gameFile.writeText(
      """{"schema_version":1,"records":[{"id":"00000000-0000-0000-0000-000000000001","mode":"game","deck_id":"official_daily_words",
        |"course":"word","score":700,"max_combo":4,"accuracy":92.5,"characters_per_minute":84,"active_duration":60,
        |"completed_item_count":8,"missed_item_count":2,"input_mode":"built_in","played_at":"1970-01-01T00:00:01Z"}],
        |"deck_progress":[{"deck_id":"official_daily_words","plays":1,"best_score":700,"best_accuracy":92.5,
        |"last_played_at":"1970-01-01T00:00:01Z"}]}""".trimMargin(),
    )
    val snapshot = gameStore.loadSnapshot()
    assertEquals(SessionInputMode.BUILT_IN, snapshot.records.single().inputMode)
    assertEquals(700, snapshot.deckProgress[GameProgressRules.progressKey("official_daily_words", inputMode = SessionInputMode.BUILT_IN)]?.bestScore)
    assertTrue(RecoverableJsonFile.backupFile(gameFile).exists())
  }

  @Test
  fun gameStoreRejectsInvalidMetricsBeforeWriting() {
    assertFailsWith<GameProgressStoreException.InvalidProgress> { gameStore.append(record(10, at(1)).copy(charactersPerMinute = Double.POSITIVE_INFINITY)) }
    assertFalse(gameFile.exists())
  }

  @Test
  fun gameLibraryAppendIsDurableAfterFlushAndSeparatesCupCombos() = runTest {
    val library = GameProgressLibrary(gameStore, testScope(), io())
    val deck = GameProgressRules.PIYO_CUP_DECK_ID
    val scopes = listOf(
      Triple(SessionInputMode.BUILT_IN, GameCompetition.OFFICIAL_DECK, 700 to 7),
      Triple(SessionInputMode.BUILT_IN, GameCompetition.WEEKLY_PIYO_CUP, 900 to 9),
      Triple(SessionInputMode.OS_IME, GameCompetition.OFFICIAL_DECK, 1_100 to 11),
      Triple(SessionInputMode.OS_IME, GameCompetition.WEEKLY_PIYO_CUP, 1_300 to 13),
    )
    scopes.forEachIndexed { offset, (input, competition, pair) ->
      assertNotNull(library.append(record(pair.first, at(offset.toLong()), deck, input, competition, maxCombo = pair.second)))
    }
    assertTrue(library.appendLesson(record(9_999, at(5), deck, mode = SessionMode.LESSON, maxCombo = 999)))
    assertFalse(library.appendLesson(record(1, at(6), deck)))
    assertTrue(library.saveFailed.value)
    assertTrue(library.flushAndWait())
    assertFalse(library.saveFailed.value)

    val reloaded = GameProgressLibrary(gameStore, testScope(), io())
    assertEquals(1_100, reloaded.progress(deck, inputMode = SessionInputMode.OS_IME)?.bestScore)
    assertEquals(1_300, reloaded.progress(deck, inputMode = SessionInputMode.OS_IME, competition = GameCompetition.WEEKLY_PIYO_CUP)?.bestScore)
    assertEquals(7, reloaded.bestCombo(deck))
    assertEquals(9, reloaded.bestCombo(deck, competition = GameCompetition.WEEKLY_PIYO_CUP))
    assertEquals(11, reloaded.bestCombo(deck, inputMode = SessionInputMode.OS_IME))
    assertEquals(13, reloaded.bestCombo(deck, GameKind.FLOW, SessionInputMode.OS_IME, GameCompetition.WEEKLY_PIYO_CUP))
    assertEquals(5, reloaded.records.value.size)
  }

  @Test
  fun gameLibraryPersistsLegacyMigrationAndKeepsMixedSummaries() = runTest {
    val deck = GameProgressRules.PIYO_CUP_DECK_ID
    val mixed = DeckProgress(deckId = deck, plays = 200, bestScore = 9_999, bestAccuracy = 100.0, lastPlayedAt = at(2))
    gameFile.parentFile.mkdirs()
    gameFile.writeText(
      """{"schema_version":2,"records":[],"deck_progress":[${Json.encodeToString(DeckProgress.serializer(), mixed)}]}""",
    )
    val library = GameProgressLibrary(gameStore, testScope(), io())
    assertNull(library.progress(deck))
    assertTrue(library.flushAndWait())
    assertEquals(3, Json.parseToJsonElement(gameFile.readText()).jsonObject["schema_version"]!!.jsonPrimitive.int)
    val outcome = assertNotNull(library.append(record(500, at(3), deck)))
    assertTrue(outcome.isNewBest)
    assertNull(outcome.previousBestScore)
    assertTrue(library.flushAndWait())
    assertEquals(mixed, gameStore.loadSnapshot().legacyMixedProgress[mixed.id])
  }

  @Test
  fun backgroundWriteNeverDowngradesAFutureSchema() = runTest {
    val library = GameProgressLibrary(gameStore, testScope(), io())
    gameFile.parentFile.mkdirs()
    val future = """{"schema_version":999,"records":[],"deck_progress":[]}"""
    gameFile.writeText(future)
    library.append(record(1, at(1)))
    assertFalse(library.flushAndWait())
    assertTrue(library.saveFailed.value)
    assertEquals(future, gameFile.readText())
  }

  // Curriculum

  private val curriculumStore = CurriculumProgressStore(File(base, "Curriculum"))
  private val curriculumFile = File(base, "Curriculum/curriculum-progress.json")
  private fun checkpoint(keys: String, duration: Double) = PracticeSessionCheckpoint(0, keys, 2, 1, listOf(0), duration)

  @Test
  fun curriculumStoreRoundTripsAndRecovers() {
    val saved = PracticeSessionCheckpoint(3, "ㄹ", 2, 1, listOf(0), 12.5)
    curriculumStore.saveActiveSession("stage", saved, at(5))
    assertEquals(saved, curriculumStore.loadSnapshot().activeSession?.checkpoint)
    assertEquals(CurriculumProgressMutation.CLEARED, curriculumStore.finishStage("stage", 0, 79.9))
    assertNull(curriculumStore.loadSnapshot().activeSession)
    assertEquals(CurriculumProgressMutation.UNCHANGED, curriculumStore.clearActiveSession())

    curriculumStore.finishStage("stage", 2, 91.0, at(1_000))
    curriculumFile.writeText(
      """{"schema_version":1,"stage_progress":[{"stage_id":"stage","stars":9,"best_accuracy":101,"completed_at":"1970-01-01T00:16:40Z"}]}""",
    )
    val recovered = curriculumStore.loadSnapshot().stageProgress.getValue("stage")
    assertEquals(2, recovered.stars)
    assertEquals(at(1_000), recovered.completedAt)
    assertTrue(RecoverableJsonFile.corruptFile(curriculumFile).exists())

    curriculumFile.writeText("""{"schema_version":99,"stage_progress":[]}""")
    assertEquals(99, assertFailsWith<CurriculumProgressStoreException.UnsupportedSchema> { curriculumStore.loadSnapshot() }.version)
  }

  @Test
  fun curriculumLibraryCoalescesCheckpointsUntilFlush() = runTest {
    val library = CurriculumProgressLibrary(curriculumStore, testScope(), io(), debounceMillis = 60_000)
    library.save("stage", checkpoint("ㄱ", 1.0))
    val latest = checkpoint("ㄱㅏ", 2.0)
    library.save("stage", latest)
    assertEquals(latest, library.checkpoint("stage"))
    assertNull(library.checkpoint("other"))
    assertFalse(curriculumFile.exists())
    assertTrue(library.flushAndWait())
    assertEquals(latest, curriculumStore.loadSnapshot().activeSession?.checkpoint)
  }

  @Test
  fun finishAndWaitRollsBackWhenFutureSchemaBlocksWrite() = runTest {
    curriculumFile.parentFile.mkdirs()
    val future = """{"schema_version":99,"stage_progress":[]}"""
    curriculumFile.writeText(future)
    val library = CurriculumProgressLibrary(curriculumStore, testScope(), io())
    assertFalse(library.finishAndWait("stage", 3, 100.0))
    assertTrue(library.saveFailed.value)
    assertFalse("stage" in library.completedStageIds)
    assertEquals(future, curriculumFile.readText())
  }

  @Test
  fun hatchCompletionsSurviveImmediateReload() = runTest {
    val required = HatchOnboardingPolicy.requiredStages
    curriculumStore.finishStage(required[0].id, 3, 100.0)
    val library = CurriculumProgressLibrary(curriculumStore, testScope(), io())
    assertTrue(library.finishAndWait(required[1].id, 3, 100.0))
    val relaunched = CurriculumProgressLibrary(curriculumStore, testScope(), io())
    assertEquals(required[2].id, HatchOnboardingPolicy.nextRequiredStage(relaunched.completedStageIds)?.id)
    assertTrue(relaunched.finishAndWait(required[2].id, 3, 100.0))
    assertTrue(HatchOnboardingPolicy.isComplete(CurriculumProgressLibrary(curriculumStore, testScope(), io()).completedStageIds))
  }

  @Test
  fun finishAndWaitVerifiesDurableFailedAttempt() = runTest {
    val library = CurriculumProgressLibrary(curriculumStore, testScope(), io())
    library.save("stage", checkpoint("ㄱ", 1.0))
    assertTrue(library.finishAndWait("stage", 0, 79.0))
    val relaunched = CurriculumProgressLibrary(curriculumStore, testScope(), io())
    assertFalse("stage" in relaunched.completedStageIds)
    assertNull(relaunched.activeSession.value)
  }

  // Review

  private val reviewStore = ReviewDeckStore(File(base, "Review"))
  private val reviewFile = File(base, "Review/review-deck.json")
  private val item = DeckItem(id = "item_company", ko = "회사", readingJa = "フェサ", meaningJa = "会社", audio = null)

  @Test
  fun reviewStorePersistsMistakesPerfectsAndRecovers() {
    assertEquals(ReviewDeckMutation.ADDED, reviewStore.recordMistake(item, "deck", at(1_000)))
    assertEquals(ReviewDeckMutation.UPDATED, reviewStore.recordPerfect(item.id, "deck"))
    assertEquals(ReviewDeckMutation.UPDATED, reviewStore.recordMistake(item, "deck"))
    val reloaded = ReviewDeckStore(File(base, "Review")).loadSnapshot().activeItems.single()
    assertEquals(2, reloaded.missCount)
    assertEquals(0, reloaded.consecutivePerfect)
    assertEquals(at(1_000), reloaded.addedAt)
    assertEquals(ReviewDeckMutation.UPDATED, reviewStore.markSourceUnavailable("deck"))
    assertEquals(ReviewDeckMutation.UNCHANGED, reviewStore.markSourceUnavailable("deck"))
    assertTrue(reviewStore.loadSnapshot().activeItems.isEmpty())
    assertEquals(false, reviewStore.loadSnapshot().items["deck::item_company"]?.isSourceAvailable)

    reviewFile.writeText("{")
    assertEquals(2, reviewStore.loadSnapshot().items.values.single().missCount)
    assertTrue(RecoverableJsonFile.corruptFile(reviewFile).exists())

    val invalid = reviewFile.readText().replace("\"회사\"", "\"🙂\"")
    reviewFile.writeText(invalid)
    assertEquals("회사", reviewStore.loadSnapshot().items.values.single().ko)

    reviewFile.writeText("""{"schema_version":99,"items":[]}""")
    assertFailsWith<ReviewDeckStoreException.UnsupportedSchema> { reviewStore.loadSnapshot() }
  }

  @Test
  fun reviewLegacySnapshotWithoutLocalizationsLoads() {
    reviewFile.parentFile.mkdirs()
    reviewFile.writeText(
      """{"schema_version":1,"items":[{"item_id":"item_company","source_deck_id":"deck","ko":"회사","reading_ja":"フェサ",
        |"meaning_ja":"会社","miss_count":2,"consecutive_perfect":1,"added_at":"1970-01-01T00:16:40Z","graduated_at":null}]}""".trimMargin(),
    )
    val loaded = reviewStore.loadSnapshot().activeItems.single()
    assertNull(loaded.localizations)
    assertNull(loaded.deckItem.localizedMeaning("en"))
    assertEquals("会社", loaded.deckItem.localizedMeaning("ja"))
    assertEquals("フェサ", loaded.deckItem.localizedReading("ja-JP"))
  }

  @Test
  fun reviewLibraryBatchesMistakesAndReconcilesAfterDeletion() = runTest {
    val library = ReviewDeckLibrary(reviewStore, testScope(), io(), debounceMillis = 60_000)
    assertEquals(ReviewDeckMutation.ADDED, library.recordMistake(item, "deck"))
    assertEquals(ReviewDeckMutation.UPDATED, library.recordMistake(item, "deck"))
    val removedItem = DeckItem(id = "item_removed", ko = "학교", readingJa = "ハッキョ", meaningJa = "学校", audio = null)
    assertEquals(ReviewDeckMutation.ADDED, library.recordMistake(removedItem, "deck"))
    assertEquals(ReviewDeckMutation.UPDATED, library.recordPerfect(item.id, "deck"))
    assertFalse(reviewFile.exists())
    assertTrue(library.isInReview(item.id, "deck"))

    assertEquals(ReviewDeckMutation.UPDATED, library.markSourceUnavailable("deck"))
    assertTrue(library.activeItems.value.isEmpty())
    assertEquals(false, reviewStore.loadSnapshot().items["deck::item_company"]?.isSourceAvailable)

    val replacement = Deck(
      deckId = "deck", version = 2, name = "更新デッキ", author = DeckAuthor("user", "User"), official = false,
      type = DeckType.WORD, level = 1, tags = listOf("単語"), createdAt = at(1_000), updatedAt = at(2_000),
      items = listOf(DeckItem(id = item.id, ko = "회사원", readingJa = "フェサウォン", meaningJa = "会社員", audio = null)),
    )
    assertEquals(ReviewDeckMutation.UPDATED, library.reconcile(replacement))
    assertEquals("회사원", library.items.value.getValue("deck::item_company").ko)
    assertTrue(library.flushAndWait())
    val reloaded = reviewStore.loadSnapshot()
    assertEquals(2, reloaded.items["deck::item_company"]?.missCount)
    assertEquals(1, reloaded.items["deck::item_company"]?.consecutivePerfect)
    assertEquals(true, reloaded.items["deck::item_company"]?.isSourceAvailable)
    assertEquals(false, reloaded.items["deck::item_removed"]?.isSourceAvailable)
    assertEquals(ReviewDeckMutation.REMOVED, library.removeManually(item.id, "deck"))
    assertEquals(ReviewDeckMutation.ADDED, library.addManually(item, "deck"))
  }

  // Retention

  private val retentionStore = RetentionStore(File(base, "Retention"))
  private val retentionFile = File(base, "Retention/retention-progress.json")
  private fun day(raw: String) = JstDay.parse(raw)!!

  @Test
  fun retentionStoreRecordsIdempotentlyAndRejectsInvalidFiles() {
    val today = day("2026-07-19")
    assertEquals(RetentionMutation.INSERTED, retentionStore.record(RetentionActivityKind.CURRICULUM, today))
    assertEquals(RetentionMutation.UNCHANGED, retentionStore.record(RetentionActivityKind.CURRICULUM, today))
    assertEquals(RetentionMutation.INSERTED, retentionStore.record(RetentionActivityKind.GAME, today))
    assertEquals(setOf(RetentionActivityKind.CURRICULUM, RetentionActivityKind.GAME), retentionStore.loadSnapshot().records.getValue(today).activities.toSet())

    retentionFile.writeText(
      """{"schema_version":1,"records":[{"day":"2026-07-19","activities":["game"]},{"day":"2026-07-19","activities":["curriculum"]}]}""",
    )
    assertEquals(2, retentionStore.loadSnapshot().records.getValue(today).activities.size)
    assertTrue(RecoverableJsonFile.corruptFile(retentionFile).exists())

    retentionFile.writeText("""{"schema_version":99,"records":[]}""")
    assertEquals(99, assertFailsWith<RetentionStoreException.UnsupportedSchema> { retentionStore.loadSnapshot() }.version)
  }

  @Test
  fun sessionKeepsStartDayAcrossMidnight() {
    val session = RetentionSession.start(Instant.parse("2026-07-19T14:59:59Z"))
    assertEquals("2026-07-19", session.day.rawValue)
    RetentionLibrary(retentionStore).record(RetentionActivityKind.GAME, session)
    assertNotNull(retentionStore.loadSnapshot().records[day("2026-07-19")])
    assertNull(retentionStore.loadSnapshot().records[day("2026-07-20")])
  }

  @Test
  fun failedRetentionSaveCanBeRetried() {
    val blocked = File(base, "blocked").also { it.writeText("blocked") }
    val library = RetentionLibrary(RetentionStore(blocked))
    val today = day("2026-07-23")
    library.record(RetentionActivityKind.GAME, today)
    assertTrue(library.saveFailed.value)
    assertFalse(library.isStamped(today))
    blocked.delete()
    library.retryLastSave()
    assertFalse(library.saveFailed.value)
    assertTrue(library.isStamped(today))
    assertEquals(1, library.streak(today).current)
    assertEquals(1, library.stampedDaysThisWeek(today))
    assertTrue(library.dailyEncouragementKey(today) in setOf("mascot.daily_encouragement.5", "mascot.daily_encouragement.6"))
  }

  @Test
  fun storesWriteIsoDatesWholeSeconds() {
    reviewStore.recordMistake(item, "deck", Instant.parse("2026-07-19T01:02:03.456Z"))
    assertTrue("\"added_at\": \"2026-07-19T01:02:03Z\"" in reviewFile.readText())
    assertContentEquals(reviewFile.readBytes(), RecoverableJsonFile.backupFile(reviewFile).readBytes())
  }
}
