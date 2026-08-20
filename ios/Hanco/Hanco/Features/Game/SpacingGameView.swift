import Foundation
import SwiftUI
import UIKit

struct SpacingPassage: Equatable, Identifiable {
  let id: String
  let titleKey: LocalizedStringKey
  let text: String
  let level: Int

  init(id: String, titleKey: LocalizedStringKey, text: String, level: Int = 1) {
    self.id = id
    self.titleKey = titleKey
    self.text = text
    self.level = level
  }

  var characterCount: Int { text.count }
  var spaceCount: Int { text.filter(\.isWhitespace).count }
}

enum SpacingPassageCatalog {
  static var all: [SpacingPassage] {
    #if DEBUG
      if ProcessInfo.processInfo.environment["UITEST_SHORT_SPACING_PASSAGE"] == "1" {
        return [
          SpacingPassage(
            id: "morning_commute",
            titleKey: "spacing.passage.morning",
            text: "나는 간다."
          )
        ]
      }
    #endif
    return [
      SpacingPassage(
        id: "morning_commute",
        titleKey: "spacing.passage.morning",
        text: AppLocalization.string("spacing.passage.morning.text"),
        level: 1
      ),
      SpacingPassage(
        id: "weekend_trip",
        titleKey: "spacing.passage.weekend",
        text: AppLocalization.string("spacing.passage.weekend.text"),
        level: 2
      ),
      SpacingPassage(
        id: "family_dinner",
        titleKey: "spacing.passage.cooking",
        text: AppLocalization.string("spacing.passage.cooking.text"),
        level: 3
      ),
      SpacingPassage(
        id: "library_afternoon",
        titleKey: "spacing.passage.library",
        text: AppLocalization.string("spacing.passage.library.text"),
        level: 4
      ),
      SpacingPassage(
        id: "language_practice",
        titleKey: "spacing.passage.practice",
        text: AppLocalization.string("spacing.passage.practice.text"),
        level: 5
      ),
      SpacingPassage(
        id: "careful_judgment",
        titleKey: "spacing.passage.judgment",
        text: AppLocalization.string("spacing.passage.judgment.text"),
        level: 6
      ),
    ]
  }
}

struct SpacingMistakeReview: Equatable, Identifiable {
  let boundary: Int
  let attemptedText: String
  let answerText: String
  let expectedSpace: Bool

  var id: Int { boundary }
}

struct SpacingGameEvaluation: Equatable {
  let answer: String
  let submittedText: String
  let correctSpaceCount: Int
  let expectedSpaceCount: Int
  let missedSpaceCount: Int
  let extraSpaceCount: Int
  let accuracyPercent: Double
  let firstAttemptCorrectCount: Int
  let totalBoundaryCount: Int
  let firstAttemptAccuracyPercent: Double
  let correctionCount: Int
  let firstAttemptMistakes: [SpacingMistakeReview]
  let score: Int
  let activeDuration: TimeInterval

  var isPerfect: Bool {
    missedSpaceCount == 0
      && extraSpaceCount == 0
      && firstAttemptAccuracyPercent == 100
  }

  var rank: String {
    switch firstAttemptAccuracyPercent {
    case 100...: "S"
    case 90...: "A"
    case 75...: "B"
    default: "C"
    }
  }
}

struct SpacingGameEngine: Equatable {
  let answer: String
  let compactText: String
  let correctBoundaries: Set<Int>

  var boundaryCount: Int { max(compactText.count - 1, 0) }

  init(answer: String) {
    let parsed = Self.parse(answer)
    precondition(!parsed.characters.isEmpty, "A spacing passage cannot be empty")
    precondition(!parsed.boundaries.isEmpty, "A spacing passage needs at least one space")
    self.answer = Self.render(characters: parsed.characters, boundaries: parsed.boundaries)
    compactText = String(parsed.characters)
    correctBoundaries = parsed.boundaries
  }

  func normalizedDraft(_ candidate: String) -> String? {
    let parsed = Self.parse(candidate)
    guard String(parsed.characters) == compactText else { return nil }
    return Self.render(characters: parsed.characters, boundaries: parsed.boundaries)
  }

  func selectedSpaceCount(in draft: String) -> Int {
    Self.parse(draft).boundaries.count
  }

  func renderedDraft(boundaries: Set<Int>) -> String {
    let validBoundaries = boundaries.filter { (1...boundaryCount).contains($0) }
    return Self.render(characters: Array(compactText), boundaries: Set(validBoundaries))
  }

  func isCorrectBoundary(_ boundary: Int) -> Bool {
    correctBoundaries.contains(boundary)
  }

  func evaluate(
    draft: String,
    activeDuration: TimeInterval,
    firstDecisions: [Int: Bool]? = nil,
    correctionCount: Int = 0
  ) -> SpacingGameEvaluation {
    let normalized = normalizedDraft(draft) ?? compactText
    let submittedBoundaries = Self.parse(normalized).boundaries
    let correct = submittedBoundaries.intersection(correctBoundaries).count
    let missed = correctBoundaries.subtracting(submittedBoundaries).count
    let extra = submittedBoundaries.subtracting(correctBoundaries).count
    let denominator = max(correctBoundaries.count + extra, 1)
    let accuracy = Double(correct) / Double(denominator) * 100
    let firstAttemptCorrect: Int
    let firstAttemptAccuracy: Double
    let firstAttemptMistakes: [SpacingMistakeReview]
    if let firstDecisions {
      firstAttemptCorrect = firstDecisions.reduce(into: 0) { count, decision in
        if decision.value == correctBoundaries.contains(decision.key) {
          count += 1
        }
      }
      firstAttemptAccuracy = Double(firstAttemptCorrect) / Double(max(boundaryCount, 1)) * 100
      firstAttemptMistakes = mistakeReviews(firstDecisions: firstDecisions)
    } else {
      firstAttemptCorrect = Int((accuracy / 100 * Double(boundaryCount)).rounded())
      firstAttemptAccuracy = accuracy
      firstAttemptMistakes = []
    }

    return SpacingGameEvaluation(
      answer: answer,
      submittedText: normalized,
      correctSpaceCount: correct,
      expectedSpaceCount: correctBoundaries.count,
      missedSpaceCount: missed,
      extraSpaceCount: extra,
      accuracyPercent: accuracy,
      firstAttemptCorrectCount: firstAttemptCorrect,
      totalBoundaryCount: boundaryCount,
      firstAttemptAccuracyPercent: firstAttemptAccuracy,
      correctionCount: max(correctionCount, 0),
      firstAttemptMistakes: firstAttemptMistakes,
      score: Int((firstAttemptAccuracy * 10).rounded()),
      activeDuration: max(activeDuration, 0)
    )
  }

  private func mistakeReviews(firstDecisions: [Int: Bool]) -> [SpacingMistakeReview] {
    firstDecisions.keys.sorted().compactMap { boundary in
      guard let choseSpace = firstDecisions[boundary] else { return nil }
      let expectedSpace = correctBoundaries.contains(boundary)
      guard choseSpace != expectedSpace else { return nil }

      let characters = Array(compactText)
      let previousSpace = correctBoundaries.filter { $0 < boundary }.max() ?? 0
      let nextSpace = correctBoundaries.filter { $0 > boundary }.min() ?? characters.count
      let range = previousSpace..<nextSpace
      var attemptedBoundaries = correctBoundaries
      if choseSpace {
        attemptedBoundaries.insert(boundary)
      } else {
        attemptedBoundaries.remove(boundary)
      }
      return SpacingMistakeReview(
        boundary: boundary,
        attemptedText: Self.renderExcerpt(
          characters: characters,
          range: range,
          boundaries: attemptedBoundaries
        ),
        answerText: Self.renderExcerpt(
          characters: characters,
          range: range,
          boundaries: correctBoundaries
        ),
        expectedSpace: expectedSpace
      )
    }
  }

  private static func parse(_ text: String) -> (characters: [Character], boundaries: Set<Int>) {
    var characters: [Character] = []
    var boundaries: Set<Int> = []
    var hasPendingSpace = false

    for character in text {
      if character.isWhitespace {
        hasPendingSpace = !characters.isEmpty
      } else {
        if hasPendingSpace {
          boundaries.insert(characters.count)
        }
        characters.append(character)
        hasPendingSpace = false
      }
    }
    return (characters, boundaries)
  }

  private static func render(characters: [Character], boundaries: Set<Int>) -> String {
    var rendered = ""
    for (index, character) in characters.enumerated() {
      if boundaries.contains(index) {
        rendered.append(" ")
      }
      rendered.append(character)
    }
    return rendered
  }

  private static func renderExcerpt(
    characters: [Character],
    range: Range<Int>,
    boundaries: Set<Int>
  ) -> String {
    var rendered = ""
    for index in range {
      if index > range.lowerBound, boundaries.contains(index) {
        rendered.append(" ")
      }
      rendered.append(characters[index])
    }
    return rendered
  }
}

enum SpacingPlacementOutcome: Equatable {
  case correct
  case incorrect

  var isCorrect: Bool {
    if case .correct = self { return true }
    return false
  }
}

@MainActor
final class SpacingGameViewModel: ObservableObject {
  enum Phase: Equatable {
    case ready
    case running
    case paused
    case finished
  }

  let passage: SpacingPassage
  let engine: SpacingGameEngine

  @Published private(set) var phase: Phase = .ready
  @Published private(set) var draft: String
  @Published private(set) var result: SpacingGameEvaluation?
  @Published private(set) var currentBoundary: Int
  @Published private(set) var decisions: [Int: Bool] = [:]
  @Published private(set) var correctionCount = 0
  @Published private(set) var feedback: SpacingPlacementOutcome?

  private(set) var firstDecisions: [Int: Bool] = [:]

  private var accumulatedDuration: TimeInterval = 0
  private var activeStartedAt: Date?

  init(passage: SpacingPassage) {
    self.passage = passage
    let engine = SpacingGameEngine(answer: passage.text)
    self.engine = engine
    draft = engine.compactText
    currentBoundary = min(1, engine.boundaryCount)
  }

  var selectedBoundaries: Set<Int> {
    Set(decisions.compactMap { $0.value ? $0.key : nil })
  }
  var selectedSpaceCount: Int { selectedBoundaries.count }
  var answeredBoundaryCount: Int { firstDecisions.count }
  var totalBoundaryCount: Int { engine.boundaryCount }
  var canMoveLeft: Bool { currentBoundary > 1 }
  var canMoveRight: Bool { currentBoundary < totalBoundaryCount }
  var currentBoundaryIsSelected: Bool { selectedBoundaries.contains(currentBoundary) }
  var isReadyToFinish: Bool { currentBoundary == totalBoundaryCount }
  func start(at date: Date = Date()) {
    guard phase == .ready else { return }
    phase = .running
    activeStartedAt = date
  }

  func moveLeft() {
    guard phase == .running, canMoveLeft else { return }
    currentBoundary -= 1
    feedback = nil
  }

  func moveRight() {
    guard phase == .running, canMoveRight else { return }
    recordCurrentChoiceIfNeeded()
    currentBoundary += 1
    feedback = nil
  }

  @discardableResult
  func toggleCurrentSpace() -> SpacingPlacementOutcome? {
    guard phase == .running, (1...totalBoundaryCount).contains(currentBoundary) else {
      return nil
    }

    let insertsSpace = !currentBoundaryIsSelected
    if firstDecisions[currentBoundary] == nil {
      firstDecisions[currentBoundary] = insertsSpace
    } else {
      correctionCount += 1
    }
    var updatedDecisions = decisions
    updatedDecisions[currentBoundary] = insertsSpace
    decisions = updatedDecisions
    draft = engine.renderedDraft(boundaries: selectedBoundaries)

    guard insertsSpace else {
      feedback = nil
      return nil
    }
    let outcome: SpacingPlacementOutcome = engine.isCorrectBoundary(currentBoundary)
      ? .correct
      : .incorrect
    feedback = outcome
    return outcome
  }

  func pause(at date: Date = Date()) {
    guard phase == .running else { return }
    accumulatedDuration += elapsedSinceStart(at: date)
    activeStartedAt = nil
    phase = .paused
  }

  func resume(at date: Date = Date()) {
    guard phase == .paused else { return }
    phase = .running
    activeStartedAt = date
  }

  func activeDuration(at date: Date = Date()) -> TimeInterval {
    accumulatedDuration + (phase == .running ? elapsedSinceStart(at: date) : 0)
  }

  @discardableResult
  func submit(at date: Date = Date()) -> SpacingGameEvaluation? {
    guard (phase == .running || phase == .paused), isReadyToFinish else { return result }
    recordCurrentChoiceIfNeeded()
    if phase == .running {
      accumulatedDuration += elapsedSinceStart(at: date)
    }
    activeStartedAt = nil
    let evaluation = engine.evaluate(
      draft: draft,
      activeDuration: accumulatedDuration,
      firstDecisions: firstDecisions,
      correctionCount: correctionCount
    )
    result = evaluation
    phase = .finished
    return evaluation
  }

  func restart(at date: Date = Date()) {
    draft = engine.compactText
    result = nil
    currentBoundary = min(1, engine.boundaryCount)
    decisions = [:]
    firstDecisions = [:]
    correctionCount = 0
    feedback = nil
    accumulatedDuration = 0
    activeStartedAt = date
    phase = .running
  }

  private func elapsedSinceStart(at date: Date) -> TimeInterval {
    guard let activeStartedAt else { return 0 }
    return max(date.timeIntervalSince(activeStartedAt), 0)
  }

  private func recordCurrentChoiceIfNeeded() {
    guard firstDecisions[currentBoundary] == nil else { return }
    firstDecisions[currentBoundary] = currentBoundaryIsSelected
  }
}

struct SpacingPassageListView: View {
  var body: some View {
    ScrollView {
      LazyVStack(alignment: .leading, spacing: 10) {
        Text("spacing.selection.intro")
          .font(.subheadline)
          .foregroundStyle(AppPalette.mutedInk)
          .fixedSize(horizontal: false, vertical: true)

        ForEach(SpacingPassageCatalog.all) { passage in
          NavigationLink {
            SpacingGameView(passage: passage)
          } label: {
            passageCard(passage)
          }
          .buttonStyle(.plain)
          .accessibilityIdentifier("spacing.passage.\(passage.id)")
        }
      }
      .padding(18)
    }
    .background(spacingBackground)
    .navigationTitle(Text("game.mode.spacing"))
    .navigationBarTitleDisplayMode(.inline)
    .accessibilityIdentifier("spacing.selection.screen")
    .rootSettingsToolbar()
  }

  private func passageCard(_ passage: SpacingPassage) -> some View {
    HStack(spacing: 14) {
      Text(
        String(
          format: AppLocalization.string("spacing.selection.level"),
          passage.level
        )
      )
      .font(.system(.caption, design: .rounded, weight: .heavy))
      .foregroundStyle(.teal)
      .frame(width: 58, height: 48)
      .background(
        Color.teal.opacity(0.08 + Double(passage.level) * 0.025),
        in: RoundedRectangle(cornerRadius: 15)
      )

      VStack(alignment: .leading, spacing: 6) {
        Text(passage.titleKey)
          .font(.system(.headline, design: .rounded, weight: .heavy))
          .foregroundStyle(AppPalette.ink)
        Text(
          String(
            format: AppLocalization.string("spacing.selection.metadata"),
            passage.characterCount,
            passage.spaceCount
          )
        )
        .font(.caption)
        .foregroundStyle(AppPalette.mutedInk)
      }
      Spacer()
      Image(systemName: "play.circle.fill")
        .font(.title2)
        .foregroundStyle(.teal)
    }
    .padding(12)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 9, y: 5)
  }
}

struct SpacingGameView: View {
  @Environment(\.dismiss) private var dismiss
  @Environment(\.scenePhase) private var scenePhase
  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @EnvironmentObject private var retention: RetentionLibrary
  @AppStorage(KeyboardPreferenceKeys.hapticsEnabled) private var hapticsEnabled = true
  @AppStorage(SoundPreferenceKeys.effectsEnabled) private var soundEffectsEnabled = true
  @StateObject private var viewModel: SpacingGameViewModel
  @State private var retentionSession = RetentionSessionContext()
  @State private var didRecordCurrentRun = false
  @State private var shakePhase: CGFloat = 0

  init(passage: SpacingPassage) {
    _viewModel = StateObject(wrappedValue: SpacingGameViewModel(passage: passage))
  }

  var body: some View {
    Group {
      if let result = viewModel.result {
        SpacingGameResultView(
          passage: viewModel.passage,
          result: result,
          onRetry: retry,
          onFinish: dismiss.callAsFunction
        )
      } else {
        playContent
      }
    }
    .toolbar(.hidden, for: .tabBar)
    .onAppear {
      viewModel.start()
    }
    .onChange(of: scenePhase) { phase in
      switch phase {
      case .active:
        viewModel.resume()
      case .inactive, .background:
        viewModel.pause()
      @unknown default:
        viewModel.pause()
      }
    }
  }

  private var playContent: some View {
    ScrollViewReader { proxy in
      ScrollView {
        VStack(alignment: .leading, spacing: 16) {
          HStack {
            Label("spacing.play.rule", systemImage: "space")
              .font(.subheadline.weight(.bold))
              .foregroundStyle(AppPalette.ink)
            Spacer()
            TimelineView(.periodic(from: .now, by: 1)) { timeline in
              Label(
                durationLabel(viewModel.activeDuration(at: timeline.date)),
                systemImage: "stopwatch.fill"
              )
              .font(.caption.monospacedDigit().weight(.bold))
              .foregroundStyle(AppPalette.secondary)
            }
          }

          Text("spacing.play.instruction")
            .font(.caption)
            .foregroundStyle(AppPalette.mutedInk)
            .fixedSize(horizontal: false, vertical: true)

          progressCard

          passageCard

          feedbackCard

          if viewModel.isReadyToFinish {
            finishCard
          }
        }
        .padding(18)
      }
      .onChange(of: viewModel.currentBoundary) { boundary in
        withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.22)) {
          proxy.scrollTo(boundaryID(boundary), anchor: .center)
        }
      }
      .onChange(of: viewModel.isReadyToFinish) { isReady in
        guard isReady else { return }
        withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.22)) {
          proxy.scrollTo("spacing-finish-card", anchor: .bottom)
        }
      }
    }
    .safeAreaInset(edge: .bottom) {
      HStack(spacing: 10) {
        navigationButton(
          key: "spacing.play.previous",
          image: "chevron.left",
          isEnabled: viewModel.canMoveLeft,
          identifier: "spacing.control.previous",
          action: goBack
        )

        spaceToggleButton

        navigationButton(
          key: "spacing.play.next",
          image: "chevron.right",
          isEnabled: viewModel.canMoveRight,
          identifier: "spacing.control.next",
          action: goForward
        )
      }
      .padding(.horizontal, 18)
      .padding(.vertical, 12)
      .background(.ultraThinMaterial)
    }
    .background(spacingBackground)
    .navigationTitle(viewModel.passage.titleKey)
    .navigationBarTitleDisplayMode(.inline)
    .navigationBarBackButtonHidden(true)
    .toolbar {
      ToolbarItem(placement: .topBarLeading) {
        Button(action: dismiss.callAsFunction) {
          Image(systemName: "xmark")
        }
        .accessibilityLabel(Text("game.end"))
      }
      ToolbarItem(placement: .topBarTrailing) {
        Button(action: submit) {
          Image(systemName: "checkmark.circle.fill")
            .foregroundStyle(Color.teal)
        }
        .accessibilityLabel(Text("spacing.play.check"))
        .accessibilityIdentifier("spacing.submit")
        .disabled(!viewModel.isReadyToFinish)
        .opacity(viewModel.isReadyToFinish ? 1 : 0.35)
      }
    }
    .overlay(alignment: .topLeading) {
      Text("spacing.play.rule")
        .font(.system(size: 1))
        .opacity(0.01)
        .accessibilityIdentifier("spacing.play.screen")
    }
  }

  private var progressCard: some View {
    VStack(spacing: 8) {
      HStack {
        Text(
          String(
            format: AppLocalization.string("spacing.play.position"),
            viewModel.currentBoundary,
            viewModel.totalBoundaryCount
          )
        )
        .accessibilityIdentifier("spacing.cursor.position")
        Spacer()
        Text(
          String(
            format: AppLocalization.string("spacing.play.answered"),
            viewModel.answeredBoundaryCount,
            viewModel.totalBoundaryCount
          )
        )
        .accessibilityIdentifier("spacing.progress.answered")
      }
      .font(.caption.monospacedDigit().weight(.bold))
      .foregroundStyle(AppPalette.secondary)

      ProgressView(
        value: Double(viewModel.answeredBoundaryCount),
        total: Double(max(viewModel.totalBoundaryCount, 1))
      )
      .tint(Color.teal)
      .accessibilityIdentifier("spacing.progress.bar")
    }
    .padding(.horizontal, 2)
  }

  private var passageCard: some View {
    SpacingTextFlowLayout(horizontalSpacing: 0, verticalSpacing: 8) {
      ForEach(Array(viewModel.engine.compactText.enumerated()), id: \.offset) { index, character in
        HStack(spacing: 0) {
          if index > 0 {
            boundaryMarker(at: index)
          }
          Text(verbatim: String(character))
            .foregroundStyle(AppPalette.ink)
        }
        .id(boundaryID(index))
      }
    }
    .font(.system(.title3, design: .rounded, weight: .semibold))
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(16)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 22))
    .overlay {
      RoundedRectangle(cornerRadius: 22)
        .stroke(Color.teal.opacity(0.35), lineWidth: 1.5)
    }
    .modifier(SpacingGameShakeEffect(animatableData: shakePhase))
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(Text("spacing.play.editor_label"))
    .accessibilityValue(Text(verbatim: viewModel.draft))
    .accessibilityIdentifier("spacing.passage.display")
  }

  @ViewBuilder
  private func boundaryMarker(at boundary: Int) -> some View {
    let isCurrent = boundary == viewModel.currentBoundary
    let hasSpace = viewModel.selectedBoundaries.contains(boundary)
    let expectedSpace = viewModel.engine.isCorrectBoundary(boundary)

    if hasSpace {
      Text(verbatim: "␣")
        .font(.system(.body, design: .rounded, weight: .black))
        .foregroundStyle(expectedSpace ? AppPalette.success : AppPalette.error)
        .padding(.horizontal, 2)
        .background(
          isCurrent ? Color.teal.opacity(0.15) : Color.clear,
          in: RoundedRectangle(cornerRadius: 5)
        )
        .overlay {
          if isCurrent {
            RoundedRectangle(cornerRadius: 5)
              .stroke(Color.teal, lineWidth: 1.5)
          }
        }
    } else {
      Color.clear
        .frame(width: 0, height: 24)
        .overlay {
          if isCurrent {
            Capsule()
              .fill(Color.teal)
              .frame(width: 3, height: 24)
          }
        }
        .zIndex(isCurrent ? 2 : 1)
        .accessibilityHidden(true)
    }
  }

  private var feedbackCard: some View {
    HStack(spacing: 10) {
      feedbackStatus
      Spacer(minLength: 8)
      Text(
        String(
          format: AppLocalization.string("spacing.play.corrections"),
          viewModel.correctionCount
        )
      )
      .font(.caption.monospacedDigit().weight(.bold))
      .foregroundStyle(AppPalette.secondary)
      .accessibilityIdentifier("spacing.corrections")
    }
    .padding(.horizontal, 14)
    .frame(minHeight: 48)
    .background(AppPalette.card.opacity(0.86), in: RoundedRectangle(cornerRadius: 16))
  }

  @ViewBuilder
  private var feedbackStatus: some View {
    switch viewModel.feedback {
    case .correct:
      Label("spacing.play.feedback.correct", systemImage: "checkmark.circle.fill")
        .foregroundStyle(AppPalette.success)
        .accessibilityIdentifier("spacing.feedback.correct")
        .transition(.scale.combined(with: .opacity))
    case .incorrect:
      Label("spacing.play.feedback.incorrect", systemImage: "xmark.circle.fill")
        .foregroundStyle(AppPalette.error)
        .accessibilityIdentifier("spacing.feedback.incorrect")
        .transition(.scale.combined(with: .opacity))
    case nil:
      Label("spacing.play.choose", systemImage: "hand.tap.fill")
      .foregroundStyle(AppPalette.secondary)
      .accessibilityIdentifier("spacing.feedback.ready")
    }
  }

  private var finishCard: some View {
    VStack(alignment: .leading, spacing: 10) {
      Label("spacing.play.finish_title", systemImage: "flag.checkered")
        .font(.headline.weight(.heavy))
        .foregroundStyle(AppPalette.ink)
      Text("spacing.play.finish_detail")
        .font(.caption)
        .foregroundStyle(AppPalette.mutedInk)
      Button(action: submit) {
        Label("spacing.play.finish", systemImage: "checkmark.circle.fill")
          .font(.headline.weight(.heavy))
          .foregroundStyle(.white)
          .frame(maxWidth: .infinity)
          .padding(.vertical, 13)
          .background(Color.teal, in: RoundedRectangle(cornerRadius: 15))
      }
      .buttonStyle(.plain)
      .accessibilityIdentifier("spacing.finish")
    }
    .padding(16)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 20))
    .id("spacing-finish-card")
  }

  private var spaceToggleButton: some View {
    let isSelected = viewModel.currentBoundaryIsSelected
    return Button {
      toggleSpace()
    } label: {
      VStack(spacing: 4) {
        Image(systemName: "space")
          .font(.title3.weight(.heavy))
        Text(
          isSelected
            ? LocalizedStringKey("spacing.play.remove_space")
            : LocalizedStringKey("spacing.play.space")
        )
          .font(.caption.weight(.heavy))
          .lineLimit(1)
          .minimumScaleFactor(0.72)
      }
      .foregroundStyle(.white)
      .frame(maxWidth: .infinity, minHeight: 58)
      .background(
        isSelected ? AppPalette.secondary : Color.teal,
        in: RoundedRectangle(cornerRadius: 17)
      )
      .overlay {
        RoundedRectangle(cornerRadius: 17)
          .stroke(Color.white.opacity(isSelected ? 0.95 : 0), lineWidth: 2.5)
      }
    }
    .buttonStyle(.plain)
    .accessibilityIdentifier("spacing.control.space")
  }

  private func navigationButton(
    key: LocalizedStringKey,
    image: String,
    isEnabled: Bool,
    identifier: String,
    action: @escaping () -> Void
  ) -> some View {
    Button(action: action) {
      VStack(spacing: 4) {
        Image(systemName: image)
          .font(.headline.weight(.heavy))
        Text(key)
          .font(.caption.weight(.bold))
      }
      .foregroundStyle(AppPalette.ink)
      .frame(maxWidth: .infinity, minHeight: 58)
      .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 17))
      .overlay {
        RoundedRectangle(cornerRadius: 17)
          .stroke(AppPalette.accentSoft, lineWidth: 1)
      }
    }
    .buttonStyle(.plain)
    .disabled(!isEnabled)
    .opacity(isEnabled ? 1 : 0.38)
    .accessibilityIdentifier(identifier)
  }

  private var spacingBackground: some View {
    LinearGradient(
      colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
      startPoint: .topLeading,
      endPoint: .bottomTrailing
    )
    .ignoresSafeArea()
  }

  private func submit() {
    guard viewModel.submit() != nil else { return }
    guard !didRecordCurrentRun else { return }
    didRecordCurrentRun = true
    retention.record(.game, session: retentionSession)
  }

  private func retry() {
    retentionSession = RetentionSessionContext()
    didRecordCurrentRun = false
    viewModel.restart()
  }

  private func durationLabel(_ duration: TimeInterval) -> String {
    let seconds = max(Int(duration), 0)
    return String(format: "%d:%02d", seconds / 60, seconds % 60)
  }

  private func boundaryID(_ boundary: Int) -> String {
    "spacing-boundary-\(boundary)"
  }

  private func toggleSpace() {
    let outcome = withAnimation(.easeInOut(duration: 0.16)) {
      viewModel.toggleCurrentSpace()
    }
    guard let outcome else {
      if soundEffectsEnabled {
        HancoSoundEngine.shared.play(.keyTap(.soft, .character))
      }
      return
    }

    if outcome.isCorrect {
      if soundEffectsEnabled {
        HancoSoundEngine.shared.play(.keyTap(.soft, .character))
      }
    } else {
      if soundEffectsEnabled {
        HancoSoundEngine.shared.play(.mistake)
      }
      if !reduceMotion {
        withAnimation(.linear(duration: 0.36)) {
          shakePhase += 1
        }
      }
    }
    SpacingGameHaptics.fire(outcome, enabled: hapticsEnabled)
    UIAccessibility.post(
      notification: .announcement,
      argument: AppLocalization.string(feedbackKey(for: outcome))
    )
  }

  private func goBack() {
    withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.18)) {
      viewModel.moveLeft()
    }
  }

  private func goForward() {
    withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.18)) {
      viewModel.moveRight()
    }
  }

  private func feedbackKey(for outcome: SpacingPlacementOutcome) -> String {
    switch outcome {
    case .correct: "spacing.play.feedback.correct"
    case .incorrect: "spacing.play.feedback.incorrect"
    }
  }
}

private struct SpacingTextFlowLayout: Layout {
  let horizontalSpacing: CGFloat
  let verticalSpacing: CGFloat

  func sizeThatFits(
    proposal: ProposedViewSize,
    subviews: Subviews,
    cache: inout ()
  ) -> CGSize {
    let maximumWidth = proposal.width ?? .infinity
    var lineWidth: CGFloat = 0
    var lineHeight: CGFloat = 0
    var totalHeight: CGFloat = 0
    var widestLine: CGFloat = 0

    for subview in subviews {
      let size = subview.sizeThatFits(.unspecified)
      if lineWidth > 0, lineWidth + horizontalSpacing + size.width > maximumWidth {
        widestLine = max(widestLine, lineWidth)
        totalHeight += lineHeight + verticalSpacing
        lineWidth = 0
        lineHeight = 0
      }
      if lineWidth > 0 { lineWidth += horizontalSpacing }
      lineWidth += size.width
      lineHeight = max(lineHeight, size.height)
    }

    widestLine = max(widestLine, lineWidth)
    totalHeight += lineHeight
    return CGSize(width: proposal.width ?? widestLine, height: totalHeight)
  }

  func placeSubviews(
    in bounds: CGRect,
    proposal: ProposedViewSize,
    subviews: Subviews,
    cache: inout ()
  ) {
    var x = bounds.minX
    var y = bounds.minY
    var lineHeight: CGFloat = 0

    for subview in subviews {
      let size = subview.sizeThatFits(.unspecified)
      if x > bounds.minX, x + horizontalSpacing + size.width > bounds.maxX {
        x = bounds.minX
        y += lineHeight + verticalSpacing
        lineHeight = 0
      }
      if x > bounds.minX { x += horizontalSpacing }
      subview.place(at: CGPoint(x: x, y: y), anchor: .topLeading, proposal: .unspecified)
      x += size.width
      lineHeight = max(lineHeight, size.height)
    }
  }
}

private struct SpacingGameShakeEffect: GeometryEffect {
  var animatableData: CGFloat

  func effectValue(size: CGSize) -> ProjectionTransform {
    let offset = sin(animatableData * .pi * 8) * 7
    return ProjectionTransform(CGAffineTransform(translationX: offset, y: 0))
  }
}

@MainActor
private enum SpacingGameHaptics {
  private static let notificationGenerator = UINotificationFeedbackGenerator()

  static func fire(_ outcome: SpacingPlacementOutcome, enabled: Bool) {
    guard enabled else { return }
    if outcome.isCorrect {
      notificationGenerator.notificationOccurred(.success)
    } else {
      notificationGenerator.notificationOccurred(.error)
    }
    notificationGenerator.prepare()
  }
}

private struct SpacingGameResultView: View {
  let passage: SpacingPassage
  let result: SpacingGameEvaluation
  let onRetry: () -> Void
  let onFinish: () -> Void

  var body: some View {
    SessionResultView(
      navigationTitle: "spacing.result.navigation_title",
      onFinish: onFinish,
      header: { _ in headerCard },
      score: scoreCard,
      metrics: metricsCard,
      review: { _ in answerCard },
      actions: { _ in EmptyView() },
      bottomBar: { _ in retryBar }
    )
  }

  private var headerCard: some View {
    HStack(spacing: 16) {
      GrowingMascotView(
        mood: result.isPerfect ? .cheer : .oops,
        reaction: result.isPerfect ? .wordCompleted : .mistake,
        reactionRevision: 1,
        showsNameTag: true,
        size: 62
      )
      .frame(width: 82, height: 124)

      VStack(alignment: .leading, spacing: 7) {
        Text(passage.titleKey)
          .font(.caption.weight(.bold))
          .foregroundStyle(AppPalette.secondary)
        Text(
          result.isPerfect
            ? LocalizedStringKey("spacing.result.perfect")
            : LocalizedStringKey("spacing.result.complete")
        )
          .font(.system(.title2, design: .rounded, weight: .heavy))
          .foregroundStyle(AppPalette.ink)
          .accessibilityIdentifier("spacing.result.screen")
        HStack(alignment: .firstTextBaseline, spacing: 6) {
          Text(verbatim: result.rank)
            .font(.system(size: 58, weight: .black, design: .rounded))
            .foregroundStyle(rankColor)
          Text("game.result.rank")
            .font(.caption.weight(.bold))
            .foregroundStyle(AppPalette.mutedInk)
        }
      }
      Spacer()
    }
    .padding(20)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
    .shadow(color: AppPalette.keyShadow, radius: 14, y: 8)
  }

  private func scoreCard(_ reveal: SessionResultRevealState) -> some View {
    VStack(spacing: 5) {
      Text("game.score")
        .font(.caption.weight(.bold))
        .foregroundStyle(AppPalette.mutedInk)
      Text(verbatim: Int(Double(result.score) * reveal.scoreProgress).formatted())
        .font(.system(size: 44, weight: .black, design: .rounded))
        .foregroundStyle(Color.teal)
        .monospacedDigit()
        .accessibilityIdentifier("spacing.result.score")
    }
    .frame(maxWidth: .infinity)
    .padding(16)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
  }

  private func metricsCard(_ reveal: SessionResultRevealState) -> some View {
    let firstProgress = reveal.metricProgress(at: 0)
    let finalProgress = reveal.metricProgress(at: 1)
    let correctionProgress = reveal.metricProgress(at: 2)
    let timeProgress = reveal.metricProgress(at: 3)
    return VStack(spacing: 12) {
      HStack(spacing: 0) {
        SessionResultMetric(
          value: percentLabel(result.firstAttemptAccuracyPercent * firstProgress),
          label: "spacing.result.first_accuracy",
          progress: firstProgress * result.firstAttemptAccuracyPercent / 100,
          identifier: "spacing.result.first_accuracy",
          tint: AppPalette.accent
        )
        Divider().frame(height: 64)
        SessionResultMetric(
          value: percentLabel(result.accuracyPercent * finalProgress),
          label: "spacing.result.final_accuracy",
          progress: finalProgress * result.accuracyPercent / 100,
          identifier: "spacing.result.final_accuracy",
          tint: AppPalette.success
        )
      }
      Divider()
      HStack(spacing: 0) {
        SessionResultMetric(
          value: String(Int(Double(result.correctionCount) * correctionProgress)),
          label: "spacing.result.corrections",
          progress: correctionProgress * min(Double(result.correctionCount) / 10, 1),
          identifier: "spacing.result.corrections",
          tint: AppPalette.error
        )
        Divider().frame(height: 64)
        SessionResultMetric(
          value: String(Int(result.activeDuration * timeProgress)),
          label: "spacing.result.seconds",
          progress: timeProgress * min(result.activeDuration / 180, 1),
          identifier: "spacing.result.duration",
          tint: AppPalette.secondary
        )
      }
    }
    .padding(.horizontal, 8)
    .padding(.vertical, 15)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
  }

  private var answerCard: some View {
    VStack(alignment: .leading, spacing: 12) {
      Label(
        result.firstAttemptMistakes.isEmpty
          ? LocalizedStringKey("spacing.result.answer_perfect")
          : LocalizedStringKey("spacing.result.review_title"),
        systemImage: result.firstAttemptMistakes.isEmpty ? "crown.fill" : "arrow.triangle.2.circlepath"
      )
      .font(.headline.weight(.bold))
      .foregroundStyle(result.firstAttemptMistakes.isEmpty ? Color.orange : AppPalette.ink)

      if !result.firstAttemptMistakes.isEmpty {
        VStack(alignment: .leading, spacing: 9) {
          ForEach(result.firstAttemptMistakes.prefix(8)) { mistake in
            mistakeReviewRow(mistake)
          }
          if result.firstAttemptMistakes.count > 8 {
            Text(
              String(
                format: AppLocalization.string("spacing.result.more_mistakes"),
                result.firstAttemptMistakes.count - 8
              )
            )
            .font(.caption.weight(.bold))
            .foregroundStyle(AppPalette.mutedInk)
          }
        }
        .accessibilityIdentifier("spacing.result.review")
      }

      if result.missedSpaceCount > 0 || result.extraSpaceCount > 0 {
        HStack(spacing: 10) {
          resultPill("spacing.result.missed", value: result.missedSpaceCount)
          resultPill("spacing.result.extra", value: result.extraSpaceCount)
        }
      }

      Divider()
      Label("spacing.result.answer_title", systemImage: "text.badge.checkmark")
        .font(.subheadline.weight(.bold))
        .foregroundStyle(AppPalette.ink)

      Text(verbatim: result.answer)
        .font(.system(.body, design: .rounded, weight: .semibold))
        .foregroundStyle(AppPalette.ink)
        .lineSpacing(5)
        .textSelection(.enabled)
        .accessibilityIdentifier("spacing.result.answer")
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(17)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 21, style: .continuous))
  }

  private func mistakeReviewRow(_ mistake: SpacingMistakeReview) -> some View {
    VStack(alignment: .leading, spacing: 5) {
      HStack(alignment: .firstTextBaseline, spacing: 7) {
        Text(verbatim: mistake.attemptedText)
          .foregroundStyle(AppPalette.error)
          .strikethrough(true, color: AppPalette.error)
        Image(systemName: "arrow.right")
          .font(.caption.weight(.bold))
          .foregroundStyle(AppPalette.mutedInk)
        Text(verbatim: mistake.answerText)
          .foregroundStyle(AppPalette.success)
      }
      .font(.system(.body, design: .rounded, weight: .semibold))
      Text(
        mistake.expectedSpace
          ? LocalizedStringKey("spacing.result.expected_space")
          : LocalizedStringKey("spacing.result.expected_attach")
      )
      .font(.caption2.weight(.bold))
      .foregroundStyle(AppPalette.mutedInk)
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(10)
    .background(AppPalette.accentSoft.opacity(0.18), in: RoundedRectangle(cornerRadius: 12))
  }

  private func resultPill(_ key: LocalizedStringKey, value: Int) -> some View {
    HStack(spacing: 5) {
      Text(key)
      Text(verbatim: value.formatted())
        .monospacedDigit()
    }
    .font(.caption.weight(.bold))
    .foregroundStyle(AppPalette.error)
    .padding(.horizontal, 10)
    .padding(.vertical, 6)
    .background(AppPalette.error.opacity(0.1), in: Capsule())
  }

  private var retryBar: some View {
    Button(action: onRetry) {
      Label("practice.result.retry", systemImage: "arrow.counterclockwise")
        .font(.headline.weight(.bold))
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(Color.teal, in: RoundedRectangle(cornerRadius: 17))
    }
    .accessibilityIdentifier("spacing.result.retry")
  }

  private var rankColor: Color {
    switch result.rank {
    case "S": Color.orange
    case "A": AppPalette.accent
    case "B": AppPalette.secondary
    default: AppPalette.mutedInk
    }
  }

  private func percentLabel(_ value: Double) -> String {
    "\(Int(value.rounded()))%"
  }
}

private var spacingBackground: some View {
  LinearGradient(
    colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
    startPoint: .topLeading,
    endPoint: .bottomTrailing
  )
  .ignoresSafeArea()
}
