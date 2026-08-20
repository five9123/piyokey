import XCTest
@testable import Hanco

final class SessionResultRevealTimelineTests: XCTestCase {
  private let timing = SessionResultRevealTiming()

  func testInitialStateKeepsScoreMetricsAndActionsHidden() {
    let state = timing.state(elapsed: 0)

    XCTAssertEqual(state.headerScale, 0.6, accuracy: 0.001)
    XCTAssertEqual(state.scoreProgress, 0, accuracy: 0.001)
    XCTAssertEqual(state.metricProgress(at: 0), 0, accuracy: 0.001)
    XCTAssertEqual(state.lowerZoneOpacity, 0, accuracy: 0.001)
    XCTAssertFalse(state.actionsEnabled)
  }

  func testScoreCountsUpFromPointFourToOnePointTwoSeconds() {
    XCTAssertEqual(timing.state(elapsed: 0.4).scoreProgress, 0, accuracy: 0.001)
    XCTAssertGreaterThan(timing.state(elapsed: 0.8).scoreProgress, 0.5)
    XCTAssertEqual(timing.state(elapsed: 1.2).scoreProgress, 1, accuracy: 0.001)
  }

  func testThreeMetricsRevealSequentially() {
    let firstFinished = timing.state(elapsed: 1.5)
    XCTAssertEqual(firstFinished.metricProgress(at: 0), 1, accuracy: 0.001)
    XCTAssertEqual(firstFinished.metricProgress(at: 1), 0, accuracy: 0.001)
    XCTAssertEqual(firstFinished.metricProgress(at: 2), 0, accuracy: 0.001)

    let allFinished = timing.state(elapsed: 2.1)
    XCTAssertEqual(allFinished.metricProgress(at: 0), 1, accuracy: 0.001)
    XCTAssertEqual(allFinished.metricProgress(at: 1), 1, accuracy: 0.001)
    XCTAssertEqual(allFinished.metricProgress(at: 2), 1, accuracy: 0.001)
  }

  func testLowerZonesAppearBeforeActionsBecomeInteractive() {
    let fading = timing.state(elapsed: 2.0)
    XCTAssertGreaterThan(fading.lowerZoneOpacity, 0)
    XCTAssertFalse(fading.actionsEnabled)

    XCTAssertTrue(timing.state(elapsed: 2.5).actionsEnabled)
  }

  func testSkipImmediatelyProducesFinalNonParticleState() {
    let state = timing.state(elapsed: 0.1, skipped: true)

    XCTAssertEqual(state.headerScale, 1, accuracy: 0.001)
    XCTAssertEqual(state.scoreProgress, 1, accuracy: 0.001)
    XCTAssertEqual(state.metricProgress(at: 2), 1, accuracy: 0.001)
    XCTAssertEqual(state.lowerZoneOpacity, 1, accuracy: 0.001)
    XCTAssertEqual(state.headerParticleProgress, 1, accuracy: 0.001)
    XCTAssertEqual(state.newRecordBurstProgress, 1, accuracy: 0.001)
    XCTAssertTrue(state.actionsEnabled)
  }

  func testUITestScaleChangesDurationButNotCanonicalProgress() {
    let slowed = SessionResultRevealTiming(scale: 4)

    XCTAssertEqual(slowed.totalDuration, 10, accuracy: 0.001)
    XCTAssertEqual(slowed.state(elapsed: 4.8).scoreProgress, 1, accuracy: 0.001)
    XCTAssertFalse(slowed.state(elapsed: 9.9).actionsEnabled)
    XCTAssertTrue(slowed.state(elapsed: 10).actionsEnabled)
  }
}
