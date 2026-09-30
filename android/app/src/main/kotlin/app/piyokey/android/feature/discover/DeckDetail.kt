package app.piyokey.android.feature.discover

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.piyokey.android.R
import app.piyokey.android.data.AppData
import app.piyokey.android.feature.practice.PracticeLauncher
import app.piyokey.android.platform.files.ContentFeedbackContext
import app.piyokey.android.platform.files.DocumentIO
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.nav.LocalTabNavigator
import app.piyokey.android.ui.nav.Route
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoBackground
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.ui.theme.centeredContent
import app.piyokey.core.deckkit.CatalogDeck
import app.piyokey.core.domain.library.CatalogText
import app.piyokey.core.domain.library.DiscoverCatalogRules
import java.text.NumberFormat
import kotlinx.coroutines.launch

/** iOS `DeckDetailView` for a catalog entry. */
class DeckDetailRoute(val deckId: String) : Route {
  @Composable
  override fun Content() {
    val navigator = LocalTabNavigator.current
    val catalog by AppData.catalog.catalog.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
      DiscoverLauncher.installDeckDownloadAnalytics()
      AppData.catalog.loadIfNeeded()
    }
    val deck = catalog?.decks?.firstOrNull { it.deckId == deckId }
    PiyoBackground {
      Column(Modifier.fillMaxSize()) {
        InlineTopBar(stringResource(R.string.deck_detail_navigation_title), onBack = { navigator.pop() })
        if (deck == null) {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Piyo.colors.accent) }
        } else {
          DeckDetailScreen(deck, catalog?.decks.orEmpty(), Modifier.weight(1f))
        }
      }
    }
  }
}

@Composable
private fun DeckDetailScreen(deck: CatalogDeck, catalogDecks: List<CatalogDeck>, modifier: Modifier) {
  val text = rememberCatalogText()
  val library = rememberDeckLibraryState()
  val tabNavigator = LocalTabNavigator.current
  val readable = Piyo.metrics.readableContentMaxWidth
  val related = remember(deck, catalogDecks, text) { DiscoverCatalogRules.relatedDecks(deck, catalogDecks, text) }
  Column(modifier.fillMaxWidth().testTag("deck.detail.screen")) {
    Column(
      Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(18.dp)
        .centeredContent(readable),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      Header(deck, text)
      Statistics(deck)
      Preview(deck)
      ContentReportLink(deck)
      if (related.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
          SectionLabel(stringResource(R.string.deck_detail_related), Icons.Rounded.Layers)
          LazyRow(contentPadding = PaddingValues(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(related, key = { it.deckId }) { other ->
              DeckCard(
                deck = other,
                text = text,
                onClick = { tabNavigator.push(DeckDetailRoute(other.deckId)) },
                isInstalled = library.isInstalled(other.deckId),
                updateAvailable = library.needsUpdate(other),
                modifier = Modifier.width(290.dp),
              )
            }
          }
        }
      }
    }
    ActionBar(deck, library)
  }
}

@Composable
private fun Header(deck: CatalogDeck, text: CatalogText) {
  val colors = Piyo.colors
  val context = LocalContext.current
  val shape = RoundedCornerShape(25.dp)
  Column(
    Modifier
      .fillMaxWidth()
      .shadow(12.dp, shape, ambientColor = colors.keyShadow, spotColor = colors.keyShadow)
      .clip(shape)
      .background(colors.card)
      .padding(18.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
      DeckCover(deck, 116.dp, 148.dp, text)
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (deck.official) {
          IconLabel(
            stringResource(R.string.deck_badge_official), Icons.Rounded.Verified, colors.secondary,
            style = PiyoType.caption().copy(fontWeight = FontWeight.Bold),
          )
        }
        Text(text.name(deck), style = PiyoType.title2(), modifier = Modifier.testTag("deck.detail.title"))
        Text(text.author(deck), style = PiyoType.subheadline().copy(color = colors.mutedInk))
        Text(
          Formatter.formatShortFileSize(context, deck.sizeBytes.toLong()),
          style = PiyoType.caption().copy(color = colors.mutedInk, fontWeight = FontWeight.SemiBold),
        )
      }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
      text.tags(deck).forEach { tag ->
        Text(
          "#$tag",
          style = PiyoType.caption().copy(color = colors.accent, fontWeight = FontWeight.SemiBold),
          modifier = Modifier.clip(CircleShape).background(colors.accentSoft.copy(alpha = 0.5f)).padding(horizontal = 10.dp, vertical = 6.dp),
        )
      }
    }
  }
}

@Composable
private fun Statistics(deck: CatalogDeck) {
  val colors = Piyo.colors
  val locale = rememberAppLocale()
  val average = DiscoverCatalogRules.averagePreviewLength(deck)?.let { String.format(locale, "%.1f", it) } ?: "—"
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(colors.card)
      .padding(vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Statistic(NumberFormat.getIntegerInstance(locale).format(deck.downloadsTotal), stringResource(R.string.deck_detail_downloads), Modifier.weight(1f))
    VerticalDivider(Modifier.height(42.dp), color = colors.mutedInk.copy(alpha = 0.25f))
    Statistic(deck.itemCount.toString(), stringResource(R.string.deck_detail_items), Modifier.weight(1f))
    VerticalDivider(Modifier.height(42.dp), color = colors.mutedInk.copy(alpha = 0.25f))
    Statistic(average, stringResource(R.string.deck_detail_average_length), Modifier.weight(1f))
  }
}

@Composable
private fun Statistic(value: String, label: String, modifier: Modifier) {
  Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(value, style = PiyoType.subheadline().copy(fontWeight = FontWeight.Bold))
    Text(label, style = PiyoType.caption2().copy(color = Piyo.colors.mutedInk))
  }
}

@Composable
private fun Preview(deck: CatalogDeck) {
  val colors = Piyo.colors
  val language = rememberCatalogText().languageCode
  Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.card).padding(18.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      SectionLabel(stringResource(R.string.deck_detail_preview), Icons.Rounded.Visibility, Modifier.weight(1f))
      Text(
        pluralStringResource(R.plurals.deck_detail_preview_count, deck.previewItems.size, deck.previewItems.size),
        style = PiyoType.caption().copy(color = colors.mutedInk, fontWeight = FontWeight.SemiBold),
      )
    }
    deck.previewItems.forEachIndexed { index, item ->
      Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).semantics(mergeDescendants = true) {}.testTag("deck.preview.$index"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          (index + 1).toString(),
          style = PiyoType.caption().copy(color = colors.secondary, fontWeight = FontWeight.Bold),
          modifier = Modifier.width(22.dp).padding(top = 3.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(item.ko, style = PiyoType.body().copy(fontWeight = FontWeight.Bold))
          item.localizedMeaning(language)?.let { Text(it, style = PiyoType.caption().copy(color = colors.mutedInk)) }
        }
      }
      if (index < deck.previewItems.size - 1) HorizontalDivider(color = colors.mutedInk.copy(alpha = 0.2f))
    }
  }
}

@Composable
private fun ContentReportLink(deck: CatalogDeck) {
  val colors = Piyo.colors
  val context = LocalContext.current
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(colors.card)
      .clickable(role = Role.Button) { DocumentIO.openContentFeedback(context, ContentFeedbackContext.report(deck.deckId, deck.version)) }
      .semantics(mergeDescendants = true) {}
      .testTag("deck.detail.content_report")
      .padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(colors.accentSoft.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
      Icon(Icons.Rounded.Feedback, contentDescription = null, tint = colors.accent, modifier = Modifier.size(19.dp))
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(stringResource(R.string.content_feedback_report_title), style = PiyoType.subheadline().copy(fontWeight = FontWeight.SemiBold))
      Text(stringResource(R.string.content_feedback_report_detail), style = PiyoType.caption().copy(color = colors.mutedInk))
    }
    Icon(Icons.Rounded.NorthEast, contentDescription = null, tint = colors.mutedInk, modifier = Modifier.size(14.dp))
  }
}

@Composable
private fun ActionBar(deck: CatalogDeck, library: DeckLibraryState) {
  val colors = Piyo.colors
  val scope = rememberCoroutineScope()
  val appNavigator = LocalAppNavigator.current
  val installed = library.installed[deck.deckId]
  val needsUpdate = library.needsUpdate(deck)
  val installing = deck.deckId in library.installing
  Column(
    Modifier
      .fillMaxWidth()
      .background(colors.card.copy(alpha = 0.94f))
      .navigationBarsPadding()
      .padding(horizontal = 18.dp, vertical = 12.dp)
      .centeredContent(Piyo.metrics.readableContentMaxWidth),
    verticalArrangement = Arrangement.spacedBy(7.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (deck.deckId in library.failed) {
      Text(stringResource(R.string.deck_detail_install_error), style = PiyoType.caption().copy(color = colors.error))
    }
    if (installed != null && !needsUpdate) {
      ActionButton(stringResource(R.string.deck_detail_play), Icons.Rounded.PlayArrow, "deck.detail.play", enabled = true) {
        PracticeLauncher.deck(appNavigator, installed.deckId)
      }
    } else {
      val tag = if (needsUpdate) "deck.detail.update" else "deck.detail.download"
      if (installing) {
        Row(
          Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(17.dp)).background(colors.accent.copy(alpha = 0.6f)).testTag(tag),
          horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
          Text(stringResource(R.string.deck_detail_installing), style = PiyoType.headline().copy(color = Color.White, fontWeight = FontWeight.Bold))
        }
      } else {
        ActionButton(
          stringResource(if (needsUpdate) R.string.deck_detail_update else R.string.deck_detail_download),
          if (needsUpdate) Icons.Rounded.Sync else Icons.Rounded.DownloadForOffline,
          tag,
          enabled = true,
        ) {
          scope.launch {
            AppData.deckLibrary.install(deck)
            AppData.deckLibrary.installedDeck(deck.deckId)?.let { AppData.review.reconcile(it) }
          }
        }
      }
    }
  }
}

@Composable
private fun ActionButton(title: String, icon: ImageVector, tag: String, enabled: Boolean, onClick: () -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 52.dp)
      .clip(RoundedCornerShape(17.dp))
      .background(Piyo.colors.accent)
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .semantics(mergeDescendants = true) {}
      .testTag(tag)
      .padding(vertical = 14.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, style = PiyoType.headline().copy(color = Color.White, fontWeight = FontWeight.Bold))
    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
  }
}
