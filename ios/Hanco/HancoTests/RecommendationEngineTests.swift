import DeckKit
import XCTest

@testable import Hanco

final class RecommendationEngineTests: XCTestCase {
  func testStarterRecommendationsRespectEachLevelBeforeInterests() throws {
    let catalog = try BundleCatalogRepository().loadCatalog()
    for goal in OnboardingGoal.allCases {
      for level in OnboardingLevel.allCases {
        let recommendations = DeckRecommendationEngine.starterRecommendations(
          catalog: catalog, preferredTags: goal.preferredTags, level: level
        )
        XCTAssertEqual(recommendations.count, 3)
        XCTAssertTrue(recommendations.allSatisfy(\.official))
        let first = try XCTUnwrap(recommendations.first)
        let minimumRank = catalog.decks.filter(\.official).map {
          level.recommendationRank(level: $0.level, tags: $0.tags, isSentence: $0.type == .sentence)
        }.min()
        XCTAssertEqual(level.recommendationRank(level: first.level, tags: first.tags, isSentence: first.type == .sentence), minimumRank)
      }
    }
    let travel = DeckRecommendationEngine.starterRecommendations(catalog: catalog, preferredTags: OnboardingGoal.travel.preferredTags, level: .sentences)
    XCTAssertTrue(travel.allSatisfy { $0.type == .sentence && !Set($0.tags).isDisjoint(with: OnboardingGoal.travel.preferredTags) })
    let topik = DeckRecommendationEngine.starterRecommendations(catalog: catalog, preferredTags: OnboardingGoal.topik.preferredTags, level: .words)
    XCTAssertEqual(topik.first?.deckId, "official_topik_one")
    let beginner = DeckRecommendationEngine.starterRecommendations(catalog: catalog, preferredTags: [], level: nil)
    XCTAssertTrue(beginner.allSatisfy { $0.level == 1 && $0.tags.contains("入門") })
    XCTAssertTrue(DeckRecommendationEngine.starterRecommendations(catalog: nil, preferredTags: [], level: nil).isEmpty)
    XCTAssertTrue(DeckRecommendationEngine.starterRecommendations(catalog: catalog, preferredTags: [], level: nil, limit: 0).isEmpty)
  }

  func testHomeRecommendationsUseDownloadTagAffinityThenPopularity() throws {
    let catalog = try BundleCatalogRepository().loadCatalog()
    let installedID = "official_daily_words"
    let recommendations = DeckRecommendationEngine.homeRecommendations(
      catalog: catalog,
      downloadHistory: [installedID: ["日常", "公式", "単語"]],
      installedDeckIDs: [installedID]
    )

    XCTAssertEqual(recommendations.count, 3)
    XCTAssertFalse(recommendations.contains { $0.deckId == installedID })
    let scores = recommendations.map { deck in
      Set(deck.tags).intersection(["日常", "公式", "単語"]).count
    }
    XCTAssertEqual(scores, scores.sorted(by: >))
  }

  func testHomeColdStartReturnsFeaturedPopularUninstalledDecks() throws {
    let catalog = try BundleCatalogRepository().loadCatalog()
    let mostPopularFeatured = try XCTUnwrap(
      catalog.decks.filter(\.featured).max { $0.downloadsTotal < $1.downloadsTotal }
    )
    let recommendations = DeckRecommendationEngine.homeRecommendations(
      catalog: catalog,
      downloadHistory: [:],
      installedDeckIDs: [mostPopularFeatured.deckId]
    )

    XCTAssertEqual(recommendations.count, 3)
    XCTAssertFalse(recommendations.contains(mostPopularFeatured))
    XCTAssertTrue(recommendations.first?.featured == true)
  }

  func testResultRecommendationsPreferUninstalledSameTagDecks() throws {
    let catalog = try BundleCatalogRepository().loadCatalog()
    let source = try XCTUnwrap(
      catalog.decks.first { $0.deckId == "official_daily_words" }
    )
    let installedRelated = try XCTUnwrap(
      catalog.decks.first {
        $0.deckId != source.deckId && !Set($0.tags).isDisjoint(with: source.tags)
      }
    )
    let recommendations = DeckRecommendationEngine.relatedRecommendations(
      sourceDeckID: source.deckId,
      sourceTags: source.tags,
      catalogDecks: catalog.decks,
      installedDeckIDs: [source.deckId, installedRelated.deckId]
    )

    XCTAssertEqual(recommendations.count, 2)
    XCTAssertTrue(
      recommendations.allSatisfy {
        $0.deckId != source.deckId && !Set($0.tags).isDisjoint(with: source.tags)
      }
    )
    if recommendations.contains(installedRelated) {
      XCTAssertNotEqual(recommendations.first?.deckId, installedRelated.deckId)
    }
  }
}
