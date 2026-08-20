import HangulEngine
import XCTest

@testable import Hanco

@MainActor
final class RetentionStoreTests: XCTestCase {
  private var rootURL: URL!
  private var store: RetentionStore!
  private var defaults: UserDefaults!
  private var defaultsSuiteName: String!

  override func setUpWithError() throws {
    rootURL = FileManager.default.temporaryDirectory
      .appendingPathComponent(UUID().uuidString, isDirectory: true)
    store = RetentionStore(rootURL: rootURL)
    defaultsSuiteName = "RetentionStoreTests.\(UUID().uuidString)"
    defaults = try XCTUnwrap(UserDefaults(suiteName: defaultsSuiteName))
  }

  override func tearDownWithError() throws {
    if FileManager.default.fileExists(atPath: rootURL.path) {
      try FileManager.default.removeItem(at: rootURL)
    }
    defaults.removePersistentDomain(forName: defaultsSuiteName)
    defaults = nil
    defaultsSuiteName = nil
    store = nil
    rootURL = nil
  }

  func testJSTDayChangesExactlyAtTokyoMidnight() throws {
    let beforeMidnight = try XCTUnwrap(
      ISO8601DateFormatter().date(from: "2026-07-19T14:59:59Z")
    )
    let midnight = try XCTUnwrap(
      ISO8601DateFormatter().date(from: "2026-07-19T15:00:00Z")
    )

    XCTAssertEqual(JSTDay(date: beforeMidnight).rawValue, "2026-07-19")
    XCTAssertEqual(JSTDay(date: midnight).rawValue, "2026-07-20")
    XCTAssertNil(JSTDay(rawValue: "2026-02-30"))
  }

  func testSessionKeepsStartDayWhenCompletionCrossesMidnight() throws {
    let startedAt = try XCTUnwrap(
      ISO8601DateFormatter().date(from: "2026-07-19T14:59:59Z")
    )
    let completedAt = try XCTUnwrap(
      ISO8601DateFormatter().date(from: "2026-07-19T15:00:01Z")
    )

    let session = RetentionSessionContext(startedAt: startedAt)
    XCTAssertEqual(session.day.rawValue, "2026-07-19")
    XCTAssertEqual(JSTDay(date: completedAt).rawValue, "2026-07-20")

    try store.record(.game, on: session.day)
    XCTAssertNotNil(try store.loadSnapshot().records[JSTDay(date: startedAt)])
    XCTAssertNil(try store.loadSnapshot().records[JSTDay(date: completedAt)])
  }

  func testActivitiesAreIdempotentAndRoundTripInOneDailyStamp() throws {
    let day = try XCTUnwrap(JSTDay(rawValue: "2026-07-19"))
    XCTAssertEqual(try store.record(.curriculum, on: day), .inserted)
    XCTAssertEqual(try store.record(.curriculum, on: day), .unchanged)
    XCTAssertEqual(try store.record(.game, on: day), .inserted)

    let record = try XCTUnwrap(store.loadSnapshot().records[day])
    XCTAssertEqual(Set(record.activities), [.curriculum, .game])
    XCTAssertTrue(record.isStamped)
    XCTAssertFalse(record.completedDailyChallenge)
  }

  func testCurrentStreakKeepsYesterdayUntilTodayEndsAndFindsLongestRun() throws {
    let today = try XCTUnwrap(JSTDay(rawValue: "2026-07-20"))
    let days: Set<JSTDay> = [
      today.adding(days: -5),
      today.adding(days: -4),
      today.adding(days: -3),
      today.adding(days: -1),
    ]

    XCTAssertEqual(
      RetentionStreak.calculate(completedDays: days, asOf: today),
      RetentionStreak(current: 1, longest: 3)
    )
    XCTAssertEqual(
      RetentionStreak.calculate(completedDays: days.union([today]), asOf: today),
      RetentionStreak(current: 2, longest: 3)
    )
    XCTAssertEqual(
      RetentionStreak.calculate(completedDays: days, asOf: today.adding(days: 1)).current,
      0
    )
  }

  func testStampCardAlwaysRunsFromMondayThroughSunday() throws {
    let expectedWeek = [
      "2026-07-20", "2026-07-21", "2026-07-22", "2026-07-23",
      "2026-07-24", "2026-07-25", "2026-07-26",
    ]

    for rawDay in ["2026-07-20", "2026-07-23", "2026-07-26"] {
      let day = try XCTUnwrap(JSTDay(rawValue: rawDay))
      XCTAssertEqual(
        RetentionCalendar.stampCardDays(asOf: day).map(\.rawValue),
        expectedWeek
      )
    }

    let newYear = try XCTUnwrap(JSTDay(rawValue: "2026-01-01"))
    XCTAssertEqual(
      RetentionCalendar.stampCardDays(asOf: newYear).map(\.rawValue),
      [
        "2025-12-29", "2025-12-30", "2025-12-31", "2026-01-01",
        "2026-01-02", "2026-01-03", "2026-01-04",
      ]
    )
  }

  func testStampDayStateSeparatesMissedTodayAndUpcomingDays() throws {
    let today = try XCTUnwrap(JSTDay(rawValue: "2026-07-23"))

    XCTAssertEqual(
      RetentionStampDayState(day: today.adding(days: -3), today: today, isStamped: true),
      .completed
    )
    XCTAssertEqual(
      RetentionStampDayState(day: today.adding(days: -1), today: today, isStamped: false),
      .missed
    )
    XCTAssertEqual(
      RetentionStampDayState(day: today, today: today, isStamped: false),
      .todayPending
    )
    XCTAssertEqual(
      RetentionStampDayState(day: today.adding(days: 1), today: today, isStamped: false),
      .upcoming
    )
  }

  func testFailedRetentionSaveCanBeRetriedWithoutLosingTheStamp() throws {
    let blockedRoot = FileManager.default.temporaryDirectory
      .appendingPathComponent(UUID().uuidString)
    try Data("blocked".utf8).write(to: blockedRoot)
    defer { try? FileManager.default.removeItem(at: blockedRoot) }

    let retryingLibrary = RetentionLibrary(store: RetentionStore(rootURL: blockedRoot))
    let day = try XCTUnwrap(JSTDay(rawValue: "2026-07-23"))
    retryingLibrary.record(.game, on: day)
    XCTAssertTrue(retryingLibrary.saveFailed)
    XCTAssertFalse(retryingLibrary.isStamped(day))

    try FileManager.default.removeItem(at: blockedRoot)
    retryingLibrary.retryLastSave()

    XCTAssertFalse(retryingLibrary.saveFailed)
    XCTAssertTrue(retryingLibrary.isStamped(day))
  }

  func testDailyChallengeIsDeterministicUniqueAndChangesNextDay() throws {
    let day = try XCTUnwrap(JSTDay(rawValue: "2026-07-19"))
    let today = DailyChallengeCatalog.challenge(for: day)
    let repeated = DailyChallengeCatalog.challenge(for: day)
    let tomorrow = DailyChallengeCatalog.challenge(for: day.adding(days: 1))

    XCTAssertEqual(today, repeated)
    XCTAssertEqual(today.items.count, 5)
    XCTAssertEqual(Set(today.items.map(\.id)).count, 5)
    XCTAssertNotEqual(today.items.map(\.id), tomorrow.items.map(\.id))
    for item in today.items {
      XCTAssertFalse(try JamoDecomposer.keySequence(for: item.ko).isEmpty)
    }
  }

  func testDailyChallengeUsesTheGoalSpecificCurriculumPool() throws {
    let day = try XCTUnwrap(JSTDay(rawValue: "2026-07-19"))
    let expectations: [(OnboardingGoal, String)] = [
      (.keyboard, "chapter_3_syllable_building"),
      (.travel, "chapter_6_sentences"),
      (.topik, "chapter_5_words"),
      (.trends, "chapter_6_sentences"),
    ]

    for (goal, stageID) in expectations {
      let stage = try XCTUnwrap(CurriculumCatalog.stage(id: stageID))
      let allowedItemIDs = Set(stage.items.map(\.id))
      let challenge = DailyChallengeCatalog.challenge(for: day, goal: goal)

      XCTAssertEqual(challenge.items.count, 5)
      XCTAssertTrue(
        challenge.items.allSatisfy { allowedItemIDs.contains($0.id) },
        "\(goal.rawValue) must use \(stageID)"
      )
    }
  }

  func testDailyMascotEncouragementUsesEveryMessageAcrossLearningContexts() throws {
    let day = try XCTUnwrap(JSTDay(rawValue: "2026-07-24"))
    let contexts: [MascotDailyEncouragement.Context] = [
      .beforeStudy, .completedToday, .activeStreak, .returning,
    ]
    let indices = contexts.flatMap(MascotDailyEncouragement.messageIndices)

    XCTAssertEqual(MascotDailyEncouragement.messageCount, 10)
    XCTAssertEqual(indices.sorted(), Array(0..<MascotDailyEncouragement.messageCount))
    XCTAssertEqual(
      MascotDailyEncouragement.localizationKey(for: day, completedDays: []),
      MascotDailyEncouragement.localizationKey(for: day.adding(days: 4), completedDays: [])
    )
  }

  func testDailyMascotEncouragementRespondsToLearningContext() throws {
    let day = try XCTUnwrap(JSTDay(rawValue: "2026-07-24"))

    XCTAssertEqual(
      MascotDailyEncouragement.context(for: day, completedDays: []),
      .beforeStudy
    )
    XCTAssertEqual(
      MascotDailyEncouragement.context(for: day, completedDays: [day]),
      .completedToday
    )
    XCTAssertEqual(
      MascotDailyEncouragement.context(
        for: day,
        completedDays: [day.adding(days: -1), day.adding(days: -2)]
      ),
      .activeStreak
    )
    XCTAssertEqual(
      MascotDailyEncouragement.context(for: day, completedDays: [day.adding(days: -2)]),
      .returning
    )
  }

  func testSevenDayStampRewardsUnlockAtThreeFiveAndSevenDays() {
    XCTAssertEqual(StampReward.allCases.map(\.requiredDays), [3, 5, 7])
    XCTAssertEqual(StampReward.three.prop, .lightstick)
    XCTAssertEqual(StampReward.five.prop, .ribbon)
    XCTAssertEqual(StampReward.seven.prop, .headphones)
  }

  func testReminderDefaultsOffAndPersistsCustomTime() {
    let settings = DailyReminderSettingsStore(defaults: defaults)
    XCTAssertEqual(
      settings.load(),
      DailyReminderPreference(isEnabled: false, hour: 20, minute: 0)
    )

    settings.save(DailyReminderPreference(isEnabled: true, hour: 21, minute: 30))
    XCTAssertEqual(
      settings.load(),
      DailyReminderPreference(isEnabled: true, hour: 21, minute: 30)
    )
  }

  func testReminderScheduleUsesJSTAndDeniedAuthorizationKeepsToggleOff() async {
    let components = DailyReminderRequest.dateComponents(hour: 7, minute: 15)
    XCTAssertEqual(components.timeZone?.identifier, "Asia/Tokyo")
    XCTAssertEqual(components.hour, 7)
    XCTAssertEqual(components.minute, 15)

    let scheduler = ReminderSchedulerSpy(result: .denied)
    let library = DailyReminderLibrary(
      store: DailyReminderSettingsStore(defaults: defaults),
      scheduler: scheduler
    )
    library.setEnabled(true)
    await Task.yield()
    await Task.yield()

    XCTAssertEqual(scheduler.scheduledTimes, [ReminderTime(hour: 20, minute: 0)])
    XCTAssertFalse(library.preference.isEnabled)
    XCTAssertEqual(library.status, .denied)
  }

  func testReminderEnableTimeChangeAndDisableStaySynchronized() async {
    let scheduler = ReminderSchedulerSpy(result: .scheduled)
    let settings = DailyReminderSettingsStore(defaults: defaults)
    let library = DailyReminderLibrary(store: settings, scheduler: scheduler)

    library.setEnabled(true)
    await settleReminderTask()
    XCTAssertTrue(library.preference.isEnabled)
    XCTAssertEqual(scheduler.scheduledTimes, [ReminderTime(hour: 20, minute: 0)])

    library.setTime(hour: 21, minute: 30)
    await settleReminderTask()
    XCTAssertEqual(scheduler.scheduledTimes.last, ReminderTime(hour: 21, minute: 30))
    XCTAssertEqual(
      settings.load(),
      DailyReminderPreference(isEnabled: true, hour: 21, minute: 30)
    )

    library.setEnabled(false)
    XCTAssertEqual(scheduler.cancelCount, 1)
    XCTAssertFalse(settings.load().isEnabled)
  }

  func testUnsupportedSchemaAndDuplicateDaysAreRejected() throws {
    try FileManager.default.createDirectory(at: rootURL, withIntermediateDirectories: true)
    let fileURL = rootURL.appendingPathComponent("retention-progress.json")
    try Data("{\"schema_version\":99,\"records\":[]}".utf8).write(to: fileURL)
    XCTAssertThrowsError(try store.loadSnapshot()) { error in
      XCTAssertEqual(error as? RetentionStoreError, .unsupportedSchema(99))
    }

    try Data(
      """
      {"schema_version":1,"records":[
        {"day":"2026-07-19","activities":["game"]},
        {"day":"2026-07-19","activities":["curriculum"]}
      ]}
      """.utf8
    ).write(to: fileURL)
    XCTAssertThrowsError(try store.loadSnapshot()) { error in
      XCTAssertEqual(error as? RetentionStoreError, .invalidRecord)
    }
  }

  func testInvalidRetentionRecordsRestoreLatestStampBackup() throws {
    let day = try XCTUnwrap(JSTDay(rawValue: "2026-07-19"))
    try store.record(.game, on: day)
    let fileURL = rootURL.appendingPathComponent("retention-progress.json")
    try Data(
      """
      {"schema_version":1,"records":[
        {"day":"2026-07-19","activities":["game"]},
        {"day":"2026-07-19","activities":["curriculum"]}
      ]}
      """.utf8
    ).write(to: fileURL)

    let recovered = try XCTUnwrap(store.loadSnapshot().records[day])

    XCTAssertEqual(recovered.activities, [.game])
    XCTAssertTrue(
      FileManager.default.fileExists(
        atPath: RecoverableJSONFile.corruptURL(for: fileURL).path
      )
    )
  }

  private func settleReminderTask() async {
    await Task.yield()
    await Task.yield()
  }
}

private struct ReminderTime: Equatable {
  let hour: Int
  let minute: Int
}

@MainActor
private final class ReminderSchedulerSpy: DailyReminderScheduling {
  let result: DailyReminderScheduleResult
  private(set) var scheduledTimes: [ReminderTime] = []
  private(set) var cancelCount = 0

  init(result: DailyReminderScheduleResult) {
    self.result = result
  }

  func schedule(hour: Int, minute: Int) async -> DailyReminderScheduleResult {
    scheduledTimes.append(ReminderTime(hour: hour, minute: minute))
    return result
  }

  func cancel() {
    cancelCount += 1
  }
}
