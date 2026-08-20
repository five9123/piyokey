import Combine
import Foundation

struct JSTDay: Codable, Comparable, Hashable, Identifiable, RawRepresentable {
  let rawValue: String

  var id: String { rawValue }

  init?(rawValue: String) {
    let parts = rawValue.split(separator: "-")
    guard parts.count == 3,
      let year = Int(parts[0]),
      let month = Int(parts[1]),
      let day = Int(parts[2]),
      String(format: "%04d-%02d-%02d", year, month, day) == rawValue,
      RetentionCalendar.isValid(year: year, month: month, day: day)
    else { return nil }
    self.rawValue = rawValue
  }

  init(date: Date) {
    let components = RetentionCalendar.jst.dateComponents([.year, .month, .day], from: date)
    guard let year = components.year, let month = components.month, let day = components.day else {
      preconditionFailure("JST calendar did not produce a complete day")
    }
    self.rawValue = String(format: "%04d-%02d-%02d", year, month, day)
  }

  static func < (lhs: JSTDay, rhs: JSTDay) -> Bool {
    lhs.rawValue < rhs.rawValue
  }

  func adding(days: Int) -> JSTDay {
    guard let date = RetentionCalendar.date(for: self),
      let shifted = RetentionCalendar.jst.date(byAdding: .day, value: days, to: date)
    else { preconditionFailure("Validated JST day could not be shifted") }
    return JSTDay(date: shifted)
  }

  var ordinal: Int {
    guard let date = RetentionCalendar.date(for: self) else {
      preconditionFailure("Validated JST day could not be converted")
    }
    let epoch = Date(timeIntervalSince1970: 0)
    return RetentionCalendar.jst.dateComponents([.day], from: epoch, to: date).day ?? 0
  }

  init(from decoder: Decoder) throws {
    let container = try decoder.singleValueContainer()
    let value = try container.decode(String.self)
    guard let day = JSTDay(rawValue: value) else {
      throw DecodingError.dataCorruptedError(
        in: container,
        debugDescription: "Expected a valid YYYY-MM-DD JST day"
      )
    }
    self = day
  }

  func encode(to encoder: Encoder) throws {
    var container = encoder.singleValueContainer()
    try container.encode(rawValue)
  }
}

enum RetentionCalendar {
  static var jst: Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.locale = Locale(identifier: "ja_JP")
    calendar.timeZone = TimeZone(identifier: "Asia/Tokyo")!
    return calendar
  }

  static func isValid(year: Int, month: Int, day: Int) -> Bool {
    var components = DateComponents()
    components.calendar = jst
    components.timeZone = jst.timeZone
    components.year = year
    components.month = month
    components.day = day
    guard let date = jst.date(from: components) else { return false }
    let roundTrip = jst.dateComponents([.year, .month, .day], from: date)
    return roundTrip.year == year && roundTrip.month == month && roundTrip.day == day
  }

  static func date(for day: JSTDay, hour: Int = 12) -> Date? {
    let parts = day.rawValue.split(separator: "-").compactMap { Int($0) }
    guard parts.count == 3 else { return nil }
    var components = DateComponents()
    components.calendar = jst
    components.timeZone = jst.timeZone
    components.year = parts[0]
    components.month = parts[1]
    components.day = parts[2]
    components.hour = hour
    return jst.date(from: components)
  }

  static func days(endingAt day: JSTDay, count: Int) -> [JSTDay] {
    guard count > 0 else { return [] }
    return (0..<count).reversed().map { day.adding(days: -$0) }
  }

  static func stampCardDays(asOf today: JSTDay) -> [JSTDay] {
    guard let date = date(for: today) else {
      preconditionFailure("Validated JST day could not be converted")
    }
    let daysSinceMonday = (jst.component(.weekday, from: date) + 5) % 7
    let monday = today.adding(days: -daysSinceMonday)
    return (0..<7).map { monday.adding(days: $0) }
  }
}

enum RetentionStampDayState: Equatable {
  case completed
  case missed
  case todayPending
  case upcoming

  init(day: JSTDay, today: JSTDay, isStamped: Bool) {
    if isStamped {
      self = .completed
    } else if day < today {
      self = .missed
    } else if day == today {
      self = .todayPending
    } else {
      self = .upcoming
    }
  }

  var localizationKey: String {
    switch self {
    case .completed: "retention.stamp.completed"
    case .missed: "retention.stamp.missed"
    case .todayPending: "retention.stamp.today_pending"
    case .upcoming: "retention.stamp.upcoming"
    }
  }
}

enum RetentionClock {
  static func now() -> Date {
    #if DEBUG
      if let rawDay = ProcessInfo.processInfo.environment["UITEST_JST_DAY"],
        let day = JSTDay(rawValue: rawDay),
        let date = RetentionCalendar.date(for: day)
      {
        return date
      }
    #endif
    return Date()
  }
}

enum MascotDailyEncouragement {
  enum Context: Equatable {
    case beforeStudy
    case completedToday
    case activeStreak
    case returning
  }

  static let messageCount = 10

  static func context(for day: JSTDay, completedDays: Set<JSTDay>) -> Context {
    if completedDays.contains(day) { return .completedToday }

    let pastDays = completedDays.filter { $0 < day }
    guard let mostRecentDay = pastDays.max() else { return .beforeStudy }
    return mostRecentDay == day.adding(days: -1) ? .activeStreak : .returning
  }

  static func localizationKey(for day: JSTDay, completedDays: Set<JSTDay>) -> String {
    let indices = messageIndices(for: context(for: day, completedDays: completedDays))
    let remainder = day.ordinal % indices.count
    let position = remainder >= 0 ? remainder : remainder + indices.count
    let index = indices[position]
    return "mascot.daily_encouragement.\(index)"
  }

  static func messageIndices(for context: Context) -> [Int] {
    switch context {
    case .beforeStudy: [0, 1, 2, 4]
    case .completedToday: [5, 6]
    case .activeStreak: [3, 7]
    case .returning: [8, 9]
    }
  }
}

struct RetentionSessionContext: Equatable {
  let day: JSTDay

  init(startedAt: Date = RetentionClock.now()) {
    self.day = JSTDay(date: startedAt)
  }
}

enum RetentionActivityKind: String, Codable, CaseIterable, Hashable {
  case curriculum
  case game
  case dailyChallenge = "daily_challenge"
}

struct RetentionDayRecord: Codable, Equatable, Identifiable {
  let day: JSTDay
  var activities: [RetentionActivityKind]

  var id: JSTDay { day }
  var isStamped: Bool { !activities.isEmpty }
  var completedDailyChallenge: Bool { activities.contains(.dailyChallenge) }

  private enum CodingKeys: String, CodingKey {
    case day
    case activities
  }
}

struct RetentionSnapshot: Equatable {
  var records: [JSTDay: RetentionDayRecord]

  static let empty = RetentionSnapshot(records: [:])

  var completedDays: Set<JSTDay> {
    Set(records.values.filter(\.isStamped).map(\.day))
  }
}

struct RetentionStreak: Equatable {
  let current: Int
  let longest: Int

  static func calculate(completedDays: Set<JSTDay>, asOf today: JSTDay) -> RetentionStreak {
    let currentAnchor: JSTDay?
    if completedDays.contains(today) {
      currentAnchor = today
    } else {
      let yesterday = today.adding(days: -1)
      currentAnchor = completedDays.contains(yesterday) ? yesterday : nil
    }

    var current = 0
    if var cursor = currentAnchor {
      while completedDays.contains(cursor) {
        current += 1
        cursor = cursor.adding(days: -1)
      }
    }

    var longest = 0
    var running = 0
    var previous: JSTDay?
    for day in completedDays.sorted() {
      running = previous?.adding(days: 1) == day ? running + 1 : 1
      longest = max(longest, running)
      previous = day
    }
    return RetentionStreak(current: current, longest: longest)
  }
}

enum RetentionMutation: Equatable {
  case inserted
  case unchanged
}

enum RetentionStoreError: Error, Equatable {
  case unsupportedSchema(Int)
  case invalidRecord
}

struct RetentionStore {
  private struct Index: Codable {
    let schemaVersion: Int
    let records: [RetentionDayRecord]

    private enum CodingKeys: String, CodingKey {
      case schemaVersion = "schema_version"
      case records
    }
  }

  private static let currentSchemaVersion = 1

  let rootURL: URL
  private let fileManager: FileManager

  init(rootURL: URL, fileManager: FileManager = .default) {
    self.rootURL = rootURL
    self.fileManager = fileManager
  }

  static var live: RetentionStore {
    let applicationSupport = FileManager.default.urls(
      for: .applicationSupportDirectory,
      in: .userDomainMask
    )[0]
    return RetentionStore(
      rootURL:
        applicationSupport
        .appendingPathComponent("Hanco", isDirectory: true)
        .appendingPathComponent("Retention", isDirectory: true)
    )
  }

  func loadSnapshot() throws -> RetentionSnapshot {
    try RecoverableJSONFile.load(
      from: indexURL,
      fileManager: fileManager,
      shouldRecover: shouldRecover,
      decode: decodeSnapshot
    ) ?? .empty
  }

  private func decodeSnapshot(from data: Data) throws -> RetentionSnapshot {
    let index = try JSONDecoder().decode(Index.self, from: data)
    guard index.schemaVersion == Self.currentSchemaVersion else {
      throw RetentionStoreError.unsupportedSchema(index.schemaVersion)
    }

    var records: [JSTDay: RetentionDayRecord] = [:]
    for record in index.records {
      guard !record.activities.isEmpty,
        Set(record.activities).count == record.activities.count,
        records[record.day] == nil
      else { throw RetentionStoreError.invalidRecord }
      records[record.day] = record
    }
    return RetentionSnapshot(records: records)
  }

  @discardableResult
  func record(
    _ activity: RetentionActivityKind,
    on day: JSTDay
  ) throws -> RetentionMutation {
    var snapshot = try loadSnapshot()
    var record = snapshot.records[day] ?? RetentionDayRecord(day: day, activities: [])
    guard !record.activities.contains(activity) else { return .unchanged }
    record.activities.append(activity)
    record.activities.sort { $0.rawValue < $1.rawValue }
    snapshot.records[day] = record
    try write(snapshot)
    return .inserted
  }

  func reset() throws {
    if fileManager.fileExists(atPath: rootURL.path) {
      try fileManager.removeItem(at: rootURL)
    }
  }

  private var indexURL: URL { rootURL.appendingPathComponent("retention-progress.json") }

  private func write(_ snapshot: RetentionSnapshot) throws {
    try fileManager.createDirectory(at: rootURL, withIntermediateDirectories: true)
    let encoder = JSONEncoder()
    encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
    let index = Index(
      schemaVersion: Self.currentSchemaVersion,
      records: snapshot.records.values.sorted { $0.day < $1.day }
    )
    try RecoverableJSONFile.write(
      encoder.encode(index),
      to: indexURL,
      fileManager: fileManager
    )
  }

  private func shouldRecover(_ error: Error) -> Bool {
    guard let error = error as? RetentionStoreError else { return true }
    switch error {
    case .unsupportedSchema: return false
    case .invalidRecord: return true
    }
  }
}

@MainActor
final class RetentionLibrary: ObservableObject {
  @Published private(set) var records: [JSTDay: RetentionDayRecord] = [:]
  @Published private(set) var saveFailed = false

  private let store: RetentionStore
  private var retryAction: (() -> Void)?

  init(store: RetentionStore = .live) {
    self.store = store
    reload()
  }

  var completedDays: Set<JSTDay> {
    Set(records.values.filter(\.isStamped).map(\.day))
  }

  func record(_ activity: RetentionActivityKind, session: RetentionSessionContext) {
    record(activity, on: session.day)
  }

  func record(_ activity: RetentionActivityKind, on day: JSTDay) {
    do {
      _ = try store.record(activity, on: day)
      reload()
    } catch {
      saveFailed = true
      retryAction = { [weak self] in self?.record(activity, on: day) }
    }
  }

  func isStamped(_ day: JSTDay) -> Bool {
    records[day]?.isStamped == true
  }

  func completedDailyChallenge(on day: JSTDay) -> Bool {
    records[day]?.completedDailyChallenge == true
  }

  func streak(asOf day: JSTDay) -> RetentionStreak {
    RetentionStreak.calculate(completedDays: completedDays, asOf: day)
  }

  func retryLastSave() {
    retryAction?()
  }

  private func reload() {
    do {
      records = try store.loadSnapshot().records
      saveFailed = false
      retryAction = nil
    } catch {
      records = [:]
      saveFailed = true
      retryAction = { [weak self] in self?.reload() }
    }
  }
}
