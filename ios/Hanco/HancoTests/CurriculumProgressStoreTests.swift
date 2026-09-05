import HangulEngine
import XCTest

@testable import Hanco

final class CurriculumProgressStoreTests: XCTestCase {
  private var rootURL: URL!
  private var store: CurriculumProgressStore!

  override func setUpWithError() throws {
    rootURL = FileManager.default.temporaryDirectory
      .appendingPathComponent(UUID().uuidString, isDirectory: true)
    store = CurriculumProgressStore(rootURL: rootURL)
  }

  override func tearDownWithError() throws {
    if FileManager.default.fileExists(atPath: rootURL.path) {
      try FileManager.default.removeItem(at: rootURL)
    }
    store = nil
    rootURL = nil
  }

  func testBundledCurriculumHasExpandedWordAndPhraseStagesWithTenTypeableItemsEach() throws {
    XCTAssertEqual(CurriculumCatalog.chapters.count, 6)
    XCTAssertEqual(CurriculumCatalog.stages.count, 12)
    XCTAssertEqual(
      CurriculumCatalog.chapters[4].stages.map(\.id),
      ["chapter_5_words", "chapter_5_travel_words", "chapter_5_study_work_words"]
    )
    XCTAssertEqual(
      CurriculumCatalog.chapters[5].stages.map(\.id),
      [
        "chapter_6_spacing", "chapter_6_sentences", "chapter_6_travel_phrases",
        "chapter_6_daily_conversation", "chapter_6_fan_support",
      ]
    )

    for stage in CurriculumCatalog.stages {
      XCTAssertEqual(stage.items.count, 10, stage.id)
      for item in stage.items {
        XCTAssertFalse(try JamoDecomposer.keySequence(for: item.ko).isEmpty)
        XCTAssertNotEqual(item.deckItem.readingJa, item.readingKey)
        XCTAssertNotEqual(item.deckItem.meaningJa, item.meaningKey)
      }
    }
  }

  func testSpacingStagePracticesExactlyOneWordBoundaryPerTarget() throws {
    let stage = try XCTUnwrap(CurriculumCatalog.stage(id: "chapter_6_spacing"))

    XCTAssertEqual(stage.chapterNumber, 6)
    XCTAssertEqual(stage.stageNumber, 1)
    for item in stage.items {
      XCTAssertLessThanOrEqual(item.ko.count, 10)
      XCTAssertEqual(item.ko.filter { $0.isWhitespace }.count, 1, item.ko)
      let sequence = try JamoDecomposer.keySequence(for: item.ko)
      XCTAssertEqual(sequence.filter { $0.isWhitespace }.count, 1, item.ko)
    }
  }

  func testStarRatingUsesClearAccuracyAndSpeedGates() {
    XCTAssertEqual(CurriculumStarRating.stars(accuracy: 79.9, charactersPerMinute: 100), 0)
    XCTAssertEqual(CurriculumStarRating.stars(accuracy: 80, charactersPerMinute: 20), 1)
    XCTAssertEqual(CurriculumStarRating.stars(accuracy: 90, charactersPerMinute: 40), 2)
    XCTAssertEqual(CurriculumStarRating.stars(accuracy: 97, charactersPerMinute: 60), 3)
  }

  func testCoreStagesUnlockSequentiallyWhileChaptersFiveAndSixAreFree() {
    let stages = CurriculumCatalog.stages
    XCTAssertTrue(
      CurriculumUnlockPolicy.isUnlocked(stages[0], completedStageIDs: [], stages: stages)
    )
    XCTAssertFalse(
      CurriculumUnlockPolicy.isUnlocked(stages[1], completedStageIDs: [], stages: stages)
    )
    XCTAssertTrue(
      CurriculumUnlockPolicy.isUnlocked(
        stages[1],
        completedStageIDs: [stages[0].id],
        stages: stages
      )
    )
    for stage in stages where stage.chapterNumber >= 5 {
      XCTAssertTrue(
        CurriculumUnlockPolicy.isUnlocked(stage, completedStageIDs: [], stages: stages),
        stage.id
      )
    }
  }

  func testEitherFreeSelectionStageCompletesChapterSixWithoutRegressingExistingProgress() throws {
    let chapter = CurriculumCatalog.chapters[5]

    XCTAssertFalse(
      CurriculumChapterCompletionPolicy.isCompleted(chapter, completedStageIDs: [])
    )
    XCTAssertTrue(
      CurriculumChapterCompletionPolicy.isCompleted(
        chapter,
        completedStageIDs: ["chapter_6_spacing"]
      )
    )
    XCTAssertTrue(
      CurriculumChapterCompletionPolicy.isCompleted(
        chapter,
        completedStageIDs: ["chapter_6_sentences"]
      )
    )
  }

  func testHatchOnboardingRequiresExactlyTheFirstThreeStagesInOrder() {
    let stages = CurriculumCatalog.stages

    XCTAssertEqual(HatchOnboardingPolicy.requiredStages.map(\.id), stages.prefix(3).map(\.id))
    XCTAssertEqual(
      HatchOnboardingPolicy.nextRequiredStage(completedStageIDs: [])?.id,
      stages[0].id
    )
    XCTAssertEqual(
      HatchOnboardingPolicy.nextRequiredStage(completedStageIDs: [stages[0].id])?.id,
      stages[1].id
    )
    XCTAssertFalse(
      HatchOnboardingPolicy.isComplete(completedStageIDs: [stages[0].id, stages[1].id])
    )
    XCTAssertTrue(
      HatchOnboardingPolicy.isComplete(
        completedStageIDs: Set(stages.prefix(3).map(\.id))
      )
    )
  }

  func testHatchTransitionSerializesMissionOneCelebrationBeforeMissionTwoExactlyOnce() {
    var transition = HatchMissionTransitionCoordinator(activeStageID: "mission-1")

    XCTAssertEqual(
      transition.resultDidDismiss(
        completedStageID: "mission-1",
        nextStageID: "mission-2",
        pendingCelebration: .hatching
      ),
      .waitForPresentationTransition
    )
    XCTAssertEqual(
      transition.resultDidDismiss(
        completedStageID: "mission-1",
        nextStageID: "mission-2",
        pendingCelebration: .hatching
      ),
      .none
    )
    XCTAssertTrue(transition.ownsCelebration(for: "mission-1"))

    XCTAssertEqual(
      transition.presentationTransitionDidFinish(),
      .presentCelebration(.hatching)
    )
    XCTAssertEqual(transition.presentationTransitionDidFinish(), .none)
    XCTAssertEqual(transition.celebrationDidDismiss(), .advanceToMission("mission-2"))
    XCTAssertEqual(transition.celebrationDidDismiss(), .none)
    XCTAssertTrue(transition.missionDidActivate("mission-2"))
    XCTAssertFalse(transition.missionDidActivate("mission-2"))
    XCTAssertEqual(transition.state, .awaitingResultDismissal("mission-2"))
    XCTAssertEqual(
      transition.resultDidDismiss(
        completedStageID: "mission-1",
        nextStageID: "mission-2",
        pendingCelebration: nil
      ),
      .none
    )
  }

  func testHatchTransitionSerializesMissionTwoResultBeforeMissionThree() {
    var transition = HatchMissionTransitionCoordinator(activeStageID: "mission-2")

    XCTAssertEqual(
      transition.resultDidDismiss(
        completedStageID: "mission-2",
        nextStageID: "mission-3",
        pendingCelebration: nil
      ),
      .waitForPresentationTransition
    )
    XCTAssertEqual(
      transition.presentationTransitionDidFinish(),
      .advanceToMission("mission-3")
    )
    XCTAssertEqual(transition.presentationTransitionDidFinish(), .none)
    XCTAssertTrue(transition.missionDidActivate("mission-3"))
    XCTAssertEqual(transition.state, .awaitingResultDismissal("mission-3"))
  }

  func testHatchTransitionSerializesFinalCelebrationAndHomeExactlyOnce() {
    var transition = HatchMissionTransitionCoordinator(activeStageID: "mission-3")

    XCTAssertEqual(
      transition.resultDidDismiss(
        completedStageID: "mission-3",
        nextStageID: nil,
        pendingCelebration: .chick
      ),
      .waitForPresentationTransition
    )
    XCTAssertEqual(
      transition.presentationTransitionDidFinish(),
      .presentCelebration(.chick)
    )
    XCTAssertEqual(transition.celebrationDidDismiss(), .completeHatch)
    XCTAssertEqual(transition.celebrationDidDismiss(), .none)
    XCTAssertEqual(transition.presentationTransitionDidFinish(), .none)
    XCTAssertEqual(transition.state, .completed)
  }

  func testHatchTransitionFinalWithoutPendingCelebrationStillWaitsForResultPop() {
    var transition = HatchMissionTransitionCoordinator(activeStageID: "mission-3")

    XCTAssertEqual(
      transition.resultDidDismiss(
        completedStageID: "mission-3",
        nextStageID: nil,
        pendingCelebration: nil
      ),
      .waitForPresentationTransition
    )
    XCTAssertEqual(transition.presentationTransitionDidFinish(), .completeHatch)
    XCTAssertEqual(transition.presentationTransitionDidFinish(), .none)
  }

  func testActiveSessionRoundTripsAndFailedAttemptClearsWithoutCompletion() throws {
    let checkpoint = makeCheckpoint(index: 3, acceptedKeys: "ㄹ", duration: 12.5)
    try store.saveActiveSession(stageId: "stage", checkpoint: checkpoint)

    XCTAssertEqual(try store.loadSnapshot().activeSession?.checkpoint, checkpoint)
    XCTAssertEqual(
      try store.finishStage(stageId: "stage", stars: 0, accuracy: 79.9),
      .cleared
    )

    let snapshot = try store.loadSnapshot()
    XCTAssertNil(snapshot.activeSession)
    XCTAssertTrue(snapshot.stageProgress.isEmpty)
  }

  func testCompletionKeepsBestStarsAccuracyAndFirstCompletedDate() throws {
    let firstDate = Date(timeIntervalSince1970: 1_000)
    let secondDate = Date(timeIntervalSince1970: 2_000)
    try store.finishStage(stageId: "stage", stars: 2, accuracy: 91, at: firstDate)
    try store.finishStage(stageId: "stage", stars: 1, accuracy: 95, at: secondDate)
    try store.finishStage(stageId: "stage", stars: 3, accuracy: 98, at: secondDate)

    let progress = try XCTUnwrap(store.loadSnapshot().stageProgress["stage"])
    XCTAssertEqual(progress.stars, 3)
    XCTAssertEqual(progress.bestAccuracy, 98)
    XCTAssertEqual(progress.completedAt, firstDate)
  }

  func testUnsupportedSchemaIsRejected() throws {
    try FileManager.default.createDirectory(at: rootURL, withIntermediateDirectories: true)
    try Data("{\"schema_version\":99,\"stage_progress\":[]}".utf8)
      .write(to: rootURL.appendingPathComponent("curriculum-progress.json"))

    XCTAssertThrowsError(try store.loadSnapshot()) { error in
      XCTAssertEqual(error as? CurriculumProgressStoreError, .unsupportedSchema(99))
    }
  }

  func testInvalidProgressRestoresValidatedBackup() throws {
    let completedAt = Date(timeIntervalSince1970: 1_000)
    try store.finishStage(stageId: "stage", stars: 2, accuracy: 91, at: completedAt)
    let fileURL = rootURL.appendingPathComponent("curriculum-progress.json")
    try Data(
      """
      {"schema_version":1,"stage_progress":[{
        "stage_id":"stage","stars":9,"best_accuracy":101,
        "completed_at":"1970-01-01T00:16:40Z"
      }]}
      """.utf8
    ).write(to: fileURL)

    let recovered = try XCTUnwrap(store.loadSnapshot().stageProgress["stage"])

    XCTAssertEqual(recovered.stars, 2)
    XCTAssertEqual(recovered.bestAccuracy, 91)
    XCTAssertEqual(recovered.completedAt, completedAt)
    XCTAssertTrue(
      FileManager.default.fileExists(
        atPath: RecoverableJSONFile.corruptURL(for: fileURL).path
      )
    )
  }

  @MainActor
  func testLibraryCoalescesLatestCheckpointUntilExplicitFlush() async throws {
    let library = CurriculumProgressLibrary(
      store: store,
      debounceNanoseconds: 60_000_000_000
    )
    let first = makeCheckpoint(index: 0, acceptedKeys: "ㄱ", duration: 1)
    let latest = makeCheckpoint(index: 0, acceptedKeys: "ㄱㅏ", duration: 2)

    library.save(stageId: "stage", checkpoint: first)
    library.save(stageId: "stage", checkpoint: latest)

    XCTAssertEqual(library.checkpoint(for: "stage"), latest)
    XCTAssertFalse(
      FileManager.default.fileExists(
        atPath: rootURL.appendingPathComponent("curriculum-progress.json").path
      )
    )
    let persisted = await library.flushAndWait()
    XCTAssertTrue(persisted)
    XCTAssertEqual(try store.loadSnapshot().activeSession?.checkpoint, latest)
  }

  @MainActor
  func testFinishAndWaitRollsBackOptimisticCompletionWhenFutureSchemaBlocksWrite()
    async throws
  {
    try FileManager.default.createDirectory(at: rootURL, withIntermediateDirectories: true)
    let fileURL = rootURL.appendingPathComponent("curriculum-progress.json")
    let futureData = Data("{\"schema_version\":99,\"stage_progress\":[]}".utf8)
    try futureData.write(to: fileURL)
    let library = CurriculumProgressLibrary(store: store)

    let persisted = await library.finishAndWait(stageId: "stage", stars: 3, accuracy: 100)

    XCTAssertFalse(persisted)
    XCTAssertTrue(library.saveFailed)
    XCTAssertFalse(library.completedStageIDs.contains("stage"))
    XCTAssertEqual(try Data(contentsOf: fileURL), futureData)
  }

  @MainActor
  func testFinalHatchCompletionSurvivesImmediateLibraryReload() async throws {
    let requiredStages = HatchOnboardingPolicy.requiredStages
    XCTAssertEqual(requiredStages.count, 3)
    for stage in requiredStages.dropLast() {
      try store.finishStage(stageId: stage.id, stars: 3, accuracy: 100)
    }
    let library = CurriculumProgressLibrary(store: store)
    let finalStage = try XCTUnwrap(requiredStages.last)

    let persisted = await library.finishAndWait(
      stageId: finalStage.id,
      stars: 3,
      accuracy: 100
    )
    let relaunchedLibrary = CurriculumProgressLibrary(store: store)

    XCTAssertTrue(persisted)
    XCTAssertTrue(
      HatchOnboardingPolicy.isComplete(
        completedStageIDs: relaunchedLibrary.completedStageIDs
      )
    )
    XCTAssertNil(relaunchedLibrary.activeSession)
  }

  @MainActor
  func testSecondHatchCompletionSurvivesImmediateLibraryReloadAndResumesMissionThree()
    async throws
  {
    let requiredStages = HatchOnboardingPolicy.requiredStages
    XCTAssertEqual(requiredStages.count, 3)
    try store.finishStage(stageId: requiredStages[0].id, stars: 3, accuracy: 100)
    let library = CurriculumProgressLibrary(store: store)

    let persisted = await library.finishAndWait(
      stageId: requiredStages[1].id,
      stars: 3,
      accuracy: 100
    )
    let relaunchedLibrary = CurriculumProgressLibrary(store: store)

    XCTAssertTrue(persisted)
    XCTAssertEqual(
      HatchOnboardingPolicy.nextRequiredStage(
        completedStageIDs: relaunchedLibrary.completedStageIDs
      )?.id,
      requiredStages[2].id
    )
    XCTAssertNil(relaunchedLibrary.activeSession)
  }

  @MainActor
  func testFinishAndWaitVerifiesDurableFailedAttemptWithoutChangingScoring() async throws {
    let library = CurriculumProgressLibrary(store: store)
    library.save(
      stageId: "stage",
      checkpoint: makeCheckpoint(index: 0, acceptedKeys: "ㄱ", duration: 1)
    )

    let persisted = await library.finishAndWait(
      stageId: "stage",
      stars: 0,
      accuracy: 79
    )
    let relaunchedLibrary = CurriculumProgressLibrary(store: store)

    XCTAssertTrue(persisted)
    XCTAssertFalse(relaunchedLibrary.completedStageIDs.contains("stage"))
    XCTAssertNil(relaunchedLibrary.activeSession)
  }

  private func makeCheckpoint(
    index: Int,
    acceptedKeys: String,
    duration: TimeInterval
  ) -> PracticeSessionCheckpoint {
    PracticeSessionCheckpoint(
      currentTargetIndex: index,
      acceptedKeys: acceptedKeys,
      mistakeCount: 2,
      currentTargetMistakeCount: 1,
      currentTargetMistakenJamoIndices: [0],
      activeDuration: duration
    )
  }
}
