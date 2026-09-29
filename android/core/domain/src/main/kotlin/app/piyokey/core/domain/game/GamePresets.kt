package app.piyokey.core.domain.game

import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.DeckType
import app.piyokey.core.deckkit.DeckValidator
import app.piyokey.core.domain.GameCompetition
import app.piyokey.core.domain.GameKind
import app.piyokey.core.domain.GameProgressRules

/*
 * Pure rules of iOS `GameDeckSelectionView.swift` and `FlowGameResultView.swift`
 * (`GamePresetLevel`, `GamePresetDeckLoader`, `GamePresetSessionRandomizer`,
 * `GameResultPresentation`, `PiyoCupDeckLoader`).
 */

/** Bundled TOPIK preset levels shown for every deck game. */
enum class GamePresetLevel(val raw: String, val deckLevel: Int) {
  BEGINNER("beginner", 1),
  INTERMEDIATE("intermediate", 2),
  ADVANCED("advanced", 3);

  fun deckId(gameKind: GameKind) = "${gameKind.raw}_topik_$raw"
  fun titleKey(gameKind: GameKind) = "game.${gameKind.raw}.preset.$raw.title"
  fun difficultyKey(gameKind: GameKind) = "game.${gameKind.raw}.preset.$raw.difficulty"
  fun detailKey(gameKind: GameKind) = "game.${gameKind.raw}.preset.$raw.detail"

  companion object {
    const val BUNDLED_DECK_VERSION = 3
    const val BUNDLED_ITEM_COUNT = 100

    /** Asset path of a bundled preset (`decks/<kind>/<deckId>_v3.json`). */
    fun assetPath(gameKind: GameKind, level: GamePresetLevel) =
      "decks/${gameKind.raw}/${level.deckId(gameKind)}_v$BUNDLED_DECK_VERSION.json"

    fun of(deckId: String, gameKind: GameKind): GamePresetLevel? = entries.firstOrNull { it.deckId(gameKind) == deckId }
  }
}

data class GamePreset(val gameKind: GameKind, val level: GamePresetLevel, val deck: Deck) {
  val id: String get() = level.raw
}

object GamePresetRules {
  /** iOS `GamePresetDeckLoader` acceptance checks for one decoded bundled deck. */
  fun isValidPreset(deck: Deck, gameKind: GameKind, level: GamePresetLevel): Boolean =
    deck.deckId == level.deckId(gameKind) &&
      deck.version == GamePresetLevel.BUNDLED_DECK_VERSION &&
      deck.type == DeckType.WORD &&
      deck.level == level.deckLevel &&
      "TOPIK" in deck.tags &&
      "タイピング" in deck.tags &&
      deck.items.size == GamePresetLevel.BUNDLED_ITEM_COUNT &&
      deck.items.map { it.ko }.toSet().size == deck.items.size &&
      DeckValidator.validate(deck).isEmpty()

  /** Loads the three presets with [load] (asset path → deck) and keeps the valid ones. */
  fun load(gameKind: GameKind, load: (String) -> Deck?): List<GamePreset> =
    GamePresetLevel.entries.mapNotNull { level ->
      load(GamePresetLevel.assetPath(gameKind, level))
        ?.takeIf { isValidPreset(it, gameKind, level) }
        ?.let { GamePreset(gameKind, level, it) }
    }

  /** iOS `PiyoCupDeckLoader`: `flow_topik_beginner` v3, schema-valid. */
  const val PIYO_CUP_DECK_VERSION = 3
  const val PIYO_CUP_ASSET_PATH = "decks/flow/${GameProgressRules.PIYO_CUP_DECK_ID}_v$PIYO_CUP_DECK_VERSION.json"

  fun isValidPiyoCupDeck(deck: Deck): Boolean =
    deck.deckId == GameProgressRules.PIYO_CUP_DECK_ID && deck.version == PIYO_CUP_DECK_VERSION &&
      DeckValidator.validate(deck).isEmpty()

  /** Whether an installed deck can start [gameKind] (iOS `supportsGame`). */
  fun supportsGame(deck: Deck, gameKind: GameKind, meaning: MeaningLookup): Boolean {
    val seed = GameSeed.forValue(deck.deckId)
    return when (gameKind) {
      GameKind.FLOW, GameKind.ACID_RAIN -> deck.items.isNotEmpty()
      GameKind.CHOSEONG -> ChoseongTypingBuilder.rounds(deck.items, 1, seed, meaning).isNotEmpty()
      GameKind.WORD_MATCH -> WordMatchTypingBuilder.rounds(deck.items, 1, seed, meaning).isNotEmpty()
      GameKind.DICTATION -> DictationTypingBuilder.rounds(deck.items, 1, seed).isNotEmpty()
    }
  }

  /**
   * Competition stored with a Flow/Acid Rain record: the weekly cup, the official ranked deck
   * (Flow only, ranked preset v3), or none (iOS `resolvedCompetition`).
   */
  fun resolvedCompetition(
    requested: GameCompetition?,
    gameKind: GameKind,
    deckId: String,
    deckVersion: Int,
    isRankedDeck: (String, Int) -> Boolean,
  ): GameCompetition? {
    if (requested == GameCompetition.WEEKLY_PIYO_CUP) return GameCompetition.WEEKLY_PIYO_CUP
    if (gameKind != GameKind.FLOW || !isRankedDeck(deckId, deckVersion)) return null
    return GameCompetition.OFFICIAL_DECK
  }

  /** Flow/Acid Rain `GameRecord.course` (acid rain uses its kind; Flow uses the deck course). */
  fun flowCourse(gameKind: GameKind, deck: Deck): String =
    if (gameKind == GameKind.ACID_RAIN) gameKind.raw else FlowGameCourse.of(deck).raw
}

/**
 * iOS `GamePresetSessionRandomizer`: bundled presets are reshuffled for every run (never the same
 * order twice in a row); other decks keep their order and a stable seed.
 */
object GamePresetSessionRandomizer {
  /** Test hook (iOS `UITEST_JST_DAY`): keeps preset order deterministic. */
  @Volatile var usesDeterministicOrder = false

  fun isBundledPreset(deckId: String, gameKind: GameKind): Boolean =
    GamePresetLevel.entries.any { it.deckId(gameKind) == deckId }

  fun freshSeed(): ULong = java.security.SecureRandom().nextLong().toULong()

  fun seed(deck: Deck, gameKind: GameKind): ULong =
    if (isBundledPreset(deck.deckId, gameKind) && !usesDeterministicOrder) freshSeed() else GameSeed.forValue(deck.deckId)

  fun shuffledItems(
    deck: Deck,
    gameKind: GameKind,
    seed: ULong = freshSeed(),
    avoiding: List<DeckItem> = emptyList(),
  ): List<DeckItem> {
    if (!isBundledPreset(deck.deckId, gameKind) || usesDeterministicOrder) return deck.items
    val items = deck.items.swiftShuffled(GameRandom(seed)).toMutableList()
    if (items.size > 1 && items.map { it.id } == avoiding.map { it.id }) items.add(items.removeAt(0))
    return items
  }
}

/** iOS `GameResultPresentation`. */
enum class GameResultPresentation(val analyticsValue: String, val modeTitleKey: String, val clearKey: String) {
  FLOW("flow", "game.mode.flow", "game.result.clear"),
  PIYO_CUP("flow", "piyo_cup.title", "piyo_cup.result.clear"),
  ACID_RAIN("acid_rain", "game.mode.acid_rain", "acid_rain.result.clear"),
  CHOSEONG("choseong", "game.mode.choseong", "choseong.result.clear"),
  WORD_MATCH("word_match", "game.mode.word_match", "word_match.result.clear"),
  DICTATION("dictation", "game.mode.dictation", "dictation.result.clear");

  val presetGameKind: GameKind?
    get() = when (this) {
      FLOW -> GameKind.FLOW
      ACID_RAIN -> GameKind.ACID_RAIN
      CHOSEONG -> GameKind.CHOSEONG
      WORD_MATCH -> GameKind.WORD_MATCH
      DICTATION -> GameKind.DICTATION
      PIYO_CUP -> null
    }

  val speedKey: String
    get() = when (this) {
      FLOW, PIYO_CUP, ACID_RAIN -> "game.result.speed"
      else -> "choseong.result.speed"
    }

  val showsInputMode: Boolean get() = this != PIYO_CUP

  /** Share badge key taking the rank (`%@`). */
  val shareBadgeKey: String
    get() = when (this) {
      FLOW -> "result.share.game_badge"
      PIYO_CUP -> "piyo_cup.share.badge"
      ACID_RAIN -> "acid_rain.share.badge"
      CHOSEONG -> "choseong.share.badge"
      WORD_MATCH -> "word_match.share.badge"
      DICTATION -> "dictation.share.badge"
    }

  /** Share caption key: Piyo Cup takes only the score; the others take deck name + score. */
  val shareCaptionKey: String
    get() = when (this) {
      FLOW -> "result.share.game_caption"
      PIYO_CUP -> "piyo_cup.share.caption"
      ACID_RAIN -> "acid_rain.share.caption"
      CHOSEONG -> "choseong.share.caption"
      WORD_MATCH -> "word_match.share.caption"
      DICTATION -> "dictation.share.caption"
    }

  fun presetLevel(deck: Deck): GamePresetLevel? {
    val kind = presetGameKind ?: return null
    return GamePresetLevel.of(deck.deckId, kind)
  }

  fun analyticsDifficulty(deck: Deck): String = if (this == PIYO_CUP) "beginner" else presetLevel(deck)?.raw ?: "custom"

  /** Level badge key (`null` → use the deck name). */
  fun resultLevelTitleKey(deck: Deck): String? {
    if (this == PIYO_CUP) return "piyo_cup.weekly_badge"
    val kind = presetGameKind ?: return null
    return presetLevel(deck)?.titleKey(kind)
  }

  companion object {
    fun of(gameKind: GameKind, isPiyoCup: Boolean = false): GameResultPresentation = when {
      isPiyoCup -> PIYO_CUP
      gameKind == GameKind.FLOW -> FLOW
      gameKind == GameKind.ACID_RAIN -> ACID_RAIN
      gameKind == GameKind.CHOSEONG -> CHOSEONG
      gameKind == GameKind.WORD_MATCH -> WORD_MATCH
      else -> DICTATION
    }

    /** Rank colour role: S → orange, A → accent, B → secondary, else muted. */
    fun rankTint(rank: String): RankTint = when (rank) {
      "S" -> RankTint.ORANGE
      "A" -> RankTint.ACCENT
      "B" -> RankTint.SECONDARY
      else -> RankTint.MUTED
    }
  }
}

enum class RankTint { ORANGE, ACCENT, SECONDARY, MUTED }

/** Analytics `deck_source` for a game session (iOS `analyticsDeckSource`). */
object GameAnalytics {
  /** [installedSourceRaw]: `bundle` / `remote` / `imported` / `created` or null when not installed. */
  fun deckSource(isPresetOrCup: Boolean, installedSourceRaw: String?): String {
    if (isPresetOrCup) return "bundled"
    return when (installedSourceRaw) {
      "bundle" -> "bundled"
      "remote" -> "catalog"
      "imported" -> "imported"
      "created" -> "created"
      else -> "unknown"
    }
  }
}
