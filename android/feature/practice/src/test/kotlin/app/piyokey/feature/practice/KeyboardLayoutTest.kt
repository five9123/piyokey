package app.piyokey.feature.practice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyboardLayoutTest {
  @Test
  fun `layout matches standard two-beolsik rows and roman hints`() {
    assertEquals("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ", DubeolsikLayout.topRow.joinToString("") { it.base.toString() })
    assertEquals("qwertyuiop", DubeolsikLayout.topRow.joinToString("") { it.roman })
    assertEquals("ㅁㄴㅇㄹㅎㅗㅓㅏㅣ", DubeolsikLayout.homeRow.joinToString("") { it.base.toString() })
    assertEquals("asdfghjkl", DubeolsikLayout.homeRow.joinToString("") { it.roman })
    assertEquals("ㅋㅌㅊㅍㅠㅜㅡ", DubeolsikLayout.bottomRow.joinToString("") { it.base.toString() })
    assertEquals("zxcvbnm", DubeolsikLayout.bottomRow.joinToString("") { it.roman })
  }

  @Test
  fun `shift maps all five tense consonants and two diphthongs`() {
    val expected = mapOf(
      'ㅂ' to 'ㅃ',
      'ㅈ' to 'ㅉ',
      'ㄷ' to 'ㄸ',
      'ㄱ' to 'ㄲ',
      'ㅅ' to 'ㅆ',
      'ㅐ' to 'ㅒ',
      'ㅔ' to 'ㅖ',
    )

    assertEquals(expected.values.toSet(), DubeolsikLayout.shiftedCharacters)
    expected.forEach { (base, shifted) ->
      val definition = requireNotNull(DubeolsikLayout.definitionFor(base))
      assertEquals(base, definition.output(isShifted = false))
      assertEquals(shifted, definition.output(isShifted = true))
      assertEquals(definition, DubeolsikLayout.definitionFor(shifted))
    }
    assertEquals('ㅛ', requireNotNull(DubeolsikLayout.definitionFor('ㅛ')).output(isShifted = true))
  }

  @Test
  fun `guide leads through shift before highlighting shifted character`() {
    assertEquals(
      KeyboardAction.Shift,
      KeyboardGuideResolver.resolve('ㄲ', isShifted = false, enabled = true).highlightedAction,
    )
    assertEquals(
      KeyboardAction.JamoKey('ㄱ'),
      KeyboardGuideResolver.resolve('ㄲ', isShifted = true, enabled = true).highlightedAction,
    )
    assertEquals(
      KeyboardAction.JamoKey('ㄱ'),
      KeyboardGuideResolver.resolve('ㄱ', isShifted = false, enabled = true).highlightedAction,
    )
  }

  @Test
  fun `guide supports space and can be disabled`() {
    assertEquals(
      KeyboardAction.Space,
      KeyboardGuideResolver.resolve(' ', isShifted = false, enabled = true).highlightedAction,
    )
    assertNull(
      KeyboardGuideResolver.resolve('ㄱ', isShifted = false, enabled = false).highlightedAction,
    )
    assertNull(
      KeyboardGuideResolver.resolve(null, isShifted = false, enabled = true).highlightedAction,
    )
  }

  @Test
  fun `same-frame shift then key emits shifted jamo and resets one-shot latch`() {
    val latch = KeyboardShiftLatch()
    val key = requireNotNull(DubeolsikLayout.definitionFor('ㄱ'))

    assertTrue(latch.toggle())
    assertEquals('ㄲ', latch.consume(key))
    assertFalse(latch.isShifted)
    assertEquals('ㄱ', latch.consume(key))
  }

  @Test
  fun `keycap grows enough to preserve both lines at font scale one point five`() {
    assertEquals(50f, KeyboardKeycapMetrics.heightDp(fontScale = 1f))

    val scaledHeight = KeyboardKeycapMetrics.heightDp(fontScale = 1.5f)
    val twoLineContentHeight = (
      KeyboardKeycapMetrics.MainLineHeightSp + KeyboardKeycapMetrics.RomanLineHeightSp
      ) * 1.5f
    assertTrue(scaledHeight >= twoLineContentHeight + 10f)
    assertTrue(scaledHeight > KeyboardKeycapMetrics.BaseHeightDp)
  }

  @Test
  fun `signature syllable join stays inside visual timing contract`() {
    assertTrue(PracticeMotion.SyllableJoinDurationMillis in 170..180)
    assertTrue(PracticeMotion.IncomingJamoSlideDistanceDp > 0f)
  }

  @Test
  fun `jamo track only auto scrolls when chips overflow the viewport`() {
    assertEquals(78f, JamoTrackMetrics.contentWidthDp(itemCount = 2))
    assertFalse(JamoTrackMetrics.requiresAutoTracking(itemCount = 2, availableWidthDp = 320f))
    assertTrue(JamoTrackMetrics.requiresAutoTracking(itemCount = 10, availableWidthDp = 320f))
  }

  @Test
  fun `rollover tracker keeps concurrent pointers independent`() {
    val tracker = RolloverTouchTracker()
    val first = KeyboardAction.JamoKey('ㄱ')
    val second = KeyboardAction.JamoKey('ㅏ')

    assertTrue(tracker.begin(101, first))
    assertTrue(tracker.begin(202, second))
    assertFalse(tracker.begin(101, second))
    assertEquals(1, tracker.pressedCount(first))
    assertEquals(1, tracker.pressedCount(second))

    assertEquals(first, tracker.end(101))
    assertEquals(0, tracker.pressedCount(first))
    assertEquals(1, tracker.pressedCount(second))
    assertEquals(setOf(second), tracker.clear())
    assertEquals(0, tracker.pressedCount(second))
  }
}
