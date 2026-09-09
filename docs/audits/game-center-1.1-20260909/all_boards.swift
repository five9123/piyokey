import Foundation

@main struct AllBoardsAudit {
  struct Spec {
    let deck: String
    let board: String
    let cup: Bool

    func record(_ score: Int = 1234) -> GameRecord {
      // OS cup isolates its single target; built-in cup is checked separately.
      GameRecord(deckId: deck, competition: cup ? .weeklyPiyoCup : .officialDeck,
                 inputMode: cup ? .osIME : .builtIn, score: score)
    }
  }

  // Independent shipping contract, deliberately not generated from service mapping.
  static let specs: [Spec] = [
    ("flow", 4), ("acid_rain", 3), ("choseong", 3), ("dictation", 3), ("word_match", 4),
  ].flatMap { game, version in
    ["beginner", "intermediate", "advanced"].map { level in
      Spec(deck: "\(game)_topik_\(level)", board: "piyokey.v\(version).\(game).\(level)", cup: false)
    }
  } + [Spec(deck: "flow_topik_beginner", board: "piyokey.v4.cup.weekly.flow", cup: true)]
  static let legacyIDs = [
    "piyokey.v3.flow.beginner", "piyokey.v3.flow.intermediate", "piyokey.v3.flow.advanced",
    "piyokey.v3.cup.weekly.flow",
  ]
  @MainActor static var checks = 0

  @MainActor static func pause(_ ms: UInt64 = 25) async {
    try? await Task.sleep(nanoseconds: ms * 1_000_000)
  }
  @MainActor static func fullList() -> [GKLeaderboard] {
    (specs.map(\.board) + legacyIDs).map(GKLeaderboard.init)
  }
  @MainActor static func reset(authenticated: Bool = true) {
    GKLeaderboard.fullListOverride = fullList()
    GKLeaderboard.fullListError = nil
    GKLeaderboard.holdFullList = false
    GKLeaderboard.fullListLoadCount = 0
    GKLeaderboard.submitted = []
    GKLeaderboard.failSubmission = false
    GKLeaderboard.holdSubmission = false
    GKLeaderboard.entries = [:]
    GKLocalPlayer.local.isAuthenticated = authenticated
    GKLocalPlayer.local.authenticateHandler = nil
    GKAccessPoint.shared.isPresentingGameCenter = false
  }
  @MainActor static func service() -> GameCenterService {
    GameCenterService(isEnabled: true, availabilityContract: .init(rawLeaderboardIDs: specs.map(\.board)))
  }
  @MainActor static func assertScope(_ record: GameRecord, _ expected: Set<String>) {
    precondition(Set(GameCenterLeaderboard.leaderboards(for: record).map(\.rawValue)) == expected)
    checks += 1
  }
  @MainActor static func eligibility() {
    precondition(Set(specs.map(\.board)) == Set(GameCenterLeaderboard.allCases.map(\.rawValue)))
    for spec in specs where !spec.cup {
      for mode in [SessionInputMode.builtIn, .osIME, .builtInKorean10Key] {
        var record = spec.record(); record.inputMode = mode
        assertScope(record, mode == .builtIn ? [spec.board] : [])
      }
      for version: Int? in [nil, 2, 4] {
        var record = spec.record(); record.deckVersion = version
        assertScope(record, [])
      }
      var lesson = spec.record(); lesson.mode = .lesson
      assertScope(lesson, [])
    }
    for id in ["remote_dynamic_deck", "user_custom_deck", "spacing_level_1"] {
      assertScope(GameRecord(deckId: id, score: 100), [])
    }
    let cup = specs.last!
    for mode in [SessionInputMode.builtIn, .osIME, .builtInKorean10Key] {
      var record = cup.record(); record.inputMode = mode
      let expected: Set<String> = mode == .builtIn
        ? [cup.board, specs[0].board] : (mode == .osIME ? [cup.board] : [])
      assertScope(record, expected)
    }
    print("ELIGIBILITY \(checks) assertions passed: 15 presets x modes/versions/lesson + exclusions + cup modes")
  }

  @MainActor static func availability(name: String, list: [GKLeaderboard], blocked: Set<String>, error: Bool = false, timeout: Bool = false) async {
    reset()
    GKLeaderboard.fullListOverride = list
    GKLeaderboard.fullListError = error ? NSError(domain: "AuditNetwork", code: 1) : nil
    GKLeaderboard.holdFullList = timeout
    let client = service()
    client.prepare()
    if timeout {
      await pause()
      precondition(specs.allSatisfy { client.isLeaderboardAvailable(for: $0.record()) })
      client.submitScore(for: specs[0].record())
      precondition(GKLeaderboard.submitted.isEmpty) // probe in flight delays submission, not button
      await pause(5200)
    } else { await pause() }
    for spec in specs {
      precondition(client.isLeaderboardAvailable(for: spec.record()) == !blocked.contains(spec.board))
      client.submitScore(for: spec.record())
    }
    await pause()
    precondition(Set(GKLeaderboard.submitted.map { $0.0 }) == Set(specs.map(\.board)).subtracting(blocked))
    let hasFailureFlag = client.lastSyncFailed
    GKLeaderboard.fullListOverride = fullList()
    GKLeaderboard.fullListError = nil
    GKLeaderboard.holdFullList = false
    client.updateSceneActivity(false); client.updateSceneActivity(true)
    client.prepare(); client.synchronize(); await pause()
    precondition(GKLeaderboard.fullListLoadCount == 1)
    for spec in specs {
      precondition(client.isLeaderboardAvailable(for: spec.record()) == !blocked.contains(spec.board))
    }
    print("AVAILABILITY \(name): blocked=\(blocked.count)/16 submittedBoards=\(16-blocked.count) fullListLoads=1 failureFlagAfterSubmissions=\(hasFailureFlag)")
  }

  @MainActor static func perBoardFailures(_ spec: Spec) async {
    // F1: first sign-in opens the dashboard before uploading the local score.
    reset(authenticated: false)
    let first = service(); let record = spec.record()
    first.submitScore(for: record); first.showLeaderboard(for: record)
    GKLocalPlayer.local.isAuthenticated = true
    GKLocalPlayer.local.authenticateHandler?(nil, nil)
    await pause(250)
    precondition(GKAccessPoint.shared.isPresentingGameCenter && GKLeaderboard.submitted.isEmpty)
    GKAccessPoint.shared.isPresentingGameCenter = false
    await pause(150)
    precondition(GKLeaderboard.submitted.contains { $0.0 == spec.board && $0.1 == record.score })

    // F2: foreground / lower next score do not repair a failed higher score.
    reset()
    let retry = service(); retry.prepare(); await pause()
    GKLeaderboard.failSubmission = true
    retry.submitScore(for: spec.record(9000)); await pause()
    GKLeaderboard.failSubmission = false
    retry.updateSceneActivity(false); retry.updateSceneActivity(true); await pause()
    precondition(GKLeaderboard.submitted.map { $0.1 } == [9000])
    retry.submitScore(for: spec.record(3000)); await pause()
    precondition(GKLeaderboard.submitted.map { $0.1 } == [9000, 3000] && !retry.lastSyncFailed)
    retry.synchronize(); await pause()
    precondition(GKLeaderboard.submitted.map { $0.1 } == [9000, 3000, 9000])

    // F3: held callback blocks equal/lower submissions and does not raise failure.
    reset()
    let held = service(); held.prepare(); await pause()
    GKLeaderboard.holdSubmission = true
    held.submitScore(for: spec.record(8000)); await pause()
    held.synchronize(); await pause()
    held.submitScore(for: spec.record(4000)); await pause()
    precondition(GKLeaderboard.submitted.map { $0.1 } == [8000] && !held.lastSyncFailed)

    // F4: an empty successful rank response stays cached after a server entry appears.
    reset()
    let stale = service(); stale.prepare(); await pause()
    let scored = spec.record(7000)
    stale.submitScore(for: scored); await pause()
    precondition(stale.rank(for: scored) == nil)
    GKLeaderboard.entries[spec.board] = .init(rank: 7)
    stale.synchronize(); await pause()
    precondition(stale.rank(for: scored) == nil)
    print("LIFECYCLE \(spec.board): F1/F2/F3/F4 reproduced (4/4)")
  }

  @MainActor static func cupFallback() async {
    for missing in [Set([specs.last!.board]), Set([specs[0].board]), Set([specs.last!.board, specs[0].board])] {
      reset()
      GKLeaderboard.fullListOverride = fullList().filter { !missing.contains($0.baseLeaderboardID) }
      let client = service(); client.prepare(); await pause()
      var cup = specs.last!.record(); cup.inputMode = .builtIn
      let expected: Set<String> = [specs.last!.board, specs[0].board]
      let remaining = expected.subtracting(missing)
      precondition(client.isLeaderboardAvailable(for: cup) == !remaining.isEmpty)
      client.submitScore(for: cup); await pause()
      precondition(Set(GKLeaderboard.submitted.map { $0.0 }) == remaining)
      print("CUP_BUILTIN missing=\(missing.sorted()) button=\(!remaining.isEmpty) submitted=\(remaining.sorted())")
    }
  }

  @MainActor static func main() async {
    eligibility()
    await availability(name: "normal_20", list: fullList(), blocked: [])
    await availability(name: "empty_success", list: [], blocked: Set(specs.map(\.board)))
    await availability(name: "legacy_only_success", list: legacyIDs.map(GKLeaderboard.init), blocked: Set(specs.map(\.board)))
    await availability(name: "error_fallback", list: [], blocked: [], error: true)
    await availability(name: "timeout_fallback", list: [], blocked: [], timeout: true)
    for spec in specs {
      await availability(name: "omit_\(spec.board)", list: fullList().filter { $0.baseLeaderboardID != spec.board }, blocked: [spec.board])
      let list = fullList()
      list.first { $0.baseLeaderboardID == spec.board }!.releaseState = []
      await availability(name: "unreleased_\(spec.board)", list: list, blocked: [spec.board])
      await perBoardFailures(spec)
    }
    await cupFallback()
    print("AUDIT_COMPLETE: 111 eligibility assertions; 37 availability scenarios; 64 lifecycle defect reproductions; 3 cup fallback scenarios. Mock responses only; real-device failure response remains unknown.")
  }
}
