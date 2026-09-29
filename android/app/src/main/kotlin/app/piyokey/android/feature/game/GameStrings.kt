package app.piyokey.android.feature.game

import app.piyokey.android.R
import app.piyokey.core.domain.GameKind
import app.piyokey.core.domain.game.GamePresetLevel

/** Resource ids of the per-game iOS keys `game.<kind>.*` (no runtime `getIdentifier`). */
internal object GameStrings {
  fun presetTitle(kind: GameKind, level: GamePresetLevel): Int = when (kind) {
    GameKind.FLOW -> when (level) {
      GamePresetLevel.BEGINNER -> R.string.game_flow_preset_beginner_title
      GamePresetLevel.INTERMEDIATE -> R.string.game_flow_preset_intermediate_title
      GamePresetLevel.ADVANCED -> R.string.game_flow_preset_advanced_title
    }
    GameKind.ACID_RAIN -> when (level) {
      GamePresetLevel.BEGINNER -> R.string.game_acid_rain_preset_beginner_title
      GamePresetLevel.INTERMEDIATE -> R.string.game_acid_rain_preset_intermediate_title
      GamePresetLevel.ADVANCED -> R.string.game_acid_rain_preset_advanced_title
    }
    GameKind.CHOSEONG -> when (level) {
      GamePresetLevel.BEGINNER -> R.string.game_choseong_preset_beginner_title
      GamePresetLevel.INTERMEDIATE -> R.string.game_choseong_preset_intermediate_title
      GamePresetLevel.ADVANCED -> R.string.game_choseong_preset_advanced_title
    }
    GameKind.WORD_MATCH -> when (level) {
      GamePresetLevel.BEGINNER -> R.string.game_word_match_preset_beginner_title
      GamePresetLevel.INTERMEDIATE -> R.string.game_word_match_preset_intermediate_title
      GamePresetLevel.ADVANCED -> R.string.game_word_match_preset_advanced_title
    }
    GameKind.DICTATION -> when (level) {
      GamePresetLevel.BEGINNER -> R.string.game_dictation_preset_beginner_title
      GamePresetLevel.INTERMEDIATE -> R.string.game_dictation_preset_intermediate_title
      GamePresetLevel.ADVANCED -> R.string.game_dictation_preset_advanced_title
    }
  }

  fun presetDifficulty(kind: GameKind, level: GamePresetLevel): Int = when (kind) {
    GameKind.FLOW -> when (level) {
      GamePresetLevel.BEGINNER -> R.string.game_flow_preset_beginner_difficulty
      GamePresetLevel.INTERMEDIATE -> R.string.game_flow_preset_intermediate_difficulty
      GamePresetLevel.ADVANCED -> R.string.game_flow_preset_advanced_difficulty
    }
    GameKind.ACID_RAIN -> when (level) {
      GamePresetLevel.BEGINNER -> R.string.game_acid_rain_preset_beginner_difficulty
      GamePresetLevel.INTERMEDIATE -> R.string.game_acid_rain_preset_intermediate_difficulty
      GamePresetLevel.ADVANCED -> R.string.game_acid_rain_preset_advanced_difficulty
    }
    GameKind.CHOSEONG -> when (level) {
      GamePresetLevel.BEGINNER -> R.string.game_choseong_preset_beginner_difficulty
      GamePresetLevel.INTERMEDIATE -> R.string.game_choseong_preset_intermediate_difficulty
      GamePresetLevel.ADVANCED -> R.string.game_choseong_preset_advanced_difficulty
    }
    GameKind.WORD_MATCH -> when (level) {
      GamePresetLevel.BEGINNER -> R.string.game_word_match_preset_beginner_difficulty
      GamePresetLevel.INTERMEDIATE -> R.string.game_word_match_preset_intermediate_difficulty
      GamePresetLevel.ADVANCED -> R.string.game_word_match_preset_advanced_difficulty
    }
    GameKind.DICTATION -> when (level) {
      GamePresetLevel.BEGINNER -> R.string.game_dictation_preset_beginner_difficulty
      GamePresetLevel.INTERMEDIATE -> R.string.game_dictation_preset_intermediate_difficulty
      GamePresetLevel.ADVANCED -> R.string.game_dictation_preset_advanced_difficulty
    }
  }

  fun presetDetail(kind: GameKind, level: GamePresetLevel): Int = when (kind) {
    GameKind.FLOW -> when (level) {
      GamePresetLevel.BEGINNER -> R.string.game_flow_preset_beginner_detail
      GamePresetLevel.INTERMEDIATE -> R.string.game_flow_preset_intermediate_detail
      GamePresetLevel.ADVANCED -> R.string.game_flow_preset_advanced_detail
    }
    GameKind.ACID_RAIN -> when (level) {
      GamePresetLevel.BEGINNER -> R.string.game_acid_rain_preset_beginner_detail
      GamePresetLevel.INTERMEDIATE -> R.string.game_acid_rain_preset_intermediate_detail
      GamePresetLevel.ADVANCED -> R.string.game_acid_rain_preset_advanced_detail
    }
    GameKind.CHOSEONG -> when (level) {
      GamePresetLevel.BEGINNER -> R.string.game_choseong_preset_beginner_detail
      GamePresetLevel.INTERMEDIATE -> R.string.game_choseong_preset_intermediate_detail
      GamePresetLevel.ADVANCED -> R.string.game_choseong_preset_advanced_detail
    }
    GameKind.WORD_MATCH -> when (level) {
      GamePresetLevel.BEGINNER -> R.string.game_word_match_preset_beginner_detail
      GamePresetLevel.INTERMEDIATE -> R.string.game_word_match_preset_intermediate_detail
      GamePresetLevel.ADVANCED -> R.string.game_word_match_preset_advanced_detail
    }
    GameKind.DICTATION -> when (level) {
      GamePresetLevel.BEGINNER -> R.string.game_dictation_preset_beginner_detail
      GamePresetLevel.INTERMEDIATE -> R.string.game_dictation_preset_intermediate_detail
      GamePresetLevel.ADVANCED -> R.string.game_dictation_preset_advanced_detail
    }
  }

  fun selectionIntro(kind: GameKind): Int = when (kind) {
    GameKind.FLOW -> R.string.game_flow_selection_intro
    GameKind.ACID_RAIN -> R.string.game_acid_rain_selection_intro
    GameKind.CHOSEONG -> R.string.game_choseong_selection_intro
    GameKind.WORD_MATCH -> R.string.game_word_match_selection_intro
    GameKind.DICTATION -> R.string.game_dictation_selection_intro
  }

  fun addedDecksTitle(kind: GameKind): Int = when (kind) {
    GameKind.FLOW -> R.string.game_flow_added_decks_title
    GameKind.ACID_RAIN -> R.string.game_acid_rain_added_decks_title
    GameKind.CHOSEONG -> R.string.game_choseong_added_decks_title
    GameKind.WORD_MATCH -> R.string.game_word_match_added_decks_title
    GameKind.DICTATION -> R.string.game_dictation_added_decks_title
  }

  fun addDeckTitle(kind: GameKind): Int = when (kind) {
    GameKind.FLOW -> R.string.game_flow_add_deck_title
    GameKind.ACID_RAIN -> R.string.game_acid_rain_add_deck_title
    GameKind.CHOSEONG -> R.string.game_choseong_add_deck_title
    GameKind.WORD_MATCH -> R.string.game_word_match_add_deck_title
    GameKind.DICTATION -> R.string.game_dictation_add_deck_title
  }

  fun addDeckDetail(kind: GameKind): Int = when (kind) {
    GameKind.FLOW -> R.string.game_flow_add_deck_detail
    GameKind.ACID_RAIN -> R.string.game_acid_rain_add_deck_detail
    GameKind.CHOSEONG -> R.string.game_choseong_add_deck_detail
    GameKind.WORD_MATCH -> R.string.game_word_match_add_deck_detail
    GameKind.DICTATION -> R.string.game_dictation_add_deck_detail
  }

  fun addDeckAction(kind: GameKind): Int = when (kind) {
    GameKind.FLOW -> R.string.game_flow_add_deck_action
    GameKind.ACID_RAIN -> R.string.game_acid_rain_add_deck_action
    GameKind.CHOSEONG -> R.string.game_choseong_add_deck_action
    GameKind.WORD_MATCH -> R.string.game_word_match_add_deck_action
    GameKind.DICTATION -> R.string.game_dictation_add_deck_action
  }

  fun presetWordCount(kind: GameKind): Int = when (kind) {
    GameKind.FLOW -> R.plurals.game_flow_preset_word_count
    GameKind.ACID_RAIN -> R.plurals.game_acid_rain_preset_word_count
    GameKind.CHOSEONG -> R.plurals.game_choseong_preset_word_count
    GameKind.WORD_MATCH -> R.plurals.game_word_match_preset_word_count
    GameKind.DICTATION -> R.plurals.game_dictation_preset_word_count
  }
}
