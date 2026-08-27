import DeckKit
import SwiftUI

struct DeckPracticeDestination: View {
  @EnvironmentObject private var deckLibrary: DeckLibrary
  @EnvironmentObject private var discoverViewModel: DiscoverViewModel

  let deck: Deck

  var body: some View {
    PracticeView(
      targets: practiceItems.map(\.ko),
      sessionTitle: deck.appName,
      reviewSources: practiceItems.map {
        PracticeReviewSource(item: $0, sourceDeckId: deck.deckId)
      },
      sourceTags: deck.tags,
      catalogDecks: discoverViewModel.catalog?.decks ?? [],
      analyticsDeckSource: analyticsDeckSource
    )
    .onAppear { deckLibrary.markPlayed(deck.deckId) }
  }

  private var analyticsDeckSource: String {
    switch deckLibrary.records[deck.deckId]?.source {
    case .bundle: "bundled"
    case .remote: "catalog"
    case .imported: "imported"
    case .created: "created"
    case nil: "unknown"
    }
  }

  private var practiceItems: [DeckItem] {
    #if DEBUG
      if let rawLimit = ProcessInfo.processInfo.environment["UITEST_DECK_ITEM_LIMIT"],
        let limit = Int(rawLimit), limit > 0
      {
        return Array(deck.items.prefix(limit))
      }
    #endif
    return deck.items
  }
}
