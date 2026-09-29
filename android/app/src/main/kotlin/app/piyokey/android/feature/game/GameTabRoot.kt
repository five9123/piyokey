package app.piyokey.android.feature.game

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.piyokey.android.R
import app.piyokey.android.feature.onboarding.AppTourTarget
import app.piyokey.android.feature.onboarding.appTourTarget
import app.piyokey.android.feature.settings.SettingsGearButton
import app.piyokey.android.platform.analytics.Telemetry
import app.piyokey.android.platform.games.PlayGamesService
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.nav.LocalTabNavigator
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.ui.theme.WidthClass
import app.piyokey.android.ui.theme.centeredContent
import app.piyokey.core.domain.GameCompetition
import app.piyokey.core.domain.GameKind

/** iOS `GameDeckSelectionView`: weekly Piyo Cup card + the 5 deck-game modes + Spacing. */
@Composable
fun GameTabRoot() {
  val tabNav = LocalTabNavigator.current
  val appNav = LocalAppNavigator.current
  val activity = LocalActivity.current
  val metrics = Piyo.metrics
  val cupDeck = remember { GameDecks.piyoCupDeck() }

  LaunchedEffect(Unit) {
    Telemetry.setCrashContext(feature = "game")
    activity?.let { runCatching { PlayGamesService.refreshSignInState(it) } }
  }

  Column(Modifier.fillMaxSize().testTag("game.selection.screen")) {
    GameNavigationBar(stringResource(R.string.game_selection_navigation_title), showsBack = false)
    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .centeredContent(metrics.hubContentMaxWidth)
        .padding(horizontal = metrics.horizontalPadding, vertical = 18.dp)
        .testTag("game.mode.grid"),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      PiyoCupCard(
        isAvailable = cupDeck != null,
        onClick = { cupDeck?.let { appNav.push(FlowGameRoute(it, GameKind.FLOW, GameCompetition.WEEKLY_PIYO_CUP)) } },
      )
      val columns = if (metrics.widthClass == WidthClass.WIDE) 3 else 2
      val cards: List<@Composable (Modifier) -> Unit> = GameMode.entries.map { mode ->
        { modifier: Modifier ->
          GameModeCard(
            mode = mode,
            onClick = { GameLauncher.mode(tabNav, mode) },
            modifier = modifier.appTourTarget(AppTourTarget.GAME_MODES, enabled = mode == GameMode.FLOW),
          )
        }
      }
      cards.chunked(columns).forEach { row ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          row.forEach { card -> card(Modifier.weight(1f).fillMaxHeight()) }
          repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
      }
    }
  }
}

/** Inline title bar with the settings gear (iOS `.navigationBarTitleDisplayMode(.inline)` + `rootSettingsToolbar`). */
@Composable
internal fun GameNavigationBar(title: String, showsBack: Boolean, onBack: () -> Unit = {}) {
  val colors = Piyo.colors
  Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).padding(horizontal = 6.dp)) {
    if (showsBack) {
      Box(
        Modifier.align(Alignment.CenterStart).size(44.dp).clickable(role = Role.Button, onClick = onBack).testTag("game.back"),
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, contentDescription = stringResource(R.string.result_back), tint = colors.accent)
      }
    }
    Text(
      title,
      style = PiyoType.headline(),
      maxLines = 1,
      textAlign = TextAlign.Center,
      modifier = Modifier.align(Alignment.Center).padding(horizontal = 52.dp),
    )
    Box(Modifier.align(Alignment.CenterEnd)) { SettingsGearButton() }
  }
}

internal val GameMode.icon: ImageVector
  get() = when (this) {
    GameMode.FLOW -> Icons.Rounded.TouchApp
    GameMode.ACID_RAIN -> Icons.Rounded.Thunderstorm
    GameMode.CHOSEONG -> Icons.Rounded.QuestionAnswer
    GameMode.WORD_MATCH -> Icons.AutoMirrored.Rounded.MenuBook
    GameMode.DICTATION -> Icons.Rounded.Hearing
    GameMode.SPACING -> Icons.Rounded.SpaceBar
  }

@Composable
internal fun GameMode.tint(): Color = when (this) {
  GameMode.FLOW -> Piyo.colors.secondary
  GameMode.ACID_RAIN -> GameColors.cyan
  GameMode.CHOSEONG -> Piyo.colors.accent
  GameMode.WORD_MATCH -> GameColors.orange
  GameMode.DICTATION -> GameColors.purple
  GameMode.SPACING -> GameColors.teal
}

private val GameMode.titleRes: Int
  get() = gameKind?.titleRes ?: R.string.game_mode_spacing

private val GameMode.detailRes: Int
  get() = when (this) {
    GameMode.FLOW -> R.string.game_mode_flow_detail
    GameMode.ACID_RAIN -> R.string.game_mode_acid_rain_detail
    GameMode.CHOSEONG -> R.string.game_mode_choseong_detail
    GameMode.WORD_MATCH -> R.string.game_mode_word_match_detail
    GameMode.DICTATION -> R.string.game_mode_dictation_detail
    GameMode.SPACING -> R.string.game_mode_spacing_detail
  }

internal val GameMode.testTagId: String get() = "game.mode." + (gameKind?.raw ?: "spacing")

@Composable
private fun PiyoCupCard(isAvailable: Boolean, onClick: () -> Unit) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(24.dp)
  val scale = Piyo.metrics.typographyScale
  var modifier = Modifier
    .fillMaxWidth()
    .clip(shape)
    .background(Brush.linearGradient(listOf(GameColors.yellow.copy(alpha = 0.13f), colors.card)))
    .border(1.5.dp, GameColors.orange.copy(alpha = 0.3f), shape)
  modifier = if (isAvailable) {
    modifier.clickable(role = Role.Button, onClick = onClick).testTag("game.piyo_cup")
  } else {
    modifier.alpha(0.6f).testTag("game.piyo_cup.unavailable")
  }
  Row(modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
    Box(
      Modifier.size((62 * scale).dp).clip(RoundedCornerShape(19.dp)).background(GameColors.yellow.copy(alpha = 0.22f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Rounded.WorkspacePremium, contentDescription = null, tint = GameColors.orange, modifier = Modifier.size(32.dp))
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(stringResource(R.string.piyo_cup_title), style = PiyoType.headline().copy(fontWeight = FontWeight.ExtraBold))
        Text(
          stringResource(R.string.piyo_cup_weekly_badge),
          style = PiyoType.caption2().copy(fontWeight = FontWeight.Black, color = GameColors.orange),
          modifier = Modifier.clip(CircleShape).background(GameColors.orange.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 3.dp),
        )
      }
      Text(
        stringResource(if (isAvailable) R.string.piyo_cup_detail else R.string.piyo_cup_unavailable),
        style = PiyoType.caption().copy(color = colors.mutedInk),
      )
    }
    Icon(
      if (isAvailable) Icons.Rounded.PlayCircle else Icons.Rounded.Error,
      contentDescription = null,
      tint = GameColors.orange,
      modifier = Modifier.size(28.dp),
    )
  }
}

@Composable
private fun GameModeCard(mode: GameMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val colors = Piyo.colors
  val tint = mode.tint()
  val shape = RoundedCornerShape(24.dp)
  val scale = Piyo.metrics.typographyScale
  Column(
    modifier
      .shadow(10.dp, shape, ambientColor = colors.keyShadow, spotColor = colors.keyShadow)
      .clip(shape)
      .background(colors.card)
      .border(1.5.dp, tint.copy(alpha = 0.2f), shape)
      .clickable(role = Role.Button, onClick = onClick)
      .defaultMinSize(minHeight = 210.dp)
      .padding(16.dp)
      .testTag(mode.testTagId),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      Modifier.size((58 * scale).dp).clip(RoundedCornerShape(18.dp)).background(tint.copy(alpha = 0.15f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(mode.icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
    }
    Text(stringResource(mode.titleRes), style = PiyoType.headline().copy(fontWeight = FontWeight.ExtraBold))
    Text(stringResource(mode.detailRes), style = PiyoType.caption().copy(color = colors.mutedInk))
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
      Text(
        stringResource(if (mode == GameMode.SPACING) R.string.spacing_selection_choose_passage else R.string.game_mode_choose_deck),
        style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = tint),
      )
      Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp).width(14.dp))
    }
  }
}
