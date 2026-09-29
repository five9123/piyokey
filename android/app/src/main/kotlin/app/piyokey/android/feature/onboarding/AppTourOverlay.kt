package app.piyokey.android.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.piyokey.android.R
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.core.domain.home.AppTourGeometry
import app.piyokey.core.domain.home.AppTourStep
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal val AppTourStep.target: AppTourTarget
  get() = AppTourTarget.entries.first { it.rawValue == targetRaw }

private val AppTourStep.icon: ImageVector
  get() = when (this) {
    AppTourStep.HOME -> Icons.Rounded.Bolt
    AppTourStep.DISCOVER -> Icons.Rounded.TravelExplore
    AppTourStep.PRACTICE -> Icons.Rounded.Keyboard
    AppTourStep.GAME -> Icons.Rounded.SportsEsports
    AppTourStep.MY_PAGE -> Icons.Rounded.AccountCircle
    AppTourStep.SETTINGS -> Icons.Rounded.Settings
  }

private val AppTourStep.titleRes: Int
  get() = when (this) {
    AppTourStep.HOME -> R.string.app_tour_homePrimary_title
    AppTourStep.DISCOVER -> R.string.app_tour_discoverSearch_title
    AppTourStep.PRACTICE -> R.string.app_tour_practiceCurriculum_title
    AppTourStep.GAME -> R.string.app_tour_gameModes_title
    AppTourStep.MY_PAGE -> R.string.app_tour_myPageProfile_title
    AppTourStep.SETTINGS -> R.string.app_tour_settings_title
  }

private val AppTourStep.detailRes: Int
  get() = when (this) {
    AppTourStep.HOME -> R.string.app_tour_homePrimary_detail
    AppTourStep.DISCOVER -> R.string.app_tour_discoverSearch_detail
    AppTourStep.PRACTICE -> R.string.app_tour_practiceCurriculum_detail
    AppTourStep.GAME -> R.string.app_tour_gameModes_detail
    AppTourStep.MY_PAGE -> R.string.app_tour_myPageProfile_detail
    AppTourStep.SETTINGS -> R.string.app_tour_settings_detail
  }

private val CalloutBackground = Color(0.10f, 0.09f, 0.16f, 0.97f)

/**
 * iOS `AppTourOverlay`: dimmed screen with a rounded spotlight cut around the step's target,
 * an arrow and a dark callout card (skip · progress capsules · next). Tapping anywhere advances.
 */
@Composable
internal fun AppTourOverlay(
  step: AppTourStep,
  onAdvance: () -> Unit,
  onSkip: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val colors = Piyo.colors
  val scope = rememberCoroutineScope()
  var origin by remember { mutableStateOf(Offset.Zero) }
  var sizePx by remember { mutableStateOf(Size.Zero) }
  var isHandlingAdvance by remember { mutableStateOf(false) }

  fun advanceOnce() {
    if (isHandlingAdvance) return
    isHandlingAdvance = true
    onAdvance()
    scope.launch {
      withFrameNanos { }
      isHandlingAdvance = false
    }
  }

  Box(
    modifier
      .fillMaxSize()
      .onGloballyPositioned {
        origin = it.positionInRoot()
        sizePx = Size(it.size.width.toFloat(), it.size.height.toFloat())
      }
      .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { advanceOnce() }
      .testTag("app_tour.step.${step.targetRaw}"),
  ) {
    if (sizePx == Size.Zero) return@Box
    val widthDp = with(density) { sizePx.width.toDp().value }
    val heightDp = with(density) { sizePx.height.toDp().value }
    val registered = AppTourTargets.bounds(step.target)
    val targetBox = if (registered != null) {
      with(density) {
        AppTourGeometry.Box(
          (registered.left - origin.x).toDp().value,
          (registered.top - origin.y).toDp().value,
          (registered.right - origin.x).toDp().value,
          (registered.bottom - origin.y).toDp().value,
        )
      }
    } else {
      val f = step.fallbackFrame(widthDp, heightDp)
      AppTourGeometry.Box(f[0], f[1], f[0] + f[2], f[1] + f[3])
    }
    val spotlight = AppTourGeometry.clampedSpotlight(targetBox, widthDp, heightDp)
    val below = AppTourGeometry.calloutBelow(spotlight, heightDp)
    val corner = step.spotlightCornerRadius
    val accentSoft = colors.accentSoft
    val accent = colors.accent

    Canvas(
      Modifier
        .fillMaxSize()
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .semantics { hideFromAccessibility() },
    ) {
      val topLeft = Offset(spotlight.left.dp.toPx(), spotlight.top.dp.toPx())
      val rectSize = Size(spotlight.width.dp.toPx(), spotlight.height.dp.toPx())
      val radius = CornerRadius(corner.dp.toPx())
      drawRect(Color.Black.copy(alpha = 0.78f))
      drawRoundRect(Color.Transparent, topLeft, rectSize, radius, blendMode = BlendMode.Clear)
      // Glow (iOS shadow radius 14, accent 0.9) approximated with soft wide strokes.
      for (i in 3 downTo 1) {
        drawRoundRect(
          accent.copy(alpha = 0.18f * i / 3f),
          topLeft,
          rectSize,
          radius,
          style = Stroke(width = (3 + i * 6).dp.toPx()),
        )
      }
      drawRoundRect(accentSoft, topLeft, rectSize, radius, style = Stroke(width = 3.dp.toPx()))
    }

    val arrowX = spotlight.midX.coerceIn(38f, maxOf(38f, widthDp - 38f))
    val arrowY = if (below) spotlight.bottom + 30f else spotlight.top - 30f
    Icon(
      if (below) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
      contentDescription = null,
      tint = Color.White,
      modifier = Modifier
        .centeredAt(arrowX, arrowY)
        .size(34.dp)
        .semantics { hideFromAccessibility() },
    )

    val calloutWidth = minOf(maxOf(0f, widthDp - 48f), 430f)
    AppTourCallout(
      step = step,
      onSkip = onSkip,
      onNext = ::advanceOnce,
      modifier = Modifier
        .centeredAt(widthDp / 2f, AppTourGeometry.calloutCenterY(spotlight, heightDp, below))
        .width(calloutWidth.dp),
    )
  }
}

/** Places the element so its centre is at ([x], [y]) dp inside the parent. */
private fun Modifier.centeredAt(x: Float, y: Float): Modifier = layout { measurable, constraints ->
  val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
  layout(constraints.maxWidth, constraints.maxHeight) {
    placeable.place(
      IntOffset(
        (x.dp.toPx() - placeable.width / 2f).roundToInt(),
        (y.dp.toPx() - placeable.height / 2f).roundToInt(),
      ),
    )
  }
}

@Composable
private fun AppTourCallout(step: AppTourStep, onSkip: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(24.dp)
  Column(
    modifier
      .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.36f), spotColor = Color.Black.copy(alpha = 0.36f))
      .background(CalloutBackground, shape)
      .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
      // Like iOS, a tap anywhere on the overlay (card included) advances.
      .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onNext() }
      .padding(18.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
      Box(
        Modifier.size(48.dp).background(colors.accent, RoundedCornerShape(15.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(step.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
          stringResource(step.titleRes),
          style = PiyoType.title3().copy(color = Color.White, fontWeight = FontWeight.ExtraBold),
        )
        Text(
          stringResource(step.detailRes),
          style = PiyoType.subheadline().copy(color = Color.White.copy(alpha = 0.82f)),
        )
      }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Row(
        Modifier
          .defaultMinSize(minHeight = 44.dp)
          .clickable(onClick = onSkip)
          .padding(end = 6.dp)
          .testTag("app_tour.skip"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White.copy(alpha = 0.82f), modifier = Modifier.size(14.dp))
        Text(
          stringResource(R.string.app_tour_skip),
          style = PiyoType.caption().copy(color = Color.White.copy(alpha = 0.82f), fontWeight = FontWeight.Bold),
        )
      }
      Spacer(Modifier.weight(1f).widthIn(min = 4.dp))
      val progressLabel = stringResource(R.string.app_tour_progress_format, step.position, AppTourStep.count)
      Row(
        Modifier.semantics { contentDescription = progressLabel },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        AppTourStep.entries.forEach { item ->
          Box(
            Modifier
              .height(6.dp)
              .width(if (item == step) 20.dp else 6.dp)
              .background(if (item == step) Color.White else Color.White.copy(alpha = 0.34f), CircleShape),
          )
        }
      }
      Spacer(Modifier.weight(1f).widthIn(min = 4.dp))
      val nextLabel = stringResource(if (step.isLast) R.string.app_tour_finish else R.string.app_tour_continue)
      Box(
        Modifier
          .size(44.dp)
          .background(colors.accent, CircleShape)
          .clickable(onClick = onNext)
          .semantics { contentDescription = nextLabel }
          .testTag("app_tour.next"),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          if (step.isLast) Icons.Rounded.Home else Icons.AutoMirrored.Rounded.ArrowForward,
          contentDescription = null,
          tint = Color.White,
        )
      }
    }
  }
}
