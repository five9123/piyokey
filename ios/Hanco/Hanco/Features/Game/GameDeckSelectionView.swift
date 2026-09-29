import DeckKit
import SwiftUI

enum PiyoCupDeckLoader {
  static func load(bundle: Bundle = .main) -> Deck? {
    let contract = GameCenterRankedDeck.piyoCupDeck
    guard let url = bundle.url(
      forResource: "\(contract.deckID)_v\(contract.version)",
      withExtension: "json",
      subdirectory: "decks/flow"
    ),
      let data = try? Data(contentsOf: url),
      let deck = try? DeckKitJSON.decodeDeck(from: data),
      deck.deckId == contract.deckID,
      deck.version == contract.version,
      DeckValidator.validate(deck).isEmpty
    else { return nil }
    return deck
  }
}

enum GamePresetLevel: String, CaseIterable, Equatable {
  static let bundledDeckVersion = 3
  static let bundledItemCount = 100

  case beginner
  case intermediate
  case advanced

  func deckID(for gameKind: GameKind) -> String {
    "\(gameKind.rawValue)_topik_\(rawValue)"
  }

  func titleKey(for gameKind: GameKind) -> String {
    "game.\(gameKind.rawValue).preset.\(rawValue).title"
  }

  func difficultyKey(for gameKind: GameKind) -> String {
    "game.\(gameKind.rawValue).preset.\(rawValue).difficulty"
  }

  func detailKey(for gameKind: GameKind) -> String {
    "game.\(gameKind.rawValue).preset.\(rawValue).detail"
  }

  var deckLevel: Int {
    switch self {
    case .beginner: 1
    case .intermediate: 2
    case .advanced: 3
    }
  }
}

struct GamePreset: Equatable, Identifiable {
  let gameKind: GameKind
  let level: GamePresetLevel
  let deck: Deck

  var id: String { level.rawValue }

  var localizedDeck: Deck {
    deck
  }
}

enum GamePresetDeckLoader {
  static func load(gameKind: GameKind, bundle: Bundle = .main) -> [GamePreset] {
    GamePresetLevel.allCases.compactMap { level in
      let deckID = level.deckID(for: gameKind)
      guard let url = bundle.url(
        forResource: "\(deckID)_v\(GamePresetLevel.bundledDeckVersion)",
        withExtension: "json",
        subdirectory: "decks/\(gameKind.rawValue)"
      ),
        let data = try? Data(contentsOf: url),
        let deck = try? DeckKitJSON.decodeDeck(from: data),
        deck.deckId == deckID,
        deck.version == GamePresetLevel.bundledDeckVersion,
        deck.type == .word,
        deck.level == level.deckLevel,
        deck.tags.contains("TOPIK"),
        deck.tags.contains("タイピング"),
        deck.items.count == GamePresetLevel.bundledItemCount,
        Set(deck.items.map(\.ko)).count == deck.items.count,
        DeckValidator.validate(deck).isEmpty
      else { return nil }
      return GamePreset(gameKind: gameKind, level: level, deck: deck)
    }
  }
}

enum GamePresetSessionRandomizer {
  private static var usesDeterministicUITestOrder: Bool {
    #if DEBUG
      ProcessInfo.processInfo.environment["UITEST_JST_DAY"] != nil
    #else
      false
    #endif
  }

  static func isBundledPreset(deckID: String, gameKind: GameKind) -> Bool {
    GamePresetLevel.allCases.contains { $0.deckID(for: gameKind) == deckID }
  }

  static func freshSeed() -> UInt64 {
    var generator = SystemRandomNumberGenerator()
    return generator.next()
  }

  static func seed(for deck: Deck, gameKind: GameKind) -> UInt64 {
    isBundledPreset(deckID: deck.deckId, gameKind: gameKind) && !usesDeterministicUITestOrder
      ? freshSeed()
      : ChoseongQuizBuilder.seed(for: deck.deckId)
  }

  static func shuffledItems(
    from deck: Deck,
    gameKind: GameKind,
    seed: UInt64 = freshSeed(),
    avoiding previousItems: [DeckItem] = []
  ) -> [DeckItem] {
    guard isBundledPreset(deckID: deck.deckId, gameKind: gameKind) else {
      return deck.items
    }
    if usesDeterministicUITestOrder { return deck.items }
    var items = deck.items
    var generator = ChoseongRandomNumberGenerator(seed: seed)
    items.shuffle(using: &generator)
    if items.count > 1, items.map(\.id) == previousItems.map(\.id) {
      items.append(items.removeFirst())
    }
    return items
  }
}

enum FlowGameCourse: String, Codable, Equatable {
  case shortWord = "short_word"
  case word
  case sentence

  init(deck: Deck) {
    if deck.type == .sentence {
      self = .sentence
    } else {
      let averageLength = deck.items.isEmpty
        ? 0
        : Double(deck.items.reduce(0) { $0 + $1.ko.count }) / Double(deck.items.count)
      self = averageLength <= 3 ? .shortWord : .word
    }
  }

  var cardTravelDuration: TimeInterval {
    switch self {
    case .shortWord: 7
    case .word: 9
    case .sentence: 12
    }
  }

  var localizedName: LocalizedStringKey {
    switch self {
    case .shortWord: "game.course.short_word"
    case .word: "game.course.word"
    case .sentence: "game.course.sentence"
    }
  }
}

struct GameDeckSelectionView: View {
  @Environment(\.hancoAdaptiveMetrics) private var adaptiveMetrics
  @Environment(\.dynamicTypeSize) private var dynamicTypeSize
  @EnvironmentObject private var gameCenter: GameCenterService

  let onFindDecks: () -> Void
  private let piyoCupDeck = PiyoCupDeckLoader.load()

  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(spacing: 14) {
          #if !PIYOKEY_MAC_DEMO
          piyoCupCard
          #endif

          LazyVGrid(
            columns: Array(
              repeating: GridItem(.flexible(), spacing: 14),
              count: dynamicTypeSize.isAccessibilitySize ? 1 : (adaptiveMetrics.widthClass == .wide ? 3 : 2)
            ),
            alignment: .center,
            spacing: 14
          ) {
            ForEach(GameKind.allCases, id: \.rawValue) { gameKind in
              NavigationLink {
                GameDeckListView(gameKind: gameKind, onFindDecks: onFindDecks)
              } label: {
                gameCard(gameKind)
              }
              .buttonStyle(.plain)
              .accessibilityIdentifier("game.mode.\(gameKind.rawValue)")
              .appTourTarget(.gameModes, enabled: gameKind == .flow)
            }

            NavigationLink {
              SpacingPassageListView()
            } label: {
              spacingGameCard
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("game.mode.spacing")
          }
        }
        .frame(maxWidth: adaptiveMetrics.hubContentMaxWidth)
        .frame(maxWidth: .infinity)
        .padding(.horizontal, adaptiveMetrics.horizontalPadding)
        .padding(.vertical, 18)
        .accessibilityIdentifier("game.mode.grid")
      }
      .background(gameBackground)
      .navigationTitle(Text("game.selection.navigation_title"))
      .navigationBarTitleDisplayMode(.inline)
      .accessibilityIdentifier("game.selection.screen")
      .rootSettingsToolbar()
      #if !PIYOKEY_MAC_DEMO
        .task { gameCenter.prepare() }
      #endif
    }
  }

  @ViewBuilder
  private var piyoCupCard: some View {
    if let piyoCupDeck {
      NavigationLink {
        FlowGameView(deck: piyoCupDeck, competition: .weeklyPiyoCup)
      } label: {
        piyoCupCardLabel(isAvailable: true)
      }
      .buttonStyle(.plain)
      .accessibilityIdentifier("game.piyo_cup")
    } else {
      piyoCupCardLabel(isAvailable: false)
        .opacity(0.6)
        .accessibilityIdentifier("game.piyo_cup.unavailable")
    }
  }

  private func piyoCupCardLabel(isAvailable: Bool) -> some View {
    HStack(spacing: 15) {
      ZStack {
        RoundedRectangle(cornerRadius: 19, style: .continuous)
          .fill(Color.yellow.opacity(0.22))
          .frame(width: 62 * adaptiveMetrics.typographyScale, height: 62 * adaptiveMetrics.typographyScale)
        Image(systemName: "crown.fill")
          .font(.system(size: 28, weight: .black))
          .foregroundStyle(Color.orange)
      }
      VStack(alignment: .leading, spacing: 5) {
        HStack(spacing: 7) {
          Text("piyo_cup.title")
            .font(.system(.headline, design: .rounded, weight: .heavy))
            .foregroundStyle(AppPalette.ink)
          Text("piyo_cup.weekly_badge")
            .font(.caption2.weight(.black))
            .foregroundStyle(Color.orange)
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(Color.orange.opacity(0.12), in: Capsule())
        }
        Text(
          LocalizedStringKey(
            isAvailable ? "piyo_cup.detail" : "piyo_cup.unavailable"
          )
        )
          .font(.caption)
          .foregroundStyle(AppPalette.mutedInk)
          .multilineTextAlignment(.leading)
          .fixedSize(horizontal: false, vertical: true)
      }
      Spacer(minLength: 6)
      Image(systemName: isAvailable ? "play.circle.fill" : "exclamationmark.circle.fill")
        .font(.title2)
        .foregroundStyle(Color.orange)
    }
    .padding(16)
    .frame(maxWidth: .infinity)
    .background(
      LinearGradient(
        colors: [Color.yellow.opacity(0.13), AppPalette.card],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
      ),
      in: RoundedRectangle(cornerRadius: 24, style: .continuous)
    )
    .overlay {
      RoundedRectangle(cornerRadius: 24, style: .continuous)
        .stroke(Color.orange.opacity(0.3), lineWidth: 1.5)
    }
  }

  private func gameCard(_ gameKind: GameKind) -> some View {
    gameCard(
      title: gameKind.titleKey,
      detail: gameKind.detailKey,
      action: "game.mode.choose_deck",
      systemImage: gameKind.systemImage,
      tint: gameKind.tint
    )
  }

  private var spacingGameCard: some View {
    gameCard(
      title: "game.mode.spacing",
      detail: "game.mode.spacing_detail",
      action: "spacing.selection.choose_passage",
      systemImage: "text.word.spacing",
      tint: .teal
    )
  }

  private func gameCard(
    title: LocalizedStringKey,
    detail: LocalizedStringKey,
    action: LocalizedStringKey,
    systemImage: String,
    tint: Color
  ) -> some View {
    VStack(alignment: .leading, spacing: 12) {
      ZStack {
        RoundedRectangle(cornerRadius: 18, style: .continuous)
          .fill(tint.opacity(0.15))
          .frame(width: 58 * adaptiveMetrics.typographyScale, height: 58 * adaptiveMetrics.typographyScale)
        Image(systemName: systemImage)
          .font(.system(size: 27, weight: .bold))
          .foregroundStyle(tint)
      }

      Text(title)
        .font(.system(.headline, design: .rounded, weight: .heavy))
        .foregroundStyle(AppPalette.ink)
        .fixedSize(horizontal: false, vertical: true)

      Text(detail)
        .font(.caption)
        .foregroundStyle(AppPalette.mutedInk)
        .multilineTextAlignment(.leading)
        .fixedSize(horizontal: false, vertical: true)

      Spacer(minLength: 4)

      HStack(spacing: 5) {
        Text(action)
        Image(systemName: "chevron.right")
      }
      .font(.caption.weight(.bold))
      .foregroundStyle(tint)
    }
    .frame(maxWidth: .infinity, minHeight: 210, alignment: .topLeading)
    .padding(16)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    .overlay {
      RoundedRectangle(cornerRadius: 24, style: .continuous)
        .stroke(tint.opacity(0.2), lineWidth: 1.5)
    }
    .shadow(color: AppPalette.keyShadow, radius: 10, y: 6)
  }

  private var gameBackground: some View {
    LinearGradient(
      colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
      startPoint: .topLeading,
      endPoint: .bottomTrailing
    )
    .ignoresSafeArea()
  }
}

private struct GameDeckListView: View {
  @Environment(\.dismiss) private var dismiss
  @Environment(\.hancoAdaptiveMetrics) private var adaptiveMetrics
  @EnvironmentObject private var deckLibrary: DeckLibrary
  @EnvironmentObject private var gameProgress: GameProgressLibrary

  let gameKind: GameKind
  let onFindDecks: () -> Void
  private let gamePresets: [GamePreset]

  init(gameKind: GameKind, onFindDecks: @escaping () -> Void) {
    self.gameKind = gameKind
    self.onFindDecks = onFindDecks
    gamePresets = GamePresetDeckLoader.load(gameKind: gameKind)
  }

  var body: some View {
    ScrollView {
      VStack(spacing: 18) {
        if !gamePresets.isEmpty {
          gamePresetDecks
        } else if availableInstalledDecks.isEmpty {
          emptyState
        } else {
          installedDecks
        }
      }
      .padding(18)
      .hancoCenteredContent(maxWidth: adaptiveMetrics.readableContentMaxWidth)
    }
    .background(
      LinearGradient(
        colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
      )
      .ignoresSafeArea()
    )
    .navigationTitle(gameKind.titleKey)
    .navigationBarTitleDisplayMode(.inline)
    .accessibilityIdentifier("game.deck_selection.screen")
    .rootSettingsToolbar()
  }

  private var gamePresetDecks: some View {
    VStack(alignment: .leading, spacing: 14) {
      Text(LocalizedStringKey(presetLocalizationKey("selection.intro")))
        .font(.subheadline)
        .foregroundStyle(AppPalette.mutedInk)
        .fixedSize(horizontal: false, vertical: true)

      LazyVGrid(
        columns: [
          GridItem(.flexible(), spacing: 12),
          GridItem(.flexible(), spacing: 12),
        ],
        spacing: 12
      ) {
        ForEach(gamePresets) { preset in
          NavigationLink {
            gameDestination(for: preset.localizedDeck)
          } label: {
            gamePresetCard(preset)
          }
          .buttonStyle(.plain)
          .accessibilityIdentifier(
            "game.\(gameKind.rawValue).preset.\(preset.level.rawValue)"
          )
        }

        Button(action: findDecks) {
          gamePresetAddDeckCard
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("game.\(gameKind.rawValue).add_deck")
      }

      if !availableInstalledDecks.isEmpty {
        VStack(alignment: .leading, spacing: 12) {
          Label(
            LocalizedStringKey(presetLocalizationKey("added_decks.title")),
            systemImage: "square.stack.3d.up.fill"
          )
            .font(.headline.weight(.bold))
            .foregroundStyle(AppPalette.ink)

          ForEach(availableInstalledDecks, id: \.deckId) { deck in
            deckLink(deck)
          }
        }
        .padding(.top, 4)
      }
    }
  }

  private func gamePresetCard(_ preset: GamePreset) -> some View {
    let tint = gamePresetTint(preset.level)
    return VStack(alignment: .leading, spacing: 9) {
      HStack(alignment: .top) {
        Image(systemName: gamePresetIcon(preset.level))
          .font(.system(size: 23, weight: .black))
          .foregroundStyle(tint)
          .frame(width: 42, height: 42)
          .background(tint.opacity(0.13), in: RoundedRectangle(cornerRadius: 13))
        Spacer(minLength: 4)
        Image(systemName: "play.circle.fill")
          .font(.title3)
          .foregroundStyle(tint)
      }

      Text(LocalizedStringKey(preset.level.titleKey(for: gameKind)))
        .font(.system(.headline, design: .rounded, weight: .heavy))
        .foregroundStyle(AppPalette.ink)

      Text(LocalizedStringKey(preset.level.difficultyKey(for: gameKind)))
        .font(.caption2.weight(.black))
        .foregroundStyle(tint)
        .padding(.horizontal, 7)
        .padding(.vertical, 3)
        .background(tint.opacity(0.1), in: Capsule())

      Text(LocalizedStringKey(preset.level.detailKey(for: gameKind)))
        .font(.caption2)
        .foregroundStyle(AppPalette.mutedInk)
        .fixedSize(horizontal: false, vertical: true)

      Spacer(minLength: 2)

      Text(
        String(
          format: AppLocalization.string(
            "game.\(gameKind.rawValue).preset.word_count"
          ),
          preset.deck.items.count
        )
      )
      .font(.caption.weight(.bold))
      .foregroundStyle(AppPalette.secondary)

      if let progress = gameProgress.progress(
        for: preset.deck.deckId,
        gameKind: gameKind,
        inputMode: .builtIn
      ) {
        Text(
          AppLocalization.format("game.selection.best_score.builtin",
            progress.bestScore.formatted()
          )
        )
        .font(.caption2.weight(.bold))
        .foregroundStyle(AppPalette.accent)
      }
      if let progress = gameProgress.progress(
        for: preset.deck.deckId,
        gameKind: gameKind,
        inputMode: .builtInKorean10Key
      ) {
        Text(
          AppLocalization.format("game.selection.best_score.korean_10key",
            progress.bestScore.formatted()
          )
        )
        .font(.caption2.weight(.bold))
        .foregroundStyle(AppPalette.secondary)
      }
    }
    .frame(maxWidth: .infinity, minHeight: 206, alignment: .topLeading)
    .padding(14)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    .overlay {
      RoundedRectangle(cornerRadius: 22, style: .continuous)
        .stroke(tint.opacity(0.24), lineWidth: 1.5)
    }
    .shadow(color: AppPalette.keyShadow, radius: 8, y: 5)
  }

  private var gamePresetAddDeckCard: some View {
    VStack(alignment: .leading, spacing: 10) {
      Image(systemName: "plus")
        .font(.system(size: 25, weight: .black))
        .foregroundStyle(AppPalette.accent)
        .frame(width: 44, height: 44)
        .background(AppPalette.accentSoft.opacity(0.55), in: Circle())

      Text(LocalizedStringKey(presetLocalizationKey("add_deck.title")))
        .font(.system(.headline, design: .rounded, weight: .heavy))
        .foregroundStyle(AppPalette.ink)

      Text(LocalizedStringKey(presetLocalizationKey("add_deck.detail")))
        .font(.caption2)
        .foregroundStyle(AppPalette.mutedInk)
        .fixedSize(horizontal: false, vertical: true)

      Spacer(minLength: 2)

      Label(
        LocalizedStringKey(presetLocalizationKey("add_deck.action")),
        systemImage: "sparkle.magnifyingglass"
      )
        .font(.caption.weight(.bold))
        .foregroundStyle(AppPalette.accent)
    }
    .frame(maxWidth: .infinity, minHeight: 206, alignment: .topLeading)
    .padding(14)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    .overlay {
      RoundedRectangle(cornerRadius: 22, style: .continuous)
        .stroke(
          AppPalette.accent.opacity(0.34),
          style: StrokeStyle(lineWidth: 1.5, dash: [6, 5])
        )
    }
  }

  private func gamePresetTint(_ level: GamePresetLevel) -> Color {
    switch level {
    case .beginner: AppPalette.success
    case .intermediate: AppPalette.secondary
    case .advanced: Color.purple
    }
  }

  private func gamePresetIcon(_ level: GamePresetLevel) -> String {
    switch level {
    case .beginner: "leaf.fill"
    case .intermediate: "bolt.fill"
    case .advanced: "crown.fill"
    }
  }

  private func presetLocalizationKey(_ suffix: String) -> String {
    "game.\(gameKind.rawValue).\(suffix)"
  }

  private var installedDecks: some View {
    VStack(alignment: .leading, spacing: 12) {
      HStack {
        Label("game.selection.choose_deck", systemImage: "square.stack.3d.up.fill")
          .font(.headline.weight(.bold))
          .foregroundStyle(AppPalette.ink)
        Spacer()
        Button("game.selection.find_more", action: findDecks)
          .font(.caption.weight(.bold))
          .foregroundStyle(AppPalette.accent)
      }

      ForEach(availableInstalledDecks, id: \.deckId) { deck in
        deckLink(deck)
      }
    }
  }

  private var availableInstalledDecks: [Deck] {
    #if PIYOKEY_MAC_DEMO
      deckLibrary.installed.filter(\.official)
    #else
      deckLibrary.installed
    #endif
  }

  @ViewBuilder
  private func deckLink(_ deck: Deck) -> some View {
    if !supportsGame(deck) {
      deckRow(deck)
        .opacity(0.6)
        .accessibilityIdentifier("game.deck.unavailable.\(deck.deckId)")
    } else {
      NavigationLink {
        gameDestination(for: deck)
      } label: {
        deckRow(deck)
      }
      .buttonStyle(.plain)
      .accessibilityIdentifier("game.deck.\(deck.deckId)")
    }
  }

  @ViewBuilder
  private func gameDestination(for deck: Deck) -> some View {
    switch gameKind {
    case .flow:
      FlowGameView(deck: deck)
    case .acidRain:
      FlowGameView(deck: deck, gameKind: .acidRain)
    case .choseong:
      ChoseongTypingView(deck: deck)
    case .wordMatch:
      ChoseongTypingView(deck: deck, mode: .wordMatch)
    case .dictation:
      ChoseongTypingView(deck: deck, mode: .dictation)
    }
  }

  private func supportsGame(_ deck: Deck) -> Bool {
    switch gameKind {
    case .flow, .acidRain:
      return !deck.items.isEmpty
    case .choseong:
      return supportsChoseong(deck)
    case .wordMatch:
      return supportsWordMatch(deck)
    case .dictation:
      return supportsDictation(deck)
    }
  }

  private func supportsChoseong(_ deck: Deck) -> Bool {
    !ChoseongTypingBuilder.rounds(
      items: deck.items,
      limit: 1,
      seed: ChoseongQuizBuilder.seed(for: deck.deckId)
    ).isEmpty
  }

  private func supportsWordMatch(_ deck: Deck) -> Bool {
    !WordMatchTypingBuilder.rounds(
      items: deck.items,
      limit: 1,
      seed: ChoseongQuizBuilder.seed(for: deck.deckId)
    ).isEmpty
  }

  private func supportsDictation(_ deck: Deck) -> Bool {
    !DictationTypingBuilder.rounds(
      items: deck.items,
      limit: 1,
      seed: ChoseongQuizBuilder.seed(for: deck.deckId)
    ).isEmpty
  }

  private var emptyState: some View {
    VStack(spacing: 14) {
      Image(systemName: "square.stack.3d.up.slash")
        .font(.system(size: 38, weight: .semibold))
        .foregroundStyle(AppPalette.mutedInk)
      Text("game.selection.empty.title")
        .font(.headline.weight(.bold))
        .foregroundStyle(AppPalette.ink)
      Text("game.selection.empty.message")
        .font(.caption)
        .foregroundStyle(AppPalette.mutedInk)
        .multilineTextAlignment(.center)
      Button(action: findDecks) {
        Label("game.selection.find_decks", systemImage: "sparkle.magnifyingglass")
          .font(.headline.weight(.bold))
          .foregroundStyle(.white)
          .frame(maxWidth: .infinity)
          .padding(.vertical, 14)
          .background(AppPalette.accent, in: RoundedRectangle(cornerRadius: 17))
      }
      .accessibilityIdentifier("game.find_decks")
    }
    .frame(maxWidth: .infinity)
    .padding(22)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
  }

  private func findDecks() {
    dismiss()
    Task { @MainActor in
      await Task.yield()
      onFindDecks()
    }
  }

  private func deckRow(_ deck: Deck) -> some View {
    let course = FlowGameCourse(deck: deck)
    return HStack(spacing: 14) {
      DeckCoverView(deck: deck, width: 70, height: 88)
      VStack(alignment: .leading, spacing: 6) {
        Text(verbatim: deck.appName)
          .font(.system(.headline, design: .rounded, weight: .bold))
          .foregroundStyle(AppPalette.ink)
          .lineLimit(2)
        HStack(spacing: 7) {
          Text(gameKind == .flow ? course.localizedName : gameKind.titleKey)
          Text(AppLocalization.format("deck.items.format", deck.items.count))
        }
        .font(.caption.weight(.semibold))
        .foregroundStyle(AppPalette.secondary)
        Text(deckHintKey(for: deck))
          .font(.caption2)
          .foregroundStyle(AppPalette.mutedInk)
        if gameKind == .flow,
          GameCenterRankedDeck.isEligible(deckID: deck.deckId, version: deck.version)
        {
          Label("game_center.ranked_deck", systemImage: "trophy.fill")
            .font(.caption2.weight(.black))
            .foregroundStyle(Color.orange)
            .accessibilityIdentifier("game.deck.ranked.\(deck.deckId)")
        }
        if let progress = gameProgress.progress(
          for: deck.deckId,
          gameKind: gameKind,
          inputMode: .builtIn
        ) {
          Text(
            AppLocalization.format(
                bestScoreKey
              ,
              progress.bestScore.formatted()
            )
          )
          .font(.caption2.weight(.bold))
          .foregroundStyle(AppPalette.accent)
          .accessibilityIdentifier("game.deck.best_score.\(deck.deckId)")
        }
        if let progress = gameProgress.progress(
          for: deck.deckId,
          gameKind: gameKind,
          inputMode: .builtInKorean10Key
        ) {
          Text(
            AppLocalization.format("game.selection.best_score.korean_10key",
              progress.bestScore.formatted()
            )
          )
          .font(.caption2.weight(.bold))
          .foregroundStyle(AppPalette.secondary)
          .accessibilityIdentifier("game.deck.best_score.korean_10key.\(deck.deckId)")
        }
        if let progress = gameProgress.progress(
          for: deck.deckId,
          gameKind: gameKind,
          inputMode: .osIME
        ) {
          Text(
            AppLocalization.format("game.selection.best_score.os_ime",
              progress.bestScore.formatted()
            )
          )
          .font(.caption2.weight(.bold))
          .foregroundStyle(AppPalette.secondary)
          .accessibilityIdentifier("game.deck.best_score.os_ime.\(deck.deckId)")
        }
      }
      Spacer()
      Image(systemName: "play.circle.fill")
        .font(.title2)
        .foregroundStyle(AppPalette.accent)
    }
    .padding(14)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 9, y: 5)
  }

  private func deckHintKey(for deck: Deck) -> LocalizedStringKey {
    switch gameKind {
    case .flow:
      return "game.selection.deck_hint.flow"
    case .acidRain:
      return "game.selection.deck_hint.acid_rain"
    case .choseong:
      return supportsChoseong(deck)
        ? "game.selection.deck_hint.choseong"
        : "game.selection.deck_hint.choseong_unavailable"
    case .wordMatch:
      return supportsWordMatch(deck)
        ? "game.selection.deck_hint.word_match"
        : "game.selection.deck_hint.word_match_unavailable"
    case .dictation:
      return supportsDictation(deck)
        ? "game.selection.deck_hint.dictation"
        : "game.selection.deck_hint.dictation_unavailable"
    }
  }

  private var bestScoreKey: String {
    switch gameKind {
    case .flow: "game.selection.best_score.builtin"
    case .acidRain: "game.selection.best_score.acid_rain"
    case .choseong: "game.selection.best_score.choseong"
    case .wordMatch: "game.selection.best_score.word_match"
    case .dictation: "game.selection.best_score.dictation"
    }
  }
}

private extension GameKind {
  var titleKey: LocalizedStringKey {
    switch self {
    case .flow: "game.mode.flow"
    case .acidRain: "game.mode.acid_rain"
    case .choseong: "game.mode.choseong"
    case .wordMatch: "game.mode.word_match"
    case .dictation: "game.mode.dictation"
    }
  }

  var detailKey: LocalizedStringKey {
    switch self {
    case .flow: "game.mode.flow_detail"
    case .acidRain: "game.mode.acid_rain_detail"
    case .choseong: "game.mode.choseong_detail"
    case .wordMatch: "game.mode.word_match_detail"
    case .dictation: "game.mode.dictation_detail"
    }
  }

  var systemImage: String {
    switch self {
    case .flow: "rectangle.and.hand.point.up.left.filled"
    case .acidRain: "cloud.rain.fill"
    case .choseong: "questionmark.bubble.fill"
    case .wordMatch: "character.book.closed.fill"
    case .dictation: "ear.badge.waveform"
    }
  }

  var tint: Color {
    switch self {
    case .flow: AppPalette.secondary
    case .acidRain: Color.cyan
    case .choseong: AppPalette.accent
    case .wordMatch: Color.orange
    case .dictation: Color.purple
    }
  }
}
