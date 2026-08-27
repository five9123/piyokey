import DeckKit
import SwiftUI

struct PracticeSetupView: View {
  @Environment(\.hancoAdaptiveMetrics) private var adaptiveMetrics
  @EnvironmentObject private var deckLibrary: DeckLibrary
  @EnvironmentObject private var onboarding: OnboardingLibrary

  private let targets: [String]?
  private let deckName: String?
  private let createsNavigationStack: Bool
  private let catalog: Catalog?

  @AppStorage(KeyboardPreferenceKeys.showsKeyGuide) private var showsKeyGuide = true
  @AppStorage(KeyboardPreferenceKeys.showsRomanHints) private var showsRomanHints = true
  @AppStorage(KeyboardPreferenceKeys.hapticsEnabled) private var hapticsEnabled = true
  @AppStorage(KeyboardPreferenceKeys.builtInLayoutDefault) private var builtInLayoutDefault =
    BuiltInKeyboardLayout.dubeolsik.rawValue
  @AppStorage(SoundPreferenceKeys.effectsEnabled) private var soundEffectsEnabled = true
  @AppStorage(SoundPreferenceKeys.typingPreset) private var typingSoundPreset =
    TypingSoundPreset.system.rawValue

  init(
    targets: [String]? = nil,
    deckName: String? = nil,
    createsNavigationStack: Bool = true,
    catalog: Catalog? = nil
  ) {
    self.targets = targets
    self.deckName = deckName
    self.createsNavigationStack = createsNavigationStack
    self.catalog = catalog
  }

  @ViewBuilder
  var body: some View {
    if createsNavigationStack {
      NavigationStack { setupContent }
    } else {
      setupContent
    }
  }

  private var setupContent: some View {
    ScrollView {
      VStack(spacing: 18) {
        heroCard
        homeRecommendations
        settingsCard
        startButton
        Text("practice.setup.session_note")
          .font(.caption)
          .foregroundStyle(AppPalette.mutedInk)
          .multilineTextAlignment(.center)
          .padding(.horizontal, 24)
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
    .navigationTitle(
      deckName ?? AppLocalization.string("practice.setup.navigation_title")
    )
    .navigationBarTitleDisplayMode(.inline)
    .accessibilityIdentifier("practice.setup.screen")
    .onChange(of: soundEffectsEnabled) { enabled in
      HancoSoundEngine.shared.setEnabled(enabled)
      if enabled {
        HancoTypingSoundFeedback.play(resolvedTypingSoundPreset)
      }
    }
    .onChange(of: typingSoundPreset) { _ in
      guard soundEffectsEnabled else { return }
      HancoTypingSoundFeedback.play(resolvedTypingSoundPreset)
    }
  }

  private var heroCard: some View {
    HStack(spacing: 12) {
      GrowingMascotView(interactive: true, size: 68)
        .frame(width: 96, height: 150)

      VStack(alignment: .leading, spacing: 10) {
        Group {
          if selectedBuiltInLayout == .korean10Key {
            Text("keyboard.layout.korean_10key")
          } else {
            Text("practice.setup.eyebrow")
          }
        }
        .font(.caption.weight(.bold))
        .foregroundStyle(AppPalette.secondary)

        HStack(spacing: 7) {
          jamoSticker("ㄱ", rotation: -5, size: 42)
          Image(systemName: "plus")
            .font(.caption.weight(.bold))
            .foregroundStyle(AppPalette.mutedInk)
          jamoSticker("ㅏ", rotation: 4, size: 42)
          Image(systemName: "arrow.right")
            .font(.caption.weight(.bold))
            .foregroundStyle(AppPalette.accent)
          jamoSticker("가", rotation: -2, size: 42)
        }
        .accessibilityHidden(true)

        Text("practice.setup.title")
          .font(.system(.title3, design: .rounded, weight: .bold))
          .foregroundStyle(AppPalette.ink)
          .multilineTextAlignment(.leading)

        Text("practice.setup.subtitle")
          .font(.caption)
          .foregroundStyle(AppPalette.mutedInk)
          .multilineTextAlignment(.leading)
      }
    }
    .frame(maxWidth: .infinity)
    .padding(22)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 14, y: 8)
  }

  private var settingsCard: some View {
    VStack(alignment: .leading, spacing: 4) {
      Label("practice.setup.settings_title", systemImage: "slider.horizontal.3")
        .font(.headline.weight(.bold))
        .foregroundStyle(AppPalette.ink)
        .padding(.bottom, 8)

      VStack(alignment: .leading, spacing: 7) {
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
        if selectedBuiltInLayout == .korean10Key {
          Text("keyboard.layout.korean_10key_help")
            .font(.caption2)
            .foregroundStyle(AppPalette.mutedInk)
        }
      }
      .padding(.vertical, 7)

      Divider().opacity(0.5)

      optionToggle(
        title: "practice.setup.key_guide",
        detail: "practice.setup.key_guide_detail",
        systemImage: "lightbulb.fill",
        isOn: $showsKeyGuide,
        identifier: "settings.key_guide"
      )

      Divider().opacity(0.5)

      optionToggle(
        title: "practice.setup.roman_hints",
        detail: "practice.setup.roman_hints_detail",
        systemImage: "character.book.closed.fill",
        isOn: $showsRomanHints,
        identifier: "settings.roman_hints"
      )

      Divider().opacity(0.5)

      optionToggle(
        title: "practice.setup.haptics",
        detail: "practice.setup.haptics_detail",
        systemImage: "hand.tap.fill",
        isOn: $hapticsEnabled,
        identifier: "settings.haptics"
      )

      Divider().opacity(0.5)

      optionToggle(
        title: "practice.setup.sound",
        detail: "practice.setup.sound_detail",
        systemImage: "speaker.wave.2.fill",
        isOn: $soundEffectsEnabled,
        identifier: "settings.sound"
      )

      VStack(alignment: .leading, spacing: 8) {
        Text("practice.setup.sound_preset")
          .font(.caption.weight(.semibold))
          .foregroundStyle(AppPalette.mutedInk)
        Picker("practice.setup.sound_preset", selection: $typingSoundPreset) {
          Text("practice.setup.sound_system")
            .tag(TypingSoundPreset.system.rawValue)
          Text("practice.setup.sound_mechanical")
            .tag(TypingSoundPreset.mechanical.rawValue)
          Text("practice.setup.sound_soft")
            .tag(TypingSoundPreset.soft.rawValue)
        }
        .pickerStyle(.segmented)
        .accessibilityIdentifier("settings.sound_preset")
        if resolvedTypingSoundPreset == .system {
          Text("practice.setup.sound_system_detail")
            .font(.caption2)
            .foregroundStyle(AppPalette.mutedInk)
        }
      }
      .padding(.vertical, 7)
      .disabled(!soundEffectsEnabled)
      .opacity(soundEffectsEnabled ? 1 : 0.45)

    }
    .padding(18)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
  }

  @ViewBuilder
  private var homeRecommendations: some View {
    let recommendations = DeckRecommendationEngine.homeRecommendations(
      catalog: catalog,
      downloadHistory: deckLibrary.downloadHistory,
      installedDeckIDs: Set(deckLibrary.installedDecks.keys),
      preferredTags: onboarding.preferredTags
    )
    if deckName == nil, !recommendations.isEmpty, let catalog {
      VStack(alignment: .leading, spacing: 10) {
        Label("recommendations.home.title", systemImage: "sparkles")
          .font(.headline.weight(.bold))
          .foregroundStyle(AppPalette.ink)
        Text(
          deckLibrary.downloadHistory.isEmpty
            ? "recommendations.home.cold_start_subtitle" : "recommendations.home.subtitle"
        )
        .font(.caption)
        .foregroundStyle(AppPalette.mutedInk)

        ScrollView(.horizontal, showsIndicators: false) {
          HStack(spacing: 12) {
            ForEach(recommendations, id: \.deckId) { deck in
              NavigationLink {
                DeckDetailView(deck: deck, catalogDecks: catalog.decks)
              } label: {
                DeckCardView(deck: deck)
                  .frame(width: 282)
              }
              .buttonStyle(.plain)
              .accessibilityIdentifier("home.recommendation.\(deck.deckId)")
            }
          }
          .padding(.bottom, 8)
        }
      }
      .frame(maxWidth: .infinity, alignment: .leading)
      .accessibilityIdentifier("home.recommendations")
    }
  }

  private var startButton: some View {
    NavigationLink {
      PracticeView(
        targets: targets,
        sessionTitle: deckName
      )
    } label: {
      HStack(spacing: 8) {
        Text("practice.setup.start")
        Image(systemName: "arrow.right.circle.fill")
      }
      .font(.headline.weight(.bold))
      .foregroundStyle(.white)
      .frame(maxWidth: .infinity)
      .padding(.vertical, 15)
      .background(AppPalette.accent, in: RoundedRectangle(cornerRadius: 18))
      .shadow(color: AppPalette.accent.opacity(0.24), radius: 10, y: 6)
    }
    .accessibilityIdentifier("practice.start")
  }

  private var selectedBuiltInLayout: BuiltInKeyboardLayout {
    BuiltInKeyboardLayout.resolved(from: builtInLayoutDefault)
  }

  private func optionToggle(
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

  private var resolvedTypingSoundPreset: TypingSoundPreset {
    TypingSoundPreset.resolved(from: typingSoundPreset)
  }

  private func jamoSticker(_ text: String, rotation: Double, size: CGFloat) -> some View {
    Text(verbatim: text)
      .font(.system(size: size * 0.52, weight: .bold, design: .rounded))
      .foregroundStyle(AppPalette.ink)
      .frame(width: size, height: size)
      .background(
        AppPalette.accentSoft.opacity(0.72),
        in: RoundedRectangle(cornerRadius: size * 0.3)
      )
      .rotationEffect(.degrees(rotation))
  }
}
