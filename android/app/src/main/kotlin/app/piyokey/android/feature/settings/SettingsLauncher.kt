package app.piyokey.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.piyokey.android.R
import app.piyokey.android.feature.onboarding.AppTourTarget
import app.piyokey.android.feature.onboarding.appTourTarget
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.nav.Navigator
import app.piyokey.android.ui.nav.Route
import app.piyokey.android.ui.theme.Piyo

/** Opens the global settings screen (iOS root `.sheet { SettingsView(showsCloseButton: true) }`). */
object SettingsLauncher {
  fun open(nav: Navigator) {
    nav.push(SettingsRoute(showsCloseButton = true))
  }
}

/** Full-screen settings destination on the app navigator. "Done" pops it. */
class SettingsRoute(private val showsCloseButton: Boolean = true) : Route {
  @Composable
  override fun Content() {
    SettingsScreen(showsCloseButton = showsCloseButton)
  }
}

/**
 * Toolbar gear on tab roots (iOS `RootSettingsToolbarModifier`): 32dp translucent card circle with
 * an accent gear inside a 44dp target; it is the app tour's settings spotlight.
 */
@Composable
fun SettingsGearButton(modifier: Modifier = Modifier) {
  val navigator = LocalAppNavigator.current
  val colors = Piyo.colors
  val label = stringResource(R.string.settings_navigation_title)
  Box(
    modifier
      .size(44.dp)
      .appTourTarget(AppTourTarget.SETTINGS)
      .clickable(role = Role.Button) { SettingsLauncher.open(navigator) }
      .semantics { contentDescription = label }
      .testTag("root.settings"),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      Modifier.size(32.dp).background(colors.card.copy(alpha = 0.9f), CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Filled.Settings, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
    }
  }
}
