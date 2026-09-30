package app.piyokey.android.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.wrapContentWidth

enum class WidthClass { COMPACT, MEDIUM, WIDE }

/** 1:1 with iOS `HancoAdaptiveMetrics` (pt → dp). Measures the window, never the device. */
@Immutable
data class AdaptiveMetrics(val availableWidth: Dp, val availableHeight: Dp = 0.dp) {
  val widthClass: WidthClass
    get() = when {
      availableWidth < 600.dp -> WidthClass.COMPACT
      availableWidth < 900.dp -> WidthClass.MEDIUM
      else -> WidthClass.WIDE
    }

  val horizontalPadding: Dp
    get() = when (widthClass) {
      WidthClass.COMPACT -> 18.dp
      WidthClass.MEDIUM -> 24.dp
      WidthClass.WIDE -> 32.dp
    }

  val formContentMaxWidth: Dp get() = 680.dp
  val readableContentMaxWidth: Dp get() = 720.dp
  val resultContentMaxWidth: Dp get() = 760.dp
  val hubContentMaxWidth: Dp get() = 1120.dp
  val isExpanded: Boolean get() = widthClass != WidthClass.COMPACT
  val isTall: Boolean get() = isExpanded && availableHeight > availableWidth
  val typographyScale: Float get() = if (isExpanded) 1.2f else 1f

  val learningScale: Float
    get() {
      if (!isExpanded) return 1f
      if (isTall) return 1.5f
      return (1.1f + (availableHeight.value - 700f) / 450f).coerceIn(1.1f, 1.6f)
    }

  val keyboardScale: Float
    get() {
      if (!isExpanded) return 1f
      if (availableHeight < 600.dp) return 1.15f
      return if (isTall) 1.6f else (1.3f + (availableHeight.value - 700f) / 1000f).coerceIn(1.3f, 1.5f)
    }

  val sessionLaneMaxWidth: Dp
    get() = if (isExpanded) maxOf(920.dp, availableWidth - 48.dp) else 920.dp

  val hubColumnCount: Int
    get() = when (widthClass) {
      WidthClass.COMPACT -> 2
      WidthClass.MEDIUM -> 3
      WidthClass.WIDE -> 4
    }

  val usesTwoColumnDashboard: Boolean get() = widthClass == WidthClass.WIDE && !isTall
}

val LocalAdaptiveMetrics = staticCompositionLocalOf { AdaptiveMetrics(390.dp, 800.dp) }

@Composable
fun AdaptiveLayout(content: @Composable () -> Unit) {
  BoxWithConstraints {
    val metrics = AdaptiveMetrics(maxWidth, maxHeight)
    CompositionLocalProvider(LocalAdaptiveMetrics provides metrics) { content() }
  }
}

/** Equivalent of iOS `hancoCenteredContent(maxWidth:)`. */
fun Modifier.centeredContent(maxWidth: Dp): Modifier =
  fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = maxWidth).fillMaxWidth()
