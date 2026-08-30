import DeckKit
import SwiftUI

struct DeckCardView: View {
  static let compactMinimumHeight: CGFloat = 124
  static let regularMinimumHeight: CGFloat = 146

  let deck: CatalogDeck
  var rank: Int?
  var isInstalled = false
  var updateAvailable = false
  var compact = false
  var fixedHeight: CGFloat?
  var limitsTitleToOneLine = false

  var body: some View {
    HStack(alignment: .top, spacing: compact ? 9 : 13) {
      DeckCoverView(
        deck: deck,
        width: compact ? 56 : 88,
        height: compact ? 72 : 112
      )

      VStack(alignment: .leading, spacing: compact ? 5 : 7) {
        HStack(spacing: 6) {
          if deck.official {
            Label("deck.badge.official", systemImage: "checkmark.seal.fill")
              .font(.caption2.weight(.bold))
              .foregroundStyle(AppPalette.secondary)
          }
          if let rank, rank <= 3 {
            Label(
              AppLocalization.format("deck.rank.format", rank),
              systemImage: rank == 1 ? "crown.fill" : "medal.fill"
            )
            .font(.caption2.weight(.bold))
            .foregroundStyle(rankColor)
          }
          if isInstalled {
            Label(
              updateAvailable ? "deck.badge.update" : "deck.badge.installed",
              systemImage: updateAvailable ? "arrow.triangle.2.circlepath" : "checkmark.circle.fill"
            )
            .font(.caption2.weight(.bold))
            .foregroundStyle(updateAvailable ? Color.orange : AppPalette.success)
          }
        }

        Text(verbatim: deck.appName)
          .font(.system(.headline, design: .rounded, weight: .bold))
          .foregroundStyle(AppPalette.ink)
          .lineLimit(limitsTitleToOneLine ? 1 : 2)
          .truncationMode(.tail)
          .accessibilityLabel(Text(verbatim: deck.appName))

        Text(verbatim: deck.appAuthorNickname)
          .font(.caption)
          .foregroundStyle(AppPalette.mutedInk)
          .lineLimit(1)

        HStack(spacing: 5) {
          ForEach(deck.appTags.prefix(2), id: \.self) { tag in
            Text(verbatim: "#\(tag)")
              .font(.caption2.weight(.semibold))
              .foregroundStyle(AppPalette.accent)
              .lineLimit(limitsTitleToOneLine ? 1 : nil)
              .truncationMode(.tail)
              .padding(.horizontal, 7)
              .padding(.vertical, 4)
              .background(AppPalette.accentSoft.opacity(0.48), in: Capsule())
          }
        }

        HStack(spacing: 10) {
          Label(itemCountText, systemImage: "rectangle.stack")
          if deck.downloadsTotal > 0 {
            Label(downloadText, systemImage: "arrow.down.circle")
          }
          Text(levelText)
        }
        .font(.caption2.weight(.medium))
        .foregroundStyle(AppPalette.mutedInk)
        .lineLimit(1)
        .minimumScaleFactor(0.75)
      }
      .frame(maxWidth: .infinity, alignment: .leading)
    }
    .padding(compact ? 11 : 14)
    .frame(maxWidth: .infinity, minHeight: minimumHeight, alignment: .leading)
    .frame(height: resolvedFixedHeight, alignment: .leading)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 10, y: 6)
    .accessibilityElement(children: .combine)
    .accessibilityIdentifier("discover.deck.\(deck.deckId)")
  }

  private var itemCountText: String {
    AppLocalization.format("deck.items.format", deck.itemCount)
  }

  private var resolvedFixedHeight: CGFloat? {
    fixedHeight.map { max($0, minimumHeight) }
  }

  private var minimumHeight: CGFloat {
    compact ? Self.compactMinimumHeight : Self.regularMinimumHeight
  }

  private var downloadText: String {
    deck.downloadsTotal.formatted(
      .number.notation(.compactName).locale(AppLocalization.locale)
    )
  }

  private var levelText: String {
    let type = AppLocalization.string(deck.type == .word ? "deck.type.word" : "deck.type.sentence")
    let level = AppLocalization.format("deck.level.format", deck.level)
    return "\(type) · \(level)"
  }

  private var rankColor: Color {
    switch rank {
    case 1: return Color.orange
    case 2: return Color.gray
    default: return Color.brown
    }
  }
}

struct DeckCoverView: View {
  private let deckId: String
  private let name: String
  private let tags: [String]
  private let type: DeckType
  private let width: CGFloat
  private let height: CGFloat

  init(deck: CatalogDeck, width: CGFloat, height: CGFloat) {
    deckId = deck.deckId
    name = deck.appName
    tags = deck.appTags
    type = deck.type
    self.width = width
    self.height = height
  }

  init(deck: Deck, width: CGFloat, height: CGFloat) {
    deckId = deck.deckId
    name = deck.appName
    tags = deck.appTags
    type = deck.type
    self.width = width
    self.height = height
  }

  var body: some View {
    ZStack(alignment: .topLeading) {
      LinearGradient(
        colors: coverColors,
        startPoint: .topLeading,
        endPoint: .bottomTrailing
      )

      Text(verbatim: String(tags.first?.prefix(1) ?? name.prefix(1)))
        .font(.system(size: min(width, height) * 0.36, weight: .black, design: .rounded))
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity, maxHeight: .infinity)

      Image(systemName: type == .word ? "textformat.abc" : "text.quote")
        .font(.caption.weight(.bold))
        .foregroundStyle(.white.opacity(0.9))
        .padding(8)
    }
    .frame(width: width, height: height)
    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    .overlay {
      RoundedRectangle(cornerRadius: 18, style: .continuous)
        .stroke(.white.opacity(0.7), lineWidth: 2)
    }
  }

  private var coverColors: [Color] {
    let palettes: [[Color]] = [
      [AppPalette.accent, Color.orange.opacity(0.78)],
      [AppPalette.secondary, Color.cyan.opacity(0.72)],
      [Color.purple.opacity(0.82), AppPalette.accent],
      [AppPalette.success, Color.teal.opacity(0.78)],
      [Color.indigo.opacity(0.8), Color.pink.opacity(0.78)],
    ]
    let checksum = deckId.unicodeScalars.reduce(0) { ($0 * 31 + Int($1.value)) % 10_007 }
    return palettes[checksum % palettes.count]
  }
}
