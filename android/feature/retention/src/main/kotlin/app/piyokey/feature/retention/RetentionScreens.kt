package app.piyokey.feature.retention

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.piyokey.core.data.ReminderPreferenceEntity
import app.piyokey.core.data.UserProgressEntity
import app.piyokey.core.design.PiyoAvatar
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.retention.CurriculumCatalog
import app.piyokey.core.retention.CurriculumPolicy
import app.piyokey.core.retention.CurriculumStage
import app.piyokey.core.retention.JstDay
import app.piyokey.core.retention.RetentionPolicy
import app.piyokey.core.retention.ReviewItem
import app.piyokey.core.retention.StampState
import app.piyokey.core.settings.PiyoAccessory
import app.piyokey.core.settings.PiyoSessionAppearance
import app.piyokey.core.settings.PiyoWardrobePolicy

private val Background = Color(0xFFFFF9F1)
private val Pink = Color(0xFFFFE4EC)
private val Yellow = Color(0xFFFFD65A)

data class ManualReviewCandidate(
  val sourceDeckId: String,
  val deckName: String,
  val item: DeckItem,
)

@Composable
fun RetentionHomeCard(
  today: JstDay,
  completedDays: Set<JstDay>,
  appearance: PiyoSessionAppearance,
  onOpenProfile: () -> Unit,
  onDailyChallenge: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val week = RetentionPolicy.week(today, completedDays)
  val streak = RetentionPolicy.streak(completedDays, today)
  val message = stringArrayResource(R.array.retention_encouragements)[
    RetentionPolicy.encouragementIndex(today, completedDays)
  ]
  val weekdays = stringArrayResource(R.array.retention_weekdays)
  Column(modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("retention-profile-card")
        .clickable(onClick = onOpenProfile),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = RoundedCornerShape(24.dp),
    ) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          PiyoAvatar(
            appearance = appearance,
            contentDescription = stringResource(R.string.retention_piyo_accessibility),
            modifier = Modifier.size(64.dp),
          )
          Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.retention_my_piyo), fontWeight = FontWeight.Black)
            Surface(color = Pink, shape = RoundedCornerShape(14.dp)) {
              Text("「$message」", Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
            }
          }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          week.forEach { stamp ->
            val symbol = when (stamp.state) {
              StampState.COMPLETED -> "✓"
              StampState.MISSED -> "·"
              StampState.TODAY_PENDING -> "○"
              StampState.UPCOMING -> "–"
            }
            val stateLabel = stringResource(
              when (stamp.state) {
                StampState.COMPLETED -> R.string.retention_stamp_completed
                StampState.MISSED -> R.string.retention_stamp_missed
                StampState.TODAY_PENDING -> R.string.retention_stamp_today
                StampState.UPCOMING -> R.string.retention_stamp_upcoming
              },
            )
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.semantics { contentDescription = "${stamp.day.value}, $stateLabel" },
            ) {
              Text(weekdays[stamp.day.date.dayOfWeek.value - 1], style = MaterialTheme.typography.labelSmall)
              Surface(
                modifier = Modifier.size(30.dp),
                shape = CircleShape,
                color = if (stamp.state == StampState.COMPLETED) Yellow else Color(0xFFF1EDE7),
              ) { Box(contentAlignment = Alignment.Center) { Text(symbol, fontWeight = FontWeight.Bold) } }
            }
          }
        }
        Text(
          stringResource(
            R.string.retention_week_progress,
            week.count { it.state == StampState.COMPLETED },
            streak.current,
          ),
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
    Button(
      onClick = onDailyChallenge,
      modifier = Modifier.fillMaxWidth().testTag("retention-daily-challenge"),
    ) { Text(stringResource(R.string.retention_daily_cta)) }
  }
}

@Composable
fun PiyoProfileSection(
  today: JstDay,
  completedDays: Set<JstDay>,
  unlockedRewards: Set<Int>,
  appearance: PiyoSessionAppearance,
  selectedAccessory: PiyoAccessory,
  unlockedAccessories: Set<PiyoAccessory>,
  onAccessorySelected: (PiyoAccessory) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showWardrobe by remember { mutableStateOf(false) }
  val streak = RetentionPolicy.streak(completedDays, today)
  val message = stringArrayResource(R.array.retention_encouragements)[
    RetentionPolicy.encouragementIndex(today, completedDays)
  ]
  Card(
    modifier = modifier.fillMaxWidth().testTag("piyo-profile-detail"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(24.dp),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PiyoAvatar(
          appearance = appearance,
          contentDescription = stringResource(R.string.retention_piyo_accessibility),
          modifier = Modifier.size(72.dp),
        )
        Column(Modifier.weight(1f)) {
          Text(stringResource(R.string.retention_my_piyo), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
          Surface(color = Pink, shape = RoundedCornerShape(14.dp)) {
            Text("「$message」", Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
          }
        }
        TextButton(
          onClick = { showWardrobe = true },
          modifier = Modifier.size(48.dp).testTag("piyo-wardrobe-open"),
          contentPadding = PaddingValues(0.dp),
        ) { Text("♧", style = MaterialTheme.typography.headlineSmall) }
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        ProfileMetric(stringResource(R.string.retention_current_streak), streak.current)
        ProfileMetric(stringResource(R.string.retention_longest_streak), streak.longest)
        ProfileMetric(stringResource(R.string.retention_total_stamps), completedDays.size)
      }
      Text(stringResource(R.string.retention_rewards), fontWeight = FontWeight.Bold)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RetentionPolicy.rewardThresholds.sorted().forEach { threshold ->
          Surface(
            modifier = Modifier.weight(1f),
            color = if (threshold in unlockedRewards) Yellow else Color(0xFFF1EDE7),
            shape = RoundedCornerShape(14.dp),
          ) {
            Text(
              stringResource(
                if (threshold in unlockedRewards) R.string.retention_reward_unlocked else R.string.retention_reward_locked,
                threshold,
              ),
              modifier = Modifier.padding(10.dp),
              textAlign = TextAlign.Center,
              style = MaterialTheme.typography.labelMedium,
            )
          }
        }
      }
    }
  }
  if (showWardrobe) {
    AlertDialog(
      onDismissRequest = { showWardrobe = false },
      title = { Text(stringResource(R.string.wardrobe_title)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          PiyoAvatar(
            appearance = appearance.copy(
              accessory = PiyoWardrobePolicy.resolvedAccessory(selectedAccessory, unlockedAccessories),
            ),
            contentDescription = stringResource(R.string.wardrobe_preview),
            modifier = Modifier.size(140.dp).align(Alignment.CenterHorizontally),
          )
          PiyoWardrobePolicy.selectableAccessories(unlockedAccessories).forEach { accessory ->
            OutlinedButton(
              onClick = { onAccessorySelected(accessory) },
              modifier = Modifier.fillMaxWidth().testTag("piyo-accessory-${accessory.name.lowercase()}"),
            ) {
              Text("${if (selectedAccessory == accessory) "✓ " else ""}${accessoryLabel(accessory)}")
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showWardrobe = false }) { Text(stringResource(R.string.action_save)) }
      },
    )
  }
}

@Composable
private fun accessoryLabel(accessory: PiyoAccessory): String = stringResource(
  when (accessory) {
    PiyoAccessory.AUTO -> R.string.wardrobe_auto
    PiyoAccessory.NONE -> R.string.wardrobe_none
    PiyoAccessory.STREAK_RIBBON -> R.string.wardrobe_ribbon
    PiyoAccessory.STAR_BERET -> R.string.wardrobe_beret
    PiyoAccessory.RAINBOW_BOW -> R.string.wardrobe_bow
    PiyoAccessory.TOPIK_GLASSES -> R.string.wardrobe_topik_glasses
    PiyoAccessory.CHAMPION_TROPHY -> R.string.wardrobe_trophy
  },
)

@Composable
private fun ProfileMetric(label: String, value: Int) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    Text(label, style = MaterialTheme.typography.labelSmall)
  }
}

@Composable
fun CurriculumMapScreen(
  progress: Map<String, UserProgressEntity>,
  activeStageId: String?,
  onStage: (CurriculumStage) -> Unit,
  onFreePractice: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val completed = progress.keys
  LazyColumn(
    modifier = modifier.fillMaxSize().background(Background).testTag("curriculum-map"),
    contentPadding = PaddingValues(18.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Text(stringResource(R.string.curriculum_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
      Text(stringResource(R.string.curriculum_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    CurriculumCatalog.chapters.forEach { chapter ->
      item("chapter-${chapter.number}") {
        Text(
          stringResource(R.string.curriculum_chapter, chapter.number, chapterTitle(chapter.number)),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
      items(chapter.stages, key = CurriculumStage::id) { stage ->
        val unlocked = CurriculumPolicy.isUnlocked(stage, completed)
        val saved = progress[stage.id]
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("curriculum-stage-${stage.id}${if (unlocked) "" else "-locked"}")
            .clickable(enabled = unlocked) { onStage(stage) },
          colors = CardDefaults.cardColors(containerColor = if (unlocked) Color.White else Color(0xFFEAE7E2)),
          shape = RoundedCornerShape(18.dp),
        ) {
          Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = if (unlocked) Yellow else Color.LightGray) {
              Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Text(if (unlocked) stage.chapterNumber.toString() else "🔒", fontWeight = FontWeight.Black)
              }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
              Text(stageTitle(stage.id), fontWeight = FontWeight.Bold)
              Text(
                if (activeStageId == stage.id) stringResource(R.string.curriculum_resume)
                else stringResource(R.string.curriculum_item_count, stage.items.size),
                style = MaterialTheme.typography.bodySmall,
              )
            }
            Text(if (saved == null) "☆☆☆" else "★".repeat(saved.stars) + "☆".repeat(3 - saved.stars))
          }
        }
      }
    }
    item {
      OutlinedButton(onClick = onFreePractice, modifier = Modifier.fillMaxWidth().testTag("curriculum-free-practice")) {
        Text(stringResource(R.string.curriculum_free_practice))
      }
    }
  }
}

@Composable
fun ReviewDeckSection(
  reviewItems: List<ReviewItem>,
  manualCandidates: List<ManualReviewCandidate>,
  onStartReview: (List<ReviewItem>) -> Unit,
  onManualAdd: (ManualReviewCandidate) -> Unit,
  onRemove: (ReviewItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showManualAdd by remember { mutableStateOf(false) }
  val active = reviewItems.filter(ReviewItem::isActive)
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(stringResource(R.string.review_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    Text(stringResource(R.string.review_count, active.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (manualCandidates.isNotEmpty()) {
      OutlinedButton(onClick = { showManualAdd = true }, modifier = Modifier.testTag("review-manual-add")) {
        Text(stringResource(R.string.review_add_manually))
      }
    }
    if (active.isEmpty()) {
      Card(colors = CardDefaults.cardColors(containerColor = Pink)) {
        Text(stringResource(R.string.review_empty), Modifier.padding(16.dp))
      }
    } else {
      Button(onClick = { onStartReview(active) }, modifier = Modifier.fillMaxWidth().testTag("review-start")) {
        Text(stringResource(R.string.review_start))
      }
      active.take(5).forEach { review ->
        Card(modifier = Modifier.fillMaxWidth().testTag("review-item-${review.id}")) {
          Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text(review.item.ko, fontWeight = FontWeight.Bold)
              Text(stringResource(R.string.review_progress, review.consecutivePerfect, 3), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { onRemove(review) }) { Text(stringResource(R.string.review_remove)) }
          }
        }
      }
    }
  }
  if (showManualAdd) {
    AlertDialog(
      onDismissRequest = { showManualAdd = false },
      title = { Text(stringResource(R.string.review_choose_word)) },
      text = {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
          items(manualCandidates.take(100), key = { "${it.sourceDeckId}::${it.item.id}" }) { candidate ->
            TextButton(
              onClick = {
                onManualAdd(candidate)
                showManualAdd = false
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(Modifier.fillMaxWidth()) {
                Text(candidate.item.ko, fontWeight = FontWeight.Bold)
                Text(candidate.deckName, style = MaterialTheme.typography.labelSmall)
              }
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { showManualAdd = false }) { Text(stringResource(R.string.action_cancel)) }
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderControls(
  preference: ReminderPreferenceEntity,
  onEnabledChange: (Boolean) -> Unit,
  onTimeChange: (Int, Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showTimePicker by remember { mutableStateOf(false) }
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(stringResource(R.string.reminder_title), fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.reminder_default_off), style = MaterialTheme.typography.bodySmall)
      }
      Switch(
        checked = preference.isEnabled,
        onCheckedChange = onEnabledChange,
        modifier = Modifier.testTag("retention-reminder-toggle"),
      )
    }
    OutlinedButton(onClick = { showTimePicker = true }, enabled = preference.isEnabled) {
      Text(stringResource(R.string.reminder_time, preference.hour, preference.minute))
    }
  }
  if (showTimePicker) {
    val state = rememberTimePickerState(preference.hour, preference.minute, is24Hour = true)
    AlertDialog(
      onDismissRequest = { showTimePicker = false },
      title = { Text(stringResource(R.string.reminder_choose_time)) },
      text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimeInput(state) } },
      confirmButton = {
        TextButton(onClick = {
          onTimeChange(state.hour, state.minute)
          showTimePicker = false
        }) { Text(stringResource(R.string.action_save)) }
      },
      dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
}

@Composable
private fun chapterTitle(chapter: Int): String = stringResource(
  when (chapter) {
    1 -> R.string.curriculum_chapter_1
    2 -> R.string.curriculum_chapter_2
    3 -> R.string.curriculum_chapter_3
    4 -> R.string.curriculum_chapter_4
    5 -> R.string.curriculum_chapter_5
    else -> R.string.curriculum_chapter_6
  },
)

@Composable
private fun stageTitle(id: String): String = stringResource(
  when (id) {
    "chapter_1_basic_consonants" -> R.string.curriculum_stage_consonants
    "chapter_2_basic_vowels" -> R.string.curriculum_stage_vowels
    "chapter_3_syllable_building" -> R.string.curriculum_stage_syllables
    "chapter_4_batchim" -> R.string.curriculum_stage_batchim
    "chapter_5_words" -> R.string.curriculum_stage_words
    "chapter_6_spacing" -> R.string.curriculum_stage_spacing
    else -> R.string.curriculum_stage_sentences
  },
)
