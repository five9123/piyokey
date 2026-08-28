import DeckKit
import Foundation
import SwiftUI

struct PracticeReviewSource {
  let item: DeckItem
  let sourceDeckId: String
}

struct PracticeView: View {
  @Environment(\.hancoAdaptiveMetrics) private var adaptiveMetrics
  @Environment(\.dismiss) private var dismiss
  @Environment(\.scenePhase) private var scenePhase
  @Environment(\.hancoFontScale) private var fontScale
  @EnvironmentObject private var deckLibrary: DeckLibrary
  @EnvironmentObject private var gameProgress: GameProgressLibrary
  @EnvironmentObject private var reviewDeck: ReviewDeckLibrary
  @EnvironmentObject private var companion: MascotCompanionLibrary
  @AppStorage(KeyboardPreferenceKeys.showsKeyGuide) private var showsKeyGuide = true
  @AppStorage(KeyboardPreferenceKeys.showsRomanHints) private var showsRomanHints = true
  @AppStorage(KeyboardPreferenceKeys.hapticsEnabled) private var hapticsEnabled = true
  @AppStorage(KeyboardPreferenceKeys.inputModeDefault) private var inputModeDefault =
    SessionInputMode.builtIn.rawValue
  @AppStorage(SoundPreferenceKeys.effectsEnabled) private var soundEffectsEnabled = true
  @AppStorage(SoundPreferenceKeys.typingPreset) private var typingSoundPreset =
    TypingSoundPreset.system.rawValue
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
  @StateObject private var viewModel: PracticeSessionViewModel
  @StateObject private var targetSpeechSynthesizer = TargetSpeechSynthesizer()
  #if DEBUG
    @StateObject private var latencyMonitor = InputLatencyMonitor()
  #endif
  @State private var shakeStep: CGFloat = 0
  @State private var errorFlashOpacity: Double = 0
  @State private var completionCardScale: CGFloat = 1
  @State private var feedbackAnimationTask: Task<Void, Never>?
  @State private var postCompletionTransitionTask: Task<Void, Never>?
  @State private var sessionReviewItems: [SessionReviewItem] = []
  @State private var didReportCurriculumCompletion = false
  @State private var completionPersistenceState: PracticeCompletionPersistenceState?
  @State private var completionPersistenceTask: Task<Void, Never>?
  @State private var completionPersistenceGeneration = 0
  @State private var didRecordCompanionOutcome = false
  @State private var didPersistLearningRecord = false
  @State private var inputMode: SessionInputMode = .builtIn
  @State private var didResolveInputMode = false
  @State private var builtInKeyboardLayout: BuiltInKeyboardLayout
  @State private var korean10KeyInterpreter = Korean10KeyInterpreter()
  @State private var inputResetRevision = 0
  @State private var isSessionSettingsPresented = false
  @State private var isOSIMEFocusSuspended = false
  @State private var showsOSIMEUnavailable = false
  @State private var showsResult = false
  @State private var exitsAfterResultDismiss = false
  @State private var dismissesAfterPersistenceFailure = false
  @State private var previousAcceptedInputCount = 0
  @State private var enteredBackground = false
  @State private var didCaptureAnalyticsStart = false
  @State private var didCaptureAnalyticsCompletion = false
  @State private var didCaptureAnalyticsAbandonment = false
  private let sessionTitle: String?
  private let reviewSources: [PracticeReviewSource]
  private let sourceTags: [String]
  private let mascotAppearance: MascotSessionAppearance
  private let catalogDecks: [CatalogDeck]
  private let curriculumStageID: String?
  private let onCheckpoint: ((PracticeSessionCheckpoint) -> Void)?
  private let onCheckpointFlush: (() async -> Void)?
  private let onCurriculumCompletion: ((Double, Double, Int) async -> Bool)?
  private let onPracticeCompletion: ((Double, Double) -> Void)?
  private let onSessionRestart: (() -> Void)?
  private let retryTitle: LocalizedStringKey
  private let retrySystemImage: String
  private let onResultFinished: (() -> Void)?
  private let onPersistenceFailureExit: (() -> Void)?
  private let chainsHatchMissions: Bool
  private let isFinalHatchMission: Bool
  private let allowsOSKeyboard: Bool
  private let analyticsSessionKind: String
  private let analyticsDeckSource: String

  init(
    targets: [String]? = nil,
    sessionTitle: String? = nil,
    reviewSources: [PracticeReviewSource] = [],
    sourceTags: [String] = [],
    catalogDecks: [CatalogDeck] = [],
    curriculumStageID: String? = nil,
    checkpoint: PracticeSessionCheckpoint? = nil,
    onCheckpoint: ((PracticeSessionCheckpoint) -> Void)? = nil,
    onCheckpointFlush: (() async -> Void)? = nil,
    onCurriculumCompletion: ((Double, Double, Int) async -> Bool)? = nil,
    onPracticeCompletion: ((Double, Double) -> Void)? = nil,
    onSessionRestart: (() -> Void)? = nil,
    retryTitle: LocalizedStringKey = "practice.result.retry",
    retrySystemImage: String = "arrow.counterclockwise",
    onResultFinished: (() -> Void)? = nil,
    onPersistenceFailureExit: (() -> Void)? = nil,
    chainsHatchMissions: Bool = false,
    isFinalHatchMission: Bool = false,
    allowsOSKeyboard: Bool = true,
    analyticsSessionKind: String = "free_practice",
    analyticsDeckSource: String = "unknown"
  ) {
    self.sessionTitle = sessionTitle
    self.sourceTags = sourceTags
    self.mascotAppearance = MascotSessionAppearance()
    self.catalogDecks = catalogDecks
    self.curriculumStageID = curriculumStageID
    self.onCheckpoint = onCheckpoint
    self.onCheckpointFlush = onCheckpointFlush
    self.onCurriculumCompletion = onCurriculumCompletion
    self.onPracticeCompletion = onPracticeCompletion
    self.onSessionRestart = onSessionRestart
    self.retryTitle = retryTitle
    self.retrySystemImage = retrySystemImage
    self.onResultFinished = onResultFinished
    self.onPersistenceFailureExit = onPersistenceFailureExit
    self.chainsHatchMissions = chainsHatchMissions
    self.isFinalHatchMission = isFinalHatchMission
    self.allowsOSKeyboard = allowsOSKeyboard
    self.analyticsSessionKind = analyticsSessionKind
    self.analyticsDeckSource = analyticsDeckSource
    let storedBuiltInLayout = BuiltInKeyboardLayout.resolved(
      from: UserDefaults.standard.string(
        forKey: KeyboardPreferenceKeys.builtInLayoutDefault
      ) ?? BuiltInKeyboardLayout.dubeolsik.rawValue
    )
    _builtInKeyboardLayout = State(
      initialValue: allowsOSKeyboard ? storedBuiltInLayout : .dubeolsik
    )
    let resolvedTargets =
      targets ?? [
        AppLocalization.string("practice.sample_target_1"),
        AppLocalization.string("practice.sample_target_2"),
        AppLocalization.string("practice.sample_target_3"),
      ]
    let resolvedReviewSources =
      reviewSources.isEmpty && targets == nil
      ? Self.builtInReviewSources(targets: resolvedTargets)
      : reviewSources
    self.reviewSources = resolvedReviewSources
    precondition(
      resolvedReviewSources.isEmpty || resolvedReviewSources.count == resolvedTargets.count,
      "Review sources must align with practice targets"
    )
    _viewModel = StateObject(
      wrappedValue: PracticeSessionViewModel(
        targets: resolvedTargets,
        checkpoint: checkpoint
      )
    )
  }

  var body: some View {
    VStack(spacing: 0) {
      ScrollView {
        VStack(spacing: 12) {
          targetCard
          if practiceShowsMascot || practiceShowsComposition {
            compositionCard
          } else if inputMode == .osIME, allowsOSKeyboard {
            osIMEInputPanel(showsFocusRecovery: true)
              .padding(.horizontal, 14)
          }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .hancoCenteredContent(maxWidth: adaptiveMetrics.sessionLaneMaxWidth)
      }
      .scrollDismissesKeyboard(.never)

      inputArea
        .hancoCenteredContent(maxWidth: adaptiveMetrics.keyboardMaxWidth)
    }
    .background(
      LinearGradient(
        colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
      )
      .ignoresSafeArea()
    )
    .overlay {
      if viewModel.isComplete {
        CompletionCelebrationView()
          .id(viewModel.itemCompletionRevision)
          .transition(.opacity)
          .allowsHitTesting(false)
      }
    }
    .navigationTitle("")
    .navigationBarTitleDisplayMode(.inline)
    .navigationBarBackButtonHidden(true)
    .toolbar(.hidden, for: .tabBar)
    .toolbar {
      ToolbarItem(placement: .topBarLeading) {
        Button(action: dismiss.callAsFunction) {
          Image(systemName: "xmark")
        }
        .accessibilityLabel(Text("practice.end"))
      }

      ToolbarItem(placement: .principal) {
        compactSessionProgress
      }

      ToolbarItem(placement: .topBarTrailing) {
        Button(action: presentSessionSettings) {
          Image(systemName: "gearshape.fill")
        }
        .accessibilityLabel(Text("practice.session_settings"))
        .accessibilityIdentifier("practice.session_settings")
      }
    }
    .overlay {
      if isSessionSettingsPresented {
        SessionSettingsOverlay(
          showsKeyGuide: $showsKeyGuide,
          showsRomanHints: $showsRomanHints,
          hapticsEnabled: $hapticsEnabled,
          inputMode: $inputMode,
          soundEffectsEnabled: $soundEffectsEnabled,
          typingSoundPreset: $typingSoundPreset,
          practiceShowsTarget: $practiceShowsTarget,
          practiceShowsMeaning: $practiceShowsMeaning,
          practiceShowsReading: $practiceShowsReading,
          practicePromptOrder: $practicePromptOrder,
          practiceShowsJamo: $practiceShowsJamo,
          practiceAutoSpeaks: $practiceAutoSpeaks,
          practiceShowsMascot: $practiceShowsMascot,
          practiceShowsComposition: $practiceShowsComposition,
          allowsOSKeyboard: allowsOSKeyboard,
          onUnavailableOSIME: { showsOSIMEUnavailable = true },
          onClose: dismissSessionSettings
        )
        .transition(.opacity.combined(with: .scale(scale: 0.97, anchor: .topTrailing)))
        .zIndex(20)
      }
    }
    .navigationDestination(isPresented: $showsResult) {
      PracticeResultView(
        sessionTitle: sessionTitle
          ?? AppLocalization.string("practice.title"),
        accuracyPercent: viewModel.accuracyPercent,
        mistakeCount: viewModel.mistakeCount,
        completedItemCount: viewModel.completedItemCount,
        earnedStarsOverride: curriculumStageID.map { _ in curriculumStars },
        reviewItems: sessionReviewItems,
        recommendations: resultRecommendations,
        catalogDecks: catalogDecks,
        showsRetry: !chainsHatchMissions,
        retryTitle: retryTitle,
        retrySystemImage: retrySystemImage,
        finishTitle: hatchResultFinishTitle,
        finishSystemImage: chainsHatchMissions ? "arrow.right.circle.fill" : "chevron.backward",
        finishAccessibilityIdentifier: chainsHatchMissions
          ? "onboarding.hatch.result.continue" : "result.done.bottom",
        finishIsPrimary: chainsHatchMissions,
        finishIsEnabled: hatchResultFinishIsEnabled,
        showsSecondaryActions: !chainsHatchMissions,
        showsToolbarFinish: !chainsHatchMissions,
        completionPersistenceState: chainsHatchMissions
          ? completionPersistenceState : nil,
        onRetry: resetSession,
        onPersistenceRetry: retryCurriculumCompletionPersistence,
        onPersistenceRecoveryExit: exitAfterPersistenceFailure,
        onFinish: finishFromResult
      )
    }
    .onChange(of: showsResult, perform: handleResultPresentationChange)
    .onChange(of: viewModel.feedbackRevision) { _ in
      playFeedbackSound()
      animateFeedback()
      if case .incorrect(let expected) = viewModel.feedback {
        companion.recordMistake(expected: expected)
      }
      if !viewModel.isComplete {
        persistCheckpoint()
      }
    }
    .onChange(of: viewModel.totalAcceptedInputCount) { count in
      let delta = max(0, count - previousAcceptedInputCount)
      if delta > 0 { companion.recordTypedJamo(delta) }
      previousAcceptedInputCount = count
    }
    .onChange(of: viewModel.itemCompletionRevision) { _ in
      persistReviewResolution()
      reviewDeck.flush()
      if viewModel.isLessonComplete {
        targetSpeechSynthesizer.stop()
        finishTrackedSessionIfNeeded()
      } else {
        persistCheckpoint()
      }
      schedulePostCompletionTransition(flushingCheckpoint: !viewModel.isLessonComplete)
    }
    .onChange(of: scenePhase) { phase in
      switch phase {
      case .active:
        viewModel.resumeTiming()
        let resumedFromBackground = enteredBackground
        if enteredBackground {
          enteredBackground = false
          viewModel.publishStretchReaction()
        }
        if resumedFromBackground {
          speakCurrentTargetIfNeeded()
        }
        if viewModel.isComplete, !exitsAfterResultDismiss,
          !dismissesAfterPersistenceFailure
        {
          schedulePostCompletionTransition(flushingCheckpoint: !viewModel.isLessonComplete)
        }
      case .inactive, .background:
        enteredBackground = true
        cancelPostCompletionTransition()
        targetSpeechSynthesizer.stop()
        viewModel.pauseTiming()
        if !viewModel.isLessonComplete {
          persistCheckpoint()
        }
      @unknown default:
        cancelPostCompletionTransition()
        targetSpeechSynthesizer.stop()
        viewModel.pauseTiming()
      }
    }
    .onAppear {
      resolveInitialInputModeIfNeeded()
      captureAnalyticsStartIfNeeded()
      previousAcceptedInputCount = viewModel.totalAcceptedInputCount
      guard !viewModel.isLessonComplete, !exitsAfterResultDismiss,
        !dismissesAfterPersistenceFailure
      else {
        targetSpeechSynthesizer.stop()
        viewModel.pauseTiming()
        return
      }
      viewModel.resumeTiming()
      speakCurrentTargetIfNeeded()
      if viewModel.isComplete {
        schedulePostCompletionTransition(flushingCheckpoint: !viewModel.isLessonComplete)
      }
    }
    .onDisappear {
      cancelPostCompletionTransition()
      targetSpeechSynthesizer.stop()
      viewModel.pauseTiming()
      captureAnalyticsAbandonmentIfNeeded()
      if !viewModel.isLessonComplete {
        persistCheckpoint()
      }
    }
    .overlay(alignment: .topLeading) {
      latencyProbe
    }
    .onChange(of: inputMode) { _ in
      showsOSIMEUnavailable = false
      korean10KeyInterpreter.reset()
      inputResetRevision += 1
      if isSessionSettingsPresented {
        dismissSessionSettings()
      }
    }
    .onChange(of: viewModel.currentTargetIndex) { _ in
      targetSpeechSynthesizer.stop()
      korean10KeyInterpreter.reset()
      inputResetRevision += 1
      speakCurrentTargetIfNeeded()
    }
    .onChange(of: practiceAutoSpeaks) { enabled in
      if enabled {
        speakCurrentTarget()
      } else {
        targetSpeechSynthesizer.stop()
      }
    }
    .sessionExitCovered(exitsAfterResultDismiss)
  }

  private var inputArea: some View {
    VStack(spacing: 0) {
      if showsOSIMEUnavailable {
        OSIMEUnavailableBanner()
          .padding(.horizontal, 12)
          .padding(.bottom, 6)
      }

      if inputMode != .osIME || !allowsOSKeyboard {
        if builtInKeyboardLayout == .korean10Key, allowsOSKeyboard {
          Korean10KeyKeyboardView(
            nextExpectedKey: korean10KeyInterpreter.nextKey(for: viewModel.nextExpectedKey),
            options: HangulKeyboardOptions(
              showsKeyGuide: showsKeyGuide,
              showsRomanHints: false,
              hapticsEnabled: hapticsEnabled
            ),
            onInputStart: recordInputStart,
            onKeyFeedback: playKeySound,
            onKey: inputKorean10Key,
            onBackspace: backspaceKorean10Key
          )
        } else {
          HangulKeyboardView(
            nextExpectedKey: viewModel.nextExpectedKey,
            options: HangulKeyboardOptions(
              showsKeyGuide: showsKeyGuide,
              showsRomanHints: showsRomanHints,
              hapticsEnabled: hapticsEnabled
            ),
            onInputStart: recordInputStart,
            onKeyFeedback: playKeySound,
            onKey: viewModel.input,
            onBackspace: viewModel.backspace
          )
        }
      }
    }
  }

  private func inputKorean10Key(_ key: Korean10KeyKey) {
    let interpretation = korean10KeyInterpreter.input(
      key,
      expecting: viewModel.nextExpectedKey
    )
    switch interpretation {
    case .pending:
      break
    case .separatorAccepted:
      break
    case .committed(let jamo):
      viewModel.input(jamo)
    case .incorrect:
      viewModel.input(key.displayText.first ?? "ㆍ")
    }
  }

  private func backspaceKorean10Key() {
    switch korean10KeyInterpreter.backspace() {
    case .pendingChanged:
      break
    case .forwardToHangulEngine:
      viewModel.backspace()
    }
  }

  @ViewBuilder
  private var latencyProbe: some View {
    ZStack {
      Text(verbatim: String(viewModel.mistakeCount))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("practice.mistakes"))
        .accessibilityValue(Text(verbatim: String(viewModel.mistakeCount)))
        .accessibilityIdentifier("practice.mistakes.value")

      #if DEBUG
        DisplayRefreshProbe(
          inputRevision: latencyMonitor.inputRevision,
          onNextFrame: latencyMonitor.finishPendingInputs
        )
        .allowsHitTesting(false)
        .accessibilityHidden(true)

        Text(verbatim: latencyValueText)
          .accessibilityElement(children: .ignore)
          .accessibilityLabel(Text("debug.input_latency"))
          .accessibilityValue(Text(verbatim: latencyAccessibilityValue))
          .accessibilityIdentifier("debug.input_latency.p95")
      #endif
    }
    .font(.system(size: 1))
    .frame(width: 1, height: 1)
    .clipped()
    .opacity(0.01)
    .allowsHitTesting(false)
  }

  private var compactSessionProgress: some View {
    HStack(spacing: 8) {
      ProgressView(value: overallSessionProgress, total: 1)
        .tint(AppPalette.accent)
        .frame(width: 128)

      Text(verbatim: "\(viewModel.currentTargetIndex + 1) / \(viewModel.targets.count)")
        .font(.caption.monospacedDigit().weight(.bold))
        .foregroundStyle(AppPalette.mutedInk)
    }
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(Text("practice.overall_progress"))
    .accessibilityValue(
      Text(verbatim: "\(viewModel.currentTargetIndex + 1) / \(viewModel.targets.count)")
    )
    .accessibilityIdentifier("practice.overall_progress")
  }

  private var targetCard: some View {
    VStack(alignment: .leading, spacing: 9) {
      HStack {
        Spacer()
        Button {
          speakCurrentTarget()
        } label: {
          Label("practice.speak_target", systemImage: "speaker.wave.2.fill")
            .font(.subheadline.weight(.bold))
            .foregroundStyle(AppPalette.secondary)
            .padding(.horizontal, 11)
            .padding(.vertical, 7)
            .background(AppPalette.accentSoft.opacity(0.48), in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityValue(Text(verbatim: viewModel.target))
        .accessibilityIdentifier("practice.speak_target")
      }

      ForEach(resolvedPromptOrder.fields) { field in
        promptField(field)
      }

      if practiceShowsJamo {
        JamoProgressTrack(
          sequence: viewModel.targetJamoSequence,
          completedCount: viewModel.completedJamoCount
        )
        .id(viewModel.currentTargetIndex)
        .overlay(alignment: .topLeading) {
          Text(verbatim: " ")
            .font(.system(size: 1))
            .frame(width: 1, height: 1)
            .opacity(0.01)
            .allowsHitTesting(false)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text("practice.jamo_progress"))
            .accessibilityValue(
              Text(
                verbatim: "\(completedJamoProgress) / \(viewModel.targetJamoSequence.count)"
              )
            )
            .accessibilityIdentifier("practice.jamo_progress.value")
        }
      }
    }
    .padding(14)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    .overlay {
      RoundedRectangle(cornerRadius: 24, style: .continuous)
        .fill(AppPalette.error.opacity(errorFlashOpacity))
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
    .shadow(color: AppPalette.keyShadow, radius: 14, y: 8)
    .modifier(ShakeEffect(animatableData: shakeStep))
  }

  private var completedJamoProgress: Int {
    min(viewModel.completedJamoCount, viewModel.targetJamoSequence.count)
  }

  @ViewBuilder
  private func promptField(_ field: PracticePromptField) -> some View {
    switch field {
    case .target:
      if practiceShowsTarget {
        TargetSyllableProgressView(
          target: viewModel.target,
          units: viewModel.targetSyllableProgress,
          fontScale: fontScale
        )
        .id(viewModel.currentTargetIndex)
      }
    case .meaning:
      if practiceShowsMeaning, let meaning = currentReviewSource?.item.appMeaning,
        !meaning.isEmpty
      {
        meaningPromptText(JapaneseMeaningDisplayText.format(meaning))
      }
    case .reading:
      if practiceShowsReading, let reading = currentReviewSource?.item.appReading,
        !reading.isEmpty
      {
        secondaryPromptText(
          reading,
          identifier: "practice.reading.value",
          colorOpacity: 0.68
        )
      }
    }
  }

  private func meaningPromptText(_ value: String) -> some View {
    Text(verbatim: value)
      .font(.system(size: max(14, 17 * fontScale), weight: .regular, design: .rounded))
      .foregroundStyle(AppPalette.mutedInk)
      .multilineTextAlignment(.center)
      .padding(.horizontal, 14)
      .padding(.vertical, 7)
      .background(AppPalette.backgroundBottom.opacity(0.78), in: Capsule())
      .frame(maxWidth: .infinity)
      .accessibilityIdentifier("practice.meaning.value")
  }

  private func secondaryPromptText(
    _ value: String,
    identifier: String,
    colorOpacity: Double = 1
  ) -> some View {
    Text(verbatim: value)
      .font(.system(size: max(14, 17 * fontScale), weight: .regular, design: .rounded))
      .foregroundStyle(AppPalette.mutedInk.opacity(colorOpacity))
      .multilineTextAlignment(.center)
      .frame(maxWidth: .infinity)
      .accessibilityIdentifier(identifier)
  }

  private var compositionCard: some View {
    VStack(spacing: 8) {
      HStack(spacing: 10) {
        if practiceShowsMascot {
          GrowingMascotView(
            mood: mascotMood,
            reaction: mascotReaction,
            reactionRevision: viewModel.reactionRevision,
            automaticProp: mascotAppearance.automaticProp,
            gazeX: mascotGazeX,
            gazeY: mascotGazeY,
            intensity: min(CGFloat(viewModel.correctStreak) / 20, 1),
            speech: mascotSpeech,
            size: 52,
            calm: true
          )
          .frame(width: 70, height: 106)
        }

        if practiceShowsComposition {
          VStack(spacing: 4) {
            SyllableAssemblyPreview(
              text: compositionPreviewText,
              incomingJamo: viewModel.lastAcceptedKey,
              revision: viewModel.compositionRevision,
              shouldAnimateJoin: viewModel.shouldAnimateSyllableJoin
            )

            HStack(spacing: 6) {
              Text("practice.entered_text")
                .foregroundStyle(AppPalette.mutedInk)
              Text(verbatim: viewModel.enteredText.isEmpty ? "…" : viewModel.enteredText)
                .fontWeight(.bold)
                .foregroundStyle(AppPalette.ink)
            }
            .font(.subheadline)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text("practice.entered_text"))
            .accessibilityValue(
              Text(verbatim: viewModel.enteredText.isEmpty ? "…" : viewModel.enteredText)
            )
            .accessibilityIdentifier("practice.entered_text.value")
          }
        }
      }
      .frame(maxWidth: .infinity)
    }
    .frame(maxWidth: .infinity)
    .padding(.horizontal, 14)
    .padding(.vertical, 10)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    .scaleEffect(completionCardScale)
    .overlay {
      ZStack {
        Text(verbatim: " ")
          .font(.system(size: 1))
          .frame(width: 1, height: 1)
          .opacity(0.01)
          .allowsHitTesting(false)
          .accessibilityIdentifier("practice.composition_card")

        if inputMode == .osIME, allowsOSKeyboard {
          osIMEInputPanel(showsFocusRecovery: false)
        }
      }
    }
  }

  private var resolvedPromptOrder: PracticePromptOrder {
    PracticePromptOrder.resolved(from: practicePromptOrder)
  }

  private func osIMEInputPanel(showsFocusRecovery: Bool) -> some View {
    OSIMEInputPanel(
      target: viewModel.target,
      acceptedText: viewModel.enteredText,
      resetRevision: inputResetRevision,
      onAcceptedSequence: viewModel.synchronizeOSIME,
      onConfirmedMismatch: viewModel.recordConfirmedOSIMEMistake,
      showsChrome: false,
      showsFocusRecovery: showsFocusRecovery,
      isFocusSuspended: isOSIMEFocusSuspended
    )
  }

  private func presentSessionSettings() {
    guard !isSessionSettingsPresented else { return }
    isOSIMEFocusSuspended = true
    // Keep the UITextField resignation and settings presentation in separate update cycles.
    // SwiftUI Menu can otherwise re-enter AttributeGraph while iPadOS changes keyplanes.
    DispatchQueue.main.async {
      withAnimation(.easeOut(duration: 0.16)) {
        isSessionSettingsPresented = true
      }
    }
  }

  private func dismissSessionSettings() {
    guard isSessionSettingsPresented else {
      isOSIMEFocusSuspended = false
      return
    }
    withAnimation(.easeIn(duration: 0.12)) {
      isSessionSettingsPresented = false
    }
    DispatchQueue.main.async {
      isOSIMEFocusSuspended = false
    }
  }

  private var mascotMood: MascotMood {
    if viewModel.consecutiveMistakes >= 3 { return .dizzy }
    switch viewModel.feedback {
    case .idle: return .idle
    case .correct: return .happy
    case .incorrect: return .oops
    case .complete:
      return viewModel.isLessonComplete && viewModel.mistakeCount == 0 ? .proud : .cheer
    }
  }

  private var mascotReaction: MascotReaction {
    switch viewModel.reactionEvent {
    case .idle: .none
    case .correctJamo: .correctJamo
    case .syllableCompleted: .syllableCompleted
    case .wordCompleted: .wordCompleted
    case .comboMilestone(let count): .comboMilestone(count)
    case .mistake: .mistake
    case .perfectSession: .perfectSession
    case .dizzy: .mistake
    case .stretch: .stretch
    }
  }

  private var mascotSpeech: String? {
    guard viewModel.consecutiveMistakes >= 3,
      case .incorrect(let expected) = viewModel.feedback
    else { return nil }
    return String(
      format: AppLocalization.string("mascot.speech.missed_jamo"),
      String(expected)
    )
  }

  private var mascotGazeX: CGFloat {
    guard case .incorrect(let expected) = viewModel.feedback else { return 0 }
    if builtInKeyboardLayout == .korean10Key, allowsOSKeyboard {
      return Korean10KeyGeometry.normalizedHorizontalPosition(
        for: Korean10KeyInterpreter().nextKey(for: expected)
      )
    }
    return HangulKeyboardGeometry.normalizedHorizontalPosition(for: expected)
  }

  private var compositionPreviewText: String {
    guard inputMode == .builtIn, builtInKeyboardLayout == .korean10Key,
      allowsOSKeyboard, let pending = korean10KeyInterpreter.pendingDisplay
    else { return viewModel.composingPreview }
    return viewModel.composingPreview + pending
  }

  private var mascotGazeY: CGFloat {
    if case .incorrect = viewModel.feedback { return 0.82 }
    return 0
  }

  private var mascotCombo: Int {
    switch viewModel.reactionEvent {
    case .comboMilestone(let count): count
    default: viewModel.correctStreak
    }
  }

  #if DEBUG
    private var latencyValueText: String {
      guard let p95Milliseconds = latencyMonitor.p95Milliseconds else { return "—" }
      return String(format: AppLocalization.string("debug.input_latency.value"), p95Milliseconds)
    }

    private var latencyAccessibilityValue: String {
      guard let p95Milliseconds = latencyMonitor.p95Milliseconds else { return "—" }
      return String(format: "%.3f", p95Milliseconds)
    }

  #endif

  private func recordInputStart() {
    #if DEBUG
      latencyMonitor.beginInput()
    #endif
  }

  private var resultRecommendations: [CatalogDeck] {
    guard let sourceDeckID = reviewSources.first?.sourceDeckId else { return [] }
    return DeckRecommendationEngine.relatedRecommendations(
      sourceDeckID: sourceDeckID,
      sourceTags: sourceTags,
      catalogDecks: catalogDecks,
      installedDeckIDs: Set(deckLibrary.installedDecks.keys)
    )
  }

  private var currentReviewSource: PracticeReviewSource? {
    guard reviewSources.indices.contains(viewModel.currentTargetIndex) else { return nil }
    return reviewSources[viewModel.currentTargetIndex]
  }

  private var overallSessionProgress: Double {
    guard !viewModel.targets.isEmpty else { return 0 }
    let targetJamoCount = max(viewModel.targetJamoSequence.count, 1)
    let currentFraction = min(
      Double(viewModel.completedJamoCount) / Double(targetJamoCount),
      1
    )
    return min(
      (Double(viewModel.currentTargetIndex) + currentFraction)
        / Double(viewModel.targets.count),
      1
    )
  }

  private func persistReviewResolution() {
    guard let resolution = viewModel.lastCompletedItem,
      reviewSources.indices.contains(resolution.itemIndex)
    else { return }

    let source = reviewSources[resolution.itemIndex]
    if resolution.hadMistake {
      reviewDeck.recordMistake(item: source.item, sourceDeckId: source.sourceDeckId)
      collectSessionReviewItem(source: source, resolution: resolution)
    } else {
      if reviewDeck.recordPerfect(
        itemId: source.item.id,
        sourceDeckId: source.sourceDeckId
      ) == .graduated {
        companion.publish(.reviewGraduated)
      }
    }
  }

  private func collectSessionReviewItem(
    source: PracticeReviewSource,
    resolution: SessionItemResolution
  ) {
    let id = ReviewDeckItem.id(itemId: source.item.id, sourceDeckId: source.sourceDeckId)
    if let index = sessionReviewItems.firstIndex(where: { $0.id == id }) {
      sessionReviewItems[index].merge(resolution)
    } else {
      sessionReviewItems.append(
        SessionReviewItem(
          item: source.item,
          sourceDeckId: source.sourceDeckId,
          resolution: resolution
        )
      )
    }
  }

  private func resetSession() {
    cancelPostCompletionTransition()
    completionPersistenceTask?.cancel()
    completionPersistenceTask = nil
    completionPersistenceGeneration &+= 1
    completionPersistenceState = nil
    sessionReviewItems.removeAll(keepingCapacity: true)
    didReportCurriculumCompletion = false
    didRecordCompanionOutcome = false
    didPersistLearningRecord = false
    didCaptureAnalyticsStart = false
    didCaptureAnalyticsCompletion = false
    didCaptureAnalyticsAbandonment = false
    previousAcceptedInputCount = 0
    onSessionRestart?()
    korean10KeyInterpreter.reset()
    viewModel.reset()
    inputResetRevision += 1
    persistCheckpoint()
    captureAnalyticsStartIfNeeded()
  }

  private func finishFromResult() {
    guard showsResult, !exitsAfterResultDismiss else { return }
    if chainsHatchMissions {
      guard completionPersistenceState == .saved else { return }
    }
    cancelPostCompletionTransition()
    exitsAfterResultDismiss = true
    targetSpeechSynthesizer.stop()
    showsResult = false
  }

  private func handleResultPresentationChange(_ isPresented: Bool) {
    if isPresented {
      targetSpeechSynthesizer.stop()
      return
    }
    if dismissesAfterPersistenceFailure {
      Task { @MainActor in
        await Task.yield()
        guard dismissesAfterPersistenceFailure else { return }
        if let onPersistenceFailureExit {
          onPersistenceFailureExit()
        } else {
          dismiss()
        }
      }
      return
    }
    guard exitsAfterResultDismiss else { return }
    Task { @MainActor in
      await Task.yield()
      guard exitsAfterResultDismiss else { return }
      if !chainsHatchMissions {
        dismiss()
      }
      onResultFinished?()
    }
  }

  private var hatchResultFinishTitle: LocalizedStringKey {
    guard chainsHatchMissions else { return "result.back" }
    return isFinalHatchMission
      ? "onboarding.hatch.open_app"
      : "onboarding.hatch.next_mission"
  }

  private var hatchResultFinishIsEnabled: Bool {
    !chainsHatchMissions || completionPersistenceState == .saved
  }

  private func retryCurriculumCompletionPersistence() {
    guard completionPersistenceState == .failed else { return }
    beginCurriculumCompletionPersistence()
  }

  private func exitAfterPersistenceFailure() {
    guard showsResult, completionPersistenceState == .failed,
      !dismissesAfterPersistenceFailure
    else { return }
    cancelPostCompletionTransition()
    dismissesAfterPersistenceFailure = true
    targetSpeechSynthesizer.stop()
    showsResult = false
  }

  private func advanceSession(persistingCheckpoint: Bool = true) {
    cancelPostCompletionTransition()
    korean10KeyInterpreter.reset()
    viewModel.advance()
    if persistingCheckpoint {
      persistCheckpoint()
    }
  }

  private func schedulePostCompletionTransition(flushingCheckpoint: Bool = false) {
    cancelPostCompletionTransition()
    guard viewModel.isComplete, !showsResult, !exitsAfterResultDismiss,
      !dismissesAfterPersistenceFailure, scenePhase == .active
    else { return }
    let completedTargetIndex = viewModel.currentTargetIndex
    let completesSession = viewModel.isLessonComplete
    let checkpointWasPreparedForNextTarget =
      flushingCheckpoint && !completesSession && viewModel.canAdvance
    if checkpointWasPreparedForNextTarget {
      onCheckpoint?(viewModel.checkpointForNextTarget())
    }
    postCompletionTransitionTask = Task { @MainActor in
      if flushingCheckpoint {
        await onCheckpointFlush?()
      }
      try? await Task.sleep(nanoseconds: 650_000_000)
      guard !Task.isCancelled,
        scenePhase == .active,
        viewModel.currentTargetIndex == completedTargetIndex,
        viewModel.isComplete,
        !showsResult,
        !exitsAfterResultDismiss
      else { return }

      if completesSession {
        guard viewModel.isLessonComplete else { return }
        showsResult = true
      } else {
        guard viewModel.canAdvance else { return }
        advanceSession(persistingCheckpoint: !checkpointWasPreparedForNextTarget)
      }
    }
  }

  private func cancelPostCompletionTransition() {
    postCompletionTransitionTask?.cancel()
    postCompletionTransitionTask = nil
  }

  private func resolveInitialInputModeIfNeeded() {
    guard !didResolveInputMode else { return }
    didResolveInputMode = true
    guard allowsOSKeyboard,
      SessionInputMode(rawValue: inputModeDefault) == .osIME
    else {
      inputMode = .builtIn
      return
    }
    if KoreanKeyboardAvailability.isAvailable {
      inputMode = .osIME
    } else {
      inputMode = .builtIn
      showsOSIMEUnavailable = true
    }
  }

  private var curriculumStars: Int {
    CurriculumStarRating.stars(
      accuracy: viewModel.accuracyPercent,
      charactersPerMinute: viewModel.charactersPerMinute
    )
  }

  private func persistCheckpoint() {
    guard curriculumStageID != nil else { return }
    onCheckpoint?(viewModel.checkpoint())
  }

  private func finishTrackedSessionIfNeeded() {
    viewModel.pauseTiming()
    captureAnalyticsCompletionIfNeeded()
    persistLearningRecordIfNeeded()
    if !didRecordCompanionOutcome {
      didRecordCompanionOutcome = true
      _ = companion.recordLessonOutcome(cleared: viewModel.accuracyPercent >= 80)
    }
    guard !didReportCurriculumCompletion,
      curriculumStageID != nil || onPracticeCompletion != nil
    else { return }
    didReportCurriculumCompletion = true
    if curriculumStageID != nil {
      beginCurriculumCompletionPersistence()
    }
    onPracticeCompletion?(viewModel.accuracyPercent, viewModel.charactersPerMinute)
  }

  private var analyticsInputMode: String {
    inputMode == .osIME ? "os_ime" : builtInKeyboardLayout.gameRecordInputMode.rawValue
  }

  private func captureAnalyticsStartIfNeeded() {
    guard !didCaptureAnalyticsStart else { return }
    didCaptureAnalyticsStart = true
    TelemetryService.shared.capture(
      .sessionStarted,
      properties: [
        .sessionKind: analyticsSessionKind,
        .deckSource: analyticsDeckSource,
        .inputMode: analyticsInputMode,
      ]
    )
    TelemetryService.shared.setCrashContext(
      feature: "practice",
      sessionKind: analyticsSessionKind,
      inputMode: analyticsInputMode
    )
  }

  private func captureAnalyticsCompletionIfNeeded() {
    guard !didCaptureAnalyticsCompletion else { return }
    didCaptureAnalyticsCompletion = true
    TelemetryService.shared.capture(
      .sessionCompleted,
      properties: [
        .sessionKind: analyticsSessionKind,
        .result: "completed",
        .durationBucket: TelemetryService.shared.durationBucket(viewModel.activeDuration),
        .itemCountBucket: TelemetryService.shared.itemCountBucket(viewModel.completedItemCount),
        .deckSource: analyticsDeckSource,
        .inputMode: analyticsInputMode,
      ]
    )
    if analyticsSessionKind == "review" {
      TelemetryService.shared.capture(
        .reviewCompleted,
        properties: [
          .itemCountBucket: TelemetryService.shared.itemCountBucket(viewModel.completedItemCount),
          .result: "completed",
        ]
      )
    }
  }

  private func captureAnalyticsAbandonmentIfNeeded() {
    guard didCaptureAnalyticsStart, !didCaptureAnalyticsCompletion,
      !didCaptureAnalyticsAbandonment
    else { return }
    didCaptureAnalyticsAbandonment = true
    TelemetryService.shared.capture(
      .sessionAbandoned,
      properties: [
        .sessionKind: analyticsSessionKind,
        .reason: "user_closed",
        .durationBucket: TelemetryService.shared.durationBucket(viewModel.activeDuration),
        .deckSource: analyticsDeckSource,
        .inputMode: analyticsInputMode,
      ]
    )
  }

  private func beginCurriculumCompletionPersistence() {
    guard curriculumStageID != nil, let onCurriculumCompletion else { return }
    completionPersistenceTask?.cancel()
    completionPersistenceGeneration &+= 1
    let generation = completionPersistenceGeneration
    let accuracy = viewModel.accuracyPercent
    let charactersPerMinute = viewModel.charactersPerMinute
    let stars = curriculumStars
    completionPersistenceState = .saving
    completionPersistenceTask = Task { @MainActor in
      let saved = await onCurriculumCompletion(accuracy, charactersPerMinute, stars)
      guard !Task.isCancelled, generation == completionPersistenceGeneration else { return }
      completionPersistenceState = saved ? .saved : .failed
      completionPersistenceTask = nil
    }
  }

  private func persistLearningRecordIfNeeded() {
    guard !didPersistLearningRecord else { return }
    didPersistLearningRecord = true
    let sourceDeckIDs = Set(reviewSources.map(\.sourceDeckId))
    let deckID: String
    if sourceDeckIDs.count == 1, let sourceDeckID = sourceDeckIDs.first {
      deckID = sourceDeckID
    } else if sourceDeckIDs.count > 1 {
      deckID = "review_deck"
    } else if curriculumStageID != nil {
      deckID = "official_curriculum"
    } else {
      deckID = "free_practice"
    }
    let course = curriculumStageID.map { "curriculum:\($0)" } ?? "practice"
    _ = gameProgress.appendLesson(
      GameRecord(
        id: UUID(),
        mode: .lesson,
        deckId: deckID,
        course: course,
        score: viewModel.acceptedJamoCount,
        maxCombo: 0,
        accuracy: viewModel.accuracyPercent,
        charactersPerMinute: viewModel.charactersPerMinute,
        activeDuration: viewModel.activeDuration,
        completedItemCount: viewModel.completedItemCount,
        missedItemCount: sessionReviewItems.count,
        inputMode: inputMode,
        playedAt: RetentionClock.now()
      )
    )
  }

  private static func builtInReviewSources(targets: [String]) -> [PracticeReviewSource] {
    zip(
      targets,
      [
        (
          AppLocalization.string("practice.sample_target_1.reading"),
          AppLocalization.string("practice.sample_target_1.meaning")
        ),
        (
          AppLocalization.string("practice.sample_target_2.reading"),
          AppLocalization.string("practice.sample_target_2.meaning")
        ),
        (
          AppLocalization.string("practice.sample_target_3.reading"),
          AppLocalization.string("practice.sample_target_3.meaning")
        ),
      ]
    ).enumerated().map { index, value in
      let (target, metadata) = value
      return PracticeReviewSource(
        item: DeckItem(
          id: "built_in_\(index + 1)",
          ko: target,
          readingJa: metadata.0,
          meaningJa: metadata.1,
          audio: BundledPronunciationAudio.relativePath(for: target)
        ),
        sourceDeckId: "official_built_in_basics"
      )
    }
  }

  private func animateFeedback() {
    feedbackAnimationTask?.cancel()

    switch viewModel.feedback {
    case .incorrect:
      var noAnimation = Transaction()
      noAnimation.disablesAnimations = true
      withTransaction(noAnimation) {
        errorFlashOpacity = 0.2
      }
      withAnimation(.linear(duration: 0.25)) {
        shakeStep += 1
      }
      withAnimation(.easeOut(duration: 0.22)) {
        errorFlashOpacity = 0
      }

    case .complete:
      completionCardScale = 0.96
      withAnimation(.spring(response: 0.2, dampingFraction: 0.58)) {
        completionCardScale = 1.04
      }
      feedbackAnimationTask = Task { @MainActor in
        try? await Task.sleep(nanoseconds: 180_000_000)
        guard !Task.isCancelled else { return }
        withAnimation(.spring(response: 0.16, dampingFraction: 0.76)) {
          completionCardScale = 1
        }
      }

    case .correct:
      completionCardScale = 1

    case .idle:
      errorFlashOpacity = 0
      completionCardScale = 1
    }
  }

  private func playKeySound(_ role: TypingSoundKeyRole) {
    guard soundEffectsEnabled else { return }
    HancoTypingSoundFeedback.play(
      TypingSoundPreset.resolved(from: typingSoundPreset),
      role: role
    )
  }

  private func speakCurrentTargetIfNeeded() {
    guard practiceAutoSpeaks else { return }
    speakCurrentTarget()
  }

  private func speakCurrentTarget() {
    guard !viewModel.isLessonComplete,
      !showsResult,
      !exitsAfterResultDismiss
    else { return }
    targetSpeechSynthesizer.speak(
      viewModel.target,
      bundledAudioPath: currentReviewSource?.item.audio
    )
  }

  private func playFeedbackSound() {
    guard soundEffectsEnabled else { return }
    switch viewModel.feedback {
    case .complete:
      HancoSoundEngine.shared.play(.completion(combo: mascotCombo))
    case .correct:
      if case .comboMilestone(let count) = viewModel.reactionEvent {
        HancoSoundEngine.shared.play(.completion(combo: count))
      }
    case .incorrect:
      HancoSoundEngine.shared.play(.mistake)
    case .idle:
      break
    }
  }
}

private struct JamoProgressTrack: View {
  private static let chipWidth: CGFloat = 30
  private static let chipSpacing: CGFloat = 7
  private static let horizontalSafeInset: CGFloat = 2

  let sequence: [Character]
  let completedCount: Int

  private var activeIndex: Int? {
    guard !sequence.isEmpty else { return nil }
    return min(completedCount, sequence.count - 1)
  }

  private var estimatedContentWidth: CGFloat {
    let chipWidths = CGFloat(sequence.count) * Self.chipWidth
    let spacing = CGFloat(max(sequence.count - 1, 0)) * Self.chipSpacing
    return chipWidths + spacing + Self.horizontalSafeInset * 2
  }

  var body: some View {
    GeometryReader { geometry in
      ScrollViewReader { scrollProxy in
        let overflows = estimatedContentWidth > geometry.size.width

        ScrollView(.horizontal, showsIndicators: false) {
          HStack(spacing: Self.chipSpacing) {
            ForEach(Array(sequence.enumerated()), id: \.offset) { index, jamo in
              Text(verbatim: String(jamo))
                .font(.system(.body, design: .rounded, weight: .bold))
                .foregroundStyle(index < completedCount ? .white : AppPalette.ink)
                .frame(width: Self.chipWidth, height: 34)
                .background(
                  index < completedCount
                    ? AppPalette.accent : AppPalette.accentSoft.opacity(0.38),
                  in: RoundedRectangle(cornerRadius: 9)
                )
                .overlay {
                  if index == completedCount {
                    RoundedRectangle(cornerRadius: 9)
                      .strokeBorder(AppPalette.accent, lineWidth: 2)
                  }
                }
                .id(index)
                .accessibilityIdentifier(
                  index == completedCount ? "practice.jamo.active" : "practice.jamo.\(index)"
                )
            }
          }
          .padding(.horizontal, Self.horizontalSafeInset)
          .frame(minWidth: geometry.size.width, alignment: .center)
          .padding(.vertical, 2)
        }
        .overlay {
          edgeFades(overflows: overflows)
        }
        .onAppear {
          followActiveJamo(using: scrollProxy, animated: false)
        }
        .onChange(of: completedCount) { _ in
          followActiveJamo(using: scrollProxy, animated: true)
        }
      }
    }
    .frame(height: 38)
  }

  private func edgeFades(overflows: Bool) -> some View {
    HStack(spacing: 0) {
      edgeFade(from: .leading)
        .opacity(overflows && completedCount > 0 ? 1 : 0)
      Spacer(minLength: 0)
      edgeFade(from: .trailing)
        .opacity(overflows && completedCount < sequence.count ? 1 : 0)
    }
    .allowsHitTesting(false)
    .accessibilityHidden(true)
  }

  private func edgeFade(from edge: HorizontalEdge) -> some View {
    LinearGradient(
      colors: [AppPalette.card, AppPalette.card.opacity(0)],
      startPoint: edge == .leading ? .leading : .trailing,
      endPoint: edge == .leading ? .trailing : .leading
    )
    .frame(width: 22)
  }

  private func followActiveJamo(using scrollProxy: ScrollViewProxy, animated: Bool) {
    guard let activeIndex else { return }
    if animated {
      withAnimation(.easeOut(duration: 0.18)) {
        scrollProxy.scrollTo(activeIndex, anchor: .center)
      }
    } else {
      scrollProxy.scrollTo(activeIndex, anchor: .center)
    }
  }
}

private struct TargetSyllableProgressView: View {
  private static let syllableWidth: CGFloat = 42
  private static let whitespaceWidth: CGFloat = 28

  let target: String
  let units: [TargetSyllableProgress]
  let fontScale: CGFloat

  private var completedCount: Int {
    units.filter { $0.isHangul && $0.state == .completed }.count
  }

  private var totalCount: Int {
    units.filter(\.isHangul).count
  }

  private var accessibilityProgress: String {
    String(
      format: AppLocalization.string("practice.syllable_progress_value"),
      completedCount,
      totalCount
    )
  }

  private var baseContentWidth: CGFloat {
    let unitWidths = units.reduce(CGFloat.zero) { width, unit in
      width + (unit.character.isWhitespace ? Self.whitespaceWidth : Self.syllableWidth)
    }
    let spacing = CGFloat(max(units.count - 1, 0)) * 5
    return unitWidths + spacing + 8
  }

  private var activeUnitID: Int? {
    units.first { $0.state != .completed }?.id
  }

  private func fittedScale(for availableWidth: CGFloat) -> CGFloat {
    guard baseContentWidth > 0 else { return fontScale }
    return min(fontScale, max(0.01, availableWidth / baseContentWidth))
  }

  var body: some View {
    GeometryReader { proxy in
      let displayScale = fittedScale(for: proxy.size.width)

      HStack(spacing: 5 * displayScale) {
        ForEach(units) { unit in
          syllable(unit, scale: displayScale)
            .id(unit.id)
        }
      }
      .padding(.horizontal, 4 * displayScale)
      .padding(.vertical, 3 * displayScale)
      .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
    }
    .frame(height: 54 * fontScale)
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(Text(verbatim: target))
    .accessibilityValue(Text(verbatim: accessibilityProgress))
    .accessibilityIdentifier("practice.target.value")
  }

  @ViewBuilder
  private func syllable(_ unit: TargetSyllableProgress, scale: CGFloat) -> some View {
    if unit.character.isWhitespace {
      spaceIndicator(
        state: unit.state,
        isActive: unit.id == activeUnitID,
        scale: scale
      )
      .frame(width: Self.whitespaceWidth * scale, height: 48 * scale)
      .accessibilityHidden(true)
    } else {
      ZStack(alignment: .topTrailing) {
        Text(verbatim: String(unit.character))
          .font(.system(size: 34 * scale, weight: .bold, design: .rounded))
          .foregroundStyle(foreground(for: unit.state))
          .minimumScaleFactor(0.7)
          .frame(minWidth: 38 * scale, minHeight: 48 * scale)
          .padding(.horizontal, 2 * scale)
          .background(background(for: unit.state), in: RoundedRectangle(cornerRadius: 13 * scale))
          .overlay {
            if unit.state == .inProgress {
              RoundedRectangle(cornerRadius: 13 * scale)
                .strokeBorder(AppPalette.accent, lineWidth: 2.5 * scale)
            }
          }

      }
      .animation(.spring(response: 0.22, dampingFraction: 0.62), value: unit.state)
      .accessibilityHidden(true)
    }
  }

  private func spaceIndicator(
    state: TargetSyllableProgress.State,
    isActive: Bool,
    scale: CGFloat
  ) -> some View {
    let isCompleted = state == .completed

    return ZStack {
      RoundedRectangle(cornerRadius: 7 * scale, style: .continuous)
        .fill(
          isCompleted
            ? AppPalette.success
            : AppPalette.accentSoft.opacity(isActive ? 0.58 : 0.32)
        )
        .overlay {
          RoundedRectangle(cornerRadius: 7 * scale, style: .continuous)
            .strokeBorder(
              isActive ? AppPalette.accent : AppPalette.mutedInk.opacity(0.18),
              lineWidth: (isActive ? 2.5 : 1) * scale
            )
        }

      SpaceKeyGlyph()
        .stroke(
          isCompleted ? Color.white : AppPalette.mutedInk.opacity(isActive ? 0.9 : 0.58),
          style: StrokeStyle(lineWidth: 2 * scale, lineCap: .round, lineJoin: .round)
        )
        .frame(width: 15 * scale, height: 7 * scale)
    }
    .frame(width: 24 * scale, height: 20 * scale)
    .animation(.spring(response: 0.22, dampingFraction: 0.62), value: state)
  }

  private func foreground(for state: TargetSyllableProgress.State) -> Color {
    state == .completed ? .white : AppPalette.ink
  }

  private func background(for state: TargetSyllableProgress.State) -> Color {
    switch state {
    case .pending: .clear
    case .inProgress: AppPalette.accentSoft.opacity(0.58)
    case .completed: AppPalette.success
    }
  }
}

private struct SpaceKeyGlyph: Shape {
  func path(in rect: CGRect) -> Path {
    var path = Path()
    path.move(to: CGPoint(x: rect.minX, y: rect.minY))
    path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
    path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
    path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
    return path
  }
}

struct SessionSettingsOverlay: View {
  @Binding var showsKeyGuide: Bool
  @Binding var showsRomanHints: Bool
  @Binding var hapticsEnabled: Bool
  @Binding var inputMode: SessionInputMode
  @Binding var soundEffectsEnabled: Bool
  @Binding var typingSoundPreset: String
  @Binding var practiceShowsTarget: Bool
  @Binding var practiceShowsMeaning: Bool
  @Binding var practiceShowsReading: Bool
  @Binding var practicePromptOrder: String
  @Binding var practiceShowsJamo: Bool
  @Binding var practiceAutoSpeaks: Bool
  @Binding var practiceShowsMascot: Bool
  @Binding var practiceShowsComposition: Bool

  let allowsOSKeyboard: Bool
  let onUnavailableOSIME: () -> Void
  let onClose: () -> Void

  @State private var showsDisplaySettings = false
  @State private var showsPromptOrder = false
  @State private var showsSoundSettings = false

  var body: some View {
    ZStack {
      Color.black.opacity(0.12)
        .ignoresSafeArea()
        .contentShape(Rectangle())
        .onTapGesture(perform: onClose)

      ScrollView {
        VStack(alignment: .leading, spacing: 14) {
          HStack {
            Text("practice.session_settings")
              .font(.headline.weight(.bold))
              .foregroundStyle(AppPalette.ink)
            Spacer()
            Button(action: onClose) {
              Image(systemName: "xmark.circle.fill")
                .font(.title3)
                .foregroundStyle(AppPalette.mutedInk)
            }
            .accessibilityLabel(Text("common.close"))
            .accessibilityIdentifier("practice.session_settings.close")
          }

          settingsSection(title: "settings.keyboard") {
            Toggle(isOn: $showsKeyGuide) {
              Label("practice.setup.key_guide", systemImage: "lightbulb.fill")
            }
            .accessibilityIdentifier("practice.session_settings.key_guide")

            Toggle(isOn: $showsRomanHints) {
              Label("practice.setup.roman_hints", systemImage: "character.book.closed.fill")
            }
            .accessibilityIdentifier("practice.session_settings.roman_hints")

            Toggle(isOn: $hapticsEnabled) {
              Label("practice.setup.haptics", systemImage: "hand.tap.fill")
            }
            .accessibilityIdentifier("practice.session_settings.haptics")

            if allowsOSKeyboard {
              SessionInputModeControl(
                selection: $inputMode,
                onUnavailableOSIME: onUnavailableOSIME
              )
            }
          }

          disclosureButton(
            title: "settings.practice_display",
            systemImage: "rectangle.3.group.fill",
            isExpanded: $showsDisplaySettings,
            identifier: "practice.session_settings.display"
          )
          if showsDisplaySettings {
            VStack(alignment: .leading, spacing: 10) {
              disclosureButton(
                title: "settings.practice_order",
                systemImage: "arrow.up.arrow.down",
                isExpanded: $showsPromptOrder,
                identifier: "practice.session_settings.order"
              )
              if showsPromptOrder {
                VStack(alignment: .leading, spacing: 8) {
                  ForEach(PracticePromptOrder.allCases) { order in
                    Button {
                      practicePromptOrder = order.rawValue
                    } label: {
                      HStack {
                        Text(verbatim: order.localizedLabel)
                        Spacer()
                        if practicePromptOrder == order.rawValue {
                          Image(systemName: "checkmark")
                            .foregroundStyle(AppPalette.accent)
                        }
                      }
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(
                      practicePromptOrder == order.rawValue ? .isSelected : []
                    )
                  }
                }
                .padding(.leading, 8)
              }

              Toggle("settings.practice_target", isOn: $practiceShowsTarget)
                .accessibilityIdentifier("practice.session_settings.target")
              Toggle("settings.practice_meaning", isOn: $practiceShowsMeaning)
                .accessibilityIdentifier("practice.session_settings.meaning")
              Toggle("settings.practice_reading", isOn: $practiceShowsReading)
                .accessibilityIdentifier("practice.session_settings.reading")
              Toggle("settings.practice_jamo", isOn: $practiceShowsJamo)
                .accessibilityIdentifier("practice.session_settings.jamo")
              Toggle("settings.practice_mascot", isOn: $practiceShowsMascot)
                .accessibilityIdentifier("practice.session_settings.mascot")
              Toggle("settings.practice_composition", isOn: $practiceShowsComposition)
                .accessibilityIdentifier("practice.session_settings.composition")
            }
            .padding(.top, 8)
          }

          disclosureButton(
            title: "settings.sound",
            systemImage: "speaker.wave.2.fill",
            isExpanded: $showsSoundSettings,
            identifier: "practice.session_settings.sound_menu"
          )
          if showsSoundSettings {
            VStack(alignment: .leading, spacing: 10) {
              Toggle(isOn: $practiceAutoSpeaks) {
                Label(
                  "settings.practice_auto_speak",
                  systemImage: practiceAutoSpeaks ? "speaker.wave.2.fill" : "speaker.slash.fill"
                )
              }
              .accessibilityIdentifier("practice.session_settings.auto_speak")

              Toggle(isOn: $soundEffectsEnabled) {
                Label(
                  "practice.setup.sound",
                  systemImage: soundEffectsEnabled ? "speaker.wave.2.fill" : "speaker.slash.fill"
                )
              }
              .accessibilityIdentifier("practice.session_settings.sound")

              Picker("practice.setup.sound_preset", selection: $typingSoundPreset) {
                Text("practice.setup.sound_system").tag(TypingSoundPreset.system.rawValue)
                Text("practice.setup.sound_mechanical").tag(TypingSoundPreset.mechanical.rawValue)
                Text("practice.setup.sound_soft").tag(TypingSoundPreset.soft.rawValue)
              }
              .pickerStyle(.segmented)
              .disabled(!soundEffectsEnabled)
              .accessibilityIdentifier("practice.session_settings.sound_preset")
            }
            .padding(.top, 8)
          }
        }
        .padding(18)
      }
      .frame(maxWidth: 430, maxHeight: 620)
      .background(.ultraThickMaterial, in: RoundedRectangle(cornerRadius: 24))
      .overlay {
        RoundedRectangle(cornerRadius: 24)
          .stroke(AppPalette.keyShadow.opacity(0.7), lineWidth: 1)
      }
      .shadow(color: .black.opacity(0.16), radius: 24, y: 10)
      .padding(14)
    }
    .accessibilityIdentifier("practice.session_settings.overlay")
    .onChange(of: soundEffectsEnabled) { enabled in
      HancoSoundEngine.shared.setEnabled(enabled)
      if enabled {
        HancoTypingSoundFeedback.play(resolvedTypingPreset)
      }
    }
    .onChange(of: typingSoundPreset) { _ in
      guard soundEffectsEnabled else { return }
      HancoTypingSoundFeedback.play(resolvedTypingPreset)
    }
  }

  private func settingsSection<Content: View>(
    title: LocalizedStringKey,
    @ViewBuilder content: () -> Content
  ) -> some View {
    VStack(alignment: .leading, spacing: 10) {
      Text(title)
        .font(.caption.weight(.bold))
        .foregroundStyle(AppPalette.mutedInk)
      content()
    }
    .padding(14)
    .background(AppPalette.card.opacity(0.94), in: RoundedRectangle(cornerRadius: 18))
  }

  private func disclosureButton(
    title: LocalizedStringKey,
    systemImage: String,
    isExpanded: Binding<Bool>,
    identifier: String
  ) -> some View {
    Button {
      withAnimation(.easeOut(duration: 0.16)) {
        isExpanded.wrappedValue.toggle()
      }
    } label: {
      HStack {
        Label(title, systemImage: systemImage)
        Spacer()
        Image(systemName: isExpanded.wrappedValue ? "chevron.up" : "chevron.down")
          .font(.caption.weight(.bold))
          .foregroundStyle(AppPalette.mutedInk)
      }
      .contentShape(Rectangle())
    }
    .buttonStyle(.plain)
    .accessibilityIdentifier(identifier)
    .accessibilityAddTraits(isExpanded.wrappedValue ? .isSelected : [])
  }

  private var resolvedTypingPreset: TypingSoundPreset {
    TypingSoundPreset.resolved(from: typingSoundPreset)
  }
}

enum JapaneseMeaningDisplayText {
  static func format(_ meaning: String) -> String {
    let trimmed = meaning.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty else { return "" }
    if (trimmed.hasPrefix("「") && trimmed.hasSuffix("」"))
      || (trimmed.hasPrefix("『") && trimmed.hasSuffix("』"))
    {
      return String(trimmed.dropFirst().dropLast())
    }
    return trimmed
  }
}

struct SyllableAssemblyPreview: View {
  @Environment(\.hancoFontScale) private var fontScale

  let text: String
  let incomingJamo: Character?
  let revision: Int
  let shouldAnimateJoin: Bool

  @State private var previousText = ""
  @State private var stagedPrevious = ""
  @State private var stagedIncoming = ""
  @State private var isShowingParts = false
  @State private var partsHaveMerged = false
  @State private var partsOpacity = 1.0
  @State private var resultScale: CGFloat = 1
  @State private var animationTask: Task<Void, Never>?

  var body: some View {
    ZStack {
      Circle()
        .fill(AppPalette.accentSoft.opacity(0.66))
        .frame(width: 96, height: 96)

      if isShowingParts {
        HStack(spacing: 0) {
          previewText(stagedPrevious)
            .offset(x: partsHaveMerged ? 9 : -3)
          previewText(stagedIncoming)
            .offset(x: partsHaveMerged ? -9 : 3)
        }
        .opacity(partsOpacity)
      } else {
        previewText(text.isEmpty ? "…" : text)
          .scaleEffect(resultScale)
      }
    }
    .frame(width: 112, height: 100)
    .onAppear {
      previousText = text
    }
    .onChange(of: revision) { _ in
      animateTransition()
    }
    .onDisappear {
      animationTask?.cancel()
    }
    .accessibilityHidden(true)
  }

  private func previewText(_ value: String) -> some View {
    Text(verbatim: value)
      .font(.system(size: 46 * fontScale, weight: .bold, design: .rounded))
      .foregroundStyle(AppPalette.ink)
      .minimumScaleFactor(0.62)
      .lineLimit(1)
  }

  private func animateTransition() {
    animationTask?.cancel()
    let priorText = previousText
    previousText = text

    guard !text.isEmpty else {
      isShowingParts = false
      resultScale = 1
      return
    }

    guard shouldAnimateJoin, !priorText.isEmpty, let incomingJamo else {
      isShowingParts = false
      resultScale = 0.9
      withAnimation(.spring(response: 0.16, dampingFraction: 0.62)) {
        resultScale = 1
      }
      return
    }

    stagedPrevious = priorText
    stagedIncoming = String(incomingJamo)
    isShowingParts = true
    partsHaveMerged = false
    partsOpacity = 1

    animationTask = Task { @MainActor in
      await Task.yield()
      guard !Task.isCancelled else { return }
      withAnimation(.easeIn(duration: 0.1)) {
        partsHaveMerged = true
        partsOpacity = 0.16
      }

      try? await Task.sleep(nanoseconds: 100_000_000)
      guard !Task.isCancelled else { return }
      isShowingParts = false
      resultScale = 0.88
      withAnimation(.spring(response: 0.08, dampingFraction: 0.54)) {
        resultScale = 1.08
      }

      try? await Task.sleep(nanoseconds: 70_000_000)
      guard !Task.isCancelled else { return }
      withAnimation(.easeOut(duration: 0.03)) {
        resultScale = 1
      }
    }
  }
}

private struct CompletionCelebrationView: View {
  private let particleCount = 18

  @State private var burstProgress: CGFloat = 0
  @State private var particleOpacity = 1.0
  @State private var animationTask: Task<Void, Never>?

  var body: some View {
    GeometryReader { proxy in
      ZStack {
        ForEach(0..<particleCount, id: \.self) { index in
          let angle = particleAngle(at: index)
          let radius = burstProgress * particleDistance(at: index, in: proxy.size)

          Image(systemName: particleSymbol(at: index))
            .font(.system(size: particleSize(at: index), weight: .bold))
            .foregroundStyle(particleColor(at: index))
            .scaleEffect(0.25 + burstProgress * particleScale(at: index))
            .rotationEffect(.degrees(Double(burstProgress) * Double(index * 29)))
            .position(x: proxy.size.width / 2, y: proxy.size.height / 2)
            .offset(x: CGFloat(cos(angle)) * radius, y: CGFloat(sin(angle)) * radius)
            .opacity(particleOpacity)
        }
      }
      .frame(width: proxy.size.width, height: proxy.size.height)
    }
    .onAppear {
      animationTask?.cancel()
      burstProgress = 0
      particleOpacity = 1
      animationTask = Task { @MainActor in
        await Task.yield()
        guard !Task.isCancelled else { return }
        withAnimation(.easeOut(duration: 0.42)) {
          burstProgress = 1
        }
        try? await Task.sleep(nanoseconds: 410_000_000)
        guard !Task.isCancelled else { return }
        withAnimation(.easeIn(duration: 0.16)) {
          particleOpacity = 0
        }
      }
    }
    .onDisappear {
      animationTask?.cancel()
    }
    .accessibilityHidden(true)
  }

  private func particleAngle(at index: Int) -> Double {
    let evenAngle = Double(index) * 2 * Double.pi / Double(particleCount)
    let stagger = index.isMultiple(of: 2) ? 0.08 : -0.05
    return evenAngle - Double.pi / 2 + stagger
  }

  private func particleDistance(at index: Int, in size: CGSize) -> CGFloat {
    let shortestSide = min(size.width, size.height)
    let baseDistance = min(max(shortestSide * 0.23, 92), 148)
    switch index % 3 {
    case 0: return baseDistance
    case 1: return baseDistance * 0.78
    default: return baseDistance * 0.58
    }
  }

  private func particleSymbol(at index: Int) -> String {
    switch index % 3 {
    case 0: "sparkle"
    case 1: "star.fill"
    default: "circle.fill"
    }
  }

  private func particleSize(at index: Int) -> CGFloat {
    switch index % 3 {
    case 0: 22
    case 1: 16
    default: 9
    }
  }

  private func particleScale(at index: Int) -> CGFloat {
    index.isMultiple(of: 3) ? 1.05 : 0.82
  }

  private func particleColor(at index: Int) -> Color {
    switch index % 4 {
    case 0: AppPalette.accent
    case 1: AppPalette.secondary
    case 2: Color.orange
    default: AppPalette.success
    }
  }
}

private struct ShakeEffect: GeometryEffect {
  var amount: CGFloat = 7
  var shakesPerUnit: CGFloat = 3
  var animatableData: CGFloat

  func effectValue(size: CGSize) -> ProjectionTransform {
    ProjectionTransform(
      CGAffineTransform(translationX: amount * sin(animatableData * .pi * shakesPerUnit), y: 0)
    )
  }
}
