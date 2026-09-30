package app.piyokey.core.domain.game

import kotlin.math.max
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Pure port of iOS `Features/Game/SpacingGameView.swift` (engine, evaluation, view model).
 * Passage texts come from `catalog/spacing_passages.json`, which mirrors the iOS
 * `spacing.passage.*.text` strings (identical in every iOS language).
 */

data class SpacingPassage(val id: String, val titleKey: String, val text: String, val level: Int = 1) {
  val characterCount: Int get() = text.codePointCount(0, text.length)
  val spaceCount: Int get() = text.count { it.isWhitespace() }
}

object SpacingPassageCatalog {
  const val ASSET_PATH = "catalog/spacing_passages.json"

  /** Passage id → iOS title key (`spacing.passage.<suffix>`). */
  val titleKeys: Map<String, String> = linkedMapOf(
    "morning_commute" to "spacing.passage.morning",
    "weekend_trip" to "spacing.passage.weekend",
    "family_dinner" to "spacing.passage.cooking",
    "library_afternoon" to "spacing.passage.library",
    "language_practice" to "spacing.passage.practice",
    "careful_judgment" to "spacing.passage.judgment",
  )

  @Serializable
  private data class File(@SerialName("schema_version") val schemaVersion: Int, val passages: List<Entry>)

  @Serializable
  private data class Entry(val id: String, val level: Int, val text: String)

  private val json = Json { ignoreUnknownKeys = true }

  /** Decodes the bundled JSON; unknown ids are skipped, order is preserved. */
  fun decode(bytes: ByteArray): List<SpacingPassage> {
    val file = json.decodeFromString(File.serializer(), bytes.toString(Charsets.UTF_8))
    require(file.schemaVersion == 1) { "Unsupported spacing passage schema ${file.schemaVersion}" }
    return file.passages.mapNotNull { entry ->
      val titleKey = titleKeys[entry.id] ?: return@mapNotNull null
      SpacingPassage(entry.id, titleKey, entry.text, entry.level)
    }
  }
}

data class SpacingMistakeReview(
  val boundary: Int,
  val attemptedText: String,
  val answerText: String,
  val expectedSpace: Boolean,
)

data class SpacingGameEvaluation(
  val answer: String,
  val submittedText: String,
  val correctSpaceCount: Int,
  val expectedSpaceCount: Int,
  val missedSpaceCount: Int,
  val extraSpaceCount: Int,
  val accuracyPercent: Double,
  val firstAttemptCorrectCount: Int,
  val totalBoundaryCount: Int,
  val firstAttemptAccuracyPercent: Double,
  val correctionCount: Int,
  val firstAttemptMistakes: List<SpacingMistakeReview>,
  val score: Int,
  val activeDuration: Double,
) {
  val isPerfect: Boolean
    get() = missedSpaceCount == 0 && extraSpaceCount == 0 && firstAttemptAccuracyPercent == 100.0

  val rank: String
    get() = when {
      firstAttemptAccuracyPercent >= 100 -> "S"
      firstAttemptAccuracyPercent >= 90 -> "A"
      firstAttemptAccuracyPercent >= 75 -> "B"
      else -> "C"
    }
}

/** Swift `Double.rounded()` (half away from zero) for non-negative values. */
private fun Double.swiftRounded(): Long = if (this >= 0) (this + 0.5).toLong() else -((-this) + 0.5).toLong()

class SpacingGameEngine(answer: String) {
  val answer: String
  val compactText: String
  val correctBoundaries: Set<Int>
  private val characters: List<String>

  init {
    val parsed = parse(answer)
    require(parsed.first.isNotEmpty()) { "A spacing passage cannot be empty" }
    require(parsed.second.isNotEmpty()) { "A spacing passage needs at least one space" }
    characters = parsed.first
    this.answer = render(parsed.first, parsed.second)
    compactText = parsed.first.joinToString("")
    correctBoundaries = parsed.second
  }

  val boundaryCount: Int get() = max(characters.size - 1, 0)

  /** Compact characters (graphemes as strings). */
  val compactCharacters: List<String> get() = characters

  fun normalizedDraft(candidate: String): String? {
    val parsed = parse(candidate)
    if (parsed.first.joinToString("") != compactText) return null
    return render(parsed.first, parsed.second)
  }

  fun selectedSpaceCount(draft: String): Int = parse(draft).second.size

  fun renderedDraft(boundaries: Set<Int>): String =
    render(characters, boundaries.filterTo(HashSet()) { it in 1..boundaryCount })

  fun isCorrectBoundary(boundary: Int): Boolean = boundary in correctBoundaries

  fun evaluate(
    draft: String,
    activeDuration: Double,
    firstDecisions: Map<Int, Boolean>? = null,
    correctionCount: Int = 0,
  ): SpacingGameEvaluation {
    val normalized = normalizedDraft(draft) ?: compactText
    val submitted = parse(normalized).second
    val correct = submitted.intersect(correctBoundaries).size
    val missed = (correctBoundaries - submitted).size
    val extra = (submitted - correctBoundaries).size
    val denominator = max(correctBoundaries.size + extra, 1)
    val accuracy = correct.toDouble() / denominator * 100
    val firstCorrect: Int
    val firstAccuracy: Double
    val mistakes: List<SpacingMistakeReview>
    if (firstDecisions != null) {
      firstCorrect = firstDecisions.count { (boundary, choseSpace) -> choseSpace == (boundary in correctBoundaries) }
      firstAccuracy = firstCorrect.toDouble() / max(boundaryCount, 1) * 100
      mistakes = mistakeReviews(firstDecisions)
    } else {
      firstCorrect = (accuracy / 100 * boundaryCount).swiftRounded().toInt()
      firstAccuracy = accuracy
      mistakes = emptyList()
    }
    return SpacingGameEvaluation(
      answer = answer,
      submittedText = normalized,
      correctSpaceCount = correct,
      expectedSpaceCount = correctBoundaries.size,
      missedSpaceCount = missed,
      extraSpaceCount = extra,
      accuracyPercent = accuracy,
      firstAttemptCorrectCount = firstCorrect,
      totalBoundaryCount = boundaryCount,
      firstAttemptAccuracyPercent = firstAccuracy,
      correctionCount = max(correctionCount, 0),
      firstAttemptMistakes = mistakes,
      score = (firstAccuracy * 10).swiftRounded().toInt(),
      activeDuration = max(activeDuration, 0.0),
    )
  }

  private fun mistakeReviews(firstDecisions: Map<Int, Boolean>): List<SpacingMistakeReview> =
    firstDecisions.keys.sorted().mapNotNull { boundary ->
      val choseSpace = firstDecisions[boundary] ?: return@mapNotNull null
      val expectedSpace = boundary in correctBoundaries
      if (choseSpace == expectedSpace) return@mapNotNull null
      val previous = correctBoundaries.filter { it < boundary }.maxOrNull() ?: 0
      val next = correctBoundaries.filter { it > boundary }.minOrNull() ?: characters.size
      val range = previous until next
      val attempted = correctBoundaries.toMutableSet().apply { if (choseSpace) add(boundary) else remove(boundary) }
      SpacingMistakeReview(
        boundary = boundary,
        attemptedText = renderExcerpt(range, attempted),
        answerText = renderExcerpt(range, correctBoundaries),
        expectedSpace = expectedSpace,
      )
    }

  private fun renderExcerpt(range: IntRange, boundaries: Set<Int>): String {
    val rendered = StringBuilder()
    for (index in range) {
      if (index > range.first && index in boundaries) rendered.append(' ')
      rendered.append(characters[index])
    }
    return rendered.toString()
  }

  companion object {
    /** (characters, boundaries): a boundary `i` means a space before character `i`. */
    private fun parse(text: String): Pair<List<String>, Set<Int>> {
      val characters = ArrayList<String>()
      val boundaries = LinkedHashSet<Int>()
      var hasPendingSpace = false
      var index = 0
      while (index < text.length) {
        val codePoint = text.codePointAt(index)
        index += Character.charCount(codePoint)
        if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
          hasPendingSpace = characters.isNotEmpty()
        } else {
          if (hasPendingSpace) boundaries += characters.size
          characters += String(Character.toChars(codePoint))
          hasPendingSpace = false
        }
      }
      return characters to boundaries
    }

    private fun render(characters: List<String>, boundaries: Set<Int>): String {
      val rendered = StringBuilder()
      characters.forEachIndexed { index, character ->
        if (index in boundaries) rendered.append(' ')
        rendered.append(character)
      }
      return rendered.toString()
    }
  }
}

enum class SpacingPlacementOutcome {
  CORRECT, INCORRECT;

  val isCorrect: Boolean get() = this == CORRECT
}

/** iOS `SpacingGameViewModel`: cursor-driven boundary decisions; time is monotonic seconds. */
class SpacingGameSession(val passage: SpacingPassage) {
  val engine = SpacingGameEngine(passage.text)

  var phase = GamePhase.READY
    private set
  var draft: String = engine.compactText
    private set
  var result: SpacingGameEvaluation? = null
    private set
  var currentBoundary: Int = minOf(1, engine.boundaryCount)
    private set
  var decisions: Map<Int, Boolean> = emptyMap()
    private set
  var correctionCount = 0
    private set
  var feedback: SpacingPlacementOutcome? = null
    private set
  var firstDecisions: Map<Int, Boolean> = emptyMap()
    private set

  private var accumulatedDuration = 0.0
  private var activeStartedAt: Double? = null

  val selectedBoundaries: Set<Int> get() = decisions.filterValues { it }.keys
  val selectedSpaceCount: Int get() = selectedBoundaries.size
  val answeredBoundaryCount: Int get() = firstDecisions.size
  val totalBoundaryCount: Int get() = engine.boundaryCount
  val canMoveLeft: Boolean get() = currentBoundary > 1
  val canMoveRight: Boolean get() = currentBoundary < totalBoundaryCount
  val currentBoundaryIsSelected: Boolean get() = currentBoundary in selectedBoundaries
  val isReadyToFinish: Boolean get() = currentBoundary == totalBoundaryCount

  fun start(at: Double) {
    if (phase != GamePhase.READY) return
    phase = GamePhase.RUNNING
    activeStartedAt = at
  }

  fun moveLeft() {
    if (phase != GamePhase.RUNNING || !canMoveLeft) return
    currentBoundary -= 1
    feedback = null
  }

  fun moveRight() {
    if (phase != GamePhase.RUNNING || !canMoveRight) return
    recordCurrentChoiceIfNeeded()
    currentBoundary += 1
    feedback = null
  }

  fun toggleCurrentSpace(): SpacingPlacementOutcome? {
    if (phase != GamePhase.RUNNING || currentBoundary !in 1..totalBoundaryCount) return null
    val insertsSpace = !currentBoundaryIsSelected
    if (firstDecisions[currentBoundary] == null) {
      firstDecisions = firstDecisions + (currentBoundary to insertsSpace)
    } else {
      correctionCount += 1
    }
    decisions = decisions + (currentBoundary to insertsSpace)
    draft = engine.renderedDraft(selectedBoundaries)
    if (!insertsSpace) {
      feedback = null
      return null
    }
    val outcome = if (engine.isCorrectBoundary(currentBoundary)) SpacingPlacementOutcome.CORRECT else SpacingPlacementOutcome.INCORRECT
    feedback = outcome
    return outcome
  }

  fun pause(at: Double) {
    if (phase != GamePhase.RUNNING) return
    accumulatedDuration += elapsedSinceStart(at)
    activeStartedAt = null
    phase = GamePhase.PAUSED
  }

  fun resume(at: Double) {
    if (phase != GamePhase.PAUSED) return
    phase = GamePhase.RUNNING
    activeStartedAt = at
  }

  fun activeDuration(at: Double): Double =
    accumulatedDuration + if (phase == GamePhase.RUNNING) elapsedSinceStart(at) else 0.0

  fun submit(at: Double): SpacingGameEvaluation? {
    if ((phase != GamePhase.RUNNING && phase != GamePhase.PAUSED) || !isReadyToFinish) return result
    recordCurrentChoiceIfNeeded()
    if (phase == GamePhase.RUNNING) accumulatedDuration += elapsedSinceStart(at)
    activeStartedAt = null
    val evaluation = engine.evaluate(draft, accumulatedDuration, firstDecisions, correctionCount)
    result = evaluation
    phase = GamePhase.FINISHED
    return evaluation
  }

  fun restart(at: Double) {
    draft = engine.compactText
    result = null
    currentBoundary = minOf(1, engine.boundaryCount)
    decisions = emptyMap()
    firstDecisions = emptyMap()
    correctionCount = 0
    feedback = null
    accumulatedDuration = 0.0
    activeStartedAt = at
    phase = GamePhase.RUNNING
  }

  private fun elapsedSinceStart(at: Double): Double {
    val started = activeStartedAt ?: return 0.0
    return max(at - started, 0.0)
  }

  private fun recordCurrentChoiceIfNeeded() {
    if (firstDecisions[currentBoundary] != null) return
    firstDecisions = firstDecisions + (currentBoundary to currentBoundaryIsSelected)
  }

  companion object {
    /** Analytics difficulty by passage level. */
    fun analyticsDifficulty(level: Int): String = when {
      level <= 2 -> "beginner"
      level <= 4 -> "intermediate"
      else -> "advanced"
    }

    /** `m:ss` for the stopwatch. */
    fun durationLabel(seconds: Double): String {
      val whole = max(seconds.toLong(), 0L)
      return "%d:%02d".format(whole / 60, whole % 60)
    }
  }
}
