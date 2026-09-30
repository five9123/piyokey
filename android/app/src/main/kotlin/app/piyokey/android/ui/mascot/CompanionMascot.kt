package app.piyokey.android.ui.mascot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.piyokey.android.R
import app.piyokey.android.data.mascot.MascotCompanionStore
import app.piyokey.android.data.mascot.MascotFanColor
import app.piyokey.android.data.mascot.MascotGrowthAppearance
import app.piyokey.android.data.mascot.MascotMood
import app.piyokey.android.data.mascot.MascotPose
import app.piyokey.android.data.mascot.MascotProp
import app.piyokey.android.data.mascot.MascotReaction
import app.piyokey.android.data.mascot.MascotRules
import app.piyokey.android.data.mascot.MascotStore
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private val JST: ZoneId = ZoneId.of("Asia/Tokyo")

/** JST day raw value (`yyyy-MM-dd`, iOS `JSTDay.rawValue`). */
internal fun jstDayRaw(): String = LocalDate.now(JST).toString()

internal fun jstHour(): Int = ZonedDateTime.now(JST).hour

/**
 * iOS `GrowingMascotView`: the companion as it lives in the app — stage from cleared chapters
 * (gated by celebrations), closet selection, egg pattern, growth axes, pet events, the home
 * time-of-day mood and the once-per-day streak-break sulk.
 */
@Composable
fun GrowingMascot(
  modifier: Modifier = Modifier,
  mood: MascotMood = MascotMood.IDLE,
  reaction: MascotReaction = MascotReaction.None,
  reactionRevision: Int = 0,
  automaticProp: MascotProp = MascotProp.NONE,
  sourceTags: List<String> = emptyList(),
  gazeX: Float = 0f,
  gazeY: Float = 0f,
  pose: MascotPose = MascotPose.FRONT,
  intensity: Float = 0f,
  showsNameTag: Boolean = false,
  speech: String? = null,
  showsFriend: Boolean = false,
  usesHomeTimeMood: Boolean = false,
  interactive: Boolean = false,
  onOpenCloset: (() -> Unit)? = null,
  size: Dp = 96.dp,
  calm: Boolean = false,
  store: MascotCompanionStore = MascotStore,
) {
  val signals by store.signals.collectAsState()
  val celebratedRank by store.celebratedRank.collectAsState()
  val selection by store.selection.collectAsState()
  val eggPattern by store.eggPattern.collectAsState()
  val typedJamo by store.typedJamoCount.collectAsState()
  val lessonClears by store.consecutiveLessonClears.collectAsState()
  val event by store.event.collectAsState()
  val name by store.name.collectAsState()

  var showsStreakBreakMood by remember { mutableStateOf(false) }
  LaunchedEffect(usesHomeTimeMood, signals.streakKnown) {
    if (usesHomeTimeMood && signals.streakKnown) {
      showsStreakBreakMood = store.consumeStreakBreak(signals.currentStreak, jstDayRaw())
    }
  }

  val presentedStage = MascotRules.presentedStage(celebratedRank, signals.stage)
  val effectiveMood = MascotRules.effectiveMood(
    mood = mood,
    showsStreakBreakMood = showsStreakBreakMood,
    usesHomeTimeMood = usesHomeTimeMood,
    consecutiveLessonClears = lessonClears,
    jstHour = jstHour(),
  )
  val defaultName = stringResource(R.string.mascot_default_name)
  val usesStoreEvent = reaction == MascotReaction.None

  ChickMascot(
    modifier = modifier,
    mood = effectiveMood,
    stage = presentedStage,
    prop = selection.resolve(MascotRules.automaticProp(presentedStage, automaticProp)),
    eggPattern = eggPattern,
    gazeX = gazeX,
    gazeY = gazeY,
    reaction = if (usesStoreEvent) event.reaction else reaction,
    reactionRevision = if (usesStoreEvent) event.revision else reactionRevision,
    pose = pose,
    intensity = intensity,
    growthAppearance = MascotGrowthAppearance.from(
      typedJamoCount = typedJamo,
      longestStreak = signals.longestStreak,
      activeDaysInLastWeek = signals.activeDaysInLastWeek,
      completedChapterCount = signals.clearedChapters,
    ),
    fanColor = MascotFanColor.forDeckTags(sourceTags),
    nameTag = if (showsNameTag) store.displayName(name, defaultName) else null,
    speech = speech,
    showsFriend = showsFriend,
    interactive = interactive,
    onOpenCloset = onOpenCloset,
    size = size,
    calm = calm,
  )
}

/** Convenience companion that reads [MascotStore] with iOS `GrowingMascotView` defaults. */
@Composable
fun CompanionMascot(
  modifier: Modifier = Modifier,
  size: Dp = 96.dp,
  mood: MascotMood = MascotMood.IDLE,
) {
  GrowingMascot(modifier = modifier, mood = mood, size = size)
}
