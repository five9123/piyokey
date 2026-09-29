package app.piyokey.android.feature.home

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.piyokey.android.R
import app.piyokey.android.data.AppData
import app.piyokey.android.data.curriculum.DomainText
import app.piyokey.android.data.mascot.MascotMood
import app.piyokey.android.data.mascot.MascotProp
import app.piyokey.android.data.mascot.MascotStage
import app.piyokey.android.data.mascot.MascotStore
import app.piyokey.android.data.mascot.MascotUnlockState
import app.piyokey.android.data.progress.RetentionClock
import app.piyokey.android.data.settings.AppSettings
import app.piyokey.android.feature.discover.InlineTopBar
import app.piyokey.android.ui.mascot.GrowingMascot
import app.piyokey.android.ui.mascot.MascotClosetRoute
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.nav.LocalTabNavigator
import app.piyokey.android.ui.nav.Route
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.ui.theme.centeredContent
import app.piyokey.core.domain.JstDay
import app.piyokey.core.domain.MascotDailyEncouragement
import app.piyokey.core.domain.RetentionCalendar
import app.piyokey.core.domain.RetentionStampDayState
import app.piyokey.core.domain.RetentionStreak
import app.piyokey.core.domain.StampReward
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** JST "today", refreshed every minute and on resume (iOS `TimelineView(.periodic(by: 60))`). */
@Composable
internal fun rememberJstToday(): JstDay {
  var today by remember { mutableStateOf(RetentionClock.today()) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(60_000)
      today = RetentionClock.today()
    }
  }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = RetentionClock.today() }
  return today
}

/** Retention snapshot for a day (iOS environment `RetentionLibrary` reads). */
private class WeekStamps(val today: JstDay, stamped: (JstDay) -> Boolean, val streak: RetentionStreak) {
  val days: List<JstDay> = RetentionCalendar.stampCardDays(today)
  val states: List<RetentionStampDayState> = days.map { RetentionStampDayState.of(it, today, stamped(it)) }
  val stampedCount: Int = states.count { it == RetentionStampDayState.COMPLETED }
}

@Composable
private fun rememberWeekStamps(today: JstDay): WeekStamps {
  val records by AppData.retention.records.collectAsState()
  return remember(records, today) {
    WeekStamps(today, { AppData.retention.isStamped(it) }, AppData.retention.streak(today))
  }
}

/** iOS `RetentionHomeView.registerEarnedRewards` / `MyPiyoDetailView.registerEarnedRewards`. */
private fun registerEarnedRewards(stampedCount: Int) {
  MascotStore.registerUnlocks(
    StampReward.entries.mapNotNull { reward ->
      val prop = MascotProp.fromRaw(reward.propRaw) ?: return@mapNotNull null
      MascotUnlockState(prop = prop, isUnlocked = stampedCount >= reward.requiredDays, conditionKey = reward.conditionKey)
    },
  )
}

@Composable
private fun rememberMascotDisplayName(): String {
  val name by MascotStore.name.collectAsState()
  val defaultName = stringResource(R.string.mascot_default_name)
  return MascotStore.displayName(name, defaultName)
}

/** iOS `MascotEncouragementBubble`. */
@Composable
private fun EncouragementBubble(text: String) {
  val bubble = Piyo.colors.accentSoft.copy(alpha = 0.56f)
  Box(Modifier.padding(start = 5.dp)) {
    Box(
      Modifier
        .align(Alignment.CenterStart)
        .offset(x = (-4).dp)
        .size(10.dp)
        .rotate(45f)
        .background(bubble, RoundedCornerShape(2.dp)),
    )
    Text(
      text,
      style = PiyoType.caption().copy(color = Piyo.colors.ink.copy(alpha = 0.78f), fontWeight = FontWeight.SemiBold),
      modifier = Modifier
        .background(bubble, RoundedCornerShape(14.dp))
        .padding(horizontal = 12.dp, vertical = 9.dp),
    )
  }
}

/** iOS `RetentionHomeView`: the "My Piyo" stamp card on Home; opens [MyPiyoDetailRoute]. */
@Composable
internal fun RetentionHomeCard(today: JstDay, modifier: Modifier = Modifier) {
  val colors = Piyo.colors
  val tabNavigator = LocalTabNavigator.current
  val week = rememberWeekStamps(today)
  val displayName = rememberMascotDisplayName()
  val records by AppData.retention.records.collectAsState()
  val encouragementKey = remember(records, today) {
    MascotDailyEncouragement.localizationKey(today, AppData.retention.completedDays)
  }
  val encouragement = DomainText.string(encouragementKey)
  LaunchedEffect(week.stampedCount) { registerEarnedRewards(week.stampedCount) }

  val streakText = pluralStringResource(R.plurals.retention_streak_current_format, week.streak.current, week.streak.current)
  val daySummary = week.days.zip(week.states).joinToString(", ") { (day, state) -> "${day.rawValue}, ${DomainText.string(state.localizationKey)}" }
  val a11yLabel = stringResource(R.string.my_page_profile_eyebrow)
  val a11yValue = "$encouragement, $streakText, ${week.stampedCount} / 7, $daySummary"
  val a11yHint = stringResource(R.string.retention_streak_open_detail_hint)
  val shape = RoundedCornerShape(24.dp)

  Column(
    modifier
      .fillMaxWidth()
      .shadow(12.dp, shape, ambientColor = colors.keyShadow, spotColor = colors.keyShadow)
      .background(colors.card, shape)
      .testTag("home.my_piyo_card")
      .clickable(onClickLabel = a11yHint) { tabNavigator.push(MyPiyoDetailRoute()) }
      .clearAndSetSemantics {
        contentDescription = a11yLabel
        stateDescription = a11yValue
      }
      .padding(18.dp),
    verticalArrangement = Arrangement.spacedBy(15.dp),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(82.dp, 92.dp).clipToBounds(), contentAlignment = Alignment.Center) {
        GrowingMascot(mood = MascotMood.HAPPY, size = 58.dp, calm = true)
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
          stringResource(R.string.my_page_profile_eyebrow),
          style = PiyoType.caption().copy(color = colors.accent, fontWeight = FontWeight.Black),
        )
        Text(displayName, style = PiyoType.title3().copy(fontWeight = FontWeight.ExtraBold), maxLines = 1)
        EncouragementBubble(encouragement)
      }
      Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = colors.mutedInk,
        modifier = Modifier.size(22.dp),
      )
    }
    HorizontalDivider(color = colors.mutedInk.copy(alpha = 0.2f))
    StampWeekHeader(week)
    SevenDayStampRow(week)
  }
}

@Composable
private fun StampWeekHeader(week: WeekStamps, modifier: Modifier = Modifier) {
  val colors = Piyo.colors
  val locale = AppSettings.currentLanguage.locale
  val range = remember(week.days, locale) {
    val formatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "Md"), locale)
    "${week.days.first().localDate.format(formatter)}–${week.days.last().localDate.format(formatter)}"
  }
  Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = colors.ink, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.retention_streak_title), style = PiyoType.headline().copy(fontWeight = FontWeight.ExtraBold))
      }
      Text(range, style = PiyoType.caption().copy(color = colors.mutedInk, fontWeight = FontWeight.SemiBold))
    }
    Spacer(Modifier.width(6.dp))
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(
        stringResource(R.string.retention_week_progress_format, week.stampedCount),
        style = PiyoType.subheadline().copy(color = colors.accent, fontWeight = FontWeight.Black),
      )
      Text(
        pluralStringResource(R.plurals.retention_streak_current_format, week.streak.current, week.streak.current),
        style = PiyoType.caption().copy(
          color = if (week.streak.current > 0) StreakOrange else colors.mutedInk,
          fontWeight = FontWeight.Bold,
        ),
      )
    }
  }
}

private val StreakOrange = Color(0xFFFF9500)

@Composable
private fun SevenDayStampRow(week: WeekStamps) {
  val colors = Piyo.colors
  val locale = AppSettings.currentLanguage.locale
  val weekdayFormatter = remember(locale) { DateTimeFormatter.ofPattern("E", locale) }
  val calendarLabel = stringResource(R.string.retention_streak_title)
  Row(
    Modifier
      .fillMaxWidth()
      .semantics {
        contentDescription = calendarLabel
        stateDescription = "${week.stampedCount} / 7"
      }
      .testTag("retention.stamp_calendar"),
    horizontalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    week.days.zip(week.states).forEach { (day, state) ->
      val stateLabel = DomainText.string(state.localizationKey)
      Column(
        Modifier
          .weight(1f)
          .testTag("retention.stamp_day.${day.rawValue}")
          .clearAndSetSemantics { contentDescription = "${day.rawValue}, $stateLabel" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Text(
          day.localDate.format(weekdayFormatter),
          style = PiyoType.caption2().copy(color = weekdayColor(state), fontWeight = FontWeight.Bold),
          maxLines = 1,
        )
        val fill = when (state) {
          RetentionStampDayState.COMPLETED -> colors.accentSoft
          RetentionStampDayState.MISSED -> colors.backgroundBottom.copy(alpha = 0.75f)
          RetentionStampDayState.TODAY_PENDING -> colors.accentSoft.copy(alpha = 0.35f)
          RetentionStampDayState.UPCOMING -> colors.backgroundBottom.copy(alpha = 0.28f)
        }
        val stroke = when (state) {
          RetentionStampDayState.COMPLETED, RetentionStampDayState.TODAY_PENDING -> colors.accent
          RetentionStampDayState.MISSED -> colors.mutedInk.copy(alpha = 0.28f)
          RetentionStampDayState.UPCOMING -> colors.mutedInk.copy(alpha = 0.1f)
        }
        val symbol = when (state) {
          RetentionStampDayState.COMPLETED -> colors.accent
          RetentionStampDayState.TODAY_PENDING -> colors.accent.copy(alpha = 0.42f)
          RetentionStampDayState.MISSED -> colors.mutedInk.copy(alpha = 0.28f)
          RetentionStampDayState.UPCOMING -> colors.mutedInk.copy(alpha = 0.1f)
        }
        Box(
          Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .drawBehind {
              val width = (if (state == RetentionStampDayState.TODAY_PENDING) 2.5.dp else 1.5.dp).toPx()
              val radius = size.minDimension / 2
              drawCircle(fill, radius)
              drawCircle(
                stroke,
                radius - width / 2,
                style = Stroke(
                  width = width,
                  pathEffect = if (state == RetentionStampDayState.MISSED) {
                    PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                  } else {
                    null
                  },
                ),
              )
            },
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            if (state == RetentionStampDayState.COMPLETED) Icons.Rounded.Verified else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = symbol,
            modifier = Modifier.size(14.dp),
          )
        }
        Text(
          day.localDate.dayOfMonth.toString(),
          style = PiyoType.caption2().copy(
            color = if (state == RetentionStampDayState.UPCOMING) colors.mutedInk.copy(alpha = 0.45f) else colors.ink,
            fontWeight = if (state == RetentionStampDayState.TODAY_PENDING) FontWeight.Black else FontWeight.Medium,
          ),
        )
      }
    }
  }
}

@Composable
private fun weekdayColor(state: RetentionStampDayState): Color {
  val colors = Piyo.colors
  return when (state) {
    RetentionStampDayState.TODAY_PENDING -> colors.accent
    RetentionStampDayState.UPCOMING -> colors.mutedInk.copy(alpha = 0.45f)
    RetentionStampDayState.COMPLETED, RetentionStampDayState.MISSED -> colors.mutedInk
  }
}

private val StampReward.icon: ImageVector
  get() = when (this) {
    StampReward.THREE -> Icons.Rounded.AutoFixHigh
    StampReward.FIVE -> Icons.Rounded.WorkspacePremium
    StampReward.SEVEN -> Icons.Rounded.Headphones
  }

private val MascotStage.labelRes: Int
  get() = when (this) {
    MascotStage.EGG -> R.string.a11y_stage_egg
    MascotStage.CRACKING -> R.string.a11y_stage_cracking
    MascotStage.HATCHING -> R.string.a11y_stage_hatching
    MascotStage.CHICK -> R.string.a11y_stage_chick
    MascotStage.ROOSTER -> R.string.a11y_stage_rooster
  }

/** iOS `MyPiyoDetailView`, pushed inside the Home tab. */
class MyPiyoDetailRoute : Route {
  @Composable
  override fun Content() {
    val navigator = LocalTabNavigator.current
    Column(Modifier.fillMaxSize()) {
      InlineTopBar(stringResource(R.string.my_page_profile_eyebrow), onBack = { navigator.pop() })
      MyPiyoDetailScreen(rememberJstToday())
    }
  }
}

@Composable
internal fun MyPiyoDetailScreen(today: JstDay) {
  val colors = Piyo.colors
  val appNavigator = LocalAppNavigator.current
  val week = rememberWeekStamps(today)
  val displayName = rememberMascotDisplayName()
  val signals by MascotStore.signals.collectAsState()
  val unlocked by MascotStore.unlockedProps.collectAsState()
  LaunchedEffect(week.stampedCount) { registerEarnedRewards(week.stampedCount) }
  val openCloset = { appNavigator.push(MascotClosetRoute()) }

  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    Column(
      Modifier.centeredContent(Piyo.metrics.readableContentMaxWidth).padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      // Profile
      val profileShape = RoundedCornerShape(26.dp)
      Row(
        Modifier
          .fillMaxWidth()
          .shadow(12.dp, profileShape, ambientColor = colors.keyShadow, spotColor = colors.keyShadow)
          .background(colors.card, profileShape)
          .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.size(118.dp, 140.dp).clipToBounds(), contentAlignment = Alignment.Center) {
          GrowingMascot(interactive = true, onOpenCloset = openCloset, size = 78.dp, calm = true)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
          Text(
            stringResource(R.string.my_page_profile_eyebrow),
            style = PiyoType.caption().copy(color = colors.accent, fontWeight = FontWeight.Black),
            modifier = Modifier.testTag("my_piyo.detail.screen"),
          )
          Text(displayName, style = PiyoType.title2().copy(fontWeight = FontWeight.ExtraBold), maxLines = 1)
          Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.mutedInk, modifier = Modifier.size(15.dp))
            Text(
              stringResource(signals.stage.labelRes),
              style = PiyoType.subheadline().copy(color = colors.mutedInk, fontWeight = FontWeight.SemiBold),
            )
          }
          Row(
            Modifier
              .background(colors.accentSoft.copy(alpha = 0.55f), CircleShape)
              .clickable(onClick = openCloset)
              .padding(horizontal = 12.dp, vertical = 8.dp)
              .testTag("my_piyo.detail.settings"),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Rounded.Checkroom, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
            Text(
              stringResource(R.string.my_page_piyo_settings),
              style = PiyoType.caption().copy(color = colors.accent, fontWeight = FontWeight.Bold),
            )
          }
        }
      }

      // Stamps
      DetailCard {
        StampWeekHeader(week, Modifier.testTag("my_piyo.detail.stamps"))
        SevenDayStampRow(week)
        Text(
          pluralStringResource(R.plurals.retention_streak_longest_format, week.streak.longest, week.streak.longest),
          style = PiyoType.caption().copy(color = colors.mutedInk),
        )
      }

      // Rewards
      val rewardsLabel = stringResource(R.string.retention_rewards_accessibility)
      DetailCard(Modifier.semantics { contentDescription = rewardsLabel }) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Rounded.CardGiftcard, contentDescription = null, tint = colors.ink, modifier = Modifier.size(18.dp))
          Text(
            stringResource(R.string.retention_rewards_navigation_title),
            style = PiyoType.headline().copy(fontWeight = FontWeight.ExtraBold),
          )
        }
        StampReward.entries.forEach { reward ->
          val prop = MascotProp.fromRaw(reward.propRaw)
          val earned = week.stampedCount >= reward.requiredDays || (prop != null && prop in unlocked)
          val tint = if (earned) colors.success else colors.mutedInk
          Row(
            Modifier
              .fillMaxWidth()
              .background(colors.backgroundBottom.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
              .semantics(mergeDescendants = true) {}
              .padding(15.dp)
              .testTag("my_piyo.detail.reward.${reward.requiredDays}"),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              Modifier
                .size(44.dp)
                .background(if (earned) colors.accentSoft else colors.backgroundBottom, RoundedCornerShape(14.dp)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(reward.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
              Text(DomainText.string(reward.titleKey), style = PiyoType.headline().copy(fontWeight = FontWeight.Bold))
              Text(DomainText.string(reward.conditionKey), style = PiyoType.caption().copy(color = colors.mutedInk))
            }
            Icon(if (earned) Icons.Rounded.Verified else Icons.Rounded.Lock, contentDescription = null, tint = tint)
          }
        }
      }
    }
  }
}

@Composable
private fun DetailCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(24.dp)
  Column(
    modifier
      .fillMaxWidth()
      .shadow(9.dp, shape, ambientColor = colors.keyShadow, spotColor = colors.keyShadow)
      .background(colors.card, shape)
      .padding(18.dp),
    verticalArrangement = Arrangement.spacedBy(13.dp),
  ) { content() }
}
