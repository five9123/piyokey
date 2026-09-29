package app.piyokey.android.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LayersClear
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.piyokey.android.R
import app.piyokey.android.data.AppData
import app.piyokey.android.data.decks.appName
import app.piyokey.android.feature.discover.DeckCover
import app.piyokey.android.platform.games.PlayGamesRules
import app.piyokey.android.ui.nav.AppTab
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.nav.LocalTabController
import app.piyokey.android.ui.nav.LocalTabNavigator
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.ui.theme.centeredContent
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.domain.GameKind
import app.piyokey.core.domain.SessionInputMode
import app.piyokey.core.domain.game.FlowGameCourse
import app.piyokey.core.domain.game.GamePreset
import app.piyokey.core.domain.game.GamePresetLevel
import app.piyokey.core.domain.game.GamePresetRules

/** iOS `GameDeckListView`: three bundled presets + "add deck", then the user's installed decks. */
@Composable
fun GameDeckListScreen(gameKind: GameKind) {
  val tabNav = LocalTabNavigator.current
  val appNav = LocalAppNavigator.current
  val tabs = LocalTabController.current
  val metrics = Piyo.metrics
  val presets = remember(gameKind) { GameDecks.presets(gameKind) }
  val installed by AppData.deckLibrary.installedList.collectAsStateWithLifecycle()
  // Observed so best scores refresh after a game.
  AppData.gameProgress.deckProgress.collectAsStateWithLifecycle().value

  val findDecks = {
    tabNav.popToRoot()
    tabs.select(AppTab.DISCOVER)
  }
  val play = { deck: Deck -> appNav.push(GameLauncher.gameRoute(gameKind, deck)) }

  Column(Modifier.fillMaxSize().testTag("game.deck_selection.screen")) {
    GameNavigationBar(stringResource(gameKind.titleRes), showsBack = tabNav.canPop, onBack = { tabNav.pop() })
    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .centeredContent(metrics.readableContentMaxWidth)
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      when {
        presets.isNotEmpty() -> PresetSection(gameKind, presets, installed, play, findDecks)
        installed.isEmpty() -> EmptyState(findDecks)
        else -> InstalledSection(gameKind, installed, play, findDecks)
      }
    }
  }
}

@Composable
@NonSkippableComposable
private fun PresetSection(
  kind: GameKind,
  presets: List<GamePreset>,
  installed: List<Deck>,
  play: (Deck) -> Unit,
  findDecks: () -> Unit,
) {
  val colors = Piyo.colors
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text(stringResource(GameStrings.selectionIntro(kind)), style = PiyoType.subheadline().copy(color = colors.mutedInk))
    val cells: List<GamePreset?> = presets + listOf(null)
    cells.chunked(2).forEach { row ->
      Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        row.forEach { preset ->
          val m = Modifier.weight(1f).fillMaxHeight()
          if (preset != null) PresetCard(kind, preset, { play(preset.deck) }, m) else AddDeckCard(kind, findDecks, m)
        }
        if (row.size == 1) Spacer(Modifier.weight(1f))
      }
    }
    if (installed.isNotEmpty()) {
      Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(Icons.Rounded.Layers, stringResource(GameStrings.addedDecksTitle(kind)))
        installed.forEach { DeckLink(kind, it, play) }
      }
    }
  }
}

@Composable
private fun SectionLabel(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
  Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Icon(icon, contentDescription = null, tint = Piyo.colors.ink, modifier = Modifier.size(20.dp))
    Text(title, style = PiyoType.headline().copy(fontWeight = FontWeight.Bold))
  }
}

@Composable
private fun presetTint(level: GamePresetLevel): Color = when (level) {
  GamePresetLevel.BEGINNER -> Piyo.colors.success
  GamePresetLevel.INTERMEDIATE -> Piyo.colors.secondary
  GamePresetLevel.ADVANCED -> GameColors.purple
}

private fun presetIcon(level: GamePresetLevel): ImageVector = when (level) {
  GamePresetLevel.BEGINNER -> Icons.Rounded.Spa
  GamePresetLevel.INTERMEDIATE -> Icons.Rounded.Bolt
  GamePresetLevel.ADVANCED -> Icons.Rounded.WorkspacePremium
}

@Composable
@NonSkippableComposable
private fun PresetCard(kind: GameKind, preset: GamePreset, onClick: () -> Unit, modifier: Modifier) {
  val colors = Piyo.colors
  val tint = presetTint(preset.level)
  val shape = RoundedCornerShape(22.dp)
  val deckId = preset.deck.deckId
  Column(
    modifier
      .shadow(8.dp, shape, ambientColor = colors.keyShadow, spotColor = colors.keyShadow)
      .clip(shape)
      .background(colors.card)
      .border(1.5.dp, tint.copy(alpha = 0.24f), shape)
      .clickable(role = Role.Button, onClick = onClick)
      .defaultMinSize(minHeight = 206.dp)
      .padding(14.dp)
      .testTag("game.${kind.raw}.preset.${preset.level.raw}"),
    verticalArrangement = Arrangement.spacedBy(9.dp),
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
      Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(tint.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center,
      ) { Icon(presetIcon(preset.level), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp)) }
      Spacer(Modifier.weight(1f))
      Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
    Text(stringResource(GameStrings.presetTitle(kind, preset.level)), style = PiyoType.headline().copy(fontWeight = FontWeight.ExtraBold))
    Text(
      stringResource(GameStrings.presetDifficulty(kind, preset.level)),
      style = PiyoType.caption2().copy(fontWeight = FontWeight.Black, color = tint),
      modifier = Modifier.clip(CircleShape).background(tint.copy(alpha = 0.1f)).padding(horizontal = 7.dp, vertical = 3.dp),
    )
    Text(stringResource(GameStrings.presetDetail(kind, preset.level)), style = PiyoType.caption2().copy(color = colors.mutedInk))
    Spacer(Modifier.height(2.dp))
    val count = preset.deck.items.size
    Text(
      pluralStringResource(GameStrings.presetWordCount(kind), count, count),
      style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.secondary),
    )
    AppData.gameProgress.progress(deckId, kind, SessionInputMode.BUILT_IN)?.let {
      Text(
        stringResource(R.string.game_selection_best_score_builtin, formatNumber(it.bestScore)),
        style = PiyoType.caption2().copy(fontWeight = FontWeight.Bold, color = colors.accent),
      )
    }
    AppData.gameProgress.progress(deckId, kind, SessionInputMode.BUILT_IN_KOREAN_10KEY)?.let {
      Text(
        stringResource(R.string.game_selection_best_score_korean_10key, formatNumber(it.bestScore)),
        style = PiyoType.caption2().copy(fontWeight = FontWeight.Bold, color = colors.secondary),
      )
    }
  }
}

@Composable
private fun AddDeckCard(kind: GameKind, onClick: () -> Unit, modifier: Modifier) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(22.dp)
  val dash = colors.accent.copy(alpha = 0.34f)
  Column(
    modifier
      .clip(shape)
      .background(colors.card)
      .drawBehind {
        drawRoundRect(
          dash,
          cornerRadius = CornerRadius(22.dp.toPx()),
          style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
        )
      }
      .clickable(role = Role.Button, onClick = onClick)
      .defaultMinSize(minHeight = 206.dp)
      .padding(14.dp)
      .testTag("game.${kind.raw}.add_deck"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(colors.accentSoft.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
      Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.accent, modifier = Modifier.size(26.dp))
    }
    Text(stringResource(GameStrings.addDeckTitle(kind)), style = PiyoType.headline().copy(fontWeight = FontWeight.ExtraBold))
    Text(stringResource(GameStrings.addDeckDetail(kind)), style = PiyoType.caption2().copy(color = colors.mutedInk))
    Spacer(Modifier.height(2.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      Icon(Icons.Rounded.TravelExplore, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
      Text(stringResource(GameStrings.addDeckAction(kind)), style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.accent))
    }
  }
}

@Composable
@NonSkippableComposable
private fun InstalledSection(kind: GameKind, installed: List<Deck>, play: (Deck) -> Unit, findDecks: () -> Unit) {
  val colors = Piyo.colors
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      SectionLabel(Icons.Rounded.Layers, stringResource(R.string.game_selection_choose_deck), Modifier.weight(1f))
      Text(
        stringResource(R.string.game_selection_find_more),
        style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.accent),
        modifier = Modifier.defaultMinSize(minHeight = 44.dp).clickable(role = Role.Button, onClick = findDecks).padding(8.dp),
      )
    }
    installed.forEach { DeckLink(kind, it, play) }
  }
}

@Composable
private fun EmptyState(findDecks: () -> Unit) {
  val colors = Piyo.colors
  Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.card).padding(22.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Icon(Icons.Rounded.LayersClear, contentDescription = null, tint = colors.mutedInk, modifier = Modifier.size(40.dp))
    Text(stringResource(R.string.game_selection_empty_title), style = PiyoType.headline().copy(fontWeight = FontWeight.Bold))
    Text(
      stringResource(R.string.game_selection_empty_message),
      style = PiyoType.caption().copy(color = colors.mutedInk),
      textAlign = TextAlign.Center,
    )
    Row(
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(17.dp))
        .background(colors.accent)
        .clickable(role = Role.Button, onClick = findDecks)
        .padding(vertical = 14.dp)
        .testTag("game.find_decks"),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Rounded.TravelExplore, contentDescription = null, tint = Color.White, modifier = Modifier.padding(end = 6.dp).size(20.dp))
      Text(stringResource(R.string.game_selection_find_decks), style = PiyoType.headline().copy(fontWeight = FontWeight.Bold, color = Color.White))
    }
  }
}

@Composable
@NonSkippableComposable
private fun DeckLink(kind: GameKind, deck: Deck, play: (Deck) -> Unit) {
  val supported = remember(deck, kind) { GamePresetRules.supportsGame(deck, kind, appMeaningLookup) }
  if (supported) {
    DeckRow(kind, deck, supported, Modifier.clickable(role = Role.Button) { play(deck) }.testTag("game.deck.${deck.deckId}"))
  } else {
    DeckRow(kind, deck, supported, Modifier.alpha(0.6f).testTag("game.deck.unavailable.${deck.deckId}"))
  }
}

@Composable
@NonSkippableComposable
private fun DeckRow(kind: GameKind, deck: Deck, supported: Boolean, modifier: Modifier) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(22.dp)
  val course = remember(deck) { FlowGameCourse.of(deck) }
  Row(
    Modifier
      .fillMaxWidth()
      .shadow(9.dp, shape, ambientColor = colors.keyShadow, spotColor = colors.keyShadow)
      .clip(shape)
      .background(colors.card)
      .then(modifier)
      .padding(14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    DeckCover(deck, 70.dp, 88.dp)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(deck.appName, style = PiyoType.headline().copy(fontWeight = FontWeight.Bold), maxLines = 2)
      Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        val caption = PiyoType.caption().copy(fontWeight = FontWeight.SemiBold, color = colors.secondary)
        Text(stringResource(if (kind == GameKind.FLOW) course.nameRes else kind.titleRes), style = caption)
        Text(pluralStringResource(R.plurals.deck_items_format, deck.items.size, deck.items.size), style = caption)
      }
      Text(stringResource(deckHintRes(kind, supported)), style = PiyoType.caption2().copy(color = colors.mutedInk))
      if (kind == GameKind.FLOW && PlayGamesRules.isEligible(deck.deckId, deck.version)) {
        Row(
          Modifier.testTag("game.deck.ranked.${deck.deckId}"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = GameColors.orange, modifier = Modifier.size(12.dp))
          Text(
            stringResource(R.string.game_center_ranked_deck),
            style = PiyoType.caption2().copy(fontWeight = FontWeight.Black, color = GameColors.orange),
          )
        }
      }
      val bold = PiyoType.caption2().copy(fontWeight = FontWeight.Bold)
      AppData.gameProgress.progress(deck.deckId, kind, SessionInputMode.BUILT_IN)?.let {
        Text(
          stringResource(bestScoreRes(kind), formatNumber(it.bestScore)),
          style = bold.copy(color = colors.accent),
          modifier = Modifier.testTag("game.deck.best_score.${deck.deckId}"),
        )
      }
      AppData.gameProgress.progress(deck.deckId, kind, SessionInputMode.BUILT_IN_KOREAN_10KEY)?.let {
        Text(
          stringResource(R.string.game_selection_best_score_korean_10key, formatNumber(it.bestScore)),
          style = bold.copy(color = colors.secondary),
          modifier = Modifier.testTag("game.deck.best_score.korean_10key.${deck.deckId}"),
        )
      }
      AppData.gameProgress.progress(deck.deckId, kind, SessionInputMode.OS_IME)?.let {
        Text(
          stringResource(R.string.game_selection_best_score_os_ime, formatNumber(it.bestScore)),
          style = bold.copy(color = colors.secondary),
          modifier = Modifier.testTag("game.deck.best_score.os_ime.${deck.deckId}"),
        )
      }
    }
    Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
  }
}

internal val FlowGameCourse.nameRes: Int
  get() = when (this) {
    FlowGameCourse.SHORT_WORD -> R.string.game_course_short_word
    FlowGameCourse.WORD -> R.string.game_course_word
    FlowGameCourse.SENTENCE -> R.string.game_course_sentence
  }

private fun deckHintRes(kind: GameKind, supported: Boolean): Int = when (kind) {
  GameKind.FLOW -> R.string.game_selection_deck_hint_flow
  GameKind.ACID_RAIN -> R.string.game_selection_deck_hint_acid_rain
  GameKind.CHOSEONG -> if (supported) R.string.game_selection_deck_hint_choseong else R.string.game_selection_deck_hint_choseong_unavailable
  GameKind.WORD_MATCH -> if (supported) R.string.game_selection_deck_hint_word_match else R.string.game_selection_deck_hint_word_match_unavailable
  GameKind.DICTATION -> if (supported) R.string.game_selection_deck_hint_dictation else R.string.game_selection_deck_hint_dictation_unavailable
}

private fun bestScoreRes(kind: GameKind): Int = when (kind) {
  GameKind.FLOW -> R.string.game_selection_best_score_builtin
  GameKind.ACID_RAIN -> R.string.game_selection_best_score_acid_rain
  GameKind.CHOSEONG -> R.string.game_selection_best_score_choseong
  GameKind.WORD_MATCH -> R.string.game_selection_best_score_word_match
  GameKind.DICTATION -> R.string.game_selection_best_score_dictation
}
