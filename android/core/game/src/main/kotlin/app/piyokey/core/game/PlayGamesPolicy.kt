package app.piyokey.core.game

/** Stable logical identifiers. Play Console resource ids are injected at build time. */
enum class PlayGamesLeaderboardKey(val storageKey: String) {
  FLOW_BEGINNER("flow_beginner"),
  FLOW_INTERMEDIATE("flow_intermediate"),
  FLOW_ADVANCED("flow_advanced"),
  ACID_RAIN_BEGINNER("acid_rain_beginner"),
  ACID_RAIN_INTERMEDIATE("acid_rain_intermediate"),
  ACID_RAIN_ADVANCED("acid_rain_advanced"),
  CHOSEONG_BEGINNER("choseong_beginner"),
  CHOSEONG_INTERMEDIATE("choseong_intermediate"),
  CHOSEONG_ADVANCED("choseong_advanced"),
  WORD_MATCH_BEGINNER("word_match_beginner"),
  WORD_MATCH_INTERMEDIATE("word_match_intermediate"),
  WORD_MATCH_ADVANCED("word_match_advanced"),
  DICTATION_BEGINNER("dictation_beginner"),
  DICTATION_INTERMEDIATE("dictation_intermediate"),
  DICTATION_ADVANCED("dictation_advanced"),
}

enum class PlayGamesAchievementKey(
  val storageKey: String,
  val target: Long,
) {
  CHAPTER_ONE("chapter_one", 1),
  CHAPTER_THREE("chapter_three", 1),
  CHAPTER_SIX("chapter_six", 1),
  JAMO_12000("jamo_12000", 12_000),
  STREAK_30("streak_30", 30),
}

data class PlayGamesResultIdentity(
  val mode: String,
  val deckId: String,
  val inputMode: String,
  val isWeeklyCup: Boolean = false,
)

object PlayGamesPolicy {
  const val BUILTIN_INPUT = "builtin"

  private val boardsByIdentity: Map<Pair<String, String>, PlayGamesLeaderboardKey> = buildMap {
    fun add(mode: String, prefix: String, keys: List<PlayGamesLeaderboardKey>) {
      listOf("beginner", "intermediate", "advanced").zip(keys).forEach { (level, key) ->
        put(mode to "${prefix}_topik_$level", key)
      }
    }
    add("flow", "flow", listOf(
      PlayGamesLeaderboardKey.FLOW_BEGINNER,
      PlayGamesLeaderboardKey.FLOW_INTERMEDIATE,
      PlayGamesLeaderboardKey.FLOW_ADVANCED,
    ))
    add("acid_rain", "acid_rain", listOf(
      PlayGamesLeaderboardKey.ACID_RAIN_BEGINNER,
      PlayGamesLeaderboardKey.ACID_RAIN_INTERMEDIATE,
      PlayGamesLeaderboardKey.ACID_RAIN_ADVANCED,
    ))
    add("choseong", "choseong", listOf(
      PlayGamesLeaderboardKey.CHOSEONG_BEGINNER,
      PlayGamesLeaderboardKey.CHOSEONG_INTERMEDIATE,
      PlayGamesLeaderboardKey.CHOSEONG_ADVANCED,
    ))
    add("word_match", "word_match", listOf(
      PlayGamesLeaderboardKey.WORD_MATCH_BEGINNER,
      PlayGamesLeaderboardKey.WORD_MATCH_INTERMEDIATE,
      PlayGamesLeaderboardKey.WORD_MATCH_ADVANCED,
    ))
    add("dictation", "dictation", listOf(
      PlayGamesLeaderboardKey.DICTATION_BEGINNER,
      PlayGamesLeaderboardKey.DICTATION_INTERMEDIATE,
      PlayGamesLeaderboardKey.DICTATION_ADVANCED,
    ))
  }

  fun leaderboard(identity: PlayGamesResultIdentity): PlayGamesLeaderboardKey? = when {
    identity.inputMode != BUILTIN_INPUT -> null
    identity.isWeeklyCup -> null
    else -> boardsByIdentity[identity.mode to identity.deckId]
  }

  fun allMappedIdentities(): Map<Pair<String, String>, PlayGamesLeaderboardKey> = boardsByIdentity.toMap()
}
