import DeckKit
import XCTest

@testable import Hanco

@MainActor
final class DiscoverViewModelTests: XCTestCase {
  func testBundledCatalogLoadsAndValidatesOfficialLaunchDecks() throws {
    let catalog = try BundleCatalogRepository().loadCatalog()

    XCTAssertEqual(catalog.decks.count, 26)
    XCTAssertTrue(catalog.decks.allSatisfy(\.official))
    XCTAssertTrue(catalog.tags.allSatisfy { $0.category != "artist" })
    XCTAssertTrue(CatalogValidator.validate(catalog).isEmpty)
  }

  func testSearchMatchesNameTagAndAuthor() {
    let viewModel = makeLoadedViewModel()

    viewModel.query = "TOPIK"
    XCTAssertFalse(viewModel.filteredDecks.isEmpty)
    XCTAssertTrue(
      viewModel.filteredDecks.allSatisfy {
        $0.name.localizedCaseInsensitiveContains("TOPIK")
          || $0.authorNickname.localizedCaseInsensitiveContains("TOPIK")
          || $0.tags.contains { $0.localizedCaseInsensitiveContains("TOPIK") }
      })
  }

  func testMultipleTagAndTypeFiltersUseIntersection() {
    let viewModel = makeLoadedViewModel()
    viewModel.selectedTags = ["公式", "単語"]
    viewModel.selectedType = .word

    XCTAssertFalse(viewModel.filteredDecks.isEmpty)
    XCTAssertTrue(
      viewModel.filteredDecks.allSatisfy {
        $0.official && $0.type == .word && $0.tags.contains("公式") && $0.tags.contains("単語")
      })
  }

  func testTrendingSortUsesCatalogRatioFormula() {
    let viewModel = makeLoadedViewModel()
    let decks = viewModel.trendingDecks

    XCTAssertEqual(decks.count, 10)
    for pair in zip(decks, decks.dropFirst()) {
      XCTAssertGreaterThanOrEqual(pair.0.trendingRatio, pair.1.trendingRatio)
    }
  }

  func testLaunchSectionsSeparateBasicsAndTrendingKorean() {
    let viewModel = makeLoadedViewModel()

    XCTAssertFalse(viewModel.basicDecks.isEmpty)
    XCTAssertEqual(
      viewModel.trendingKoreanDecks.map(\.deckId),
      ["official_trending_korean", "official_fun_friend_reactions"]
    )
    XCTAssertEqual(viewModel.officialDecks.count, 26)
    XCTAssertEqual(viewModel.officialDecks.filter { $0.deckId.hasPrefix("official_fun_") }.count, 9)
    XCTAssertEqual(viewModel.officialDecks.filter { $0.tags.contains("K-POP") }.count, 4)
    XCTAssertEqual(viewModel.officialDecks.filter { $0.tags.contains("Kドラマ") }.count, 3)
    XCTAssertTrue(viewModel.purposeDecks.contains { $0.tags.contains("K-POP") })
    XCTAssertTrue(viewModel.purposeDecks.contains { $0.tags.contains("Kドラマ") })
  }

  func testOfficialOnlyCatalogRemovesNonOfficialDecksFromEveryRoute() throws {
    let bundled = try BundleCatalogRepository().loadCatalog()
    let source = try XCTUnwrap(bundled.decks.first)
    let nonOfficial = CatalogDeck(
      deckId: "user_cached_deck",
      version: source.version,
      name: source.name,
      authorNickname: source.authorNickname,
      official: false,
      featured: true,
      type: source.type,
      level: source.level,
      tags: source.tags,
      itemCount: source.itemCount,
      sizeBytes: source.sizeBytes,
      downloadsTotal: source.downloadsTotal + 1,
      downloads7d: source.downloads7d + 1,
      createdAt: source.createdAt,
      previewItems: source.previewItems,
      fileUrl: source.fileUrl,
      localizations: source.localizations
    )
    let mixed = Catalog(
      catalogVersion: bundled.catalogVersion,
      generatedAt: bundled.generatedAt,
      decks: bundled.decks + [nonOfficial],
      tags: bundled.tags
    )
    let viewModel = DiscoverViewModel(
      repository: StaticCatalogRepository(catalog: mixed),
      officialOnly: true
    )

    viewModel.loadIfNeeded()

    XCTAssertEqual(viewModel.loadState, .loaded)
    XCTAssertTrue(try XCTUnwrap(viewModel.catalog).decks.allSatisfy(\.official))
    XCTAssertTrue(viewModel.filteredDecks.allSatisfy(\.official))
    XCTAssertTrue(viewModel.featuredDecks.allSatisfy(\.official))
    XCTAssertFalse(viewModel.catalog?.decks.contains { $0.deckId == nonOfficial.deckId } ?? true)
  }

  private func makeLoadedViewModel() -> DiscoverViewModel {
    let viewModel = DiscoverViewModel(repository: BundleCatalogRepository())
    viewModel.loadIfNeeded()
    XCTAssertEqual(viewModel.loadState, .loaded)
    return viewModel
  }
}

private struct StaticCatalogRepository: CatalogRepository {
  let catalog: Catalog

  func loadCatalog() throws -> Catalog { catalog }
  func refreshCatalog() async throws -> Catalog? { nil }
}
