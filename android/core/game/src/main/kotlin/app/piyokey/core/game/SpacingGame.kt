package app.piyokey.core.game

data class SpacingPassage(val id: String, val level: Int, val text: String) {
  val characterCount: Int get() = text.length
  val spaceCount: Int get() = text.count(Char::isWhitespace)
}

data class SpacingMistake(
  val boundary: Int,
  val choseSpace: Boolean,
  val expectedSpace: Boolean,
  val context: String,
)

data class SpacingEvaluation(
  val submittedText: String,
  val correctSpaceCount: Int,
  val missedSpaceCount: Int,
  val extraSpaceCount: Int,
  val firstDecisionAccuracyPercent: Double,
  val finalAccuracyPercent: Double,
  val correctionCount: Int,
  val score: Int,
  val mistakes: List<SpacingMistake>,
)

class SpacingEngine(answer: String) {
  val compactText: String
  val correctBoundaries: Set<Int>
  val boundaryCount: Int get() = (compactText.length - 1).coerceAtLeast(0)

  init {
    val parsed = parse(answer)
    require(parsed.first.isNotEmpty()) { "A spacing passage cannot be empty" }
    require(parsed.second.isNotEmpty()) { "A spacing passage needs at least one space" }
    compactText = parsed.first
    correctBoundaries = parsed.second
  }

  fun render(boundaries: Set<Int>): String = buildString {
    compactText.forEachIndexed { index, character ->
      if (index in boundaries) append(' ')
      append(character)
    }
  }

  fun normalize(candidate: String): String? {
    val parsed = parse(candidate)
    return if (parsed.first == compactText) render(parsed.second) else null
  }

  fun evaluate(selected: Set<Int>, firstDecisions: Map<Int, Boolean>, correctionCount: Int): SpacingEvaluation {
    val valid = selected.filterTo(mutableSetOf()) { it in 1..boundaryCount }
    val correct = valid.intersect(correctBoundaries).size
    val missed = correctBoundaries.subtract(valid).size
    val extra = valid.subtract(correctBoundaries).size
    val finalDenominator = (correctBoundaries.size + extra).coerceAtLeast(1)
    val firstCorrect = (1..boundaryCount).count { boundary -> firstDecisions[boundary] == correctBoundaries.contains(boundary) }
    val firstAccuracy = firstCorrect.toDouble() / boundaryCount.coerceAtLeast(1) * 100.0
    val mistakes = (1..boundaryCount).mapNotNull { boundary ->
      val chose = firstDecisions[boundary] ?: return@mapNotNull null
      val expected = boundary in correctBoundaries
      if (chose == expected) null else SpacingMistake(boundary, chose, expected, context(boundary))
    }.take(8)
    return SpacingEvaluation(
      submittedText = render(valid),
      correctSpaceCount = correct,
      missedSpaceCount = missed,
      extraSpaceCount = extra,
      firstDecisionAccuracyPercent = firstAccuracy,
      finalAccuracyPercent = correct.toDouble() / finalDenominator * 100.0,
      correctionCount = correctionCount.coerceAtLeast(0),
      score = (firstAccuracy * 10).toInt(),
      mistakes = mistakes,
    )
  }

  private fun context(boundary: Int): String {
    val start = (boundary - 6).coerceAtLeast(0)
    val end = (boundary + 6).coerceAtMost(compactText.length)
    return compactText.substring(start, end)
  }

  private fun parse(text: String): Pair<String, Set<Int>> {
    val characters = StringBuilder()
    val boundaries = mutableSetOf<Int>()
    var pendingSpace = false
    text.forEach { character ->
      if (character.isWhitespace()) {
        if (characters.isNotEmpty()) pendingSpace = true
      } else {
        if (pendingSpace) boundaries += characters.length
        characters.append(character)
        pendingSpace = false
      }
    }
    return characters.toString() to boundaries
  }
}

data class SpacingGameState(
  val engine: SpacingEngine,
  val cursor: Int = 1,
  val selectedBoundaries: Set<Int> = emptySet(),
  val firstDecisions: Map<Int, Boolean> = emptyMap(),
  val correctionCount: Int = 0,
  val activeStartedAtMillis: Long? = null,
  val activeElapsedMillis: Long = 0,
  val paused: Boolean = false,
  val result: SpacingEvaluation? = null,
)

sealed interface SpacingEvent {
  data class Start(val nowMillis: Long) : SpacingEvent
  data object Previous : SpacingEvent
  data object Next : SpacingEvent
  data object ToggleSpace : SpacingEvent
  data class Pause(val nowMillis: Long) : SpacingEvent
  data class Resume(val nowMillis: Long) : SpacingEvent
  data class Submit(val nowMillis: Long) : SpacingEvent
}

object SpacingReducer {
  fun reduce(state: SpacingGameState, event: SpacingEvent): SpacingGameState = when (event) {
    is SpacingEvent.Start -> if (state.activeStartedAtMillis == null && state.result == null) state.copy(activeStartedAtMillis = event.nowMillis, paused = false) else state
    SpacingEvent.Previous -> if (state.result == null) state.copy(cursor = (state.cursor - 1).coerceAtLeast(1)) else state
    SpacingEvent.Next -> if (state.result == null) {
      val decisions = if (state.cursor !in state.firstDecisions) state.firstDecisions + (state.cursor to false) else state.firstDecisions
      state.copy(cursor = (state.cursor + 1).coerceAtMost(state.engine.boundaryCount), firstDecisions = decisions)
    } else state
    SpacingEvent.ToggleSpace -> toggle(state)
    is SpacingEvent.Pause -> consume(state, event.nowMillis).copy(activeStartedAtMillis = null, paused = true)
    is SpacingEvent.Resume -> if (state.paused && state.result == null) state.copy(activeStartedAtMillis = event.nowMillis, paused = false) else state
    is SpacingEvent.Submit -> {
      val timed = consume(state, event.nowMillis)
      val decisions = (1..state.engine.boundaryCount).associateWith { boundary -> timed.firstDecisions[boundary] ?: false }
      timed.copy(activeStartedAtMillis = null, result = state.engine.evaluate(timed.selectedBoundaries, decisions, timed.correctionCount))
    }
  }

  private fun toggle(state: SpacingGameState): SpacingGameState {
    if (state.result != null || state.cursor !in 1..state.engine.boundaryCount) return state
    val adding = state.cursor !in state.selectedBoundaries
    val selected = if (adding) state.selectedBoundaries + state.cursor else state.selectedBoundaries - state.cursor
    val first = if (state.cursor !in state.firstDecisions) state.firstDecisions + (state.cursor to adding) else state.firstDecisions
    return state.copy(
      selectedBoundaries = selected,
      firstDecisions = first,
      correctionCount = state.correctionCount + if (state.cursor in state.firstDecisions) 1 else 0,
    )
  }

  private fun consume(state: SpacingGameState, now: Long): SpacingGameState {
    val started = state.activeStartedAtMillis ?: return state
    return state.copy(activeElapsedMillis = state.activeElapsedMillis + (now - started).coerceAtLeast(0), activeStartedAtMillis = now)
  }
}
