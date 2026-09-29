package app.piyokey.core.hangul

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Direct guard-by-guard tests for the internal OS IME tolerance predicates of [Korean10KeyRecipe].
 * Each row is (target, committedText, expected).
 */
class Korean10KeyRecipePredicateTest {
  private fun check(
    name: String,
    predicate: (String, String) -> Boolean,
    rows: List<Triple<String, String, Boolean>>,
  ) {
    for ((target, committed, expected) in rows) {
      assertEquals(expected, predicate(target, committed), "$name($target, $committed)")
    }
  }

  @Test
  fun reachableIntermediate() = check(
    "intermediate",
    Korean10KeyRecipe::committedDocumentEndsInReachableIntermediate,
    listOf(
      Triple("돼", "되", true),
      Triple("ㅙ", "ㅚ", true),
      Triple("가", "", false),
      Triple("가", "가나", false),
      Triple("가나", "다가", false),
      Triple("가", "1", false),
      Triple("1", "가", false),
      Triple("돼", "뒤", false),
      Triple("돼", "괴", false),
      Triple("돼", "됨", false),
    ),
  )

  @Test
  fun reachableRawVowelPrefix() = check(
    "rawVowel",
    Korean10KeyRecipe::committedDocumentEndsInReachableRawVowelPrefix,
    listOf(
      Triple("돼", "ㄷㆍ", true),
      Triple("1어", "1ㅇㆍ", true),
      Triple("ㅓ", "ㆍ", true),
      Triple("가", "나ㆍ", false),
      Triple("가나", "다ㆍ", false),
      Triple("ㅓ", "", false),
      Triple("어", "ㅇ", false),
      Triple("어", "ㅇㄱ", false),
      Triple("어", "ㅇㆍㅣ", false),
      Triple("1", "ㆍ", false),
    ),
  )

  @Test
  fun reachableConsonantCycle() = check(
    "consonantCycle",
    Korean10KeyRecipe::committedDocumentEndsInReachableConsonantCycle,
    listOf(
      Triple("ㄹ", "ㄴ", true),
      Triple("달", "단", true),
      Triple("대형", "댓", true),
      Triple("가", "", false),
      Triple("1", "ㄴ", false),
      Triple("달", "다", false),
      Triple("다", "단", false),
      Triple("대형가", "개댓", false),
      Triple("대형", "ㅅ", false),
      Triple("1형", "댓", false),
      Triple("대형", "갯", false),
      Triple("대형", "닷", false),
      Triple("댁형", "댓", false),
      Triple("대형", "대", false),
      Triple("대1", "댓", false),
      Triple("대형", "댓형가", false),
      Triple("대형", "댐", false),
    ),
  )

  @Test
  fun reachableClosedSyllableBoundary() = check(
    "closedBoundary",
    Korean10KeyRecipe::committedDocumentEndsInReachableClosedSyllableBoundary,
    listOf(
      Triple("일해", "잀", true),
      Triple("일해", "", false),
      Triple("일해", "일해", false),
      Triple("대형", "댓", false),
      Triple("일해", "이", false),
      Triple("일해", "잇", false),
      Triple("일해", "읷", false),
      Triple("일해", "읽", false),
      Triple("가일해", "나잀", false),
      Triple("일해", "ㅇ", false),
      Triple("1해", "잀", false),
      Triple("일해", "잘", false),
      Triple("일해", "알", false),
      Triple("일1", "잀", false),
    ),
  )

  @Test
  fun reachableDanglingComplexTrailingPrefix() = check(
    "dangling",
    Korean10KeyRecipe::committedDocumentEndsInReachableDanglingComplexTrailingPrefix,
    listOf(
      Triple("찮아", "찬ㅅ", true),
      Triple("찮", "찬", false),
      Triple("찮아", "찬아", false),
      Triple("찮", "찮가ㅅ", false),
      Triple("가찮", "나찬ㅅ", false),
      Triple("찮", "1ㅅ", false),
      Triple("1", "찬ㅅ", false),
      Triple("찮", "잔ㅅ", false),
      Triple("찮", "첸ㅅ", false),
      Triple("찮", "차ㅅ", false),
      Triple("차", "찬ㅅ", false),
      Triple("찬", "찬ㅅ", false),
      Triple("찮", "찰ㅅ", false),
      Triple("찮", "찬ㅈ", false),
    ),
  )

  @Test
  fun sameRecipeBoundaryCycle() {
    val rows = listOf(
      Triple("학교", "핰", "ㅎㅏㄱ"),
      Triple("학교", "하", null),
      Triple("학교", "학", null),
      Triple("닭고", "달", null),
      Triple("학도", "핰", null),
      Triple("학교", "핛", null),
      Triple("학교", "한", null),
      Triple("학교", "", null),
      Triple("학교", "학교", null),
    )
    for ((target, committed, expected) in rows) {
      assertEquals(
        expected?.toList(),
        Korean10KeyRecipe.acceptedSequenceForUnconfirmedSameRecipeBoundaryCycle(target, committed),
        "sameRecipe($target, $committed)",
      )
    }
  }

  @Test
  fun reachableComplexTrailingAssembly() = check(
    "complexAssembly",
    Korean10KeyRecipe::committedDocumentEndsInReachableComplexTrailingAssembly,
    listOf(
      Triple("닭", "단", true),
      Triple("앓다", "앐", true),
      Triple("닭", "", false),
      Triple("닭", "닭닭", false),
      Triple("가닭", "나단", false),
      Triple("닭", "1", false),
      Triple("1", "단", false),
      Triple("닭", "당", false),
      Triple("닭", "단단", false),
      Triple("닭", "다", false),
      Triple("다", "단", false),
      Triple("단", "달", false),
      Triple("앓다", "앖", false),
      Triple("닭", "닥", false),
      Triple("닭", "택", false),
    ),
  )

  @Test
  fun graphemeHelpers() {
    assertEquals(listOf("가", "é", "😀"), "가é😀".graphemeClusters())
    assertEquals(emptyList(), "".graphemeClusters())
    assertEquals(null, "".onlyScalar())
    assertEquals(null, "😀".singleChar())
    assertEquals(0x1F600, "😀".onlyScalar())
  }
}
