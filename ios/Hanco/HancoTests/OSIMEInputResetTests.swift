import SwiftUI
import UIKit
import XCTest

@testable import Hanco

/// TYP-68 regression: advancing `resetRevision` between game targets must
/// discard the previous target's committed and marked OS IME input, keep the
/// field focused, and deliver no stale `onTextChange` event to the judge.
@MainActor
final class OSIMEInputResetTests: XCTestCase {
  private final class HarnessModel: ObservableObject {
    @Published var resetRevision = 0
    @Published var acceptedText = ""
    var receivedChanges: [(committed: String, marked: String?)] = []
  }

  private struct Harness: View {
    @ObservedObject var model: HarnessModel
    @State private var fieldText = ""

    var body: some View {
      IMETextField(
        text: $fieldText,
        resetText: model.acceptedText,
        resetRevision: model.resetRevision,
        focusRevision: 1,
        isFocusSuspended: false,
        isFocused: .constant(false),
        onReturn: {},
        onTextChange: { committed, marked in
          model.receivedChanges.append((committed, marked))
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

  private func advanceTarget(acceptedText: String = "") {
    model.receivedChanges.removeAll()
    model.acceptedText = acceptedText
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

  func testTenConsecutiveTargetTransitionsStartEmpty() throws {
    let field = try findTextField()
    field.becomeFirstResponder()
    pumpRunLoop()

    for _ in 0..<10 {
      field.insertText("가")
      field.setMarkedText("나", selectedRange: NSRange(location: 1, length: 0))
      pumpRunLoop()

      advanceTarget()

      XCTAssertEqual(field.text, "")
      XCTAssertNil(field.markedTextRange)
      XCTAssertTrue(model.receivedChanges.isEmpty)
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
