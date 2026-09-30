package app.piyokey.android.data.mascot

import android.content.SharedPreferences
import app.piyokey.android.data.settings.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

/** Latest pet event plus a revision so the same reaction can replay (iOS `event` + `eventRevision`). */
data class MascotEventState(val reaction: MascotReaction = MascotReaction.None, val revision: Int = 0)

/**
 * Inputs owned by other stores (curriculum progress, retention, deck library). They are not
 * persisted here; the owning stores push them in with the `update…` functions so the mascot
 * surfaces (companion, closet) can derive stage, growth axes and unlocks like iOS does from its
 * environment objects.
 */
data class MascotSignals(
  val clearedChapters: Int = 0,
  val currentStreak: Int = 0,
  val longestStreak: Int = 0,
  val activeDaysInLastWeek: Int = 0,
  val installedDeckCount: Int = 0,
  val installedDeckTags: Set<String> = emptySet(),
  /** True once retention has pushed a real streak (guards the streak-break sulk). */
  val streakKnown: Boolean = false,
) {
  val stage: MascotStage get() = MascotStage.forClearedChapters(clearedChapters)
}

/**
 * Port of iOS `MascotCompanionLibrary`: name, prop selection, egg pattern, celebrations, typing
 * and mistake memory, lesson streak and permanent closet unlocks. Keys are the iOS UserDefaults
 * keys verbatim. `mascot.mistake_counts` (a plist dictionary on iOS) and `mascot.unlocked_props`
 * (string array) are stored as JSON strings.
 */
open class MascotCompanionStore(private val prefsProvider: () -> SharedPreferences) {
  companion object {
    const val NAME_KEY = "mascot.name"
    const val PROP_SELECTION_KEY = "mascot.prop_selection"
    const val CELEBRATED_STAGE_KEY = "mascot.celebrated_stage"
    const val TYPED_JAMO_KEY = "mascot.typed_jamo_count"
    const val EGG_PATTERN_KEY = "mascot.egg_pattern"
    const val CONSECUTIVE_LESSON_KEY = "mascot.consecutive_lesson_clears"
    const val LAST_KNOWN_STREAK_KEY = "mascot.last_known_streak"
    const val STREAK_BREAK_DAY_KEY = "mascot.streak_break_presented_day"
    const val MISTAKE_COUNTS_KEY = "mascot.mistake_counts"
    const val UNLOCKED_PROPS_KEY = "mascot.unlocked_props"
    val ALL_KEYS = listOf(
      NAME_KEY, PROP_SELECTION_KEY, CELEBRATED_STAGE_KEY, TYPED_JAMO_KEY, EGG_PATTERN_KEY,
      CONSECUTIVE_LESSON_KEY, LAST_KNOWN_STREAK_KEY, STREAK_BREAK_DAY_KEY, MISTAKE_COUNTS_KEY,
      UNLOCKED_PROPS_KEY,
    )
  }

  private val prefs: SharedPreferences get() = prefsProvider()

  private inner class State {
    val name = MutableStateFlow(prefs.getString(NAME_KEY, null) ?: "")
    val selection = MutableStateFlow(MascotSelection.fromRaw(prefs.getString(PROP_SELECTION_KEY, null)))
    val eggPattern = MutableStateFlow(MascotEggPattern.fromRaw(prefs.getString(EGG_PATTERN_KEY, null)) ?: MascotEggPattern.PLAIN)
    val typedJamoCount = MutableStateFlow(maxOf(0, readInt(TYPED_JAMO_KEY)))
    val consecutiveLessonClears = MutableStateFlow(maxOf(0, readInt(CONSECUTIVE_LESSON_KEY)))
    val mistakeCounts = MutableStateFlow(readMistakes())
    val unlockedProps = MutableStateFlow(readUnlocked())
    val celebratedRank = MutableStateFlow(readInt(CELEBRATED_STAGE_KEY))
    val event = MutableStateFlow(MascotEventState())
    val signals = MutableStateFlow(MascotSignals())

    init {
      // A fixed selection is always considered earned (iOS init).
      (selection.value as? MascotSelection.Fixed)?.let { unlockedProps.value = unlockedProps.value + it.prop }
      writeUnlocked(unlockedProps.value)
    }
  }

  private val state by lazy { State() }

  val name: StateFlow<String> get() = state.name.asStateFlow()
  val selection: StateFlow<MascotSelection> get() = state.selection.asStateFlow()
  val eggPattern: StateFlow<MascotEggPattern> get() = state.eggPattern.asStateFlow()
  val typedJamoCount: StateFlow<Int> get() = state.typedJamoCount.asStateFlow()
  val consecutiveLessonClears: StateFlow<Int> get() = state.consecutiveLessonClears.asStateFlow()
  val mistakeCounts: StateFlow<Map<String, Int>> get() = state.mistakeCounts.asStateFlow()
  val unlockedProps: StateFlow<Set<MascotProp>> get() = state.unlockedProps.asStateFlow()
  /** Raw stored `mascot.celebrated_stage` (0 when never set). */
  val celebratedRank: StateFlow<Int> get() = state.celebratedRank.asStateFlow()
  val event: StateFlow<MascotEventState> get() = state.event.asStateFlow()
  val signals: StateFlow<MascotSignals> get() = state.signals.asStateFlow()

  // MARK: name / selection / pattern

  fun setName(value: String) {
    state.name.value = value
    prefs.edit().putString(NAME_KEY, value).apply()
  }

  /** Trimmed name, or [defaultName] (`mascot.default_name`) when blank. */
  fun displayName(defaultName: String): String = displayName(state.name.value, defaultName)

  fun displayName(name: String, defaultName: String): String = name.trim().ifEmpty { defaultName }

  fun selectProp(selection: MascotSelection) {
    state.selection.value = selection
    prefs.edit().putString(PROP_SELECTION_KEY, selection.raw).apply()
  }

  fun selectEggPattern(pattern: MascotEggPattern) {
    state.eggPattern.value = pattern
    prefs.edit().putString(EGG_PATTERN_KEY, pattern.raw).apply()
  }

  fun resolve(automatic: MascotProp): MascotProp = state.selection.value.resolve(automatic)

  // MARK: growth celebration

  fun pendingCelebration(current: MascotStage): MascotStage? =
    MascotRules.pendingCelebration(state.celebratedRank.value, current)

  fun presentedStage(current: MascotStage): MascotStage =
    MascotRules.presentedStage(state.celebratedRank.value, current)

  fun markCelebrated(stage: MascotStage) {
    prefs.edit().putInt(CELEBRATED_STAGE_KEY, stage.growthRank).apply()
    state.celebratedRank.value = stage.growthRank
  }

  // MARK: learning inputs

  fun recordTypedJamo(count: Int = 1) {
    if (count <= 0) return
    val next = state.typedJamoCount.value + count
    state.typedJamoCount.value = next
    prefs.edit().putInt(TYPED_JAMO_KEY, next).apply()
  }

  /** Returns the new consecutive-clear count; publishes `lessonStreak` from the third clear. */
  fun recordLessonOutcome(cleared: Boolean): Int {
    val next = if (cleared) state.consecutiveLessonClears.value + 1 else 0
    state.consecutiveLessonClears.value = next
    prefs.edit().putInt(CONSECUTIVE_LESSON_KEY, next).apply()
    if (cleared && next >= 3) publish(MascotReaction.LessonStreak(next))
    return next
  }

  /** `day` is the JST day raw value (`yyyy-MM-dd`, iOS `JSTDay.rawValue`). */
  fun consumeStreakBreak(currentStreak: Int, day: String): Boolean {
    val previous = maxOf(0, readInt(LAST_KNOWN_STREAK_KEY))
    prefs.edit().putInt(LAST_KNOWN_STREAK_KEY, currentStreak).apply()
    val presented = prefs.getString(STREAK_BREAK_DAY_KEY, null)
    if (!MascotRules.isStreakBreak(previous, currentStreak, presented, day)) return false
    prefs.edit().putString(STREAK_BREAK_DAY_KEY, day).apply()
    return true
  }

  fun recordMistake(expected: String) {
    val next = state.mistakeCounts.value.toMutableMap()
    next[expected] = (next[expected] ?: 0) + 1
    state.mistakeCounts.value = next
    prefs.edit().putString(MISTAKE_COUNTS_KEY, Json.encodeToString(next)).apply()
  }

  fun recordMistake(expected: Char) = recordMistake(expected.toString())

  val mostMissedJamo: String? get() = MascotRules.mostMissedJamo(state.mistakeCounts.value)

  // MARK: closet unlocks

  /** Closet rewards are achievements: once earned they are never taken away. */
  fun registerUnlocks(states: List<MascotUnlockState>) {
    val earned = states.filter { it.isUnlocked }.map { it.prop }.toSet()
    val merged = state.unlockedProps.value + earned
    if (merged == state.unlockedProps.value) return
    state.unlockedProps.value = merged
    writeUnlocked(merged)
  }

  fun hasUnlocked(prop: MascotProp): Boolean = prop in state.unlockedProps.value

  fun registerGameCenterScoreSubmission() {
    registerUnlocks(listOf(MascotUnlockState(MascotProp.GAME_CENTER_TROPHY, true, "closet.cond_game_center")))
  }

  fun registerTOPIKSessionScore(score: Int, sourceTags: List<String>) {
    if (score < 0 || !MascotRules.isTopikSource(sourceTags)) return
    registerUnlocks(listOf(MascotUnlockState(MascotProp.GLASSES, true, "closet.cond_glasses")))
  }

  /** Unlock rows evaluated from the current [signals] (closet `evaluatedUnlocks`). */
  fun evaluatedUnlocks(signals: MascotSignals = state.signals.value): List<MascotUnlockState> =
    MascotRules.evaluatedUnlocks(
      streak = signals.currentStreak,
      chaptersCleared = signals.clearedChapters,
      decksInstalled = signals.installedDeckCount,
      installedTags = signals.installedDeckTags,
    )

  fun registerCurrentUnlocks() = registerUnlocks(evaluatedUnlocks())

  // MARK: events

  fun publish(reaction: MascotReaction) {
    state.event.update { MascotEventState(reaction, it.revision + 1) }
  }

  /** The choseong game reuses the pet contract; a pass never earns the eureka. */
  fun recordChoseongSolved(usedPass: Boolean) {
    if (usedPass) return
    publish(MascotReaction.Eureka)
  }

  // MARK: signals from other stores

  fun updateClearedChapters(count: Int) = state.signals.update { it.copy(clearedChapters = maxOf(0, count)) }

  fun updateStreak(current: Int, longest: Int, activeDaysInLastWeek: Int) = state.signals.update {
    it.copy(
      currentStreak = maxOf(0, current),
      longestStreak = maxOf(0, longest),
      activeDaysInLastWeek = activeDaysInLastWeek.coerceIn(0, 7),
      streakKnown = true,
    )
  }

  fun updateInstalledDecks(count: Int, tags: Set<String>) = state.signals.update {
    it.copy(installedDeckCount = maxOf(0, count), installedDeckTags = tags)
  }

  fun growthAppearance(signals: MascotSignals = state.signals.value): MascotGrowthAppearance =
    MascotGrowthAppearance.from(
      typedJamoCount = state.typedJamoCount.value,
      longestStreak = signals.longestStreak,
      activeDaysInLastWeek = signals.activeDaysInLastWeek,
      completedChapterCount = signals.clearedChapters,
    )

  /** Re-reads every value from storage (backup restore / reset flows). */
  fun reload() {
    state.name.value = prefs.getString(NAME_KEY, null) ?: ""
    state.selection.value = MascotSelection.fromRaw(prefs.getString(PROP_SELECTION_KEY, null))
    state.eggPattern.value = MascotEggPattern.fromRaw(prefs.getString(EGG_PATTERN_KEY, null)) ?: MascotEggPattern.PLAIN
    state.typedJamoCount.value = maxOf(0, readInt(TYPED_JAMO_KEY))
    state.consecutiveLessonClears.value = maxOf(0, readInt(CONSECUTIVE_LESSON_KEY))
    state.mistakeCounts.value = readMistakes()
    state.celebratedRank.value = readInt(CELEBRATED_STAGE_KEY)
    val unlocked = readUnlocked().toMutableSet()
    (state.selection.value as? MascotSelection.Fixed)?.let { unlocked += it.prop }
    state.unlockedProps.value = unlocked
    writeUnlocked(unlocked)
  }

  // MARK: storage helpers

  private fun readInt(key: String): Int = runCatching { prefs.getInt(key, 0) }.getOrDefault(0)

  private fun readMistakes(): Map<String, Int> =
    prefs.getString(MISTAKE_COUNTS_KEY, null)
      ?.let { runCatching { Json.decodeFromString<Map<String, Int>>(it) }.getOrNull() }
      ?: emptyMap()

  private fun readUnlocked(): Set<MascotProp> =
    (
      prefs.getString(UNLOCKED_PROPS_KEY, null)
        ?.let { runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull() }
        ?: emptyList()
      )
      .mapNotNull { MascotProp.fromRaw(it) }
      .filter { it != MascotProp.NONE }
      .toSet()

  private fun writeUnlocked(props: Set<MascotProp>) {
    prefs.edit().putString(UNLOCKED_PROPS_KEY, Json.encodeToString(props.map { it.raw }.sorted())).apply()
  }
}

/** Process-wide companion store over [Prefs.shared] (iOS environment `MascotCompanionLibrary`). */
object MascotStore : MascotCompanionStore({ Prefs.shared })
