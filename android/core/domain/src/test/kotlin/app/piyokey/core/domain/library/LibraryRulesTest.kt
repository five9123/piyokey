package app.piyokey.core.domain.library

import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckAuthor
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.DeckType
import app.piyokey.core.deckkit.PiyoDeckImportError
import app.piyokey.core.deckkit.PiyoDeckPackage
import app.piyokey.core.deckkit.PiyoDeckPackageReader
import app.piyokey.core.deckkit.PiyoDeckPackageWriter
import app.piyokey.core.deckkit.UserDeckValidator
import app.piyokey.core.domain.DailyLearningActivity
import app.piyokey.core.domain.JstDay
import app.piyokey.core.domain.LearningInsightPeriod
import app.piyokey.core.domain.LearningInsights
import app.piyokey.core.domain.PiyoDeckCollisionComparison
import app.piyokey.core.domain.PiyoDeckComparisonSide
import app.piyokey.core.domain.PiyoDeckDocumentNotice
import app.piyokey.core.domain.PiyoDeckDocumentRules
import app.piyokey.core.domain.PiyoDeckImportCollision
import app.piyokey.core.domain.TestSupport
import app.piyokey.core.domain.UserDeckDraft
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ports of iOS `PiyoDeckDocumentFlowTests`, `DeckEditorTests` (flow/commit) and My Page helpers. */
class LibraryRulesTest {
  private val schema = File(TestSupport.sharedRoot, "schema/deck.schema.json").readBytes()
  private val fixtures = File(TestSupport.sharedRoot, "piyodeck/fixtures")
  private val zeroHash = "0".repeat(64)

  private fun userDeck(version: Int = 1, meaning: String = "意味", name: String = "デッキ", items: Int = 1, updatedAt: Long = 100) = Deck(
    deckId = "user_0123456789abcdef0123456789abcdef",
    version = version,
    name = name,
    author = DeckAuthor(UserDeckDraft.LOCAL_AUTHOR_ID, "ピヨ"),
    official = false,
    type = DeckType.WORD,
    level = 1,
    tags = listOf("テスト"),
    createdAt = Instant.ofEpochSecond(1),
    updatedAt = Instant.ofEpochSecond(updatedAt),
    items = (0 until items).map { index ->
      DeckItem(id = "item_%032x".format(index), ko = "사과", readingJa = "サグァ", meaningJa = meaning, audio = null)
    },
  )

  private fun packageOf(deck: Deck): PiyoDeckPackage =
    PiyoDeckPackageReader.read(PiyoDeckPackageWriter.write(deck, schema), schema)

  // PiyoDeckDocumentFlowTests ---------------------------------------------------------------

  @Test
  fun collisionUsesContentHashBeforeVersion() {
    val pkg = packageOf(userDeck(version = 2, meaning = "更新"))
    assertEquals(PiyoDeckImportCollision.New, PiyoDeckImportCollision.of(null, null, null, pkg))
    assertEquals(PiyoDeckImportCollision.Identical, PiyoDeckImportCollision.of(1, pkg.contentSha256, null, pkg))
    val different = PiyoDeckImportCollision.of(3, zeroHash, null, pkg)
    assertEquals(PiyoDeckImportCollision.Different(3, 2), different)
    assertTrue(different.isDowngrade)
    assertFalse(different.isSameVersionConflict)
    assertEquals(PiyoDeckImportCollision.Identical, PiyoDeckImportCollision.of(3, zeroHash, pkg.deck, pkg))
    assertEquals("downgrade", PiyoDeckImportRules.statusKey(different))
  }

  @Test
  fun collisionRecognizesSameVersionWithDifferentContent() {
    val pkg = packageOf(userDeck(version = 2, meaning = "更新"))
    val collision = PiyoDeckImportCollision.of(2, zeroHash, null, pkg)
    assertEquals(PiyoDeckImportCollision.Different(2, 2), collision)
    assertFalse(collision.isDowngrade)
    assertTrue(collision.isSameVersionConflict)
    assertEquals("same_version", PiyoDeckImportRules.statusKey(collision))
    assertEquals("different", PiyoDeckImportRules.statusKey(PiyoDeckImportCollision.Different(1, 2)))
  }

  @Test
  fun collisionComparisonIncludesAllUserDecisionFields() {
    val current = userDeck(version = 5, name = "現在のデッキ", items = 2, updatedAt = 500)
    val incoming = userDeck(version = 4, name = "読み込むデッキ", items = 3, updatedAt = 700)
    val comparison = PiyoDeckCollisionComparison(PiyoDeckComparisonSide.of(current), PiyoDeckComparisonSide.of(incoming))
    assertEquals(PiyoDeckComparisonSide("現在のデッキ", 5, Instant.ofEpochSecond(500), 2), comparison.current)
    assertEquals(PiyoDeckComparisonSide("読み込むデッキ", 4, Instant.ofEpochSecond(700), 3), comparison.incoming)
  }

  @Test
  fun previewPrimaryActionFollowsCollisionAndFreeLimit() {
    assertEquals(PiyoDeckImportRules.PrimaryAction.IMPORT, PiyoDeckImportRules.primaryAction(PiyoDeckImportCollision.New, true))
    assertEquals(PiyoDeckImportRules.PrimaryAction.UNLOCK_PRO, PiyoDeckImportRules.primaryAction(PiyoDeckImportCollision.New, false))
    assertEquals(PiyoDeckImportRules.PrimaryAction.DONE, PiyoDeckImportRules.primaryAction(PiyoDeckImportCollision.Identical, false))
    assertEquals(PiyoDeckImportRules.PrimaryAction.KEEP, PiyoDeckImportRules.primaryAction(PiyoDeckImportCollision.Different(1, 2), true))
    assertEquals("user_a", PiyoDeckImportRules.derivedFromDeckIdForInstall(true, "user_a"))
    assertNull(PiyoDeckImportRules.derivedFromDeckIdForInstall(false, "user_a"))
  }

  @Test
  fun userCopyGetsFreshIdentifiersAndTextOnlyItems() {
    val source = userDeck(version = 9).copy(
      deckId = "official_daily_words",
      official = true,
      items = listOf(DeckItem("daily_001", "사과", "サグァ", "りんご", audio = "audio/daily.caf")),
    )
    val copy = PiyoDeckDocumentRules.makeUserCopy(source, Instant.ofEpochSecond(500))
    assertTrue(Regex("^user_[0-9a-f]{32}$").matches(copy.deckId))
    assertTrue(Regex("^item_[0-9a-f]{32}$").matches(copy.items[0].id))
    assertEquals(1, copy.version)
    assertFalse(copy.official)
    assertNull(copy.items[0].audio)
    assertTrue(UserDeckValidator.validate(copy).isEmpty())
  }

  @Test
  fun exportRoundTripsThroughReaderAndFixturesImport() {
    val deck = userDeck()
    assertEquals(deck, PiyoDeckPackageReader.read(PiyoDeckPackageWriter.write(deck, schema), schema).deck)
    val basic = PiyoDeckPackageReader.read(File(fixtures, "valid/basic.typedeck").readBytes(), schema)
    assertTrue(basic.deck.deckId.startsWith("user_"))
    assertEquals("piyokey-deck", PiyoDeckDocumentRules.safeFilename("!!!"))
  }

  @Test
  fun importErrorsMapToNotices() {
    fun noticeFor(name: String): PiyoDeckDocumentNotice {
      val error = runCatching { PiyoDeckPackageReader.read(File(fixtures, "invalid/$name").readBytes(), schema) }.exceptionOrNull()!!
      return PiyoDeckImportRules.notice(error)
    }
    assertEquals(PiyoDeckDocumentNotice.UNSUPPORTED_VERSION, noticeFor("future-version.typedeck"))
    assertEquals(PiyoDeckDocumentNotice.INVALID_DOCUMENT, noticeFor("crc-mismatch.typedeck"))
    assertEquals(PiyoDeckDocumentNotice.INVALID_DOCUMENT, noticeFor("wrong-sha.typedeck"))
    assertEquals(PiyoDeckDocumentNotice.PACKAGE_TOO_LARGE, PiyoDeckImportRules.notice(PiyoDeckImportError.PackageTooLarge(9, 8)))
    assertEquals(PiyoDeckDocumentNotice.PACKAGE_TOO_LARGE, PiyoDeckImportRules.notice(IOException(), isFileTooLarge = true))
    assertEquals(PiyoDeckDocumentNotice.CANNOT_READ, PiyoDeckImportRules.notice(IOException()))
  }

  @Test
  fun officialPackageIdentifierIsRejected() {
    val official = userDeck().copy(deckId = "official_daily_words")
    val error = runCatching { PiyoDeckPackageWriter.write(official, schema) }.exceptionOrNull()
    assertTrue(error is PiyoDeckImportError)
  }

  // DeckEditorTests (flow + commit guards) ---------------------------------------------------

  @Test
  fun draftFlowMatchesOnlyTheSameRequestedWork() {
    val original = userDeck(version = 4)
    val editing = UserDeckDraft.editing(original)
    assertTrue(DeckMakerDraftFlow.Editing(original.deckId).matches(editing))
    assertFalse(DeckMakerDraftFlow.New.matches(editing))
    assertFalse(DeckMakerDraftFlow.Editing("user_other").matches(editing))
    assertEquals("edited", DeckMakerCommitRules.action(editing))
  }

  @Test
  fun editingCommitRefusesMissingOrChangedSourceVersion() {
    val editing = UserDeckDraft.editing(userDeck(version = 4))
    DeckMakerCommitRules.requireUnchangedSource(editing, 4)
    assertFailsWith<UserDeckSourceChangedException> { DeckMakerCommitRules.requireUnchangedSource(editing, 5) }
    assertFailsWith<UserDeckSourceChangedException> { DeckMakerCommitRules.requireUnchangedSource(editing, null) }
  }

  @Test
  fun newAndOfficialCopyCommitsDoNotRequireInstalledSource() {
    val newDraft = UserDeckDraft.new()
    DeckMakerCommitRules.requireUnchangedSource(newDraft, null)
    assertEquals("created", DeckMakerCommitRules.action(newDraft))
    val official = userDeck().copy(deckId = "official_daily_words", official = true, author = DeckAuthor("official", "PIYOKEY"))
    val copied = UserDeckDraft.copyingOfficial(official)
    DeckMakerCommitRules.requireUnchangedSource(copied, null)
    assertEquals(DeckMakerDraftFlow.OfficialCopy("official_daily_words"), DeckMakerDraftFlow.of(copied))
    assertEquals("copied", DeckMakerCommitRules.action(copied))
  }

  @Test
  fun editorExpandsSmallDraftsAndCountsSyllables() {
    val small = UserDeckDraft.editing(userDeck(items = 8))
    assertEquals(8, DeckMakerCommitRules.initiallyExpandedItemIds(small).size)
    val large = UserDeckDraft.editing(userDeck(items = 9))
    assertEquals(setOf(large.items[0].id), DeckMakerCommitRules.initiallyExpandedItemIds(large))
    assertEquals(4, DeckMakerCommitRules.koreanSyllableCount("안녕 하세"))
  }

  // My Page -------------------------------------------------------------------------------

  @Test
  fun myDecksSortOrders() {
    val a = userDeck().copy(deckId = "a", name = "b-deck")
    val b = userDeck().copy(deckId = "b", name = "a-deck")
    val c = userDeck().copy(deckId = "c", name = "c-deck")
    val dates = mapOf(
      "a" to DeckInstallDates(Instant.ofEpochSecond(10), lastPlayedAt = Instant.ofEpochSecond(100)),
      "b" to DeckInstallDates(Instant.ofEpochSecond(30), lastPlayedAt = null),
      "c" to DeckInstallDates(Instant.ofEpochSecond(20), lastPlayedAt = null),
    )
    fun sorted(order: MyDeckSortOrder) = MyDecksRules.sorted(listOf(a, b, c), order, dates::get, { it.name }).map { it.deckId }
    assertEquals(listOf("a", "b", "c"), sorted(MyDeckSortOrder.RECENT))
    assertEquals(listOf("b", "a", "c"), sorted(MyDeckSortOrder.NAME))
    assertEquals(listOf("b", "c", "a"), sorted(MyDeckSortOrder.INSTALLED))
    assertEquals("가・나・다", MyDecksRules.reviewPreview(listOf("가", "나", "다", "라")))
  }

  @Test
  fun insightFormatting() {
    val day = JstDay.parse("2026-09-03")!!
    assertEquals("9/3", InsightsFormatting.shortDay(day))
    assertNull(InsightsFormatting.roundedMinutes(59.0))
    assertEquals(2, InsightsFormatting.roundedMinutes(100.0))
    fun activity(duration: Double, active: Boolean) = DailyLearningActivity(day, 1, duration, active)
    val empty = LearningInsights.make(LearningInsightPeriod.WEEK, Instant.now(), emptyList(), emptyMap(), emptyList(), emptyMap())
    assertEquals(5.0, InsightsFormatting.activityBarHeight(activity(0.0, false), empty))
    assertEquals(15.0, InsightsFormatting.activityBarHeight(activity(0.0, true), empty))
    val insights = empty.copy(dailyActivities = listOf(activity(100.0, true), activity(1.0, true), activity(0.0, true)))
    assertEquals(58.0, InsightsFormatting.activityBarHeight(insights.dailyActivities[0], insights))
    assertEquals(8.0, InsightsFormatting.activityBarHeight(insights.dailyActivities[1], insights))
    assertEquals(12.0, InsightsFormatting.activityBarHeight(insights.dailyActivities[2], insights))
  }
}
