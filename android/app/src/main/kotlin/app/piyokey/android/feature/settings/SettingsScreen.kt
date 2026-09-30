package app.piyokey.android.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardAlt
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.piyokey.android.BuildConfig
import app.piyokey.android.R
import app.piyokey.android.data.settings.AppLanguage
import app.piyokey.android.data.settings.AppSettings
import app.piyokey.android.data.settings.AppTheme
import app.piyokey.android.data.settings.FontScale
import app.piyokey.android.data.settings.PracticeDisplayPreset
import app.piyokey.android.data.settings.PracticePromptField
import app.piyokey.android.data.settings.PracticePromptOrder
import app.piyokey.android.data.settings.PrefValue
import app.piyokey.android.data.settings.PrivacyNoticePolicy
import app.piyokey.android.feature.input.BuiltInKeyboardLayout
import app.piyokey.android.feature.input.KeyboardPreferences
import app.piyokey.android.feature.input.KoreanKeyboardGuideSheet
import app.piyokey.android.feature.input.PhysicalKeyboardGuidePolicy
import app.piyokey.android.feature.input.SessionInputMode
import app.piyokey.android.feature.input.SessionInputModeControl
import app.piyokey.android.platform.HapticsPrefs
import app.piyokey.android.platform.analytics.Telemetry
import app.piyokey.android.platform.audio.SoundEngine
import app.piyokey.android.platform.audio.SoundPrefs
import app.piyokey.android.platform.audio.TypingSoundPreset
import app.piyokey.android.platform.files.ContentFeedbackContext
import app.piyokey.android.platform.files.ContentFeedbackSource
import app.piyokey.android.platform.files.DocumentIO
import app.piyokey.android.platform.reminder.DailyReminder
import app.piyokey.android.platform.reminder.DailyReminderStatus
import app.piyokey.android.platform.reminder.rememberNotificationPermissionRequester
import app.piyokey.android.ui.mascot.MascotClosetRoute
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.ui.theme.centeredContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Global settings (iOS `SettingsView`). Every change persists immediately through the shared
 * preference objects; the UI language, theme and font size re-render the app live.
 *
 * @param showsCloseButton shows "Done" in the top bar (iOS sheet presentation).
 * @param onClose "Done" action; defaults to popping the app navigator.
 */
@Composable
fun SettingsScreen(
  showsCloseButton: Boolean,
  modifier: Modifier = Modifier,
  onClose: (() -> Unit)? = null,
) {
  val navigator = LocalAppNavigator.current
  val close: () -> Unit = onClose ?: { navigator.pop() }
  val colors = Piyo.colors
  var showsKoreanKeyboardGuide by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    KeyboardPreferences.repairInvalidValues()
    Telemetry.featureViewed("settings")
    Telemetry.setCrashContext(feature = "settings")
  }

  Column(
    modifier
      .fillMaxSize()
      .background(Brush.linearGradient(listOf(colors.backgroundTop, colors.backgroundBottom)))
      .statusBarsPadding()
      .testTag("settings.screen"),
  ) {
    SettingsTopBar(showsCloseButton, close)
    Column(
      Modifier
        .weight(1f)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .navigationBarsPadding(),
    ) {
      Column(
        Modifier
          .centeredContent(Piyo.metrics.formContentMaxWidth)
          .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        KeyboardSection(onShowKoreanKeyboardGuide = { showsKoreanKeyboardGuide = true })
        SoundSection()
        PracticeDisplaySection()
        DisplaySection()
        // `LocalizedApp` swaps `LocalContext`, so the registry owner lookup can miss the Activity.
        val registryOwner = LocalActivityResultRegistryOwner.current
          ?: (LocalActivity.current as? ActivityResultRegistryOwner)
          ?: LocalView.current.context.findRegistryOwner()
        if (registryOwner != null) {
          CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) { ReminderSection() }
        }
        MascotSection()
        PrivacySection()
        AppInformationSection()
      }
    }
  }

  if (showsKoreanKeyboardGuide) {
    KoreanKeyboardGuideSheet(onDismiss = { showsKoreanKeyboardGuide = false })
  }
}

@Composable
private fun SettingsTopBar(showsCloseButton: Boolean, onClose: () -> Unit) {
  val colors = Piyo.colors
  Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)) {
    Text(
      stringResource(R.string.settings_navigation_title),
      style = PiyoType.headline(),
      modifier = Modifier.align(Alignment.Center).semantics { heading() },
    )
    if (showsCloseButton) {
      Box(
        Modifier
          .align(Alignment.CenterEnd)
          .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
          .clickable(role = Role.Button, onClick = onClose)
          .testTag("settings.done")
          .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          stringResource(R.string.settings_done),
          style = PiyoType.headline().copy(color = colors.accent, fontWeight = FontWeight.Bold),
        )
      }
    }
  }
}

@Composable
private fun <T> PrefValue<T>.state(): State<T> = flow.collectAsState()

private fun captureSetting(setting: String, value: String) = Telemetry.settingChanged(setting, value)

// Keyboard ------------------------------------------------------------------------------------

@Composable
private fun KeyboardSection(onShowKoreanKeyboardGuide: () -> Unit) {
  val inputRaw by KeyboardPreferences.inputModeDefault.state()
  val layoutRaw by KeyboardPreferences.builtInLayoutDefault.state()
  val showsKeyGuide by KeyboardPreferences.showsKeyGuide.state()
  val showsRomanHints by KeyboardPreferences.showsRomanHints.state()
  val hapticsEnabled by KeyboardPreferences.hapticsEnabled.state()
  val showsPhysicalGuide by KeyboardPreferences.showsPhysicalKeyboardGuide.state()
  val inputMode = SessionInputMode.resolvedDefault(inputRaw)
  val layout = BuiltInKeyboardLayout.resolved(layoutRaw)
  val metrics = Piyo.metrics

  SettingsCard(stringResource(R.string.settings_keyboard), Icons.Filled.Keyboard, "settings.section.keyboard") {
    Column(Modifier.padding(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(stringResource(R.string.practice_setup_input_mode), style = PiyoType.subheadline().copy(fontWeight = FontWeight.SemiBold))
      SessionInputModeControl(
        selection = inputMode,
        onSelect = { mode ->
          if (mode != KeyboardPreferences.defaultInputMode) {
            KeyboardPreferences.setDefaultInputMode(mode)
            captureSetting("input_mode", KeyboardPreferences.defaultInputMode.raw)
          }
        },
        onUnavailableOsIme = onShowKoreanKeyboardGuide,
      )
    }
    SettingsDivider()
    SettingsSegmentedPicker(
      title = stringResource(R.string.keyboard_layout_title),
      options = listOf(
        BuiltInKeyboardLayout.DUBEOLSIK to stringResource(R.string.keyboard_layout_dubeolsik),
        BuiltInKeyboardLayout.KOREAN_10KEY to stringResource(R.string.keyboard_layout_korean_10key),
      ),
      selection = layout,
      onSelect = { KeyboardPreferences.setBuiltInLayout(it) },
      identifier = "settings.builtin_keyboard_layout",
      valueTag = { it.raw },
      enabled = SettingsRules.isLayoutEnabled(inputMode),
    )
    SettingsDivider()
    SettingsToggle(
      title = stringResource(R.string.practice_setup_key_guide),
      icon = Icons.Filled.Lightbulb,
      checked = showsKeyGuide,
      onCheckedChange = { KeyboardPreferences.showsKeyGuide.value = it },
      identifier = "settings.key_guide",
    )
    SettingsDivider()
    SettingsToggle(
      title = stringResource(R.string.practice_setup_roman_hints),
      detail = stringResource(R.string.practice_setup_roman_hints_detail),
      icon = Icons.AutoMirrored.Filled.MenuBook,
      checked = showsRomanHints,
      onCheckedChange = { KeyboardPreferences.showsRomanHints.value = it },
      identifier = "settings.roman_hints",
    )
    SettingsDivider()
    SettingsToggle(
      title = stringResource(R.string.practice_setup_haptics),
      icon = Icons.Filled.TouchApp,
      checked = hapticsEnabled,
      onCheckedChange = {
        KeyboardPreferences.hapticsEnabled.value = it
        // `Haptics` keeps its own cached pref object for the same key.
        HapticsPrefs.enabled.reload()
      },
      identifier = "settings.haptics",
    )
    if (PhysicalKeyboardGuidePolicy.isVisible(metrics)) {
      SettingsDivider()
      SettingsToggle(
        title = stringResource(R.string.physical_keyboard_show_guide),
        detail = stringResource(R.string.physical_keyboard_show_guide_detail),
        icon = Icons.Filled.KeyboardAlt,
        checked = showsPhysicalGuide,
        onCheckedChange = { KeyboardPreferences.setShowsPhysicalKeyboardGuide(it) },
        identifier = "settings.physical_keyboard_guide",
      )
    }
  }
}

// Sound ---------------------------------------------------------------------------------------

@Composable
private fun SoundSection() {
  val enabled by SoundPrefs.effectsEnabled.state()
  val presetRaw by SoundPrefs.typingPreset.state()
  val preset = TypingSoundPreset.resolved(presetRaw)

  SettingsCard(stringResource(R.string.settings_sound), Icons.AutoMirrored.Filled.VolumeUp, "settings.section.sound") {
    SettingsToggle(
      title = stringResource(R.string.practice_setup_sound),
      detail = stringResource(R.string.practice_setup_sound_detail),
      icon = Icons.AutoMirrored.Filled.VolumeUp,
      checked = enabled,
      onCheckedChange = { value ->
        SoundEngine.isEnabled = value
        if (value) SoundEngine.keyTap(preset = SoundPrefs.preset)
        captureSetting("sound", SettingsRules.enabledBucket(value))
      },
      identifier = "settings.sound",
    )
    SettingsDivider()
    SettingsSegmentedPicker(
      title = stringResource(R.string.practice_setup_sound_preset),
      options = listOf(
        TypingSoundPreset.SYSTEM to stringResource(R.string.practice_setup_sound_system),
        TypingSoundPreset.MECHANICAL to stringResource(R.string.practice_setup_sound_mechanical),
        TypingSoundPreset.SOFT to stringResource(R.string.practice_setup_sound_soft),
      ),
      selection = preset,
      onSelect = { newPreset ->
        SoundEngine.preset = newPreset
        if (SoundPrefs.effectsEnabled.value) SoundEngine.keyTap(preset = newPreset)
      },
      identifier = "settings.sound_preset",
      valueTag = { it.raw },
      enabled = enabled,
    )
    if (preset == TypingSoundPreset.SYSTEM) {
      Text(
        stringResource(R.string.practice_setup_sound_system_detail),
        style = PiyoType.caption().copy(color = Piyo.colors.mutedInk),
      )
    }
  }
}

// Typing display ------------------------------------------------------------------------------

@Composable
private fun PracticeDisplaySection() {
  val presetRaw by AppSettings.practiceDisplayPreset.state()
  val orderRaw by AppSettings.practicePromptOrder.state()
  val showsTarget by AppSettings.practiceShowsTarget.state()
  val showsMeaning by AppSettings.practiceShowsMeaning.state()
  val showsReading by AppSettings.practiceShowsReading.state()
  val showsJamo by AppSettings.practiceShowsJamo.state()
  val autoSpeaks by AppSettings.practiceAutoSpeaks.state()
  val showsMascot by AppSettings.practiceShowsMascot.state()
  val showsComposition by AppSettings.practiceShowsComposition.state()
  val choseongMeaning by AppSettings.choseongShowsMeaning.state()

  SettingsCard(stringResource(R.string.settings_practice_display), Icons.Filled.Dashboard, "settings.section.practice_display") {
    SettingsSegmentedPicker(
      title = stringResource(R.string.settings_practice_display_preset),
      options = listOf(
        PracticeDisplayPreset.LEARNING to stringResource(R.string.settings_practice_display_learning),
        PracticeDisplayPreset.FOCUS to stringResource(R.string.settings_practice_display_focus),
      ),
      selection = PracticeDisplayPreset.resolved(presetRaw),
      onSelect = { preset ->
        AppSettings.practiceDisplayPreset.value = preset.raw
        applyPracticePreset(preset)
        captureSetting("practice_display", preset.raw)
      },
      identifier = "settings.practice_display_preset",
      valueTag = { it.raw },
    )
    SettingsDivider()
    PracticeOrderPicker(PracticePromptOrder.resolved(orderRaw))
    SettingsDivider()
    SettingsToggle(
      title = stringResource(R.string.settings_practice_target),
      icon = Icons.Filled.TextFields,
      checked = showsTarget,
      onCheckedChange = { AppSettings.practiceShowsTarget.value = it },
      identifier = "settings.practice_target",
    )
    SettingsDivider()
    SettingsToggle(
      title = stringResource(R.string.settings_practice_meaning),
      detail = stringResource(R.string.settings_practice_meaning_detail),
      icon = Icons.AutoMirrored.Filled.MenuBook,
      checked = showsMeaning,
      onCheckedChange = { AppSettings.practiceShowsMeaning.value = it },
      identifier = "settings.practice_meaning",
    )
    SettingsDivider()
    SettingsToggle(
      title = stringResource(R.string.settings_practice_reading),
      detail = stringResource(R.string.settings_practice_reading_detail),
      icon = Icons.Filled.ChatBubble,
      checked = showsReading,
      onCheckedChange = { AppSettings.practiceShowsReading.value = it },
      identifier = "settings.practice_reading",
    )
    SettingsDivider()
    SettingsToggle(
      title = stringResource(R.string.settings_practice_jamo),
      icon = Icons.Filled.GridView,
      checked = showsJamo,
      onCheckedChange = { AppSettings.practiceShowsJamo.value = it },
      identifier = "settings.practice_jamo",
    )
    SettingsDivider()
    SettingsToggle(
      title = stringResource(R.string.settings_practice_auto_speak),
      detail = stringResource(R.string.settings_practice_auto_speak_detail),
      icon = Icons.AutoMirrored.Filled.VolumeUp,
      checked = autoSpeaks,
      onCheckedChange = { AppSettings.practiceAutoSpeaks.value = it },
      identifier = "settings.practice_auto_speak",
    )
    SettingsDivider()
    SettingsToggle(
      title = stringResource(R.string.settings_practice_mascot),
      icon = Icons.Filled.Egg,
      checked = showsMascot,
      onCheckedChange = { AppSettings.practiceShowsMascot.value = it },
      identifier = "settings.practice_mascot",
    )
    SettingsDivider()
    SettingsToggle(
      title = stringResource(R.string.settings_practice_composition),
      detail = stringResource(R.string.settings_practice_composition_detail),
      icon = Icons.Filled.EditNote,
      checked = showsComposition,
      onCheckedChange = { AppSettings.practiceShowsComposition.value = it },
      identifier = "settings.practice_composition",
    )
    SettingsDivider()
    SettingsToggle(
      title = stringResource(R.string.settings_choseong_meaning),
      detail = stringResource(R.string.settings_choseong_meaning_detail),
      icon = Icons.AutoMirrored.Filled.MenuBook,
      checked = choseongMeaning,
      onCheckedChange = { AppSettings.choseongShowsMeaning.value = it },
      identifier = "settings.choseong_meaning",
    )
  }
}

private fun applyPracticePreset(preset: PracticeDisplayPreset) {
  val toggles = SettingsRules.toggles(preset)
  AppSettings.practiceShowsTarget.value = toggles.showsTarget
  AppSettings.practiceShowsMeaning.value = toggles.showsMeaning
  AppSettings.practiceShowsReading.value = toggles.showsReading
  AppSettings.practiceShowsJamo.value = toggles.showsJamo
  AppSettings.practiceShowsMascot.value = toggles.showsMascot
  AppSettings.practiceShowsComposition.value = toggles.showsComposition
}

@Composable
private fun PracticeOrderPicker(order: PracticePromptOrder) {
  val fieldNames = mapOf(
    PracticePromptField.TARGET to stringResource(R.string.settings_practice_target),
    PracticePromptField.MEANING to stringResource(R.string.settings_practice_meaning),
    PracticePromptField.READING to stringResource(R.string.settings_practice_reading),
  )
  val title = stringResource(R.string.settings_practice_order)
  Column(Modifier.padding(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(title, style = PiyoType.subheadline().copy(fontWeight = FontWeight.SemiBold))
    Text(stringResource(R.string.settings_practice_order_detail), style = PiyoType.caption().copy(color = Piyo.colors.mutedInk))
    SettingsMenuPicker(
      label = title,
      options = PracticePromptOrder.entries,
      selection = order,
      optionLabel = { SettingsRules.promptOrderLabel(it) { field -> fieldNames.getValue(field) } },
      onSelect = { AppSettings.practicePromptOrder.value = it.raw },
      identifier = "settings.practice_order",
      valueTag = { it.raw },
    )
  }
}

// Display -------------------------------------------------------------------------------------

@Composable
private fun DisplaySection() {
  val fontRaw by AppSettings.fontScale.state()
  val themeRaw by AppSettings.theme.state()
  val languageRaw by AppSettings.language.state()
  val fontScale = FontScale.resolved(fontRaw)
  val theme = AppTheme.resolved(themeRaw)
  val colors = Piyo.colors

  SettingsCard(stringResource(R.string.settings_display), Icons.Filled.FormatSize, "settings.section.display") {
    SettingsSegmentedPicker(
      title = stringResource(R.string.settings_font_size),
      options = listOf(
        FontScale.SMALL to stringResource(R.string.settings_font_size_small),
        FontScale.STANDARD to stringResource(R.string.settings_font_size_standard),
        FontScale.LARGE to stringResource(R.string.settings_font_size_large),
      ),
      selection = fontScale,
      onSelect = { AppSettings.fontScale.value = it.raw },
      identifier = "settings.font_scale",
      valueTag = { it.raw },
    )
    SettingsDivider()
    FontPreview(fontScale)
    SettingsDivider()
    SettingsSegmentedPicker(
      title = stringResource(R.string.settings_theme),
      options = listOf(
        AppTheme.LIGHT to stringResource(R.string.settings_theme_light),
        AppTheme.DARK to stringResource(R.string.settings_theme_dark),
      ),
      selection = theme,
      onSelect = {
        AppSettings.theme.value = it.raw
        captureSetting("theme", it.raw)
      },
      identifier = "settings.theme",
      valueTag = { it.raw },
    )
    SettingsDivider()
    Column(Modifier.fillMaxWidth().testTag("settings.language"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(stringResource(R.string.settings_language), style = PiyoType.subheadline().copy(fontWeight = FontWeight.SemiBold))
      Column {
        AppLanguage.entries.forEach { option ->
          val isSelected = languageRaw == option.raw
          Row(
            Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 44.dp)
              .clickable(role = Role.Button) {
                if (AppSettings.language.value != option.raw) {
                  AppSettings.language.value = option.raw
                  captureSetting("language", option.raw)
                  DailyReminder.refreshLocalizedContent()
                }
              }
              .semantics { selected = isSelected }
              .testTag("settings.language.${option.raw}"),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(stringResource(languageNameRes(option)), style = PiyoType.body(), modifier = Modifier.weight(1f))
            if (isSelected) {
              Icon(Icons.Filled.Check, contentDescription = null, tint = colors.ink, modifier = Modifier.size(18.dp))
            }
          }
        }
      }
    }
  }
}

private fun languageNameRes(language: AppLanguage): Int = when (language) {
  AppLanguage.JAPANESE -> R.string.settings_language_japanese
  AppLanguage.ENGLISH -> R.string.settings_language_english
  AppLanguage.SPANISH -> R.string.settings_language_spanish
  AppLanguage.GERMAN -> R.string.settings_language_german
  AppLanguage.FRENCH -> R.string.settings_language_french
}

@Composable
private fun FontPreview(fontScale: FontScale) {
  val colors = Piyo.colors
  Row(
    Modifier.fillMaxWidth().padding(vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    val shape = RoundedCornerShape(14.dp)
    Box(
      Modifier
        .size(width = 58.dp, height = 52.dp)
        .shadow(3.dp, shape, ambientColor = colors.keyShadow, spotColor = colors.keyShadow)
        .background(colors.key, shape)
        .semantics { stateDescription = fontScale.raw }
        .testTag("settings.font_preview"),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        "한",
        style = PiyoType.body().copy(fontSize = (30f * fontScale.multiplier).sp, fontWeight = FontWeight.Bold),
      )
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(stringResource(R.string.settings_font_preview), style = PiyoType.subheadline().copy(fontWeight = FontWeight.Bold))
      Text(stringResource(R.string.settings_font_preview_detail), style = PiyoType.caption().copy(color = colors.mutedInk))
    }
  }
}

// Practice reminder ---------------------------------------------------------------------------

@Composable
private fun ReminderSection() {
  val preference by DailyReminder.preference.collectAsState()
  val status by DailyReminder.status.collectAsState()
  val requestPermission = rememberNotificationPermissionRequester()
  val scope = rememberCoroutineScope()
  val colors = Piyo.colors
  val isScheduling = status == DailyReminderStatus.SCHEDULING

  SettingsCard(stringResource(R.string.retention_reminder_title), Icons.Filled.NotificationsActive, "settings.section.reminder") {
    Row(
      Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 44.dp)
        .toggleable(value = preference.isEnabled, role = Role.Switch) { enabled ->
          scope.launch { DailyReminder.setEnabled(enabled, requestPermission) }
        }
        .testTag("retention.reminder.toggle"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        stringResource(R.string.retention_reminder_detail),
        style = PiyoType.subheadline().copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.weight(1f),
      )
      SettingsSwitch(preference.isEnabled)
    }
    Row(
      Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      SettingsMenuPicker(
        label = stringResource(R.string.retention_reminder_hour),
        options = SettingsRules.reminderHours,
        selection = preference.hour,
        optionLabel = SettingsRules::twoDigits,
        onSelect = { DailyReminder.setTime(it, preference.minute) },
        identifier = "retention.reminder.hour",
        valueTag = { it.toString() },
        enabled = !isScheduling,
      )
      Text(":", style = PiyoType.headline())
      val minutes = SettingsRules.reminderMinutes.let { if (preference.minute in it) it else (it + preference.minute).sorted() }
      SettingsMenuPicker(
        label = stringResource(R.string.retention_reminder_minute),
        options = minutes,
        selection = preference.minute,
        optionLabel = SettingsRules::twoDigits,
        onSelect = { DailyReminder.setTime(preference.hour, it) },
        identifier = "retention.reminder.minute",
        valueTag = { it.toString() },
        enabled = !isScheduling,
      )
      Text(
        stringResource(R.string.retention_reminder_local_time),
        style = PiyoType.caption().copy(color = colors.mutedInk, fontWeight = FontWeight.Bold),
      )
      Spacer(Modifier.weight(1f))
    }
    when (status) {
      DailyReminderStatus.DENIED -> Text(
        stringResource(R.string.retention_reminder_denied),
        style = PiyoType.caption().copy(color = colors.error),
        modifier = Modifier.testTag("retention.reminder.denied"),
      )
      DailyReminderStatus.FAILED -> Text(
        stringResource(R.string.retention_reminder_failed),
        style = PiyoType.caption().copy(color = colors.error),
        modifier = Modifier.testTag("retention.reminder.failed"),
      )
      else -> Unit
    }
  }
}

// Piyo ----------------------------------------------------------------------------------------

@Composable
private fun MascotSection() {
  val navigator = LocalAppNavigator.current
  val colors = Piyo.colors
  SettingsCard(stringResource(R.string.settings_mascot), Icons.Filled.Egg, "settings.section.mascot") {
    Row(
      Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 44.dp)
        .clickable(role = Role.Button) { navigator.push(MascotClosetRoute()) }
        .testTag("settings.mascot")
        .padding(vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      SettingsIconTile(Icons.Filled.Checkroom)
      SettingsTitleDetail(
        stringResource(R.string.closet_title),
        stringResource(R.string.settings_mascot_detail),
        Modifier.weight(1f),
      )
      Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.mutedInk)
    }
  }
}

// Privacy -------------------------------------------------------------------------------------

@Composable
private fun PrivacySection() {
  val analytics by AppSettings.anonymousAnalyticsEnabled.state()
  val diagnostics by AppSettings.crashDiagnosticsEnabled.state()
  val context = LocalContext.current
  val activity = LocalActivity.current

  SettingsCard(stringResource(R.string.settings_privacy), Icons.Filled.PanTool, "settings.section.privacy") {
    SettingsToggle(
      title = stringResource(R.string.settings_anonymous_analytics),
      detail = stringResource(R.string.settings_anonymous_analytics_detail),
      icon = Icons.Filled.BarChart,
      checked = analytics,
      onCheckedChange = { enabled ->
        AppSettings.anonymousAnalyticsEnabled.value = enabled
        AppSettings.privacyNoticeVersion.value = PrivacyNoticePolicy.CURRENT_VERSION
        Telemetry.updateConsent(productAnalytics = enabled, crashDiagnostics = AppSettings.crashDiagnosticsEnabled.value)
        if (enabled) captureSetting("analytics_consent", "enabled")
      },
      identifier = "settings.anonymous_analytics",
    )
    SettingsDivider()
    SettingsToggle(
      title = stringResource(R.string.settings_crash_diagnostics),
      detail = stringResource(R.string.settings_crash_diagnostics_detail),
      icon = Icons.Filled.MonitorHeart,
      checked = diagnostics,
      onCheckedChange = { enabled ->
        AppSettings.crashDiagnosticsEnabled.value = enabled
        Telemetry.updateConsent(productAnalytics = AppSettings.anonymousAnalyticsEnabled.value, crashDiagnostics = enabled)
        captureSetting("diagnostics_consent", SettingsRules.enabledBucket(enabled))
      },
      identifier = "settings.crash_diagnostics",
    )
    Text(
      stringResource(R.string.settings_analytics_privacy_note),
      style = PiyoType.caption().copy(color = Piyo.colors.mutedInk),
      modifier = Modifier.padding(top = 4.dp),
    )
    SettingsDivider()
    SettingsLinkRow(stringResource(R.string.settings_privacy_policy), Icons.Filled.PanTool, "settings.privacy_policy") {
      DocumentIO.openUrl(activity ?: context, BuildConfig.PRIVACY_URL)
    }
  }
}

// App information -----------------------------------------------------------------------------

@Composable
private fun AppInformationSection() {
  val context = LocalContext.current
  val activity = LocalActivity.current
  SettingsCard(stringResource(R.string.settings_app_information), Icons.Filled.Info, "settings.section.app_information") {
    VersionRow()
    SettingsDivider()
    SettingsLinkRow(stringResource(R.string.content_feedback_settings_title), Icons.Filled.Forum, "settings.content_feedback") {
      DocumentIO.openContentFeedback(activity ?: context, ContentFeedbackContext.general(ContentFeedbackSource.SETTINGS))
    }
    SettingsDivider()
    SettingsLinkRow(stringResource(R.string.settings_support), Icons.Filled.QuestionAnswer, "settings.support") {
      DocumentIO.openUrl(activity ?: context, BuildConfig.SUPPORT_URL)
    }
  }
}

@Composable
private fun VersionRow() {
  val context = LocalContext.current
  val colors = Piyo.colors
  val display = stringResource(R.string.settings_version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toString())
  val copiedText = stringResource(R.string.settings_version_copied)
  val copyHint = stringResource(R.string.settings_version_copy_hint)
  var copyRevision by remember { mutableIntStateOf(0) }
  val didCopy = copyRevision > 0

  LaunchedEffect(copyRevision) {
    if (copyRevision > 0) {
      delay(SettingsRules.VERSION_COPIED_MILLIS)
      copyRevision = 0
    }
  }

  val copy = {
    copyToClipboard(context, display)
    copyRevision += 1
  }
  Row(
    Modifier
      .fillMaxWidth()
      .defaultMinSize(minHeight = 44.dp)
      .clickable(role = Role.Button, onClick = copy)
      .semantics(mergeDescendants = true) {
        contentDescription = display
        if (didCopy) stateDescription = copiedText
        onClick(label = copyHint) { copy(); true }
      }
      .testTag("settings.version")
      .padding(vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    SettingsIconTile(Icons.Filled.ContentCopy)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(display, style = PiyoType.subheadline().copy(fontWeight = FontWeight.SemiBold))
      if (didCopy) {
        Text(
          copiedText,
          style = PiyoType.caption().copy(color = colors.accent),
          modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
      }
    }
  }
}

private tailrec fun Context.findRegistryOwner(): ActivityResultRegistryOwner? = when (this) {
  is ActivityResultRegistryOwner -> this
  is ContextWrapper -> baseContext.findRegistryOwner()
  else -> null
}

private fun copyToClipboard(context: Context, text: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
  clipboard.setPrimaryClip(ClipData.newPlainText(text, text))
}
