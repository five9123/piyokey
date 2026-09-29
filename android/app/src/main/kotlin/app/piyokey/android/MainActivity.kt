package app.piyokey.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.piyokey.android.data.settings.AppLanguage
import app.piyokey.android.data.settings.AppSettings
import app.piyokey.android.data.settings.AppTheme
import app.piyokey.android.data.settings.FontScale
import app.piyokey.android.ui.nav.AppRoot
import app.piyokey.android.ui.theme.LocalizedApp
import app.piyokey.android.ui.theme.PiyokeyTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AppSettings.migrateLegacyLanguage()
    handleIncoming(intent)
    setContent {
      val themeRaw by AppSettings.theme.flow.collectAsStateWithLifecycle()
      val fontRaw by AppSettings.fontScale.flow.collectAsStateWithLifecycle()
      val languageRaw by AppSettings.language.flow.collectAsStateWithLifecycle()
      val theme = AppTheme.resolved(themeRaw)
      val dark = theme == AppTheme.DARK
      enableEdgeToEdge(
        statusBarStyle = if (dark) SystemBarStyle.dark(0) else SystemBarStyle.light(0, 0),
        navigationBarStyle = if (dark) SystemBarStyle.dark(0) else SystemBarStyle.light(0, 0),
      )
      LocalizedApp(AppLanguage.resolved(languageRaw)) {
        PiyokeyTheme(theme, FontScale.resolved(fontRaw)) {
          AppRoot()
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIncoming(intent)
  }

  private fun handleIncoming(intent: Intent?) {
    if (intent == null) return
    if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND) {
      IncomingDocuments.offer(intent)
    }
  }
}

/** Incoming `.typedeck` VIEW/SEND intents, consumed by the deck-import feature. */
object IncomingDocuments {
  private val state = MutableStateFlow<Intent?>(null)
  val pending: StateFlow<Intent?> get() = state

  fun offer(intent: Intent) {
    state.value = intent
  }

  fun consume(): Intent? = state.value.also { state.value = null }
}
