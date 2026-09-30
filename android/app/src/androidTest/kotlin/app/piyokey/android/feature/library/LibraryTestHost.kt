package app.piyokey.android.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.piyokey.android.data.settings.AppLanguage
import app.piyokey.android.data.settings.AppTheme
import app.piyokey.android.data.settings.FontScale
import app.piyokey.android.ui.nav.AppTab
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.nav.LocalTabController
import app.piyokey.android.ui.nav.LocalTabNavigator
import app.piyokey.android.ui.nav.Navigator
import app.piyokey.android.ui.nav.Route
import app.piyokey.android.ui.nav.TabController
import app.piyokey.android.ui.theme.LocalizedApp
import app.piyokey.android.ui.theme.PiyoBackground
import app.piyokey.android.ui.theme.PiyokeyTheme

/** Hosts a tab root the way MainTabs + AppRoot do (tab stack below, app cover stack above). */
@Composable
fun LibraryTestHost(tab: AppTab, root: @Composable () -> Unit) {
  val controller = remember { TabController().also { it.selected = tab } }
  val tabNavigator = remember {
    Navigator(object : Route {
      @Composable
      override fun Content() = root()
    })
  }
  val appNavigator = remember { Navigator() }
  LocalizedApp(AppLanguage.ENGLISH) {
    PiyokeyTheme(AppTheme.LIGHT, FontScale.STANDARD) {
      CompositionLocalProvider(
        LocalTabController provides controller,
        LocalTabNavigator provides tabNavigator,
        LocalAppNavigator provides appNavigator,
      ) {
        PiyoBackground {
          Box(Modifier.fillMaxSize()) {
            tabNavigator.RenderStack()
            if (appNavigator.depth > 0) PiyoBackground { appNavigator.RenderStack(allowRootBack = true) }
          }
        }
      }
    }
  }
}
