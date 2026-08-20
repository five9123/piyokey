import XCTest
@testable import Hanco

final class FlowGameRankTuningTests: XCTestCase {
  func testBundledTuningLoadsExternalWeightsCapsAndThresholds() throws {
    let tuning = try FlowGameRankTuningLoader.loadBundled()

    XCTAssertEqual(tuning.schemaVersion, 1)
    XCTAssertEqual(tuning.accuracyWeight, 0.6, accuracy: 0.000_001)
    XCTAssertEqual(tuning.speedWeight, 0.4, accuracy: 0.000_001)
    XCTAssertEqual(tuning.speedCap, 120, accuracy: 0.000_001)
    XCTAssertEqual(tuning.sThreshold, 90, accuracy: 0.000_001)
    XCTAssertEqual(tuning.aThreshold, 75, accuracy: 0.000_001)
    XCTAssertEqual(tuning.bThreshold, 55, accuracy: 0.000_001)
  }

  func testDecoderRejectsWeightsThatDoNotSumToOne() {
    let data = tuningData(accuracyWeight: 0.6, speedWeight: 0.5)

    XCTAssertThrowsError(try FlowGameRankTuningLoader.decode(data)) { error in
      XCTAssertEqual(error as? FlowGameRankTuningError, .invalidWeights)
    }
  }

  func testDecoderRejectsUnorderedThresholds() {
    let data = tuningData(sThreshold: 75, aThreshold: 90, bThreshold: 55)

    XCTAssertThrowsError(try FlowGameRankTuningLoader.decode(data)) { error in
      XCTAssertEqual(error as? FlowGameRankTuningError, .invalidThresholds)
    }
  }

  private func tuningData(
    accuracyWeight: Double = 0.6,
    speedWeight: Double = 0.4,
    sThreshold: Double = 90,
    aThreshold: Double = 75,
    bThreshold: Double = 55
  ) -> Data {
    Data(
      """
      {
        "schema_version": 1,
        "accuracy_weight": \(accuracyWeight),
        "speed_weight": \(speedWeight),
        "speed_cap_characters_per_minute": 120,
        "s_threshold": \(sThreshold),
        "a_threshold": \(aThreshold),
        "b_threshold": \(bThreshold)
      }
      """.utf8
    )
  }
}
