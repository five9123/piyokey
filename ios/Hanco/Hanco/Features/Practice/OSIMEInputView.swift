import HangulEngine
import SwiftUI
import UIKit

enum KoreanKeyboardAvailability {
  static var isAvailable: Bool {
    #if DEBUG
      if let override = ProcessInfo.processInfo.environment["UITEST_KOREAN_KEYBOARD_AVAILABLE"] {
        return override == "1"
      }
    #endif
    return containsKorean(languages: UITextInputMode.activeInputModes.map(\.primaryLanguage))
  }

  static func containsKorean(languages: [String?]) -> Bool {
    languages.contains { $0?.lowercased().hasPrefix("ko") == true }
  }
}

struct SessionInputModeControl: View {
  @Binding var selection: SessionInputMode
  let onUnavailableOSIME: () -> Void

  var body: some View {
    HStack(spacing: 7) {
      modeButton(
        .builtIn,
        title: "input_mode.builtin",
        systemImage: "rectangle.grid.3x2.fill"
      )
      modeButton(
        .osIME,
        title: "input_mode.os_ime",
        systemImage: "keyboard"
      )
    }
    .padding(5)
    .background(AppPalette.card.opacity(0.92), in: RoundedRectangle(cornerRadius: 15))
  }

  private func modeButton(
    _ mode: SessionInputMode,
    title: LocalizedStringKey,
    systemImage: String
  ) -> some View {
    Button {
      if mode == .osIME, !KoreanKeyboardAvailability.isAvailable {
        onUnavailableOSIME()
      } else {
        selection = mode
      }
    } label: {
      Label(title, systemImage: systemImage)
        .font(.caption.weight(.bold))
        .foregroundStyle(selection == mode ? Color.white : AppPalette.mutedInk)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 9)
        .background(
          selection == mode ? AppPalette.accent : Color.clear,
          in: RoundedRectangle(cornerRadius: 11)
        )
    }
    .buttonStyle(.plain)
    .accessibilityIdentifier("input_mode.\(mode.rawValue)")
    .accessibilityAddTraits(selection == mode ? .isSelected : [])
  }
}

struct OSIMEInputPanel: View {
  @Environment(\.scenePhase) private var scenePhase
  @Environment(\.accessibilityReduceMotion) private var reduceMotion

  let target: String
  let candidateTargets: [String]
  let acceptedText: String
  let resetRevision: Int
  let onInputStart: () -> Void
  let onAcceptedSequence: ([Character]) -> Void
  let onAcceptedCandidateSequence: ((String, [Character]) -> Void)?
  let onConfirmedMismatch: () -> Void
  let showsChrome: Bool
  let showsFocusRecovery: Bool
  let isFocusSuspended: Bool

  @State private var fieldText = ""
  @State private var focusRevision = 0
  @State private var isFieldFocused = false
  @State private var showsInputSourceWarning = false
  @State private var inputSourceWarningFeedbackRevision = 0
  @State private var inputSourceWarningShakeStep: CGFloat = 0
  @State private var isInputSourceWarningEmphasized = false

  init(
    target: String,
    candidateTargets: [String] = [],
    acceptedText: String,
    resetRevision: Int,
    onInputStart: @escaping () -> Void = {},
    onAcceptedSequence: @escaping ([Character]) -> Void,
    onAcceptedCandidateSequence: ((String, [Character]) -> Void)? = nil,
    onConfirmedMismatch: @escaping () -> Void,
    showsChrome: Bool = true,
    showsFocusRecovery: Bool = false,
    isFocusSuspended: Bool = false
  ) {
    self.target = target
    self.candidateTargets = candidateTargets
    self.acceptedText = acceptedText
    self.resetRevision = resetRevision
    self.onInputStart = onInputStart
    self.onAcceptedSequence = onAcceptedSequence
    self.onAcceptedCandidateSequence = onAcceptedCandidateSequence
    self.onConfirmedMismatch = onConfirmedMismatch
    self.showsChrome = showsChrome
    self.showsFocusRecovery = showsFocusRecovery
    self.isFocusSuspended = isFocusSuspended
  }

  var body: some View {
    ZStack(alignment: .top) {
      if showsChrome {
        visibleInputPanel
      } else if showsFocusRecovery {
        compactInputPanel
      } else {
        inputField
          .frame(maxWidth: .infinity, maxHeight: .infinity)
          .contentShape(Rectangle())
      }

      if showsInputSourceWarning {
        OSIMEInputSourceBanner(isEmphasized: isInputSourceWarningEmphasized)
          .padding(.horizontal, 12)
          .padding(.top, 10)
          .transition(.move(edge: .top).combined(with: .opacity))
          .modifier(
            OSIMEInputSourceWarningShakeEffect(
              animatableData: inputSourceWarningShakeStep
            )
          )
          .allowsHitTesting(false)
      }
    }
    .animation(.easeOut(duration: 0.18), value: showsInputSourceWarning)
    .onAppear {
      fieldText = acceptedText
      if !isFocusSuspended { requestFocus() }
    }
    .onChange(of: resetRevision) { _ in
      fieldText = acceptedText
      showsInputSourceWarning = false
      if !isFocusSuspended { requestFocus() }
    }
    .onChange(of: scenePhase) { phase in
      guard phase == .active, !isFocusSuspended else { return }
      requestFocus()
    }
    .onChange(of: isFocusSuspended) { suspended in
      if !suspended { requestFocus() }
    }
    .task(id: inputSourceWarningFeedbackRevision) {
      guard inputSourceWarningFeedbackRevision > 0 else { return }
      withAnimation(.easeOut(duration: 0.1)) {
        isInputSourceWarningEmphasized = true
      }
      try? await Task.sleep(nanoseconds: 420_000_000)
      guard !Task.isCancelled else { return }
      withAnimation(.easeOut(duration: 0.18)) {
        isInputSourceWarningEmphasized = false
      }
    }
  }

  private var compactInputPanel: some View {
    ZStack {
      inputField
        .frame(maxWidth: .infinity, maxHeight: .infinity)

      Label(
        "os_ime.input.title",
        systemImage: isFieldFocused ? "keyboard.fill" : "keyboard.badge.ellipsis"
      )
      .font(.caption.weight(.bold))
      .foregroundStyle(isFieldFocused ? AppPalette.secondary : AppPalette.accent)
      .padding(.horizontal, 14)
      .allowsHitTesting(false)
      .accessibilityIdentifier("os_ime.input.recovery")
    }
    .frame(maxWidth: .infinity, minHeight: 48)
    .background(AppPalette.card.opacity(0.96), in: RoundedRectangle(cornerRadius: 15))
    .overlay {
      RoundedRectangle(cornerRadius: 15)
        .stroke(AppPalette.accent.opacity(isFieldFocused ? 0.26 : 0.55), lineWidth: 1.5)
        .allowsHitTesting(false)
    }
    .contentShape(Rectangle())
  }

  private var visibleInputPanel: some View {
    VStack(spacing: 9) {
      HStack(spacing: 8) {
        Image(systemName: "keyboard.badge.ellipsis")
          .foregroundStyle(AppPalette.accent)
        Text("os_ime.input.title")
          .font(.caption.weight(.bold))
          .foregroundStyle(AppPalette.ink)
        Spacer()
        Text("os_ime.input.live_badge")
          .font(.caption2.weight(.bold))
          .foregroundStyle(AppPalette.secondary)
      }

      ZStack {
        inputField.frame(height: 50)

        HStack(spacing: 8) {
          Text(verbatim: fieldText.isEmpty ? "…" : fieldText)
            .font(.system(.title3, design: .rounded, weight: .bold))
            .foregroundStyle(fieldText.isEmpty ? AppPalette.mutedInk : AppPalette.ink)
            .lineLimit(1)
          Spacer()
          Image(systemName: "cursorarrow.click.2")
            .foregroundStyle(AppPalette.secondary)
        }
        .padding(.horizontal, 14)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
      }
      .background(AppPalette.backgroundTop, in: RoundedRectangle(cornerRadius: 14))
      .overlay {
        RoundedRectangle(cornerRadius: 14)
          .stroke(AppPalette.accent.opacity(0.42), lineWidth: 1.5)
      }

      Text("os_ime.input.hint")
        .font(.caption2)
        .foregroundStyle(AppPalette.mutedInk)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
    .padding(.horizontal, 14)
    .padding(.vertical, 11)
    .background(.ultraThinMaterial)
    .accessibilityIdentifier("os_ime.input.chrome")
  }

  private var inputField: some View {
    IMETextField(
      text: $fieldText,
      resetText: acceptedText,
      targets: candidateTargets.isEmpty ? [target] : candidateTargets,
      resetRevision: resetRevision,
      focusRevision: focusRevision,
      isFocusSuspended: isFocusSuspended,
      isFocused: $isFieldFocused,
      onReturn: requestFocus,
      onFocusRecovery: requestFocus,
      onTextChange: evaluate(committedText:markedText:)
    )
  }

  private func requestFocus() {
    focusRevision &+= 1
  }

  private func evaluate(committedText: String, markedText: String?) {
    if HancoSoundWarmupPolicy.shouldPrepareForOSIMEInput(
      committedText: committedText,
      markedText: markedText
    ) {
      onInputStart()
    }

    if !candidateTargets.isEmpty,
      let onAcceptedCandidateSequence,
      let selection = OSIMECandidateTextJudge.evaluate(
        targets: candidateTargets,
        preferredTarget: target,
        committedText: committedText,
        markedText: markedText
      )
    {
      switch selection.evaluation.status {
      case .matching, .composingMismatch:
        dismissInputSourceWarning()
        onAcceptedCandidateSequence(selection.target, selection.evaluation.acceptedSequence)
      case .unsupportedASCIIInput:
        handleUnsupportedASCIIInput(acceptedSequence: selection.evaluation.acceptedSequence)
      case .confirmedMismatch:
        dismissInputSourceWarning()
        onAcceptedCandidateSequence(selection.target, selection.evaluation.acceptedSequence)
        onConfirmedMismatch()
        fieldText = HangulComposer.compose(selection.evaluation.acceptedSequence).text
      }
      return
    }

    guard
      let evaluation = try? OSIMETextJudge.evaluate(
        target: target,
        committedText: committedText,
        markedText: markedText
      )
    else { return }

    switch evaluation.status {
    case .matching, .composingMismatch:
      dismissInputSourceWarning()
      onAcceptedSequence(evaluation.acceptedSequence)
    case .unsupportedASCIIInput:
      handleUnsupportedASCIIInput(acceptedSequence: evaluation.acceptedSequence)
    case .confirmedMismatch:
      dismissInputSourceWarning()
      onAcceptedSequence(evaluation.acceptedSequence)
      onConfirmedMismatch()
      fieldText = HangulComposer.compose(evaluation.acceptedSequence).text
    }
  }

  private func handleUnsupportedASCIIInput(acceptedSequence: [Character]) {
    let shouldEmphasizeWarning = showsInputSourceWarning
    showsInputSourceWarning = true
    fieldText = HangulComposer.compose(acceptedSequence).text

    guard shouldEmphasizeWarning else { return }
    inputSourceWarningFeedbackRevision &+= 1
    if !reduceMotion {
      withAnimation(.linear(duration: 0.3)) {
        inputSourceWarningShakeStep += 1
      }
    }
  }

  private func dismissInputSourceWarning() {
    showsInputSourceWarning = false
    isInputSourceWarningEmphasized = false
  }
}

struct OSIMEInputSourceBanner: View {
  let isEmphasized: Bool

  var body: some View {
    HStack(spacing: 10) {
      Image(systemName: "globe")
        .font(.headline.weight(.bold))
        .foregroundStyle(Color.orange)
      VStack(alignment: .leading, spacing: 2) {
        Text("os_ime.input_source_warning.title")
          .font(.subheadline.weight(.bold))
          .foregroundStyle(AppPalette.ink)
        Text("os_ime.input_source_warning.detail")
          .font(.caption)
          .foregroundStyle(AppPalette.mutedInk)
          .fixedSize(horizontal: false, vertical: true)
      }
      Spacer(minLength: 0)
    }
    .padding(.horizontal, 14)
    .padding(.vertical, 11)
    .background(
      Color.orange.opacity(isEmphasized ? 0.34 : 0.16),
      in: RoundedRectangle(cornerRadius: 15)
    )
    .overlay {
      RoundedRectangle(cornerRadius: 15)
        .stroke(
          Color.orange.opacity(isEmphasized ? 0.9 : 0.45),
          lineWidth: isEmphasized ? 2.5 : 1
        )
    }
    .accessibilityElement(children: .combine)
    .accessibilityIdentifier("os_ime.input_source_warning")
  }
}

private struct OSIMEInputSourceWarningShakeEffect: GeometryEffect {
  var animatableData: CGFloat

  func effectValue(size: CGSize) -> ProjectionTransform {
    ProjectionTransform(
      CGAffineTransform(
        translationX: 9 * sin(animatableData * .pi * 6),
        y: 0
      )
    )
  }
}

struct OSIMECandidateTextSelection: Equatable {
  let target: String
  let evaluation: OSIMETextEvaluation
}

enum OSIMECandidateTextJudge {
  static func evaluate(
    targets: [String],
    preferredTarget: String,
    committedText: String,
    markedText: String? = nil
  ) -> OSIMECandidateTextSelection? {
    let evaluations = targets.enumerated().compactMap { index, target in
      try? Candidate(
        index: index,
        target: target,
        evaluation: OSIMETextJudge.evaluate(
          target: target,
          committedText: committedText,
          markedText: markedText
        )
      )
    }
    guard !evaluations.isEmpty else { return nil }

    let viable = evaluations.filter { !$0.isConfirmedMismatch }
    let pool = viable.isEmpty ? evaluations : viable
    let chosen = pool.max { lhs, rhs in
      lhs.preferenceScore(preferredTarget: preferredTarget)
        < rhs.preferenceScore(preferredTarget: preferredTarget)
    }
    guard let chosen else { return nil }
    return OSIMECandidateTextSelection(
      target: chosen.target,
      evaluation: chosen.evaluation
    )
  }

  private struct Candidate {
    let index: Int
    let target: String
    let evaluation: OSIMETextEvaluation

    var isConfirmedMismatch: Bool {
      if case .confirmedMismatch = evaluation.status { return true }
      return false
    }

    private var isCompleted: Bool {
      if case .matching(let completed, _) = evaluation.status { return completed }
      return false
    }

    func preferenceScore(preferredTarget: String) -> (Int, Int, Int, Int) {
      (
        isCompleted ? 1 : 0,
        evaluation.acceptedSequence.count,
        target == preferredTarget ? 1 : 0,
        -index
      )
    }
  }
}

struct OSIMEUnavailableBanner: View {
  var body: some View {
    Label("os_ime.unavailable.session_message", systemImage: "exclamationmark.triangle.fill")
      .font(.caption.weight(.semibold))
      .foregroundStyle(AppPalette.ink)
      .frame(maxWidth: .infinity, alignment: .leading)
      .padding(.horizontal, 13)
      .padding(.vertical, 10)
      .background(Color.orange.opacity(0.16), in: RoundedRectangle(cornerRadius: 13))
      .accessibilityIdentifier("os_ime.unavailable.banner")
  }
}

struct KoreanKeyboardGuideView: View {
  @Environment(\.dismiss) private var dismiss

  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(spacing: 16) {
          GrowingMascotView(interactive: true, size: 52, calm: true)
            .frame(width: 82, height: 126)
          Text("os_ime.guide.title")
            .font(.system(.title2, design: .rounded, weight: .bold))
            .foregroundStyle(AppPalette.ink)
            .multilineTextAlignment(.center)
          Text("os_ime.guide.subtitle")
            .font(.subheadline)
            .foregroundStyle(AppPalette.mutedInk)
            .multilineTextAlignment(.center)

          guideScreenshot(
            step: 1,
            systemImage: "gearshape.fill",
            title: "os_ime.guide.step_1.title",
            detail: "os_ime.guide.step_1.detail"
          )
          guideScreenshot(
            step: 2,
            systemImage: "keyboard.fill",
            title: "os_ime.guide.step_2.title",
            detail: "os_ime.guide.step_2.detail"
          )
          guideScreenshot(
            step: 3,
            systemImage: "globe.asia.australia.fill",
            title: "os_ime.guide.step_3.title",
            detail: "os_ime.guide.step_3.detail"
          )
        }
        .padding(20)
      }
      .background(AppPalette.backgroundTop.ignoresSafeArea())
      .toolbar {
        ToolbarItem(placement: .confirmationAction) {
          Button("common.close", action: dismiss.callAsFunction)
        }
      }
    }
    .presentationDetents([.medium, .large])
    .accessibilityIdentifier("os_ime.guide.sheet")
  }

  private func guideScreenshot(
    step: Int,
    systemImage: String,
    title: LocalizedStringKey,
    detail: LocalizedStringKey
  ) -> some View {
    HStack(spacing: 14) {
      Text(verbatim: String(step))
        .font(.headline.monospacedDigit().weight(.black))
        .foregroundStyle(.white)
        .frame(width: 34, height: 34)
        .background(AppPalette.accent, in: Circle())
      Image(systemName: systemImage)
        .font(.title2)
        .foregroundStyle(AppPalette.secondary)
        .frame(width: 38)
      VStack(alignment: .leading, spacing: 3) {
        Text(title)
          .font(.subheadline.weight(.bold))
          .foregroundStyle(AppPalette.ink)
        Text(detail)
          .font(.caption)
          .foregroundStyle(AppPalette.mutedInk)
      }
      Spacer()
    }
    .padding(15)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 19))
    .overlay(alignment: .topLeading) {
      Text("os_ime.guide.preview")
        .font(.system(size: 8, weight: .bold))
        .foregroundStyle(AppPalette.mutedInk)
        .padding(.horizontal, 6)
        .padding(.vertical, 3)
        .background(AppPalette.backgroundBottom, in: Capsule())
        .offset(x: 10, y: -7)
    }
  }
}

private func moveCursorToEnd(of textField: UITextField) {
  textField.selectedTextRange = textField.textRange(
    from: textField.endOfDocument,
    to: textField.endOfDocument
  )
}

struct IMETextField: UIViewRepresentable {
  @Binding var text: String
  /// Document to restore when `resetRevision` advances.
  ///
  /// This is the model's accepted text rather than `text`, because `text` is a
  /// `@State` mirror that a reset is still in the middle of clearing. Reading
  /// the model prop makes the reset independent of the order in which SwiftUI
  /// runs the panel's `onChange` and this representable's `updateUIView`.
  let resetText: String
  /// Current target documents used only to avoid mistaking a legitimate first
  /// post-reset prefix for an abandoned previous-target snapshot.
  let targets: [String]
  let resetRevision: Int
  let focusRevision: Int
  let isFocusSuspended: Bool
  @Binding var isFocused: Bool
  let onReturn: () -> Void
  let onFocusRecovery: () -> Void
  let onTextChange: (String, String?) -> Void

  func makeCoordinator() -> Coordinator {
    Coordinator(parent: self)
  }

  func makeUIView(context: Context) -> UITextField {
    let textField = UITextField()
    textField.delegate = context.coordinator
    textField.addTarget(
      context.coordinator,
      action: #selector(Coordinator.textDidChange(_:)),
      for: .editingChanged
    )
    textField.autocorrectionType = .no
    textField.spellCheckingType = .no
    textField.smartInsertDeleteType = .no
    textField.smartQuotesType = .no
    textField.smartDashesType = .no
    textField.returnKeyType = .done
    textField.textColor = .clear
    textField.tintColor = .clear
    textField.backgroundColor = .clear
    textField.accessibilityLabel = AppLocalization.string("os_ime.input.accessibility_label")
    textField.accessibilityIdentifier = "os_ime.text_field"
    return textField
  }

  func updateUIView(_ textField: UITextField, context: Context) {
    context.coordinator.parent = self
    context.coordinator.isMounted = true
    if context.coordinator.lastResetRevision != resetRevision {
      context.coordinator.lastResetRevision = resetRevision
      context.coordinator.scheduleSessionReset(
        in: textField,
        replacingWith: resetText,
        revision: resetRevision
      )
    } else if !context.coordinator.hasPendingReset,
      textField.text != text,
      textField.markedTextRange == nil
    {
      textField.text = text
      moveCursorToEnd(of: textField)
    }
    if context.coordinator.lastFocusSuspended != isFocusSuspended {
      context.coordinator.lastFocusSuspended = isFocusSuspended
      if isFocusSuspended {
        DispatchQueue.main.async { [weak textField, weak coordinator = context.coordinator] in
          guard let textField, let coordinator, coordinator.isMounted else { return }
          textField.resignFirstResponder()
        }
      }
    }
    if !isFocusSuspended, context.coordinator.lastFocusRevision != focusRevision {
      context.coordinator.lastFocusRevision = focusRevision
      let requestedRevision = focusRevision
      DispatchQueue.main.async { [weak textField, weak coordinator = context.coordinator] in
        guard let textField, let coordinator,
          coordinator.isMounted,
          coordinator.lastFocusRevision == requestedRevision,
          !coordinator.parent.isFocusSuspended,
          textField.window != nil
        else { return }
        textField.becomeFirstResponder()
        moveCursorToEnd(of: textField)
      }
    }
  }

  static func dismantleUIView(_ textField: UITextField, coordinator: Coordinator) {
    coordinator.isMounted = false
    textField.resignFirstResponder()
  }

  final class Coordinator: NSObject, UITextFieldDelegate {
    var parent: IMETextField
    var lastResetRevision = -1
    var lastFocusRevision = -1
    var lastFocusSuspended: Bool?
    var isMounted = true
    private(set) var hasPendingReset = false
    private var resetGeneration = 0
    private var resetDocument = ""
    private var observedDocuments: Set<String> = []
    private var staleDocuments: Set<String> = []
    private var isAwaitingPostResetInput = false
    /// True while a deferred reset is rewriting the document.
    ///
    /// The rewrite goes through `UITextInput`, so UIKit echoes it back as
    /// `.editingChanged`. Those echoes are ours, not the user's, and must never
    /// reach the judge or the view model.
    private(set) var isApplyingReset = false

    init(parent: IMETextField) {
      self.parent = parent
      lastResetRevision = parent.resetRevision
    }

    /// Defers composition disposal until UIKit has returned from the keyboard's
    /// current editing callback. Rewriting a `UITextInput` synchronously from
    /// that callback can clear the field while leaving the Korean keyboard's
    /// private composition buffer alive.
    func scheduleSessionReset(
      in textField: UITextField,
      replacingWith replacementText: String,
      revision: Int,
      preservingStaleDocuments: Bool = false
    ) {
      resetGeneration &+= 1
      let generation = resetGeneration
      hasPendingReset = true
      resetDocument = replacementText

      if !preservingStaleDocuments {
        staleDocuments = observedDocuments
        staleDocuments.formUnion(Self.compositionSnapshots(for: textField.text ?? ""))
        staleDocuments.remove(replacementText)
        observedDocuments.removeAll()
        isAwaitingPostResetInput = true
      }

      DispatchQueue.main.async { [weak self, weak textField] in
        guard let self, let textField,
          self.isMounted,
          self.resetGeneration == generation,
          self.lastResetRevision == revision
        else { return }
        self.performSessionReset(in: textField, replacingWith: replacementText)
      }
    }

    private func performSessionReset(
      in textField: UITextField,
      replacingWith replacementText: String
    ) {
      let shouldRestartSession = textField.isFirstResponder && !parent.isFocusSuspended

      isApplyingReset = true
      defer {
        isApplyingReset = false
        hasPendingReset = false
      }

      if shouldRestartSession {
        textField.resignFirstResponder()
      }
      if let markedRange = textField.markedTextRange {
        textField.replace(markedRange, withText: "")
      }
      textField.unmarkText()

      let inputDelegate = textField.inputDelegate
      inputDelegate?.selectionWillChange(textField)
      inputDelegate?.textWillChange(textField)
      textField.text = replacementText
      parent.text = replacementText
      moveCursorToEnd(of: textField)
      inputDelegate?.textDidChange(textField)
      inputDelegate?.selectionDidChange(textField)

      if shouldRestartSession, isMounted, textField.window != nil {
        textField.becomeFirstResponder()
        moveCursorToEnd(of: textField)

        let generation = resetGeneration
        DispatchQueue.main.async { [weak self, weak textField] in
          guard let self, let textField,
            self.isMounted,
            self.resetGeneration == generation,
            !self.parent.isFocusSuspended,
            textField.window != nil,
            !textField.isFirstResponder
          else { return }
          // The panel advances focusRevision here so SwiftUI's existing focus
          // path retries a failed responder restart.
          self.parent.onFocusRecovery()
        }
      }
    }

    @objc func textDidChange(_ textField: UITextField) {
      guard !isApplyingReset, !hasPendingReset else { return }
      let fullText = textField.text ?? ""

      if isAwaitingPostResetInput {
        if fullText == resetDocument {
          return
        }
        if containsPriorTargetMaterial(in: fullText),
          !isValidPostResetDocument(fullText)
        {
          // A keyboard may publish the abandoned previous-word snapshot after
          // the deferred reset. Never expose it to the judge. Clean it on a
          // later runloop as well, so this callback remains read-only.
          scheduleSessionReset(
            in: textField,
            replacingWith: resetDocument,
            revision: lastResetRevision,
            preservingStaleDocuments: true
          )
          return
        }
        isAwaitingPostResetInput = false
        staleDocuments.removeAll()
      }

      observedDocuments.insert(fullText)
      parent.text = fullText

      guard let markedRange = textField.markedTextRange else {
        parent.onTextChange(fullText, nil)
        return
      }

      let start = textField.offset(from: textField.beginningOfDocument, to: markedRange.start)
      let length = textField.offset(from: markedRange.start, to: markedRange.end)
      let utf16 = fullText as NSString
      guard start >= 0, length >= 0, start + length <= utf16.length else {
        parent.onTextChange(fullText, nil)
        return
      }
      let before = utf16.substring(to: start)
      let marked = utf16.substring(with: NSRange(location: start, length: length))
      let after = utf16.substring(from: start + length)
      parent.onTextChange(before + after, marked)
    }

    private func containsPriorTargetMaterial(in text: String) -> Bool {
      staleDocuments.contains { stale in
        guard stale != resetDocument,
          let sequence = try? JamoDecomposer.keySequence(for: stale),
          sequence.count >= 2
        else { return false }
        return text.contains(stale)
      }
    }

    private func isValidPostResetDocument(_ text: String) -> Bool {
      parent.targets.contains { target in
        guard let evaluation = try? OSIMETextJudge.evaluate(
          target: target,
          committedText: text
        ) else { return false }
        switch evaluation.status {
        case .matching, .composingMismatch:
          return true
        case .unsupportedASCIIInput, .confirmedMismatch:
          return false
        }
      }
    }

    private static func compositionSnapshots(for text: String) -> Set<String> {
      guard !text.isEmpty else { return [] }
      var snapshots: Set<String> = [text]
      for count in 1...text.count {
        snapshots.insert(String(text.prefix(count)))
      }
      if let sequence = try? JamoDecomposer.keySequence(for: text) {
        for count in 1...sequence.count {
          snapshots.insert(HangulComposer.compose(Array(sequence.prefix(count))).text)
        }
      }
      return snapshots
    }

    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
      parent.onReturn()
      return false
    }

    func textFieldDidBeginEditing(_ textField: UITextField) {
      parent.isFocused = true
    }

    func textFieldDidEndEditing(_ textField: UITextField) {
      parent.isFocused = false
    }

    func textFieldDidChangeSelection(_ textField: UITextField) {
      guard !isApplyingReset,
        !hasPendingReset,
        textField.markedTextRange == nil,
        textField.selectedTextRange?.end != textField.endOfDocument
      else { return }
      textField.selectedTextRange = textField.textRange(
        from: textField.endOfDocument,
        to: textField.endOfDocument
      )
    }
  }
}
