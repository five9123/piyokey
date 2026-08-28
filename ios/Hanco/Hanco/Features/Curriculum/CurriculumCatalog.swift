import DeckKit
import Foundation

struct CurriculumItem: Equatable, Identifiable {
  let id: String
  let ko: String
  let readingKey: String
  let meaningKey: String

  var deckItem: DeckItem {
    DeckItem(
      id: id,
      ko: ko,
      readingJa: AppLocalization.string(readingKey, language: .japanese),
      meaningJa: AppLocalization.string(meaningKey, language: .japanese),
      audio: BundledPronunciationAudio.relativePath(for: ko),
      localizations: Dictionary(uniqueKeysWithValues:
        AppLanguage.allCases.filter { $0 != .japanese }.map { language in
          (language.rawValue, DeckItemLocalization(
            meaning: AppLocalization.string(meaningKey, language: language),
            reading: AppLocalization.string(readingKey, language: .english)
          ))
        }
      ).merging([
        "ko": DeckItemLocalization(
          meaning: KoreanLearningContent.string(meaningKey),
          reading: KoreanLearningContent.string(readingKey)
        ),
      ]) { current, _ in current }
    )
  }
}

/// Preserved learning values, not an app UI locale.
enum KoreanLearningContent {
  static func string(_ key: String) -> String {
    Bundle.main.localizedString(forKey: key, value: nil, table: "KoreanLearningContent")
  }
}

struct CurriculumStage: Equatable, Identifiable {
  let id: String
  let chapterNumber: Int
  let stageNumber: Int
  let titleKey: String
  let detailKey: String
  let symbol: String
  let items: [CurriculumItem]

  var localizedTitle: String { AppLocalization.string(titleKey) }
  var localizedDetail: String { AppLocalization.string(detailKey) }
  var sourceDeckId: String { "curriculum::\(id)" }
}

struct CurriculumChapter: Equatable, Identifiable {
  let number: Int
  let titleKey: String
  let stages: [CurriculumStage]

  var id: Int { number }
  var localizedTitle: String { AppLocalization.string(titleKey) }
}

enum CurriculumCatalog {
  static let chapters: [CurriculumChapter] = [
    chapter(
      1,
      stageID: "chapter_1_basic_consonants",
      symbol: "character.book.closed.fill",
      targets: ["ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅎ"]
    ),
    chapter(
      2,
      stageID: "chapter_2_basic_vowels",
      symbol: "textformat.abc",
      targets: ["ㅏ", "ㅓ", "ㅗ", "ㅜ", "ㅡ", "ㅣ", "ㅐ", "ㅔ", "ㅑ", "ㅕ"]
    ),
    chapter(
      3,
      stageID: "chapter_3_syllable_building",
      symbol: "square.grid.3x3.fill",
      targets: ["가", "나", "다", "라", "마", "바", "사", "아", "자", "하"]
    ),
    chapter(
      4,
      stageID: "chapter_4_batchim",
      symbol: "rectangle.bottomhalf.filled",
      targets: ["간", "난", "달", "밤", "밥", "산", "강", "문", "공", "집"]
    ),
    chapter(
      5,
      stageID: "chapter_5_words",
      symbol: "text.book.closed.fill",
      targets: ["사랑", "친구", "학교", "음식", "여행", "사진", "음악", "선물", "오늘", "응원"]
    ),
    chapter(
      6,
      stageID: "chapter_6_spacing",
      symbol: "keyboard.fill",
      targets: [
        "좋은 아침", "내 친구", "한국 음식", "매운 음식", "여행 사진",
        "작은 선물", "좋은 음악", "같이 가요", "물 주세요", "또 만나요",
      ],
      followingStages: [
        (
          id: "chapter_6_sentences",
          symbol: "text.bubble.fill",
          targets: [
            "안녕하세요", "감사합니다", "사랑해요", "만나서 반가워요", "오늘도 힘내요",
            "정말 멋있어요", "같이 가요", "사진을 찍어요", "음악을 들어요", "좋은 하루 보내요",
          ]
        )
      ]
    ),
  ]

  static let stages: [CurriculumStage] = chapters.flatMap(\.stages)

  static func stage(id: String) -> CurriculumStage? {
    stages.first { $0.id == id }
  }

  private static func chapter(
    _ number: Int,
    stageID: String,
    symbol: String,
    targets: [String],
    followingStages: [(id: String, symbol: String, targets: [String])] = []
  ) -> CurriculumChapter {
    let stageDefinitions = [(id: stageID, symbol: symbol, targets: targets)] + followingStages
    let stages = stageDefinitions.enumerated().map { offset, definition in
      stage(
        chapterNumber: number,
        stageNumber: offset + 1,
        stageID: definition.id,
        symbol: definition.symbol,
        targets: definition.targets
      )
    }
    return CurriculumChapter(
      number: number,
      titleKey: "curriculum.chapter_\(number).title",
      stages: stages
    )
  }

  private static func stage(
    chapterNumber: Int,
    stageNumber: Int,
    stageID: String,
    symbol: String,
    targets: [String]
  ) -> CurriculumStage {
    let items = targets.enumerated().map { index, target in
      CurriculumItem(
        id: "\(stageID)_\(index + 1)",
        ko: target,
        readingKey: "curriculum.\(stageID).item_\(index + 1).reading",
        meaningKey:
          chapterNumber >= 5
          ? "curriculum.\(stageID).item_\(index + 1).meaning"
          : "curriculum.\(stageID).item_meaning"
      )
    }
    return CurriculumStage(
      id: stageID,
      chapterNumber: chapterNumber,
      stageNumber: stageNumber,
      titleKey: "curriculum.\(stageID).title",
      detailKey: "curriculum.\(stageID).detail",
      symbol: symbol,
      items: items
    )
  }
}

enum CurriculumUnlockPolicy {
  static func isUnlocked(
    _ stage: CurriculumStage,
    completedStageIDs: Set<String>,
    stages: [CurriculumStage] = CurriculumCatalog.stages
  ) -> Bool {
    if stage.chapterNumber >= 5 { return true }
    guard let index = stages.firstIndex(where: { $0.id == stage.id }) else { return false }
    guard index > 0 else { return true }
    return completedStageIDs.contains(stages[index - 1].id)
  }
}

enum CurriculumChapterCompletionPolicy {
  static func isCompleted(
    _ chapter: CurriculumChapter,
    completedStageIDs: Set<String>
  ) -> Bool {
    if chapter.number >= 5 {
      return chapter.stages.contains { completedStageIDs.contains($0.id) }
    }
    return chapter.stages.allSatisfy { completedStageIDs.contains($0.id) }
  }
}

enum HatchOnboardingPolicy {
  static let requiredChapterCount = 3

  static var requiredStages: [CurriculumStage] {
    Array(CurriculumCatalog.stages.prefix(requiredChapterCount))
  }

  static func nextRequiredStage(completedStageIDs: Set<String>) -> CurriculumStage? {
    requiredStages.first { !completedStageIDs.contains($0.id) }
  }

  static func isComplete(completedStageIDs: Set<String>) -> Bool {
    nextRequiredStage(completedStageIDs: completedStageIDs) == nil
  }
}
