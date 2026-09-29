package app.piyokey.android.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import app.piyokey.android.ui.theme.Piyo
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.piyokey.android.feature.settings.PrivacyConsent
import app.piyokey.android.feature.settings.PrivacyConsentDecision
import app.piyokey.android.feature.settings.PrivacyConsentScreen
import app.piyokey.android.platform.analytics.Telemetry
import app.piyokey.android.ui.theme.PiyoBackground

/**
 * Root presentation of the privacy notice (iOS `.sheet { PrivacyConsentView }` with
 * `interactiveDismissDisabled`): a full-screen, non-dismissible dialog around the settings
 * package's [PrivacyConsentScreen]. The decision is applied like iOS `applyPrivacyDecision`.
 */
@Composable
internal fun RootPrivacyConsentDialog(isAnalyticsUpdate: Boolean, currentFeature: () -> String, onFinished: () -> Unit) {
  Dialog(
    onDismissRequest = {},
    properties = DialogProperties(
      dismissOnBackPress = false,
      dismissOnClickOutside = false,
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false,
    ),
  ) {
    val view = LocalView.current
    val dark = Piyo.colors.isDark
    SideEffect {
      (view.parent as? DialogWindowProvider)?.window?.let { window ->
        WindowCompat.getInsetsController(window, view).apply {
          isAppearanceLightStatusBars = !dark
          isAppearanceLightNavigationBars = !dark
        }
      }
    }
    PiyoBackground {
      PrivacyConsentScreen(
        onDecision = { decision ->
          applyRootPrivacyDecision(decision, currentFeature())
          onFinished()
        },
        isAnalyticsUpdate = isAnalyticsUpdate,
      )
    }
  }
}

/** iOS `AppRootView.applyPrivacyDecision`: store both choices, update SDK consent, re-send the tab view. */
internal fun applyRootPrivacyDecision(decision: PrivacyConsentDecision, currentFeature: String) {
  PrivacyConsent.apply(decision)
  if (decision.analyticsEnabled) Telemetry.featureViewed(currentFeature)
}
