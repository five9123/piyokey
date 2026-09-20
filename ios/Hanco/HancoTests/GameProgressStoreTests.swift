import DeckKit
import XCTest
@testable import Hanco

@MainActor
final class GameProgressStoreTests: XCTestCase {
  private var temporaryRoot: URL!
  private var store: GameProgressStore!

  override func setUp() {
    super.setUp()
    temporaryRoot = FileManager.default.temporaryDirectory
      .appendingPathComponent("hanco-game-progress-\(UUID().uuidString)", isDirectory: true)
    store = GameProgressStore(rootURL: temporaryRoot)
  }

  override func tearDown() {
    try? FileManager.default.removeItem(at: temporaryRoot)
    store = nil
    temporaryRoot = nil
    super.tearDown()
  }

  func testFirstRecordCreatesNewBestAndRoundTripsEveryMetric() throws {
    let record = makeRecord(
      score: 700,
      accuracy: 92.5,
      playedAt: date(1),
      deckVersion: 2,
      competition: .officialDeck
    )
    let outcome = try store.append(record)
    let snapshot = try store.loadSnapshot()
    let data = try Data(
      contentsOf: temporaryRoot.appendingPathComponent("game-progress.json")
    )
    let object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])

    XCTAssertTrue(outcome.isNewBest)
    XCTAssertNil(outcome.previousBestScore)
    XCTAssertEqual(outcome.deckProgress.plays, 1)
    XCTAssertEqual(outcome.deckProgress.bestScore, 700)
    XCTAssertEqual(outcome.deckProgress.bestAccuracy, 92.5, accuracy: 0.001)
    XCTAssertEqual(snapshot.records, [record])
    XCTAssertEqual(object["schema_version"] as? Int, 3)
    XCTAssertEqual(
      snapshot.deckProgress[
        GameProgressSnapshot.progressKey(deckId: record.deckId, inputMode: .builtIn)
      ],
      outcome.deckProgress
    )
  }

  func testLowerScoreIncrementsPlaysWithoutReplacingBestScore() throws {
    _ = try store.append(makeRecord(score: 700, accuracy: 80, playedAt: date(1)))
    let outcome = try store.append(makeRecord(score: 500, accuracy: 95, playedAt: date(2)))

    XCTAssertFalse(outcome.isNewBest)
    XCTAssertEqual(outcome.previousBestScore, 700)
    XCTAssertEqual(outcome.deckProgress.plays, 2)
    XCTAssertEqual(outcome.deckProgress.bestScore, 700)
    XCTAssertEqual(outcome.deckProgress.bestAccuracy, 95, accuracy: 0.001)
    XCTAssertEqual(outcome.deckProgress.lastPlayedAt, date(2))
  }

  func testHigherScoreReplacesBestAndSurvivesLibraryReload() throws {
    _ = try store.append(makeRecord(score: 700, accuracy: 90, playedAt: date(1)))
    let outcome = try store.append(makeRecord(score: 900, accuracy: 88, playedAt: date(2)))
    let library = GameProgressLibrary(store: store)

    XCTAssertTrue(outcome.isNewBest)
    XCTAssertEqual(outcome.previousBestScore, 700)
    XCTAssertEqual(library.records.count, 2)
    XCTAssertEqual(library.progress(for: "official_daily_words")?.bestScore, 900)
  }

  func testEqualScoreIsNotNewBest() throws {
    _ = try store.append(makeRecord(score: 700, accuracy: 90, playedAt: date(1)))
    let outcome = try store.append(makeRecord(score: 700, accuracy: 90, playedAt: date(2)))

    XCTAssertFalse(outcome.isNewBest)
    XCTAssertEqual(outcome.previousBestScore, 700)
  }

  func testUnsupportedSchemaIsRejected() throws {
    try FileManager.default.createDirectory(at: temporaryRoot, withIntermediateDirectories: true)
    let data = Data("{\"schema_version\":999,\"records\":[],\"deck_progress\":[]}".utf8)
    try data.write(to: temporaryRoot.appendingPathComponent("game-progress.json"))

    XCTAssertThrowsError(try store.loadSnapshot()) { error in
      XCTAssertEqual(error as? GameProgressStoreError, .unsupportedSchema(999))
    }
  }

  func testCorruptPrimaryRestoresLatestValidatedBackupAndKeepsEvidence() throws {
    let record = makeRecord(score: 700, accuracy: 92.5, playedAt: date(1))
    _ = try store.append(record)
    let fileURL = temporaryRoot.appendingPathComponent("game-progress.json")
    let backupURL = RecoverableJSONFile.backupURL(for: fileURL)
    XCTAssertTrue(FileManager.default.fileExists(atPath: backupURL.path))

    var object = try XCTUnwrap(
      JSONSerialization.jsonObject(with: Data(contentsOf: fileURL)) as? [String: Any]
    )
    var records = try XCTUnwrap(object["records"] as? [[String: Any]])
    records[0]["accuracy"] = 200
    object["records"] = records
    try JSONSerialization.data(withJSONObject: object).write(to: fileURL)
    let recovered = try store.loadSnapshot()

    XCTAssertEqual(recovered.records, [record])
    XCTAssertTrue(FileManager.default.fileExists(atPath: fileURL.path))
    XCTAssertTrue(
      FileManager.default.fileExists(
        atPath: RecoverableJSONFile.corruptURL(for: fileURL).path
      )
    )
  }

  func testFutureSchemaBackupIsPreservedWhenPrimaryIsMissing() throws {
    try FileManager.default.createDirectory(at: temporaryRoot, withIntermediateDirectories: true)
    let fileURL = temporaryRoot.appendingPathComponent("game-progress.json")
    let backupURL = RecoverableJSONFile.backupURL(for: fileURL)
    try Data("{\"schema_version\":999,\"records\":[],\"deck_progress\":[]}".utf8)
      .write(to: backupURL)

    XCTAssertThrowsError(try store.loadSnapshot()) { error in
      XCTAssertEqual(error as? GameProgressStoreError, .unsupportedSchema(999))
    }
    XCTAssertTrue(FileManager.default.fileExists(atPath: backupURL.path))
    XCTAssertFalse(
      FileManager.default.fileExists(
        atPath: RecoverableJSONFile.corruptURL(for: backupURL).path
      )
    )
  }

  func testBestScoresAreSeparatedByInputMode() throws {
    _ = try store.append(makeRecord(score: 700, accuracy: 90, playedAt: date(1)))
    _ = try store.append(
      makeRecord(score: 900, accuracy: 95, playedAt: date(2), inputMode: .osIME)
    )
    _ = try store.append(
      makeRecord(
        score: 800,
        accuracy: 92,
        playedAt: date(3),
        inputMode: .builtInKorean10Key
      )
    )
    let library = GameProgressLibrary(store: store)

    XCTAssertEqual(
      library.progress(for: "official_daily_words", inputMode: .builtIn)?.bestScore,
      700
    )
    XCTAssertEqual(
      library.progress(for: "official_daily_words", inputMode: .osIME)?.bestScore,
      900
    )
    XCTAssertEqual(
      library.progress(
        for: "official_daily_words",
        inputMode: .builtInKorean10Key
      )?.bestScore,
      800
    )
  }

  func testBestScoresAreSeparatedByGameKind() throws {
    _ = try store.append(makeRecord(score: 700, accuracy: 90, playedAt: date(1)))
    _ = try store.append(
      makeRecord(score: 1_100, accuracy: 100, playedAt: date(2), course: "choseong")
    )
    _ = try store.append(
      makeRecord(score: 900, accuracy: 95, playedAt: date(3), course: "acid_rain")
    )
    _ = try store.append(
      makeRecord(score: 800, accuracy: 85, playedAt: date(4), course: "word_match")
    )
    _ = try store.append(
      makeRecord(score: 1_250, accuracy: 98, playedAt: date(5), course: "dictation")
    )
    let library = GameProgressLibrary(store: store)

    XCTAssertEqual(library.progress(for: "official_daily_words", gameKind: .flow)?.bestScore, 700)
    XCTAssertEqual(
      library.progress(for: "official_daily_words", gameKind: .choseong)?.bestScore,
      1_100
    )
    XCTAssertEqual(
      library.progress(for: "official_daily_words", gameKind: .acidRain)?.bestScore,
      900
    )
    XCTAssertEqual(
      library.progress(for: "official_daily_words", gameKind: .wordMatch)?.bestScore,
      800
    )
    XCTAssertEqual(
      library.progress(for: "official_daily_words", gameKind: .dictation)?.bestScore,
      1_250
    )
  }

  func testLegacyInputModeValuesAndUnscopedProgressMigrateToBuiltIn() throws {
    try FileManager.default.createDirectory(at: temporaryRoot, withIntermediateDirectories: true)
    let legacy = """
      {
        "schema_version": 1,
        "records": [{
          "id": "00000000-0000-0000-0000-000000000001",
          "mode": "game",
          "deck_id": "official_daily_words",
          "course": "word",
          "score": 700,
          "max_combo": 4,
          "accuracy": 92.5,
          "characters_per_minute": 84,
          "active_duration": 60,
          "completed_item_count": 8,
          "missed_item_count": 2,
          "input_mode": "built_in",
          "played_at": "1970-01-01T00:00:01Z"
        }],
        "deck_progress": [{
          "deck_id": "official_daily_words",
          "plays": 1,
          "best_score": 700,
          "best_accuracy": 92.5,
          "last_played_at": "1970-01-01T00:00:01Z"
        }]
      }
      """
    try Data(legacy.utf8).write(to: temporaryRoot.appendingPathComponent("game-progress.json"))

    let snapshot = try store.loadSnapshot()

    XCTAssertEqual(snapshot.records.first?.inputMode, .builtIn)
    XCTAssertNil(snapshot.records.first?.deckVersion)
    XCTAssertNil(snapshot.records.first?.competition)
    XCTAssertTrue(
      FileManager.default.fileExists(
        atPath: RecoverableJSONFile.backupURL(
          for: temporaryRoot.appendingPathComponent("game-progress.json")
        ).path
      )
    )
    XCTAssertEqual(
      snapshot.deckProgress[
        GameProgressSnapshot.progressKey(
          deckId: "official_daily_words",
          inputMode: .builtIn
        )
      ]?.bestScore,
      700
    )
  }

  func testLessonRecordIsStoredWithoutChangingGameBestProgress() throws {
    let game = makeRecord(score: 700, accuracy: 90, playedAt: date(1))
    _ = try store.append(game)
    let lesson = makeRecord(
      score: 12,
      accuracy: 95,
      playedAt: date(2),
      course: "practice",
      mode: .lesson
    )

    try store.appendLesson(lesson)
    let snapshot = try store.loadSnapshot()

    XCTAssertEqual(snapshot.records, [game, lesson])
    XCTAssertEqual(snapshot.deckProgress.count, 1)
    XCTAssertEqual(
      snapshot.deckProgress[
        GameProgressSnapshot.progressKey(deckId: game.deckId, inputMode: .builtIn)
      ]?.bestScore,
      700
    )
  }

  func testLearningInsightsAggregatePeriodTrendReviewAndWeakJamo() throws {
    let today = jstDate(year: 2026, month: 7, day: 20)
    let records = [
      makeRecord(
        score: 50,
        accuracy: 90,
        playedAt: today,
        charactersPerMinute: 60,
        activeDuration: 60,
        completedItemCount: 5
      ),
      makeRecord(
        score: 80,
        accuracy: 100,
        playedAt: jstDate(year: 2026, month: 7, day: 19),
        charactersPerMinute: 120,
        activeDuration: 120,
        completedItemCount: 7,
        mode: .lesson
      ),
      makeRecord(
        score: 30,
        accuracy: 80,
        playedAt: jstDate(year: 2026, month: 7, day: 10),
        charactersPerMinute: 50,
        activeDuration: 60,
        completedItemCount: 3
      ),
    ]
    let activityDay = JSTDay(date: jstDate(year: 2026, month: 7, day: 18))
    let retention = [
      activityDay: RetentionDayRecord(day: activityDay, activities: [.curriculum])
    ]
    let item = DeckItem(
      id: "school",
      ko: "학교",
      readingJa: "ハッキョ",
      meaningJa: "学校",
      audio: nil
    )
    let activeReview = ReviewDeckItem(
      item: item,
      sourceDeckId: "deck-a",
      addedAt: today,
      missCount: 2
    )
    var graduatedReview = ReviewDeckItem(
      item: item,
      sourceDeckId: "deck-b",
      addedAt: jstDate(year: 2026, month: 7, day: 1),
      missCount: 1
    )
    graduatedReview.graduatedAt = jstDate(year: 2026, month: 7, day: 19)

    let insights = LearningInsights.make(
      period: .week,
      asOf: today,
      records: records,
      retentionRecords: retention,
      reviewItems: [activeReview, graduatedReview],
      mistakeCounts: ["ㄱ": 3, "ㅏ": 5, "ㅂ": 1]
    )

    XCTAssertEqual(insights.dailyActivities.count, 7)
    XCTAssertEqual(insights.activeDays, 3)
    XCTAssertEqual(insights.sessionCount, 2)
    XCTAssertEqual(insights.totalActiveDuration, 180, accuracy: 0.001)
    XCTAssertEqual(try XCTUnwrap(insights.averageAccuracy), 95, accuracy: 0.001)
    XCTAssertEqual(
      try XCTUnwrap(insights.averageCharactersPerMinute),
      100,
      accuracy: 0.001
    )
    XCTAssertEqual(try XCTUnwrap(insights.accuracyChange), 15, accuracy: 0.001)
    XCTAssertEqual(try XCTUnwrap(insights.speedChange), 50, accuracy: 0.001)
    XCTAssertEqual(insights.completedItemCount, 12)
    XCTAssertEqual(insights.activeReviewCount, 1)
    XCTAssertEqual(insights.addedReviewCount, 1)
    XCTAssertEqual(insights.graduatedReviewCount, 1)
    XCTAssertEqual(insights.weakJamo.map(\.jamo), ["ㅏ", "ㄱ", "ㅂ"])
  }

  func testNonFiniteMetricsAreRejectedBeforeJSONEncoding() throws {
    let record = makeRecord(
      score: 10,
      accuracy: 90,
      playedAt: date(1),
      charactersPerMinute: .infinity
    )

    XCTAssertThrowsError(try store.append(record)) { error in
      XCTAssertEqual(error as? GameProgressStoreError, .invalidProgress)
    }
    XCTAssertFalse(
      FileManager.default.fileExists(
        atPath: temporaryRoot.appendingPathComponent("game-progress.json").path
      )
    )
  }

  func testCompactionKeepsLeaderboardRepresentativesAndAllDeckSummaries() throws {
    XCTAssertGreaterThanOrEqual(
      GameProgressStore.maximumRetainedRecords,
      GameCenterLeaderboard.allCases.count
    )
    let referenceDate = jstDate(year: 2026, month: 8, day: 11)
    let classicBest = GameRecord(
      id: UUID(),
      mode: .game,
      deckId: "flow_topik_beginner",
      deckVersion: 3,
      competition: .officialDeck,
      course: "word",
      score: 9_999,
      maxCombo: 20,
      accuracy: 100,
      charactersPerMinute: 180,
      activeDuration: 60,
      completedItemCount: 20,
      missedItemCount: 0,
      inputMode: .builtIn,
      playedAt: referenceDate.addingTimeInterval(-86_400)
    )
    let weeklyBest = GameRecord(
      id: UUID(),
      mode: .game,
      deckId: "flow_topik_beginner",
      deckVersion: 3,
      competition: .weeklyPiyoCup,
      course: "word",
      score: 18_888,
      maxCombo: 18,
      accuracy: 99,
      charactersPerMinute: 170,
      activeDuration: 60,
      completedItemCount: 18,
      missedItemCount: 0,
      inputMode: .builtIn,
      playedAt: referenceDate
    )
    let progress = DeckProgress(
      deckId: "downloaded-deck",
      inputMode: .builtIn,
      plays: 300,
      bestScore: 7_777,
      bestAccuracy: 98,
      lastPlayedAt: referenceDate
    )
    let recentRecords = (0..<300).map { offset in
      GameRecord(
        id: UUID(),
        mode: .game,
        deckId: "downloaded-deck",
        course: "word",
        score: offset,
        maxCombo: 1,
        accuracy: 90,
        charactersPerMinute: 60,
        activeDuration: 60,
        completedItemCount: 1,
        missedItemCount: 0,
        inputMode: .builtIn,
        playedAt: referenceDate.addingTimeInterval(Double(offset + 1))
      )
    }
    let snapshot = GameProgressSnapshot(
      records: [classicBest, weeklyBest] + recentRecords,
      deckProgress: [progress.id: progress]
    )

    let compacted = GameProgressStore.compacted(snapshot, asOf: referenceDate, limit: 128)
    let scores = GameCenterLeaderboard.bestScores(
      from: compacted.records,
      asOf: referenceDate
    )

    XCTAssertEqual(compacted.records.count, 128)
    XCTAssertTrue(compacted.records.contains(where: { $0.id == classicBest.id }))
    XCTAssertTrue(compacted.records.contains(where: { $0.id == weeklyBest.id }))
    XCTAssertEqual(scores[.flowBeginner], classicBest.score)
    XCTAssertEqual(scores[.weeklyPiyoCup], weeklyBest.score)
    XCTAssertEqual(compacted.deckProgress[progress.id], progress)
  }

  func testLibraryAppendIsDurableAfterExplicitAsyncFlush() async throws {
    let library = GameProgressLibrary(store: store)
    let record = makeRecord(score: 700, accuracy: 92, playedAt: date(1))

    let outcome = library.append(record)
    XCTAssertEqual(outcome?.record, record)
    XCTAssertEqual(library.records, [record])

    let persisted = await library.flushAndWait()
    XCTAssertTrue(persisted)
    XCTAssertEqual(try store.loadSnapshot().records, [record])
  }

  func testLearningInsightsLargeFixtureUsesSinglePassDailyBuckets() {
    let today = jstDate(year: 2026, month: 8, day: 11)
    let records = (0..<10_000).map { offset in
      makeRecord(
        score: offset,
        accuracy: 90,
        playedAt: today.addingTimeInterval(-Double(offset % 60) * 86_400),
        charactersPerMinute: 80,
        activeDuration: 60,
        completedItemCount: 1
      )
    }
    var latest: LearningInsights?

    measure(metrics: [XCTClockMetric()]) {
      latest = LearningInsights.make(
        period: .month,
        asOf: today,
        records: records,
        retentionRecords: [:],
        reviewItems: [],
        mistakeCounts: [:]
      )
    }

    XCTAssertEqual(latest?.dailyActivities.count, 30)
    XCTAssertGreaterThan(latest?.sessionCount ?? 0, 0)
  }

  func testCupAndOrdinaryFlowHaveIndependentBestScoresAndNewRecordResults() throws {
    let deckID = GameCenterRankedDeck.piyoCupDeckID
    _ = try store.append(makeRecord(
      score: 700, accuracy: 90, playedAt: date(1), competition: .officialDeck, deckID: deckID
    ))
    let cup = try store.append(makeRecord(
      score: 900, accuracy: 99, playedAt: date(2), competition: .weeklyPiyoCup, deckID: deckID
    ))
    let flow = try store.append(makeRecord(
      score: 800, accuracy: 91, playedAt: date(3), deckID: deckID
    ))
    let lowerCup = try store.append(makeRecord(
      score: 850, accuracy: 95, playedAt: date(4), competition: .weeklyPiyoCup, deckID: deckID
    ))

    XCTAssertTrue(cup.isNewBest)
    XCTAssertNil(cup.previousBestScore)
    XCTAssertTrue(flow.isNewBest)
    XCTAssertEqual(flow.previousBestScore, 700)
    XCTAssertEqual(flow.deckProgress.plays, 2)
    XCTAssertEqual(flow.deckProgress.bestAccuracy, 91)
    XCTAssertFalse(lowerCup.isNewBest)
    XCTAssertEqual(lowerCup.previousBestScore, 900)
    XCTAssertEqual(lowerCup.deckProgress.plays, 2)
    XCTAssertEqual(lowerCup.deckProgress.bestAccuracy, 99)
    let reloaded = GameProgressLibrary(store: store)
    XCTAssertEqual(reloaded.progress(for: deckID)?.bestScore, 800)
    XCTAssertEqual(reloaded.progress(for: deckID, competition: .officialDeck)?.bestScore, 800)
    XCTAssertEqual(reloaded.progress(for: deckID, competition: .weeklyPiyoCup)?.bestScore, 900)
  }

  func testCupRecordDoesNotCreateOrdinaryFlowProgress() throws {
    let deckID = GameCenterRankedDeck.piyoCupDeckID
    _ = try store.append(makeRecord(
      score: 900, accuracy: 90, playedAt: date(1), competition: .weeklyPiyoCup, deckID: deckID
    ))
    let library = GameProgressLibrary(store: store)

    XCTAssertNil(library.progress(for: deckID))
    XCTAssertEqual(library.progress(for: deckID, competition: .weeklyPiyoCup)?.plays, 1)
  }

  func testCupAndFlowRemainSeparateForOSInputAndComboCelebrations() async throws {
    let deckID = GameCenterRankedDeck.piyoCupDeckID
    let library = GameProgressLibrary(store: store)
    for (offset, scope) in [
      (SessionInputMode.builtIn, GameCompetition.officialDeck, 700, 7),
      (.builtIn, .weeklyPiyoCup, 900, 9),
      (.osIME, .officialDeck, 1_100, 11),
      (.osIME, .weeklyPiyoCup, 1_300, 13),
    ].enumerated() {
      _ = library.append(makeRecord(
        score: scope.2, accuracy: 90, playedAt: date(Double(offset)),
        inputMode: scope.0, competition: scope.1, deckID: deckID, maxCombo: scope.3
      ))
    }
    _ = library.appendLesson(makeRecord(
      score: 9_999, accuracy: 100, playedAt: date(5), mode: .lesson,
      deckID: deckID, maxCombo: 999
    ))
    let persisted = await library.flushAndWait()
    XCTAssertTrue(persisted)
    let reloaded = GameProgressLibrary(store: store)

    XCTAssertEqual(reloaded.progress(for: deckID, inputMode: .osIME)?.bestScore, 1_100)
    XCTAssertEqual(reloaded.progress(
      for: deckID, inputMode: .osIME, competition: .weeklyPiyoCup
    )?.bestScore, 1_300)
    XCTAssertEqual(reloaded.bestCombo(for: deckID), 7)
    XCTAssertEqual(reloaded.bestCombo(for: deckID, competition: .weeklyPiyoCup), 9)
    XCTAssertEqual(reloaded.bestCombo(for: deckID, inputMode: .osIME), 11)
    XCTAssertEqual(reloaded.bestCombo(
      for: deckID, inputMode: .osIME, competition: .weeklyPiyoCup
    ), 13)
  }

  func testLegacyMixedSummariesArePreservedAndRebuiltFromAttributedRecords() async throws {
    let deckID = GameCenterRankedDeck.piyoCupDeckID
    let flow = makeRecord(
      score: 400, accuracy: 90, playedAt: date(1), competition: .officialDeck, deckID: deckID
    )
    let cup = makeRecord(
      score: 900, accuracy: 99, playedAt: date(2), competition: .weeklyPiyoCup, deckID: deckID
    )
    let mixed = DeckProgress(
      deckId: deckID, inputMode: .builtIn, plays: 200, bestScore: 9_999,
      bestAccuracy: 100, lastPlayedAt: date(2)
    )
    try writeLegacyProgress(records: [cup, flow], summaries: [mixed])
    let migrated = try store.loadSnapshot()
    let cupKey = GameProgressSnapshot.progressKey(
      deckId: deckID, inputMode: .builtIn, competition: .weeklyPiyoCup
    )

    XCTAssertEqual(migrated.records, [cup, flow])
    XCTAssertEqual(migrated.legacyMixedProgress[mixed.id], mixed)
    XCTAssertEqual(migrated.deckProgress[mixed.id]?.bestScore, 400)
    XCTAssertEqual(migrated.deckProgress[mixed.id]?.plays, 1)
    XCTAssertEqual(migrated.deckProgress[cupKey]?.bestScore, 900)
    XCTAssertEqual(migrated.deckProgress[cupKey]?.lastPlayedAt, date(2))

    let library = GameProgressLibrary(store: store)
    let persisted = await library.flushAndWait()
    XCTAssertTrue(persisted)
    XCTAssertEqual(try store.loadSnapshot(), migrated)
    let saved = try XCTUnwrap(JSONSerialization.jsonObject(
      with: Data(contentsOf: temporaryRoot.appendingPathComponent("game-progress.json"))
    ) as? [String: Any])
    XCTAssertEqual(saved["schema_version"] as? Int, 3)
    let outcome = try XCTUnwrap(library.append(makeRecord(
      score: 500, accuracy: 95, playedAt: date(3), deckID: deckID
    )))
    XCTAssertTrue(outcome.isNewBest)
    XCTAssertEqual(outcome.previousBestScore, 400)
    let appended = await library.flushAndWait()
    XCTAssertTrue(appended)
    XCTAssertEqual(try store.loadSnapshot().legacyMixedProgress[mixed.id], mixed)
  }

  func testCompactedLegacyCupCannotBeMisattributedToOrdinaryFlow() throws {
    let deckID = GameCenterRankedDeck.piyoCupDeckID
    let mixed = DeckProgress(
      deckId: deckID, inputMode: .builtIn, plays: 200, bestScore: 9_999,
      bestAccuracy: 100, lastPlayedAt: date(2)
    )
    try writeLegacyProgress(records: [], summaries: [mixed])
    let migrated = try store.loadSnapshot()

    XCTAssertTrue(migrated.deckProgress.isEmpty)
    XCTAssertEqual(migrated.legacyMixedProgress[mixed.id], mixed)
    let outcome = try store.append(makeRecord(
      score: 500, accuracy: 95, playedAt: date(3), deckID: deckID
    ))
    XCTAssertTrue(outcome.isNewBest)
    XCTAssertNil(outcome.previousBestScore)
    XCTAssertEqual(try store.loadSnapshot().legacyMixedProgress[mixed.id], mixed)
  }

  func testLegacyMigrationPreservesUnaffectedModesAndSeparatesOSCup() throws {
    let deckID = GameCenterRankedDeck.piyoCupDeckID
    let summaries = [
      DeckProgress(deckId: deckID, inputMode: .builtInKorean10Key, plays: 10,
        bestScore: 800, bestAccuracy: 95, lastPlayedAt: date(2)),
      DeckProgress(deckId: "flow_topik_intermediate", inputMode: .builtIn, plays: 10,
        bestScore: 900, bestAccuracy: 96, lastPlayedAt: date(2)),
      DeckProgress(deckId: deckID, inputMode: .osIME, plays: 20,
        bestScore: 1_000, bestAccuracy: 98, lastPlayedAt: date(2)),
    ]
    try writeLegacyProgress(records: [
      makeRecord(score: 500, accuracy: 90, playedAt: date(1), inputMode: .osIME, deckID: deckID),
      makeRecord(score: 1_000, accuracy: 98, playedAt: date(2), inputMode: .osIME,
        competition: .weeklyPiyoCup, deckID: deckID),
    ], summaries: summaries)
    let library = GameProgressLibrary(store: store)
    let migrated = try store.loadSnapshot()

    XCTAssertEqual(migrated.deckProgress[summaries[0].id], summaries[0])
    XCTAssertEqual(migrated.deckProgress[summaries[1].id], summaries[1])
    XCTAssertEqual(migrated.legacyMixedProgress, [summaries[2].id: summaries[2]])
    XCTAssertEqual(library.progress(for: deckID, inputMode: .osIME)?.bestScore, 500)
    XCTAssertEqual(library.progress(
      for: deckID, inputMode: .osIME, competition: .weeklyPiyoCup
    )?.bestScore, 1_000)
  }

  private func writeLegacyProgress(records: [GameRecord], summaries: [DeckProgress]) throws {
    let encoder = JSONEncoder()
    encoder.dateEncodingStrategy = .iso8601
    let object: [String: Any] = [
      "schema_version": 2,
      "records": try JSONSerialization.jsonObject(with: encoder.encode(records)),
      "deck_progress": try JSONSerialization.jsonObject(with: encoder.encode(summaries)),
    ]
    try FileManager.default.createDirectory(at: temporaryRoot, withIntermediateDirectories: true)
    try JSONSerialization.data(withJSONObject: object)
      .write(to: temporaryRoot.appendingPathComponent("game-progress.json"))
  }

  private func makeRecord(
    score: Int,
    accuracy: Double,
    playedAt: Date,
    inputMode: SessionInputMode = .builtIn,
    course: String = "word",
    charactersPerMinute: Double = 84,
    activeDuration: TimeInterval = 60,
    completedItemCount: Int = 8,
    deckVersion: Int? = nil,
    competition: GameCompetition? = nil,
    mode: SessionMode = .game,
    deckID: String = "official_daily_words",
    maxCombo: Int = 4
  ) -> GameRecord {
    GameRecord(
      id: UUID(),
      mode: mode,
      deckId: deckID,
      deckVersion: deckVersion,
      competition: competition,
      course: course,
      score: score,
      maxCombo: maxCombo,
      accuracy: accuracy,
      charactersPerMinute: charactersPerMinute,
      activeDuration: activeDuration,
      completedItemCount: completedItemCount,
      missedItemCount: 2,
      inputMode: inputMode,
      playedAt: playedAt
    )
  }

  private func date(_ value: TimeInterval) -> Date {
    Date(timeIntervalSince1970: value)
  }

  private func jstDate(year: Int, month: Int, day: Int) -> Date {
    var components = DateComponents()
    components.calendar = RetentionCalendar.jst
    components.timeZone = RetentionCalendar.jst.timeZone
    components.year = year
    components.month = month
    components.day = day
    components.hour = 12
    return components.date!
  }
}
