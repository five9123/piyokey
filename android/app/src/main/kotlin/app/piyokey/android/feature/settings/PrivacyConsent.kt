package app.piyokey.android.feature.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.piyokey.android.BuildConfig
import app.piyokey.android.R
import app.piyokey.android.data.settings.AppSettings
import app.piyokey.android.data.settings.PrivacyNoticePolicy
import app.piyokey.android.platform.analytics.Telemetry
import app.piyokey.android.platform.files.DocumentIO
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.ui.theme.PrimaryButton

/** iOS `PrivacyConsentDecision`. */
enum class PrivacyConsentDecision {
  PARTICIPATE,
  CONTINUE_WITHOUT_SHARING;

  val analyticsEnabled: Boolean get() = this == PARTICIPATE
  val diagnosticsEnabled: Boolean get() = this == PARTICIPATE
}

object PrivacyConsent {
  /** iOS shows the update wording once any earlier notice was reviewed. */
  val isAnalyticsUpdate: Boolean get() = AppSettings.privacyNoticeVersion.value > 0

  /**
   * Applies a notice decision like iOS `AppRootView.applyPrivacyDecision` (minus the
   * `feature_viewed` of the current tab, which the caller captures when [decision] participates).
   */
  fun apply(decision: PrivacyConsentDecision) {
    AppSettings.anonymousAnalyticsEnabled.value = decision.analyticsEnabled
    val diagnostics = PrivacyNoticePolicy.diagnosticsAfterNotice(
      participate = decision.diagnosticsEnabled,
      reviewedVersion = AppSettings.privacyNoticeVersion.value,
      previousDiagnostics = AppSettings.crashDiagnosticsEnabled.value,
    )
    AppSettings.crashDiagnosticsEnabled.value = diagnostics
    AppSettings.privacyNoticeVersion.value = PrivacyNoticePolicy.CURRENT_VERSION
    Telemetry.updateConsent(productAnalytics = decision.analyticsEnabled, crashDiagnostics = diagnostics)
  }
}

/**
 * iOS `PrivacyConsentView`: non-dismissable notice with two choices. Present it full screen
 * (e.g. on the app navigator); back is swallowed like `interactiveDismissDisabled()`.
 * [onDecision] is called once; the presenter applies it (see [PrivacyConsent.apply]) and closes.
 */
@Composable
fun PrivacyConsentScreen(
  onDecision: (PrivacyConsentDecision) -> Unit,
  modifier: Modifier = Modifier,
  isAnalyticsUpdate: Boolean = PrivacyConsent.isAnalyticsUpdate,
) {
  val colors = Piyo.colors
  val context = LocalContext.current
  val activity = LocalActivity.current
  BackHandler(enabled = true) { }

  Column(
    modifier
      .fillMaxSize()
      .background(Brush.linearGradient(listOf(colors.backgroundTop, colors.backgroundBottom)))
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("privacy_consent.screen"),
  ) {
    Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
      Text(
        stringResource(R.string.privacy_consent_navigation_title),
        style = PiyoType.headline(),
        modifier = Modifier.semantics { heading() },
      )
    }
    Column(
      Modifier
        .weight(1f)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .testTag("privacy_consent.scroll")
        .padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        Modifier.widthIn(max = 680.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
          Icon(Icons.Filled.PanTool, contentDescription = null, tint = colors.accent, modifier = Modifier.size(30.dp))
          Text(
            stringResource(R.string.privacy_consent_title),
            style = PiyoType.title2().copy(fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.semantics { heading() },
          )
          Text(
            stringResource(
              if (isAnalyticsUpdate) R.string.privacy_consent_update_introduction else R.string.privacy_consent_introduction,
            ),
            style = PiyoType.body().copy(color = colors.mutedInk),
          )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          InformationRow(
            title = stringResource(R.string.privacy_consent_usage_title),
            detail = stringResource(R.string.privacy_consent_usage_detail),
            icon = Icons.Filled.BarChart,
            identifier = "privacy_consent.usage_information",
          )
          InformationRow(
            title = stringResource(R.string.privacy_consent_error_title),
            detail = stringResource(
              if (isAnalyticsUpdate) R.string.privacy_consent_existing_diagnostics_detail else R.string.privacy_consent_error_detail,
            ),
            icon = Icons.Filled.MonitorHeart,
            identifier = "privacy_consent.error_information",
          )
        }
        Text(
          stringResource(R.string.privacy_consent_excluded_data),
          style = PiyoType.footnote().copy(fontWeight = FontWeight.SemiBold),
          modifier = Modifier
            .fillMaxWidth()
            .background(colors.accentSoft.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(13.dp)
            .testTag("privacy_consent.excluded_data"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(stringResource(R.string.privacy_consent_settings_note), style = PiyoType.footnote().copy(color = colors.mutedInk))
          Row(
            Modifier
              .defaultMinSize(minHeight = 44.dp)
              .clickable(role = Role.Button) { DocumentIO.openUrl(activity ?: context, BuildConfig.PRIVACY_URL) }
              .testTag("privacy_consent.privacy_policy"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(Icons.Filled.NorthEast, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
            Text(
              stringResource(R.string.settings_privacy_policy),
              style = PiyoType.subheadline().copy(color = colors.accent, fontWeight = FontWeight.SemiBold),
            )
          }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          PrimaryButton(
            text = stringResource(R.string.privacy_consent_participate_and_continue),
            onClick = { onDecision(PrivacyConsentDecision.PARTICIPATE) },
            modifier = Modifier.fillMaxWidth().testTag("privacy_consent.participate_and_continue"),
          )
          Box(
            Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 44.dp)
              .clickable(role = Role.Button) { onDecision(PrivacyConsentDecision.CONTINUE_WITHOUT_SHARING) }
              .testTag("privacy_consent.continue_without_sharing"),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              stringResource(R.string.privacy_consent_continue_without_sharing),
              style = PiyoType.subheadline().copy(color = colors.mutedInk, fontWeight = FontWeight.SemiBold),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun InformationRow(title: String, detail: String, icon: ImageVector, identifier: String) {
  val colors = Piyo.colors
  Row(
    Modifier
      .fillMaxWidth()
      .background(colors.card, RoundedCornerShape(16.dp))
      .semantics(mergeDescendants = true) {}
      .testTag(identifier)
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = PiyoType.subheadline().copy(fontWeight = FontWeight.Bold))
      Text(detail, style = PiyoType.footnote().copy(color = colors.mutedInk))
    }
  }
}
