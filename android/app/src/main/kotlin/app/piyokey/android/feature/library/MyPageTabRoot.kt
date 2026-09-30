package app.piyokey.android.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LayersClear
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SavedSearch
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.piyokey.android.R
import app.piyokey.android.data.AppData
import app.piyokey.android.data.decks.InstalledDeckSource
import app.piyokey.android.feature.discover.DeckCover
import app.piyokey.android.feature.discover.DiscoverLauncher
import app.piyokey.android.feature.discover.InlineTopBar
import app.piyokey.android.feature.discover.SystemColors
import app.piyokey.android.feature.discover.rememberCatalogText
import app.piyokey.android.feature.home.rememberJstToday
import app.piyokey.android.feature.practice.PracticeLauncher
import app.piyokey.android.feature.settings.SettingsGearButton
import app.piyokey.android.platform.analytics.Telemetry
import app.piyokey.android.platform.billing.ProStore
import app.piyokey.android.platform.files.DocumentIO
import app.piyokey.android.ui.mascot.MascotClosetRoute
import app.piyokey.android.ui.nav.AppTab
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.nav.LocalTabController
import app.piyokey.android.ui.nav.LocalTabNavigator
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.ui.theme.centeredContent
import app.piyokey.core.deckkit.CatalogDeck
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.domain.LearningInsightPeriod
import app.piyokey.core.domain.UserDeckDraft
import app.piyokey.core.domain.library.DeckInstallDates
import app.piyokey.core.domain.library.DeckMakerDraftFlow
import app.piyokey.core.domain.library.MyDeckSortOrder
import app.piyokey.core.domain.library.MyDecksRules
import java.text.Collator
import kotlinx.coroutines.launch

/** iOS `DeckMakerAction`. */
private sealed interface DeckMakerAction {
  data object Create : DeckMakerAction
  data class Edit(val deck: Deck) : DeckMakerAction
  data class CopyOfficial(val deck: Deck) : DeckMakerAction

  val flow: DeckMakerDraftFlow
    get() = when (this) {
      Create -> DeckMakerDraftFlow.New
      is Edit -> DeckMakerDraftFlow.Editing(deck.deckId)
      is CopyOfficial -> DeckMakerDraftFlow.OfficialCopy(deck.deckId)
    }
}

private data class DeletionPresentation(val deck: Deck, val didOfferExport: Boolean)

/** MIME types offered by the system picker for `.typedeck` (providers often report zip/octet-stream). */
internal val TYPEDECK_PICKER_TYPES = arrayOf(DocumentIO.TYPEDECK_MIME, "application/zip", "application/octet-stream")

/** My Page tab (iOS `MyPageView`): profile, growth, insights, My Decks and Deck Maker. */
@Composable
fun MyPageTabRoot() {
  val colors = Piyo.colors
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val appNavigator = LocalAppNavigator.current
  val tabNavigator = LocalTabNavigator.current
  val tabController = LocalTabController.current
  val coordinator = LibraryLauncher.documents
  val candidate by coordinator.candidate.collectAsStateWithLifecycle()
  val notice by coordinator.notice.collectAsStateWithLifecycle()
  val isReading by coordinator.isReading.collectAsStateWithLifecycle()
  val hasAccess by ProStore.hasAccess.collectAsStateWithLifecycle()
  val today = rememberJstToday()

  var insightPeriod by rememberSaveable { mutableStateOf(LearningInsightPeriod.WEEK) }
  var showsPaywall by remember { mutableStateOf(false) }
  var pendingAction by remember { mutableStateOf<DeckMakerAction?>(null) }
  var paywallGrantedAccess by remember { mutableStateOf(false) }
  var routeAfterPaywall by remember { mutableStateOf<DeckMakerAction?>(null) }
  var draftConflict by remember { mutableStateOf<Pair<DeckMakerAction, app.piyokey.android.data.decks.ActiveUserDeckDraft>?>(null) }
  var deletion by remember { mutableStateOf<DeletionPresentation?>(null) }
  var pendingDeletionAfterExport by remember { mutableStateOf<Deck?>(null) }
  var exportingDeck by remember { mutableStateOf<Deck?>(null) }
  var deletionFailure by remember { mutableStateOf<String?>(null) }
  var deletingDeckId by remember { mutableStateOf<String?>(null) }
  var draftRecoveryFailure by remember { mutableStateOf(false) }

  val isActive = tabController.selected == AppTab.MY_PAGE && appNavigator.depth == 0 && AppData.onboarding.appTourCompleted
  val canPresentDocumentUI = isActive && !showsPaywall && draftConflict == null && deletion == null && deletionFailure == null
  val canPresentDraftUI = canPresentDocumentUI && candidate == null && exportingDeck == null && notice == null && pendingDeletionAfterExport == null

  // Exports (iOS share sheet → Android "save as" document).
  fun finishExport() {
    exportingDeck = null
    val deck = pendingDeletionAfterExport ?: return
    pendingDeletionAfterExport = null
    if (AppData.deckLibrary.isInstalled(deck.deckId)) deletion = DeletionPresentation(deck, didOfferExport = true)
  }
  val exportLauncher = rememberLauncherForActivityResult(DocumentIO.exportContract()) { uri ->
    val deck = exportingDeck
    if (uri == null || deck == null) {
      finishExport()
      return@rememberLauncherForActivityResult
    }
    scope.launch {
      val ok = runCatching { DocumentIO.writeBytes(context, uri, DeckMakerCommits.exportBytes(deck)) }.getOrDefault(false)
      if (ok) Telemetry.deckMakerAction("exported") else coordinator.reportExportFailure()
      finishExport()
    }
  }
  fun prepareExport(deck: Deck) {
    exportingDeck = deck
    runCatching { exportLauncher.launch(DeckMakerCommits.exportFilename(deck)) }.onFailure {
      coordinator.reportExportFailure()
      finishExport()
    }
  }

  val importLauncher = rememberLauncherForActivityResult(DocumentIO.importContract()) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    val resolver = context.contentResolver
    scope.launch { coordinator.receive { resolver.openInputStream(uri) } }
  }
  fun openImporter() {
    runCatching { importLauncher.launch(TYPEDECK_PICKER_TYPES) }.onFailure { coordinator.reportReadFailure() }
  }
  LaunchedEffect(LibraryLauncher.importRequested) {
    if (LibraryLauncher.importRequested) {
      LibraryLauncher.importRequested = false
      openImporter()
    }
  }

  // Deck Maker routing -------------------------------------------------------------------
  fun presentEditor(active: app.piyokey.android.data.decks.ActiveUserDeckDraft, source: InstalledDeckSource, derivedFrom: String?, deletes: String?) {
    // Explicitly resuming makes an interrupted editing session recoverable again.
    if (DeckMakerPrefs.dismissedDraftId.value == active.draftId) DeckMakerPrefs.dismissedDraftId.value = ""
    appNavigator.push(DeckEditorRoute(DeckEditorPresentation(active.draftId, active.draft, source, derivedFrom, deletes)))
  }

  fun sourceFor(deckId: String) =
    if (AppData.deckLibrary.record(deckId)?.source == InstalledDeckSource.IMPORTED) InstalledDeckSource.IMPORTED else InstalledDeckSource.CREATED

  fun presentExisting(active: app.piyokey.android.data.decks.ActiveUserDeckDraft) {
    val draft = active.draft
    when (val origin = draft.origin) {
      UserDeckDraft.Origin.New -> presentEditor(active, InstalledDeckSource.CREATED, null, null)
      UserDeckDraft.Origin.Editing -> {
        if (!AppData.deckLibrary.isInstalled(draft.deckId)) return
        presentEditor(active, sourceFor(draft.deckId), AppData.deckLibrary.record(draft.deckId)?.derivedFromDeckId, draft.deckId)
      }
      is UserDeckDraft.Origin.OfficialCopy -> presentEditor(active, InstalledDeckSource.CREATED, origin.sourceDeckId, null)
    }
  }

  fun presentNew(action: DeckMakerAction) {
    val (draft, source, derived, deletes) = when (action) {
      DeckMakerAction.Create -> Quad(UserDeckDraft.new(), InstalledDeckSource.CREATED, null, null)
      is DeckMakerAction.Edit -> Quad(
        UserDeckDraft.editing(action.deck),
        sourceFor(action.deck.deckId),
        AppData.deckLibrary.record(action.deck.deckId)?.derivedFromDeckId,
        action.deck.deckId,
      )
      is DeckMakerAction.CopyOfficial -> Quad(UserDeckDraft.copyingOfficial(action.deck), InstalledDeckSource.CREATED, action.deck.deckId, null)
    }
    try {
      presentEditor(AppData.drafts.save(draft), source, derived, deletes)
    } catch (_: Exception) {
      draftRecoveryFailure = true
    }
  }

  fun routeAction(action: DeckMakerAction) {
    if (!canPresentDraftUI) return
    try {
      val active = AppData.drafts.load()
      when {
        active == null -> presentNew(action)
        action.flow.matches(active.draft) -> presentExisting(active)
        else -> draftConflict = action to active
      }
    } catch (_: Exception) {
      draftRecoveryFailure = true
    }
  }

  fun requestDeckMaker(action: DeckMakerAction) {
    Telemetry.featureViewed("deck_maker")
    Telemetry.setCrashContext("deck_maker")
    if (ProStore.hasAccess.value) {
      routeAction(action)
    } else {
      pendingAction = action
      paywallGrantedAccess = false
      showsPaywall = true
    }
  }

  fun finishPaywall() {
    val action = pendingAction
    val granted = paywallGrantedAccess
    showsPaywall = false
    pendingAction = null
    paywallGrantedAccess = false
    // Route once the paywall has left composition: `canPresentDraftUI` in this closure was
    // captured while the paywall was still showing.
    if (granted && ProStore.hasAccess.value && action != null) routeAfterPaywall = action
  }
  LaunchedEffect(routeAfterPaywall, canPresentDraftUI) {
    val action = routeAfterPaywall ?: return@LaunchedEffect
    if (canPresentDraftUI) {
      routeAfterPaywall = null
      routeAction(action)
    }
  }

  fun restoreDraftIfAvailable() {
    if (!ProStore.hasAccess.value || !canPresentDraftUI || pendingAction != null) return
    try {
      val active = AppData.drafts.load() ?: return
      val draft = active.draft
      when (draft.origin) {
        UserDeckDraft.Origin.New, is UserDeckDraft.Origin.OfficialCopy -> {
          if (AppData.deckLibrary.isInstalled(draft.deckId)) {
            runCatching { AppData.drafts.clear(active.draftId) }
            return
          }
        }
        // Deletion and entitlement changes never silently discard authored content.
        UserDeckDraft.Origin.Editing -> if (!AppData.deckLibrary.isInstalled(draft.deckId)) return
      }
      // Closing the editor keeps its draft but opts out of automatic presentation,
      // including after relaunch. Create/edit actions still resume it explicitly.
      if (active.draftId == DeckMakerPrefs.dismissedDraftId.value) return
      presentExisting(active)
    } catch (_: Exception) {
      draftRecoveryFailure = true
    }
  }

  LaunchedEffect(isActive, hasAccess, candidate == null) {
    if (isActive) restoreDraftIfAvailable()
  }

  fun delete(deck: Deck) {
    deletion = null
    if (deletingDeckId != null) return
    deletingDeckId = deck.deckId
    val name = deck.localizedName(app.piyokey.android.data.settings.AppSettings.currentLanguage.raw) ?: deck.name
    scope.launch {
      try {
        DeckMakerCommits.delete(deck)
      } catch (_: Exception) {
        deletionFailure = name
      } finally {
        deletingDeckId = null
      }
    }
  }

  // Layout -------------------------------------------------------------------------------
  val metrics = Piyo.metrics
  Column(Modifier.fillMaxSize().testTag("my_page.screen")) {
    InlineTopBar(stringResource(R.string.my_page_navigation_title), onBack = null, trailing = { SettingsGearButton() })
    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = metrics.horizontalPadding, vertical = 18.dp)
        .centeredContent(if (metrics.usesTwoColumnDashboard) metrics.hubContentMaxWidth else metrics.readableContentMaxWidth),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      val openCloset = { appNavigator.push(MascotClosetRoute()) }
      if (metrics.usesTwoColumnDashboard) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ProfileCard(today, openCloset)
            GrowthRecordCard(today)
          }
          Column(Modifier.weight(1f)) { LearningInsightsCard(insightPeriod) { insightPeriod = it } }
        }
      } else {
        ProfileCard(today, openCloset)
        GrowthRecordCard(today)
        LearningInsightsCard(insightPeriod) { insightPeriod = it }
      }
      DeckLibrarySection(
        hasAccess = hasAccess,
        isReading = isReading,
        deletingDeckId = deletingDeckId,
        onImport = { openImporter() },
        onCreate = { requestDeckMaker(DeckMakerAction.Create) },
        onFindDecks = {
          DiscoverLauncher.installDeckDownloadAnalytics()
          if (tabController.selected != AppTab.DISCOVER) tabController.select(AppTab.DISCOVER)
        },
        onReview = { LibraryLauncher.reviewDeck(tabNavigator) },
        onPlay = { deck -> PracticeLauncher.deck(appNavigator, deck.deckId) },
        onUpdate = { entry ->
          scope.launch {
            AppData.deckLibrary.install(entry)
            AppData.deckLibrary.installedDeck(entry.deckId)?.let { AppData.review.reconcile(it) }
          }
        },
        onEdit = { requestDeckMaker(DeckMakerAction.Edit(it)) },
        onCopyOfficial = { requestDeckMaker(DeckMakerAction.CopyOfficial(it)) },
        onExport = { prepareExport(it) },
        onDelete = { deletion = DeletionPresentation(it, didOfferExport = false) },
      )
    }
  }

  // Modals ---------------------------------------------------------------------------------
  val currentCandidate = candidate
  if (canPresentDocumentUI && exportingDeck == null && currentCandidate != null) {
    ImportPreviewSheet(currentCandidate, coordinator)
  }
  val currentNotice = notice
  if (canPresentDocumentUI && currentCandidate == null && exportingDeck == null && currentNotice != null) {
    NoticeDialog(currentNotice) { coordinator.dismissNotice() }
  }
  if (showsPaywall) {
    DeckMakerPaywallSheet(onDismiss = { finishPaywall() }, onAccessGranted = { paywallGrantedAccess = true })
  }
  deletion?.let { presentation ->
    DeckDeletionSheet(
      deck = presentation.deck,
      onDelete = { delete(presentation.deck) },
      onExportThenDelete = if (presentation.deck.official || presentation.didOfferExport) null else ({
        deletion = null
        pendingDeletionAfterExport = presentation.deck
        prepareExport(presentation.deck)
      }),
      onCancel = { deletion = null },
    )
  }
  deletionFailure?.let { name ->
    AlertDialog(
      onDismissRequest = { deletionFailure = null },
      title = { Text(stringResource(R.string.my_decks_delete_failure_title)) },
      text = { Text(stringResource(R.string.my_decks_delete_failure_message_format, name)) },
      confirmButton = { TextButton(onClick = { deletionFailure = null }) { Text(stringResource(R.string.deck_maker_alert_ok)) } },
      containerColor = colors.card,
    )
  }
  if (draftRecoveryFailure) {
    AlertDialog(
      onDismissRequest = { draftRecoveryFailure = false },
      title = { Text(stringResource(R.string.deck_editor_draft_failure_title)) },
      text = { Text(stringResource(R.string.deck_editor_draft_failure_message)) },
      confirmButton = { TextButton(onClick = { draftRecoveryFailure = false }) { Text(stringResource(R.string.deck_maker_alert_ok)) } },
      containerColor = colors.card,
    )
  }
  draftConflict?.let { (action, active) ->
    AlertDialog(
      onDismissRequest = { draftConflict = null },
      title = { Text(stringResource(R.string.deck_editor_draft_conflict_title)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(stringResource(R.string.deck_editor_draft_conflict_message))
          TextButton(onClick = {
            draftConflict = null
            presentExisting(active)
          }, modifier = Modifier.fillMaxWidth().testTag("deck_editor.draft.conflict.resume")) { Text(stringResource(R.string.deck_editor_draft_conflict_resume)) }
          TextButton(onClick = {
            draftConflict = null
            try {
              if (!AppData.drafts.clear(active.draftId)) throw IllegalStateException("draft changed")
              presentNew(action)
            } catch (_: Exception) {
              draftRecoveryFailure = true
            }
          }, modifier = Modifier.fillMaxWidth().testTag("deck_editor.draft.conflict.discard")) {
            Text(stringResource(R.string.deck_editor_draft_conflict_discard), color = colors.error)
          }
        }
      },
      confirmButton = {},
      dismissButton = { TextButton(onClick = { draftConflict = null }) { Text(stringResource(R.string.common_cancel)) } },
      containerColor = colors.card,
    )
  }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
private fun DeckLibrarySection(
  hasAccess: Boolean,
  isReading: Boolean,
  deletingDeckId: String?,
  onImport: () -> Unit,
  onCreate: () -> Unit,
  onFindDecks: () -> Unit,
  onReview: () -> Unit,
  onPlay: (Deck) -> Unit,
  onUpdate: (CatalogDeck) -> Unit,
  onEdit: (Deck) -> Unit,
  onCopyOfficial: (Deck) -> Unit,
  onExport: (Deck) -> Unit,
  onDelete: (Deck) -> Unit,
) {
  val colors = Piyo.colors
  val installedList by AppData.deckLibrary.installedList.collectAsStateWithLifecycle()
  val records by AppData.deckLibrary.records.collectAsStateWithLifecycle()
  AppData.deckLibrary.installedDecks.collectAsStateWithLifecycle().value
  val reviewItems by AppData.review.activeItems.collectAsStateWithLifecycle()
  val catalog by AppData.catalog.catalog.collectAsStateWithLifecycle()
  LaunchedEffect(Unit) { AppData.catalog.loadIfNeeded() }
  val text = rememberCatalogText()
  var sortOrder by rememberSaveable { mutableStateOf(MyDeckSortOrder.RECENT) }
  val collator = remember(text.languageCode) { Collator.getInstance(app.piyokey.android.data.settings.AppSettings.currentLanguage.locale) }
  fun nameOf(deck: Deck) = deck.localizedName(text.languageCode) ?: text.unavailableTitle
  val sorted = remember(installedList, records, sortOrder, text) {
    MyDecksRules.sorted(
      installedList,
      sortOrder,
      { id -> records[id]?.let { DeckInstallDates(it.installedAt, it.lastPlayedAt) } },
      ::nameOf,
      Comparator { a, b -> collator.compare(a, b) },
    )
  }

  Column(Modifier.fillMaxWidth().testTag("my_page.decks"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      HeavyLabel(stringResource(R.string.my_page_decks_title), Icons.Rounded.ViewAgenda, Modifier.weight(1f))
      Text(
        pluralStringResource(R.plurals.my_page_decks_count_format, installedList.size, installedList.size),
        style = PiyoType.caption().copy(color = colors.mutedInk, fontWeight = FontWeight.Bold),
      )
    }
    // Import / create (iOS ViewThatFits: side by side, else stacked).
    BoxWithConstraints(Modifier.fillMaxWidth()) {
      val importButton: @Composable (Modifier) -> Unit = { modifier ->
        Row(
          modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.5.dp, colors.accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .clickable(enabled = !isReading, role = Role.Button, onClick = onImport)
            .semantics(mergeDescendants = true) {}
            .testTag("my_decks.import"),
          horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (isReading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp), color = colors.accent)
          else Icon(Icons.Rounded.FileDownload, contentDescription = null, tint = colors.ink, modifier = Modifier.size(18.dp))
          Text(stringResource(R.string.piyodeck_import_action_open), style = PiyoType.subheadline().copy(fontWeight = FontWeight.Bold))
        }
      }
      val status = stringResource(if (hasAccess) R.string.deck_maker_accessibility_status_unlocked else R.string.deck_maker_accessibility_status_locked)
      val hint = stringResource(if (hasAccess) R.string.deck_maker_accessibility_hint_available else R.string.deck_maker_accessibility_hint_locked)
      val createButton: @Composable (Modifier) -> Unit = { modifier ->
        Row(
          modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.accent)
            .clickable(role = Role.Button, onClickLabel = hint, onClick = onCreate)
            .semantics(mergeDescendants = true) { stateDescription = status }
            .testTag("my_decks.create"),
          horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(if (hasAccess) Icons.Rounded.AddCircle else Icons.Rounded.Lock, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(18.dp))
          Text(stringResource(R.string.piyodeck_create_action), style = PiyoType.subheadline().copy(color = colors.onAccent, fontWeight = FontWeight.Bold))
        }
      }
      if (maxWidth >= 330.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          importButton(Modifier.weight(1f))
          createButton(Modifier.weight(1f))
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          importButton(Modifier.fillMaxWidth())
          createButton(Modifier.fillMaxWidth())
        }
      }
    }

    if (installedList.isEmpty() && reviewItems.isEmpty()) {
      EmptyDecks(onFindDecks)
    } else {
      SortRow(sortOrder) { sortOrder = it }
      if (reviewItems.isNotEmpty()) ReviewDeckRow(reviewItems.map { it.ko }, onReview)
      sorted.forEach { deck ->
        val update = catalog?.decks?.firstOrNull { it.deckId == deck.deckId }?.takeIf { AppData.deckLibrary.needsUpdate(it) }
        InstalledDeckRow(
          deck = deck,
          name = nameOf(deck),
          author = deck.localizedAuthorNickname(text.languageCode) ?: if (deck.official) text.officialAuthor else text.unavailableAuthor,
          update = update,
          hasAccess = hasAccess,
          deleting = deletingDeckId == deck.deckId,
          onPlay = { onPlay(deck) },
          onUpdate = onUpdate,
          onEdit = { onEdit(deck) },
          onCopyOfficial = { onCopyOfficial(deck) },
          onExport = { onExport(deck) },
          onDelete = { onDelete(deck) },
        )
      }
    }
  }
}

@Composable
private fun SortRow(order: MyDeckSortOrder, onChange: (MyDeckSortOrder) -> Unit) {
  val colors = Piyo.colors
  var expanded by remember { mutableStateOf(false) }
  val labels = mapOf(
    MyDeckSortOrder.RECENT to stringResource(R.string.my_decks_sort_recent),
    MyDeckSortOrder.NAME to stringResource(R.string.my_decks_sort_name),
    MyDeckSortOrder.INSTALLED to stringResource(R.string.my_decks_sort_installed),
  )
  val title = stringResource(R.string.my_decks_sort_title)
  Row(verticalAlignment = Alignment.CenterVertically) {
    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
      Icon(Icons.Rounded.SwapVert, contentDescription = null, tint = colors.mutedInk, modifier = Modifier.size(14.dp))
      Text(title, style = PiyoType.caption().copy(color = colors.mutedInk, fontWeight = FontWeight.Bold))
    }
    Box {
      Text(
        labels.getValue(order),
        style = PiyoType.subheadline().copy(color = colors.accent),
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .clickable(role = Role.DropdownList) { expanded = true }
          .semantics { contentDescription = title; stateDescription = labels.getValue(order) }
          .testTag("my_decks.sort")
          .heightIn(min = 44.dp)
          .padding(horizontal = 8.dp, vertical = 12.dp),
      )
      DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = colors.card) {
        MyDeckSortOrder.entries.forEach { value ->
          DropdownMenuItem(
            text = { Text(labels.getValue(value)) },
            onClick = {
              onChange(value)
              expanded = false
            },
            modifier = Modifier.testTag("my_decks.sort.${value.raw}"),
          )
        }
      }
    }
  }
}

@Composable
private fun EmptyDecks(onFindDecks: () -> Unit) {
  val colors = Piyo.colors
  CardColumn(Modifier.testTag("my_decks.empty"), spacing = 16) {
    Column(Modifier.fillMaxWidth().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Box(Modifier.size(72.dp).clip(CircleShape).background(colors.accentSoft.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.LayersClear, contentDescription = null, tint = colors.accent, modifier = Modifier.size(34.dp))
      }
      Text(stringResource(R.string.my_decks_empty_title), style = PiyoType.title2())
      Text(stringResource(R.string.my_decks_empty_message), style = PiyoType.subheadline().copy(color = colors.mutedInk), textAlign = TextAlign.Center)
      Row(
        Modifier
          .clip(CircleShape)
          .background(colors.accent)
          .clickable(role = Role.Button, onClick = onFindDecks)
          .testTag("my_decks.find_decks")
          .padding(horizontal = 22.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Rounded.SavedSearch, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.my_decks_find_decks), style = PiyoType.headline().copy(color = Color.White, fontWeight = FontWeight.Bold))
      }
    }
  }
}

@Composable
private fun RowCard(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(22.dp)
  Box(
    modifier
      .fillMaxWidth()
      .shadow(9.dp, shape, ambientColor = colors.keyShadow, spotColor = colors.keyShadow)
      .clip(shape)
      .background(colors.card),
  ) { content() }
}

@Composable
private fun ReviewDeckRow(targets: List<String>, onClick: () -> Unit) {
  val colors = Piyo.colors
  RowCard(Modifier.clickable(role = Role.Button, onClick = onClick).testTag("my_decks.review_deck")) {
    Row(Modifier.padding(14.dp).semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(74.dp, 94.dp).clip(RoundedCornerShape(20.dp)).background(colors.accent), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = Color.White, modifier = Modifier.size(25.dp))
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        IconText(stringResource(R.string.review_deck_badge), Icons.Rounded.Sync, colors.secondary, style = PiyoType.caption2().copy(fontWeight = FontWeight.Bold), iconSize = 12)
        Text(stringResource(R.string.review_deck_title), style = PiyoType.headline().copy(fontWeight = FontWeight.Bold))
        Text(MyDecksRules.reviewPreview(targets), style = PiyoType.caption().copy(color = colors.mutedInk), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          val style = PiyoType.caption().copy(color = colors.accent, fontWeight = FontWeight.SemiBold)
          Text(pluralStringResource(R.plurals.review_deck_count_format, targets.size, targets.size), style = style)
          Text(stringResource(R.string.review_deck_play_hint), style = style)
          Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
        }
      }
    }
  }
}

@Composable
private fun InstalledDeckRow(
  deck: Deck,
  name: String,
  author: String,
  update: CatalogDeck?,
  hasAccess: Boolean,
  deleting: Boolean,
  onPlay: () -> Unit,
  onUpdate: (CatalogDeck) -> Unit,
  onEdit: () -> Unit,
  onCopyOfficial: () -> Unit,
  onExport: () -> Unit,
  onDelete: () -> Unit,
) {
  val colors = Piyo.colors
  var menu by remember { mutableStateOf(false) }
  RowCard(Modifier) {
    Row(
      Modifier
        .fillMaxWidth()
        .clickable(role = Role.Button, onClick = onPlay)
        .semantics(mergeDescendants = true) {}
        .testTag("my_decks.deck.${deck.deckId}")
        .padding(14.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      DeckCover(deck, 74.dp, 94.dp)
      Column(Modifier.weight(1f).padding(end = 36.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          val badge = PiyoType.caption2().copy(fontWeight = FontWeight.Bold)
          if (deck.official) IconText(stringResource(R.string.deck_badge_official), Icons.Rounded.Verified, colors.secondary, style = badge, iconSize = 12)
          else IconText(stringResource(R.string.piyodeck_import_user_badge), Icons.Rounded.AccountBox, colors.ink, style = badge, iconSize = 12)
          IconText(stringResource(R.string.deck_badge_offline), Icons.Rounded.DownloadForOffline, colors.successText, style = badge, iconSize = 12)
          if (update != null) IconText(stringResource(R.string.deck_badge_update), Icons.Rounded.Sync, SystemColors.orange, style = badge, iconSize = 12)
        }
        Text(name, style = PiyoType.headline().copy(fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(author, style = PiyoType.caption().copy(color = colors.mutedInk))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          val style = PiyoType.caption().copy(fontWeight = FontWeight.SemiBold)
          Text(pluralStringResource(R.plurals.deck_items_format, deck.items.size, deck.items.size), style = style)
          Text(stringResource(R.string.my_decks_play_hint), style = style)
          Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.ink, modifier = Modifier.size(14.dp))
        }
      }
    }
    Box(Modifier.align(Alignment.TopEnd)) {
      val actionsLabel = stringResource(R.string.my_decks_actions)
      Box(
        Modifier
          .padding(4.dp)
          .size(44.dp)
          .clip(CircleShape)
          .clickable(enabled = !deleting, role = Role.Button) { menu = true }
          .semantics { contentDescription = actionsLabel }
          .testTag("my_decks.actions.${deck.deckId}"),
        contentAlignment = Alignment.Center,
      ) {
        if (deleting) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = colors.mutedInk)
        else Box(Modifier.size(24.dp).clip(CircleShape).background(colors.mutedInk), contentAlignment = Alignment.Center) {
          Icon(Icons.Rounded.MoreHoriz, contentDescription = null, tint = colors.card, modifier = Modifier.size(18.dp))
        }
      }
      DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = colors.card) {
        MenuItem(stringResource(R.string.my_decks_play_hint), Icons.Rounded.Keyboard, "my_decks.menu.play") { menu = false; onPlay() }
        if (update != null) MenuItem(stringResource(R.string.deck_detail_update), Icons.Rounded.Sync, "my_decks.menu.update") { menu = false; onUpdate(update) }
        if (deck.official) {
          MenuItem(stringResource(R.string.piyodeck_action_edit_copy), if (hasAccess) Icons.Rounded.ContentCopy else Icons.Rounded.Lock, "my_decks.menu.edit_copy") {
            menu = false
            onCopyOfficial()
          }
        } else {
          MenuItem(stringResource(R.string.piyodeck_action_edit), if (hasAccess) Icons.Rounded.Edit else Icons.Rounded.Lock, "my_decks.menu.edit") { menu = false; onEdit() }
          MenuItem(stringResource(R.string.piyodeck_action_export), Icons.Rounded.IosShare, "my_decks.menu.export") { menu = false; onExport() }
        }
        MenuItem(stringResource(R.string.my_decks_delete), Icons.Rounded.Delete, "my_decks.menu.delete", tint = colors.error, enabled = !deleting) {
          menu = false
          onDelete()
        }
      }
    }
  }
}

@Composable
private fun MenuItem(title: String, icon: ImageVector, tag: String, tint: Color = Piyo.colors.ink, enabled: Boolean = true, onClick: () -> Unit) {
  DropdownMenuItem(
    text = { Text(title, color = tint) },
    trailingIcon = { Icon(icon, contentDescription = null, tint = tint) },
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.testTag(tag),
  )
}

/** iOS `DeckDeletionConfirmationView` (medium/large sheet, no interactive dismiss). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckDeletionSheet(deck: Deck, onDelete: () -> Unit, onExportThenDelete: (() -> Unit)?, onCancel: () -> Unit) {
  val colors = Piyo.colors
  val text = rememberCatalogText()
  val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { false })
  ModalBottomSheet(onDismissRequest = onCancel, sheetState = state, containerColor = colors.backgroundTop, dragHandle = null) {
    Column(Modifier.fillMaxWidth().testTag("my_decks.delete.confirmation")) {
      InlineTopBar(stringResource(R.string.my_decks_delete), onBack = null, leading = {
        TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel), color = colors.accent) }
      })
      Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
      ) {
        Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = colors.errorText, modifier = Modifier.size(54.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            stringResource(R.string.my_decks_delete_confirm_title_format, deck.localizedName(text.languageCode) ?: text.unavailableTitle),
            style = PiyoType.title2().copy(fontWeight = FontWeight.ExtraBold),
            textAlign = TextAlign.Center,
          )
          Text(stringResource(R.string.my_decks_delete_confirm_message), style = PiyoType.subheadline().copy(color = colors.mutedInk), textAlign = TextAlign.Center)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          if (onExportThenDelete != null) {
            SheetButton(stringResource(R.string.my_decks_delete_export_then_delete), Icons.Rounded.IosShare, colors.accent, colors.onAccent, "my_decks.delete.export_then_delete", onExportThenDelete)
          }
          SheetButton(stringResource(R.string.my_decks_delete), Icons.Rounded.Delete, colors.error.copy(alpha = 0.1f), colors.errorText, "my_decks.delete.confirm", onDelete)
          TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("my_decks.delete.cancel")) {
            Text(stringResource(R.string.common_cancel), style = PiyoType.subheadline().copy(color = colors.mutedInk, fontWeight = FontWeight.Bold))
          }
        }
      }
    }
  }
}

@Composable
private fun SheetButton(title: String, icon: ImageVector, background: Color, content: Color, tag: String, onClick: () -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 52.dp)
      .clip(RoundedCornerShape(17.dp))
      .background(background)
      .clickable(role = Role.Button, onClick = onClick)
      .semantics(mergeDescendants = true) {}
      .testTag(tag),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) { IconText(title, icon, content, style = PiyoType.headline().copy(fontWeight = FontWeight.Bold), iconSize = 18) }
}
