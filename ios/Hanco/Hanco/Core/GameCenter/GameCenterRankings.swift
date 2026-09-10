import Foundation

enum GameCenterRankingScope: String, CaseIterable, Hashable {
  case global, friends
}

struct GameCenterRankingQuery: Hashable {
  let leaderboard: GameCenterLeaderboard
  let scope: GameCenterRankingScope
}

struct GameCenterRankingEntry: Equatable, Identifiable {
  let id: String
  let displayName: String
  let rank: Int
  let score: Int
}

/// A server read, scoped to one account, board and (for the Cup) occurrence.
/// Counts describe submitted players, never concurrent/online players.
struct GameCenterRankingSnapshot: Equatable {
  let localEntry: GameCenterRankingEntry?
  let entries: [GameCenterRankingEntry]
  let totalPlayerCount: Int
  let fetchedAt: Date
  let periodStart: Date?

  var nearbyEntries: [GameCenterRankingEntry] {
    var unique = Dictionary(entries.filter { $0.rank > 0 }.map { ($0.id, $0) },
      uniquingKeysWith: { _, latest in latest })
    if let localEntry { unique[localEntry.id] = localEntry }
    return unique.values.sorted {
      $0.rank == $1.rank ? $0.id < $1.id : $0.rank < $1.rank
    }
  }

  /// Only describe an adjacent, verified row. A different score or a tie is a
  /// target to match, not a promise of overtaking someone after the next attempt.
  var nextTarget: GameCenterRankingEntry? {
    guard let localEntry, localEntry.rank > 1 else { return nil }
    return nearbyEntries.first {
      $0.id != localEntry.id && $0.rank == localEntry.rank - 1 && $0.score >= localEntry.score
    }
  }

  var pointsToMatch: Int? {
    guard let localEntry, let nextTarget else { return nil }
    return nextTarget.score - localEntry.score
  }

  static func nearbyRange(localRank: Int?) -> NSRange {
    NSRange(location: max(1, (localRank ?? 1) - 2), length: 5)
  }

  func belongsToCurrentWeek(asOf date: Date) -> Bool {
    periodStart == nil || periodStart == PiyoCupWeek.start(containing: date)
  }
}

struct GameCenterRankingReadState: Equatable {
  var snapshot: GameCenterRankingSnapshot?
  var isLoading = false
  var failed = false

  func needsRefresh(asOf date: Date, maxAge: TimeInterval = 60) -> Bool {
    guard !isLoading else { return false }
    guard !failed, let snapshot, snapshot.belongsToCurrentWeek(asOf: date) else { return true }
    return date.timeIntervalSince(snapshot.fetchedAt) >= maxAge
  }
}

enum GameCenterRankingSelection {
  static func preferredLeaderboard(
    candidates: [GameCenterLeaderboard], records: [GameRecord]
  ) -> GameCenterLeaderboard? {
    let allowed = Set(candidates)
    return records.sorted { $0.playedAt > $1.playedAt }
      .compactMap { GameCenterLeaderboard.leaderboard(for: $0) }
      .first(where: allowed.contains) ?? candidates.first
  }
}
