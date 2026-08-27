package app.piyokey.core.retention

import app.piyokey.core.deckkit.DeckItem
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

private val JST: ZoneId = ZoneId.of("Asia/Tokyo")

data class CurriculumItem(val id: String, val ko: String)

data class CurriculumStage(
  val id: String,
  val chapterNumber: Int,
  val stageNumber: Int,
  val items: List<CurriculumItem>,
)

data class CurriculumChapter(val number: Int, val stages: List<CurriculumStage>)

object CurriculumCatalog {
  val chapters: List<CurriculumChapter> = listOf(
    chapter(1, "chapter_1_basic_consonants", listOf("ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅎ")),
    chapter(2, "chapter_2_basic_vowels", listOf("ㅏ", "ㅓ", "ㅗ", "ㅜ", "ㅡ", "ㅣ", "ㅐ", "ㅔ", "ㅑ", "ㅕ")),
    chapter(3, "chapter_3_syllable_building", listOf("가", "나", "다", "라", "마", "바", "사", "아", "자", "하")),
    chapter(4, "chapter_4_batchim", listOf("간", "난", "달", "밤", "밥", "산", "강", "문", "공", "집")),
    chapter(5, "chapter_5_words", listOf("사랑", "친구", "학교", "음식", "여행", "사진", "음악", "선물", "오늘", "응원")),
    chapter(
      6,
      "chapter_6_spacing",
      listOf("좋은 아침", "내 친구", "한국 음식", "매운 음식", "여행 사진", "작은 선물", "좋은 음악", "같이 가요", "물 주세요", "또 만나요"),
      listOf(
        "chapter_6_sentences" to listOf(
          "안녕하세요", "감사합니다", "사랑해요", "만나서 반가워요", "오늘도 힘내요",
          "정말 멋있어요", "같이 가요", "사진을 찍어요", "음악을 들어요", "좋은 하루 보내요",
        ),
      ),
    ),
  )

  val stages: List<CurriculumStage> = chapters.flatMap(CurriculumChapter::stages)

  fun stage(id: String): CurriculumStage? = stages.firstOrNull { it.id == id }

  private fun chapter(
    number: Int,
    firstId: String,
    firstTargets: List<String>,
    following: List<Pair<String, List<String>>> = emptyList(),
  ): CurriculumChapter {
    val definitions = listOf(firstId to firstTargets) + following
    return CurriculumChapter(
      number,
      definitions.mapIndexed { index, (id, targets) ->
        CurriculumStage(
          id = id,
          chapterNumber = number,
          stageNumber = index + 1,
          items = targets.mapIndexed { itemIndex, target ->
            CurriculumItem("${id}_${itemIndex + 1}", target)
          },
        )
      },
    )
  }
}

object CurriculumPolicy {
  const val clearAccuracy = 80.0

  fun stars(accuracy: Double, charactersPerMinute: Double): Int = when {
    accuracy < clearAccuracy -> 0
    accuracy >= 97.0 && charactersPerMinute >= 60.0 -> 3
    accuracy >= 90.0 && charactersPerMinute >= 40.0 -> 2
    else -> 1
  }

  fun isUnlocked(
    stage: CurriculumStage,
    completedStageIds: Set<String>,
    stages: List<CurriculumStage> = CurriculumCatalog.stages,
  ): Boolean {
    if (stage.chapterNumber >= 5) return true
    val index = stages.indexOfFirst { it.id == stage.id }
    return index == 0 || index > 0 && stages[index - 1].id in completedStageIds
  }

  fun isChapterCompleted(chapter: CurriculumChapter, completedStageIds: Set<String>): Boolean =
    if (chapter.number >= 5) chapter.stages.any { it.id in completedStageIds }
    else chapter.stages.all { it.id in completedStageIds }
}

@JvmInline
value class JstDay(val value: String) : Comparable<JstDay> {
  init {
    require(runCatching { LocalDate.parse(value) }.getOrNull()?.toString() == value) {
      "Expected a valid YYYY-MM-DD day"
    }
  }

  val date: LocalDate get() = LocalDate.parse(value)
  fun plusDays(days: Long): JstDay = JstDay(date.plusDays(days).toString())
  override fun compareTo(other: JstDay): Int = value.compareTo(other.value)

  companion object {
    fun fromEpochMillis(epochMillis: Long): JstDay =
      JstDay(Instant.ofEpochMilli(epochMillis).atZone(JST).toLocalDate().toString())
  }
}

enum class RetentionActivity(val storageValue: String) {
  CURRICULUM("curriculum"),
  GAME("game"),
  DAILY_CHALLENGE("daily_challenge"),
}

enum class StampState { COMPLETED, MISSED, TODAY_PENDING, UPCOMING }

data class StampDay(val day: JstDay, val state: StampState)
data class Streak(val current: Int, val longest: Int)

object RetentionPolicy {
  val rewardThresholds = setOf(3, 5, 7)

  fun week(today: JstDay, completedDays: Set<JstDay>): List<StampDay> {
    val monday = today.plusDays(-(today.date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    return (0L..6L).map { offset ->
      val day = monday.plusDays(offset)
      val state = when {
        day in completedDays -> StampState.COMPLETED
        day < today -> StampState.MISSED
        day == today -> StampState.TODAY_PENDING
        else -> StampState.UPCOMING
      }
      StampDay(day, state)
    }
  }

  fun streak(completedDays: Set<JstDay>, today: JstDay): Streak {
    val anchor = when {
      today in completedDays -> today
      today.plusDays(-1) in completedDays -> today.plusDays(-1)
      else -> null
    }
    var current = 0
    var cursor = anchor
    while (cursor != null && cursor in completedDays) {
      current += 1
      cursor = cursor.plusDays(-1)
    }

    var longest = 0
    var running = 0
    var previous: JstDay? = null
    completedDays.sorted().forEach { day ->
      running = if (previous?.plusDays(1) == day) running + 1 else 1
      longest = max(longest, running)
      previous = day
    }
    return Streak(current, longest)
  }

  fun newlyUnlockedRewards(completedDays: Set<JstDay>, existing: Set<Int>): Set<Int> {
    val bestWeek = completedDays.groupBy { day ->
      day.plusDays(-(day.date.dayOfWeek.value - 1).toLong())
    }.values.maxOfOrNull { it.size } ?: 0
    return rewardThresholds.filterTo(mutableSetOf()) { it <= bestWeek && it !in existing }
  }

  fun encouragementIndex(today: JstDay, completedDays: Set<JstDay>): Int {
    val indices = when {
      today in completedDays -> listOf(5, 6)
      completedDays.none { it < today } -> listOf(0, 1, 2, 4)
      completedDays.filter { it < today }.maxOrNull() == today.plusDays(-1) -> listOf(3, 7)
      else -> listOf(8, 9)
    }
    return indices[Math.floorMod(today.date.toEpochDay(), indices.size.toLong()).toInt()]
  }
}

object DailyChallengePolicy {
  private val pool: List<CurriculumItem> = CurriculumCatalog.chapters
    .filter { it.number >= 5 }
    .flatMap(CurriculumChapter::stages)
    .flatMap(CurriculumStage::items)
    .distinctBy(CurriculumItem::ko)

  fun items(day: JstDay, count: Int = 5, goalSalt: Int = 0): List<CurriculumItem> {
    require(count in 1..pool.size)
    val start = Math.floorMod(day.date.toEpochDay() + goalSalt * 11L, pool.size.toLong()).toInt()
    return (0 until count).map { pool[(start + it * 7) % pool.size] }
  }
}

data class ReviewItem(
  val item: DeckItem,
  val sourceDeckId: String,
  val missCount: Int,
  val consecutivePerfect: Int,
  val addedAtEpochMillis: Long,
  val graduatedAtEpochMillis: Long? = null,
) {
  val id: String get() = "$sourceDeckId::${item.id}"
  val isActive: Boolean get() = graduatedAtEpochMillis == null
}

enum class ReviewMutation { ADDED, UPDATED, GRADUATED, REMOVED, UNCHANGED }

data class ReviewReduction(val item: ReviewItem?, val mutation: ReviewMutation)

object ReviewPolicy {
  const val graduationThreshold = 3

  fun recordMistake(existing: ReviewItem?, item: DeckItem, sourceDeckId: String, at: Long): ReviewReduction {
    val updated = existing?.copy(
      item = item,
      missCount = existing.missCount + 1,
      consecutivePerfect = 0,
      graduatedAtEpochMillis = null,
    ) ?: ReviewItem(item, sourceDeckId, 1, 0, at)
    return ReviewReduction(updated, if (existing == null) ReviewMutation.ADDED else ReviewMutation.UPDATED)
  }

  fun addManually(existing: ReviewItem?, item: DeckItem, sourceDeckId: String, at: Long): ReviewReduction {
    if (existing?.isActive == true) return ReviewReduction(existing, ReviewMutation.UNCHANGED)
    val updated = existing?.copy(item = item, consecutivePerfect = 0, graduatedAtEpochMillis = null)
      ?: ReviewItem(item, sourceDeckId, 0, 0, at)
    return ReviewReduction(updated, if (existing == null) ReviewMutation.ADDED else ReviewMutation.UPDATED)
  }

  fun recordPerfect(existing: ReviewItem?, at: Long): ReviewReduction {
    if (existing?.isActive != true) return ReviewReduction(existing, ReviewMutation.UNCHANGED)
    val consecutive = (existing.consecutivePerfect + 1).coerceAtMost(graduationThreshold)
    val graduated = consecutive == graduationThreshold
    return ReviewReduction(
      existing.copy(
        consecutivePerfect = consecutive,
        graduatedAtEpochMillis = if (graduated) at else null,
      ),
      if (graduated) ReviewMutation.GRADUATED else ReviewMutation.UPDATED,
    )
  }
}
