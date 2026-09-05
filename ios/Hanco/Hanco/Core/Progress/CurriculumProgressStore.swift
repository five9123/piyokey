import Combine
import Foundation

enum CurriculumStarRating {
  static let clearAccuracy = 80.0

  static func stars(accuracy: Double, charactersPerMinute: Double) -> Int {
    guard accuracy >= clearAccuracy else { return 0 }
    if accuracy >= 97, charactersPerMinute >= 60 { return 3 }
    if accuracy >= 90, charactersPerMinute >= 40 { return 2 }
    return 1
  }
}

struct PracticeSessionCheckpoint: Codable, Equatable {
  let currentTargetIndex: Int
  let acceptedKeys: String
  let mistakeCount: Int
  let currentTargetMistakeCount: Int
  let currentTargetMistakenJamoIndices: [Int]
  let activeDuration: TimeInterval

  private enum CodingKeys: String, CodingKey {
    case currentTargetIndex = "current_target_index"
    case acceptedKeys = "accepted_keys"
    case mistakeCount = "mistake_count"
    case currentTargetMistakeCount = "current_target_mistake_count"
    case currentTargetMistakenJamoIndices = "current_target_mistaken_jamo_indices"
    case activeDuration = "active_duration"
  }
}

struct CurriculumActiveSession: Codable, Equatable {
  let stageId: String
  let checkpoint: PracticeSessionCheckpoint
  let updatedAt: Date

  private enum CodingKeys: String, CodingKey {
    case stageId = "stage_id"
    case checkpoint
    case updatedAt = "updated_at"
  }
}

struct CurriculumStageProgress: Codable, Equatable, Identifiable {
  let stageId: String
  var stars: Int
  var bestAccuracy: Double
  let completedAt: Date

  var id: String { stageId }

  private enum CodingKeys: String, CodingKey {
    case stageId = "stage_id"
    case stars
    case bestAccuracy = "best_accuracy"
    case completedAt = "completed_at"
  }
}

struct CurriculumProgressSnapshot: Equatable {
  var stageProgress: [String: CurriculumStageProgress]
  var activeSession: CurriculumActiveSession?

  static let empty = CurriculumProgressSnapshot(stageProgress: [:], activeSession: nil)
}

enum CurriculumProgressMutation: Equatable {
  case saved
  case cleared
  case completed
  case unchanged
}

enum CurriculumProgressStoreError: Error, Equatable {
  case unsupportedSchema(Int)
  case invalidProgress
}

struct CurriculumProgressStore {
  private struct Index: Codable {
    let schemaVersion: Int
    var stageProgress: [CurriculumStageProgress]
    var activeSession: CurriculumActiveSession?

    private enum CodingKeys: String, CodingKey {
      case schemaVersion = "schema_version"
      case stageProgress = "stage_progress"
      case activeSession = "active_session"
    }
  }

  private static let currentSchemaVersion = 1

  let rootURL: URL
  private let fileManager: FileManager

  init(rootURL: URL, fileManager: FileManager = .default) {
    self.rootURL = rootURL
    self.fileManager = fileManager
  }

  static var live: CurriculumProgressStore {
    let applicationSupport = FileManager.default.urls(
      for: .applicationSupportDirectory,
      in: .userDomainMask
    )[0]
    return CurriculumProgressStore(
      rootURL:
        applicationSupport
        .appendingPathComponent("Hanco", isDirectory: true)
        .appendingPathComponent("Curriculum", isDirectory: true)
    )
  }

  func loadSnapshot() throws -> CurriculumProgressSnapshot {
    try RecoverableJSONFile.load(
      from: indexURL,
      fileManager: fileManager,
      shouldRecover: shouldRecover,
      decode: decodeSnapshot
    ) ?? .empty
  }

  private func decodeSnapshot(from data: Data) throws -> CurriculumProgressSnapshot {
    let index = try decoder.decode(Index.self, from: data)
    guard index.schemaVersion == Self.currentSchemaVersion else {
      throw CurriculumProgressStoreError.unsupportedSchema(index.schemaVersion)
    }

    var progress: [String: CurriculumStageProgress] = [:]
    for item in index.stageProgress {
      guard (1...3).contains(item.stars), (80...100).contains(item.bestAccuracy) else {
        throw CurriculumProgressStoreError.invalidProgress
      }
      progress[item.stageId] = item
    }
    return CurriculumProgressSnapshot(
      stageProgress: progress,
      activeSession: index.activeSession
    )
  }

  @discardableResult
  func saveActiveSession(
    stageId: String,
    checkpoint: PracticeSessionCheckpoint,
    at date: Date = Date()
  ) throws -> CurriculumProgressMutation {
    var snapshot = try loadSnapshot()
    let mutation = Self.applySave(
      stageId: stageId,
      checkpoint: checkpoint,
      at: date,
      to: &snapshot
    )
    try write(snapshot)
    return mutation
  }

  @discardableResult
  func finishStage(
    stageId: String,
    stars: Int,
    accuracy: Double,
    at date: Date = Date()
  ) throws -> CurriculumProgressMutation {
    var snapshot = try loadSnapshot()
    let mutation = Self.applyFinish(
      stageId: stageId,
      stars: stars,
      accuracy: accuracy,
      at: date,
      to: &snapshot
    )
    try write(snapshot)
    return mutation
  }

  @discardableResult
  func clearActiveSession(stageId: String? = nil) throws -> CurriculumProgressMutation {
    var snapshot = try loadSnapshot()
    let mutation = Self.applyClear(stageId: stageId, to: &snapshot)
    guard mutation != .unchanged else { return mutation }
    try write(snapshot)
    return mutation
  }

  func reset() throws {
    if fileManager.fileExists(atPath: rootURL.path) {
      try fileManager.removeItem(at: rootURL)
    }
  }

  private var indexURL: URL { rootURL.appendingPathComponent("curriculum-progress.json") }

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

  fileprivate func persistSnapshot(_ snapshot: CurriculumProgressSnapshot) throws {
    // A background writer must still inspect the on-disk generation before replacing it.
    // In particular this preserves a future-schema primary/backup instead of downgrading it.
    _ = try loadSnapshot()
    try write(snapshot)
  }

  private func write(_ snapshot: CurriculumProgressSnapshot) throws {
    try fileManager.createDirectory(at: rootURL, withIntermediateDirectories: true)
    let index = Index(
      schemaVersion: Self.currentSchemaVersion,
      stageProgress: snapshot.stageProgress.values.sorted { $0.stageId < $1.stageId },
      activeSession: snapshot.activeSession
    )
    try RecoverableJSONFile.write(
      encoder.encode(index),
      to: indexURL,
      fileManager: fileManager
    )
  }

  private func shouldRecover(_ error: Error) -> Bool {
    guard let error = error as? CurriculumProgressStoreError else { return true }
    switch error {
    case .unsupportedSchema: return false
    case .invalidProgress: return true
    }
  }

  fileprivate static func applySave(
    stageId: String,
    checkpoint: PracticeSessionCheckpoint,
    at date: Date,
    to snapshot: inout CurriculumProgressSnapshot
  ) -> CurriculumProgressMutation {
    snapshot.activeSession = CurriculumActiveSession(
      stageId: stageId,
      checkpoint: checkpoint,
      updatedAt: date
    )
    return .saved
  }

  fileprivate static func applyFinish(
    stageId: String,
    stars: Int,
    accuracy: Double,
    at date: Date,
    to snapshot: inout CurriculumProgressSnapshot
  ) -> CurriculumProgressMutation {
    if snapshot.activeSession?.stageId == stageId {
      snapshot.activeSession = nil
    }

    guard stars > 0, accuracy >= CurriculumStarRating.clearAccuracy else {
      return .cleared
    }

    if var existing = snapshot.stageProgress[stageId] {
      existing.stars = max(existing.stars, min(stars, 3))
      existing.bestAccuracy = max(existing.bestAccuracy, min(accuracy, 100))
      snapshot.stageProgress[stageId] = existing
    } else {
      snapshot.stageProgress[stageId] = CurriculumStageProgress(
        stageId: stageId,
        stars: min(stars, 3),
        bestAccuracy: min(accuracy, 100),
        completedAt: date
      )
    }
    return .completed
  }

  fileprivate static func applyClear(
    stageId: String?,
    to snapshot: inout CurriculumProgressSnapshot
  ) -> CurriculumProgressMutation {
    guard let active = snapshot.activeSession,
      stageId == nil || active.stageId == stageId
    else { return .unchanged }
    snapshot.activeSession = nil
    return .cleared
  }
}

private actor CurriculumProgressWriter {
  private let store: CurriculumProgressStore
  private var latestRequestedRevision = 0
  private var persistedRevision = 0

  init(store: CurriculumProgressStore) {
    self.store = store
  }

  func persist(snapshot: CurriculumProgressSnapshot, revision: Int) throws {
    guard revision >= latestRequestedRevision else { return }
    latestRequestedRevision = revision
    guard revision > persistedRevision else { return }
    try store.persistSnapshot(snapshot)
    persistedRevision = revision
  }

  func persistAndVerifyCompletion(
    snapshot: CurriculumProgressSnapshot,
    revision: Int,
    stageId: String
  ) throws -> Bool {
    guard revision >= latestRequestedRevision else { return false }
    latestRequestedRevision = revision
    try store.persistSnapshot(snapshot)
    let persisted = try store.loadSnapshot()
    guard persisted.activeSession?.stageId != stageId else { return false }
    let verified: Bool
    switch (snapshot.stageProgress[stageId], persisted.stageProgress[stageId]) {
    case (nil, nil):
      verified = true
    case (.some(let expected), .some(let actual)):
      verified = expected.stars == actual.stars
        && expected.bestAccuracy == actual.bestAccuracy
    default:
      verified = false
    }
    if verified {
      persistedRevision = max(persistedRevision, revision)
    }
    return verified
  }
}

@MainActor
final class CurriculumProgressLibrary: ObservableObject {
  @Published private(set) var stageProgress: [String: CurriculumStageProgress] = [:]
  @Published private(set) var activeSession: CurriculumActiveSession?
  @Published private(set) var saveFailed = false

  private let store: CurriculumProgressStore
  private let writer: CurriculumProgressWriter
  private let debounceNanoseconds: UInt64
  private var retryAction: (() -> Void)?
  private var debounceTask: Task<Void, Never>?
  private var revision = 0

  init(
    store: CurriculumProgressStore = .live,
    debounceNanoseconds: UInt64 = 750_000_000
  ) {
    self.store = store
    writer = CurriculumProgressWriter(store: store)
    self.debounceNanoseconds = debounceNanoseconds
    reload()
  }

  var completedStageIDs: Set<String> { Set(stageProgress.keys) }

  func progress(for stageId: String) -> CurriculumStageProgress? {
    stageProgress[stageId]
  }

  func checkpoint(for stageId: String) -> PracticeSessionCheckpoint? {
    activeSession?.stageId == stageId ? activeSession?.checkpoint : nil
  }

  func save(stageId: String, checkpoint: PracticeSessionCheckpoint) {
    var snapshot = currentSnapshot
    _ = CurriculumProgressStore.applySave(
      stageId: stageId,
      checkpoint: checkpoint,
      at: Date(),
      to: &snapshot
    )
    accept(snapshot)
    scheduleDebouncedFlush()
  }

  /// Source-compatible fire-and-forget completion. Call `finishAndWait` when the
  /// caller must gate navigation on durable persistence.
  func finish(stageId: String, stars: Int, accuracy: Double) {
    applyFinish(stageId: stageId, stars: stars, accuracy: accuracy)
    flush()
  }

  /// Applies completion in memory immediately and returns only after that exact
  /// (or a newer) snapshot has been durably written off the main actor.
  func finishAndWait(stageId: String, stars: Int, accuracy: Double) async -> Bool {
    let previousSnapshot = currentSnapshot
    applyFinish(stageId: stageId, stars: stars, accuracy: accuracy)
    let completionRevision = revision
    debounceTask?.cancel()
    debounceTask = nil
    let succeeded = await persistAndVerifyCompletion(
      snapshot: currentSnapshot,
      revision: completionRevision,
      stageId: stageId
    )
    guard !succeeded, completionRevision == revision else { return succeeded }

    // Completion unlocks curriculum navigation, so a failed durable write must not
    // leave an optimistic stage completion visible in memory. Keep a newer mutation
    // if one arrived while awaiting the writer; otherwise restore the exact prior state.
    revision += 1
    stageProgress = previousSnapshot.stageProgress
    activeSession = previousSnapshot.activeSession
    saveFailed = true
    retryAction = { [weak self] in
      self?.finish(stageId: stageId, stars: stars, accuracy: accuracy)
    }
    return false
  }

  func flush() {
    debounceTask?.cancel()
    debounceTask = nil
    let snapshot = currentSnapshot
    let requestedRevision = revision
    Task { @MainActor [weak self] in
      _ = await self?.persist(snapshot: snapshot, revision: requestedRevision)
    }
  }

  @discardableResult
  func flushAndWait() async -> Bool {
    debounceTask?.cancel()
    debounceTask = nil
    return await persist(snapshot: currentSnapshot, revision: revision)
  }

  func retryLastSave() {
    retryAction?()
  }

  private var currentSnapshot: CurriculumProgressSnapshot {
    CurriculumProgressSnapshot(stageProgress: stageProgress, activeSession: activeSession)
  }

  private func applyFinish(stageId: String, stars: Int, accuracy: Double) {
    var snapshot = currentSnapshot
    _ = CurriculumProgressStore.applyFinish(
      stageId: stageId,
      stars: stars,
      accuracy: accuracy,
      at: Date(),
      to: &snapshot
    )
    accept(snapshot)
  }

  private func accept(_ snapshot: CurriculumProgressSnapshot) {
    revision += 1
    stageProgress = snapshot.stageProgress
    activeSession = snapshot.activeSession
  }

  private func scheduleDebouncedFlush() {
    debounceTask?.cancel()
    let snapshot = currentSnapshot
    let requestedRevision = revision
    let delay = debounceNanoseconds
    debounceTask = Task { @MainActor [weak self] in
      do {
        try await Task.sleep(nanoseconds: delay)
      } catch {
        return
      }
      guard let self, requestedRevision == self.revision else { return }
      self.debounceTask = nil
      _ = await self.persist(snapshot: snapshot, revision: requestedRevision)
    }
  }

  private func persist(
    snapshot: CurriculumProgressSnapshot,
    revision requestedRevision: Int
  ) async -> Bool {
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

  private func persistAndVerifyCompletion(
    snapshot: CurriculumProgressSnapshot,
    revision requestedRevision: Int,
    stageId: String
  ) async -> Bool {
    do {
      let verified = try await writer.persistAndVerifyCompletion(
        snapshot: snapshot,
        revision: requestedRevision,
        stageId: stageId
      )
      guard verified else {
        if requestedRevision == revision {
          saveFailed = true
          retryAction = { [weak self] in self?.flush() }
        }
        return false
      }
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
      stageProgress = snapshot.stageProgress
      activeSession = snapshot.activeSession
      revision = 0
      saveFailed = false
      retryAction = nil
    } catch {
      stageProgress = [:]
      activeSession = nil
      saveFailed = true
      retryAction = { [weak self] in self?.reload() }
    }
  }
}
