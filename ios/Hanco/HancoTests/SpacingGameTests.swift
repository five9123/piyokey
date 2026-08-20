import XCTest

@testable import Hanco

final class SpacingGameEngineTests: XCTestCase {
  func testEngineRemovesSpacesAndRestoresOnlyValidWhitespaceEdits() {
    let engine = SpacingGameEngine(answer: "오늘은 날씨가 좋아요.")

    XCTAssertEqual(engine.compactText, "오늘은날씨가좋아요.")
    XCTAssertEqual(engine.normalizedDraft("오늘은  날씨가\n좋아요."), "오늘은 날씨가 좋아요.")
    XCTAssertEqual(engine.normalizedDraft("오늘은　날씨가 좋아요."), "오늘은 날씨가 좋아요.")
    XCTAssertNil(engine.normalizedDraft("오늘은 날씨도 좋아요."))
    XCTAssertNil(engine.normalizedDraft("오늘은 날씨가 좋아요"))
  }

  func testEvaluationCountsCorrectMissedAndExtraBoundaries() {
    let engine = SpacingGameEngine(answer: "나는 오늘 학교에 걸어서 간다.")
    let result = engine.evaluate(
      draft: "나는오 늘 학교에걸어서 간다.",
      activeDuration: 12
    )

    XCTAssertEqual(result.correctSpaceCount, 2)
    XCTAssertEqual(result.expectedSpaceCount, 4)
    XCTAssertEqual(result.missedSpaceCount, 2)
    XCTAssertEqual(result.extraSpaceCount, 1)
    XCTAssertEqual(result.accuracyPercent, 40, accuracy: 0.001)
    XCTAssertEqual(result.score, 400)
    XCTAssertEqual(result.rank, "C")
    XCTAssertFalse(result.isPerfect)
  }

  func testPerfectEvaluationGetsFullScoreAndSRank() {
    let engine = SpacingGameEngine(answer: "띄어쓰기를 정확하게 넣어요.")
    let result = engine.evaluate(draft: engine.answer, activeDuration: 9)

    XCTAssertEqual(result.accuracyPercent, 100, accuracy: 0.001)
    XCTAssertEqual(result.score, 1_000)
    XCTAssertEqual(result.rank, "S")
    XCTAssertTrue(result.isPerfect)
  }

  func testFirstDecisionsDriveScoreAndProduceContextualMistakeReview() {
    let engine = SpacingGameEngine(answer: "나는 학교에 간다.")
    var firstDecisions = Dictionary(
      uniqueKeysWithValues: (1...engine.boundaryCount).map { boundary in
        (boundary, engine.isCorrectBoundary(boundary))
      }
    )
    firstDecisions[1] = true

    let result = engine.evaluate(
      draft: engine.answer,
      activeDuration: 12,
      firstDecisions: firstDecisions,
      correctionCount: 1
    )

    XCTAssertEqual(result.firstAttemptCorrectCount, engine.boundaryCount - 1)
    XCTAssertEqual(result.totalBoundaryCount, engine.boundaryCount)
    XCTAssertEqual(result.firstAttemptAccuracyPercent, 600.0 / 7.0, accuracy: 0.001)
    XCTAssertEqual(result.score, 857)
    XCTAssertEqual(result.correctionCount, 1)
    XCTAssertEqual(
      result.firstAttemptMistakes,
      [
        SpacingMistakeReview(
          boundary: 1,
          attemptedText: "나 는",
          answerText: "나는",
          expectedSpace: false
        )
      ]
    )
    XCTAssertEqual(result.accuracyPercent, 100, accuracy: 0.001)
    XCTAssertFalse(result.isPerfect)
  }

  func testBundledPassagesStayWithinRequestedLengthAndAreOriginalSpacingExercises() {
    let passages = SpacingPassageCatalog.all
    XCTAssertEqual(passages.count, 6)
    XCTAssertEqual(Set(passages.map(\.id)).count, 6)
    XCTAssertEqual(passages.map(\.level), Array(1...6))
    XCTAssertTrue(
      zip(passages, passages.dropFirst()).allSatisfy { pair in
        pair.0.characterCount < pair.1.characterCount
      }
    )
    XCTAssertGreaterThanOrEqual(passages[5].characterCount, 180)

    for passage in passages {
      XCTAssertTrue((100...200).contains(passage.characterCount), passage.id)
      XCTAssertGreaterThanOrEqual(passage.spaceCount, 30, passage.id)
      XCTAssertFalse(passage.text.contains("  "), passage.id)
      XCTAssertEqual(
        SpacingGameEngine(answer: passage.text).answer,
        passage.text,
        passage.id
      )
    }
  }
}

@MainActor
final class SpacingGameViewModelTests: XCTestCase {
  func testViewModelMovesManuallyAndTogglesSpaceAtCurrentBoundary() {
    let passage = SpacingPassage(
      id: "test",
      titleKey: "spacing.passage.morning",
      text: "나는 학교에 간다."
    )
    let model = SpacingGameViewModel(passage: passage)
    let origin = Date(timeIntervalSince1970: 10_000)

    model.start(at: origin)
    XCTAssertEqual(model.currentBoundary, 1)
    XCTAssertFalse(model.canMoveLeft)
    XCTAssertEqual(
      model.toggleCurrentSpace(),
      .incorrect
    )
    XCTAssertEqual(model.feedback, .incorrect)
    XCTAssertEqual(model.draft, "나 는학교에간다.")
    XCTAssertEqual(model.currentBoundary, 1)

    XCTAssertNil(model.toggleCurrentSpace())
    XCTAssertNil(model.feedback)
    XCTAssertEqual(model.draft, "나는학교에간다.")
    XCTAssertEqual(model.correctionCount, 1)
    XCTAssertEqual(model.firstDecisions[1], true)

    model.moveRight()
    XCTAssertEqual(model.currentBoundary, 2)
    XCTAssertEqual(
      model.toggleCurrentSpace(),
      .correct
    )
    XCTAssertEqual(model.feedback, .correct)
    XCTAssertEqual(model.draft, "나는 학교에간다.")
    XCTAssertEqual(model.answeredBoundaryCount, 2)

    model.pause(at: origin.addingTimeInterval(5))
    XCTAssertEqual(model.activeDuration(at: origin.addingTimeInterval(100)), 5, accuracy: 0.001)
    model.resume(at: origin.addingTimeInterval(100))
    while model.canMoveRight {
      let shouldHaveSpace = model.engine.isCorrectBoundary(model.currentBoundary)
      if model.currentBoundaryIsSelected != shouldHaveSpace {
        _ = model.toggleCurrentSpace()
      }
      model.moveRight()
    }
    let shouldHaveFinalSpace = model.engine.isCorrectBoundary(model.currentBoundary)
    if model.currentBoundaryIsSelected != shouldHaveFinalSpace {
      _ = model.toggleCurrentSpace()
    }
    XCTAssertTrue(model.isReadyToFinish)
    let result = model.submit(at: origin.addingTimeInterval(103))

    XCTAssertEqual(result?.activeDuration ?? -1, 8, accuracy: 0.001)
    XCTAssertEqual(model.phase, .finished)
  }

  func testRestartClearsAnswerAndStartsFreshRun() {
    let passage = SpacingPassage(
      id: "test",
      titleKey: "spacing.passage.morning",
      text: "오늘 날씨가 좋다."
    )
    let model = SpacingGameViewModel(passage: passage)
    let origin = Date(timeIntervalSince1970: 20_000)

    model.start(at: origin)
    for boundary in 1...model.totalBoundaryCount {
      let expectedSpace = model.engine.isCorrectBoundary(boundary)
      if expectedSpace {
        XCTAssertEqual(model.toggleCurrentSpace(), .correct)
      }
      if boundary < model.totalBoundaryCount {
        model.moveRight()
      }
    }
    XCTAssertTrue(model.isReadyToFinish)
    XCTAssertTrue(model.submit(at: origin.addingTimeInterval(3))?.isPerfect == true)

    model.restart(at: origin.addingTimeInterval(10))
    XCTAssertEqual(model.phase, .running)
    XCTAssertNil(model.result)
    XCTAssertEqual(model.draft, "오늘날씨가좋다.")
    XCTAssertEqual(model.currentBoundary, 1)
    XCTAssertTrue(model.decisions.isEmpty)
    XCTAssertTrue(model.firstDecisions.isEmpty)
    XCTAssertTrue(model.selectedBoundaries.isEmpty)
    XCTAssertEqual(model.correctionCount, 0)
    XCTAssertNil(model.feedback)
    XCTAssertEqual(model.activeDuration(at: origin.addingTimeInterval(12)), 2, accuracy: 0.001)
  }
}
