package app.piyokey.android.data.mascot

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of iOS `HancoTests/MascotGrowthSystemTests.swift` plus rule edge cases. */
class MascotGrowthSystemTest {
  private fun store(prefs: InMemoryPrefs = InMemoryPrefs()) = MascotCompanionStore { prefs }

  @Test
  fun idlePettingPolicyBecomesShyOnThirdConsecutiveTap() {
    assertEquals(MascotMood.HAPPY, MascotPettingPolicy.singleTapMood(1))
    assertEquals(MascotMood.HAPPY, MascotPettingPolicy.singleTapMood(2))
    assertEquals(MascotMood.SHY, MascotPettingPolicy.singleTapMood(3))
    assertEquals(MascotMood.SHY, MascotPettingPolicy.singleTapMood(8))
    assertEquals(280L, MascotPettingPolicy.DOUBLE_TAP_WINDOW_MS)
    assertEquals(900L, MascotPettingPolicy.HAPPY_REVERT_MS)
    assertEquals(1_100L, MascotPettingPolicy.CHEER_REVERT_MS)
    assertEquals(1_800L, MascotPettingPolicy.TAP_SERIES_RESET_MS)
  }

  @Test
  fun motionPolicyNeverTurnsEdgeOnOrLeavesItsPresentationSlot() {
    assertEquals(4.5f, MascotMotionPolicy.horizontalOffset(100f, 100f), 0.001f)
    assertEquals(-4.5f, MascotMotionPolicy.horizontalOffset(-100f, 100f), 0.001f)
    assertEquals(-8.5f, MascotMotionPolicy.verticalOffset(-100f, 100f), 0.001f)
    assertEquals(5f, MascotMotionPolicy.verticalOffset(100f, 100f), 0.001f)
    assertEquals(1.08f, MascotMotionPolicy.reactionScale(1.5f), 0.001f)
    assertEquals(0.72f, MascotMotionPolicy.reactionScale(0.1f), 0.001f)
    assertEquals(-10f, MascotMotionPolicy.capOffset(-100f, 100f), 0.001f)
    assertEquals(4f, MascotMotionPolicy.capOffset(100f, 100f), 0.001f)
    for (pose in MascotPose.entries) {
      assertTrue(abs(MascotMotionPolicy.yawDegrees(pose, isLookingBack = true)) < 45f)
    }
    assertEquals(0f, MascotMotionPolicy.yawDegrees(MascotPose.FRONT, false))
    assertEquals(-12f, MascotMotionPolicy.yawDegrees(MascotPose.THREE_QUARTER_LEFT, false))
    assertEquals(12f, MascotMotionPolicy.yawDegrees(MascotPose.THREE_QUARTER_RIGHT, false))
  }

  @Test
  fun growthStagesFollowClearedChapterThresholds() {
    assertEquals(MascotStage.CRACKING, MascotStage.forClearedChapters(0))
    assertEquals(MascotStage.CRACKING, MascotStage.forClearedChapters(-3))
    assertEquals(MascotStage.HATCHING, MascotStage.forClearedChapters(1))
    assertEquals(MascotStage.HATCHING, MascotStage.forClearedChapters(2))
    assertEquals(MascotStage.CHICK, MascotStage.forClearedChapters(3))
    assertEquals(MascotStage.CHICK, MascotStage.forClearedChapters(5))
    assertEquals(MascotStage.ROOSTER, MascotStage.forClearedChapters(6))
    assertEquals(MascotStage.ROOSTER, MascotStage.forClearedChapters(40))
  }

  @Test
  fun growthRankRoundTrips() {
    for (stage in MascotStage.entries) assertEquals(stage, MascotStage.forGrowthRank(stage.growthRank))
    assertEquals(MascotStage.EGG, MascotStage.forGrowthRank(-1))
    assertEquals(MascotStage.ROOSTER, MascotStage.forGrowthRank(9))
  }

  @Test
  fun growthCelebrationIsEmittedOncePerAdvancedStage() {
    val companion = store()
    assertNull(companion.pendingCelebration(MascotStage.CRACKING))
    assertEquals(MascotStage.HATCHING, companion.pendingCelebration(MascotStage.HATCHING))

    companion.markCelebrated(MascotStage.HATCHING)
    assertNull(companion.pendingCelebration(MascotStage.HATCHING))
    assertEquals(MascotStage.CHICK, companion.pendingCelebration(MascotStage.CHICK))

    companion.markCelebrated(MascotStage.CHICK)
    assertNull(companion.pendingCelebration(MascotStage.CHICK))
    assertEquals(MascotStage.ROOSTER, companion.pendingCelebration(MascotStage.ROOSTER))
  }

  @Test
  fun growthCelebrationDoesNotSkipHatchingOrRevealItEarly() {
    val companion = store()
    assertEquals(MascotStage.CRACKING, companion.presentedStage(MascotStage.HATCHING))
    assertEquals(MascotStage.HATCHING, companion.pendingCelebration(MascotStage.CHICK))

    companion.markCelebrated(MascotStage.HATCHING)
    assertEquals(MascotStage.HATCHING, companion.presentedStage(MascotStage.CHICK))
    assertEquals(MascotStage.CHICK, companion.pendingCelebration(MascotStage.CHICK))
    assertEquals(2, companion.celebratedRank.value)
  }

  @Test
  fun nameAndEquippedPropPersist() {
    val prefs = InMemoryPrefs()
    val companion = store(prefs)
    companion.setName("초코")
    companion.selectProp(MascotSelection.Fixed(MascotProp.HEADPHONES))

    val restored = store(prefs)
    assertEquals("초코", restored.displayName("Piyo"))
    assertEquals(MascotSelection.Fixed(MascotProp.HEADPHONES), restored.selection.value)
    assertEquals(MascotProp.HEADPHONES, restored.resolve(MascotProp.LIGHTSTICK))
    assertEquals("headphones", prefs.getString("mascot.prop_selection", null))
    // A fixed selection counts as earned on launch.
    assertTrue(restored.hasUnlocked(MascotProp.HEADPHONES))
  }

  @Test
  fun blankNameFallsBackToDefault() {
    val companion = store()
    assertEquals("Piyo", companion.displayName("Piyo"))
    companion.setName("   ")
    assertEquals("Piyo", companion.displayName("Piyo"))
    companion.setName("  Chiko \n")
    assertEquals("Chiko", companion.displayName("Piyo"))
  }

  @Test
  fun closetSelectionResolvesContextAndManualProps() {
    val companion = store()
    companion.selectProp(MascotSelection.Automatic)
    assertEquals(MascotProp.LIGHTSTICK, companion.resolve(MascotProp.LIGHTSTICK))
    companion.selectProp(MascotSelection.Fixed(MascotProp.HEADPHONES))
    assertEquals(MascotProp.HEADPHONES, companion.resolve(MascotProp.LIGHTSTICK))
    companion.selectProp(MascotSelection.None)
    assertEquals(MascotProp.NONE, companion.resolve(MascotProp.LIGHTSTICK))
  }

  @Test
  fun selectionRawValuesMatchIOS() {
    assertEquals("auto", MascotSelection.Automatic.raw)
    assertEquals("none", MascotSelection.None.raw)
    assertEquals("gameCenterTrophy", MascotSelection.Fixed(MascotProp.GAME_CENTER_TROPHY).raw)
    assertEquals(MascotSelection.Automatic, MascotSelection.fromRaw(null))
    assertEquals(MascotSelection.Automatic, MascotSelection.fromRaw("auto"))
    assertEquals(MascotSelection.Automatic, MascotSelection.fromRaw("bogus"))
    assertEquals(MascotSelection.None, MascotSelection.fromRaw("none"))
    assertEquals(MascotSelection.Fixed(MascotProp.GRAD_CAP), MascotSelection.fromRaw("gradCap"))
  }

  @Test
  fun growthAxesEggPatternAndMistakeMemoryPersist() {
    val prefs = InMemoryPrefs()
    val companion = store(prefs)
    companion.selectEggPattern(MascotEggPattern.HEARTS)
    companion.recordTypedJamo(321)
    companion.recordTypedJamo(0)
    companion.recordTypedJamo(-4)
    companion.recordMistake("ㄱ")
    companion.recordMistake('ㄱ')
    companion.recordMistake("ㅏ")

    val restored = store(prefs)
    assertEquals(MascotEggPattern.HEARTS, restored.eggPattern.value)
    assertEquals(321, restored.typedJamoCount.value)
    assertEquals("ㄱ", restored.mostMissedJamo)
    assertEquals(mapOf("ㄱ" to 2, "ㅏ" to 1), restored.mistakeCounts.value)
  }

  @Test
  fun mostMissedJamoTieBreaksOnSmallerKey() {
    assertNull(MascotRules.mostMissedJamo(emptyMap()))
    assertEquals("ㄱ", MascotRules.mostMissedJamo(mapOf("ㅏ" to 2, "ㄱ" to 2)))
    assertEquals("ㅏ", MascotRules.mostMissedJamo(mapOf("ㅏ" to 3, "ㄱ" to 2)))
  }

  @Test
  fun lessonStreakAndChoseongPublishPetEvents() {
    val companion = store()
    assertEquals(1, companion.recordLessonOutcome(cleared = true))
    assertEquals(2, companion.recordLessonOutcome(cleared = true))
    assertEquals(MascotReaction.None, companion.event.value.reaction)
    assertEquals(3, companion.recordLessonOutcome(cleared = true))
    assertEquals(MascotReaction.LessonStreak(3), companion.event.value.reaction)

    val revision = companion.event.value.revision
    companion.recordChoseongSolved(usedPass = true)
    assertEquals(revision, companion.event.value.revision)
    companion.recordChoseongSolved(usedPass = false)
    assertEquals(MascotReaction.Eureka, companion.event.value.reaction)
    assertEquals(revision + 1, companion.event.value.revision)

    assertEquals(0, companion.recordLessonOutcome(cleared = false))
  }

  @Test
  fun sessionAppearanceDoesNotAutomaticallyEquipDeckProps() {
    assertEquals(MascotProp.NONE, MascotSessionAppearance().automaticProp)
  }

  @Test
  fun topikSessionScorePermanentlyUnlocksGlasses() {
    val prefs = InMemoryPrefs()
    val companion = store(prefs)
    companion.registerTOPIKSessionScore(-1, listOf("TOPIK"))
    assertFalse(companion.hasUnlocked(MascotProp.GLASSES))
    companion.registerTOPIKSessionScore(0, listOf("일상"))
    assertFalse(companion.hasUnlocked(MascotProp.GLASSES))
    companion.registerTOPIKSessionScore(0, listOf("topik", "タイピング"))
    assertTrue(companion.hasUnlocked(MascotProp.GLASSES))

    assertTrue(store(prefs).hasUnlocked(MascotProp.GLASSES))
    assertEquals("closet.cond_glasses", MascotRules.topikSessionUnlockState.conditionKey)
  }

  @Test
  fun growthAppearanceClampsIndependentAxes() {
    val appearance = MascotGrowthAppearance.from(
      typedJamoCount = 99_999,
      longestStreak = 99,
      activeDaysInLastWeek = 7,
      completedChapterCount = 9,
    )
    assertEquals(1f, appearance.combProgress)
    assertEquals(1.08f, appearance.bodyScale, 0.0001f)
    assertEquals(1f, appearance.featherSheen)
    assertEquals(3, appearance.crackProgress)

    val fresh = MascotGrowthAppearance.from(0, 0, 0, 0)
    assertEquals(0f, fresh.combProgress)
    assertEquals(1f, fresh.bodyScale)
    assertEquals(0.18f, fresh.featherSheen, 0.0001f)
    assertEquals(1, fresh.crackProgress)

    assertEquals(0.35f, MascotGrowthAppearance.STANDARD.featherSheen)
    assertEquals(1.12f, MascotGrowthAppearance.of(0f, 3f, 0f, 1).bodyScale)
  }

  @Test
  fun propsUnlockFromExistingRetentionSignals() {
    assertTrue(MascotRules.unlockStates(streak = 2, chaptersCleared = 2, decksInstalled = 6).none { it.isUnlocked })
    assertTrue(MascotRules.unlockStates(streak = 3, chaptersCleared = 3, decksInstalled = 7).all { it.isUnlocked })
  }

  @Test
  fun contextualTagsUnlockThemedProps() {
    val states = MascotRules.contextualUnlockStates(setOf("Travel Korean", "カフェ会話")).associateBy { it.prop }
    assertTrue(states.getValue(MascotProp.TRAVEL_CASE).isUnlocked)
    assertTrue(states.getValue(MascotProp.COFFEE_CUP).isUnlocked)
    assertFalse(states.getValue(MascotProp.MICROPHONE).isUnlocked)
    assertFalse(states.getValue(MascotProp.HEART_BALLOON).isUnlocked)
    assertFalse(states.getValue(MascotProp.FOOD_PLATE).isUnlocked)
    assertFalse(states.getValue(MascotProp.RIBBON).isUnlocked)
    assertTrue(MascotRules.contextualUnlockStates(setOf("韓国料理")).first { it.prop == MascotProp.FOOD_PLATE }.isUnlocked)
  }

  @Test
  fun closetRowsFollowIOSOrder() {
    val rows = MascotRules.evaluatedUnlocks(0, 0, 0, emptySet()).map { it.prop }
    assertEquals(
      listOf(
        MascotProp.LIGHTSTICK, MascotProp.GRAD_CAP, MascotProp.HEADPHONES, MascotProp.TRAVEL_CASE,
        MascotProp.COFFEE_CUP, MascotProp.MICROPHONE, MascotProp.HEART_BALLOON, MascotProp.FOOD_PLATE,
        MascotProp.RIBBON, MascotProp.GLASSES, MascotProp.GAME_CENTER_TROPHY,
      ),
      rows,
    )
  }

  @Test
  fun earnedPropsStayUnlockedAfterSignalsDisappearAndRelaunch() {
    val prefs = InMemoryPrefs()
    val companion = store(prefs)
    companion.registerUnlocks(MascotRules.unlockStates(streak = 3, chaptersCleared = 3, decksInstalled = 7))
    assertTrue(companion.hasUnlocked(MascotProp.LIGHTSTICK))
    assertTrue(companion.hasUnlocked(MascotProp.GRAD_CAP))
    assertTrue(companion.hasUnlocked(MascotProp.HEADPHONES))

    val restored = store(prefs)
    val currentlyLocked = MascotRules.unlockStates(streak = 0, chaptersCleared = 0, decksInstalled = 0)
    assertTrue(currentlyLocked.none { it.isUnlocked })
    assertTrue(currentlyLocked.all { restored.hasUnlocked(it.prop) })
    assertTrue(MascotRules.mergeEarned(currentlyLocked, restored.unlockedProps.value).all { it.isUnlocked })
    assertEquals("[\"gradCap\",\"headphones\",\"lightstick\"]", prefs.getString("mascot.unlocked_props", null))
  }

  @Test
  fun registerCurrentUnlocksUsesPushedSignals() {
    val companion = store()
    companion.updateClearedChapters(3)
    companion.updateInstalledDecks(2, setOf("恋愛ドラマ"))
    companion.registerCurrentUnlocks()
    assertTrue(companion.hasUnlocked(MascotProp.GRAD_CAP))
    assertTrue(companion.hasUnlocked(MascotProp.HEART_BALLOON))
    assertFalse(companion.hasUnlocked(MascotProp.LIGHTSTICK))
    assertEquals(MascotStage.CHICK, companion.signals.value.stage)
  }

  @Test
  fun gameCenterScorePermanentlyUnlocksTrophyProp() {
    val prefs = InMemoryPrefs()
    val companion = store(prefs)
    assertFalse(companion.hasUnlocked(MascotProp.GAME_CENTER_TROPHY))
    companion.registerGameCenterScoreSubmission()
    assertTrue(companion.hasUnlocked(MascotProp.GAME_CENTER_TROPHY))
    assertTrue(store(prefs).hasUnlocked(MascotProp.GAME_CENTER_TROPHY))
    assertEquals("closet.cond_game_center", MascotRules.gameCenterUnlockState.conditionKey)
  }

  @Test
  fun streakBreakIsPresentedOncePerDay() {
    val prefs = InMemoryPrefs()
    val companion = store(prefs)
    assertFalse(companion.consumeStreakBreak(currentStreak = 4, day = "2026-09-29"))
    assertTrue(companion.consumeStreakBreak(currentStreak = 0, day = "2026-09-30"))
    // Same day, streak still broken: already presented.
    assertFalse(companion.consumeStreakBreak(currentStreak = 0, day = "2026-09-30"))
    // Previous is now 0, so a new day with 0 does not sulk again.
    assertFalse(companion.consumeStreakBreak(currentStreak = 0, day = "2026-10-01"))
    assertEquals("2026-09-30", prefs.getString("mascot.streak_break_presented_day", null))
  }

  @Test
  fun homeTimeMoodMatchesIOS() {
    fun mood(m: MascotMood = MascotMood.IDLE, sulk: Boolean = false, home: Boolean = true, clears: Int = 0, hour: Int = 12) =
      MascotRules.effectiveMood(m, sulk, home, clears, hour)
    assertEquals(MascotMood.SULK, mood(sulk = true, m = MascotMood.HAPPY))
    assertEquals(MascotMood.SATISFIED, mood(clears = 3, hour = 23))
    assertEquals(MascotMood.SLEEPY, mood(hour = 21))
    assertEquals(MascotMood.SLEEPY, mood(hour = 4))
    assertEquals(MascotMood.IDLE, mood(hour = 5))
    assertEquals(MascotMood.HAPPY, mood(m = MascotMood.HAPPY, hour = 23))
    assertEquals(MascotMood.IDLE, mood(home = false, hour = 23, clears = 5))
  }

  @Test
  fun roosterWearsGradCapAutomatically() {
    assertEquals(MascotProp.GRAD_CAP, MascotRules.automaticProp(MascotStage.ROOSTER, MascotProp.NONE))
    assertEquals(MascotProp.LIGHTSTICK, MascotRules.automaticProp(MascotStage.CHICK, MascotProp.LIGHTSTICK))
    assertEquals(MascotStage.CHICK, MascotRules.closetPreviewStage(MascotStage.CRACKING))
    assertEquals(MascotStage.ROOSTER, MascotRules.closetPreviewStage(MascotStage.ROOSTER))
  }

  @Test
  fun eggPatternForGoal() {
    assertEquals(MascotEggPattern.HEARTS, MascotEggPattern.forGoal("oshi"))
    assertEquals(MascotEggPattern.HEARTS, MascotEggPattern.forGoal("trends"))
    assertEquals(MascotEggPattern.STARS, MascotEggPattern.forGoal("topik"))
    assertEquals(MascotEggPattern.STARS, MascotEggPattern.forGoal("exam"))
    assertEquals(MascotEggPattern.POLKA, MascotEggPattern.forGoal("travel"))
    assertEquals(MascotEggPattern.PLAIN, MascotEggPattern.forGoal(null))
    assertEquals(MascotEggPattern.PLAIN, store().eggPattern.value)
  }

  @Test
  fun fanColorChecksumIsStable() {
    assertEquals(MascotFanColor.PINK, MascotFanColor.forDeckTags(emptyList()))
    // "a" = 97 → 97 % 6 = 1 → lavender
    assertEquals(MascotFanColor.LAVENDER, MascotFanColor.forDeckTags(listOf("a")))
    // "a|b": ((1*31+124)%6=5, (5*31+98)%6=1) → lavender
    assertEquals(MascotFanColor.LAVENDER, MascotFanColor.forDeckTags(listOf("a", "b")))
  }

  @Test
  fun persistedKeysMatchIOS() {
    assertEquals(
      listOf(
        "mascot.name", "mascot.prop_selection", "mascot.celebrated_stage", "mascot.typed_jamo_count",
        "mascot.egg_pattern", "mascot.consecutive_lesson_clears", "mascot.last_known_streak",
        "mascot.streak_break_presented_day", "mascot.mistake_counts", "mascot.unlocked_props",
      ),
      MascotCompanionStore.ALL_KEYS,
    )
    assertEquals("closet.prop_game_center", MascotProp.GAME_CENTER_TROPHY.labelKey)
    assertEquals("growth.hatched", MascotStage.HATCHING.celebrationTitleKey())
    assertEquals("growth.master", MascotStage.ROOSTER.celebrationTitleKey())
    assertEquals("growth.advanced", MascotStage.CHICK.celebrationTitleKey())
  }
}
