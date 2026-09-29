package app.piyokey.android.feature.practice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ArrowCircleRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.piyokey.android.R
import app.piyokey.android.data.AppData
import app.piyokey.android.data.curriculum.CurriculumContent
import app.piyokey.android.data.mascot.MascotMood
import app.piyokey.android.data.mascot.MascotStage
import app.piyokey.android.data.mascot.MascotStore
import app.piyokey.android.feature.onboarding.AppTourTarget
import app.piyokey.android.feature.onboarding.appTourTarget
import app.piyokey.android.feature.settings.SettingsGearButton
import app.piyokey.android.ui.mascot.GrowingMascot
import app.piyokey.android.ui.mascot.GrowthCelebrationRoute
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.session.SessionColors
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.ui.theme.centeredContent
import app.piyokey.core.domain.CurriculumCatalog
import app.piyokey.core.domain.CurriculumChapter
import app.piyokey.core.domain.CurriculumStage
import app.piyokey.core.domain.CurriculumStageProgress
import app.piyokey.core.domain.CurriculumUnlockPolicy
import app.piyokey.core.domain.HatchOnboardingPolicy
import kotlinx.coroutines.delay

object CurriculumTags {
  const val MAP_SCREEN = "curriculum.map.screen"
  const val HATCH_SCREEN = "onboarding.hatch.screen"
  const val HATCH_CONTINUE = "onboarding.hatch.continue"
  const val HATCH_OPEN_APP = "onboarding.hatch.open_app"
  const val PERSISTENCE_BANNER = "persistence.failure.banner"
  fun stage(id: String) = "curriculum.stage.$id"
  fun lockedStage(id: String) = "curriculum.stage.$id.locked"
  fun hatchStage(id: String) = "onboarding.hatch.stage.$id"
}

/**
 * iOS `CurriculumMapView`: six chapters of stage cards with stars, locks (chapters 1–4 sequential,
 * 5+ free choice) and "resume". In hatch-onboarding mode only chapters 1–3 are shown with a
 * mission header whose button starts [HatchMissionSequenceRoute].
 */
@Composable
fun CurriculumMapScreen(
  isHatchOnboarding: Boolean,
  modifier: Modifier = Modifier,
  onHatchCompleted: () -> Unit = {},
) {
  val metrics = Piyo.metrics
  val appNavigator = LocalAppNavigator.current
  val stageProgress by AppData.curriculum.stageProgress.collectAsState()
  val activeSession by AppData.curriculum.activeSession.collectAsState()
  val saveFailed by AppData.curriculum.saveFailed.collectAsState()
  val completed = stageProgress.keys
  val cleared = PracticeGrowth.clearedChapterCount(completed)

  LaunchedEffect(cleared) { MascotStore.updateClearedChapters(cleared) }

  // iOS `schedulePendingGrowthCelebration` on appear (here: whenever the map is uncovered).
  val uncovered = appNavigator.depth == 0
  LaunchedEffect(uncovered, cleared) {
    if (!uncovered) return@LaunchedEffect
    delay(550)
    val pending = MascotStore.pendingCelebration(MascotStage.forClearedChapters(cleared)) ?: return@LaunchedEffect
    appNavigator.push(
      GrowthCelebrationRoute(pending) {
        MascotStore.markCelebrated(pending)
        appNavigator.pop()
      },
    )
  }

  Column(
    modifier
      .fillMaxSize()
      .testTag(if (isHatchOnboarding) CurriculumTags.HATCH_SCREEN else CurriculumTags.MAP_SCREEN),
  ) {
    MapTopBar(stringResource(if (isHatchOnboarding) R.string.onboarding_hatch_navigation_title else R.string.curriculum_navigation_title))
    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .centeredContent(metrics.readableContentMaxWidth)
        .padding(horizontal = metrics.horizontalPadding, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      if (isHatchOnboarding) {
        HatchMissionHeader(completed, onHatchCompleted)
        if (saveFailed) PersistenceRecoveryBanner { AppData.curriculum.retryLastSave() }
      }
      val chapters = if (isHatchOnboarding) CurriculumCatalog.chapters.take(HatchOnboardingPolicy.REQUIRED_CHAPTER_COUNT) else CurriculumCatalog.chapters
      chapters.forEach { chapter ->
        ChapterSection(chapter, isHatchOnboarding, stageProgress, activeSession?.stageId, completed)
      }
    }
  }
}

@Composable
private fun MapTopBar(title: String) {
  Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 8.dp)) {
    Text(
      title,
      style = PiyoType.headline().copy(fontWeight = FontWeight.Bold),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
      modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp).semantics { heading() },
    )
    SettingsGearButton(Modifier.align(Alignment.CenterEnd))
  }
}

@Composable
private fun HatchMissionHeader(completed: Set<String>, onHatchCompleted: () -> Unit) {
  val colors = Piyo.colors
  val appNavigator = LocalAppNavigator.current
  val done = HatchOnboardingPolicy.requiredStages.count { it.id in completed }
  val total = HatchOnboardingPolicy.REQUIRED_CHAPTER_COUNT
  val next = HatchOnboardingPolicy.nextRequiredStage(completed)
  val shape = RoundedCornerShape(28.dp)
  Column(
    Modifier
      .fillMaxWidth()
      .shadow(14.dp, shape, ambientColor = colors.keyShadow, spotColor = colors.keyShadow)
      .clip(shape)
      .background(colors.card)
      .padding(18.dp),
    verticalArrangement = Arrangement.spacedBy(15.dp),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(96.dp, 128.dp), contentAlignment = Alignment.Center) {
        GrowingMascot(mood = if (done == 0) MascotMood.IDLE else MascotMood.HAPPY, size = 74.dp, calm = true)
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.onboarding_hatch_title), style = PiyoType.title2().copy(fontWeight = FontWeight.Black))
        Text(stringResource(R.string.onboarding_hatch_subtitle), style = PiyoType.subheadline().copy(color = colors.mutedInk))
      }
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
      Row {
        Text(stringResource(R.string.onboarding_hatch_progress_label), style = PiyoType.caption().copy(color = colors.secondary, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        Text(stringResource(R.string.onboarding_hatch_progress_format, done, total), style = PiyoType.caption().copy(color = colors.accent, fontWeight = FontWeight.Black))
      }
      LinearProgressIndicator(
        progress = { done.toFloat() / total },
        modifier = Modifier.fillMaxWidth(),
        color = colors.accent,
        trackColor = colors.accentSoft.copy(alpha = 0.6f),
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
      )
    }
    if (next != null) {
      HeaderButton(
        stringResource(R.string.onboarding_hatch_continue_format, next.chapterNumber),
        Icons.Rounded.ArrowCircleRight,
        colors.accent,
        CurriculumTags.HATCH_CONTINUE,
      ) { appNavigator.push(HatchMissionSequenceRoute(next.id, onHatchCompleted)) }
    } else {
      HeaderButton(stringResource(R.string.onboarding_hatch_open_app), Icons.Rounded.AutoAwesome, colors.success, CurriculumTags.HATCH_OPEN_APP, onHatchCompleted)
    }
  }
}

@Composable
private fun HeaderButton(title: String, icon: ImageVector, background: Color, testTag: String, onClick: () -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .defaultMinSize(minHeight = 50.dp)
      .clip(RoundedCornerShape(17.dp))
      .background(background)
      .clickable(role = Role.Button, onClick = onClick)
      .padding(vertical = 14.dp)
      .testTag(testTag),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    Spacer(Modifier.size(6.dp))
    Text(title, style = PiyoType.headline().copy(color = Color.White, fontWeight = FontWeight.Bold))
  }
}

/** iOS `PersistenceRecoveryBanner`. */
@Composable
fun PersistenceRecoveryBanner(onRetry: () -> Unit) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(18.dp)
  Row(
    Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(SessionColors.Orange.copy(alpha = 0.11f))
      .border(1.dp, SessionColors.Orange.copy(alpha = 0.3f), shape)
      .padding(14.dp)
      .testTag(CurriculumTags.PERSISTENCE_BANNER),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Rounded.Lock, contentDescription = null, tint = SessionColors.Orange, modifier = Modifier.size(22.dp))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(stringResource(R.string.persistence_failure_title), style = PiyoType.subheadline().copy(fontWeight = FontWeight.Bold))
      Text(stringResource(R.string.persistence_failure_detail), style = PiyoType.caption().copy(color = colors.mutedInk))
    }
    Text(
      stringResource(R.string.persistence_failure_retry),
      style = PiyoType.subheadline().copy(color = colors.accent, fontWeight = FontWeight.Bold),
      modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp).clickable(role = Role.Button, onClick = onRetry).padding(vertical = 12.dp),
    )
  }
}

@Composable
private fun ChapterSection(
  chapter: CurriculumChapter,
  isHatchOnboarding: Boolean,
  progress: Map<String, CurriculumStageProgress>,
  activeStageId: String?,
  completed: Set<String>,
) {
  val colors = Piyo.colors
  val appNavigator = LocalAppNavigator.current
  Column(
    Modifier.fillMaxWidth().appTourTarget(AppTourTarget.PRACTICE_CURRICULUM, enabled = !isHatchOnboarding && chapter.number == 1),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        stringResource(R.string.curriculum_chapter_number_format, chapter.number),
        style = PiyoType.caption().copy(color = Color.White, fontWeight = FontWeight.Black),
        modifier = Modifier.clip(CircleShape).background(colors.secondary).padding(horizontal = 9.dp, vertical = 5.dp),
      )
      Spacer(Modifier.size(8.dp))
      Text(CurriculumContent.title(chapter), style = PiyoType.headline().copy(fontWeight = FontWeight.ExtraBold), modifier = Modifier.weight(1f))
      if (chapter.number >= 5) {
        Text(stringResource(R.string.curriculum_free_selection), style = PiyoType.caption2().copy(color = colors.secondary, fontWeight = FontWeight.Bold))
      }
    }
    chapter.stages.forEach { stage ->
      if (isHatchOnboarding) {
        val isCurrent = HatchOnboardingPolicy.nextRequiredStage(completed)?.id == stage.id
        CurriculumStageRow(
          stage = stage,
          stageProgress = progress[stage.id],
          isResumable = activeStageId == stage.id,
          isLocked = stage.id !in completed && !isCurrent,
          isHighlighted = isCurrent,
          modifier = Modifier.testTag(CurriculumTags.hatchStage(stage.id)),
        )
      } else if (CurriculumUnlockPolicy.isUnlocked(stage, completed)) {
        CurriculumStageRow(
          stage = stage,
          stageProgress = progress[stage.id],
          isResumable = activeStageId == stage.id,
          isLocked = false,
          modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Button) { PracticeLauncher.curriculumStage(appNavigator, stage.id) }
            .testTag(CurriculumTags.stage(stage.id)),
        )
      } else {
        val locked = stringResource(R.string.curriculum_locked)
        CurriculumStageRow(
          stage = stage,
          stageProgress = null,
          isResumable = false,
          isLocked = true,
          modifier = Modifier.testTag(CurriculumTags.lockedStage(stage.id)).semantics(mergeDescendants = true) { stateDescription = locked },
        )
      }
    }
  }
}

@Composable
private fun CurriculumStageRow(
  stage: CurriculumStage,
  stageProgress: CurriculumStageProgress?,
  isResumable: Boolean,
  isLocked: Boolean,
  modifier: Modifier = Modifier,
  isHighlighted: Boolean = false,
) {
  val colors = Piyo.colors
  val stars = stageProgress?.stars ?: 0
  val shape = RoundedCornerShape(22.dp)
  Row(
    modifier
      .fillMaxWidth()
      .clip(shape)
      .background(if (isLocked) colors.card.copy(alpha = colors.card.alpha * 0.62f) else colors.card)
      .border(2.dp, if (isResumable || isHighlighted) colors.accent.copy(alpha = 0.55f) else Color.Transparent, shape)
      .padding(16.dp)
      .semantics(mergeDescendants = true) {},
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    StageStamp(if (isLocked) Icons.Rounded.Lock else stageIcon(stage.symbol), stageProgress != null, isLocked)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
      Text(CurriculumContent.title(stage), style = PiyoType.headline().copy(fontWeight = FontWeight.Bold, color = if (isLocked) colors.mutedInk else colors.ink))
      Text(CurriculumContent.detail(stage), style = PiyoType.caption().copy(color = colors.mutedInk), maxLines = 2, overflow = TextOverflow.Ellipsis)
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
          Icon(
            if (index < stars) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = null,
            tint = if (index < stars) SessionColors.Yellow else colors.mutedInk.copy(alpha = 0.28f),
            modifier = Modifier.size(14.dp),
          )
        }
        if (isResumable) {
          Text(
            stringResource(R.string.curriculum_resume),
            style = PiyoType.caption2().copy(color = colors.accent, fontWeight = FontWeight.Black),
            modifier = Modifier.padding(start = 5.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun StageStamp(icon: ImageVector, isCompleted: Boolean, isLocked: Boolean) {
  val colors = Piyo.colors
  val scale = remember { Animatable(0.72f) }
  LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = 340f)) }
  val borderColor = if (isCompleted) colors.accent else colors.mutedInk.copy(alpha = 0.25f)
  Box(Modifier.size(58.dp).scale(scale.value), contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize()) {
      drawCircle(if (isCompleted) colors.accentSoft.copy(alpha = 0.8f) else colors.backgroundBottom.copy(alpha = if (isLocked) 0.45f else 0.9f))
      val stroke = 2.dp.toPx()
      drawCircle(
        borderColor,
        radius = size.minDimension / 2 - stroke / 2,
        style = Stroke(stroke, pathEffect = if (isCompleted) null else PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
      )
    }
    Icon(
      if (isCompleted) Icons.Rounded.Verified else icon,
      contentDescription = null,
      tint = if (isCompleted) colors.accent else colors.mutedInk.copy(alpha = if (isLocked) 0.45f else 0.8f),
      modifier = Modifier.size(26.dp),
    )
  }
}

/** SF Symbol names from the curriculum catalog → Material icons. */
fun stageIcon(symbol: String): ImageVector = when (symbol) {
  "character.book.closed.fill" -> Icons.AutoMirrored.Rounded.MenuBook
  "textformat.abc" -> Icons.Rounded.TextFields
  "square.grid.3x3.fill" -> Icons.Rounded.GridOn
  "rectangle.bottomhalf.filled" -> Icons.Rounded.ViewDay
  "text.book.closed.fill" -> Icons.Rounded.AutoStories
  "airplane.circle.fill" -> Icons.Rounded.Flight
  "briefcase.fill" -> Icons.Rounded.Work
  "keyboard.fill" -> Icons.Rounded.Keyboard
  "text.bubble.fill" -> Icons.AutoMirrored.Rounded.Chat
  "cup.and.saucer.fill" -> Icons.Rounded.LocalCafe
  "heart.fill" -> Icons.Rounded.Favorite
  else -> Icons.Rounded.Star
}

