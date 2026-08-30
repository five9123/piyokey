import DeckKit
import HangulEngine
import SwiftUI
import UIKit

struct ChoseongQuizRound: Equatable, Identifiable {
  let answer: DeckItem
  let initials: String
  let options: [DeckItem]

  var id: String { answer.id }
}

enum ChoseongExtractor {
  private static let initials = Array("ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ")

  static func extract(from text: String) -> String {
    var result = ""
    var lastWasSpace = false
    for character in text {
      guard let scalar = character.unicodeScalars.first,
        character.unicodeScalars.count == 1
      else { continue }
      let value = Int(scalar.value)
      if (0xAC00...0xD7A3).contains(value) {
        let index = (value - 0xAC00) / 588
        result.append(initials[index])
        lastWasSpace = false
      } else if character.isWhitespace, !result.isEmpty, !lastWasSpace {
        result.append(" ")
        lastWasSpace = true
      }
    }
    return result.trimmingCharacters(in: .whitespaces)
  }
}

struct ChoseongInitialProgressUnit: Equatable, Identifiable {
  enum State: Equatable {
    case pending
    case active
    case completed
  }

  let offset: Int
  let character: Character
  let state: State

  var id: Int { offset }
  var isSeparator: Bool { character.isWhitespace }
}

enum ChoseongInitialProgressBuilder {
  static func units(answer: String, completedJamoCount: Int) -> [ChoseongInitialProgressUnit] {
    var units: [ChoseongInitialProgressUnit] = []
    var jamoOffset = 0
    var lastWasSpace = false

    for character in answer {
      let keyCount = (try? JamoDecomposer.keySequence(for: String(character)))?.count ?? 0
      let extracted = ChoseongExtractor.extract(from: String(character))

      if let initial = extracted.first {
        let upperBound = jamoOffset + keyCount
        let state: ChoseongInitialProgressUnit.State
        if completedJamoCount >= upperBound {
          state = .completed
        } else if completedJamoCount >= jamoOffset {
          state = .active
        } else {
          state = .pending
        }
        units.append(
          ChoseongInitialProgressUnit(
            offset: units.count,
            character: initial,
            state: state
          )
        )
        lastWasSpace = false
      } else if character.isWhitespace, !units.isEmpty, !lastWasSpace {
        units.append(
          ChoseongInitialProgressUnit(
            offset: units.count,
            character: " ",
            state: .pending
          )
        )
        lastWasSpace = true
      }

      jamoOffset += keyCount
    }

    while units.last?.isSeparator == true {
      units.removeLast()
    }
    return units
  }
}

enum ChoseongQuizBuilder {
  static func seed(for value: String) -> UInt64 {
    value.utf8.reduce(1_469_598_103_934_665_603) { partial, byte in
      (partial ^ UInt64(byte)) &* 1_099_511_628_211
    }
  }

  static func rounds(
    items: [DeckItem],
    limit: Int = 10,
    seed: UInt64
  ) -> [ChoseongQuizRound] {
    var seenWords: Set<String> = []
    var uniqueItems = items.filter { item in
      !ChoseongExtractor.extract(from: item.ko).isEmpty && seenWords.insert(item.ko).inserted
    }
    guard uniqueItems.count >= 4 else { return [] }

    var generator = ChoseongRandomNumberGenerator(seed: seed)
    uniqueItems.shuffle(using: &generator)
    let eligibleAnswers = uniqueItems.filter { answer in
      let answerInitials = ChoseongExtractor.extract(from: answer.ko)
      return uniqueItems.filter {
        $0.id != answer.id && ChoseongExtractor.extract(from: $0.ko) != answerInitials
      }.count >= 3
    }
    let answers = Array(eligibleAnswers.prefix(max(1, min(limit, eligibleAnswers.count))))

    return answers.map { answer in
      let answerInitials = ChoseongExtractor.extract(from: answer.ko)
      var candidates = uniqueItems.filter {
        $0.id != answer.id && ChoseongExtractor.extract(from: $0.ko) != answerInitials
      }
      candidates.shuffle(using: &generator)
      var options = [answer] + Array(candidates.prefix(3))
      options.shuffle(using: &generator)
      return ChoseongQuizRound(answer: answer, initials: answerInitials, options: options)
    }
  }
}

enum WordMatchQuizBuilder {
  static func rounds(
    items: [DeckItem],
    limit: Int = 10,
    seed: UInt64
  ) -> [ChoseongQuizRound] {
    var seenWords: Set<String> = []
    let eligible = items.filter { item in
      guard let meaning = item.appMeaning else { return false }
      return !item.ko.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        && !meaning.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        && seenWords.insert(item.ko).inserted
    }
    guard eligible.count >= 4 else { return [] }

    var generator = ChoseongRandomNumberGenerator(seed: seed ^ 0xA11C_E5EED)
    var shuffledAnswers = eligible
    shuffledAnswers.shuffle(using: &generator)
    let answers = Array(shuffledAnswers.prefix(max(1, min(limit, shuffledAnswers.count))))
    return answers.map { answer in
      let answerIndex = eligible.firstIndex { $0.id == answer.id } ?? 0
      let groupStart = (answerIndex / 5) * 5
      let groupEnd = min(groupStart + 5, eligible.count)
      var candidates = Array(eligible[groupStart..<groupEnd]).filter { $0.id != answer.id }
      candidates.shuffle(using: &generator)

      if candidates.count < 3 {
        let candidateIDs = Set(candidates.map(\.id)).union([answer.id])
        var fallback = eligible.filter { !candidateIDs.contains($0.id) }
        fallback.shuffle(using: &generator)
        candidates.append(contentsOf: fallback.prefix(3 - candidates.count))
      }
      var options = [answer] + Array(candidates.prefix(3))
      options.shuffle(using: &generator)
      return ChoseongQuizRound(
        answer: answer,
        initials: answer.appMeaning ?? "",
        options: options
      )
    }
  }
}

enum ChoiceQuizMode: Equatable {
  case choseong
  case wordMatch

  var gameKind: GameKind {
    switch self {
    case .choseong: .choseong
    case .wordMatch: .wordMatch
    }
  }
}

struct ChoseongRandomNumberGenerator: RandomNumberGenerator {
  private var state: UInt64

  init(seed: UInt64) {
    state = seed == 0 ? 0x9E37_79B9_7F4A_7C15 : seed
  }

  mutating func next() -> UInt64 {
    state ^= state << 13
    state ^= state >> 7
    state ^= state << 17
    return state
  }
}

struct ChoseongQuizAnswerOutcome: Equatable {
  let answer: DeckItem
  let isCorrect: Bool
  let points: Int
  let usedHint: Bool
}

@MainActor
final class ChoseongQuizViewModel: ObservableObject {
  enum Phase: Equatable {
    case ready
    case running
    case paused
    case finished
  }

  enum Feedback: Equatable {
    case correct(points: Int)
    case incorrect(answerID: String)
  }

  private(set) var rounds: [ChoseongQuizRound]

  @Published private(set) var phase: Phase = .ready
  @Published private(set) var currentIndex = 0
  @Published private(set) var score = 0
  @Published private(set) var combo = 0
  @Published private(set) var maxCombo = 0
  @Published private(set) var correctCount = 0
  @Published private(set) var incorrectCount = 0
  @Published private(set) var selectedOptionID: String?
  @Published private(set) var feedback: Feedback?
  @Published private(set) var feedbackRevision = 0
  @Published private(set) var isHintVisible = false

  private var activeElapsed: TimeInterval = 0
  private var questionStartedAt: TimeInterval = 0
  private var lastActiveDate: Date?

  init(rounds: [ChoseongQuizRound]) {
    precondition(!rounds.isEmpty, "Choseong quiz requires at least one round")
    precondition(rounds.allSatisfy { $0.options.count >= 2 })
    self.rounds = rounds
  }

  convenience init(items: [DeckItem], limit: Int = 10, seed: UInt64) {
    self.init(rounds: ChoseongQuizBuilder.rounds(items: items, limit: limit, seed: seed))
  }

  var currentRound: ChoseongQuizRound { rounds[currentIndex] }
  var questionNumber: Int { currentIndex + 1 }
  var answeredCount: Int { correctCount + incorrectCount }
  var accuracyPercent: Double {
    guard answeredCount > 0 else { return 0 }
    return Double(correctCount) / Double(answeredCount) * 100
  }
  var questionsPerMinute: Double {
    guard activeElapsed > 0 else { return 0 }
    return Double(answeredCount) / (activeElapsed / 60)
  }
  var result: FlowGameResult {
    FlowGameResult(
      score: score,
      maxCombo: maxCombo,
      accuracyPercent: accuracyPercent,
      charactersPerMinute: questionsPerMinute,
      activeDuration: activeElapsed,
      completedItemCount: correctCount,
      missedCardCount: incorrectCount,
      rank: rank
    )
  }

  func start(at date: Date = Date()) {
    guard phase == .ready else { return }
    phase = .running
    lastActiveDate = date
    questionStartedAt = activeElapsed
  }

  func useHint() {
    guard phase == .running, feedback == nil else { return }
    isHintVisible = true
  }

  @discardableResult
  func select(optionID: String, at date: Date = Date()) -> ChoseongQuizAnswerOutcome? {
    guard phase == .running, feedback == nil,
      currentRound.options.contains(where: { $0.id == optionID })
    else { return nil }

    consumeActiveTime(until: date)
    lastActiveDate = nil
    selectedOptionID = optionID
    let isCorrect = optionID == currentRound.answer.id
    let responseDuration = max(0, activeElapsed - questionStartedAt)
    let points: Int
    if isCorrect {
      combo += 1
      maxCombo = max(maxCombo, combo)
      correctCount += 1
      let speedBonus = max(0, Int((5 - responseDuration) * 10))
      let comboBonus = min(combo, 10) * 10
      points = 100 + speedBonus + comboBonus - (isHintVisible ? 30 : 0)
      score += max(50, points)
      feedback = .correct(points: max(50, points))
    } else {
      combo = 0
      incorrectCount += 1
      points = 0
      feedback = .incorrect(answerID: currentRound.answer.id)
    }
    feedbackRevision &+= 1
    return ChoseongQuizAnswerOutcome(
      answer: currentRound.answer,
      isCorrect: isCorrect,
      points: max(0, points),
      usedHint: isHintVisible
    )
  }

  func advance(at date: Date = Date()) {
    guard phase == .running, feedback != nil else { return }
    if currentIndex + 1 >= rounds.count {
      phase = .finished
      return
    }
    currentIndex += 1
    selectedOptionID = nil
    feedback = nil
    isHintVisible = false
    questionStartedAt = activeElapsed
    lastActiveDate = date
  }

  func pause(at date: Date = Date()) {
    guard phase == .running else { return }
    consumeActiveTime(until: date)
    lastActiveDate = nil
    phase = .paused
  }

  func resume(at date: Date = Date()) {
    guard phase == .paused else { return }
    phase = .running
    if feedback == nil { lastActiveDate = date }
  }

  func restart(
    rounds replacementRounds: [ChoseongQuizRound]? = nil,
    at date: Date = Date()
  ) {
    if let replacementRounds {
      precondition(!replacementRounds.isEmpty, "Choseong quiz requires at least one round")
      precondition(replacementRounds.allSatisfy { $0.options.count >= 2 })
      rounds = replacementRounds
    }
    currentIndex = 0
    score = 0
    combo = 0
    maxCombo = 0
    correctCount = 0
    incorrectCount = 0
    selectedOptionID = nil
    feedback = nil
    feedbackRevision = 0
    isHintVisible = false
    activeElapsed = 0
    questionStartedAt = 0
    phase = .running
    lastActiveDate = date
  }

  private var rank: String {
    switch accuracyPercent {
    case 95...: "S"
    case 80...: "A"
    case 60...: "B"
    default: "C"
    }
  }

  private func consumeActiveTime(until date: Date) {
    guard let lastActiveDate else { return }
    activeElapsed += max(0, date.timeIntervalSince(lastActiveDate))
    self.lastActiveDate = date
  }
}

struct ChoseongQuizView: View {
  @Environment(\.dismiss) private var dismiss
  @Environment(\.scenePhase) private var scenePhase
  @EnvironmentObject private var deckLibrary: DeckLibrary
  @EnvironmentObject private var discoverViewModel: DiscoverViewModel
  @EnvironmentObject private var gameProgress: GameProgressLibrary
  @EnvironmentObject private var reviewDeck: ReviewDeckLibrary
  @EnvironmentObject private var retention: RetentionLibrary
  @EnvironmentObject private var companion: MascotCompanionLibrary
  @AppStorage(KeyboardPreferenceKeys.hapticsEnabled) private var hapticsEnabled = true
  @AppStorage(SoundPreferenceKeys.effectsEnabled) private var soundEffectsEnabled = true

  let deck: Deck
  let mode: ChoiceQuizMode
  private let mascotAppearance: MascotSessionAppearance
  @StateObject private var viewModel: ChoseongQuizViewModel
  @State private var advanceTask: Task<Void, Never>?
  @State private var recordOutcome: GameRecordSaveOutcome?
  @State private var didPersistCurrentRun = false
  @State private var sessionReviewItems: [SessionReviewItem] = []
  @State private var retentionSession = RetentionSessionContext()
  @State private var showsResult = false
  @State private var exitsAfterResultDismiss = false

  init(deck: Deck, mode: ChoiceQuizMode = .choseong) {
    self.deck = deck
    self.mode = mode
    self.mascotAppearance = MascotSessionAppearance()
    let seed = GamePresetSessionRandomizer.seed(for: deck, gameKind: mode.gameKind)
    let rounds: [ChoseongQuizRound]
    switch mode {
    case .choseong:
      rounds = ChoseongQuizBuilder.rounds(items: deck.items, seed: seed)
    case .wordMatch:
      rounds = WordMatchQuizBuilder.rounds(items: deck.items, seed: seed)
    }
    _viewModel = StateObject(
      wrappedValue: ChoseongQuizViewModel(rounds: rounds)
    )
  }

  var body: some View {
    VStack(spacing: 14) {
      hud
      ProgressView(value: Double(viewModel.questionNumber), total: Double(viewModel.rounds.count))
        .tint(AppPalette.accent)
        .accessibilityIdentifier(mode == .choseong ? "choseong.progress" : "word_match.progress")
      quizCard
      optionsGrid
      feedbackLabel
        .frame(minHeight: 28)
    }
    .padding(18)
    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    .background(
      LinearGradient(
        colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
      ).ignoresSafeArea()
    )
    .navigationTitle(Text(verbatim: deck.appName))
    .navigationBarTitleDisplayMode(.inline)
    .navigationBarBackButtonHidden(true)
    .toolbar(.hidden, for: .tabBar)
    .toolbar {
      ToolbarItem(placement: .topBarLeading) {
        Button(action: dismiss.callAsFunction) { Image(systemName: "xmark") }
          .accessibilityLabel(Text("game.end"))
      }
      ToolbarItem(placement: .topBarTrailing) { quizSettings }
    }
    .navigationDestination(isPresented: $showsResult) {
      FlowGameResultView(
        deck: deck,
        result: viewModel.result,
        recordOutcome: recordOutcome,
        reviewItems: sessionReviewItems,
        recommendations: resultRecommendations,
        catalogDecks: discoverViewModel.catalog?.decks ?? [],
        presentation: mode == .choseong ? .choseong : .wordMatch,
        onRetry: retry,
        onFinish: finishFromResult
      )
    }
    .onChange(of: showsResult) { isPresented in
      guard !isPresented, exitsAfterResultDismiss else { return }
      Task { @MainActor in
        await Task.yield()
        guard exitsAfterResultDismiss else { return }
        dismiss()
      }
    }
    .onAppear {
      if viewModel.phase == .ready {
        viewModel.start()
        deckLibrary.markPlayed(deck.deckId)
      } else if viewModel.phase == .paused, scenePhase == .active {
        viewModel.resume()
      }
    }
    .onDisappear {
      advanceTask?.cancel()
      advanceTask = nil
      reviewDeck.flush()
    }
    .onChange(of: scenePhase) { phase in
      switch phase {
      case .active:
        viewModel.resume()
        if viewModel.feedback != nil { scheduleAdvance() }
      case .inactive, .background:
        advanceTask?.cancel()
        advanceTask = nil
        viewModel.pause()
      @unknown default:
        viewModel.pause()
      }
    }
    .onChange(of: viewModel.phase) { phase in
      if phase == .finished {
        persistFinishedGame()
        showsResult = true
      }
    }
    .overlay(alignment: .topLeading) {
      Text(playAccessibilityKey)
        .font(.system(size: 1))
        .opacity(0.01)
        .accessibilityIdentifier(
          mode == .choseong ? "choseong.play.screen" : "word_match.play.screen"
        )
    }
    .sessionExitCovered(exitsAfterResultDismiss)
  }

  private var hud: some View {
    HStack(spacing: 8) {
      metric(
        image: "number.circle.fill",
        value: "\(viewModel.questionNumber)/\(viewModel.rounds.count)",
        label: mode == .choseong ? "choseong.question" : "word_match.question",
        identifier: mode == .choseong ? "choseong.question.value" : "word_match.question.value"
      )
      metric(
        image: "star.fill",
        value: viewModel.score.formatted(),
        label: "game.score",
        identifier: mode == .choseong ? "choseong.score.value" : "word_match.score.value"
      )
      metric(
        image: "flame.fill",
        value: String(viewModel.combo),
        label: "game.combo",
        identifier: mode == .choseong ? "choseong.combo.value" : "word_match.combo.value"
      )
    }
  }

  private var quizCard: some View {
    VStack(spacing: 14) {
      Text(promptTitleKey)
        .font(.caption.weight(.bold))
        .foregroundStyle(AppPalette.secondary)
      Text(verbatim: viewModel.currentRound.initials)
        .font(
          .system(
            size: mode == .choseong ? 54 : 34,
            weight: .black,
            design: .rounded
          )
        )
        .foregroundStyle(AppPalette.ink)
        .minimumScaleFactor(0.55)
        .lineLimit(mode == .choseong ? 1 : 2)
        .multilineTextAlignment(.center)
        .accessibilityIdentifier(
          mode == .choseong ? "choseong.initials.value" : "word_match.prompt.value"
        )
      HStack(spacing: 14) {
        GrowingMascotView(
          mood: viewModel.feedback == nil ? .focus : mascotMood,
          reaction: viewModel.feedback == nil ? .none : mascotReaction,
          reactionRevision: viewModel.feedbackRevision,
          automaticProp: mascotAppearance.automaticProp,
          size: 48,
          calm: true
        )
        .frame(width: 72, height: 88)

        if mode == .wordMatch {
          Label("word_match.prompt.guide", systemImage: "character.book.closed.fill")
            .font(.caption.weight(.bold))
            .foregroundStyle(AppPalette.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
        } else if viewModel.isHintVisible,
          let meaning = viewModel.currentRound.answer.appMeaning,
          !meaning.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        {
          VStack(alignment: .leading, spacing: 4) {
            Label("choseong.hint.used", systemImage: "lightbulb.fill")
              .font(.caption.weight(.bold))
              .foregroundStyle(Color.orange)
            Text(verbatim: meaning)
              .font(.headline.weight(.bold))
              .foregroundStyle(AppPalette.ink)
          }
          .frame(maxWidth: .infinity, alignment: .leading)
          .accessibilityIdentifier("choseong.hint.value")
        } else if viewModel.currentRound.answer.appMeaning?
          .trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
        {
          Button {
            viewModel.useHint()
          } label: {
            Label("choseong.hint.show", systemImage: "lightbulb")
              .font(.headline.weight(.bold))
              .foregroundStyle(AppPalette.accent)
              .frame(maxWidth: .infinity)
              .padding(.vertical, 12)
              .background(AppPalette.accentSoft.opacity(0.48), in: Capsule())
          }
          .disabled(viewModel.feedback != nil)
          .accessibilityIdentifier("choseong.hint.show")
        }
      }
    }
    .frame(maxWidth: .infinity)
    .padding(20)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 12, y: 7)
  }

  private var optionsGrid: some View {
    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
      ForEach(viewModel.currentRound.options, id: \.id) { option in
        Button {
          answer(option.id)
        } label: {
          Text(verbatim: option.ko)
            .font(.system(.headline, design: .rounded, weight: .heavy))
            .foregroundStyle(optionForeground(option.id))
            .frame(maxWidth: .infinity, minHeight: 58)
            .padding(.horizontal, 8)
            .background(optionBackground(option.id), in: RoundedRectangle(cornerRadius: 18))
            .overlay {
              RoundedRectangle(cornerRadius: 18)
                .stroke(optionBorder(option.id), lineWidth: 2)
            }
        }
        .buttonStyle(.plain)
        .disabled(viewModel.feedback != nil)
        .accessibilityIdentifier(
          option.id == viewModel.currentRound.answer.id
            ? (mode == .choseong ? "choseong.option.correct" : "word_match.option.correct")
            : (mode == .choseong ? "choseong.option.\(option.id)" : "word_match.option.\(option.id)")
        )
      }
    }
  }

  @ViewBuilder
  private var feedbackLabel: some View {
    switch viewModel.feedback {
    case .correct(let points):
      Text(
        AppLocalization.format(
            mode == .choseong
              ? "choseong.feedback.correct" : "word_match.feedback.correct"
          ,
          points
        )
      )
        .font(.headline.weight(.bold))
        .foregroundStyle(AppPalette.success)
    case .incorrect:
      Text(
        AppLocalization.format(
            mode == .choseong
              ? "choseong.feedback.incorrect" : "word_match.feedback.incorrect"
          ,
          viewModel.currentRound.answer.ko
        )
      )
      .font(.headline.weight(.bold))
      .foregroundStyle(AppPalette.error)
    case nil:
      Text(readyFeedbackKey)
        .font(.caption.weight(.semibold))
        .foregroundStyle(AppPalette.mutedInk)
    }
  }

  private var quizSettings: some View {
    Menu {
      Toggle(isOn: $hapticsEnabled) {
        Label("practice.setup.haptics", systemImage: "hand.tap.fill")
      }
      Toggle(isOn: $soundEffectsEnabled) {
        Label(
          "practice.setup.sound",
          systemImage: soundEffectsEnabled ? "speaker.wave.2.fill" : "speaker.slash.fill"
        )
      }
    } label: {
      Image(systemName: "gearshape.fill")
        .font(.headline.weight(.bold))
        .foregroundStyle(AppPalette.accent)
    }
    .accessibilityLabel(Text("practice.session_settings"))
    .accessibilityIdentifier(
      mode == .choseong ? "choseong.session_settings" : "word_match.session_settings"
    )
  }

  private func metric(
    image: String,
    value: String,
    label: LocalizedStringKey,
    identifier: String
  ) -> some View {
    HStack(spacing: 5) {
      Image(systemName: image)
        .foregroundStyle(AppPalette.accent)
        .fixedSize()
      VStack(alignment: .leading, spacing: 1) {
        Text(verbatim: value)
          .font(.headline.monospacedDigit().weight(.heavy))
          .foregroundStyle(AppPalette.ink)
          .lineLimit(1)
          .minimumScaleFactor(CompactGameHUDMetricLayout.minimumValueScale)
          .allowsTightening(true)
          .frame(maxWidth: .infinity, alignment: .leading)
          .accessibilityIdentifier(identifier)
        Text(label)
          .font(.caption2)
          .foregroundStyle(AppPalette.mutedInk)
          .lineLimit(1)
          .minimumScaleFactor(0.6)
      }
      .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(.horizontal, 9)
    .padding(.vertical, 8)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 15))
  }

  private func answer(_ optionID: String) {
    guard let outcome = viewModel.select(optionID: optionID) else { return }
    if outcome.isCorrect {
      if reviewDeck.recordPerfect(itemId: outcome.answer.id, sourceDeckId: deck.deckId)
        == .graduated
      {
        companion.publish(.reviewGraduated)
      }
      if mode == .choseong {
        companion.recordChoseongSolved(usedPass: outcome.usedHint)
      } else {
        companion.publish(.eureka)
      }
      if soundEffectsEnabled { HancoSoundEngine.shared.play(.completion(combo: viewModel.combo)) }
      ChoseongQuizHaptics.fire(success: true, enabled: hapticsEnabled)
    } else {
      reviewDeck.recordMistake(item: outcome.answer, sourceDeckId: deck.deckId)
      collectReviewItem(outcome.answer)
      companion.publish(.startle)
      if soundEffectsEnabled { HancoSoundEngine.shared.play(.mistake) }
      ChoseongQuizHaptics.fire(success: false, enabled: hapticsEnabled)
    }
    reviewDeck.flush()
    scheduleAdvance()
  }

  private func scheduleAdvance() {
    guard advanceTask == nil, viewModel.feedback != nil, scenePhase == .active else { return }
    advanceTask = Task { @MainActor in
      defer { advanceTask = nil }
      try? await Task.sleep(nanoseconds: 650_000_000)
      guard !Task.isCancelled else { return }
      viewModel.advance()
    }
  }

  private func collectReviewItem(_ item: DeckItem) {
    let resolution = SessionItemResolution(
      itemIndex: deck.items.firstIndex(where: { $0.id == item.id }) ?? 0,
      hadMistake: true,
      mistakeCount: 1,
      mistakenJamoIndices: []
    )
    let id = ReviewDeckItem.id(itemId: item.id, sourceDeckId: deck.deckId)
    if let index = sessionReviewItems.firstIndex(where: { $0.id == id }) {
      sessionReviewItems[index].merge(resolution)
    } else {
      sessionReviewItems.append(
        SessionReviewItem(item: item, sourceDeckId: deck.deckId, resolution: resolution)
      )
    }
  }

  private func persistFinishedGame() {
    guard !didPersistCurrentRun else { return }
    didPersistCurrentRun = true
    let result = viewModel.result
    recordOutcome = gameProgress.append(
      GameRecord(
        id: UUID(),
        mode: .game,
        deckId: deck.deckId,
        deckVersion: deck.version,
        course: mode.gameKind.rawValue,
        score: result.score,
        maxCombo: result.maxCombo,
        accuracy: result.accuracyPercent,
        charactersPerMinute: result.charactersPerMinute,
        activeDuration: result.activeDuration,
        completedItemCount: result.completedItemCount,
        missedItemCount: result.missedCardCount,
        inputMode: .builtIn,
        playedAt: Date()
      )
    )
    if recordOutcome?.isNewBest == true { companion.publish(.newBest) }
    retention.record(.game, session: retentionSession)
  }

  private func retry() {
    showsResult = false
    recordOutcome = nil
    didPersistCurrentRun = false
    sessionReviewItems.removeAll(keepingCapacity: true)
    retentionSession = RetentionSessionContext()
    if GamePresetSessionRandomizer.isBundledPreset(
      deckID: deck.deckId,
      gameKind: mode.gameKind
    ) {
      let seed = GamePresetSessionRandomizer.seed(for: deck, gameKind: mode.gameKind)
      let rounds: [ChoseongQuizRound]
      switch mode {
      case .choseong:
        rounds = ChoseongQuizBuilder.rounds(items: deck.items, seed: seed)
      case .wordMatch:
        rounds = WordMatchQuizBuilder.rounds(items: deck.items, seed: seed)
      }
      viewModel.restart(rounds: rounds)
    } else {
      viewModel.restart()
    }
  }

  private func finishFromResult() {
    guard showsResult, !exitsAfterResultDismiss else { return }
    exitsAfterResultDismiss = true
    showsResult = false
  }

  private var resultRecommendations: [CatalogDeck] {
    DeckRecommendationEngine.relatedRecommendations(
      sourceDeckID: deck.deckId,
      sourceTags: deck.tags,
      catalogDecks: discoverViewModel.catalog?.decks ?? [],
      installedDeckIDs: Set(deckLibrary.installedDecks.keys)
    )
  }

  private var mascotMood: MascotMood {
    switch viewModel.feedback {
    case .correct: .eureka
    case .incorrect: .oops
    case nil: .focus
    }
  }

  private var mascotReaction: MascotReaction {
    switch viewModel.feedback {
    case .correct: .eureka
    case .incorrect: .startle
    case nil: .none
    }
  }

  private var promptTitleKey: LocalizedStringKey {
    mode == .choseong ? "choseong.initials.title" : "word_match.prompt.title"
  }

  private var readyFeedbackKey: LocalizedStringKey {
    mode == .choseong ? "choseong.feedback.ready" : "word_match.feedback.ready"
  }

  private var playAccessibilityKey: LocalizedStringKey {
    mode == .choseong
      ? "choseong.accessibility.play_screen" : "word_match.accessibility.play_screen"
  }

  private func optionForeground(_ optionID: String) -> Color {
    guard viewModel.feedback != nil else { return AppPalette.ink }
    if optionID == viewModel.currentRound.answer.id { return .white }
    if optionID == viewModel.selectedOptionID { return .white }
    return AppPalette.mutedInk
  }

  private func optionBackground(_ optionID: String) -> Color {
    guard viewModel.feedback != nil else { return AppPalette.card }
    if optionID == viewModel.currentRound.answer.id { return AppPalette.success }
    if optionID == viewModel.selectedOptionID { return AppPalette.error }
    return AppPalette.card.opacity(0.72)
  }

  private func optionBorder(_ optionID: String) -> Color {
    guard viewModel.feedback != nil else { return AppPalette.accentSoft }
    if optionID == viewModel.currentRound.answer.id { return AppPalette.success }
    if optionID == viewModel.selectedOptionID { return AppPalette.error }
    return .clear
  }
}

@MainActor
private enum ChoseongQuizHaptics {
  private static let generator = UINotificationFeedbackGenerator()

  static func fire(success: Bool, enabled: Bool) {
    guard enabled else { return }
    generator.notificationOccurred(success ? .success : .error)
    generator.prepare()
  }
}

struct ChoseongTypingRound: Equatable, Identifiable {
  let answer: DeckItem
  let initials: String
  let requiresMeaningHint: Bool

  var id: String { answer.id }
}

enum ChoseongTypingBuilder {
  static func rounds(
    items: [DeckItem],
    limit: Int = 10,
    seed: UInt64
  ) -> [ChoseongTypingRound] {
    guard limit > 0 else { return [] }
    var seenWords: Set<String> = []
    var eligible = items.filter { item in
      let initials = ChoseongExtractor.extract(from: item.ko)
      return !initials.isEmpty
        && (try? JamoDecomposer.keySequence(for: item.ko)) != nil
        && seenWords.insert(item.ko).inserted
    }
    guard !eligible.isEmpty else { return [] }

    let initialGroups = Dictionary(grouping: eligible) {
      ChoseongExtractor.extract(from: $0.ko)
    }
    eligible = eligible.filter { item in
      let initials = ChoseongExtractor.extract(from: item.ko)
      let hasMeaning = item.appMeaning.map {
        !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
      } ?? false
      return initialGroups[initials, default: []].count == 1 || hasMeaning
    }
    guard !eligible.isEmpty else { return [] }

    let initialsCounts = Dictionary(grouping: eligible) {
      ChoseongExtractor.extract(from: $0.ko)
    }.mapValues(\.count)
    var generator = ChoseongRandomNumberGenerator(seed: seed ^ 0x7A17_1E5)
    eligible.shuffle(using: &generator)
    return eligible.prefix(min(limit, eligible.count)).map {
      let initials = ChoseongExtractor.extract(from: $0.ko)
      return ChoseongTypingRound(
        answer: $0,
        initials: initials,
        requiresMeaningHint: initialsCounts[initials, default: 0] > 1
      )
    }
  }
}

enum DictationTypingBuilder {
  static func rounds(
    items: [DeckItem],
    limit: Int = 10,
    seed: UInt64
  ) -> [ChoseongTypingRound] {
    guard limit > 0 else { return [] }
    var seenWords: Set<String> = []
    var eligible = items.filter { item in
      guard !item.ko.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
        let sequence = try? JamoDecomposer.keySequence(for: item.ko),
        !sequence.isEmpty
      else { return false }
      return seenWords.insert(item.ko).inserted
    }
    guard !eligible.isEmpty else { return [] }

    var generator = ChoseongRandomNumberGenerator(seed: seed ^ 0xD1C7_A710)
    eligible.shuffle(using: &generator)
    return eligible.prefix(min(limit, eligible.count)).map {
      ChoseongTypingRound(answer: $0, initials: "", requiresMeaningHint: false)
    }
  }
}

enum WordMatchTypingBuilder {
  static func rounds(
    items: [DeckItem],
    limit: Int = 10,
    seed: UInt64
  ) -> [ChoseongTypingRound] {
    guard limit > 0 else { return [] }
    var seenWords: Set<String> = []
    var eligible = items.filter { item in
      guard let meaning = item.appMeaning else { return false }
      guard !item.ko.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
        !meaning.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
        let sequence = try? JamoDecomposer.keySequence(for: item.ko),
        !sequence.isEmpty
      else { return false }
      return seenWords.insert(item.ko).inserted
    }
    guard !eligible.isEmpty else { return [] }

    var generator = ChoseongRandomNumberGenerator(seed: seed ^ 0xA11C_E5EED)
    eligible.shuffle(using: &generator)
    return eligible.prefix(min(limit, eligible.count)).map {
      ChoseongTypingRound(answer: $0, initials: "", requiresMeaningHint: false)
    }
  }
}

struct ChoseongTypingCompletion: Equatable {
  let answer: DeckItem
  let points: Int
  let usedHint: Bool
  let resolution: SessionItemResolution
}

enum ChoseongTypingInputOutcome: Equatable {
  case correct
  case incorrect(answer: DeckItem, expected: Character, jamoIndex: Int)
  case completed(ChoseongTypingCompletion)
}

enum PronunciationHintUse: Equatable {
  case firstUse(answer: DeckItem)
  case replay(answer: DeckItem)
}

@MainActor
final class ChoseongTypingViewModel: ObservableObject {
  static let pronunciationHintLimit = 3

  enum Phase: Equatable {
    case ready
    case running
    case paused
    case finished
  }

  enum Feedback: Equatable {
    case ready
    case correct
    case incorrect
    case completed(points: Int)
  }

  private(set) var rounds: [ChoseongTypingRound]

  @Published private(set) var phase: Phase = .ready
  @Published private(set) var currentIndex = 0
  @Published private(set) var score = 0
  @Published private(set) var combo = 0
  @Published private(set) var maxCombo = 0
  @Published private(set) var completedItemCount = 0
  @Published private(set) var imperfectItemCount = 0
  @Published private(set) var mistakeCount = 0
  @Published private(set) var composition = CompositionState()
  @Published private(set) var judgeState: JamoJudgeState
  @Published private(set) var feedback: Feedback = .ready
  @Published private(set) var feedbackRevision = 0
  @Published private(set) var roundRevision = 0
  @Published private(set) var compositionRevision = 0
  @Published private(set) var lastAcceptedKey: Character?
  @Published private(set) var shouldAnimateSyllableJoin = false
  @Published private(set) var isHintVisible = false
  @Published private(set) var isHintPenaltyApplied = false
  @Published private(set) var pronunciationHintsRemaining =
    ChoseongTypingViewModel.pronunciationHintLimit
  @Published private(set) var didUsePronunciationHintForCurrentRound = false
  @Published private(set) var totalAcceptedInputCount = 0

  private var acceptedKeys: [Character] = []
  private var currentWordMistakeCount = 0
  private var currentWordMistakenJamoIndices: Set<Int> = []
  private var activeElapsed: TimeInterval = 0
  private var questionStartedAt: TimeInterval = 0
  private var lastActiveDate: Date?

  init(rounds: [ChoseongTypingRound]) {
    precondition(!rounds.isEmpty, "Choseong typing requires at least one round")
    guard let judge = try? JamoJudgeState(target: rounds[0].answer.ko) else {
      preconditionFailure("Validated choseong target became invalid")
    }
    self.rounds = rounds
    self.judgeState = judge
  }

  convenience init(items: [DeckItem], limit: Int = 10, seed: UInt64) {
    self.init(rounds: ChoseongTypingBuilder.rounds(items: items, limit: limit, seed: seed))
  }

  var currentRound: ChoseongTypingRound { rounds[currentIndex] }
  var questionNumber: Int { currentIndex + 1 }
  var targetJamoSequence: [Character] { judgeState.expectedSequence }
  var completedJamoCount: Int { judgeState.currentIndex }
  var enteredText: String { composition.text }
  var composingPreview: String { composition.composingText }
  var initialProgressUnits: [ChoseongInitialProgressUnit] {
    ChoseongInitialProgressBuilder.units(
      answer: currentRound.answer.ko,
      completedJamoCount: completedJamoCount
    )
  }
  var acceptedKeySequence: [Character] { acceptedKeys }
  var nextExpectedKey: Character? { judgeState.expectedNext }
  var accuracyPercent: Double {
    let total = totalAcceptedInputCount + mistakeCount
    guard total > 0 else { return 0 }
    return Double(totalAcceptedInputCount) / Double(total) * 100
  }
  var questionsPerMinute: Double {
    guard activeElapsed > 0 else { return 0 }
    return Double(completedItemCount) / (activeElapsed / 60)
  }
  var result: FlowGameResult {
    FlowGameResult(
      score: score,
      maxCombo: maxCombo,
      accuracyPercent: accuracyPercent,
      charactersPerMinute: questionsPerMinute,
      activeDuration: activeElapsed,
      completedItemCount: completedItemCount,
      missedCardCount: imperfectItemCount,
      rank: rank
    )
  }

  func start(at date: Date = Date()) {
    guard phase == .ready else { return }
    phase = .running
    lastActiveDate = date
    questionStartedAt = activeElapsed
  }

  func useHint(shouldPenalize: Bool = true) {
    guard phase == .running, !isRoundComplete else { return }
    isHintVisible = true
    isHintPenaltyApplied = isHintPenaltyApplied || shouldPenalize
  }

  var canUsePronunciationHint: Bool {
    phase == .running && !isRoundComplete
      && (didUsePronunciationHintForCurrentRound || pronunciationHintsRemaining > 0)
  }

  @discardableResult
  func usePronunciationHint() -> PronunciationHintUse? {
    guard phase == .running, !isRoundComplete else { return nil }
    if didUsePronunciationHintForCurrentRound {
      return .replay(answer: currentRound.answer)
    }
    guard pronunciationHintsRemaining > 0 else { return nil }

    pronunciationHintsRemaining -= 1
    didUsePronunciationHintForCurrentRound = true
    isHintPenaltyApplied = true
    combo = 0
    return .firstUse(answer: currentRound.answer)
  }

  @discardableResult
  func input(_ key: Character, at date: Date = Date()) -> ChoseongTypingInputOutcome? {
    guard phase == .running, !isRoundComplete else { return nil }
    consumeActiveTime(until: date)
    let evaluation = JamoSequenceJudge.evaluate(key, state: judgeState)
    judgeState = evaluation.state

    let outcome: ChoseongTypingInputOutcome
    switch evaluation.result {
    case .correct(let completed):
      let previousPhase = composition.phase
      acceptedKeys.append(key)
      totalAcceptedInputCount += 1
      composition = HangulComposer.reduce(composition, event: .key(key))
      lastAcceptedKey = key
      shouldAnimateSyllableJoin = Self.isSyllableJoin(
        from: previousPhase,
        to: composition.phase
      )
      compositionRevision &+= 1
      if completed {
        outcome = completeCurrentRound()
      } else {
        feedback = .correct
        outcome = .correct
      }

    case .incorrect(let expected):
      outcome = recordMistake(expected: expected)

    case .alreadyComplete:
      return nil
    }
    feedbackRevision &+= 1
    return outcome
  }

  func backspace(at date: Date = Date()) {
    guard phase == .running, !isRoundComplete, !acceptedKeys.isEmpty else { return }
    consumeActiveTime(until: date)
    acceptedKeys.removeLast()
    composition = HangulComposer.reduce(composition, event: .backspace)
    lastAcceptedKey = acceptedKeys.last
    shouldAnimateSyllableJoin = false
    compositionRevision &+= 1
    rebuildJudgeFromAcceptedKeys()
    feedback = .ready
    feedbackRevision &+= 1
  }

  func synchronizeOSIME(acceptedSequence: [Character], at date: Date = Date())
    -> [ChoseongTypingInputOutcome]
  {
    guard phase == .running, !isRoundComplete,
      acceptedSequence.count <= targetJamoSequence.count,
      Array(targetJamoSequence.prefix(acceptedSequence.count)) == acceptedSequence
    else { return [] }

    while acceptedKeys.count > acceptedSequence.count {
      backspace(at: date)
    }
    guard acceptedKeys.count < acceptedSequence.count else { return [] }
    var outcomes: [ChoseongTypingInputOutcome] = []
    for key in acceptedSequence.dropFirst(acceptedKeys.count) {
      if let outcome = input(key, at: date) { outcomes.append(outcome) }
    }
    return outcomes
  }

  @discardableResult
  func recordConfirmedOSIMEMistake(at date: Date = Date()) -> ChoseongTypingInputOutcome? {
    guard phase == .running, !isRoundComplete, let expected = judgeState.expectedNext else {
      return nil
    }
    consumeActiveTime(until: date)
    let outcome = recordMistake(expected: expected)
    feedbackRevision &+= 1
    return outcome
  }

  func advance(at date: Date = Date()) {
    guard phase == .running, isRoundComplete else { return }
    if currentIndex + 1 >= rounds.count {
      phase = .finished
      lastActiveDate = nil
      return
    }
    currentIndex += 1
    resetCurrentRound()
    questionStartedAt = activeElapsed
    lastActiveDate = date
    roundRevision &+= 1
  }

  func pause(at date: Date = Date()) {
    guard phase == .running else { return }
    if !isRoundComplete { consumeActiveTime(until: date) }
    lastActiveDate = nil
    phase = .paused
  }

  func resume(at date: Date = Date()) {
    guard phase == .paused else { return }
    phase = .running
    if !isRoundComplete { lastActiveDate = date }
  }

  func restart(
    rounds replacementRounds: [ChoseongTypingRound]? = nil,
    at date: Date = Date()
  ) {
    if let replacementRounds {
      precondition(!replacementRounds.isEmpty, "Choseong typing requires at least one round")
      rounds = replacementRounds
    }
    currentIndex = 0
    score = 0
    combo = 0
    maxCombo = 0
    completedItemCount = 0
    imperfectItemCount = 0
    mistakeCount = 0
    totalAcceptedInputCount = 0
    pronunciationHintsRemaining = Self.pronunciationHintLimit
    activeElapsed = 0
    questionStartedAt = 0
    feedbackRevision = 0
    roundRevision &+= 1
    phase = .running
    resetCurrentRound()
    lastActiveDate = date
  }

  private var isRoundComplete: Bool {
    if case .completed = feedback { return true }
    return false
  }

  private var rank: String {
    switch accuracyPercent {
    case 95...: "S"
    case 80...: "A"
    case 60...: "B"
    default: "C"
    }
  }

  private func completeCurrentRound() -> ChoseongTypingInputOutcome {
    let responseDuration = max(0, activeElapsed - questionStartedAt)
    let flawless = currentWordMistakeCount == 0
    if flawless {
      combo += 1
      maxCombo = max(maxCombo, combo)
    } else {
      combo = 0
      imperfectItemCount += 1
    }
    let speedBonus = max(0, Int((5 - responseDuration) * 10))
    let comboBonus = min(combo, 10) * 10
    let points = max(50, 100 + speedBonus + comboBonus - (isHintPenaltyApplied ? 30 : 0))
    score += points
    completedItemCount += 1
    feedback = .completed(points: points)
    lastActiveDate = nil
    let resolution = SessionItemResolution(
      itemIndex: currentIndex,
      hadMistake: !flawless,
      mistakeCount: currentWordMistakeCount,
      mistakenJamoIndices: currentWordMistakenJamoIndices
    )
    return .completed(
      ChoseongTypingCompletion(
        answer: currentRound.answer,
        points: points,
        usedHint: isHintPenaltyApplied,
        resolution: resolution
      )
    )
  }

  private func recordMistake(expected: Character) -> ChoseongTypingInputOutcome {
    let jamoIndex = judgeState.currentIndex
    mistakeCount += 1
    currentWordMistakeCount += 1
    currentWordMistakenJamoIndices.insert(jamoIndex)
    combo = 0
    feedback = .incorrect
    return .incorrect(
      answer: currentRound.answer,
      expected: expected,
      jamoIndex: jamoIndex
    )
  }

  private func resetCurrentRound() {
    guard let judge = try? JamoJudgeState(target: currentRound.answer.ko) else {
      preconditionFailure("Validated choseong target became invalid")
    }
    judgeState = judge
    acceptedKeys.removeAll(keepingCapacity: true)
    composition = CompositionState()
    lastAcceptedKey = nil
    shouldAnimateSyllableJoin = false
    compositionRevision &+= 1
    currentWordMistakeCount = 0
    currentWordMistakenJamoIndices.removeAll(keepingCapacity: true)
    isHintVisible = false
    isHintPenaltyApplied = false
    didUsePronunciationHintForCurrentRound = false
    feedback = .ready
  }

  private func rebuildJudgeFromAcceptedKeys() {
    guard var rebuilt = try? JamoJudgeState(target: currentRound.answer.ko) else {
      preconditionFailure("Validated choseong target became invalid")
    }
    for key in acceptedKeys {
      rebuilt = JamoSequenceJudge.evaluate(key, state: rebuilt).state
    }
    judgeState = rebuilt
  }

  private func consumeActiveTime(until date: Date) {
    guard let lastActiveDate else { return }
    activeElapsed += max(0, date.timeIntervalSince(lastActiveDate))
    self.lastActiveDate = date
  }

  private static func isSyllableJoin(
    from previousPhase: CompositionPhase,
    to nextPhase: CompositionPhase
  ) -> Bool {
    switch (previousPhase, nextPhase) {
    case (.cho, .choJung), (.choJung, .choJung), (.choJungJong, .choJung):
      true
    default:
      false
    }
  }
}

enum RecallTypingGameMode: Equatable {
  case choseong
  case wordMatch
  case dictation

  var gameKind: GameKind {
    switch self {
    case .choseong: .choseong
    case .wordMatch: .wordMatch
    case .dictation: .dictation
    }
  }

  var resultPresentation: GameResultPresentation {
    switch self {
    case .choseong: .choseong
    case .wordMatch: .wordMatch
    case .dictation: .dictation
    }
  }

  var accessibilityNamespace: String {
    switch self {
    case .choseong: "choseong"
    case .wordMatch: "word_match"
    case .dictation: "dictation"
    }
  }

  var requiresCountdown: Bool {
    self == .wordMatch || self == .dictation
  }
}

private enum RecallTypingCountdownAction: Equatable {
  case start
  case resume
  case restart
}

struct ChoseongTypingView: View {
  @Environment(\.dynamicTypeSize) private var dynamicTypeSize
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
  @AppStorage(KeyboardPreferenceKeys.showsRomanHints) private var showsRomanHints = true
  @AppStorage(KeyboardPreferenceKeys.hapticsEnabled) private var hapticsEnabled = true
  @AppStorage(KeyboardPreferenceKeys.inputModeDefault) private var inputModeDefault =
    SessionInputMode.builtIn.rawValue
  @AppStorage(SoundPreferenceKeys.effectsEnabled) private var soundEffectsEnabled = true
  @AppStorage(SoundPreferenceKeys.typingPreset) private var typingSoundPreset =
    TypingSoundPreset.system.rawValue
  @AppStorage(SettingsPreferenceKeys.choseongShowsMeaning) private var choseongShowsMeaning = true

  let deck: Deck
  let mode: RecallTypingGameMode
  private let mascotAppearance: MascotSessionAppearance
  @StateObject private var viewModel: ChoseongTypingViewModel
  @StateObject private var targetSpeechSynthesizer = TargetSpeechSynthesizer()
  @State private var advanceTask: Task<Void, Never>?
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
  @State private var shakeStep: CGFloat = 0
  @State private var countdownValue: Int?
  @State private var countdownAction: RecallTypingCountdownAction?
  @State private var countdownTask: Task<Void, Never>?
  @State private var pendingRestartRounds: [ChoseongTypingRound]?
  @State private var didCaptureAnalyticsStart = false
  @State private var didCaptureAnalyticsCompletion = false
  @State private var didCaptureAnalyticsAbandonment = false

  init(deck: Deck, mode: RecallTypingGameMode = .choseong) {
    self.deck = deck
    self.mode = mode
    self.mascotAppearance = MascotSessionAppearance()
    _builtInKeyboardLayout = State(
      initialValue: BuiltInKeyboardLayout.resolved(
        from: UserDefaults.standard.string(
          forKey: KeyboardPreferenceKeys.builtInLayoutDefault
        ) ?? BuiltInKeyboardLayout.dubeolsik.rawValue
      )
    )
    let seed = GamePresetSessionRandomizer.seed(for: deck, gameKind: mode.gameKind)
    let rounds: [ChoseongTypingRound]
    switch mode {
    case .choseong:
      rounds = ChoseongTypingBuilder.rounds(items: deck.items, seed: seed)
    case .wordMatch:
      rounds = WordMatchTypingBuilder.rounds(items: deck.items, seed: seed)
    case .dictation:
      #if DEBUG
        if let rawLimit = ProcessInfo.processInfo.environment["UITEST_DECK_ITEM_LIMIT"],
          let limit = Int(rawLimit), limit > 0
        {
          rounds = DictationTypingBuilder.rounds(
            items: Array(deck.items.prefix(limit)),
            seed: seed
          )
        } else {
          rounds = DictationTypingBuilder.rounds(items: deck.items, seed: seed)
        }
      #else
        rounds = DictationTypingBuilder.rounds(items: deck.items, seed: seed)
      #endif
    }
    _viewModel = StateObject(wrappedValue: ChoseongTypingViewModel(rounds: rounds))
  }

  var body: some View {
    VStack(spacing: 0) {
      if adaptiveMetrics.isExpanded {
        GeometryReader { viewport in
          ScrollView {
            VStack(spacing: usesLandscapeCards ? 8 : 12) {
              quizCard
                .frame(minHeight: expandsSessionCards ? max(0, viewport.size.height - 28) * 0.55 : 0)
              typingCard
                .frame(minHeight: expandsSessionCards ? max(0, viewport.size.height - 28) * 0.45 : 0)
            }
            .frame(minHeight: max(0, viewport.size.height - 20))
            .padding(.horizontal, 14)
            .padding(.vertical, expandsSessionCards ? 10 : 4)
            .hancoCenteredContent(maxWidth: adaptiveMetrics.sessionLaneMaxWidth)
          }
          .scrollDismissesKeyboard(.never)
        }
      } else {
        VStack(spacing: 12) {
          quizCard
          typingCard
        }
        .frame(maxHeight: .infinity)
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .hancoCenteredContent(maxWidth: adaptiveMetrics.sessionLaneMaxWidth)
      }

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
      FlowGameResultView(
        deck: deck,
        result: viewModel.result,
        recordOutcome: recordOutcome,
        reviewItems: sessionReviewItems,
        recommendations: resultRecommendations,
        catalogDecks: discoverViewModel.catalog?.decks ?? [],
        presentation: mode.resultPresentation,
        onRetry: retry,
        onFinish: finishFromResult
      )
    }
    .onAppear {
      resolveInitialInputModeIfNeeded()
      captureAnalyticsStartIfNeeded()
      if mode.requiresCountdown {
        prepareCountdownSessionOnAppear()
      } else {
        if viewModel.phase == .ready {
          viewModel.start()
          deckLibrary.markPlayed(deck.deckId)
        } else if viewModel.phase == .paused, scenePhase == .active {
          viewModel.resume()
        }
      }
    }
    .onDisappear {
      advanceTask?.cancel()
      advanceTask = nil
      countdownTask?.cancel()
      countdownTask = nil
      targetSpeechSynthesizer.stop()
      reviewDeck.flush()
      captureAnalyticsAbandonmentIfNeeded()
    }
    .onChange(of: scenePhase) { phase in
      switch phase {
      case .active:
        if mode.requiresCountdown {
          restartPendingCountdownIfNeeded()
        } else {
          viewModel.resume()
          if case .completed = viewModel.feedback { scheduleAdvance() }
        }
      case .inactive, .background:
        advanceTask?.cancel()
        advanceTask = nil
        if mode.requiresCountdown {
          targetSpeechSynthesizer.stop()
          if countdownAction == nil, viewModel.phase == .running {
            countdownAction = .resume
          }
          countdownTask?.cancel()
          countdownTask = nil
          countdownValue = nil
        }
        viewModel.pause()
      @unknown default:
        targetSpeechSynthesizer.stop()
        viewModel.pause()
      }
    }
    .onChange(of: viewModel.phase) { phase in
      if phase == .finished {
        targetSpeechSynthesizer.stop()
        persistFinishedGame()
        showsResult = true
      }
    }
    .onChange(of: inputMode) { mode in
      showsOSIMEUnavailable = false
      korean10KeyInterpreter.reset()
      inputResetRevision &+= 1
      if mode == .osIME, !hasUsedBuiltInInput { recordInputMode = .osIME }
    }
    .onChange(of: viewModel.roundRevision) { _ in
      korean10KeyInterpreter.reset()
    }
    .onChange(of: showsResult) { isPresented in
      guard !isPresented, exitsAfterResultDismiss else { return }
      Task { @MainActor in
        await Task.yield()
        guard exitsAfterResultDismiss else { return }
        dismiss()
      }
    }
    .overlay(alignment: .topLeading) {
      VStack(spacing: 0) {
        Text(playAccessibilityKey)
          .accessibilityIdentifier("\(mode.accessibilityNamespace).play.screen")
        #if DEBUG
          if mode != .dictation {
            Text(verbatim: String(viewModel.targetJamoSequence))
              .accessibilityIdentifier("\(mode.accessibilityNamespace).answer.keys")
          }
        #endif
      }
      .font(.system(size: 1))
      .opacity(0.01)
    }
    .overlay {
      if let countdownValue {
        ZStack {
          Rectangle().fill(.ultraThinMaterial).ignoresSafeArea()
          VStack(spacing: 12) {
            Text("game.countdown.ready")
              .font(.headline.weight(.bold))
              .foregroundStyle(AppPalette.secondary)
            Text(verbatim: String(countdownValue))
              .font(.system(size: 84, weight: .black, design: .rounded))
              .foregroundStyle(AppPalette.accent)
              .contentTransition(.numericText())
          }
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("\(mode.accessibilityNamespace).countdown")
      }
    }
    .sessionExitCovered(exitsAfterResultDismiss)
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

  private var hud: some View {
    HStack(spacing: 5) {
      metric(
        image: "number.circle.fill",
        value: "\(viewModel.questionNumber)/\(viewModel.rounds.count)",
        label: questionLabelKey,
        identifier: "\(mode.accessibilityNamespace).question.value",
        tint: AppPalette.secondary
      )
      metric(
        image: "star.fill",
        value: viewModel.score.formatted(),
        label: "game.score",
        identifier: "\(mode.accessibilityNamespace).score.value",
        tint: AppPalette.accent
      )
      metric(
        image: "flame.fill",
        value: String(viewModel.combo),
        label: "game.combo",
        identifier: "\(mode.accessibilityNamespace).combo.value",
        tint: viewModel.combo == 0 ? AppPalette.mutedInk : Color.orange
      )
    }
  }

  @ViewBuilder
  private var quizCard: some View {
    switch mode {
    case .choseong:
      choseongQuizCard
    case .wordMatch:
      wordMatchQuizCard
    case .dictation:
      dictationQuizCard
    }
  }

  private var usesLandscapeCards: Bool {
    adaptiveMetrics.isExpanded && !adaptiveMetrics.isTall && !dynamicTypeSize.isAccessibilitySize
  }

  private var quizCardPadding: CGFloat { usesLandscapeCards ? (expandsSessionCards ? 8 : 6) : 20 }

  private var expandsSessionCards: Bool {
    adaptiveMetrics.isTall || adaptiveMetrics.availableHeight >= 800
  }

  private var quizLearningScale: CGFloat {
    usesLandscapeCards && expandsSessionCards ? adaptiveMetrics.learningScale : 1
  }

  private var compositionScale: CGFloat {
    // Keep the two text rows below the preview clear of the keyboard on iPad mini.
    usesLandscapeCards && !expandsSessionCards ? 1 : adaptiveMetrics.learningScale
  }

  private var choseongQuizCard: some View {
    VStack(spacing: usesLandscapeCards ? 6 : 10) {
      Text("choseong.initials.title")
        .font(.caption.weight(.bold))
        .foregroundStyle(AppPalette.secondary)
      if case .completed = viewModel.feedback {
        solvedWordReveal
          .transition(.scale(scale: 0.86).combined(with: .opacity))
      } else {
        ChoseongInitialProgressTrack(
          initials: viewModel.currentRound.initials,
          units: viewModel.initialProgressUnits,
          fontScale: fontScale * quizLearningScale
        )
        .transition(.opacity)
      }
      hint
      if !usesLandscapeCards { pronunciationHintButton }
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .padding(.horizontal, 22)
    .padding(.vertical, quizCardPadding)
    .overlay(alignment: .topTrailing) {
      if usesLandscapeCards { pronunciationHintButton.padding(8) }
    }
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
    .overlay {
      RoundedRectangle(cornerRadius: 26, style: .continuous)
        .stroke(AppPalette.secondary.opacity(0.2), lineWidth: 1)
    }
    .animation(.spring(response: 0.28, dampingFraction: 0.72), value: viewModel.feedbackRevision)
  }

  private var solvedWordReveal: some View {
    HStack(spacing: 10) {
      Image(systemName: "checkmark.circle.fill")
        .font(.system(size: 27 * fontScale, weight: .bold))
        .foregroundStyle(AppPalette.success)
      Text(verbatim: viewModel.currentRound.answer.ko)
        .font(.system(size: 42 * fontScale, weight: .black, design: .rounded))
        .foregroundStyle(AppPalette.ink)
        .lineLimit(1)
        .minimumScaleFactor(0.52)
    }
    .padding(.horizontal, 18)
    .frame(maxWidth: .infinity, minHeight: 68 * fontScale)
    .background(AppPalette.success.opacity(0.12), in: RoundedRectangle(cornerRadius: 18))
    .overlay {
      RoundedRectangle(cornerRadius: 18)
        .strokeBorder(AppPalette.success.opacity(0.42), lineWidth: 1.5)
    }
    .accessibilityElement(children: .combine)
    .accessibilityIdentifier("choseong.answer.reveal")
  }

  private var dictationQuizCard: some View {
    VStack(spacing: 11) {
      Text("dictation.prompt.title")
        .font(.caption.weight(.bold))
        .foregroundStyle(AppPalette.secondary)
      Button(action: speakCurrentAnswer) {
        ZStack {
          Circle()
            .fill(AppPalette.accentSoft.opacity(0.7))
            .frame(width: 72 * quizLearningScale, height: 72 * quizLearningScale)
          Image(systemName: "speaker.wave.3.fill")
            .font(.system(size: 29 * quizLearningScale, weight: .bold))
            .foregroundStyle(AppPalette.accent)
        }
      }
      .buttonStyle(.plain)
      .accessibilityLabel(Text("dictation.replay"))
      .accessibilityIdentifier("dictation.replay")
      Label("dictation.prompt.guide", systemImage: "waveform")
        .font(.caption.weight(.semibold))
        .foregroundStyle(AppPalette.mutedInk)
        .multilineTextAlignment(.center)
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .padding(.horizontal, 22)
    .padding(.vertical, quizCardPadding)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
    .overlay {
      RoundedRectangle(cornerRadius: 26, style: .continuous)
        .stroke(AppPalette.secondary.opacity(0.2), lineWidth: 1)
    }
  }

  private var wordMatchQuizCard: some View {
    VStack(spacing: usesLandscapeCards ? 6 : 12) {
      Text("word_match.prompt.title")
        .font(.caption.weight(.bold))
        .foregroundStyle(AppPalette.secondary)

      VStack(spacing: 9) {
        if !usesLandscapeCards {
          Image(systemName: "character.book.closed.fill")
            .font(.system(size: 24, weight: .bold))
            .foregroundStyle(Color.orange)
        }
        Text(
          verbatim: JapaneseMeaningDisplayText.format(
            viewModel.currentRound.answer.appMeaning ?? ""
          )
        )
        .font(.system(size: max(22, 30 * fontScale * quizLearningScale), weight: .black, design: .rounded))
        .foregroundStyle(AppPalette.ink)
        .multilineTextAlignment(.center)
        .lineLimit(3)
        .minimumScaleFactor(0.7)
        .accessibilityIdentifier("word_match.prompt.value")
      }
      .frame(maxWidth: .infinity, minHeight: (usesLandscapeCards ? 44 : 112) * fontScale)
      .padding(.horizontal, 18)
      .padding(.vertical, usesLandscapeCards ? 4 : 14)
      .background(Color.orange.opacity(0.11), in: RoundedRectangle(cornerRadius: 18))
      .overlay {
        RoundedRectangle(cornerRadius: 18)
          .strokeBorder(Color.orange.opacity(0.24), lineWidth: 1)
      }

      Label("word_match.prompt.guide", systemImage: "keyboard.fill")
        .font(.caption.weight(.semibold))
        .foregroundStyle(AppPalette.mutedInk)
        .multilineTextAlignment(.center)

      if !usesLandscapeCards { pronunciationHintButton }
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .padding(.horizontal, 22)
    .padding(.vertical, quizCardPadding)
    .overlay(alignment: .topTrailing) {
      if usesLandscapeCards { pronunciationHintButton.padding(8) }
    }
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
    .overlay {
      RoundedRectangle(cornerRadius: 26, style: .continuous)
        .stroke(AppPalette.secondary.opacity(0.2), lineWidth: 1)
    }
  }

  private var gameMascot: some View {
    GrowingMascotView(
      mood: mascotMood,
      reaction: mascotReaction,
      reactionRevision: viewModel.feedbackRevision,
      automaticProp: mascotAppearance.automaticProp,
      pose: .front,
      size: 52 * compositionScale,
      calm: true
    )
    .accessibilityIdentifier("\(mode.accessibilityNamespace).mascot")
  }

  private var pronunciationHintButton: some View {
    Button(action: usePronunciationHint) {
      Label(pronunciationHintTitle, systemImage: "speaker.wave.2.fill")
        .font(.caption.weight(.bold))
        .foregroundStyle(
          viewModel.canUsePronunciationHint ? AppPalette.accent : AppPalette.mutedInk
        )
        .padding(.horizontal, 13)
        .padding(.vertical, 8)
        .frame(minHeight: adaptiveMetrics.isExpanded ? 44 : nil)
        .background(AppPalette.accentSoft.opacity(0.46), in: Capsule())
    }
    .buttonStyle(.plain)
    .disabled(!viewModel.canUsePronunciationHint)
    .accessibilityIdentifier("\(mode.accessibilityNamespace).pronunciation_hint")
  }

  private var pronunciationHintTitle: String {
    let key: String
    if viewModel.didUsePronunciationHintForCurrentRound {
      key = "game.pronunciation_hint.replay_format"
    } else if viewModel.pronunciationHintsRemaining > 0 {
      key = "game.pronunciation_hint.use_format"
    } else {
      key = "game.pronunciation_hint.exhausted_format"
    }
    return AppLocalization.format(key,
      viewModel.pronunciationHintsRemaining
    )
  }

  @ViewBuilder
  private var hint: some View {
    if let meaning = viewModel.currentRound.answer.appMeaning,
      !meaning.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
      choseongShowsMeaning || viewModel.isHintVisible
    {
      HStack(spacing: 8) {
        Image(systemName: "lightbulb.fill")
          .font(.system(size: 17, weight: .bold))
          .foregroundStyle(Color.orange)
        Text(
          verbatim: JapaneseMeaningDisplayText.format(
            meaning
          )
        )
        .font(.system(size: max(17, 19 * fontScale), weight: .bold, design: .rounded))
        .foregroundStyle(AppPalette.ink)
        .multilineTextAlignment(.center)
        .lineLimit(2)
        .minimumScaleFactor(0.8)
      }
      .padding(.horizontal, 16)
      .padding(.vertical, usesLandscapeCards ? 4 : 10)
      .background(Color.orange.opacity(0.11), in: RoundedRectangle(cornerRadius: 14))
      .overlay {
        RoundedRectangle(cornerRadius: 14)
          .strokeBorder(Color.orange.opacity(0.22), lineWidth: 1)
      }
      .fixedSize(horizontal: false, vertical: true)
      .accessibilityElement(children: .combine)
      .accessibilityIdentifier("choseong.hint.value")
    } else if viewModel.currentRound.answer.appMeaning?
      .trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
    {
      Button {
        viewModel.useHint(shouldPenalize: !viewModel.currentRound.requiresMeaningHint)
      } label: {
        Label("choseong.hint.show", systemImage: "lightbulb")
          .font(.subheadline.weight(.bold))
          .foregroundStyle(AppPalette.accent)
          .padding(.horizontal, 14)
          .padding(.vertical, 8)
          .background(AppPalette.accentSoft.opacity(0.48), in: Capsule())
      }
      .buttonStyle(.plain)
      .accessibilityIdentifier("choseong.hint.show")
    }
  }

  private var typingCard: some View {
    recallCompositionCard
  }

  private var recallCompositionCard: some View {
    VStack(spacing: 6) {
      HStack(spacing: 10) {
        gameMascot
          .frame(width: 70 * compositionScale, height: 106 * compositionScale)

        VStack(spacing: 4) {
          SyllableAssemblyPreview(
            text: compositionPreviewText,
            incomingJamo: viewModel.lastAcceptedKey,
            revision: viewModel.compositionRevision,
            shouldAnimateJoin: viewModel.shouldAnimateSyllableJoin,
            displayScale: compositionScale
          )

          HStack(spacing: 6) {
            Text("practice.entered_text")
              .foregroundStyle(AppPalette.mutedInk)
            Text(verbatim: viewModel.enteredText.isEmpty ? "…" : viewModel.enteredText)
              .fontWeight(.bold)
              .foregroundStyle(AppPalette.ink)
              .lineLimit(1)
              .minimumScaleFactor(0.75)
          }
          .font(.subheadline)
          .accessibilityElement(children: .ignore)
          .accessibilityLabel(Text("practice.entered_text"))
          .accessibilityValue(
            Text(verbatim: viewModel.enteredText.isEmpty ? "…" : viewModel.enteredText)
          )
          .accessibilityIdentifier("\(mode.accessibilityNamespace).typing.value")
        }
      }
      .frame(maxWidth: .infinity)

      feedbackLabel
        .font(.subheadline.weight(.semibold))
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity, minHeight: 22, alignment: .center)
        .accessibilityIdentifier("\(mode.accessibilityNamespace).typing.feedback")
    }
    .padding(.horizontal, 14)
    .padding(.top, usesLandscapeCards ? 2 : 8)
    .padding(.bottom, usesLandscapeCards ? 2 : 9)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    .overlay {
      Text(verbatim: " ")
        .font(.system(size: 1))
        .frame(width: 1, height: 1)
        .opacity(0.01)
        .allowsHitTesting(false)
        .accessibilityIdentifier("\(mode.accessibilityNamespace).composition_card")
    }
    .modifier(ChoseongTypingShakeEffect(animatableData: shakeStep))
  }

  @ViewBuilder
  private var feedbackLabel: some View {
    switch viewModel.feedback {
    case .ready:
      Text(feedbackReadyKey)
        .foregroundStyle(AppPalette.mutedInk)
    case .correct:
      Text(feedbackCorrectJamoKey)
        .foregroundStyle(AppPalette.success)
    case .incorrect:
      Text(feedbackIncorrectKey)
        .foregroundStyle(AppPalette.error)
    case .completed(let points):
      Text(AppLocalization.format(feedbackCompletedKey, points))
        .foregroundStyle(AppPalette.success)
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
          target: viewModel.currentRound.answer.ko,
          acceptedText: viewModel.enteredText,
          resetRevision: viewModel.roundRevision + inputResetRevision,
          onInputStart: {
            HancoSoundEngine.shared.prepareForInputFeedback(currentCombo: viewModel.combo)
          },
          onAcceptedSequence: synchronizeOSIME,
          onConfirmedMismatch: recordOSIMEMistake
        )
      } else {
        if builtInKeyboardLayout == .korean10Key {
          Korean10KeyKeyboardView(
            nextExpectedKey: korean10KeyInterpreter.nextKey(for: viewModel.nextExpectedKey),
            options: HangulKeyboardOptions(
              showsKeyGuide: false,
              showsRomanHints: false,
              hapticsEnabled: hapticsEnabled
            ),
            onKeyFeedback: playKeySound,
            onKey: inputKorean10Key,
            onBackspace: backspaceKorean10Key
          )
        } else {
          HangulKeyboardView(
            nextExpectedKey: nil,
            options: HangulKeyboardOptions(
              showsKeyGuide: false,
              showsRomanHints: showsRomanHints,
              hapticsEnabled: hapticsEnabled
            ),
            onKeyFeedback: playKeySound,
            onKey: handleInput,
            onBackspace: handleBackspace
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
      guard let outcome = viewModel.input(jamo) else { return }
      handle(outcome)
    case .incorrect:
      guard let outcome = viewModel.input(key.displayText.first ?? "ㆍ") else { return }
      handle(outcome)
    }
  }

  private func backspaceKorean10Key() {
    markBuiltInInputUsed()
    guard korean10KeyInterpreter.backspace() == .forwardToHangulEngine else { return }
    viewModel.backspace()
  }

  private var gameBackground: some View {
    LinearGradient(
      colors: [
        AppPalette.backgroundTop,
        viewModel.combo >= 5 ? AppPalette.accentSoft.opacity(0.92) : AppPalette.backgroundBottom,
      ],
      startPoint: .topLeading,
      endPoint: .bottomTrailing
    )
    .animation(.easeInOut(duration: 0.35), value: viewModel.combo)
  }

  private func metric(
    image: String,
    value: String,
    label: LocalizedStringKey,
    identifier: String,
    tint: Color
  ) -> some View {
    CompactGameHUDMetric(
      systemImage: image,
      value: value,
      label: label,
      identifier: identifier,
      tint: tint,
      contentWidth: 64
    )
  }

  private func handleInput(_ key: Character) {
    markBuiltInInputUsed()
    guard let outcome = viewModel.input(key) else { return }
    handle(outcome)
  }

  private func handleBackspace() {
    markBuiltInInputUsed()
    viewModel.backspace()
  }

  private func synchronizeOSIME(_ sequence: [Character]) {
    for outcome in viewModel.synchronizeOSIME(acceptedSequence: sequence) {
      handle(outcome)
    }
  }

  private func recordOSIMEMistake() {
    guard let outcome = viewModel.recordConfirmedOSIMEMistake() else { return }
    handle(outcome)
  }

  private func handle(_ outcome: ChoseongTypingInputOutcome) {
    switch outcome {
    case .correct:
      companion.recordTypedJamo(1)
    case .incorrect(let answer, let expected, let jamoIndex):
      reviewDeck.recordMistake(item: answer, sourceDeckId: deck.deckId)
      collectReviewItem(answer, jamoIndex: jamoIndex)
      companion.recordMistake(expected: expected)
      companion.publish(.startle)
      if soundEffectsEnabled { HancoSoundEngine.shared.play(.mistake) }
      ChoseongQuizHaptics.fire(success: false, enabled: hapticsEnabled)
      withAnimation(.linear(duration: 0.24)) { shakeStep += 1 }
    case .completed(let completion):
      companion.recordTypedJamo(1)
      if !completion.usedHint, !completion.resolution.hadMistake,
        reviewDeck.recordPerfect(itemId: completion.answer.id, sourceDeckId: deck.deckId)
          == .graduated
      {
        companion.publish(.reviewGraduated)
      }
      if mode == .choseong {
        companion.recordChoseongSolved(usedPass: completion.usedHint)
      } else {
        companion.publish(.eureka)
      }
      if soundEffectsEnabled { HancoSoundEngine.shared.play(.completion(combo: viewModel.combo)) }
      ChoseongQuizHaptics.fire(success: true, enabled: hapticsEnabled)
      reviewDeck.flush()
      scheduleAdvance()
    }
  }

  private func scheduleAdvance() {
    guard advanceTask == nil, scenePhase == .active else { return }
    advanceTask = Task { @MainActor in
      defer { advanceTask = nil }
      try? await Task.sleep(nanoseconds: completedFeedbackNanoseconds)
      guard !Task.isCancelled else { return }
      viewModel.advance()
      if mode == .dictation, viewModel.phase == .running {
        speakCurrentAnswer()
      }
    }
  }

  private var completedFeedbackNanoseconds: UInt64 {
    mode == .choseong ? 1_350_000_000 : 650_000_000
  }

  private func collectReviewItem(_ item: DeckItem, jamoIndex: Int) {
    collectReviewItem(item, mistakenJamoIndices: [jamoIndex])
  }

  private func collectReviewItem(_ item: DeckItem, mistakenJamoIndices: Set<Int>) {
    let resolution = SessionItemResolution(
      itemIndex: deck.items.firstIndex(where: { $0.id == item.id }) ?? 0,
      hadMistake: true,
      mistakeCount: 1,
      mistakenJamoIndices: mistakenJamoIndices
    )
    let id = ReviewDeckItem.id(itemId: item.id, sourceDeckId: deck.deckId)
    if let index = sessionReviewItems.firstIndex(where: { $0.id == id }) {
      sessionReviewItems[index].merge(resolution)
    } else {
      sessionReviewItems.append(
        SessionReviewItem(item: item, sourceDeckId: deck.deckId, resolution: resolution)
      )
    }
  }

  private func persistFinishedGame() {
    guard !didPersistCurrentRun else { return }
    didPersistCurrentRun = true
    let result = viewModel.result
    recordOutcome = gameProgress.append(
      GameRecord(
        id: UUID(),
        mode: .game,
        deckId: deck.deckId,
        deckVersion: deck.version,
        course: mode.gameKind.rawValue,
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
    if recordOutcome?.isNewBest == true { companion.publish(.newBest) }
    retention.record(.game, session: retentionSession)
    captureAnalyticsCompletionIfNeeded(result)
  }

  private func retry() {
    showsResult = false
    recordOutcome = nil
    didPersistCurrentRun = false
    sessionReviewItems.removeAll(keepingCapacity: true)
    retentionSession = RetentionSessionContext()
    hasUsedBuiltInInput = false
    recordInputMode = resolvedRecordInputMode
    didCaptureAnalyticsStart = false
    didCaptureAnalyticsCompletion = false
    didCaptureAnalyticsAbandonment = false
    korean10KeyInterpreter.reset()
    inputResetRevision &+= 1
    let nextRounds = randomizedPresetRounds()
    if mode.requiresCountdown {
      targetSpeechSynthesizer.stop()
      pendingRestartRounds = nextRounds
      startCountdown(for: .restart)
    } else {
      viewModel.restart(rounds: nextRounds)
    }
    captureAnalyticsStartIfNeeded()
  }

  private var analyticsDeckSource: String {
    if mode.resultPresentation.presetLevel(for: deck) != nil { return "bundled" }
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
        .gameMode: mode.resultPresentation.analyticsValue,
        .difficulty: mode.resultPresentation.analyticsDifficulty(for: deck),
      ]
    )
    TelemetryService.shared.setCrashContext(
      feature: "game",
      sessionKind: "game",
      inputMode: recordInputMode.rawValue,
      gameMode: mode.resultPresentation.analyticsValue
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
        .gameMode: mode.resultPresentation.analyticsValue,
        .difficulty: mode.resultPresentation.analyticsDifficulty(for: deck),
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
        .gameMode: mode.resultPresentation.analyticsValue,
        .difficulty: mode.resultPresentation.analyticsDifficulty(for: deck),
      ]
    )
  }

  private func randomizedPresetRounds() -> [ChoseongTypingRound]? {
    guard GamePresetSessionRandomizer.isBundledPreset(
      deckID: deck.deckId,
      gameKind: mode.gameKind
    ) else { return nil }
    let seed = GamePresetSessionRandomizer.seed(for: deck, gameKind: mode.gameKind)
    switch mode {
    case .choseong:
      return ChoseongTypingBuilder.rounds(items: deck.items, seed: seed)
    case .wordMatch:
      return WordMatchTypingBuilder.rounds(items: deck.items, seed: seed)
    case .dictation:
      return DictationTypingBuilder.rounds(items: deck.items, seed: seed)
    }
  }

  private func finishFromResult() {
    guard showsResult, !exitsAfterResultDismiss else { return }
    targetSpeechSynthesizer.stop()
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

  private var compositionPreviewText: String {
    guard inputMode == .builtIn, builtInKeyboardLayout == .korean10Key,
      let pending = korean10KeyInterpreter.pendingDisplay
    else { return viewModel.composingPreview }
    return viewModel.composingPreview + pending
  }

  private func resolveInitialInputModeIfNeeded() {
    guard !didResolveInputMode else { return }
    didResolveInputMode = true
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

  private func playKeySound(_ role: TypingSoundKeyRole) {
    guard soundEffectsEnabled else { return }
    HancoTypingSoundFeedback.play(
      TypingSoundPreset.resolved(from: typingSoundPreset),
      role: role
    )
  }

  private func prepareCountdownSessionOnAppear() {
    guard scenePhase == .active else { return }
    if countdownAction != nil {
      restartPendingCountdownIfNeeded()
      return
    }
    switch viewModel.phase {
    case .ready:
      startCountdown(for: .start)
    case .paused:
      startCountdown(for: .resume)
    case .running:
      if case .completed = viewModel.feedback {
        scheduleAdvance()
      } else {
        speakCurrentAnswer()
      }
    case .finished:
      break
    }
  }

  private func restartPendingCountdownIfNeeded() {
    guard mode.requiresCountdown, scenePhase == .active else { return }
    if let countdownAction {
      startCountdown(for: countdownAction)
    } else if viewModel.phase == .ready {
      startCountdown(for: .start)
    } else if viewModel.phase == .paused {
      startCountdown(for: .resume)
    }
  }

  private func startCountdown(for action: RecallTypingCountdownAction) {
    guard mode.requiresCountdown, scenePhase == .active else {
      countdownAction = action
      return
    }
    countdownTask?.cancel()
    countdownAction = action
    let values = reduceMotion ? [1] : [3, 2, 1]
    countdownTask = Task { @MainActor in
      for value in values {
        guard !Task.isCancelled else { return }
        withAnimation(.easeOut(duration: 0.18)) { countdownValue = value }
        do {
          try await Task.sleep(nanoseconds: countdownStepNanoseconds)
        } catch {
          return
        }
      }
      guard !Task.isCancelled, scenePhase == .active else { return }
      countdownValue = nil
      countdownAction = nil
      countdownTask = nil
      completeCountdown(action)
    }
  }

  private func completeCountdown(_ action: RecallTypingCountdownAction) {
    switch action {
    case .start:
      viewModel.start()
    case .resume:
      viewModel.resume()
    case .restart:
      viewModel.restart(rounds: pendingRestartRounds)
      pendingRestartRounds = nil
    }
    if action == .start || action == .restart {
      deckLibrary.markPlayed(deck.deckId)
    }
    if case .completed = viewModel.feedback {
      scheduleAdvance()
    } else {
      speakCurrentAnswer()
    }
  }

  private func speakCurrentAnswer() {
    guard mode == .dictation, viewModel.phase == .running else { return }
    if case .completed = viewModel.feedback { return }
    let answer = viewModel.currentRound.answer
    targetSpeechSynthesizer.speak(answer.ko, bundledAudioPath: answer.audio)
  }

  private func usePronunciationHint() {
    guard mode == .choseong || mode == .wordMatch,
      let hintUse = viewModel.usePronunciationHint()
    else { return }

    let answer: DeckItem
    switch hintUse {
    case .firstUse(let firstAnswer):
      answer = firstAnswer
      reviewDeck.recordMistake(item: answer, sourceDeckId: deck.deckId)
      collectReviewItem(answer, mistakenJamoIndices: [])
      reviewDeck.flush()
      let announcement = AppLocalization.format("game.pronunciation_hint.used_announcement_format",
        viewModel.pronunciationHintsRemaining
      )
      UIAccessibility.post(notification: .announcement, argument: announcement)
    case .replay(let replayAnswer):
      answer = replayAnswer
    }
    targetSpeechSynthesizer.speak(answer.ko, bundledAudioPath: answer.audio)
  }

  private var countdownStepNanoseconds: UInt64 {
    #if DEBUG
      if let rawValue = ProcessInfo.processInfo.environment["UITEST_GAME_COUNTDOWN_STEP_SECONDS"],
        let seconds = Double(rawValue), seconds >= 0
      {
        return UInt64(seconds * 1_000_000_000)
      }
    #endif
    return reduceMotion ? 350_000_000 : 1_000_000_000
  }

  private var playAccessibilityKey: LocalizedStringKey {
    switch mode {
    case .choseong: "choseong.accessibility.play_screen"
    case .wordMatch: "word_match.accessibility.play_screen"
    case .dictation: "dictation.accessibility.play_screen"
    }
  }

  private var questionLabelKey: LocalizedStringKey {
    switch mode {
    case .choseong: "choseong.question"
    case .wordMatch: "word_match.question"
    case .dictation: "dictation.question"
    }
  }

  private var feedbackReadyKey: LocalizedStringKey {
    switch mode {
    case .choseong: "choseong.feedback.ready"
    case .wordMatch: "word_match.feedback.ready"
    case .dictation: "dictation.feedback.ready"
    }
  }

  private var feedbackCorrectJamoKey: LocalizedStringKey {
    switch mode {
    case .choseong: "choseong.feedback.jamo_correct"
    case .wordMatch: "word_match.feedback.jamo_correct"
    case .dictation: "dictation.feedback.jamo_correct"
    }
  }

  private var feedbackIncorrectKey: LocalizedStringKey {
    switch mode {
    case .choseong: "choseong.feedback.incorrect_typing"
    case .wordMatch: "word_match.feedback.incorrect_typing"
    case .dictation: "dictation.feedback.incorrect_typing"
    }
  }

  private var feedbackCompletedKey: String {
    switch mode {
    case .choseong: "choseong.feedback.correct"
    case .wordMatch: "word_match.feedback.correct"
    case .dictation: "dictation.feedback.correct"
    }
  }

  private var resultRecommendations: [CatalogDeck] {
    DeckRecommendationEngine.relatedRecommendations(
      sourceDeckID: deck.deckId,
      sourceTags: deck.tags,
      catalogDecks: discoverViewModel.catalog?.decks ?? [],
      installedDeckIDs: Set(deckLibrary.installedDecks.keys)
    )
  }

  private var mascotMood: MascotMood {
    switch viewModel.feedback {
    case .incorrect: .oops
    case .completed: .eureka
    case .ready, .correct: .focus
    }
  }

  private var mascotReaction: MascotReaction {
    switch viewModel.feedback {
    case .incorrect: .startle
    case .completed: .eureka
    case .ready, .correct: .none
    }
  }
}

private struct ChoseongTypingShakeEffect: GeometryEffect {
  var animatableData: CGFloat

  func effectValue(size: CGSize) -> ProjectionTransform {
    ProjectionTransform(
      CGAffineTransform(
        translationX: 7 * sin(animatableData * .pi * 2),
        y: 0
      )
    )
  }
}

private struct ChoseongInitialProgressTrack: View {
  let initials: String
  let units: [ChoseongInitialProgressUnit]
  let fontScale: CGFloat

  private var glyphCount: Int {
    max(1, units.filter { !$0.isSeparator }.count)
  }

  private var baseContentWidth: CGFloat {
    let glyphs = CGFloat(glyphCount) * 58
    let separators = CGFloat(units.filter(\.isSeparator).count) * 18
    let spacing = CGFloat(max(units.count - 1, 0)) * 8
    return glyphs + separators + spacing
  }

  var body: some View {
    GeometryReader { proxy in
      let scale = min(fontScale, max(0.48, proxy.size.width / max(1, baseContentWidth)))

      HStack(spacing: 8 * scale) {
        ForEach(units) { unit in
          if unit.isSeparator {
            Color.clear
              .frame(width: 18 * scale, height: 64 * scale)
              .accessibilityHidden(true)
          } else {
            initial(unit, scale: scale)
          }
        }
      }
      .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
    }
    .frame(height: 68 * fontScale)
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(Text(verbatim: initials))
    .accessibilityValue(
      Text(
        verbatim: "\(units.filter { $0.state == .completed }.count) / \(glyphCount)"
      )
    )
    .accessibilityIdentifier("choseong.initials.value")
  }

  private func initial(_ unit: ChoseongInitialProgressUnit, scale: CGFloat) -> some View {
    let cornerRadius = 18 * scale
    let shadowColor: Color =
      unit.state == .active ? AppPalette.secondary.opacity(0.2) : .clear

    return ZStack {
      RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        .fill(background(for: unit.state))
        .overlay {
          RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            .strokeBorder(stroke(for: unit.state), lineWidth: 2 * scale)
        }

      Text(verbatim: String(unit.character))
        .font(.system(size: 42 * scale, weight: .black, design: .rounded))
        .foregroundStyle(foreground(for: unit.state))
    }
    .frame(width: 58 * scale, height: 62 * scale)
    .scaleEffect(unit.state == .active ? 1.06 : 1)
    .shadow(
      color: shadowColor,
      radius: 8 * scale,
      y: 3 * scale
    )
    .animation(.spring(response: 0.24, dampingFraction: 0.68), value: unit.state)
    .accessibilityHidden(true)
  }

  private func foreground(for state: ChoseongInitialProgressUnit.State) -> Color {
    switch state {
    case .pending: AppPalette.ink
    case .active: AppPalette.secondary
    case .completed: .white
    }
  }

  private func background(for state: ChoseongInitialProgressUnit.State) -> Color {
    switch state {
    case .pending: AppPalette.backgroundBottom.opacity(0.42)
    case .active: AppPalette.accentSoft.opacity(0.72)
    case .completed: AppPalette.success
    }
  }

  private func stroke(for state: ChoseongInitialProgressUnit.State) -> Color {
    switch state {
    case .pending: AppPalette.secondary.opacity(0.12)
    case .active: AppPalette.secondary.opacity(0.72)
    case .completed: AppPalette.success
    }
  }
}
