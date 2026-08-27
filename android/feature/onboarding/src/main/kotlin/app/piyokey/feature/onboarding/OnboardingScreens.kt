package app.piyokey.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.piyokey.core.settings.AppPreferences
import app.piyokey.core.design.PiyoAvatar
import app.piyokey.core.settings.PiyoGrowthStage
import app.piyokey.core.settings.PiyoSessionAppearance
import app.piyokey.core.settings.OnboardingGoal
import app.piyokey.core.settings.OnboardingIntroStep
import app.piyokey.feature.practice.DubeolsikKeyboard
import app.piyokey.feature.practice.PracticeKeyboardOptions

private val Accent = Color(0xFFFF6F91)
private val Yellow = Color(0xFFFFD65A)

@Composable
fun OnboardingRoute(
  preferences: AppPreferences,
  onUpdate: (AppPreferences) -> Unit,
  onEnableReminder: () -> Unit,
  onSkipReminder: () -> Unit,
  modifier: Modifier = Modifier,
) {
  when {
    preferences.firstInputCompleted -> FirstRewardScreen(
      onEnableReminder = onEnableReminder,
      onSkipReminder = onSkipReminder,
      modifier = modifier,
    )
    preferences.onboardingIntroStep == OnboardingIntroStep.GOAL -> GoalScreen(
      selected = preferences.onboardingGoal,
      onSelect = { onUpdate(preferences.copy(onboardingGoal = it)) },
      onNext = { onUpdate(preferences.copy(onboardingIntroStep = OnboardingIntroStep.KEYBOARD)) },
      onSkip = {
        onUpdate(
          preferences.copy(
            onboardingIntroSkipped = true,
            onboardingIntroStep = OnboardingIntroStep.COMPLETE,
            firstInputCompleted = true,
            hatchHandoffCompleted = true,
          ),
        )
      },
      modifier = modifier,
    )
    preferences.onboardingIntroStep == OnboardingIntroStep.KEYBOARD -> KeyboardIntroScreen(
      onNext = { onUpdate(preferences.copy(onboardingIntroStep = OnboardingIntroStep.FIRST_INPUT)) },
      onSkip = {
        onUpdate(
          preferences.copy(
            onboardingIntroSkipped = true,
            onboardingIntroStep = OnboardingIntroStep.COMPLETE,
            firstInputCompleted = true,
            hatchHandoffCompleted = true,
          ),
        )
      },
      modifier = modifier,
    )
    else -> FirstInputScreen(
      onCompleted = {
        onUpdate(
          preferences.copy(
            firstInputCompleted = true,
            onboardingIntroStep = OnboardingIntroStep.COMPLETE,
          ),
        )
      },
      modifier = modifier,
    )
  }
}

@Composable
private fun OnboardingFrame(
  step: Int,
  allowSkip: Boolean,
  onSkip: () -> Unit,
  modifier: Modifier,
  content: @Composable () -> Unit,
) {
  Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LinearProgressIndicator(
        progress = { step / 3f },
        modifier = Modifier.weight(1f).height(8.dp),
        color = Accent,
        trackColor = Color(0xFFFFE4EC),
      )
      if (allowSkip) {
        TextButton(onClick = onSkip, modifier = Modifier.testTag("onboarding-skip")) {
          Text(stringResource(R.string.onboarding_skip))
        }
      }
    }
    content()
  }
}

@Composable
private fun GoalScreen(
  selected: OnboardingGoal?,
  onSelect: (OnboardingGoal) -> Unit,
  onNext: () -> Unit,
  onSkip: () -> Unit,
  modifier: Modifier,
) {
  OnboardingFrame(1, true, onSkip, modifier) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("onboarding-goal"),
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        PiyoMark(PiyoGrowthStage.EGG)
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(stringResource(R.string.onboarding_goal_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        TrustCard()
      }
      items(OnboardingGoal.entries) { goal ->
        val active = selected == goal
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding-goal-${goal.name.lowercase()}")
            .clickable { onSelect(goal) }
            .then(if (active) Modifier.border(3.dp, Accent, RoundedCornerShape(20.dp)) else Modifier),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(20.dp),
        ) {
          Column(Modifier.padding(16.dp)) {
            Text(stringResource(goal.titleResource()), fontWeight = FontWeight.Black)
            Text(stringResource(goal.detailResource()), style = MaterialTheme.typography.bodySmall)
          }
        }
      }
      item {
        Button(
          onClick = onNext,
          enabled = selected != null,
          modifier = Modifier.fillMaxWidth().testTag("onboarding-next"),
        ) { Text(stringResource(R.string.onboarding_next)) }
      }
    }
  }
}

@Composable
private fun TrustCard() {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    shape = RoundedCornerShape(18.dp),
  ) {
    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("✓ ${stringResource(R.string.onboarding_trust_standard)}", fontWeight = FontWeight.Bold)
      Text("✓ ${stringResource(R.string.onboarding_trust_local)}", fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun KeyboardIntroScreen(onNext: () -> Unit, onSkip: () -> Unit, modifier: Modifier) {
  OnboardingFrame(2, true, onSkip, modifier) {
    Column(
      Modifier.fillMaxSize().padding(22.dp).testTag("onboarding-keyboard"),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
      PiyoMark(PiyoGrowthStage.EGG)
      Text(stringResource(R.string.onboarding_keyboard_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
      Text(stringResource(R.string.onboarding_keyboard_subtitle), textAlign = TextAlign.Center)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HandCard("ㄱ ㄴ ㄷ", stringResource(R.string.onboarding_left_hand), Modifier.weight(1f))
        HandCard("ㅏ ㅓ ㅗ", stringResource(R.string.onboarding_right_hand), Modifier.weight(1f))
      }
      Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(18.dp)) {
        Text("ㄱ  +  ㅏ  →  가", Modifier.padding(horizontal = 24.dp, vertical = 15.dp), fontSize = 24.sp, fontWeight = FontWeight.Black)
      }
      Button(onClick = onNext, modifier = Modifier.fillMaxWidth().testTag("onboarding-keyboard-try")) {
        Text(stringResource(R.string.onboarding_try))
      }
    }
  }
}

@Composable
private fun HandCard(example: String, title: String, modifier: Modifier) {
  Card(
    modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(20.dp),
  ) {
    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Text(example, fontSize = 22.sp, fontWeight = FontWeight.Black)
      Text(title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
  }
}

@Composable
private fun FirstInputScreen(onCompleted: () -> Unit, modifier: Modifier) {
  var entered by remember { mutableStateOf<List<Char>>(emptyList()) }
  var mistake by remember { mutableStateOf(false) }
  val expected = listOf('ㄱ', 'ㅏ')
  OnboardingFrame(3, false, {}, modifier) {
    Column(
      Modifier.fillMaxSize().testTag("onboarding-first-input"),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        PiyoMark(if (entered.size == 2) PiyoGrowthStage.HATCHING else PiyoGrowthStage.EGG)
        Text(stringResource(R.string.onboarding_first_input_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text(stringResource(R.string.onboarding_first_input_hint), textAlign = TextAlign.Center)
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp), shadowElevation = 7.dp) {
          Text(
            if (entered.size == 2) "가" else entered.joinToString("").ifEmpty { "○" },
            modifier = Modifier.padding(horizontal = 52.dp, vertical = 22.dp),
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
            color = if (mistake) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
          )
        }
        Text(
          if (mistake) stringResource(R.string.onboarding_first_input_retry)
          else stringResource(R.string.onboarding_first_input_next, expected.getOrNull(entered.size)?.toString().orEmpty()),
          color = if (mistake) MaterialTheme.colorScheme.error else Accent,
          fontWeight = FontWeight.Bold,
        )
      }
      DubeolsikKeyboard(
        nextExpectedJamo = expected.getOrNull(entered.size),
        onJamo = { jamo ->
          if (jamo == expected.getOrNull(entered.size)) {
            mistake = false
            entered = entered + jamo
            if (entered.size == expected.size) onCompleted()
          } else {
            mistake = true
          }
        },
        onBackspace = {
          entered = entered.dropLast(1)
          mistake = false
        },
        options = PracticeKeyboardOptions(showsKeyGuide = true, showsRomanHints = true, hapticsEnabled = true),
      )
    }
  }
}

@Composable
private fun FirstRewardScreen(
  onEnableReminder: () -> Unit,
  onSkipReminder: () -> Unit,
  modifier: Modifier,
) {
  Column(
    modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .verticalScroll(rememberScrollState())
      .padding(24.dp)
      .testTag("onboarding-first-reward"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
  ) {
    PiyoMark(PiyoGrowthStage.HATCHING)
    Text(stringResource(R.string.onboarding_reward_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(18.dp)) {
      Text(stringResource(R.string.onboarding_reward_badge), Modifier.padding(16.dp), fontWeight = FontWeight.Black)
    }
    Text(stringResource(R.string.onboarding_hatch_handoff), textAlign = TextAlign.Center)
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
      Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.onboarding_reminder_title), fontWeight = FontWeight.Black)
        Text(stringResource(R.string.onboarding_reminder_detail), style = MaterialTheme.typography.bodySmall)
      }
    }
    Button(onClick = onEnableReminder, modifier = Modifier.fillMaxWidth().testTag("onboarding-enable-reminder")) {
      Text(stringResource(R.string.onboarding_reminder_allow_and_begin))
    }
    TextButton(onClick = onSkipReminder, modifier = Modifier.testTag("onboarding-begin-hatch")) {
      Text(stringResource(R.string.onboarding_reminder_not_now))
    }
  }
}

@Composable
fun HatchMissionGateScreen(
  chapter: Int,
  completed: Int,
  onStart: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(22.dp).testTag("hatch-gate"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
  ) {
    PiyoMark(if (completed == 0) PiyoGrowthStage.HATCHING else PiyoGrowthStage.CHICK)
    Text(stringResource(R.string.hatch_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
    Text(stringResource(R.string.hatch_progress, completed, 3), fontWeight = FontWeight.Bold)
    LinearProgressIndicator(
      progress = { completed / 3f },
      modifier = Modifier.fillMaxWidth().height(10.dp),
      color = MaterialTheme.colorScheme.secondary,
      trackColor = MaterialTheme.colorScheme.secondaryContainer,
    )
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp)) {
      Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.hatch_mission, chapter), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text(stringResource(hatchChapterResource(chapter)), textAlign = TextAlign.Center)
      }
    }
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth().testTag("hatch-start-$chapter")) {
      Text(stringResource(R.string.hatch_start))
    }
  }
}

@Composable
fun HatchMissionResultScreen(
  chapter: Int,
  accuracyPercent: Double,
  stars: Int,
  initialNickname: String,
  onContinue: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var nickname by remember(initialNickname) { mutableStateOf(initialNickname) }
  Column(
    modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(22.dp).testTag("hatch-result-$chapter"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
  ) {
    PiyoMark(if (chapter >= 3) PiyoGrowthStage.CHICK else PiyoGrowthStage.HATCHING)
    Text(stringResource(R.string.hatch_result_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
    Text("★".repeat(stars.coerceIn(0, 3)) + "☆".repeat((3 - stars).coerceAtLeast(0)), fontSize = 32.sp, color = Accent)
    Text(stringResource(R.string.hatch_accuracy, accuracyPercent), fontWeight = FontWeight.Bold)
    if (chapter == 1) {
      Text(stringResource(R.string.hatch_growth_message), textAlign = TextAlign.Center)
      OutlinedTextField(
        value = nickname,
        onValueChange = { nickname = it.take(12) },
        label = { Text(stringResource(R.string.hatch_nickname)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("hatch-nickname"),
      )
    }
    Button(
      onClick = { onContinue(nickname.trim()) },
      enabled = chapter != 1 || nickname.isNotBlank(),
      modifier = Modifier.fillMaxWidth().testTag("hatch-result-continue"),
    ) {
      Text(stringResource(if (chapter >= 3) R.string.hatch_open_app else R.string.hatch_next_mission))
    }
  }
}

@Composable
private fun PiyoMark(stage: PiyoGrowthStage) {
  PiyoAvatar(
    appearance = PiyoSessionAppearance(stage, null),
    contentDescription = stringResource(R.string.onboarding_piyo_accessibility),
    modifier = Modifier.size(92.dp),
  )
}

private fun OnboardingGoal.titleResource(): Int = when (this) {
  OnboardingGoal.KEYBOARD -> R.string.onboarding_goal_keyboard
  OnboardingGoal.TRAVEL -> R.string.onboarding_goal_travel
  OnboardingGoal.TOPIK -> R.string.onboarding_goal_topik
  OnboardingGoal.TRENDS -> R.string.onboarding_goal_trends
}

private fun OnboardingGoal.detailResource(): Int = when (this) {
  OnboardingGoal.KEYBOARD -> R.string.onboarding_goal_keyboard_detail
  OnboardingGoal.TRAVEL -> R.string.onboarding_goal_travel_detail
  OnboardingGoal.TOPIK -> R.string.onboarding_goal_topik_detail
  OnboardingGoal.TRENDS -> R.string.onboarding_goal_trends_detail
}

private fun hatchChapterResource(chapter: Int): Int = when (chapter) {
  1 -> R.string.hatch_chapter_consonants
  2 -> R.string.hatch_chapter_vowels
  else -> R.string.hatch_chapter_syllables
}

@Composable
fun MainAppTourOverlay(
  step: Int,
  onAdvance: () -> Unit,
  onSkip: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier
      .fillMaxSize()
      .background(Color.Black.copy(alpha = 0.62f))
      .clickable(onClick = onAdvance)
      .testTag("main-tour-$step"),
  ) {
    if (step == 5) {
      Box(
        Modifier
          .align(Alignment.TopEnd)
          .padding(7.dp)
          .size(52.dp)
          .border(3.dp, Yellow, CircleShape),
      )
    }
    Card(
      modifier = Modifier.align(Alignment.Center).padding(24.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = RoundedCornerShape(24.dp),
    ) {
      Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.main_tour_progress, step + 1, 6), color = Accent, fontWeight = FontWeight.Black)
        Text(stringResource(mainTourTitle(step)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text(stringResource(mainTourDetail(step)))
        Text(stringResource(R.string.main_tour_tap), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(
          onClick = onSkip,
          modifier = Modifier.align(Alignment.End).testTag("main-tour-skip"),
        ) { Text(stringResource(R.string.main_tour_skip)) }
      }
    }
  }
}

private fun mainTourTitle(step: Int): Int = when (step) {
  0 -> R.string.main_tour_home_title
  1 -> R.string.main_tour_discover_title
  2 -> R.string.main_tour_practice_title
  3 -> R.string.main_tour_games_title
  4 -> R.string.main_tour_profile_title
  else -> R.string.main_tour_settings_title
}

private fun mainTourDetail(step: Int): Int = when (step) {
  0 -> R.string.main_tour_home_detail
  1 -> R.string.main_tour_discover_detail
  2 -> R.string.main_tour_practice_detail
  3 -> R.string.main_tour_games_detail
  4 -> R.string.main_tour_profile_detail
  else -> R.string.main_tour_settings_detail
}
