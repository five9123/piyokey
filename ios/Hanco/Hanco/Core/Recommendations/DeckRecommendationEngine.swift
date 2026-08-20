import DeckKit
import Foundation

enum DeckRecommendationEngine {
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
}
