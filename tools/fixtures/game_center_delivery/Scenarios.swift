import Foundation
@main struct DeliveryScenarios {
  @MainActor static func pause(_ seconds: Double = 0.035) async {
    try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
  }
  @MainActor static func check(_ condition: @autoclosure () -> Bool, _ label: String) {
    precondition(condition(), "FAIL: \(label)")
    print("PASS: \(label)"); fflush(stdout)
  }
  @MainActor static func makeService(timeout: Double = 0.07, delay: Double = 0.06) -> GameCenterService {
    GKLocalPlayer.local.gamePlayerID = UUID().uuidString
    GKLocalPlayer.local.isAuthenticated = true
    GKLeaderboard.submitted = []
    GKLeaderboard.callbacks = []
    GKLeaderboard.entryCallbacks = []
    GKLeaderboard.error = nil
    GKLeaderboard.storesSubmittedScores = true
    GKLeaderboard.currentOccurrence = 0
    GKLeaderboard.occurrenceStart = nil
    GKLeaderboard.occurrenceSubmissions = 0
    GKLeaderboard.holdWeeklyLookup = false
    GKLeaderboard.weeklyLookups = []
    GKLeaderboard.holdSubmission = false
    GKLeaderboard.holdRank = false
    GKLeaderboard.entries = [:]
    GKLeaderboard.probeIDs = nil
    GKLeaderboard.probeReleased = true
    GKLeaderboard.entryLoads = 0
    GKLeaderboard.readHandler = nil
    GKLeaderboard.loadedRanges = []
    GKAccessPoint.shared.openedBoard = nil
    GKAccessPoint.shared.openedScope = nil
    GKAccessPoint.shared.openedState = nil
    GKAchievement.reportedBanners = []
    GKAccessPoint.shared.isPresentingGameCenter = false
    GKAccessPoint.shared.submittedAtOpen = []
    return GameCenterService(isEnabled: true, availabilityContract:
      GameCenterAvailabilityContract(rawLeaderboardIDs: GameCenterLeaderboard.allCases.map(\.rawValue)),
      requestTimeout: timeout, retryDelay: delay)
  }
  @MainActor static func stop(_ service: GameCenterService) async {
    service.updateSceneActivity(false)
    GKAccessPoint.shared.isPresentingGameCenter = false
    await pause(0.15)
  }
  @MainActor static func main() async {
    do {
      let s = makeService(timeout: 0.07)
      GKLocalPlayer.local.isAuthenticated = false
      s.showLeaderboard(.flowBeginner)
      await pause(0.12)
      check(!s.isDashboardBusy && s.state == .signInRequired,
        "silent authentication timeout releases the app navigation")
      await stop(s)
    }
    do {
      let s = makeService(timeout: 0.5)
      GKLocalPlayer.local.isAuthenticated = false
      s.showLeaderboard(.flowBeginner)
      s.updateSceneActivity(false)
      GKLocalPlayer.local.authenticateHandler?(UIViewController(), nil)
      await pause()
      check(!s.isDashboardBusy, "authentication arriving in background releases navigation")
      check(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        .first?.windows.first?.rootViewController?.presentedViewController == nil,
        "background authentication callback never presents a modal")
      await stop(s)
    }
    // Real service mapping/submit for every board and every keyboard with an empty probe.
    for mode in SessionInputMode.allCases {
      let service = makeService()
      GKLeaderboard.probeIDs = []
      var runs = GameCenterRankedDeck.all.map { deck in
        GameRecord(deckId: deck.deckID, deckVersion: deck.version, inputMode: mode, score: 100)
      }
      runs.append(GameRecord(competition: .weeklyPiyoCup, inputMode: mode, score: 200))
      service.updateLocalState(records: runs, growth: .empty)
      service.prepare()
      await pause()
      check(service.availableLeaderboards.count == 16, "empty list retains 16 Live boards / \(mode)")
      for run in runs {
        check(service.isLeaderboardAvailable(for: run), "CTA / \(run.deckId) / \(run.competition != nil) / \(mode)")
        check(service.submissionState(for: run) == .submitted, "submitted / \(run.deckId) / \(run.competition != nil) / \(mode)")
      }
      check(GKLeaderboard.submitted.count == 16, "exactly 16 destinations / \(mode)")
      check(GKLeaderboard.occurrenceSubmissions == 1, "Cup binds to one weekly occurrence / \(mode)")
      check(GKLeaderboard.submitted.first { $0.id == GameCenterLeaderboard.flowBeginner.rawValue }?.score == 100, "cup never raises Flow score / \(mode)")
      await stop(service)
    }
    do {
      let s = makeService(); GKLeaderboard.probeReleased = false
      s.updateLocalState(records: [GameRecord(score: 500)], growth: .empty)
      s.prepare(); await pause()
      check(s.submissionStates[.flowBeginner] == .submitted, "missing released flag retains Live baseline")
      await stop(s)
    }
    do {
      let s = makeService(timeout: 0.5, delay: 1)
      GKLocalPlayer.local.isAuthenticated = false
      s.updateLocalState(records: [GameRecord(inputMode: .osIME, score: 9000)], growth: .empty)
      s.showLeaderboard(for: GameRecord(score: 9000))
      GKLocalPlayer.local.isAuthenticated = true
      GKLocalPlayer.local.authenticateHandler?(nil, nil)
      await pause(0.35)
      check(GKAccessPoint.shared.submittedAtOpen.first?.score == 9000, "first login submits retained best before dashboard")
      await stop(s)
    }
    do {
      let s = makeService(delay: 1)
      GKLeaderboard.error = NSError(domain: "TestOffline", code: 1)
      s.submitScore(for: GameRecord(inputMode: .osIME, score: 9000)); await pause()
      check(s.submissionFailures[.flowBeginner]?.score == 9000, "failure contains board/score/domain/code")
      GKLeaderboard.error = nil
      s.submitScore(for: GameRecord(inputMode: .builtInKorean10Key, score: 3000)); await pause()
      check(GKLeaderboard.submitted.map(\.score) == [9000, 9000], "lower next game resends failed highest across keyboards")
      await stop(s)
    }
    do {
      let s = makeService(delay: 1)
      GKLeaderboard.error = NSError(domain: "TestOffline", code: 1)
      s.submitScore(for: GameRecord(score: 6000)); await pause()
      s.updateSceneActivity(false); GKLeaderboard.error = nil
      s.updateSceneActivity(true); await pause()
      check(GKLeaderboard.submitted.map(\.score) == [6000, 6000], "foreground retries without another game")
      await stop(s)
    }
    do {
      let s = makeService(); GKLeaderboard.error = NSError(domain: "TestOffline", code: 1)
      s.submitScore(for: GameRecord(score: 1000)); await pause()
      GKLeaderboard.error = nil; await pause(0.08)
      check(s.submissionStates[.flowBeginner] == .submitted, "automatic retry recovers connectivity")
      await stop(s)
    }
    do {
      let s = makeService(delay: 0.5); GKLeaderboard.holdSubmission = true
      let run = GameRecord(score: 8000)
      s.submitScore(for: run); await pause(0.09)
      let expired = GKLeaderboard.callbacks[0]
      check(s.submissionStates[.flowBeginner] == .failed, "missing callback expires instead of blocking forever")
      s.retrySubmission(for: run)
      GKLeaderboard.storesSubmittedScores = false
      expired(nil); await pause(0.015)
      check(s.submissionStates[.flowBeginner] == .submitting, "late callback cannot overwrite newer request")
      GKLeaderboard.storesSubmittedScores = true
      GKLeaderboard.callbacks.last?(nil); await pause()
      check(s.submissionStates[.flowBeginner] == .submitted, "manual retry can finish newer request")
      await stop(s)
    }
    do {
      let s = makeService(); let run = GameRecord(score: 7000)
      GKLeaderboard.storesSubmittedScores = false
      s.submitScore(for: run); await pause()
      check(s.rank(for: run) == nil, "rank initially absent")
      check(s.submissionState(for: run) == .confirming, "submit success alone is not confirmed registration")
      GKLeaderboard.entries[GameCenterLeaderboard.flowBeginner.rawValue] = .init(rank: 7, score: 7000)
      await pause(0.08)
      check(s.rank(for: run) == 7, "delayed server rank refreshed automatically")
      check(s.submissionState(for: run) == .submitted, "matching server score confirms registration")
      await stop(s)
    }
    do {
      let s = makeService(delay: 1); let run = GameRecord(score: 6000)
      GKLeaderboard.holdRank = true
      s.submitScore(for: run); await pause(0.18)
      let old = GKLeaderboard.entryCallbacks.first { $0.id == GameCenterLeaderboard.flowBeginner.rawValue }!.callback
      GKLeaderboard.holdRank = false
      GKLeaderboard.entries[GameCenterLeaderboard.flowBeginner.rawValue] = .init(rank: 5, score: 6000)
      s.synchronize(); await pause()
      old(.init(rank: 99), [], 0, nil); await pause()
      check(s.rank(for: run) == 5, "rank timeout releases lock and ignores stale entry callback")
      await stop(s)
    }
    do {
      let s = makeService(delay: 0.02)
      GKLeaderboard.error = NSError(domain: "PermanentTestFailure", code: 2)
      s.submitScore(for: GameRecord(score: 200)); await pause(0.3)
      check(GKLeaderboard.submitted.count == 3, "automatic retry is bounded to two attempts")
      await stop(s)
    }
    do {
      let s = makeService(); GKLeaderboard.holdSubmission = true
      s.submitScore(for: GameRecord(score: 1000)); let old = GKLeaderboard.callbacks.last!
      GKLocalPlayer.local.gamePlayerID = UUID().uuidString
      s.prepare(); old(nil); await pause(0.02)
      check(s.submissionStates[.flowBeginner] == .submitting, "old player callback cannot complete new player's request")
      await stop(s)
    }
    do {
      let s = makeService(delay: 0.01)
      let run = GameRecord(score: 5000)
      GKLeaderboard.storesSubmittedScores = false
      GKLeaderboard.entries[GameCenterLeaderboard.flowBeginner.rawValue] = .init(rank: 4, score: 3000)
      s.submitScore(for: run); await pause(0.025)
      check(s.rank(for: run) == 4 && s.serverScores[.flowBeginner] == 3000,
        "server rank alone does not imply the new score is present")
      check(s.submissionState(for: run) == .confirming,
        "older lower server score does not confirm the retained best")
      await pause(0.7)
      check(s.submissionState(for: run) == .unconfirmed,
        "unconfirmed read-back becomes an explicit retryable state")
      check(GKLeaderboard.submitted.count == 3,
        "successful callbacks without remote score cannot restart retries forever")
      check(s.submissionFailures[.flowBeginner]?.score == 5000,
        "unconfirmed best score remains available for recovery")
      GKLeaderboard.storesSubmittedScores = true
      s.retrySubmission(for: run); await pause()
      check(s.submissionState(for: run) == .submitted && s.serverScores[.flowBeginner] == 5000,
        "manual recovery resends and verifies the retained best")
      await stop(s)
    }
    do {
      let s = makeService()
      GKLeaderboard.entries[GameCenterLeaderboard.flowBeginner.rawValue] = .init(rank: 2, score: 9000)
      GKLeaderboard.holdSubmission = true
      let run = GameRecord(score: 1000)
      s.submitScore(for: run); await pause()
      check(s.submissionState(for: run) == .submitted,
        "a higher existing server best confirms a lower game result")
      GKLeaderboard.callbacks.last?(NSError(domain: "LateFailure", code: 1)); await pause()
      check(s.submissionState(for: run) == .submitted,
        "late submit failure cannot override a confirmed server entry")
      await stop(s)
    }
    do {
      let s = makeService(delay: 1)
      GKLeaderboard.holdWeeklyLookup = true
      let cup = GameRecord(competition: .weeklyPiyoCup, score: 800)
      s.submitScore(for: cup); await pause(0.015)
      GKLeaderboard.currentOccurrence += 1
      GKLeaderboard.weeklyLookups.first?(); await pause()
      check(GKLeaderboard.occurrenceSubmissions == 1,
        "weekly submission uses the loaded occurrence instance")
      check(GKLeaderboard.submitted.isEmpty && s.submissionState(for: cup) == .failed,
        "expired weekly occurrence does not route a delayed score into the next cup")
      await stop(s)
    }
    do {
      let s = makeService(timeout: 0.5)
      s.refreshRankings([.flowBeginner]); await pause()
      check(s.summary(for: .flowBeginner).snapshot?.totalPlayerCount == 0,
        "successful empty board is distinct from failed or not fetched")
      let count = GKLeaderboard.entryLoads
      s.refreshRankings([.flowBeginner]); s.prepare(); await pause()
      check(GKLeaderboard.entryLoads == count, "fresh empty boards do not refetch on repeated appearances")
      GKLeaderboard.readHandler = { _, _, _, done in done(nil, nil, 0, NSError(domain: "Offline", code: 1)) }
      s.refreshRankings([.flowBeginner], force: true); await pause()
      check(s.summary(for: .flowBeginner).failed && s.summary(for: .flowBeginner).snapshot != nil,
        "failed summary keeps the last successful value and timestamp")
      GKLocalPlayer.local.isAuthenticated = false
      s.prepare()
      check(s.rankingSummaries.isEmpty && s.rankingDetails.isEmpty, "logout clears account-scoped cached reads")
      await stop(s)
    }
    do {
      let s = makeService(timeout: 0.5)
      let global = GameCenterRankingQuery(leaderboard: .flowBeginner, scope: .global)
      let friends = GameCenterRankingQuery(leaderboard: .flowBeginner, scope: .friends)
      let rival = GKPlayer(); rival.gamePlayerID = "rival"; rival.displayName = "Rival"
      GKLeaderboard.readHandler = { _, scope, range, done in
        let localRank = scope == .global ? 42 : 3
        let local = GKLeaderboard.Entry(rank: localRank, score: 100)
        let above = GKLeaderboard.Entry(rank: localRank - 1, score: 125, player: rival)
        done(local, range.location == max(1, localRank - 2) ? [above, local] : [], 100, nil)
      }
      s.refreshRankingDetails(global); await pause()
      check(s.rankingDetails[global]?.snapshot?.pointsToMatch == 25,
        "nearby target comes from an adjacent row returned by the server")
      check(GKLeaderboard.loadedRanges.contains { $0.2 == NSRange(location: 40, length: 5) },
        "far-away local rank fetches its own neighborhood")
      s.refreshRankingDetails(friends); await pause()
      check(s.rankingDetails[friends]?.snapshot?.localEntry?.rank == 3 && s.ranks[.flowBeginner] == 42,
        "friends rank never overwrites global result rank")
      let old = s.rankingDetails[friends]?.snapshot
      GKLeaderboard.readHandler = { _, _, _, done in done(nil, nil, 0, NSError(domain: "Offline", code: 1)) }
      s.refreshRankingDetails(friends, force: true); await pause()
      check(s.rankingDetails[friends]?.failed == true && s.rankingDetails[friends]?.snapshot == old,
        "failed neighborhood keeps the prior verified data")
      s.submitScore(for: GameRecord(score: 200)); await pause()
      check(s.rankingDetails[global] == nil && s.rankingDetails[friends] == nil,
        "new best invalidates both scope caches before submitting")
      await stop(s)
    }
    do {
      let s = makeService(timeout: 0.08)
      let query = GameCenterRankingQuery(leaderboard: .flowBeginner, scope: .global)
      var oldCallback: ((GKLeaderboard.Entry?, [GKLeaderboard.Entry]?, Int, Error?) -> Void)?
      GKLeaderboard.readHandler = { _, _, range, done in if range.length == 5 { oldCallback = done } else { done(nil, [], 0, nil) } }
      s.refreshRankingDetails(query); await pause(0.12)
      check(s.rankingDetails[query]?.failed == true && s.rankingDetails[query]?.isLoading == false,
        "neighborhood timeout releases loading state")
      oldCallback?(.init(rank: 2, score: 100), [], 2, nil); await pause()
      check(s.rankingDetails[query]?.snapshot == nil, "timed-out neighborhood callback cannot restore stale data")
      s.refreshRankingDetails(query, force: true); await pause(0.01)
      GKLocalPlayer.local.gamePlayerID = "new-account"
      s.prepare(); await pause()
      oldCallback?(.init(rank: 2, score: 100), [], 2, nil); await pause()
      check(s.rankingDetails[query] == nil, "late neighborhood callback cannot cross accounts")
      await stop(s)
    }
    do {
      let s = makeService(timeout: 0.5)
      GKLeaderboard.occurrenceStart = PiyoCupWeek.start(containing: Date()).addingTimeInterval(-604800)
      let query = GameCenterRankingQuery(leaderboard: .weeklyPiyoCup, scope: .global)
      s.refreshRankingDetails(query); await pause()
      check(s.rankingDetails[query]?.failed == true && s.rankingDetails[query]?.snapshot == nil,
        "a prior server Cup occurrence cannot become the current neighborhood")
      check(s.summary(for: .weeklyPiyoCup).failed && s.summary(for: .weeklyPiyoCup).snapshot == nil,
        "a prior server Cup occurrence cannot become the current summary")
      await stop(s)
    }
    for board in GameCenterLeaderboard.allCases {
      let s = makeService(timeout: 0.5)
      s.showLeaderboard(board, scope: .friends); await pause(0.35)
      check(GKAccessPoint.shared.openedBoard == board.rawValue && GKAccessPoint.shared.openedScope == .friendsOnly,
        "native board opens without a GameRecord / \(board.rawValue)")
      await stop(s)
    }
    do {
      let s = makeService(timeout: 0.5)
      s.showAchievements(); await pause(0.35)
      check(GKAccessPoint.shared.openedState == .achievements, "growth opens native achievements")
      check(!GKAchievement.reportedBanners.contains(true), "achievement synchronization never schedules a session-interrupting banner")
      await stop(s)
    }
    print("All controlled Game Center delivery scenarios passed; real-device/server verification remains separate.")
  }
}
