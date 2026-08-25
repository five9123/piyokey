package app.piyokey.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.piyokey.core.data.DeckFilters
import app.piyokey.core.data.DeckSort
import app.piyokey.core.data.DiscoveryEngine
import app.piyokey.core.data.InstalledDeck
import app.piyokey.core.data.ImportedDeckConflict
import app.piyokey.core.data.ImportedDeckPreview
import app.piyokey.core.deckkit.Catalog
import app.piyokey.core.deckkit.CatalogDeck
import app.piyokey.core.deckkit.DeckType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DiscoverScreen(
  catalog: Catalog,
  installedDeckIds: Set<String>,
  filters: DeckFilters,
  onFiltersChange: (DeckFilters) -> Unit,
  onDeckClick: (CatalogDeck) -> Unit,
  modifier: Modifier = Modifier,
) {
  val languageCode = LocalConfiguration.current.locales[0].language
  val results = remember(catalog, filters, languageCode) {
    DiscoveryEngine.filterAndSort(catalog, filters, languageCode)
  }
  val shortcuts = listOf("入門", "子音", "母音", "パッチム", "日常", "韓国旅行", "TOPIK", "今どき")
  val isFiltered = filters.query.isNotBlank() || filters.type != null || filters.level != null ||
    filters.canonicalTags.isNotEmpty() || filters.minimumItems != null || filters.maximumItems != null

  LazyColumn(
    modifier = modifier.fillMaxSize().background(PiyokeyColors.Background),
    contentPadding = PaddingValues(bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(
          text = stringResource(R.string.discover_title),
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = filters.query,
          onValueChange = { onFiltersChange(filters.copy(query = it)) },
          modifier = Modifier.fillMaxWidth(),
          placeholder = { Text(stringResource(R.string.discover_search_hint)) },
          singleLine = true,
          shape = RoundedCornerShape(18.dp),
        )
      }
    }

    item {
      FilterRows(filters, onFiltersChange)
    }

    item {
      Column {
        SectionTitle(stringResource(R.string.discover_shortcuts))
        LazyRow(
          contentPadding = PaddingValues(horizontal = 18.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(shortcuts) { canonical ->
            val tag = catalog.tags.firstOrNull { it.tag == canonical }
            val label = tag?.localizedTag(languageCode) ?: canonical
            FilterChip(
              selected = canonical in filters.canonicalTags,
              onClick = {
                val tags = filters.canonicalTags.toMutableSet().apply {
                  if (!add(canonical)) remove(canonical)
                }
                onFiltersChange(filters.copy(canonicalTags = tags))
              },
              label = { Text("#$label") },
            )
          }
        }
      }
    }

    if (isFiltered) {
      if (results.isEmpty()) {
        item {
          Text(
            text = stringResource(R.string.discover_no_results),
            modifier = Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        items(results, key = CatalogDeck::deckId) { deck ->
          DeckCard(
            deck = deck,
            installed = deck.deckId in installedDeckIds,
            languageCode = languageCode,
            onClick = { onDeckClick(deck) },
            modifier = Modifier.padding(horizontal = 18.dp),
          )
        }
      }
    } else {
      item {
        DeckSection(
          title = stringResource(R.string.discover_start_here),
          decks = catalog.decks.filter { "入門" in it.tags },
          installedDeckIds = installedDeckIds,
          languageCode = languageCode,
          onDeckClick = onDeckClick,
        )
      }
      item {
        DeckSection(
          title = stringResource(R.string.discover_trending_korean),
          decks = catalog.decks.filter { "今どき" in it.tags },
          installedDeckIds = installedDeckIds,
          languageCode = languageCode,
          onDeckClick = onDeckClick,
        )
      }
      item {
        DeckSection(
          title = stringResource(R.string.discover_by_goal),
          decks = catalog.decks.filter { deck ->
            deck.tags.any { it in setOf("韓国旅行", "日常", "TOPIK", "K-POP", "Kドラマ") }
          }.distinctBy(CatalogDeck::deckId).take(10),
          installedDeckIds = installedDeckIds,
          languageCode = languageCode,
          onDeckClick = onDeckClick,
        )
      }
      item {
        SectionTitle(stringResource(R.string.discover_official))
      }
      items(catalog.decks.filter(CatalogDeck::official), key = { "official-${it.deckId}" }) { deck ->
        DeckCard(
          deck = deck,
          installed = deck.deckId in installedDeckIds,
          languageCode = languageCode,
          onClick = { onDeckClick(deck) },
          modifier = Modifier.padding(horizontal = 18.dp),
        )
      }
    }
  }
}

@Composable
private fun FilterRows(filters: DeckFilters, onChange: (DeckFilters) -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
    verticalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
      ChoiceChip(stringResource(R.string.filter_all), filters.type == null) { onChange(filters.copy(type = null)) }
      ChoiceChip(stringResource(R.string.filter_words), filters.type == DeckType.WORD) {
        onChange(filters.copy(type = DeckType.WORD))
      }
      ChoiceChip(stringResource(R.string.filter_sentences), filters.type == DeckType.SENTENCE) {
        onChange(filters.copy(type = DeckType.SENTENCE))
      }
      ChoiceChip(stringResource(R.string.filter_beginner), filters.level == 1) {
        onChange(filters.copy(level = if (filters.level == 1) null else 1))
      }
      ChoiceChip(stringResource(R.string.filter_intermediate), filters.level == 2) {
        onChange(filters.copy(level = if (filters.level == 2) null else 2))
      }
      ChoiceChip(stringResource(R.string.filter_advanced), filters.level == 3) {
        onChange(filters.copy(level = if (filters.level == 3) null else 3))
      }
      ChoiceChip(stringResource(R.string.filter_up_to_10), filters.maximumItems == 10) {
        onChange(filters.copy(maximumItems = if (filters.maximumItems == 10) null else 10, minimumItems = null))
      }
      ChoiceChip(stringResource(R.string.filter_11_plus), filters.minimumItems == 11) {
        onChange(filters.copy(minimumItems = if (filters.minimumItems == 11) null else 11, maximumItems = null))
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
      DeckSort.entries.forEach { sort ->
        val label = when (sort) {
          DeckSort.POPULAR -> R.string.sort_popular
          DeckSort.NEWEST -> R.string.sort_newest
          DeckSort.TRENDING -> R.string.sort_trending
          DeckSort.ITEM_COUNT -> R.string.sort_item_count
        }
        ChoiceChip(stringResource(label), filters.sort == sort) { onChange(filters.copy(sort = sort)) }
      }
    }
  }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
  FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun DeckSection(
  title: String,
  decks: List<CatalogDeck>,
  installedDeckIds: Set<String>,
  languageCode: String,
  onDeckClick: (CatalogDeck) -> Unit,
) {
  Column {
    SectionTitle(title)
    LazyRow(
      contentPadding = PaddingValues(horizontal = 18.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      items(decks, key = CatalogDeck::deckId) { deck ->
        DeckCard(
          deck = deck,
          installed = deck.deckId in installedDeckIds,
          languageCode = languageCode,
          onClick = { onDeckClick(deck) },
          modifier = Modifier.width(286.dp),
        )
      }
    }
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(
    text = text,
    modifier = Modifier.padding(horizontal = 18.dp, vertical = 5.dp),
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.ExtraBold,
  )
}

@Composable
fun DeckCard(
  deck: CatalogDeck,
  installed: Boolean,
  languageCode: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth().testTag("deck-card-${deck.deckId}").clickable(onClick = onClick),
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
  ) {
    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
      DeckCover(deck, languageCode)
      Spacer(Modifier.width(13.dp))
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = deck.localizedName(languageCode).orEmpty(),
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
          )
          if (installed) Text(" ✓", color = PiyokeyColors.Green, fontWeight = FontWeight.Black)
        }
        Text(
          text = deck.localizedAuthorNickname(languageCode).orEmpty(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
          deck.localizedTags(languageCode).orEmpty().take(2).forEach { tag ->
            Text(
              text = "#$tag",
              color = PiyokeyColors.AccentDark,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
            )
          }
        }
        Text(
          text = stringResource(R.string.deck_item_count, deck.itemCount) + " · " +
            if (deck.type == DeckType.WORD) {
              stringResource(R.string.deck_level_word, deck.level)
            } else {
              stringResource(R.string.deck_level_sentence, deck.level)
            },
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (deck.downloadsTotal > 0) {
          Text(
            text = stringResource(R.string.deck_downloads, compactCount(deck.downloadsTotal)),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun DeckCover(deck: CatalogDeck, languageCode: String) {
  val palette = listOf(Color(0xFFFFD6E2), Color(0xFFFFE4B8), Color(0xFFCFEEDF), Color(0xFFDCE6FF))
  val color = palette[(deck.deckId.hashCode() and Int.MAX_VALUE) % palette.size]
  Box(
    modifier = Modifier.size(64.dp).background(color, RoundedCornerShape(18.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      deck.localizedName(languageCode).orEmpty().take(1),
      fontSize = 25.sp,
      fontWeight = FontWeight.Black,
      color = PiyokeyColors.Ink,
    )
  }
}

@Composable
fun DeckDetailScreen(
  deck: CatalogDeck,
  catalog: Catalog,
  installedVersion: Int?,
  isWorking: Boolean,
  onBack: () -> Unit,
  onInstall: () -> Unit,
  onPlay: () -> Unit,
  onDeckClick: (CatalogDeck) -> Unit,
  modifier: Modifier = Modifier,
) {
  val languageCode = LocalConfiguration.current.locales[0].language
  val recommendations = remember(catalog, deck) {
    DiscoveryEngine.sameTagRecommendations(catalog, deck, emptySet())
  }
  val averageLength = deck.previewItems.map { it.ko.length }.average().takeUnless(Double::isNaN) ?: 0.0
  val needsUpdate = installedVersion != null && installedVersion < deck.version

  Column(modifier.fillMaxSize().background(PiyokeyColors.Background)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TextButton(onClick = onBack) { Text("‹ ${stringResource(R.string.action_back)}") }
    }
    LazyColumn(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          DeckCover(deck, languageCode)
          Spacer(Modifier.width(15.dp))
          Column(Modifier.weight(1f)) {
            Text(deck.localizedName(languageCode).orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(deck.localizedAuthorNickname(languageCode).orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (deck.official) Text(stringResource(R.string.deck_official), color = PiyokeyColors.AccentDark, fontWeight = FontWeight.Bold)
          }
        }
      }
      item {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
          deck.localizedTags(languageCode).orEmpty().forEach { AssistChip(onClick = {}, label = { Text("#$it") }) }
        }
      }
      item {
        Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
          Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
            Stat(stringResource(R.string.deck_item_count, deck.itemCount))
            Stat(stringResource(R.string.deck_average_length, averageLength))
            Stat(stringResource(R.string.deck_file_size, deck.sizeBytes / 1024.0))
          }
        }
      }
      item { SectionTitle(stringResource(R.string.deck_preview)) }
      items(deck.previewItems.take(10)) { preview ->
        Surface(color = Color.White, shape = RoundedCornerShape(15.dp)) {
          Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(preview.ko, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Spacer(Modifier.width(16.dp))
            Text(preview.localizedMeaning(languageCode).orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
      if (recommendations.isNotEmpty()) {
        item { SectionTitle(stringResource(R.string.deck_same_tags)) }
        items(recommendations) { recommendation ->
          DeckCard(
            deck = recommendation,
            installed = false,
            languageCode = languageCode,
            onClick = { onDeckClick(recommendation) },
          )
        }
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth().background(Color.White).padding(14.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (installedVersion != null) {
        Button(
          onClick = onPlay,
          modifier = Modifier.weight(1f).testTag("deck-detail-play"),
          enabled = !isWorking,
        ) {
          Text(stringResource(R.string.deck_play))
        }
        if (needsUpdate) {
          OutlinedButton(
            onClick = onInstall,
            modifier = Modifier.testTag("deck-detail-update"),
            enabled = !isWorking,
          ) { Text(stringResource(R.string.deck_update)) }
        }
      } else {
        Button(
          onClick = onInstall,
          modifier = Modifier.fillMaxWidth().testTag("deck-detail-download"),
          enabled = !isWorking,
        ) {
          Text(stringResource(if (isWorking) R.string.deck_downloading else R.string.deck_download))
        }
      }
    }
  }
}

@Composable
private fun Stat(text: String) {
  Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun MyDecksScreen(
  installed: List<InstalledDeck>,
  catalog: Catalog,
  onFindDecks: () -> Unit,
  onPlay: (InstalledDeck) -> Unit,
  onUpdate: (CatalogDeck) -> Unit,
  onDelete: (InstalledDeck) -> Unit,
  onImport: () -> Unit,
  onExport: (InstalledDeck) -> Unit,
  onExportThenDelete: (InstalledDeck) -> Unit,
  hasDeckMakerAccess: Boolean,
  hasActiveDraft: Boolean,
  onNewDeck: () -> Unit,
  onResumeDraft: () -> Unit,
  onEditDeck: (InstalledDeck) -> Unit,
  onCopyOfficial: (InstalledDeck) -> Unit,
  header: @Composable () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val languageCode = LocalConfiguration.current.locales[0].language
  var pendingDelete by remember { mutableStateOf<InstalledDeck?>(null) }
  val deckToDelete = pendingDelete
  if (deckToDelete != null) {
    val isUserDeck = deckToDelete.metadata.source in setOf("imported", "created")
    val deckName = deckToDelete.deck.localizedName(languageCode) ?: deckToDelete.deck.name
    AlertDialog(
      onDismissRequest = { pendingDelete = null },
      title = { Text(stringResource(R.string.deck_delete_title)) },
      text = {
        Text(
          if (isUserDeck) stringResource(R.string.user_deck_delete_message, deckName)
          else stringResource(R.string.deck_delete_message),
        )
      },
      confirmButton = {
        TextButton(onClick = {
          pendingDelete?.let(onDelete)
          pendingDelete = null
        }) { Text(stringResource(R.string.deck_delete), color = MaterialTheme.colorScheme.error) }
      },
      dismissButton = {
        Column(horizontalAlignment = Alignment.End) {
          if (isUserDeck) {
            TextButton(onClick = {
              onExportThenDelete(deckToDelete)
              pendingDelete = null
            }) { Text(stringResource(R.string.user_deck_export_then_delete)) }
          }
          TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
        }
      },
    )
  }

  LazyColumn(
    modifier = modifier.fillMaxSize().background(PiyokeyColors.Background),
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(13.dp),
  ) {
    item { Text(stringResource(R.string.my_decks_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black) }
    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onImport, modifier = Modifier.weight(1f).testTag("import-deck")) {
          Text(stringResource(R.string.user_deck_import))
        }
        OutlinedButton(onClick = onNewDeck, modifier = Modifier.weight(1f).testTag("new-deck")) {
          Text(stringResource(if (hasDeckMakerAccess) R.string.user_deck_new else R.string.user_deck_new_locked))
        }
      }
    }
    if (hasActiveDraft) {
      item {
        Button(onClick = onResumeDraft, modifier = Modifier.fillMaxWidth().testTag("resume-deck-draft")) {
          Text(stringResource(R.string.deck_editor_resume_draft))
        }
      }
    }
    item { header() }
    if (installed.isEmpty()) {
      item {
        Surface(color = Color.White, shape = RoundedCornerShape(24.dp)) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text("🐣", fontSize = 48.sp)
            Text(stringResource(R.string.my_decks_empty_title), fontWeight = FontWeight.Black)
            Text(stringResource(R.string.my_decks_empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onFindDecks) { Text(stringResource(R.string.my_decks_find)) }
          }
        }
      }
    } else {
      items(installed, key = { it.metadata.deckId }) { item ->
        val entry = catalog.decks.firstOrNull { it.deckId == item.metadata.deckId }
        Surface(color = Color.White, shape = RoundedCornerShape(22.dp), shadowElevation = 2.dp) {
          Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(item.deck.localizedName(languageCode).orEmpty(), fontWeight = FontWeight.Black, fontSize = 17.sp)
            if (item.metadata.source in setOf("imported", "created")) {
              Text(stringResource(R.string.user_deck_badge), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.deck_item_count, item.deck.items.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(onClick = { onPlay(item) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.deck_play)) }
              if (entry != null && entry.version > item.metadata.version) {
                OutlinedButton(onClick = { onUpdate(entry) }) { Text(stringResource(R.string.deck_update)) }
              }
              TextButton(onClick = { pendingDelete = item }) { Text(stringResource(R.string.deck_delete)) }
            }
            if (item.metadata.source in setOf("imported", "created")) {
              OutlinedButton(
                onClick = { onEditDeck(item) },
                modifier = Modifier.fillMaxWidth().testTag("edit-${item.metadata.deckId}"),
              ) { Text(stringResource(R.string.deck_editor_edit)) }
              OutlinedButton(
                onClick = { onExport(item) },
                modifier = Modifier.fillMaxWidth().testTag("export-${item.metadata.deckId}"),
              ) { Text(stringResource(R.string.user_deck_export)) }
            } else if (item.deck.official) {
              OutlinedButton(
                onClick = { onCopyOfficial(item) },
                modifier = Modifier.fillMaxWidth().testTag("copy-${item.metadata.deckId}"),
              ) { Text(stringResource(R.string.deck_editor_copy_official)) }
            }
          }
        }
      }
    }
  }
}

enum class UserDeckImportError {
  INVALID,
  TOO_LARGE,
  UPDATE_REQUIRED,
  READ_FAILED,
}

@Composable
fun UserDeckImportScreen(
  preview: ImportedDeckPreview?,
  error: UserDeckImportError?,
  isWorking: Boolean,
  onBack: () -> Unit,
  onInstall: () -> Unit,
  onKeepCurrent: () -> Unit,
  onReplace: () -> Unit,
  onExportCurrent: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val languageCode = LocalConfiguration.current.locales[0].language
  val locale = Locale.forLanguageTag(languageCode)
  val dateFormatter = remember(locale) {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
      .withLocale(locale)
      .withZone(ZoneId.systemDefault())
  }
  var confirmsReplacement by remember(preview) { mutableStateOf(false) }
  if (confirmsReplacement) {
    AlertDialog(
      onDismissRequest = { confirmsReplacement = false },
      title = { Text(stringResource(R.string.user_deck_replace_confirm_title)) },
      text = { Text(stringResource(R.string.user_deck_replace_confirm_body)) },
      confirmButton = {
        TextButton(onClick = { confirmsReplacement = false; onReplace() }) {
          Text(stringResource(R.string.user_deck_replace), color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmsReplacement = false }) { Text(stringResource(R.string.action_cancel)) }
      },
    )
  }
  LazyColumn(
    modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
      .testTag("user-deck-import-screen"),
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item { TextButton(onClick = onBack, enabled = !isWorking) { Text("‹ ${stringResource(R.string.action_back)}") } }
    item { Text(stringResource(R.string.user_deck_import_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black) }
    if (error != null) {
      item {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(20.dp)) {
          Text(
            stringResource(
              when (error) {
                UserDeckImportError.INVALID -> R.string.user_deck_error_invalid
                UserDeckImportError.TOO_LARGE -> R.string.user_deck_error_too_large
                UserDeckImportError.UPDATE_REQUIRED -> R.string.user_deck_error_update
                UserDeckImportError.READ_FAILED -> R.string.user_deck_error_read
              },
            ),
            modifier = Modifier.padding(18.dp).testTag("import-error"),
          )
        }
      }
    } else if (preview == null) {
      item { Text(stringResource(R.string.user_deck_validating), modifier = Modifier.testTag("import-loading")) }
    } else {
      item {
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp), shadowElevation = 2.dp) {
          Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(preview.deck.localizedName(languageCode) ?: preview.deck.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(preview.deck.localizedAuthorNickname(languageCode) ?: preview.deck.author.nickname)
            Text(stringResource(R.string.user_deck_file, preview.sourceDisplayName), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.user_deck_version_items, preview.deck.version, preview.deck.items.size))
            Text(
              stringResource(
                if (preview.deck.type == DeckType.WORD) R.string.deck_level_word else R.string.deck_level_sentence,
                preview.deck.level,
              ),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(preview.deck.localizedTags(languageCode).orEmpty().joinToString(" · "))
          }
        }
      }
      item { Text(stringResource(R.string.deck_preview), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
      items(preview.deck.items.take(3), key = { it.id }) { item ->
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
          Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(item.ko, fontWeight = FontWeight.Bold)
            Text(item.localizedMeaning(languageCode).orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
      when (preview.conflict) {
        ImportedDeckConflict.NEW -> item {
          Button(onClick = onInstall, enabled = !isWorking, modifier = Modifier.fillMaxWidth().testTag("confirm-import")) {
            Text(stringResource(if (isWorking) R.string.user_deck_importing else R.string.user_deck_install))
          }
        }
        ImportedDeckConflict.IDENTICAL -> item {
          Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(18.dp)) {
            Text(stringResource(R.string.user_deck_already_imported), Modifier.padding(16.dp).testTag("import-identical"))
          }
        }
        else -> {
          item {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(18.dp)) {
              Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  stringResource(
                    when (preview.conflict) {
                      ImportedDeckConflict.OLDER_VERSION -> R.string.user_deck_conflict_downgrade
                      ImportedDeckConflict.SAME_VERSION_DIFFERENT_CONTENT -> R.string.user_deck_conflict_same_version
                      else -> R.string.user_deck_conflict_update
                    },
                  ),
                  fontWeight = FontWeight.Bold,
                )
                val installed = preview.installed
                if (installed != null) {
                  Text(
                    stringResource(
                      R.string.user_deck_current_compare,
                      installed.deck.localizedName(languageCode) ?: installed.deck.name,
                      installed.metadata.version,
                      dateFormatter.format(installed.deck.updatedAt),
                      installed.deck.items.size,
                    ),
                  )
                }
                Text(
                  stringResource(
                    R.string.user_deck_incoming_compare,
                    preview.deck.localizedName(languageCode) ?: preview.deck.name,
                    preview.deck.version,
                    dateFormatter.format(preview.deck.updatedAt),
                    preview.deck.items.size,
                  ),
                )
              }
            }
          }
          item {
            OutlinedButton(onClick = onExportCurrent, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.user_deck_export_current)) }
          }
          item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(onClick = onKeepCurrent, enabled = !isWorking, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.user_deck_keep_current)) }
              OutlinedButton(
                onClick = { confirmsReplacement = true },
                enabled = !isWorking,
                modifier = Modifier.weight(1f).testTag("replace-import"),
              ) { Text(stringResource(R.string.user_deck_replace), color = MaterialTheme.colorScheme.error) }
            }
          }
        }
      }
    }
  }
}

@Composable
fun RecommendationHome(
  recommendations: List<CatalogDeck>,
  installedDeckIds: Set<String>,
  onDeckClick: (CatalogDeck) -> Unit,
  header: @Composable () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val languageCode = LocalConfiguration.current.locales[0].language
  LazyColumn(
    modifier = modifier.fillMaxSize().background(PiyokeyColors.Background),
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
    verticalArrangement = Arrangement.spacedBy(13.dp),
  ) {
    item { header() }
    item { Text(stringResource(R.string.brand_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black) }
    item {
      Text(stringResource(R.string.home_recommended), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
      Text(stringResource(R.string.home_recommended_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    items(recommendations, key = CatalogDeck::deckId) { deck ->
      DeckCard(deck, deck.deckId in installedDeckIds, languageCode, { onDeckClick(deck) })
    }
  }
}

@Composable
fun PracticeResultScreen(
  accuracyPercent: Double,
  misses: Int,
  completed: Int,
  charactersPerMinute: Double = 0.0,
  stars: Int? = null,
  recommendations: List<CatalogDeck>,
  installedDeckIds: Set<String>,
  onRetry: () -> Unit,
  onDeckClick: (CatalogDeck) -> Unit,
  onBack: () -> Unit,
  shareActions: @Composable () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val languageCode = LocalConfiguration.current.locales[0].language
  LazyColumn(
    modifier = modifier.fillMaxSize().background(PiyokeyColors.Background),
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item { TextButton(onClick = onBack) { Text("‹ ${stringResource(R.string.action_back)}") } }
    item {
      Text(stringResource(R.string.practice_result_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
    }
    if (stars != null) {
      item {
        Text(
          "★".repeat(stars) + "☆".repeat(3 - stars),
          modifier = Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center,
          fontSize = 36.sp,
          color = PiyokeyColors.AccentDark,
        )
      }
    }
    item {
      Surface(color = Color.White, shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.fillMaxWidth().padding(22.dp), horizontalArrangement = Arrangement.SpaceAround) {
          ResultStat(stringResource(R.string.practice_result_accuracy), "${accuracyPercent.roundToInt()}%")
          ResultStat(stringResource(R.string.practice_result_misses), misses.toString())
          ResultStat(stringResource(R.string.practice_result_completed), completed.toString())
        }
      }
    }
    item {
      Text(
        stringResource(R.string.practice_result_speed_value, charactersPerMinute.roundToInt()),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    item { Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.practice_again)) } }
    item { shareActions() }
    if (recommendations.isNotEmpty()) {
      item { SectionTitle(stringResource(R.string.deck_same_tags)) }
      items(recommendations, key = CatalogDeck::deckId) { deck ->
        DeckCard(deck, deck.deckId in installedDeckIds, languageCode, { onDeckClick(deck) })
      }
    }
  }
}

@Composable
private fun ResultStat(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, fontSize = 26.sp, fontWeight = FontWeight.Black, color = PiyokeyColors.AccentDark)
    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

private fun compactCount(value: Int): String = when {
  value >= 10_000 -> "%.1f万".format(Locale.ROOT, value / 10_000.0)
  value >= 1_000 -> "%.1fk".format(Locale.ROOT, value / 1_000.0)
  else -> value.toString()
}

private object PiyokeyColors {
  val Background = Color(0xFFFFF9F1)
  val AccentDark = Color(0xFFC94368)
  val Green = Color(0xFF2B9A68)
  val Ink = Color(0xFF3F3540)
}
