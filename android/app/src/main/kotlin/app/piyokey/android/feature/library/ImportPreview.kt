package app.piyokey.android.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Drafts
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.IosShare
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.piyokey.android.R
import app.piyokey.android.data.AppData
import app.piyokey.android.data.decks.InstalledDeckSource
import app.piyokey.android.data.decks.PiyokeyProAccessException
import app.piyokey.android.feature.discover.DeckCover
import app.piyokey.android.feature.discover.InlineTopBar
import app.piyokey.android.feature.discover.rememberAppLocale
import app.piyokey.android.feature.discover.rememberCatalogText
import app.piyokey.android.platform.analytics.Telemetry
import app.piyokey.android.platform.billing.ProStore
import app.piyokey.android.platform.files.DocumentIO
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.ui.theme.centeredContent
import app.piyokey.core.deckkit.DeckType
import app.piyokey.core.domain.PiyoDeckCollisionComparison
import app.piyokey.core.domain.PiyoDeckComparisonSide
import app.piyokey.core.domain.PiyoDeckDocumentNotice
import app.piyokey.core.domain.PiyoDeckDocumentRules
import app.piyokey.core.domain.PiyoDeckImportCollision
import app.piyokey.core.domain.library.PiyoDeckImportRules
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/** iOS `PiyoDeckImportPreviewView` as a full-height sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewSheet(candidate: PiyoDeckImportCandidate, coordinator: PiyoDeckImportCoordinator) {
  var busy by remember { mutableStateOf(false) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !busy })
  ModalBottomSheet(
    onDismissRequest = { if (!busy) coordinator.dismissCandidate() },
    sheetState = sheetState,
    containerColor = Piyo.colors.backgroundTop,
    dragHandle = null,
    properties = androidx.compose.material3.ModalBottomSheetProperties(shouldDismissOnBackPress = true),
  ) {
    ImportPreview(candidate, coordinator, onBusyChange = { busy = it })
  }
}

/** Mutations of the import preview, separated for tests (bypasses the Play purchase sheet). */
internal object ImportActions {
  /** Commits the package as an imported user deck. Returns false when the free limit requires Pro. */
  suspend fun install(candidate: PiyoDeckImportCandidate, coordinator: PiyoDeckImportCoordinator, replacing: Boolean): Boolean {
    val library = AppData.deckLibrary
    val pkg = candidate.pkg
    val derivedFrom = PiyoDeckImportRules.derivedFromDeckIdForInstall(replacing, library.record(pkg.deck.deckId)?.derivedFromDeckId)
    return try {
      val installed = library.installUserDeck(
        data = pkg.deckData,
        source = InstalledDeckSource.IMPORTED,
        contentSha256 = pkg.contentSha256,
        packageFormatVersion = pkg.manifest.formatVersion,
        isLocallyModified = false,
        hasPiyokeyProAccess = ProStore.hasAccess.value,
        derivedFromDeckId = derivedFrom,
      )
      AppData.review.reconcile(installed)
      Telemetry.deckMakerAction("imported", installed.items.size, "imported")
      coordinator.finishImport()
      true
    } catch (_: PiyokeyProAccessException.FreeUserDeckLimitReached) {
      false
    } catch (_: Exception) {
      coordinator.dismissCandidate(showNext = false)
      coordinator.reportSaveFailure()
      true
    }
  }

  /** iOS `importAsCopy` (Pro): fresh ids, created source. */
  suspend fun importAsCopy(candidate: PiyoDeckImportCandidate, coordinator: PiyoDeckImportCoordinator) {
    try {
      ProStore.requireAccess()
      val copy = PiyoDeckDocumentRules.makeUserCopy(candidate.pkg.deck, Instant.now())
      val pkg = DeckMakerCommits.canonicalPackage(copy)
      ProStore.requireAccess()
      AppData.deckLibrary.installUserDeck(
        data = pkg.deckData,
        source = InstalledDeckSource.CREATED,
        contentSha256 = pkg.contentSha256,
        packageFormatVersion = pkg.manifest.formatVersion,
        isLocallyModified = true,
        hasPiyokeyProAccess = ProStore.hasAccess.value,
      )
      Telemetry.deckMakerAction("copied", copy.items.size, "created")
      coordinator.finishImport()
    } catch (_: Exception) {
      coordinator.dismissCandidate(showNext = false)
      coordinator.reportSaveFailure()
    }
  }
}

@Composable
fun ImportPreview(candidate: PiyoDeckImportCandidate, coordinator: PiyoDeckImportCoordinator, onBusyChange: (Boolean) -> Unit = {}) {
  val colors = Piyo.colors
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val records by AppData.deckLibrary.records.collectAsStateWithLifecycle()
  val installedDecks by AppData.deckLibrary.installedDecks.collectAsStateWithLifecycle()
  val hasAccess by ProStore.hasAccess.collectAsStateWithLifecycle()
  val deck = candidate.pkg.deck
  val installedDeck = installedDecks[deck.deckId]
  val record = records[deck.deckId]
  val collision = PiyoDeckImportCollision.of(record?.version, record?.contentSha256, installedDeck, candidate.pkg)
  val canInstallNewDeck = remember(records, hasAccess) { AppData.deckLibrary.canInstallNewUserDeck(hasAccess) }
  val text = rememberCatalogText()
  var isSaving by remember { mutableStateOf(false) }
  var isExportingCurrent by remember { mutableStateOf(false) }
  var showsPaywall by remember { mutableStateOf(false) }
  var showsReplaceConfirmation by remember { mutableStateOf(false) }
  var showsExportError by remember { mutableStateOf(false) }
  var afterPurchase by remember { mutableStateOf<(() -> Unit)?>(null) }
  val disabled = isSaving || isExportingCurrent
  androidx.compose.runtime.SideEffect { onBusyChange(disabled) }

  val exportLauncher = rememberLauncherForActivityResult(DocumentIO.exportContract()) { uri ->
    val current = installedDeck
    if (uri == null || current == null) {
      isExportingCurrent = false
      return@rememberLauncherForActivityResult
    }
    scope.launch {
      val ok = runCatching { DocumentIO.writeBytes(context, uri, DeckMakerCommits.exportBytes(current)) }.getOrDefault(false)
      isExportingCurrent = false
      if (ok) Telemetry.deckMakerAction("exported") else showsExportError = true
    }
  }

  fun install(replacing: Boolean) {
    if (!replacing && collision != PiyoDeckImportCollision.New) return
    if (!replacing && !canInstallNewDeck) {
      afterPurchase = { install(false) }
      showsPaywall = true
      return
    }
    isSaving = true
    scope.launch {
      val done = ImportActions.install(candidate, coordinator, replacing)
      isSaving = false
      if (!done) {
        afterPurchase = { install(false) }
        showsPaywall = true
      }
    }
  }

  fun importAsCopy() {
    if (!ProStore.hasAccess.value) {
      afterPurchase = { importAsCopy() }
      showsPaywall = true
      return
    }
    isSaving = true
    scope.launch {
      ImportActions.importAsCopy(candidate, coordinator)
      isSaving = false
    }
  }

  val statusKey = PiyoDeckImportRules.statusKey(collision)
  val statusText = stringResource(
    when (statusKey) {
      "new" -> R.string.piyodeck_import_status_new
      "identical" -> R.string.piyodeck_import_status_identical
      "downgrade" -> R.string.piyodeck_import_status_downgrade
      "same_version" -> R.string.piyodeck_import_status_same_version
      else -> R.string.piyodeck_import_status_different
    },
  )

  Column(Modifier.fillMaxSize().testTag("piyodeck.import.preview")) {
    InlineTopBar(
      stringResource(R.string.piyodeck_import_navigation_title),
      onBack = null,
      leading = {
        TextButton(onClick = { coordinator.dismissCandidate() }, enabled = !disabled, modifier = Modifier.testTag("piyodeck.import.close")) {
          Text(stringResource(R.string.common_close), color = colors.accent)
        }
      },
    )
    Column(
      Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(20.dp)
        .centeredContent(Piyo.metrics.formContentMaxWidth),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      // Header
      Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("piyodeck.import.header"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        DeckCover(deck, 68.dp, 86.dp)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
          Text(deck.localizedName(text.languageCode) ?: text.unavailableTitle, style = PiyoType.title2().copy(fontWeight = FontWeight.ExtraBold))
          Text(
            deck.localizedAuthorNickname(text.languageCode) ?: text.unavailableAuthor,
            style = PiyoType.subheadline().copy(color = colors.mutedInk, fontWeight = FontWeight.SemiBold),
          )
          IconText(stringResource(R.string.piyodeck_import_user_badge), Icons.Rounded.AccountBox, colors.ink)
        }
      }
      // Metadata
      val locale = rememberAppLocale()
      val number = remember(locale) { NumberFormat.getIntegerInstance(locale) }
      Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.card).padding(16.dp).testTag("piyodeck.import.metadata"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        MetadataRow(
          stringResource(R.string.piyodeck_import_type),
          stringResource(if (deck.type == DeckType.WORD) R.string.deck_editor_type_word else R.string.deck_editor_type_sentence),
        )
        MetadataRow(stringResource(R.string.piyodeck_import_level), deck.level.toString())
        MetadataRow(stringResource(R.string.piyodeck_import_items), number.format(deck.items.size))
        MetadataRow(stringResource(R.string.piyodeck_import_version), number.format(deck.version))
        MetadataRow(stringResource(R.string.piyodeck_import_tags), (deck.localizedTags(text.languageCode) ?: emptyList()).joinToString(" · "))
      }
      // Preview (first 3 items)
      Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.card).padding(16.dp).testTag("piyodeck.import.items_preview"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(stringResource(R.string.piyodeck_import_preview), style = PiyoType.headline().copy(fontWeight = FontWeight.ExtraBold))
        val previewItems = deck.items.take(3)
        previewItems.forEachIndexed { index, item ->
          Column(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("piyodeck.import.preview.item.$index"),
            verticalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Text(item.ko, style = PiyoType.headline().copy(fontWeight = FontWeight.Bold))
            item.localizedMeaning(text.languageCode)?.takeIf { it.isNotEmpty() }?.let {
              Text(it, style = PiyoType.subheadline().copy(color = colors.mutedInk))
            }
            item.localizedReading(text.languageCode)?.takeIf { it.isNotEmpty() }?.let {
              Text(it, style = PiyoType.caption().copy(color = colors.mutedInk.copy(alpha = 0.8f)))
            }
          }
          if (index < previewItems.size - 1) HorizontalDivider(color = colors.mutedInk.copy(alpha = 0.2f))
        }
      }
      // Collision status
      val statusLabel = stringResource(R.string.piyodeck_import_status_accessibility_label)
      when (collision) {
        PiyoDeckImportCollision.New, PiyoDeckImportCollision.Identical -> IconText(
          statusText,
          if (collision == PiyoDeckImportCollision.New) Icons.Rounded.Verified else Icons.Rounded.Drafts,
          colors.ink,
          style = PiyoType.body(),
          modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = statusLabel
            stateDescription = statusText
          }.testTag("piyodeck.import.status.$statusKey"),
        )
        is PiyoDeckImportCollision.Different -> {
          val detail = stringResource(R.string.piyodeck_import_status_different_detail)
          Column(
            Modifier.semantics(mergeDescendants = true) {
              contentDescription = statusLabel
              stateDescription = "$statusText $detail"
            }.testTag("piyodeck.import.status.$statusKey"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            IconText(statusText, Icons.Rounded.Warning, colors.errorText, style = PiyoType.headline().copy(fontWeight = FontWeight.Bold), iconSize = 18)
            Text(detail, style = PiyoType.caption())
          }
        }
      }
      // Comparison
      if (collision is PiyoDeckImportCollision.Different && installedDeck != null) {
        val comparison = PiyoDeckCollisionComparison(
          PiyoDeckComparisonSide.of(installedDeck, installedDeck.localizedName(text.languageCode) ?: text.unavailableTitle),
          PiyoDeckComparisonSide.of(deck, deck.localizedName(text.languageCode) ?: text.unavailableTitle),
        )
        ComparisonCard(comparison, collision, disabled, isExportingCurrent) {
          isExportingCurrent = true
          exportLauncher.launch("${PiyoDeckDocumentRules.safeFilename(installedDeck.name)}.typedeck")
        }
      }
    }
    // Action bar
    Column(
      Modifier
        .fillMaxWidth()
        .background(colors.card.copy(alpha = 0.94f))
        .navigationBarsPadding()
        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)
        .centeredContent(Piyo.metrics.formContentMaxWidth),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      when (collision) {
        PiyoDeckImportCollision.New -> PrimaryAction(
          stringResource(if (canInstallNewDeck) R.string.piyodeck_import_action_import else R.string.piyodeck_import_action_unlock_pro),
          if (canInstallNewDeck) Icons.Rounded.Download else Icons.Rounded.Lock,
          "piyodeck.import.action.import",
          isSaving,
          !disabled,
        ) { install(false) }
        PiyoDeckImportCollision.Identical -> PrimaryAction(
          stringResource(R.string.piyodeck_import_action_done), Icons.Rounded.Check, "piyodeck.import.action.done", isSaving, !disabled,
        ) { coordinator.markAlreadyImported() }
        is PiyoDeckImportCollision.Different -> {
          PrimaryAction(
            stringResource(R.string.piyodeck_import_action_keep), Icons.Rounded.Shield, "piyodeck.import.action.keep", isSaving, !disabled,
          ) { coordinator.dismissCandidate() }
          val copyLabel = stringResource(if (hasAccess) R.string.piyodeck_import_action_copy else R.string.piyodeck_import_action_copy_locked_label)
          BorderedAction(
            stringResource(R.string.piyodeck_import_action_copy),
            if (hasAccess) Icons.Rounded.ContentCopy else Icons.Rounded.Lock,
            "piyodeck.import.action.copy",
            !disabled,
            colors.ink,
            contentDescription = copyLabel,
          ) { importAsCopy() }
          BorderedAction(
            stringResource(R.string.piyodeck_import_action_replace),
            Icons.Rounded.Sync,
            "piyodeck.import.action.replace",
            !disabled,
            colors.error,
          ) { showsReplaceConfirmation = true }
        }
      }
    }
  }

  if (showsReplaceConfirmation) {
    val history = stringResource(R.string.piyodeck_import_replace_confirm_message)
    val message = if (collision.isDowngrade || collision.isSameVersionConflict) "$statusText $history" else history
    AlertDialog(
      onDismissRequest = { showsReplaceConfirmation = false },
      title = { Text(stringResource(R.string.piyodeck_import_replace_confirm_title)) },
      text = { Text(message) },
      confirmButton = {
        TextButton(onClick = {
          showsReplaceConfirmation = false
          install(true)
        }, modifier = Modifier.testTag("piyodeck.import.replace.confirm")) {
          Text(stringResource(R.string.piyodeck_import_replace_confirm_action), color = colors.error)
        }
      },
      dismissButton = { TextButton(onClick = { showsReplaceConfirmation = false }) { Text(stringResource(R.string.deck_editor_cancel)) } },
      containerColor = colors.card,
    )
  }
  if (showsExportError) {
    NoticeDialog(PiyoDeckDocumentNotice.CANNOT_EXPORT) { showsExportError = false }
  }
  if (showsPaywall) {
    DeckMakerPaywallSheet(
      onDismiss = { showsPaywall = false },
      onAccessGranted = {
        val next = afterPurchase
        afterPurchase = null
        showsPaywall = false
        next?.invoke()
      },
    )
  }
}

@Composable
private fun ComparisonCard(
  comparison: PiyoDeckCollisionComparison,
  collision: PiyoDeckImportCollision,
  disabled: Boolean,
  isExporting: Boolean,
  onExport: () -> Unit,
) {
  val colors = Piyo.colors
  val locale = rememberAppLocale()
  val number = remember(locale) { NumberFormat.getIntegerInstance(locale) }
  val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(ZoneId.systemDefault()) }
  Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.card).padding(16.dp).testTag("piyodeck.import.comparison"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    IconText(stringResource(R.string.piyodeck_import_compare_title), Icons.Rounded.VerticalSplit, colors.ink, style = PiyoType.headline().copy(fontWeight = FontWeight.ExtraBold), iconSize = 18)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(stringResource(R.string.piyodeck_import_compare_current), style = PiyoType.caption2().copy(color = colors.mutedInk, fontWeight = FontWeight.ExtraBold), modifier = Modifier.weight(1f))
      Text(stringResource(R.string.piyodeck_import_compare_incoming), style = PiyoType.caption2().copy(color = colors.mutedInk, fontWeight = FontWeight.ExtraBold), modifier = Modifier.weight(1f))
    }
    ComparisonRow(stringResource(R.string.piyodeck_import_compare_name), comparison.current.name, comparison.incoming.name)
    ComparisonRow(
      stringResource(R.string.piyodeck_import_version),
      number.format(comparison.current.version),
      number.format(comparison.incoming.version),
      highlightsIncoming = collision.isDowngrade || collision.isSameVersionConflict,
    )
    ComparisonRow(
      stringResource(R.string.piyodeck_import_compare_updated_at),
      dateFormatter.format(comparison.current.updatedAt),
      dateFormatter.format(comparison.incoming.updatedAt),
    )
    ComparisonRow(stringResource(R.string.piyodeck_import_items), number.format(comparison.current.itemCount), number.format(comparison.incoming.itemCount))
    Row(
      Modifier
        .fillMaxWidth()
        .heightIn(min = 44.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(colors.accentSoft.copy(alpha = 0.35f))
        .clickable(enabled = !disabled, role = Role.Button, onClick = onExport)
        .testTag("piyodeck.import.action.export_current"),
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (isExporting) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp), color = colors.accent)
      IconText(stringResource(R.string.piyodeck_import_action_export_current), Icons.Rounded.IosShare, colors.accent, style = PiyoType.subheadline().copy(fontWeight = FontWeight.Bold), iconSize = 16)
    }
  }
}

@Composable
private fun ComparisonRow(key: String, current: String, incoming: String, highlightsIncoming: Boolean = false) {
  val colors = Piyo.colors
  val value = stringResource(R.string.piyodeck_import_compare_accessibility_value, current, incoming)
  Column(
    Modifier.fillMaxWidth().semantics(mergeDescendants = true) { stateDescription = value },
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(key, style = PiyoType.caption().copy(color = colors.mutedInk, fontWeight = FontWeight.Bold))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(current, style = PiyoType.subheadline().copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
      Text(
        incoming,
        style = PiyoType.subheadline().copy(
          fontWeight = if (highlightsIncoming) FontWeight.ExtraBold else FontWeight.SemiBold,
          color = if (highlightsIncoming) colors.errorText else colors.ink,
        ),
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun MetadataRow(key: String, value: String) {
  Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
    Text(key, style = PiyoType.caption().copy(color = Piyo.colors.mutedInk, fontWeight = FontWeight.Bold))
    Text(value, style = PiyoType.subheadline().copy(fontWeight = FontWeight.SemiBold), textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 12.dp))
  }
}

@Composable
internal fun IconText(
  text: String,
  icon: ImageVector,
  color: Color,
  modifier: Modifier = Modifier,
  style: androidx.compose.ui.text.TextStyle = PiyoType.caption().copy(fontWeight = FontWeight.Bold),
  iconSize: Int = 14,
) {
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize.dp))
    Text(text, style = style.copy(color = color))
  }
}

@Composable
private fun PrimaryAction(title: String, icon: ImageVector, tag: String, saving: Boolean, enabled: Boolean, onClick: () -> Unit) {
  val colors = Piyo.colors
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 52.dp)
      .clip(RoundedCornerShape(17.dp))
      .background(colors.accent.copy(alpha = if (enabled) 1f else 0.5f))
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .semantics(mergeDescendants = true) {}
      .testTag(tag),
    horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (saving) CircularProgressIndicator(color = colors.onAccent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
    IconText(title, icon, colors.onAccent, style = PiyoType.headline().copy(fontWeight = FontWeight.ExtraBold), iconSize = 18)
  }
}

@Composable
private fun BorderedAction(
  title: String,
  icon: ImageVector,
  tag: String,
  enabled: Boolean,
  tint: Color,
  contentDescription: String? = null,
  onClick: () -> Unit,
) {
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(tint.copy(alpha = 0.1f))
      .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .semantics(mergeDescendants = true) { if (contentDescription != null) this.contentDescription = contentDescription }
      .testTag(tag),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconText(title, icon, tint.copy(alpha = if (enabled) 1f else 0.4f), style = PiyoType.headline().copy(fontWeight = FontWeight.Bold), iconSize = 18)
  }
}

/** `piyodeck.notice.*` alert with Android copy for Play/Files mentions. */
@Composable
fun NoticeDialog(notice: PiyoDeckDocumentNotice, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(noticeTitle(notice))) },
    text = { Text(stringResource(noticeMessage(notice))) },
    confirmButton = {
      TextButton(onClick = onDismiss, modifier = Modifier.testTag("piyodeck.notice.ok")) { Text(stringResource(R.string.deck_maker_alert_ok)) }
    },
    containerColor = Piyo.colors.card,
    modifier = Modifier.testTag("piyodeck.notice.${notice.id}"),
    properties = DialogProperties(),
  )
}

internal fun noticeTitle(notice: PiyoDeckDocumentNotice): Int = when (notice) {
  PiyoDeckDocumentNotice.PACKAGE_TOO_LARGE -> R.string.piyodeck_notice_packageTooLarge_title
  PiyoDeckDocumentNotice.UNSUPPORTED_VERSION -> R.string.piyodeck_notice_unsupportedVersion_title
  PiyoDeckDocumentNotice.INVALID_DOCUMENT -> R.string.piyodeck_notice_invalidDocument_title
  PiyoDeckDocumentNotice.CANNOT_READ -> R.string.piyodeck_notice_cannotRead_title
  PiyoDeckDocumentNotice.CANNOT_SAVE -> R.string.piyodeck_notice_cannotSave_title
  PiyoDeckDocumentNotice.CANNOT_EXPORT -> R.string.piyodeck_notice_cannotExport_title
  PiyoDeckDocumentNotice.ALREADY_IMPORTED -> R.string.piyodeck_notice_alreadyImported_title
  PiyoDeckDocumentNotice.IMPORTED -> R.string.piyodeck_notice_imported_title
}

internal fun noticeMessage(notice: PiyoDeckDocumentNotice): Int = when (notice) {
  PiyoDeckDocumentNotice.PACKAGE_TOO_LARGE -> R.string.piyodeck_notice_packageTooLarge_message
  PiyoDeckDocumentNotice.UNSUPPORTED_VERSION -> R.string.library_notice_unsupported_version_message
  PiyoDeckDocumentNotice.INVALID_DOCUMENT -> R.string.piyodeck_notice_invalidDocument_message
  PiyoDeckDocumentNotice.CANNOT_READ -> R.string.library_notice_cannot_read_message
  PiyoDeckDocumentNotice.CANNOT_SAVE -> R.string.piyodeck_notice_cannotSave_message
  PiyoDeckDocumentNotice.CANNOT_EXPORT -> R.string.piyodeck_notice_cannotExport_message
  PiyoDeckDocumentNotice.ALREADY_IMPORTED -> R.string.piyodeck_notice_alreadyImported_message
  PiyoDeckDocumentNotice.IMPORTED -> R.string.piyodeck_notice_imported_message
}
