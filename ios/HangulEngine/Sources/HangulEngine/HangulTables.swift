enum HangulTables {
  static let leading: [Character] = Array("ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ")
  static let medial: [Character] = Array("ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ")
  static let trailing: [Character?] = [
    nil, "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ", "ㄾ",
    "ㄿ", "ㅀ", "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ",
  ]

  static let compoundMedial: [JamoPair: Character] = [
    JamoPair("ㅗ", "ㅏ"): "ㅘ",
    JamoPair("ㅗ", "ㅐ"): "ㅙ",
    JamoPair("ㅗ", "ㅣ"): "ㅚ",
    JamoPair("ㅜ", "ㅓ"): "ㅝ",
    JamoPair("ㅜ", "ㅔ"): "ㅞ",
    JamoPair("ㅜ", "ㅣ"): "ㅟ",
    JamoPair("ㅡ", "ㅣ"): "ㅢ",
  ]

  static let compoundTrailing: [JamoPair: Character] = [
    JamoPair("ㄱ", "ㅅ"): "ㄳ",
    JamoPair("ㄴ", "ㅈ"): "ㄵ",
    JamoPair("ㄴ", "ㅎ"): "ㄶ",
    JamoPair("ㄹ", "ㄱ"): "ㄺ",
    JamoPair("ㄹ", "ㅁ"): "ㄻ",
    JamoPair("ㄹ", "ㅂ"): "ㄼ",
    JamoPair("ㄹ", "ㅅ"): "ㄽ",
    JamoPair("ㄹ", "ㅌ"): "ㄾ",
    JamoPair("ㄹ", "ㅍ"): "ㄿ",
    JamoPair("ㄹ", "ㅎ"): "ㅀ",
    JamoPair("ㅂ", "ㅅ"): "ㅄ",
  ]

  static let splitMedial: [Character: JamoPair] = Dictionary(
    uniqueKeysWithValues: compoundMedial.map { ($0.value, $0.key) }
  )
  static let splitTrailing: [Character: JamoPair] = Dictionary(
    uniqueKeysWithValues: compoundTrailing.map { ($0.value, $0.key) }
  )

  static let shiftedJamo = Set(Array("ㄲㄸㅃㅆㅉㅒㅖ"))
  static let allowedLiteralPunctuation = Set(Array(" .,!?…'\"()-·~♡♥。！？"))

  static let leadingIndex = Dictionary(
    uniqueKeysWithValues: leading.enumerated().map { ($0.element, $0.offset) })
  static let medialIndex = Dictionary(
    uniqueKeysWithValues: medial.enumerated().map { ($0.element, $0.offset) })
  static let trailingIndex = Dictionary(
    uniqueKeysWithValues: trailing.enumerated().compactMap { index, character in
      character.map { ($0, index) }
    }
  )
}

struct JamoPair: Hashable, Sendable {
  let first: Character
  let second: Character

  init(_ first: Character, _ second: Character) {
    self.first = first
    self.second = second
  }
}
