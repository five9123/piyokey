package app.piyokey.android.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val CardShape = RoundedCornerShape(22.dp)

/** Rounded translucent card matching iOS `AppPalette.card` surfaces. */
@Composable
fun PiyoCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  padding: PaddingValues = PaddingValues(16.dp),
  cornerRadius: Dp = 22.dp,
  borderColor: Color? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = RoundedCornerShape(cornerRadius)
  var m = modifier
    .shadow(6.dp, shape, ambientColor = Piyo.colors.keyShadow, spotColor = Piyo.colors.keyShadow)
    .clip(shape)
    .background(Piyo.colors.card)
  if (borderColor != null) m = m.border(BorderStroke(1.5.dp, borderColor), shape)
  if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick)
  Column(m.padding(padding), content = content)
}

@Composable
fun PrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: ImageVector? = null,
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.defaultMinSize(minHeight = 52.dp),
    shape = RoundedCornerShape(18.dp),
    colors = ButtonDefaults.buttonColors(
      containerColor = Piyo.colors.accent,
      contentColor = Piyo.colors.onAccent,
      disabledContainerColor = Piyo.colors.accent.copy(alpha = 0.35f),
      disabledContentColor = Piyo.colors.onAccent.copy(alpha = 0.6f),
    ),
  ) {
    ButtonContent(text, icon)
  }
}

@Composable
fun SecondaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: ImageVector? = null,
) {
  OutlinedButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.defaultMinSize(minHeight = 48.dp),
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(1.5.dp, Piyo.colors.accent.copy(alpha = if (enabled) 0.8f else 0.3f)),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = Piyo.colors.ink),
  ) {
    ButtonContent(text, icon)
  }
}

@Composable
private fun RowScope.ButtonContent(text: String, icon: ImageVector?) {
  if (icon != null) {
    Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
  }
  Text(text, style = PiyoType.headline().copy(color = Color.Unspecified), textAlign = TextAlign.Center)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
  Row(
    modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(title, style = PiyoType.title3())
    trailing?.invoke()
  }
}

@Composable
fun PiyoChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val colors = Piyo.colors
  Box(
    modifier
      .clip(CircleShape)
      .background(if (selected) colors.accent else colors.card)
      .border(1.dp, if (selected) colors.accent else colors.mutedInk.copy(alpha = 0.3f), CircleShape)
      .clickable(role = Role.Button, onClick = onClick)
      .defaultMinSize(minHeight = 44.dp)
      .padding(horizontal = 14.dp, vertical = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, style = PiyoType.subheadline().copy(color = if (selected) colors.onAccent else colors.ink))
  }
}

/** 44dp+ icon button used for toolbar actions (settings gear, close, speaker). */
@Composable
fun PiyoIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
  IconButton(onClick = onClick, modifier = modifier) {
    Icon(icon, contentDescription = contentDescription, tint = if (tint == Color.Unspecified) Piyo.colors.ink else tint)
  }
}

/** Large rounded title header used at the top of each tab (iOS navigation large title). */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
  Row(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(title, style = PiyoType.largeTitle(), modifier = Modifier.weight(1f))
    trailing?.invoke(this)
  }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, textColor: Color = Color.White) {
  Box(modifier.clip(CircleShape).background(color).padding(horizontal = 10.dp, vertical = 4.dp)) {
    Text(text, style = PiyoType.caption().copy(color = textColor, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
  }
}
