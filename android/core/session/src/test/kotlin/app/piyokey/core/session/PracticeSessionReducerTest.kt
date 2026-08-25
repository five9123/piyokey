package app.piyokey.core.session

import app.piyokey.core.hangul.CompositionPhase
import app.piyokey.core.hangul.JamoDecompositionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PracticeSessionReducerTest {
  @Test
  fun activeDurationFreezesInBackgroundAndResumesWithoutCountingTheGap() {
    var clock = ActiveDurationClock().start(1_000)
    clock = clock.pause(2_500)
    assertEquals(1_500, clock.duration(100_000))
    clock = clock.start(100_000)
    assertEquals(2_000, clock.duration(100_500))
  }
  @Test
  fun checkpointRestoresCompletedProblemsCurrentPrefixAndMistakes() {
    var state = PracticeSessionReducer.initialState(listOf("가", "나"))
    state = PracticeSessionReducer.reduce(state, PracticeSessionEvent.Key('ㄱ')).state
    state = PracticeSessionReducer.reduce(state, PracticeSessionEvent.Key('ㅏ')).state
    state = PracticeSessionReducer.reduce(
      state,
      PracticeSessionEvent.Advance(requireNotNull(state.pendingTransition).token),
    ).state
    state = PracticeSessionReducer.reduce(state, PracticeSessionEvent.Key('ㄷ')).state
    state = PracticeSessionReducer.reduce(state, PracticeSessionEvent.Key('ㄴ')).state
    val checkpoint = PracticeSessionReducer.checkpoint(state, 1_234)

    val restored = PracticeSessionReducer.restoreState(listOf("가", "나"), checkpoint)

    assertEquals(1, restored.currentTargetIndex)
    assertEquals("ㄴ", restored.acceptedKeys.joinToString(""))
    assertEquals(1, restored.mistakeCount)
    assertEquals(1, restored.itemResolutions.size)
    assertEquals(1_234, checkpoint.activeDurationMillis)
  }
  @Test
  fun `dokkaebi target is judged by jamo sequence and schedules one transition`() {
    var state = PracticeSessionReducer.initialState("가나")

    state = state.type("ㄱㅏㄴ")
    assertEquals("간", state.enteredText)
    assertFalse(state.isCurrentTargetComplete)
    assertEquals('ㅏ', state.nextExpected)

    val reduction = PracticeSessionReducer.reduce(state, PracticeSessionEvent.Key('ㅏ'))
    state = reduction.state

    assertEquals("가나", state.enteredText)
    assertTrue(state.isCurrentTargetComplete)
    assertTrue(state.isSessionInputComplete)
    assertEquals(PracticeFeedback.Complete, state.feedback)
    assertEquals(1, state.completedItemCount)
    val effect = assertIs<PracticeSessionEffect.ScheduleAdvance>(reduction.effects.single())
    assertEquals(PRACTICE_AUTO_ADVANCE_DELAY_MILLIS, effect.delayMillis)
    assertEquals(PracticeCompletionDestination.RESULTS, effect.destination)
    assertEquals(effect.transitionToken, state.pendingTransition?.token)
  }

  @Test
  fun `wrong key records a mistake and never contaminates accepted composition`() {
    val initial = PracticeSessionReducer.initialState("가")

    val state = PracticeSessionReducer.reduce(
      initial,
      PracticeSessionEvent.Key('ㄴ'),
    ).state

    assertEquals(emptyList(), state.acceptedKeys)
    assertEquals("", state.enteredText)
    assertEquals("", state.composingText)
    assertEquals(0, state.completedJamoCount)
    assertEquals(1, state.mistakeCount)
    assertEquals(1, state.currentItemMistakeCount)
    assertEquals(setOf(0), state.currentItemMistakenJamoIndices)
    assertEquals(PracticeFeedback.Incorrect('ㄱ'), state.feedback)
    assertEquals(initial.compositionRevision, state.compositionRevision)
    assertNull(state.pendingTransition)
  }

  @Test
  fun `shift jamo is one logical accepted key`() {
    var state = PracticeSessionReducer.initialState("꿀")

    assertEquals(listOf('ㄲ', 'ㅜ', 'ㄹ'), state.targetJamoSequence)
    assertEquals('ㄲ', state.nextExpected)
    state = state.type("ㄲㅜㄹ")

    assertEquals(listOf('ㄲ', 'ㅜ', 'ㄹ'), state.acceptedKeys)
    assertEquals(3, state.acceptedJamoCount)
    assertEquals("꿀", state.enteredText)
    assertTrue(state.isCurrentTargetComplete)
  }

  @Test
  fun `backspace rebuilds judge and composition across carryover`() {
    var state = PracticeSessionReducer.initialState("가나").type("ㄱㅏㄴㅏ")
    val staleToken = requireNotNull(state.pendingTransition).token

    state = PracticeSessionReducer.reduce(state, PracticeSessionEvent.Backspace).state

    assertEquals("간", state.enteredText)
    assertEquals("간", state.composingText)
    assertEquals(CompositionPhase.CHO_JUNG_JONG, state.composition.phase)
    assertEquals(3, state.completedJamoCount)
    assertEquals('ㅏ', state.nextExpected)
    assertFalse(state.isCurrentTargetComplete)
    assertEquals(0, state.completedItemCount)
    assertNull(state.pendingTransition)
    assertFalse(state.shouldAnimateSyllableJoin)

    val stale = PracticeSessionReducer.reduce(
      state,
      PracticeSessionEvent.Advance(staleToken),
    )
    assertSame(state, stale.state)
    assertTrue(stale.effects.isEmpty())
  }

  @Test
  fun `completed item advances only with its matching transition token`() {
    var state = PracticeSessionReducer.initialState(listOf("가", "나"))
    state = PracticeSessionReducer.reduce(state, PracticeSessionEvent.Key('ㄴ')).state
    state = state.type("ㄱㅏ")
    val transition = requireNotNull(state.pendingTransition)

    assertTrue(state.canAdvance)
    assertEquals(PracticeCompletionDestination.NEXT_TARGET, transition.destination)
    assertEquals(
      PracticeItemResolution(
        itemIndex = 0,
        mistakeCount = 1,
        mistakenJamoIndices = setOf(0),
      ),
      state.lastCompletedItem,
    )

    val stale = PracticeSessionReducer.reduce(
      state,
      PracticeSessionEvent.Advance(transition.token + 1),
    )
    assertSame(state, stale.state)

    state = PracticeSessionReducer.reduce(
      state,
      PracticeSessionEvent.Advance(transition.token),
    ).state
    assertEquals(1, state.currentTargetIndex)
    assertEquals("나", state.currentTarget)
    assertEquals('ㄴ', state.nextExpected)
    assertEquals("", state.enteredText)
    assertEquals(1, state.mistakeCount)
    assertEquals(1, state.completedItemCount)
    assertNull(state.pendingTransition)
  }

  @Test
  fun `accuracy and per-item resolution cover the whole session`() {
    var state = PracticeSessionReducer.initialState(listOf("가", "나"))
    state = PracticeSessionReducer.reduce(state, PracticeSessionEvent.Key('ㄴ')).state
    state = state.type("ㄱㅏ")
    state = state.advancePending()
    state = state.type("ㄴㅏ")

    assertEquals(4, state.acceptedJamoCount)
    assertEquals(5, state.totalInputCount)
    assertEquals(80.0, state.accuracyPercent, absoluteTolerance = 0.0001)
    assertEquals(2, state.completedItemCount)
    assertEquals(1, state.itemResolutions[0].mistakeCount)
    assertTrue(state.itemResolutions[0].hadMistake)
    assertEquals(0, state.itemResolutions[1].mistakeCount)
    assertFalse(state.itemResolutions[1].hadMistake)

    val token = requireNotNull(state.pendingTransition).token
    val finished = PracticeSessionReducer.reduce(
      state,
      PracticeSessionEvent.Advance(token),
    )
    assertTrue(finished.state.isResultReady)
    assertNull(finished.state.pendingTransition)
    assertEquals(PracticeSessionEffect.SessionCompleted(token), finished.effects.single())
  }

  @Test
  fun `restart clears learning progress but never reuses a delayed token`() {
    var state = PracticeSessionReducer.initialState(listOf("가", "나"))
    state = PracticeSessionReducer.reduce(state, PracticeSessionEvent.Key('ㄷ')).state
    state = state.type("ㄱㅏ")
    val oldToken = requireNotNull(state.pendingTransition).token

    state = PracticeSessionReducer.reduce(state, PracticeSessionEvent.Restart).state

    assertEquals(0, state.currentTargetIndex)
    assertEquals("가", state.currentTarget)
    assertEquals("", state.enteredText)
    assertEquals(0, state.mistakeCount)
    assertEquals(0, state.completedItemCount)
    assertEquals(0.0, state.accuracyPercent)
    assertNull(state.pendingTransition)
    assertFalse(state.isResultReady)

    state = state.type("ㄱㅏ")
    val newToken = requireNotNull(state.pendingTransition).token
    assertNotEquals(oldToken, newToken)

    val stale = PracticeSessionReducer.reduce(
      state,
      PracticeSessionEvent.Advance(oldToken),
    )
    assertSame(state, stale.state)
    assertEquals(0, stale.state.currentTargetIndex)
  }

  @Test
  fun `target syllable progress distinguishes pending in-progress and completed units`() {
    var state = PracticeSessionReducer.initialState("학교")
    assertEquals(
      listOf(TargetSyllableState.PENDING, TargetSyllableState.PENDING),
      state.targetSyllableProgress.map(TargetSyllableProgress::state),
    )
    assertEquals(0, state.completedSyllableCount)
    assertEquals(2, state.targetSyllableCount)

    state = PracticeSessionReducer.reduce(state, PracticeSessionEvent.Key('ㅎ')).state
    assertEquals(
      listOf(TargetSyllableState.IN_PROGRESS, TargetSyllableState.PENDING),
      state.targetSyllableProgress.map(TargetSyllableProgress::state),
    )

    state = state.type("ㅏㄱ")
    assertEquals(
      listOf(TargetSyllableState.COMPLETED, TargetSyllableState.PENDING),
      state.targetSyllableProgress.map(TargetSyllableProgress::state),
    )
    assertEquals(1, state.completedSyllableCount)

    state = PracticeSessionReducer.reduce(state, PracticeSessionEvent.Key('ㄱ')).state
    assertEquals(
      listOf(TargetSyllableState.COMPLETED, TargetSyllableState.IN_PROGRESS),
      state.targetSyllableProgress.map(TargetSyllableProgress::state),
    )
  }

  @Test
  fun `join metadata covers basic compound-vowel and carryover joins`() {
    var basic = PracticeSessionReducer.initialState("가")
    basic = PracticeSessionReducer.reduce(basic, PracticeSessionEvent.Key('ㄱ')).state
    assertFalse(basic.shouldAnimateSyllableJoin)
    basic = PracticeSessionReducer.reduce(basic, PracticeSessionEvent.Key('ㅏ')).state
    assertTrue(basic.shouldAnimateSyllableJoin)
    assertEquals(2, basic.compositionRevision)

    var compound = PracticeSessionReducer.initialState("외").type("ㅇㅗ")
    compound = PracticeSessionReducer.reduce(compound, PracticeSessionEvent.Key('ㅣ')).state
    assertEquals("외", compound.enteredText)
    assertTrue(compound.shouldAnimateSyllableJoin)

    var carryover = PracticeSessionReducer.initialState("가나").type("ㄱㅏㄴ")
    carryover = PracticeSessionReducer.reduce(carryover, PracticeSessionEvent.Key('ㅏ')).state
    assertEquals("가나", carryover.enteredText)
    assertTrue(carryover.shouldAnimateSyllableJoin)
  }

  @Test
  fun `invalid target collections fail before a session exists`() {
    assertFailsWith<IllegalArgumentException> {
      PracticeSessionReducer.initialState(emptyList())
    }
    assertFailsWith<JamoDecompositionException.EmptyTarget> {
      PracticeSessionReducer.initialState(listOf("가", ""))
    }
    assertFailsWith<JamoDecompositionException.UnsupportedCharacter> {
      PracticeSessionReducer.initialState(listOf("가", "A"))
    }
  }

  @Test
  fun `initial state snapshots a mutable target list`() {
    val targets = mutableListOf("가", "나")
    val state = PracticeSessionReducer.initialState(targets)

    targets[0] = "다"
    targets += "라"

    assertEquals(listOf("가", "나"), state.targets)
    assertEquals("가", state.currentTarget)
  }

  @Test
  fun `empty backspace and key after completion are no-ops`() {
    val initial = PracticeSessionReducer.initialState("가")
    assertSame(
      initial,
      PracticeSessionReducer.reduce(initial, PracticeSessionEvent.Backspace).state,
    )

    val complete = initial.type("ㄱㅏ")
    assertSame(
      complete,
      PracticeSessionReducer.reduce(complete, PracticeSessionEvent.Key('ㄴ')).state,
    )
  }

  private fun PracticeSessionState.type(keys: String): PracticeSessionState =
    keys.fold(this) { state, key ->
      PracticeSessionReducer.reduce(state, PracticeSessionEvent.Key(key)).state
    }

  private fun PracticeSessionState.advancePending(): PracticeSessionState {
    val token = requireNotNull(pendingTransition).token
    return PracticeSessionReducer.reduce(this, PracticeSessionEvent.Advance(token)).state
  }
}
