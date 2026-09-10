import XCTest
@testable import Hanco

final class GameCenterServiceTests: XCTestCase {
  func testFifteenDifficultyPresetsHaveVersionedLeaderboards() {
    XCTAssertEqual(GameCenterRankedDeck.all.count, 15)
    XCTAssertEqual(Set(GameCenterRankedDeck.all.map(\.deckID)).count, 15)
    XCTAssertEqual(Set(GameCenterRankedDeck.all.map(\.leaderboard)).count, 15)
    XCTAssertEqual(GameCenterLeaderboard.flowBeginner.rawValue, "piyokey.v5.flow.beginner")
    XCTAssertEqual(
      GameCenterLeaderboard.flowIntermediate.rawValue,
      "piyokey.v4.flow.intermediate"
    )
    XCTAssertEqual(GameCenterLeaderboard.flowAdvanced.rawValue, "piyokey.v4.flow.advanced")
    XCTAssertEqual(
      GameCenterLeaderboard.weeklyPiyoCup.rawValue,
      "piyokey.v4.cup.weekly.flow"
    )
    XCTAssertEqual(GameCenterLeaderboard.wordMatchBeginner.rawValue, "piyokey.v4.word_match.beginner")
    XCTAssertEqual(
      GameCenterLeaderboard.wordMatchIntermediate.rawValue,
      "piyokey.v4.word_match.intermediate"
    )
    XCTAssertEqual(GameCenterLeaderboard.wordMatchAdvanced.rawValue, "piyokey.v4.word_match.advanced")
    XCTAssertTrue(
      GameCenterLeaderboard.allCases
        .filter {
          ![
            .flowBeginner, .flowIntermediate, .flowAdvanced, .weeklyPiyoCup,
            .wordMatchBeginner, .wordMatchIntermediate, .wordMatchAdvanced,
          ].contains($0)
        }
        .allSatisfy { $0.rawValue.hasPrefix("piyokey.v3.") }
    )
    XCTAssertTrue(
      GameCenterRankedDeck.all.allSatisfy {
        GameCenterRankedDeck.isEligible(deckID: $0.deckID, version: $0.version)
      }
    )
  }

  func testBundledAvailabilityIncludesAllCurrentlyLiveLeaderboards() {
    let expectedLive: Set<GameCenterLeaderboard> = [
      .flowBeginner, .flowIntermediate, .flowAdvanced,
      .acidRainBeginner, .acidRainIntermediate, .acidRainAdvanced,
      .choseongBeginner, .choseongIntermediate, .choseongAdvanced,
      .wordMatchBeginner, .wordMatchIntermediate, .wordMatchAdvanced,
      .dictationBeginner, .dictationIntermediate, .dictationAdvanced,
      .weeklyPiyoCup,
    ]
    let contract = GameCenterAvailabilityContract.bundled()

    XCTAssertEqual(contract.availableLeaderboards, expectedLive)
    XCTAssertEqual(contract.intendedLeaderboards, expectedLive)
  }

  func testFlowBeginnerCutoverRejectsLegacyServerID() {
    XCTAssertNil(GameCenterLeaderboard(rawValue: "piyokey.v4.flow.beginner"))
    let contract = GameCenterAvailabilityContract.bundled()
    XCTAssertTrue(contract.contains(.flowBeginner))
    for input in [SessionInputMode.builtIn, .builtInKorean10Key, .osIME] {
      let flow = record(inputMode: input)
      XCTAssertEqual(GameCenterLeaderboard.leaderboards(for: flow).map(\.rawValue),
        ["piyokey.v5.flow.beginner"])
      let cup = record(competition: .weeklyPiyoCup, inputMode: input)
      XCTAssertEqual(GameCenterLeaderboard.leaderboards(for: cup).map(\.rawValue),
        ["piyokey.v4.cup.weekly.flow"])
    }
  }

  func testBundledPiyoCupDeckMatchesFixedCompetitionContract() {
    let deck = PiyoCupDeckLoader.load()

    XCTAssertEqual(deck?.deckId, GameCenterRankedDeck.piyoCupDeck.deckID)
    XCTAssertEqual(deck?.version, GameCenterRankedDeck.piyoCupDeck.version)
  }

  func testBundledPresetUsesItsOwnLeaderboard() {
    let ranked = record(
      deckID: "flow_topik_intermediate",
      deckVersion: 3
    )
    XCTAssertEqual(GameCenterLeaderboard.leaderboards(for: ranked), [.flowIntermediate])
    XCTAssertEqual(GameCenterLeaderboard.leaderboard(for: ranked), .flowIntermediate)
  }

  func testEachGameModeUsesItsOwnDifficultyLeaderboard() {
    let acidRain = record(
      deckID: "acid_rain_topik_advanced",
      deckVersion: 3,
      course: "acid_rain"
    )
    XCTAssertEqual(GameCenterLeaderboard.leaderboards(for: acidRain), [.acidRainAdvanced])
  }

  func testWeeklyPiyoCupSubmitsOnlyToWeeklyLeaderboard() {
    let cup = record(
      deckID: GameCenterRankedDeck.piyoCupDeckID,
      deckVersion: GameCenterRankedDeck.piyoCupDeck.version,
      competition: .weeklyPiyoCup
    )
    XCTAssertEqual(
      GameCenterLeaderboard.leaderboards(for: cup),
      [.weeklyPiyoCup]
    )
    XCTAssertEqual(GameCenterLeaderboard.leaderboard(for: cup), .weeklyPiyoCup)
  }

  func testWeeklyPiyoCupOSIMESubmitsOnlyToWeeklyLeaderboard() {
    let cup = record(
      deckID: GameCenterRankedDeck.piyoCupDeckID,
      deckVersion: GameCenterRankedDeck.piyoCupDeck.version,
      competition: .weeklyPiyoCup,
      inputMode: .osIME
    )

    XCTAssertEqual(GameCenterLeaderboard.leaderboards(for: cup), [.weeklyPiyoCup])
    XCTAssertEqual(GameCenterLeaderboard.leaderboard(for: cup), .weeklyPiyoCup)
  }

  func testUnrankedConditionsNeverEnterGameCenterLeaderboards() {
    XCTAssertTrue(
      GameCenterLeaderboard.leaderboards(
        for: record(mode: .lesson, competition: .officialDeck)
      ).isEmpty
    )
    XCTAssertTrue(
      GameCenterLeaderboard.leaderboards(
        for: record(deckVersion: 999, competition: .officialDeck)
      ).isEmpty
    )
    XCTAssertTrue(
      GameCenterLeaderboard.leaderboards(
        for: record(deckID: "remote_dynamic_deck", competition: .officialDeck)
      ).isEmpty
    )
  }

  func testAvailabilityContractFailsClosedForMissingAndUnknownIDs() {
    XCTAssertTrue(
      GameCenterAvailabilityContract(rawLeaderboardIDs: []).availableLeaderboards.isEmpty
    )
    XCTAssertTrue(
      GameCenterAvailabilityContract(rawLeaderboardIDs: ["piyokey.future.unknown"])
        .availableLeaderboards.isEmpty
    )
  }

  func testAvailabilityContractGatesPendingAndUnrankedRecords() {
    let contract = GameCenterAvailabilityContract(rawLeaderboardIDs: [
      GameCenterLeaderboard.acidRainAdvanced.rawValue,
    ])
    let acidRain = record(
      deckID: "acid_rain_topik_advanced",
      deckVersion: 3,
      course: "acid_rain"
    )

    XCTAssertEqual(contract.leaderboards(for: acidRain), [.acidRainAdvanced])
    XCTAssertTrue(contract.leaderboards(for: record()).isEmpty)
    XCTAssertTrue(
      contract.leaderboards(for: record(inputMode: .osIME)).isEmpty
    )
    XCTAssertTrue(
      contract.leaderboards(for: record(deckID: "remote_dynamic_deck")).isEmpty
    )
  }

  func testRuntimeAvailabilityKeepsLiveBaselineAcrossPartialResults() throws {
    let contract = GameCenterAvailabilityContract(
      rawLeaderboardIDs: [GameCenterLeaderboard.acidRainBeginner.rawValue],
      intendedLeaderboardIDs: [
        GameCenterLeaderboard.acidRainBeginner.rawValue,
        GameCenterLeaderboard.flowBeginner.rawValue,
        GameCenterLeaderboard.weeklyPiyoCup.rawValue,
      ]
    )
    var runtime = GameCenterRuntimeAvailability(contract: contract)
    let probeToken = try XCTUnwrap(runtime.beginProbe())
    XCTAssertNil(runtime.beginProbe())
    XCTAssertTrue(
      runtime.completeProbe(
        token: probeToken,
        returned: [.flowBeginner],
        succeeded: true
      )
    )
    XCTAssertEqual(runtime.available, [.acidRainBeginner, .flowBeginner])
    XCTAssertTrue(runtime.probeIsComplete)
    XCTAssertNil(runtime.beginProbe())

    var failedRuntime = GameCenterRuntimeAvailability(contract: contract)
    let failedProbeToken = try XCTUnwrap(failedRuntime.beginProbe())
    XCTAssertTrue(
      failedRuntime.completeProbe(
        token: failedProbeToken,
        returned: [.flowBeginner],
        succeeded: false
      )
    )
    XCTAssertEqual(failedRuntime.available, [.acidRainBeginner])
    XCTAssertTrue(failedRuntime.probeIsComplete)
    XCTAssertNil(failedRuntime.beginProbe())
  }

  func testRuntimeAvailabilityTimeoutFallsBackAndIgnoresLateCallback() throws {
    let contract = GameCenterAvailabilityContract(
      rawLeaderboardIDs: [GameCenterLeaderboard.acidRainBeginner.rawValue],
      intendedLeaderboardIDs: [
        GameCenterLeaderboard.acidRainBeginner.rawValue,
        GameCenterLeaderboard.flowBeginner.rawValue,
      ]
    )
    var runtime = GameCenterRuntimeAvailability(contract: contract)
    let timedOutToken = try XCTUnwrap(runtime.beginProbe())

    XCTAssertTrue(runtime.timeoutProbe(token: timedOutToken))
    XCTAssertEqual(runtime.available, [.acidRainBeginner])
    XCTAssertFalse(runtime.probeIsInFlight)
    XCTAssertTrue(runtime.probeIsComplete)
    XCTAssertFalse(
      runtime.completeProbe(
        token: timedOutToken,
        returned: [.flowBeginner],
        succeeded: true
      )
    )
    XCTAssertEqual(runtime.available, [.acidRainBeginner])

    runtime.resetForPlayerChange()
    let nextPlayerToken = try XCTUnwrap(runtime.beginProbe())
    XCTAssertNotEqual(nextPlayerToken, timedOutToken)
    XCTAssertFalse(
      runtime.completeProbe(
        token: timedOutToken,
        returned: [.flowBeginner],
        succeeded: true
      )
    )
    XCTAssertTrue(runtime.probeIsInFlight)
  }

  func testRuntimeAvailabilityResetsDynamicIDsWhenPlayerChanges() throws {
    let contract = GameCenterAvailabilityContract(
      rawLeaderboardIDs: [GameCenterLeaderboard.acidRainBeginner.rawValue],
      intendedLeaderboardIDs: [
        GameCenterLeaderboard.acidRainBeginner.rawValue,
        GameCenterLeaderboard.flowBeginner.rawValue,
      ]
    )
    var runtime = GameCenterRuntimeAvailability(contract: contract)
    let probeToken = try XCTUnwrap(runtime.beginProbe())
    XCTAssertTrue(
      runtime.completeProbe(
        token: probeToken,
        returned: [.flowBeginner],
        succeeded: true
      )
    )
    XCTAssertTrue(runtime.available.contains(.flowBeginner))

    runtime.resetForPlayerChange()

    XCTAssertEqual(runtime.available, [.acidRainBeginner])
    XCTAssertFalse(runtime.probeIsInFlight)
    XCTAssertFalse(runtime.probeIsComplete)
  }

  func testCupRankNeverFallsBackToOrdinaryFlowRank() {
    let contract = GameCenterAvailabilityContract(rawLeaderboardIDs: [
      GameCenterLeaderboard.flowBeginner.rawValue,
    ])
    let cup = record(
      deckID: GameCenterRankedDeck.piyoCupDeckID,
      deckVersion: GameCenterRankedDeck.piyoCupDeck.version,
      competition: .weeklyPiyoCup
    )
    let ranks: [GameCenterLeaderboard: Int] = [
      .weeklyPiyoCup: 3,
      .flowBeginner: 19,
    ]

    XCTAssertTrue(contract.leaderboards(for: cup).isEmpty)
    XCTAssertNil(contract.rank(for: cup, ranks: ranks))
    XCTAssertEqual(contract.rank(for: record(), ranks: ranks), 19)
    let bothAvailable = GameCenterAvailabilityContract(rawLeaderboardIDs: [
      GameCenterLeaderboard.flowBeginner.rawValue,
      GameCenterLeaderboard.weeklyPiyoCup.rawValue,
    ])
    XCTAssertEqual(bothAvailable.rank(for: cup, ranks: ranks), 3)
    XCTAssertNil(bothAvailable.rank(for: cup, ranks: [.flowBeginner: 1]))
    XCTAssertEqual(ranks[.weeklyPiyoCup], 3)
    XCTAssertEqual(ranks[.flowBeginner], 19)
  }

  func testAuthenticationPresentationPolicySerializesRepeatedControllerCallbacks() {
    XCTAssertTrue(
      GameCenterPresentationPolicy.canPresentAuthentication(
        sceneIsActive: true,
        dashboardIsBusy: true,
        authenticationControllerIsPresented: false,
        presenterHasPresentedController: false
      )
    )
    XCTAssertFalse(
      GameCenterPresentationPolicy.canPresentAuthentication(
        sceneIsActive: true,
        dashboardIsBusy: true,
        authenticationControllerIsPresented: true,
        presenterHasPresentedController: false
      )
    )
    XCTAssertFalse(
      GameCenterPresentationPolicy.canPresentAuthentication(
        sceneIsActive: false,
        dashboardIsBusy: true,
        authenticationControllerIsPresented: false,
        presenterHasPresentedController: false
      )
    )
  }

  func testDashboardPolicyWaitsForAuthenticationDismissalAndAccessPoint() {
    XCTAssertFalse(
      GameCenterPresentationPolicy.canPresentDashboard(
        sceneIsActive: true,
        dashboardIsBusy: true,
        playerIsAuthenticated: true,
        authenticationIsDismissed: false,
        accessPointIsPresenting: false
      )
    )
    XCTAssertFalse(
      GameCenterPresentationPolicy.canPresentDashboard(
        sceneIsActive: true,
        dashboardIsBusy: true,
        playerIsAuthenticated: true,
        authenticationIsDismissed: true,
        accessPointIsPresenting: true
      )
    )
    XCTAssertTrue(
      GameCenterPresentationPolicy.canPresentDashboard(
        sceneIsActive: true,
        dashboardIsBusy: true,
        playerIsAuthenticated: true,
        authenticationIsDismissed: true,
        accessPointIsPresenting: false
      )
    )
  }

  func testBestScoreUsesHighestRecordPerLeaderboard() {
    let now = Date(timeIntervalSince1970: 1)
    let scores = GameCenterLeaderboard.bestScores(from: [
      record(score: 400, competition: .officialDeck),
      record(score: 900, competition: .weeklyPiyoCup),
      record(
        score: 700,
        deckID: "flow_topik_advanced",
        deckVersion: 3
      ),
      record(mode: .lesson, score: 9_999, competition: .officialDeck),
    ], asOf: now)

    XCTAssertEqual(scores[.flowBeginner], 400)
    XCTAssertEqual(scores[.weeklyPiyoCup], 900)
    XCTAssertEqual(scores[.flowAdvanced], 700)
    XCTAssertEqual(scores.count, 3)
  }

  func testWeeklyScoreUsesOnlyCurrentJSTWeekWhileClassicKeepsHistory() {
    let previousSunday = date("2026-07-19T14:59:59Z")
    let currentMonday = date("2026-07-19T15:00:00Z")
    let asOf = date("2026-07-22T03:00:00Z")
    let scores = GameCenterLeaderboard.bestScores(
      from: [
        record(score: 700, competition: .officialDeck, playedAt: previousSunday),
        record(score: 1_200, competition: .weeklyPiyoCup, playedAt: previousSunday),
        record(score: 900, competition: .weeklyPiyoCup, playedAt: currentMonday),
      ],
      asOf: asOf
    )

    XCTAssertEqual(PiyoCupWeek.start(containing: currentMonday), currentMonday)
    XCTAssertEqual(scores[.weeklyPiyoCup], 900)
    XCTAssertEqual(scores[.flowBeginner], 700)
  }

  func testCupOnlyHistoryNeverCreatesAClassicScore() {
    let now = date("2026-09-09T03:00:00Z")
    let scores = GameCenterLeaderboard.bestScores(from: [
      record(score: 900, competition: .weeklyPiyoCup, playedAt: now),
      record(score: 1_100, competition: .weeklyPiyoCup, inputMode: .osIME, playedAt: now),
    ], asOf: now)

    XCTAssertEqual(scores, [.weeklyPiyoCup: 1_100])
    XCTAssertEqual(GameCenterLeaderboard.leaderboards(for: record()), [.flowBeginner])
    XCTAssertEqual(GameCenterLeaderboard.leaderboards(for: record(
      competition: .weeklyPiyoCup, inputMode: .builtInKorean10Key
    )), [.weeklyPiyoCup])
  }

  @MainActor
  func testEveryKeyboardCanEnterAllSixteenLeaderboardsWithoutBeatingPersonalBest() {
    let modes: [SessionInputMode] = [.builtIn, .builtInKorean10Key, .osIME]
    let service = GameCenterService(isEnabled: false, availabilityContract:
      GameCenterAvailabilityContract(rawLeaderboardIDs: GameCenterLeaderboard.allCases.map(\.rawValue)))
    for mode in modes {
      for deck in GameCenterRankedDeck.all {
        let run = record(score: 1, deckID: deck.deckID, deckVersion: deck.version, inputMode: mode)
        XCTAssertEqual(GameCenterLeaderboard.leaderboards(for: run), [deck.leaderboard])
        XCTAssertTrue(service.isLeaderboardAvailable(for: run))
      }
      let cup = record(score: 1, competition: .weeklyPiyoCup, inputMode: mode)
      XCTAssertEqual(GameCenterLeaderboard.leaderboards(for: cup), [.weeklyPiyoCup])
      XCTAssertTrue(service.isLeaderboardAvailable(for: cup))
    }
  }

  func testBestScoreCombinesKeyboardMethodsButNeverCombinesCupAndFlow() {
    let now = date("2026-09-09T03:00:00Z")
    let scores = GameCenterLeaderboard.bestScores(from: [
      record(score: 100, inputMode: .builtIn, playedAt: now),
      record(score: 900, inputMode: .builtInKorean10Key, playedAt: now),
      record(score: 700, inputMode: .osIME, playedAt: now),
      record(score: 1200, competition: .weeklyPiyoCup, inputMode: .builtInKorean10Key, playedAt: now),
    ], asOf: now)
    XCTAssertEqual(scores, [.flowBeginner: 900, .weeklyPiyoCup: 1200])
  }

  func testEmptyProbeCannotHideLiveBoardsAndCanBeRetriedAfterForeground() throws {
    let live = Set(GameCenterLeaderboard.allCases)
    var runtime = GameCenterRuntimeAvailability(contract:
      GameCenterAvailabilityContract(rawLeaderboardIDs: live.map(\.rawValue)))
    let first = try XCTUnwrap(runtime.beginProbe())
    XCTAssertTrue(runtime.completeProbe(token: first, returned: [], succeeded: true))
    XCTAssertEqual(runtime.available, live)
    runtime.allowRetry()
    let retry = try XCTUnwrap(runtime.beginProbe())
    XCTAssertNotEqual(first, retry)
    XCTAssertFalse(runtime.completeProbe(token: first, returned: [], succeeded: true))
    XCTAssertTrue(runtime.completeProbe(token: retry, returned: live, succeeded: true))
  }

  func testGrowthAchievementsMirrorExistingGrowthThresholds() {
    let snapshot = GameCenterGrowthSnapshot(
      completedChapterCount: 3,
      typedJamoCount: 6_000,
      longestStreak: 15
    )
    let progress = GameCenterGrowthAchievement.progress(for: snapshot)

    XCTAssertEqual(progress[.hatching], 100)
    XCTAssertEqual(progress[.chick], 100)
    XCTAssertEqual(progress[.rooster], 50)
    XCTAssertEqual(progress[.typedJamo], 50)
    XCTAssertEqual(progress[.streak], 50)
  }

  func testGrowthAchievementProgressClampsToValidGameCenterRange() {
    let over = GameCenterGrowthSnapshot(
      completedChapterCount: 99,
      typedJamoCount: 99_999,
      longestStreak: 99
    )
    XCTAssertTrue(
      GameCenterGrowthAchievement.progress(for: over).values.allSatisfy { $0 == 100 }
    )

    let under = GameCenterGrowthSnapshot(
      completedChapterCount: -1,
      typedJamoCount: -1,
      longestStreak: -1
    )
    XCTAssertTrue(
      GameCenterGrowthAchievement.progress(for: under).values.allSatisfy { $0 == 0 }
    )
  }

  func testRankingTitlesIncludeLocalizedGameAndDifficultyForEveryBoard() {
    for game in GameKind.allCases {
      for level in GamePresetLevel.allCases {
        let board = GameCenterLeaderboard.board(game: game, level: level)
        XCTAssertNotNil(board)
        XCTAssertTrue(board?.displayTitle.contains(AppLocalization.string("game.mode.\(game.rawValue)")) == true)
        XCTAssertFalse(board?.displayTitle.contains("game_center.") == true)
      }
    }
    XCTAssertFalse(GameCenterLeaderboard.weeklyPiyoCup.displayTitle.contains("piyo_cup."))
  }

  func testRankingSelectionDefaultsToBeginnerAndKeepsCupSeparate() {
    let boards: [GameCenterLeaderboard] = [.flowBeginner, .flowIntermediate, .flowAdvanced]
    XCTAssertEqual(GameCenterRankingSelection.preferredLeaderboard(candidates: boards, records: []), .flowBeginner)
    let records = [
      record(deckID: "flow_topik_advanced", playedAt: date("2026-09-10T00:00:00Z")),
      record(competition: .weeklyPiyoCup, playedAt: date("2026-09-11T00:00:00Z")),
      record(deckID: "user_custom", playedAt: date("2026-09-12T00:00:00Z")),
    ]
    XCTAssertEqual(GameCenterRankingSelection.preferredLeaderboard(candidates: boards, records: records), .flowAdvanced)
  }

  func testRankingTargetsRequireAnAdjacentVerifiedEntryAndHandleTies() {
    func snapshot(rank: Int, score: Int, entries: [GameCenterRankingEntry]) -> GameCenterRankingSnapshot {
      .init(localEntry: .init(id: "me", displayName: "Me", rank: rank, score: score),
        entries: entries, totalPlayerCount: 100, fetchedAt: Date(), periodStart: nil)
    }
    let above = GameCenterRankingEntry(id: "above", displayName: "Other", rank: 41, score: 900)
    let below = GameCenterRankingEntry(id: "below", displayName: "Other", rank: 43, score: 800)
    let value = snapshot(rank: 42, score: 850, entries: [below, above, above])
    XCTAssertEqual(value.nearbyEntries.map(\.id), ["above", "me", "below"])
    XCTAssertEqual(value.pointsToMatch, 50)
    XCTAssertEqual(snapshot(rank: 42, score: 900, entries: [above]).pointsToMatch, 0)
    XCTAssertNil(snapshot(rank: 1, score: 900, entries: [above]).nextTarget)
    XCTAssertNil(snapshot(rank: 45, score: 800, entries: [above]).nextTarget)
    XCTAssertNil(snapshot(rank: 42, score: 950, entries: [above]).nextTarget)
    XCTAssertEqual(GameCenterRankingSnapshot.nearbyRange(localRank: 42), NSRange(location: 40, length: 5))
    XCTAssertEqual(GameCenterRankingSnapshot.nearbyRange(localRank: nil), NSRange(location: 1, length: 5))
  }

  func testRankingFreshnessAndWeeklyExpiryAreDifferentFromFailures() {
    let sunday = date("2026-09-13T14:59:30Z")
    let monday = date("2026-09-13T15:00:00Z")
    let snapshot = GameCenterRankingSnapshot(localEntry: nil, entries: [], totalPlayerCount: 0,
      fetchedAt: sunday, periodStart: PiyoCupWeek.start(containing: sunday))
    var read = GameCenterRankingReadState(snapshot: snapshot)
    XCTAssertFalse(read.needsRefresh(asOf: sunday.addingTimeInterval(10)))
    XCTAssertTrue(read.needsRefresh(asOf: monday))
    XCTAssertEqual(PiyoCupWeek.end(containing: sunday), monday)
    read.failed = true
    XCTAssertTrue(read.needsRefresh(asOf: sunday))
    read.isLoading = true
    XCTAssertFalse(read.needsRefresh(asOf: monday))
  }

  private func record(
    mode: SessionMode = .game,
    score: Int = 500,
    deckID: String = "flow_topik_beginner",
    deckVersion: Int = 3,
    competition: GameCompetition? = nil,
    course: String = "word",
    inputMode: SessionInputMode = .builtIn,
    playedAt: Date = Date(timeIntervalSince1970: 1)
  ) -> GameRecord {
    GameRecord(
      id: UUID(),
      mode: mode,
      deckId: deckID,
      deckVersion: deckVersion,
      competition: competition,
      course: course,
      score: score,
      maxCombo: 4,
      accuracy: 95,
      charactersPerMinute: 80,
      activeDuration: 60,
      completedItemCount: 8,
      missedItemCount: 1,
      inputMode: inputMode,
      playedAt: playedAt
    )
  }

  private func date(_ value: String) -> Date {
    ISO8601DateFormatter().date(from: value)!
  }
}
