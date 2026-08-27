import XCTest
@testable import Hanco

final class GameCenterServiceTests: XCTestCase {
  func testFifteenDifficultyPresetsHaveVersionedLeaderboards() {
    XCTAssertEqual(GameCenterRankedDeck.all.count, 15)
    XCTAssertEqual(Set(GameCenterRankedDeck.all.map(\.deckID)).count, 15)
    XCTAssertEqual(Set(GameCenterRankedDeck.all.map(\.leaderboard)).count, 15)
    XCTAssertEqual(GameCenterLeaderboard.flowBeginner.rawValue, "piyokey.v4.flow.beginner")
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

  func testWeeklyPiyoCupSubmitsToCupAndFixedDeckLeaderboards() {
    let cup = record(
      deckID: GameCenterRankedDeck.piyoCupDeckID,
      deckVersion: GameCenterRankedDeck.piyoCupDeck.version,
      competition: .weeklyPiyoCup
    )
    XCTAssertEqual(
      GameCenterLeaderboard.leaderboards(for: cup),
      [.weeklyPiyoCup, .flowBeginner]
    )
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
        for: record(competition: .officialDeck, inputMode: .osIME)
      ).isEmpty
    )
    XCTAssertTrue(
      GameCenterLeaderboard.leaderboards(
        for: record(competition: .officialDeck, inputMode: .builtInKorean10Key)
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

  func testRuntimeAvailabilityDeduplicatesAndUsesAuthoritativeProbeResult() throws {
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
    XCTAssertEqual(runtime.available, [.flowBeginner])
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

  func testAvailabilityRankLookupIsPureAndFallsBackToAvailableClassicBoard() {
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

    XCTAssertEqual(contract.rank(for: cup, ranks: ranks), 19)
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

    XCTAssertEqual(scores[.flowBeginner], 900)
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
        record(score: 1_200, competition: .weeklyPiyoCup, playedAt: previousSunday),
        record(score: 900, competition: .weeklyPiyoCup, playedAt: currentMonday),
      ],
      asOf: asOf
    )

    XCTAssertEqual(PiyoCupWeek.start(containing: currentMonday), currentMonday)
    XCTAssertEqual(scores[.weeklyPiyoCup], 900)
    XCTAssertEqual(scores[.flowBeginner], 1_200)
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
