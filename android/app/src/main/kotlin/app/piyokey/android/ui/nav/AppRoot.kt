package app.piyokey.android.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.piyokey.android.feature.onboarding.RootGate
import app.piyokey.android.ui.theme.PiyoBackground

/** Root: gate (onboarding → hatch → tabs) plus the app-level cover stack drawn above it. */
@Composable
fun AppRoot() {
  val appNavigator = remember { Navigator() }
  CompositionLocalProvider(LocalAppNavigator provides appNavigator) {
    PiyoBackground {
      Box(Modifier.fillMaxSize()) {
        RootGate { MainTabs() }
        if (appNavigator.depth > 0) {
          PiyoBackground { appNavigator.RenderStack(allowRootBack = true) }
        }
      }
    }
  }
}
