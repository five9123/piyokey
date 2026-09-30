package app.piyokey.core.domain.practice

import app.piyokey.core.domain.CurriculumStarRating
import app.piyokey.core.domain.SessionItemResolution
import app.piyokey.core.domain.practice.TargetSyllableProgress.State.COMPLETED
import app.piyokey.core.domain.practice.TargetSyllableProgress.State.IN_PROGRESS
import app.piyokey.core.domain.practice.TargetSyllableProgress.State.PENDING
import app.piyokey.core.hangul.JamoDecomposer
import app.piyokey.core.hangul.Korean10KeyInterpretation
import app.piyokey.core.hangul.Korean10KeyInterpreter
import app.piyokey.core.hangul.Korean10KeyKey
import app.piyokey.core.hangul.OSIMETextJudge
import app.piyokey.core.hangul.OSIMETextJudgeStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Port of iOS `PracticeSessionViewModelTests` (reducer cases). */
class PracticeSessionTest {
  private fun PracticeSession.type(keys: String) = keys.forEach { input(it) }

  @Test
  fun correctSequenceCompletesDokkaebiTarget() {
    val model = PracticeSession("가나")
    model.type("ㄱㅏㄴ")
    assertEquals("간", model.enteredText)
    assertFalse(model.isComplete)
    model.input('ㅏ')
    assertEquals("가나", model.enteredText)
    assertTrue(model.isComplete)
    assertEquals(PracticeFeedback.Complete, model.feedback)
  }

  @Test
  fun targetSyllableProgressMarksOnlyFullyAcceptedSyllables() {
    val model = PracticeSession("학교")
    assertEquals(listOf(PENDING, PENDING), model.targetSyllableProgress.map { it.state })
    assertEquals(0, model.completedSyllableCount)
    assertEquals(2, model.targetSyllableCount)

    model.input('ㅎ')
    assertEquals(listOf(IN_PROGRESS, PENDING), model.targetSyllableProgress.map { it.state })
    assertEquals(PracticeReactionEvent.CorrectJamo, model.reactionEvent)

    model.type("ㅏㄱ")
    assertEquals(listOf(COMPLETED, PENDING), model.targetSyllableProgress.map { it.state })
    assertEquals(1, model.completedSyllableCount)
    assertEquals(PracticeReactionEvent.SyllableCompleted(0), model.reactionEvent)

    model.input('ㄱ')
    assertEquals(listOf(COMPLETED, IN_PROGRESS), model.targetSyllableProgress.map { it.state })
  }

  @Test
  fun mascotReactionEventsCoverCombosMistakesWordsAndPerfectSession() {
    val combo = PracticeSession("가나다라마바사아자차카")
    val sequence = JamoDecomposer.keySequence(combo.target)
    sequence.take(5).forEach { combo.input(it) }
    assertEquals(5, combo.correctStreak)
    assertEquals(PracticeReactionEvent.ComboMilestone(5), combo.reactionEvent)
    sequence.drop(5).take(5).forEach { combo.input(it) }
    assertEquals(PracticeReactionEvent.ComboMilestone(10), combo.reactionEvent)
    sequence.drop(10).take(10).forEach { combo.input(it) }
    assertEquals(PracticeReactionEvent.ComboMilestone(20), combo.reactionEvent)
    combo.input('ㅂ')
    assertEquals(0, combo.correctStreak)
    assertEquals(PracticeReactionEvent.Mistake('ㅋ'), combo.reactionEvent)

    val perfect = PracticeSession("가")
    perfect.type("ㄱㅏ")
    assertEquals(PracticeReactionEvent.PerfectSession, perfect.reactionEvent)

    val recovered = PracticeSession("가")
    recovered.type("ㄴㄱㅏ")
    assertEquals(PracticeReactionEvent.WordCompleted, recovered.reactionEvent)
  }

  @Test
  fun incorrectInputIsCountedAndDoesNotPolluteComposition() {
    val model = PracticeSession("가")
    model.input('ㄴ')
    assertEquals("", model.enteredText)
    assertEquals(0, model.completedJamoCount)
    assertEquals(1, model.mistakeCount)
    assertEquals(PracticeFeedback.Incorrect('ㄱ'), model.feedback)
  }

  @Test
  fun mascotTracksConsecutiveMistakesAcceptedInputsAndForegroundStretch() {
    val model = PracticeSession("가")
    model.type("ㄴㄴㄴ")
    assertEquals(3, model.consecutiveMistakes)
    assertEquals(PracticeReactionEvent.Dizzy('ㄱ'), model.reactionEvent)
    model.input('ㄱ')
    assertEquals(0, model.consecutiveMistakes)
    assertEquals(1, model.totalAcceptedInputCount)
    model.publishStretchReaction()
    assertEquals(PracticeReactionEvent.Stretch, model.reactionEvent)
  }

  @Test
  fun backspaceRewindsJudgeAcrossCarryover() {
    val model = PracticeSession("가나")
    model.type("ㄱㅏㄴㅏ")
    model.backspace()
    assertEquals("간", model.enteredText)
    assertEquals(3, model.completedJamoCount)
    assertEquals('ㅏ', model.nextExpectedKey)
    assertFalse(model.isComplete)
  }

  @Test
  fun shiftJamoIsOneLogicalInput() {
    val model = PracticeSession("꿀")
    assertEquals('ㄲ', model.nextExpectedKey)
    model.type("ㄲㅜㄹ")
    assertTrue(model.isComplete)
    assertEquals(3, model.completedJamoCount)
    assertEquals("꿀", model.enteredText)
  }

  @Test
  fun resetClearsProgressAndMistakes() {
    val model = PracticeSession("가")
    model.type("ㄴㄱ")
    model.reset()
    assertEquals("", model.enteredText)
    assertEquals(0, model.completedJamoCount)
    assertEquals(0, model.mistakeCount)
    assertEquals(PracticeFeedback.Idle, model.feedback)
  }

  @Test
  fun resetRestoresFirstTargetSyllableProgressAfterLastCard() {
    val model = PracticeSession(listOf("가", "학교"))
    JamoDecomposer.keySequence("가").forEach { model.input(it) }
    model.advance()
    JamoDecomposer.keySequence("학교").forEach { model.input(it) }
    assertEquals(listOf("학", "교"), model.targetSyllableProgress.map { it.character })
    model.reset()
    assertEquals("가", model.target)
    assertEquals(listOf("가"), model.targetSyllableProgress.map { it.character })
    assertEquals(listOf(PENDING), model.targetSyllableProgress.map { it.state })
    assertEquals(1, model.targetSyllableCount)
    assertEquals(0, model.completedSyllableCount)
  }

  @Test
  fun completedProblemAdvancesWithoutClearingLessonMistakes() {
    val model = PracticeSession(listOf("가", "나"))
    model.type("ㄴㄱㅏ")
    assertTrue(model.canAdvance)
    model.advance()
    assertEquals(1, model.currentTargetIndex)
    assertEquals("나", model.target)
    assertEquals("", model.enteredText)
    assertEquals('ㄴ', model.nextExpectedKey)
    assertEquals(1, model.mistakeCount)
  }

  @Test
  fun nextTargetCheckpointCanBePersistedBeforeVisibleAdvance() {
    val model = PracticeSession(listOf("가", "나"))
    model.type("ㄷㄱㅏ")
    val restored = PracticeSession(listOf("가", "나"), model.checkpointForNextTarget())
    assertEquals(0, model.currentTargetIndex)
    assertEquals(1, restored.currentTargetIndex)
    assertEquals("나", restored.target)
    assertEquals("", restored.enteredText)
    assertEquals(1, restored.mistakeCount)
  }

  @Test
  fun completedSessionReportsJamoAccuracyAndItemCount() {
    val model = PracticeSession(listOf("가", "나"))
    model.type("ㄴㄱㅏ")
    model.advance()
    model.type("ㄴㅏ")
    assertTrue(model.isLessonComplete)
    assertEquals(4, model.acceptedJamoCount)
    assertEquals(80.0, model.accuracyPercent, 0.001)
    assertEquals(2, model.completedItemCount)
  }

  @Test
  fun completionResolutionTracksMistakeStatePerItem() {
    val model = PracticeSession(listOf("가", "나"))
    model.type("ㄴㄱㅏ")
    assertEquals(SessionItemResolution(0, true, 1, setOf(0)), model.lastCompletedItem)
    assertEquals(1, model.itemCompletionRevision)
    model.advance()
    model.type("ㄴㅏ")
    assertEquals(SessionItemResolution(1, false, 0, emptySet()), model.lastCompletedItem)
    assertEquals(2, model.itemCompletionRevision)
  }

  @Test
  fun compositionAnimationMetadataTracksOnlyAcceptedStateChanges() {
    val model = PracticeSession("가")
    model.input('ㄴ')
    assertEquals(0, model.compositionRevision)
    assertNull(model.lastAcceptedKey)
    model.input('ㄱ')
    assertEquals(1, model.compositionRevision)
    assertEquals('ㄱ', model.lastAcceptedKey)
    assertFalse(model.shouldAnimateSyllableJoin)
    model.input('ㅏ')
    assertEquals(2, model.compositionRevision)
    assertEquals('ㅏ', model.lastAcceptedKey)
    assertTrue(model.shouldAnimateSyllableJoin)
    model.backspace()
    assertEquals(3, model.compositionRevision)
    assertEquals('ㄱ', model.lastAcceptedKey)
    assertFalse(model.shouldAnimateSyllableJoin)
  }

  @Test
  fun compositionAnimationMetadataCoversCompoundVowelAndCarryover() {
    val compound = PracticeSession("외")
    compound.type("ㅇㅗ")
    compound.input('ㅣ')
    assertEquals("외", compound.enteredText)
    assertTrue(compound.shouldAnimateSyllableJoin)

    val carryover = PracticeSession("가나")
    carryover.type("ㄱㅏㄴ")
    carryover.input('ㅏ')
    assertEquals("가나", carryover.enteredText)
    assertTrue(carryover.shouldAnimateSyllableJoin)
  }

  @Test
  fun checkpointRestoresCurrentProblemInputMistakesAndDuration() {
    var now = 100.0
    val model = PracticeSession(listOf("가", "나"), now = { now })
    model.type("ㄴㄱ")
    now += 4
    model.pauseTiming()
    val restored = PracticeSession(listOf("가", "나"), model.checkpoint(), now = { now })
    assertEquals(0, restored.currentTargetIndex)
    assertEquals("ㄱ", restored.enteredText)
    assertEquals(1, restored.mistakeCount)
    assertEquals(1, restored.completedJamoCount)
    assertEquals(4.0, restored.activeDuration, 0.001)
    assertTrue(restored.hasResumableProgress)
  }

  @Test
  fun invalidCheckpointPrefixRestartsCurrentTargetButKeepsCounters() {
    val checkpoint = PracticeSession(listOf("가", "나")).checkpoint().copy(
      currentTargetIndex = 1,
      acceptedKeys = "ㄱ",
      mistakeCount = 2,
      currentTargetMistakenJamoIndices = listOf(0, 9),
    )
    val restored = PracticeSession(listOf("가", "나"), checkpoint)
    assertEquals(1, restored.currentTargetIndex)
    assertEquals("", restored.enteredText)
    assertEquals(2, restored.mistakeCount)
    assertTrue(restored.hasResumableProgress)

    val outOfRange = PracticeSession(listOf("가"), checkpoint)
    assertEquals(0, outOfRange.currentTargetIndex)
    assertEquals(0, outOfRange.mistakeCount)
  }

  @Test
  fun onlyStartedUnfinishedSessionsHaveResumableProgress() {
    val model = PracticeSession(listOf("가", "나"))
    assertFalse(model.hasResumableProgress)
    model.input('ㄴ')
    assertTrue(model.hasResumableProgress)
    model.type("ㄱㅏ")
    assertTrue(model.hasResumableProgress)
    model.advance()
    model.type("ㄴㅏ")
    assertFalse(model.hasResumableProgress)
    model.reset()
    assertFalse(model.hasResumableProgress)
  }

  @Test
  fun pausedTimingExcludesBackgroundTimeAndResumesFromForeground() {
    var now = 200.0
    val model = PracticeSession(listOf("가"), now = { now })
    model.input('ㄱ')
    now += 2
    model.pauseTiming()
    now += 30
    assertEquals(2.0, model.activeDuration, 0.001)
    model.resumeTiming()
    now += 3
    model.input('ㅏ')
    assertEquals(5.0, model.activeDuration, 0.001)
    assertEquals(24.0, model.charactersPerMinute, 0.001)
    // The lesson is complete: timing stays paused.
    now += 10
    model.resumeTiming()
    assertEquals(5.0, model.activeDuration, 0.001)
  }

  @Test
  fun starsFromSessionUseCurriculumRating() {
    var now = 0.0
    val model = PracticeSession(listOf("가나"), now = { now })
    model.input('ㄱ')
    now += 3
    model.type("ㅏㄴㅏ")
    // 4 jamo in 3 s → 80 CPM, 100% accuracy → 3 stars.
    assertEquals(3, CurriculumStarRating.stars(model.accuracyPercent, model.charactersPerMinute))
  }

  @Test
  fun osImeSynchronizationCanAdvanceAndRewindWithoutLosingSessionState() {
    val model = PracticeSession(listOf("가나"))
    model.synchronizeOsIme("ㄱㅏㄴ".toList())
    assertEquals("간", model.enteredText)
    assertEquals(3, model.completedJamoCount)
    model.synchronizeOsIme("ㄱㅏ".toList())
    assertEquals("가", model.enteredText)
    assertEquals(2, model.completedJamoCount)
    model.synchronizeOsIme("ㄱㅏㄴㅏ".toList())
    assertTrue(model.isComplete)
    assertEquals("가나", model.enteredText)
    // Not a prefix of the target: ignored.
    val other = PracticeSession("가")
    other.synchronizeOsIme("ㄴ".toList())
    assertEquals(0, other.completedJamoCount)
    assertEquals(0, other.mistakeCount)
  }

  @Test
  fun osImePhysicalKeyboardSnapshotsDoNotDuplicateMarkedCommitOrBackspace() {
    val model = PracticeSession(listOf("한국 사람"))
    val marked = "ㅎㅏㄴㄱㅜㄱ ㅅㅏ".toList()
    model.synchronizeOsIme(marked)
    assertEquals("한국 사", model.enteredText)
    assertEquals(marked.size, model.totalAcceptedInputCount)
    model.synchronizeOsIme(marked)
    assertEquals("한국 사", model.enteredText)
    assertEquals(marked.size, model.totalAcceptedInputCount)
    model.synchronizeOsIme("ㅎㅏㄴㄱㅜㄱ ".toList())
    assertEquals("한국 ", model.enteredText)
    assertEquals(0, model.mistakeCount)
    model.synchronizeOsIme("ㅎㅏㄴㄱㅜㄱ ㅅㅏㄹㅏㅁ".toList())
    assertTrue(model.isComplete)
    assertEquals("한국 사람", model.enteredText)
    assertEquals(0, model.mistakeCount)
  }

  @Test
  fun confirmedOsImeMistakeCountsOnceWithoutPollutingComposition() {
    val model = PracticeSession("가")
    model.recordConfirmedMistake()
    assertEquals(1, model.mistakeCount)
    assertEquals(0, model.completedJamoCount)
    assertEquals("", model.enteredText)
    assertEquals(PracticeFeedback.Incorrect('ㄱ'), model.feedback)
    model.type("ㄱㅏ")
    model.recordConfirmedMistake()
    assertEquals(1, model.mistakeCount)
  }

  @Test
  fun osImeReachableCheonjiinCommittedVowelsDoNotCountMistakesOrRollback() {
    val model = PracticeSession(listOf("돼지"))
    val snapshots = listOf("ㄷㆍ" to "ㄷ", "도" to "도", "되" to "도", "돠" to "도")
    for ((committed, accepted) in snapshots) {
      val evaluation = OSIMETextJudge.evaluate(model.target, committed, null)
      model.synchronizeOsIme(evaluation.acceptedSequence)
      if (evaluation.status is OSIMETextJudgeStatus.ConfirmedMismatch) fail("reachable snapshot rolled back: $committed")
      assertEquals(accepted, model.enteredText, committed)
      assertEquals(0, model.mistakeCount, committed)
    }
    val completed = OSIMETextJudge.evaluate("돼지", "돼지", null)
    model.synchronizeOsIme(completed.acceptedSequence)
    assertTrue(model.isComplete)
    assertEquals("돼지", model.enteredText)
    assertEquals(0, model.mistakeCount)
  }

  @Test
  fun korean10KeyPendingMatchingCompletedFlickCountsOneMistakeWithoutAdvancing() {
    val model = PracticeSession("가")
    var interpreter = Korean10KeyInterpreter()
    var step = interpreter.input(Korean10KeyKey.GIYEOK, model.nextExpectedKey)
    interpreter = step.interpreter
    assertEquals(Korean10KeyInterpretation.Committed('ㄱ'), step.interpretation)
    model.input('ㄱ')
    step = interpreter.input(Korean10KeyKey.VERTICAL, model.nextExpectedKey)
    interpreter = step.interpreter
    assertEquals(Korean10KeyInterpretation.Pending("ㅣ"), step.interpretation)
    step = interpreter.inputCompletedJamo('ㅏ', model.nextExpectedKey)
    interpreter = step.interpreter
    assertEquals(Korean10KeyInterpretation.Incorrect('ㅏ'), step.interpretation)
    model.recordConfirmedMistake()
    assertEquals(1, model.mistakeCount)
    assertEquals(1, model.completedJamoCount)
    assertEquals('ㅏ', model.nextExpectedKey)
    assertEquals("ㄱ", model.enteredText)
    assertEquals(Korean10KeyKey.VERTICAL, interpreter.nextKey(model.nextExpectedKey))
  }

  @Test
  fun korean10KeyEmitsOnlyCompletedJamoIntoSharedJudge() {
    for (target in listOf("가나", "꽤", "뼈", "휘", "의자", "언니", "띄어 쓰기", "외국")) {
      val model = PracticeSession(target)
      var interpreter = Korean10KeyInterpreter()
      for (jamo in JamoDecomposer.keySequence(target)) {
        val recipe = Korean10KeyInterpreter.recipe(jamo) ?: fail("no recipe for $jamo")
        if (interpreter.nextKey(model.nextExpectedKey) == Korean10KeyKey.NEXT) {
          val step = interpreter.input(Korean10KeyKey.NEXT, model.nextExpectedKey)
          interpreter = step.interpreter
          assertEquals(Korean10KeyInterpretation.SeparatorAccepted, step.interpretation)
        }
        recipe.forEachIndexed { index, key ->
          val step = interpreter.input(key, model.nextExpectedKey)
          interpreter = step.interpreter
          val result = step.interpretation
          if (index == recipe.lastIndex) {
            val emitted = (result as? Korean10KeyInterpretation.Committed)?.jamo ?: fail("expected commit of $jamo, got $result")
            assertEquals(jamo, emitted)
            model.input(emitted)
          } else if (result is Korean10KeyInterpretation.Pending) {
            assertEquals(jamo, model.nextExpectedKey)
          } else {
            fail("Recipe for $jamo committed too early")
          }
        }
      }
      assertTrue(model.isComplete, target)
      assertEquals(target, model.enteredText)
      assertEquals(0, model.mistakeCount)
    }
  }

  @Test
  fun inputAfterCompletionIsIgnored() {
    val model = PracticeSession("가")
    model.type("ㄱㅏ")
    val revision = model.feedbackRevision
    model.input('ㄴ')
    assertEquals(revision, model.feedbackRevision)
    assertEquals(0, model.mistakeCount)
  }
}
