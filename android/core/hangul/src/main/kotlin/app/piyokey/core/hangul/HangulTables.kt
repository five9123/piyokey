package app.piyokey.core.hangul

internal data class JamoPair(
  val first: Char,
  val second: Char,
)

internal object HangulTables {
  val leading = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ".toList()
  val medial = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ".toList()
  val trailing: List<Char?> = listOf(
    null,
    'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ', 'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ',
    'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ',
  )

  val compoundMedial = mapOf(
    JamoPair('ㅗ', 'ㅏ') to 'ㅘ',
    JamoPair('ㅗ', 'ㅐ') to 'ㅙ',
    JamoPair('ㅗ', 'ㅣ') to 'ㅚ',
    JamoPair('ㅜ', 'ㅓ') to 'ㅝ',
    JamoPair('ㅜ', 'ㅔ') to 'ㅞ',
    JamoPair('ㅜ', 'ㅣ') to 'ㅟ',
    JamoPair('ㅡ', 'ㅣ') to 'ㅢ',
  )

  val compoundTrailing = mapOf(
    JamoPair('ㄱ', 'ㅅ') to 'ㄳ',
    JamoPair('ㄴ', 'ㅈ') to 'ㄵ',
    JamoPair('ㄴ', 'ㅎ') to 'ㄶ',
    JamoPair('ㄹ', 'ㄱ') to 'ㄺ',
    JamoPair('ㄹ', 'ㅁ') to 'ㄻ',
    JamoPair('ㄹ', 'ㅂ') to 'ㄼ',
    JamoPair('ㄹ', 'ㅅ') to 'ㄽ',
    JamoPair('ㄹ', 'ㅌ') to 'ㄾ',
    JamoPair('ㄹ', 'ㅍ') to 'ㄿ',
    JamoPair('ㄹ', 'ㅎ') to 'ㅀ',
    JamoPair('ㅂ', 'ㅅ') to 'ㅄ',
  )

  val splitMedial = compoundMedial.entries.associate { (pair, compound) -> compound to pair }
  val splitTrailing = compoundTrailing.entries.associate { (pair, compound) -> compound to pair }

  val shiftedJamo = "ㄲㄸㅃㅆㅉㅒㅖ".toSet()
  val allowedLiteralPunctuation = " .,!?…'\"()-·~♡♥。！？".toSet()

  val leadingIndex = leading.withIndex().associate { (index, character) -> character to index }
  val medialIndex = medial.withIndex().associate { (index, character) -> character to index }
  val trailingIndex = trailing.withIndex().mapNotNull { (index, character) ->
    character?.let { it to index }
  }.toMap()
}
