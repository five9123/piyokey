package app.piyokey.core.hangul

import kotlin.test.Test
import kotlin.test.assertEquals

class OSIMETextJudgeTest {
  @Test
  fun confirmedTextPassesRepresentativeCarryoverAndShiftCases() {
    val carryover = OSIMETextJudge.evaluate(target = "달가", committedText = "닭")
    assertEquals(
      OSIMETextEvaluation(
        status = OSIMETextJudgeStatus.Matching(completed = false, isComposing = false),
        acceptedSequence = "ㄷㅏㄹㄱ".toList(),
      ),
      carryover,
    )

    val shifted = OSIMETextJudge.evaluate(target = "꿀", committedText = "꿀")
    assertEquals("ㄲㅜㄹ".toList(), shifted.acceptedSequence)
    assertEquals(
      OSIMETextJudgeStatus.Matching(completed = true, isComposing = false),
      shifted.status,
    )
  }

  @Test
  fun markedMismatchDoesNotCountUntilConfirmed() {
    val composing = OSIMETextJudge.evaluate(
      target = "가나",
      committedText = "가",
      markedText = "다",
    )
    assertEquals(OSIMETextJudgeStatus.ComposingMismatch, composing.status)
    assertEquals("ㄱㅏ".toList(), composing.acceptedSequence)

    val confirmed = OSIMETextJudge.evaluate(target = "가나", committedText = "가다")
    assertEquals(OSIMETextJudgeStatus.ConfirmedMismatch(expectedIndex = 2), confirmed.status)
    assertEquals("ㄱㅏ".toList(), confirmed.acceptedSequence)
  }

  @Test
  fun correctMarkedProgressDeletionAndExtraTextMatchSwiftContract() {
    val composing = OSIMETextJudge.evaluate(
      target = "가나",
      committedText = "가",
      markedText = "ㄴ",
    )
    assertEquals(
      OSIMETextJudgeStatus.Matching(completed = false, isComposing = true),
      composing.status,
    )
    assertEquals("ㄱㅏㄴ".toList(), composing.acceptedSequence)

    val deletion = OSIMETextJudge.evaluate(target = "가나", committedText = "")
    assertEquals(
      OSIMETextJudgeStatus.Matching(completed = false, isComposing = false),
      deletion.status,
    )
    assertEquals(emptyList(), deletion.acceptedSequence)

    val extra = OSIMETextJudge.evaluate(target = "가", committedText = "가가")
    assertEquals(
      OSIMETextJudgeStatus.Matching(completed = true, isComposing = false),
      extra.status,
    )
    assertEquals("ㄱㅏ".toList(), extra.acceptedSequence)

    val unsupportedAfterCompletion = OSIMETextJudge.evaluate(target = "가", committedText = "가A")
    assertEquals(
      OSIMETextJudgeStatus.Matching(completed = true, isComposing = false),
      unsupportedAfterCompletion.status,
    )

    val unsupported = OSIMETextJudge.evaluate(target = "가나", committedText = "가A")
    assertEquals(OSIMETextJudgeStatus.ConfirmedMismatch(expectedIndex = 2), unsupported.status)
    assertEquals("ㄱㅏ".toList(), unsupported.acceptedSequence)
  }

  @Test
  fun unsupportedMarkedTextStaysUnconfirmedAndCompletedMarkedTextPasses() {
    val unsupportedMarked = OSIMETextJudge.evaluate(
      target = "가나",
      committedText = "가",
      markedText = "A",
    )
    assertEquals(OSIMETextJudgeStatus.ComposingMismatch, unsupportedMarked.status)
    assertEquals("ㄱㅏ".toList(), unsupportedMarked.acceptedSequence)

    val completedMarked = OSIMETextJudge.evaluate(
      target = "가나",
      committedText = "가",
      markedText = "나",
    )
    assertEquals(
      OSIMETextJudgeStatus.Matching(completed = true, isComposing = true),
      completedMarked.status,
    )
    assertEquals("ㄱㅏㄴㅏ".toList(), completedMarked.acceptedSequence)

    val invalidAtStart = OSIMETextJudge.evaluate(target = "가", committedText = "A")
    assertEquals(OSIMETextJudgeStatus.ConfirmedMismatch(expectedIndex = 0), invalidAtStart.status)
    assertEquals(emptyList(), invalidAtStart.acceptedSequence)

    val confirmedAtStart = OSIMETextJudge.evaluate(target = "가", committedText = "나")
    assertEquals(OSIMETextJudgeStatus.ConfirmedMismatch(expectedIndex = 0), confirmedAtStart.status)
    assertEquals(emptyList(), confirmedAtStart.acceptedSequence)

    val invalidAfterMismatch = OSIMETextJudge.evaluate(target = "가", committedText = "나A")
    assertEquals(
      OSIMETextJudgeStatus.ConfirmedMismatch(expectedIndex = 0),
      invalidAfterMismatch.status,
    )

    val explicitEmptyMarked = OSIMETextJudge.evaluate(
      target = "가나",
      committedText = "가",
      markedText = "",
    )
    assertEquals(
      OSIMETextJudgeStatus.Matching(completed = false, isComposing = false),
      explicitEmptyMarked.status,
    )

    val extraMarked = OSIMETextJudge.evaluate(
      target = "가",
      committedText = "ㄱ",
      markedText = "ㅏㄴ",
    )
    assertEquals(OSIMETextJudgeStatus.ComposingMismatch, extraMarked.status)
    assertEquals("ㄱ".toList(), extraMarked.acceptedSequence)
  }
}
