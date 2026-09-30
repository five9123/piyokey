package app.piyokey.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType

/** iOS `settingsCard`: heavy headline label + content on a 24dp card. */
@Composable
internal fun SettingsCard(
  title: String,
  icon: ImageVector,
  identifier: String,
  content: @Composable ColumnScope.() -> Unit,
) {
  val colors = Piyo.colors
  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .background(colors.card)
      .padding(18.dp),
    verticalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Row(
      Modifier.padding(bottom = 7.dp).semantics(mergeDescendants = true) { heading() }.testTag(identifier),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(8.dp))
      Text(title, style = PiyoType.headline().copy(fontWeight = FontWeight.ExtraBold))
    }
    content()
  }
}

@Composable
internal fun SettingsDivider() {
  HorizontalDivider(Modifier.alpha(0.5f), color = Piyo.colors.mutedInk.copy(alpha = 0.35f))
}

/** 32dp accent icon tile used by toggles and link rows. */
@Composable
internal fun SettingsIconTile(icon: ImageVector) {
  val colors = Piyo.colors
  Box(
    Modifier.size(32.dp).background(colors.accentSoft.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
  }
}

@Composable
internal fun SettingsTitleDetail(title: String, detail: String?, modifier: Modifier = Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(if (detail == null) 0.dp else 2.dp)) {
    Text(title, style = PiyoType.subheadline().copy(fontWeight = FontWeight.SemiBold))
    if (detail != null) Text(detail, style = PiyoType.caption().copy(color = Piyo.colors.mutedInk))
  }
}

@Composable
internal fun SettingsSwitch(checked: Boolean, enabled: Boolean = true) {
  val colors = Piyo.colors
  Switch(
    checked = checked,
    onCheckedChange = null,
    enabled = enabled,
    colors = SwitchDefaults.colors(
      checkedTrackColor = colors.accent,
      checkedThumbColor = Color.White,
      checkedBorderColor = Color.Transparent,
      uncheckedTrackColor = colors.mutedInk.copy(alpha = 0.22f),
      uncheckedThumbColor = Color.White,
      uncheckedBorderColor = Color.Transparent,
    ),
  )
}

/** iOS `settingToggle`: icon tile, title/detail and a trailing switch; the whole row toggles. */
@Composable
internal fun SettingsToggle(
  title: String,
  icon: ImageVector?,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  identifier: String,
  detail: String? = null,
  enabled: Boolean = true,
) {
  Row(
    Modifier
      .fillMaxWidth()
      .defaultMinSize(minHeight = 44.dp)
      .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
      .testTag(identifier)
      .padding(vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (icon != null) SettingsIconTile(icon)
    SettingsTitleDetail(title, detail, Modifier.weight(1f))
    SettingsSwitch(checked, enabled)
  }
}

/** iOS `settingPicker` with `.segmented` style. Segments are tagged `<identifier>.<value>`. */
@Composable
internal fun <T> SettingsSegmentedPicker(
  title: String,
  options: List<Pair<T, String>>,
  selection: T,
  onSelect: (T) -> Unit,
  identifier: String,
  valueTag: (T) -> String,
  enabled: Boolean = true,
) {
  val colors = Piyo.colors
  Column(
    Modifier
      .fillMaxWidth()
      .alpha(if (enabled) 1f else SettingsRules.DISABLED_ALPHA)
      .padding(vertical = 7.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(title, style = PiyoType.subheadline().copy(fontWeight = FontWeight.SemiBold))
    Row(
      Modifier
        .fillMaxWidth()
        .background(colors.mutedInk.copy(alpha = 0.12f), RoundedCornerShape(9.dp))
        .padding(2.dp)
        .testTag(identifier),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      options.forEach { (value, label) ->
        val isSelected = value == selection
        val shape = RoundedCornerShape(7.dp)
        Box(
          Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 44.dp)
            .then(if (isSelected) Modifier.shadow(2.dp, shape).background(colors.card, shape) else Modifier)
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Tab) { if (!isSelected) onSelect(value) }
            .semantics { selected = isSelected }
            .testTag("$identifier.${valueTag(value)}")
            .padding(horizontal = 6.dp, vertical = 6.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            label,
            style = PiyoType.footnote().copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
            maxLines = 2,
          )
        }
      }
    }
  }
}

/** iOS `Picker` with `.menu` style: current value + chevrons, options in a dropdown. */
@Composable
internal fun <T> SettingsMenuPicker(
  label: String,
  options: List<T>,
  selection: T,
  optionLabel: (T) -> String,
  onSelect: (T) -> Unit,
  identifier: String,
  valueTag: (T) -> String,
  enabled: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val colors = Piyo.colors
  var expanded by remember { mutableStateOf(false) }
  Box(modifier) {
    Row(
      Modifier
        .defaultMinSize(minHeight = 44.dp)
        .clip(RoundedCornerShape(10.dp))
        .clickable(enabled = enabled, role = Role.DropdownList) { expanded = true }
        .semantics { contentDescription = label }
        .testTag(identifier)
        .padding(horizontal = 6.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val tint = if (enabled) colors.accent else colors.mutedInk
      Text(optionLabel(selection), style = PiyoType.body().copy(color = tint))
      Icon(Icons.Filled.UnfoldMore, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = colors.card) {
      options.forEach { option ->
        DropdownMenuItem(
          text = {
            Text(
              optionLabel(option),
              style = PiyoType.body().copy(fontWeight = if (option == selection) FontWeight.SemiBold else FontWeight.Normal),
            )
          },
          onClick = {
            expanded = false
            if (option != selection) onSelect(option)
          },
          modifier = Modifier.semantics { selected = option == selection }.testTag("$identifier.${valueTag(option)}"),
        )
      }
    }
  }
}

/** iOS `externalLinkLabel`: icon tile, title and a trailing ↗. */
@Composable
internal fun SettingsLinkRow(title: String, icon: ImageVector, identifier: String, onClick: () -> Unit) {
  val colors = Piyo.colors
  Row(
    Modifier
      .fillMaxWidth()
      .defaultMinSize(minHeight = 44.dp)
      .clickable(role = Role.Button, onClick = onClick)
      .testTag(identifier)
      .padding(vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    SettingsIconTile(icon)
    Text(title, style = PiyoType.subheadline().copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
    Icon(Icons.Filled.NorthEast, contentDescription = null, tint = colors.mutedInk, modifier = Modifier.size(14.dp))
  }
}
