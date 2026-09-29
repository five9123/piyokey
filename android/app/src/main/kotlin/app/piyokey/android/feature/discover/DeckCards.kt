package app.piyokey.android.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.rounded.Abc
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.piyokey.android.R
import app.piyokey.android.data.AppData
import app.piyokey.android.data.settings.AppSettings
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.core.deckkit.CatalogDeck
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckType
import app.piyokey.core.domain.library.CatalogText
import java.text.NumberFormat
import java.util.Locale

/** iOS system colors used by Discover/My Page badges and covers (light values). */
object SystemColors {
  val orange = Color(0xFFFF9500)
  val gray = Color(0xFF8E8E93)
  val brown = Color(0xFFA2845E)
  val cyan = Color(0xFF32ADE6)
  val purple = Color(0xFFAF52DE)
  val teal = Color(0xFF30B0C7)
  val indigo = Color(0xFF5856D6)
  val pink = Color(0xFFFF2D55)
}

/** Catalog text in the in-app language (recomputed when the language setting changes). */
@Composable
fun rememberCatalogText(): CatalogText {
  val languageRaw by AppSettings.language.flow.collectAsStateWithLifecycle()
  val unavailableTitle = stringResource(R.string.deck_localized_title_unavailable)
  val officialAuthor = stringResource(R.string.deck_official_author)
  val unavailableAuthor = stringResource(R.string.deck_author_unavailable)
  return remember(languageRaw, unavailableTitle, officialAuthor, unavailableAuthor) {
    CatalogText(AppSettings.currentLanguage.raw, unavailableTitle, officialAuthor, unavailableAuthor)
  }
}

/** App locale for number formatting (iOS `AppLocalization.locale`). */
@Composable
fun rememberAppLocale(): Locale {
  val languageRaw by AppSettings.language.flow.collectAsStateWithLifecycle()
  return remember(languageRaw) { AppSettings.currentLanguage.locale }
}

/** iOS inline navigation bar: back chevron, centered title, trailing actions. */
@Composable
fun InlineTopBar(
  title: String,
  onBack: (() -> Unit)?,
  modifier: Modifier = Modifier,
  leading: (@Composable RowScope.() -> Unit)? = null,
  trailing: (@Composable RowScope.() -> Unit)? = null,
) {
  Box(modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp)) {
    Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
      if (onBack != null) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("nav.back")) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.library_back), tint = Piyo.colors.accent)
        }
      }
      leading?.invoke(this)
    }
    Text(
      title,
      style = PiyoType.headline().copy(fontWeight = FontWeight.Bold),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
      modifier = Modifier.align(Alignment.Center).padding(horizontal = 96.dp).semantics { heading() },
    )
    Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) { trailing?.invoke(this) }
  }
}

/** Label with a leading icon (SwiftUI `Label`). */
@Composable
fun IconLabel(
  text: String,
  icon: ImageVector,
  color: Color,
  modifier: Modifier = Modifier,
  style: androidx.compose.ui.text.TextStyle = PiyoType.caption2().copy(fontWeight = FontWeight.Bold),
  iconSize: Dp = 13.dp,
  maxLines: Int = 1,
) {
  Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
    Text(text, style = style.copy(color = color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
  }
}

/** iOS `DeckCoverView`: deterministic gradient with the first tag/name character. */
@Composable
fun DeckCover(deckId: String, name: String, tags: List<String>, type: DeckType, width: Dp, height: Dp, modifier: Modifier = Modifier) {
  val colors = Piyo.colors
  val palettes = listOf(
    listOf(colors.accent, SystemColors.orange.copy(alpha = 0.78f)),
    listOf(colors.secondary, SystemColors.cyan.copy(alpha = 0.72f)),
    listOf(SystemColors.purple.copy(alpha = 0.82f), colors.accent),
    listOf(colors.success, SystemColors.teal.copy(alpha = 0.78f)),
    listOf(SystemColors.indigo.copy(alpha = 0.8f), SystemColors.pink.copy(alpha = 0.78f)),
  )
  val checksum = remember(deckId) { deckId.codePoints().toArray().fold(0) { acc, cp -> (acc * 31 + cp) % 10_007 } }
  val shape = RoundedCornerShape(18.dp)
  val glyph = (tags.firstOrNull() ?: name).let { if (it.isEmpty()) "" else String(Character.toChars(it.codePointAt(0))) }
  val minSide = if (width < height) width else height
  Box(
    modifier
      .size(width, height)
      .clip(shape)
      .background(Brush.linearGradient(palettes[checksum % palettes.size]))
      .border(2.dp, Color.White.copy(alpha = 0.7f), shape),
  ) {
    Text(
      glyph,
      style = PiyoType.style(minSide.value * 0.36f / Piyo.fontScale, FontWeight.Black).copy(color = Color.White),
      modifier = Modifier.align(Alignment.Center),
    )
    Icon(
      if (type == DeckType.WORD) Icons.Rounded.Abc else Icons.Rounded.FormatQuote,
      contentDescription = null,
      tint = Color.White.copy(alpha = 0.9f),
      modifier = Modifier.padding(8.dp).size(16.dp).align(Alignment.TopStart),
    )
  }
}

@Composable
fun DeckCover(deck: CatalogDeck, width: Dp, height: Dp, text: CatalogText, modifier: Modifier = Modifier) =
  DeckCover(deck.deckId, text.name(deck), text.tags(deck), deck.type, width, height, modifier)

@Composable
fun DeckCover(deck: Deck, width: Dp, height: Dp, modifier: Modifier = Modifier) {
  val text = rememberCatalogText()
  val name = deck.localizedName(text.languageCode) ?: text.unavailableTitle
  val tags = deck.localizedTags(text.languageCode) ?: emptyList()
  DeckCover(deck.deckId, name, tags, deck.type, width, height, modifier)
}

object DeckCardMetrics {
  // iOS uses 124/146pt; Android font line heights (especially CJK) are taller, so the same
  // five text rows need more room to avoid clipping the item-count row.
  val compactMinimumHeight = 136.dp
  val regularMinimumHeight = 166.dp
}

/** iOS `DeckCardView`. */
@Composable
fun DeckCard(
  deck: CatalogDeck,
  text: CatalogText,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  rank: Int? = null,
  isInstalled: Boolean = false,
  updateAvailable: Boolean = false,
  compact: Boolean = false,
  fixedHeight: Dp? = null,
  limitsTitleToOneLine: Boolean = false,
) {
  val colors = Piyo.colors
  val locale = rememberAppLocale()
  val minimumHeight = if (compact) DeckCardMetrics.compactMinimumHeight else DeckCardMetrics.regularMinimumHeight
  val shape = RoundedCornerShape(22.dp)
  val heightModifier = if (fixedHeight != null) Modifier.height(maxOf(fixedHeight, minimumHeight)) else Modifier.heightIn(min = minimumHeight)
  Row(
    modifier
      .fillMaxWidth()
      .then(heightModifier)
      .shadow(10.dp, shape, ambientColor = colors.keyShadow, spotColor = colors.keyShadow)
      .clip(shape)
      .background(colors.card)
      .clickable(role = Role.Button, onClick = onClick)
      .semantics(mergeDescendants = true) {}
      .testTag("discover.deck.${deck.deckId}")
      .padding(if (compact) 11.dp else 14.dp),
    horizontalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 13.dp),
    verticalAlignment = Alignment.Top,
  ) {
    DeckCover(deck, if (compact) 56.dp else 88.dp, if (compact) 72.dp else 112.dp, text)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (deck.official) IconLabel(stringResource(R.string.deck_badge_official), Icons.Rounded.Verified, colors.secondary)
        if (rank != null && rank <= 3) {
          IconLabel(
            stringResource(R.string.deck_rank_format, rank),
            if (rank == 1) Icons.Rounded.EmojiEvents else Icons.Rounded.WorkspacePremium,
            when (rank) {
              1 -> SystemColors.orange
              2 -> SystemColors.gray
              else -> SystemColors.brown
            },
          )
        }
        if (isInstalled) {
          IconLabel(
            stringResource(if (updateAvailable) R.string.deck_badge_update else R.string.deck_badge_installed),
            if (updateAvailable) Icons.Rounded.Sync else Icons.Rounded.CheckCircle,
            if (updateAvailable) SystemColors.orange else colors.success,
          )
        }
      }
      Text(
        text.name(deck),
        style = PiyoType.headline().copy(fontWeight = FontWeight.Bold),
        maxLines = if (limitsTitleToOneLine) 1 else 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(text.author(deck), style = PiyoType.caption().copy(color = colors.mutedInk), maxLines = 1, overflow = TextOverflow.Ellipsis)
      Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        text.tags(deck).take(2).forEach { tag ->
          Text(
            "#$tag",
            style = PiyoType.caption2().copy(color = colors.accent, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
              .widthIn(max = 140.dp)
              .clip(CircleShape)
              .background(colors.accentSoft.copy(alpha = 0.48f))
              .padding(horizontal = 7.dp, vertical = 4.dp),
          )
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        val metaStyle = PiyoType.caption2().copy(color = colors.mutedInk, fontWeight = FontWeight.Medium)
        IconLabel(pluralStringResource(R.plurals.deck_items_format, deck.itemCount, deck.itemCount), Icons.Outlined.Layers, colors.mutedInk, style = metaStyle)
        if (deck.downloadsTotal > 0) {
          IconLabel(compactNumber(deck.downloadsTotal, locale), Icons.Outlined.ArrowCircleDown, colors.mutedInk, style = metaStyle)
        }
        Text(deckTypeLevelText(deck.type, deck.level), style = metaStyle, maxLines = 1)
      }
    }
  }
}

@Composable
fun deckTypeLevelText(type: DeckType, level: Int): String {
  val typeText = stringResource(if (type == DeckType.WORD) R.string.deck_type_word else R.string.deck_type_sentence)
  return "$typeText · ${stringResource(R.string.deck_level_format, level)}"
}

/** iOS `.number.notation(.compactName)` approximation (1.2K / 3.4M). */
fun compactNumber(value: Int, locale: Locale): String {
  if (android.os.Build.VERSION.SDK_INT >= 30) {
    return android.icu.number.NumberFormatter.withLocale(locale)
      .notation(android.icu.number.Notation.compactShort())
      .format(value.toLong())
      .toString()
  }
  return NumberFormat.getIntegerInstance(locale).format(value)
}

/** Deck state flows as Compose state (forces recomposition when the library changes). */
@Composable
fun rememberDeckLibraryState(): DeckLibraryState {
  val library = AppData.deckLibrary
  val installed by library.installedDecks.collectAsStateWithLifecycle()
  val records by library.records.collectAsStateWithLifecycle()
  val installing by library.installingDeckIds.collectAsStateWithLifecycle()
  val failed by library.failedDeckIds.collectAsStateWithLifecycle()
  return DeckLibraryState(installed, records.mapValues { it.value.version }, installing, failed)
}

/** Snapshot of the deck library used by Discover rows (iOS `DeckLibrary` published state). */
data class DeckLibraryState(
  val installed: Map<String, Deck>,
  val installedVersions: Map<String, Int>,
  val installing: Set<String>,
  val failed: Set<String>,
) {
  fun isInstalled(deckId: String) = installed.containsKey(deckId)
  fun needsUpdate(entry: CatalogDeck): Boolean = AppData.deckLibrary.needsUpdate(entry)
}
