package app.piyokey.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.piyokey.core.design.PiyokeyIcon
import app.piyokey.core.design.PiyokeyIconKind
import app.piyokey.core.settings.AppLanguage
import app.piyokey.core.settings.AppPreferences
import app.piyokey.core.settings.AppTheme
import app.piyokey.core.settings.FontScale
import app.piyokey.core.settings.InputMode
import app.piyokey.core.settings.KeySoundStyle
import app.piyokey.core.settings.PracticeDisplayPreset
import app.piyokey.core.settings.PracticePromptOrder
import app.piyokey.core.settings.PrivacyNoticePolicy

@Composable
fun CommonSettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val description = stringResource(R.string.settings_open)
  Surface(
    modifier = modifier.testTag("common-settings-button"),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
    shadowElevation = 5.dp,
  ) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
      PiyokeyIcon(
        kind = PiyokeyIconKind.SETTINGS,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
  preferences: AppPreferences,
  reminderEnabled: Boolean,
  reminderHour: Int,
  reminderMinute: Int,
  onPreferencesChange: (AppPreferences) -> Unit,
  onReminderEnabled: (Boolean) -> Unit,
  onReminderTime: (Int, Int) -> Unit,
  onOpenPrivacy: () -> Unit,
  onOpenFeedback: () -> Unit,
  onOpenSupport: () -> Unit,
  onDismiss: () -> Unit,
) {
  var showTimePicker by remember { mutableStateOf(false) }
  var showPrivacyChoices by remember { mutableStateOf(false) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = Modifier.testTag("settings-sheet"),
  ) {
    LazyColumn(
      contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
      }
      item {
        SettingsCard(stringResource(R.string.settings_sound)) {
          ToggleRow(stringResource(R.string.settings_sound_effects), preferences.soundEffectsEnabled) {
            onPreferencesChange(preferences.copy(soundEffectsEnabled = it))
          }
          ChoiceRow(
            values = KeySoundStyle.entries,
            selected = preferences.keySoundStyle,
            label = { keySoundLabel(it) },
            onSelect = { onPreferencesChange(preferences.copy(keySoundStyle = it)) },
          )
          ToggleRow(stringResource(R.string.settings_haptics), preferences.hapticsEnabled) {
            onPreferencesChange(preferences.copy(hapticsEnabled = it))
          }
        }
      }
      item {
        SettingsCard(stringResource(R.string.settings_keyboard)) {
          ToggleRow(stringResource(R.string.settings_roman_hints), preferences.romanHintsEnabled) {
            onPreferencesChange(preferences.copy(romanHintsEnabled = it))
          }
          ToggleRow(stringResource(R.string.settings_key_guide), preferences.keyGuideEnabled) {
            onPreferencesChange(preferences.copy(keyGuideEnabled = it))
          }
          Text(stringResource(R.string.settings_default_input), fontWeight = FontWeight.Bold)
          ChoiceRow(
            values = InputMode.entries,
            selected = preferences.defaultInputMode,
            label = { if (it == InputMode.BUILTIN) stringResource(R.string.settings_input_builtin) else stringResource(R.string.settings_input_os) },
            onSelect = { onPreferencesChange(preferences.copy(defaultInputMode = it)) },
          )
          if (preferences.defaultInputMode == InputMode.OS_IME) {
            ToggleRow(
              stringResource(R.string.settings_physical_keyboard_guide),
              preferences.showsPhysicalKeyboardGuide,
            ) {
              onPreferencesChange(preferences.copy(showsPhysicalKeyboardGuide = it))
            }
          }
        }
      }
      item {
        SettingsCard(stringResource(R.string.settings_appearance)) {
          Text(stringResource(R.string.settings_font_size), fontWeight = FontWeight.Bold)
          ChoiceRow(
            values = FontScale.entries,
            selected = preferences.fontScale,
            label = { fontScaleLabel(it) },
            onSelect = { onPreferencesChange(preferences.copy(fontScale = it)) },
          )
          Text(stringResource(R.string.settings_theme), fontWeight = FontWeight.Bold)
          ChoiceRow(
            values = AppTheme.entries,
            selected = preferences.theme,
            label = { if (it == AppTheme.LIGHT) stringResource(R.string.settings_light) else stringResource(R.string.settings_dark) },
            onSelect = { onPreferencesChange(preferences.copy(theme = it)) },
          )
          Text(stringResource(R.string.settings_language), fontWeight = FontWeight.Bold)
          ChoiceRow(
            values = AppLanguage.entries,
            selected = preferences.language,
            label = { languageLabel(it) },
            onSelect = { onPreferencesChange(preferences.copy(language = it)) },
          )
        }
      }
      item {
        SettingsCard(stringResource(R.string.settings_practice_display)) {
          Text(stringResource(R.string.settings_preset), fontWeight = FontWeight.Bold)
          ChoiceRow(
            values = PracticeDisplayPreset.entries,
            selected = preferences.displayPreset,
            label = { if (it == PracticeDisplayPreset.LEARNING) stringResource(R.string.settings_learning) else stringResource(R.string.settings_focus) },
            onSelect = { onPreferencesChange(preferences.withDisplayPreset(it)) },
          )
          ToggleRow(stringResource(R.string.settings_show_target), preferences.showsTarget) {
            onPreferencesChange(preferences.copy(showsTarget = it))
          }
          ToggleRow(stringResource(R.string.settings_show_meaning), preferences.showsMeaning) {
            onPreferencesChange(preferences.copy(showsMeaning = it))
          }
          ToggleRow(stringResource(R.string.settings_show_reading), preferences.showsReading) {
            onPreferencesChange(preferences.copy(showsReading = it))
          }
          Text(stringResource(R.string.settings_prompt_order), fontWeight = FontWeight.Bold)
          TextButton(
            onClick = {
              val next = PracticePromptOrder.entries[(preferences.promptOrder.ordinal + 1) % PracticePromptOrder.entries.size]
              onPreferencesChange(preferences.copy(promptOrder = next))
            },
            modifier = Modifier.fillMaxWidth().testTag("settings-prompt-order"),
          ) { Text(promptOrderLabel(preferences.promptOrder)) }
          ToggleRow(stringResource(R.string.settings_show_jamo), preferences.showsJamo) {
            onPreferencesChange(preferences.copy(showsJamo = it))
          }
          ToggleRow(stringResource(R.string.settings_show_composition), preferences.showsComposition) {
            onPreferencesChange(preferences.copy(showsComposition = it))
          }
          ToggleRow(stringResource(R.string.settings_show_piyo), preferences.showsMascot) {
            onPreferencesChange(preferences.copy(showsMascot = it))
          }
          ToggleRow(stringResource(R.string.settings_auto_pronounce), preferences.autoPronouncesPractice) {
            onPreferencesChange(preferences.copy(autoPronouncesPractice = it))
          }
          ToggleRow(stringResource(R.string.settings_choseong_meaning), preferences.choseongShowsMeaning) {
            onPreferencesChange(preferences.copy(choseongShowsMeaning = it))
          }
        }
      }
      item {
        SettingsCard(stringResource(R.string.settings_reminder)) {
          ToggleRow(
            stringResource(R.string.settings_reminder_enabled),
            reminderEnabled,
            onChecked = onReminderEnabled,
          )
          TextButton(onClick = { showTimePicker = true }, modifier = Modifier.testTag("settings-reminder-time")) {
            Text(stringResource(R.string.settings_reminder_time, reminderHour, reminderMinute))
          }
        }
      }
      item {
        SettingsCard(stringResource(R.string.settings_data_privacy)) {
          ToggleRow(
            stringResource(R.string.settings_anonymous_analytics),
            preferences.anonymousAnalyticsEnabled,
          ) {
            onPreferencesChange(
              preferences.copy(
                anonymousAnalyticsEnabled = it,
                privacyNoticeVersion = PrivacyNoticePolicy.currentVersion,
              ),
            )
          }
          Text(
            stringResource(R.string.settings_anonymous_analytics_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          ToggleRow(
            stringResource(R.string.settings_crash_diagnostics),
            preferences.crashDiagnosticsEnabled,
          ) {
            onPreferencesChange(
              preferences.copy(
                crashDiagnosticsEnabled = it,
                privacyNoticeVersion = PrivacyNoticePolicy.currentVersion,
              ),
            )
          }
          Text(
            stringResource(R.string.settings_crash_diagnostics_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            stringResource(R.string.settings_analytics_privacy_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          TextButton(
            onClick = { showPrivacyChoices = true },
            modifier = Modifier.fillMaxWidth().testTag("settings-review-privacy-choices"),
          ) { Text(stringResource(R.string.settings_review_privacy_choices)) }
        }
      }
      item {
        SettingsCard(stringResource(R.string.settings_about)) {
          ExternalButton(stringResource(R.string.settings_privacy), "settings-privacy", onOpenPrivacy)
          ExternalButton(stringResource(R.string.settings_feedback), "settings-feedback", onOpenFeedback)
          ExternalButton(stringResource(R.string.settings_support), "settings-support", onOpenSupport)
        }
      }
      item {
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().testTag("settings-done")) {
          Text(stringResource(R.string.settings_done))
        }
      }
    }
  }

  if (showTimePicker) {
    val state = rememberTimePickerState(initialHour = reminderHour, initialMinute = reminderMinute, is24Hour = true)
    AlertDialog(
      onDismissRequest = { showTimePicker = false },
      title = { Text(stringResource(R.string.settings_reminder)) },
      text = { TimeInput(state) },
      confirmButton = {
        TextButton(onClick = {
          onReminderTime(state.hour, state.minute)
          showTimePicker = false
        }) { Text(stringResource(R.string.settings_save)) }
      },
      dismissButton = {
        TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.settings_cancel)) }
      },
    )
  }

  if (showPrivacyChoices) {
    PrivacyConsentDialog(
      initialAnalyticsEnabled = preferences.anonymousAnalyticsEnabled,
      initialDiagnosticsEnabled = preferences.crashDiagnosticsEnabled,
      onOpenPrivacy = onOpenPrivacy,
      onSave = { analytics, diagnostics ->
        onPreferencesChange(
          preferences.copy(
            anonymousAnalyticsEnabled = analytics,
            crashDiagnosticsEnabled = diagnostics,
            privacyNoticeVersion = PrivacyNoticePolicy.currentVersion,
          ),
        )
        showPrivacyChoices = false
      },
      onContinueWithoutSharing = {
        onPreferencesChange(
          preferences.copy(
            anonymousAnalyticsEnabled = false,
            crashDiagnosticsEnabled = false,
            privacyNoticeVersion = PrivacyNoticePolicy.currentVersion,
          ),
        )
        showPrivacyChoices = false
      },
    )
  }
}

@Composable
fun PrivacyConsentDialog(
  initialAnalyticsEnabled: Boolean,
  initialDiagnosticsEnabled: Boolean,
  onOpenPrivacy: () -> Unit,
  onSave: (analyticsEnabled: Boolean, diagnosticsEnabled: Boolean) -> Unit,
  onContinueWithoutSharing: () -> Unit,
) {
  var analyticsEnabled by remember(initialAnalyticsEnabled) { mutableStateOf(initialAnalyticsEnabled) }
  var diagnosticsEnabled by remember(initialDiagnosticsEnabled) { mutableStateOf(initialDiagnosticsEnabled) }

  AlertDialog(
    onDismissRequest = {},
    modifier = Modifier.testTag("privacy-consent-dialog"),
    title = { Text(stringResource(R.string.privacy_consent_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.privacy_consent_introduction))
        Text(
          stringResource(R.string.privacy_consent_optional_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ToggleRow(
          stringResource(R.string.settings_anonymous_analytics),
          analyticsEnabled,
          tag = "privacy-consent-analytics",
        ) {
          analyticsEnabled = it
        }
        Text(
          stringResource(R.string.privacy_consent_analytics_detail),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ToggleRow(
          stringResource(R.string.settings_crash_diagnostics),
          diagnosticsEnabled,
          tag = "privacy-consent-diagnostics",
        ) {
          diagnosticsEnabled = it
        }
        Text(
          stringResource(R.string.privacy_consent_diagnostics_detail),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          stringResource(R.string.privacy_consent_excluded_data),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
          onClick = onOpenPrivacy,
          modifier = Modifier.testTag("privacy-consent-privacy-policy"),
        ) { Text(stringResource(R.string.settings_privacy)) }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onSave(analyticsEnabled, diagnosticsEnabled) },
        modifier = Modifier.testTag("privacy-consent-save"),
      ) { Text(stringResource(R.string.privacy_consent_save)) }
    },
    dismissButton = {
      TextButton(
        onClick = onContinueWithoutSharing,
        modifier = Modifier.testTag("privacy-consent-continue-without-sharing"),
      ) { Text(stringResource(R.string.privacy_consent_continue_without_sharing)) }
    },
  )
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
  Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp), tonalElevation = 2.dp) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
      content()
    }
  }
}

@Composable
private fun ToggleRow(
  title: String,
  checked: Boolean,
  tag: String? = null,
  onChecked: (Boolean) -> Unit,
) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(title, modifier = Modifier.weight(1f))
    Switch(
      checked = checked,
      onCheckedChange = onChecked,
      modifier = if (tag == null) Modifier else Modifier.testTag(tag),
    )
  }
}

@Composable
private fun <T> ChoiceRow(values: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
    values.forEach { value ->
      FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label(value)) })
    }
  }
}

@Composable
private fun ExternalButton(title: String, tag: String, onClick: () -> Unit) {
  TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag)) { Text("$title ↗") }
}

@Composable
private fun keySoundLabel(value: KeySoundStyle): String = when (value) {
  KeySoundStyle.DEFAULT -> stringResource(R.string.settings_sound_default)
  KeySoundStyle.MECHANICAL -> stringResource(R.string.settings_sound_mechanical)
  KeySoundStyle.SOFT -> stringResource(R.string.settings_sound_soft)
}

@Composable
private fun fontScaleLabel(value: FontScale): String = when (value) {
  FontScale.SMALL -> stringResource(R.string.settings_small)
  FontScale.STANDARD -> stringResource(R.string.settings_standard)
  FontScale.LARGE -> stringResource(R.string.settings_large)
}

@Composable
private fun languageLabel(value: AppLanguage): String = when (value) {
  AppLanguage.JAPANESE -> stringResource(R.string.settings_language_japanese)
  AppLanguage.ENGLISH -> stringResource(R.string.settings_language_english)
  AppLanguage.SPANISH -> stringResource(R.string.settings_language_spanish)
  AppLanguage.GERMAN -> stringResource(R.string.settings_language_german)
  AppLanguage.FRENCH -> stringResource(R.string.settings_language_french)
}

@Composable
private fun promptOrderLabel(value: PracticePromptOrder): String = stringResource(
  when (value) {
    PracticePromptOrder.TARGET_MEANING_READING -> R.string.settings_order_target_meaning_reading
    PracticePromptOrder.TARGET_READING_MEANING -> R.string.settings_order_target_reading_meaning
    PracticePromptOrder.MEANING_TARGET_READING -> R.string.settings_order_meaning_target_reading
    PracticePromptOrder.MEANING_READING_TARGET -> R.string.settings_order_meaning_reading_target
    PracticePromptOrder.READING_TARGET_MEANING -> R.string.settings_order_reading_target_meaning
    PracticePromptOrder.READING_MEANING_TARGET -> R.string.settings_order_reading_meaning_target
  },
)
