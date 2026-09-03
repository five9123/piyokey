import SwiftUI

struct OnboardingView: View {
  @Environment(\.hancoFontScale) private var fontScale
  @Environment(\.hancoAdaptiveMetrics) private var adaptiveMetrics
  @EnvironmentObject private var onboarding: OnboardingLibrary
  @EnvironmentObject private var companion: MascotCompanionLibrary
  @AppStorage(SoundPreferenceKeys.effectsEnabled) private var soundEffectsEnabled = true
  @AppStorage(SoundPreferenceKeys.typingPreset) private var typingSoundPreset =
    TypingSoundPreset.system.rawValue
  @AppStorage(KeyboardPreferenceKeys.inputModeDefault) private var inputModeDefault =
    SessionInputMode.builtIn.rawValue
  @AppStorage(KeyboardPreferenceKeys.showsPhysicalKeyboardGuide) private
    var showsPhysicalKeyboardGuide = false

  @StateObject private var lesson = PracticeSessionViewModel(target: "가")
  @State private var eggReactionRevision = 0
  @State private var didPlayEggKnock = false
  @State private var showsKoreanKeyboardGuide = false
  @State private var didCaptureFirstInput = false

  var body: some View {
    VStack(spacing: 0) {
      header
      Group {
        switch onboarding.snapshot.step {
        case .goal:
          goalStep
        case .level:
          levelStep
        case .keyboard:
          keyboardStep
        case .lesson:
          lessonStep
        }
      }
      .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    .background(
      LinearGradient(
        colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
      )
      .ignoresSafeArea()
    )
    .tint(AppPalette.accent)
    .sheet(isPresented: $showsKoreanKeyboardGuide) {
      KoreanKeyboardGuideView()
    }
    .onChange(of: lesson.feedbackRevision) { _ in
      playLessonFeedbackSound()
    }
    .onChange(of: lesson.isComplete) { isComplete in
      guard isComplete, !didCaptureFirstInput else { return }
      didCaptureFirstInput = true
      captureOnboardingStep("first_input")
    }
    .onAppear {
      guard !didPlayEggKnock else { return }
      didPlayEggKnock = true
      eggReactionRevision += 1
      if soundEffectsEnabled { HancoSoundEngine.shared.play(.eggKnock) }
    }
  }

  private var header: some View {
    VStack(spacing: 8) {
      HStack {
        Text(
          AppLocalization.format("onboarding.progress_format",
            onboarding.snapshot.step.position,
            OnboardingStep.allCases.count
          )
        )
        .font(.caption.weight(.bold))
        .foregroundStyle(AppPalette.secondary)

        Spacer()

        Button("onboarding.skip") {
          onboarding.complete(skipped: true)
        }
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(AppPalette.mutedInk)
        .accessibilityIdentifier("onboarding.skip")
      }

      ProgressView(
        value: Double(onboarding.snapshot.step.position),
        total: Double(OnboardingStep.allCases.count)
      )
      .tint(AppPalette.accent)
    }
    .padding(.horizontal, 20)
    .padding(.top, 12)
    .padding(.bottom, 10)
    .hancoCenteredContent(maxWidth: adaptiveMetrics.readableContentMaxWidth)
  }

  private var goalStep: some View {
    ScrollView {
      VStack(spacing: 20) {
        MascotView(
          stage: .egg,
          eggPattern: companion.eggPattern,
          reaction: .eggKnock,
          reactionRevision: eggReactionRevision,
          size: 82
        )
        .frame(width: 112, height: 150)
        .accessibilityIdentifier("onboarding.mascot.egg")

        Text("onboarding.mascot.egg_intro")
          .font(.caption.weight(.bold))
          .foregroundStyle(AppPalette.accent)

        VStack(spacing: 7) {
          Text("onboarding.goal.title")
            .font(.system(.title2, design: .rounded, weight: .bold))
            .foregroundStyle(AppPalette.ink)
          Text("onboarding.goal.subtitle")
            .font(.subheadline)
            .foregroundStyle(AppPalette.mutedInk)
            .multilineTextAlignment(.center)
        }

        trustCard

        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
          ForEach(OnboardingGoal.allCases) { goal in
            goalCard(goal)
          }
        }

        primaryButton(title: "onboarding.next", systemImage: "arrow.right") {
          onboarding.move(to: .level)
          captureOnboardingStep("goal")
        }
        .disabled(onboarding.selectedGoal == nil)
        .opacity(onboarding.selectedGoal == nil ? 0.45 : 1)
        .accessibilityIdentifier("onboarding.next")
      }
      .padding(.horizontal, 20)
      .padding(.bottom, 24)
      .hancoCenteredContent(maxWidth: adaptiveMetrics.readableContentMaxWidth)
    }
    .accessibilityIdentifier("onboarding.goal.screen")
  }

  private var trustCard: some View {
    VStack(alignment: .leading, spacing: 10) {
      Label("onboarding.trust.standard", systemImage: "checkmark.seal.fill")
      Label("onboarding.trust.local", systemImage: "lock.shield.fill")
    }
    .font(.caption.weight(.semibold))
    .foregroundStyle(AppPalette.ink)
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(15)
    .background(AppPalette.card.opacity(0.92), in: RoundedRectangle(cornerRadius: 18))
    .accessibilityIdentifier("onboarding.trust")
  }

  private var levelStep: some View {
    ScrollView {
      VStack(spacing: 20) {
        MascotView(stage: .egg, eggPattern: companion.eggPattern, size: 82)
          .frame(width: 112, height: 150)
        VStack(spacing: 7) {
          Text("onboarding.level.title")
            .font(.system(.title2, design: .rounded, weight: .bold))
            .foregroundStyle(AppPalette.ink)
          Text("onboarding.level.subtitle")
            .font(.subheadline)
            .foregroundStyle(AppPalette.mutedInk)
        }
        .multilineTextAlignment(.center)

        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
          ForEach(OnboardingLevel.allCases) { level in
            levelCard(level)
          }
        }

        primaryButton(title: "onboarding.next", systemImage: "arrow.right") {
          onboarding.move(to: .keyboard)
        }
        .disabled(onboarding.selectedLevel == nil)
        .opacity(onboarding.selectedLevel == nil ? 0.45 : 1)
        .accessibilityIdentifier("onboarding.next")
      }
      .padding(.horizontal, 20)
      .padding(.bottom, 24)
      .hancoCenteredContent(maxWidth: adaptiveMetrics.readableContentMaxWidth)
    }
    .accessibilityIdentifier("onboarding.level.screen")
  }

  private func levelCard(_ level: OnboardingLevel) -> some View {
    let selected = onboarding.selectedLevel == level
    return Button {
      onboarding.selectLevel(level)
    } label: {
      VStack(alignment: .leading, spacing: 8) {
        Text(verbatim: AppLocalization.string("onboarding.level.\(level.rawValue).example"))
          .font(.title2.weight(.bold))
          .foregroundStyle(AppPalette.accent)
        Text(LocalizedStringKey(level.titleKey))
          .font(.headline.weight(.bold))
        Text(LocalizedStringKey(level.detailKey))
          .font(.caption)
      }
      .fixedSize(horizontal: false, vertical: true)
      .foregroundStyle(AppPalette.ink)
      .frame(maxWidth: .infinity, minHeight: 105, alignment: .leading)
      .padding(15)
      .background(
        selected ? AppPalette.accentSoft : AppPalette.card,
        in: RoundedRectangle(cornerRadius: 20, style: .continuous)
      )
      .overlay {
        RoundedRectangle(cornerRadius: 20, style: .continuous)
          .strokeBorder(selected ? AppPalette.accent : AppPalette.keyShadow, lineWidth: 1.5)
      }
    }
    .buttonStyle(.plain)
    .accessibilityAddTraits(selected ? .isSelected : [])
    .accessibilityIdentifier("onboarding.level.\(level.rawValue)")
  }

  private func goalCard(_ goal: OnboardingGoal) -> some View {
    let selected = onboarding.selectedGoal == goal
    return Button {
      onboarding.select(goal)
      companion.eggPattern = MascotEggPattern.forGoal(goal.rawValue)
    } label: {
      VStack(alignment: .leading, spacing: 8) {
        Image(systemName: goal.symbol)
          .font(.title3.weight(.bold))
        Text(LocalizedStringKey(goal.titleKey))
          .font(.headline.weight(.bold))
        Text(LocalizedStringKey(goal.detailKey))
          .font(.caption)
          .lineLimit(2)
      }
      .foregroundStyle(selected ? Color.white : AppPalette.ink)
      .frame(maxWidth: .infinity, minHeight: 105, alignment: .leading)
      .padding(15)
      .background(
        selected ? AppPalette.accent : AppPalette.card,
        in: RoundedRectangle(cornerRadius: 20, style: .continuous)
      )
      .overlay {
        RoundedRectangle(cornerRadius: 20, style: .continuous)
          .stroke(selected ? AppPalette.accent : AppPalette.keyShadow, lineWidth: 1.5)
      }
    }
    .buttonStyle(.plain)
    .accessibilityIdentifier("onboarding.goal.\(goal.rawValue)")
  }

  private var keyboardStep: some View {
    ScrollView {
      VStack(spacing: 22) {
        VStack(spacing: 8) {
          Text("onboarding.keyboard.title")
            .font(.system(.title2, design: .rounded, weight: .bold))
            .foregroundStyle(AppPalette.ink)
          Text("onboarding.keyboard.subtitle")
            .font(.subheadline)
            .foregroundStyle(AppPalette.mutedInk)
            .multilineTextAlignment(.center)
        }

        HStack(spacing: 12) {
          keyboardHandCard(
            title: "onboarding.keyboard.left_hand",
            detail: "onboarding.keyboard.consonants",
            jamo: "ㄱ ㄴ ㄷ ㄹ ㅁ",
            color: AppPalette.secondary
          )
          keyboardHandCard(
            title: "onboarding.keyboard.right_hand",
            detail: "onboarding.keyboard.vowels",
            jamo: "ㅏ ㅓ ㅗ ㅜ ㅣ",
            color: AppPalette.accent
          )
        }

        HStack(spacing: 10) {
          jamoTile("ㄱ")
          Image(systemName: "plus")
          jamoTile("ㅏ")
          Image(systemName: "arrow.right")
            .foregroundStyle(AppPalette.accent)
          jamoTile("가", emphasized: true)
        }
        .font(.headline.weight(.bold))
        .accessibilityLabel(Text("onboarding.keyboard.example_accessibility"))

        HStack(spacing: 12) {
          MascotView(
            stage: .egg,
            eggPattern: companion.eggPattern,
            size: 62,
            calm: true
          )
          .frame(width: 84, height: 116)
          .accessibilityIdentifier("onboarding.mascot.keyboard_egg")
          VStack(alignment: .leading, spacing: 4) {
            Text("onboarding.keyboard.tip")
              .font(.subheadline.weight(.semibold))
              .foregroundStyle(AppPalette.ink)
            Text("onboarding.mascot.hatch_hint")
              .font(.caption.weight(.bold))
              .foregroundStyle(AppPalette.accent)
          }
          .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(16)
        .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 22))

        VStack(spacing: 10) {
          primaryButton(
            title: "onboarding.keyboard.try_builtin",
            systemImage: "rectangle.grid.3x2.fill"
          ) {
            beginLesson(using: .builtIn)
          }
          .accessibilityIdentifier("onboarding.next")

          Button {
            beginLesson(using: .osIME)
          } label: {
            HStack(spacing: 8) {
              Text("onboarding.keyboard.try_device")
              Image(systemName: "keyboard")
            }
            .font(.headline.weight(.bold))
            .foregroundStyle(AppPalette.accent)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 18))
            .overlay {
              RoundedRectangle(cornerRadius: 18)
                .stroke(AppPalette.accent, lineWidth: 1.5)
            }
          }
          .buttonStyle(.plain)
          .accessibilityIdentifier("onboarding.input_device.hardware")
        }

        Text("onboarding.keyboard.device_note")
          .font(.caption2)
          .foregroundStyle(AppPalette.mutedInk)
          .multilineTextAlignment(.center)
      }
      .padding(.horizontal, 20)
      .padding(.vertical, 18)
      .hancoCenteredContent(maxWidth: adaptiveMetrics.readableContentMaxWidth)
    }
    .accessibilityIdentifier("onboarding.keyboard.screen")
  }

  private func keyboardHandCard(
    title: LocalizedStringKey,
    detail: LocalizedStringKey,
    jamo: String,
    color: Color
  ) -> some View {
    VStack(alignment: .leading, spacing: 8) {
      Text(title)
        .font(.headline.weight(.bold))
        .foregroundStyle(color)
      Text(detail)
        .font(.caption)
        .foregroundStyle(AppPalette.mutedInk)
      Text(verbatim: jamo)
        .font(.system(.body, design: .rounded, weight: .bold))
        .foregroundStyle(AppPalette.ink)
    }
    .frame(maxWidth: .infinity, minHeight: 112, alignment: .leading)
    .padding(16)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 22))
  }

  private func jamoTile(_ value: String, emphasized: Bool = false) -> some View {
    Text(verbatim: value)
      .font(.system(size: 24, weight: .bold, design: .rounded))
      .foregroundStyle(emphasized ? Color.white : AppPalette.ink)
      .frame(width: 50, height: 50)
      .background(
        emphasized ? AppPalette.accent : AppPalette.card,
        in: RoundedRectangle(cornerRadius: 15)
      )
  }

  @ViewBuilder
  private var lessonStep: some View {
    if lesson.isComplete {
      hatchMissionHandoff
    } else {
      VStack(spacing: 0) {
        ScrollView {
          VStack(spacing: 12) {
            HStack(spacing: 12) {
              MascotView(
                mood: lesson.feedback == .complete ? .cheer : .idle,
                stage: lesson.enteredText.isEmpty ? .egg : .cracking,
                eggPattern: companion.eggPattern,
                size: 56,
                calm: true
              )
              .frame(width: 76, height: 102)
              .accessibilityIdentifier("onboarding.mascot.typing_egg")
              VStack(alignment: .leading, spacing: 5) {
                Text("onboarding.lesson.title")
                  .font(.system(.title3, design: .rounded, weight: .bold))
                  .foregroundStyle(AppPalette.ink)
                Text("onboarding.lesson.subtitle")
                  .font(.caption)
                  .foregroundStyle(AppPalette.mutedInk)
              }
            }

            if !lesson.enteredText.isEmpty {
              Text("onboarding.mascot.hatch_soon")
                .font(.caption.weight(.bold))
                .foregroundStyle(AppPalette.accent)
            }

            HStack(spacing: 18) {
              VStack(spacing: 3) {
                Text("onboarding.lesson.target")
                  .font(.caption.weight(.bold))
                  .foregroundStyle(AppPalette.mutedInk)
                Text(verbatim: "가")
                  .font(.system(size: 40 * fontScale, weight: .bold, design: .rounded))
                  .foregroundStyle(AppPalette.ink)
                  .accessibilityIdentifier("onboarding.lesson.target.value")
              }
              Image(systemName: "arrow.right")
                .foregroundStyle(AppPalette.accent)
              VStack(spacing: 3) {
                Text("onboarding.lesson.input")
                  .font(.caption.weight(.bold))
                  .foregroundStyle(AppPalette.mutedInk)
                Text(verbatim: lesson.enteredText.isEmpty ? "…" : lesson.enteredText)
                  .font(.system(size: 40 * fontScale, weight: .bold, design: .rounded))
                  .foregroundStyle(AppPalette.accent)
                  .accessibilityValue(
                    Text(verbatim: lesson.enteredText.isEmpty ? "…" : lesson.enteredText)
                  )
                  .accessibilityIdentifier("onboarding.lesson.entered.value")
              }
            }
            .frame(maxWidth: .infinity)
            .padding(15)
            .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 22))

            Text("onboarding.lesson.guide")
              .font(.caption.weight(.semibold))
              .foregroundStyle(AppPalette.secondary)
          }
          .padding(.horizontal, 20)
          .padding(.bottom, 10)
          .hancoCenteredContent(maxWidth: adaptiveMetrics.sessionLaneMaxWidth)
        }

        .opacity(lesson.enteredText.isEmpty ? 0.48 : 1)
        .animation(.easeOut(duration: 0.2), value: lesson.enteredText.isEmpty)

        if lesson.enteredText.isEmpty {
          Label("onboarding.lesson.coachmark", systemImage: "hand.tap.fill")
            .font(.caption.weight(.bold))
            .foregroundStyle(.white)
            .padding(.horizontal, 14)
            .padding(.vertical, 9)
            .background(AppPalette.secondary, in: Capsule())
            .shadow(color: AppPalette.secondary.opacity(0.25), radius: 8, y: 4)
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .accessibilityIdentifier("onboarding.lesson.coachmark")
        }

        if usesDeviceKeyboard {
          VStack(spacing: 7) {
            OSIMEInputPanel(
              target: lesson.target,
              acceptedText: lesson.enteredText,
              resetRevision: 0,
              onInputStart: {
                HancoSoundEngine.shared.prepareForInputFeedback(currentCombo: 0)
              },
              onAcceptedSequence: lesson.synchronizeOSIME,
              onConfirmedMismatch: lesson.recordConfirmedOSIMEMistake,
              showsChrome: false,
              showsFocusRecovery: true
            )
            .padding(.horizontal, 10)

            if PhysicalKeyboardGuidePolicy.isVisibleOnCurrentDevice {
              PhysicalKeyboardGuideView(nextExpectedKey: lesson.nextExpectedKey)
                .padding(.horizontal, 8)
            }
          }
          .hancoCenteredContent(maxWidth: adaptiveMetrics.keyboardMaxWidth)
        } else {
          HangulKeyboardView(
            nextExpectedKey: lesson.nextExpectedKey,
            onKeyFeedback: playKeySound,
            onKey: lesson.input,
            onBackspace: lesson.backspace
          )
          .hancoCenteredContent(maxWidth: adaptiveMetrics.keyboardMaxWidth)
        }
      }
    }
  }

  private var hatchMissionHandoff: some View {
    ScrollView {
      VStack(spacing: 18) {
        MascotView(
          mood: .happy,
          stage: .cracking,
          eggPattern: companion.eggPattern,
          size: 70
        )
        .frame(width: 94, height: 126)
        .accessibilityIdentifier("onboarding.mascot.cracking")

        Label("onboarding.reward.first", systemImage: "gift.fill")
          .font(.caption.weight(.black))
          .foregroundStyle(AppPalette.secondary)
          .padding(.horizontal, 12)
          .padding(.vertical, 7)
          .background(AppPalette.secondary.opacity(0.12), in: Capsule())
          .accessibilityIdentifier("onboarding.reward.first")

        VStack(spacing: 6) {
          Text("onboarding.hatch.handoff_title")
            .font(.system(.title2, design: .rounded, weight: .bold))
            .foregroundStyle(AppPalette.ink)
          Text("onboarding.hatch.handoff_subtitle")
            .font(.subheadline)
            .foregroundStyle(AppPalette.mutedInk)
            .multilineTextAlignment(.center)
        }

        Label("onboarding.hatch.handoff_detail", systemImage: "lock.shield.fill")
          .font(.subheadline.weight(.semibold))
          .foregroundStyle(AppPalette.ink)
          .frame(maxWidth: .infinity, alignment: .leading)
          .padding(16)
          .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 20))

        primaryButton(title: "onboarding.hatch.begin", systemImage: "arrow.right") {
          onboarding.complete(skipped: false)
        }
        .accessibilityIdentifier("onboarding.finish")
      }
      .padding(.horizontal, 20)
      .padding(.bottom, 28)
      .hancoCenteredContent(maxWidth: adaptiveMetrics.readableContentMaxWidth)
    }
    .accessibilityIdentifier("onboarding.hatch.handoff.screen")
  }

  private func primaryButton(
    title: LocalizedStringKey,
    systemImage: String,
    action: @escaping () -> Void
  ) -> some View {
    Button(action: action) {
      HStack(spacing: 8) {
        Text(title)
        Image(systemName: systemImage)
      }
      .font(.headline.weight(.bold))
      .foregroundStyle(.white)
      .frame(maxWidth: .infinity)
      .padding(.vertical, 15)
      .background(AppPalette.accent, in: RoundedRectangle(cornerRadius: 18))
      .shadow(color: AppPalette.accent.opacity(0.22), radius: 9, y: 5)
    }
  }

  private var usesDeviceKeyboard: Bool {
    SessionInputMode(rawValue: inputModeDefault) == .osIME && showsPhysicalKeyboardGuide
  }

  private func beginLesson(using mode: SessionInputMode) {
    if mode == .osIME, !KoreanKeyboardAvailability.isAvailable {
      showsKoreanKeyboardGuide = true
      return
    }
    inputModeDefault = mode.rawValue
    showsPhysicalKeyboardGuide = mode == .osIME
    onboarding.move(to: .lesson)
    captureOnboardingStep("keyboard")
  }

  private func playKeySound(_ role: TypingSoundKeyRole) {
    guard soundEffectsEnabled else { return }
    HancoTypingSoundFeedback.play(
      TypingSoundPreset.resolved(from: typingSoundPreset),
      role: role
    )
  }

  private func playLessonFeedbackSound() {
    guard soundEffectsEnabled else { return }
    switch lesson.feedback {
    case .complete:
      HancoSoundEngine.shared.play(.completion(combo: 0))
    case .incorrect:
      HancoSoundEngine.shared.play(.mistake)
    case .idle, .correct:
      break
    }
  }

  private func captureOnboardingStep(_ step: String) {
    TelemetryService.shared.capture(
      .onboardingStepCompleted,
      properties: [.onboardingStep: step]
    )
  }
}
