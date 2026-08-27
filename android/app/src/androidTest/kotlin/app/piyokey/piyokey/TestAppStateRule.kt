package app.piyokey.piyokey

import androidx.test.platform.app.InstrumentationRegistry
import app.piyokey.core.data.PIYODECK_STAGING_DIRECTORY_NAME
import app.piyokey.core.data.PiyokeyDatabase
import app.piyokey.core.settings.AppPreferencesStore
import app.piyokey.core.settings.AppTheme
import app.piyokey.core.settings.FontScale
import app.piyokey.core.settings.InputMode
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

class TestAppStateRule(
  private val skipOnboarding: Boolean,
  private val freshInstall: Boolean = false,
  private val forceOSIME: Boolean = false,
  private val resetStorage: Boolean = freshInstall,
  private val theme: AppTheme? = null,
  private val fontScale: FontScale? = null,
) : ExternalResource() {
  override fun before() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    if (resetStorage) {
      PiyokeyDatabase.closeSingletonForTesting()
      File(context.filesDir, "datastore/piyokey_preferences.preferences_pb").delete()
      context.deleteDatabase("piyokey.db")
      File(context.filesDir, "piyokey").deleteRecursively()
      File(context.cacheDir, PIYODECK_STAGING_DIRECTORY_NAME).deleteRecursively()
      File(context.cacheDir, "shared_results").deleteRecursively()
    }
    if (skipOnboarding || theme != null || fontScale != null) {
      runBlocking {
        AppPreferencesStore.create(context).update { current ->
          current.copy(
            theme = theme ?: current.theme,
            fontScale = fontScale ?: current.fontScale,
            defaultInputMode = if (skipOnboarding) {
              if (forceOSIME) InputMode.OS_IME else InputMode.BUILTIN
            } else {
              current.defaultInputMode
            },
          )
        }
      }
    }
    context.getSharedPreferences("piyokey_test_overrides", 0)
      .edit()
      .putBoolean("skip_onboarding", skipOnboarding)
      .putBoolean("fresh_onboarding", freshInstall)
      .putBoolean("force_os_ime", forceOSIME)
      .commit()
  }
}
