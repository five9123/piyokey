package app.piyokey.android.platform.games

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.min

/*
 * Pure ranking/routing rules ported from iOS `Core/GameCenter/GameCenterService.swift`.
 * No Play Games imports: everything here is JVM-unit-testable.
 */

/**
 * Logical leaderboards. [iosId] matches the App Store Connect ID; [resourceKey] is the suffix of
 * the injected Play Games string resource `piyokey_pgs_<key>` (see `playGamesKeys` in app/build.gradle.kts).
 */
enum class PiyoLeaderboard(val iosId: String, val resourceKey: String) {
  FLOW_BEGINNER("piyokey.v5.flow.beginner", "flow_beginner"),
  FLOW_INTERMEDIATE("piyokey.v4.flow.intermediate", "flow_intermediate"),
  FLOW_ADVANCED("piyokey.v4.flow.advanced", "flow_advanced"),
  ACID_RAIN_BEGINNER("piyokey.v3.acid_rain.beginner", "acid_rain_beginner"),
  ACID_RAIN_INTERMEDIATE("piyokey.v3.acid_rain.intermediate", "acid_rain_intermediate"),
  ACID_RAIN_ADVANCED("piyokey.v3.acid_rain.advanced", "acid_rain_advanced"),
  CHOSEONG_BEGINNER("piyokey.v3.choseong.beginner", "choseong_beginner"),
  CHOSEONG_INTERMEDIATE("piyokey.v3.choseong.intermediate", "choseong_intermediate"),
  CHOSEONG_ADVANCED("piyokey.v3.choseong.advanced", "choseong_advanced"),
  WORD_MATCH_BEGINNER("piyokey.v4.word_match.beginner", "word_match_beginner"),
  WORD_MATCH_INTERMEDIATE("piyokey.v4.word_match.intermediate", "word_match_intermediate"),
  WORD_MATCH_ADVANCED("piyokey.v4.word_match.advanced", "word_match_advanced"),
  DICTATION_BEGINNER("piyokey.v3.dictation.beginner", "dictation_beginner"),
  DICTATION_INTERMEDIATE("piyokey.v3.dictation.intermediate", "dictation_intermediate"),
  DICTATION_ADVANCED("piyokey.v3.dictation.advanced", "dictation_advanced"),
  WEEKLY_PIYO_CUP("piyokey.v4.cup.weekly.flow", "cup_weekly_flow");

  val isWeekly: Boolean get() = this == WEEKLY_PIYO_CUP

  companion object {
    fun fromIosId(id: String) = entries.firstOrNull { it.iosId == id }
    fun fromResourceKey(key: String) = entries.firstOrNull { it.resourceKey == key }
  }
}

/** iOS `GameCenterGrowthAchievement`. */
enum class PiyoAchievement(val iosId: String, val resourceKey: String) {
  HATCHING("piyokey.growth.hatching", "growth_hatching"),
  CHICK("piyokey.growth.chick", "growth_chick"),
  ROOSTER("piyokey.growth.rooster", "growth_rooster"),
  TYPED_JAMO("piyokey.growth.typed_12000", "growth_typed_12000"),
  STREAK("piyokey.growth.streak_30", "growth_streak_30");

  fun percentComplete(snapshot: GrowthSnapshot): Double {
    val progress = when (this) {
      HATCHING -> snapshot.completedChapterCount.toDouble()
      CHICK -> snapshot.completedChapterCount / 3.0
      ROOSTER -> snapshot.completedChapterCount / 6.0
      TYPED_JAMO -> snapshot.typedJamoCount / 12_000.0
      STREAK -> snapshot.longestStreak / 30.0
    }
    return min(max(progress * 100, 0.0), 100.0)
  }

  companion object {
    fun progress(snapshot: GrowthSnapshot): Map<PiyoAchievement, Double> =
      entries.associateWith { it.percentComplete(snapshot) }

    fun unlocked(snapshot: GrowthSnapshot): Set<PiyoAchievement> =
      entries.filterTo(mutableSetOf()) { it.percentComplete(snapshot) >= 100.0 }
  }
}

/** iOS `GameCenterGrowthSnapshot`. */
data class GrowthSnapshot(val completedChapterCount: Int, val typedJamoCount: Int, val longestStreak: Int) {
  companion object {
    val EMPTY = GrowthSnapshot(0, 0, 0)
  }
}

/** Minimal game record view needed for ranking (fields of iOS `GameRecord`). */
data class RankedRecord(
  val id: String,
  /** `GameRecord.mode`: only `game` records are ranked (not practice/spacing). */
  val isGameMode: Boolean,
  val deckId: String,
  val deckVersion: Int?,
  /** `GameRecord.competition` raw value (e.g. `weekly_piyo_cup_v1`) or null. */
  val competition: String?,
  val score: Int,
  val playedAt: Instant,
)

/** iOS `GameCenterRankedDeck`: bundled preset decks v3 only. */
data class RankedDeck(val deckId: String, val version: Int, val leaderboard: PiyoLeaderboard)

object PlayGamesRules {
  const val WEEKLY_PIYO_CUP_COMPETITION = "weekly_piyo_cup_v1"
  const val PIYO_CUP_DECK_ID = "flow_topik_beginner"

  val rankedDecks: List<RankedDeck> = listOf(
    RankedDeck("flow_topik_beginner", 3, PiyoLeaderboard.FLOW_BEGINNER),
    RankedDeck("flow_topik_intermediate", 3, PiyoLeaderboard.FLOW_INTERMEDIATE),
    RankedDeck("flow_topik_advanced", 3, PiyoLeaderboard.FLOW_ADVANCED),
    RankedDeck("acid_rain_topik_beginner", 3, PiyoLeaderboard.ACID_RAIN_BEGINNER),
    RankedDeck("acid_rain_topik_intermediate", 3, PiyoLeaderboard.ACID_RAIN_INTERMEDIATE),
    RankedDeck("acid_rain_topik_advanced", 3, PiyoLeaderboard.ACID_RAIN_ADVANCED),
    RankedDeck("choseong_topik_beginner", 3, PiyoLeaderboard.CHOSEONG_BEGINNER),
    RankedDeck("choseong_topik_intermediate", 3, PiyoLeaderboard.CHOSEONG_INTERMEDIATE),
    RankedDeck("choseong_topik_advanced", 3, PiyoLeaderboard.CHOSEONG_ADVANCED),
    RankedDeck("word_match_topik_beginner", 3, PiyoLeaderboard.WORD_MATCH_BEGINNER),
    RankedDeck("word_match_topik_intermediate", 3, PiyoLeaderboard.WORD_MATCH_INTERMEDIATE),
    RankedDeck("word_match_topik_advanced", 3, PiyoLeaderboard.WORD_MATCH_ADVANCED),
    RankedDeck("dictation_topik_beginner", 3, PiyoLeaderboard.DICTATION_BEGINNER),
    RankedDeck("dictation_topik_intermediate", 3, PiyoLeaderboard.DICTATION_INTERMEDIATE),
    RankedDeck("dictation_topik_advanced", 3, PiyoLeaderboard.DICTATION_ADVANCED),
  )

  fun matching(deckId: String, version: Int?): RankedDeck? =
    version?.let { v -> rankedDecks.firstOrNull { it.deckId == deckId && it.version == v } }

  fun isEligible(deckId: String, version: Int) = matching(deckId, version) != null

  /**
   * Boards a record is submitted to. Every input mode is eligible. A weekly Piyo Cup record goes
   * only to the cup board and only for [PIYO_CUP_DECK_ID]; a regular record goes to its deck board.
   */
  fun leaderboards(record: RankedRecord): List<PiyoLeaderboard> {
    if (!record.isGameMode) return emptyList()
    val deck = matching(record.deckId, record.deckVersion) ?: return emptyList()
    if (record.competition == WEEKLY_PIYO_CUP_COMPETITION) {
      return if (deck.deckId == PIYO_CUP_DECK_ID) listOf(PiyoLeaderboard.WEEKLY_PIYO_CUP) else emptyList()
    }
    return listOf(deck.leaderboard)
  }

  /** Best kept score per board; cup scores count only inside the current JST week. */
  fun bestScores(records: List<RankedRecord>, asOf: Instant = Instant.now()): Map<PiyoLeaderboard, Int> {
    val result = mutableMapOf<PiyoLeaderboard, Int>()
    for (record in records) {
      for (board in leaderboards(record)) {
        if (board.isWeekly && !PiyoCupWeek.contains(record.playedAt, asOf)) continue
        result[board] = max(result[board] ?: 0, record.score)
      }
    }
    return result
  }

  /** Rank shown for a record: the best (lowest) rank of its available boards. */
  fun rank(record: RankedRecord, ranks: Map<PiyoLeaderboard, Long>, available: Set<PiyoLeaderboard>): Long? =
    leaderboards(record).filter { it in available }.mapNotNull { ranks[it] }.minOrNull()
}

/** iOS `PiyoCupWeek`: weeks start Monday 00:00 Asia/Tokyo. */
object PiyoCupWeek {
  val JST: ZoneId = ZoneId.of("Asia/Tokyo")

  fun start(containing: Instant): Instant =
    containing.atZone(JST).toLocalDate()
      .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
      .atStartOfDay(JST).toInstant()

  fun contains(date: Instant, weekContaining: Instant): Boolean {
    val start = start(weekContaining)
    val end = start.atZone(JST).plusDays(7).toInstant()
    return !date.isBefore(start) && date.isBefore(end)
  }
}

/** Per-board submission states (iOS `GameCenterService.SubmissionState`). */
enum class SubmissionState { IDLE, SUBMITTING, CONFIRMING, CONFIRMED, UNCONFIRMED, FAILED }

/**
 * Pure submission policy mirroring iOS: submit only a kept best that is higher than what was
 * accepted or is in flight; confirm by re-reading the player's score (≥ kept best); retry a
 * failed or unconfirmed submission at most [MAX_RETRIES] times with linear backoff.
 */
object SubmissionPolicy {
  const val MAX_RETRIES = 2
  const val REQUEST_TIMEOUT_MILLIS = 10_000L
  const val RETRY_DELAY_MILLIS = 3_000L

  /** Read-back delays after an accepted submit: immediately, then 3 s, 6 s, 9 s (iOS rank refresh). */
  val confirmationDelaysMillis: List<Long> = listOf(0L, RETRY_DELAY_MILLIS, RETRY_DELAY_MILLIS * 2, RETRY_DELAY_MILLIS * 3)

  fun shouldSubmit(score: Int, submitted: Int?, submitting: Int?): Boolean =
    score >= 0 && score > max(submitted ?: -1, submitting ?: -1)

  fun isConfirmed(serverScore: Long?, keptBest: Int): Boolean = serverScore != null && serverScore >= keptBest

  fun canRetry(retryCount: Int) = retryCount < MAX_RETRIES

  fun retryDelayMillis(attempt: Int) = RETRY_DELAY_MILLIS * attempt
}

/**
 * Deterministic per-board state machine used by the Play Games adapter (and tests). Each call
 * returns the new state; side effects (network, delays) are the caller's job.
 */
class SubmissionTracker(val keptBest: Int) {
  var state: SubmissionState = SubmissionState.IDLE
    private set
  var retryCount = 0
    private set

  fun begin(): SubmissionState { state = SubmissionState.SUBMITTING; return state }

  fun submitFinished(success: Boolean): SubmissionState {
    state = if (success) SubmissionState.CONFIRMING else SubmissionState.FAILED
    return state
  }

  /** A read-back result; stays CONFIRMING until a read meets the kept best. */
  fun readBack(serverScore: Long?): SubmissionState {
    if (state == SubmissionState.CONFIRMING && SubmissionPolicy.isConfirmed(serverScore, keptBest)) {
      state = SubmissionState.CONFIRMED
      retryCount = 0
    }
    return state
  }

  /** All read-backs exhausted without confirmation. */
  fun confirmationExhausted(): SubmissionState {
    if (state == SubmissionState.CONFIRMING) state = SubmissionState.UNCONFIRMED
    return state
  }

  /** Consumes one retry if allowed; returns the attempt number (1-based) or null when capped. */
  fun nextRetry(): Int? {
    if (state != SubmissionState.FAILED && state != SubmissionState.UNCONFIRMED) return null
    if (!SubmissionPolicy.canRetry(retryCount)) return null
    retryCount += 1
    return retryCount
  }
}
