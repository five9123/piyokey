package app.piyokey.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.piyokey.android.data.settings.AppTheme
import app.piyokey.android.data.settings.FontScale

/** User font scale (small/standard/large) × adaptive typography scale, like iOS `hancoFontScale`. */
val LocalFontScale = staticCompositionLocalOf { 1f }

object Piyo {
  val colors: PiyoPalette
    @Composable @ReadOnlyComposable get() = LocalPalette.current

  val metrics: AdaptiveMetrics
    @Composable @ReadOnlyComposable get() = LocalAdaptiveMetrics.current

  val fontScale: Float
    @Composable @ReadOnlyComposable get() = LocalFontScale.current

  /** Scaled `sp` that follows the in-app font-size setting. */
  @Composable @ReadOnlyComposable
  fun sp(value: Float): TextUnit = (value * LocalFontScale.current).sp
}

/** Rounded, bold type roles approximating iOS `.system(design: .rounded)` text styles. */
object PiyoType {
  @Composable fun largeTitle() = style(34f, FontWeight.Bold)
  @Composable fun title() = style(28f, FontWeight.Bold)
  @Composable fun title2() = style(22f, FontWeight.Bold)
  @Composable fun title3() = style(20f, FontWeight.SemiBold)
  @Composable fun headline() = style(17f, FontWeight.SemiBold)
  @Composable fun body() = style(17f, FontWeight.Normal)
  @Composable fun callout() = style(16f, FontWeight.Normal)
  @Composable fun subheadline() = style(15f, FontWeight.Normal)
  @Composable fun footnote() = style(13f, FontWeight.Normal)
  @Composable fun caption() = style(12f, FontWeight.Normal)
  @Composable fun caption2() = style(11f, FontWeight.Normal)

  @Composable
  fun style(size: Float, weight: FontWeight): TextStyle =
    TextStyle(fontSize = Piyo.sp(size), fontWeight = weight, color = LocalPalette.current.ink)
}

@Composable
fun PiyokeyTheme(theme: AppTheme, fontScale: FontScale, content: @Composable () -> Unit) {
  val palette = if (theme == AppTheme.DARK) DarkPalette else LightPalette
  val scheme = if (palette.isDark) {
    darkColorScheme(
      primary = palette.accent, onPrimary = palette.onAccent, secondary = palette.secondary,
      background = palette.backgroundBottom, surface = palette.card, onSurface = palette.ink,
      onBackground = palette.ink, error = palette.error,
    )
  } else {
    lightColorScheme(
      primary = palette.accent, onPrimary = palette.onAccent, secondary = palette.secondary,
      background = palette.backgroundBottom, surface = palette.card, onSurface = palette.ink,
      onBackground = palette.ink, error = palette.error,
    )
  }
  AdaptiveLayout {
    val metrics = LocalAdaptiveMetrics.current
    CompositionLocalProvider(
      LocalPalette provides palette,
      LocalFontScale provides fontScale.multiplier * metrics.typographyScale,
    ) {
      MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
  }
}

/** Soft vertical gradient used behind every screen (iOS backgroundTop → backgroundBottom). */
@Composable
fun PiyoBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  val palette = Piyo.colors
  Box(
    modifier
      .fillMaxSize()
      .background(Brush.verticalGradient(listOf(palette.backgroundTop, palette.backgroundBottom))),
  ) { content() }
}
