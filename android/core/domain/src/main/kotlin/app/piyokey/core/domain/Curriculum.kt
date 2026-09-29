package app.piyokey.core.domain

import java.security.MessageDigest

/**
 * One curriculum target. Clues are localization KEYS: [readingKey]/[meaningKey] exist in the UI
 * string table (`R.string.<key with . → _>`), and the Korean learning values in
 * `KoreanLearningContent` (`R.string.ko_<key>`). The app resolves them (see `data/curriculum`).
 */
data class CurriculumItem(
  val id: String,
  val ko: String,
  val readingKey: String,
  val meaningKey: String,
) {
  /** Bundled pronunciation path (`audio/ko_<sha256 prefix>.mp3`, iOS `BundledPronunciationAudio`). */
  val audioRelativePath: String get() = pronunciationAudioPath(ko)
}

data class CurriculumStage(
  val id: String,
  val chapterNumber: Int,
  val stageNumber: Int,
  val titleKey: String,
  val detailKey: String,
  /** SF Symbol name on iOS; Android maps it to an icon. */
  val symbol: String,
  val items: List<CurriculumItem>,
) {
  /** Review-deck source id for mistakes made in this stage. */
  val sourceDeckId: String get() = "curriculum::$id"
}

data class CurriculumChapter(
  val number: Int,
  val titleKey: String,
  val stages: List<CurriculumStage>,
) {
  val id: Int get() = number
}

/** iOS `CurriculumCatalog`: 6 chapters, 12 stages (ch.5 has 3 stages, ch.6 has 5), 10 items each. */
object CurriculumCatalog {
  private class StageDef(val id: String, val symbol: String, val targets: List<String>)

  val chapters: List<CurriculumChapter> = listOf(
    chapter(
      1, StageDef("chapter_1_basic_consonants", "character.book.closed.fill", listOf("ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅎ")),
    ),
    chapter(
      2, StageDef("chapter_2_basic_vowels", "textformat.abc", listOf("ㅏ", "ㅓ", "ㅗ", "ㅜ", "ㅡ", "ㅣ", "ㅐ", "ㅔ", "ㅑ", "ㅕ")),
    ),
    chapter(
      3, StageDef("chapter_3_syllable_building", "square.grid.3x3.fill", listOf("가", "나", "다", "라", "마", "바", "사", "아", "자", "하")),
    ),
    chapter(
      4, StageDef("chapter_4_batchim", "rectangle.bottomhalf.filled", listOf("간", "난", "달", "밤", "밥", "산", "강", "문", "공", "집")),
    ),
    chapter(
      5,
      StageDef("chapter_5_words", "text.book.closed.fill", listOf("사랑", "친구", "학교", "음식", "여행", "사진", "음악", "선물", "오늘", "응원")),
      StageDef("chapter_5_travel_words", "airplane.circle.fill", listOf("기차", "버스", "택시", "지도", "시장", "지하철", "공항", "식당", "화장실", "관광")),
      StageDef("chapter_5_study_work_words", "briefcase.fill", listOf("회사", "공부", "선생님", "학생", "도서관", "업무", "회의", "계획", "발표", "보고서")),
    ),
    chapter(
      6,
      StageDef(
        "chapter_6_spacing", "keyboard.fill",
        listOf("좋은 아침", "내 친구", "한국 음식", "매운 음식", "여행 사진", "작은 선물", "좋은 음악", "같이 가요", "물 주세요", "또 만나요"),
      ),
      StageDef(
        "chapter_6_sentences", "text.bubble.fill",
        listOf("안녕하세요", "감사합니다", "사랑해요", "만나서 반가워요", "오늘도 힘내요", "정말 멋있어요", "같이 가요", "사진을 찍어요", "음악을 들어요", "좋은 하루 보내요"),
      ),
      StageDef(
        "chapter_6_travel_phrases", "airplane.circle.fill",
        listOf("추천 메뉴가 뭐예요", "지하철역이 어디예요", "이거 얼마예요", "사진 찍어 주세요", "화장실이 어디예요", "카드로 계산할게요", "예약했어요", "길을 잃었어요", "한 장 주세요", "매운 음식 괜찮아요"),
      ),
      StageDef(
        "chapter_6_daily_conversation", "cup.and.saucer.fill",
        listOf("행복하게 웃어요", "오늘 하루 어땠어요", "도와줘서 고마워요", "다음에 또 봐요", "좋은 아침이에요", "오늘 날씨가 좋아요", "점심 같이 먹어요", "조금 피곤해요", "지금 집에 가요", "내일 다시 만나요"),
      ),
      StageDef(
        "chapter_6_fan_support", "heart.fill",
        listOf("건강 꼭 챙겨요", "하트 해 주세요", "웃는 모습 좋아요", "평생 응원할게요", "화면 너머로 설레요", "다음 방송도 올게요", "오늘도 레전드예요", "목소리 진짜 좋아요", "와 줘서 고마워요", "오늘 착장 최고예요"),
      ),
    ),
  )

  val stages: List<CurriculumStage> = chapters.flatMap { it.stages }

  fun stage(id: String): CurriculumStage? = stages.firstOrNull { it.id == id }

  fun chapter(number: Int): CurriculumChapter? = chapters.firstOrNull { it.number == number }

  private fun chapter(number: Int, vararg definitions: StageDef) = CurriculumChapter(
    number = number,
    titleKey = "curriculum.chapter_$number.title",
    stages = definitions.mapIndexed { offset, definition -> stage(number, offset + 1, definition) },
  )

  private fun stage(chapterNumber: Int, stageNumber: Int, definition: StageDef) = CurriculumStage(
    id = definition.id,
    chapterNumber = chapterNumber,
    stageNumber = stageNumber,
    titleKey = "curriculum.${definition.id}.title",
    detailKey = "curriculum.${definition.id}.detail",
    symbol = definition.symbol,
    items = definition.targets.mapIndexed { index, target ->
      CurriculumItem(
        id = "${definition.id}_${index + 1}",
        ko = target,
        readingKey = "curriculum.${definition.id}.item_${index + 1}.reading",
        meaningKey = if (chapterNumber >= 5) {
          "curriculum.${definition.id}.item_${index + 1}.meaning"
        } else {
          "curriculum.${definition.id}.item_meaning"
        },
      )
    },
  )
}

/** Chapters 1–4 unlock in order; chapters 5 and 6 are free selection. */
object CurriculumUnlockPolicy {
  fun isUnlocked(
    stage: CurriculumStage,
    completedStageIds: Set<String>,
    stages: List<CurriculumStage> = CurriculumCatalog.stages,
  ): Boolean {
    if (stage.chapterNumber >= 5) return true
    val index = stages.indexOfFirst { it.id == stage.id }
    if (index < 0) return false
    if (index == 0) return true
    return stages[index - 1].id in completedStageIds
  }
}

/** Core chapters need every stage; free-selection chapters (5, 6) need any one stage. */
object CurriculumChapterCompletionPolicy {
  fun isCompleted(chapter: CurriculumChapter, completedStageIds: Set<String>): Boolean =
    if (chapter.number >= 5) {
      chapter.stages.any { it.id in completedStageIds }
    } else {
      chapter.stages.all { it.id in completedStageIds }
    }
}

/** Hatching onboarding = the first three stages, in order. */
object HatchOnboardingPolicy {
  const val REQUIRED_CHAPTER_COUNT = 3

  val requiredStages: List<CurriculumStage> get() = CurriculumCatalog.stages.take(REQUIRED_CHAPTER_COUNT)

  fun nextRequiredStage(completedStageIds: Set<String>): CurriculumStage? =
    requiredStages.firstOrNull { it.id !in completedStageIds }

  fun isComplete(completedStageIds: Set<String>): Boolean = nextRequiredStage(completedStageIds) == null
}

/** iOS `BundledPronunciationAudio.relativePath(for:)`. */
fun pronunciationAudioPath(target: String): String {
  val digest = MessageDigest.getInstance("SHA-256").digest(target.toByteArray(Charsets.UTF_8))
  return "audio/ko_" + digest.take(10).joinToString("") { "%02x".format(it.toInt() and 0xff) } + ".mp3"
}
