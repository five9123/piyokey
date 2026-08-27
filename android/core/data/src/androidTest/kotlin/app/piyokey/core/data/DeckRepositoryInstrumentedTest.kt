package app.piyokey.core.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.URL
import app.piyokey.core.game.FlowGameRecord
import app.piyokey.core.retention.JstDay
import app.piyokey.core.session.PracticeItemResolution
import app.piyokey.core.session.PracticeSessionCheckpoint
import app.piyokey.core.piyodeck.PiyoDeckImportException
import app.piyokey.core.piyodeck.PiyoDeckPackageReader
import app.piyokey.core.piyodeck.PiyoDeckPackageWriter
import app.piyokey.core.piyodeck.UserDeckDraft
import app.piyokey.core.piyodeck.UserDeckItemDraft
import app.piyokey.core.piyodeck.UserDeckLanguage
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeckRepositoryInstrumentedTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var database: PiyokeyDatabase
  private lateinit var repository: DeckRepository
  private var now = 1_000L

  @Before
  fun setUp() {
    File(context.filesDir, "piyokey/content").deleteRecursively()
    File(context.filesDir, "piyokey/deck-maker").deleteRecursively()
    File(context.cacheDir, PIYODECK_STAGING_DIRECTORY_NAME).deleteRecursively()
    database = Room.inMemoryDatabaseBuilder(context, PiyokeyDatabase::class.java).build()
    repository = DeckRepository.createForTesting(context, database, clock = { now++ })
  }

  @After
  fun tearDown() {
    database.close()
    File(context.filesDir, "piyokey/content").deleteRecursively()
    File(context.filesDir, "piyokey/deck-maker").deleteRecursively()
    File(context.cacheDir, PIYODECK_STAGING_DIRECTORY_NAME).deleteRecursively()
  }

  @Test
  fun importedDeckLifecycleIsIdempotentReplaceableExportableAndHistorySafe() = runTest {
    val initialFile = stageFixture("valid/basic.piyodeck")
    val preview = repository.previewImportedDeck(initialFile, "basic.piyodeck")
    assertEquals(ImportedDeckConflict.NEW, preview.conflict)
    val first = repository.commitImportedDeck(initialFile, preview.contentSha256, false, true)
    assertTrue(first is ImportedDeckCommitResult.Installed)

    repository.markPlayed(preview.deck.deckId)
    val beforeIdentical = requireNotNull(database.dao().installedDeck(preview.deck.deckId))
    val identical = repository.previewImportedDeck(initialFile, "basic.piyodeck")
    assertEquals(ImportedDeckConflict.IDENTICAL, identical.conflict)
    assertTrue(repository.commitImportedDeck(initialFile, identical.contentSha256, false, true) is ImportedDeckCommitResult.AlreadyInstalled)
    assertEquals(beforeIdentical, database.dao().installedDeck(preview.deck.deckId))

    val schema = context.assets.open("deck.schema.json").bufferedReader().use { it.readText() }
    val parsed = PiyoDeckPackageReader.read(initialFile.readBytes(), schema)
    val replacementData = PiyoDeckPackageWriter.write(
      parsed.deck.copy(version = parsed.deck.version + 1, name = "置き換えデッキ"),
      schema,
    )
    val replacementFile = stageBytes(replacementData)
    val replacement = repository.previewImportedDeck(replacementFile, "replacement.piyodeck")
    assertEquals(ImportedDeckConflict.NEWER_VERSION, replacement.conflict)
    try {
      repository.commitImportedDeck(replacementFile, replacement.contentSha256, false, true)
      throw AssertionError("replacement confirmation was not required")
    } catch (_: ImportedDeckException.ReplacementConfirmationRequired) {
      // Expected: conflict replacement is never implicit.
    }
    repository.commitImportedDeck(replacementFile, replacement.contentSha256, true, true)
    val replaced = requireNotNull(database.dao().installedDeck(preview.deck.deckId))
    assertEquals(parsed.deck.version + 1, replaced.version)
    assertEquals(beforeIdentical.lastPlayedAtEpochMillis, replaced.lastPlayedAtEpochMillis)

    val exported = repository.exportUserDeck(preview.deck.deckId)
    assertEquals(parsed.deck.version + 1, PiyoDeckPackageReader.read(exported, schema).deck.version)

    repository.delete(preview.deck.deckId)
    assertTrue(repository.snapshot().installed.isEmpty())
    assertTrue(database.dao().userDeckHistory(preview.deck.deckId)?.lastDeletedAtEpochMillis != null)
    repository.commitImportedDeck(replacementFile, replacement.contentSha256, false, true)
    assertEquals(beforeIdentical.lastPlayedAtEpochMillis, database.dao().installedDeck(preview.deck.deckId)?.lastPlayedAtEpochMillis)
  }

  @Test
  fun conflictingImportCanBecomeIndependentCopyWithoutTouchingOriginalOrDraft() = runTest {
    val initialFile = stageFixture("valid/basic.piyodeck")
    val initialPreview = repository.previewImportedDeck(initialFile, "basic.piyodeck")
    repository.commitImportedDeck(initialFile, initialPreview.contentSha256, false, true)
    repository.markPlayed(initialPreview.deck.deckId)
    val originalBefore = requireNotNull(database.dao().installedDeck(initialPreview.deck.deckId))

    val activeDraft = repository.saveUserDeckDraft(
      complete(UserDeckDraft.new(Instant.ofEpochMilli(now)) { "9".repeat(32) }),
    )
    val schema = context.assets.open("deck.schema.json").bufferedReader().use { it.readText() }
    val parsed = PiyoDeckPackageReader.read(initialFile.readBytes(), schema)
    val incomingItems = parsed.deck.items.mapIndexed { index, item ->
      if (index == 0) item.copy(meaningJa = item.meaningJa + "（別内容）") else item
    }
    val conflictFile = stageBytes(PiyoDeckPackageWriter.write(parsed.deck.copy(items = incomingItems), schema))
    val conflict = repository.previewImportedDeck(conflictFile, "conflict.piyodeck")
    assertEquals(ImportedDeckConflict.SAME_VERSION_DIFFERENT_CONTENT, conflict.conflict)

    val separate = repository.commitImportedDeckAsSeparateCopy(
      conflictFile,
      conflict.contentSha256,
      UserDeckLanguage.JAPANESE,
    )

    assertTrue(separate.deck.deckId.startsWith("user_"))
    assertEquals(1, separate.deck.version)
    assertEquals("created", separate.metadata.source)
    assertTrue(separate.deck.items.map { it.id }.toSet().intersect(parsed.deck.items.map { it.id }.toSet()).isEmpty())
    assertEquals(originalBefore, database.dao().installedDeck(initialPreview.deck.deckId))
    assertEquals(null, separate.metadata.lastPlayedAtEpochMillis)
    assertEquals(activeDraft.draftId, repository.loadUserDeckDraft()?.draftId)
    assertEquals(2, repository.snapshot().installed.size)
  }

  @Test
  fun maliciousImportNeverMutatesInstalledDecks() = runTest {
    val valid = stageFixture("valid/basic.piyodeck")
    val preview = repository.previewImportedDeck(valid, "basic.piyodeck")
    repository.commitImportedDeck(valid, preview.contentSha256, false, true)
    val before = database.dao().installedDecks()
    val invalid = stageFixture("invalid/wrong-sha.piyodeck")
    try {
      repository.previewImportedDeck(invalid, "wrong-sha.piyodeck")
      throw AssertionError("malicious import was accepted")
    } catch (_: PiyoDeckImportException.Sha256Mismatch) {
      // Expected.
    }
    assertEquals(before, database.dao().installedDecks())
  }

  @Test
  fun deckMakerCreatesEditsAndClearsOnlyTheCommittedDraft() = runTest {
    val draft = complete(UserDeckDraft.new(Instant.ofEpochMilli(now)) { "1".repeat(32) })
    val active = repository.saveUserDeckDraft(draft)
    val created = repository.commitUserDeckDraft(active, UserDeckLanguage.JAPANESE)

    assertEquals(1, created.deck.version)
    assertEquals("created", created.metadata.source)
    assertEquals(null, repository.loadUserDeckDraft())

    val editing = UserDeckDraft.editing(created.deck).copy(name = "편집됨")
    val editingActive = repository.saveUserDeckDraft(editing)
    val edited = repository.commitUserDeckDraft(editingActive, UserDeckLanguage.JAPANESE)
    assertEquals(2, edited.deck.version)
    assertEquals("편집됨", edited.deck.name)
    assertEquals(created.deck.deckId, edited.deck.deckId)
  }

  @Test
  fun editBaseVersionConflictPreservesDraftForSeparateCopy() = runTest {
    val initialDraft = complete(UserDeckDraft.new(Instant.ofEpochMilli(now)) { "2".repeat(32) })
    val created = repository.commitUserDeckDraft(
      repository.saveUserDeckDraft(initialDraft),
      UserDeckLanguage.JAPANESE,
    )
    val stale = repository.saveUserDeckDraft(
      UserDeckDraft.editing(created.deck).copy(name = "보존할 변경"),
    )
    database.dao().upsertInstalledDeck(created.metadata.copy(version = 2))

    try {
      repository.commitUserDeckDraft(stale, UserDeckLanguage.JAPANESE)
      throw AssertionError("stale edit was committed")
    } catch (error: UserDeckEditCommitException.SourceChanged) {
      assertEquals(1, error.expectedVersion)
      assertEquals(2, error.actualVersion)
    }
    val recovered = requireNotNull(repository.loadUserDeckDraft())
    assertEquals("보존할 변경", recovered.draft.name)
    val separate = recovered.draft.asSeparateCopy(
      Instant.ofEpochMilli(now),
    ) { "3".repeat(32) }
    assertTrue(separate.deckId != created.deck.deckId)
  }

  @Test
  fun officialCopyStoresLocalProvenanceWithoutChangingExportedDeck() = runTest {
    val official = repository.install(
      repository.snapshot().catalog.decks.first { it.deckId == "official_daily_words" },
    )
    var id = 10
    val draft = UserDeckDraft.copyingOfficial(
      official.deck,
      Instant.ofEpochMilli(now),
    ) { (++id).toString(16).padStart(32, '0') }
    val copied = repository.commitUserDeckDraft(
      repository.saveUserDeckDraft(draft),
      UserDeckLanguage.JAPANESE,
    )

    assertEquals(official.deck.deckId, copied.metadata.derivedFromDeckId)
    assertFalse(copied.deck.official)
    val schema = context.assets.open("deck.schema.json").bufferedReader().use { it.readText() }
    val exported = PiyoDeckPackageReader.read(repository.exportUserDeck(copied.deck.deckId), schema)
    assertEquals(copied.deck.deckId, exported.deck.deckId)
  }

  @Test
  fun freeUserDeckLimitBlocksOnlyFourthNewDeckAndAllowsReplacement() = runTest {
    val schema = context.assets.open("deck.schema.json").bufferedReader().use { it.readText() }
    val base = PiyoDeckPackageReader.read(
      context.assets.open("valid/basic.piyodeck").use { it.readBytes() },
      schema,
    ).deck
    val installedFiles = (1..3).map { index ->
      stageBytes(
        PiyoDeckPackageWriter.write(
          base.copy(deckId = "user_${index.toString(16).padStart(32, '0')}", name = "무료 $index"),
          schema,
        ),
      )
    }
    installedFiles.forEach { file ->
      val preview = repository.previewImportedDeck(file, file.name)
      repository.commitImportedDeck(file, preview.contentSha256, false, false)
    }
    assertEquals(3, repository.snapshot().installed.size)

    val fourthFile = stageBytes(
      PiyoDeckPackageWriter.write(
        base.copy(deckId = "user_${"f".repeat(32)}", name = "네 번째"),
        schema,
      ),
    )
    val fourth = repository.previewImportedDeck(fourthFile, fourthFile.name)
    try {
      repository.commitImportedDeck(fourthFile, fourth.contentSha256, false, false)
      throw AssertionError("the fourth free user deck was installed")
    } catch (_: ImportedDeckException.FreeUserDeckLimitReached) {
      // Expected: validation and preview remain available, but the new install is gated.
    }

    val firstInstalled = PiyoDeckPackageReader.read(installedFiles.first().readBytes(), schema).deck
    val replacementFile = stageBytes(
      PiyoDeckPackageWriter.write(
        firstInstalled.copy(version = firstInstalled.version + 1, name = "무료 교체"),
        schema,
      ),
    )
    val replacement = repository.previewImportedDeck(replacementFile, replacementFile.name)
    repository.commitImportedDeck(replacementFile, replacement.contentSha256, true, false)
    assertEquals(firstInstalled.version + 1, database.dao().installedDeck(firstInstalled.deckId)?.version)

    repository.commitImportedDeck(fourthFile, fourth.contentSha256, false, true)
    assertEquals(4, repository.snapshot().installed.size)
  }

  private fun complete(draft: UserDeckDraft): UserDeckDraft = draft.copy(
    name = "내 덱",
    authorNickname = "나",
    tags = listOf("일상"),
    items = draft.items.map {
      UserDeckItemDraft(
        id = it.id,
        ko = "가",
        readingJa = "カ",
        meaningJa = "行く",
      )
    },
  )

  private fun stageFixture(path: String): File = stageBytes(context.assets.open(path).use { it.readBytes() })

  private fun stageBytes(bytes: ByteArray): File {
    val directory = File(context.cacheDir, PIYODECK_STAGING_DIRECTORY_NAME).apply { mkdirs() }
    return File(directory, "${System.nanoTime()}.piyodeck").apply { writeBytes(bytes) }
  }

  @Test
  fun bundledCatalogInstallOfflineReadAndDeletePreserveHistory() = runTest {
    val initial = repository.snapshot()
    assertEquals(26, initial.catalog.decks.size)
    assertTrue(initial.installed.isEmpty())

    val entry = initial.catalog.decks.first { it.deckId == "official_daily_words" }
    val installed = repository.install(entry)
    assertEquals(entry.deckId, installed.deck.deckId)
    assertEquals(entry.itemCount, installed.deck.items.size)

    val offline = repository.snapshot()
    assertEquals(setOf(entry.deckId), offline.installedDeckIds)
    assertTrue(offline.downloadHistoryTags.flatten().containsAll(entry.tags))

    repository.delete(entry.deckId)
    val deleted = repository.snapshot()
    assertFalse(entry.deckId in deleted.installedDeckIds)
    assertTrue(deleted.downloadHistoryTags.flatten().containsAll(entry.tags))
  }

  @Test
  fun corruptedInstalledPayloadIsQuarantinedWithoutBreakingCatalog() = runTest {
    val initial = repository.snapshot()
    val entry = initial.catalog.decks.first()
    val installed = repository.install(entry)
    File(context.filesDir, "piyokey/content/${installed.metadata.payloadName}").writeText("broken")

    val recovered = repository.snapshot()
    assertEquals(26, recovered.catalog.decks.size)
    assertTrue(recovered.installed.isEmpty())
    assertTrue(File(context.filesDir, "piyokey/content/quarantine").listFiles().orEmpty().isNotEmpty())
  }

  @Test
  fun corruptedInstalledPayloadRollsBackToLastVerifiedBackup() = runTest {
    val entry = repository.snapshot().catalog.decks.first()
    repository.install(entry)
    val updated = repository.install(entry)
    File(context.filesDir, "piyokey/content/${updated.metadata.payloadName}").writeText("broken")

    val recovered = repository.snapshot()
    assertEquals(setOf(entry.deckId), recovered.installedDeckIds)
    assertEquals(entry.version, recovered.installed.single().metadata.version)
    assertEquals("recovered", recovered.installed.single().metadata.source)
  }

  @Test
  fun conditionalCatalogRefreshPersistsValidatedV11AndUsesValidators() = runTest {
    val updateBytes = context.assets.open("updates/catalog.json").use { it.readBytes() }
    val requests = mutableListOf<Pair<String?, String?>>()
    var requestCount = 0
    val client = object : StaticContentClient {
      override suspend fun get(
        url: URL,
        etag: String?,
        lastModified: String?,
      ): StaticContentResponse {
        requests += etag to lastModified
        return if (requestCount++ == 0) {
          StaticContentResponse(200, updateBytes, "\"catalog-v11\"", "Sat, 22 Aug 2026 00:00:00 GMT")
        } else {
          StaticContentResponse(304, null, "\"catalog-v11\"", "Sat, 22 Aug 2026 00:00:00 GMT")
        }
      }
    }
    repository = DeckRepository.createForTesting(
      context = context,
      database = database,
      remoteClient = client,
      catalogUrl = URL("https://cdn.example.com/piyokey/catalog.json"),
      clock = { now++ },
    )

    assertEquals(CatalogRefreshResult.Updated(11), repository.refreshCatalog())
    assertEquals(11, repository.snapshot().catalog.catalogVersion)
    assertEquals(CatalogRefreshResult.NotModified, repository.refreshCatalog())
    assertEquals(null to null, requests.first())
    assertEquals(
      "\"catalog-v11\"" to "Sat, 22 Aug 2026 00:00:00 GMT",
      requests.last(),
    )
  }

  @Test
  fun corruptedCatalogCacheRollsBackToLastVerifiedCache() = runTest {
    val baseBytes = context.assets.open("catalog.json").use { it.readBytes() }
    val updateBytes = context.assets.open("updates/catalog.json").use { it.readBytes() }
    var requestCount = 0
    val client = object : StaticContentClient {
      override suspend fun get(
        url: URL,
        etag: String?,
        lastModified: String?,
      ): StaticContentResponse = when (requestCount++) {
        0 -> StaticContentResponse(200, baseBytes, "\"v10\"", "Fri, 21 Aug 2026 00:00:00 GMT")
        else -> StaticContentResponse(200, updateBytes, "\"v11\"", "Sat, 22 Aug 2026 00:00:00 GMT")
      }
    }
    repository = DeckRepository.createForTesting(
      context,
      database,
      client,
      URL("https://cdn.example.com/piyokey/catalog.json"),
      clock = { now++ },
    )
    assertEquals(CatalogRefreshResult.Updated(10), repository.refreshCatalog())
    assertEquals(CatalogRefreshResult.Updated(11), repository.refreshCatalog())
    val current = requireNotNull(database.dao().catalogState())
    File(context.filesDir, "piyokey/content/${current.payloadName}").writeText("broken")

    assertEquals(10, repository.snapshot().catalog.catalogVersion)
    assertEquals(10, database.dao().catalogState()?.catalogVersion)
  }

  @Test
  fun bundledFlowPresetsContainThreeDisjointOneHundredWordCourses() = runTest {
    val presets = repository.bundledFlowDecks()
    assertEquals(
      listOf("flow_topik_beginner", "flow_topik_intermediate", "flow_topik_advanced"),
      presets.map { it.deckId },
    )
    assertTrue(presets.all { it.items.size == 100 })
    assertTrue(presets.all { deck -> deck.items.map { it.ko }.distinct().size == 100 })
    assertEquals(300, presets.flatMap { deck -> deck.items.map { it.ko } }.distinct().size)
    assertEquals("S", repository.flowRankTuning().rank(100.0, 120.0))
  }

  @Test
  fun allBundledGamePresetsAndSpacingPassagesMeetOfflineContracts() = runTest {
    listOf("acid_rain", "choseong", "word_match", "dictation").forEach { mode ->
      val presets = repository.bundledGameDecks(mode)
      assertEquals(mode, 3, presets.size)
      assertTrue(mode, presets.all { it.items.size == 100 })
      assertTrue(mode, presets.all { deck -> deck.items.map { it.ko }.distinct().size == 100 })
      if (mode == "dictation") {
        presets.flatMap { it.items }.forEach { item ->
          val audio = requireNotNull(item.audio) { "Bundled dictation item ${item.id} needs canonical audio" }
          assertTrue(audio, context.assets.open(audio).use { it.readBytes().isNotEmpty() })
        }
      }
    }
    val passages = repository.bundledSpacingPassages()
    assertEquals((1..6).toList(), passages.map { it.level })
    assertTrue(passages.all { it.characterCount in 100..200 })
    assertTrue(passages.last().characterCount >= 180)
  }

  @Test
  fun gameRecordsAreAppendOnlyWhileDeckProgressKeepsBestAndPlayCount() = runTest {
    val first = sampleFlowRecord(score = 800, accuracy = 92.5, playedAt = 2_000)
    val second = sampleFlowRecord(score = 500, accuracy = 98.0, playedAt = 3_000)

    assertTrue(repository.saveFlowRecord(first).isNewBest)
    val saved = repository.saveFlowRecord(second)

    assertFalse(saved.isNewBest)
    assertEquals(2, saved.progress.plays)
    assertEquals(800, saved.progress.bestScore)
    assertEquals(98.0, saved.progress.bestAccuracyPercent, 0.001)
    assertEquals(2, database.dao().gameRecords(first.deckId, "flow", "builtin").size)
  }

  @Test
  fun curriculumCheckpointCompletionAndJstStampAreTransactional() = runTest {
    val checkpoint = PracticeSessionCheckpoint(
      currentTargetIndex = 2,
      acceptedKeys = "ㄷ",
      mistakeCount = 1,
      currentItemMistakeCount = 1,
      currentItemMistakenJamoIndices = setOf(0),
      itemResolutions = listOf(PracticeItemResolution(0, 0, emptySet()), PracticeItemResolution(1, 0, emptySet())),
      activeDurationMillis = 12_345,
    )
    repository.saveCurriculumCheckpoint("chapter_1_basic_consonants", checkpoint)
    assertEquals(checkpoint, repository.learningSnapshot().activeSession?.checkpoint)

    val stars = repository.finishCurriculumStage(
      stageId = "chapter_1_basic_consonants",
      accuracyPercent = 95.0,
      charactersPerMinute = 50.0,
      sessionDay = JstDay("2026-08-25"),
      completedAtEpochMillis = 10_000,
    )
    val snapshot = repository.learningSnapshot()
    assertEquals(2, stars)
    assertEquals(2, snapshot.progress.getValue("chapter_1_basic_consonants").stars)
    assertEquals(null, snapshot.activeSession)
    assertTrue(JstDay("2026-08-25") in snapshot.completedDays)
  }

  @Test
  fun reviewItemsCollectMistakesAndGraduateAfterThreePerfectReviewSessions() = runTest {
    val deck = repository.bundledFlowDecks().first()
    val item = deck.items.first()
    repository.recordPracticeReview(
      deck.deckId,
      listOf(item),
      listOf(PracticeItemResolution(0, 2, setOf(0))),
      isReviewSession = false,
      atEpochMillis = 100,
    )
    repeat(3) { run ->
      repository.recordPracticeReview(
        deck.deckId,
        listOf(item),
        listOf(PracticeItemResolution(0, 0, emptySet())),
        isReviewSession = true,
        atEpochMillis = (200 + run).toLong(),
      )
    }
    val stored = repository.learningSnapshot().reviewItems.single()
    assertEquals(1, stored.missCount)
    assertEquals(3, stored.consecutivePerfect)
    assertFalse(stored.isActive)
    repository.addReviewItemManually(item, deck.deckId, 500)
    val reactivated = repository.learningSnapshot().reviewItems.single()
    assertTrue(reactivated.isActive)
    repository.removeReviewItemManually(reactivated.id)
    assertTrue(repository.learningSnapshot().reviewItems.isEmpty())
  }

  @Test
  fun threeFiveSevenWeeklyRewardsUnlockPermanently() = runTest {
    (24..30).forEach { day ->
      repository.recordDailyCompletion(JstDay("2026-08-$day"), day.toLong())
    }
    assertEquals(setOf(3, 5, 7), repository.learningSnapshot().unlockedRewards)
  }

  @Test
  fun quickPracticeCreatesStampWithoutCompletingDailyChallenge() = runTest {
    val day = JstDay("2026-08-25")
    repository.recordQuickPracticeCompletion(day, 1_000)
    val activities = repository.learningSnapshot().activitiesByDay.getValue(day)
    assertEquals(setOf(app.piyokey.core.retention.RetentionActivity.QUICK_PRACTICE), activities)
    assertFalse(app.piyokey.core.retention.RetentionActivity.DAILY_CHALLENGE in activities)
  }

  private fun sampleFlowRecord(score: Int, accuracy: Double, playedAt: Long) = FlowGameRecord(
    deckId = "flow_topik_beginner",
    course = "beginner",
    score = score,
    maxCombo = 7,
    accuracyPercent = accuracy,
    correctJamoCount = 20,
    charactersPerMinute = 64.0,
    mistakeCount = 2,
    completedItemCount = 5,
    missedItemCount = 1,
    playDurationMillis = 60_000,
    playedAtEpochMillis = playedAt,
  )
}
