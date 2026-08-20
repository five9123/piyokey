import Foundation

struct FlowGameRankTuning: Codable, Equatable {
  let schemaVersion: Int
  let accuracyWeight: Double
  let speedWeight: Double
  let speedCap: Double
  let sThreshold: Double
  let aThreshold: Double
  let bThreshold: Double

  init(
    schemaVersion: Int = 1,
    accuracyWeight: Double = 0.6,
    speedWeight: Double = 0.4,
    speedCap: Double = 120,
    sThreshold: Double = 90,
    aThreshold: Double = 75,
    bThreshold: Double = 55
  ) {
    self.schemaVersion = schemaVersion
    self.accuracyWeight = accuracyWeight
    self.speedWeight = speedWeight
    self.speedCap = speedCap
    self.sThreshold = sThreshold
    self.aThreshold = aThreshold
    self.bThreshold = bThreshold
  }

  func rank(accuracyPercent: Double, charactersPerMinute: Double) -> String {
    let accuracyScore = min(max(accuracyPercent, 0), 100)
    let speedScore = min(max(charactersPerMinute / speedCap * 100, 0), 100)
    let weightedScore = accuracyScore * accuracyWeight + speedScore * speedWeight

    if weightedScore >= sThreshold { return "S" }
    if weightedScore >= aThreshold { return "A" }
    if weightedScore >= bThreshold { return "B" }
    return "C"
  }

  private enum CodingKeys: String, CodingKey {
    case schemaVersion = "schema_version"
    case accuracyWeight = "accuracy_weight"
    case speedWeight = "speed_weight"
    case speedCap = "speed_cap_characters_per_minute"
    case sThreshold = "s_threshold"
    case aThreshold = "a_threshold"
    case bThreshold = "b_threshold"
  }
}

enum FlowGameRankTuningError: Error, Equatable {
  case missingBundledResource
  case unsupportedSchema(Int)
  case invalidWeights
  case invalidSpeedCap
  case invalidThresholds
}

enum FlowGameRankTuningLoader {
  static func decode(_ data: Data) throws -> FlowGameRankTuning {
    let tuning = try JSONDecoder().decode(FlowGameRankTuning.self, from: data)
    guard tuning.schemaVersion == 1 else {
      throw FlowGameRankTuningError.unsupportedSchema(tuning.schemaVersion)
    }
    guard
      tuning.accuracyWeight >= 0,
      tuning.speedWeight >= 0,
      abs(tuning.accuracyWeight + tuning.speedWeight - 1) < 0.000_001
    else {
      throw FlowGameRankTuningError.invalidWeights
    }
    guard tuning.speedCap > 0 else {
      throw FlowGameRankTuningError.invalidSpeedCap
    }
    guard
      (0...100).contains(tuning.sThreshold),
      (0...100).contains(tuning.aThreshold),
      (0...100).contains(tuning.bThreshold),
      tuning.sThreshold > tuning.aThreshold,
      tuning.aThreshold > tuning.bThreshold
    else {
      throw FlowGameRankTuningError.invalidThresholds
    }
    return tuning
  }

  static func loadBundled(bundle: Bundle = .main) throws -> FlowGameRankTuning {
    guard let url = bundle.url(forResource: "game_rank_tuning", withExtension: "json") else {
      throw FlowGameRankTuningError.missingBundledResource
    }
    return try decode(Data(contentsOf: url))
  }

  static func requiredBundled(bundle: Bundle = .main) -> FlowGameRankTuning {
    do {
      return try loadBundled(bundle: bundle)
    } catch {
      preconditionFailure("Bundled game rank tuning is invalid: \(error)")
    }
  }
}
