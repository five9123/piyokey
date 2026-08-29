import DeckKit
import SwiftUI

struct DiscoverView: View {
  @Environment(\.hancoAdaptiveMetrics) private var adaptiveMetrics
  @EnvironmentObject private var deckLibrary: DeckLibrary
  @ObservedObject var viewModel: DiscoverViewModel

  var body: some View {
    NavigationStack {
      VStack(spacing: 0) {
        searchBar
          .frame(maxWidth: adaptiveMetrics.readableContentMaxWidth)
          .frame(maxWidth: .infinity)
          .padding(.horizontal, adaptiveMetrics.horizontalPadding)
          .padding(.vertical, 10)
          .background(AppPalette.card)
          .appTourTarget(.discoverSearch)

        content
      }
      .background(
        LinearGradient(
          colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
          startPoint: .topLeading,
          endPoint: .bottomTrailing
        )
        .ignoresSafeArea()
      )
    }
  }

  private var searchBar: some View {
    HStack(spacing: 10) {
      HStack(spacing: 8) {
        Image(systemName: "magnifyingglass")
          .foregroundStyle(AppPalette.mutedInk)
        TextField("discover.search.placeholder", text: $viewModel.query)
          .textInputAutocapitalization(.never)
          .autocorrectionDisabled()
          .accessibilityIdentifier("discover.search")
        if !viewModel.query.isEmpty {
          Button {
            viewModel.query = ""
          } label: {
            Image(systemName: "xmark.circle.fill")
              .foregroundStyle(AppPalette.mutedInk)
          }
          .accessibilityLabel(Text("discover.search.clear"))
        }
      }
      .padding(.horizontal, 13)
      .frame(height: 44)
      .background(
        AppPalette.backgroundBottom.opacity(0.7),
        in: RoundedRectangle(cornerRadius: 15, style: .continuous)
      )

      filterMenu
    }
  }

  private var filterMenu: some View {
    Menu {
      Picker("discover.filter.type", selection: $viewModel.selectedType) {
        Text("discover.filter.all").tag(Optional<DeckType>.none)
        Text("deck.type.word").tag(Optional(DeckType.word))
        Text("deck.type.sentence").tag(Optional(DeckType.sentence))
      }

      Picker("discover.filter.level", selection: $viewModel.selectedLevel) {
        Text("discover.filter.all").tag(Optional<Int>.none)
        ForEach(1...3, id: \.self) { level in
          Text(AppLocalization.format("deck.level.format", level))
            .tag(Optional(level))
        }
      }

      Picker("discover.sort.title", selection: $viewModel.sortOrder) {
        Text("discover.sort.popular").tag(CatalogSortOrder.popular)
        Text("discover.sort.newest").tag(CatalogSortOrder.newest)
        Text("discover.sort.trending").tag(CatalogSortOrder.trending)
      }

      if viewModel.hasActiveFilters {
        Divider()
        Button("discover.filter.reset", role: .destructive) {
          viewModel.resetFilters()
        }
      }
    } label: {
      Image(
        systemName: viewModel.hasActiveFilters
          ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle"
      )
      .font(.title2)
      .foregroundStyle(viewModel.hasActiveFilters ? AppPalette.accent : AppPalette.ink)
      .frame(width: 44, height: 44)
      .background(AppPalette.backgroundBottom.opacity(0.7), in: RoundedRectangle(cornerRadius: 15))
    }
    .accessibilityLabel(Text("discover.filter.title"))
    .accessibilityIdentifier("discover.filter")
  }

  @ViewBuilder
  private var content: some View {
    switch viewModel.loadState {
    case .idle:
      ProgressView()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    case .failed:
      VStack(spacing: 14) {
        Image(systemName: "wifi.exclamationmark")
          .font(.system(size: 38))
          .foregroundStyle(AppPalette.mutedInk)
        Text("discover.error.title")
          .font(.headline)
        Text("discover.error.message")
          .font(.subheadline)
          .foregroundStyle(AppPalette.mutedInk)
          .multilineTextAlignment(.center)
        Button("discover.error.retry") { viewModel.retry() }
          .buttonStyle(.borderedProminent)
          .tint(AppPalette.accent)
      }
      .padding(28)
      .frame(maxWidth: .infinity, maxHeight: .infinity)
    case .loaded:
      catalogContent
    }
  }

  private var catalogContent: some View {
    ScrollView {
      LazyVStack(alignment: .leading, spacing: 22) {
        shortcutSection

        if viewModel.hasActiveFilters {
          filteredResults
        } else {
          catalogSection(
            title: "discover.section.start_here",
            systemImage: "keyboard.fill",
            decks: viewModel.basicDecks
          )
          catalogSection(
            title: "discover.section.now_korean",
            systemImage: "bubble.left.and.bubble.right.fill",
            decks: viewModel.trendingKoreanDecks
          )
          catalogSection(
            title: "discover.section.by_goal",
            systemImage: "scope",
            decks: viewModel.purposeDecks
          )
          catalogSection(
            title: "discover.section.official_all",
            systemImage: "checkmark.seal.fill",
            decks: viewModel.officialDecks
          )
        }

        deckSuggestionCard
      }
      .frame(maxWidth: adaptiveMetrics.hubContentMaxWidth)
      .frame(maxWidth: .infinity)
      .padding(.vertical, 18)
    }
    .accessibilityIdentifier("discover.catalog")
  }

  private var deckSuggestionCard: some View {
    ContentFeedbackLink(context: .suggestion(source: .discover)) {
      HStack(spacing: 14) {
        Image(systemName: "lightbulb.max.fill")
          .font(.system(size: 20, weight: .semibold))
          .foregroundStyle(AppPalette.accent)
          .frame(width: 44, height: 44)
          .background(AppPalette.accentSoft.opacity(0.55), in: RoundedRectangle(cornerRadius: 14))

        VStack(alignment: .leading, spacing: 3) {
          Text("content_feedback.suggestion.title")
            .font(.subheadline.weight(.bold))
            .foregroundStyle(AppPalette.ink)
          Text("content_feedback.suggestion.detail")
            .font(.caption)
            .foregroundStyle(AppPalette.mutedInk)
        }

        Spacer()

        Image(systemName: "arrow.up.right")
          .font(.caption.weight(.bold))
          .foregroundStyle(AppPalette.mutedInk)
      }
      .padding(16)
      .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
      .contentShape(Rectangle())
    }
    .buttonStyle(.plain)
    .padding(.horizontal, 18)
    .accessibilityIdentifier("discover.deck_suggestion")
  }

  private var shortcutSection: some View {
    VStack(alignment: .leading, spacing: 10) {
      Label("discover.shortcuts.title", systemImage: "number")
        .font(.headline.weight(.bold))
        .foregroundStyle(AppPalette.ink)
        .padding(.horizontal, 18)

      ScrollView(.horizontal, showsIndicators: false) {
        HStack(spacing: 8) {
          ForEach(viewModel.shortcutTags, id: \.self) { tag in
            Button {
              viewModel.toggleTag(tag)
            } label: {
              Text(verbatim: "#\(viewModel.localizedTag(tag))")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(
                  viewModel.selectedTags.contains(tag) ? Color.white : AppPalette.ink
                )
                .padding(.horizontal, 13)
                .padding(.vertical, 9)
                .background(
                  viewModel.selectedTags.contains(tag) ? AppPalette.accent : AppPalette.card,
                  in: Capsule()
                )
            }
            .accessibilityIdentifier("discover.tag.\(tag)")
          }
        }
        .padding(.horizontal, 18)
      }
    }
  }

  private var filteredResults: some View {
    VStack(alignment: .leading, spacing: 12) {
      HStack {
        Text("discover.results.title")
          .font(.headline.weight(.bold))
        Spacer()
        Text(
          AppLocalization.format("discover.results.count", viewModel.filteredDecks.count)
        )
        .font(.caption.weight(.semibold))
        .foregroundStyle(AppPalette.mutedInk)
        .accessibilityIdentifier("discover.results.count")
      }

      if viewModel.filteredDecks.isEmpty {
        VStack(spacing: 10) {
          Image(systemName: "magnifyingglass")
            .font(.title)
          Text("discover.results.empty")
            .font(.subheadline)
        }
        .foregroundStyle(AppPalette.mutedInk)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40)
      } else {
        ForEach(viewModel.filteredDecks, id: \.deckId) { deck in
          deckLink(deck)
        }
      }
    }
    .padding(.horizontal, 18)
  }

  private func catalogSection(
    title: LocalizedStringKey,
    systemImage: String,
    decks: [CatalogDeck],
    showsRank: Bool = false
  ) -> some View {
    VStack(alignment: .leading, spacing: 11) {
      HStack {
        Label(title, systemImage: systemImage)
          .font(.headline.weight(.bold))
          .foregroundStyle(AppPalette.ink)
        Spacer()
        NavigationLink {
          DeckListView(
            title: title,
            decks: decks,
            catalogDecks: viewModel.catalog?.decks ?? []
          )
        } label: {
          Text("discover.section.see_all")
            .font(.caption.weight(.semibold))
            .foregroundStyle(AppPalette.accent)
        }
      }
      .padding(.horizontal, 18)

      ScrollView(.horizontal, showsIndicators: false) {
        LazyHStack(spacing: 12) {
          ForEach(Array(decks.enumerated()), id: \.element.deckId) { index, deck in
            deckLink(deck, rank: showsRank ? index + 1 : nil)
              .frame(width: adaptiveMetrics.isExpanded ? 390 : 290)
          }
        }
        .padding(.horizontal, 18)
        .padding(.bottom, 10)
      }
    }
  }

  private func deckLink(_ deck: CatalogDeck, rank: Int? = nil) -> some View {
    NavigationLink {
      DeckDetailView(deck: deck, catalogDecks: viewModel.catalog?.decks ?? [])
    } label: {
      DeckCardView(
        deck: deck,
        rank: rank,
        isInstalled: deckLibrary.isInstalled(deck.deckId),
        updateAvailable: deckLibrary.needsUpdate(deck)
      )
    }
    .buttonStyle(.plain)
  }
}

private struct DeckListView: View {
  @EnvironmentObject private var deckLibrary: DeckLibrary

  let title: LocalizedStringKey
  let decks: [CatalogDeck]
  let catalogDecks: [CatalogDeck]

  var body: some View {
    ScrollView {
      LazyVStack(spacing: 12) {
        ForEach(decks, id: \.deckId) { deck in
          NavigationLink {
            DeckDetailView(deck: deck, catalogDecks: catalogDecks)
          } label: {
            DeckCardView(
              deck: deck,
              isInstalled: deckLibrary.isInstalled(deck.deckId),
              updateAvailable: deckLibrary.needsUpdate(deck)
            )
          }
          .buttonStyle(.plain)
        }
      }
      .padding(18)
    }
    .background(
      LinearGradient(
        colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
      )
      .ignoresSafeArea()
    )
    .navigationTitle(Text(title))
    .navigationBarTitleDisplayMode(.inline)
  }
}
