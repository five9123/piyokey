import SwiftUI

struct ReviewDeckView: View {
  @Environment(\.hancoAdaptiveMetrics) private var adaptiveMetrics
  @EnvironmentObject private var reviewDeck: ReviewDeckLibrary

  var body: some View {
    Group {
      if reviewDeck.activeItems.isEmpty {
        emptyState
      } else {
        reviewContent
      }
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(
      LinearGradient(
        colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
      )
      .ignoresSafeArea()
    )
    .navigationTitle(Text("review.deck.navigation_title"))
    .navigationBarTitleDisplayMode(.inline)
    .accessibilityIdentifier("review.deck.screen")
  }

  private var reviewContent: some View {
    ScrollView {
      LazyVStack(spacing: 12) {
        summaryCard
        startButton

        ForEach(reviewDeck.activeItems) { item in
          reviewRow(item)
        }
      }
      .padding(18)
      .hancoCenteredContent(maxWidth: adaptiveMetrics.readableContentMaxWidth)
    }
  }

  private var summaryCard: some View {
    HStack(spacing: 14) {
      Image(systemName: "bookmark.fill")
        .font(.system(size: 30, weight: .bold))
        .foregroundStyle(.white)
        .frame(width: 64, height: 76)
        .background(AppPalette.accent, in: RoundedRectangle(cornerRadius: 20))

      VStack(alignment: .leading, spacing: 6) {
        Text("review.deck.title")
          .font(.system(.title3, design: .rounded, weight: .bold))
          .foregroundStyle(AppPalette.ink)
        Text("review.deck.subtitle")
          .font(.caption)
          .foregroundStyle(AppPalette.mutedInk)
        Text(
          AppLocalization.format("review.deck.count_format",
            reviewDeck.activeItems.count
          )
        )
        .font(.caption.weight(.bold))
        .foregroundStyle(AppPalette.accent)
        .accessibilityIdentifier("review.deck.count")
      }
      Spacer()
    }
    .padding(18)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 10, y: 6)
  }

  private var startButton: some View {
    NavigationLink {
      PracticeView(
        targets: reviewDeck.activeItems.map(\.ko),
        sessionTitle: AppLocalization.string("review.deck.title"),
        reviewSources: reviewDeck.activeItems.map {
          PracticeReviewSource(item: $0.deckItem, sourceDeckId: $0.sourceDeckId)
        },
        analyticsSessionKind: "review",
        analyticsDeckSource: "review"
      )
    } label: {
      Label("review.deck.start", systemImage: "play.fill")
        .font(.headline.weight(.bold))
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 15)
        .background(AppPalette.accent, in: RoundedRectangle(cornerRadius: 18))
    }
    .accessibilityIdentifier("review.deck.start")
  }

  private func reviewRow(_ item: ReviewDeckItem) -> some View {
    HStack(spacing: 13) {
      VStack(alignment: .leading, spacing: 5) {
        HStack(spacing: 8) {
          Text(verbatim: item.ko)
            .font(.system(.title3, design: .rounded, weight: .bold))
            .foregroundStyle(AppPalette.ink)
          if let reading = item.deckItem.appReading {
            Text(verbatim: reading)
              .font(.caption)
              .foregroundStyle(AppPalette.secondary)
          }
        }
        if let meaning = item.deckItem.appMeaning {
          Text(verbatim: meaning)
            .font(.subheadline)
            .foregroundStyle(AppPalette.mutedInk)
        }
        HStack(spacing: 12) {
          Label(
            AppLocalization.format("review.deck.miss_count_format", item.missCount),
            systemImage: "exclamationmark.circle.fill"
          )
          Label(
            AppLocalization.format("review.deck.perfect_count_format",
              item.consecutivePerfect
            ),
            systemImage: "checkmark.seal.fill"
          )
        }
        .font(.caption2.weight(.semibold))
        .foregroundStyle(AppPalette.mutedInk)
      }
      Spacer()
      Button(role: .destructive) {
        reviewDeck.removeManually(itemId: item.itemId, sourceDeckId: item.sourceDeckId)
      } label: {
        Image(systemName: "trash")
          .font(.body.weight(.semibold))
          .padding(10)
      }
      .accessibilityLabel(Text("review.deck.remove"))
      .accessibilityIdentifier("review.deck.remove.\(item.id)")
    }
    .padding(15)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    .accessibilityIdentifier("review.deck.item.\(item.id)")
  }

  private var emptyState: some View {
    VStack(spacing: 14) {
      GrowingMascotView(mood: .cheer, size: 60)
        .frame(width: 108, height: 164)
      Text("review.deck.empty_title")
        .font(.system(.title2, design: .rounded, weight: .bold))
        .foregroundStyle(AppPalette.ink)
      Text("review.deck.empty_message")
        .font(.subheadline)
        .foregroundStyle(AppPalette.mutedInk)
        .multilineTextAlignment(.center)
    }
    .padding(24)
  }
}
