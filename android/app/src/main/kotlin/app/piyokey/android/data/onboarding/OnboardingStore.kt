package app.piyokey.android.data.onboarding

import app.piyokey.android.data.persistence.KeyValueStore
import app.piyokey.core.domain.OnboardingGoal
import app.piyokey.core.domain.OnboardingLevel
import app.piyokey.core.domain.OnboardingSnapshot
import app.piyokey.core.domain.OnboardingStep
import app.piyokey.core.domain.OnboardingStoreException
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Onboarding state in preferences with the iOS `UserDefaults` keys (iOS `OnboardingStore`):
 * `onboarding.state` JSON plus `.backup` / `.corrupt` copies, and the app-tour / home /
 * notification flags. The JSON is stored as a string (iOS stores `Data`).
 */
class OnboardingStore(private val defaults: KeyValueStore) {
  val hasStoredState: Boolean get() = defaults.getString(STATE_KEY) != null || defaults.getString(STATE_BACKUP_KEY) != null

  fun load(): OnboardingSnapshot {
    val data = defaults.getString(STATE_KEY)
    if (data == null) {
      val backup = defaults.getString(STATE_BACKUP_KEY) ?: return OnboardingSnapshot.EMPTY
      val snapshot = decode(backup)
      defaults.putString(STATE_KEY, backup)
      return snapshot
    }
    try {
      val snapshot = decode(data)
      if (defaults.getString(STATE_BACKUP_KEY) == null) defaults.putString(STATE_BACKUP_KEY, data)
      return snapshot
    } catch (primaryError: Exception) {
      if (primaryError is OnboardingStoreException) throw primaryError
      val backup = defaults.getString(STATE_BACKUP_KEY)
      if (backup == null) {
        defaults.putString(STATE_CORRUPT_KEY, data)
        defaults.remove(STATE_KEY)
        throw primaryError
      }
      try {
        val snapshot = decode(backup)
        defaults.putString(STATE_CORRUPT_KEY, data)
        defaults.putString(STATE_KEY, backup)
        return snapshot
      } catch (_: Exception) {
        defaults.putString(STATE_CORRUPT_KEY, data)
        defaults.remove(STATE_KEY, STATE_BACKUP_KEY)
        throw primaryError
      }
    }
  }

  fun save(snapshot: OnboardingSnapshot) {
    val data = json.encodeToString(OnboardingSnapshot.serializer(), snapshot)
    defaults.putString(STATE_KEY, data)
    defaults.putString(STATE_BACKUP_KEY, data)
  }

  var appTourCompleted: Boolean
    get() = defaults.getBoolean(APP_TOUR_COMPLETED_KEY)
    set(value) = defaults.putBoolean(APP_TOUR_COMPLETED_KEY, value)

  var homeLearningStarted: Boolean
    get() = defaults.getBoolean(HOME_LEARNING_STARTED_KEY)
    set(value) = defaults.putBoolean(HOME_LEARNING_STARTED_KEY, value)

  var notificationPermissionRequested: Boolean
    get() = defaults.getBoolean(NOTIFICATION_PERMISSION_REQUESTED_KEY)
    set(value) = defaults.putBoolean(NOTIFICATION_PERMISSION_REQUESTED_KEY, value)

  fun reset() {
    defaults.remove(
      STATE_KEY, STATE_BACKUP_KEY, STATE_CORRUPT_KEY, APP_TOUR_COMPLETED_KEY,
      HOME_LEARNING_STARTED_KEY, NOTIFICATION_PERMISSION_REQUESTED_KEY,
    )
  }

  private fun decode(data: String): OnboardingSnapshot {
    val snapshot = json.decodeFromString(OnboardingSnapshot.serializer(), data)
    if (snapshot.schemaVersion != OnboardingSnapshot.CURRENT_SCHEMA_VERSION) {
      throw OnboardingStoreException.UnsupportedSchema(snapshot.schemaVersion)
    }
    return snapshot
  }

  companion object {
    const val STATE_KEY = "onboarding.state"
    const val STATE_BACKUP_KEY = "onboarding.state.backup"
    const val STATE_CORRUPT_KEY = "onboarding.state.corrupt"
    const val APP_TOUR_COMPLETED_KEY = "onboarding.app_tour.completed"
    const val HOME_LEARNING_STARTED_KEY = "onboarding.home_learning_started"
    const val NOTIFICATION_PERMISSION_REQUESTED_KEY = "onboarding.notification_permission_requested"

    /** iOS `KeyboardPreferenceKeys.all`: any of these means the app was used before onboarding existed. */
    val KEYBOARD_PREFERENCE_KEYS = listOf(
      "keyboard.shows_key_guide",
      "keyboard.shows_roman_hints",
      "keyboard.haptics_enabled",
      "keyboard.input_mode_default",
      "keyboard.shows_physical_keyboard_guide",
      "keyboard.builtin_layout_default",
    )

    private val json = Json {
      ignoreUnknownKeys = true
      explicitNulls = false
      encodeDefaults = true
    }
  }
}

/** iOS `OnboardingLegacyDataDetector`: keyboard prefs or any file under `filesDir/Hanco`. */
object OnboardingLegacyDataDetector {
  fun hasExistingData(defaults: KeyValueStore, hancoRoot: File): Boolean {
    if (OnboardingStore.KEYBOARD_PREFERENCE_KEYS.any(defaults::contains)) return true
    return hancoRoot.walkTopDown().any { it.isFile }
  }
}

/**
 * Observable onboarding progress (iOS `OnboardingLibrary`). A user with legacy data and no stored
 * state is migrated as completed+skipped so they never see onboarding.
 */
class OnboardingLibrary(private val store: OnboardingStore, hasLegacyData: () -> Boolean) {
  private val snapshotState: MutableStateFlow<OnboardingSnapshot>
  private val saveFailedState = MutableStateFlow(false)
  private var retryAction: (() -> Unit)? = null

  init {
    if (!store.hasStoredState && hasLegacyData()) {
      runCatching { store.save(OnboardingSnapshot.EMPTY.copy(isCompleted = true, wasSkipped = true)) }
    }
    snapshotState = MutableStateFlow(runCatching { store.load() }.getOrDefault(OnboardingSnapshot.EMPTY))
  }

  val snapshot: StateFlow<OnboardingSnapshot> = snapshotState.asStateFlow()
  val saveFailed: StateFlow<Boolean> = saveFailedState.asStateFlow()

  val shouldPresent: Boolean get() = !snapshotState.value.isCompleted
  val selectedGoal: OnboardingGoal? get() = snapshotState.value.selectedGoal
  val selectedLevel: OnboardingLevel? get() = snapshotState.value.selectedLevel
  val preferredTags: List<String> get() = snapshotState.value.preferredTags

  /** Flags kept outside the snapshot (same keys as iOS). */
  var appTourCompleted: Boolean
    get() = store.appTourCompleted
    set(value) {
      store.appTourCompleted = value
    }
  var homeLearningStarted: Boolean
    get() = store.homeLearningStarted
    set(value) {
      store.homeLearningStarted = value
    }
  var notificationPermissionRequested: Boolean
    get() = store.notificationPermissionRequested
    set(value) {
      store.notificationPermissionRequested = value
    }

  fun select(goal: OnboardingGoal) = mutate { it.copy(selectedGoal = goal) }
  fun selectLevel(level: OnboardingLevel) = mutate { it.copy(selectedLevel = level) }
  fun move(step: OnboardingStep) = mutate { it.copy(step = step) }
  fun complete(skipped: Boolean) = mutate { it.copy(isCompleted = true, wasSkipped = skipped) }

  fun retryLastSave() {
    retryAction?.invoke()
  }

  /** Clears onboarding state and flags (debug/reset). */
  fun reset() {
    store.reset()
    snapshotState.value = OnboardingSnapshot.EMPTY
  }

  private fun mutate(update: (OnboardingSnapshot) -> OnboardingSnapshot) {
    val updated = update(snapshotState.value)
    try {
      store.save(updated)
      snapshotState.value = updated
      saveFailedState.value = false
      retryAction = null
    } catch (_: Exception) {
      saveFailedState.value = true
      retryAction = { mutate(update) }
    }
  }
}
