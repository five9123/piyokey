package app.piyokey.feature.practice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhysicalDubeolsikLayoutTest {
  @Test
  fun mapsBaseShiftSpaceAndHomeKeys() {
    val giyeok = PhysicalDubeolsikLayout.target('ㄱ')!!
    assertEquals('R', giyeok.key?.latin)
    assertEquals(PhysicalKeyboardHand.LEFT, giyeok.hand)
    assertEquals(PhysicalKeyboardFinger.INDEX, giyeok.finger)
    assertFalse(giyeok.requiresShift)

    val ssangGiyeok = PhysicalDubeolsikLayout.target('ㄲ')!!
    assertEquals('R', ssangGiyeok.key?.latin)
    assertTrue(ssangGiyeok.requiresShift)
    assertEquals(PhysicalKeyboardHand.RIGHT, ssangGiyeok.shiftHand)

    val space = PhysicalDubeolsikLayout.target(' ')!!
    assertNull(space.key)
    assertEquals(PhysicalKeyboardFinger.THUMB, space.finger)

    val home = PhysicalDubeolsikLayout.rows.flatten().filter { it.isHomePosition }
    assertEquals(setOf('F', 'J'), home.map { it.latin }.toSet())
  }

  @Test
  fun coversEveryBuiltInDubeolsikJamo() {
    val expected = setOf(
      'ㅂ', 'ㅃ', 'ㅈ', 'ㅉ', 'ㄷ', 'ㄸ', 'ㄱ', 'ㄲ', 'ㅅ', 'ㅆ',
      'ㅛ', 'ㅕ', 'ㅑ', 'ㅐ', 'ㅒ', 'ㅔ', 'ㅖ', 'ㅁ', 'ㄴ', 'ㅇ',
      'ㄹ', 'ㅎ', 'ㅗ', 'ㅓ', 'ㅏ', 'ㅣ', 'ㅋ', 'ㅌ', 'ㅊ', 'ㅍ',
      'ㅠ', 'ㅜ', 'ㅡ', ' ',
    )
    assertEquals(expected, expected.filter { PhysicalDubeolsikLayout.target(it) != null }.toSet())
  }
}
