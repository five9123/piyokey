package app.piyokey.android.data.mascot

import java.util.Locale

/*
 * Pure mascot rules ported 1:1 from iOS `DesignSystem/ChickMascotView.swift` and
 * `DesignSystem/MascotGrowthSystem.swift`. No Android imports: everything here is JVM-testable.
 */

/** iOS `MascotMood`. */
enum class MascotMood {
  IDLE, HAPPY, CHEER, OOPS, LOVE, PROUD, FOCUS, SLEEPY,
  SULK, SURPRISE, GRIT, DIZZY, WINK, SHY, EUREKA, SATISFIED,
}

/** iOS `MascotStage`: growth stage driven by cleared curriculum chapters. */
enum class MascotStage(val id: String, val growthRank: Int, val l10nKey: String) {
  EGG("egg", 0, "a11y.stage.egg"),
  CRACKING("cracking", 1, "a11y.stage.cracking"),
  HATCHING("hatching", 2, "a11y.stage.hatching"),
  CHICK("chick", 3, "a11y.stage.chick"),
  ROOSTER("rooster", 4, "a11y.stage.rooster");

  companion object {
    fun forGrowthRank(rank: Int): MascotStage = when {
      rank <= 0 -> EGG
      rank == 1 -> CRACKING
      rank == 2 -> HATCHING
      rank == 3 -> CHICK
      else -> ROOSTER
    }

    /** 0 → cracking, 1–2 → hatching, 3–5 → chick, 6+ → rooster (egg is never chapter-driven). */
    fun forClearedChapters(n: Int): MascotStage = when {
      n < 1 -> CRACKING
      n <= 2 -> HATCHING
      n <= 5 -> CHICK
      else -> ROOSTER
    }
  }
}

/** iOS `MascotProp` — raw values are the Swift case names (persisted in `mascot.prop_selection`). */
enum class MascotProp(val raw: String) {
  NONE("none"),
  LIGHTSTICK("lightstick"),
  GRAD_CAP("gradCap"),
  HEADPHONES("headphones"),
  TRAVEL_CASE("travelCase"),
  COFFEE_CUP("coffeeCup"),
  MICROPHONE("microphone"),
  HEART_BALLOON("heartBalloon"),
  FOOD_PLATE("foodPlate"),
  RIBBON("ribbon"),
  GLASSES("glasses"),
  GAME_CENTER_TROPHY("gameCenterTrophy");

  /** Closet label key (iOS `MascotClosetView.propLabelKey`). */
  val labelKey: String
    get() = when (this) {
      NONE -> "closet.bare"
      LIGHTSTICK -> "closet.prop_lightstick"
      GRAD_CAP -> "closet.prop_gradcap"
      HEADPHONES -> "closet.prop_headphones"
      TRAVEL_CASE -> "closet.prop_travel"
      COFFEE_CUP -> "closet.prop_cafe"
      MICROPHONE -> "closet.prop_microphone"
      HEART_BALLOON -> "closet.prop_balloon"
      FOOD_PLATE -> "closet.prop_food"
      RIBBON -> "closet.prop_ribbon"
      GLASSES -> "closet.prop_glasses"
      GAME_CENTER_TROPHY -> "closet.prop_game_center"
    }

  companion object {
    fun fromRaw(raw: String?): MascotProp? = entries.firstOrNull { it.raw == raw }
  }
}

/** iOS `MascotEggPattern` (onboarding-goal personalisation). */
enum class MascotEggPattern(val raw: String) {
  PLAIN("plain"), HEARTS("hearts"), STARS("stars"), POLKA("polka");

  companion object {
    fun fromRaw(raw: String?): MascotEggPattern? = entries.firstOrNull { it.raw == raw }

    /** Onboarding goal raw value → pattern. */
    fun forGoal(goalRaw: String?): MascotEggPattern = when (goalRaw) {
      "trends", "oshi" -> HEARTS
      "topik", "exam" -> STARS
      "travel" -> POLKA
      else -> PLAIN
    }
  }
}

/** iOS `MascotPose`. */
enum class MascotPose { FRONT, THREE_QUARTER_LEFT, THREE_QUARTER_RIGHT }

/** iOS `MascotReaction`: a repeatable, event-driven motion independent from the mood. */
sealed interface MascotReaction {
  data object None : MascotReaction
  data object CorrectJamo : MascotReaction
  data object SyllableCompleted : MascotReaction
  data object WordCompleted : MascotReaction
  data class ComboMilestone(val count: Int) : MascotReaction
  data object Mistake : MascotReaction
  data object PerfectSession : MascotReaction
  data class Rhythm(val count: Int) : MascotReaction
  data object Startle : MascotReaction
  data object GrowthTransition : MascotReaction
  data object Stretch : MascotReaction
  data object EggKnock : MascotReaction
  data object ReviewGraduated : MascotReaction
  data object NewBest : MascotReaction
  data class LessonStreak(val count: Int) : MascotReaction
  data object Eureka : MascotReaction
}

/** iOS `MascotSessionAppearance`: sessions never auto-equip deck props. */
data class MascotSessionAppearance(val automaticProp: MascotProp = MascotProp.NONE)

/** iOS `MascotPettingPolicy`. Durations in milliseconds. */
object MascotPettingPolicy {
  const val DOUBLE_TAP_WINDOW_MS = 280L
  const val TAP_SERIES_RESET_MS = 1_800L
  const val HAPPY_REVERT_MS = 900L
  const val CHEER_REVERT_MS = 1_100L

  fun singleTapMood(consecutiveTapCount: Int): MascotMood =
    if (consecutiveTapCount >= 3) MascotMood.SHY else MascotMood.HAPPY
}

/** iOS `MascotMotionPolicy`: keeps the silhouette inside small slots and never edge-on. */
object MascotMotionPolicy {
  fun horizontalOffset(rawValue: Float, size: Float): Float =
    rawValue.coerceIn(-size * 0.045f, size * 0.045f)

  fun verticalOffset(rawValue: Float, size: Float): Float =
    minOf(maxOf(rawValue, -size * 0.085f), size * 0.05f)

  fun reactionScale(rawValue: Float): Float = minOf(maxOf(rawValue, 0.72f), 1.08f)

  fun yawDegrees(pose: MascotPose, isLookingBack: Boolean): Float {
    if (!isLookingBack) {
      return when (pose) {
        MascotPose.FRONT -> 0f
        MascotPose.THREE_QUARTER_LEFT -> -12f
        MascotPose.THREE_QUARTER_RIGHT -> 12f
      }
    }
    // A 180° turn passes through zero width and the character would vanish.
    return when (pose) {
      MascotPose.FRONT, MascotPose.THREE_QUARTER_RIGHT -> 24f
      MascotPose.THREE_QUARTER_LEFT -> -24f
    }
  }

  fun capOffset(rawValue: Float, size: Float): Float =
    minOf(maxOf(rawValue, -size * 0.10f), size * 0.04f)
}

/** iOS `MascotGrowthAppearance`: permanent and recent-activity axes over the stage. */
data class MascotGrowthAppearance private constructor(
  val combProgress: Float,
  val bodyScale: Float,
  val featherSheen: Float,
  val crackProgress: Int,
) {
  companion object {
    val STANDARD = of(combProgress = 0f, bodyScale = 1f, featherSheen = 0.35f, crackProgress = 1)

    fun from(
      typedJamoCount: Int,
      longestStreak: Int,
      activeDaysInLastWeek: Int,
      completedChapterCount: Int,
    ) = MascotGrowthAppearance(
      combProgress = (typedJamoCount / 12_000f).coerceIn(0f, 1f),
      bodyScale = 1f + (longestStreak / 30f).coerceIn(0f, 1f) * 0.08f,
      featherSheen = 0.18f + (activeDaysInLastWeek / 7f).coerceIn(0f, 1f) * 0.82f,
      crackProgress = (completedChapterCount + 1).coerceIn(1, 3),
    )

    fun of(combProgress: Float, bodyScale: Float, featherSheen: Float, crackProgress: Int) =
      MascotGrowthAppearance(
        combProgress = combProgress.coerceIn(0f, 1f),
        bodyScale = bodyScale.coerceIn(0.92f, 1.12f),
        featherSheen = featherSheen.coerceIn(0f, 1f),
        crackProgress = crackProgress.coerceIn(1, 3),
      )
  }
}

/** iOS `MascotFanColor`. */
enum class MascotFanColor(val raw: String) {
  PINK("pink"), LAVENDER("lavender"), MINT("mint"), SKY("sky"), CORAL("coral"), GOLD("gold");

  companion object {
    fun forDeckTags(tags: List<String>): MascotFanColor {
      val joined = tags.joinToString("|")
      var checksum = 0L
      var index = 0
      while (index < joined.length) {
        val scalar = joined.codePointAt(index)
        checksum = (checksum * 31 + scalar) % entries.size
        index += Character.charCount(scalar)
      }
      return entries[checksum.toInt()]
    }
  }
}

/** iOS `MascotCompanionLibrary.Selection`. Persisted as "auto" / "none" / prop raw value. */
sealed interface MascotSelection {
  data object Automatic : MascotSelection
  data object None : MascotSelection
  data class Fixed(val prop: MascotProp) : MascotSelection

  val raw: String
    get() = when (this) {
      Automatic -> "auto"
      None -> "none"
      is Fixed -> prop.raw
    }

  fun resolve(automatic: MascotProp): MascotProp = when (this) {
    Automatic -> automatic
    None -> MascotProp.NONE
    is Fixed -> prop
  }

  companion object {
    fun fromRaw(raw: String?): MascotSelection {
      if (raw == "none") return None
      val prop = MascotProp.fromRaw(raw)
      return if (prop != null && prop != MascotProp.NONE) Fixed(prop) else Automatic
    }
  }
}

/** iOS `MascotCompanionLibrary.UnlockState`. */
data class MascotUnlockState(val prop: MascotProp, val isUnlocked: Boolean, val conditionKey: String)

/** Static rules of iOS `MascotCompanionLibrary`. */
object MascotRules {
  fun unlockStates(streak: Int, chaptersCleared: Int, decksInstalled: Int): List<MascotUnlockState> = listOf(
    MascotUnlockState(MascotProp.LIGHTSTICK, streak >= 3, "closet.cond_lightstick"),
    MascotUnlockState(MascotProp.GRAD_CAP, chaptersCleared >= 3, "closet.cond_gradcap"),
    MascotUnlockState(MascotProp.HEADPHONES, decksInstalled >= 7, "closet.cond_headphones"),
  )

  private val contextualRules: List<Triple<MascotProp, List<String>, String>> = listOf(
    Triple(MascotProp.TRAVEL_CASE, listOf("旅行", "travel"), "closet.cond_travel"),
    Triple(MascotProp.COFFEE_CUP, listOf("カフェ", "cafe"), "closet.cond_cafe"),
    Triple(MascotProp.MICROPHONE, listOf("韓ドラ", "セリフ"), "closet.cond_microphone"),
    Triple(MascotProp.HEART_BALLOON, listOf("恋愛", "love"), "closet.cond_balloon"),
    Triple(MascotProp.FOOD_PLATE, listOf("グルメ", "料理", "食"), "closet.cond_food"),
    Triple(MascotProp.RIBBON, listOf("基本", "あいさつ"), "closet.cond_ribbon"),
  )

  fun contextualUnlockStates(installedTags: Set<String>): List<MascotUnlockState> =
    contextualRules.map { (prop, needles, condition) ->
      MascotUnlockState(
        prop = prop,
        isUnlocked = installedTags.any { tag -> needles.any { containsIgnoringCase(tag, it) } },
        conditionKey = condition,
      )
    }

  val gameCenterUnlockState = MascotUnlockState(MascotProp.GAME_CENTER_TROPHY, false, "closet.cond_game_center")
  val topikSessionUnlockState = MascotUnlockState(MascotProp.GLASSES, false, "closet.cond_glasses")

  /** Every closet row in iOS order (before merging earned unlocks). */
  fun evaluatedUnlocks(
    streak: Int,
    chaptersCleared: Int,
    decksInstalled: Int,
    installedTags: Set<String>,
  ): List<MascotUnlockState> =
    unlockStates(streak, chaptersCleared, decksInstalled) +
      contextualUnlockStates(installedTags) +
      listOf(topikSessionUnlockState, gameCenterUnlockState)

  /** Closet display: a prop earned once stays unlocked. */
  fun mergeEarned(states: List<MascotUnlockState>, earned: Set<MascotProp>): List<MascotUnlockState> =
    states.map { it.copy(isUnlocked = it.isUnlocked || it.prop in earned) }

  /** Stored celebrated rank is floored at 1 (cracking is never celebrated). */
  fun pendingCelebration(storedCelebratedRank: Int, current: MascotStage): MascotStage? {
    val nextRank = maxOf(storedCelebratedRank, 1) + 1
    if (current.growthRank < nextRank) return null
    return MascotStage.forGrowthRank(nextRank)
  }

  fun presentedStage(storedCelebratedRank: Int, current: MascotStage): MascotStage =
    MascotStage.forGrowthRank(minOf(current.growthRank, maxOf(storedCelebratedRank, 1)))

  /** Largest count wins; ties resolve to the lexicographically smaller key (iOS `max(by:)`). */
  fun mostMissedJamo(mistakeCounts: Map<String, Int>): String? =
    mistakeCounts.entries.fold(null as Map.Entry<String, Int>?) { best, entry ->
      when {
        best == null -> entry
        // Swift `max` keeps the later element when `best < entry` under the comparator.
        isLess(best, entry) -> entry
        else -> best
      }
    }?.key

  private fun isLess(lhs: Map.Entry<String, Int>, rhs: Map.Entry<String, Int>): Boolean =
    if (lhs.value == rhs.value) lhs.key > rhs.key else lhs.value < rhs.value

  /** iOS `consumeStreakBreak` decision (after `previous` has been read from storage). */
  fun isStreakBreak(previousStreak: Int, currentStreak: Int, presentedDay: String?, day: String): Boolean =
    currentStreak == 0 && maxOf(0, previousStreak) > 0 && presentedDay != day

  /** iOS `GrowingMascotView.effectiveMood`. `jstHour` is 0–23 in Asia/Tokyo. */
  fun effectiveMood(
    mood: MascotMood,
    showsStreakBreakMood: Boolean,
    usesHomeTimeMood: Boolean,
    consecutiveLessonClears: Int,
    jstHour: Int,
  ): MascotMood {
    if (showsStreakBreakMood) return MascotMood.SULK
    if (usesHomeTimeMood && mood == MascotMood.IDLE && consecutiveLessonClears >= 3) return MascotMood.SATISFIED
    if (!usesHomeTimeMood || mood != MascotMood.IDLE) return mood
    return if (jstHour >= 21 || jstHour < 5) MascotMood.SLEEPY else mood
  }

  /** iOS `GrowingMascotView`: the rooster wears the graduation cap automatically. */
  fun automaticProp(presentedStage: MascotStage, contextProp: MascotProp): MascotProp =
    if (presentedStage == MascotStage.ROOSTER) MascotProp.GRAD_CAP else contextProp

  /** Closet preview rows never show anything smaller than a chick (`max(stage, .chick)`). */
  fun closetPreviewStage(stage: MascotStage): MascotStage =
    if (stage.growthRank < MascotStage.CHICK.growthRank) MascotStage.CHICK else stage

  /** Growth-condition rows in the closet: stage, condition key, chapter threshold. */
  val growthConditions: List<Triple<MascotStage, String, Int>> = listOf(
    Triple(MascotStage.HATCHING, "growth.cond_hatch", 1),
    Triple(MascotStage.CHICK, "growth.cond_chick", 3),
    Triple(MascotStage.ROOSTER, "growth.cond_rooster", 6),
  )

  /** `localizedCaseInsensitiveContains` approximation. */
  fun containsIgnoringCase(haystack: String, needle: String): Boolean =
    haystack.lowercase(Locale.ROOT).contains(needle.lowercase(Locale.ROOT))

  fun isTopikSource(sourceTags: List<String>): Boolean = sourceTags.any { containsIgnoringCase(it, "TOPIK") }
}

/** Growth-celebration title key (iOS `GrowthCelebrationView.titleKey`). */
fun MascotStage.celebrationTitleKey(): String = when (this) {
  MascotStage.HATCHING -> "growth.hatched"
  MascotStage.ROOSTER -> "growth.master"
  else -> "growth.advanced"
}
