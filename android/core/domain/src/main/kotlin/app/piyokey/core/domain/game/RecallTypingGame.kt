package app.piyokey.core.domain.game

import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.domain.GameKind
import app.piyokey.core.domain.SessionItemResolution
import app.piyokey.core.hangul.CompositionEvent
import app.piyokey.core.hangul.CompositionPhase
import app.piyokey.core.hangul.CompositionState
import app.piyokey.core.hangul.HangulComposer
import app.piyokey.core.hangul.JamoDecomposer
import app.piyokey.core.hangul.JamoJudgeResult
import app.piyokey.core.hangul.JamoJudgeState
import app.piyokey.core.hangul.JamoSequenceJudge
import kotlin.math.max
import kotlin.math.min

/*
 * Pure port of the typing recall games in iOS `Features/Game/ChoseongQuizView.swift`
 * (`ChoseongTypingViewModel`, builders, `RecallTypingGameMode`). The multiple-choice
 * `ChoseongQuizView` in that file is unused on iOS and is not ported.
 *
 * Builders take `meaning` (iOS `DeckItem.appMeaning`) so the app language stays outside the domain.
 */

typealias MeaningLookup = (DeckItem) -> String?

/** iOS `ChoseongExtractor`: initial consonants of every Hangul syllable, keeping word gaps. */
object ChoseongExtractor {
  private const val INITIALS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"

  fun extract(text: String): String {
    val result = StringBuilder()
    var lastWasSpace = false
    var index = 0
    while (index < text.length) {
      val codePoint = text.codePointAt(index)
      index += Character.charCount(codePoint)
      if (codePoint in 0xAC00..0xD7A3) {
        result.append(INITIALS[(codePoint - 0xAC00) / 588])
        lastWasSpace = false
      } else if (Character.isWhitespace(codePoint) && result.isNotEmpty() && !lastWasSpace) {
        result.append(' ')
        lastWasSpace = true
      }
    }
    return result.toString().trim(' ')
  }
}

data class ChoseongInitialProgressUnit(val offset: Int, val character: Char, val state: State) {
  enum class State { PENDING, ACTIVE, COMPLETED }

  val isSeparator: Boolean get() = character.isWhitespace()
}

/** iOS `ChoseongInitialProgressBuilder`: per-syllable initial tiles driven by typed jamo. */
object ChoseongInitialProgressBuilder {
  fun units(answer: String, completedJamoCount: Int): List<ChoseongInitialProgressUnit> {
    val units = ArrayList<ChoseongInitialProgressUnit>()
    var jamoOffset = 0
    var lastWasSpace = false
    var index = 0
    while (index < answer.length) {
      val codePoint = answer.codePointAt(index)
      val character = String(Character.toChars(codePoint))
      index += Character.charCount(codePoint)
      val keyCount = runCatching { JamoDecomposer.keySequence(character).size }.getOrDefault(0)
      val initial = ChoseongExtractor.extract(character).firstOrNull()
      if (initial != null) {
        val upper = jamoOffset + keyCount
        val state = when {
          completedJamoCount >= upper -> ChoseongInitialProgressUnit.State.COMPLETED
          completedJamoCount >= jamoOffset -> ChoseongInitialProgressUnit.State.ACTIVE
          else -> ChoseongInitialProgressUnit.State.PENDING
        }
        units += ChoseongInitialProgressUnit(units.size, initial, state)
        lastWasSpace = false
      } else if (Character.isWhitespace(codePoint) && units.isNotEmpty() && !lastWasSpace) {
        units += ChoseongInitialProgressUnit(units.size, ' ', ChoseongInitialProgressUnit.State.PENDING)
        lastWasSpace = true
      }
      jamoOffset += keyCount
    }
    while (units.lastOrNull()?.isSeparator == true) units.removeAt(units.lastIndex)
    return units
  }
}

/** iOS `JapaneseMeaningDisplayText`: strips 「」/『』 around a meaning. */
object JapaneseMeaningDisplayText {
  fun format(meaning: String): String {
    val trimmed = meaning.trim()
    if (trimmed.isEmpty()) return ""
    if ((trimmed.startsWith("「") && trimmed.endsWith("」")) || (trimmed.startsWith("『") && trimmed.endsWith("』"))) {
      return trimmed.substring(1, trimmed.length - 1)
    }
    return trimmed
  }
}

data class ChoseongTypingRound(val answer: DeckItem, val initials: String, val requiresMeaningHint: Boolean) {
  val id: String get() = answer.id
}

private fun String.isBlankText() = trim().isEmpty()

private fun isTypeable(text: String): Boolean = runCatching { JamoDecomposer.keySequence(text) }.isSuccess

private fun nonEmptySequence(text: String): Boolean =
  runCatching { JamoDecomposer.keySequence(text).isNotEmpty() }.getOrDefault(false)

object ChoseongTypingBuilder {
  fun rounds(items: List<DeckItem>, limit: Int = 10, seed: ULong, meaning: MeaningLookup): List<ChoseongTypingRound> {
    if (limit <= 0) return emptyList()
    val seen = HashSet<String>()
    var eligible = items.filter { item ->
      ChoseongExtractor.extract(item.ko).isNotEmpty() && isTypeable(item.ko) && seen.add(item.ko)
    }
    if (eligible.isEmpty()) return emptyList()
    val groups = eligible.groupingBy { ChoseongExtractor.extract(it.ko) }.eachCount()
    eligible = eligible.filter { item ->
      val hasMeaning = meaning(item)?.isBlankText() == false
      groups[ChoseongExtractor.extract(item.ko)] == 1 || hasMeaning
    }
    if (eligible.isEmpty()) return emptyList()
    val counts = eligible.groupingBy { ChoseongExtractor.extract(it.ko) }.eachCount()
    val generator = GameRandom(seed xor 0x7A17_1E5uL)
    val shuffled = eligible.swiftShuffled(generator)
    return shuffled.take(min(limit, shuffled.size)).map {
      val initials = ChoseongExtractor.extract(it.ko)
      ChoseongTypingRound(it, initials, (counts[initials] ?: 0) > 1)
    }
  }
}

object DictationTypingBuilder {
  fun rounds(items: List<DeckItem>, limit: Int = 10, seed: ULong): List<ChoseongTypingRound> {
    if (limit <= 0) return emptyList()
    val seen = HashSet<String>()
    val eligible = items.filter { item ->
      !item.ko.isBlankText() && nonEmptySequence(item.ko) && seen.add(item.ko)
    }
    if (eligible.isEmpty()) return emptyList()
    val shuffled = eligible.swiftShuffled(GameRandom(seed xor 0xD1C7_A710uL))
    return shuffled.take(min(limit, shuffled.size)).map { ChoseongTypingRound(it, "", false) }
  }
}

object WordMatchTypingBuilder {
  fun rounds(items: List<DeckItem>, limit: Int = 10, seed: ULong, meaning: MeaningLookup): List<ChoseongTypingRound> {
    if (limit <= 0) return emptyList()
    val seen = HashSet<String>()
    val eligible = items.filter { item ->
      val itemMeaning = meaning(item) ?: return@filter false
      !item.ko.isBlankText() && !itemMeaning.isBlankText() && nonEmptySequence(item.ko) && seen.add(item.ko)
    }
    if (eligible.isEmpty()) return emptyList()
    val shuffled = eligible.swiftShuffled(GameRandom(seed xor 0xA11C_E5EEDuL))
    return shuffled.take(min(limit, shuffled.size)).map { ChoseongTypingRound(it, "", false) }
  }
}

/** iOS `RecallTypingGameMode`. */
enum class RecallTypingGameMode(val gameKind: GameKind, val accessibilityNamespace: String) {
  CHOSEONG(GameKind.CHOSEONG, "choseong"),
  WORD_MATCH(GameKind.WORD_MATCH, "word_match"),
  DICTATION(GameKind.DICTATION, "dictation");

  val requiresCountdown: Boolean get() = this == WORD_MATCH || this == DICTATION

  /** Delay before the next question after a completed answer (ms). */
  val completedFeedbackMillis: Long get() = if (this == CHOSEONG) 1_350 else 650

  fun rounds(items: List<DeckItem>, seed: ULong, meaning: MeaningLookup, limit: Int = 10): List<ChoseongTypingRound> =
    when (this) {
      CHOSEONG -> ChoseongTypingBuilder.rounds(items, limit, seed, meaning)
      WORD_MATCH -> WordMatchTypingBuilder.rounds(items, limit, seed, meaning)
      DICTATION -> DictationTypingBuilder.rounds(items, limit, seed)
    }
}

data class ChoseongTypingCompletion(
  val answer: DeckItem,
  val points: Int,
  val usedHint: Boolean,
  val resolution: SessionItemResolution,
)

sealed interface ChoseongTypingInputOutcome {
  data object Correct : ChoseongTypingInputOutcome
  data class Incorrect(val answer: DeckItem, val expected: Char, val jamoIndex: Int) : ChoseongTypingInputOutcome
  data class Completed(val completion: ChoseongTypingCompletion) : ChoseongTypingInputOutcome
}

sealed interface PronunciationHintUse {
  val answer: DeckItem

  data class FirstUse(override val answer: DeckItem) : PronunciationHintUse
  data class Replay(override val answer: DeckItem) : PronunciationHintUse
}

sealed interface RecallTypingFeedback {
  data object Ready : RecallTypingFeedback
  data object Correct : RecallTypingFeedback
  data object Incorrect : RecallTypingFeedback
  data class Completed(val points: Int) : RecallTypingFeedback
}

/**
 * iOS `ChoseongTypingViewModel`: 10 typed recall rounds. Score per word
 * `max(50, 100 + speedBonus + min(combo, 10) × 10 − 30 if hinted)`; time is monotonic seconds.
 */
class ChoseongTypingEngine(rounds: List<ChoseongTypingRound>) {
  init {
    require(rounds.isNotEmpty()) { "Choseong typing requires at least one round" }
  }

  var rounds: List<ChoseongTypingRound> = rounds
    private set
  var phase = GamePhase.READY
    private set
  var currentIndex = 0
    private set
  var score = 0
    private set
  var combo = 0
    private set
  var maxCombo = 0
    private set
  var completedItemCount = 0
    private set
  var imperfectItemCount = 0
    private set
  var mistakeCount = 0
    private set
  var composition = CompositionState()
    private set
  var judgeState = JamoJudgeState(rounds[0].answer.ko)
    private set
  var feedback: RecallTypingFeedback = RecallTypingFeedback.Ready
    private set
  var feedbackRevision = 0
    private set
  var roundRevision = 0
    private set
  var compositionRevision = 0
    private set
  var lastAcceptedKey: Char? = null
    private set
  var shouldAnimateSyllableJoin = false
    private set
  var isHintVisible = false
    private set
  var isHintPenaltyApplied = false
    private set
  var pronunciationHintsRemaining = PRONUNCIATION_HINT_LIMIT
    private set
  var didUsePronunciationHintForCurrentRound = false
    private set
  var totalAcceptedInputCount = 0
    private set

  private val acceptedKeys = ArrayList<Char>()
  private var currentWordMistakeCount = 0
  private val currentWordMistakenJamoIndices = LinkedHashSet<Int>()
  private var activeElapsed = 0.0
  private var questionStartedAt = 0.0
  private var lastActive: Double? = null

  val currentRound: ChoseongTypingRound get() = rounds[currentIndex]
  val questionNumber: Int get() = currentIndex + 1
  val targetJamoSequence: List<Char> get() = judgeState.expectedSequence
  val completedJamoCount: Int get() = judgeState.currentIndex
  val enteredText: String get() = composition.text
  val composingPreview: String get() = composition.composingText
  val initialProgressUnits: List<ChoseongInitialProgressUnit>
    get() = ChoseongInitialProgressBuilder.units(currentRound.answer.ko, completedJamoCount)
  val acceptedKeySequence: List<Char> get() = acceptedKeys.toList()
  val nextExpectedKey: Char? get() = judgeState.expectedNext
  val isRoundComplete: Boolean get() = feedback is RecallTypingFeedback.Completed

  val accuracyPercent: Double
    get() {
      val total = totalAcceptedInputCount + mistakeCount
      return if (total > 0) totalAcceptedInputCount.toDouble() / total * 100 else 0.0
    }

  val questionsPerMinute: Double
    get() = if (activeElapsed > 0) completedItemCount / (activeElapsed / 60) else 0.0

  val activeDuration: Double get() = activeElapsed

  val result: FlowGameResult
    get() = FlowGameResult(
      score = score,
      maxCombo = maxCombo,
      accuracyPercent = accuracyPercent,
      charactersPerMinute = questionsPerMinute,
      activeDuration = activeElapsed,
      completedItemCount = completedItemCount,
      missedCardCount = imperfectItemCount,
      rank = accuracyRank(accuracyPercent),
    )

  val canUsePronunciationHint: Boolean
    get() = phase == GamePhase.RUNNING && !isRoundComplete &&
      (didUsePronunciationHintForCurrentRound || pronunciationHintsRemaining > 0)

  fun start(at: Double) {
    if (phase != GamePhase.READY) return
    phase = GamePhase.RUNNING
    lastActive = at
    questionStartedAt = activeElapsed
  }

  fun useHint(shouldPenalize: Boolean = true) {
    if (phase != GamePhase.RUNNING || isRoundComplete) return
    isHintVisible = true
    isHintPenaltyApplied = isHintPenaltyApplied || shouldPenalize
  }

  fun usePronunciationHint(): PronunciationHintUse? {
    if (phase != GamePhase.RUNNING || isRoundComplete) return null
    if (didUsePronunciationHintForCurrentRound) return PronunciationHintUse.Replay(currentRound.answer)
    if (pronunciationHintsRemaining <= 0) return null
    pronunciationHintsRemaining -= 1
    didUsePronunciationHintForCurrentRound = true
    isHintPenaltyApplied = true
    combo = 0
    return PronunciationHintUse.FirstUse(currentRound.answer)
  }

  fun input(key: Char, at: Double): ChoseongTypingInputOutcome? {
    if (phase != GamePhase.RUNNING || isRoundComplete) return null
    consumeActiveTime(at)
    val evaluation = JamoSequenceJudge.evaluate(key, judgeState)
    judgeState = evaluation.state
    val outcome: ChoseongTypingInputOutcome = when (val result = evaluation.result) {
      is JamoJudgeResult.Correct -> {
        val previousPhase = composition.phase
        acceptedKeys += key
        totalAcceptedInputCount += 1
        composition = HangulComposer.reduce(composition, CompositionEvent.Key(key))
        lastAcceptedKey = key
        shouldAnimateSyllableJoin = isSyllableJoin(previousPhase, composition.phase)
        compositionRevision += 1
        if (result.completed) {
          completeCurrentRound()
        } else {
          feedback = RecallTypingFeedback.Correct
          ChoseongTypingInputOutcome.Correct
        }
      }
      is JamoJudgeResult.Incorrect -> recordMistake(result.expected)
      JamoJudgeResult.AlreadyComplete -> return null
    }
    feedbackRevision += 1
    return outcome
  }

  fun backspace(at: Double) {
    if (phase != GamePhase.RUNNING || isRoundComplete || acceptedKeys.isEmpty()) return
    consumeActiveTime(at)
    acceptedKeys.removeAt(acceptedKeys.lastIndex)
    composition = HangulComposer.reduce(composition, CompositionEvent.Backspace)
    lastAcceptedKey = acceptedKeys.lastOrNull()
    shouldAnimateSyllableJoin = false
    compositionRevision += 1
    rebuildJudgeFromAcceptedKeys()
    feedback = RecallTypingFeedback.Ready
    feedbackRevision += 1
  }

  fun synchronizeOsIme(acceptedSequence: List<Char>, at: Double): List<ChoseongTypingInputOutcome> {
    if (phase != GamePhase.RUNNING || isRoundComplete || acceptedSequence.size > targetJamoSequence.size ||
      targetJamoSequence.take(acceptedSequence.size) != acceptedSequence
    ) {
      return emptyList()
    }
    while (acceptedKeys.size > acceptedSequence.size) backspace(at)
    if (acceptedKeys.size >= acceptedSequence.size) return emptyList()
    return acceptedSequence.drop(acceptedKeys.size).mapNotNull { input(it, at) }
  }

  fun recordConfirmedOsImeMistake(at: Double): ChoseongTypingInputOutcome? {
    if (phase != GamePhase.RUNNING || isRoundComplete) return null
    val expected = judgeState.expectedNext ?: return null
    consumeActiveTime(at)
    val outcome = recordMistake(expected)
    feedbackRevision += 1
    return outcome
  }

  fun advance(at: Double) {
    if (phase != GamePhase.RUNNING || !isRoundComplete) return
    if (currentIndex + 1 >= rounds.size) {
      phase = GamePhase.FINISHED
      lastActive = null
      return
    }
    currentIndex += 1
    resetCurrentRound()
    questionStartedAt = activeElapsed
    lastActive = at
    roundRevision += 1
  }

  fun pause(at: Double) {
    if (phase != GamePhase.RUNNING) return
    if (!isRoundComplete) consumeActiveTime(at)
    lastActive = null
    phase = GamePhase.PAUSED
  }

  fun resume(at: Double) {
    if (phase != GamePhase.PAUSED) return
    phase = GamePhase.RUNNING
    if (!isRoundComplete) lastActive = at
  }

  fun restart(replacementRounds: List<ChoseongTypingRound>? = null, at: Double) {
    prepareRestart(replacementRounds)
    start(at)
  }

  /** Resets the run before a retry countdown; stays non-interactive until [start]. */
  fun prepareRestart(replacementRounds: List<ChoseongTypingRound>? = null) {
    if (replacementRounds != null) {
      require(replacementRounds.isNotEmpty()) { "Choseong typing requires at least one round" }
      rounds = replacementRounds
    }
    currentIndex = 0
    score = 0
    combo = 0
    maxCombo = 0
    completedItemCount = 0
    imperfectItemCount = 0
    mistakeCount = 0
    totalAcceptedInputCount = 0
    pronunciationHintsRemaining = PRONUNCIATION_HINT_LIMIT
    activeElapsed = 0.0
    questionStartedAt = 0.0
    feedbackRevision = 0
    roundRevision += 1
    phase = GamePhase.READY
    resetCurrentRound()
    lastActive = null
  }

  private fun completeCurrentRound(): ChoseongTypingInputOutcome {
    val responseDuration = max(0.0, activeElapsed - questionStartedAt)
    val flawless = currentWordMistakeCount == 0
    if (flawless) {
      combo += 1
      maxCombo = max(maxCombo, combo)
    } else {
      combo = 0
      imperfectItemCount += 1
    }
    val points = score(responseDuration, combo, isHintPenaltyApplied)
    score += points
    completedItemCount += 1
    feedback = RecallTypingFeedback.Completed(points)
    lastActive = null
    return ChoseongTypingInputOutcome.Completed(
      ChoseongTypingCompletion(
        answer = currentRound.answer,
        points = points,
        usedHint = isHintPenaltyApplied,
        resolution = SessionItemResolution(
          currentIndex, !flawless, currentWordMistakeCount, currentWordMistakenJamoIndices.toSet(),
        ),
      ),
    )
  }

  private fun recordMistake(expected: Char): ChoseongTypingInputOutcome {
    val jamoIndex = judgeState.currentIndex
    mistakeCount += 1
    currentWordMistakeCount += 1
    currentWordMistakenJamoIndices += jamoIndex
    combo = 0
    feedback = RecallTypingFeedback.Incorrect
    return ChoseongTypingInputOutcome.Incorrect(currentRound.answer, expected, jamoIndex)
  }

  private fun resetCurrentRound() {
    judgeState = JamoJudgeState(currentRound.answer.ko)
    acceptedKeys.clear()
    composition = CompositionState()
    lastAcceptedKey = null
    shouldAnimateSyllableJoin = false
    compositionRevision += 1
    currentWordMistakeCount = 0
    currentWordMistakenJamoIndices.clear()
    isHintVisible = false
    isHintPenaltyApplied = false
    didUsePronunciationHintForCurrentRound = false
    feedback = RecallTypingFeedback.Ready
  }

  private fun rebuildJudgeFromAcceptedKeys() {
    var rebuilt = JamoJudgeState(currentRound.answer.ko)
    for (key in acceptedKeys) rebuilt = JamoSequenceJudge.evaluate(key, rebuilt).state
    judgeState = rebuilt
  }

  private fun consumeActiveTime(until: Double) {
    val last = lastActive ?: return
    activeElapsed += max(0.0, until - last)
    lastActive = until
  }

  companion object {
    const val PRONUNCIATION_HINT_LIMIT = 3

    /** `max(50, 100 + speedBonus + min(combo, 10) × 10 − 30 if hinted)`. */
    fun score(responseSeconds: Double, combo: Int, hinted: Boolean): Int {
      val speedBonus = max(0, ((5 - responseSeconds) * 10).toInt())
      val comboBonus = min(combo, 10) * 10
      return max(50, 100 + speedBonus + comboBonus - if (hinted) 30 else 0)
    }

    /** S ≥ 95 %, A ≥ 80 %, B ≥ 60 %, else C. */
    fun accuracyRank(accuracy: Double): String = when {
      accuracy >= 95 -> "S"
      accuracy >= 80 -> "A"
      accuracy >= 60 -> "B"
      else -> "C"
    }

    private fun isSyllableJoin(previous: CompositionPhase, next: CompositionPhase): Boolean =
      (previous == CompositionPhase.CHO && next == CompositionPhase.CHO_JUNG) ||
        (previous == CompositionPhase.CHO_JUNG && next == CompositionPhase.CHO_JUNG) ||
        (previous == CompositionPhase.CHO_JUNG_JONG && next == CompositionPhase.CHO_JUNG)
  }
}
