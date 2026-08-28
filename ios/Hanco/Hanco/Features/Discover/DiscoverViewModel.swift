import DeckKit
import Foundation

enum CatalogSortOrder: String, CaseIterable, Identifiable {
  case popular
  case newest
  case trending

  var id: Self { self }
}

@MainActor
final class DiscoverViewModel: ObservableObject {
  enum LoadState: Equatable {
    case idle
    case loaded
    case failed
  }

  @Published var query = ""
  @Published var selectedTags: Set<String> = []
  @Published var selectedType: DeckType?
  @Published var selectedLevel: Int?
  @Published var sortOrder: CatalogSortOrder = .popular
  @Published private(set) var loadState: LoadState = .idle
  @Published private(set) var catalog: Catalog?

  private let repository: any CatalogRepository
  private var refreshTask: Task<Void, Never>?

  init(repository: any CatalogRepository = CatalogRepositoryFactory.live()) {
    self.repository = repository
  }

  @discardableResult
  func loadIfNeeded() -> Task<Void, Never>? {
    guard loadState == .idle else { return refreshTask }
    do {
      catalog = try repository.loadCatalog()
      loadState = .loaded
    } catch {
      catalog = nil
      loadState = .failed
    }

    let task = Task { [weak self] in
      guard let self else { return }
      do {
        if let refreshed = try await repository.refreshCatalog(), !Task.isCancelled {
          catalog = refreshed
          loadState = .loaded
        }
      } catch {
        if catalog == nil {
          loadState = .failed
        }
      }
    }
    refreshTask = task
    return task
  }

  @discardableResult
  func retry() -> Task<Void, Never>? {
    refreshTask?.cancel()
    refreshTask = nil
    loadState = .idle
    return loadIfNeeded()
  }

  func toggleTag(_ tag: String) {
    if selectedTags.contains(tag) {
      selectedTags.remove(tag)
    } else {
      selectedTags.insert(tag)
    }
  }

  func resetFilters() {
    query = ""
    selectedTags.removeAll()
    selectedType = nil
    selectedLevel = nil
    sortOrder = .popular
  }

  var hasActiveFilters: Bool {
    !normalizedQuery.isEmpty || !selectedTags.isEmpty || selectedType != nil
      || selectedLevel != nil
  }

  var totalDeckCount: Int { availableDecks.count }

  func localizedTag(_ rawTag: String) -> String {
    catalog?.tags.first(where: { $0.tag == rawTag })?.appTag ?? ""
  }

  var shortcutTags: [String] {
    guard let catalog else { return [] }
    let preferred = [
      "入門", "子音", "母音", "パッチム", "日常", "韓国旅行", "TOPIK", "今どき", "K-POP", "Kドラマ",
    ]
    let popularTopics = catalog.tags
      .sorted {
        if $0.deckCount == $1.deckCount { return $0.tag < $1.tag }
        return $0.deckCount > $1.deckCount
      }
      .map(\.tag)
    return unique(preferred + popularTopics)
      .filter { tag in
        catalog.tags.contains { $0.tag == tag && $0.appTag?.isEmpty == false }
      }
      .prefix(12)
      .map { $0 }
  }

  var filteredDecks: [CatalogDeck] {
    guard catalog != nil else { return [] }
    let filtered = availableDecks.filter { deck in
      matchesQuery(deck) && selectedTags.isSubset(of: Set(deck.tags))
        && (selectedType == nil || deck.type == selectedType)
        && (selectedLevel == nil || deck.level == selectedLevel)
    }
    return sorted(filtered, by: sortOrder)
  }

  var trendingDecks: [CatalogDeck] {
    sorted(availableDecks, by: .trending).prefix(10).map { $0 }
  }

  var popularDecks: [CatalogDeck] {
    sorted(availableDecks, by: .popular).prefix(10).map { $0 }
  }

  var newestDecks: [CatalogDeck] {
    sorted(availableDecks, by: .newest).prefix(10).map { $0 }
  }

  var featuredDecks: [CatalogDeck] {
    sorted(availableDecks.filter(\.featured), by: .popular)
  }

  var basicDecks: [CatalogDeck] {
    let basicTags: Set<String> = ["入門", "キーボード", "子音", "母音", "組み立て", "パッチム"]
    return availableDecks.filter { deck in
      deck.official && !basicTags.isDisjoint(with: deck.tags)
    }
  }

  var trendingKoreanDecks: [CatalogDeck] {
    availableDecks.filter { $0.official && $0.tags.contains("今どき") }
  }

  var purposeDecks: [CatalogDeck] {
    let purposeTags: Set<String> = ["日常", "韓国旅行", "TOPIK", "基礎単語", "K-POP", "Kドラマ"]
    return availableDecks.filter { deck in
      deck.official && !purposeTags.isDisjoint(with: deck.tags)
    }
  }

  var officialDecks: [CatalogDeck] {
    availableDecks.filter(\.official)
  }

  func sorted(_ decks: [CatalogDeck], by order: CatalogSortOrder) -> [CatalogDeck] {
    decks.sorted { lhs, rhs in
      switch order {
      case .popular:
        if lhs.downloadsTotal == rhs.downloadsTotal { return lhs.appName < rhs.appName }
        return lhs.downloadsTotal > rhs.downloadsTotal
      case .newest:
        if lhs.createdAt == rhs.createdAt { return lhs.appName < rhs.appName }
        return lhs.createdAt > rhs.createdAt
      case .trending:
        if lhs.trendingRatio == rhs.trendingRatio { return lhs.downloads7d > rhs.downloads7d }
        return lhs.trendingRatio > rhs.trendingRatio
      }
    }
  }

  private var normalizedQuery: String {
    query.trimmingCharacters(in: .whitespacesAndNewlines)
  }

  private func matchesQuery(_ deck: CatalogDeck) -> Bool {
    guard !normalizedQuery.isEmpty else { return true }
    let fields = [deck.appName, deck.appAuthorNickname] + deck.appTags
    return fields.contains {
      $0.range(
        of: normalizedQuery,
        options: [.caseInsensitive, .diacriticInsensitive],
        locale: AppLocalization.locale
      ) != nil
    }
  }

  private var availableDecks: [CatalogDeck] {
    (catalog?.decks ?? []).filter(\.isAvailableInCurrentLanguage)
  }

  private func unique(_ values: [String]) -> [String] {
    var seen: Set<String> = []
    return values.filter { seen.insert($0).inserted }
  }
}
