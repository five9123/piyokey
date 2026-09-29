package app.piyokey.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.piyokey.android.R
import app.piyokey.android.data.AppData
import app.piyokey.android.data.mascot.MascotMood
import app.piyokey.android.feature.discover.IconLabel
import app.piyokey.android.feature.discover.InlineTopBar
import app.piyokey.android.feature.discover.rememberCatalogText
import app.piyokey.android.feature.practice.PracticeLauncher
import app.piyokey.android.ui.mascot.GrowingMascot
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.nav.LocalTabNavigator
import app.piyokey.android.ui.nav.Route
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoBackground
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.ui.theme.centeredContent
import app.piyokey.core.domain.ReviewDeckItem

/** iOS `ReviewDeckView`: active review items, practice, manual removal. */
class ReviewDeckRoute : Route {
  @Composable
  override fun Content() {
    val navigator = LocalTabNavigator.current
    val appNavigator = LocalAppNavigator.current
    val items by AppData.review.activeItems.collectAsStateWithLifecycle()
    PiyoBackground {
      Column(Modifier.fillMaxSize().testTag("review.deck.screen")) {
        InlineTopBar(stringResource(R.string.review_deck_navigation_title), onBack = { navigator.pop() })
        if (items.isEmpty()) {
          EmptyReview()
        } else {
          LazyColumn(
            Modifier.fillMaxSize().centeredContent(Piyo.metrics.readableContentMaxWidth),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            item("summary") { SummaryCard(items.size) }
            item("start") {
              Row(
                Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(18.dp))
                  .background(Piyo.colors.accent)
                  .clickable(role = Role.Button) {
                    PracticeLauncher.reviewDeck(appNavigator)
                  }
                  .semantics(mergeDescendants = true) {}
                  .testTag("review.deck.start")
                  .padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White)
                Text(stringResource(R.string.review_deck_start), style = PiyoType.headline().copy(color = Color.White, fontWeight = FontWeight.Bold))
              }
            }
            items(items, key = { it.id }) { ReviewRow(it) }
          }
        }
      }
    }
  }
}

@Composable
private fun SummaryCard(count: Int) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(24.dp)
  Row(
    Modifier
      .fillMaxWidth()
      .shadow(10.dp, shape, ambientColor = colors.keyShadow, spotColor = colors.keyShadow)
      .clip(shape)
      .background(colors.card)
      .padding(18.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(64.dp, 76.dp).clip(RoundedCornerShape(20.dp)).background(colors.accent), contentAlignment = Alignment.Center) {
      Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(stringResource(R.string.review_deck_title), style = PiyoType.title3().copy(fontWeight = FontWeight.Bold))
      Text(stringResource(R.string.review_deck_subtitle), style = PiyoType.caption().copy(color = colors.mutedInk))
      Text(
        pluralStringResource(R.plurals.review_deck_count_format, count, count),
        style = PiyoType.caption().copy(color = colors.accent, fontWeight = FontWeight.Bold),
        modifier = Modifier.testTag("review.deck.count"),
      )
    }
  }
}

@Composable
private fun ReviewRow(item: ReviewDeckItem) {
  val colors = Piyo.colors
  val language = rememberCatalogText().languageCode
  val deckItem = item.deckItem
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(colors.card)
      .testTag("review.deck.item.${item.id}")
      .padding(start = 15.dp, top = 15.dp, bottom = 15.dp, end = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(13.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(5.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(item.ko, style = PiyoType.title3().copy(fontWeight = FontWeight.Bold))
        deckItem.localizedReading(language)?.let { Text(it, style = PiyoType.caption().copy(color = colors.secondary)) }
      }
      deckItem.localizedMeaning(language)?.let { Text(it, style = PiyoType.subheadline().copy(color = colors.mutedInk)) }
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val style = PiyoType.caption2().copy(fontWeight = FontWeight.SemiBold)
        IconLabel(pluralStringResource(R.plurals.review_deck_miss_count_format, item.missCount, item.missCount), Icons.Rounded.Error, colors.mutedInk, style = style)
        IconLabel(stringResource(R.string.review_deck_perfect_count_format, item.consecutivePerfect), Icons.Rounded.Verified, colors.mutedInk, style = style)
      }
    }
    IconButton(
      onClick = { AppData.review.removeManually(item.itemId, item.sourceDeckId) },
      modifier = Modifier.testTag("review.deck.remove.${item.id}"),
    ) {
      Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.review_deck_remove), tint = colors.error)
    }
  }
}

@Composable
private fun EmptyReview() {
  Column(
    Modifier.fillMaxSize().padding(24.dp).testTag("review.deck.empty"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
  ) {
    Box(Modifier.size(108.dp, 164.dp), contentAlignment = Alignment.Center) { GrowingMascot(mood = MascotMood.CHEER, size = 60.dp) }
    Text(stringResource(R.string.review_deck_empty_title), style = PiyoType.title2())
    Text(
      stringResource(R.string.review_deck_empty_message),
      style = PiyoType.subheadline().copy(color = Piyo.colors.mutedInk),
      textAlign = TextAlign.Center,
    )
  }
}
