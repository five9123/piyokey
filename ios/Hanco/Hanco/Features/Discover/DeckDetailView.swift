import DeckKit
import SwiftUI

struct DeckDetailView: View {
  @EnvironmentObject private var deckLibrary: DeckLibrary

  let deck: CatalogDeck
  let catalogDecks: [CatalogDeck]

  var body: some View {
    ScrollView {
      VStack(alignment: .leading, spacing: 20) {
        header
        statistics
        preview
        contentReportLink
        recommendations
      }
      .padding(18)
      .padding(.bottom, 92)
    }
    .background(
      LinearGradient(
        colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
      )
      .ignoresSafeArea()
    )
    .navigationTitle(Text("deck.detail.navigation_title"))
    .navigationBarTitleDisplayMode(.inline)
    .safeAreaInset(edge: .bottom) { actionBar }
  }

  private var header: some View {
    VStack(alignment: .leading, spacing: 14) {
      HStack(alignment: .top, spacing: 16) {
        DeckCoverView(deck: deck, width: 116, height: 148)

        VStack(alignment: .leading, spacing: 8) {
          if deck.official {
            Label("deck.badge.official", systemImage: "checkmark.seal.fill")
              .font(.caption.weight(.bold))
              .foregroundStyle(AppPalette.secondary)
          }
          Text(verbatim: deck.appName)
            .font(.system(.title2, design: .rounded, weight: .bold))
            .foregroundStyle(AppPalette.ink)
          Text(verbatim: deck.appAuthorNickname)
            .font(.subheadline)
            .foregroundStyle(AppPalette.mutedInk)
          Text(sizeText)
            .font(.caption.weight(.semibold))
            .foregroundStyle(AppPalette.mutedInk)
        }
      }

      ScrollView(.horizontal, showsIndicators: false) {
        HStack(spacing: 7) {
          ForEach(deck.appTags, id: \.self) { tag in
            Text(verbatim: "#\(tag)")
              .font(.caption.weight(.semibold))
              .foregroundStyle(AppPalette.accent)
              .padding(.horizontal, 10)
              .padding(.vertical, 6)
              .background(AppPalette.accentSoft.opacity(0.5), in: Capsule())
          }
        }
      }
    }
    .padding(18)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 25, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 12, y: 7)
  }

  private var statistics: some View {
    HStack(spacing: 0) {
      statistic(
        value: deck.downloadsTotal.formatted(.number.locale(AppLocalization.locale)),
        label: "deck.detail.downloads"
      )
      Divider().frame(height: 42)
      statistic(value: String(deck.itemCount), label: "deck.detail.items")
      Divider().frame(height: 42)
      statistic(value: averageLengthText, label: "deck.detail.average_length")
    }
    .padding(.vertical, 14)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
  }

  private var preview: some View {
    VStack(alignment: .leading, spacing: 12) {
      HStack {
        Label("deck.detail.preview", systemImage: "eye.fill")
          .font(.headline.weight(.bold))
        Spacer()
        Text(
          String(
            format: AppLocalization.string("deck.detail.preview_count"),
            deck.previewItems.count
          )
        )
        .font(.caption.weight(.semibold))
        .foregroundStyle(AppPalette.mutedInk)
      }

      ForEach(Array(deck.previewItems.enumerated()), id: \.offset) { index, item in
        HStack(alignment: .firstTextBaseline, spacing: 12) {
          Text(verbatim: String(index + 1))
            .font(.caption.monospacedDigit().weight(.bold))
            .foregroundStyle(AppPalette.secondary)
            .frame(width: 22)
          VStack(alignment: .leading, spacing: 3) {
            Text(verbatim: item.ko)
              .font(.system(.body, design: .rounded, weight: .bold))
              .foregroundStyle(AppPalette.ink)
            if let meaning = item.appMeaning {
              Text(verbatim: meaning)
                .font(.caption)
                .foregroundStyle(AppPalette.mutedInk)
            }
          }
          Spacer()
        }
        .padding(.vertical, 5)
        .accessibilityIdentifier("deck.preview.\(index)")
        if index < deck.previewItems.count - 1 {
          Divider()
        }
      }
    }
    .padding(18)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
  }

  private var contentReportLink: some View {
    ContentFeedbackLink(
      context: .report(deckID: deck.deckId, deckVersion: deck.version)
    ) {
      HStack(spacing: 12) {
        Image(systemName: "exclamationmark.bubble.fill")
          .font(.system(size: 17, weight: .semibold))
          .foregroundStyle(AppPalette.accent)
          .frame(width: 36, height: 36)
          .background(AppPalette.accentSoft.opacity(0.5), in: RoundedRectangle(cornerRadius: 11))

        VStack(alignment: .leading, spacing: 2) {
          Text("content_feedback.report.title")
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(AppPalette.ink)
          Text("content_feedback.report.detail")
            .font(.caption)
            .foregroundStyle(AppPalette.mutedInk)
        }

        Spacer()

        Image(systemName: "arrow.up.right")
          .font(.caption.weight(.bold))
          .foregroundStyle(AppPalette.mutedInk)
      }
      .padding(16)
      .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
      .contentShape(Rectangle())
    }
    .buttonStyle(.plain)
    .accessibilityIdentifier("deck.detail.content_report")
  }

  @ViewBuilder
  private var recommendations: some View {
    if !relatedDecks.isEmpty {
      VStack(alignment: .leading, spacing: 11) {
        Label("deck.detail.related", systemImage: "square.stack.3d.up.fill")
          .font(.headline.weight(.bold))
          .foregroundStyle(AppPalette.ink)

        ScrollView(.horizontal, showsIndicators: false) {
          HStack(spacing: 12) {
            ForEach(relatedDecks, id: \.deckId) { related in
              NavigationLink {
                DeckDetailView(deck: related, catalogDecks: catalogDecks)
              } label: {
                DeckCardView(
                  deck: related,
                  isInstalled: deckLibrary.isInstalled(related.deckId),
                  updateAvailable: deckLibrary.needsUpdate(related)
                )
                .frame(width: 290)
              }
              .buttonStyle(.plain)
            }
          }
          .padding(.bottom, 10)
        }
      }
    }
  }

  private var actionBar: some View {
    VStack(spacing: 7) {
      if deckLibrary.failedDeckIDs.contains(deck.deckId) {
        Text("deck.detail.install_error")
          .font(.caption)
          .foregroundStyle(AppPalette.error)
      }

      if let installedDeck = deckLibrary.installedDeck(deck.deckId), !deckLibrary.needsUpdate(deck)
      {
        NavigationLink {
          DeckPracticeDestination(deck: installedDeck)
        } label: {
          actionLabel("deck.detail.play", systemImage: "play.fill")
        }
        .accessibilityIdentifier("deck.detail.play")
      } else {
        Button {
          Task { await deckLibrary.install(deck) }
        } label: {
          if deckLibrary.installingDeckIDs.contains(deck.deckId) {
            HStack(spacing: 9) {
              ProgressView().tint(.white)
              Text("deck.detail.installing")
            }
            .frame(maxWidth: .infinity)
          } else {
            actionLabel(
              deckLibrary.needsUpdate(deck) ? "deck.detail.update" : "deck.detail.download",
              systemImage: deckLibrary.needsUpdate(deck)
                ? "arrow.triangle.2.circlepath" : "arrow.down.circle.fill"
            )
          }
        }
        .disabled(deckLibrary.installingDeckIDs.contains(deck.deckId))
        .accessibilityIdentifier(
          deckLibrary.needsUpdate(deck) ? "deck.detail.update" : "deck.detail.download"
        )
      }
    }
    .font(.headline.weight(.bold))
    .foregroundStyle(.white)
    .padding(.horizontal, 18)
    .padding(.vertical, 12)
    .background(.ultraThinMaterial)
  }

  private func actionLabel(_ title: LocalizedStringKey, systemImage: String) -> some View {
    HStack(spacing: 8) {
      Text(title)
      Image(systemName: systemImage)
    }
    .frame(maxWidth: .infinity)
    .padding(.vertical, 14)
    .background(AppPalette.accent, in: RoundedRectangle(cornerRadius: 17))
  }

  private func statistic(value: String, label: LocalizedStringKey) -> some View {
    VStack(spacing: 4) {
      Text(verbatim: value)
        .font(.subheadline.monospacedDigit().weight(.bold))
        .foregroundStyle(AppPalette.ink)
      Text(label)
        .font(.caption2)
        .foregroundStyle(AppPalette.mutedInk)
    }
    .frame(maxWidth: .infinity)
  }

  private var relatedDecks: [CatalogDeck] {
    catalogDecks
      .filter { candidate in
        candidate.deckId != deck.deckId && candidate.isAvailableInCurrentLanguage
          && !Set(candidate.tags).isDisjoint(with: deck.tags)
      }
      .sorted { lhs, rhs in
        let lhsMatches = Set(lhs.tags).intersection(deck.tags).count
        let rhsMatches = Set(rhs.tags).intersection(deck.tags).count
        if lhsMatches == rhsMatches { return lhs.downloadsTotal > rhs.downloadsTotal }
        return lhsMatches > rhsMatches
      }
      .prefix(6)
      .map { $0 }
  }

  private var averageLengthText: String {
    guard !deck.previewItems.isEmpty else { return "—" }
    let total = deck.previewItems.reduce(0) { $0 + $1.ko.count }
    return String(format: "%.1f", Double(total) / Double(deck.previewItems.count))
  }

  private var sizeText: String {
    ByteCountFormatter.string(fromByteCount: Int64(deck.sizeBytes), countStyle: .file)
  }
}
