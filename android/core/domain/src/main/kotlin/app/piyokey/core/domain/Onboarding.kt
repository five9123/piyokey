package app.piyokey.core.domain

import kotlin.math.abs
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Learning goal chosen in onboarding. Legacy `casual` → keyboard, `oshi` → trends. */
@Serializable(with = OnboardingGoalSerializer::class)
enum class OnboardingGoal(val raw: String, val preferredTags: List<String>, val symbol: String) {
  KEYBOARD("keyboard", listOf("入門", "キーボード", "子音", "母音"), "keyboard.fill"),
  TRAVEL("travel", listOf("韓国旅行", "旅行", "日常"), "airplane"),
  TOPIK("topik", listOf("TOPIK", "検定", "基礎単語"), "graduationcap.fill"),
  TRENDS("trends", listOf("今どき", "SNS", "日常"), "bubble.left.and.bubble.right.fill");

  val titleKey: String get() = "onboarding.goal.$raw.title"
  val detailKey: String get() = "onboarding.goal.$raw.detail"

  companion object {
    fun fromRaw(value: String): OnboardingGoal? = when (value) {
      "keyboard", "casual" -> KEYBOARD
      "travel" -> TRAVEL
      "topik" -> TOPIK
      "trends", "oshi" -> TRENDS
      else -> null
    }
  }
}

object OnboardingGoalSerializer : KSerializer<OnboardingGoal> {
  override val descriptor = PrimitiveSerialDescriptor("app.piyokey.OnboardingGoal", PrimitiveKind.STRING)
  override fun serialize(encoder: Encoder, value: OnboardingGoal) = encoder.encodeString(value.raw)
  override fun deserialize(decoder: Decoder): OnboardingGoal {
    val raw = decoder.decodeString()
    return OnboardingGoal.fromRaw(raw) ?: throw SerializationException("Unsupported onboarding goal: $raw")
  }
}

@Serializable
enum class OnboardingLevel(val raw: String) {
  @SerialName("beginner") BEGINNER("beginner"),
  @SerialName("jamo") JAMO("jamo"),
  @SerialName("words") WORDS("words"),
  @SerialName("sentences") SENTENCES("sentences");

  val titleKey: String get() = "onboarding.level.$raw.title"
  val detailKey: String get() = "onboarding.level.$raw.detail"

  /** Lower is a better fit for this learner level. */
  fun recommendationRank(level: Int, tags: List<String>, isSentence: Boolean): Int = when (this) {
    BEGINNER -> abs(level - 1) * 10 + (if ("入門" in tags) 0 else 1)
    JAMO -> abs(level - 1) * 10 + (if ("入門" !in tags && !isSentence) 0 else 1)
    WORDS -> abs(level - 2) * 10
    SENTENCES -> maxOf(0, level - 3) * 10 + (if (isSentence && level >= 2) 0 else 1)
  }
}

/** Stored ids keep the original three-step values (`level` was added later as 4). */
@Serializable(with = OnboardingStepSerializer::class)
enum class OnboardingStep(val raw: Int, val position: Int) {
  GOAL(1, 1),
  LEVEL(4, 2),
  KEYBOARD(2, 3),
  LESSON(3, 4);

  companion object {
    fun fromRaw(value: Int): OnboardingStep? = entries.firstOrNull { it.raw == value }
  }
}

object OnboardingStepSerializer : KSerializer<OnboardingStep> {
  override val descriptor = PrimitiveSerialDescriptor("app.piyokey.OnboardingStep", PrimitiveKind.INT)
  override fun serialize(encoder: Encoder, value: OnboardingStep) = encoder.encodeInt(value.raw)
  override fun deserialize(decoder: Decoder): OnboardingStep {
    val raw = decoder.decodeInt()
    return OnboardingStep.fromRaw(raw) ?: throw SerializationException("Unsupported onboarding step: $raw")
  }
}

/** `onboarding.state` JSON (iOS `OnboardingSnapshot`). */
@Serializable
data class OnboardingSnapshot(
  @SerialName("schema_version") val schemaVersion: Int,
  @SerialName("is_completed") val isCompleted: Boolean,
  @SerialName("was_skipped") val wasSkipped: Boolean,
  @SerialName("selected_goal") val selectedGoal: OnboardingGoal? = null,
  val step: OnboardingStep,
  @SerialName("selected_level") val selectedLevel: OnboardingLevel? = null,
) {
  val preferredTags: List<String> get() = selectedGoal?.preferredTags ?: emptyList()

  companion object {
    const val CURRENT_SCHEMA_VERSION = 1
    val EMPTY = OnboardingSnapshot(
      schemaVersion = 1,
      isCompleted = false,
      wasSkipped = false,
      selectedGoal = null,
      step = OnboardingStep.GOAL,
    )
  }
}

sealed class OnboardingStoreException(message: String) : Exception(message) {
  data class UnsupportedSchema(val version: Int) : OnboardingStoreException("Unsupported onboarding schema $version")
}
