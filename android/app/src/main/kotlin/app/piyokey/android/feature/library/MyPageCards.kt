package app.piyokey.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardAlt
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.piyokey.android.R
import app.piyokey.android.data.AppData
import app.piyokey.android.data.curriculum.DomainText
import app.piyokey.android.data.mascot.MascotMood
import app.piyokey.android.data.mascot.MascotStore
import app.piyokey.android.feature.discover.SystemColors
import app.piyokey.android.feature.discover.rememberAppLocale
import app.piyokey.android.feature.onboarding.AppTourTarget
import app.piyokey.android.feature.onboarding.appTourTarget
import app.piyokey.android.ui.mascot.GrowingMascot
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.core.domain.CurriculumCatalog
import app.piyokey.core.domain.CurriculumChapterCompletionPolicy
import app.piyokey.core.domain.JstDay
import app.piyokey.core.domain.LearningInsightPeriod
import app.piyokey.core.domain.LearningInsights
import app.piyokey.core.domain.RetentionCalendar
import app.piyokey.core.domain.library.InsightsFormatting
import java.text.NumberFormat
import kotlin.math.roundToInt

@Composable
internal fun CardColumn(modifier: Modifier = Modifier, radius: Int = 24, spacing: Int = 14, shadow: Int = 9, content: @Composable () -> Unit) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(radius.dp)
  Column(
    modifier
      .fillMaxWidth()
      .shadow(shadow.dp, shape, ambientColor = colors.keyShadow, spotColor = colors.keyShadow)
      .clip(shape)
      .background(colors.card)
      .padding(18.dp),
    verticalArrangement = Arrangement.spacedBy(spacing.dp),
  ) { content() }
}

@Composable
internal fun HeavyLabel(text: String, icon: ImageVector, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = PiyoType.headline()) {
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, contentDescription = null, tint = Piyo.colors.ink, modifier = Modifier.size(20.dp))
    Text(text, style = style.copy(fontWeight = FontWeight.ExtraBold))
  }
}

/** Profile card with the growing Piyo and closet entry (iOS `profileCard`). */
@Composable
internal fun ProfileCard(today: JstDay, onOpenCloset: () -> Unit) {
  val colors = Piyo.colors
  val name by MascotStore.name.collectAsStateWithLifecycle()
  val records by AppData.retention.records.collectAsStateWithLifecycle()
  val defaultName = stringResource(R.string.mascot_default_name)
  val encouragementKey = remember(records, today) { AppData.retention.dailyEncouragementKey(today) }
  val shape = RoundedCornerShape(26.dp)
  Box(
    Modifier
      .fillMaxWidth()
      .appTourTarget(AppTourTarget.MY_PAGE_PROFILE)
      .shadow(12.dp, shape, ambientColor = colors.keyShadow, spotColor = colors.keyShadow)
      .clip(shape)
      .background(colors.card)
      .padding(18.dp),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(112.dp, 132.dp).clipToBounds(), contentAlignment = Alignment.Center) {
        GrowingMascot(mood = MascotMood.IDLE, showsNameTag = false, interactive = true, onOpenCloset = onOpenCloset, size = 72.dp, calm = true)
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
          stringResource(R.string.my_page_profile_eyebrow),
          style = PiyoType.caption().copy(color = colors.accent, fontWeight = FontWeight.Black),
          modifier = Modifier.testTag("my_page.profile"),
        )
        Text(
          MascotStore.displayName(name, defaultName),
          style = PiyoType.title2().copy(fontWeight = FontWeight.ExtraBold),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(end = 48.dp),
        )
        EncouragementBubble(DomainText.string(encouragementKey))
      }
    }
    val closetLabel = stringResource(R.string.closet_title)
    Box(
      Modifier
        .align(Alignment.TopEnd)
        .size(44.dp)
        .shadow(5.dp, CircleShape, ambientColor = colors.keyShadow, spotColor = colors.keyShadow)
        .clip(CircleShape)
        .background(colors.card)
        .border(1.5.dp, colors.accent.copy(alpha = 0.24f), CircleShape)
        .clickable(role = Role.Button, onClick = onOpenCloset)
        .semantics { contentDescription = closetLabel }
        .testTag("my_page.piyo_settings"),
      contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Checkroom, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp)) }
  }
}

/** iOS `MascotEncouragementBubble`. */
@Composable
private fun EncouragementBubble(text: String) {
  val bubble = Piyo.colors.accentSoft.copy(alpha = 0.56f)
  Box(Modifier.padding(start = 5.dp)) {
    Box(Modifier.align(Alignment.CenterStart).offset(x = (-4).dp).size(10.dp).rotate(45f).clip(RoundedCornerShape(2.dp)).background(bubble))
    Text(
      text,
      style = PiyoType.caption().copy(color = Piyo.colors.ink.copy(alpha = 0.78f), fontWeight = FontWeight.SemiBold),
      modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(bubble).padding(horizontal = 12.dp, vertical = 9.dp),
    )
  }
}

/** iOS `growthRecordCard`. */
@Composable
internal fun GrowthRecordCard(today: JstDay) {
  val colors = Piyo.colors
  val locale = rememberAppLocale()
  val number = remember(locale) { NumberFormat.getIntegerInstance(locale) }
  val progress by AppData.curriculum.stageProgress.collectAsStateWithLifecycle()
  val retentionRecords by AppData.retention.records.collectAsStateWithLifecycle()
  val typed by MascotStore.typedJamoCount.collectAsStateWithLifecycle()
  val chapters = remember(progress) {
    CurriculumCatalog.chapters.count { CurriculumChapterCompletionPolicy.isCompleted(it, progress.keys) }
  }
  val streak = remember(retentionRecords, today) { AppData.retention.streak(today).current }
  val activeDays = remember(retentionRecords, today) { RetentionCalendar.days(today, 7).count { AppData.retention.isStamped(it) } }
  CardColumn(Modifier.testTag("my_page.growth")) {
    HeavyLabel(stringResource(R.string.my_page_growth_title), Icons.AutoMirrored.Rounded.TrendingUp)
    Grid2(
      listOf(
        { GrowthMetric(stringResource(R.string.my_page_growth_chapters), "$chapters / 6", Icons.Rounded.Map, colors.accent, it) },
        { GrowthMetric(stringResource(R.string.my_page_growth_typed), number.format(typed), Icons.Rounded.Keyboard, colors.secondary, it) },
        { GrowthMetric(stringResource(R.string.my_page_growth_streak), number.format(streak), Icons.Rounded.LocalFireDepartment, SystemColors.orange, it) },
        { GrowthMetric(stringResource(R.string.my_page_growth_activity), "$activeDays / 7", Icons.Rounded.EventAvailable, colors.success, it) },
      ),
    )
  }
}

@Composable
private fun Grid2(cells: List<@Composable (Modifier) -> Unit>) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    cells.chunked(2).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        row.forEach { cell -> cell(Modifier.weight(1f)) }
      }
    }
  }
}

@Composable
private fun GrowthMetric(title: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier) {
  val colors = Piyo.colors
  Row(
    modifier
      .heightIn(min = 68.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(colors.backgroundBottom.copy(alpha = 0.68f))
      .semantics(mergeDescendants = true) {}
      .padding(11.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(value, style = PiyoType.headline().copy(fontWeight = FontWeight.Black))
      Text(title, style = PiyoType.caption2().copy(color = colors.mutedInk, fontWeight = FontWeight.SemiBold), maxLines = 2)
    }
  }
}

/** iOS `learningInsightsCard`. */
@Composable
internal fun LearningInsightsCard(period: LearningInsightPeriod, onPeriodChange: (LearningInsightPeriod) -> Unit) {
  val colors = Piyo.colors
  val locale = rememberAppLocale()
  val number = remember(locale) { NumberFormat.getIntegerInstance(locale) }
  val records by AppData.gameProgress.records.collectAsStateWithLifecycle()
  val retention by AppData.retention.records.collectAsStateWithLifecycle()
  val reviewItems by AppData.review.items.collectAsStateWithLifecycle()
  val mistakes by MascotStore.mistakeCounts.collectAsStateWithLifecycle()
  val insights = remember(period, records, retention, reviewItems, mistakes) {
    LearningInsights.make(period, app.piyokey.android.data.progress.RetentionClock.now(), records, retention, reviewItems.values, mistakes)
  }
  CardColumn(Modifier.testTag("my_page.insights"), spacing = 16) {
    HeavyLabel(stringResource(R.string.my_page_insights_title), Icons.Rounded.MonitorHeart)
    val periods = listOf(LearningInsightPeriod.WEEK to R.string.my_page_insights_week, LearningInsightPeriod.MONTH to R.string.my_page_insights_month)
    val periodLabel = stringResource(R.string.my_page_insights_period)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().semantics { contentDescription = periodLabel }.testTag("my_page.insights.period")) {
      periods.forEachIndexed { index, (value, res) ->
        SegmentedButton(
          selected = period == value,
          onClick = { onPeriodChange(value) },
          shape = SegmentedButtonDefaults.itemShape(index, periods.size),
          icon = {},
          colors = SegmentedButtonDefaults.colors(
            activeContainerColor = colors.card,
            activeContentColor = colors.ink,
            inactiveContainerColor = colors.backgroundBottom.copy(alpha = 0.68f),
            inactiveContentColor = colors.ink,
          ),
          modifier = Modifier.testTag("my_page.insights.period.${value.name.lowercase()}"),
        ) { Text(stringResource(res), style = PiyoType.subheadline().copy(fontWeight = FontWeight.SemiBold)) }
      }
    }
    ActivityChart(insights, period)
    val percent = insights.averageAccuracy?.let { stringResource(R.string.my_page_insights_percent_format, it) } ?: "—"
    val speed = insights.averageCharactersPerMinute?.let { stringResource(R.string.my_page_insights_speed_format, it.roundToInt()) } ?: "—"
    val accuracyChange = insights.accuracyChange?.let { stringResource(R.string.my_page_insights_change_format, String.format(locale, "%+.1f", it)) }
    val speedChange = insights.speedChange?.let { stringResource(R.string.my_page_insights_change_format, String.format(locale, "%+.0f", it)) }
    Grid2(
      listOf(
        {
          InsightMetric(
            stringResource(R.string.my_page_insights_active_days),
            stringResource(R.string.my_page_insights_days_format, insights.activeDays, period.days),
            null, Icons.Rounded.EventAvailable, colors.success, it,
          )
        },
        {
          InsightMetric(
            stringResource(R.string.my_page_insights_sessions),
            number.format(insights.sessionCount),
            pluralStringResource(R.plurals.my_page_insights_items_format, insights.completedItemCount, insights.completedItemCount),
            Icons.Rounded.CheckCircle, colors.secondary, it,
          )
        },
        { InsightMetric(stringResource(R.string.my_page_insights_accuracy), percent, accuracyChange, Icons.Rounded.TrackChanges, colors.accent, it) },
        { InsightMetric(stringResource(R.string.my_page_insights_speed), speed, speedChange, Icons.Rounded.Speed, SystemColors.orange, it) },
      ),
    )
    if (insights.sessionCount == 0) {
      Text(
        stringResource(R.string.my_page_insights_empty),
        style = PiyoType.caption().copy(color = colors.mutedInk),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
      )
    }
    HorizontalDivider(color = colors.mutedInk.copy(alpha = 0.2f))
    WeakJamoSection(insights)
    HorizontalDivider(color = colors.mutedInk.copy(alpha = 0.2f))
    ReviewProgressSection(insights, number)
  }
}

@Composable
private fun ActivityChart(insights: LearningInsights, period: LearningInsightPeriod) {
  val colors = Piyo.colors
  val minutes = InsightsFormatting.roundedMinutes(insights.totalActiveDuration)
  Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.backgroundBottom.copy(alpha = 0.58f)).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(9.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(stringResource(R.string.my_page_insights_activity_chart), style = PiyoType.caption().copy(color = colors.mutedInk, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
      Text(
        if (minutes == null) stringResource(R.string.my_page_insights_less_than_minute) else stringResource(R.string.my_page_insights_minutes_format, minutes),
        style = PiyoType.caption().copy(fontWeight = FontWeight.Bold),
      )
    }
    Row(
      Modifier.fillMaxWidth().height(62.dp),
      horizontalArrangement = Arrangement.spacedBy(if (period == LearningInsightPeriod.WEEK) 8.dp else 2.dp),
      verticalAlignment = Alignment.Bottom,
    ) {
      insights.dailyActivities.forEach { activity ->
        val value = stringResource(R.string.my_page_insights_day_activity_format, activity.sessionCount, activity.activeDuration.roundToInt())
        Box(
          Modifier
            .weight(1f)
            .height(InsightsFormatting.activityBarHeight(activity, insights).dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (activity.isActive) colors.accent else colors.accentSoft.copy(alpha = 0.65f))
            .semantics {
              contentDescription = activity.day.rawValue
              stateDescription = value
            },
        )
      }
    }
    val first = insights.dailyActivities.firstOrNull()?.day
    val last = insights.dailyActivities.lastOrNull()?.day
    if (first != null && last != null) {
      Row {
        Text(InsightsFormatting.shortDay(first), style = PiyoType.caption2().copy(color = colors.mutedInk), modifier = Modifier.weight(1f))
        Text(InsightsFormatting.shortDay(last), style = PiyoType.caption2().copy(color = colors.mutedInk))
      }
    }
  }
}

@Composable
private fun InsightMetric(title: String, value: String, detail: String?, icon: ImageVector, tint: Color, modifier: Modifier) {
  val colors = Piyo.colors
  Column(
    modifier
      .heightIn(min = 96.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(colors.backgroundBottom.copy(alpha = 0.68f))
      .semantics(mergeDescendants = true) {}
      .padding(11.dp),
    verticalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    Text(value, style = PiyoType.headline().copy(fontWeight = FontWeight.Black), maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(title, style = PiyoType.caption2().copy(color = colors.mutedInk, fontWeight = FontWeight.SemiBold))
    if (detail != null) Text(detail, style = PiyoType.caption2().copy(color = tint, fontWeight = FontWeight.Bold), maxLines = 1)
  }
}

@Composable
private fun WeakJamoSection(insights: LearningInsights) {
  val colors = Piyo.colors
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    HeavyLabel(stringResource(R.string.my_page_insights_weak_jamo), Icons.Rounded.KeyboardAlt, style = PiyoType.subheadline())
    if (insights.weakJamo.isEmpty()) {
      Text(stringResource(R.string.my_page_insights_no_weak_jamo), style = PiyoType.caption().copy(color = colors.mutedInk))
    } else {
      val maximum = maxOf(1, insights.weakJamo.maxOf { it.mistakeCount })
      insights.weakJamo.forEach { item ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(colors.error.copy(alpha = 0.11f)), contentAlignment = Alignment.Center) {
            Text(item.jamo, style = PiyoType.headline().copy(fontWeight = FontWeight.Black))
          }
          BoxWithConstraints(Modifier.weight(1f).height(16.dp), contentAlignment = Alignment.CenterStart) {
            val width = maxOf(8.dp, maxWidth * InsightsFormatting.weakJamoFraction(item.mistakeCount, maximum))
            Box(Modifier.width(width).height(8.dp).clip(CircleShape).background(colors.error.copy(alpha = 0.72f)))
          }
          Text(stringResource(R.string.my_page_insights_mistake_format, item.mistakeCount), style = PiyoType.caption().copy(color = colors.mutedInk, fontWeight = FontWeight.Bold))
        }
      }
    }
  }
}

@Composable
private fun ReviewProgressSection(insights: LearningInsights, number: NumberFormat) {
  val colors = Piyo.colors
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    HeavyLabel(stringResource(R.string.my_page_insights_review), Icons.Rounded.Sync, style = PiyoType.subheadline())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ReviewMetric(number.format(insights.activeReviewCount), stringResource(R.string.my_page_insights_review_active), colors.accent, Modifier.weight(1f))
      ReviewMetric(number.format(insights.addedReviewCount), stringResource(R.string.my_page_insights_review_added), colors.error, Modifier.weight(1f))
      ReviewMetric(number.format(insights.graduatedReviewCount), stringResource(R.string.my_page_insights_review_graduated), colors.success, Modifier.weight(1f))
    }
  }
}

@Composable
private fun ReviewMetric(value: String, title: String, tint: Color, modifier: Modifier) {
  Column(
    modifier
      .heightIn(min = 62.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(tint.copy(alpha = 0.09f))
      .semantics(mergeDescendants = true) {}
      .padding(vertical = 10.dp, horizontal = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(value, style = PiyoType.headline().copy(color = tint, fontWeight = FontWeight.Black))
    Text(
      title,
      style = PiyoType.caption2().copy(color = Piyo.colors.mutedInk, fontWeight = FontWeight.SemiBold),
      maxLines = 2,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
  }
}
