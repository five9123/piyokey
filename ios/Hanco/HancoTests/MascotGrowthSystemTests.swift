import XCTest
@testable import Hanco

@MainActor
final class MascotGrowthSystemTests: XCTestCase {
  func testIdlePettingPolicyBecomesShyOnThirdConsecutiveTap() {
    XCTAssertEqual(MascotPettingPolicy.singleTapMood(consecutiveTapCount: 1), .happy)
    XCTAssertEqual(MascotPettingPolicy.singleTapMood(consecutiveTapCount: 2), .happy)
    XCTAssertEqual(MascotPettingPolicy.singleTapMood(consecutiveTapCount: 3), .shy)
    XCTAssertEqual(MascotPettingPolicy.singleTapMood(consecutiveTapCount: 8), .shy)
    XCTAssertEqual(MascotPettingPolicy.doubleTapWindowNanoseconds, 280_000_000)
    XCTAssertEqual(MascotPettingPolicy.happyRevertNanoseconds, 900_000_000)
    XCTAssertEqual(MascotPettingPolicy.cheerRevertNanoseconds, 1_100_000_000)
  }

  func testMotionPolicyNeverTurnsEdgeOnOrLeavesItsPresentationSlot() {
    XCTAssertEqual(MascotMotionPolicy.horizontalOffset(100, size: 100), 4.5, accuracy: 0.001)
    XCTAssertEqual(MascotMotionPolicy.horizontalOffset(-100, size: 100), -4.5, accuracy: 0.001)
    XCTAssertEqual(MascotMotionPolicy.verticalOffset(-100, size: 100), -8.5, accuracy: 0.001)
    XCTAssertEqual(MascotMotionPolicy.verticalOffset(100, size: 100), 5, accuracy: 0.001)
    XCTAssertEqual(MascotMotionPolicy.reactionScale(1.5), 1.08, accuracy: 0.001)
    XCTAssertEqual(MascotMotionPolicy.capOffset(-100, size: 100), -10, accuracy: 0.001)

    for pose in [MascotPose.front, .threeQuarterLeft, .threeQuarterRight] {
      XCTAssertLessThan(
        abs(MascotMotionPolicy.yawDegrees(pose: pose, isLookingBack: true)),
        45,
        "A mascot must never become edge-on and disappear during an idle turn"
      )
    }
  }

  func testGrowthStagesFollowClearedChapterThresholds() {
    XCTAssertEqual(MascotStage.forClearedChapters(0), .cracking)
    XCTAssertEqual(MascotStage.forClearedChapters(1), .hatching)
    XCTAssertEqual(MascotStage.forClearedChapters(2), .hatching)
    XCTAssertEqual(MascotStage.forClearedChapters(3), .chick)
    XCTAssertEqual(MascotStage.forClearedChapters(5), .chick)
    XCTAssertEqual(MascotStage.forClearedChapters(6), .rooster)
  }

  func testGrowthCelebrationIsEmittedOncePerAdvancedStage() {
    let defaults = isolatedDefaults()
    let companion = MascotCompanionLibrary(defaults: defaults)

    XCTAssertNil(companion.pendingCelebration(current: .cracking))
    XCTAssertEqual(companion.pendingCelebration(current: .hatching), .hatching)

    companion.markCelebrated(.hatching)
    XCTAssertNil(companion.pendingCelebration(current: .hatching))
    XCTAssertEqual(companion.pendingCelebration(current: .chick), .chick)

    companion.markCelebrated(.chick)
    XCTAssertNil(companion.pendingCelebration(current: .chick))
    XCTAssertEqual(companion.pendingCelebration(current: .rooster), .rooster)
  }

  func testGrowthCelebrationDoesNotSkipHatchingOrRevealItEarly() {
    let defaults = isolatedDefaults()
    let companion = MascotCompanionLibrary(defaults: defaults)

    XCTAssertEqual(companion.presentedStage(current: .hatching), .cracking)
    XCTAssertEqual(companion.pendingCelebration(current: .chick), .hatching)

    companion.markCelebrated(.hatching)

    XCTAssertEqual(companion.presentedStage(current: .chick), .hatching)
    XCTAssertEqual(companion.pendingCelebration(current: .chick), .chick)
  }

  func testNameAndEquippedPropPersist() {
    let defaults = isolatedDefaults()
    var companion: MascotCompanionLibrary? = MascotCompanionLibrary(defaults: defaults)
    companion?.name = "초코"
    companion?.selection = .fixed(.headphones)
    companion = nil

    let restored = MascotCompanionLibrary(defaults: defaults)
    XCTAssertEqual(restored.displayName, "초코")
    XCTAssertEqual(restored.selection, .fixed(.headphones))
    XCTAssertEqual(restored.resolve(automatic: .lightstick), .headphones)
  }

  func testClosetSelectionResolvesContextAndManualProps() {
    let companion = MascotCompanionLibrary(defaults: isolatedDefaults())

    companion.selection = .automatic
    XCTAssertEqual(companion.resolve(automatic: .lightstick), .lightstick)

    companion.selection = .fixed(.headphones)
    XCTAssertEqual(companion.resolve(automatic: .lightstick), .headphones)

    companion.selection = .none
    XCTAssertEqual(companion.resolve(automatic: .lightstick), .none)
  }

  func testGrowthAxesEggPatternAndMistakeMemoryPersist() {
    let defaults = isolatedDefaults()
    var companion: MascotCompanionLibrary? = MascotCompanionLibrary(defaults: defaults)
    companion?.eggPattern = .hearts
    companion?.recordTypedJamo(321)
    companion?.recordMistake(expected: "ㄱ")
    companion?.recordMistake(expected: "ㄱ")
    companion?.recordMistake(expected: "ㅏ")
    companion = nil

    let restored = MascotCompanionLibrary(defaults: defaults)
    XCTAssertEqual(restored.eggPattern, .hearts)
    XCTAssertEqual(restored.typedJamoCount, 321)
    XCTAssertEqual(restored.mostMissedJamo, "ㄱ")
  }

  func testLessonStreakAndChoseongPublishPetEvents() {
    let companion = MascotCompanionLibrary(defaults: isolatedDefaults())

    XCTAssertEqual(companion.recordLessonOutcome(cleared: true), 1)
    XCTAssertEqual(companion.recordLessonOutcome(cleared: true), 2)
    XCTAssertEqual(companion.recordLessonOutcome(cleared: true), 3)
    XCTAssertEqual(companion.event, .lessonStreak(3))

    let revision = companion.eventRevision
    companion.recordChoseongSolved(usedPass: true)
    XCTAssertEqual(companion.eventRevision, revision)
    companion.recordChoseongSolved(usedPass: false)
    XCTAssertEqual(companion.event, .eureka)
  }

  func testSessionAppearanceDoesNotAutomaticallyEquipDeckProps() {
    XCTAssertEqual(MascotSessionAppearance().automaticProp, .none)
  }

  func testTOPIKSessionScorePermanentlyUnlocksGlasses() {
    let defaults = isolatedDefaults()
    var companion: MascotCompanionLibrary? = MascotCompanionLibrary(defaults: defaults)

    companion?.registerTOPIKSessionScore(-1, sourceTags: ["TOPIK"])
    XCTAssertFalse(companion?.hasUnlocked(.glasses) == true)
    companion?.registerTOPIKSessionScore(0, sourceTags: ["일상"])
    XCTAssertFalse(companion?.hasUnlocked(.glasses) == true)
    companion?.registerTOPIKSessionScore(0, sourceTags: ["TOPIK", "タイピング"])
    XCTAssertTrue(companion?.hasUnlocked(.glasses) == true)
    companion = nil

    let restored = MascotCompanionLibrary(defaults: defaults)
    XCTAssertTrue(restored.hasUnlocked(.glasses))
    XCTAssertEqual(
      MascotCompanionLibrary.topikSessionUnlockState.conditionKey,
      "closet.cond_glasses"
    )
  }

  func testGrowthAppearanceClampsIndependentAxes() {
    let appearance = MascotGrowthAppearance(
      typedJamoCount: 99_999,
      longestStreak: 99,
      activeDaysInLastWeek: 7,
      completedChapterCount: 9
    )

    XCTAssertEqual(appearance.combProgress, 1)
    XCTAssertEqual(appearance.bodyScale, 1.08, accuracy: 0.0001)
    XCTAssertEqual(appearance.featherSheen, 1)
    XCTAssertEqual(appearance.crackProgress, 3)
  }

  func testPropsUnlockFromExistingRetentionSignals() {
    let locked = MascotCompanionLibrary.unlockStates(
      streak: 2,
      chaptersCleared: 2,
      decksInstalled: 6
    )
    XCTAssertTrue(locked.allSatisfy { !$0.isUnlocked })

    let unlocked = MascotCompanionLibrary.unlockStates(
      streak: 3,
      chaptersCleared: 3,
      decksInstalled: 7
    )
    XCTAssertTrue(unlocked.allSatisfy(\.isUnlocked))
  }

  func testEarnedPropsStayUnlockedAfterSignalsDisappearAndRelaunch() {
    let defaults = isolatedDefaults()
    var companion: MascotCompanionLibrary? = MascotCompanionLibrary(defaults: defaults)
    let earned = MascotCompanionLibrary.unlockStates(
      streak: 3,
      chaptersCleared: 3,
      decksInstalled: 7
    )
    companion?.registerUnlocks(earned)
    XCTAssertTrue(companion?.hasUnlocked(.lightstick) == true)
    XCTAssertTrue(companion?.hasUnlocked(.gradCap) == true)
    XCTAssertTrue(companion?.hasUnlocked(.headphones) == true)
    companion = nil

    let restored = MascotCompanionLibrary(defaults: defaults)
    let currentlyLocked = MascotCompanionLibrary.unlockStates(
      streak: 0,
      chaptersCleared: 0,
      decksInstalled: 0
    )
    XCTAssertTrue(currentlyLocked.allSatisfy { !$0.isUnlocked })
    XCTAssertTrue(currentlyLocked.allSatisfy { restored.hasUnlocked($0.prop) })
  }

  func testGameCenterScorePermanentlyUnlocksTrophyProp() {
    let defaults = isolatedDefaults()
    var companion: MascotCompanionLibrary? = MascotCompanionLibrary(defaults: defaults)

    XCTAssertFalse(companion?.hasUnlocked(.gameCenterTrophy) == true)
    companion?.registerGameCenterScoreSubmission()
    XCTAssertTrue(companion?.hasUnlocked(.gameCenterTrophy) == true)
    companion = nil

    let restored = MascotCompanionLibrary(defaults: defaults)
    XCTAssertTrue(restored.hasUnlocked(.gameCenterTrophy))
    XCTAssertEqual(
      MascotCompanionLibrary.gameCenterUnlockState.conditionKey,
      "closet.cond_game_center"
    )
  }

  private func isolatedDefaults() -> UserDefaults {
    let suiteName = "MascotGrowthSystemTests.\(UUID().uuidString)"
    let defaults = UserDefaults(suiteName: suiteName)!
    defaults.removePersistentDomain(forName: suiteName)
    return defaults
  }
}
