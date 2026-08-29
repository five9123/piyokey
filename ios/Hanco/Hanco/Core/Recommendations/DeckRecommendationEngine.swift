import DeckKit
import Foundation

enum DeckRecommendationEngine {
  private static let officialLearningPath = [
    "official_keyboard_start",
    "official_consonants",
    "official_vowels",
    "official_syllable_building",
    "official_batchim",
    "official_daily_words",
    "official_verbs_adjectives",
    "official_daily_phrases",
  ]

  static func starterRecommendations(
    catalog: Catalog?,
    preferredTags: [String],
    level: OnboardingLevel?,
    limit: Int = 3
  ) -> [CatalogDeck] {
    guard let catalog, limit > 0 else { return [] }
    let learner = level ?? .beginner
    let preferred = Set(preferredTags)
    return Array(catalog.decks.filter(\.official).sorted { lhs, rhs in
      let leftRank = learner.recommendationRank(level: lhs.level, tags: lhs.tags, isSentence: lhs.type == .sentence)
      let rightRank = learner.recommendationRank(level: rhs.level, tags: rhs.tags, isSentence: rhs.type == .sentence)
      if leftRank != rightRank { return leftRank < rightRank }
      let leftTags = preferred.intersection(lhs.tags).count
      let rightTags = preferred.intersection(rhs.tags).count
      if leftTags != rightTags { return leftTags > rightTags }
      if lhs.featured != rhs.featured { return lhs.featured }
      if lhs.downloadsTotal != rhs.downloadsTotal { return lhs.downloadsTotal > rhs.downloadsTotal }
      return lhs.deckId < rhs.deckId
    }.prefix(limit))
  }

  static func homeRecommendations(
    catalog: Catalog?,
    downloadHistory: [String: [String]],
    installedDeckIDs: Set<String>,
    preferredTags: [String] = [],
    limit: Int = 3
  ) -> [CatalogDeck] {
    guard let catalog, limit > 0 else { return [] }
    var tagWeights = downloadHistory.values.reduce(into: [String: Int]()) { weights, tags in
      for tag in Set(tags) {
        weights[tag, default: 0] += 2
      }
    }
    for tag in Set(preferredTags) {
      tagWeights[tag, default: 0] += 1
    }

    return catalog.decks
      .filter { !installedDeckIDs.contains($0.deckId) }
      .sorted { lhs, rhs in
        let lhsScore = affinityScore(for: lhs, tagWeights: tagWeights)
        let rhsScore = affinityScore(for: rhs, tagWeights: tagWeights)
        if lhsScore != rhsScore { return lhsScore > rhsScore }
        if lhs.featured != rhs.featured { return lhs.featured }
        if lhs.downloadsTotal != rhs.downloadsTotal {
          return lhs.downloadsTotal > rhs.downloadsTotal
        }
        return lhs.name < rhs.name
      }
      .prefix(limit)
      .map { $0 }
  }

  static func nextStepRecommendations(
    catalog: Catalog?,
    installedDeckIDs: Set<String>,
    excludingDeckIDs: Set<String> = [],
    preferredLevel: OnboardingLevel?,
    recentDeckID: String? = nil,
    recentAccuracy: Double? = nil,
    limit: Int = 3
  ) -> [CatalogDeck] {
    guard let catalog, limit > 0 else { return [] }
    let excluded = installedDeckIDs.union(excludingDeckIDs)
    let candidates = catalog.decks.filter {
      $0.official && !excluded.contains($0.deckId)
    }
    guard !candidates.isEmpty else { return [] }

    let source = recentDeckID.flatMap { deckID in
      catalog.decks.first { $0.deckId == deckID }
    }
    let accuracy = recentAccuracy ?? 0
    let sourcePathIndex = source.flatMap { officialLearningPath.firstIndex(of: $0.deckId) }
    let shouldAdvancePath = sourcePathIndex != nil && accuracy >= 80
    let usesStarterPath = source == nil && (preferredLevel == nil || preferredLevel == .beginner || preferredLevel == .jamo)

    return candidates.sorted { lhs, rhs in
      if shouldAdvancePath, let sourcePathIndex {
        let left = pathProgressionRank(deckID: lhs.deckId, after: sourcePathIndex)
        let right = pathProgressionRank(deckID: rhs.deckId, after: sourcePathIndex)
        if left != right { return left < right }
      } else if usesStarterPath {
        let left = starterPathRank(deckID: lhs.deckId)
        let right = starterPathRank(deckID: rhs.deckId)
        if left != right { return left < right }
      }

      let leftRank = nextStepRank(
        deck: lhs,
        source: source,
        accuracy: accuracy,
        preferredLevel: preferredLevel
      )
      let rightRank = nextStepRank(
        deck: rhs,
        source: source,
        accuracy: accuracy,
        preferredLevel: preferredLevel
      )
      if leftRank != rightRank { return leftRank.lexicographicallyPrecedes(rightRank) }
      if lhs.featured != rhs.featured { return lhs.featured }
      if lhs.downloadsTotal != rhs.downloadsTotal {
        return lhs.downloadsTotal > rhs.downloadsTotal
      }
      return lhs.deckId < rhs.deckId
    }
    .prefix(limit)
    .map { $0 }
  }

  static func learningPathDeckID(forCurriculumChapter chapterNumber: Int) -> String? {
    switch chapterNumber {
    case 1: "official_consonants"
    case 2: "official_vowels"
    case 3: "official_syllable_building"
    case 4: "official_batchim"
    case 5: "official_daily_words"
    case 6: "official_daily_phrases"
    default: nil
    }
  }

  static func onboardingRecommendations(
    catalog: Catalog?,
    preferredTags: [String],
    installedDeckIDs: Set<String>,
    limit: Int = 3
  ) -> [CatalogDeck] {
    guard let catalog, limit > 0 else { return [] }
    let candidates = catalog.decks.filter { !installedDeckIDs.contains($0.deckId) }
    let officialCandidates = candidates.filter(\.official)
    let pool = officialCandidates.count >= limit ? officialCandidates : candidates
    let preferred = Set(preferredTags)

    return pool
      .sorted { lhs, rhs in
        let lhsMatches = preferred.intersection(lhs.tags).count
        let rhsMatches = preferred.intersection(rhs.tags).count
        if lhsMatches != rhsMatches { return lhsMatches > rhsMatches }
        if lhs.featured != rhs.featured { return lhs.featured }
        if lhs.downloadsTotal != rhs.downloadsTotal {
          return lhs.downloadsTotal > rhs.downloadsTotal
        }
        return lhs.name < rhs.name
      }
      .prefix(limit)
      .map { $0 }
  }

  static func relatedRecommendations(
    sourceDeckID: String,
    sourceTags: [String],
    catalogDecks: [CatalogDeck],
    installedDeckIDs: Set<String>,
    limit: Int = 2
  ) -> [CatalogDeck] {
    guard limit > 0 else { return [] }
    let sourceTagSet = Set(sourceTags)
    return
      catalogDecks
      .filter {
        $0.deckId != sourceDeckID && !sourceTagSet.isDisjoint(with: $0.tags)
      }
      .sorted { lhs, rhs in
        let lhsInstalled = installedDeckIDs.contains(lhs.deckId)
        let rhsInstalled = installedDeckIDs.contains(rhs.deckId)
        if lhsInstalled != rhsInstalled { return !lhsInstalled }
        let lhsMatches = sourceTagSet.intersection(lhs.tags).count
        let rhsMatches = sourceTagSet.intersection(rhs.tags).count
        if lhsMatches != rhsMatches { return lhsMatches > rhsMatches }
        if lhs.downloadsTotal != rhs.downloadsTotal {
          return lhs.downloadsTotal > rhs.downloadsTotal
        }
        return lhs.name < rhs.name
      }
      .prefix(limit)
      .map { $0 }
  }

  private static func affinityScore(
    for deck: CatalogDeck,
    tagWeights: [String: Int]
  ) -> Int {
    Set(deck.tags).reduce(0) { $0 + tagWeights[$1, default: 0] }
  }

  private static func pathProgressionRank(deckID: String, after sourceIndex: Int) -> Int {
    guard let index = officialLearningPath.firstIndex(of: deckID), index > sourceIndex else {
      return officialLearningPath.count + 1
    }
    return index - sourceIndex - 1
  }

  private static func starterPathRank(deckID: String) -> Int {
    officialLearningPath.firstIndex(of: deckID) ?? officialLearningPath.count + 1
  }

  private static func nextStepRank(
    deck: CatalogDeck,
    source: CatalogDeck?,
    accuracy: Double,
    preferredLevel: OnboardingLevel?
  ) -> [Int] {
    if let source {
      let targetLevel = accuracy >= 90 ? min(3, source.level + 1) : source.level
      let meaningfulSourceTags = Set(source.tags).subtracting(["公式"])
      let matchingTags = meaningfulSourceTags.intersection(deck.tags).count
      return [
        abs(deck.level - targetLevel),
        -matchingTags,
        deck.type == source.type ? 0 : 1,
      ]
    }

    let learner = preferredLevel ?? .beginner
    return [
      learner.recommendationRank(
        level: deck.level,
        tags: deck.tags,
        isSentence: deck.type == .sentence
      ),
      deck.level,
      deck.type == .word ? 0 : 1,
    ]
  }
}
