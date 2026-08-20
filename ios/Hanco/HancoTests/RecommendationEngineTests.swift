import DeckKit
import XCTest

@testable import Hanco

final class RecommendationEngineTests: XCTestCase {
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
