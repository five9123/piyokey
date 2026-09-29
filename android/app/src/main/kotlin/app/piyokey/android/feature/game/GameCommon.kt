package app.piyokey.android.feature.game

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.piyokey.android.R
import app.piyokey.android.data.AppData
import app.piyokey.android.data.decks.appMeaning
import app.piyokey.android.platform.Haptics
import app.piyokey.android.platform.games.RankedRecord
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.domain.GameKind
import app.piyokey.core.domain.GameRecord
import app.piyokey.core.domain.SessionMode
import app.piyokey.core.domain.game.CompactGameHudMetricLayout
import app.piyokey.core.domain.game.GamePreset
import app.piyokey.core.domain.game.GamePresetRules
import app.piyokey.core.domain.game.GameResultPresentation
import app.piyokey.core.domain.game.MeaningLookup
import java.text.NumberFormat

/** iOS system colours used by the game screens. */
internal object GameColors {
  val orange = Color(0xFFFF9500)
  val yellow = Color(0xFFFFCC00)
  val cyan = Color(0xFF32ADE6)
  val purple = Color(0xFFAF52DE)
  val teal = Color(0xFF30B0C7)
}

/** Debug/test hooks (iOS `UITEST_*` environment variables). Production never changes them. */
object GameTestHooks {
  /** Countdown step (iOS `UITEST_GAME_COUNTDOWN_STEP_SECONDS`), null = 1 s. */
  @Volatile var countdownStepMillis: Long? = null

  /** Flow/Acid Rain duration (iOS `UITEST_GAME_DURATION_SECONDS`), null = 60 s. */
  @Volatile var gameDurationSeconds: Double? = null

  /** Result reveal time scale (iOS `UITEST_RESULT_ANIMATION_SCALE`). */
  @Volatile var resultAnimationScale: Double = 1.0

  /** Shows the debug FPS probe (iOS `HANCO_SHOW_GAME_FPS=1`); debug builds only. */
  @Volatile var showsFrameRate: Boolean = false
}

/** Monotonic seconds for the pure game reducers (`SystemClock.uptimeMillis` base). */
internal fun monotonicSeconds(): Double = SystemClock.uptimeMillis() / 1_000.0

/** iOS `DeckItem.appMeaning` as a domain lookup. */
internal val appMeaningLookup: MeaningLookup = { it.appMeaning }

internal fun formatNumber(value: Int): String = NumberFormat.getIntegerInstance(app.piyokey.android.ui.theme.L.locale).format(value)

internal val GameKind.titleRes: Int
  get() = when (this) {
    GameKind.FLOW -> R.string.game_mode_flow
    GameKind.ACID_RAIN -> R.string.game_mode_acid_rain
    GameKind.CHOSEONG -> R.string.game_mode_choseong
    GameKind.WORD_MATCH -> R.string.game_mode_word_match
    GameKind.DICTATION -> R.string.game_mode_dictation
  }

/** Bundled game decks (iOS `GamePresetDeckLoader` / `PiyoCupDeckLoader`). */
internal object GameDecks {
  private val presetCache = HashMap<GameKind, List<GamePreset>>()

  fun presets(kind: GameKind): List<GamePreset> =
    presetCache.getOrPut(kind) { GamePresetRules.load(kind) { path -> AppData.bundled.deck(path) } }

  fun piyoCupDeck(): Deck? = AppData.bundled.deck(GamePresetRules.PIYO_CUP_ASSET_PATH)?.takeIf(GamePresetRules::isValidPiyoCupDeck)

  /** A preset of [kind] or an installed deck. */
  fun deck(kind: GameKind, deckId: String): Deck? =
    presets(kind).firstOrNull { it.deck.deckId == deckId }?.deck ?: AppData.deckLibrary.installedDeck(deckId)

  /** Analytics `deck_source` (iOS `analyticsDeckSource`). */
  fun analyticsDeckSource(deck: Deck, presentation: GameResultPresentation): String =
    app.piyokey.core.domain.game.GameAnalytics.deckSource(
      isPresetOrCup = presentation == GameResultPresentation.PIYO_CUP || presentation.presetLevel(deck) != null,
      installedSourceRaw = AppData.deckLibrary.record(deck.deckId)?.source?.name?.lowercase(),
    )
}

internal fun GameRecord.toRanked() = RankedRecord(
  id = id,
  isGameMode = mode == SessionMode.GAME,
  deckId = deckId,
  deckVersion = deckVersion,
  competition = competition?.raw,
  score = score,
  playedAt = playedAt,
)

/** Notification-style haptics (iOS `UINotificationFeedbackGenerator`), gated by the keyboard pref. */
internal object GameHaptics {
  fun success(view: View) {
    if (!Haptics.isEnabled) return
    view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP)
  }

  fun error(view: View) {
    if (!Haptics.isEnabled) return
    view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
  }
}

/** iOS `CompactGameHUDMetric`. */
@Composable
internal fun CompactGameHudMetric(
  icon: ImageVector,
  value: String,
  label: String,
  testTag: String,
  tint: Color,
  contentWidth: Int,
  modifier: Modifier = Modifier,
) {
  val colors = Piyo.colors
  Box(
    modifier
      .clip(RoundedCornerShape(10.dp))
      .background(colors.card)
      .border(0.8.dp, colors.secondary.copy(alpha = 0.24f), RoundedCornerShape(12.dp))
      .padding(horizontal = 5.dp, vertical = 4.dp)
      .width(contentWidth.dp),
  ) {
    Icon(
      icon,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.align(Alignment.CenterStart).size(CompactGameHudMetricLayout.ICON_COLUMN_WIDTH.dp),
    )
    Column(
      Modifier.fillMaxWidth().padding(horizontal = CompactGameHudMetricLayout.VALUE_HORIZONTAL_INSET.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        value,
        style = PiyoType.style(CompactGameHudMetricLayout.valuePointSize(value), FontWeight.ExtraBold),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.testTag(testTag).semantics { contentDescription = value },
      )
      Text(
        label,
        style = PiyoType.style(9f, FontWeight.Medium).copy(color = colors.mutedInk),
        maxLines = 1,
        softWrap = false,
      )
    }
  }
}

/** Session close "×" (iOS toolbar leading button, `game.end`). */
@Composable
internal fun GameCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val colors = Piyo.colors
  val label = stringResource(R.string.game_end)
  Box(
    modifier
      .size(44.dp)
      .clickable(role = Role.Button, onClick = onClick)
      .semantics { contentDescription = label }
      .testTag("game.end"),
    contentAlignment = Alignment.Center,
  ) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(colors.card), contentAlignment = Alignment.Center) {
      Icon(Icons.Rounded.Close, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
    }
  }
}

/** Inline navigation bar of a game session: close button on the left, HUD centred. */
@Composable
internal fun GameSessionTopBar(onClose: () -> Unit, trailing: @Composable () -> Unit = {}, hud: @Composable () -> Unit) {
  Box(Modifier.fillMaxWidth().background(Piyo.colors.backgroundTop).padding(horizontal = 6.dp, vertical = 4.dp)) {
    GameCloseButton(onClose, Modifier.align(Alignment.CenterStart))
    Row(Modifier.align(Alignment.Center).padding(horizontal = 46.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) { hud() }
    Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
  }
}

/** 3-2-1 overlay (iOS `countdownOverlay`). */
@Composable
internal fun GameCountdownOverlay(value: Int, testTag: String, modifier: Modifier = Modifier) {
  val colors = Piyo.colors
  val ready = stringResource(R.string.game_countdown_ready)
  Box(
    modifier
      .fillMaxSize()
      .background(Color.Black.copy(alpha = 0.18f))
      .clickable(enabled = false) {}
      .semantics { contentDescription = "$ready $value" }
      .testTag(testTag),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      Modifier
        .shadow(18.dp, RoundedCornerShape(30.dp), ambientColor = colors.keyShadow, spotColor = colors.keyShadow)
        .clip(RoundedCornerShape(30.dp))
        .background(colors.card)
        .padding(horizontal = 38.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(ready, style = PiyoType.headline().copy(fontWeight = FontWeight.ExtraBold))
      Text(
        value.toString(),
        style = PiyoType.style(76f, FontWeight.Black).copy(color = colors.accent),
        textAlign = TextAlign.Center,
      )
    }
  }
}

/** VoiceOver-style announcement (iOS `UIAccessibility.post(.announcement)`). */
@Suppress("DEPRECATION")
internal fun announce(view: View, text: CharSequence) = view.announceForAccessibility(text)
