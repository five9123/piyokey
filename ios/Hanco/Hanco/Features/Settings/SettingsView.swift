import SwiftUI

struct SettingsView: View {
  @Environment(\.hancoAdaptiveMetrics) private var adaptiveMetrics
  @Environment(\.dismiss) private var dismiss
  @EnvironmentObject private var reminder: DailyReminderLibrary

  @AppStorage(SettingsPreferenceKeys.fontScale) private var fontScale =
    HancoFontScale.standard.rawValue
  @AppStorage(SettingsPreferenceKeys.theme) private var theme = HancoTheme.light.rawValue
  @AppStorage(SettingsPreferenceKeys.language) private var language =
    AppLanguage.preferred.rawValue
  @AppStorage(SettingsPreferenceKeys.practiceDisplayPreset) private var practiceDisplayPreset =
    PracticeDisplayPreset.learning.rawValue
  @AppStorage(SettingsPreferenceKeys.practiceShowsTarget) private var practiceShowsTarget = true
  @AppStorage(SettingsPreferenceKeys.practiceShowsMeaning) private var practiceShowsMeaning = true
  @AppStorage(SettingsPreferenceKeys.practiceShowsReading) private var practiceShowsReading = false
  @AppStorage(SettingsPreferenceKeys.practicePromptOrder) private var practicePromptOrder =
    PracticePromptOrder.targetMeaningReading.rawValue
  @AppStorage(SettingsPreferenceKeys.practiceShowsJamo) private var practiceShowsJamo = true
  @AppStorage(SettingsPreferenceKeys.practiceAutoSpeaks) private var practiceAutoSpeaks = false
  @AppStorage(SettingsPreferenceKeys.practiceShowsMascot) private var practiceShowsMascot = true
  @AppStorage(SettingsPreferenceKeys.practiceShowsComposition)
  private var practiceShowsComposition = true
  @AppStorage(SettingsPreferenceKeys.choseongShowsMeaning) private var choseongShowsMeaning = true
  @AppStorage(SettingsPreferenceKeys.anonymousAnalyticsEnabled)
  private var anonymousAnalyticsEnabled = false
  @AppStorage(SettingsPreferenceKeys.crashDiagnosticsEnabled)
  private var crashDiagnosticsEnabled = false
  @AppStorage(SettingsPreferenceKeys.privacyNoticeVersion)
  private var privacyNoticeVersion = 0

  @AppStorage(KeyboardPreferenceKeys.showsKeyGuide) private var showsKeyGuide = true
  @AppStorage(KeyboardPreferenceKeys.showsRomanHints) private var showsRomanHints = true
  @AppStorage(KeyboardPreferenceKeys.hapticsEnabled) private var hapticsEnabled = true
  @AppStorage(KeyboardPreferenceKeys.inputModeDefault) private var inputModeDefault =
    SessionInputMode.builtIn.rawValue
  @AppStorage(KeyboardPreferenceKeys.showsPhysicalKeyboardGuide) private
    var showsPhysicalKeyboardGuide = false
  @AppStorage(KeyboardPreferenceKeys.builtInLayoutDefault) private var builtInLayoutDefault =
    BuiltInKeyboardLayout.dubeolsik.rawValue
  @AppStorage(SoundPreferenceKeys.effectsEnabled) private var soundEffectsEnabled = true
  @AppStorage(SoundPreferenceKeys.typingPreset) private var typingSoundPreset =
    TypingSoundPreset.system.rawValue

  @State private var showsKoreanKeyboardGuide = false
  @State private var showsMascotCloset = false
  @State private var showsPrivacyChoices = false

  let createsNavigationStack: Bool
  let showsCloseButton: Bool

  init(createsNavigationStack: Bool = true, showsCloseButton: Bool = false) {
    self.createsNavigationStack = createsNavigationStack
    self.showsCloseButton = showsCloseButton
  }

  var body: some View {
    Group {
      if createsNavigationStack {
        NavigationStack {
          settingsContent
        }
      } else {
        settingsContent
      }
    }
    .id(language)
    .sheet(isPresented: $showsKoreanKeyboardGuide) {
      KoreanKeyboardGuideView()
    }
    .sheet(isPresented: $showsMascotCloset) {
      MascotClosetView()
    }
    .sheet(isPresented: $showsPrivacyChoices) {
      PrivacyConsentView(
        initialAnalyticsEnabled: anonymousAnalyticsEnabled,
        initialDiagnosticsEnabled: crashDiagnosticsEnabled,
        onSave: applyPrivacyChoices,
        onContinueWithoutSharing: {
          applyPrivacyChoices(analytics: false, diagnostics: false)
        }
      )
    }
    .onAppear {
      TelemetryService.shared.capture(.featureViewed, properties: [.feature: "settings"])
      TelemetryService.shared.setCrashContext(feature: "settings")
    }
    .onChange(of: soundEffectsEnabled) { enabled in
      HancoSoundEngine.shared.setEnabled(enabled)
      if enabled {
        HancoTypingSoundFeedback.play(resolvedTypingPreset)
      }
      captureSetting("sound", value: enabled ? "enabled" : "disabled")
    }
    .onChange(of: language) { rawValue in
      captureSetting("language", value: AppLanguage.resolved(from: rawValue).rawValue)
    }
    .onChange(of: theme) { rawValue in
      captureSetting("theme", value: HancoTheme.resolved(from: rawValue).rawValue)
    }
    .onChange(of: inputModeDefault) { rawValue in
      captureSetting(
        "input_mode",
        value: SessionInputMode(rawValue: rawValue)?.rawValue ?? "builtin"
      )
    }
    .onChange(of: typingSoundPreset) { _ in
      guard soundEffectsEnabled else { return }
      HancoTypingSoundFeedback.play(resolvedTypingPreset)
    }
    .onChange(of: practiceDisplayPreset) { rawValue in
      let preset = PracticeDisplayPreset.resolved(from: rawValue)
      applyPracticePreset(preset)
      captureSetting("practice_display", value: preset.rawValue)
    }
    .onChange(of: anonymousAnalyticsEnabled) { enabled in
      privacyNoticeVersion = PrivacyNoticePolicy.currentVersion
      TelemetryService.shared.updateConsent(
        productAnalytics: enabled,
        crashDiagnostics: crashDiagnosticsEnabled
      )
      if enabled {
        TelemetryService.shared.capture(
          .settingChanged,
          properties: [.setting: "analytics_consent", .valueBucket: "enabled"]
        )
      }
    }
    .onChange(of: crashDiagnosticsEnabled) { enabled in
      privacyNoticeVersion = PrivacyNoticePolicy.currentVersion
      TelemetryService.shared.updateConsent(
        productAnalytics: anonymousAnalyticsEnabled,
        crashDiagnostics: enabled
      )
      TelemetryService.shared.capture(
        .settingChanged,
        properties: [
          .setting: "diagnostics_consent",
          .valueBucket: enabled ? "enabled" : "disabled",
        ]
      )
    }
    .hancoUITestDynamicTypeOverride()
  }

  private func captureSetting(_ setting: String, value: String) {
    TelemetryService.shared.capture(
      .settingChanged,
      properties: [.setting: setting, .valueBucket: value]
    )
  }

  private var settingsContent: some View {
    ScrollView {
      VStack(spacing: 18) {
        soundSection
        displaySection
        practiceDisplaySection
        gameDisplaySection
        reminderSection
        keyboardSection
        mascotSection
        privacySection
        appInformationSection
      }
      .padding(.horizontal, 18)
      .padding(.vertical, 16)
      .hancoCenteredContent(maxWidth: adaptiveMetrics.formContentMaxWidth)
    }
    .background(
      LinearGradient(
        colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
      )
      .ignoresSafeArea()
    )
    .accessibilityIdentifier("settings.screen")
    .overlay(alignment: .topLeading) {
      #if DEBUG
        DynamicTypeDebugProbe()
      #endif
    }
    .navigationTitle(Text("settings.navigation_title"))
    .navigationBarTitleDisplayMode(.inline)
    .toolbar {
      if showsCloseButton {
        ToolbarItem(placement: .confirmationAction) {
          Button("settings.done") { dismiss() }
            .accessibilityIdentifier("settings.done")
        }
      }
    }
  }

  private var displaySection: some View {
    settingsCard(title: "settings.display", systemImage: "textformat.size") {
      settingPicker(
        title: "settings.font_size",
        selection: $fontScale,
        identifier: "settings.font_scale"
      ) {
        Text("settings.font_size.small").tag(HancoFontScale.small.rawValue)
        Text("settings.font_size.standard").tag(HancoFontScale.standard.rawValue)
        Text("settings.font_size.large").tag(HancoFontScale.large.rawValue)
      }

      Divider().opacity(0.5)

      fontPreview

      Divider().opacity(0.5)

      settingPicker(
        title: "settings.theme",
        selection: $theme,
        identifier: "settings.theme"
      ) {
        Text("settings.theme.light").tag(HancoTheme.light.rawValue)
        Text("settings.theme.dark").tag(HancoTheme.dark.rawValue)
      }

      Divider().opacity(0.5)

      settingPicker(
        title: "settings.language",
        selection: $language,
        identifier: "settings.language"
      ) {
        Text("settings.language.japanese").tag(AppLanguage.japanese.rawValue)
        Text("settings.language.english").tag(AppLanguage.english.rawValue)
        Text("settings.language.spanish").tag(AppLanguage.spanish.rawValue)
      }
    }
  }

  private var fontPreview: some View {
    HStack(spacing: 12) {
      Text(verbatim: "한")
        .font(
          .system(
            size: 30 * HancoFontScale.resolved(from: fontScale).multiplier,
            weight: .bold,
            design: .rounded
          )
        )
        .foregroundStyle(AppPalette.ink)
        .frame(width: 58, height: 52)
        .background(AppPalette.key, in: RoundedRectangle(cornerRadius: 14))
        .shadow(color: AppPalette.keyShadow, radius: 3, y: 2)
        .accessibilityIdentifier("settings.font_preview")
        .accessibilityValue(Text(verbatim: fontScale))
      VStack(alignment: .leading, spacing: 3) {
        Text("settings.font_preview")
          .font(.subheadline.weight(.bold))
          .foregroundStyle(AppPalette.ink)
        Text("settings.font_preview_detail")
          .font(.caption)
          .foregroundStyle(AppPalette.mutedInk)
      }
      Spacer()
    }
    .padding(.vertical, 7)
  }

  private var keyboardSection: some View {
    settingsCard(title: "settings.keyboard", systemImage: "keyboard") {
      VStack(alignment: .leading, spacing: 8) {
        Text("keyboard.layout.title")
          .font(.subheadline.weight(.semibold))
          .foregroundStyle(AppPalette.ink)
        Text("keyboard.layout.detail")
          .font(.caption)
          .foregroundStyle(AppPalette.mutedInk)
        Picker("keyboard.layout.title", selection: $builtInLayoutDefault) {
          Text("keyboard.layout.dubeolsik")
            .tag(BuiltInKeyboardLayout.dubeolsik.rawValue)
          Text("keyboard.layout.korean_10key")
            .tag(BuiltInKeyboardLayout.korean10Key.rawValue)
        }
        .pickerStyle(.menu)
        .accessibilityIdentifier("settings.builtin_keyboard_layout")
        if BuiltInKeyboardLayout.resolved(from: builtInLayoutDefault) == .korean10Key {
          Text("keyboard.layout.korean_10key_help")
            .font(.caption2)
            .foregroundStyle(AppPalette.mutedInk)
        }
      }
      .padding(.vertical, 7)

      Divider().opacity(0.5)

      settingToggle(
        title: "practice.setup.key_guide",
        detail: "practice.setup.key_guide_detail",
        systemImage: "lightbulb.fill",
        isOn: $showsKeyGuide,
        identifier: "settings.key_guide"
      )
      Divider().opacity(0.5)
      settingToggle(
        title: "practice.setup.roman_hints",
        detail: "practice.setup.roman_hints_detail",
        systemImage: "character.book.closed.fill",
        isOn: $showsRomanHints,
        identifier: "settings.roman_hints"
      )
      Divider().opacity(0.5)
      settingToggle(
        title: "practice.setup.haptics",
        detail: "practice.setup.haptics_detail",
        systemImage: "hand.tap.fill",
        isOn: $hapticsEnabled,
        identifier: "settings.haptics"
      )
      Divider().opacity(0.5)
      VStack(alignment: .leading, spacing: 8) {
        Text("practice.setup.input_mode")
          .font(.subheadline.weight(.semibold))
          .foregroundStyle(AppPalette.ink)
        Text("practice.setup.input_mode_detail")
          .font(.caption)
          .foregroundStyle(AppPalette.mutedInk)
        SessionInputModeControl(
          selection: defaultInputModeBinding,
          onUnavailableOSIME: { showsKoreanKeyboardGuide = true }
        )
      }
      .padding(.vertical, 7)
      Divider().opacity(0.5)
      settingToggle(
        title: "physical_keyboard.show_guide",
        detail: "physical_keyboard.show_guide_detail",
        systemImage: "keyboard.badge.ellipsis",
        isOn: $showsPhysicalKeyboardGuide,
        identifier: "settings.physical_keyboard_guide"
      )
    }
  }

  private var practiceDisplaySection: some View {
    settingsCard(title: "settings.practice_display", systemImage: "rectangle.3.group.fill") {
      settingPicker(
        title: "settings.practice_display_preset",
        selection: $practiceDisplayPreset,
        identifier: "settings.practice_display_preset"
      ) {
        Text("settings.practice_display.learning").tag(PracticeDisplayPreset.learning.rawValue)
        Text("settings.practice_display.focus").tag(PracticeDisplayPreset.focus.rawValue)
      }

      Divider().opacity(0.5)
      practiceOrderPicker
      Divider().opacity(0.5)
      settingToggle(
        title: "settings.practice_target",
        detail: "settings.practice_target_detail",
        systemImage: "textformat",
        isOn: $practiceShowsTarget,
        identifier: "settings.practice_target"
      )
      Divider().opacity(0.5)
      settingToggle(
        title: "settings.practice_meaning",
        detail: "settings.practice_meaning_detail",
        systemImage: "character.book.closed.fill",
        isOn: $practiceShowsMeaning,
        identifier: "settings.practice_meaning"
      )
      Divider().opacity(0.5)
      settingToggle(
        title: "settings.practice_reading",
        detail: "settings.practice_reading_detail",
        systemImage: "text.bubble.fill",
        isOn: $practiceShowsReading,
        identifier: "settings.practice_reading"
      )
      Divider().opacity(0.5)
      settingToggle(
        title: "settings.practice_jamo",
        detail: "settings.practice_jamo_detail",
        systemImage: "square.grid.3x1.below.line.grid.1x2",
        isOn: $practiceShowsJamo,
        identifier: "settings.practice_jamo"
      )
      Divider().opacity(0.5)
      settingToggle(
        title: "settings.practice_auto_speak",
        detail: "settings.practice_auto_speak_detail",
        systemImage: "speaker.wave.2.fill",
        isOn: $practiceAutoSpeaks,
        identifier: "settings.practice_auto_speak"
      )
      Divider().opacity(0.5)
      settingToggle(
        title: "settings.practice_mascot",
        detail: "settings.practice_mascot_detail",
        systemImage: "bird.fill",
        isOn: $practiceShowsMascot,
        identifier: "settings.practice_mascot"
      )
      Divider().opacity(0.5)
      settingToggle(
        title: "settings.practice_composition",
        detail: "settings.practice_composition_detail",
        systemImage: "character.cursor.ibeam",
        isOn: $practiceShowsComposition,
        identifier: "settings.practice_composition"
      )
    }
  }

  private var practiceOrderPicker: some View {
    VStack(alignment: .leading, spacing: 4) {
      Text("settings.practice_order")
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(AppPalette.ink)
      Text("settings.practice_order_detail")
        .font(.caption)
        .foregroundStyle(AppPalette.mutedInk)
      Picker("settings.practice_order", selection: $practicePromptOrder) {
        ForEach(PracticePromptOrder.allCases) { order in
          Text(verbatim: order.localizedLabel).tag(order.rawValue)
        }
      }
      .pickerStyle(.menu)
      .tint(AppPalette.accent)
      .accessibilityIdentifier("settings.practice_order")
    }
    .padding(.vertical, 7)
  }

  private var gameDisplaySection: some View {
    settingsCard(title: "settings.game_display", systemImage: "gamecontroller.fill") {
      settingToggle(
        title: "settings.choseong_meaning",
        detail: "settings.choseong_meaning_detail",
        systemImage: "character.book.closed.fill",
        isOn: $choseongShowsMeaning,
        identifier: "settings.choseong_meaning"
      )
    }
  }

  private var reminderSection: some View {
    settingsCard(title: "retention.reminder.title", systemImage: "bell.badge.fill") {
      Toggle(isOn: reminderEnabled) {
        Text("retention.reminder.detail")
          .font(.subheadline.weight(.semibold))
          .foregroundStyle(AppPalette.ink)
      }
      .tint(AppPalette.accent)
      .accessibilityIdentifier("retention.reminder.toggle")

      HStack(spacing: 12) {
        Picker("retention.reminder.hour", selection: reminderHour) {
          ForEach(0..<24, id: \.self) { hour in
            Text(verbatim: String(format: "%02d", hour)).tag(hour)
          }
        }
        .pickerStyle(.menu)
        .accessibilityIdentifier("retention.reminder.hour")

        Text(verbatim: ":")
          .font(.headline.monospacedDigit())

        Picker("retention.reminder.minute", selection: reminderMinute) {
          ForEach([0, 15, 30, 45], id: \.self) { minute in
            Text(verbatim: String(format: "%02d", minute)).tag(minute)
          }
        }
        .pickerStyle(.menu)
        .accessibilityIdentifier("retention.reminder.minute")

        Text("retention.reminder.local_time")
          .font(.caption.weight(.bold))
          .foregroundStyle(AppPalette.mutedInk)
        Spacer()
      }
      .disabled(reminder.status == .scheduling)

      if reminder.status == .denied {
        Text("retention.reminder.denied")
          .font(.caption)
          .foregroundStyle(AppPalette.error)
      } else if reminder.status == .failed {
        Text("retention.reminder.failed")
          .font(.caption)
          .foregroundStyle(AppPalette.error)
      }
    }
  }

  private var soundSection: some View {
    settingsCard(title: "settings.sound", systemImage: "speaker.wave.2.fill") {
      settingToggle(
        title: "practice.setup.sound",
        detail: "practice.setup.sound_detail",
        systemImage: "speaker.wave.2.fill",
        isOn: $soundEffectsEnabled,
        identifier: "settings.sound"
      )
      Divider().opacity(0.5)
      settingPicker(
        title: "practice.setup.sound_preset",
        selection: $typingSoundPreset,
        identifier: "settings.sound_preset"
      ) {
        Text("practice.setup.sound_system").tag(TypingSoundPreset.system.rawValue)
        Text("practice.setup.sound_mechanical").tag(TypingSoundPreset.mechanical.rawValue)
        Text("practice.setup.sound_soft").tag(TypingSoundPreset.soft.rawValue)
      }
      .disabled(!soundEffectsEnabled)
      .opacity(soundEffectsEnabled ? 1 : 0.45)
      if resolvedTypingPreset == .system {
        Text("practice.setup.sound_system_detail")
          .font(.caption)
          .foregroundStyle(AppPalette.mutedInk)
      }
    }
  }

  private var mascotSection: some View {
    settingsCard(title: "settings.mascot", systemImage: "bird.fill") {
      Button {
        showsMascotCloset = true
      } label: {
        HStack(spacing: 12) {
          Image(systemName: "hanger")
            .font(.system(size: 17, weight: .semibold))
            .foregroundStyle(AppPalette.accent)
            .frame(width: 32, height: 32)
            .background(AppPalette.accentSoft.opacity(0.5), in: RoundedRectangle(cornerRadius: 10))
          VStack(alignment: .leading, spacing: 2) {
            Text("closet.title")
              .font(.subheadline.weight(.semibold))
              .foregroundStyle(AppPalette.ink)
            Text("settings.mascot_detail")
              .font(.caption)
              .foregroundStyle(AppPalette.mutedInk)
          }
          Spacer()
          Image(systemName: "chevron.right")
            .foregroundStyle(AppPalette.mutedInk)
        }
        .padding(.vertical, 7)
      }
      .buttonStyle(.plain)
      .accessibilityIdentifier("settings.mascot")
    }
  }

  private var privacySection: some View {
    settingsCard(title: "settings.privacy", systemImage: "hand.raised.fill") {
      settingToggle(
        title: "settings.anonymous_analytics",
        detail: "settings.anonymous_analytics_detail",
        systemImage: "chart.bar.xaxis",
        isOn: $anonymousAnalyticsEnabled,
        identifier: "settings.anonymous_analytics"
      )
      Divider().opacity(0.5)
      settingToggle(
        title: "settings.crash_diagnostics",
        detail: "settings.crash_diagnostics_detail",
        systemImage: "stethoscope",
        isOn: $crashDiagnosticsEnabled,
        identifier: "settings.crash_diagnostics"
      )
      Text("settings.analytics_privacy_note")
        .font(.caption)
        .foregroundStyle(AppPalette.mutedInk)
        .padding(.top, 4)
      Button {
        showsPrivacyChoices = true
      } label: {
        HStack(spacing: 10) {
          Image(systemName: "checklist")
          Text("settings.review_privacy_choices")
          Spacer()
          Image(systemName: "chevron.right")
            .font(.caption.weight(.bold))
        }
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(AppPalette.accent)
        .frame(minHeight: 44)
        .contentShape(Rectangle())
      }
      .buttonStyle(.plain)
      .accessibilityIdentifier("settings.review_privacy_choices")
    }
  }

  private var appInformationSection: some View {
    settingsCard(title: "settings.app_information", systemImage: "info.circle.fill") {
      legalLink(
        title: "settings.privacy_policy",
        systemImage: "hand.raised.fill",
        destination: AppReleaseLinks.privacyPolicy,
        identifier: "settings.privacy_policy"
      )
      Divider().opacity(0.5)
      contentFeedbackLink(
        title: "content_feedback.settings.title",
        systemImage: "bubble.left.and.bubble.right.fill",
        context: .general(source: .settings),
        identifier: "settings.content_feedback"
      )
      Divider().opacity(0.5)
      legalLink(
        title: "settings.support",
        systemImage: "questionmark.bubble.fill",
        destination: AppReleaseLinks.support,
        identifier: "settings.support"
      )
    }
  }

  private func legalLink(
    title: LocalizedStringKey,
    systemImage: String,
    destination: URL,
    identifier: String
  ) -> some View {
    Link(destination: destination) {
      externalLinkLabel(title: title, systemImage: systemImage)
    }
    .buttonStyle(.plain)
    .accessibilityIdentifier(identifier)
  }

  private func contentFeedbackLink(
    title: LocalizedStringKey,
    systemImage: String,
    context: ContentFeedbackContext,
    identifier: String
  ) -> some View {
    ContentFeedbackLink(context: context) {
      externalLinkLabel(title: title, systemImage: systemImage)
    }
    .buttonStyle(.plain)
    .accessibilityIdentifier(identifier)
  }

  private func externalLinkLabel(
    title: LocalizedStringKey,
    systemImage: String
  ) -> some View {
    HStack(spacing: 12) {
      Image(systemName: systemImage)
        .font(.system(size: 17, weight: .semibold))
        .foregroundStyle(AppPalette.accent)
        .frame(width: 32, height: 32)
        .background(AppPalette.accentSoft.opacity(0.5), in: RoundedRectangle(cornerRadius: 10))
      Text(title)
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(AppPalette.ink)
      Spacer()
      Image(systemName: "arrow.up.right")
        .font(.caption.weight(.bold))
        .foregroundStyle(AppPalette.mutedInk)
    }
    .padding(.vertical, 7)
    .contentShape(Rectangle())
  }

  private func settingsCard<Content: View>(
    title: LocalizedStringKey,
    systemImage: String,
    @ViewBuilder content: () -> Content
  ) -> some View {
    VStack(alignment: .leading, spacing: 5) {
      Label(title, systemImage: systemImage)
        .font(.headline.weight(.heavy))
        .foregroundStyle(AppPalette.ink)
        .padding(.bottom, 7)
      content()
    }
    .padding(18)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
  }

  private func settingToggle(
    title: LocalizedStringKey,
    detail: LocalizedStringKey,
    systemImage: String,
    isOn: Binding<Bool>,
    identifier: String
  ) -> some View {
    Toggle(isOn: isOn) {
      HStack(spacing: 12) {
        Image(systemName: systemImage)
          .font(.system(size: 17, weight: .semibold))
          .foregroundStyle(AppPalette.accent)
          .frame(width: 32, height: 32)
          .background(AppPalette.accentSoft.opacity(0.5), in: RoundedRectangle(cornerRadius: 10))
        VStack(alignment: .leading, spacing: 2) {
          Text(title)
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(AppPalette.ink)
          Text(detail)
            .font(.caption)
            .foregroundStyle(AppPalette.mutedInk)
        }
      }
    }
    .tint(AppPalette.accent)
    .padding(.vertical, 7)
    .accessibilityIdentifier(identifier)
  }

  private func settingPicker<Content: View>(
    title: LocalizedStringKey,
    selection: Binding<String>,
    identifier: String,
    @ViewBuilder content: () -> Content
  ) -> some View {
    VStack(alignment: .leading, spacing: 8) {
      Text(title)
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(AppPalette.ink)
      Picker(title, selection: selection, content: content)
        .pickerStyle(.segmented)
        .accessibilityIdentifier(identifier)
    }
    .padding(.vertical, 7)
  }

  private var defaultInputModeBinding: Binding<SessionInputMode> {
    Binding(
      get: { SessionInputMode(rawValue: inputModeDefault) ?? .builtIn },
      set: { mode in
        if mode == .osIME, !KoreanKeyboardAvailability.isAvailable {
          showsKoreanKeyboardGuide = true
        } else {
          inputModeDefault = mode.rawValue
        }
      }
    )
  }

  private var reminderEnabled: Binding<Bool> {
    Binding(
      get: { reminder.preference.isEnabled },
      set: reminder.setEnabled
    )
  }

  private var reminderHour: Binding<Int> {
    Binding(
      get: { reminder.preference.hour },
      set: { reminder.setTime(hour: $0, minute: reminder.preference.minute) }
    )
  }

  private var reminderMinute: Binding<Int> {
    Binding(
      get: { reminder.preference.minute },
      set: { reminder.setTime(hour: reminder.preference.hour, minute: $0) }
    )
  }

  private var resolvedTypingPreset: TypingSoundPreset {
    TypingSoundPreset.resolved(from: typingSoundPreset)
  }

  private func applyPracticePreset(_ preset: PracticeDisplayPreset) {
    switch preset {
    case .learning:
      practiceShowsTarget = true
      practiceShowsMeaning = true
      practiceShowsReading = false
      practiceShowsJamo = true
      practiceShowsMascot = true
      practiceShowsComposition = true
    case .focus:
      practiceShowsTarget = true
      practiceShowsMeaning = false
      practiceShowsReading = false
      practiceShowsJamo = false
      practiceShowsMascot = false
      practiceShowsComposition = true
    }
  }

  private func applyPrivacyChoices(analytics: Bool, diagnostics: Bool) {
    anonymousAnalyticsEnabled = analytics
    crashDiagnosticsEnabled = diagnostics
    privacyNoticeVersion = PrivacyNoticePolicy.currentVersion
    TelemetryService.shared.updateConsent(
      productAnalytics: analytics,
      crashDiagnostics: diagnostics
    )
  }
}

struct PrivacyConsentView: View {
  @Environment(\.dismiss) private var dismiss
  @State private var analyticsEnabled: Bool
  @State private var diagnosticsEnabled: Bool

  let onSave: (_ analytics: Bool, _ diagnostics: Bool) -> Void
  let onContinueWithoutSharing: () -> Void

  init(
    initialAnalyticsEnabled: Bool,
    initialDiagnosticsEnabled: Bool,
    onSave: @escaping (_ analytics: Bool, _ diagnostics: Bool) -> Void,
    onContinueWithoutSharing: @escaping () -> Void
  ) {
    _analyticsEnabled = State(initialValue: initialAnalyticsEnabled)
    _diagnosticsEnabled = State(initialValue: initialDiagnosticsEnabled)
    self.onSave = onSave
    self.onContinueWithoutSharing = onContinueWithoutSharing
  }

  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 18) {
          privacyHeader
          consentOption(
            title: "settings.anonymous_analytics",
            detail: "privacy_consent.analytics_detail",
            systemImage: "chart.bar.xaxis",
            isOn: $analyticsEnabled,
            identifier: "privacy_consent.analytics"
          )
          consentOption(
            title: "settings.crash_diagnostics",
            detail: "privacy_consent.diagnostics_detail",
            systemImage: "stethoscope",
            isOn: $diagnosticsEnabled,
            identifier: "privacy_consent.diagnostics"
          )

          Text("privacy_consent.excluded_data")
            .font(.footnote)
            .foregroundStyle(AppPalette.mutedInk)
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(AppPalette.accentSoft.opacity(0.35), in: RoundedRectangle(cornerRadius: 16))

          Link(destination: AppReleaseLinks.privacyPolicy) {
            Label("settings.privacy_policy", systemImage: "arrow.up.right")
              .font(.subheadline.weight(.semibold))
              .frame(minHeight: 44)
          }
          .accessibilityIdentifier("privacy_consent.privacy_policy")

          VStack(spacing: 10) {
            Button {
              onSave(analyticsEnabled, diagnosticsEnabled)
              dismiss()
            } label: {
              Text("privacy_consent.save")
                .font(.headline.weight(.bold))
                .frame(maxWidth: .infinity, minHeight: 50)
            }
            .buttonStyle(.borderedProminent)
            .tint(AppPalette.accent)
            .accessibilityIdentifier("privacy_consent.save")

            Button {
              analyticsEnabled = false
              diagnosticsEnabled = false
              onContinueWithoutSharing()
              dismiss()
            } label: {
              Text("privacy_consent.continue_without_sharing")
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.plain)
            .foregroundStyle(AppPalette.mutedInk)
            .accessibilityIdentifier("privacy_consent.continue_without_sharing")
          }
        }
        .padding(20)
      }
      .background(
        LinearGradient(
          colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
          startPoint: .topLeading,
          endPoint: .bottomTrailing
        )
        .ignoresSafeArea()
      )
      .navigationTitle(Text("privacy_consent.navigation_title"))
      .navigationBarTitleDisplayMode(.inline)
    }
    .interactiveDismissDisabled()
    .accessibilityIdentifier("privacy_consent.screen")
  }

  private var privacyHeader: some View {
    VStack(alignment: .leading, spacing: 10) {
      Image(systemName: "hand.raised.fill")
        .font(.system(size: 34, weight: .bold))
        .foregroundStyle(AppPalette.accent)
        .accessibilityHidden(true)
      Text("privacy_consent.title")
        .font(.title2.weight(.heavy))
        .foregroundStyle(AppPalette.ink)
      Text("privacy_consent.introduction")
        .font(.body)
        .foregroundStyle(AppPalette.mutedInk)
      Text("privacy_consent.optional_note")
        .font(.footnote.weight(.semibold))
        .foregroundStyle(AppPalette.ink)
    }
  }

  private func consentOption(
    title: LocalizedStringKey,
    detail: LocalizedStringKey,
    systemImage: String,
    isOn: Binding<Bool>,
    identifier: String
  ) -> some View {
    Toggle(isOn: isOn) {
      HStack(alignment: .top, spacing: 12) {
        Image(systemName: systemImage)
          .font(.system(size: 18, weight: .semibold))
          .foregroundStyle(AppPalette.accent)
          .frame(width: 34, height: 34)
          .background(AppPalette.accentSoft.opacity(0.5), in: RoundedRectangle(cornerRadius: 10))
          .accessibilityHidden(true)
        VStack(alignment: .leading, spacing: 4) {
          Text(title)
            .font(.headline)
            .foregroundStyle(AppPalette.ink)
          Text(detail)
            .font(.footnote)
            .foregroundStyle(AppPalette.mutedInk)
        }
      }
    }
    .tint(AppPalette.accent)
    .padding(16)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    .accessibilityIdentifier(identifier)
  }
}

#if DEBUG
  private struct DynamicTypeDebugProbe: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
      Text(verbatim: " ")
        .font(.system(size: 1))
        .opacity(0.01)
        .accessibilityElement(children: .ignore)
        .accessibilityValue(
          Text(verbatim: dynamicTypeSize.isAccessibilitySize ? "accessibility" : "standard")
        )
        .accessibilityIdentifier("debug.dynamic_type")
    }
  }
#endif
