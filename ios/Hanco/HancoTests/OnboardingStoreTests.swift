import DeckKit
import XCTest

@testable import Hanco

final class OnboardingStoreTests: XCTestCase {
  func testStorePersistsGoalStepAndCompletion() throws {
    let defaults = makeDefaults()
    defer { clear(defaults) }
    let store = OnboardingStore(defaults: defaults)
    var snapshot = OnboardingSnapshot.empty
    snapshot.selectedGoal = .travel
    snapshot.selectedLevel = .words
    snapshot.step = .lesson
    snapshot.isCompleted = true

    try store.save(snapshot)

    XCTAssertEqual(try store.load(), snapshot)
    XCTAssertTrue(store.hasStoredState)
  }

  @MainActor
  func testLibraryPersistsSkippedCompletion() {
    let defaults = makeDefaults()
    defer { clear(defaults) }
    let library = OnboardingLibrary(
      store: OnboardingStore(defaults: defaults),
      hasLegacyData: { false }
    )

    library.complete(skipped: true)

    XCTAssertFalse(library.shouldPresent)
    XCTAssertTrue(library.snapshot.isCompleted)
    XCTAssertTrue(library.snapshot.wasSkipped)
  }

  @MainActor
  func testLegacyUserMigratesWithoutSeeingOnboarding() throws {
    let defaults = makeDefaults()
    defer { clear(defaults) }
    let store = OnboardingStore(defaults: defaults)

    let library = OnboardingLibrary(store: store, hasLegacyData: { true })

    XCTAssertFalse(library.shouldPresent)
    XCTAssertTrue(library.snapshot.isCompleted)
    XCTAssertTrue(library.snapshot.wasSkipped)
    XCTAssertEqual(try store.load(), library.snapshot)
  }

  func testUnsupportedSchemaIsRejected() throws {
    let defaults = makeDefaults()
    defer { clear(defaults) }
    let store = OnboardingStore(defaults: defaults)
    let snapshot = OnboardingSnapshot(
      schemaVersion: 99,
      isCompleted: false,
      wasSkipped: false,
      selectedGoal: nil,
      step: .goal
    )
    defaults.set(try JSONEncoder().encode(snapshot), forKey: OnboardingStore.stateKey)

    XCTAssertThrowsError(try store.load()) { error in
      XCTAssertEqual(error as? OnboardingStoreError, .unsupportedSchema(99))
    }
  }

  func testResetAlsoClearsCompletedMainAppTourAndNotificationRequest() {
    let defaults = makeDefaults()
    defer { clear(defaults) }
    let store = OnboardingStore(defaults: defaults)
    defaults.set(true, forKey: OnboardingStore.appTourCompletedKey)
    defaults.set(true, forKey: OnboardingStore.homeLearningStartedKey)
    defaults.set(true, forKey: OnboardingStore.notificationPermissionRequestedKey)

    store.reset()

    XCTAssertFalse(defaults.bool(forKey: OnboardingStore.appTourCompletedKey))
    XCTAssertFalse(defaults.bool(forKey: OnboardingStore.homeLearningStartedKey))
    XCTAssertFalse(defaults.bool(forKey: OnboardingStore.notificationPermissionRequestedKey))
  }

  func testExistingCompletedAndInFlightSnapshotsKeepTheirStepWithoutLevel() throws {
    for step in [1, 2, 3] {
      for completed in [false, true] {
        let data = Data("{\"schema_version\":1,\"is_completed\":\(completed),\"was_skipped\":false,\"selected_goal\":\"travel\",\"step\":\(step)}".utf8)
        let snapshot = try JSONDecoder().decode(OnboardingSnapshot.self, from: data)
        XCTAssertNil(snapshot.selectedLevel)
        XCTAssertEqual(snapshot.step.rawValue, step)
        XCTAssertEqual(snapshot.isCompleted, completed)
      }
    }
    XCTAssertEqual(OnboardingStep.allCases.map(\.position), [1, 2, 3, 4])
  }

  @MainActor
  func testLevelSelectionAndStepSurviveRelaunchAndSkip() {
    let defaults = makeDefaults()
    defer { clear(defaults) }
    let store = OnboardingStore(defaults: defaults)
    let library = OnboardingLibrary(store: store, hasLegacyData: { false })
    library.select(.trends)
    library.selectLevel(.sentences)
    library.move(to: .level)
    let restored = OnboardingLibrary(store: store, hasLegacyData: { false })
    XCTAssertEqual(restored.selectedGoal, .trends)
    XCTAssertEqual(restored.selectedLevel, .sentences)
    XCTAssertEqual(restored.snapshot.step, .level)
    restored.complete(skipped: true)
    XCTAssertEqual(restored.selectedLevel, .sentences)
    XCTAssertFalse(restored.shouldPresent)
  }

  func testLegacyGoalValuesMigrateToCurrentLaunchGoals() throws {
    XCTAssertEqual(
      try JSONDecoder().decode(OnboardingGoal.self, from: Data(#""casual""#.utf8)),
      .keyboard
    )
    XCTAssertEqual(
      try JSONDecoder().decode(OnboardingGoal.self, from: Data(#""oshi""#.utf8)),
      .trends
    )
  }

  func testLegacyPrimarySeedsBackupAfterFirstValidatedLoad() throws {
    let defaults = makeDefaults()
    defer { clear(defaults) }
    let snapshot = OnboardingSnapshot.empty
    defaults.set(try JSONEncoder().encode(snapshot), forKey: OnboardingStore.stateKey)

    XCTAssertEqual(try OnboardingStore(defaults: defaults).load(), snapshot)
    XCTAssertEqual(
      defaults.data(forKey: OnboardingStore.stateKey),
      defaults.data(forKey: OnboardingStore.stateBackupKey)
    )
  }

  func testCorruptPrimaryRestoresCompletedOnboardingFromBackup() throws {
    let defaults = makeDefaults()
    defer { clear(defaults) }
    let store = OnboardingStore(defaults: defaults)
    var snapshot = OnboardingSnapshot.empty
    snapshot.isCompleted = true
    snapshot.selectedGoal = .trends
    try store.save(snapshot)
    defaults.set(Data("broken".utf8), forKey: OnboardingStore.stateKey)

    let recovered = try store.load()

    XCTAssertTrue(recovered.isCompleted)
    XCTAssertEqual(recovered.selectedGoal, .trends)
    XCTAssertNotNil(defaults.data(forKey: OnboardingStore.stateCorruptKey))
    XCTAssertEqual(
      defaults.data(forKey: OnboardingStore.stateKey),
      defaults.data(forKey: OnboardingStore.stateBackupKey)
    )
  }

  func testOnboardingRecommendationsReturnThreeOfficialGoalBasedDecks() throws {
    let catalog = try BundleCatalogRepository().loadCatalog()

    let recommendations = DeckRecommendationEngine.onboardingRecommendations(
      catalog: catalog,
      preferredTags: OnboardingGoal.travel.preferredTags,
      installedDeckIDs: []
    )

    XCTAssertEqual(recommendations.count, 3)
    XCTAssertTrue(recommendations.allSatisfy(\.official))
    XCTAssertFalse(Set(recommendations[0].tags).isDisjoint(with: OnboardingGoal.travel.preferredTags))
  }

  func testGoalPreferencePersonalizesHomeColdStart() throws {
    let catalog = try BundleCatalogRepository().loadCatalog()

    let recommendations = DeckRecommendationEngine.homeRecommendations(
      catalog: catalog,
      downloadHistory: [:],
      installedDeckIDs: [],
      preferredTags: OnboardingGoal.topik.preferredTags
    )

    XCTAssertEqual(recommendations.count, 3)
    XCTAssertFalse(
      Set(recommendations[0].tags).isDisjoint(with: OnboardingGoal.topik.preferredTags)
    )
  }

  private func makeDefaults() -> UserDefaults {
    let suite = "OnboardingStoreTests.\(UUID().uuidString)"
    guard let defaults = UserDefaults(suiteName: suite) else {
      fatalError("Could not create isolated UserDefaults")
    }
    return defaults
  }

  private func clear(_ defaults: UserDefaults) {
    defaults.dictionaryRepresentation().keys.forEach(defaults.removeObject(forKey:))
  }
}
