import Combine
import Foundation

enum SessionMode: String, Codable, Equatable {
  case lesson
  case game
}

enum GameCompetition: String, Codable, Equatable {
  case officialDeck = "official_deck_v1"
  case weeklyPiyoCup = "weekly_piyo_cup_v1"
}

enum SessionInputMode: String, Codable, CaseIterable, Equatable {
  case builtIn = "builtin"
  case osIME = "os_ime"

  init(from decoder: Decoder) throws {
    let container = try decoder.singleValueContainer()
    let value = try container.decode(String.self)
    switch value {
    case Self.builtIn.rawValue, "built_in": self = .builtIn
    case Self.osIME.rawValue, "os_keyboard": self = .osIME
    default:
      throw DecodingError.dataCorruptedError(
        in: container,
        debugDescription: "Unsupported input mode: \(value)"
      )
    }
  }

  func encode(to encoder: Encoder) throws {
    var container = encoder.singleValueContainer()
    try container.encode(rawValue)
  }
}

enum GameKind: String, Codable, CaseIterable, Equatable {
  case flow
  case acidRain = "acid_rain"
  case choseong
  case dictation
  case wordMatch = "word_match"

  init(course: String) {
    switch course {
    case Self.acidRain.rawValue: self = .acidRain
    case Self.choseong.rawValue: self = .choseong
    case Self.wordMatch.rawValue: self = .wordMatch
    case Self.dictation.rawValue: self = .dictation
    default: self = .flow
    }
  }
}

struct GameRecord: Codable, Equatable, Identifiable {
  let id: UUID
  let mode: SessionMode
  let deckId: String
  let deckVersion: Int?
  let competition: GameCompetition?
  let course: String
  let score: Int
  let maxCombo: Int
  let accuracy: Double
  let charactersPerMinute: Double
  let activeDuration: TimeInterval
  let completedItemCount: Int
  let missedItemCount: Int
  let inputMode: SessionInputMode
  let playedAt: Date

  private enum CodingKeys: String, CodingKey {
    case id
    case mode
    case deckId = "deck_id"
    case deckVersion = "deck_version"
    case competition
    case course
    case score
    case maxCombo = "max_combo"
    case accuracy
    case charactersPerMinute = "characters_per_minute"
    case activeDuration = "active_duration"
    case completedItemCount = "completed_item_count"
    case missedItemCount = "missed_item_count"
    case inputMode = "input_mode"
    case playedAt = "played_at"
  }

  init(
    id: UUID,
    mode: SessionMode,
    deckId: String,
    deckVersion: Int? = nil,
    competition: GameCompetition? = nil,
    course: String,
    score: Int,
    maxCombo: Int,
    accuracy: Double,
    charactersPerMinute: Double,
    activeDuration: TimeInterval,
    completedItemCount: Int,
    missedItemCount: Int,
    inputMode: SessionInputMode,
    playedAt: Date
  ) {
    self.id = id
    self.mode = mode
    self.deckId = deckId
    self.deckVersion = deckVersion
    self.competition = competition
    self.course = course
    self.score = score
    self.maxCombo = maxCombo
    self.accuracy = accuracy
    self.charactersPerMinute = charactersPerMinute
    self.activeDuration = activeDuration
    self.completedItemCount = completedItemCount
    self.missedItemCount = missedItemCount
    self.inputMode = inputMode
    self.playedAt = playedAt
  }
}

struct DeckProgress: Codable, Equatable, Identifiable {
  let deckId: String
  let gameKind: GameKind
  let inputMode: SessionInputMode
  var plays: Int
  var bestScore: Int
  var bestAccuracy: Double
  var lastPlayedAt: Date

  var id: String {
    GameProgressSnapshot.progressKey(
      deckId: deckId,
      gameKind: gameKind,
      inputMode: inputMode
    )
  }

  private enum CodingKeys: String, CodingKey {
    case deckId = "deck_id"
    case gameKind = "game_kind"
    case inputMode = "input_mode"
    case plays
    case bestScore = "best_score"
    case bestAccuracy = "best_accuracy"
    case lastPlayedAt = "last_played_at"
  }

  init(
    deckId: String,
    gameKind: GameKind = .flow,
    inputMode: SessionInputMode,
    plays: Int,
    bestScore: Int,
    bestAccuracy: Double,
    lastPlayedAt: Date
  ) {
    self.deckId = deckId
    self.gameKind = gameKind
    self.inputMode = inputMode
    self.plays = plays
    self.bestScore = bestScore
    self.bestAccuracy = bestAccuracy
    self.lastPlayedAt = lastPlayedAt
  }

  init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    deckId = try container.decode(String.self, forKey: .deckId)
    gameKind = try container.decodeIfPresent(GameKind.self, forKey: .gameKind) ?? .flow
    inputMode = try container.decodeIfPresent(SessionInputMode.self, forKey: .inputMode) ?? .builtIn
    plays = try container.decode(Int.self, forKey: .plays)
    bestScore = try container.decode(Int.self, forKey: .bestScore)
    bestAccuracy = try container.decode(Double.self, forKey: .bestAccuracy)
    lastPlayedAt = try container.decode(Date.self, forKey: .lastPlayedAt)
  }
}

struct GameProgressSnapshot: Equatable {
  var records: [GameRecord]
  var deckProgress: [String: DeckProgress]

  static let empty = GameProgressSnapshot(records: [], deckProgress: [:])

  static func progressKey(
    deckId: String,
    gameKind: GameKind = .flow,
    inputMode: SessionInputMode
  ) -> String {
    "\(deckId)::\(gameKind.rawValue)::\(inputMode.rawValue)"
  }
}

enum LearningInsightPeriod: Int, CaseIterable, Identifiable {
  case week = 7
  case month = 30

  var id: Int { rawValue }
}

struct DailyLearningActivity: Equatable, Identifiable {
  let day: JSTDay
  let sessionCount: Int
  let activeDuration: TimeInterval
  let isActive: Bool

  var id: JSTDay { day }
}

struct WeakJamoInsight: Equatable, Identifiable {
  let jamo: String
  let mistakeCount: Int

  var id: String { jamo }
}

struct LearningInsights: Equatable {
  let period: LearningInsightPeriod
  let dailyActivities: [DailyLearningActivity]
  let activeDays: Int
  let sessionCount: Int
  let totalActiveDuration: TimeInterval
  let averageAccuracy: Double?
  let averageCharactersPerMinute: Double?
  let accuracyChange: Double?
  let speedChange: Double?
  let completedItemCount: Int
  let activeReviewCount: Int
  let addedReviewCount: Int
  let graduatedReviewCount: Int
  let weakJamo: [WeakJamoInsight]

  static func make(
    period: LearningInsightPeriod,
    asOf date: Date,
    records: [GameRecord],
    retentionRecords: [JSTDay: RetentionDayRecord],
    reviewItems: [ReviewDeckItem],
    mistakeCounts: [String: Int]
  ) -> LearningInsights {
    let today = JSTDay(date: date)
    let days = RetentionCalendar.days(endingAt: today, count: period.rawValue)
    let daySet = Set(days)
    let previousEnd = today.adding(days: -period.rawValue)
    let previousDays = Set(
      RetentionCalendar.days(endingAt: previousEnd, count: period.rawValue)
    )
    var currentRecords: [GameRecord] = []
    var previousRecords: [GameRecord] = []
    var currentRecordsByDay: [JSTDay: [GameRecord]] = [:]
    currentRecords.reserveCapacity(min(records.count, period.rawValue * 4))
    previousRecords.reserveCapacity(min(records.count, period.rawValue * 4))
    for record in records {
      let day = JSTDay(date: record.playedAt)
      if daySet.contains(day) {
        currentRecords.append(record)
        currentRecordsByDay[day, default: []].append(record)
      } else if previousDays.contains(day) {
        previousRecords.append(record)
      }
    }
    let currentMetrics = metrics(for: currentRecords)
    let previousMetrics = metrics(for: previousRecords)

    let activities = days.map { day in
      let dayRecords = currentRecordsByDay[day] ?? []
      return DailyLearningActivity(
        day: day,
        sessionCount: dayRecords.count,
        activeDuration: dayRecords.reduce(0) { $0 + $1.activeDuration },
        isActive: retentionRecords[day]?.isStamped == true || !dayRecords.isEmpty
      )
    }

    var activeReviewCount = 0
    var addedReviewCount = 0
    var graduatedReviewCount = 0
    for item in reviewItems {
      if item.isActive { activeReviewCount += 1 }
      if daySet.contains(JSTDay(date: item.addedAt)) { addedReviewCount += 1 }
      if let graduatedAt = item.graduatedAt,
        daySet.contains(JSTDay(date: graduatedAt))
      {
        graduatedReviewCount += 1
      }
    }

    return LearningInsights(
      period: period,
      dailyActivities: activities,
      activeDays: activities.filter(\.isActive).count,
      sessionCount: currentRecords.count,
      totalActiveDuration: currentMetrics.totalDuration,
      averageAccuracy: currentMetrics.averageAccuracy,
      averageCharactersPerMinute: currentMetrics.averageCharactersPerMinute,
      accuracyChange: change(
        current: currentMetrics.averageAccuracy,
        previous: previousMetrics.averageAccuracy
      ),
      speedChange: change(
        current: currentMetrics.averageCharactersPerMinute,
        previous: previousMetrics.averageCharactersPerMinute
      ),
      completedItemCount: currentRecords.reduce(0) { $0 + $1.completedItemCount },
      activeReviewCount: activeReviewCount,
      addedReviewCount: addedReviewCount,
      graduatedReviewCount: graduatedReviewCount,
      weakJamo:
        mistakeCounts
        .filter { $0.value > 0 }
        .map { WeakJamoInsight(jamo: $0.key, mistakeCount: $0.value) }
        .sorted {
          if $0.mistakeCount == $1.mistakeCount { return $0.jamo < $1.jamo }
          return $0.mistakeCount > $1.mistakeCount
        }
        .prefix(5)
        .map { $0 }
    )
  }

  private struct Metrics {
    let totalDuration: TimeInterval
    let averageAccuracy: Double?
    let averageCharactersPerMinute: Double?
  }

  private static func metrics(for records: [GameRecord]) -> Metrics {
    guard !records.isEmpty else {
      return Metrics(
        totalDuration: 0,
        averageAccuracy: nil,
        averageCharactersPerMinute: nil
      )
    }
    let totalDuration = records.reduce(0) { $0 + $1.activeDuration }
    let averageAccuracy = records.reduce(0) { $0 + $1.accuracy } / Double(records.count)
    let timedRecords = records.filter { $0.activeDuration > 0 }
    let averageCharactersPerMinute: Double
    if totalDuration > 0, !timedRecords.isEmpty {
      let typedCharacters = timedRecords.reduce(0) {
        $0 + $1.charactersPerMinute * ($1.activeDuration / 60)
      }
      averageCharactersPerMinute = typedCharacters / (totalDuration / 60)
    } else {
      averageCharactersPerMinute =
        records.reduce(0) { $0 + $1.charactersPerMinute } / Double(records.count)
    }
    return Metrics(
      totalDuration: totalDuration,
      averageAccuracy: averageAccuracy,
      averageCharactersPerMinute: averageCharactersPerMinute
    )
  }

  private static func change(current: Double?, previous: Double?) -> Double? {
    guard let current, let previous else { return nil }
    return current - previous
  }
}

struct GameRecordSaveOutcome: Equatable {
  let record: GameRecord
  let previousBestScore: Int?
  let isNewBest: Bool
  let deckProgress: DeckProgress
}

enum GameProgressStoreError: Error, Equatable {
  case unsupportedSchema(Int)
  case invalidProgress
}

struct GameProgressStore {
  private struct Index: Codable {
    let schemaVersion: Int
    var records: [GameRecord]
    var deckProgress: [DeckProgress]

    private enum CodingKeys: String, CodingKey {
      case schemaVersion = "schema_version"
      case records
      case deckProgress = "deck_progress"
    }
  }

  private static let currentSchemaVersion = 2
  static let maximumRetainedRecords = 2_048

  let rootURL: URL
  private let fileManager: FileManager

  init(rootURL: URL, fileManager: FileManager = .default) {
    self.rootURL = rootURL
    self.fileManager = fileManager
  }

  static var live: GameProgressStore {
    let applicationSupport = FileManager.default.urls(
      for: .applicationSupportDirectory,
      in: .userDomainMask
    )[0]
    return GameProgressStore(
      rootURL:
        applicationSupport
        .appendingPathComponent("Hanco", isDirectory: true)
        .appendingPathComponent("Progress", isDirectory: true)
    )
  }

  func loadSnapshot() throws -> GameProgressSnapshot {
    let snapshot = try RecoverableJSONFile.load(
      from: indexURL,
      fileManager: fileManager,
      shouldRecover: shouldRecover,
      decode: decodeSnapshot
    ) ?? .empty
    return Self.compacted(snapshot, asOf: Date())
  }

  private func decodeSnapshot(from data: Data) throws -> GameProgressSnapshot {
    let index = try decoder.decode(Index.self, from: data)
    guard (1...Self.currentSchemaVersion).contains(index.schemaVersion) else {
      throw GameProgressStoreError.unsupportedSchema(index.schemaVersion)
    }

    var progressByDeck: [String: DeckProgress] = [:]
    for progress in index.deckProgress {
      guard !progress.deckId.isEmpty,
        progress.plays >= 0,
        progress.bestScore >= 0,
        progress.bestAccuracy.isFinite,
        (0...100).contains(progress.bestAccuracy),
        progressByDeck[progress.id] == nil
      else { throw GameProgressStoreError.invalidProgress }
      progressByDeck[progress.id] = progress
    }
    guard Set(index.records.map(\.id)).count == index.records.count,
      index.records.allSatisfy({ record in
        !record.deckId.isEmpty && !record.course.isEmpty && record.score >= 0
          && record.maxCombo >= 0 && record.accuracy.isFinite
          && (0...100).contains(record.accuracy)
          && record.charactersPerMinute.isFinite && record.charactersPerMinute >= 0
          && record.activeDuration.isFinite && record.activeDuration >= 0
          && record.completedItemCount >= 0 && record.missedItemCount >= 0
      })
    else { throw GameProgressStoreError.invalidProgress }
    return GameProgressSnapshot(records: index.records, deckProgress: progressByDeck)
  }

  @discardableResult
  func append(_ record: GameRecord) throws -> GameRecordSaveOutcome {
    var snapshot = try loadSnapshot()
    let outcome = try Self.applyGameRecord(record, to: &snapshot)
    snapshot = Self.compacted(snapshot, asOf: Date())
    try write(snapshot)
    return outcome
  }

  func appendLesson(_ record: GameRecord) throws {
    var snapshot = try loadSnapshot()
    try Self.applyLessonRecord(record, to: &snapshot)
    snapshot = Self.compacted(snapshot, asOf: Date())
    try write(snapshot)
  }

  func reset() throws {
    if fileManager.fileExists(atPath: rootURL.path) {
      try fileManager.removeItem(at: rootURL)
    }
  }

  private var indexURL: URL {
    rootURL.appendingPathComponent("game-progress.json")
  }

  private var decoder: JSONDecoder {
    let decoder = JSONDecoder()
    decoder.dateDecodingStrategy = .iso8601
    return decoder
  }

  private var encoder: JSONEncoder {
    let encoder = JSONEncoder()
    encoder.dateEncodingStrategy = .iso8601
    encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
    return encoder
  }

  fileprivate func persistSnapshot(_ snapshot: GameProgressSnapshot) throws {
    // Validate the generation currently on disk before replacing it so that a
    // future-schema primary or backup is never silently downgraded.
    _ = try loadSnapshot()
    try write(Self.compacted(snapshot, asOf: Date()))
  }

  private func write(_ snapshot: GameProgressSnapshot) throws {
    try fileManager.createDirectory(at: rootURL, withIntermediateDirectories: true)
    let index = Index(
      schemaVersion: Self.currentSchemaVersion,
      records: snapshot.records,
      deckProgress: snapshot.deckProgress.values.sorted {
        ($0.deckId, $0.gameKind.rawValue, $0.inputMode.rawValue)
          < ($1.deckId, $1.gameKind.rawValue, $1.inputMode.rawValue)
      }
    )
    try RecoverableJSONFile.write(
      encoder.encode(index),
      to: indexURL,
      fileManager: fileManager
    )
  }

  private func shouldRecover(_ error: Error) -> Bool {
    guard let error = error as? GameProgressStoreError else { return true }
    switch error {
    case .unsupportedSchema: return false
    case .invalidProgress: return true
    }
  }

  fileprivate static func applyGameRecord(
    _ record: GameRecord,
    to snapshot: inout GameProgressSnapshot
  ) throws -> GameRecordSaveOutcome {
    try validate(record)
    let progressKey = GameProgressSnapshot.progressKey(
      deckId: record.deckId,
      gameKind: GameKind(course: record.course),
      inputMode: record.inputMode
    )
    let previous = snapshot.deckProgress[progressKey]
    let previousBestScore = previous?.bestScore
    let isNewBest = previousBestScore == nil || record.score > (previousBestScore ?? 0)
    let progress = DeckProgress(
      deckId: record.deckId,
      gameKind: GameKind(course: record.course),
      inputMode: record.inputMode,
      plays: (previous?.plays ?? 0) + 1,
      bestScore: max(previousBestScore ?? record.score, record.score),
      bestAccuracy: max(previous?.bestAccuracy ?? record.accuracy, record.accuracy),
      lastPlayedAt: record.playedAt
    )

    snapshot.records.append(record)
    snapshot.deckProgress[progressKey] = progress
    return GameRecordSaveOutcome(
      record: record,
      previousBestScore: previousBestScore,
      isNewBest: isNewBest,
      deckProgress: progress
    )
  }

  fileprivate static func applyLessonRecord(
    _ record: GameRecord,
    to snapshot: inout GameProgressSnapshot
  ) throws {
    guard record.mode == .lesson else { throw GameProgressStoreError.invalidProgress }
    try validate(record)
    snapshot.records.append(record)
  }

  static func compacted(
    _ snapshot: GameProgressSnapshot,
    asOf date: Date,
    limit: Int = maximumRetainedRecords
  ) -> GameProgressSnapshot {
    guard limit > 0, snapshot.records.count > limit else { return snapshot }

    // DeckProgress is the durable, unbounded summary for every deck/game/input key.
    // Keep raw records bounded while retaining the representative needed to rebuild
    // every current Game Center all-time score and the active weekly cup score.
    var bestByLeaderboard: [GameCenterLeaderboard: GameRecord] = [:]
    for record in snapshot.records {
      for leaderboard in GameCenterLeaderboard.leaderboards(for: record) {
        if leaderboard == .weeklyPiyoCup,
          !PiyoCupWeek.contains(record.playedAt, inWeekContaining: date)
        {
          continue
        }
        if let existing = bestByLeaderboard[leaderboard],
          existing.score > record.score
            || (existing.score == record.score && existing.playedAt >= record.playedAt)
        {
          continue
        }
        bestByLeaderboard[leaderboard] = record
      }
    }

    var retainedIDs = Set(bestByLeaderboard.values.map(\.id))
    for record in snapshot.records.reversed() where retainedIDs.count < limit {
      retainedIDs.insert(record.id)
    }

    var result = snapshot
    result.records = snapshot.records.filter { retainedIDs.contains($0.id) }
    return result
  }

  private static func validate(_ record: GameRecord) throws {
    guard !record.deckId.isEmpty, !record.course.isEmpty, record.score >= 0,
      record.maxCombo >= 0, record.accuracy.isFinite,
      (0...100).contains(record.accuracy), record.charactersPerMinute.isFinite,
      record.charactersPerMinute >= 0, record.activeDuration.isFinite,
      record.activeDuration >= 0,
      record.completedItemCount >= 0, record.missedItemCount >= 0
    else { throw GameProgressStoreError.invalidProgress }
  }
}

private actor GameProgressWriter {
  private let store: GameProgressStore
  private var latestRequestedRevision = 0
  private var persistedRevision = 0

  init(store: GameProgressStore) {
    self.store = store
  }

  func persist(snapshot: GameProgressSnapshot, revision: Int) throws {
    guard revision >= latestRequestedRevision else { return }
    latestRequestedRevision = revision
    guard revision > persistedRevision else { return }
    try store.persistSnapshot(snapshot)
    persistedRevision = revision
  }
}

@MainActor
final class GameProgressLibrary: ObservableObject {
  @Published private(set) var records: [GameRecord] = []
  @Published private(set) var deckProgress: [String: DeckProgress] = [:]
  @Published private(set) var saveFailed = false

  private let store: GameProgressStore
  private let writer: GameProgressWriter
  private var retryAction: (() -> Void)?
  private var revision = 0

  init(store: GameProgressStore = .live) {
    self.store = store
    writer = GameProgressWriter(store: store)
    reload()
  }

  @discardableResult
  func append(_ record: GameRecord) -> GameRecordSaveOutcome? {
    var snapshot = currentSnapshot
    do {
      let outcome = try GameProgressStore.applyGameRecord(record, to: &snapshot)
      accept(GameProgressStore.compacted(snapshot, asOf: Date()))
      flush()
      return outcome
    } catch {
      saveFailed = true
      retryAction = { [weak self] in _ = self?.append(record) }
      return nil
    }
  }

  @discardableResult
  func appendLesson(_ record: GameRecord) -> Bool {
    var snapshot = currentSnapshot
    do {
      try GameProgressStore.applyLessonRecord(record, to: &snapshot)
      accept(GameProgressStore.compacted(snapshot, asOf: Date()))
      flush()
      return true
    } catch {
      saveFailed = true
      retryAction = { [weak self] in _ = self?.appendLesson(record) }
      return false
    }
  }

  func progress(
    for deckId: String,
    gameKind: GameKind = .flow,
    inputMode: SessionInputMode = .builtIn
  ) -> DeckProgress? {
    deckProgress[
      GameProgressSnapshot.progressKey(
        deckId: deckId,
        gameKind: gameKind,
        inputMode: inputMode
      )
    ]
  }

  func retryLastSave() {
    retryAction?()
  }

  func flush() {
    let snapshot = currentSnapshot
    let requestedRevision = revision
    Task { @MainActor [weak self] in
      _ = await self?.persist(snapshot: snapshot, revision: requestedRevision)
    }
  }

  @discardableResult
  func flushAndWait() async -> Bool {
    await persist(snapshot: currentSnapshot, revision: revision)
  }

  private var currentSnapshot: GameProgressSnapshot {
    GameProgressSnapshot(records: records, deckProgress: deckProgress)
  }

  private func accept(_ snapshot: GameProgressSnapshot) {
    revision += 1
    records = snapshot.records
    deckProgress = snapshot.deckProgress
  }

  private func persist(snapshot: GameProgressSnapshot, revision requestedRevision: Int) async
    -> Bool
  {
    do {
      try await writer.persist(snapshot: snapshot, revision: requestedRevision)
      if requestedRevision == revision {
        saveFailed = false
        retryAction = nil
      }
      return true
    } catch {
      if requestedRevision == revision {
        saveFailed = true
        retryAction = { [weak self] in self?.flush() }
      }
      return false
    }
  }

  private func reload() {
    do {
      let snapshot = try store.loadSnapshot()
      records = snapshot.records
      deckProgress = snapshot.deckProgress
      revision = 0
      saveFailed = false
      retryAction = nil
    } catch {
      records = []
      deckProgress = [:]
      saveFailed = true
      retryAction = { [weak self] in self?.reload() }
    }
  }
}
