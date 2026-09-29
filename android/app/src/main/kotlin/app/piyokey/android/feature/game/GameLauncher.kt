package app.piyokey.android.feature.game

import androidx.compose.runtime.Composable
import app.piyokey.android.ui.nav.Navigator
import app.piyokey.android.ui.nav.Route
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.domain.GameCompetition
import app.piyokey.core.domain.GameKind
import app.piyokey.core.domain.game.RecallTypingGameMode

/** The six game-tab modes (iOS `GameKind.allCases` + Spacing). */
enum class GameMode(val gameKind: GameKind?) {
  FLOW(GameKind.FLOW),
  ACID_RAIN(GameKind.ACID_RAIN),
  CHOSEONG(GameKind.CHOSEONG),
  WORD_MATCH(GameKind.WORD_MATCH),
  DICTATION(GameKind.DICTATION),
  SPACING(null);

  companion object {
    fun of(kind: GameKind): GameMode = entries.first { it.gameKind == kind }
  }
}

/**
 * Cross-feature entry points into the game tab (contract). Mode lists are pushed on the given
 * navigator (a tab navigator for in-tab browsing); sessions go full screen on the app navigator
 * passed by the caller.
 */
object GameLauncher {
  /** Weekly Piyo Cup (Flow on `flow_topik_beginner` v3, competition `weekly_piyo_cup_v1`). */
  fun piyoCup(nav: Navigator) {
    val deck = GameDecks.piyoCupDeck() ?: return
    nav.push(FlowGameRoute(deck, GameKind.FLOW, GameCompetition.WEEKLY_PIYO_CUP))
  }

  /** Deck list for a mode (or the passage list for Spacing). */
  fun mode(nav: Navigator, mode: GameMode) {
    val kind = mode.gameKind
    nav.push(if (kind == null) SpacingPassageListRoute() else GameDeckListRoute(kind))
  }

  /** Starts [mode] directly on a bundled preset or installed deck. Spacing: [deckId] is a passage id. */
  fun play(nav: Navigator, mode: GameMode, deckId: String) {
    val kind = mode.gameKind
    if (kind == null) {
      SpacingPassages.all().firstOrNull { it.id == deckId }?.let { nav.push(SpacingGameRoute(it)) }
      return
    }
    val deck = GameDecks.deck(kind, deckId) ?: return
    nav.push(gameRoute(kind, deck))
  }

  internal fun gameRoute(kind: GameKind, deck: Deck): Route = when (kind) {
    GameKind.FLOW, GameKind.ACID_RAIN -> FlowGameRoute(deck, kind, null)
    GameKind.CHOSEONG -> RecallTypingRoute(deck, RecallTypingGameMode.CHOSEONG)
    GameKind.WORD_MATCH -> RecallTypingRoute(deck, RecallTypingGameMode.WORD_MATCH)
    GameKind.DICTATION -> RecallTypingRoute(deck, RecallTypingGameMode.DICTATION)
  }
}

class FlowGameRoute(val deck: Deck, val gameKind: GameKind, val competition: GameCompetition?) : Route {
  @Composable
  override fun Content() = FlowGameScreen(deck, gameKind, competition)
}

class RecallTypingRoute(val deck: Deck, val mode: RecallTypingGameMode) : Route {
  @Composable
  override fun Content() = RecallTypingScreen(deck, mode)
}

class GameDeckListRoute(val gameKind: GameKind) : Route {
  @Composable
  override fun Content() = GameDeckListScreen(gameKind)
}
