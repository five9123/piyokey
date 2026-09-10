import SwiftUI

extension GameCenterLeaderboard {
  static func board(game: GameKind, level: GamePresetLevel) -> GameCenterLeaderboard? {
    GameCenterRankedDeck.matching(deckID: level.deckID(for: game),
      version: GamePresetLevel.bundledDeckVersion)?.leaderboard
  }

  var level: GamePresetLevel? {
    guard self != .weeklyPiyoCup, let suffix = rawValue.split(separator: ".").last else { return nil }
    return GamePresetLevel(rawValue: String(suffix))
  }

  var displayTitle: String {
    if self == .weeklyPiyoCup { return AppLocalization.string("piyo_cup.title") }
    guard let deck = GameCenterRankedDeck.all.first(where: { $0.leaderboard == self }),
      let level else { return AppLocalization.string("game_center.ranking.leaderboards") }
    let game = deck.deckID.replacingOccurrences(of: "_topik_\(level.rawValue)", with: "")
    return AppLocalization.format("game_center.ranking.title",
      AppLocalization.string("game.mode.\(game)"),
      AppLocalization.string("game_center.ranking.level.\(level.rawValue)"))
  }
}

/// A single, explicit button opens the selected native board without requiring a GameRecord.
struct GameCenterRankingLink: View {
  @EnvironmentObject private var gameCenter: GameCenterService
  let leaderboard: GameCenterLeaderboard
  var showsLevel = false
  var accessibilityID: String? = nil

  var body: some View {
    TimelineView(.periodic(from: .now, by: 60)) { context in
      let read = gameCenter.summary(for: leaderboard, asOf: context.date)
      Button {
        gameCenter.showLeaderboard(leaderboard)
      } label: {
        HStack(spacing: 7) {
          Image(systemName: "trophy")
          VStack(alignment: .leading, spacing: 2) {
            if showsLevel, let level = leaderboard.level {
              Text(AppLocalization.string("game_center.ranking.level.\(level.rawValue)"))
                .font(.caption2)
            }
            if gameCenter.isAuthenticated, let entry = read.snapshot?.localEntry {
              Text(AppLocalization.format("game_center.ranking.world_rank", entry.rank))
            } else {
              Text("game_center.ranking.leaderboards")
            }
          }
          .fixedSize(horizontal: false, vertical: true)
          Spacer(minLength: 2)
          if read.isLoading { ProgressView().controlSize(.small) }
          Image(systemName: "chevron.right").font(.caption2)
        }
        .font(.caption.weight(.semibold))
        .foregroundStyle(AppPalette.accent)
        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
        .contentShape(Rectangle())
      }
      .buttonStyle(.plain)
      .disabled(!gameCenter.canPresentDashboard)
      .accessibilityIdentifier(accessibilityID ?? "game_center.ranking.open.\(leaderboard.rawValue)")
      .accessibilityHint(Text(leaderboard.displayTitle))
      .task(id: PiyoCupWeek.start(containing: context.date)) {
        gameCenter.refreshRankings([leaderboard])
      }
    }
  }
}

struct GameCenterRankingPanel: View {
  @EnvironmentObject private var gameCenter: GameCenterService
  let leaderboard: GameCenterLeaderboard
  var record: GameRecord? = nil

  var body: some View {
    VStack(alignment: .leading, spacing: 7) {
      GameCenterRankingLink(leaderboard: leaderboard,
        accessibilityID: record == nil ? nil : "game.result.game_center")
      TimelineView(.periodic(from: .now, by: 60)) { context in
        let read = gameCenter.summary(for: leaderboard, asOf: context.date)
        if !gameCenter.isAuthenticated {
          Text("game_center.submission.sign_in").font(.caption)
        } else {
          if let snapshot = read.snapshot {
            if let entry = snapshot.localEntry {
              Text(AppLocalization.format("game_center.ranking.server_best", entry.score.formatted()))
                .font(.caption.weight(.semibold))
            } else {
              Text(snapshot.isEmpty
                ? "game_center.ranking.empty" : "game_center.ranking.not_played")
                .font(.caption)
            }
            GameCenterRankingTimestamp(date: snapshot.fetchedAt)
          }
          if read.failed { Text("game_center.ranking.failed").font(.caption) }
          if read.isLoading { Text("game_center.ranking.loading").font(.caption) }
        }
      }
      if let record, gameCenter.isAuthenticated,
        let state = gameCenter.submissionState(for: record)
      {
        HStack {
          Text(LocalizedStringKey(state.localizationKey)).font(.caption)
            .accessibilityIdentifier("game.result.game_center_status")
          if state == .failed || state == .unconfirmed {
            Button("game_center.submission.retry") { gameCenter.retrySubmission(for: record) }
              .font(.caption.weight(.semibold))
              .frame(minHeight: 44)
              .accessibilityIdentifier("game.result.game_center_retry")
          }
        }
      }
      ViewThatFits(in: .horizontal) {
        HStack { detailsLink; Spacer(minLength: 8); refreshButton }
        VStack(alignment: .leading, spacing: 0) { detailsLink; refreshButton }
      }
    }
    .foregroundStyle(AppPalette.mutedInk)
    .accessibilityElement(children: .contain)
  }

  private var detailsLink: some View {
    NavigationLink {
      GameCenterRankingDetailView(leaderboard: leaderboard)
    } label: {
      Label("game_center.ranking.nearby", systemImage: "list.number")
        .font(.caption.weight(.semibold)).frame(minHeight: 44)
    }
    .accessibilityIdentifier("game_center.ranking.details.\(leaderboard.rawValue)")
  }

  private var refreshButton: some View {
    Button {
      gameCenter.refreshRankings([leaderboard], force: true)
    } label: {
      Label("game_center.ranking.refresh", systemImage: "arrow.clockwise")
        .font(.caption).frame(minHeight: 44)
    }
    .disabled(!gameCenter.isAuthenticated || gameCenter.summary(for: leaderboard).isLoading)
  }
}

extension GameCenterService.SubmissionState {
  var localizationKey: String {
    switch self {
    case .pending: "game_center.submission.pending"
    case .submitting: "game_center.submission.submitting"
    case .confirming: "game_center.submission.confirming"
    case .submitted: "game_center.submission.submitted"
    case .failed: "game_center.submission.failed"
    case .unconfirmed: "game_center.submission.unconfirmed"
    }
  }
}

private struct GameCenterRankingTimestamp: View {
  let date: Date
  var body: some View {
    HStack(spacing: 4) {
      Text("game_center.ranking.checked")
      Text(date.formatted(Date.FormatStyle(date: .abbreviated, time: .shortened)
        .locale(AppLocalization.locale)))
    }
    .font(.caption2)
    .accessibilityElement(children: .combine)
  }
}

struct GameCenterRankingDetailView: View {
  @EnvironmentObject private var gameCenter: GameCenterService
  @Environment(\.scenePhase) private var scenePhase
  @Environment(\.dynamicTypeSize) private var dynamicTypeSize
  @State private var scope = GameCenterRankingScope.global
  let leaderboard: GameCenterLeaderboard

  private var query: GameCenterRankingQuery { .init(leaderboard: leaderboard, scope: scope) }

  var body: some View {
    TimelineView(.periodic(from: .now, by: 60)) { context in
      let read = gameCenter.rankingDetails[query] ?? GameCenterRankingReadState()
      let snapshot = read.snapshot.flatMap { $0.belongsToCurrentWeek(asOf: context.date) ? $0 : nil }
      ScrollView {
        VStack(alignment: .leading, spacing: 16) {
          Picker("game_center.ranking.scope", selection: $scope) {
            Text("game_center.ranking.world").tag(GameCenterRankingScope.global)
            Text("game_center.ranking.friends").tag(GameCenterRankingScope.friends)
          }
          .pickerStyle(.segmented)
          .accessibilityIdentifier("game_center.ranking.scope")
          if !gameCenter.isAuthenticated {
            Text("game_center.submission.sign_in")
          } else if let snapshot {
            rankingContent(snapshot)
            GameCenterRankingTimestamp(date: snapshot.fetchedAt)
          } else if !read.isLoading && !read.failed {
            Text("game_center.ranking.not_played")
          }
          if read.isLoading { ProgressView("game_center.ranking.loading") }
          if read.failed { Text("game_center.ranking.failed").foregroundStyle(AppPalette.mutedInk) }
          Button {
            gameCenter.showLeaderboard(leaderboard, scope: scope)
          } label: {
            Label("game_center.ranking.open_game_center", systemImage: "trophy")
              .font(.caption.weight(.semibold))
              .fixedSize(horizontal: false, vertical: true)
              .frame(minHeight: 44)
          }
          .disabled(!gameCenter.canPresentDashboard)
          .accessibilityIdentifier("game_center.ranking.native")
          Button("game_center.ranking.refresh") { refresh(force: true) }
            .frame(minHeight: 44)
            .disabled(!gameCenter.isAuthenticated || read.isLoading)
        }
        .padding(18)
        .hancoCenteredContent(maxWidth: 620)
      }
      .task(id: PiyoCupWeek.start(containing: context.date)) { refresh() }
    }
    .background(AppPalette.backgroundBottom)
    .navigationTitle(Text(leaderboard.displayTitle))
    .navigationBarTitleDisplayMode(.inline)
    .accessibilityIdentifier("game_center.ranking.detail.screen")
    .onChange(of: scope) { _ in refresh() }
    .onChange(of: scenePhase) { phase in if phase == .active { refresh(force: true) } }
    .onChange(of: gameCenter.isDashboardBusy) { busy in if !busy { refresh(force: true) } }
  }

  @ViewBuilder
  private func rankingContent(_ snapshot: GameCenterRankingSnapshot) -> some View {
    if scope == .friends, snapshot.totalPlayerCount <= 1,
      snapshot.nearbyEntries.allSatisfy({ $0.id == snapshot.localEntry?.id })
    {
      Text("game_center.ranking.no_friends")
    } else if snapshot.isEmpty {
      Text("game_center.ranking.empty")
    } else if snapshot.localEntry == nil {
      Text("game_center.ranking.not_played")
    }
    if let local = snapshot.localEntry {
      if local.rank == 1 {
        Text("game_center.ranking.first").font(.headline)
      } else if let points = snapshot.pointsToMatch {
        Text(points == 0 ? AppLocalization.string("game_center.ranking.tie")
          : AppLocalization.format("game_center.ranking.target", points.formatted()))
          .font(.headline)
      }
    }
    ForEach(snapshot.nearbyEntries) { entry in
      let layout = dynamicTypeSize.isAccessibilitySize
        ? AnyLayout(VStackLayout(alignment: .leading, spacing: 8))
        : AnyLayout(HStackLayout(alignment: .top, spacing: 12))
      layout {
        HStack(alignment: .top, spacing: 8) {
          Text(entry.rank.formatted()).monospacedDigit()
          Text(entry.id == snapshot.localEntry?.id
            ? AppLocalization.string("game_center.ranking.you") : entry.displayName)
            .fixedSize(horizontal: false, vertical: true)
          Spacer(minLength: 0)
        }
        Text(entry.score.formatted()).monospacedDigit()
          .lineLimit(1).minimumScaleFactor(0.6).layoutPriority(1)
      }
      .font(.body.weight(entry.id == snapshot.localEntry?.id ? .semibold : .regular))
      .padding(14)
      .frame(maxWidth: .infinity)
      .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 14))
      .accessibilityElement(children: .combine)
      .accessibilityIdentifier("game_center.ranking.row.\(entry.id)")
    }
    Text(AppLocalization.format("game_center.ranking.participants", snapshot.totalPlayerCount))
      .font(.caption).foregroundStyle(AppPalette.mutedInk)
  }

  private func refresh(force: Bool = false) { gameCenter.refreshRankingDetails(query, force: force) }
}

struct GameCenterGrowthCard: View {
  @EnvironmentObject private var gameCenter: GameCenterService
  @EnvironmentObject private var companion: MascotCompanionLibrary

  var body: some View {
    VStack(alignment: .leading, spacing: 14) {
      Text("game_center.growth.title").font(.headline)
      ForEach(GameCenterGrowthAchievement.allCases, id: \.rawValue) { achievement in
        let value = gameCenter.growthProgress[achievement] ?? 0
        VStack(alignment: .leading, spacing: 5) {
          HStack(alignment: .top) {
            Text(AppLocalization.string("game_center.growth.\(achievement.rawValue.split(separator: ".").last ?? "")"))
            Spacer(minLength: 8)
            Text(value / 100, format: .percent.precision(.fractionLength(0)))
              .monospacedDigit()
          }
          ProgressView(value: value, total: 100)
        }
        .font(.caption)
        .accessibilityElement(children: .combine)
      }
      Text(companion.hasUnlocked(.gameCenterTrophy)
        ? "game_center.growth.trophy_unlocked" : "game_center.growth.trophy_condition")
        .font(.caption)
      Button { gameCenter.showAchievements() } label: {
        Label("game_center.growth.open", systemImage: "rosette")
          .font(.caption.weight(.semibold))
          .fixedSize(horizontal: false, vertical: true)
          .frame(minHeight: 44)
          .foregroundStyle(AppPalette.accent)
      }
      .disabled(!gameCenter.canPresentDashboard)
      .accessibilityIdentifier("game_center.growth.open")
    }
    .foregroundStyle(AppPalette.ink)
    .padding(18)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 24))
    .accessibilityElement(children: .contain)
    .accessibilityIdentifier("game_center.growth.card")
    .task { gameCenter.prepare() }
  }
}
