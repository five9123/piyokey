package app.piyokey.core.hangul

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class OSIMETextJudgeTest {
  private val composingMismatch = OSIMETextJudgeStatus.ComposingMismatch
  private fun matching(completed: Boolean, isComposing: Boolean) =
    OSIMETextJudgeStatus.Matching(completed, isComposing)
  private fun confirmed(index: Int) = OSIMETextJudgeStatus.ConfirmedMismatch(index)
  private fun seq(text: String) = text.toList()
  private fun eval(target: String, committed: String, marked: String? = null) =
    OSIMETextJudge.evaluate(target = target, committedText = committed, markedText = marked)

  @Test
  fun textDiffUsesJamoPrefixesAcrossCarryoverAndShift() {
    assertEquals(
      OSIMETextEvaluation(matching(completed = false, isComposing = false), seq("ㄷㅏㄹㄱ")),
      eval("달가", "닭"),
    )

    val shifted = eval("꿀", "꿀")
    assertEquals(seq("ㄲㅜㄹ"), shifted.acceptedSequence)
    assertEquals(matching(completed = true, isComposing = false), shifted.status)
  }

  @Test
  fun markedMismatchDoesNotCountUntilConfirmed() {
    val composing = eval("가나", "가", "다")
    assertEquals(composingMismatch, composing.status)
    assertEquals(seq("ㄱㅏ"), composing.acceptedSequence)

    val confirmedResult = eval("가나", "가다")
    assertEquals(confirmed(2), confirmedResult.status)
    assertEquals(seq("ㄱㅏ"), confirmedResult.acceptedSequence)
  }

  @Test
  fun reachableCheonjiinCommittedVowelsRemainCompositionInProgress() {
    val snapshots = listOf(
      Triple("돼지", "되", "ㄷㅗ"),
      Triple("돼지", "돠", "ㄷㅗ"),
      Triple("과자", "괴", "ㄱㅗ"),
      Triple("웨딩", "워", "ㅇㅜ"),
      Triple("대형", "다", "ㄷ"),
      Triple("세계", "서", "ㅅ"),
      Triple("얘", "야", "ㅇ"),
      Triple("예", "여", "ㅇ"),
    )
    for ((target, committed, accepted) in snapshots) {
      val evaluation = eval(target, committed)
      assertEquals(composingMismatch, evaluation.status, target)
      assertEquals(seq(accepted), evaluation.acceptedSequence, target)
    }
  }

  @Test
  fun measuredCommittedCheonjiinStrokePrefixDoesNotRollback() {
    val allTapSnapshots = listOf(
      Triple("ㄷ", matching(completed = false, isComposing = false), "ㄷ"),
      Triple("ㄷㆍ", composingMismatch, "ㄷ"),
      Triple("도", matching(completed = false, isComposing = false), "ㄷㅗ"),
      Triple("되", composingMismatch, "ㄷㅗ"),
      Triple("돠", composingMismatch, "ㄷㅗ"),
      Triple("돼", matching(completed = true, isComposing = false), "ㄷㅗㅐ"),
    )
    for ((committed, status, accepted) in allTapSnapshots) {
      val evaluation = eval("돼", committed)
      assertEquals(status, evaluation.status, committed)
      assertEquals(seq(accepted), evaluation.acceptedSequence, committed)
      assertFalse(evaluation.status is OSIMETextJudgeStatus.ConfirmedMismatch, "rollback: $committed")
    }

    for (committed in listOf("ㄷ", "도", "돠", "돼")) {
      assertFalse(eval("돼", committed).status is OSIMETextJudgeStatus.ConfirmedMismatch, committed)
    }

    val afterAcceptedWordPrefix = eval("줘도 돼", "줘도 ㄷㆍ")
    assertEquals(composingMismatch, afterAcceptedWordPrefix.status)
    assertEquals(seq("ㅈㅜㅓㄷㅗ ㄷ"), afterAcceptedWordPrefix.acceptedSequence)

    val unreachableDot = eval("뒤", "ㄷㆍ")
    assertEquals(confirmed(1), unreachableDot.status)
    assertEquals(seq("ㄷ"), unreachableDot.acceptedSequence)

    val arbitraryJamo = eval("돼", "ㄷㆍㄱ")
    assertEquals(confirmed(1), arbitraryJamo.status)
    assertEquals(seq("ㄷ"), arbitraryJamo.acceptedSequence)
  }

  @Test
  fun standaloneDotFirstVowelPrefixesRemainCompositionInProgress() {
    val snapshots = listOf("ㅓ" to "ㆍ", "ㅗ" to "ㆍ", "ㅔ" to "ㆍ", "ㅔ" to "ㆍㅣ", "ㅕ" to "ㆍ", "ㅕ" to "ㆍㆍ")
    for ((target, committed) in snapshots) {
      val evaluation = eval(target, committed)
      assertEquals(composingMismatch, evaluation.status, "$target: $committed")
      assertEquals(emptyList(), evaluation.acceptedSequence, "$target: $committed")
    }

    val wrongStroke = eval("ㅗ", "ㆍㅣ")
    assertEquals(confirmed(0), wrongStroke.status)
    assertEquals(emptyList(), wrongStroke.acceptedSequence)
  }

  @Test
  fun cheonjiinConsonantCyclesRemainCompositionInProgress() {
    val snapshots = listOf(
      Triple("ㄹ", "ㄴ", ""),
      Triple("하", "ㅅ", ""),
      Triple("대형", "대ㅅ", "ㄷㅐ"),
      Triple("대형", "댓", "ㄷㅐ"),
      Triple("달", "단", "ㄷㅏ"),
      Triple("밤", "방", "ㅂㅏ"),
    )
    for ((target, committed, accepted) in snapshots) {
      val evaluation = eval(target, committed)
      assertEquals(composingMismatch, evaluation.status, "$target: $committed")
      assertEquals(seq(accepted), evaluation.acceptedSequence, "$target: $committed")
    }

    assertEquals(confirmed(0), eval("하", "ㅈ").status)
    assertEquals(confirmed(2), eval("대형", "대성").status)
    assertEquals(confirmed(2), eval("달", "담").status)
  }

  @Test
  fun closedBatchimBoundariesFollowOnlyTheNextOnsetRecipe() {
    val snapshots = listOf(
      Triple("일해", "잀", "ㅇㅣㄹ"),
      Triple("말해", "맔", "ㅁㅏㄹ"),
      Triple("말했다", "맔", "ㅁㅏㄹ"),
      Triple("급해", "긊", "ㄱㅡㅂ"),
      Triple("입학", "잆", "ㅇㅣㅂ"),
      Triple("번째", "벉", "ㅂㅓㄴ"),
      Triple("각하", "갃", "ㄱㅏㄱ"),
    )
    for ((target, committed, accepted) in snapshots) {
      val intermediate = eval(target, committed)
      assertEquals(composingMismatch, intermediate.status, target)
      assertEquals(seq(accepted), intermediate.acceptedSequence, target)
      assertEquals(matching(completed = true, isComposing = false), eval(target, target).status, target)
    }
  }

  @Test
  fun complexBatchimAssemblyUsesOnlyExactTargetComponents() {
    val snapshots = listOf(
      Triple("읽어", "인", "ㅇㅣ"),
      Triple("닭", "단", "ㄷㅏ"),
      Triple("삶", "산", "ㅅㅏ"),
      Triple("앓다", "안", "ㅇㅏ"),
      Triple("앓다", "앐", "ㅇㅏㄹ"),
      Triple("읊다", "은", "ㅇㅡ"),
      Triple("읊다", "읇", "ㅇㅡㄹ"),
    )
    for ((target, committed, accepted) in snapshots) {
      val intermediate = eval(target, committed)
      assertEquals(composingMismatch, intermediate.status, target)
      assertEquals(seq(accepted), intermediate.acceptedSequence, target)
    }
    for (target in listOf("읽어", "닭", "삶", "많이", "앓다", "읊다")) {
      assertEquals(matching(completed = true, isComposing = false), eval(target, target).status, target)
    }
  }

  @Test
  fun reachableDanglingComplexBatchimPrefixesRemainCompositionInProgress() {
    val snapshots = listOf(
      Triple("괜찮아", "괜찬ㅅ", "괜찬"),
      Triple("찮아", "찬ㅅ", "찬"),
      Triple("않아", "안ㅅ", "안"),
      Triple("많이", "만ㅅ", "만"),
      Triple("삶", "살ㅇ", "살"),
      Triple("핥다", "할ㄷ", "할"),
      Triple("읊다", "을ㅂ", "을"),
      Triple("앓다", "알ㅅ", "알"),
    )
    for ((target, committed, stable) in snapshots) {
      val evaluation = eval(target, committed)
      assertEquals(composingMismatch, evaluation.status, "$target: $committed")
      assertEquals(JamoDecomposer.keySequence(stable), evaluation.acceptedSequence, "$target: $committed")
    }
  }

  @Test
  fun batchimBoundaryNegativeMatrixRemainsConfirmed() {
    val snapshots = listOf(
      Triple("일해", "읽", 3),
      Triple("급해", "긁", 2),
      Triple("닭", "닮", 3),
      Triple("앓다", "앎", 3),
      Triple("읊다", "읅", 3),
      Triple("삶", "살ㅅ", 3),
      Triple("많이", "만ㅈ", 3),
      Triple("않아", "안ㅈ", 3),
      Triple("읽어", "읽아", 5),
      Triple("일해", "일개", 3),
    )
    for ((target, committed, mismatch) in snapshots) {
      assertEquals(confirmed(mismatch), eval(target, committed).status, "$target: $committed")
    }
  }

  @Test
  fun sameRecipeBatchimBoundaryCyclesPreserveTheClosedSyllable() {
    val snapshots = listOf(
      Triple("학교", "핰", "ㅎㅏㄱ"),
      Triple("학교", "핚", "ㅎㅏㄱ"),
      Triple("각각", "갘", "ㄱㅏㄱ"),
      Triple("닫다", "닽", "ㄷㅏㄷ"),
      Triple("십분", "싶", "ㅅㅣㅂ"),
      Triple("옷사", "옿", "ㅇㅗㅅ"),
      Triple("잊지", "잋", "ㅇㅣㅈ"),
      Triple("만나", "말", "ㅁㅏㄴ"),
      Triple("공원", "곰", "ㄱㅗㅇ"),
    )
    for ((target, committed, accepted) in snapshots) {
      val evaluation = eval(target, committed)
      assertEquals(composingMismatch, evaluation.status, target)
      assertEquals(seq(accepted), evaluation.acceptedSequence, target)
    }

    val confirmedBoundary = eval("학교", "학ㄱ")
    assertEquals(matching(completed = false, isComposing = false), confirmedBoundary.status)
    assertEquals(seq("ㅎㅏㄱㄱ"), confirmedBoundary.acceptedSequence)

    val completed = eval("학교", "학교")
    assertEquals(matching(completed = true, isComposing = false), completed.status)
    assertEquals(seq("ㅎㅏㄱㄱㅛ"), completed.acceptedSequence)
  }

  @Test
  fun sameRecipeBatchimBoundaryCycleDoesNotAcceptWrongBoundaries() {
    val negatives = listOf(
      Triple("학교", "핱", 2),
      Triple("학코", "핰", 2),
      Triple("학교", "학고", 4),
      Triple("학교", "학쿄", 3),
      Triple("학교", "하교", 3),
    )
    for ((target, committed, mismatch) in negatives) {
      assertEquals(confirmed(mismatch), eval(target, committed).status, "$target: $committed")
    }
  }

  @Test
  fun doubleDotScalarsExpandToStrictRecipePrefixes() {
    for (dot in listOf("ㆍ", "ᆞ")) {
      val evaluation = eval("어", "ㅇ$dot")
      assertEquals(composingMismatch, evaluation.status, dot)
      assertEquals(seq("ㅇ"), evaluation.acceptedSequence, dot)
    }

    val doubleDotSnapshots = listOf(
      Triple("요", "ㅇᆢ", "ㅇ"),
      Triple("여자", "ㅇᆢ", "ㅇ"),
      Triple("예", "ㅇᆢ", "ㅇ"),
      Triple("예", "ㅇᆢㅣ", "ㅇ"),
      Triple("교", "ㄱᆢ", "ㄱ"),
      Triple("며칠", "ㅁᆢ", "ㅁ"),
      Triple("표", "ㅍᆢ", "ㅍ"),
      Triple("효", "ㅎᆢ", "ㅎ"),
      Triple("요", "ㅇㆍㆍ", "ㅇ"),
      Triple("요", "ㅇᆞᆞ", "ㅇ"),
    )
    for ((target, committed, accepted) in doubleDotSnapshots) {
      val evaluation = eval(target, committed)
      assertEquals(composingMismatch, evaluation.status, target)
      assertEquals(seq(accepted), evaluation.acceptedSequence, target)
    }
  }

  @Test
  fun wordPrefixDoubleDotVowelStatesRemainCompositionInProgress() {
    val snapshots = listOf(
      Triple("어요", "어ㅇᆢ", "어ㅇ"),
      Triple("와요", "와ㅇᆢ", "와ㅇ"),
      Triple("해요", "해ㅇᆢ", "해ㅇ"),
      Triple("좋아요", "좋아ㅇᆢ", "좋아ㅇ"),
    )
    for ((target, committed, stable) in snapshots) {
      val evaluation = eval(target, committed)
      assertEquals(composingMismatch, evaluation.status, "$target: $committed")
      assertEquals(JamoDecomposer.keySequence(stable), evaluation.acceptedSequence, "$target: $committed")
    }
  }

  @Test
  fun doubleDotStrictPrefixesRejectUnrelatedAndWrongFollowingStrokes() {
    val negatives = listOf(
      Triple("어", "ㅇᆢ", 1),
      Triple("뒤", "ㄷᆢ", 1),
      Triple("요", "ㅇᆢㅣ", 1),
      Triple("여", "ㅇᆢㅡ", 1),
      Triple("교", "ㄱᆢㅣ", 1),
      Triple("요", "ㅇᆢㅡ", 1),
    )
    for ((target, committed, mismatch) in negatives) {
      assertEquals(confirmed(mismatch), eval(target, committed).status, "$target: $committed")
    }
  }

  @Test
  fun cheonjiinRepresentativeVowelsCompleteWithoutWeakeningRealTypos() {
    for (target in listOf("과자", "돼지", "회사", "원", "웨딩", "귀", "의사", "대형", "세계", "얘", "예")) {
      assertEquals(matching(completed = true, isComposing = false), eval(target, target).status, target)
    }

    val dubeolsikTypo = eval("돼지", "뒤")
    assertEquals(confirmed(1), dubeolsikTypo.status)
    assertEquals(seq("ㄷ"), dubeolsikTypo.acceptedSequence)

    val wrongTrailing = eval("과자", "괸")
    assertEquals(confirmed(2), wrongTrailing.status)
    assertEquals(seq("ㄱㅗ"), wrongTrailing.acceptedSequence)
  }

  @Test
  fun asciiInputDoesNotAdvanceOrBecomeAMistake() {
    val initial = eval("가나", "q")
    assertEquals(OSIMETextJudgeStatus.UnsupportedASCIIInput, initial.status)
    assertEquals(emptyList(), initial.acceptedSequence)

    val afterAcceptedPrefix = eval("가나", "가s")
    assertEquals(OSIMETextJudgeStatus.UnsupportedASCIIInput, afterAcceptedPrefix.status)
    assertEquals(seq("ㄱㅏ"), afterAcceptedPrefix.acceptedSequence)

    val resumed = eval("가나", "가나")
    assertEquals(matching(completed = true, isComposing = false), resumed.status)
    assertEquals(seq("ㄱㅏㄴㅏ"), resumed.acceptedSequence)
  }

  @Test
  fun replaysCheonjiinMarkedAndCommittedSnapshots() {
    data class Snapshot(
      val committed: String,
      val marked: String?,
      val expectedScalars: List<Int>,
      val expectedStatus: OSIMETextJudgeStatus,
      val expectedAcceptedSequence: List<Char>,
    )

    val snapshots = listOf(
      Snapshot("", "ㄴ", listOf(0x3134), composingMismatch, emptyList()),
      Snapshot("", "ㄹ", listOf(0x3139), matching(completed = false, isComposing = true), seq("ㄹ")),
      Snapshot("", "ㄹㆍ", listOf(0x3139, 0x318D), composingMismatch, emptyList()),
      Snapshot("", "러", listOf(0xB7EC), composingMismatch, emptyList()),
      Snapshot("", "레", listOf(0xB808), matching(completed = false, isComposing = true), seq("ㄹㅔ")),
      Snapshot("레", null, listOf(0xB808), matching(completed = false, isComposing = false), seq("ㄹㅔ")),
    )
    for (snapshot in snapshots) {
      assertEquals(
        snapshot.expectedScalars,
        (snapshot.committed + (snapshot.marked ?: "")).codePoints().toArray().toList(),
      )
      val evaluation = eval("레전드", snapshot.committed, snapshot.marked)
      assertEquals(snapshot.expectedStatus, evaluation.status)
      assertEquals(snapshot.expectedAcceptedSequence, evaluation.acceptedSequence)
    }
  }

  @Test
  fun ignoresUnconfirmedASCIIWhileCommittedEnglishStillWarns() {
    val markedIntermediate = eval("레전드", "", "1")
    assertEquals(composingMismatch, markedIntermediate.status)
    assertEquals(emptyList(), markedIntermediate.acceptedSequence)

    val committedEnglish = eval("레전드", "q")
    assertEquals(OSIMETextJudgeStatus.UnsupportedASCIIInput, committedEnglish.status)
    assertEquals(emptyList(), committedEnglish.acceptedSequence)
  }

  @Test
  fun allowsCorrectMarkedProgressDeletionAndRejectsExtraText() {
    val composing = eval("가나", "가", "ㄴ")
    assertEquals(matching(completed = false, isComposing = true), composing.status)
    assertEquals(seq("ㄱㅏㄴ"), composing.acceptedSequence)

    val deletion = eval("가나", "")
    assertEquals(matching(completed = false, isComposing = false), deletion.status)
    assertEquals(emptyList(), deletion.acceptedSequence)

    val extraAfterCompletion = eval("가", "가가")
    assertEquals(matching(completed = true, isComposing = false), extraAfterCompletion.status)
    assertEquals(seq("ㄱㅏ"), extraAfterCompletion.acceptedSequence)

    assertEquals(matching(completed = true, isComposing = false), eval("가", "가A").status)

    val unsupported = eval("가나", "가A")
    assertEquals(OSIMETextJudgeStatus.UnsupportedASCIIInput, unsupported.status)
    assertEquals(seq("ㄱㅏ"), unsupported.acceptedSequence)
  }

  @Test
  fun physicalKeyboardSnapshotsPreserveSpaceBackspaceAndMarkedCommit() {
    val composing = eval("한국 사람", "한국 ", "사")
    assertEquals(
      OSIMETextEvaluation(matching(completed = false, isComposing = true), seq("ㅎㅏㄴㄱㅜㄱ ㅅㅏ")),
      composing,
    )

    val committed = eval("한국 사람", "한국 사")
    assertEquals(composing.acceptedSequence, committed.acceptedSequence)
    assertEquals(matching(completed = false, isComposing = false), committed.status)

    assertEquals(
      OSIMETextEvaluation(matching(completed = false, isComposing = false), seq("ㅎㅏㄴㄱㅜㄱ ")),
      eval("한국 사람", "한국 "),
    )

    assertEquals(matching(completed = true, isComposing = false), eval("한국 사람", "한국 사람").status)
  }

  // Additional Kotlin-side coverage.

  @Test
  fun markedTextCompletingTheTargetReportsCompletedWhileComposing() {
    assertEquals(
      OSIMETextEvaluation(matching(completed = true, isComposing = true), seq("ㄱㅏㄴㅏ")),
      eval("가나", "가", "나"),
    )
    // Empty marked text is treated like no marked text.
    assertEquals(matching(completed = false, isComposing = false), eval("가나", "가", "").status)
  }

  @Test
  fun undecomposableCommittedTextAfterCompletionIsCompleted() {
    assertEquals(OSIMETextEvaluation(matching(completed = true, isComposing = false), seq("ㄱㅏ")), eval("가", "가😀"))
    assertEquals(confirmed(2), eval("가나", "가😀").status)
  }

  @Test
  fun asciiAfterMismatchAcceptsOnlyTheMatchingPrefix() {
    val evaluation = eval("가나", "갑a")
    assertEquals(OSIMETextJudgeStatus.UnsupportedASCIIInput, evaluation.status)
    assertEquals(seq("ㄱㅏ"), evaluation.acceptedSequence)
    assertEquals(OSIMETextEvaluation(matching(completed = true, isComposing = false), seq("ㄱㅏ")), eval("가", "각a"))
  }

  @Test
  fun invalidTargetThrows() {
    assertIs<JamoDecompositionError.EmptyTarget>(assertFailsWith<JamoDecompositionError> { eval("", "") })
  }
}
