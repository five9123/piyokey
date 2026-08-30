import Combine
import Foundation

enum OnboardingGoal: String, Codable, CaseIterable, Identifiable {
  case keyboard
  case travel
  case topik
  case trends

  var id: Self { self }

  var preferredTags: [String] {
    switch self {
    case .keyboard: ["入門", "キーボード", "子音", "母音"]
    case .travel: ["韓国旅行", "旅行", "日常"]
    case .topik: ["TOPIK", "検定", "基礎単語"]
    case .trends: ["今どき", "SNS", "日常"]
    }
  }

  var titleKey: String { "onboarding.goal.\(rawValue).title" }
  var detailKey: String { "onboarding.goal.\(rawValue).detail" }

  var symbol: String {
    switch self {
    case .keyboard: "keyboard.fill"
    case .travel: "airplane"
    case .topik: "graduationcap.fill"
    case .trends: "bubble.left.and.bubble.right.fill"
    }
  }

  init(from decoder: Decoder) throws {
    let container = try decoder.singleValueContainer()
    let rawValue = try container.decode(String.self)
    switch rawValue {
    case Self.keyboard.rawValue, "casual": self = .keyboard
    case Self.travel.rawValue: self = .travel
    case Self.topik.rawValue: self = .topik
    case Self.trends.rawValue, "oshi": self = .trends
    default:
      throw DecodingError.dataCorruptedError(
        in: container,
        debugDescription: "Unsupported onboarding goal: \(rawValue)"
      )
    }
  }

  func encode(to encoder: Encoder) throws {
    var container = encoder.singleValueContainer()
    try container.encode(rawValue)
  }
}

enum OnboardingLevel: String, Codable, CaseIterable, Identifiable {
  case beginner, jamo, words, sentences

  var id: Self { self }
  var titleKey: String { "onboarding.level.\(rawValue).title" }
  var detailKey: String { "onboarding.level.\(rawValue).detail" }

  func recommendationRank(level: Int, tags: [String], isSentence: Bool) -> Int {
    switch self {
    case .beginner:
      return abs(level - 1) * 10 + (tags.contains("入門") ? 0 : 1)
    case .jamo:
      return abs(level - 1) * 10 + (!tags.contains("入門") && !isSentence ? 0 : 1)
    case .words:
      return abs(level - 2) * 10
    case .sentences:
      return max(0, level - 3) * 10 + (isSentence && level >= 2 ? 0 : 1)
    }
  }
}

enum OnboardingStep: Int, Codable, CaseIterable {
  case goal = 1
  // Preserve stored step IDs from the original three-step introduction.
  case level = 4
  case keyboard = 2
  case lesson = 3

  var position: Int {
    switch self {
    case .goal: 1
    case .level: 2
    case .keyboard: 3
    case .lesson: 4
    }
  }
}

struct OnboardingSnapshot: Codable, Equatable {
  let schemaVersion: Int
  var isCompleted: Bool
  var wasSkipped: Bool
  var selectedGoal: OnboardingGoal?
  var step: OnboardingStep
  var selectedLevel: OnboardingLevel? = nil

  static let empty = OnboardingSnapshot(
    schemaVersion: 1,
    isCompleted: false,
    wasSkipped: false,
    selectedGoal: nil,
    step: .goal
  )

  private enum CodingKeys: String, CodingKey {
    case schemaVersion = "schema_version"
    case isCompleted = "is_completed"
    case wasSkipped = "was_skipped"
    case selectedGoal = "selected_goal"
    case selectedLevel = "selected_level"
    case step
  }
}

enum OnboardingStoreError: Error, Equatable {
  case unsupportedSchema(Int)
}

struct OnboardingStore {
  static let stateKey = "onboarding.state"
  static let stateBackupKey = "onboarding.state.backup"
  static let stateCorruptKey = "onboarding.state.corrupt"
  static let appTourCompletedKey = "onboarding.app_tour.completed"
  static let homeLearningStartedKey = "onboarding.home_learning_started"
  static let notificationPermissionRequestedKey =
    "onboarding.notification_permission_requested"
  private static let currentSchemaVersion = 1

  private let defaults: UserDefaults

  init(defaults: UserDefaults = .standard) {
    self.defaults = defaults
  }

  var hasStoredState: Bool {
    defaults.data(forKey: Self.stateKey) != nil
      || defaults.data(forKey: Self.stateBackupKey) != nil
  }

  func load() throws -> OnboardingSnapshot {
    guard let data = defaults.data(forKey: Self.stateKey) else {
      guard let backup = defaults.data(forKey: Self.stateBackupKey) else { return .empty }
      let snapshot = try decode(backup)
      defaults.set(backup, forKey: Self.stateKey)
      return snapshot
    }
    do {
      let snapshot = try decode(data)
      if defaults.data(forKey: Self.stateBackupKey) == nil {
        defaults.set(data, forKey: Self.stateBackupKey)
      }
      return snapshot
    } catch {
      let primaryError = error
      if primaryError is OnboardingStoreError { throw primaryError }
      guard let backup = defaults.data(forKey: Self.stateBackupKey) else {
        defaults.set(data, forKey: Self.stateCorruptKey)
        defaults.removeObject(forKey: Self.stateKey)
        throw primaryError
      }
      do {
        let snapshot = try decode(backup)
        defaults.set(data, forKey: Self.stateCorruptKey)
        defaults.set(backup, forKey: Self.stateKey)
        return snapshot
      } catch {
        defaults.set(data, forKey: Self.stateCorruptKey)
        defaults.removeObject(forKey: Self.stateKey)
        defaults.removeObject(forKey: Self.stateBackupKey)
        throw primaryError
      }
    }
  }

  private func decode(_ data: Data) throws -> OnboardingSnapshot {
    let snapshot = try JSONDecoder().decode(OnboardingSnapshot.self, from: data)
    guard snapshot.schemaVersion == Self.currentSchemaVersion else {
      throw OnboardingStoreError.unsupportedSchema(snapshot.schemaVersion)
    }
    return snapshot
  }

  func save(_ snapshot: OnboardingSnapshot) throws {
    let data = try JSONEncoder().encode(snapshot)
    defaults.set(data, forKey: Self.stateKey)
    defaults.set(data, forKey: Self.stateBackupKey)
  }

  func reset() {
    defaults.removeObject(forKey: Self.stateKey)
    defaults.removeObject(forKey: Self.stateBackupKey)
    defaults.removeObject(forKey: Self.stateCorruptKey)
    defaults.removeObject(forKey: Self.appTourCompletedKey)
    defaults.removeObject(forKey: Self.homeLearningStartedKey)
    defaults.removeObject(forKey: Self.notificationPermissionRequestedKey)
  }
}

enum OnboardingLegacyDataDetector {
  static func hasExistingData(
    fileManager: FileManager = .default,
    defaults: UserDefaults = .standard
  ) -> Bool {
    if KeyboardPreferenceKeys.all.contains(where: { defaults.object(forKey: $0) != nil }) {
      return true
    }
    guard let applicationSupport = fileManager.urls(
      for: .applicationSupportDirectory,
      in: .userDomainMask
    ).first else { return false }
    let root = applicationSupport.appendingPathComponent("Hanco", isDirectory: true)
    guard let enumerator = fileManager.enumerator(
      at: root,
      includingPropertiesForKeys: [.isRegularFileKey]
    ) else { return false }
    for case let fileURL as URL in enumerator {
      if (try? fileURL.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true {
        return true
      }
    }
    return false
  }
}

@MainActor
final class OnboardingLibrary: ObservableObject {
  @Published private(set) var snapshot: OnboardingSnapshot
  @Published private(set) var saveFailed = false

  private let store: OnboardingStore
  private var retryAction: (() -> Void)?

  init(
    store: OnboardingStore = OnboardingStore(),
    hasLegacyData: () -> Bool = { OnboardingLegacyDataDetector.hasExistingData() }
  ) {
    self.store = store
    if !store.hasStoredState, hasLegacyData() {
      var migrated = OnboardingSnapshot.empty
      migrated.isCompleted = true
      migrated.wasSkipped = true
      try? store.save(migrated)
    }
    self.snapshot = (try? store.load()) ?? .empty
  }

  var shouldPresent: Bool {
    #if DEBUG
      if ProcessInfo.processInfo.environment["UITEST_FORCE_ONBOARDING"] == "1" {
        return !snapshot.isCompleted || snapshot.wasSkipped
      }
      if ProcessInfo.processInfo.environment["UITEST_SKIP_ONBOARDING"] == "1" {
        return false
      }
    #endif
    return !snapshot.isCompleted
  }

  var selectedGoal: OnboardingGoal? { snapshot.selectedGoal }
  var selectedLevel: OnboardingLevel? { snapshot.selectedLevel }
  var preferredTags: [String] { snapshot.selectedGoal?.preferredTags ?? [] }

  func select(_ goal: OnboardingGoal) {
    mutate { $0.selectedGoal = goal }
  }

  func selectLevel(_ level: OnboardingLevel) {
    mutate { $0.selectedLevel = level }
  }

  func move(to step: OnboardingStep) {
    mutate { $0.step = step }
  }

  func complete(skipped: Bool) {
    mutate {
      $0.isCompleted = true
      $0.wasSkipped = skipped
    }
  }

  func retryLastSave() {
    retryAction?()
  }

  private func mutate(_ update: @escaping (inout OnboardingSnapshot) -> Void) {
    var updated = snapshot
    update(&updated)
    do {
      try store.save(updated)
      snapshot = updated
      saveFailed = false
      retryAction = nil
    } catch {
      saveFailed = true
      retryAction = { [weak self] in self?.mutate(update) }
    }
  }
}
