package app.piyokey.core.domain

import kotlin.math.abs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** `tuning/game_rank_tuning.json` (iOS `FlowGameRankTuning`): S/A/B/C result rank. */
@Serializable
data class FlowGameRankTuning(
  @SerialName("schema_version") val schemaVersion: Int = 1,
  @SerialName("accuracy_weight") val accuracyWeight: Double = 0.6,
  @SerialName("speed_weight") val speedWeight: Double = 0.4,
  @SerialName("speed_cap_characters_per_minute") val speedCap: Double = 120.0,
  @SerialName("s_threshold") val sThreshold: Double = 90.0,
  @SerialName("a_threshold") val aThreshold: Double = 75.0,
  @SerialName("b_threshold") val bThreshold: Double = 55.0,
) {
  /** `"S"`, `"A"`, `"B"` or `"C"`. Accuracy and capped speed are clamped to 0…100. */
  fun rank(accuracyPercent: Double, charactersPerMinute: Double): String {
    val accuracyScore = accuracyPercent.coerceIn(0.0, 100.0)
    val speedScore = (charactersPerMinute / speedCap * 100).coerceIn(0.0, 100.0)
    val weighted = accuracyScore * accuracyWeight + speedScore * speedWeight
    return when {
      weighted >= sThreshold -> "S"
      weighted >= aThreshold -> "A"
      weighted >= bThreshold -> "B"
      else -> "C"
    }
  }
}

sealed class FlowGameRankTuningException(message: String) : Exception(message) {
  data object MissingBundledResource : FlowGameRankTuningException("Missing game_rank_tuning.json")
  data class UnsupportedSchema(val version: Int) : FlowGameRankTuningException("Unsupported tuning schema $version")
  data object InvalidWeights : FlowGameRankTuningException("Weights must be ≥ 0 and sum to 1")
  data object InvalidSpeedCap : FlowGameRankTuningException("Speed cap must be > 0")
  data object InvalidThresholds : FlowGameRankTuningException("Thresholds must be 0…100 and S > A > B")
}

object FlowGameRankTuningLoader {
  const val ASSET_PATH = "tuning/game_rank_tuning.json"

  private val json = Json { ignoreUnknownKeys = true }

  /** Decodes and validates. Missing JSON keys are errors (as Swift `Codable`). */
  fun decode(bytes: ByteArray): FlowGameRankTuning {
    val element = json.parseToJsonElement(bytes.toString(Charsets.UTF_8))
    val required = listOf(
      "schema_version", "accuracy_weight", "speed_weight", "speed_cap_characters_per_minute",
      "s_threshold", "a_threshold", "b_threshold",
    )
    val keys = (element as? kotlinx.serialization.json.JsonObject)?.keys ?: emptySet()
    require(required.all { it in keys }) { "game_rank_tuning.json is missing keys" }
    val tuning = json.decodeFromJsonElement(FlowGameRankTuning.serializer(), element)
    if (tuning.schemaVersion != 1) throw FlowGameRankTuningException.UnsupportedSchema(tuning.schemaVersion)
    if (tuning.accuracyWeight < 0 || tuning.speedWeight < 0 ||
      abs(tuning.accuracyWeight + tuning.speedWeight - 1) >= 0.000_001
    ) {
      throw FlowGameRankTuningException.InvalidWeights
    }
    if (tuning.speedCap <= 0) throw FlowGameRankTuningException.InvalidSpeedCap
    val range = 0.0..100.0
    if (tuning.sThreshold !in range || tuning.aThreshold !in range || tuning.bThreshold !in range ||
      tuning.sThreshold <= tuning.aThreshold || tuning.aThreshold <= tuning.bThreshold
    ) {
      throw FlowGameRankTuningException.InvalidThresholds
    }
    return tuning
  }
}
