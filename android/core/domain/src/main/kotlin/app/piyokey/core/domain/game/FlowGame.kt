package app.piyokey.core.domain.game

import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckType
import app.piyokey.core.domain.FlowGameRankTuning
import app.piyokey.core.domain.SessionItemResolution
import app.piyokey.core.hangul.CompositionEvent
import app.piyokey.core.hangul.CompositionState
import app.piyokey.core.hangul.HangulComposer
import app.piyokey.core.hangul.JamoDecomposer
import app.piyokey.core.hangul.JamoJudgeResult
import app.piyokey.core.hangul.JamoJudgeState
import app.piyokey.core.hangul.JamoSequenceJudge
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/*
 * Pure port of iOS `Features/Game/FlowGameViewModel.swift` (+ the layout/pacing helpers of
 * `FlowGameView.swift` and `GameDeckSelectionView.swift`). All time is monotonic seconds
 * (`Double`); the UI passes frame timestamps so the reducer never reads a clock itself.
 */

/** iOS `FlowGameResult`, shared by every deck game result screen. */
data class FlowGameResult(
  val score: Int,
  val maxCombo: Int,
  val accuracyPercent: Double,
  val charactersPerMinute: Double,
  /** Seconds. */
  val activeDuration: Double,
  val completedItemCount: Int,
  val missedCardCount: Int,
  val rank: String,
  val endedByLives: Boolean = false,
)

object FlowGamePacing {
  const val DEFAULT_MAXIMUM_LIVES = 3
  const val FLOW_MAXIMUM_SPEED_MULTIPLIER = 1.8
  const val ACID_RAIN_MAXIMUM_SPEED_MULTIPLIER = 1.5
  const val SECONDS_UNTIL_MAXIMUM_SPEED = 50.0
  const val MAXIMUM_CONCURRENT_ACID_RAIN_CARDS = 4

  fun flowSpeedMultiplier(activeElapsed: Double) = speedMultiplier(activeElapsed, FLOW_MAXIMUM_SPEED_MULTIPLIER)

  fun acidRainSpeedMultiplier(activeElapsed: Double) = speedMultiplier(activeElapsed, ACID_RAIN_MAXIMUM_SPEED_MULTIPLIER)

  private fun speedMultiplier(activeElapsed: Double, maximum: Double): Double {
    val progress = (activeElapsed / SECONDS_UNTIL_MAXIMUM_SPEED).coerceIn(0.0, 1.0)
    return 1 + (maximum - 1) * progress
  }

  fun acidRainSpawnInterval(cardTravelDuration: Double): Double =
    minOf(max(1.4, cardTravelDuration * 0.42), 3.6, cardTravelDuration * 0.72)
}

/** iOS `FlowGameCourse`: card travel time by deck shape. */
enum class FlowGameCourse(val raw: String, val cardTravelDuration: Double, val localizationKey: String) {
  SHORT_WORD("short_word", 7.0, "game.course.short_word"),
  WORD("word", 9.0, "game.course.word"),
  SENTENCE("sentence", 12.0, "game.course.sentence");

  companion object {
    fun of(deck: Deck): FlowGameCourse {
      if (deck.type == DeckType.SENTENCE) return SENTENCE
      val average = if (deck.items.isEmpty()) 0.0 else deck.items.sumOf { graphemeCount(it.ko) }.toDouble() / deck.items.size
      return if (average <= 3) SHORT_WORD else WORD
    }

    /** Travel time used for a game kind (acid rain is 78 % of Flow, at least 5.5 s). */
    fun travelDuration(course: FlowGameCourse, isAcidRain: Boolean): Double =
      if (isAcidRain) max(5.5, course.cardTravelDuration * 0.78) else course.cardTravelDuration
  }
}

/** Swift `String.count` for the Korean content used here (surrogate pairs count once). */
internal fun graphemeCount(text: String): Int = text.codePointCount(0, text.length)

data class AcidRainFallingCard(
  val id: Int,
  val itemIndex: Int,
  val laneIndex: Int,
  val elapsed: Double,
  val travelDuration: Double,
)

data class AcidRainCardSnapshot(
  val id: Int,
  val itemIndex: Int,
  val laneIndex: Int,
  val progress: Double,
  val isInputTarget: Boolean,
)

sealed interface FlowGameMascotEvent {
  data object Idle : FlowGameMascotEvent
  data object CorrectJamo : FlowGameMascotEvent
  data object WordCompleted : FlowGameMascotEvent
  data class Rhythm(val combo: Int) : FlowGameMascotEvent
  data class Startle(val expected: Char) : FlowGameMascotEvent
  data class Dizzy(val expected: Char) : FlowGameMascotEvent
  data object Stretch : FlowGameMascotEvent
  data object NewBest : FlowGameMascotEvent
}

enum class GamePhase { READY, RUNNING, PAUSED, FINISHED }

sealed interface FlowGameFeedback {
  data object Ready : FlowGameFeedback
  data object Correct : FlowGameFeedback
  data class Incorrect(val expected: Char) : FlowGameFeedback
  data class Completed(val points: Int) : FlowGameFeedback
  data object Escaped : FlowGameFeedback
}

/**
 * iOS `FlowGameViewModel` as a monotonic reducer: Flow (one moving card) and Acid Rain
 * ([allowsConcurrentCards], up to 4 falling cards on 3 lanes).
 */
class FlowGameEngine(
  targets: List<String>,
  val initialDuration: Double = 60.0,
  val flawlessBonus: Double = 2.0,
  val cardTravelDuration: Double = 8.0,
  val maximumLives: Int = FlowGamePacing.DEFAULT_MAXIMUM_LIVES,
  val allowsConcurrentCards: Boolean = false,
  private val rankTuning: FlowGameRankTuning = FlowGameRankTuning(),
) {
  init {
    require(targets.isNotEmpty()) { "Flow game requires at least one target" }
    require(initialDuration > 0) { "Flow game duration must be positive" }
    require(cardTravelDuration > 0) { "Card travel duration must be positive" }
    require(maximumLives > 0) { "Flow game requires at least one life" }
    require(targets.all(::isTypeable)) { "Every flow game target must be decomposable by HangulEngine" }
  }

  var targets: List<String> = targets
    private set
  var phase = GamePhase.READY
    private set
  var remainingTime = initialDuration
    private set
  var score = 0
    private set
  var combo = 0
    private set
  var maxCombo = 0
    private set
  var completedItemCount = 0
    private set
  var missedCardCount = 0
    private set
  var remainingLives = maximumLives
    private set
  var mistakeCount = 0
    private set
  var currentTargetIndex = 0
    private set
  var target: String = targets[0]
    private set
  var judgeState = JamoJudgeState(targets[0])
    private set
  val targetJamoSequence: List<Char> get() = judgeState.expectedSequence
  var composition = CompositionState()
    private set
  var feedback: FlowGameFeedback = FlowGameFeedback.Ready
    private set
  var feedbackRevision = 0
    private set
  var cardRevision = 0
    private set
  var completionRevision = 0
    private set
  var reviewResolutionRevision = 0
    private set
  var lastCompletedItem: SessionItemResolution? = null
    private set
  var consecutiveMistakes = 0
    private set
  var totalAcceptedInputCount = 0
    private set
  var mascotEvent: FlowGameMascotEvent = FlowGameMascotEvent.Idle
    private set
  var mascotRevision = 0
    private set
  var currentCardTravelDuration = cardTravelDuration
    private set
  var acidRainCards: List<AcidRainFallingCard> = emptyList()
    private set

  private val acceptedKeys = ArrayList<Char>()
  private var completedAcceptedJamoCount = 0
  private var scoredJamoCount = 0
  private var currentWordMistakeCount = 0
  private val currentWordMistakenJamoIndices = LinkedHashSet<Int>()
  private var lastTick: Double? = null
  private var activeCardElapsed = 0.0
  private var activeElapsed = 0.0
  private var activeAcidRainCardId: Int? = null
  private var nextAcidRainCardId = 0
  private var nextAcidRainItemIndex = 0
  private var acidRainSpawnElapsed = 0.0
  private var acidRainSpawnInterval = 0.0

  init {
    if (allowsConcurrentCards) {
      acidRainCards = listOf(AcidRainFallingCard(0, 0, 0, 0.0, cardTravelDuration))
      activeAcidRainCardId = 0
      nextAcidRainCardId = 1
      nextAcidRainItemIndex = if (targets.size > 1) 1 else 0
      acidRainSpawnInterval = FlowGamePacing.acidRainSpawnInterval(cardTravelDuration)
    }
  }

  val completedJamoCount: Int get() = judgeState.currentIndex
  val acceptedKeySequence: List<Char> get() = acceptedKeys.toList()
  val nextExpectedKey: Char? get() = judgeState.expectedNext
  val enteredText: String get() = composition.text
  val composingPreview: String get() = composition.composingText
  val acceptedJamoCount: Int get() = completedAcceptedJamoCount + acceptedKeys.size
  val activeDuration: Double get() = activeElapsed

  val accuracyPercent: Double
    get() {
      val total = acceptedJamoCount + mistakeCount
      return if (total > 0) acceptedJamoCount.toDouble() / total * 100 else 0.0
    }

  val charactersPerMinute: Double
    get() = if (activeElapsed > 0) scoredJamoCount / (activeElapsed / 60) else 0.0

  val result: FlowGameResult
    get() = FlowGameResult(
      score = score,
      maxCombo = maxCombo,
      accuracyPercent = accuracyPercent,
      charactersPerMinute = charactersPerMinute,
      activeDuration = activeElapsed,
      completedItemCount = completedItemCount,
      missedCardCount = missedCardCount,
      rank = rankTuning.rank(accuracyPercent, charactersPerMinute),
      endedByLives = remainingLives == 0,
    )

  /** Whole seconds shown by the HUD (iOS `ceil(remainingTime)`). */
  val displayedSeconds: Int get() = ceil(remainingTime).toInt()

  fun start(at: Double) {
    if (phase != GamePhase.READY) return
    phase = GamePhase.RUNNING
    lastTick = at
  }

  /** Back to the pre-start state; the UI owns the countdown and calls [start] afterwards. */
  fun restart(replacementTargets: List<String>? = null) {
    if (replacementTargets != null) {
      require(replacementTargets.isNotEmpty()) { "Flow game requires at least one target" }
      require(replacementTargets.all(::isTypeable)) { "Every flow game target must be decomposable by HangulEngine" }
      targets = replacementTargets
    }
    remainingTime = initialDuration
    score = 0
    combo = 0
    maxCombo = 0
    completedItemCount = 0
    missedCardCount = 0
    remainingLives = maximumLives
    mistakeCount = 0
    consecutiveMistakes = 0
    totalAcceptedInputCount = 0
    completedAcceptedJamoCount = 0
    scoredJamoCount = 0
    activeElapsed = 0.0
    currentTargetIndex = 0
    target = targets[0]
    lastCompletedItem = null
    mascotEvent = FlowGameMascotEvent.Idle
    mascotRevision += 1
    if (allowsConcurrentCards) resetAcidRainCards() else resetCurrentCard()
    feedback = FlowGameFeedback.Ready
    feedbackRevision += 1
    cardRevision += 1
    phase = GamePhase.READY
    lastTick = null
  }

  fun tick(at: Double) {
    val last = lastTick ?: return
    if (phase != GamePhase.RUNNING) return
    var unconsumed = max(0.0, at - last)
    lastTick = at
    if (allowsConcurrentCards) {
      tickConcurrentCards(unconsumed)
      return
    }
    while (unconsumed > 0 && phase == GamePhase.RUNNING) {
      val untilTimeout = remainingTime
      val untilEscape = currentCardTravelDuration - activeCardElapsed
      val step = minOf(unconsumed, untilTimeout, untilEscape)
      if (step <= 0) {
        if (remainingTime <= 0) finish() else escapeCurrentCard()
        continue
      }
      remainingTime -= step
      activeElapsed += step
      activeCardElapsed += step
      unconsumed -= step
      if (remainingTime <= EPSILON) {
        remainingTime = 0.0
        finish()
      } else if (activeCardElapsed >= currentCardTravelDuration - EPSILON) {
        escapeCurrentCard()
      }
    }
  }

  private fun tickConcurrentCards(initialUnconsumed: Double) {
    var unconsumed = initialUnconsumed
    while (unconsumed > 0 && phase == GamePhase.RUNNING) {
      val canSpawn = acidRainCards.size < maximumConcurrentAcidRainCardCount
      val untilSpawn = if (canSpawn) max(0.0, acidRainSpawnInterval - acidRainSpawnElapsed) else Double.MAX_VALUE
      val untilEscape = acidRainCards.minOfOrNull { max(0.0, it.travelDuration - it.elapsed) } ?: Double.MAX_VALUE
      val step = minOf(minOf(unconsumed, remainingTime), minOf(untilSpawn, untilEscape))

      if (step > EPSILON) {
        remainingTime -= step
        activeElapsed += step
        acidRainSpawnElapsed += step
        acidRainCards = acidRainCards.map { it.copy(elapsed = it.elapsed + step) }
        synchronizeActiveAcidRainTiming()
        unconsumed -= step
      }

      if (remainingTime <= EPSILON) {
        remainingTime = 0.0
        finish()
        continue
      }

      val escapedIds = acidRainCards
        .filter { it.elapsed >= it.travelDuration - EPSILON }
        .sortedByDescending { it.elapsed / it.travelDuration }
        .map { it.id }
      for (id in escapedIds) {
        if (phase == GamePhase.RUNNING) escapeAcidRainCard(id)
      }

      var didSpawn = false
      if (phase == GamePhase.RUNNING && acidRainCards.size < maximumConcurrentAcidRainCardCount &&
        acidRainSpawnElapsed >= acidRainSpawnInterval - EPSILON
      ) {
        spawnAcidRainCard()
        didSpawn = true
      }
      if (step <= EPSILON && escapedIds.isEmpty() && !didSpawn) break
    }
  }

  fun pause(at: Double) {
    if (phase != GamePhase.RUNNING) return
    tick(at)
    if (phase != GamePhase.RUNNING) return
    phase = GamePhase.PAUSED
    lastTick = null
  }

  fun resume(at: Double) {
    if (phase != GamePhase.PAUSED) return
    phase = GamePhase.RUNNING
    lastTick = at
  }

  fun projectedCardProgress(at: Double): Double {
    if (allowsConcurrentCards) {
      projectedAcidRainCards(at).firstOrNull { it.isInputTarget }?.let { return it.progress }
    }
    var elapsed = activeCardElapsed
    val last = lastTick
    if (phase == GamePhase.RUNNING && last != null) elapsed += max(0.0, at - last)
    return (elapsed / currentCardTravelDuration).coerceIn(0.0, 1.0)
  }

  fun projectedAcidRainCards(at: Double): List<AcidRainCardSnapshot> {
    val last = lastTick
    val additional = if (phase == GamePhase.RUNNING && last != null) max(0.0, at - last) else 0.0
    return acidRainCards.map { card ->
      AcidRainCardSnapshot(
        id = card.id,
        itemIndex = card.itemIndex,
        laneIndex = card.laneIndex,
        progress = ((card.elapsed + additional) / card.travelDuration).coerceIn(0.0, 1.0),
        isInputTarget = card.id == activeAcidRainCardId,
      )
    }
  }

  fun input(key: Char) {
    if (phase != GamePhase.RUNNING) return
    val evaluation = JamoSequenceJudge.evaluate(key, judgeState)
    judgeState = evaluation.state
    when (val result = evaluation.result) {
      is JamoJudgeResult.Correct -> {
        acceptedKeys += key
        totalAcceptedInputCount += 1
        consecutiveMistakes = 0
        composition = HangulComposer.reduce(composition, CompositionEvent.Key(key))
        if (result.completed) {
          completeCurrentCard()
        } else {
          feedback = FlowGameFeedback.Correct
          publishMascot(FlowGameMascotEvent.CorrectJamo)
        }
      }
      is JamoJudgeResult.Incorrect -> recordMistake(result.expected)
      JamoJudgeResult.AlreadyComplete -> return
    }
    feedbackRevision += 1
  }

  fun backspace() {
    if (phase != GamePhase.RUNNING || acceptedKeys.isEmpty()) return
    acceptedKeys.removeAt(acceptedKeys.lastIndex)
    composition = HangulComposer.reduce(composition, CompositionEvent.Backspace)
    rebuildJudgeFromAcceptedKeys()
    feedback = FlowGameFeedback.Ready
    feedbackRevision += 1
  }

  fun synchronizeOsIme(acceptedSequence: List<Char>) {
    if (phase != GamePhase.RUNNING || acceptedSequence.size > targetJamoSequence.size ||
      targetJamoSequence.take(acceptedSequence.size) != acceptedSequence
    ) {
      return
    }
    while (acceptedKeys.size > acceptedSequence.size) backspace()
    if (acceptedKeys.size >= acceptedSequence.size) return
    for (key in acceptedSequence.drop(acceptedKeys.size)) input(key)
  }

  /** Acid rain + OS IME: the most urgent visible card showing [matchingTarget] becomes the target. */
  fun synchronizeAcidRainOsIme(matchingTarget: String, acceptedSequence: List<Char>) {
    if (!allowsConcurrentCards || phase != GamePhase.RUNNING) return
    val matching = acidRainCards
      .filter { targets[it.itemIndex] == matchingTarget }
      .maxByOrNull { it.elapsed / it.travelDuration } ?: return
    if (matching.id != activeAcidRainCardId) selectAcidRainCard(matching.id, publishesCardChange = false)
    synchronizeOsIme(acceptedSequence)
  }

  fun recordConfirmedOsImeMistake() {
    if (phase != GamePhase.RUNNING) return
    val expected = judgeState.expectedNext ?: return
    recordMistake(expected)
    feedbackRevision += 1
  }

  fun publishStretchReaction() {
    if (phase == GamePhase.RUNNING) publishMascot(FlowGameMascotEvent.Stretch)
  }

  fun publishNewBestReaction() {
    if (phase == GamePhase.RUNNING) publishMascot(FlowGameMascotEvent.NewBest)
  }

  private fun completeCurrentCard() {
    val flawless = currentWordMistakeCount == 0
    lastCompletedItem = SessionItemResolution(
      itemIndex = currentTargetIndex,
      hadMistake = !flawless,
      mistakeCount = currentWordMistakeCount,
      mistakenJamoIndices = currentWordMistakenJamoIndices.toSet(),
    )
    if (flawless) {
      combo += 1
      maxCombo = max(maxCombo, combo)
      remainingTime += flawlessBonus
    }
    val baseScore = targetJamoSequence.size * 10
    val points = (baseScore * comboMultiplier(combo)).toInt()
    score += points
    completedItemCount += 1
    scoredJamoCount += targetJamoSequence.size
    completedAcceptedJamoCount += acceptedKeys.size
    feedback = FlowGameFeedback.Completed(points)
    if (combo > 0 && combo % 5 == 0) {
      publishMascot(FlowGameMascotEvent.Rhythm(combo))
    } else {
      publishMascot(FlowGameMascotEvent.WordCompleted)
    }
    completionRevision += 1
    reviewResolutionRevision += 1
    if (allowsConcurrentCards) removeCompletedAcidRainCard() else advanceToNextCard()
  }

  private fun escapeCurrentCard() {
    if (currentWordMistakeCount > 0) {
      lastCompletedItem = SessionItemResolution(
        currentTargetIndex, true, currentWordMistakeCount, currentWordMistakenJamoIndices.toSet(),
      )
      reviewResolutionRevision += 1
    }
    completedAcceptedJamoCount += acceptedKeys.size
    missedCardCount += 1
    remainingLives -= 1
    combo = 0
    feedback = FlowGameFeedback.Escaped
    feedbackRevision += 1
    if (remainingLives == 0) finish() else advanceToNextCard()
  }

  private fun advanceToNextCard() {
    currentTargetIndex = (currentTargetIndex + 1) % targets.size
    target = targets[currentTargetIndex]
    resetCurrentCard()
    cardRevision += 1
  }

  private val maximumConcurrentAcidRainCardCount: Int
    get() = min(FlowGamePacing.MAXIMUM_CONCURRENT_ACID_RAIN_CARDS, targets.size)

  private fun resetAcidRainCards() {
    val travel = cardTravelDuration / FlowGamePacing.acidRainSpeedMultiplier(activeElapsed)
    acidRainCards = listOf(AcidRainFallingCard(0, 0, 0, 0.0, travel))
    activeAcidRainCardId = 0
    nextAcidRainCardId = 1
    nextAcidRainItemIndex = if (targets.size > 1) 1 else 0
    acidRainSpawnElapsed = 0.0
    acidRainSpawnInterval = FlowGamePacing.acidRainSpawnInterval(travel)
    currentTargetIndex = 0
    target = targets[0]
    resetCurrentInput()
    synchronizeActiveAcidRainTiming()
  }

  private fun spawnAcidRainCard() {
    if (acidRainCards.size >= maximumConcurrentAcidRainCardCount) return
    val travel = cardTravelDuration / FlowGamePacing.acidRainSpeedMultiplier(activeElapsed)
    acidRainCards = acidRainCards + AcidRainFallingCard(
      id = nextAcidRainCardId,
      itemIndex = nextAcidRainItemIndex,
      laneIndex = nextAcidRainCardId % 3,
      elapsed = 0.0,
      travelDuration = travel,
    )
    nextAcidRainCardId += 1
    nextAcidRainItemIndex = (nextAcidRainItemIndex + 1) % targets.size
    acidRainSpawnElapsed = max(0.0, acidRainSpawnElapsed - acidRainSpawnInterval)
    acidRainSpawnInterval = FlowGamePacing.acidRainSpawnInterval(travel)
    if (activeAcidRainCardId == null) selectMostUrgentAcidRainCard()
  }

  private fun removeCompletedAcidRainCard() {
    val active = activeAcidRainCardId ?: return
    acidRainCards = acidRainCards.filterNot { it.id == active }
    activeAcidRainCardId = null
    if (acidRainCards.isEmpty()) {
      acidRainSpawnElapsed = 0.0
      spawnAcidRainCard()
    }
    selectMostUrgentAcidRainCard()
  }

  private fun escapeAcidRainCard(id: Int) {
    val index = acidRainCards.indexOfFirst { it.id == id }
    if (index < 0) return
    val wasInputTarget = id == activeAcidRainCardId
    if (wasInputTarget) {
      if (currentWordMistakeCount > 0) {
        lastCompletedItem = SessionItemResolution(
          currentTargetIndex, true, currentWordMistakeCount, currentWordMistakenJamoIndices.toSet(),
        )
        reviewResolutionRevision += 1
      }
      completedAcceptedJamoCount += acceptedKeys.size
      activeAcidRainCardId = null
    }
    acidRainCards = acidRainCards.toMutableList().also { it.removeAt(index) }
    missedCardCount += 1
    remainingLives -= 1
    combo = 0
    feedback = FlowGameFeedback.Escaped
    feedbackRevision += 1
    if (remainingLives == 0) {
      finish()
      return
    }
    if (wasInputTarget) {
      if (acidRainCards.isEmpty()) {
        acidRainSpawnElapsed = 0.0
        spawnAcidRainCard()
      }
      selectMostUrgentAcidRainCard()
    }
  }

  private fun selectMostUrgentAcidRainCard() {
    // Swift `max(by:)` keeps the first of equal maxima (same as `maxByOrNull`).
    val best = acidRainCards.maxByOrNull { it.elapsed / it.travelDuration }
    if (best == null) {
      activeAcidRainCardId = null
      return
    }
    selectAcidRainCard(best.id, publishesCardChange = true)
  }

  private fun selectAcidRainCard(id: Int, publishesCardChange: Boolean) {
    val card = acidRainCards.firstOrNull { it.id == id } ?: return
    activeAcidRainCardId = card.id
    currentTargetIndex = card.itemIndex
    target = targets[card.itemIndex]
    resetCurrentInput()
    synchronizeActiveAcidRainTiming()
    if (publishesCardChange) cardRevision += 1
  }

  private fun synchronizeActiveAcidRainTiming() {
    val id = activeAcidRainCardId ?: return
    val card = acidRainCards.firstOrNull { it.id == id } ?: return
    activeCardElapsed = card.elapsed
    currentCardTravelDuration = card.travelDuration
  }

  private fun resetCurrentCard() {
    resetCurrentInput()
    activeCardElapsed = 0.0
    currentCardTravelDuration = cardTravelDuration / FlowGamePacing.flowSpeedMultiplier(activeElapsed)
  }

  private fun resetCurrentInput() {
    judgeState = JamoJudgeState(target)
    acceptedKeys.clear()
    composition = CompositionState()
    currentWordMistakeCount = 0
    currentWordMistakenJamoIndices.clear()
  }

  private fun rebuildJudgeFromAcceptedKeys() {
    var rebuilt = JamoJudgeState(target)
    for (key in acceptedKeys) rebuilt = JamoSequenceJudge.evaluate(key, rebuilt).state
    judgeState = rebuilt
  }

  private fun finish() {
    phase = GamePhase.FINISHED
    lastTick = null
  }

  private fun recordMistake(expected: Char) {
    mistakeCount += 1
    consecutiveMistakes += 1
    currentWordMistakeCount += 1
    currentWordMistakenJamoIndices += judgeState.currentIndex
    combo = 0
    feedback = FlowGameFeedback.Incorrect(expected)
    publishMascot(
      if (consecutiveMistakes >= 3) FlowGameMascotEvent.Dizzy(expected) else FlowGameMascotEvent.Startle(expected),
    )
  }

  private fun publishMascot(event: FlowGameMascotEvent) {
    mascotEvent = event
    mascotRevision += 1
  }

  companion object {
    private const val EPSILON = 0.000_001

    fun comboMultiplier(combo: Int): Double = when {
      combo >= 20 -> 2.0
      combo >= 10 -> 1.5
      combo >= 5 -> 1.2
      else -> 1.0
    }

    /** Combo tier for backgrounds/particles (iOS `comboTier`). */
    fun comboTier(combo: Int): Int = when {
      combo >= 20 -> 3
      combo >= 10 -> 2
      combo >= 5 -> 1
      else -> 0
    }

    fun isTypeable(text: String): Boolean = runCatching { JamoDecomposer.keySequence(text) }.isSuccess
  }
}

/** iOS `FlowLaneLayout`: the card slides from fully right to fully left. */
object FlowLaneLayout {
  fun horizontalOffset(progress: Double, containerWidth: Float, cardWidth: Float): Float {
    val clamped = progress.coerceIn(0.0, 1.0).toFloat()
    return containerWidth - (containerWidth + cardWidth) * clamped
  }
}

/** iOS `MovingWordCardLayout`: content-fitted card widths (dp). */
object MovingWordCardLayout {
  fun flowWidth(korean: String, meaning: String, reading: String, fontScale: Float, maximumWidth: Float): Float =
    preferredWidth(korean, meaning, reading, 25 * fontScale, 12f, 11f, 28f, 96f, maximumWidth)

  fun acidRainWidth(korean: String, meaning: String, reading: String, fontScale: Float, maximumWidth: Float): Float =
    preferredWidth(korean, meaning, reading, 21 * fontScale, 11f, 10f, 24f, 92f, maximumWidth)

  private fun preferredWidth(
    korean: String,
    meaning: String,
    reading: String,
    koreanPointSize: Float,
    meaningPointSize: Float,
    readingPointSize: Float,
    horizontalPadding: Float,
    minimumWidth: Float,
    maximumWidth: Float,
  ): Float {
    val content = maxOf(
      estimatedWidth(korean, koreanPointSize),
      estimatedWidth(meaning, meaningPointSize),
      estimatedWidth("[$reading]", readingPointSize),
    )
    return min(maximumWidth, max(minimumWidth, ceil(content + horizontalPadding)))
  }

  private fun estimatedWidth(text: String, pointSize: Float): Float {
    var width = 0f
    var index = 0
    while (index < text.length) {
      val codePoint = text.codePointAt(index)
      val unit = when {
        Character.isWhitespace(codePoint) -> 0.35f
        codePoint < 0x80 -> 0.58f
        else -> 1f
      }
      width += pointSize * unit
      index += Character.charCount(codePoint)
    }
    return width
  }
}

/** iOS `AcidRainLaneLayout` (dp). */
object AcidRainLaneLayout {
  const val DANGER_ZONE_HEIGHT = 52f
  private const val HORIZONTAL_INSET = 12f
  private const val ESTIMATED_CARD_HEIGHT = 90f
  private const val CARD_TOP_CLEARANCE = 48f
  private val laneFractions = floatArrayOf(0.08f, 0.5f, 0.92f)

  fun cardWidth(containerWidth: Float): Float = min(188f, containerWidth * 0.56f)

  fun cardCenterX(laneIndex: Int, containerWidth: Float, cardWidth: Float): Float {
    val room = max(0f, containerWidth - cardWidth - HORIZONTAL_INSET * 2)
    return cardWidth / 2 + HORIZONTAL_INSET + room * laneFractions[laneIndex % laneFractions.size]
  }

  fun cardCenterY(progress: Double, containerHeight: Float): Float {
    val half = ESTIMATED_CARD_HEIGHT / 2
    val startY = CARD_TOP_CLEARANCE + half
    val endY = max(startY, containerHeight - DANGER_ZONE_HEIGHT - half)
    return startY + (endY - startY) * progress.coerceIn(0.0, 1.0).toFloat()
  }
}

/** iOS `CompactGameHUDMetricLayout`. */
object CompactGameHudMetricLayout {
  const val ICON_COLUMN_WIDTH = 12f
  const val VALUE_HORIZONTAL_INSET = 14f
  const val MINIMUM_VALUE_SCALE = 0.55f

  fun valuePointSize(value: String): Float = when (value.length) {
    in 0..3 -> 15f
    in 4..5 -> 14f
    in 6..7 -> 12f
    else -> 10f
  }
}

data class GameFrameRateSnapshot(
  val sampleCount: Int,
  val averageFps: Double,
  val p95FrameDurationMilliseconds: Double,
  val worstFrameDurationMilliseconds: Double,
  val overBudgetFrameCount: Int,
)

/** iOS `GameFrameRateCalculator` (debug FPS probe); timestamps in seconds. */
object GameFrameRateCalculator {
  fun snapshot(timestamps: List<Double>): GameFrameRateSnapshot? {
    if (timestamps.size < 2) return null
    val intervals = timestamps.zipWithNext { a, b -> b - a }.filter { it > 0 }
    if (intervals.isEmpty()) return null
    val sorted = intervals.sorted()
    val p95Index = max(0, ceil(sorted.size * 0.95).toInt() - 1)
    return GameFrameRateSnapshot(
      sampleCount = intervals.size,
      averageFps = intervals.size / intervals.sum(),
      p95FrameDurationMilliseconds = sorted[p95Index] * 1_000,
      worstFrameDurationMilliseconds = sorted.last() * 1_000,
      overBudgetFrameCount = intervals.count { it > 0.020 },
    )
  }
}

/** iOS `GameFrameRateMonitor`: rolling window, publishes every 30 frames once 121 samples exist. */
class GameFrameRateMonitor(sampleLimit: Int = 600) {
  private val limit = max(120, sampleLimit)
  private val timestamps = ArrayDeque<Double>()
  private var framesSincePublish = 0

  var snapshot: GameFrameRateSnapshot? = null
    private set

  /** Returns true when [snapshot] changed. */
  fun record(timestamp: Double): Boolean {
    timestamps.addLast(timestamp)
    while (timestamps.size > limit + 1) timestamps.removeFirst()
    framesSincePublish += 1
    if (timestamps.size < 121 || framesSincePublish < 30) return false
    snapshot = GameFrameRateCalculator.snapshot(timestamps.toList())
    framesSincePublish = 0
    return true
  }

  fun reset() {
    timestamps.clear()
    framesSincePublish = 0
    snapshot = null
  }
}
