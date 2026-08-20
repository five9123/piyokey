import XCTest
@testable import Hanco

@MainActor
final class SessionShareCardTests: XCTestCase {
  private var originalLanguage: String?

  override func setUp() {
    super.setUp()
    originalLanguage = UserDefaults.standard.string(forKey: SettingsPreferenceKeys.language)
    UserDefaults.standard.set(
      AppLanguage.japanese.rawValue,
      forKey: SettingsPreferenceKeys.language
    )
  }

  override func tearDown() {
    if let originalLanguage {
      UserDefaults.standard.set(originalLanguage, forKey: SettingsPreferenceKeys.language)
    } else {
      UserDefaults.standard.removeObject(forKey: SettingsPreferenceKeys.language)
    }
    super.tearDown()
  }

  func testRendererCreatesOpaqueSquareImageForSocialPreview() async throws {
    let model = makeModel()

    let image = try XCTUnwrap(SessionShareCardRenderer.render(model))
    XCTAssertEqual(image.size, SessionShareCardRenderer.logicalSize)
    XCTAssertEqual(image.scale, SessionShareCardRenderer.scale)
    XCTAssertEqual(image.cgImage?.width, 1200)
    XCTAssertEqual(image.cgImage?.height, 1200)
    let encoded = await SessionSharePNGEncoder.encode(image)
    XCTAssertGreaterThan(try XCTUnwrap(encoded).count, 50_000)
    XCTAssertEqual(model.sessionTitle, "フローモード")
    XCTAssertEqual(model.sessionSubtitle, "初級")

    let attachment = XCTAttachment(image: image)
    attachment.name = "f9-share-card-game-ja"
    attachment.lifetime = .keepAlways
    add(attachment)
  }

  func testJapaneseShareCopyContainsNoEnglishFallbacks() {
    XCTAssertEqual(AppLocalization.string("app.name"), "ピヨキー")
    XCTAssertEqual(AppLocalization.string("result.share.eyebrow"), "今日のハングル記録")
    XCTAssertEqual(AppLocalization.string("result.share.practice_badge"), "レッスンクリア")
    XCTAssertEqual(AppLocalization.string("result.share.game_badge"), "%@ランク")
    XCTAssertEqual(
      AppLocalization.string("result.share.download_cta"),
      "ピヨキーで韓国語タイピングを楽しもう"
    )
    XCTAssertEqual(
      AppLocalization.string("result.share.download_detail"),
      "好きな言葉で、ハングルがもっと身近に。"
    )
  }

  func testRendererMainActorWorkStaysWithinInteractionBudget() throws {
    let startedAt = ProcessInfo.processInfo.systemUptime
    let image = SessionShareCardRenderer.render(makeModel())
    let elapsed = ProcessInfo.processInfo.systemUptime - startedAt

    XCTAssertNotNil(image)
    XCTAssertLessThan(
      elapsed,
      0.2,
      "MainActor share rendering took \(elapsed)s; keep it below the interaction freeze budget"
    )
  }

  func testPNGEncoderProducesTransportReadyPNG() async throws {
    let image = try XCTUnwrap(SessionShareCardRenderer.render(makeModel()))
    let encoded = await SessionSharePNGEncoder.encode(image)
    let data = try XCTUnwrap(encoded)

    XCTAssertEqual(Array(data.prefix(8)), [137, 80, 78, 71, 13, 10, 26, 10])
  }

  func testPNGEncodingPerformance() throws {
    let image = try XCTUnwrap(SessionShareCardRenderer.render(makeModel()))
    let cgImage = try XCTUnwrap(image.cgImage)
    var encoded: Data?

    measure(metrics: [XCTClockMetric(), XCTMemoryMetric()]) {
      autoreleasepool {
        encoded = SessionSharePNGEncoder.encode(cgImage)
      }
    }

    XCTAssertGreaterThan(try XCTUnwrap(encoded).count, 50_000)
  }

  func testSharePreparationRejectsDuplicateTapUntilCurrentWorkFinishes() {
    var gate = SessionShareSingleFlightGate()

    XCTAssertTrue(gate.begin())
    XCTAssertFalse(gate.begin())
    gate.finish()
    XCTAssertTrue(gate.begin())
  }

  func testProgressPresentationDelayFitsWithinOneDisplayFrameAt60HzPlusMargin() {
    XCTAssertGreaterThan(SessionSharePreparationPolicy.progressPresentationDelayNanoseconds, 0)
    XCTAssertLessThanOrEqual(
      SessionSharePreparationPolicy.progressPresentationDelayNanoseconds,
      25_000_000
    )
  }

  private func makeModel() -> SessionShareCardModel {
    SessionShareCardModel(
      sessionTitle: "フローモード",
      sessionSubtitle: "初級",
      achievement: String(
        format: AppLocalization.string("result.share.game_badge"),
        "S"
      ),
      scoreLabel: "スコア",
      scoreValue: "12,340",
      metrics: [
        SessionShareCardMetric(
          id: "accuracy",
          label: "正確度",
          value: "98%",
          systemImage: "scope"
        ),
        SessionShareCardMetric(
          id: "combo",
          label: "最大コンボ",
          value: "24",
          systemImage: "flame.fill"
        ),
        SessionShareCardMetric(
          id: "streak",
          label: "連続日数",
          value: "7日",
          systemImage: "seal.fill"
        ),
      ],
      caption: "#ピヨキー"
    )
  }
}
