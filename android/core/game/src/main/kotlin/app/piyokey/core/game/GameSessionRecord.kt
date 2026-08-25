package app.piyokey.core.game

data class GameSessionRecord(
  val mode: String,
  val deckId: String,
  val course: String,
  val inputMode: String,
  val score: Int,
  val maxCombo: Int,
  val accuracyPercent: Double,
  val correctJamoCount: Int,
  val ratePerMinute: Double,
  val mistakeCount: Int,
  val completedItemCount: Int,
  val missedItemCount: Int,
  val playDurationMillis: Long,
  val playedAtEpochMillis: Long,
)

fun TypingGameState.toRecord(deckId: String, inputMode: String, playedAtEpochMillis: Long): GameSessionRecord = GameSessionRecord(
  mode = mode.name.lowercase(),
  deckId = deckId,
  course = courseForRecord(deckId),
  inputMode = inputMode,
  score = score,
  maxCombo = maxCombo,
  accuracyPercent = accuracyPercent,
  correctJamoCount = correctJamoCount,
  ratePerMinute = questionsPerMinute,
  mistakeCount = mistakeCount,
  completedItemCount = completedItemCount,
  missedItemCount = imperfectItemCount,
  playDurationMillis = activeElapsedMillis,
  playedAtEpochMillis = playedAtEpochMillis,
)

fun AcidRainState.toRecord(inputMode: String, playedAtEpochMillis: Long): GameSessionRecord = GameSessionRecord(
  mode = "acid_rain",
  deckId = deckId,
  course = course,
  inputMode = inputMode,
  score = score,
  maxCombo = maxCombo,
  accuracyPercent = accuracyPercent,
  correctJamoCount = correctJamoCount,
  ratePerMinute = if (playElapsedMillis == 0L) 0.0 else completedItemCount.toDouble() / playElapsedMillis * 60_000.0,
  mistakeCount = mistakeCount,
  completedItemCount = completedItemCount,
  missedItemCount = missedItemCount,
  playDurationMillis = playElapsedMillis,
  playedAtEpochMillis = playedAtEpochMillis,
)

fun SpacingGameState.toRecord(passageId: String, playedAtEpochMillis: Long): GameSessionRecord {
  val evaluation = requireNotNull(result) { "Spacing result is not ready" }
  return GameSessionRecord(
    mode = "spacing",
    deckId = passageId,
    course = "level",
    inputMode = "spacing_controls",
    score = evaluation.score,
    maxCombo = 0,
    accuracyPercent = evaluation.firstDecisionAccuracyPercent,
    correctJamoCount = evaluation.correctSpaceCount,
    ratePerMinute = 0.0,
    mistakeCount = evaluation.missedSpaceCount + evaluation.extraSpaceCount,
    completedItemCount = 1,
    missedItemCount = evaluation.mistakes.size,
    playDurationMillis = activeElapsedMillis,
    playedAtEpochMillis = playedAtEpochMillis,
  )
}

private fun courseForRecord(deckId: String): String = when {
  deckId.endsWith("beginner") -> "beginner"
  deckId.endsWith("intermediate") -> "intermediate"
  deckId.endsWith("advanced") -> "advanced"
  else -> "custom"
}
