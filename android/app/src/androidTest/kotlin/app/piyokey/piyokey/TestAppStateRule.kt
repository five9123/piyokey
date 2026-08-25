package app.piyokey.piyokey

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.rules.ExternalResource

class TestAppStateRule(
  private val skipOnboarding: Boolean,
  private val freshInstall: Boolean = false,
  private val forceOSIME: Boolean = false,
) : ExternalResource() {
  override fun before() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    if (freshInstall) {
      File(context.filesDir, "datastore/piyokey_preferences.preferences_pb").delete()
      context.deleteDatabase("piyokey.db")
    }
    context.getSharedPreferences("piyokey_test_overrides", 0)
      .edit()
      .putBoolean("skip_onboarding", skipOnboarding)
      .putBoolean("fresh_onboarding", freshInstall)
      .putBoolean("force_os_ime", forceOSIME)
      .commit()
  }
}
