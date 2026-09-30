package app.piyokey.android.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.piyokey.android.R
import app.piyokey.android.feature.discover.DiscoverTabRoot
import app.piyokey.android.feature.game.GameTabRoot
import app.piyokey.android.feature.home.HomeTabRoot
import app.piyokey.android.feature.library.MyPageTabRoot
import app.piyokey.android.feature.practice.PracticeTabRoot
import app.piyokey.android.ui.theme.Piyo

enum class AppTab(val analyticsValue: String, val labelRes: Int, val icon: ImageVector) {
  HOME("home", R.string.tab_home, Icons.Rounded.Home),
  DISCOVER("discover", R.string.tab_discover, Icons.Rounded.Search),
  PRACTICE("practice", R.string.tab_practice, Icons.Rounded.Keyboard),
  GAME("game", R.string.tab_game, Icons.Rounded.SportsEsports),
  MY_PAGE("my_page", R.string.tab_my_page, Icons.Rounded.Person),
}

/** Tab selection shared with the app tour and deep links. */
class TabController {
  var selected by mutableStateOf(AppTab.HOME)
  /** Badge flags by tab, e.g. My Page shows a dot while a `.typedeck` import is pending. */
  val badges = androidx.compose.runtime.mutableStateMapOf<AppTab, Boolean>()
  /** Invoked on every tab change (analytics `feature_viewed`). */
  var onTabChanged: (AppTab) -> Unit = {}
  internal val navigators = AppTab.entries.associateWith { Navigator(tabRoot(it)) }

  fun navigator(tab: AppTab): Navigator = navigators.getValue(tab)

  fun select(tab: AppTab) {
    if (selected == tab) navigators.getValue(tab).popToRoot() else {
      selected = tab
      onTabChanged(tab)
    }
  }
}

val LocalTabController = staticCompositionLocalOf<TabController> { error("No tab controller") }

private fun tabRoot(tab: AppTab): Route = object : Route {
  @Composable
  override fun Content() {
    when (tab) {
      AppTab.HOME -> HomeTabRoot()
      AppTab.DISCOVER -> DiscoverTabRoot()
      AppTab.PRACTICE -> PracticeTabRoot()
      AppTab.GAME -> GameTabRoot()
      AppTab.MY_PAGE -> MyPageTabRoot()
    }
  }
}

@Composable
fun MainTabs(controller: TabController = remember { TabController() }) {
  val holder = rememberSaveableStateHolder()
  CompositionLocalProvider(LocalTabController provides controller) {
    Column(Modifier.fillMaxSize()) {
      Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
        val tab = controller.selected
        val navigator = controller.navigator(tab)
        holder.SaveableStateProvider(tab.name) {
          CompositionLocalProvider(LocalTabNavigator provides navigator) {
            navigator.RenderStack()
          }
        }
      }
      val colors = Piyo.colors
      NavigationBar(containerColor = colors.card, modifier = Modifier.navigationBarsPadding()) {
        AppTab.entries.forEach { tab ->
          NavigationBarItem(
            selected = controller.selected == tab,
            onClick = { controller.select(tab) },
            icon = {
              BadgedBox(badge = { if (controller.badges[tab] == true) Badge() }) {
                Icon(tab.icon, contentDescription = null)
              }
            },
            label = { Text(stringResource(tab.labelRes)) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = colors.accent,
              selectedTextColor = colors.accent,
              indicatorColor = colors.accentSoft,
              unselectedIconColor = colors.mutedInk,
              unselectedTextColor = colors.mutedInk,
            ),
          )
        }
      }
    }
  }
}
