package app.piyokey.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 1:1 with iOS `DesignSystem/AppPalette.swift`. */
@Immutable
data class PiyoPalette(
  val backgroundTop: Color,
  val backgroundBottom: Color,
  val card: Color,
  val ink: Color,
  val mutedInk: Color,
  val accent: Color,
  val onAccent: Color,
  val accentSoft: Color,
  val secondary: Color,
  val success: Color,
  val error: Color,
  val successText: Color,
  val errorText: Color,
  val key: Color,
  val keyShadow: Color,
  val isDark: Boolean,
)

private fun rgb(r: Double, g: Double, b: Double, a: Double = 1.0) =
  Color(r.toFloat(), g.toFloat(), b.toFloat(), a.toFloat())

val LightPalette = PiyoPalette(
  backgroundTop = rgb(1.00, 0.94, 0.97),
  backgroundBottom = rgb(0.92, 0.95, 1.00),
  card = Color(1f, 1f, 1f, 0.92f),
  ink = rgb(0.20, 0.17, 0.28),
  mutedInk = rgb(0.46, 0.43, 0.53),
  accent = rgb(0.96, 0.31, 0.55),
  onAccent = rgb(0.10, 0.03, 0.07),
  accentSoft = rgb(1.00, 0.79, 0.87),
  secondary = rgb(0.39, 0.45, 0.96),
  success = rgb(0.17, 0.68, 0.49),
  error = rgb(0.91, 0.25, 0.34),
  successText = rgb(0.04, 0.38, 0.25),
  errorText = rgb(0.62, 0.06, 0.12),
  key = Color.White,
  keyShadow = rgb(0.45, 0.40, 0.62, 0.18),
  isDark = false,
)

val DarkPalette = PiyoPalette(
  backgroundTop = rgb(0.10, 0.08, 0.15),
  backgroundBottom = rgb(0.08, 0.11, 0.19),
  card = rgb(0.16, 0.14, 0.22, 0.96),
  ink = rgb(0.96, 0.94, 0.98),
  mutedInk = rgb(0.70, 0.67, 0.77),
  accent = rgb(0.96, 0.31, 0.55),
  onAccent = rgb(0.10, 0.03, 0.07),
  accentSoft = rgb(0.39, 0.14, 0.25),
  secondary = rgb(0.55, 0.61, 1.00),
  success = rgb(0.17, 0.68, 0.49),
  error = rgb(0.91, 0.25, 0.34),
  successText = rgb(0.42, 0.92, 0.70),
  errorText = rgb(1.00, 0.58, 0.64),
  key = rgb(0.22, 0.20, 0.29),
  keyShadow = Color(0f, 0f, 0f, 0.42f),
  isDark = true,
)

val LocalPalette = staticCompositionLocalOf { LightPalette }
