package app.piyokey.android.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.piyokey.android.R
import app.piyokey.android.data.AppData
import app.piyokey.android.data.catalog.CatalogLibrary
import app.piyokey.android.feature.onboarding.AppTourTarget
import app.piyokey.android.feature.onboarding.appTourTarget
import app.piyokey.android.platform.files.ContentFeedbackContext
import app.piyokey.android.platform.files.ContentFeedbackSource
import app.piyokey.android.platform.files.DocumentIO
import app.piyokey.android.ui.nav.LocalTabNavigator
import app.piyokey.android.ui.nav.Route
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoBackground
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.ui.theme.centeredContent
import app.piyokey.core.deckkit.Catalog
import app.piyokey.core.deckkit.CatalogDeck
import app.piyokey.core.deckkit.DeckType
import app.piyokey.core.domain.library.CatalogSortOrder
import app.piyokey.core.domain.library.CatalogText
import app.piyokey.core.domain.library.DiscoverCatalogRules
import app.piyokey.core.domain.library.DiscoverFilters

/** Discover tab (iOS `DiscoverView`): search → shortcut chips → basics → trending → purpose → official. */
@Composable
fun DiscoverTabRoot() {
  LaunchedEffect(Unit) {
    DiscoverLauncher.installDeckDownloadAnalytics()
    AppData.catalog.loadIfNeeded()
  }
  val catalog by AppData.catalog.catalog.collectAsStateWithLifecycle()
  val loadState by AppData.catalog.loadState.collectAsStateWithLifecycle()
  val metrics = Piyo.metrics
  Column(Modifier.fillMaxSize().testTag("discover.screen")) {
    Box(
      Modifier
        .fillMaxWidth()
        .background(Piyo.colors.card)
        .appTourTarget(AppTourTarget.DISCOVER_SEARCH)
        .padding(horizontal = metrics.horizontalPadding, vertical = 10.dp),
    ) {
      SearchBar(Modifier.centeredContent(metrics.readableContentMaxWidth))
    }
    when (loadState) {
      CatalogLibrary.LoadState.IDLE -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Piyo.colors.accent)
      }
      CatalogLibrary.LoadState.FAILED -> ErrorState()
      CatalogLibrary.LoadState.LOADED -> CatalogContent(catalog)
    }
  }
}

@Composable
private fun SearchBar(modifier: Modifier = Modifier) {
  val colors = Piyo.colors
  val filters = DiscoverModel.filters
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
    Row(
      Modifier
        .weight(1f)
        .height(44.dp)
        .clip(RoundedCornerShape(15.dp))
        .background(colors.backgroundBottom.copy(alpha = 0.7f))
        .padding(start = 13.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.mutedInk)
      val placeholder = stringResource(R.string.discover_search_placeholder)
      BasicTextField(
        value = filters.query,
        onValueChange = { DiscoverModel.filters = DiscoverModel.filters.copy(query = it) },
        singleLine = true,
        textStyle = PiyoType.body(),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
        modifier = Modifier.weight(1f).testTag("discover.search").semantics { contentDescription = placeholder },
        decorationBox = { inner ->
          Box(contentAlignment = Alignment.CenterStart) {
            if (filters.query.isEmpty()) Text(placeholder, style = PiyoType.body().copy(color = colors.mutedInk.copy(alpha = 0.8f)), maxLines = 1)
            inner()
          }
        },
      )
      if (filters.query.isNotEmpty()) {
        val clear = stringResource(R.string.discover_search_clear)
        Box(
          Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button) { DiscoverModel.filters = DiscoverModel.filters.copy(query = "") }
            .semantics { contentDescription = clear },
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Rounded.Cancel, contentDescription = null, tint = colors.mutedInk, modifier = Modifier.size(20.dp))
        }
      }
    }
    FilterMenu()
  }
}

@Composable
private fun FilterMenu() {
  val colors = Piyo.colors
  var expanded by remember { mutableStateOf(false) }
  val filters = DiscoverModel.filters
  val label = stringResource(R.string.discover_filter_title)
  Box {
    Box(
      Modifier
        .size(44.dp)
        .clip(RoundedCornerShape(15.dp))
        .background(colors.backgroundBottom.copy(alpha = 0.7f))
        .clickable(role = Role.Button) { expanded = true }
        .semantics { contentDescription = label }
        .testTag("discover.filter"),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        Modifier
          .size(26.dp)
          .clip(CircleShape)
          .background(if (filters.hasActiveFilters) colors.accent else Color.Transparent),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.Rounded.FilterList,
          contentDescription = null,
          tint = if (filters.hasActiveFilters) Color.White else colors.ink,
          modifier = Modifier.size(20.dp),
        )
      }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = colors.card) {
      MenuSection(stringResource(R.string.discover_filter_type))
      MenuOption(stringResource(R.string.discover_filter_all), filters.selectedType == null, "discover.filter.type.all") {
        DiscoverModel.filters = DiscoverModel.filters.copy(selectedType = null)
      }
      MenuOption(stringResource(R.string.deck_type_word), filters.selectedType == DeckType.WORD, "discover.filter.type.word") {
        DiscoverModel.filters = DiscoverModel.filters.copy(selectedType = DeckType.WORD)
      }
      MenuOption(stringResource(R.string.deck_type_sentence), filters.selectedType == DeckType.SENTENCE, "discover.filter.type.sentence") {
        DiscoverModel.filters = DiscoverModel.filters.copy(selectedType = DeckType.SENTENCE)
      }
      HorizontalDivider(color = colors.mutedInk.copy(alpha = 0.2f))
      MenuSection(stringResource(R.string.discover_filter_level))
      MenuOption(stringResource(R.string.discover_filter_all), filters.selectedLevel == null, "discover.filter.level.all") {
        DiscoverModel.filters = DiscoverModel.filters.copy(selectedLevel = null)
      }
      (1..3).forEach { level ->
        MenuOption(stringResource(R.string.deck_level_format, level), filters.selectedLevel == level, "discover.filter.level.$level") {
          DiscoverModel.filters = DiscoverModel.filters.copy(selectedLevel = level)
        }
      }
      HorizontalDivider(color = colors.mutedInk.copy(alpha = 0.2f))
      MenuSection(stringResource(R.string.discover_sort_title))
      listOf(
        CatalogSortOrder.POPULAR to R.string.discover_sort_popular,
        CatalogSortOrder.NEWEST to R.string.discover_sort_newest,
        CatalogSortOrder.TRENDING to R.string.discover_sort_trending,
      ).forEach { (order, res) ->
        MenuOption(stringResource(res), filters.sortOrder == order, "discover.sort.${order.raw}") {
          DiscoverModel.filters = DiscoverModel.filters.copy(sortOrder = order)
        }
      }
      if (filters.hasActiveFilters) {
        HorizontalDivider(color = colors.mutedInk.copy(alpha = 0.2f))
        DropdownMenuItem(
          text = { Text(stringResource(R.string.discover_filter_reset), style = PiyoType.body().copy(color = colors.error)) },
          onClick = {
            DiscoverModel.filters = DiscoverModel.filters.reset()
            expanded = false
          },
          modifier = Modifier.testTag("discover.filter.reset"),
        )
      }
    }
  }
}

@Composable
private fun MenuSection(title: String) {
  Text(
    title,
    style = PiyoType.caption().copy(color = Piyo.colors.mutedInk, fontWeight = FontWeight.SemiBold),
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).semantics { heading() },
  )
}

@Composable
private fun MenuOption(title: String, selected: Boolean, tag: String, onClick: () -> Unit) {
  DropdownMenuItem(
    text = { Text(title, style = PiyoType.body()) },
    leadingIcon = {
      Box(Modifier.size(20.dp)) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Piyo.colors.accent)
      }
    },
    onClick = onClick,
    modifier = Modifier.testTag(tag).semantics { this.selected = selected },
  )
}

@Composable
private fun ErrorState() {
  val colors = Piyo.colors
  Column(
    Modifier.fillMaxSize().padding(28.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(Icons.Rounded.WifiOff, contentDescription = null, tint = colors.mutedInk, modifier = Modifier.size(38.dp))
    Text(stringResource(R.string.discover_error_title), style = PiyoType.headline())
    Text(
      stringResource(R.string.discover_error_message),
      style = PiyoType.subheadline().copy(color = colors.mutedInk),
      textAlign = TextAlign.Center,
    )
    Button(
      onClick = { AppData.catalog.retry() },
      colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = Color.White),
      modifier = Modifier.heightIn(min = 44.dp).testTag("discover.error.retry"),
    ) { Text(stringResource(R.string.discover_error_retry)) }
  }
}

@Composable
private fun CatalogContent(catalog: Catalog?) {
  val text = rememberCatalogText()
  val filters = DiscoverModel.filters
  val library = rememberDeckLibraryState()
  val navigator = LocalTabNavigator.current
  val metrics = Piyo.metrics
  val cardWidth = if (metrics.isExpanded) 390.dp else 290.dp
  val openDeck: (CatalogDeck) -> Unit = { navigator.push(DeckDetailRoute(it.deckId)) }
  val sections = remember(catalog, text) {
    listOf(
      DiscoverSection(R.string.discover_section_start_here, Icons.Rounded.Keyboard, DiscoverCatalogRules.basicDecks(catalog, text)),
      DiscoverSection(R.string.discover_section_now_korean, Icons.Rounded.Forum, DiscoverCatalogRules.trendingKoreanDecks(catalog, text)),
      DiscoverSection(R.string.discover_section_by_goal, Icons.Rounded.TrackChanges, DiscoverCatalogRules.purposeDecks(catalog, text)),
      DiscoverSection(R.string.discover_section_official_all, Icons.Rounded.Verified, DiscoverCatalogRules.officialDecks(catalog, text)),
    )
  }
  val filtered = remember(catalog, text, filters) { DiscoverCatalogRules.filteredDecks(catalog, filters, text) }
  val shortcutTags = remember(catalog, text) { DiscoverCatalogRules.shortcutTags(catalog, text) }

  LazyColumn(
    Modifier.fillMaxSize().centeredContent(metrics.hubContentMaxWidth).testTag("discover.catalog"),
    contentPadding = PaddingValues(vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(22.dp),
  ) {
    item("shortcuts") { ShortcutSection(catalog, shortcutTags, text) }
    if (filters.hasActiveFilters) {
      item("results.header") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(stringResource(R.string.discover_results_title), style = PiyoType.headline().copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
          Text(
            pluralStringResource(R.plurals.discover_results_count, filtered.size, filtered.size),
            style = PiyoType.caption().copy(color = Piyo.colors.mutedInk, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.testTag("discover.results.count"),
          )
        }
      }
      if (filtered.isEmpty()) {
        item("results.empty") {
          Column(
            Modifier.fillMaxWidth().padding(vertical = 40.dp).testTag("discover.results.empty"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = Piyo.colors.mutedInk, modifier = Modifier.size(30.dp))
            Text(stringResource(R.string.discover_results_empty), style = PiyoType.subheadline().copy(color = Piyo.colors.mutedInk))
          }
        }
      } else {
        items(filtered, key = { "result." + it.deckId }) { deck ->
          DeckCard(
            deck = deck,
            text = text,
            onClick = { openDeck(deck) },
            isInstalled = library.isInstalled(deck.deckId),
            updateAvailable = library.needsUpdate(deck),
            fixedHeight = DeckCardMetrics.regularMinimumHeight * Piyo.fontScale,
            limitsTitleToOneLine = true,
            modifier = Modifier.padding(horizontal = 18.dp),
          )
        }
      }
    } else {
      sections.forEachIndexed { index, section ->
        item("section.$index") {
          CatalogSection(section, text, library, cardWidth, openDeck)
        }
      }
    }
    item("suggestion") { DeckSuggestionCard() }
  }
}

private class DiscoverSection(val titleRes: Int, val icon: ImageVector, val decks: List<CatalogDeck>)

@Composable
private fun ShortcutSection(catalog: Catalog?, tags: List<String>, text: CatalogText) {
  val colors = Piyo.colors
  val selected = DiscoverModel.filters.selectedTags
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    SectionLabel(stringResource(R.string.discover_shortcuts_title), Icons.Rounded.Tag, Modifier.padding(horizontal = 18.dp))
    LazyRow(
      modifier = Modifier.testTag("discover.shortcuts"),
      contentPadding = PaddingValues(horizontal = 18.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(tags, key = { it }) { tag ->
        val isSelected = tag in selected
        val localized = catalog?.let { text.tag(it, tag) } ?: ""
        Box(
          Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(if (isSelected) colors.accent else colors.card)
            .clickable(role = Role.Button) { DiscoverModel.filters = DiscoverModel.filters.togglingTag(tag) }
            .semantics { this.selected = isSelected }
            .testTag("discover.tag.$tag")
            .padding(horizontal = 13.dp, vertical = 9.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            "#$localized",
            style = PiyoType.subheadline().copy(color = if (isSelected) Color.White else colors.ink, fontWeight = FontWeight.SemiBold),
          )
        }
      }
    }
  }
}

@Composable
internal fun SectionLabel(title: String, icon: ImageVector, modifier: Modifier = Modifier, color: Color = Piyo.colors.ink) {
  Row(
    modifier.semantics(mergeDescendants = true) { heading() },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
    Text(title, style = PiyoType.headline().copy(fontWeight = FontWeight.Bold, color = color))
  }
}

@Composable
private fun CatalogSection(
  section: DiscoverSection,
  text: CatalogText,
  library: DeckLibraryState,
  cardWidth: Dp,
  openDeck: (CatalogDeck) -> Unit,
) {
  val navigator = LocalTabNavigator.current
  val title = stringResource(section.titleRes)
  Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
      SectionLabel(title, section.icon, Modifier.weight(1f))
      Text(
        stringResource(R.string.discover_section_see_all),
        style = PiyoType.caption().copy(color = Piyo.colors.accent, fontWeight = FontWeight.SemiBold),
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .clickable(role = Role.Button) { navigator.push(DeckListRoute(section.titleRes, section.decks.map { it.deckId })) }
          .heightIn(min = 44.dp)
          .padding(horizontal = 6.dp, vertical = 13.dp),
      )
    }
    LazyRow(
      contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      itemsIndexed(section.decks, key = { _, deck -> deck.deckId }) { _, deck ->
        DeckCard(
          deck = deck,
          text = text,
          onClick = { openDeck(deck) },
          isInstalled = library.isInstalled(deck.deckId),
          updateAvailable = library.needsUpdate(deck),
          fixedHeight = DeckCardMetrics.regularMinimumHeight * Piyo.fontScale,
          limitsTitleToOneLine = true,
          modifier = Modifier.width(cardWidth),
        )
      }
    }
  }
}

@Composable
private fun DeckSuggestionCard() {
  val colors = Piyo.colors
  val context = LocalContext.current
  Row(
    Modifier
      .padding(horizontal = 18.dp)
      .fillMaxWidth()
      .clip(RoundedCornerShape(22.dp))
      .background(colors.card)
      .clickable(role = Role.Button) {
        DocumentIO.openContentFeedback(context, ContentFeedbackContext.suggestion(ContentFeedbackSource.DISCOVER))
      }
      .semantics(mergeDescendants = true) {}
      .testTag("discover.deck_suggestion")
      .padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(colors.accentSoft.copy(alpha = 0.55f)),
      contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp)) }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(stringResource(R.string.content_feedback_suggestion_title), style = PiyoType.subheadline().copy(fontWeight = FontWeight.Bold))
      Text(stringResource(R.string.content_feedback_suggestion_detail), style = PiyoType.caption().copy(color = colors.mutedInk))
    }
    Icon(Icons.Rounded.NorthEast, contentDescription = null, tint = colors.mutedInk, modifier = Modifier.size(14.dp))
  }
}

/** iOS `DeckListView` ("See all"). */
class DeckListRoute(private val titleRes: Int, private val deckIds: List<String>) : Route {
  @Composable
  override fun Content() {
    val navigator = LocalTabNavigator.current
    val catalog by AppData.catalog.catalog.collectAsStateWithLifecycle()
    val text = rememberCatalogText()
    val library = rememberDeckLibraryState()
    val decks = remember(catalog, deckIds) { deckIds.mapNotNull { id -> catalog?.decks?.firstOrNull { it.deckId == id } } }
    PiyoBackground {
      Column(Modifier.fillMaxSize()) {
        InlineTopBar(stringResource(titleRes), onBack = { navigator.pop() })
        LazyColumn(
          Modifier.fillMaxSize().centeredContent(Piyo.metrics.hubContentMaxWidth),
          contentPadding = PaddingValues(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(decks, key = { it.deckId }) { deck ->
            DeckCard(
              deck = deck,
              text = text,
              onClick = { navigator.push(DeckDetailRoute(deck.deckId)) },
              isInstalled = library.isInstalled(deck.deckId),
              updateAvailable = library.needsUpdate(deck),
            )
          }
        }
      }
    }
  }
}
