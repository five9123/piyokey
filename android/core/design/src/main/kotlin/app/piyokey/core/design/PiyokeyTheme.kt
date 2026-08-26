package app.piyokey.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PiyokeyPalette {
  val Pink = Color(0xFFE94D7D)
  val PinkSoft = Color(0xFFFFD9E5)
  val Lavender = Color(0xFF7654C6)
  val LavenderSoft = Color(0xFFE9E0FF)
  val Yellow = Color(0xFFFFCF52)
  val Green = Color(0xFF23865B)
  val Ink = Color(0xFF292331)
}

private val PiyokeyLightColors = lightColorScheme(
  primary = PiyokeyPalette.Lavender,
  onPrimary = Color.White,
  primaryContainer = PiyokeyPalette.LavenderSoft,
  onPrimaryContainer = Color(0xFF2D1D5B),
  secondary = PiyokeyPalette.Pink,
  onSecondary = Color.White,
  secondaryContainer = PiyokeyPalette.PinkSoft,
  onSecondaryContainer = Color(0xFF5A1730),
  tertiary = Color(0xFF9A6800),
  onTertiary = Color.White,
  tertiaryContainer = Color(0xFFFFE6A2),
  onTertiaryContainer = Color(0xFF352300),
  background = Color(0xFFFFF8F3),
  onBackground = PiyokeyPalette.Ink,
  surface = Color(0xFFFFFCFA),
  onSurface = PiyokeyPalette.Ink,
  surfaceVariant = Color(0xFFF1EBF2),
  onSurfaceVariant = Color(0xFF665E6C),
  outline = Color(0xFF8C838F),
  outlineVariant = Color(0xFFD9D1DB),
  error = Color(0xFFBA1A1A),
)

private val PiyokeyDarkColors = darkColorScheme(
  primary = Color(0xFFCDBBFF),
  onPrimary = Color(0xFF3C237A),
  primaryContainer = Color(0xFF533A92),
  onPrimaryContainer = Color(0xFFE9E0FF),
  secondary = Color(0xFFFFB0C5),
  onSecondary = Color(0xFF650D32),
  secondaryContainer = Color(0xFF842449),
  onSecondaryContainer = Color(0xFFFFD9E5),
  tertiary = Color(0xFFFFD16D),
  onTertiary = Color(0xFF4F3700),
  tertiaryContainer = Color(0xFF705000),
  onTertiaryContainer = Color(0xFFFFE6A2),
  background = Color(0xFF18151D),
  onBackground = Color(0xFFECE5EF),
  surface = Color(0xFF211D27),
  onSurface = Color(0xFFECE5EF),
  surfaceVariant = Color(0xFF312B37),
  onSurfaceVariant = Color(0xFFD1C7D4),
  outline = Color(0xFF9B929E),
  outlineVariant = Color(0xFF4B444F),
  error = Color(0xFFFFB4AB),
)

private val PiyokeyTypography = Typography(
  headlineMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Black,
    fontSize = 28.sp,
    lineHeight = 34.sp,
    letterSpacing = (-0.4).sp,
  ),
  headlineSmall = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 23.sp,
    lineHeight = 29.sp,
    letterSpacing = (-0.2).sp,
  ),
  titleLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 20.sp,
    lineHeight = 26.sp,
  ),
  titleMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 22.sp,
  ),
  bodyLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
  ),
  bodyMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 21.sp,
  ),
  labelLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 14.sp,
    lineHeight = 20.sp,
  ),
)

private val PiyokeyShapes = Shapes(
  extraSmall = RoundedCornerShape(10.dp),
  small = RoundedCornerShape(14.dp),
  medium = RoundedCornerShape(20.dp),
  large = RoundedCornerShape(26.dp),
  extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun PiyokeyTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = if (darkTheme) PiyokeyDarkColors else PiyokeyLightColors,
    typography = PiyokeyTypography,
    shapes = PiyokeyShapes,
    content = content,
  )
}
