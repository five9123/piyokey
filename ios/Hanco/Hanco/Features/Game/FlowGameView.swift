import DeckKit
import SwiftUI
import UIKit

struct FlowGameView: View {
  @Environment(\.dismiss) private var dismiss
  @Environment(\.scenePhase) private var scenePhase
  @Environment(\.hancoFontScale) private var fontScale
  @Environment(\.hancoAdaptiveMetrics) private var adaptiveMetrics
  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @EnvironmentObject private var deckLibrary: DeckLibrary
  @EnvironmentObject private var discoverViewModel: DiscoverViewModel
  @EnvironmentObject private var gameProgress: GameProgressLibrary
  @EnvironmentObject private var reviewDeck: ReviewDeckLibrary
  @EnvironmentObject private var retention: RetentionLibrary
  @EnvironmentObject private var companion: MascotCompanionLibrary
  @AppStorage(KeyboardPreferenceKeys.showsKeyGuide) private var showsKeyGuide = true
  @AppStorage(KeyboardPreferenceKeys.showsRomanHints) private var showsRomanHints = true
  @AppStorage(KeyboardPreferenceKeys.hapticsEnabled) private var hapticsEnabled = true
  @AppStorage(KeyboardPreferenceKeys.inputModeDefault) private var inputModeDefault =
    SessionInputMode.builtIn.rawValue
  @AppStorage(SoundPreferenceKeys.effectsEnabled) private var soundEffectsEnabled = true
  @AppStorage(SoundPreferenceKeys.typingPreset) private var typingSoundPreset =
    TypingSoundPreset.system.rawValue

  let deck: Deck
  let gameKind: GameKind
  let competition: GameCompetition?
  private let course: FlowGameCourse
  private let mascotAppearance: MascotSessionAppearance
  private let countdownStepDuration: TimeInterval
  @StateObject private var viewModel: FlowGameViewModel
  @State private var sessionItems: [DeckItem]
  @State private var tickerTask: Task<Void, Never>?
  @State private var tickerGeneration = 0
  @State private var countdownTask: Task<Void, Never>?
  @State private var countdownGeneration = 0
  @State private var countdownValue: Int?
  @State private var shakeStep: CGFloat = 0
  @State private var lifeLossShakeStep: CGFloat = 0
  @State private var lifeLossFlashOpacity: Double = 0
  @State private var lifeLossFeedbackTask: Task<Void, Never>?
  @State private var resultPresentationTask: Task<Void, Never>?
  @State private var recordOutcome: GameRecordSaveOutcome?
  @State private var didPersistCurrentRun = false
  @State private var sessionReviewItems: [SessionReviewItem] = []
  @State private var retentionSession = RetentionSessionContext()
  @State private var inputMode: SessionInputMode = .builtIn
  @State private var recordInputMode: SessionInputMode = .builtIn
  @State private var builtInKeyboardLayout: BuiltInKeyboardLayout
  @State private var korean10KeyInterpreter = Korean10KeyInterpreter()
  @State private var hasUsedBuiltInInput = false
  @State private var didResolveInputMode = false
  @State private var inputResetRevision = 0
  @State private var showsOSIMEUnavailable = false
  @State private var showsResult = false
  @State private var exitsAfterResultDismiss = false
  @State private var previousAcceptedInputCount = 0
  @State private var priorBestCombo = 0
  @State private var didCelebrateBestCombo = false
  @State private var enteredBackground = false
  @State private var didCaptureAnalyticsStart = false
  @State private var didCaptureAnalyticsCompletion = false
  @State private var didCaptureAnalyticsAbandonment = false
  #if DEBUG
    @StateObject private var frameRateMonitor = GameFrameRateMonitor()
  #endif

  init(
    deck: Deck,
    gameKind: GameKind = .flow,
    competition: GameCompetition? = nil
  ) {
    precondition(gameKind == .flow || gameKind == .acidRain)
    precondition(competition == nil || gameKind == .flow)
    self.deck = deck
    self.gameKind = gameKind
    self.competition = competition
    self.mascotAppearance = MascotSessionAppearance()
    let storedBuiltInLayout = BuiltInKeyboardLayout.resolved(
      from: UserDefaults.standard.string(
        forKey: KeyboardPreferenceKeys.builtInLayoutDefault
      ) ?? BuiltInKeyboardLayout.dubeolsik.rawValue
    )
    _builtInKeyboardLayout = State(
      initialValue: competition == nil ? storedBuiltInLayout : .dubeolsik
    )
    var sessionItems = GamePresetSessionRandomizer.shuffledItems(
      from: deck,
      gameKind: gameKind
    )
    #if DEBUG
      if let rawIndex = ProcessInfo.processInfo.environment["UITEST_FLOW_START_INDEX"],
        let requestedIndex = Int(rawIndex), sessionItems.indices.contains(requestedIndex)
      {
        sessionItems = Array(sessionItems[requestedIndex...]) + Array(sessionItems[..<requestedIndex])
      }
    #endif
    _sessionItems = State(initialValue: sessionItems)
    let course = FlowGameCourse(deck: deck)
    self.course = course
    var duration: TimeInterval = 60
    #if DEBUG
      if let rawDuration = ProcessInfo.processInfo.environment["UITEST_GAME_DURATION_SECONDS"],
        let testDuration = TimeInterval(rawDuration), testDuration > 0
      {
        duration = testDuration
      }
    #endif
    var countdownStepDuration: TimeInterval = 1
    var flawlessBonus: TimeInterval = 2
    #if DEBUG
      if let rawDuration = ProcessInfo.processInfo.environment[
        "UITEST_GAME_COUNTDOWN_STEP_SECONDS"],
        let testDuration = TimeInterval(rawDuration), testDuration >= 0
      {
        countdownStepDuration = testDuration
      }
      if let rawBonus = ProcessInfo.processInfo.environment[
        "UITEST_FLOW_FLAWLESS_BONUS_SECONDS"],
        let testBonus = TimeInterval(rawBonus), testBonus >= 0
      {
        flawlessBonus = testBonus
      }
    #endif
    self.countdownStepDuration = countdownStepDuration
    _viewModel = StateObject(
      wrappedValue: FlowGameViewModel(
        targets: sessionItems.map(\.ko),
        initialDuration: duration,
        flawlessBonus: flawlessBonus,
        cardTravelDuration: gameKind == .acidRain
          ? max(5.5, course.cardTravelDuration * 0.78)
          : course.cardTravelDuration,
        allowsConcurrentCards: gameKind == .acidRain,
        rankTuning: FlowGameRankTuningLoader.requiredBundled()
      )
    )
  }

  var body: some View {
    #if DEBUG
      debugDecoratedContent.sessionExitCovered(exitsAfterResultDismiss)
    #else
      decoratedContent.sessionExitCovered(exitsAfterResultDismiss)
    #endif
  }

  private var navigationContent: some View {
    VStack(spacing: 0) {
      VStack(spacing: 12) {
        gameLane
        inputStatus
      }
      .frame(maxHeight: .infinity)
      .padding(.horizontal, 14)
      .padding(.vertical, 10)
      .hancoCenteredContent(maxWidth: adaptiveMetrics.sessionLaneMaxWidth)

      inputArea
        .hancoCenteredContent(maxWidth: adaptiveMetrics.keyboardMaxWidth)
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(gameBackground.ignoresSafeArea())
    .navigationTitle("")
    .navigationBarTitleDisplayMode(.inline)
    .navigationBarBackButtonHidden(true)
    .toolbar(.hidden, for: .tabBar)
    .toolbar { sessionToolbar }
    .toolbarBackground(AppPalette.backgroundTop, for: .navigationBar)
    .toolbarBackground(.automatic, for: .navigationBar)
    .navigationDestination(isPresented: $showsResult) {
      resultDestination
    }
  }

  @ToolbarContentBuilder
  private var sessionToolbar: some ToolbarContent {
    ToolbarItem(placement: .topBarLeading) {
      Button(action: dismiss.callAsFunction) {
        Image(systemName: "xmark")
          .font(.system(size: 16, weight: .bold))
          .foregroundStyle(AppPalette.accent)
          .frame(width: 36, height: 36)
          .background(AppPalette.card, in: Circle())
      }
      .frame(width: 44, height: 44)
      .accessibilityLabel(Text("game.end"))
      .accessibilityIdentifier("game.end")
    }
    ToolbarItem(placement: .principal) {
      hud
    }
  }

  private var resultDestination: some View {
    FlowGameResultView(
      deck: deck,
      result: viewModel.result,
      recordOutcome: recordOutcome,
      reviewItems: sessionReviewItems,
      recommendations: resultRecommendations,
      catalogDecks: discoverViewModel.catalog?.decks ?? [],
      presentation: competition == .weeklyPiyoCup
        ? .piyoCup
        : (gameKind == .acidRain ? .acidRain : .flow),
      onRetry: retryGame,
      onFinish: finishFromResult
    )
  }

  private var lifecycleContent: some View {
    navigationContent
      .onChange(of: showsResult, perform: handleResultPresentationChange)
      .onAppear(perform: handleAppear)
      .onDisappear(perform: handleDisappear)
      .onChange(of: scenePhase, perform: handleScenePhaseChange)
      .onChange(of: viewModel.feedbackRevision, perform: handleFeedbackRevision)
      .onChange(of: viewModel.totalAcceptedInputCount, perform: handleAcceptedInputCount)
      .onChange(of: viewModel.combo, perform: handleComboChange)
      .onChange(of: viewModel.reviewResolutionRevision, perform: handleReviewRevision)
      .onChange(of: viewModel.phase, perform: handlePhaseChange)
      .onChange(of: inputMode, perform: handleInputModeChange)
      .onChange(of: viewModel.cardRevision) { _ in korean10KeyInterpreter.reset() }
  }

  private var decoratedContent: some View {
    lifecycleContent
      .modifier(FlowLifeLossShakeEffect(animatableData: lifeLossShakeStep))
      .overlay(alignment: .topLeading) {
        VStack(spacing: 0) {
          Text(
            gameKind == .acidRain
              ? "acid_rain.accessibility.play_screen" : "game.accessibility.play_screen"
          )
          .accessibilityIdentifier(
            gameKind == .acidRain ? "acid_rain.play.screen" : "game.play.screen"
          )
          if gameKind == .acidRain {
            Text("acid_rain.accessibility.play_screen")
              .accessibilityIdentifier("acid_rain.lane")
            Text(verbatim: currentItem.ko)
              .accessibilityIdentifier("acid_rain.target.value")
          }
        }
        .font(.system(size: 1))
        .opacity(0.01)
      }
      .overlay {
        if let countdownValue {
          countdownOverlay(countdownValue)
        }
      }
      .overlay { lifeLossOverlay }
  }

  #if DEBUG
    private var debugDecoratedContent: some View {
      decoratedContent.overlay(alignment: .top) {
        if ProcessInfo.processInfo.environment["HANCO_SHOW_GAME_FPS"] == "1" {
          ZStack(alignment: .top) {
            GameFrameRateProbe(onFrame: frameRateMonitor.record)
              .frame(width: 0, height: 0)
              .accessibilityHidden(true)

            if let snapshot = frameRateMonitor.snapshot {
              Text(
                String(
                  format: AppLocalization.string("debug.game_fps.format"),
                  snapshot.averageFPS,
                  snapshot.p95FrameDurationMilliseconds,
                  snapshot.overBudgetFrameCount
                )
              )
              .font(.caption.monospacedDigit().weight(.black))
              .foregroundStyle(Color.black)
              .padding(.horizontal, 10)
              .padding(.vertical, 5)
              .background(Color.white.opacity(0.96), in: Capsule())
              .overlay {
                Capsule().stroke(AppPalette.accent.opacity(0.5), lineWidth: 1)
              }
              .padding(.top, 4)
              .accessibilityIdentifier("debug.game_fps")
            }
          }
        }
      }
    }
  #endif

  private func handleResultPresentationChange(_ isPresented: Bool) {
    guard !isPresented, exitsAfterResultDismiss else { return }
    Task { @MainActor in
      await Task.yield()
      guard exitsAfterResultDismiss else { return }
      dismiss()
    }
  }

  private func handleAppear() {
    resolveInitialInputModeIfNeeded()
    captureAnalyticsStartIfNeeded()
    priorBestCombo =
      gameProgress.records
      .filter {
        $0.deckId == deck.deckId && GameKind(course: $0.course) == gameKind
      }
      .map(\.maxCombo)
      .max() ?? 0
    previousAcceptedInputCount = viewModel.totalAcceptedInputCount
    if viewModel.phase == .ready {
      beginCountdown()
    } else if viewModel.phase == .paused, scenePhase == .active {
      viewModel.resume()
      startTicker()
    }
  }

  private func handleDisappear() {
    cancelCountdown(reset: false)
    stopTicker()
    reviewDeck.flush()
    lifeLossFeedbackTask?.cancel()
    lifeLossFeedbackTask = nil
    resultPresentationTask?.cancel()
    resultPresentationTask = nil
    captureAnalyticsAbandonmentIfNeeded()
  }

  private func handleScenePhaseChange(_ phase: ScenePhase) {
    switch phase {
    case .active:
      if viewModel.phase == .ready {
        beginCountdown()
      } else {
        viewModel.resume()
        startTicker()
      }
      if enteredBackground, viewModel.phase == .running {
        enteredBackground = false
        viewModel.publishStretchReaction()
      }
    case .inactive, .background:
      enteredBackground = true
      stopTicker()
      if viewModel.phase == .ready {
        cancelCountdown(reset: true)
      }
      viewModel.pause()
      #if DEBUG
        frameRateMonitor.reset()
      #endif
    @unknown default:
      stopTicker()
      viewModel.pause()
    }
  }

  private func handleFeedbackRevision(_: Int) {
    playFeedbackSound()
    if case .escaped = viewModel.feedback {
      triggerLifeLossFeedback()
    } else if case .incorrect = viewModel.feedback {
      withAnimation(.linear(duration: 0.24)) { shakeStep += 1 }
      if case .incorrect(let expected) = viewModel.feedback {
        companion.recordMistake(expected: expected)
      }
    }
  }

  private func handleAcceptedInputCount(_ count: Int) {
    let delta = max(0, count - previousAcceptedInputCount)
    if delta > 0 { companion.recordTypedJamo(delta) }
    previousAcceptedInputCount = count
  }

  private func handleComboChange(_ combo: Int) {
    guard !didCelebrateBestCombo, priorBestCombo > 0, combo > priorBestCombo else { return }
    didCelebrateBestCombo = true
    viewModel.publishNewBestReaction()
  }

  private func handleReviewRevision(_: Int) {
    persistReviewResolution()
  }

  private func handlePhaseChange(_ phase: FlowGameViewModel.Phase) {
    if phase == .finished {
      persistFinishedGame()
      resultPresentationTask?.cancel()
      if case .escaped = viewModel.feedback {
        resultPresentationTask = Task { @MainActor in
          try? await Task.sleep(nanoseconds: 520_000_000)
          guard !Task.isCancelled else { return }
          showsResult = true
        }
      } else {
        showsResult = true
      }
    }
  }

  private func retryGame() {
    showsResult = false
    recordOutcome = nil
    didPersistCurrentRun = false
    sessionReviewItems.removeAll(keepingCapacity: true)
    retentionSession = RetentionSessionContext()
    hasUsedBuiltInInput = false
    recordInputMode = resolvedRecordInputMode
    korean10KeyInterpreter.reset()
    previousAcceptedInputCount = 0
    didCelebrateBestCombo = false
    didCaptureAnalyticsStart = false
    didCaptureAnalyticsCompletion = false
    didCaptureAnalyticsAbandonment = false
    lifeLossFeedbackTask?.cancel()
    lifeLossFeedbackTask = nil
    resultPresentationTask?.cancel()
    resultPresentationTask = nil
    lifeLossFlashOpacity = 0
    inputResetRevision += 1
    if GamePresetSessionRandomizer.isBundledPreset(
      deckID: deck.deckId,
      gameKind: gameKind
    ) {
      let nextItems = GamePresetSessionRandomizer.shuffledItems(
        from: deck,
        gameKind: gameKind,
        avoiding: sessionItems
      )
      sessionItems = nextItems
      viewModel.restart(targets: nextItems.map(\.ko))
    } else {
      viewModel.restart()
    }
    captureAnalyticsStartIfNeeded()
    beginCountdown()
  }

  private func handleInputModeChange(_ mode: SessionInputMode) {
    showsOSIMEUnavailable = false
    korean10KeyInterpreter.reset()
    inputResetRevision += 1
    if mode == .osIME, !hasUsedBuiltInInput {
      recordInputMode = .osIME
    }
  }

  private var inputArea: some View {
    VStack(spacing: 7) {
      if showsOSIMEUnavailable {
        OSIMEUnavailableBanner()
          .padding(.horizontal, 12)
      }

      if inputMode == .osIME {
        OSIMEInputPanel(
          target: viewModel.target,
          candidateTargets: acidRainOSIMECandidateTargets,
          acceptedText: viewModel.enteredText,
          resetRevision: viewModel.cardRevision + inputResetRevision,
          onAcceptedSequence: viewModel.synchronizeOSIME,
          onAcceptedCandidateSequence: synchronizeAcidRainOSIME,
          onConfirmedMismatch: viewModel.recordConfirmedOSIMEMistake
        )
      } else {
        if builtInKeyboardLayout == .korean10Key {
          Korean10KeyKeyboardView(
            nextExpectedKey: korean10KeyInterpreter.nextKey(for: viewModel.nextExpectedKey),
            options: HangulKeyboardOptions(
              showsKeyGuide: showsKeyGuide,
              showsRomanHints: false,
              hapticsEnabled: hapticsEnabled
            ),
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
            onKeyFeedback: playKeySound,
            onKey: { key in
              markBuiltInInputUsed()
              viewModel.input(key)
            },
            onBackspace: {
              markBuiltInInputUsed()
              viewModel.backspace()
            }
          )
        }
      }
    }
  }

  private func inputKorean10Key(_ key: Korean10KeyKey) {
    markBuiltInInputUsed()
    switch korean10KeyInterpreter.input(key, expecting: viewModel.nextExpectedKey) {
    case .pending, .separatorAccepted:
      break
    case .committed(let jamo):
      viewModel.input(jamo)
    case .incorrect:
      viewModel.input(key.displayText.first ?? "ㆍ")
    }
  }

  private func backspaceKorean10Key() {
    markBuiltInInputUsed()
    guard korean10KeyInterpreter.backspace() == .forwardToHangulEngine else { return }
    viewModel.backspace()
  }

  @ViewBuilder
  private var gameBackground: some View {
    LinearGradient(
      colors: [
        AppPalette.backgroundTop,
        comboTier >= 2 ? AppPalette.accentSoft.opacity(0.92) : AppPalette.backgroundBottom,
      ],
      startPoint: .topLeading,
      endPoint: .bottomTrailing
    )
    .animation(.easeInOut(duration: 0.35), value: comboTier)
  }

  private var acidRainOSIMECandidateTargets: [String] {
    guard gameKind == .acidRain, inputMode == .osIME else { return [] }
    return viewModel.acidRainCards.reduce(into: []) { result, card in
      let candidate = sessionItems[card.itemIndex % sessionItems.count].ko
      if !result.contains(candidate) {
        result.append(candidate)
      }
    }
  }

  private func synchronizeAcidRainOSIME(
    matchingTarget: String,
    acceptedSequence: [Character]
  ) {
    viewModel.synchronizeAcidRainOSIME(
      matchingTarget: matchingTarget,
      acceptedSequence: acceptedSequence
    )
  }

  private var hud: some View {
    HStack(spacing: 5) {
      hudMetric(
        systemImage: "timer",
        value: String(Int(ceil(viewModel.remainingTime))),
        label: "game.time",
        identifier: "game.timer.value",
        tint: viewModel.remainingTime <= 10
          ? AppPalette.error
          : AppPalette.secondary
      )
      hudMetric(
        systemImage: "star.fill",
        value: viewModel.score.formatted(),
        label: "game.score",
        identifier: "game.score.value",
        tint: AppPalette.accent
      )
      hudMetric(
        systemImage: "flame.fill",
        value: String(viewModel.combo),
        label: "game.combo",
        identifier: "game.combo.value",
        tint: comboTier == 0 ? AppPalette.mutedInk : Color.orange
      )
      hudMetric(
        systemImage: "heart.fill",
        value: String(viewModel.remainingLives),
        label: "game.lives",
        identifier: "game.lives.value",
        tint: viewModel.remainingLives == 1 ? AppPalette.error : AppPalette.accent
      )
      .scaleEffect(1 + CGFloat(lifeLossFlashOpacity) * 0.12)
      .shadow(
        color: AppPalette.error.opacity(lifeLossFlashOpacity * 0.55),
        radius: 8
      )
    }
  }

  @ViewBuilder
  private var gameLane: some View {
    if gameKind == .acidRain {
      acidRainLane
    } else {
      flowLane
    }
  }

  private var acidRainLane: some View {
    GeometryReader { geometry in
      ZStack {
        acidRainBackdrop

        TimelineView(.animation(minimumInterval: 1.0 / 60.0)) { timeline in
          let cards = viewModel.projectedAcidRainCards(at: timeline.date)
          let targetProgress = cards.first(where: \.isInputTarget)?.progress ?? 0
          ZStack {
            rainStreaks(size: geometry.size, progress: targetProgress)

            ForEach(cards) { card in
              let item = sessionItems[card.itemIndex % sessionItems.count]
              let meaning = item.appMeaning ?? ""
              let reading = item.appReading ?? ""
              let showsInputTarget = card.isInputTarget
                && (inputMode != .osIME || viewModel.completedJamoCount > 0)
              let cardWidth = MovingWordCardLayout.acidRainWidth(
                korean: item.ko,
                meaning: meaning,
                reading: reading,
                fontScale: fontScale,
                maximumWidth: AcidRainLaneLayout.cardWidth(containerWidth: geometry.size.width)
              )
              rainCard(
                item: item,
                isInputTarget: showsInputTarget,
                acceptsFreeInput: inputMode == .osIME
              )
                .frame(width: cardWidth)
                .position(
                  x: AcidRainLaneLayout.cardCenterX(
                    itemIndex: card.laneIndex,
                    containerWidth: geometry.size.width,
                    cardWidth: cardWidth
                  ),
                  y: AcidRainLaneLayout.cardCenterY(
                    progress: card.progress,
                    containerHeight: geometry.size.height
                  )
                )
                .modifier(
                  FlowCardShakeEffect(animatableData: card.isInputTarget ? shakeStep : 0)
                )
                .zIndex(card.isInputTarget ? 1 : 0)
            }

            ComboParticleCanvas(
              revision: viewModel.completionRevision,
              tier: comboTier
            )
            .allowsHitTesting(false)
            .accessibilityHidden(true)
          }
        }
      }
      .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
      .overlay {
        RoundedRectangle(cornerRadius: 26, style: .continuous)
          .stroke(AppPalette.secondary.opacity(0.24), lineWidth: 1.2)
      }
    }
    .frame(minHeight: inputMode == .osIME ? 150 : 214, maxHeight: .infinity)
    .overlay(alignment: .topLeading) {
      Label("game.mode.acid_rain", systemImage: "cloud.rain.fill")
        .font(.caption2.weight(.bold))
        .foregroundStyle(AppPalette.secondary)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(.ultraThinMaterial, in: Capsule())
        .padding(10)
    }
    .overlay(alignment: .bottomTrailing) {
      gameMascot(progress: viewModel.projectedCardProgress(), size: 34)
        .padding(.trailing, 10)
        .padding(.bottom, 8)
    }
  }

  private var acidRainBackdrop: some View {
    ZStack {
      LinearGradient(
        colors: [
          AppPalette.secondary.opacity(0.12),
          AppPalette.card.opacity(0.88),
        ],
        startPoint: .top,
        endPoint: .bottom
      )

      HStack(spacing: 8) {
        ForEach(0..<3, id: \.self) { _ in
          RoundedRectangle(cornerRadius: 18, style: .continuous)
            .fill(AppPalette.secondary.opacity(0.025))
            .overlay {
              RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(
                  AppPalette.secondary.opacity(0.09),
                  style: StrokeStyle(lineWidth: 1, dash: [5, 8])
                )
            }
        }
      }
      .padding(.horizontal, 12)
      .padding(.top, 46)
      .padding(.bottom, AcidRainLaneLayout.dangerZoneHeight + 8)

      VStack(spacing: 0) {
        Spacer()
        VStack(spacing: 5) {
          Rectangle()
            .fill(AppPalette.error.opacity(0.58))
            .frame(height: 2)
          HStack(spacing: 0) {
            ForEach(0..<9, id: \.self) { _ in
              Image(systemName: "chevron.down")
                .font(.system(size: 8, weight: .black))
                .foregroundStyle(AppPalette.error.opacity(0.42))
                .frame(maxWidth: .infinity)
            }
          }
          Spacer(minLength: 0)
        }
        .padding(.horizontal, 8)
        .frame(height: AcidRainLaneLayout.dangerZoneHeight)
        .background(
          LinearGradient(
            colors: [AppPalette.error.opacity(0.13), AppPalette.secondary.opacity(0.08)],
            startPoint: .top,
            endPoint: .bottom
          )
        )
      }
    }
    .accessibilityHidden(true)
  }

  private func rainStreaks(size: CGSize, progress: Double) -> some View {
    Canvas { context, canvasSize in
      for index in 0..<15 {
        let x = canvasSize.width * CGFloat((index * 37) % 101) / 100
        let offset = CGFloat(progress * 120) + CGFloat(index * 29)
        let y = offset.truncatingRemainder(dividingBy: max(canvasSize.height, 1))
        let rect = CGRect(x: x, y: y, width: 2, height: 18)
        context.fill(
          Path(roundedRect: rect, cornerRadius: 1), with: .color(AppPalette.secondary.opacity(0.18))
        )
      }
    }
    .frame(width: size.width, height: size.height)
    .accessibilityHidden(true)
  }

  private func rainCard(
    item: DeckItem,
    isInputTarget: Bool,
    acceptsFreeInput: Bool
  ) -> some View {
    VStack(spacing: 7) {
      Text(verbatim: item.ko)
        .font(.system(size: 21 * fontScale, weight: .heavy, design: .rounded))
        .foregroundStyle(AppPalette.ink)
        .lineLimit(1)
        .minimumScaleFactor(0.62)
      ProgressView(
        value: isInputTarget ? Double(viewModel.completedJamoCount) : 0,
        total: Double(max(isInputTarget ? viewModel.targetJamoSequence.count : 1, 1))
      )
      .tint(isInputTarget ? AppPalette.success : AppPalette.secondary.opacity(0.45))
      if let meaning = item.appMeaning {
        Text(verbatim: meaning)
          .font(.caption2)
          .foregroundStyle(AppPalette.mutedInk)
          .lineLimit(1)
      }
      if let reading = item.appReading {
        Text(verbatim: "[\(reading)]")
          .font(.system(size: 10, weight: .medium))
          .foregroundStyle(AppPalette.secondary)
          .lineLimit(1)
      }
    }
    .padding(.horizontal, 12)
    .padding(.vertical, 9)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 17, style: .continuous))
    .overlay {
      RoundedRectangle(cornerRadius: 17, style: .continuous)
        .stroke(
          isInputTarget
            ? AppPalette.accent
            : (acceptsFreeInput
              ? AppPalette.accent.opacity(0.56) : AppPalette.secondary.opacity(0.42)),
          lineWidth: isInputTarget ? 2.5 : 1.5
        )
    }
    .shadow(
      color: isInputTarget ? AppPalette.accent.opacity(0.24) : AppPalette.secondary.opacity(0.16),
      radius: isInputTarget ? 10 : 6,
      y: 5
    )
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(Text(verbatim: item.ko))
    .accessibilityValue(
      Text(verbatim: localizedAccessibilityDetail(for: item))
    )
    .accessibilityIdentifier("acid_rain.falling_card")
  }

  private var flowLane: some View {
    GeometryReader { geometry in
      let cardWidth = MovingWordCardLayout.flowWidth(
        korean: currentItem.ko,
        meaning: currentItem.appMeaning ?? "",
        reading: currentItem.appReading ?? "",
        fontScale: fontScale,
        maximumWidth: min(236, geometry.size.width * 0.68)
      )
      ZStack(alignment: .leading) {
        FlowSimpleBackdrop()

        TimelineView(.animation(minimumInterval: 1.0 / 60.0)) { timeline in
          let progress = viewModel.projectedCardProgress(at: timeline.date)
          ZStack(alignment: .leading) {
            cheeringCard
              .frame(width: cardWidth)
              .offset(
                x: FlowLaneLayout.horizontalOffset(
                  progress: progress,
                  containerWidth: geometry.size.width,
                  cardWidth: cardWidth
                )
              )
              .modifier(FlowCardShakeEffect(animatableData: shakeStep))
            ComboParticleCanvas(
              revision: viewModel.completionRevision,
              tier: comboTier
            )
            .allowsHitTesting(false)
            .accessibilityHidden(true)
          }
        }
      }
      .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
      .overlay {
        RoundedRectangle(cornerRadius: 26, style: .continuous)
          .stroke(AppPalette.secondary.opacity(0.16), lineWidth: 1)
      }
    }
    .frame(minHeight: inputMode == .osIME ? 110 : 178, maxHeight: .infinity)
    .overlay(alignment: .topLeading) {
      Text(course.localizedName)
        .font(.caption2.weight(.bold))
        .foregroundStyle(AppPalette.secondary)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(AppPalette.secondary.opacity(0.07), in: Capsule())
        .padding(10)
        .accessibilityIdentifier("game.flow.simple_lane")
    }
    .overlay(alignment: .bottomTrailing) {
      gameMascot(progress: viewModel.projectedCardProgress())
        .padding(.trailing, 8)
        .padding(.bottom, 7)
    }
  }

  private func gameMascot(progress: Double, size: CGFloat? = nil) -> some View {
    GrowingMascotView(
      mood: gameMascotMood,
      reaction: gameMascotReaction,
      reactionRevision: viewModel.mascotRevision,
      automaticProp: mascotAppearance.automaticProp,
      gazeX: CGFloat(1 - progress * 2),
      gazeY: viewModel.consecutiveMistakes > 0 ? 0.7 : -0.05,
      pose: FlowGameMascotLayout.pose,
      intensity: min(CGFloat(viewModel.combo) / 20, 1),
      speech: gameMascotSpeech,
      size: size ?? (inputMode == .osIME ? 34 : 43),
      calm: true
    )
    .accessibilityLabel(Text("game.mascot.label"))
    .accessibilityIdentifier("game.mascot")
  }

  private var gameMascotMood: MascotMood {
    if viewModel.remainingTime <= 10 { return .grit }
    if viewModel.consecutiveMistakes >= 3 { return .dizzy }
    switch viewModel.mascotEvent {
    case .newBest: return .surprise
    case .startle: return .oops
    case .wordCompleted, .rhythm: return .cheer
    case .correctJamo: return .happy
    case .dizzy: return .dizzy
    case .stretch, .idle: return viewModel.combo >= 10 ? .focus : .idle
    }
  }

  private var gameMascotReaction: MascotReaction {
    switch viewModel.mascotEvent {
    case .idle: .none
    case .correctJamo: .correctJamo
    case .wordCompleted: .wordCompleted
    case .rhythm(let combo): .rhythm(combo)
    case .startle: .startle
    case .dizzy: .mistake
    case .stretch: .stretch
    case .newBest: .newBest
    }
  }

  private var gameMascotSpeech: String? {
    guard viewModel.consecutiveMistakes >= 3,
      case .incorrect(let expected) = viewModel.feedback
    else { return nil }
    return String(
      format: AppLocalization.string("mascot.speech.missed_jamo"),
      String(expected)
    )
  }

  private var cheeringCard: some View {
    VStack(spacing: 4) {
      Text(verbatim: currentItem.ko)
        .font(.system(size: 25 * fontScale, weight: .black, design: .rounded))
        .foregroundStyle(AppPalette.ink)
        .lineLimit(1)
        .minimumScaleFactor(0.56)
      if let meaning = currentItem.appMeaning {
        Text(verbatim: meaning)
          .font(.caption.weight(.semibold))
          .foregroundStyle(AppPalette.mutedInk)
          .lineLimit(1)
      }
      if let reading = currentItem.appReading {
        Text(verbatim: "[\(reading)]")
          .font(.caption2.weight(.medium))
          .foregroundStyle(AppPalette.secondary)
          .lineLimit(1)
      }
    }
    .frame(maxWidth: .infinity)
    .padding(.horizontal, 14)
    .padding(.vertical, 11)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    .overlay {
      RoundedRectangle(cornerRadius: 18, style: .continuous)
        .stroke(AppPalette.accentSoft, lineWidth: 2)
    }
    .shadow(color: AppPalette.accent.opacity(0.15), radius: 9, y: 5)
    .id(viewModel.cardRevision)
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(Text(verbatim: currentItem.ko))
    .accessibilityValue(
      Text(verbatim: localizedAccessibilityDetail(for: currentItem))
    )
    .accessibilityIdentifier("game.target.value")
  }

  private func localizedAccessibilityDetail(for item: DeckItem) -> String {
    [item.appMeaning, item.appReading]
      .compactMap { $0 }
      .joined(separator: ", ")
  }

  @ViewBuilder
  private var inputStatus: some View {
    if gameKind == .acidRain, inputMode == .osIME {
      freeAcidRainInputStatus
    } else {
      standardInputStatus
    }
  }

  private var freeAcidRainInputStatus: some View {
    VStack(spacing: 7) {
      if viewModel.completedJamoCount > 0 {
        FlowJamoProgressTrack(
          sequence: viewModel.targetJamoSequence,
          completedCount: viewModel.completedJamoCount
        )
        .id(viewModel.cardRevision)
      }

      if viewModel.completedJamoCount == 0,
        viewModel.composingPreview.isEmpty,
        viewModel.feedback == .ready
      {
        Text("acid_rain.os_ime.any_word_hint")
          .foregroundStyle(AppPalette.mutedInk)
          .multilineTextAlignment(.center)
          .frame(maxWidth: .infinity, minHeight: 25, alignment: .center)
          .accessibilityIdentifier("game.feedback.status")
      } else {
        feedbackLabel
          .multilineTextAlignment(.center)
          .frame(maxWidth: .infinity, minHeight: 25, alignment: .center)
          .accessibilityIdentifier("game.feedback.status")
      }
    }
    .padding(.horizontal, 12)
    .padding(.vertical, 9)
    .frame(maxWidth: .infinity)
    .background(AppPalette.card.opacity(0.84), in: RoundedRectangle(cornerRadius: 18))
    .accessibilityIdentifier("acid_rain.os_ime.free_input_status")
  }

  private var standardInputStatus: some View {
    VStack(spacing: 7) {
      FlowJamoProgressTrack(
        sequence: viewModel.targetJamoSequence,
        completedCount: viewModel.completedJamoCount
      )
      .id(viewModel.cardRevision)

      if let pending = korean10KeyPendingDisplay {
        Text(verbatim: pending)
          .font(.caption.weight(.bold))
          .foregroundStyle(AppPalette.secondary)
          .accessibilityIdentifier("game.10key.pending")
      }

      feedbackLabel
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity, minHeight: 25, alignment: .center)
        .accessibilityIdentifier("game.feedback.status")
    }
    .padding(.horizontal, 12)
    .padding(.vertical, 9)
    .frame(maxWidth: .infinity)
    .background(AppPalette.card.opacity(0.84), in: RoundedRectangle(cornerRadius: 18))
  }

  @ViewBuilder
  private var feedbackLabel: some View {
    switch viewModel.feedback {
    case .ready:
      Text("game.feedback.ready")
        .foregroundStyle(AppPalette.mutedInk)
    case .correct:
      Text("game.feedback.correct").foregroundStyle(AppPalette.success)
    case .incorrect:
      Text("game.feedback.incorrect").foregroundStyle(AppPalette.error)
    case .completed(let points):
      Text(String(format: AppLocalization.string("game.feedback.completed"), points))
        .foregroundStyle(AppPalette.accent)
    case .escaped:
      Text("game.feedback.escaped")
        .foregroundStyle(AppPalette.mutedInk)
    }
  }

  private func hudMetric(
    systemImage: String,
    value: String,
    label: LocalizedStringKey,
    identifier: String,
    tint: Color
  ) -> some View {
    CompactGameHUDMetric(
      systemImage: systemImage,
      value: value,
      label: label,
      identifier: identifier,
      tint: tint,
      contentWidth: 58
    )
  }

  private var currentItem: DeckItem {
    sessionItems[viewModel.currentTargetIndex % sessionItems.count]
  }

  private var comboTier: Int {
    if viewModel.combo >= 20 { return 3 }
    if viewModel.combo >= 10 { return 2 }
    if viewModel.combo >= 5 { return 1 }
    return 0
  }

  private var resultRecommendations: [CatalogDeck] {
    DeckRecommendationEngine.relatedRecommendations(
      sourceDeckID: deck.deckId,
      sourceTags: deck.tags,
      catalogDecks: discoverViewModel.catalog?.decks ?? [],
      installedDeckIDs: Set(deckLibrary.installedDecks.keys)
    )
  }

  private func startTicker() {
    guard tickerTask == nil, viewModel.phase != .finished else { return }
    tickerGeneration &+= 1
    let generation = tickerGeneration
    tickerTask = Task { @MainActor in
      defer {
        if tickerGeneration == generation { tickerTask = nil }
      }
      while !Task.isCancelled, tickerGeneration == generation,
        viewModel.phase != .finished
      {
        try? await Task.sleep(nanoseconds: 33_000_000)
        guard !Task.isCancelled, tickerGeneration == generation else { break }
        viewModel.tick()
      }
    }
  }

  private func stopTicker() {
    tickerGeneration &+= 1
    tickerTask?.cancel()
    tickerTask = nil
  }

  private func beginCountdown() {
    guard countdownTask == nil, viewModel.phase == .ready, scenePhase == .active else { return }
    countdownGeneration &+= 1
    let generation = countdownGeneration
    countdownTask = Task { @MainActor in
      defer {
        if countdownGeneration == generation { countdownTask = nil }
      }
      let values = reduceMotion ? [1] : [3, 2, 1]
      for value in values {
        guard !Task.isCancelled, countdownGeneration == generation,
          viewModel.phase == .ready
        else { return }
        countdownValue = value
        let duration = reduceMotion ? min(countdownStepDuration, 0.35) : countdownStepDuration
        if duration > 0 {
          try? await Task.sleep(nanoseconds: UInt64(duration * 1_000_000_000))
        } else {
          await Task.yield()
        }
      }
      guard !Task.isCancelled, countdownGeneration == generation,
        viewModel.phase == .ready, scenePhase == .active
      else { return }
      countdownValue = nil
      enteredBackground = false
      viewModel.start()
      if deckLibrary.isInstalled(deck.deckId) {
        deckLibrary.markPlayed(deck.deckId)
      }
      startTicker()
    }
  }

  private func cancelCountdown(reset: Bool) {
    countdownGeneration &+= 1
    countdownTask?.cancel()
    countdownTask = nil
    if reset { countdownValue = nil }
  }

  private func countdownOverlay(_ value: Int) -> some View {
    ZStack {
      Color.black.opacity(0.18)
        .ignoresSafeArea()
      VStack(spacing: 10) {
        Text("game.countdown.ready")
          .font(.headline.weight(.heavy))
          .foregroundStyle(AppPalette.ink)
        Text(verbatim: String(value))
          .font(.system(size: 76, weight: .black, design: .rounded))
          .foregroundStyle(AppPalette.accent)
      }
      .padding(.horizontal, 38)
      .padding(.vertical, 24)
      .background(.ultraThickMaterial, in: RoundedRectangle(cornerRadius: 30))
      .shadow(color: AppPalette.keyShadow, radius: 18, y: 10)
    }
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(Text("game.countdown.ready"))
    .accessibilityValue(Text(verbatim: String(value)))
    .accessibilityIdentifier("game.countdown")
  }

  private func persistFinishedGame() {
    guard !didPersistCurrentRun else { return }
    didPersistCurrentRun = true
    let result = viewModel.result
    let outcome = gameProgress.append(
      GameRecord(
        id: UUID(),
        mode: .game,
        deckId: deck.deckId,
        deckVersion: deck.version,
        competition: resolvedCompetition,
        course: gameKind == .acidRain ? gameKind.rawValue : course.rawValue,
        score: result.score,
        maxCombo: result.maxCombo,
        accuracy: result.accuracyPercent,
        charactersPerMinute: result.charactersPerMinute,
        activeDuration: result.activeDuration,
        completedItemCount: result.completedItemCount,
        missedItemCount: result.missedCardCount,
        inputMode: recordInputMode,
        playedAt: Date()
      )
    )
    recordOutcome = outcome
    if outcome?.isNewBest == true {
      companion.publish(.newBest)
    }
    retention.record(.game, session: retentionSession)
    captureAnalyticsCompletionIfNeeded(result)
  }

  private var analyticsPresentation: GameResultPresentation {
    competition == .weeklyPiyoCup
      ? .piyoCup
      : (gameKind == .acidRain ? .acidRain : .flow)
  }

  private var analyticsDeckSource: String {
    if analyticsPresentation == .piyoCup || analyticsPresentation.presetLevel(for: deck) != nil {
      return "bundled"
    }
    switch deckLibrary.records[deck.deckId]?.source {
    case .bundle: return "bundled"
    case .remote: return "catalog"
    case .imported: return "imported"
    case .created: return "created"
    case nil: return "unknown"
    }
  }

  private func captureAnalyticsStartIfNeeded() {
    guard !didCaptureAnalyticsStart else { return }
    didCaptureAnalyticsStart = true
    TelemetryService.shared.capture(
      .sessionStarted,
      properties: [
        .sessionKind: "game",
        .deckSource: analyticsDeckSource,
        .inputMode: recordInputMode.rawValue,
        .gameMode: analyticsPresentation.analyticsValue,
        .difficulty: analyticsPresentation.analyticsDifficulty(for: deck),
      ]
    )
    TelemetryService.shared.setCrashContext(
      feature: "game",
      sessionKind: "game",
      inputMode: recordInputMode.rawValue,
      gameMode: analyticsPresentation.analyticsValue
    )
  }

  private func captureAnalyticsCompletionIfNeeded(_ result: FlowGameResult) {
    guard !didCaptureAnalyticsCompletion else { return }
    didCaptureAnalyticsCompletion = true
    TelemetryService.shared.capture(
      .sessionCompleted,
      properties: [
        .sessionKind: "game",
        .result: "completed",
        .durationBucket: TelemetryService.shared.durationBucket(result.activeDuration),
        .itemCountBucket: TelemetryService.shared.itemCountBucket(result.completedItemCount),
        .deckSource: analyticsDeckSource,
        .inputMode: recordInputMode.rawValue,
        .gameMode: analyticsPresentation.analyticsValue,
        .difficulty: analyticsPresentation.analyticsDifficulty(for: deck),
      ]
    )
  }

  private func captureAnalyticsAbandonmentIfNeeded() {
    guard didCaptureAnalyticsStart, !didCaptureAnalyticsCompletion,
      !didCaptureAnalyticsAbandonment
    else { return }
    didCaptureAnalyticsAbandonment = true
    TelemetryService.shared.capture(
      .sessionAbandoned,
      properties: [
        .sessionKind: "game",
        .reason: "user_closed",
        .durationBucket: TelemetryService.shared.durationBucket(viewModel.result.activeDuration),
        .deckSource: analyticsDeckSource,
        .inputMode: recordInputMode.rawValue,
        .gameMode: analyticsPresentation.analyticsValue,
        .difficulty: analyticsPresentation.analyticsDifficulty(for: deck),
      ]
    )
  }

  private func finishFromResult() {
    guard showsResult, !exitsAfterResultDismiss else { return }
    exitsAfterResultDismiss = true
    showsResult = false
  }

  private func markBuiltInInputUsed() {
    hasUsedBuiltInInput = true
    recordInputMode = builtInKeyboardLayout.gameRecordInputMode
  }

  private var resolvedRecordInputMode: SessionInputMode {
    inputMode == .osIME ? .osIME : builtInKeyboardLayout.gameRecordInputMode
  }

  private var korean10KeyPendingDisplay: String? {
    guard inputMode == .builtIn, builtInKeyboardLayout == .korean10Key else { return nil }
    return korean10KeyInterpreter.pendingDisplay
  }

  private func resolveInitialInputModeIfNeeded() {
    guard !didResolveInputMode else { return }
    didResolveInputMode = true
    if competition != nil {
      inputMode = .builtIn
      recordInputMode = .builtIn
      return
    }
    if SessionInputMode(rawValue: inputModeDefault) == .osIME,
      KoreanKeyboardAvailability.isAvailable
    {
      inputMode = .osIME
      recordInputMode = .osIME
    } else {
      inputMode = .builtIn
      recordInputMode = builtInKeyboardLayout.gameRecordInputMode
      if SessionInputMode(rawValue: inputModeDefault) == .osIME {
        showsOSIMEUnavailable = true
      }
    }
  }

  private var resolvedCompetition: GameCompetition? {
    if competition == .weeklyPiyoCup {
      return .weeklyPiyoCup
    }
    guard gameKind == .flow, recordInputMode == .builtIn,
      GameCenterRankedDeck.isEligible(deckID: deck.deckId, version: deck.version)
    else { return nil }
    return .officialDeck
  }

  private func persistReviewResolution() {
    guard let resolution = viewModel.lastCompletedItem,
      sessionItems.indices.contains(resolution.itemIndex)
    else { return }

    let item = sessionItems[resolution.itemIndex]
    if resolution.hadMistake {
      reviewDeck.recordMistake(item: item, sourceDeckId: deck.deckId)
      collectSessionReviewItem(item: item, resolution: resolution)
    } else {
      if reviewDeck.recordPerfect(itemId: item.id, sourceDeckId: deck.deckId) == .graduated {
        companion.publish(.reviewGraduated)
      }
    }
    reviewDeck.flush()
  }

  private func collectSessionReviewItem(
    item: DeckItem,
    resolution: SessionItemResolution
  ) {
    let id = ReviewDeckItem.id(itemId: item.id, sourceDeckId: deck.deckId)
    if let index = sessionReviewItems.firstIndex(where: { $0.id == id }) {
      sessionReviewItems[index].merge(resolution)
    } else {
      sessionReviewItems.append(
        SessionReviewItem(
          item: item,
          sourceDeckId: deck.deckId,
          resolution: resolution
        )
      )
    }
  }

  private func playKeySound(_ role: TypingSoundKeyRole) {
    guard soundEffectsEnabled else { return }
    HancoTypingSoundFeedback.play(
      TypingSoundPreset.resolved(from: typingSoundPreset),
      role: role
    )
  }

  private func playFeedbackSound() {
    guard soundEffectsEnabled else { return }
    switch viewModel.feedback {
    case .completed:
      HancoSoundEngine.shared.play(.completion(combo: viewModel.combo))
    case .incorrect:
      HancoSoundEngine.shared.play(.mistake)
    case .escaped:
      HancoSoundEngine.shared.play(.lifeLost)
    case .ready, .correct:
      break
    }
  }

  private func triggerLifeLossFeedback() {
    FlowGameHaptics.fireLifeLost(enabled: hapticsEnabled)
    UIAccessibility.post(
      notification: .announcement,
      argument: AppLocalization.string("game.feedback.escaped")
    )

    lifeLossFeedbackTask?.cancel()
    lifeLossFlashOpacity = 1
    withAnimation(.linear(duration: 0.34)) {
      lifeLossShakeStep += 1
    }
    lifeLossFeedbackTask = Task { @MainActor in
      try? await Task.sleep(nanoseconds: 140_000_000)
      guard !Task.isCancelled else { return }
      withAnimation(.easeOut(duration: 0.42)) {
        lifeLossFlashOpacity = 0
      }
    }
  }

  @ViewBuilder
  private var lifeLossOverlay: some View {
    if lifeLossFlashOpacity > 0 {
      ZStack {
        AppPalette.error
          .opacity(0.16 * lifeLossFlashOpacity)
          .ignoresSafeArea()

        Label("game.feedback.escaped", systemImage: "heart.slash.fill")
          .font(.headline.weight(.black))
          .foregroundStyle(Color.white)
          .padding(.horizontal, 18)
          .padding(.vertical, 11)
          .background(AppPalette.error.opacity(0.94), in: Capsule())
          .shadow(color: AppPalette.error.opacity(0.35), radius: 12, y: 5)
          .scaleEffect(0.96 + CGFloat(lifeLossFlashOpacity) * 0.08)
          .accessibilityHidden(true)
      }
      .allowsHitTesting(false)
      .transition(.opacity)
      .accessibilityIdentifier("game.life_loss.feedback")
    }
  }
}

@MainActor
private enum FlowGameHaptics {
  private static let notificationGenerator = UINotificationFeedbackGenerator()
  private static let impactGenerator = UIImpactFeedbackGenerator(style: .rigid)

  static func fireLifeLost(enabled: Bool) {
    guard enabled else { return }
    notificationGenerator.notificationOccurred(.error)
    impactGenerator.impactOccurred(intensity: 1)
    notificationGenerator.prepare()
    impactGenerator.prepare()
  }
}

enum FlowLaneLayout {
  static func horizontalOffset(
    progress: Double,
    containerWidth: CGFloat,
    cardWidth: CGFloat
  ) -> CGFloat {
    let clampedProgress = CGFloat(min(max(progress, 0), 1))
    return containerWidth - (containerWidth + cardWidth) * clampedProgress
  }
}

enum MovingWordCardLayout {
  static func flowWidth(
    korean: String,
    meaning: String,
    reading: String,
    fontScale: CGFloat,
    maximumWidth: CGFloat
  ) -> CGFloat {
    preferredWidth(
      korean: korean,
      meaning: meaning,
      reading: reading,
      koreanPointSize: 25 * fontScale,
      meaningPointSize: 12,
      readingPointSize: 11,
      horizontalPadding: 28,
      minimumWidth: 96,
      maximumWidth: maximumWidth
    )
  }

  static func acidRainWidth(
    korean: String,
    meaning: String,
    reading: String,
    fontScale: CGFloat,
    maximumWidth: CGFloat
  ) -> CGFloat {
    preferredWidth(
      korean: korean,
      meaning: meaning,
      reading: reading,
      koreanPointSize: 21 * fontScale,
      meaningPointSize: 11,
      readingPointSize: 10,
      horizontalPadding: 24,
      minimumWidth: 92,
      maximumWidth: maximumWidth
    )
  }

  private static func preferredWidth(
    korean: String,
    meaning: String,
    reading: String,
    koreanPointSize: CGFloat,
    meaningPointSize: CGFloat,
    readingPointSize: CGFloat,
    horizontalPadding: CGFloat,
    minimumWidth: CGFloat,
    maximumWidth: CGFloat
  ) -> CGFloat {
    let contentWidth = max(
      estimatedWidth(korean, pointSize: koreanPointSize),
      estimatedWidth(meaning, pointSize: meaningPointSize),
      estimatedWidth("[\(reading)]", pointSize: readingPointSize)
    )
    return min(maximumWidth, max(minimumWidth, ceil(contentWidth + horizontalPadding)))
  }

  private static func estimatedWidth(_ text: String, pointSize: CGFloat) -> CGFloat {
    text.reduce(CGFloat.zero) { width, character in
      let isASCII = character.unicodeScalars.allSatisfy(\.isASCII)
      let unit: CGFloat
      if character.isWhitespace {
        unit = 0.35
      } else if isASCII {
        unit = 0.58
      } else {
        unit = 1
      }
      return width + pointSize * unit
    }
  }
}

enum FlowGameMascotLayout {
  // Track the moving card with the eyes while preserving the same body ratio
  // and deck-resolved outfit used at the start of the game.
  static let pose = MascotPose.front
}

enum CompactGameHUDMetricLayout {
  static let iconColumnWidth: CGFloat = 12
  static let valueHorizontalInset: CGFloat = 14
  static let minimumValueScale: CGFloat = 0.55

  static func valuePointSize(for value: String) -> CGFloat {
    switch value.count {
    case 0...3: 15
    case 4...5: 14
    case 6...7: 12
    default: 10
    }
  }
}

struct CompactGameHUDMetric: View {
  let systemImage: String
  let value: String
  let label: LocalizedStringKey
  let identifier: String
  let tint: Color
  let contentWidth: CGFloat

  var body: some View {
    ZStack(alignment: .leading) {
      Image(systemName: systemImage)
        .font(.caption2.weight(.bold))
        .foregroundStyle(tint)
        .frame(width: CompactGameHUDMetricLayout.iconColumnWidth)

      VStack(spacing: 0) {
        Text(verbatim: value)
          .font(
            .system(
              size: CompactGameHUDMetricLayout.valuePointSize(for: value),
              weight: .heavy,
              design: .rounded
            )
          )
          .monospacedDigit()
          .foregroundStyle(AppPalette.ink)
          .lineLimit(1)
          .minimumScaleFactor(CompactGameHUDMetricLayout.minimumValueScale)
          .allowsTightening(true)
          .frame(maxWidth: .infinity, alignment: .center)
          .accessibilityIdentifier(identifier)
        Text(label)
          .font(.system(size: 9, weight: .medium))
          .foregroundStyle(AppPalette.mutedInk)
          .lineLimit(1)
          .minimumScaleFactor(0.6)
          .allowsTightening(true)
          .frame(maxWidth: .infinity, alignment: .center)
      }
      .padding(.horizontal, CompactGameHUDMetricLayout.valueHorizontalInset)
    }
    .frame(width: contentWidth)
    .padding(.horizontal, 5)
    .padding(.vertical, 4)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 10))
    .overlay {
      RoundedRectangle(cornerRadius: 12)
        .stroke(AppPalette.secondary.opacity(0.24), lineWidth: 0.8)
    }
  }
}

enum AcidRainLaneLayout {
  static let dangerZoneHeight: CGFloat = 52
  private static let horizontalInset: CGFloat = 12
  private static let estimatedCardHeight: CGFloat = 90
  private static let cardTopClearance: CGFloat = 48
  private static let laneFractions: [CGFloat] = [0.08, 0.5, 0.92]

  static func cardWidth(containerWidth: CGFloat) -> CGFloat {
    min(188, containerWidth * 0.56)
  }

  static func cardCenterX(
    itemIndex: Int,
    containerWidth: CGFloat,
    cardWidth: CGFloat
  ) -> CGFloat {
    let horizontalRoom = max(0, containerWidth - cardWidth - horizontalInset * 2)
    let fraction = laneFractions[itemIndex % laneFractions.count]
    return cardWidth / 2 + horizontalInset + horizontalRoom * fraction
  }

  static func cardCenterY(progress: Double, containerHeight: CGFloat) -> CGFloat {
    let halfCardHeight = estimatedCardHeight / 2
    let startY = cardTopClearance + halfCardHeight
    let endY = max(startY, containerHeight - dangerZoneHeight - halfCardHeight)
    let clampedProgress = CGFloat(min(max(progress, 0), 1))
    return startY + (endY - startY) * clampedProgress
  }
}

private struct FlowJamoProgressTrack: View {
  private static let chipWidth: CGFloat = 24
  private static let chipSpacing: CGFloat = 5
  private static let horizontalSafeInset: CGFloat = 4

  let sequence: [Character]
  let completedCount: Int

  private var activeIndex: Int? {
    guard !sequence.isEmpty else { return nil }
    return min(completedCount, sequence.count - 1)
  }

  var body: some View {
    GeometryReader { geometry in
      ScrollViewReader { scrollProxy in
        ScrollView(.horizontal, showsIndicators: false) {
          HStack(spacing: Self.chipSpacing) {
            ForEach(Array(sequence.enumerated()), id: \.offset) { index, jamo in
              Text(verbatim: String(jamo))
                .font(.system(.caption, design: .rounded, weight: .bold))
                .foregroundStyle(index < completedCount ? .white : AppPalette.ink)
                .frame(width: Self.chipWidth, height: 28)
                .background(
                  index < completedCount
                    ? AppPalette.accent : AppPalette.accentSoft.opacity(0.42),
                  in: RoundedRectangle(cornerRadius: 8)
                )
                .overlay {
                  if index == completedCount {
                    RoundedRectangle(cornerRadius: 8)
                      .strokeBorder(AppPalette.accent, lineWidth: 2)
                  }
                }
                .id(index)
                .accessibilityIdentifier(
                  index == completedCount ? "game.jamo.active" : "game.jamo.\(index)"
                )
            }
          }
          .padding(.horizontal, Self.horizontalSafeInset)
          .frame(minWidth: geometry.size.width, alignment: .center)
        }
        .onAppear {
          followActiveJamo(using: scrollProxy, animated: false)
        }
        .onChange(of: completedCount) { _ in
          followActiveJamo(using: scrollProxy, animated: true)
        }
      }
    }
    .frame(maxWidth: .infinity)
    .frame(height: 28)
    .clipped()
    .accessibilityIdentifier("game.jamo.track")
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

private struct FlowSimpleBackdrop: View {
  var body: some View {
    ZStack(alignment: .bottom) {
      AppPalette.card

      Rectangle()
        .fill(AppPalette.mutedInk.opacity(0.06))
        .frame(height: 38)

      HStack(spacing: 0) {
        ForEach(0..<11, id: \.self) { _ in
          Circle()
            .fill(AppPalette.mutedInk.opacity(0.12))
            .frame(width: 14, height: 14)
            .frame(maxWidth: .infinity)
        }
      }
      .padding(.horizontal, 24)
      .padding(.bottom, 12)
    }
    .accessibilityHidden(true)
  }
}

private struct ComboParticleCanvas: View {
  let revision: Int
  let tier: Int
  @State private var startedAt = Date.distantPast

  var body: some View {
    TimelineView(.animation(minimumInterval: 1.0 / 60.0)) { timeline in
      let progress = min(max(timeline.date.timeIntervalSince(startedAt) / 0.52, 0), 1)
      Canvas { context, size in
        guard revision > 0, progress < 1 else { return }
        let center = CGPoint(x: size.width * 0.5, y: size.height * 0.5)
        let count = tier >= 2 ? 16 : 10
        for index in 0..<count {
          let angle = Double(index) / Double(count) * Double.pi * 2 - Double.pi / 2
          let radius = 20 + progress * Double(tier >= 3 ? 120 : 82)
          let point = CGPoint(
            x: center.x + CGFloat(cos(angle) * radius),
            y: center.y + CGFloat(sin(angle) * radius)
          )
          var particle = context
          particle.opacity = 1 - progress
          let image = particle.resolve(
            Image(systemName: index.isMultiple(of: 2) ? "heart.fill" : "star.fill")
          )
          particle.draw(image, at: point)
        }
      }
    }
    .onChange(of: revision) { _ in startedAt = Date() }
  }
}

private struct FlowCardShakeEffect: GeometryEffect {
  var animatableData: CGFloat

  func effectValue(size: CGSize) -> ProjectionTransform {
    ProjectionTransform(
      CGAffineTransform(translationX: 7 * sin(animatableData * .pi * 3), y: 0)
    )
  }
}

private struct FlowLifeLossShakeEffect: GeometryEffect {
  var animatableData: CGFloat

  func effectValue(size: CGSize) -> ProjectionTransform {
    ProjectionTransform(
      CGAffineTransform(translationX: 6 * sin(animatableData * .pi * 6), y: 0)
    )
  }
}
