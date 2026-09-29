package app.piyokey.android.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material.icons.rounded.SdCardAlert
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.piyokey.android.R
import app.piyokey.android.data.AppData
import app.piyokey.android.feature.discover.InlineTopBar
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.nav.Route
import app.piyokey.android.ui.theme.L
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoBackground
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.ui.theme.centeredContent
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckType
import app.piyokey.core.deckkit.PiyoDeckPackageLimits
import app.piyokey.core.domain.UserDeckDraft
import app.piyokey.core.domain.UserDeckItemDraft
import app.piyokey.core.domain.UserDeckValidationFieldKind
import app.piyokey.core.domain.UserDeckValidationSummary
import app.piyokey.core.domain.library.DeckMakerCommitRules
import app.piyokey.core.domain.library.UserDeckSourceChangedException
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Deck Maker editor (iOS `DeckEditorView`) on the app navigator; autosaves the single active draft. */
class DeckEditorRoute(private val presentation: DeckEditorPresentation) : Route {
  @Composable
  override fun Content() {
    val navigator = LocalAppNavigator.current
    DeckEditorScreen(
      presentation = presentation,
      onDraftChange = { AppData.drafts.save(it).draftId },
      onSave = { deck, draftId -> DeckMakerCommits.save(deck, presentation, draftId ?: presentation.draftId) },
      onSaveAsCopy = { deck, draftId -> DeckMakerCommits.saveAsCopy(deck, presentation, draftId ?: presentation.draftId) },
      onDelete = presentation.deletesDeckId?.let { deckId ->
        { draftId: String? -> DeckMakerCommits.deleteFromEditor(deckId, presentation, draftId ?: presentation.draftId) }
      },
      onClose = { navigator.pop() },
      // Closing keeps the draft but opts it out of automatic restore (iOS 1.1.2, #195).
      onCancel = { latestDraftId -> DeckMakerPrefs.dismissedDraftId.value = latestDraftId ?: presentation.draftId },
    )
  }
}

/** iOS `@AppStorage("deck_maker.dismissed_draft_id")`. */
object DeckMakerPrefs {
  val dismissedDraftId = app.piyokey.android.data.settings.StringPref("deck_maker.dismissed_draft_id", "")
}

private enum class ItemPart { KOREAN, READING, MEANING }

private val validationKeyIds: Map<String, Int> = mapOf(
  "deck_editor.validation.single_language" to R.string.deck_editor_validation_single_language,
  "deck_editor.validation.name_required" to R.string.deck_editor_validation_name_required,
  "deck_editor.validation.author_required" to R.string.deck_editor_validation_author_required,
  "deck_editor.validation.tags_duplicate" to R.string.deck_editor_validation_tags_duplicate,
  "deck_editor.validation.tags" to R.string.deck_editor_validation_tags,
  "deck_editor.validation.items_required" to R.string.deck_editor_validation_items_required,
  "deck_editor.validation.korean_length" to R.string.deck_editor_validation_korean_length,
  "deck_editor.validation.korean_input" to R.string.deck_editor_validation_korean_input,
  "deck_editor.validation.item_limit" to R.string.deck_editor_validation_item_limit,
  "deck_editor.validation.text_length" to R.string.deck_editor_validation_text_length,
  "deck_editor.validation.duplicate" to R.string.deck_editor_validation_duplicate,
  "deck_editor.validation.required" to R.string.deck_editor_validation_required,
  "deck_editor.validation.invalid" to R.string.deck_editor_validation_invalid,
)

private fun validationRes(key: String) = validationKeyIds[key] ?: R.string.deck_editor_validation_invalid

@Composable
fun DeckEditorScreen(
  presentation: DeckEditorPresentation,
  onDraftChange: ((UserDeckDraft) -> String)?,
  onSave: suspend (Deck, String?) -> Unit,
  onSaveAsCopy: (suspend (Deck, String?) -> Unit)?,
  onDelete: (suspend (String?) -> Unit)?,
  onClose: () -> Unit,
  onCancel: ((String?) -> Unit)? = null,
) {
  val colors = Piyo.colors
  val scope = rememberCoroutineScope()
  val view = LocalView.current
  val initialLocale = presentation.draft.defaultLocale ?: "ja"
  var draft by remember { mutableStateOf(presentation.draft) }
  var editingLocale by remember { mutableStateOf(initialLocale) }
  var localeInput by remember { mutableStateOf(initialLocale) }
  var tagsText by remember { mutableStateOf(presentation.draft.tags(initialLocale).joinToString(", ")) }
  var pendingContentLocale by remember { mutableStateOf<String?>(null) }
  var summary by remember { mutableStateOf(UserDeckValidationSummary(emptyList())) }
  var saveErrorKey by remember { mutableStateOf<Int?>(null) }
  var draftSaveFailed by remember { mutableStateOf(false) }
  var isSaving by remember { mutableStateOf(false) }
  var isDeleting by remember { mutableStateOf(false) }
  var showsDeleteConfirmation by remember { mutableStateOf(false) }
  var expanded by remember { mutableStateOf(DeckMakerCommitRules.initiallyExpandedItemIds(presentation.draft)) }
  var isReordering by remember { mutableStateOf(false) }
  var completedTerminalAction by remember { mutableStateOf(false) }
  var latestDraftId by remember { mutableStateOf<String?>(presentation.draftId) }
  var focusRequest by remember { mutableIntStateOf(0) }
  val listState = rememberLazyListState()
  val focusRequesters = remember { HashMap<String, FocusRequester>() }
  fun requester(key: String) = focusRequesters.getOrPut(key) { FocusRequester() }
  val busy = isSaving || isDeleting

  fun persist(value: UserDeckDraft): Boolean {
    val change = onDraftChange ?: return true
    return try {
      latestDraftId = change(value)
      draftSaveFailed = false
      true
    } catch (_: Exception) {
      draftSaveFailed = true
      false
    }
  }

  // Autosave 350 ms after the last change (iOS `scheduleDraftSave`).
  var lastPersisted by remember { mutableStateOf(presentation.draft) }
  LaunchedEffect(draft) {
    if (onDraftChange == null || draft == lastPersisted) return@LaunchedEffect
    delay(350)
    if (persist(draft)) lastPersisted = draft
  }
  // Plain refs: a terminal save pops the route before another recomposition could run.
  val terminalRef = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
  val draftRef = remember { java.util.concurrent.atomic.AtomicReference(presentation.draft) }
  androidx.compose.runtime.SideEffect { draftRef.set(draft) }
  LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (!terminalRef.get()) persist(draftRef.get()) }
  DisposableEffect(Unit) { onDispose { if (!terminalRef.get()) persist(draftRef.get()) } }

  fun cancel() {
    if (busy) return
    if (persist(draft)) {
      onCancel?.invoke(latestDraftId)
      onClose()
    }
  }
  BackHandler { cancel() }

  fun announceFirst() {
    val key = summary.localizationKeys.firstOrNull() ?: "deck_editor.validation.invalid"
    @Suppress("DEPRECATION")
    view.announceForAccessibility(L.string(validationRes(key)))
  }

  LaunchedEffect(focusRequest) {
    if (focusRequest == 0) return@LaunchedEffect
    val field = summary.firstField ?: return@LaunchedEffect
    val itemIndex = field.index
    val (listKey, focusKey) = when (field.kind) {
      UserDeckValidationFieldKind.LANGUAGE -> "language" to "language"
      UserDeckValidationFieldKind.NAME -> "name" to "name"
      UserDeckValidationFieldKind.AUTHOR -> "author" to "author"
      UserDeckValidationFieldKind.TAGS -> "tags" to "tags"
      UserDeckValidationFieldKind.ITEMS -> "items.header" to null
      else -> {
        val item = itemIndex?.let { draft.items.getOrNull(it) }
        if (item == null) {
          "items.header" to null
        } else {
          expanded = expanded + item.id
          val part = when (field.kind) {
            UserDeckValidationFieldKind.ITEM_KOREAN -> ItemPart.KOREAN
            UserDeckValidationFieldKind.ITEM_READING -> ItemPart.READING
            UserDeckValidationFieldKind.ITEM_MEANING -> ItemPart.MEANING
            else -> null
          }
          item.id to part?.let { "${item.id}:${it.name}" }
        }
      }
    }
    val index = keyIndex(listKey, draft)
    if (index >= 0) listState.animateScrollToItem(index)
    delay(60)
    focusKey?.let { runCatching { requester(it).requestFocus() } }
    announceFirst()
  }

  fun save() {
    saveErrorKey = null
    if (!persist(draft)) return
    summary = draft.validationSummary(Instant.now(), editingLocale)
    if (!summary.isEmpty) {
      focusRequest++
      return
    }
    val deck = try {
      draft.validatedDeck(Instant.now(), editingLocale)
    } catch (_: Exception) {
      summary = draft.validationSummary(Instant.now(), editingLocale)
      focusRequest++
      return
    }
    isSaving = true
    scope.launch {
      try {
        onSave(deck, latestDraftId)
        completedTerminalAction = true
        terminalRef.set(true)
        isSaving = false
        onClose()
      } catch (_: UserDeckSourceChangedException) {
        isSaving = false
        saveErrorKey = R.string.deck_editor_save_source_changed
      } catch (_: Exception) {
        isSaving = false
        saveErrorKey = R.string.deck_editor_save_failed
      }
    }
  }

  fun saveAsCopy() {
    val action = onSaveAsCopy ?: return
    if (!persist(draft)) return
    summary = draft.validationSummary(Instant.now(), editingLocale)
    val deck = if (summary.isEmpty) runCatching { draft.validatedDeck(Instant.now(), editingLocale) }.getOrNull() else null
    if (deck == null) {
      focusRequest++
      return
    }
    isSaving = true
    scope.launch {
      try {
        action(deck, latestDraftId)
        completedTerminalAction = true
        terminalRef.set(true)
        isSaving = false
        onClose()
      } catch (_: Exception) {
        isSaving = false
        saveErrorKey = R.string.deck_editor_save_failed
      }
    }
  }

  fun deleteDeck() {
    val action = onDelete ?: return
    saveErrorKey = null
    isDeleting = true
    scope.launch {
      try {
        action(latestDraftId)
        completedTerminalAction = true
        terminalRef.set(true)
        isDeleting = false
        onClose()
      } catch (_: Exception) {
        isDeleting = false
        saveErrorKey = R.string.deck_editor_delete_failed
      }
    }
  }

  fun synchronizeLocale(code: String) {
    editingLocale = code
    localeInput = code
    tagsText = draft.tags(code).joinToString(", ")
    summary = draft.validationSummary(Instant.now(), code)
  }

  fun requestLanguageChange() {
    val canonical = UserDeckDraft.canonicalLocale(localeInput) ?: return
    if (draft.requiresContentBundleSelection) {
      if (canonical !in draft.contentLocaleCodes) return
      pendingContentLocale = canonical
    } else {
      draft = draft.retaggingDeckLanguage(canonical)
      synchronizeLocale(canonical)
    }
  }

  val titleRes = when (draft.origin) {
    UserDeckDraft.Origin.New -> R.string.deck_editor_title_new
    UserDeckDraft.Origin.Editing -> R.string.deck_editor_title_edit
    is UserDeckDraft.Origin.OfficialCopy -> R.string.deck_editor_title_copy
  }

  PiyoBackground {
    Column(Modifier.fillMaxSize().testTag("deck_editor.screen")) {
      InlineTopBar(
        stringResource(titleRes),
        onBack = null,
        leading = {
          TextButton(onClick = { cancel() }, enabled = !busy, modifier = Modifier.testTag("deck_editor.cancel")) {
            Text(stringResource(R.string.deck_editor_cancel), color = colors.accent)
          }
        },
        trailing = {
          TextButton(onClick = { isReordering = !isReordering }, enabled = !busy, modifier = Modifier.testTag("deck_editor.reorder")) {
            Text(stringResource(if (isReordering) R.string.deck_editor_items_reorder_done else R.string.deck_editor_items_reorder), color = colors.accent)
          }
          if (isSaving) {
            val label = stringResource(R.string.deck_editor_saving)
            CircularProgressIndicator(
              color = colors.accent,
              strokeWidth = 2.dp,
              modifier = Modifier.padding(horizontal = 14.dp).size(20.dp).semantics { contentDescription = label },
            )
          } else {
            TextButton(onClick = { save() }, enabled = !busy, modifier = Modifier.testTag("deck_editor.save")) {
              Text(stringResource(R.string.deck_editor_save), color = colors.accent, fontWeight = FontWeight.Bold)
            }
          }
        },
      )
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().imePadding().centeredContent(Piyo.metrics.formContentMaxWidth),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Metadata ---------------------------------------------------------------------
        item("metadata.header") {
          Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionHeaderText(stringResource(R.string.deck_editor_metadata_section), Modifier.weight(1f))
            LanguageLabel(editingLocale)
          }
        }
        item("language") {
          FormCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
              EditorField(
                value = localeInput,
                onValueChange = { localeInput = it },
                label = stringResource(R.string.deck_editor_language_accessibility_format, editingLocale),
                tag = "deck_editor.language",
                modifier = Modifier.weight(1f).focusRequester(requester("language")),
                capitalization = KeyboardCapitalization.None,
                singleLine = true,
              )
              val canonical = UserDeckDraft.canonicalLocale(localeInput)
              val enabled = canonical != null && (!draft.requiresContentBundleSelection || canonical in draft.contentLocaleCodes)
              IconButton(
                onClick = { requestLanguageChange() },
                enabled = enabled,
                modifier = Modifier.testTag("deck_editor.language.apply").semantics {
                  contentDescription = L.string(R.string.deck_editor_language_accessibility_format, editingLocale)
                },
              ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = if (enabled) colors.accent else colors.mutedInk.copy(alpha = 0.4f))
              }
            }
            HelpText(stringResource(R.string.deck_editor_language_single_help))
            if (draft.requiresContentBundleSelection) {
              IconText(stringResource(R.string.deck_editor_language_multilingual_warning), Icons.Rounded.Warning, colors.errorText, style = PiyoType.caption())
              Text(stringResource(R.string.deck_editor_language_bundle_picker), style = PiyoType.caption().copy(color = colors.mutedInk))
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                draft.contentLocaleCodes.forEach { code ->
                  Choice(code, localeInput == code, "deck_editor.language.bundle.$code") { localeInput = code }
                }
              }
            }
          }
        }
        item("name") {
          FormCard {
            EditorField(
              value = draft.name(editingLocale),
              onValueChange = { draft = draft.withName(it, editingLocale) },
              label = stringResource(R.string.deck_editor_name),
              tag = "deck_editor.name",
              modifier = Modifier.fillMaxWidth().focusRequester(requester("name")),
              capitalization = KeyboardCapitalization.Sentences,
            )
          }
        }
        item("author") {
          FormCard {
            EditorField(
              value = draft.authorNickname(editingLocale),
              onValueChange = { draft = draft.withAuthorNickname(it, editingLocale) },
              label = stringResource(R.string.deck_editor_author),
              tag = "deck_editor.author",
              modifier = Modifier.fillMaxWidth().focusRequester(requester("author")),
              capitalization = KeyboardCapitalization.Words,
            )
          }
        }
        item("type.level") {
          FormCard {
            Text(stringResource(R.string.deck_editor_type), style = PiyoType.caption().copy(color = colors.mutedInk, fontWeight = FontWeight.SemiBold))
            Segmented(
              listOf(DeckType.WORD to R.string.deck_editor_type_word, DeckType.SENTENCE to R.string.deck_editor_type_sentence),
              draft.type,
              "deck_editor.type",
            ) { draft = draft.copy(type = it) }
            Text(stringResource(R.string.deck_editor_level), style = PiyoType.caption().copy(color = colors.mutedInk, fontWeight = FontWeight.SemiBold))
            Segmented(
              listOf(1 to R.string.deck_editor_level_1, 2 to R.string.deck_editor_level_2, 3 to R.string.deck_editor_level_3),
              draft.level,
              "deck_editor.level",
            ) { draft = draft.copy(level = it) }
          }
        }
        item("tags") {
          FormCard {
            EditorField(
              value = tagsText,
              onValueChange = {
                tagsText = it
                draft = draft.withTags(UserDeckDraft.parseTags(it), editingLocale)
              },
              label = stringResource(R.string.deck_editor_tags),
              tag = "deck_editor.tags",
              modifier = Modifier.fillMaxWidth().focusRequester(requester("tags")),
              capitalization = KeyboardCapitalization.None,
            )
            HelpText(stringResource(R.string.deck_editor_tags_help))
          }
        }
        // Items ------------------------------------------------------------------------
        item("items.header") {
          Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionHeaderText(stringResource(R.string.deck_editor_items_section), Modifier.weight(1f))
            Text(draft.items.size.toString(), style = PiyoType.caption().copy(color = colors.mutedInk))
          }
        }
        itemsIndexed(draft.items, key = { _, item -> item.id }) { index, item ->
          ItemCard(
            item = item,
            index = index,
            count = draft.items.size,
            locale = editingLocale,
            expanded = item.id in expanded,
            reordering = isReordering,
            requester = ::requester,
            onToggle = { expanded = if (item.id in expanded) expanded - item.id else expanded + item.id },
            onChange = { updated -> draft = draft.updatingItem(index) { updated } },
            onMove = { delta ->
              val destination = if (delta < 0) index - 1 else index + 2
              if (index + delta in draft.items.indices) draft = draft.movingItems(setOf(index), destination)
            },
            onDelete = if (draft.items.size > 1) ({
              draft = draft.removingItems(setOf(index))
              expanded = expanded - item.id
            }) else null,
          )
        }
        item("items.add") {
          val enabled = draft.items.size < PiyoDeckPackageLimits.MAXIMUM_ITEM_COUNT
          Row(
            Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(colors.card)
              .clickable(enabled = enabled, role = Role.Button) {
                draft = draft.addingItem()
                draft.items.lastOrNull()?.let { added ->
                  expanded = expanded + added.id
                  scope.launch {
                    listState.animateScrollToItem(keyIndex(added.id, draft))
                    delay(60)
                    runCatching { requester("${added.id}:${ItemPart.KOREAN.name}").requestFocus() }
                  }
                }
              }
              .testTag("deck_editor.items.add")
              .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconText(stringResource(R.string.deck_editor_items_add), Icons.Rounded.AddCircle, if (enabled) colors.accent else colors.mutedInk, style = PiyoType.body(), iconSize = 20)
          }
          HelpText(stringResource(R.string.deck_editor_items_help), Modifier.padding(top = 6.dp, start = 4.dp))
        }
        // Validation -------------------------------------------------------------------
        if (!summary.isEmpty || saveErrorKey != null || draftSaveFailed) {
          item("validation") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              SectionHeaderText(stringResource(R.string.deck_editor_validation_section))
              FormCard(Modifier.testTag("deck_editor.validation")) {
                if (draftSaveFailed) IconText(stringResource(R.string.deck_editor_draft_save_failed), Icons.Rounded.SdCardAlert, colors.errorText, style = PiyoType.subheadline(), iconSize = 18)
                saveErrorKey?.let { key ->
                  IconText(stringResource(key), Icons.Rounded.Warning, colors.errorText, style = PiyoType.subheadline(), iconSize = 18)
                  if (key == R.string.deck_editor_save_source_changed && onSaveAsCopy != null) {
                    val hint = stringResource(R.string.deck_editor_save_as_copy_hint)
                    TextButton(
                      onClick = { saveAsCopy() },
                      enabled = !busy,
                      modifier = Modifier.testTag("deck_editor.save_as_copy").semantics { contentDescription = hint },
                    ) { IconText(stringResource(R.string.deck_editor_save_as_copy), Icons.Rounded.ContentCopy, colors.accent, style = PiyoType.body(), iconSize = 18) }
                  }
                }
                summary.localizationKeys.forEach { key ->
                  IconText(stringResource(validationRes(key)), Icons.Rounded.Error, colors.errorText, style = PiyoType.subheadline(), iconSize = 18)
                }
              }
            }
          }
        }
        if (onDelete != null) {
          item("delete") {
            FormCard {
              TextButton(
                onClick = { showsDeleteConfirmation = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("deck_editor.delete"),
              ) { IconText(stringResource(R.string.deck_editor_delete), Icons.Rounded.Delete, colors.error, style = PiyoType.body(), iconSize = 18) }
            }
          }
        }
      }
    }
  }

  if (showsDeleteConfirmation) {
    AlertDialog(
      onDismissRequest = { showsDeleteConfirmation = false },
      title = { Text(stringResource(R.string.deck_editor_delete_confirm_title)) },
      text = { Text(stringResource(R.string.deck_editor_delete_confirm_message)) },
      confirmButton = {
        TextButton(onClick = {
          showsDeleteConfirmation = false
          deleteDeck()
        }) { Text(stringResource(R.string.deck_editor_delete_confirm_action), color = colors.error) }
      },
      dismissButton = { TextButton(onClick = { showsDeleteConfirmation = false }) { Text(stringResource(R.string.deck_editor_cancel)) } },
      containerColor = colors.card,
    )
  }
  pendingContentLocale?.let { code ->
    AlertDialog(
      onDismissRequest = { pendingContentLocale = null },
      title = { Text(stringResource(R.string.deck_editor_language_change_title)) },
      text = { Text(stringResource(R.string.deck_editor_language_change_message)) },
      confirmButton = {
        TextButton(onClick = {
          draft = draft.selectingContentBundle(code)
          synchronizeLocale(code)
          pendingContentLocale = null
        }) { Text(stringResource(R.string.deck_editor_language_change_confirm), color = colors.error) }
      },
      dismissButton = { TextButton(onClick = { pendingContentLocale = null }) { Text(stringResource(R.string.deck_editor_cancel)) } },
      containerColor = colors.card,
    )
  }
}

/** Index of a LazyColumn key in the editor list (fixed metadata rows precede the items). */
private fun keyIndex(key: String, draft: UserDeckDraft): Int {
  val fixed = listOf("metadata.header", "language", "name", "author", "type.level", "tags", "items.header")
  fixed.indexOf(key).takeIf { it >= 0 }?.let { return it }
  val itemIndex = draft.items.indexOfFirst { it.id == key }
  return if (itemIndex >= 0) fixed.size + itemIndex else -1
}

@Composable
private fun ItemCard(
  item: UserDeckItemDraft,
  index: Int,
  count: Int,
  locale: String,
  expanded: Boolean,
  reordering: Boolean,
  requester: (String) -> FocusRequester,
  onToggle: () -> Unit,
  onChange: (UserDeckItemDraft) -> Unit,
  onMove: (Int) -> Unit,
  onDelete: (() -> Unit)?,
) {
  val colors = Piyo.colors
  FormCard(Modifier.testTag("deck_editor.item.$index")) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (reordering && onDelete != null) {
        IconButton(onClick = onDelete, modifier = Modifier.testTag("deck_editor.item.$index.delete")) {
          Icon(Icons.Rounded.RemoveCircle, contentDescription = stringResource(R.string.library_item_delete), tint = colors.error)
        }
      }
      Column(
        Modifier
          .weight(1f)
          .clip(RoundedCornerShape(8.dp))
          .clickable(role = Role.Button, onClick = onToggle)
          .semantics(mergeDescendants = true) {}
          .heightIn(min = 44.dp)
          .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Text(stringResource(R.string.deck_editor_item_position_format, index + 1, count), style = PiyoType.subheadline().copy(fontWeight = FontWeight.SemiBold))
        Text(
          item.ko.ifEmpty { stringResource(R.string.deck_editor_item_empty) },
          style = PiyoType.caption().copy(color = colors.mutedInk),
          maxLines = 1,
        )
      }
      if (reordering) {
        IconButton(onClick = { onMove(-1) }, enabled = index > 0) {
          Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.library_item_move_up), tint = colors.ink)
        }
        IconButton(onClick = { onMove(1) }, enabled = index < count - 1) {
          Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.library_item_move_down), tint = colors.ink)
        }
      } else {
        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = colors.mutedInk)
      }
    }
    if (expanded && !reordering) {
      EditorField(
        value = item.ko,
        onValueChange = { onChange(item.copy(ko = UserDeckDraft.acceptedKoreanInput(item.ko, it))) },
        label = stringResource(R.string.deck_editor_item_korean),
        tag = "deck_editor.item.$index.korean",
        modifier = Modifier.fillMaxWidth().focusRequester(requester("${item.id}:${ItemPart.KOREAN.name}")),
        capitalization = KeyboardCapitalization.None,
      )
      HelpText(
        stringResource(
          R.string.deck_editor_item_korean_count_format,
          DeckMakerCommitRules.koreanSyllableCount(item.ko),
          UserDeckDraft.MAXIMUM_KOREAN_SYLLABLE_COUNT,
          item.ko.codePointCount(0, item.ko.length),
          UserDeckDraft.MAXIMUM_KOREAN_CHARACTER_COUNT,
        ),
      )
      val reading = item.reading(locale)
      EditorField(
        value = reading,
        onValueChange = { onChange(item.withReading(UserDeckDraft.acceptedMeaningOrReadingInput(reading, it), locale)) },
        label = stringResource(R.string.deck_editor_item_reading),
        tag = "deck_editor.item.$index.reading",
        modifier = Modifier.fillMaxWidth().focusRequester(requester("${item.id}:${ItemPart.READING.name}")),
        capitalization = KeyboardCapitalization.None,
      )
      HelpText(stringResource(R.string.deck_editor_item_text_count_format, UserDeckDraft.productCharacterCount(reading), UserDeckDraft.MAXIMUM_MEANING_OR_READING_COUNT))
      val meaning = item.meaning(locale)
      EditorField(
        value = meaning,
        onValueChange = { onChange(item.withMeaning(UserDeckDraft.acceptedMeaningOrReadingInput(meaning, it), locale)) },
        label = stringResource(R.string.deck_editor_item_meaning),
        tag = "deck_editor.item.$index.meaning",
        modifier = Modifier.fillMaxWidth().focusRequester(requester("${item.id}:${ItemPart.MEANING.name}")),
        capitalization = KeyboardCapitalization.Sentences,
      )
      HelpText(stringResource(R.string.deck_editor_item_text_count_format, UserDeckDraft.productCharacterCount(meaning), UserDeckDraft.MAXIMUM_MEANING_OR_READING_COUNT))
    }
  }
}

@Composable
private fun FormCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Column(
    modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Piyo.colors.card).padding(horizontal = 14.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) { content() }
}

@Composable
private fun SectionHeaderText(text: String, modifier: Modifier = Modifier) {
  Text(
    text.uppercase(app.piyokey.android.data.settings.AppSettings.currentLanguage.locale),
    style = PiyoType.footnote().copy(color = Piyo.colors.mutedInk),
    modifier = modifier.padding(start = 4.dp).semantics { heading() },
  )
}

@Composable
private fun HelpText(text: String, modifier: Modifier = Modifier) {
  Text(text, style = PiyoType.caption().copy(color = Piyo.colors.mutedInk), modifier = modifier)
}

@Composable
private fun LanguageLabel(code: String) {
  val description = stringResource(R.string.deck_editor_language_accessibility_format, code)
  Row(
    Modifier.semantics(mergeDescendants = true) { contentDescription = description },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(Icons.Rounded.Translate, contentDescription = null, tint = Piyo.colors.mutedInk, modifier = Modifier.size(14.dp))
    Text(code, style = PiyoType.caption().copy(color = Piyo.colors.mutedInk, fontWeight = FontWeight.SemiBold))
  }
}

@Composable
private fun EditorField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  tag: String,
  modifier: Modifier,
  capitalization: KeyboardCapitalization,
  singleLine: Boolean = false,
) {
  val colors = Piyo.colors
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label) },
    singleLine = singleLine,
    textStyle = PiyoType.body(),
    keyboardOptions = KeyboardOptions(capitalization = capitalization, autoCorrectEnabled = capitalization != KeyboardCapitalization.None),
    colors = OutlinedTextFieldDefaults.colors(
      focusedBorderColor = colors.accent,
      unfocusedBorderColor = colors.mutedInk.copy(alpha = 0.3f),
      focusedLabelColor = colors.accent,
      unfocusedLabelColor = colors.mutedInk,
      cursorColor = colors.accent,
      focusedTextColor = colors.ink,
      unfocusedTextColor = colors.ink,
    ),
    modifier = modifier.testTag(tag),
  )
}

@Composable
private fun Choice(text: String, selected: Boolean, tag: String, onClick: () -> Unit) {
  val colors = Piyo.colors
  Box(
    Modifier
      .heightIn(min = 44.dp)
      .clip(RoundedCornerShape(22.dp))
      .background(if (selected) colors.accent else colors.backgroundBottom)
      .clickable(role = Role.RadioButton, onClick = onClick)
      .testTag(tag)
      .padding(horizontal = 14.dp, vertical = 10.dp),
    contentAlignment = Alignment.Center,
  ) { Text(text, style = PiyoType.subheadline().copy(color = if (selected) colors.onAccent else colors.ink)) }
}

@Composable
private fun <T> Segmented(options: List<Pair<T, Int>>, selected: T, tag: String, onSelect: (T) -> Unit) {
  val colors = Piyo.colors
  SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
    options.forEachIndexed { index, (value, res) ->
      SegmentedButton(
        selected = value == selected,
        onClick = { onSelect(value) },
        shape = SegmentedButtonDefaults.itemShape(index, options.size),
        colors = SegmentedButtonDefaults.colors(
          activeContainerColor = colors.accentSoft,
          activeContentColor = colors.ink,
          inactiveContainerColor = colors.card,
          inactiveContentColor = colors.ink,
        ),
        modifier = Modifier.testTag("$tag.$index"),
        icon = {},
      ) { Text(stringResource(res), style = PiyoType.subheadline()) }
    }
  }
}
