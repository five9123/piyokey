package app.piyokey.core.hangul

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Ports iOS `FlowGameViewModelTests.testOSIMECandidateJudge*` plus selection edge cases. */
class OSIMECandidateTextJudgeTest {
  private fun select(targets: List<String>, preferred: String, committed: String, marked: String? = null) =
    assertNotNull(OSIMECandidateTextJudge.evaluate(targets, preferred, committed, marked))

  @Test
  fun prefersAnyCompletedVisibleWord() {
    val selection = select(listOf("가", "너"), "가", "너")
    assertEquals("너", selection.target)
    assertEquals(OSIMETextJudgeStatus.Matching(completed = true, isComposing = false), selection.evaluation.status)
    assertEquals("ㄴㅓ".toList(), selection.evaluation.acceptedSequence)
  }

  @Test
  fun keepsReachableCheonjiinCommittedVowelViable() {
    for (committed in listOf("ㄷㆍ", "되")) {
      val selection = select(listOf("뒤", "돼지"), "뒤", committed)
      assertEquals("돼지", selection.target, committed)
      assertEquals(OSIMETextJudgeStatus.ComposingMismatch, selection.evaluation.status, committed)
      assertEquals((if (committed == "ㄷㆍ") "ㄷ" else "ㄷㅗ").toList(), selection.evaluation.acceptedSequence)
    }
  }

  @Test
  fun keepsReachableCheonjiinConsonantCycleViable() {
    for (committed in listOf("대ㅅ", "댓")) {
      val selection = select(listOf("대자", "대형"), "대자", committed)
      assertEquals("대형", selection.target, committed)
      assertEquals(OSIMETextJudgeStatus.ComposingMismatch, selection.evaluation.status, committed)
      assertEquals("ㄷㅐ".toList(), selection.evaluation.acceptedSequence, committed)
    }
  }

  @Test
  fun keepsBatchimBoundaryIntermediateViable() {
    val boundary = select(listOf("일개", "일해"), "일개", "잀")
    assertEquals("일해", boundary.target)
    assertEquals(OSIMETextJudgeStatus.ComposingMismatch, boundary.evaluation.status)
    assertEquals("ㅇㅣㄹ".toList(), boundary.evaluation.acceptedSequence)

    val complexFinal = select(listOf("읅", "읊다"), "읅", "읇")
    assertEquals("읊다", complexFinal.target)
    assertEquals(OSIMETextJudgeStatus.ComposingMismatch, complexFinal.evaluation.status)
    assertEquals("ㅇㅡㄹ".toList(), complexFinal.evaluation.acceptedSequence)
  }

  @Test
  fun keepsDanglingComplexBatchimPrefixViable() {
    val selection = select(listOf("괜자", "괜찮아"), "괜자", "괜찬ㅅ")
    assertEquals("괜찮아", selection.target)
    assertEquals(OSIMETextJudgeStatus.ComposingMismatch, selection.evaluation.status)
    assertEquals(JamoDecomposer.keySequence("괜찬"), selection.evaluation.acceptedSequence)
  }

  @Test
  fun keepsClassCAndDIntermediatesViable() {
    for (committed in listOf("핰", "핚")) {
      val selection = select(listOf("학코", "학교"), "학코", committed)
      assertEquals("학교", selection.target, committed)
      assertEquals(OSIMETextJudgeStatus.ComposingMismatch, selection.evaluation.status, committed)
      assertEquals("ㅎㅏㄱ".toList(), selection.evaluation.acceptedSequence, committed)
    }
    val doubleDot = select(listOf("어", "요"), "어", "ㅇᆢ")
    assertEquals("요", doubleDot.target)
    assertEquals(OSIMETextJudgeStatus.ComposingMismatch, doubleDot.evaluation.status)
    assertEquals("ㅇ".toList(), doubleDot.evaluation.acceptedSequence)
  }

  @Test
  fun preferredTargetBreaksTiesThenEarliestIndex() {
    // Both "가나" and "가다" accept "ㄱㅏ"; the preferred target wins.
    assertEquals("가다", select(listOf("가나", "가다"), "가다", "가").target)
    // No preferred match among ties: earliest index wins.
    assertEquals("가나", select(listOf("가나", "가다"), "없음", "가").target)
  }

  @Test
  fun confirmedMismatchIsDroppedEvenWhenPreferred() {
    val selection = select(listOf("가다", "가나다"), "가다", "가나")
    assertEquals("가나다", selection.target)
    assertEquals(OSIMETextJudgeStatus.Matching(completed = false, isComposing = false), selection.evaluation.status)
  }

  @Test
  fun allConfirmedMismatchesStillReturnBestCandidate() {
    val selection = select(listOf("가", "나"), "나", "다")
    assertEquals("나", selection.target)
    assertIs<OSIMETextJudgeStatus.ConfirmedMismatch>(selection.evaluation.status)
  }

  @Test
  fun unsupportedAsciiIsReportedForTheChosenCandidate() {
    val selection = select(listOf("가"), "가", "a")
    assertEquals(OSIMETextJudgeStatus.UnsupportedASCIIInput, selection.evaluation.status)
  }

  @Test
  fun untypeableTargetsAreSkippedAndEmptyPoolReturnsNull() {
    assertNull(OSIMECandidateTextJudge.evaluate(emptyList(), "", "가"))
    assertNull(OSIMECandidateTextJudge.evaluate(listOf("", "ABC!"), "", "가"))
    assertEquals("가", select(listOf("", "가"), "", "가").target)
  }
}
