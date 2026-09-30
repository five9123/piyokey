package app.piyokey.android.data.onboarding

import app.piyokey.android.data.DataTestSupport
import app.piyokey.android.data.persistence.InMemoryKeyValueStore
import app.piyokey.android.data.retention.RandomWordPracticeHistory
import app.piyokey.core.domain.OnboardingGoal
import app.piyokey.core.domain.OnboardingLevel
import app.piyokey.core.domain.OnboardingSnapshot
import app.piyokey.core.domain.OnboardingStep
import app.piyokey.core.domain.OnboardingStoreException
import app.piyokey.core.domain.RandomWordPracticeSession
import app.piyokey.core.domain.RandomWordPracticeSource
import app.piyokey.core.deckkit.DeckItem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OnboardingStoreTest {
  private val defaults = InMemoryKeyValueStore()
  private val store = OnboardingStore(defaults)

  @Test
  fun storePersistsGoalLevelStepAndCompletion() {
    val snapshot = OnboardingSnapshot.EMPTY.copy(
      selectedGoal = OnboardingGoal.TRAVEL, selectedLevel = OnboardingLevel.WORDS, step = OnboardingStep.LESSON, isCompleted = true,
    )
    store.save(snapshot)
    assertEquals(snapshot, store.load())
    assertTrue(store.hasStoredState)
    assertEquals(defaults.getString(OnboardingStore.STATE_KEY), defaults.getString(OnboardingStore.STATE_BACKUP_KEY))
  }

  @Test
  fun libraryPersistsSkippedCompletionLevelAndStepAcrossRelaunch() {
    val library = OnboardingLibrary(store) { false }
    assertTrue(library.shouldPresent)
    library.select(OnboardingGoal.TRENDS)
    library.selectLevel(OnboardingLevel.SENTENCES)
    library.move(OnboardingStep.LEVEL)
    val restored = OnboardingLibrary(store) { false }
    assertEquals(OnboardingGoal.TRENDS, restored.selectedGoal)
    assertEquals(OnboardingLevel.SENTENCES, restored.selectedLevel)
    assertEquals(OnboardingStep.LEVEL, restored.snapshot.value.step)
    assertEquals(OnboardingGoal.TRENDS.preferredTags, restored.preferredTags)
    restored.complete(skipped = true)
    assertFalse(restored.shouldPresent)
    assertTrue(restored.snapshot.value.wasSkipped)
    assertEquals(OnboardingLevel.SENTENCES, restored.selectedLevel)
  }

  @Test
  fun legacyUserMigratesWithoutSeeingOnboarding() {
    val library = OnboardingLibrary(store) { true }
    assertFalse(library.shouldPresent)
    assertTrue(library.snapshot.value.isCompleted)
    assertTrue(library.snapshot.value.wasSkipped)
    assertEquals(library.snapshot.value, store.load())
    // Stored state wins over legacy detection afterwards.
    assertEquals(library.snapshot.value, OnboardingLibrary(store) { error("not consulted") }.snapshot.value)
  }

  @Test
  fun legacyDetectorLooksAtKeyboardPrefsAndHancoFiles() {
    val root = File(DataTestSupport.tempDir(), "Hanco")
    assertFalse(OnboardingLegacyDataDetector.hasExistingData(defaults, root))
    File(root, "Progress").mkdirs()
    assertFalse(OnboardingLegacyDataDetector.hasExistingData(defaults, root))
    File(root, "Progress/game-progress.json").writeText("{}")
    assertTrue(OnboardingLegacyDataDetector.hasExistingData(defaults, root))
    val other = InMemoryKeyValueStore().also { it.putBoolean("keyboard.haptics_enabled", false) }
    assertTrue(OnboardingLegacyDataDetector.hasExistingData(other, File(root, "missing")))
  }

  @Test
  fun unsupportedSchemaIsRejectedWithoutTouchingBackup() {
    defaults.putString(OnboardingStore.STATE_KEY, """{"schema_version":99,"is_completed":false,"was_skipped":false,"step":1}""")
    assertEquals(99, assertFailsWith<OnboardingStoreException.UnsupportedSchema> { store.load() }.version)
    assertFalse(defaults.contains(OnboardingStore.STATE_CORRUPT_KEY))
  }

  @Test
  fun legacyPrimarySeedsBackupAndCorruptPrimaryRestoresFromBackup() {
    defaults.putString(OnboardingStore.STATE_KEY, """{"schema_version":1,"is_completed":false,"was_skipped":false,"selected_goal":"oshi","step":2}""")
    assertEquals(OnboardingGoal.TRENDS, store.load().selectedGoal)
    assertEquals(defaults.getString(OnboardingStore.STATE_KEY), defaults.getString(OnboardingStore.STATE_BACKUP_KEY))

    store.save(OnboardingSnapshot.EMPTY.copy(isCompleted = true, selectedGoal = OnboardingGoal.TRENDS))
    defaults.putString(OnboardingStore.STATE_KEY, "broken")
    val recovered = store.load()
    assertTrue(recovered.isCompleted)
    assertEquals(OnboardingGoal.TRENDS, recovered.selectedGoal)
    assertEquals("broken", defaults.getString(OnboardingStore.STATE_CORRUPT_KEY))
    assertEquals(defaults.getString(OnboardingStore.STATE_KEY), defaults.getString(OnboardingStore.STATE_BACKUP_KEY))

    defaults.putString(OnboardingStore.STATE_KEY, "broken")
    defaults.putString(OnboardingStore.STATE_BACKUP_KEY, "also-broken")
    assertFailsWith<Exception> { store.load() }
    assertFalse(defaults.contains(OnboardingStore.STATE_KEY))
    assertFalse(defaults.contains(OnboardingStore.STATE_BACKUP_KEY))
  }

  @Test
  fun resetClearsStateAppTourHomeAndNotificationFlags() {
    val library = OnboardingLibrary(store) { false }
    library.complete(skipped = false)
    library.appTourCompleted = true
    library.homeLearningStarted = true
    library.notificationPermissionRequested = true
    assertTrue(defaults.getBoolean(OnboardingStore.APP_TOUR_COMPLETED_KEY))
    library.reset()
    assertFalse(defaults.getBoolean(OnboardingStore.APP_TOUR_COMPLETED_KEY))
    assertFalse(defaults.getBoolean(OnboardingStore.HOME_LEARNING_STARTED_KEY))
    assertFalse(defaults.getBoolean(OnboardingStore.NOTIFICATION_PERMISSION_REQUESTED_KEY))
    assertFalse(store.hasStoredState)
    assertTrue(library.shouldPresent)
  }

  @Test
  fun randomWordHistoryKeepsLatestTwentyInPreferences() {
    val history = RandomWordPracticeHistory(defaults)
    repeat(5) { index ->
      history.record(
        RandomWordPracticeSession(
          (0 until 5).map { offset ->
            RandomWordPracticeSource(DeckItem("$index-$offset", "단어${index * 5 + offset}", "r", "m"), "deck", emptyList())
          },
        ),
      )
    }
    assertEquals(20, history.recentWordKeys.size)
    assertEquals("단어24", history.recentWordKeys.last())
    assertNotNull(defaults.getString("retention.random_word_practice.recent_words"))
    history.reset()
    assertTrue(history.recentWordKeys.isEmpty())
  }
}
