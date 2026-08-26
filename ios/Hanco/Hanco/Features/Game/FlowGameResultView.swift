import DeckKit
import SwiftUI

enum GameResultPresentation: Equatable {
  case flow
  case piyoCup
  case acidRain
  case choseong
  case wordMatch
  case dictation

  var analyticsValue: String {
    switch self {
    case .flow, .piyoCup: "flow"
    case .acidRain: "acid_rain"
    case .choseong: "choseong"
    case .wordMatch: "word_match"
    case .dictation: "dictation"
    }
  }

  func analyticsDifficulty(for deck: Deck) -> String {
    if self == .piyoCup { return "beginner" }
    return presetLevel(for: deck)?.rawValue ?? "custom"
  }

  var modeTitle: String {
    AppLocalization.string(modeTitleKey)
  }

  var modeTitleKey: String {
    switch self {
    case .flow: "game.mode.flow"
    case .piyoCup: "piyo_cup.title"
    case .acidRain: "game.mode.acid_rain"
    case .choseong: "game.mode.choseong"
    case .wordMatch: "game.mode.word_match"
    case .dictation: "game.mode.dictation"
    }
  }

  var presetGameKind: GameKind? {
    switch self {
    case .flow: .flow
    case .acidRain: .acidRain
    case .choseong: .choseong
    case .wordMatch: .wordMatch
    case .dictation: .dictation
    case .piyoCup: nil
    }
  }

  func presetLevel(for deck: Deck) -> GamePresetLevel? {
    guard let gameKind = presetGameKind else { return nil }
    return GamePresetLevel.allCases.first {
      $0.deckID(for: gameKind) == deck.deckId
    }
  }

  func resultLevelTitle(for deck: Deck) -> String {
    if self == .piyoCup {
      return AppLocalization.string("piyo_cup.weekly_badge")
    }
    guard let gameKind = presetGameKind, let level = presetLevel(for: deck) else {
      return deck.appName
    }
    return AppLocalization.string(level.titleKey(for: gameKind))
  }

  var clearLabel: LocalizedStringKey {
    switch self {
    case .flow: "game.result.clear"
    case .piyoCup: "piyo_cup.result.clear"
    case .acidRain: "acid_rain.result.clear"
    case .choseong: "choseong.result.clear"
    case .wordMatch: "word_match.result.clear"
    case .dictation: "dictation.result.clear"
    }
  }

  var speedLabel: LocalizedStringKey {
    switch self {
    case .flow, .piyoCup, .acidRain: "game.result.speed"
    case .choseong, .wordMatch, .dictation: "choseong.result.speed"
    }
  }

  var showsInputMode: Bool {
    self == .flow || self == .acidRain || self == .choseong || self == .wordMatch
      || self == .dictation
  }

  func shareAchievement(rank: String) -> String {
    switch self {
    case .flow:
      String(format: AppLocalization.string("result.share.game_badge"), rank)
    case .piyoCup:
      String(format: AppLocalization.string("piyo_cup.share.badge"), rank)
    case .acidRain:
      String(format: AppLocalization.string("acid_rain.share.badge"), rank)
    case .choseong:
      String(format: AppLocalization.string("choseong.share.badge"), rank)
    case .wordMatch:
      String(format: AppLocalization.string("word_match.share.badge"), rank)
    case .dictation:
      String(format: AppLocalization.string("dictation.share.badge"), rank)
    }
  }

  func shareCaption(deckName: String, score: Int) -> String {
    switch self {
    case .flow:
      String(
        format: AppLocalization.string("result.share.game_caption"),
        deckName,
        score
      )
    case .piyoCup:
      String(
        format: AppLocalization.string("piyo_cup.share.caption"),
        score
      )
    case .acidRain:
      String(
        format: AppLocalization.string("acid_rain.share.caption"),
        deckName,
        score
      )
    case .choseong:
      String(
        format: AppLocalization.string("choseong.share.caption"),
        deckName,
        score
      )
    case .wordMatch:
      String(
        format: AppLocalization.string("word_match.share.caption"),
        deckName,
        score
      )
    case .dictation:
      String(
        format: AppLocalization.string("dictation.share.caption"),
        deckName,
        score
      )
    }
  }
}

struct FlowGameResultView: View {
  @EnvironmentObject private var deckLibrary: DeckLibrary
  @EnvironmentObject private var retention: RetentionLibrary
  @EnvironmentObject private var companion: MascotCompanionLibrary
  @EnvironmentObject private var gameCenter: GameCenterService
  @State private var showsReviewDeck = false
  @State private var didCaptureAnalytics = false

  let deck: Deck
  let result: FlowGameResult
  let recordOutcome: GameRecordSaveOutcome?
  let reviewItems: [SessionReviewItem]
  let recommendations: [CatalogDeck]
  let catalogDecks: [CatalogDeck]
  let presentation: GameResultPresentation
  let onRetry: () -> Void
  let onFinish: () -> Void

  var body: some View {
    SessionResultView(
      navigationTitle: "game.result.navigation_title",
      onFinish: onFinish,
      celebratesNewRecord: recordOutcome?.isNewBest == true,
      header: { _ in rankCard },
      score: scoreCard,
      metrics: metricsCard,
      review: { _ in
        SessionResultReviewSection(
          items: reviewItems,
          onSeeAll: { showsReviewDeck = true }
        )
      },
      actions: { _ in nextActionsSection },
      bottomBar: { _ in retryBar }
    )
    .navigationDestination(isPresented: $showsReviewDeck) {
      ReviewDeckView()
    }
    .onAppear {
      companion.registerTOPIKSessionScore(result.score, sourceTags: deck.tags)
      captureAnalyticsIfNeeded()
    }
    .task(id: recordOutcome?.record.id) {
      if let record = recordOutcome?.record {
        gameCenter.submitScore(for: record)
      }
    }
  }

  private func captureAnalyticsIfNeeded() {
    guard !didCaptureAnalytics else { return }
    didCaptureAnalytics = true
    TelemetryService.shared.capture(
      .gameResult,
      properties: [
        .gameMode: presentation.analyticsValue,
        .difficulty: presentation.analyticsDifficulty(for: deck),
        .result: "completed",
        .scoreBucket: TelemetryService.shared.scoreBucket(result.score),
        .inputMode: recordOutcome?.record.inputMode.rawValue ?? "builtin",
        .deckSource: analyticsDeckSource,
      ]
    )
  }

  private var analyticsDeckSource: String {
    if presentation == .piyoCup || presentation.presetLevel(for: deck) != nil {
      return "bundled"
    }
    switch deckLibrary.records[deck.deckId]?.source {
    case .bundle: return "bundled"
    case .remote: return "catalog"
    case .imported: return "imported"
    case .created: return "created"
    case nil: return "unknown"
    }
  }

  private var rankCard: some View {
    HStack(spacing: 16) {
      GrowingMascotView(
        mood: recordOutcome?.isNewBest == true ? .surprise : .cheer,
        reaction: recordOutcome?.isNewBest == true ? .newBest : .wordCompleted,
        reactionRevision: 1,
        automaticProp: MascotSessionAppearance().automaticProp,
        showsNameTag: true,
        size: 64
      )
        .frame(width: 84, height: 128)
      VStack(alignment: .leading, spacing: 7) {
        HStack(spacing: 7) {
          Text(verbatim: presentation.modeTitle)
            .font(.system(.headline, design: .rounded, weight: .heavy))
            .foregroundStyle(AppPalette.secondary)
            .lineLimit(1)
            .minimumScaleFactor(0.76)
            .accessibilityIdentifier("game.result.mode")
          Text(verbatim: presentation.resultLevelTitle(for: deck))
            .font(.caption2.weight(.black))
            .foregroundStyle(AppPalette.accent)
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(AppPalette.accentSoft.opacity(0.62), in: Capsule())
            .accessibilityIdentifier("game.result.level")
        }
        Text(result.endedByLives ? "game.result.game_over" : presentation.clearLabel)
          .font(.system(.title2, design: .rounded, weight: .heavy))
          .foregroundStyle(AppPalette.ink)
          .accessibilityIdentifier("game.result.screen")
        HStack(alignment: .firstTextBaseline, spacing: 6) {
          Text(verbatim: result.rank)
            .font(.system(size: 58, weight: .black, design: .rounded))
            .foregroundStyle(rankColor)
          Text("game.result.rank")
            .font(.caption.weight(.bold))
            .foregroundStyle(AppPalette.mutedInk)
        }
        if presentation.showsInputMode, let inputMode = recordOutcome?.record.inputMode {
          Label(
            inputMode == .builtIn ? "input_mode.builtin" : "input_mode.os_ime",
            systemImage: inputMode == .builtIn ? "rectangle.grid.3x2.fill" : "keyboard"
          )
          .font(.caption2.weight(.bold))
          .foregroundStyle(AppPalette.secondary)
          .accessibilityIdentifier("game.result.input_mode")
        }
      }
      Spacer()
    }
    .padding(20)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 14, y: 8)
  }

  private func scoreCard(_ reveal: SessionResultRevealState) -> some View {
    let displayedScore = Int(Double(result.score) * reveal.scoreProgress)
    let newRecordOpacity = min(reveal.newRecordBurstProgress * 5, 1)
    return VStack(spacing: 5) {
      Text("game.score")
        .font(.caption.weight(.bold))
        .foregroundStyle(AppPalette.mutedInk)
      Text(verbatim: displayedScore.formatted())
        .font(.system(size: 44, weight: .black, design: .rounded))
        .foregroundStyle(AppPalette.accent)
        .monospacedDigit()
        .accessibilityIdentifier("game.result.score.value")
      if let recordOutcome {
        if recordOutcome.isNewBest {
          Label("game.result.new_record", systemImage: "sparkles")
            .font(.caption.weight(.black))
            .foregroundStyle(Color.orange)
            .opacity(newRecordOpacity)
            .scaleEffect(CGFloat(0.82 + newRecordOpacity * 0.18))
            .accessibilityIdentifier("game.result.new_record")
          if let previousBestScore = recordOutcome.previousBestScore {
            Text(
              String(
                format: AppLocalization.string("game.result.previous_best"),
                previousBestScore.formatted()
              )
            )
            .font(.caption2)
            .foregroundStyle(AppPalette.mutedInk)
            .opacity(newRecordOpacity)
          }
        } else {
          Text(
            String(
              format: AppLocalization.string("game.result.best_score"),
              recordOutcome.deckProgress.bestScore.formatted()
            )
          )
          .font(.caption.weight(.bold))
          .foregroundStyle(AppPalette.secondary)
          .accessibilityIdentifier("game.result.best_score.value")
        }
      }
    }
    .frame(maxWidth: .infinity)
    .padding(16)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
  }

  private func metricsCard(_ reveal: SessionResultRevealState) -> some View {
    let accuracyProgress = reveal.metricProgress(at: 0)
    let comboProgress = reveal.metricProgress(at: 1)
    let speedProgress = reveal.metricProgress(at: 2)
    return HStack(spacing: 0) {
      SessionResultMetric(
        value: String(
          format: AppLocalization.string("practice.result.accuracy_value"),
          result.accuracyPercent * accuracyProgress
        ),
        label: "practice.result.accuracy",
        progress: accuracyProgress * min(max(result.accuracyPercent / 100, 0), 1),
        identifier: "game.result.accuracy.value",
        tint: AppPalette.success
      )
      Divider().frame(height: 64)
      SessionResultMetric(
        value: String(Int(Double(result.maxCombo) * comboProgress)),
        label: "game.result.max_combo",
        progress: comboProgress * min(Double(result.maxCombo) / 20, 1),
        identifier: "game.result.combo.value",
        tint: .orange
      )
      Divider().frame(height: 64)
      SessionResultMetric(
        value: String(Int(result.charactersPerMinute * speedProgress)),
        label: presentation.speedLabel,
        progress: speedProgress * min(result.charactersPerMinute / 120, 1),
        identifier: "game.result.speed.value",
        tint: AppPalette.secondary
      )
    }
    .padding(.horizontal, 8)
    .padding(.vertical, 15)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
  }

  private var nextActionsSection: some View {
    VStack(spacing: 14) {
      if let record = recordOutcome?.record,
        gameCenter.isLeaderboardAvailable(for: record)
      {
        gameCenterButton(for: record)
      }

      SessionShareButton(
        model: shareCardModel,
        accessibilityIdentifier: "game.result.share"
      )

      if !reviewItems.isEmpty {
        NavigationLink {
          PracticeView(
            targets: reviewItems.map(\.item.ko),
            sessionTitle: AppLocalization.string("review.deck.title"),
            reviewSources: reviewItems.map {
              PracticeReviewSource(item: $0.item, sourceDeckId: $0.sourceDeckId)
            },
            analyticsSessionKind: "review",
            analyticsDeckSource: "review"
          )
        } label: {
          Label("result.review.start", systemImage: "arrow.triangle.2.circlepath")
            .font(.headline.weight(.bold))
            .foregroundStyle(AppPalette.accent)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 13)
            .background(AppPalette.accentSoft.opacity(0.55), in: RoundedRectangle(cornerRadius: 17))
        }
        .accessibilityIdentifier("game.result.review")
      }

      recommendationSection
    }
  }

  private func gameCenterButton(for record: GameRecord) -> some View {
    Button {
      gameCenter.showLeaderboard(for: record)
    } label: {
      HStack(spacing: 9) {
        if gameCenter.isDashboardBusy {
          ProgressView()
            .tint(Color.orange)
        } else {
          Image(systemName: "trophy.fill")
        }
        Text(verbatim: gameCenterButtonTitle(for: record))
        Spacer()
        if !gameCenter.isDashboardBusy {
          Image(systemName: "chevron.right")
        }
      }
      .font(.headline.weight(.bold))
      .foregroundStyle(Color.orange)
      .frame(maxWidth: .infinity)
      .padding(.vertical, 13)
      .padding(.horizontal, 15)
      .background(Color.orange.opacity(0.11), in: RoundedRectangle(cornerRadius: 17))
    }
    .disabled(!gameCenter.canPresentDashboard)
    .opacity(gameCenter.canPresentDashboard ? 1 : 0.64)
    .accessibilityIdentifier("game.result.game_center")
  }

  private func gameCenterButtonTitle(for record: GameRecord) -> String {
    if let rank = gameCenter.rank(for: record) {
      return String(
        format: AppLocalization.string("game_center.result_rank_format"),
        rank
      )
    }
    return AppLocalization.string("game_center.result_action")
  }

  @ViewBuilder
  private var recommendationSection: some View {
    if !recommendations.isEmpty {
      VStack(alignment: .leading, spacing: 11) {
        Label("recommendations.result.title", systemImage: "sparkles.rectangle.stack.fill")
          .font(.headline.weight(.bold))
          .foregroundStyle(AppPalette.ink)
        ForEach(recommendations, id: \.deckId) { recommendedDeck in
          NavigationLink {
            DeckDetailView(deck: recommendedDeck, catalogDecks: catalogDecks)
          } label: {
            DeckCardView(
              deck: recommendedDeck,
              isInstalled: deckLibrary.isInstalled(recommendedDeck.deckId),
              updateAvailable: deckLibrary.needsUpdate(recommendedDeck)
            )
          }
          .buttonStyle(.plain)
        }
      }
      .frame(maxWidth: .infinity, alignment: .leading)
    }
  }

  private var retryBar: some View {
    Button(action: onRetry) {
      Label("practice.result.retry", systemImage: "arrow.counterclockwise")
        .font(.headline.weight(.bold))
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(AppPalette.accent, in: RoundedRectangle(cornerRadius: 17))
    }
    .accessibilityIdentifier("game.result.retry")
  }

  private var rankColor: Color {
    switch result.rank {
    case "S": Color.orange
    case "A": AppPalette.accent
    case "B": AppPalette.secondary
    default: AppPalette.mutedInk
    }
  }

  private var shareCardModel: SessionShareCardModel {
    SessionShareCardModel(
      sessionTitle: presentation.modeTitle,
      sessionSubtitle: presentation.resultLevelTitle(for: deck),
      achievement: presentation.shareAchievement(rank: result.rank),
      scoreLabel: AppLocalization.string("game.score"),
      scoreValue: result.score.formatted(),
      metrics: [
        SessionShareCardMetric(
          id: "accuracy",
          label: AppLocalization.string("practice.result.accuracy"),
          value: String(
            format: AppLocalization.string("practice.result.accuracy_value"),
            result.accuracyPercent
          ),
          systemImage: "scope"
        ),
        SessionShareCardMetric(
          id: "combo",
          label: AppLocalization.string("game.result.max_combo"),
          value: result.maxCombo.formatted(),
          systemImage: "flame.fill"
        ),
        SessionShareCardMetric(
          id: "streak",
          label: AppLocalization.string("result.share.streak"),
          value: String(
            format: AppLocalization.string("result.share.streak_value"),
            currentStreak
          ),
          systemImage: "seal.fill"
        ),
      ],
      caption: presentation.shareCaption(deckName: deck.appName, score: result.score),
      mascotName: companion.displayName
    )
  }

  private var currentStreak: Int {
    retention.streak(asOf: JSTDay(date: RetentionClock.now())).current
  }
}
