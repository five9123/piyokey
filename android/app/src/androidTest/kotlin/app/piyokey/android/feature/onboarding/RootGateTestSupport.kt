package app.piyokey.android.feature.onboarding

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import app.piyokey.android.data.AppData
import app.piyokey.android.data.mascot.MascotStore
import app.piyokey.android.data.settings.AppLanguage
import app.piyokey.android.data.settings.AppSettings
import app.piyokey.android.data.settings.AppTheme
import app.piyokey.android.data.settings.FontScale
import app.piyokey.android.data.settings.Prefs
import app.piyokey.android.ui.nav.AppRoot
import app.piyokey.android.ui.theme.LocalizedApp
import app.piyokey.android.ui.theme.PiyokeyTheme
import app.piyokey.core.domain.HatchOnboardingPolicy
import java.io.File
import kotlinx.coroutines.runBlocking

/**
 * Saves and restores the app state touched by root-gate tests (preferences, curriculum and
 * retention files) so the emulator keeps its data, and offers seeds for the gate stages.
 */
class RootGateTestState {
  private lateinit var savedPrefs: Map<String, *>
  private val dirs = listOf("Curriculum", "Retention")
  private val backupRoot = File(AppData.root.parentFile, "root-gate-test-backup")

  fun save() {
    savedPrefs = HashMap(Prefs.shared.all)
    backupRoot.deleteRecursively()
    dirs.forEach { name ->
      val dir = File(AppData.root, name)
      if (dir.exists()) dir.copyRecursively(File(backupRoot, name), overwrite = true)
    }
    AppSettings.language.value = AppLanguage.ENGLISH.raw
  }

  fun restore() {
    val editor = Prefs.shared.edit().clear()
    savedPrefs.forEach { (key, value) ->
      when (value) {
        is Boolean -> editor.putBoolean(key, value)
        is Int -> editor.putInt(key, value)
        is Long -> editor.putLong(key, value)
        is Float -> editor.putFloat(key, value)
        is String -> editor.putString(key, value)
        is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
      }
    }
    editor.commit()
    dirs.forEach { name ->
      val dir = File(AppData.root, name)
      dir.deleteRecursively()
      File(backupRoot, name).takeIf { it.exists() }?.copyRecursively(dir, overwrite = true)
    }
    backupRoot.deleteRecursively()
    reloadStores()
  }

  /** Fresh install: no onboarding, no curriculum/retention progress, no tour, no notice. */
  fun seedFreshInstall() {
    dirs.forEach { File(AppData.root, it).deleteRecursively() }
    AppData.onboarding.reset()
    AppSettings.privacyNoticeVersion.value = 0
    AppSettings.anonymousAnalyticsEnabled.value = false
    AppSettings.crashDiagnosticsEnabled.value = false
    reloadStores()
  }

  /** Onboarding and the three hatch missions done (tour/notice as given). */
  fun seedHatched(tourCompleted: Boolean, privacyNoticeVersion: Int, notificationRequested: Boolean = true) {
    seedFreshInstall()
    AppData.onboarding.complete(skipped = true)
    runBlocking {
      // CurriculumProgressLibrary.reload() resets its revision while its RevisionWriter keeps the
      // last requested one, so verified writes after a reload are refused until the revision
      // catches up (data-layer issue, reported). Retry until durable.
      HatchOnboardingPolicy.requiredStages.forEach { stage ->
        var attempts = 0
        while (!AppData.curriculum.finishAndWait(stage.id, 3, 100.0) && attempts++ < 50) Unit
      }
      check(HatchOnboardingPolicy.isComplete(AppData.curriculum.completedStageIds)) { "hatch seed failed" }
    }
    AppData.onboarding.appTourCompleted = tourCompleted
    AppData.onboarding.notificationPermissionRequested = notificationRequested
    AppSettings.privacyNoticeVersion.value = privacyNoticeVersion
  }

  private fun reloadStores() {
    AppData.curriculum.reload()
    AppData.retention.reload()
    AppSettings.privacyNoticeVersion.reload()
    AppSettings.anonymousAnalyticsEnabled.reload()
    AppSettings.crashDiagnosticsEnabled.reload()
    AppSettings.language.reload()
    MascotStore.reload()
  }
}

fun ComposeContentTestRule.setAppRoot() {
  setContent {
    LocalizedApp(AppLanguage.ENGLISH) {
      PiyokeyTheme(AppTheme.LIGHT, FontScale.STANDARD) { AppRoot() }
    }
  }
}
