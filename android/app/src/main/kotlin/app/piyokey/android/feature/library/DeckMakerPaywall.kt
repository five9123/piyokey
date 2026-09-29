package app.piyokey.android.feature.library

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.piyokey.android.BuildConfig
import app.piyokey.android.R
import app.piyokey.android.feature.discover.InlineTopBar
import app.piyokey.android.platform.analytics.Telemetry
import app.piyokey.android.platform.billing.ProPurchaseActivity
import app.piyokey.android.platform.billing.ProPurchaseNotice
import app.piyokey.android.platform.billing.ProStore
import app.piyokey.android.platform.files.DocumentIO
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.ui.theme.centeredContent
import kotlinx.coroutines.launch

/** Google Play's terms apply to Play purchases (iOS links Apple's standard EULA). */
internal const val PLAY_TERMS_URL = "https://play.google.com/about/play-terms/"

private class PaywallFeature(val icon: ImageVector, val title: Int, val detail: Int)

/** iOS `DeckMakerPaywallView` presented as a full-height sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckMakerPaywallSheet(onDismiss: () -> Unit, onAccessGranted: () -> Unit) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = Piyo.colors.backgroundTop,
    dragHandle = null,
  ) {
    DeckMakerPaywall(onClose = onDismiss, onAccessGranted = onAccessGranted)
  }
}

@Composable
fun DeckMakerPaywall(onClose: () -> Unit, onAccessGranted: () -> Unit) {
  val colors = Piyo.colors
  val context = LocalContext.current
  val activity = LocalActivity.current
  val scope = rememberCoroutineScope()
  val hasAccess by ProStore.hasAccess.collectAsStateWithLifecycle()
  val product by ProStore.product.collectAsStateWithLifecycle()
  val purchaseActivity by ProStore.activity.collectAsStateWithLifecycle()
  val notice by ProStore.notice.collectAsStateWithLifecycle()
  val isBusy = purchaseActivity.isBusy
  var didCaptureView by rememberSaveable { mutableStateOfFalse() }

  LaunchedEffect(Unit) {
    if (!didCaptureView) {
      didCaptureView = true
      Telemetry.purchaseFlow("viewed")
    }
    ProStore.prepare()
  }

  val features = listOf(
    PaywallFeature(Icons.Rounded.LibraryAdd, R.string.deck_maker_paywall_feature_unlimitedImports_title, R.string.deck_maker_paywall_feature_unlimitedImports_detail),
    PaywallFeature(Icons.Rounded.LibraryAdd, R.string.deck_maker_paywall_feature_create_title, R.string.deck_maker_paywall_feature_create_detail),
    PaywallFeature(Icons.Rounded.EditNote, R.string.deck_maker_paywall_feature_edit_title, R.string.library_paywall_feature_edit_detail),
    PaywallFeature(Icons.Rounded.ContentCopy, R.string.deck_maker_paywall_feature_copyOfficial_title, R.string.deck_maker_paywall_feature_copyOfficial_detail),
    PaywallFeature(Icons.Rounded.AllInclusive, R.string.deck_maker_paywall_feature_lifetime_title, R.string.deck_maker_paywall_feature_lifetime_detail),
  )

  val primaryTitle = when {
    hasAccess -> stringResource(R.string.deck_maker_paywall_already_owned)
    product?.displayPrice.isNullOrEmpty() ->
      if (purchaseActivity == ProPurchaseActivity.LOADING) stringResource(R.string.deck_maker_paywall_loading)
      else stringResource(R.string.deck_maker_paywall_retry)
    else -> stringResource(R.string.deck_maker_paywall_purchase_format, product!!.displayPrice)
  }

  fun primaryAction() {
    if (hasAccess) {
      onAccessGranted()
      onClose()
      return
    }
    scope.launch {
      if (ProStore.product.value == null) {
        ProStore.prepare(forceReload = true)
        return@launch
      }
      val host = activity ?: return@launch
      if (ProStore.purchase(host)) {
        onAccessGranted()
        onClose()
      }
    }
  }

  Column(Modifier.fillMaxSize().testTag("deck_maker.paywall.screen")) {
    InlineTopBar(
      stringResource(R.string.deck_maker_paywall_navigation_title),
      onBack = null,
      leading = {
        TextButton(onClick = onClose, modifier = Modifier.testTag("deck_maker.paywall.close")) {
          Text(stringResource(R.string.common_close), color = colors.accent)
        }
      },
    )
    Column(
      Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(top = 16.dp, bottom = 20.dp)
        .centeredContent(Piyo.metrics.formContentMaxWidth),
      verticalArrangement = Arrangement.spacedBy(22.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        Modifier.size(86.dp).clip(RoundedCornerShape(28.dp)).background(colors.accentSoft.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
      ) { Icon(Icons.Rounded.LibraryAdd, contentDescription = null, tint = colors.accent, modifier = Modifier.size(42.dp)) }
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.deck_maker_paywall_title), style = PiyoType.style(28f, FontWeight.ExtraBold), textAlign = TextAlign.Center)
        Text(
          stringResource(R.string.deck_maker_paywall_subtitle),
          style = PiyoType.style(15f, FontWeight.Medium).copy(color = colors.mutedInk),
          textAlign = TextAlign.Center,
        )
      }
      Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(colors.card).padding(horizontal = 18.dp),
      ) {
        features.forEachIndexed { index, feature ->
          if (index > 0) HorizontalDivider(color = colors.mutedInk.copy(alpha = 0.2f))
          Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
              Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(colors.accentSoft.copy(alpha = 0.48f)),
              contentAlignment = Alignment.Center,
            ) { Icon(feature.icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp)) }
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(3.dp)) {
              Text(stringResource(feature.title), style = PiyoType.style(15f, FontWeight.Bold))
              Text(stringResource(feature.detail), style = PiyoType.style(12.5f, FontWeight.Medium).copy(color = colors.mutedInk))
            }
          }
        }
      }
      Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.card.copy(alpha = 0.76f)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(Icons.Rounded.Verified, contentDescription = null, tint = colors.successText, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.deck_maker_paywall_free_import_note), style = PiyoType.style(13f, FontWeight.SemiBold).copy(color = colors.mutedInk))
      }
    }
    // Purchase controls (iOS safe-area inset).
    Column(
      Modifier
        .fillMaxWidth()
        .background(colors.card.copy(alpha = 0.94f))
        .navigationBarsPadding()
        .padding(horizontal = 20.dp)
        .padding(top = 14.dp, bottom = 8.dp)
        .centeredContent(Piyo.metrics.formContentMaxWidth),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        Modifier
          .fillMaxWidth()
          .heightIn(min = 54.dp)
          .clip(RoundedCornerShape(18.dp))
          .background(colors.accent)
          .alpha(if (isBusy && purchaseActivity == ProPurchaseActivity.RESTORING) 0.55f else 1f)
          .clickable(enabled = !isBusy, role = Role.Button) { primaryAction() }
          .semantics(mergeDescendants = true) {}
          .testTag("deck_maker.paywall.purchase")
          .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (purchaseActivity == ProPurchaseActivity.PURCHASING || purchaseActivity == ProPurchaseActivity.LOADING) {
          CircularProgressIndicator(color = colors.onAccent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else if (hasAccess) {
          Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.onAccent)
        }
        Text(primaryTitle, style = PiyoType.style(17f, FontWeight.ExtraBold).copy(color = colors.onAccent), textAlign = TextAlign.Center, maxLines = 2)
      }
      Row(
        Modifier
          .heightIn(min = 44.dp)
          .clip(RoundedCornerShape(10.dp))
          .clickable(enabled = !isBusy, role = Role.Button) {
            scope.launch {
              if (ProStore.restore()) {
                ProStore.dismissNotice()
                onAccessGranted()
                onClose()
              }
            }
          }
          .testTag("deck_maker.paywall.restore")
          .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        if (purchaseActivity == ProPurchaseActivity.RESTORING) {
          CircularProgressIndicator(color = colors.ink, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        }
        Text(stringResource(R.string.deck_maker_paywall_restore), style = PiyoType.style(14f, FontWeight.SemiBold))
      }
      FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)) {
        LegalLink(stringResource(R.string.deck_maker_paywall_terms)) { DocumentIO.openUrl(context, PLAY_TERMS_URL) }
        LegalLink(stringResource(R.string.deck_maker_paywall_privacy)) { DocumentIO.openUrl(context, BuildConfig.PRIVACY_URL) }
      }
    }
  }

  notice?.let { current ->
    AlertDialog(
      onDismissRequest = { ProStore.dismissNotice() },
      title = { Text(stringResource(current.titleRes)) },
      text = {
        Text(stringResource(if (current == ProPurchaseNotice.NOTHING_TO_RESTORE) R.string.library_restore_empty_message else current.messageRes))
      },
      confirmButton = { TextButton(onClick = { ProStore.dismissNotice() }) { Text(stringResource(R.string.deck_maker_alert_ok)) } },
      containerColor = colors.card,
    )
  }
}

@Composable
private fun LegalLink(text: String, onClick: () -> Unit) {
  Text(
    text,
    style = PiyoType.style(12f, FontWeight.Medium).copy(color = Piyo.colors.mutedInk),
    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onClick).heightIn(min = 44.dp).padding(horizontal = 6.dp, vertical = 14.dp),
  )
}

private fun mutableStateOfFalse() = androidx.compose.runtime.mutableStateOf(false)
