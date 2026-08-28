package app.piyokey.feature.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.piyokey.core.deckkit.DeckType
import app.piyokey.core.design.PiyokeyIcon
import app.piyokey.core.design.PiyokeyIconKind
import app.piyokey.core.piyodeck.UserDeckDraft
import app.piyokey.core.piyodeck.UserDeckItemDraft
import app.piyokey.core.piyodeck.UserDeckLanguage
import app.piyokey.core.piyodeck.UserDeckValidationField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class DeckMakerUiNotice {
  PURCHASE_PENDING, PURCHASE_FAILED, RESTORE_SUCCEEDED, NOTHING_TO_RESTORE,
  RESTORE_FAILED, PRODUCT_UNAVAILABLE,
}

@Composable
fun DeckMakerPaywallScreen(
  formattedPrice: String?,
  hasAccess: Boolean,
  isWorking: Boolean,
  notice: DeckMakerUiNotice?,
  onPurchase: () -> Unit,
  onRestore: () -> Unit,
  onDismissNotice: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onClose)
  LazyColumn(
    modifier = modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("deck-maker-paywall"),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) }
        Text(stringResource(R.string.deck_maker_product_name), fontWeight = FontWeight.Bold)
      }
    }
    item {
      Text(stringResource(R.string.deck_maker_paywall_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
      Spacer(Modifier.height(8.dp))
      Text(stringResource(R.string.deck_maker_paywall_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    item {
      Card {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          listOf(
            R.string.deck_maker_feature_unlimited,
            R.string.deck_maker_feature_create,
            R.string.deck_maker_feature_edit,
            R.string.deck_maker_feature_copy,
            R.string.deck_maker_feature_lifetime,
          ).forEachIndexed { index, resource ->
            if (index > 0) HorizontalDivider()
            Text("✓ ${stringResource(resource)}", fontWeight = FontWeight.SemiBold)
          }
        }
      }
    }
    item {
      Text("✓ ${stringResource(R.string.deck_maker_free_note)}", color = MaterialTheme.colorScheme.primary)
    }
    notice?.let { current ->
      item {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
          Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(current.messageResource()), modifier = Modifier.weight(1f))
            TextButton(onClick = onDismissNotice) { Text(stringResource(R.string.action_ok)) }
          }
        }
      }
    }
    item {
      Button(
        onClick = onPurchase,
        enabled = !isWorking,
        modifier = Modifier.fillMaxWidth().testTag("deck-maker-purchase"),
      ) {
        if (isWorking) CircularProgressIndicator(Modifier.height(18.dp))
        Text(
          if (hasAccess) stringResource(R.string.deck_maker_access_ready)
          else formattedPrice?.let { stringResource(R.string.deck_maker_buy_price, it) }
            ?: stringResource(R.string.deck_maker_buy),
          modifier = Modifier.padding(horizontal = 8.dp),
        )
      }
      TextButton(onClick = onRestore, enabled = !isWorking, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.deck_maker_restore))
      }
    }
  }
}

@Composable
fun UserDeckEditorScreen(
  initialDraft: UserDeckDraft,
  isWorking: Boolean,
  saveError: Boolean,
  onDraftChanged: (UserDeckDraft) -> Unit,
  onSave: (UserDeckDraft, UserDeckLanguage) -> Unit,
  onClose: (UserDeckDraft) -> Unit,
  modifier: Modifier = Modifier,
) {
  val language = currentUserDeckLanguage()
  var draft by remember(initialDraft.deckId) { mutableStateOf(initialDraft) }
  var expandedItemId by remember(initialDraft.deckId) { mutableStateOf(draft.items.firstOrNull()?.id) }
  var validationMessage by remember { mutableStateOf<Int?>(null) }
  var itemFocusRequest by remember { mutableStateOf<Pair<UserDeckValidationField, Int>?>(null) }
  var focusRequestSerial by remember { mutableStateOf(0) }
  val listState = rememberLazyListState()
  val nameFocus = remember { FocusRequester() }
  val authorFocus = remember { FocusRequester() }
  val tagsFocus = remember { FocusRequester() }
  val scope = rememberCoroutineScope()

  LaunchedEffect(draft) {
    delay(500)
    onDraftChanged(draft)
  }
  BackHandler { onClose(draft) }
  LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { onDraftChanged(draft) }

  LazyColumn(
    state = listState,
    modifier = modifier.fillMaxSize().testTag("deck-maker-editor"),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        TextButton(onClick = { onClose(draft) }, enabled = !isWorking) { Text(stringResource(R.string.action_close)) }
        Text(stringResource(R.string.deck_editor_title), fontWeight = FontWeight.Black)
        TextButton(
          onClick = {
            val summary = draft.validationSummary(java.time.Instant.now(), language)
            if (summary.issues.isEmpty()) {
              validationMessage = null
              onDraftChanged(draft)
              onSave(draft, language)
            } else {
              validationMessage = R.string.deck_editor_validation_error
              when (val field = summary.firstField) {
                UserDeckValidationField.Name,
                UserDeckValidationField.Author,
                UserDeckValidationField.Tags,
                -> {
                  when (field) {
                    UserDeckValidationField.Name -> nameFocus.requestFocus()
                    UserDeckValidationField.Author -> authorFocus.requestFocus()
                    UserDeckValidationField.Tags -> tagsFocus.requestFocus()
                  }
                  scope.launch { listState.animateScrollToItem(1) }
                }
                UserDeckValidationField.Items -> scope.launch {
                  listState.animateScrollToItem(1)
                }
                is UserDeckValidationField.Item,
                is UserDeckValidationField.ItemKorean,
                is UserDeckValidationField.ItemMeaning,
                is UserDeckValidationField.ItemReading,
                -> {
                  val index = when (field) {
                    is UserDeckValidationField.Item -> field.index
                    is UserDeckValidationField.ItemKorean -> field.index
                    is UserDeckValidationField.ItemMeaning -> field.index
                    is UserDeckValidationField.ItemReading -> field.index
                  }
                  expandedItemId = draft.items.getOrNull(index)?.id
                  focusRequestSerial += 1
                  itemFocusRequest = field to focusRequestSerial
                  scope.launch { listState.animateScrollToItem(index + 2) }
                }
                null -> Unit
              }
            }
          },
          enabled = !isWorking,
          modifier = Modifier.testTag("deck-editor-save"),
        ) { Text(stringResource(if (isWorking) R.string.deck_editor_saving else R.string.deck_editor_save)) }
      }
    }
    item {
      Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.deck_editor_language, language.code.uppercase()), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        validationMessage?.let {
          Text(
            stringResource(it),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
              .testTag("deck-editor-validation"),
          )
        }
        if (saveError) {
          Text(stringResource(R.string.deck_editor_save_error), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
        }
        OutlinedTextField(
          value = draft.name(language),
          onValueChange = { draft = draft.withName(it, language) },
          label = { Text(stringResource(R.string.deck_editor_name)) },
          modifier = Modifier.fillMaxWidth().focusRequester(nameFocus).testTag("deck-editor-name"),
          singleLine = true,
        )
        OutlinedTextField(
          value = draft.authorNickname(language),
          onValueChange = { draft = draft.withAuthorNickname(it, language) },
          label = { Text(stringResource(R.string.deck_editor_author)) },
          modifier = Modifier.fillMaxWidth().focusRequester(authorFocus),
          singleLine = true,
        )
        OutlinedTextField(
          value = draft.tags(language).joinToString(", "),
          onValueChange = { draft = draft.withTags(UserDeckDraft.parseTags(it), language) },
          label = { Text(stringResource(R.string.deck_editor_tags)) },
          modifier = Modifier.fillMaxWidth().focusRequester(tagsFocus),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { draft = draft.copy(type = DeckType.WORD) },
            enabled = draft.type != DeckType.WORD,
          ) { Text(stringResource(R.string.filter_words)) }
          OutlinedButton(
            onClick = { draft = draft.copy(type = DeckType.SENTENCE) },
            enabled = draft.type != DeckType.SENTENCE,
          ) { Text(stringResource(R.string.filter_sentences)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          (1..3).forEach { level ->
            OutlinedButton(onClick = { draft = draft.copy(level = level) }, enabled = draft.level != level) {
              Text(stringResource(R.string.deck_editor_level, level))
            }
          }
        }
        Text(stringResource(R.string.deck_editor_item_count, draft.items.size), fontWeight = FontWeight.Bold)
      }
    }
    itemsIndexed(draft.items, key = { _, item -> item.id }) { index, item ->
      DeckEditorItem(
        index = index,
        item = item,
        language = language,
        expanded = expandedItemId == item.id,
        canDelete = draft.items.size > 1,
        canMoveDown = index < draft.items.lastIndex,
        focusRequest = itemFocusRequest?.takeIf { (field, _) -> field.itemIndexOrNull() == index },
        onToggle = { expandedItemId = if (expandedItemId == item.id) null else item.id },
        onChange = { updated -> draft = draft.copy(items = draft.items.toMutableList().also { it[index] = updated }) },
        onMoveUp = { draft = draft.moveItem(index, index - 1) },
        onMoveDown = { draft = draft.moveItem(index, index + 1) },
        onDelete = { draft = draft.removeItem(index) },
        modifier = Modifier.padding(horizontal = 18.dp),
      )
    }
    item {
      OutlinedButton(
        onClick = { draft = draft.addItem() },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).testTag("deck-editor-add-item"),
      ) { Text(stringResource(R.string.deck_editor_add_item)) }
      Spacer(Modifier.height(32.dp))
    }
  }
}

@Composable
private fun DeckEditorItem(
  index: Int,
  item: UserDeckItemDraft,
  language: UserDeckLanguage,
  expanded: Boolean,
  canDelete: Boolean,
  canMoveDown: Boolean,
  focusRequest: Pair<UserDeckValidationField, Int>?,
  onToggle: () -> Unit,
  onChange: (UserDeckItemDraft) -> Unit,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val koreanFocus = remember(item.id) { FocusRequester() }
  val readingFocus = remember(item.id) { FocusRequester() }
  val meaningFocus = remember(item.id) { FocusRequester() }
  LaunchedEffect(expanded, focusRequest) {
    val field = focusRequest?.first ?: return@LaunchedEffect
    if (!expanded) return@LaunchedEffect
    delay(50)
    when (field) {
      is UserDeckValidationField.ItemReading -> readingFocus.requestFocus()
      is UserDeckValidationField.ItemMeaning -> meaningFocus.requestFocus()
      else -> koreanFocus.requestFocus()
    }
  }
  Card(modifier.fillMaxWidth()) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
      TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Text("${index + 1}. ${item.ko.ifBlank { stringResource(R.string.deck_editor_empty_item) }}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Text(if (expanded) "▲" else "▼")
      }
      if (expanded) {
        OutlinedTextField(
          value = item.ko,
          onValueChange = { onChange(item.copy(ko = it)) },
          label = { Text(stringResource(R.string.deck_editor_korean)) },
          modifier = Modifier.fillMaxWidth().focusRequester(koreanFocus).testTag("deck-editor-item-$index-ko"),
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        )
        OutlinedTextField(
          value = item.reading(language),
          onValueChange = { onChange(item.withReading(it, language)) },
          label = { Text(stringResource(R.string.deck_editor_reading)) },
          modifier = Modifier.fillMaxWidth().focusRequester(readingFocus),
        )
        OutlinedTextField(
          value = item.meaning(language),
          onValueChange = { onChange(item.withMeaning(it, language)) },
          label = { Text(stringResource(R.string.deck_editor_meaning)) },
          modifier = Modifier.fillMaxWidth().focusRequester(meaningFocus),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          TextButton(onClick = onMoveUp, enabled = index > 0) {
            PiyokeyIcon(PiyokeyIconKind.MOVE_UP, stringResource(R.string.deck_editor_move_up), Modifier.size(20.dp))
          }
          TextButton(onClick = onMoveDown, enabled = canMoveDown) {
            PiyokeyIcon(PiyokeyIconKind.MOVE_DOWN, stringResource(R.string.deck_editor_move_down), Modifier.size(20.dp))
          }
          TextButton(onClick = onDelete, enabled = canDelete) { Text(stringResource(R.string.deck_editor_delete_item), color = MaterialTheme.colorScheme.error) }
        }
      }
    }
  }
}

private fun UserDeckValidationField.itemIndexOrNull(): Int? = when (this) {
  is UserDeckValidationField.Item -> index
  is UserDeckValidationField.ItemKorean -> index
  is UserDeckValidationField.ItemReading -> index
  is UserDeckValidationField.ItemMeaning -> index
  else -> null
}

@Composable
private fun currentUserDeckLanguage(): UserDeckLanguage = when (LocalConfiguration.current.locales[0].language) {
  "en" -> UserDeckLanguage.ENGLISH
  "ko" -> UserDeckLanguage.KOREAN
  else -> UserDeckLanguage.JAPANESE
}

private fun DeckMakerUiNotice.messageResource(): Int = when (this) {
  DeckMakerUiNotice.PURCHASE_PENDING -> R.string.deck_maker_notice_pending
  DeckMakerUiNotice.PURCHASE_FAILED -> R.string.deck_maker_notice_purchase_failed
  DeckMakerUiNotice.RESTORE_SUCCEEDED -> R.string.deck_maker_notice_restore_succeeded
  DeckMakerUiNotice.NOTHING_TO_RESTORE -> R.string.deck_maker_notice_restore_empty
  DeckMakerUiNotice.RESTORE_FAILED -> R.string.deck_maker_notice_restore_failed
  DeckMakerUiNotice.PRODUCT_UNAVAILABLE -> R.string.deck_maker_notice_product_unavailable
}
