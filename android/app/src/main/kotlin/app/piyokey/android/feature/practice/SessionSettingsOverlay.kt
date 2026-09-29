package app.piyokey.android.feature.practice

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.piyokey.android.R
import app.piyokey.android.data.settings.AppSettings
import app.piyokey.android.data.settings.BoolPref
import app.piyokey.android.data.settings.PracticePromptField
import app.piyokey.android.data.settings.PracticePromptOrder
import app.piyokey.android.feature.input.KeyboardPreferences
import app.piyokey.android.feature.input.SessionInputModeControl
import app.piyokey.android.feature.input.SessionKeyboardState
import app.piyokey.android.platform.audio.SoundEngine
import app.piyokey.android.platform.audio.SoundPrefs
import app.piyokey.android.platform.audio.TypingSoundPreset
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.feature.input.SessionInputMode

object SessionSettingsTags {
  const val OVERLAY = "practice.session_settings.overlay"
  const val CLOSE = "practice.session_settings.close"
  const val KEY_GUIDE = "practice.session_settings.key_guide"
  const val ROMAN_HINTS = "practice.session_settings.roman_hints"
  const val HAPTICS = "practice.session_settings.haptics"
  const val PHYSICAL_GUIDE = "practice.session_settings.physical_keyboard_guide"
  const val SOUND_MENU = "practice.session_settings.sound_menu"
  const val AUTO_SPEAK = "practice.session_settings.auto_speak"
  const val SOUND = "practice.session_settings.sound"
  const val SOUND_PRESET = "practice.session_settings.sound_preset"
  const val DISPLAY = "practice.session_settings.display"
  const val ORDER = "practice.session_settings.order"
  const val TARGET = "practice.session_settings.target"
  const val MEANING = "practice.session_settings.meaning"
  const val READING = "practice.session_settings.reading"
  const val JAMO = "practice.session_settings.jamo"
  const val MASCOT = "practice.session_settings.mascot"
  const val COMPOSITION = "practice.session_settings.composition"
}

/** Localization of a prompt field (iOS `PracticePromptField.localizationKey`). */
@Composable
fun promptFieldLabel(field: PracticePromptField): String = stringResource(
  when (field) {
    PracticePromptField.TARGET -> R.string.settings_practice_target
    PracticePromptField.MEANING -> R.string.settings_practice_meaning
    PracticePromptField.READING -> R.string.settings_practice_reading
  },
)

/**
 * iOS `SessionSettingsOverlay`: an in-session panel over a dimmed scrim (never a modal sheet).
 * Changes apply immediately and persist as the user's defaults.
 */
@Composable
fun SessionSettingsOverlay(
  keyboard: SessionKeyboardState,
  onClose: () -> Unit,
  onAutoSpeakChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = Piyo.colors
  var showsDisplay by rememberSaveable { mutableStateOf(true) }
  var showsOrder by rememberSaveable { mutableStateOf(false) }
  var showsSound by rememberSaveable { mutableStateOf(true) }
  val soundEnabled by SoundPrefs.effectsEnabled.flow.collectAsState()
  val presetRaw by SoundPrefs.typingPreset.flow.collectAsState()
  val orderRaw by AppSettings.practicePromptOrder.flow.collectAsState()
  val autoSpeaks by AppSettings.practiceAutoSpeaks.flow.collectAsState()

  Box(modifier.fillMaxSize().testTag(SessionSettingsTags.OVERLAY), contentAlignment = Alignment.Center) {
    Box(
      Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.12f))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    )
    val panelShape = RoundedCornerShape(24.dp)
    Column(
      Modifier
        .padding(14.dp)
        .widthIn(max = 430.dp)
        .heightIn(max = 620.dp)
        .shadow(24.dp, panelShape)
        .clip(panelShape)
        .background(if (colors.isDark) Color(0xFF221E2C) else Color(0xFFF7F4FA))
        .border(1.dp, colors.keyShadow.copy(alpha = 0.7f), panelShape)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        .verticalScroll(rememberScrollState())
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.practice_session_settings), style = PiyoType.headline().copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        Box(
          Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = onClose).testTag(SessionSettingsTags.CLOSE),
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Rounded.Cancel, contentDescription = stringResource(R.string.common_close), tint = colors.mutedInk, modifier = Modifier.size(26.dp))
        }
      }

      Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.card.copy(alpha = 0.94f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(stringResource(R.string.settings_keyboard), style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.mutedInk))
        PrefToggle(KeyboardPreferences.showsKeyGuide, stringResource(R.string.practice_setup_key_guide), Icons.Rounded.Lightbulb, SessionSettingsTags.KEY_GUIDE)
        PrefToggle(KeyboardPreferences.showsRomanHints, stringResource(R.string.practice_setup_roman_hints), Icons.Rounded.MenuBook, SessionSettingsTags.ROMAN_HINTS)
        PrefToggle(KeyboardPreferences.hapticsEnabled, stringResource(R.string.practice_setup_haptics), Icons.Rounded.TouchApp, SessionSettingsTags.HAPTICS)
        if (keyboard.allowsOsIme) {
          SessionInputModeControl(
            selection = keyboard.mode,
            onSelect = { mode ->
              keyboard.selectMode(mode)
              onClose()
            },
            onUnavailableOsIme = { keyboard.showsOsImeUnavailable = true },
          )
          if (keyboard.mode == SessionInputMode.OS_IME && Piyo.metrics.isExpanded) {
            PrefToggle(KeyboardPreferences.showsPhysicalKeyboardGuide, stringResource(R.string.physical_keyboard_show_guide), null, SessionSettingsTags.PHYSICAL_GUIDE)
          }
        }
      }

      DisclosureButton(stringResource(R.string.settings_sound), Icons.AutoMirrored.Rounded.VolumeUp, showsSound, SessionSettingsTags.SOUND_MENU) { showsSound = !showsSound }
      if (showsSound) {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          ToggleRow(
            stringResource(R.string.settings_practice_auto_speak),
            if (autoSpeaks) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
            autoSpeaks,
            SessionSettingsTags.AUTO_SPEAK,
          ) {
            AppSettings.practiceAutoSpeaks.value = it
            onAutoSpeakChanged(it)
          }
          ToggleRow(
            stringResource(R.string.practice_setup_sound),
            if (soundEnabled) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
            soundEnabled,
            SessionSettingsTags.SOUND,
          ) {
            SoundEngine.isEnabled = it
            if (it) SoundEngine.keyTap()
          }
          PresetPicker(TypingSoundPreset.resolved(presetRaw), enabled = soundEnabled) { preset ->
            SoundEngine.preset = preset
            if (SoundEngine.isEnabled) SoundEngine.keyTap(preset = preset)
          }
        }
      }

      DisclosureButton(stringResource(R.string.settings_practice_display), Icons.Rounded.ViewAgenda, showsDisplay, SessionSettingsTags.DISPLAY) { showsDisplay = !showsDisplay }
      if (showsDisplay) {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          DisclosureButton(stringResource(R.string.settings_practice_order), Icons.Rounded.SwapVert, showsOrder, SessionSettingsTags.ORDER) { showsOrder = !showsOrder }
          if (showsOrder) {
            Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              PracticePromptOrder.entries.forEach { order ->
                val selected = orderRaw == order.raw
                val label = order.fields.map { promptFieldLabel(it) }.joinToString(" → ")
                Row(
                  Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
                    .clickable(role = Role.Button) { AppSettings.practicePromptOrder.value = order.raw }
                    .semantics { this.selected = selected },
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(label, style = PiyoType.body(), modifier = Modifier.weight(1f))
                  if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.accent)
                }
              }
            }
          }
          PrefToggle(AppSettings.practiceShowsTarget, stringResource(R.string.settings_practice_target), null, SessionSettingsTags.TARGET)
          PrefToggle(AppSettings.practiceShowsMeaning, stringResource(R.string.settings_practice_meaning), null, SessionSettingsTags.MEANING)
          PrefToggle(AppSettings.practiceShowsReading, stringResource(R.string.settings_practice_reading), null, SessionSettingsTags.READING)
          PrefToggle(AppSettings.practiceShowsJamo, stringResource(R.string.settings_practice_jamo), null, SessionSettingsTags.JAMO)
          PrefToggle(AppSettings.practiceShowsMascot, stringResource(R.string.settings_practice_mascot), null, SessionSettingsTags.MASCOT)
          PrefToggle(AppSettings.practiceShowsComposition, stringResource(R.string.settings_practice_composition), null, SessionSettingsTags.COMPOSITION)
        }
      }
    }
  }
}

@Composable
private fun PrefToggle(pref: BoolPref, label: String, icon: ImageVector?, testTag: String) {
  val value by pref.flow.collectAsState()
  ToggleRow(label, icon, value, testTag) { pref.value = it }
}

@Composable
private fun ToggleRow(label: String, icon: ImageVector?, checked: Boolean, testTag: String, onChange: (Boolean) -> Unit) {
  val colors = Piyo.colors
  Row(
    Modifier
      .fillMaxWidth()
      .defaultMinSize(minHeight = 44.dp)
      .clickable(role = Role.Switch) { onChange(!checked) }
      .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.padding(end = 10.dp).size(20.dp))
    Text(label, style = PiyoType.body(), modifier = Modifier.weight(1f))
    Switch(
      checked = checked,
      onCheckedChange = null,
      colors = SwitchDefaults.colors(checkedTrackColor = colors.success, checkedThumbColor = Color.White),
    )
  }
}

@Composable
private fun DisclosureButton(title: String, icon: ImageVector, expanded: Boolean, testTag: String, onToggle: () -> Unit) {
  val colors = Piyo.colors
  Row(
    Modifier
      .fillMaxWidth()
      .defaultMinSize(minHeight = 44.dp)
      .clickable(role = Role.Button, onClick = onToggle)
      .semantics { selected = expanded }
      .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.padding(end = 10.dp).size(20.dp))
    Text(title, style = PiyoType.body(), modifier = Modifier.weight(1f))
    Icon(
      if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
      contentDescription = null,
      tint = colors.mutedInk,
    )
  }
}

@Composable
private fun PresetPicker(selection: TypingSoundPreset, enabled: Boolean, onSelect: (TypingSoundPreset) -> Unit) {
  val colors = Piyo.colors
  Row(
    Modifier
      .fillMaxWidth()
      .alpha(if (enabled) 1f else 0.45f)
      .clip(RoundedCornerShape(10.dp))
      .background(colors.mutedInk.copy(alpha = 0.12f))
      .padding(2.dp)
      .testTag(SessionSettingsTags.SOUND_PRESET),
  ) {
    listOf(
      TypingSoundPreset.SYSTEM to R.string.practice_setup_sound_system,
      TypingSoundPreset.MECHANICAL to R.string.practice_setup_sound_mechanical,
      TypingSoundPreset.SOFT to R.string.practice_setup_sound_soft,
    ).forEach { (preset, label) ->
      val selected = preset == selection
      Box(
        Modifier
          .weight(1f)
          .defaultMinSize(minHeight = 40.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(if (selected) colors.card else Color.Transparent)
          .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(preset) }
          .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
      ) {
        Text(stringResource(label), style = PiyoType.footnote().copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal))
      }
    }
  }
  Spacer(Modifier.size(0.dp))
}
