import SwiftUI
import UIKit
import XCTest

@testable import Hanco

/// TYP-68 regression: advancing `resetRevision` between game targets must
/// discard the previous target's committed and marked OS IME input, keep the
/// field focused, and deliver no stale `onTextChange` event to the judge.
@MainActor
final class OSIMEInputResetTests: XCTestCase {
  private final class RestartProbeTextField: UITextField {
    var allowsBecomeFirstResponder = true
    private(set) var becomeCallCount = 0
    private(set) var resignCallCount = 0

    override func becomeFirstResponder() -> Bool {
      becomeCallCount += 1
      guard allowsBecomeFirstResponder else { return false }
      return super.becomeFirstResponder()
    }

    override func resignFirstResponder() -> Bool {
      resignCallCount += 1
      return super.resignFirstResponder()
    }
  }

  private final class HarnessModel: ObservableObject {
    @Published var resetRevision = 0
    @Published var acceptedText = ""
    @Published var isFocused = false
    @Published var targets = ["고기"]
    var receivedChanges: [(committed: String, marked: String?)] = []
    var onTextChange: ((String, String?) -> Void)?
  }

  private struct Harness: View {
    @ObservedObject var model: HarnessModel
    @State private var fieldText = ""

    var body: some View {
      IMETextField(
        text: $fieldText,
        resetText: model.acceptedText,
        targets: model.targets,
        resetRevision: model.resetRevision,
        focusRevision: 1,
        isFocusSuspended: false,
        isFocused: $model.isFocused,
        onReturn: {},
        onFocusRecovery: {},
        onTextChange: { committed, marked in
          model.receivedChanges.append((committed, marked))
          model.onTextChange?(committed, marked)
        }
      )
      .frame(width: 280, height: 44)
    }
  }

  private var window: UIWindow!
  private var host: UIHostingController<Harness>!
  private var model: HarnessModel!

  override func setUp() async throws {
    try await super.setUp()
    model = HarnessModel()
    host = UIHostingController(rootView: Harness(model: model))
    window = UIWindow(frame: CGRect(x: 0, y: 0, width: 320, height: 200))
    window.rootViewController = host
    window.makeKeyAndVisible()
    host.view.setNeedsLayout()
    host.view.layoutIfNeeded()
    pumpRunLoop()
  }

  override func tearDown() async throws {
    window.rootViewController = nil
    window.isHidden = true
    window = nil
    host = nil
    model = nil
    try await super.tearDown()
  }

  private func pumpRunLoop(times: Int = 4) {
    for _ in 0..<times {
      RunLoop.current.run(until: Date().addingTimeInterval(0.02))
    }
  }

  private func findTextField() throws -> UITextField {
    func search(_ view: UIView) -> UITextField? {
      if let field = view as? UITextField { return field }
      for subview in view.subviews {
        if let found = search(subview) { return found }
      }
      return nil
    }
    return try XCTUnwrap(search(host.view), "hosted IMETextField not found")
  }

  private func advanceTarget(acceptedText: String = "", targets: [String]? = nil) {
    model.receivedChanges.removeAll()
    model.acceptedText = acceptedText
    if let targets { model.targets = targets }
    model.resetRevision += 1
    pumpRunLoop()
  }

  func testResetDiscardsMarkedCompositionWithoutCommittingIt() throws {
    let field = try findTextField()
    field.becomeFirstResponder()
    pumpRunLoop()

    // Simulate an abandoned in-flight composition from the previous target.
    field.setMarkedText("나", selectedRange: NSRange(location: 1, length: 0))
    pumpRunLoop()
    XCTAssertEqual(field.text, "나")
    XCTAssertNotNil(field.markedTextRange)

    advanceTarget()

    // The marked syllable is discarded, not committed into the new target.
    XCTAssertEqual(field.text, "")
    XCTAssertNil(field.markedTextRange)
    // The reset itself must not surface any judge event.
    XCTAssertTrue(model.receivedChanges.isEmpty)
  }

  func testResetDiscardsCommittedTextFromPreviousTarget() throws {
    let field = try findTextField()
    field.becomeFirstResponder()
    pumpRunLoop()

    field.insertText("가")
    pumpRunLoop()
    XCTAssertEqual(field.text, "가")

    advanceTarget()

    XCTAssertEqual(field.text, "")
    XCTAssertNil(field.markedTextRange)
    XCTAssertTrue(model.receivedChanges.isEmpty)
  }

  func testResetKeepsFirstResponderFocus() throws {
    let field = try findTextField()
    field.becomeFirstResponder()
    pumpRunLoop()
    XCTAssertTrue(field.isFirstResponder)

    field.setMarkedText("소", selectedRange: NSRange(location: 1, length: 0))
    pumpRunLoop()

    advanceTarget()

    XCTAssertTrue(field.isFirstResponder)
    XCTAssertTrue(model.isFocused)
  }

  func testResetRestoresNonEmptyAcceptedText() throws {
    let field = try findTextField()
    field.becomeFirstResponder()
    pumpRunLoop()

    field.setMarkedText("낫", selectedRange: NSRange(location: 1, length: 0))
    pumpRunLoop()

    // A session restore can hand the field a non-empty accepted document.
    advanceTarget(acceptedText: "소")

    XCTAssertEqual(field.text, "소")
    XCTAssertNil(field.markedTextRange)
    XCTAssertTrue(model.receivedChanges.isEmpty)
  }

  func testRestoredPrefixDoesNotSuppressFirstConfirmedMismatch() throws {
    let field = try findTextField()
    field.becomeFirstResponder()
    pumpRunLoop()

    field.text = "가나다"
    field.sendActions(for: .editingChanged)
    pumpRunLoop()

    advanceTarget(acceptedText: "가나", targets: ["고기"])
    XCTAssertEqual(field.text, "가나")

    // The restored prefix overlaps the prior document. The user's new ㄴ is
    // still a real committed mismatch and must reach the judge exactly once.
    field.text = "가나ㄴ"
    field.sendActions(for: .editingChanged)
    pumpRunLoop()

    XCTAssertEqual(model.receivedChanges.count, 1)
    XCTAssertEqual(model.receivedChanges[0].committed, "가나ㄴ")
    XCTAssertNil(model.receivedChanges[0].marked)
  }

  func testFailedResponderRestartRequestsFocusRevisionRecovery() {
    var recoveryCount = 0
    let parent = IMETextField(
      text: .constant(""),
      resetText: "",
      targets: ["고기"],
      resetRevision: 1,
      focusRevision: 1,
      isFocusSuspended: false,
      isFocused: .constant(false),
      onReturn: {},
      onFocusRecovery: { recoveryCount += 1 },
      onTextChange: { _, _ in }
    )
    let coordinator = IMETextField.Coordinator(parent: parent)
    let field = RestartProbeTextField(frame: CGRect(x: 0, y: 0, width: 280, height: 44))
    window.addSubview(field)
    XCTAssertTrue(field.becomeFirstResponder())
    XCTAssertTrue(field.isFirstResponder)

    field.allowsBecomeFirstResponder = false
    // Other tests cover the DispatchQueue boundary. Invoke its body directly
    // here so no unrelated hosted responder can race this fallback assertion.
    coordinator.performSessionReset(in: field, replacingWith: "")

    XCTAssertEqual(recoveryCount, 1)
    XCTAssertFalse(field.isFirstResponder)
    XCTAssertGreaterThanOrEqual(field.resignCallCount, 1)
  }

  func testSuspendedFocusResetDoesNotCycleResponderSession() {
    let activeParent = IMETextField(
      text: .constant(""),
      resetText: "",
      targets: ["고기"],
      resetRevision: 1,
      focusRevision: 1,
      isFocusSuspended: false,
      isFocused: .constant(false),
      onReturn: {},
      onFocusRecovery: {},
      onTextChange: { _, _ in }
    )
    let coordinator = IMETextField.Coordinator(parent: activeParent)
    let field = RestartProbeTextField(frame: CGRect(x: 0, y: 0, width: 280, height: 44))
    window.addSubview(field)
    XCTAssertTrue(field.becomeFirstResponder())
    XCTAssertTrue(field.isFirstResponder)

    coordinator.parent = IMETextField(
      text: .constant(""),
      resetText: "",
      targets: ["고기"],
      resetRevision: 1,
      focusRevision: 1,
      isFocusSuspended: true,
      isFocused: .constant(false),
      onReturn: {},
      onFocusRecovery: {},
      onTextChange: { _, _ in }
    )
    let becomeCalls = field.becomeCallCount
    let resignCalls = field.resignCallCount
    field.text = "좋다"

    coordinator.performSessionReset(in: field, replacingWith: "")

    XCTAssertEqual(field.text, "")
    XCTAssertEqual(field.becomeCallCount, becomeCalls)
    XCTAssertEqual(field.resignCallCount, resignCalls)
  }

  func testFirstJamoAfterResetIsDeliveredCleanly() throws {
    let field = try findTextField()
    field.becomeFirstResponder()
    pumpRunLoop()

    field.setMarkedText("나", selectedRange: NSRange(location: 1, length: 0))
    pumpRunLoop()

    advanceTarget()

    // The user's first keystroke on the new target starts from an empty
    // document and reaches the judge without the abandoned syllable.
    field.insertText("ㅅ")
    field.sendActions(for: .editingChanged)
    pumpRunLoop()

    let last = try XCTUnwrap(model.receivedChanges.last)
    XCTAssertEqual(last.committed + (last.marked ?? ""), "ㅅ")
  }

  func testResetIsDeferredOutsideReentrantEditingChanged() throws {
    let field = try findTextField()
    field.becomeFirstResponder()
    pumpRunLoop()

    model.onTextChange = { [weak model] committed, _ in
      guard committed == "좋다", let model else { return }
      model.acceptedText = ""
      model.resetRevision += 1
    }

    field.text = "좋다"
    field.sendActions(for: .editingChanged)

    // The editing callback may advance the target, but must not mutate the
    // UITextInput until that callback has returned to the main runloop.
    XCTAssertEqual(field.text, "좋다")

    pumpRunLoop()

    XCTAssertEqual(field.text, "")
    XCTAssertTrue(field.isFirstResponder)
  }

  func testPriorWordMinusLastJamoRewriteIsDiscardedWithoutJudgeEvent() throws {
    let field = try findTextField()
    field.becomeFirstResponder()
    pumpRunLoop()

    field.text = "좋다"
    field.sendActions(for: .editingChanged)
    pumpRunLoop()
    advanceTarget()

    // ㅈㅗㅎㄷ: the build-10 failure rewrites this previous-target prefix
    // after the field has already been cleared for 고기.
    field.text = "좋ㄷ"
    field.sendActions(for: .editingChanged)

    XCTAssertTrue(model.receivedChanges.isEmpty)
    pumpRunLoop()
    XCTAssertEqual(field.text, "")
    XCTAssertTrue(model.receivedChanges.isEmpty)
  }

  func testFirstValidNewTargetInputIsPreservedAfterStaleRewrite() throws {
    let field = try findTextField()
    field.becomeFirstResponder()
    pumpRunLoop()

    field.text = "좋다"
    field.sendActions(for: .editingChanged)
    pumpRunLoop()
    advanceTarget()

    field.text = "좋ㄷ"
    field.sendActions(for: .editingChanged)
    pumpRunLoop()
    XCTAssertTrue(model.receivedChanges.isEmpty)

    field.text = "ㄱ"
    field.sendActions(for: .editingChanged)
    pumpRunLoop()

    XCTAssertEqual(model.receivedChanges.count, 1)
    XCTAssertEqual(model.receivedChanges[0].committed, "ㄱ")
    XCTAssertNil(model.receivedChanges[0].marked)
  }

  func testRestartWindowSuppressesEditingAndSelectionEchoes() throws {
    var receivedChanges: [(String, String?)] = []
    let parent = IMETextField(
      text: .constant(""),
      resetText: "",
      targets: ["고기"],
      resetRevision: 1,
      focusRevision: 1,
      isFocusSuspended: false,
      isFocused: .constant(false),
      onReturn: {},
      onFocusRecovery: {},
      onTextChange: { receivedChanges.append(($0, $1)) }
    )
    let coordinator = IMETextField.Coordinator(parent: parent)
    let field = UITextField(frame: CGRect(x: 0, y: 0, width: 280, height: 44))
    host.view.addSubview(field)
    field.text = "좋다"
    field.selectedTextRange = field.textRange(
      from: field.beginningOfDocument,
      to: field.beginningOfDocument
    )

    coordinator.scheduleSessionReset(in: field, replacingWith: "", revision: 1)
    XCTAssertTrue(coordinator.hasPendingReset)

    coordinator.textDidChange(field)
    coordinator.textFieldDidChangeSelection(field)

    XCTAssertTrue(receivedChanges.isEmpty)
    XCTAssertEqual(
      field.offset(from: field.beginningOfDocument, to: field.selectedTextRange!.start),
      0
    )

    pumpRunLoop()
    XCTAssertEqual(field.text, "")
  }

  func testFiveDirectInputGamePathsDiscardPriorTargetRewriteAndAcceptFirstJamo() throws {
    let field = try findTextField()
    field.becomeFirstResponder()
    pumpRunLoop()

    let paths: [(name: String, target: String, firstJamo: String)] = [
      ("flow", "고기", "ㄱ"),
      ("acid_rain", "산성비", "ㅅ"),
      ("choseong", "초성", "ㅊ"),
      ("word_match", "단어", "ㄷ"),
      ("dictation", "받아쓰기", "ㅂ"),
    ]

    for path in paths {
      field.text = "좋다"
      field.sendActions(for: .editingChanged)
      pumpRunLoop()
      advanceTarget(targets: [path.target])

      field.text = "좋ㄷ"
      field.sendActions(for: .editingChanged)
      pumpRunLoop()
      XCTAssertTrue(model.receivedChanges.isEmpty, path.name)

      field.text = path.firstJamo
      field.sendActions(for: .editingChanged)
      pumpRunLoop()
      XCTAssertEqual(model.receivedChanges.last?.committed, path.firstJamo, path.name)
      XCTAssertTrue(field.isFirstResponder, path.name)
    }
  }

  func testTenConsecutiveTargetTransitionsDiscardStaleRewritesAndPreserveFirstInput() throws {
    let field = try findTextField()
    field.becomeFirstResponder()
    pumpRunLoop()

    for _ in 0..<10 {
      field.text = "좋다"
      field.sendActions(for: .editingChanged)
      pumpRunLoop()

      advanceTarget()

      XCTAssertEqual(field.text, "")
      XCTAssertNil(field.markedTextRange)
      XCTAssertTrue(model.receivedChanges.isEmpty)
      XCTAssertTrue(field.isFirstResponder)

      field.text = "좋ㄷ"
      field.sendActions(for: .editingChanged)
      pumpRunLoop()
      XCTAssertEqual(field.text, "")
      XCTAssertTrue(model.receivedChanges.isEmpty)

      field.text = "ㄱ"
      field.sendActions(for: .editingChanged)
      pumpRunLoop()
      XCTAssertEqual(model.receivedChanges.last?.committed, "ㄱ")
      XCTAssertTrue(field.isFirstResponder)
    }
  }

  func testDelayedEditingChangedAfterResetDeliversResetDocumentNotStaleText() throws {
    let field = try findTextField()
    field.becomeFirstResponder()
    pumpRunLoop()

    field.setMarkedText("나", selectedRange: NSRange(location: 1, length: 0))
    pumpRunLoop()

    advanceTarget()

    // A late .editingChanged echo delivered after the reset observes the reset
    // document, so it cannot re-credit the abandoned syllable to the judge.
    field.sendActions(for: .editingChanged)
    pumpRunLoop()

    for change in model.receivedChanges {
      XCTAssertEqual(change.committed, "")
      XCTAssertNil(change.marked)
    }
  }
}
