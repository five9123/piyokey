package app.piyokey.core.domain

import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckAuthor
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.DeckItemLocalization
import app.piyokey.core.deckkit.DeckType
import app.piyokey.core.domain.TestSupport.at
import app.piyokey.core.hangul.JamoDecomposer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurriculumAndReviewRulesTest {
  @Test
  fun bundledCurriculumHasExpandedStagesWithTenTypeableItemsEach() {
    assertEquals(6, CurriculumCatalog.chapters.size)
    assertEquals(12, CurriculumCatalog.stages.size)
    assertEquals(
      listOf("chapter_5_words", "chapter_5_travel_words", "chapter_5_study_work_words"),
      CurriculumCatalog.chapters[4].stages.map { it.id },
    )
    assertEquals(
      listOf("chapter_6_spacing", "chapter_6_sentences", "chapter_6_travel_phrases", "chapter_6_daily_conversation", "chapter_6_fan_support"),
      CurriculumCatalog.chapters[5].stages.map { it.id },
    )
    for (stage in CurriculumCatalog.stages) {
      assertEquals(10, stage.items.size, stage.id)
      for (item in stage.items) assertTrue(JamoDecomposer.keySequence(item.ko).isNotEmpty())
    }
    val first = CurriculumCatalog.stages.first().items.first()
    assertEquals("chapter_1_basic_consonants_1", first.id)
    assertEquals("curriculum.chapter_1_basic_consonants.item_1.reading", first.readingKey)
    assertEquals("curriculum.chapter_1_basic_consonants.item_meaning", first.meaningKey)
    assertEquals("curriculum.chapter_5_words.item_3.meaning", CurriculumCatalog.stage("chapter_5_words")!!.items[2].meaningKey)
    assertEquals("curriculum::chapter_5_words", CurriculumCatalog.stage("chapter_5_words")!!.sourceDeckId)
    assertTrue(first.audioRelativePath.matches(Regex("^audio/ko_[0-9a-f]{20}\\.mp3$")))
  }

  @Test
  fun spacingStagePracticesExactlyOneWordBoundaryPerTarget() {
    val stage = assertNotNull(CurriculumCatalog.stage("chapter_6_spacing"))
    assertEquals(6, stage.chapterNumber)
    assertEquals(1, stage.stageNumber)
    for (item in stage.items) {
      assertTrue(item.ko.graphemeCount() <= 10)
      assertEquals(1, item.ko.count { it.isWhitespace() }, item.ko)
      assertEquals(1, JamoDecomposer.keySequence(item.ko).count { it.isWhitespace() })
    }
  }

  @Test
  fun starRatingUsesClearAccuracyAndSpeedGates() {
    assertEquals(0, CurriculumStarRating.stars(79.9, 100.0))
    assertEquals(1, CurriculumStarRating.stars(80.0, 20.0))
    assertEquals(2, CurriculumStarRating.stars(90.0, 40.0))
    assertEquals(3, CurriculumStarRating.stars(97.0, 60.0))
  }

  @Test
  fun coreStagesUnlockSequentiallyWhileChaptersFiveAndSixAreFree() {
    val stages = CurriculumCatalog.stages
    assertTrue(CurriculumUnlockPolicy.isUnlocked(stages[0], emptySet()))
    assertFalse(CurriculumUnlockPolicy.isUnlocked(stages[1], emptySet()))
    assertTrue(CurriculumUnlockPolicy.isUnlocked(stages[1], setOf(stages[0].id)))
    for (stage in stages.filter { it.chapterNumber >= 5 }) assertTrue(CurriculumUnlockPolicy.isUnlocked(stage, emptySet()), stage.id)
  }

  @Test
  fun anyFreeSelectionStageCompletesChapterSix() {
    val chapter = CurriculumCatalog.chapters[5]
    assertFalse(CurriculumChapterCompletionPolicy.isCompleted(chapter, emptySet()))
    assertTrue(CurriculumChapterCompletionPolicy.isCompleted(chapter, setOf("chapter_6_spacing")))
    assertTrue(CurriculumChapterCompletionPolicy.isCompleted(chapter, setOf("chapter_6_sentences")))
    assertFalse(CurriculumChapterCompletionPolicy.isCompleted(CurriculumCatalog.chapters[0], emptySet()))
    assertTrue(CurriculumChapterCompletionPolicy.isCompleted(CurriculumCatalog.chapters[0], setOf("chapter_1_basic_consonants")))
  }

  @Test
  fun hatchOnboardingRequiresExactlyTheFirstThreeStagesInOrder() {
    val stages = CurriculumCatalog.stages
    assertEquals(stages.take(3).map { it.id }, HatchOnboardingPolicy.requiredStages.map { it.id })
    assertEquals(stages[0].id, HatchOnboardingPolicy.nextRequiredStage(emptySet())?.id)
    assertEquals(stages[1].id, HatchOnboardingPolicy.nextRequiredStage(setOf(stages[0].id))?.id)
    assertFalse(HatchOnboardingPolicy.isComplete(setOf(stages[0].id, stages[1].id)))
    assertTrue(HatchOnboardingPolicy.isComplete(stages.take(3).map { it.id }.toSet()))
  }

  @Test
  fun curriculumFinishKeepsBestStarsAccuracyAndFirstDate() {
    val checkpoint = PracticeSessionCheckpoint(3, "ㄹ", 2, 1, listOf(0), 12.5)
    var snapshot = CurriculumProgressRules.applySave("stage", checkpoint, at(10), CurriculumProgressSnapshot.EMPTY).snapshot
    assertEquals(checkpoint, snapshot.activeSession?.checkpoint)
    val failed = CurriculumProgressRules.applyFinish("stage", 0, 79.9, at(20), snapshot)
    assertEquals(CurriculumProgressMutation.CLEARED, failed.mutation)
    assertNull(failed.snapshot.activeSession)
    assertTrue(failed.snapshot.stageProgress.isEmpty())

    snapshot = CurriculumProgressRules.applyFinish("stage", 2, 91.0, at(1_000), CurriculumProgressSnapshot.EMPTY).snapshot
    snapshot = CurriculumProgressRules.applyFinish("stage", 1, 95.0, at(2_000), snapshot).snapshot
    snapshot = CurriculumProgressRules.applyFinish("stage", 5, 120.0, at(2_000), snapshot).snapshot
    val progress = snapshot.stageProgress.getValue("stage")
    assertEquals(3, progress.stars)
    assertEquals(100.0, progress.bestAccuracy)
    assertEquals(at(1_000), progress.completedAt)
  }

  @Test
  fun curriculumClearOnlyMatchesTheActiveStageAndValidationRejectsImpossibleStars() {
    val checkpoint = PracticeSessionCheckpoint(0, "", 0, 0, emptyList(), 0.0)
    val saved = CurriculumProgressRules.applySave("a", checkpoint, at(1), CurriculumProgressSnapshot.EMPTY).snapshot
    assertEquals(CurriculumProgressMutation.UNCHANGED, CurriculumProgressRules.applyClear("b", saved).mutation)
    assertEquals(CurriculumProgressMutation.CLEARED, CurriculumProgressRules.applyClear(null, saved).mutation)
    assertFailsWith<CurriculumProgressStoreException.InvalidProgress> {
      CurriculumProgressRules.validated(listOf(CurriculumStageProgress("s", 9, 101.0, at(1))), null)
    }
  }

  private val item = DeckItem(id = "item_company", ko = "회사", readingJa = "フェサ", meaningJa = "会社", audio = null)

  @Test
  fun reviewMistakeAddsAndPerfectsGraduateAfterThree() {
    var s = ReviewDeckRules.applyMistake(item, "deck", at(1_000), ReviewDeckSnapshot.EMPTY).also {
      assertEquals(ReviewDeckMutation.ADDED, it.mutation)
    }.snapshot
    s = ReviewDeckRules.applyPerfect(item.id, "deck", at(1_100), s).also { assertEquals(ReviewDeckMutation.UPDATED, it.mutation) }.snapshot
    s = ReviewDeckRules.applyMistake(item, "deck", at(1_200), s).also { assertEquals(ReviewDeckMutation.UPDATED, it.mutation) }.snapshot
    val reset = s.activeItems.single()
    assertEquals(2, reset.missCount)
    assertEquals(0, reset.consecutivePerfect)
    assertEquals(at(1_000), reset.addedAt)

    s = ReviewDeckRules.applyPerfect(item.id, "deck", at(2), s).snapshot
    s = ReviewDeckRules.applyPerfect(item.id, "deck", at(3), s).snapshot
    val graduated = ReviewDeckRules.applyPerfect(item.id, "deck", at(2_000), s)
    assertEquals(ReviewDeckMutation.GRADUATED, graduated.mutation)
    assertTrue(graduated.snapshot.activeItems.isEmpty())
    val stored = graduated.snapshot.items.values.single()
    assertEquals(3, stored.consecutivePerfect)
    assertEquals(at(2_000), stored.graduatedAt)
    assertEquals(ReviewDeckMutation.UNCHANGED, ReviewDeckRules.applyPerfect(item.id, "deck", at(3_000), graduated.snapshot).mutation)
    assertEquals(ReviewDeckMutation.UNCHANGED, ReviewDeckRules.applyPerfect("other", "deck", at(3_000), graduated.snapshot).mutation)
  }

  @Test
  fun reviewManualAddRemoveAndGraduatedReactivation() {
    val added = ReviewDeckRules.applyManualAdd(item, "deck", at(1), ReviewDeckSnapshot.EMPTY)
    assertEquals(ReviewDeckMutation.ADDED, added.mutation)
    assertEquals(0, added.snapshot.items.values.single().missCount)
    assertEquals(ReviewDeckMutation.UNCHANGED, ReviewDeckRules.applyManualAdd(item, "deck", at(1), added.snapshot).mutation)
    val removed = ReviewDeckRules.applyManualRemove(item.id, "deck", added.snapshot)
    assertEquals(ReviewDeckMutation.REMOVED, removed.mutation)
    assertEquals(ReviewDeckMutation.UNCHANGED, ReviewDeckRules.applyManualRemove(item.id, "deck", removed.snapshot).mutation)

    var s = ReviewDeckRules.applyMistake(item, "deck", at(1), ReviewDeckSnapshot.EMPTY).snapshot
    repeat(3) { s = ReviewDeckRules.applyPerfect(item.id, "deck", at(2), s).snapshot }
    val reactivated = ReviewDeckRules.applyManualAdd(item, "deck", at(3), s)
    assertEquals(ReviewDeckMutation.UPDATED, reactivated.mutation)
    val active = reactivated.snapshot.activeItems.single()
    assertEquals(0, active.consecutivePerfect)
    assertNull(active.graduatedAt)
  }

  @Test
  fun translatedMistakeEnrichesLegacyLocalizations() {
    var s = ReviewDeckRules.applyMistake(item, "deck", at(1_000), ReviewDeckSnapshot.EMPTY).snapshot
    assertNull(s.activeItems.single().deckItem.localizedMeaning("en"))
    assertEquals("会社", s.activeItems.single().deckItem.localizedMeaning("ja"))
    val translated = item.copy(localizations = mapOf("en" to DeckItemLocalization("company", "hoesa")))
    s = ReviewDeckRules.applyMistake(translated, "deck", at(1_100), s).snapshot
    val enriched = s.activeItems.single()
    assertEquals("company", enriched.deckItem.localizedMeaning("en"))
    assertEquals("hoesa", enriched.deckItem.localizedReading("en"))
    assertEquals(2, enriched.missCount)
  }

  @Test
  fun sourceUnavailableKeepsHistoryAndReconcileReactivates() {
    val removedItem = DeckItem(id = "item_removed", ko = "학교", readingJa = "ハッキョ", meaningJa = "学校", audio = null)
    var s = ReviewDeckRules.applyMistake(item, "deck", at(1_000), ReviewDeckSnapshot.EMPTY).snapshot
    s = ReviewDeckRules.applyMistake(removedItem, "deck", at(1_000), s).snapshot
    s = ReviewDeckRules.applyPerfect(item.id, "deck", at(1_100), s).snapshot

    val unavailable = ReviewDeckRules.applySourceUnavailable("deck", s)
    assertEquals(ReviewDeckMutation.UPDATED, unavailable.mutation)
    assertTrue(unavailable.snapshot.activeItems.isEmpty())
    assertEquals(ReviewDeckMutation.UNCHANGED, ReviewDeckRules.applySourceUnavailable("deck", unavailable.snapshot).mutation)

    val replacement = Deck(
      deckId = "deck", version = 2, name = "更新デッキ", author = DeckAuthor("user", "User"), official = false,
      type = DeckType.WORD, level = 1, tags = listOf("単語"), createdAt = at(1_000), updatedAt = at(2_000),
      items = listOf(DeckItem(id = item.id, ko = "회사원", readingJa = "フェサウォン", meaningJa = "会社員", audio = null)),
    )
    val reconciled = ReviewDeckRules.applyDeckContent(replacement, unavailable.snapshot)
    assertEquals(ReviewDeckMutation.UPDATED, reconciled.mutation)
    val updated = reconciled.snapshot.items.getValue("deck::item_company")
    assertEquals("회사원", updated.ko)
    assertEquals(1, updated.missCount)
    assertEquals(1, updated.consecutivePerfect)
    assertEquals(true, updated.isSourceAvailable)
    assertEquals(false, reconciled.snapshot.items.getValue("deck::item_removed").isSourceAvailable)
    assertEquals(ReviewDeckMutation.UNCHANGED, ReviewDeckRules.applyDeckContent(replacement, reconciled.snapshot).mutation)
  }

  @Test
  fun reviewValidationRejectsUntypeableTargetsAndInconsistentGraduation() {
    val valid = ReviewDeckItem.of(item, "deck", at(1), 1)
    assertTrue(ReviewDeckRules.isValid(valid))
    assertFalse(ReviewDeckRules.isValid(valid.copy(ko = "🙂")))
    assertFalse(ReviewDeckRules.isValid(valid.copy(ko = "가나다라마바사아자차카")))
    assertFalse(ReviewDeckRules.isValid(valid.copy(graduatedAt = at(2))))
    assertFailsWith<ReviewDeckStoreException.InvalidItem> { ReviewDeckRules.validated(listOf(valid, valid)) }
  }

  @Test
  fun sessionReviewItemMergeKeepsUniqueJamoLocationsAndAddsMistakeCounts() {
    val merged = SessionReviewItem.of(item, "deck", SessionItemResolution(0, true, 2, setOf(0, 1)))
      .merge(SessionItemResolution(0, true, 2, setOf(1, 3)))
    assertEquals(4, merged.mistakeCount)
    assertEquals(setOf(0, 1, 3), merged.mistakenJamoIndices)
    assertEquals("deck::item_company", merged.id)
  }
}
