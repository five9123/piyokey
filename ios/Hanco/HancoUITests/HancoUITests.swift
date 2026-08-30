import XCTest

final class HancoUITests: XCTestCase {
  private var app: XCUIApplication!
  private let regularRecommendationCardMinimumHeight: CGFloat = 146

  // Global-suffixed tests use real localized UI. Other regression tests stay Japanese.
  private var storeCaptureLanguage: String {
    if name.contains("GlobalEN") { return "en" }
    if name.contains("GlobalKO") { return "ko" }
    if name.contains("GlobalES") { return "es" }
    if name.contains("GlobalDE") { return "de" }
    if name.contains("GlobalFR") { return "fr" }
    return "ja"
  }

  private func storeText(_ ja: String, _ en: String, _ ko: String) -> String {
    let translations: [String: [String: String]] = [
      "es": ["4-day streak": "Racha de 4 días", "MY PIYO": "MI PIYO", "End practice": "Terminar práctica",
             "Game": "Juego", "Profile": "Perfil", "Piyo": "Piyo", "Practice": "Práctica"],
      "de": ["4-day streak": "4-Tage-Serie", "MY PIYO": "MEIN PIYO", "End practice": "Übung beenden",
             "Game": "Spiel", "Profile": "Profil", "Piyo": "Piyo", "Practice": "Üben"],
      "fr": ["4-day streak": "Série de 4 jours", "MY PIYO": "MON PIYO", "End practice": "Terminer l’exercice",
             "Game": "Jeu", "Profile": "Profil", "Piyo": "Piyo", "Practice": "S’entraîner"],
    ]
    if let language = translations[storeCaptureLanguage] {
      guard let value = language[en] else {
        XCTFail("Missing store capture label: \(storeCaptureLanguage):\(en)")
        return en
      }
      return value
    }
    switch storeCaptureLanguage {
    case "en": return en
    case "ko": return en
    default: return ja
    }
  }

  private func storeScene(_ scene: String, hold: TimeInterval = 0) {
    guard name.contains("testAppStoreScreenshotGlobal") else { return }
    print("STORE_SCENE \(scene) \(Date().timeIntervalSince1970)")
    if hold > 0 { Thread.sleep(forTimeInterval: hold) }
  }

  override func setUp() {
    super.setUp()
    continueAfterFailure = false
    XCUIDevice.shared.orientation = .portrait
    let isAppPreviewCapture = name.contains("testAppPreview")
    let isAppStoreScreenshotCapture = name.contains("testAppStoreScreenshot")
    let isMarketingCapture = isAppPreviewCapture || isAppStoreScreenshotCapture
    let isOSIMEAudioLatencyTest = name.contains(
      "testOSIMERapidCorrectFeedbackStartsWithoutQueueingOldSounds"
    )
    app = makeApplication(
      resetKeyboardPreferences: !isAppPreviewCapture,
      gameDuration: isMarketingCapture ? 60 : nil,
      flowStartIndex: isMarketingCapture ? 0 : nil,
      koreanKeyboardAvailable: isOSIMEAudioLatencyTest ? true : nil,
      audioProbe: isOSIMEAudioLatencyTest,
      seedsAppStoreCaptureState: isAppStoreScreenshotCapture
    )
    if isOSIMEAudioLatencyTest {
      app.launchEnvironment["UITEST_SEED_PRACTICE_DECK"] = "1"
    }
    if name.contains("testR11") {
      app.launchEnvironment["UITEST_DECK_MAKER_ACCESS"] =
        name.contains("ImportAndEditor") || name.contains("RestoredDraft")
          || name.contains("ProEdits") ? "1" : "0"
    }
    if name.contains("testR11ImportAndEditor") {
      app.launchEnvironment["UITEST_SEED_PIYODECK_CAPTURE"] = "1"
    }
    if name.contains("testR11UserDeckDelete") {
      app.launchEnvironment["UITEST_SEED_USER_DECK_DELETE"] = "1"
    }
    if name.contains("testR11ProEdits") {
      app.launchEnvironment["UITEST_SEED_USER_DECK_DELETE"] = "1"
    }
    if name.contains("testR11DowngradeImport") {
      app.launchEnvironment["UITEST_SEED_PIYODECK_DOWNGRADE"] = "1"
    }
    if name.contains("testR11RestoredDraft") {
      app.launchEnvironment["UITEST_SEED_USER_DECK_DRAFT"] = "1"
    }
    if name.contains("testKorean10KeyLayoutCarriesInto") {
      app.launchArguments += ["-keyboard.builtin_layout_default", "korean_10key"]
    }
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
  }

  override func tearDown() {
    app.terminate()
    app = nil
    super.tearDown()
  }

  func testInitialJapanesePracticeScreenSnapshot() {
    startPractice()
    XCTAssertEqual(element("practice.target.value").label, "사랑해요")
    XCTAssertEqual(element("practice.mistakes.value").value as? String, "0")
    XCTAssertTrue(element("practice.overall_progress").exists)
    XCTAssertTrue(element("practice.composition_card").exists)
    XCTAssertEqual(element("practice.meaning.value").label, "愛しています")
    XCTAssertFalse(element("practice.reading.value").exists)
    XCTAssertTrue(element("practice.jamo_progress.value").exists)
    let firstJamo = element("practice.jamo.active")
    let lastJamo = element("practice.jamo.8")
    XCTAssertTrue(firstJamo.exists)
    XCTAssertTrue(lastJamo.exists)
    XCTAssertEqual(
      (firstJamo.frame.minX + lastJamo.frame.maxX) / 2,
      app.frame.midX,
      accuracy: 2
    )
    XCTAssertFalse(app.staticTexts["0 / 9"].isHittable)
    XCTAssertFalse(app.staticTexts["組み立てプレビュー"].exists)
    XCTAssertFalse(app.buttons["practice.hint"].exists)
    attachScreenshot(named: "practice-initial-ja")
  }

  func testCompletedSyllableShowsSuccessStateWhileTyping() {
    startPractice()
    let target = element("practice.target.value")
    let jamoProgress = element("practice.jamo_progress.value")
    XCTAssertEqual(target.label, "사랑해요")
    XCTAssertEqual(target.value as? String, "0 / 4 音節完了")
    XCTAssertEqual(jamoProgress.value as? String, "0 / 9")

    app.buttons["keyboard.key.ㅅ"].tap()
    XCTAssertEqual(target.value as? String, "0 / 4 音節完了")
    app.buttons["keyboard.key.ㅏ"].tap()

    waitForValue("1 / 4 音節完了", on: target, timeout: 3)
    waitForValue("2 / 9", on: jamoProgress, timeout: 3)
    attachScreenshot(named: "practice-syllable-success-ja")
  }

  func testKorean10KeyPracticeUsesThreeByFourLayoutAndPendingBackspace() {
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: true)
    app.launchArguments += ["-settings.font_scale", "large"]
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    openSettings()
    let layoutPicker = element("settings.builtin_keyboard_layout")
    scrollToHittable(layoutPicker)
    layoutPicker.tap()
    app.buttons["韓国語10キー（天地人式）"].tap()
    app.buttons["settings.done"].tap()
    startPractice()

    let vertical = app.buttons["keyboard.10key.vertical"]
    let dot = app.buttons["keyboard.10key.dot"]
    let horizontal = app.buttons["keyboard.10key.horizontal"]
    let siot = app.buttons["keyboard.10key.siot"]
    let ieung = app.buttons["keyboard.10key.ieung"]
    let next = app.buttons["keyboard.10key.next"]
    let space = app.buttons["keyboard.10key.space"]
    let backspace = app.buttons["keyboard.backspace"]
    XCTAssertTrue(vertical.exists)
    XCTAssertTrue(dot.exists)
    XCTAssertTrue(horizontal.exists)
    XCTAssertTrue(siot.exists)
    XCTAssertTrue(ieung.exists)
    XCTAssertTrue(next.exists)
    XCTAssertTrue(space.exists)
    XCTAssertTrue(backspace.exists)
    XCTAssertEqual(siot.label, "ㅅ、ㅎ、ㅆグループ")
    XCTAssertFalse(app.buttons["keyboard.key.ㅅ"].exists)

    for key in [vertical, dot, horizontal, siot, ieung, next, space, backspace] {
      XCTAssertGreaterThanOrEqual(key.frame.minX, app.frame.minX)
      XCTAssertLessThanOrEqual(key.frame.maxX, app.frame.maxX)
    }
    XCTAssertEqual(vertical.frame.midY, dot.frame.midY, accuracy: 1)
    XCTAssertEqual(dot.frame.midY, horizontal.frame.midY, accuracy: 1)
    XCTAssertLessThan(vertical.frame.midY, siot.frame.midY)
    XCTAssertLessThan(siot.frame.midY, ieung.frame.midY)
    XCTAssertEqual(next.frame.midY, ieung.frame.midY, accuracy: 1)
    XCTAssertGreaterThan(space.frame.midY, next.frame.midY)

    let progress = element("practice.jamo_progress.value")
    siot.tap()
    waitForValue("1 / 9", on: progress, timeout: 3)

    vertical.tap()
    XCTAssertEqual(progress.value as? String, "1 / 9")
    backspace.tap()
    XCTAssertEqual(progress.value as? String, "1 / 9")

    vertical.tap()
    dot.tap()
    waitForValue("2 / 9", on: progress, timeout: 3)
    XCTAssertEqual(element("practice.target.value").value as? String, "1 / 4 音節完了")
    attachScreenshot(named: "practice-korean-10key-large-ja")
  }

  func testKorean10KeyLayoutCarriesIntoFlowGame() {
    app.tabBars.buttons["ゲーム"].tap()
    app.buttons["game.mode.flow"].tap()
    element("game.flow.preset.beginner").tap()
    XCTAssertTrue(element("game.play.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(app.buttons["keyboard.10key.vertical"].waitForExistence(timeout: 5))
    XCTAssertTrue(app.buttons["keyboard.10key.next"].exists)
    XCTAssertFalse(app.buttons["keyboard.key.ㄱ"].exists)
    app.buttons["game.end"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
  }

  func testKorean10KeyLayoutCarriesIntoRecallGame() {
    app.tabBars.buttons["ゲーム"].tap()
    app.buttons["game.mode.choseong"].tap()
    element("game.choseong.preset.beginner").tap()
    XCTAssertTrue(element("choseong.play.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(app.buttons["keyboard.10key.vertical"].waitForExistence(timeout: 5))
    XCTAssertTrue(app.buttons["keyboard.10key.next"].exists)
    XCTAssertFalse(app.buttons["keyboard.key.ㅎ"].exists)
  }

  func testCurriculumMapStartsWithSequentialCoreUnlocks() {
    openPracticeTab()
    XCTAssertTrue(element("curriculum.stage.chapter_1_basic_consonants").exists)
    XCTAssertTrue(element("curriculum.stage.chapter_2_basic_vowels.locked").exists)
    XCTAssertFalse(app.staticTexts["ハングルキーボードコース"].exists)
    XCTAssertFalse(app.staticTexts["スタンプを集めよう"].exists)
    XCTAssertTrue(app.buttons["root.settings"].exists)
    attachScreenshot(named: "curriculum-map-ja")
    scrollToHittable(element("curriculum.stage.chapter_6_sentences"))
    app.scrollViews.firstMatch.swipeUp()
    XCTAssertFalse(element("curriculum.free_practice").exists)
    XCTAssertFalse(element("practice.setup.screen").exists)
    attachScreenshot(named: "curriculum-bottom-without-free-practice-ja")
  }

  func testChapterSixSpacingMissionPracticesTheSpaceKey() {
    openPracticeTab()
    let spacingStage = element("curriculum.stage.chapter_6_spacing")
    scrollToHittable(spacingStage)
    XCTAssertTrue(app.staticTexts["単語の間にスペース"].exists)
    spacingStage.tap()

    let target = element("practice.target.value")
    let jamoProgress = element("practice.jamo_progress.value")
    XCTAssertTrue(target.waitForExistence(timeout: 5))
    XCTAssertEqual(target.label, "좋은 아침")
    XCTAssertEqual(element("practice.meaning.value").label, "おはよう")

    for key in Array("ㅈㅗㅎㅇㅡㄴ") {
      app.buttons["keyboard.key.\(key)"].tap()
    }
    waitForValue("6 / 12", on: jamoProgress, timeout: 3)
    attachScreenshot(named: "curriculum-spacing-active-space-ja")

    app.buttons["keyboard.space"].tap()
    waitForValue("7 / 12", on: jamoProgress, timeout: 3)
  }

  func testMascotClosetRemainsAvailableFromSettings() {
    openSettings()
    let closet = app.buttons["settings.mascot"]
    XCTAssertTrue(closet.waitForExistence(timeout: 3))
    scrollToHittable(closet)
    closet.tap()

    XCTAssertTrue(app.navigationBars["クローゼット"].waitForExistence(timeout: 3))
    XCTAssertTrue(element("closet.preview").waitForExistence(timeout: 3))
    XCTAssertTrue(app.staticTexts["ピヨの成長条件"].exists)
    scrollToHittable(app.staticTexts["たまごがかえる — チャプター1個"])
    let automatic = app.buttons["closet.selection.automatic"]
    let list = app.collectionViews.firstMatch
    let dragStart = list.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.75))
    let dragEnd = list.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
    for _ in 0..<16 where !automatic.isHittable {
      dragStart.press(forDuration: 0.05, thenDragTo: dragEnd)
    }
    XCTAssertTrue(automatic.isHittable, app.debugDescription)
    XCTAssertFalse(app.staticTexts["たまごの思い出"].exists)
    XCTAssertFalse(app.buttons["プレーン"].exists)
    XCTAssertFalse(app.buttons["おまかせ（季節アイテムあり）"].exists)
    attachScreenshot(named: "mascot-closet-ja")
  }

  func testMyPageShowsGrowthSettingsAndRoutesEmptyDecksToDiscover() {
    app.tabBars.buttons["マイページ"].tap()

    XCTAssertTrue(element("my_page.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("my_page.profile").exists)
    XCTAssertTrue(element("my_page.growth").exists)
    XCTAssertTrue(element("my_page.insights").exists)
    let settings = app.buttons["my_page.settings"]
    scrollToHittable(settings)
    settings.tap()
    XCTAssertTrue(element("settings.screen").waitForExistence(timeout: 3))
    app.buttons["settings.done"].tap()

    let emptyDecks = element("my_decks.empty")
    for _ in 0..<4 where !emptyDecks.exists {
      app.swipeUp()
    }
    XCTAssertTrue(emptyDecks.waitForExistence(timeout: 5))
    let findDecks = app.buttons["my_decks.find_decks"]
    scrollToHittable(findDecks)
    findDecks.tap()

    XCTAssertTrue(element("discover.search").waitForExistence(timeout: 5))
    XCTAssertFalse(app.navigationBars["さがす"].exists)
    XCTAssertFalse(app.buttons["root.settings"].exists)
    XCTAssertTrue(element("discover.catalog").waitForExistence(timeout: 5))

    app.tabBars.buttons["ゲーム"].tap()
    XCTAssertTrue(element("game.selection.screen").waitForExistence(timeout: 5))
    XCTAssertFalse(element("game.deck_selection.screen").exists)
  }

  func testMyDeckImportIsFreeWhileNewDeckOpensDeckMakerPaywall() {
    app.tabBars.buttons["マイページ"].tap()
    XCTAssertTrue(element("my_page.screen").waitForExistence(timeout: 5))

    let importDeck = app.buttons["my_decks.import"]
    let createDeck = app.buttons["my_decks.create"]
    scrollToHittable(importDeck)
    XCTAssertTrue(importDeck.isEnabled)
    XCTAssertTrue(createDeck.exists)

    scrollToHittable(createDeck)
    createDeck.tap()

    XCTAssertTrue(element("deck_maker.paywall.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(app.buttons["deck_maker.paywall.restore"].exists)
    XCTAssertTrue(app.buttons["deck_maker.paywall.close"].exists)
  }

  func testR11ImportAndEditorScreenshotCapture() {
    app.tabBars.buttons["マイページ"].tap()

    XCTAssertTrue(app.navigationBars["デッキを読み込む"].waitForExistence(timeout: 5))
    attachScreenshot(named: "r11-import-preview-ja")

    app.buttons["piyodeck.import.close"].tap()
    XCTAssertTrue(app.navigationBars["マイページ"].waitForExistence(timeout: 5))

    let importDeck = app.buttons["my_decks.import"]
    scrollToHittable(importDeck)
    XCTAssertTrue(app.buttons["my_decks.create"].isHittable)
    attachScreenshot(named: "r11-my-decks-actions-ja")

    app.buttons["my_decks.create"].tap()
    XCTAssertTrue(app.navigationBars["新しいデッキ"].waitForExistence(timeout: 5))
    attachScreenshot(named: "r11-new-deck-editor-ja")
  }

  func testR11PaywallScreenshotCapture() {
    app.tabBars.buttons["マイページ"].tap()
    let createDeck = app.buttons["my_decks.create"]
    scrollToHittable(createDeck)
    createDeck.tap()

    XCTAssertTrue(element("deck_maker.paywall.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(app.buttons["deck_maker.paywall.purchase"].waitForExistence(timeout: 5))
    RunLoop.current.run(until: Date().addingTimeInterval(0.5))
    attachScreenshot(named: "r11-deck-maker-paywall-ja")
  }

  func testR11UserDeckDeleteCancelPreservesDeckAndConfirmationRemovesIt() {
    app.tabBars.buttons["マイページ"].tap()
    XCTAssertTrue(element("my_page.screen").waitForExistence(timeout: 5))

    let deckID = "user_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    let deckRow = element("my_decks.deck.\(deckID)")
    let actions = app.buttons["my_decks.actions.\(deckID)"]
    scrollToHittable(actions)
    XCTAssertTrue(deckRow.exists)

    actions.tap()
    let deleteMenuItem = app.buttons["デッキを削除"]
    XCTAssertTrue(deleteMenuItem.waitForExistence(timeout: 3), app.debugDescription)
    deleteMenuItem.tap()

    let confirmation = element("my_decks.delete.confirmation")
    XCTAssertTrue(confirmation.waitForExistence(timeout: 3))
    XCTAssertTrue(app.staticTexts["「削除確認デッキ」を削除しますか？"].exists)
    XCTAssertTrue(app.buttons["my_decks.delete.export_then_delete"].exists)
    attachScreenshot(named: "r11-delete-confirmation-ja")
    app.buttons["my_decks.delete.cancel"].tap()

    waitForNonexistence(confirmation, timeout: 3)
    XCTAssertTrue(deckRow.exists, "Cancel must leave the installed deck intact")

    actions.tap()
    XCTAssertTrue(deleteMenuItem.waitForExistence(timeout: 3), app.debugDescription)
    deleteMenuItem.tap()
    XCTAssertTrue(confirmation.waitForExistence(timeout: 3))
    app.buttons["my_decks.delete.confirm"].tap()

    waitForNonexistence(confirmation, timeout: 5)
    waitForNonexistence(deckRow, timeout: 5)
  }

  func testR11ProEditsDeckSavesAndPersistsAfterRelaunch() {
    let deckID = "user_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    let savedName = "Pro Saved Deck"
    let savedMeaning = "Saved meaning"

    app.tabBars.buttons["マイページ"].tap()
    let actions = app.buttons["my_decks.actions.\(deckID)"]
    scrollToHittable(actions)
    actions.tap()
    let edit = app.buttons["編集"]
    XCTAssertTrue(edit.waitForExistence(timeout: 3), app.debugDescription)
    edit.tap()

    XCTAssertTrue(element("deck_editor.screen").waitForExistence(timeout: 5))
    let nameField = element("deck_editor.name")
    XCTAssertTrue(nameField.waitForExistence(timeout: 3))
    nameField.tap()
    let nextKeyboard = app.buttons["次のキーボード"]
    if nextKeyboard.exists, (nextKeyboard.value as? String)?.contains("English") == true {
      nextKeyboard.tap()
    }
    nameField.typeKey("a", modifierFlags: .command)
    nameField.typeText(savedName)
    XCTAssertEqual(nameField.value as? String, savedName)

    let meaningField = app.textFields.matching(identifier: "deck_editor.item.0").element(boundBy: 2)
    XCTAssertTrue(meaningField.waitForExistence(timeout: 3), app.debugDescription)
    meaningField.tap()
    meaningField.typeKey("a", modifierFlags: .command)
    meaningField.typeText(savedMeaning)
    XCTAssertEqual(meaningField.value as? String, savedMeaning)
    app.buttons["deck_editor.save"].tap()

    waitForNonexistence(element("deck_editor.screen"), timeout: 5)
    XCTAssertTrue(app.staticTexts[savedName].waitForExistence(timeout: 5))

    app.terminate()
    app.launchEnvironment.removeValue(forKey: "UITEST_RESET_DECK_LIBRARY")
    app.launchEnvironment.removeValue(forKey: "UITEST_SEED_USER_DECK_DELETE")
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))

    app.tabBars.buttons["マイページ"].tap()
    let reloadedActions = app.buttons["my_decks.actions.\(deckID)"]
    scrollToHittable(reloadedActions)
    XCTAssertTrue(app.staticTexts[savedName].waitForExistence(timeout: 5))
    reloadedActions.tap()
    XCTAssertTrue(app.buttons["編集"].waitForExistence(timeout: 3))
    app.buttons["編集"].tap()

    XCTAssertTrue(element("deck_editor.screen").waitForExistence(timeout: 5))
    XCTAssertEqual(element("deck_editor.name").value as? String, savedName)
    let reloadedMeaning = app.textFields
      .matching(identifier: "deck_editor.item.0")
      .element(boundBy: 2)
    XCTAssertTrue(reloadedMeaning.waitForExistence(timeout: 3), app.debugDescription)
    XCTAssertEqual(reloadedMeaning.value as? String, savedMeaning)
  }

  func testR11DowngradeImportShowsComparisonAndKeepCurrentIsSafePath() {
    openSeededDowngradeImport()

    let comparison = element("piyodeck.import.comparison")
    XCTAssertTrue(comparison.exists)
    XCTAssertTrue(element("piyodeck.import.status.downgrade").exists)
    XCTAssertTrue(app.staticTexts["現在の安全デッキ"].exists)
    XCTAssertTrue(app.staticTexts["古い読み込みデッキ"].exists)
    XCTAssertTrue(app.buttons["piyodeck.import.action.export_current"].exists)
    attachScreenshot(named: "r11-import-conflict-ja")

    let keep = app.buttons["piyodeck.import.action.keep"]
    let replace = app.buttons["piyodeck.import.action.replace"]
    XCTAssertTrue(keep.exists)
    XCTAssertTrue(replace.exists)
    XCTAssertLessThan(keep.frame.minY, replace.frame.minY)
    keep.tap()

    waitForNonexistence(element("piyodeck.import.preview"), timeout: 4)
    let currentDeck = element(
      "my_decks.deck.user_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    )
    scrollToHittable(currentDeck)
    XCTAssertTrue(currentDeck.exists, "Keeping current must not replace or remove the deck")
  }

  func testR11DowngradeImportReplaceRequiresDestructiveConfirmation() {
    openSeededDowngradeImport()

    let replace = app.buttons["piyodeck.import.action.replace"]
    scrollToHittable(replace)
    replace.tap()

    XCTAssertTrue(
      app.staticTexts["現在のデッキを置き換えますか？"].waitForExistence(timeout: 3)
    )
    let destructiveConfirmation = app.buttons["置き換える"]
    XCTAssertTrue(destructiveConfirmation.exists)
  }

  func testR11RestoredDraftWaitsForActiveMyPage() {
    XCTAssertFalse(
      element("deck_editor.screen").waitForExistence(timeout: 1),
      "A stored draft must not interrupt Home"
    )

    app.tabBars.buttons["ゲーム"].tap()
    XCTAssertTrue(element("game.selection.screen").waitForExistence(timeout: 5))
    XCTAssertFalse(element("deck_editor.screen").exists)

    app.tabBars.buttons["マイページ"].tap()
    XCTAssertTrue(element("deck_editor.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(app.navigationBars["新しいデッキ"].exists)
  }

  func testIdlePiyoReactsToTouchAndLongPressOpensCloset() {
    app.tabBars.buttons["マイページ"].tap()

    let mascot = app.buttons["成長するひよこのマスコット"]
    XCTAssertTrue(mascot.waitForExistence(timeout: 5))
    mascot.tap()
    waitForValueContaining("うれしそうにしている", on: mascot, timeout: 3)

    mascot.press(forDuration: 0.6)
    XCTAssertTrue(app.navigationBars["クローゼット"].waitForExistence(timeout: 3))
  }

  func testHomeMyPiyoCombinesStampsRewardsAndKeepsMyPageProfile() {
    let dailyEncouragement = "「今日もいっしょに始めよう！ピヨ！」"
    let myPiyoCard = element("home.my_piyo_card")
    XCTAssertTrue(myPiyoCard.waitForExistence(timeout: 3))
    let primaryAction = element("home.primary.recommend_deck")
    XCTAssertTrue(primaryAction.exists)
    XCTAssertLessThan(myPiyoCard.frame.minY, primaryAction.frame.minY)
    XCTAssertTrue(
      (myPiyoCard.value as? String)?.hasPrefix(
        "\(dailyEncouragement), 0日連続, 0 / 7"
      ) == true
    )
    myPiyoCard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2)).tap()

    XCTAssertTrue(app.navigationBars["MY ピヨ"].waitForExistence(timeout: 5))
    XCTAssertTrue(element("my_piyo.detail.stamps").exists)
    XCTAssertTrue(element("my_piyo.detail.settings").exists)
    let weekDays = (13...19).map { day in
      element(String(format: "retention.stamp_day.2026-07-%02d", day))
    }
    for day in weekDays {
      XCTAssertTrue(day.exists)
    }
    for (earlier, later) in zip(weekDays, weekDays.dropFirst()) {
      XCTAssertLessThan(earlier.frame.minX, later.frame.minX)
    }
    XCTAssertTrue(weekDays[0].label.contains("未達成"))
    XCTAssertTrue(weekDays[6].label.contains("今日・未達成"))
    for requiredDays in [3, 5, 7] {
      let reward = element("my_piyo.detail.reward.\(requiredDays)")
      scrollToHittable(reward)
    }
    attachScreenshot(named: "home-my-piyo-stamps-rewards-ja")

    app.tabBars.buttons["マイページ"].tap()
    XCTAssertTrue(element("my_page.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("my_page.profile").exists)
  }

  func testHomeShowsMyPiyoBeforePrimaryAction() {
    let myPiyoCard = element("home.my_piyo_card")
    let primaryAction = element("home.primary.recommend_deck")

    XCTAssertTrue(myPiyoCard.waitForExistence(timeout: 3))
    XCTAssertTrue(primaryAction.exists)
    XCTAssertLessThan(myPiyoCard.frame.minY, primaryAction.frame.minY)
  }

  func testHomeQuickActionStartsRandomWords() {
    let primaryAction = element("home.primary.recommend_deck")
    let piyoCup = element("home.quick.piyo_cup")
    let randomWords = element("home.quick.random")

    XCTAssertTrue(primaryAction.waitForExistence(timeout: 3))
    scrollToHittable(piyoCup)
    XCTAssertTrue(piyoCup.isHittable)
    XCTAssertTrue(randomWords.isHittable)
    XCTAssertGreaterThan(piyoCup.frame.minY, primaryAction.frame.minY)
    XCTAssertEqual(piyoCup.frame.minY, randomWords.frame.minY, accuracy: 2)

    randomWords.tap()
    XCTAssertTrue(element("practice.target.value").waitForExistence(timeout: 5))
    waitForValue("1 / 5", on: element("practice.overall_progress"), timeout: 3)
  }

  func testHomeQuickActionStartsWeeklyPiyoCupDirectly() {
    let piyoCup = app.buttons["home.quick.piyo_cup"]

    app.swipeUp()
    XCTAssertTrue(piyoCup.waitForExistence(timeout: 3))
    XCTAssertTrue(piyoCup.isHittable)
    piyoCup.tap()
    XCTAssertFalse(element("game.selection.screen").exists)
    XCTAssertTrue(element("game.play.screen").waitForExistence(timeout: 5))
  }

  func testDailyMascotEncouragementMatchesHomeAndMyPage() {
    let dailyEncouragement = "「今日もいっしょに始めよう！ピヨ！」"
    let myPiyoCard = element("home.my_piyo_card")

    XCTAssertTrue(myPiyoCard.waitForExistence(timeout: 3))
    XCTAssertTrue(
      (myPiyoCard.value as? String)?.hasPrefix(
        "\(dailyEncouragement), 0日連続, 0 / 7"
      ) == true
    )

    app.tabBars.buttons["マイページ"].tap()
    XCTAssertTrue(element("my_page.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(app.staticTexts[dailyEncouragement].exists)
    XCTAssertTrue(app.buttons["クローゼット"].exists)
    attachScreenshot(named: "my-page-daily-encouragement-ja")
  }

  func testOnboardingIntroducesPersonalizedEgg() {
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: true, showsOnboarding: true)
    app.launch()

    XCTAssertTrue(element("onboarding.goal.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("onboarding.mascot.egg").exists)
    XCTAssertTrue(app.staticTexts["きみだけのたまごだよ"].exists)
    XCTAssertFalse(app.staticTexts["たまごの模様を選ぶ"].exists)
    XCTAssertFalse(element("onboarding.egg_pattern.plain").exists)
    attachScreenshot(named: "onboarding-mascot-egg-ja")
  }

  func testFirstClearedChapterHatchesMascotAfterSessionEnds() {
    openPracticeTab()
    let firstStage = element("curriculum.stage.chapter_1_basic_consonants")
    scrollToHittable(firstStage)
    firstStage.tap()

    let targets = Array("ㄱㄴㄷㄹㅁㅂㅅㅇㅈㅎ")
    for (index, target) in targets.enumerated() {
      waitForLabel(String(target), on: element("practice.target.value"), timeout: 3)
      app.buttons["keyboard.key.\(target)"].tap()
      if index < targets.count - 1 {
        waitForLabel(String(targets[index + 1]), on: element("practice.target.value"), timeout: 3)
      }
    }

    XCTAssertTrue(element("practice.result.screen").waitForExistence(timeout: 5))
    XCTAssertFalse(app.buttons["practice.show_result"].exists)
    app.buttons["result.done"].tap()

    XCTAssertTrue(element("mascot.growth.celebration").waitForExistence(timeout: 5))
    let name = app.textFields["mascot.growth.name"]
    XCTAssertTrue(name.waitForExistence(timeout: 4))
    name.tap()
    name.typeText("Momo")
    attachScreenshot(named: "mascot-hatching-celebration-ja")

    app.buttons["mascot.growth.confirm"].tap()
    XCTAssertTrue(element("curriculum.map.screen").waitForExistence(timeout: 5))
  }

  func testDailyChallengeCompletesTodaysStampAndReminderDefaultsOff() {
    let myPiyoCard = element("home.my_piyo_card")
    XCTAssertTrue(myPiyoCard.waitForExistence(timeout: 3))
    XCTAssertTrue((myPiyoCard.value as? String)?.contains("0日連続, 0 / 7") == true)
    XCTAssertFalse(app.switches["retention.reminder.toggle"].exists)

    openSettings()
    let reminderToggle = app.switches["retention.reminder.toggle"]
    scrollToHittable(reminderToggle)
    XCTAssertEqual(reminderToggle.value as? String, "0")
    app.buttons["settings.done"].tap()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 3))

    let dailyChallenge = element("retention.daily_challenge")
    scrollToHittable(dailyChallenge, direction: .down)
    dailyChallenge.tap()

    let targets: [(String, String)] = [
      ("음식", "ㅇㅡㅁㅅㅣㄱ"),
      ("음악", "ㅇㅡㅁㅇㅏㄱ"),
      ("응원", "ㅇㅡㅇㅇㅜㅓㄴ"),
      ("학교", "ㅎㅏㄱㄱㅛ"),
      ("사진", "ㅅㅏㅈㅣㄴ"),
    ]
    for (index, target) in targets.enumerated() {
      XCTAssertEqual(element("practice.target.value").label, target.0)
      for key in target.1 {
        app.buttons["keyboard.key.\(key)"].tap()
      }
      if index < targets.count - 1 {
        waitForLabel(targets[index + 1].0, on: element("practice.target.value"), timeout: 3)
      }
    }

    XCTAssertTrue(element("practice.result.screen").waitForExistence(timeout: 5))
    XCTAssertFalse(app.buttons["practice.show_result"].exists)
    let done = app.buttons["result.done"]
    XCTAssertTrue(done.isEnabled)
    done.tap()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    XCTAssertTrue((myPiyoCard.value as? String)?.contains("1日連続, 1 / 7") == true)
    XCTAssertTrue(app.staticTexts["今日のチャレンジ完了！"].exists)
    attachScreenshot(named: "retention-daily-complete-ja")
  }

  func testCurriculumCheckpointRestoresCompletedProblemAcrossRelaunch() {
    openPracticeTab()
    let firstStage = element("curriculum.stage.chapter_1_basic_consonants")
    XCTAssertTrue(firstStage.waitForExistence(timeout: 3))
    scrollToHittable(firstStage)
    firstStage.tap()

    XCTAssertEqual(element("practice.target.value").label, "ㄱ")
    app.buttons["keyboard.key.ㄱ"].tap()
    waitForLabel("ㄴ", on: element("practice.target.value"), timeout: 3)

    app.terminate()
    app = makeApplication(resetKeyboardPreferences: false)
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    openPracticeTab()
    let restoredStage = element("curriculum.stage.chapter_1_basic_consonants")
    scrollToHittable(restoredStage)
    restoredStage.tap()

    XCTAssertEqual(element("practice.target.value").label, "ㄴ")
    XCTAssertEqual(element("practice.entered_text.value").value as? String, "…")
  }

  func testWrongKeyIsIgnoredAndBackspaceRewindsAcceptedInput() {
    startPractice()
    app.buttons["keyboard.key.ㄱ"].tap()
    XCTAssertEqual(element("practice.mistakes.value").value as? String, "1")
    XCTAssertEqual(element("practice.entered_text.value").value as? String, "…")

    app.buttons["keyboard.key.ㅅ"].tap()
    XCTAssertEqual(element("practice.entered_text.value").value as? String, "ㅅ")
    app.buttons["keyboard.backspace"].tap()
    XCTAssertEqual(element("practice.entered_text.value").value as? String, "…")
  }

  func testCompletingDokkaebiTargetAdvancesToNextProblem() throws {
    startPractice()
    for key in Array("ㅅㅏㄹㅏㅇㅎㅐㅇㅛ") {
      app.buttons["keyboard.key.\(key)"].tap()
    }

    let latencyElement = element("debug.input_latency.p95")
    XCTAssertTrue(latencyElement.waitForExistence(timeout: 2))
    let latencyText = try XCTUnwrap(latencyElement.value as? String)
    let p95Milliseconds = try XCTUnwrap(Double(latencyText))
    XCTAssertTrue(p95Milliseconds.isFinite)
    XCTAssertGreaterThan(p95Milliseconds, 0)
    waitForLabel("안녕하세요", on: element("practice.target.value"), timeout: 3)
    XCTAssertFalse(app.buttons["practice.next"].exists)
    XCTAssertEqual(element("practice.entered_text.value").value as? String, "…")
    attachScreenshot(named: "practice-auto-advanced-ja")
  }

  func testPracticeToolbarOffersTargetSpeechAndLiveSessionSettings() {
    startPractice()

    let speakTarget = app.buttons["practice.speak_target"]
    XCTAssertTrue(speakTarget.waitForExistence(timeout: 3))
    XCTAssertEqual(speakTarget.value as? String, "사랑해요")
    speakTarget.tap()
    XCTAssertTrue(element("practice.target.value").exists)
    XCTAssertFalse(app.buttons["最初からやり直す"].exists)

    let settings = app.buttons["practice.session_settings"]
    XCTAssertTrue(settings.waitForExistence(timeout: 3))
    settings.tap()
    XCTAssertTrue(element("practice.session_settings.key_guide").waitForExistence(timeout: 3))
    XCTAssertTrue(element("practice.session_settings.roman_hints").exists)
    XCTAssertTrue(element("practice.session_settings.haptics").exists)
    let displaySettings = app.buttons["practice.session_settings.display"]
    XCTAssertTrue(displaySettings.exists)
    displaySettings.tap()
    XCTAssertTrue(element("practice.session_settings.target").exists)
    XCTAssertTrue(element("practice.session_settings.meaning").exists)
    XCTAssertTrue(element("practice.session_settings.reading").exists)
    XCTAssertTrue(element("practice.session_settings.order").exists)
    XCTAssertTrue(element("practice.session_settings.jamo").exists)
    XCTAssertTrue(element("practice.session_settings.mascot").exists)
    XCTAssertTrue(element("practice.session_settings.composition").exists)
  }

  func testPracticeSessionSoundSettingsToggleAutomaticSpeech() {
    startPractice()

    let settings = app.buttons["practice.session_settings"]
    settings.tap()
    app.buttons["practice.session_settings.sound_menu"].tap()

    let automaticSpeech = element("practice.session_settings.auto_speak")
    XCTAssertTrue(automaticSpeech.waitForExistence(timeout: 3))
    XCTAssertEqual(automaticSpeech.value as? String, "0")
    automaticSpeech.tap()

    app.buttons["practice.session_settings.close"].tap()
    settings.tap()
    app.buttons["practice.session_settings.sound_menu"].tap()
    XCTAssertEqual(automaticSpeech.value as? String, "1")
    automaticSpeech.tap()
  }

  func testPracticePromptOrderCanPutMeaningBeforeTarget() {
    startPractice()

    app.buttons["practice.session_settings"].tap()
    app.buttons["practice.session_settings.display"].tap()
    element("practice.session_settings.order").tap()
    app.buttons["日本語の意味 → お題を表示 → 日本語式の読み方"].tap()
    app.buttons["practice.session_settings.close"].tap()

    let meaning = element("practice.meaning.value")
    let target = element("practice.target.value")
    XCTAssertTrue(meaning.waitForExistence(timeout: 3))
    XCTAssertTrue(target.exists)
    XCTAssertLessThan(meaning.frame.minY, target.frame.minY)
  }

  func testJapaneseSettingsDefaultsCarryIntoDeckPractice() {
    openSettings()
    let sound = app.switches["settings.sound"]
    scrollToHittable(sound)
    XCTAssertEqual(sound.value as? String, "1")
    XCTAssertTrue(element("settings.sound_preset").isEnabled)
    attachScreenshot(named: "practice-common-settings-ja")

    scrollToHittable(app.switches["settings.key_guide"])
    XCTAssertEqual(app.switches["settings.key_guide"].value as? String, "1")
    XCTAssertEqual(app.switches["settings.roman_hints"].value as? String, "1")
    XCTAssertEqual(app.switches["settings.haptics"].value as? String, "1")

    app.buttons["settings.done"].tap()
    startPractice()
    XCTAssertFalse(app.switches["settings.key_guide"].exists)
    XCTAssertFalse(app.switches["settings.roman_hints"].exists)
    XCTAssertFalse(app.switches["settings.haptics"].exists)
    XCTAssertFalse(app.switches["settings.sound"].exists)
  }

  func testKeyboardSettingsPersistAcrossDeckPracticeExitAndRelaunch() {
    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: true,
      koreanKeyboardAvailable: true
    )
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))

    openSettings()
    let layoutPicker = element("settings.builtin_keyboard_layout")
    scrollToHittable(layoutPicker)
    layoutPicker.tap()
    app.buttons["韓国語10キー（天地人式）"].tap()

    let physicalGuide = app.switches["settings.physical_keyboard_guide"]
    scrollToHittable(physicalGuide)
    physicalGuide.tap()
    XCTAssertEqual(physicalGuide.value as? String, "1")
    app.buttons["settings.done"].tap()

    startPractice()
    XCTAssertTrue(app.buttons["keyboard.10key.vertical"].waitForExistence(timeout: 5))
    app.buttons["practice.session_settings"].tap()
    app.buttons["input_mode.os_ime"].tap()
    XCTAssertTrue(app.textFields["os_ime.text_field"].waitForExistence(timeout: 3))
    XCTAssertTrue(element("physical_keyboard.guide").waitForExistence(timeout: 3))

    XCUIDevice.shared.press(.home)
    app.activate()
    XCTAssertTrue(app.textFields["os_ime.text_field"].waitForExistence(timeout: 5))
    XCTAssertTrue(element("physical_keyboard.guide").waitForExistence(timeout: 3))

    app.buttons[storeText("練習を終了する", "End practice", "연습 끝내기")].tap()
    XCTAssertTrue(element("my_page.screen").waitForExistence(timeout: 5))
    openSettings()
    let persistedLayout = element("settings.builtin_keyboard_layout")
    scrollToHittable(persistedLayout)
    let persistedOSMode = app.buttons["input_mode.os_ime"]
    scrollToHittable(persistedOSMode)
    XCTAssertTrue(persistedOSMode.isSelected)
    let persistedPhysicalGuide = app.switches["settings.physical_keyboard_guide"]
    scrollToHittable(persistedPhysicalGuide)
    XCTAssertEqual(persistedPhysicalGuide.value as? String, "1")

    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: false,
      koreanKeyboardAvailable: true
    )
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    startPractice()
    XCTAssertTrue(app.textFields["os_ime.text_field"].waitForExistence(timeout: 3))
    XCTAssertTrue(element("physical_keyboard.guide").waitForExistence(timeout: 3))
    app.buttons["practice.session_settings"].tap()
    app.buttons["input_mode.builtin"].tap()
    XCTAssertTrue(app.buttons["keyboard.10key.vertical"].waitForExistence(timeout: 5))
  }

  func testSoundSettingAndPresetPersistAcrossRelaunch() {
    openSettings()
    let sound = app.switches["settings.sound"]
    scrollToHittable(sound)
    XCTAssertEqual(sound.value as? String, "1")

    XCTAssertTrue(element("settings.sound_preset").buttons["標準"].isSelected)
    let soft = element("settings.sound_preset").buttons["ソフト"]
    XCTAssertTrue(soft.waitForExistence(timeout: 2))
    soft.tap()
    XCTAssertTrue(soft.isSelected)

    sound.tap()
    XCTAssertEqual(sound.value as? String, "0")
    XCTAssertFalse(element("settings.sound_preset").isEnabled)

    app.terminate()
    app = makeApplication(resetKeyboardPreferences: false)
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    openSettings()

    let persistedSound = app.switches["settings.sound"]
    scrollToHittable(persistedSound)
    XCTAssertEqual(persistedSound.value as? String, "0")
    XCTAssertTrue(element("settings.sound_preset").buttons["ソフト"].isSelected)
    XCTAssertFalse(element("settings.sound_preset").isEnabled)
  }

  func testOSIMEUnavailableShowsJapaneseSetupGuideWithoutEnteringSessionMode() {
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: true, koreanKeyboardAvailable: false)
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    openSettings()

    let osMode = app.buttons["input_mode.os_ime"]
    scrollToHittable(osMode)
    osMode.tap()

    XCTAssertTrue(element("os_ime.guide.sheet").waitForExistence(timeout: 3))
    XCTAssertTrue(app.staticTexts["韓国語キーボードを追加しよう"].exists)
    attachScreenshot(named: "os-ime-install-guide-ja")
  }

  func testSessionSwitchesToOSIMEAndKeepsAcceptedJamoProgress() {
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: true, koreanKeyboardAvailable: true)
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    startPractice()

    app.buttons["keyboard.key.ㅅ"].tap()
    XCTAssertEqual(element("practice.entered_text.value").value as? String, "ㅅ")
    app.buttons["practice.session_settings"].tap()
    app.buttons["OSキーボード"].tap()

    let imeField = app.textFields["os_ime.text_field"]
    XCTAssertTrue(imeField.waitForExistence(timeout: 3))
    XCTAssertTrue(element("practice.composition_card").exists)
    XCTAssertFalse(element("os_ime.input.chrome").exists)
    imeField.tap()
    imeField.typeText("ㅏ랑해")

    XCTAssertEqual(element("practice.entered_text.value").value as? String, "사랑해")
    app.buttons["practice.session_settings"].tap()
    app.buttons["内蔵キーボード"].tap()
    XCTAssertEqual(element("practice.entered_text.value").value as? String, "사랑해")
    attachScreenshot(named: "os-ime-session-complete-ja")
  }

  func testOSIMEEnglishInputShowsSwitchHintWithoutChangingSessionAndResumesInKorean() {
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: true, koreanKeyboardAvailable: true)
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    startPractice()

    app.buttons["practice.session_settings"].tap()
    app.buttons["OSキーボード"].tap()

    let imeField = app.textFields["os_ime.text_field"]
    XCTAssertTrue(imeField.waitForExistence(timeout: 3))
    imeField.tap()
    imeField.typeText("r")

    let warning = element("os_ime.input_source_warning")
    XCTAssertTrue(warning.waitForExistence(timeout: 3))
    XCTAssertTrue(app.staticTexts["英語キーボードになっています"].exists)
    XCTAssertEqual(element("practice.entered_text.value").value as? String, "…")
    XCTAssertEqual(element("practice.mistakes.value").value as? String, "0")
    attachScreenshot(named: "os-ime-english-source-warning-ja")

    imeField.typeText("t")
    XCTAssertTrue(warning.exists)
    XCTAssertEqual(element("practice.entered_text.value").value as? String, "…")
    XCTAssertEqual(element("practice.mistakes.value").value as? String, "0")
    attachScreenshot(named: "os-ime-english-source-warning-repeat-feedback-ja")

    imeField.typeText("사랑해요")
    waitForLabel("안녕하세요", on: element("practice.target.value"), timeout: 5)
    XCTAssertTrue(warning.waitForNonExistence(timeout: 3))
  }

  func testOSIMEReturnAndForegroundRestoreFocusWithRecoveryAffordance() {
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: true, koreanKeyboardAvailable: true)
    app.launchArguments += [
      "-settings.practice_shows_mascot", "NO",
      "-settings.practice_shows_composition", "NO",
    ]
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    startPractice()

    app.buttons["practice.session_settings"].tap()
    app.buttons["OSキーボード"].tap()

    XCTAssertFalse(element("practice.composition_card").exists)
    XCTAssertTrue(element("os_ime.input.recovery").waitForExistence(timeout: 3))
    let imeField = app.textFields["os_ime.text_field"]
    XCTAssertTrue(imeField.waitForExistence(timeout: 3))
    imeField.tap()
    imeField.typeText("사")
    waitForValue("사", on: imeField, timeout: 3)

    imeField.typeText("\n")
    imeField.typeText("랑")
    waitForValue("사랑", on: imeField, timeout: 3)

    XCUIDevice.shared.press(.home)
    app.activate()
    let restoredField = app.textFields["os_ime.text_field"]
    XCTAssertTrue(restoredField.waitForExistence(timeout: 5))
    restoredField.typeText("해")
    waitForValue("사랑해", on: restoredField, timeout: 3)
  }

  func testOSIMEHardwareStyleDeleteAndReturnKeepAcceptedPrefixAligned() {
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: true, koreanKeyboardAvailable: true)
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    startPractice()

    app.buttons["practice.session_settings"].tap()
    app.buttons["OSキーボード"].tap()

    let imeField = app.textFields["os_ime.text_field"]
    XCTAssertTrue(imeField.waitForExistence(timeout: 3))
    imeField.tap()
    imeField.typeText("사랑")
    waitForValue("사랑", on: imeField, timeout: 3)

    imeField.typeText(XCUIKeyboardKey.delete.rawValue)
    waitForValue("사", on: imeField, timeout: 3)
    XCTAssertEqual(element("practice.entered_text.value").value as? String, "사")

    imeField.typeText("\n")
    imeField.typeText("랑해")
    waitForValue("사랑해", on: imeField, timeout: 3)
    XCTAssertEqual(element("practice.entered_text.value").value as? String, "사랑해")
  }

  func testOSIMERapidCorrectFeedbackStartsWithoutQueueingOldSounds() {
    let profile = app.buttons["マイページ"].firstMatch
    XCTAssertTrue(profile.waitForExistence(timeout: 3))
    profile.tap()
    let deck = element("my_decks.deck.user_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
    scrollToHittable(deck)
    deck.tap()
    XCTAssertTrue(element("practice.target.value").waitForExistence(timeout: 5))

    app.buttons["practice.session_settings"].tap()
    app.buttons["OSキーボード"].tap()

    let imeField = app.textFields["os_ime.text_field"]
    XCTAssertTrue(imeField.waitForExistence(timeout: 3))
    let playbackStarts = element("debug.effect.playback_start_count")
    XCTAssertTrue(playbackStarts.waitForExistence(timeout: 3))
    let initialPlaybackStarts = playbackStarts.label
    imeField.tap()
    imeField.typeText("사")
    waitForValue("사", on: imeField, timeout: 3)
    imeField.typeText("랑해요")
    waitForLabel("안녕하세요", on: element("practice.target.value"), timeout: 5)
    waitForLabelDifferentFrom(initialPlaybackStarts, on: playbackStarts, timeout: 3)
    let firstTargetPlaybackStarts = playbackStarts.label

    imeField.typeText("안")
    waitForValue("안", on: imeField, timeout: 3)
    imeField.typeText("녕하세요")
    waitForLabel("최고예요", on: element("practice.target.value"), timeout: 5)
    waitForLabelDifferentFrom(firstTargetPlaybackStarts, on: playbackStarts, timeout: 3)
    let secondTargetPlaybackStarts = playbackStarts.label

    imeField.typeText("최")
    waitForValue("최", on: imeField, timeout: 3)
    imeField.typeText("고예요")
    waitForLabelDifferentFrom(secondTargetPlaybackStarts, on: playbackStarts, timeout: 3)
    let schedulingP95 = element("debug.effect.scheduling_p95")
    XCTAssertTrue(schedulingP95.waitForExistence(timeout: 3))
    let milliseconds = Double(schedulingP95.label) ?? .infinity
    print("OSIME_EFFECT_SCHEDULING_P95_MS \(milliseconds)")
    XCTAssertLessThanOrEqual(milliseconds, 50)
  }


  func testOSIMESessionSettingsPreservesProgressAndRestoresFocus() {
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: true, koreanKeyboardAvailable: true)
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    startPractice()

    app.buttons["practice.session_settings"].tap()
    app.buttons["OSキーボード"].tap()

    let imeField = app.textFields["os_ime.text_field"]
    XCTAssertTrue(imeField.waitForExistence(timeout: 3))
    imeField.tap()
    imeField.typeText("사")
    waitForValue("사", on: imeField, timeout: 3)

    for _ in 0..<3 {
      app.buttons["practice.session_settings"].tap()
      XCTAssertTrue(element("practice.session_settings.overlay").waitForExistence(timeout: 3))
      XCTAssertEqual(element("practice.entered_text.value").value as? String, "사")
      app.buttons["practice.session_settings.close"].tap()
      XCTAssertTrue(element("practice.session_settings.overlay").waitForNonExistence(timeout: 3))
      XCTAssertEqual(element("practice.entered_text.value").value as? String, "사")
    }

    imeField.typeText("랑해요")
    waitForLabel("안녕하세요", on: element("practice.target.value"), timeout: 5)
  }

  func testFirstHomeRecommendsUntilDeckPracticeStartsAndThenResumesAfterRelaunch() {
    let recommendation = element("home.primary.recommend_deck")
    XCTAssertTrue(recommendation.waitForExistence(timeout: 5))
    XCTAssertFalse(element("home.primary.resume_deck").exists)
    attachScreenshot(named: "home-first-recommendation-ja")
    scrollToHittable(recommendation)
    recommendation.tap()
    let download = app.buttons["deck.detail.download"]
    XCTAssertTrue(download.waitForExistence(timeout: 5))
    download.tap()
    XCTAssertTrue(app.buttons["deck.detail.play"].waitForExistence(timeout: 5))

    app.terminate()
    app = makeApplication(resetKeyboardPreferences: false)
    app.launch()
    XCTAssertTrue(element("home.primary.recommend_deck").waitForExistence(timeout: 5))
    XCTAssertFalse(element("home.primary.resume_deck").exists)
    scrollAndTap(element("home.primary.recommend_deck"))
    app.buttons["deck.detail.play"].tap()
    XCTAssertTrue(element("practice.target.value").waitForExistence(timeout: 5))

    app.terminate()
    app = makeApplication(resetKeyboardPreferences: false)
    app.launch()
    let resume = element("home.primary.resume_deck")
    XCTAssertTrue(resume.waitForExistence(timeout: 5))
    XCTAssertFalse(element("home.primary.recommend_deck").exists)
    scrollAndTap(resume)
    XCTAssertTrue(element("practice.target.value").waitForExistence(timeout: 5))
  }

  func testHomeRecommendationsOpenDeckDetail() {
    XCTAssertTrue(element("home.screen").exists)
    XCTAssertTrue(element("home.my_piyo_card").exists)
    XCTAssertTrue(element("home.primary.recommend_deck").exists)
    XCTAssertFalse(element("curriculum.free_practice").exists)
    XCTAssertFalse(app.switches["retention.reminder.toggle"].exists)
    XCTAssertTrue(element("home.recommendations").waitForExistence(timeout: 5))
    XCTAssertTrue(element("home.recommendations.personal").exists)
    XCTAssertTrue(element("home.recommendations.next_step").exists)
    XCTAssertTrue(element("home.next_step.official_consonants").exists)
    let recommendation = element("home.recommendation.official_topik_one")
    XCTAssertTrue(recommendation.waitForExistence(timeout: 3))
    scrollToHittable(recommendation)
    attachScreenshot(named: "home-recommendations-ja")
    recommendation.tap()

    XCTAssertTrue(app.navigationBars["デッキ詳細"].waitForExistence(timeout: 5))
    XCTAssertTrue(app.staticTexts["TOPIK I 基本単語"].exists)
  }

  func testHomeRecommendationCardsKeepFixedHeightWithDifferentContentLengthsGlobalES() throws {
    try requireCompactPhoneDestination()
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: true)
    app.launchEnvironment["UITEST_DYNAMIC_TYPE_XSMALL"] = "1"
    app.launch()

    let recommendations = spanishHomeRecommendationCards()

    XCTAssertTrue(element("home.recommendations.personal").waitForExistence(timeout: 5))
    recommendations.forEach { card, title in
      XCTAssertTrue(card.waitForExistence(timeout: 3))
      XCTAssertTrue(card.label.contains(title), "Accessibility label must contain the full title")
    }

    let cardHeight = recommendations[0].0.frame.height
    XCTAssertEqual(cardHeight, regularRecommendationCardMinimumHeight, accuracy: 1)
    recommendations.dropFirst().forEach { card, _ in
      XCTAssertEqual(card.frame.height, cardHeight, accuracy: 1)
    }
    let nextStepSection = element("home.recommendations.next_step")
    XCTAssertTrue(nextStepSection.waitForExistence(timeout: 3))
    XCTAssertLessThanOrEqual(recommendations[0].0.frame.maxY, nextStepSection.frame.minY)

    scrollToHittable(recommendations[0].0)
    attachScreenshot(named: "home-recommendations-fixed-height-es")
    recommendations[0].0.tap()
    XCTAssertTrue(app.navigationBars["Detalles del mazo"].waitForExistence(timeout: 5))
    XCTAssertTrue(app.staticTexts[recommendations[0].1].exists)
  }

  func testHomeRecommendationCardsScaleFixedHeightAtAccessibilitySizeGlobalES() throws {
    try requireCompactPhoneDestination()
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: true)
    app.launchEnvironment["UITEST_DYNAMIC_TYPE_ACCESSIBILITY"] = "1"
    app.launch()

    let recommendations = spanishHomeRecommendationCards()
    XCTAssertTrue(element("home.recommendations.personal").waitForExistence(timeout: 5))
    recommendations.forEach { card, title in
      XCTAssertTrue(card.waitForExistence(timeout: 3))
      XCTAssertTrue(card.label.contains(title), "Accessibility label must contain the full title")
    }

    let cardHeight = recommendations[0].0.frame.height
    XCTAssertGreaterThan(cardHeight, regularRecommendationCardMinimumHeight)
    recommendations.dropFirst().forEach { card, _ in
      XCTAssertEqual(card.frame.height, cardHeight, accuracy: 1)
    }
    let visibleFrame = app.frame.insetBy(dx: 8, dy: 12)
    for _ in 0..<12 where !visibleFrame.intersects(recommendations[0].0.frame) {
      scrollVisibleSurface(.up)
    }
    XCTAssertTrue(visibleFrame.intersects(recommendations[0].0.frame))
    attachScreenshot(named: "home-recommendations-accessibility-fixed-height-es")
  }

  private func spanishHomeRecommendationCards() -> [(XCUIElement, String)] {
    [
      ("official_topik_one", "Vocabulario esencial del TOPIK I"),
      ("official_keyboard_start", "Primeros pasos con Dubeolsik"),
      ("official_trending_korean", "Jerga y tendencias coreanas"),
    ].map { deckID, title in
      (element("home.recommendation.\(deckID)"), title)
    }
  }

  private func requireCompactPhoneDestination() throws {
    guard max(app.frame.width, app.frame.height) < 1_000 else {
      throw XCTSkip("This fixed-height recommendation regression runs on iPhone destinations")
    }
  }

  func testContentTabsExceptDiscoverOfferSettingsFromTheTopBar() {
    let contentTabs = ["ホーム", "練習", "ゲーム", "マイページ"]
    XCTAssertFalse(app.tabBars.buttons["設定"].exists)

    let gameTab = app.tabBars.buttons["ゲーム"]
    let myDecksTab = app.tabBars.buttons["マイページ"]
    XCTAssertLessThan(gameTab.frame.minX, myDecksTab.frame.minX)

    for tab in contentTabs {
      let button = app.tabBars.buttons[tab]
      XCTAssertTrue(button.exists)
      button.tap()
      XCTAssertTrue(app.buttons["root.settings"].waitForExistence(timeout: 3))
    }

    app.tabBars.buttons["さがす"].tap()
    XCTAssertTrue(element("discover.search").waitForExistence(timeout: 3))
    XCTAssertFalse(app.navigationBars["さがす"].exists)
    XCTAssertFalse(app.buttons["root.settings"].exists)
  }

  func testRomanHintPreferencePersistsAcrossRelaunch() {
    openSettings()
    let romanHints = app.switches["settings.roman_hints"]
    scrollAndTap(romanHints)
    XCTAssertEqual(romanHints.value as? String, "0")

    app.terminate()
    app = makeApplication(resetKeyboardPreferences: false)
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    openSettings()

    let persistedRomanHints = app.switches["settings.roman_hints"]
    scrollToHittable(persistedRomanHints)
    XCTAssertEqual(persistedRomanHints.value as? String, "0")

    app.buttons["settings.done"].tap()
    startPractice()
    XCTAssertNotEqual(app.buttons["keyboard.key.ㅂ"].value as? String, "q")
  }

  func testDiscoverCatalogLoadsAndFiltersBundledFixture() {
    app.tabBars.buttons["さがす"].tap()

    let search = app.textFields["discover.search"]
    XCTAssertTrue(search.waitForExistence(timeout: 5))
    XCTAssertFalse(app.navigationBars["さがす"].exists)
    XCTAssertFalse(app.buttons["root.settings"].exists)
    XCTAssertLessThan(search.frame.minY, app.frame.height * 0.12)
    XCTAssertTrue(element("discover.catalog").waitForExistence(timeout: 5))
    XCTAssertTrue(element("discover.deck.official_keyboard_start").waitForExistence(timeout: 3))

    search.tap()
    search.typeText("TOPIK")

    XCTAssertTrue(element("discover.results.count").waitForExistence(timeout: 3))
    XCTAssertTrue(element("discover.deck.official_topik_one").waitForExistence(timeout: 3))
    attachScreenshot(named: "discover-search-twice-ja")
  }

  func testDiscoverAndDeckDetailExposeContentFeedbackLinks() {
    app.tabBars.buttons["さがす"].tap()

    let search = app.textFields["discover.search"]
    XCTAssertTrue(search.waitForExistence(timeout: 5))
    search.tap()
    search.typeText("存在しないデッキ")

    let suggestion = element("discover.deck_suggestion")
    scrollToHittable(suggestion)
    XCTAssertTrue(suggestion.isHittable)

    app.terminate()
    app = makeApplication(resetKeyboardPreferences: false)
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    app.tabBars.buttons["さがす"].tap()

    let reloadedSearch = app.textFields["discover.search"]
    XCTAssertTrue(reloadedSearch.waitForExistence(timeout: 5))
    reloadedSearch.tap()
    reloadedSearch.typeText("毎日使う短いフレーズ")

    let deckCard = element("discover.deck.official_daily_phrases")
    XCTAssertTrue(deckCard.waitForExistence(timeout: 3))
    deckCard.tap()
    XCTAssertTrue(app.navigationBars["デッキ詳細"].waitForExistence(timeout: 5))

    let contentReport = element("deck.detail.content_report")
    scrollToHittable(contentReport)
    XCTAssertTrue(contentReport.isHittable)
  }

  func testDeckDetailDownloadsAndStartsOfflinePracticeWithinThreeTaps() {
    app.tabBars.buttons["さがす"].tap()
    XCTAssertTrue(app.textFields["discover.search"].waitForExistence(timeout: 5))

    let search = app.textFields["discover.search"]
    search.tap()
    search.typeText("毎日使う短いフレーズ")

    let deckCard = element("discover.deck.official_daily_phrases")
    XCTAssertTrue(deckCard.waitForExistence(timeout: 3))
    deckCard.tap()

    XCTAssertTrue(app.navigationBars["デッキ詳細"].waitForExistence(timeout: 5))
    XCTAssertTrue(element("deck.preview.0").waitForExistence(timeout: 3))
    attachScreenshot(named: "deck-detail-twice-ja")

    let download = app.buttons["deck.detail.download"]
    XCTAssertTrue(download.waitForExistence(timeout: 3))
    download.tap()

    let play = app.buttons["deck.detail.play"]
    XCTAssertTrue(play.waitForExistence(timeout: 5))
    play.tap()

    XCTAssertTrue(element("practice.target.value").waitForExistence(timeout: 5))
    XCTAssertEqual(element("practice.target.value").label, "행복하게 웃어요")
    XCTAssertFalse(app.tabBars.buttons["さがす"].exists)

    for key in Array("ㅎㅐㅇㅂㅗㄱㅎㅏㄱㅔ") {
      app.buttons["keyboard.key.\(key)"].tap()
    }
    app.buttons["keyboard.space"].tap()
    app.buttons["keyboard.key.ㅇ"].tap()

    waitForValue("12 / 18", on: element("practice.jamo_progress.value"), timeout: 3)
    let activeJamo = element("practice.jamo.active")
    let activeJamoVisible = XCTNSPredicateExpectation(
      predicate: NSPredicate(format: "hittable == true"),
      object: activeJamo
    )
    XCTAssertEqual(XCTWaiter.wait(for: [activeJamoVisible], timeout: 3), .completed)
    XCTAssertEqual(activeJamo.label, "ㅜ")
    attachScreenshot(named: "practice-long-jamo-follow-ja")
  }

  func testInstalledDeckPersistsInMyDecksAcrossRelaunch() {
    app.tabBars.buttons["さがす"].tap()
    XCTAssertTrue(app.textFields["discover.search"].waitForExistence(timeout: 5))

    let search = app.textFields["discover.search"]
    search.tap()
    search.typeText("TOPIK")
    let deckCard = element("discover.deck.official_topik_one")
    XCTAssertTrue(deckCard.waitForExistence(timeout: 3))
    deckCard.tap()

    let download = app.buttons["deck.detail.download"]
    XCTAssertTrue(download.waitForExistence(timeout: 3))
    download.tap()
    XCTAssertTrue(app.buttons["deck.detail.play"].waitForExistence(timeout: 5))

    app.terminate()
    app = makeApplication(resetKeyboardPreferences: false)
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    app.tabBars.buttons["マイページ"].tap()

    XCTAssertTrue(app.navigationBars["マイページ"].waitForExistence(timeout: 5))
    let installedDeck = element("my_decks.deck.official_topik_one")
    scrollToHittable(installedDeck)
    XCTAssertTrue(installedDeck.exists)
    attachScreenshot(named: "my-decks-installed-ja")
  }

  func testDeckResultShowsSameTagRecommendationsAndRetriesInOneTap() {
    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: true,
      deckItemLimit: 2,
      resultAnimationScale: 4
    )
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    app.tabBars.buttons["さがす"].tap()

    let search = app.textFields["discover.search"]
    XCTAssertTrue(search.waitForExistence(timeout: 5))
    search.tap()
    search.typeText("毎日の韓国語")
    let deckCard = element("discover.deck.official_daily_words")
    XCTAssertTrue(deckCard.waitForExistence(timeout: 3))
    deckCard.tap()
    app.buttons["deck.detail.download"].tap()
    XCTAssertTrue(app.buttons["deck.detail.play"].waitForExistence(timeout: 5))
    app.buttons["deck.detail.play"].tap()

    XCTAssertEqual(element("practice.target.value").label, "회사")
    for key in Array("ㅎㅗㅣㅅㅏ") {
      app.buttons["keyboard.key.\(key)"].tap()
    }
    waitForLabel("학교", on: element("practice.target.value"), timeout: 3)
    for key in Array("ㅎㅏㄱㄱㅛ") {
      app.buttons["keyboard.key.\(key)"].tap()
    }
    XCTAssertTrue(element("practice.result.screen").waitForExistence(timeout: 5))
    XCTAssertFalse(app.buttons["practice.show_result"].exists)
    let retry = app.buttons["practice.result.retry"]
    XCTAssertFalse(retry.isEnabled)
    XCTAssertEqual(element("result.animation.state").label, "結果演出中")

    element("practice.result.screen").tap()
    XCTAssertTrue(retry.isEnabled)
    XCTAssertTrue(element("practice.result.screen").exists)
    XCTAssertEqual(element("result.animation.state").label, "結果を操作できます")
    XCTAssertTrue(element("practice.result.recommendations").exists)
    XCTAssertEqual(element("practice.result.accuracy.value").label, "100.0%")
    XCTAssertTrue(app.buttons["practice.result.share"].exists)
    XCTAssertEqual(app.buttons["result.done"].label, "完了")
    XCTAssertEqual(app.buttons["result.done.bottom"].label, "前の画面へ")
    attachScreenshot(named: "practice-result-recommendations-ja")

    retry.tap()
    let restartedTargets = app.descendants(matching: .any)
      .matching(identifier: "practice.target.value")
    XCTAssertTrue(restartedTargets.firstMatch.waitForExistence(timeout: 5))
    XCTAssertEqual(restartedTargets.count, 1)
    XCTAssertEqual(restartedTargets.firstMatch.label, "회사")
    XCTAssertEqual(restartedTargets.firstMatch.value as? String, "0 / 2 音節完了")
    XCTAssertEqual(element("practice.entered_text.value").value as? String, "…")
    XCTAssertEqual(element("practice.jamo_progress.value").value as? String, "0 / 5")
    attachScreenshot(named: "practice-retry-first-card-iphone-ja")
  }

  func testDoneExitsPracticeAndGameResultsEvenDuringReveal() {
    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: true,
      deckItemLimit: 1,
      gameDuration: 2,
      resultAnimationScale: 4,
      practiceAutoSpeaks: true,
      audioProbe: true
    )
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))

    app.tabBars.buttons["さがす"].tap()
    let search = app.textFields["discover.search"]
    XCTAssertTrue(search.waitForExistence(timeout: 5))
    search.tap()
    search.typeText("毎日の韓国語")
    let deckCard = element("discover.deck.official_daily_words")
    XCTAssertTrue(deckCard.waitForExistence(timeout: 3))
    deckCard.tap()
    app.buttons["deck.detail.download"].tap()
    XCTAssertTrue(app.buttons["deck.detail.play"].waitForExistence(timeout: 5))
    app.buttons["deck.detail.play"].tap()

    for key in Array("ㅎㅗㅣㅅㅏ") {
      app.buttons["keyboard.key.\(key)"].tap()
    }
    XCTAssertTrue(element("practice.result.screen").waitForExistence(timeout: 5))
    XCTAssertFalse(app.buttons["practice.show_result"].exists)
    XCTAssertEqual(element("result.animation.state").label, "結果演出中")
    let pronunciationStarts = element("debug.pronunciation.start_count")
    XCTAssertTrue(pronunciationStarts.waitForExistence(timeout: 3))
    let pronunciationStartsBeforeDone = pronunciationStarts.label
    XCTAssertNotEqual(pronunciationStartsBeforeDone, "0")
    element("practice.result.screen").tap()
    let practiceBack = app.buttons["result.done.bottom"]
    XCTAssertTrue(practiceBack.isEnabled)
    practiceBack.tap()
    XCTAssertFalse(element("practice.target.value").exists)
    XCTAssertTrue(app.buttons["deck.detail.play"].waitForExistence(timeout: 5))
    XCTAssertEqual(pronunciationStarts.label, pronunciationStartsBeforeDone)

    app.tabBars.buttons["ゲーム"].tap()
    XCTAssertTrue(element("game.selection.screen").waitForExistence(timeout: 5))
    app.buttons["game.mode.flow"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    element("game.flow.preset.beginner").tap()
    XCTAssertTrue(element("game.result.screen").waitForExistence(timeout: 8))
    XCTAssertFalse(element("game.result.game_center").exists)
    XCTAssertEqual(element("result.animation.state").label, "結果演出中")
    let gameDone = app.buttons["result.done"]
    XCTAssertTrue(gameDone.isEnabled)
    gameDone.tap()
    XCTAssertFalse(element("game.play.screen").exists)
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
  }

  func testMistakenDeckItemAppearsInReviewDeckAndStartsPractice() {
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: true, deckItemLimit: 1)
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))

    app.tabBars.buttons["さがす"].tap()
    let search = app.textFields["discover.search"]
    XCTAssertTrue(search.waitForExistence(timeout: 5))
    search.tap()
    search.typeText("毎日の韓国語")
    let deckCard = element("discover.deck.official_daily_words")
    XCTAssertTrue(deckCard.waitForExistence(timeout: 3))
    deckCard.tap()
    app.buttons["deck.detail.download"].tap()
    XCTAssertTrue(app.buttons["deck.detail.play"].waitForExistence(timeout: 5))
    app.buttons["deck.detail.play"].tap()

    XCTAssertEqual(element("practice.target.value").label, "회사")
    app.buttons["keyboard.key.ㄱ"].tap()
    for key in Array("ㅎㅗㅣㅅㅏ") {
      app.buttons["keyboard.key.\(key)"].tap()
    }
    XCTAssertTrue(element("practice.result.screen").waitForExistence(timeout: 5))
    XCTAssertFalse(app.buttons["practice.show_result"].exists)
    element("practice.result.screen").tap()
    XCTAssertTrue(element("result.review.list").waitForExistence(timeout: 3))
    let reviewedItem = element("result.review.item.i_001")
    XCTAssertTrue(reviewedItem.exists, app.debugDescription)
    XCTAssertTrue(reviewedItem.label.contains("フェサ"))
    let resultReview = app.buttons["practice.result.review"]
    XCTAssertTrue(resultReview.exists)
    attachScreenshot(named: "practice-result-review-ja")

    resultReview.tap()
    XCTAssertTrue(element("practice.target.value").waitForExistence(timeout: 5))
    XCTAssertEqual(element("practice.target.value").label, "회사")

    app.terminate()
    app = makeApplication(resetKeyboardPreferences: false)
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    app.tabBars.buttons["マイページ"].tap()

    let reviewDeck = element("my_decks.review_deck")
    scrollToHittable(reviewDeck)
    reviewDeck.tap()
    XCTAssertTrue(element("review.deck.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("review.deck.item.official_daily_words::i_001").exists)
    XCTAssertTrue(app.buttons["復習デッキから削除"].exists)

    app.buttons["review.deck.start"].tap()
    XCTAssertTrue(element("practice.target.value").waitForExistence(timeout: 5))
    XCTAssertEqual(element("practice.target.value").label, "회사")
  }

  func testFlowPresetsAndAddDeckRouteToDiscover() {
    app.tabBars.buttons["ゲーム"].tap()

    XCTAssertTrue(element("game.selection.screen").waitForExistence(timeout: 5))
    XCTAssertFalse(app.staticTexts["プレイできるデッキがありません"].exists)
    app.buttons["game.mode.flow"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("game.flow.preset.beginner").exists)
    XCTAssertTrue(element("game.flow.preset.intermediate").exists)
    XCTAssertTrue(element("game.flow.preset.advanced").exists)
    XCTAssertFalse(app.staticTexts["プレイできるデッキがありません"].exists)
    attachScreenshot(named: "game-flow-presets-ja")
    let addDeck = element("game.flow.add_deck")
    scrollToHittable(addDeck)
    addDeck.tap()

    XCTAssertTrue(element("discover.search").waitForExistence(timeout: 5))
    XCTAssertFalse(app.navigationBars["さがす"].exists)
    XCTAssertFalse(app.buttons["root.settings"].exists)
    XCTAssertTrue(element("discover.catalog").waitForExistence(timeout: 5))
  }

  func testFlowKeepsLongJamoTrackAndKeyboardInsideScreen() {
    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: true,
      gameDuration: 30,
      flowStartIndex: 85
    )
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))

    app.tabBars.buttons["ゲーム"].tap()
    app.buttons["game.mode.flow"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))

    let advanced = element("game.flow.preset.advanced")
    scrollToHittable(advanced)
    advanced.tap()

    XCTAssertTrue(element("game.play.screen").waitForExistence(timeout: 5))
    XCTAssertEqual(element("game.target.value").label, "지속가능성")

    let jamoTrack = element("game.jamo.track")
    XCTAssertTrue(jamoTrack.waitForExistence(timeout: 3))
    XCTAssertGreaterThanOrEqual(jamoTrack.frame.minX, app.frame.minX)
    XCTAssertLessThanOrEqual(jamoTrack.frame.maxX, app.frame.maxX)

    let leadingKey = app.buttons["keyboard.key.ㅂ"]
    let trailingKey = app.buttons["keyboard.key.ㅔ"]
    XCTAssertTrue(leadingKey.isHittable)
    XCTAssertTrue(trailingKey.isHittable)
    XCTAssertGreaterThanOrEqual(leadingKey.frame.minX, app.frame.minX)
    XCTAssertLessThanOrEqual(trailingKey.frame.maxX, app.frame.maxX)

    for key in Array("ㅈㅣㅅㅗㄱㄱㅏㄴ") {
      app.buttons["keyboard.key.\(key)"].tap()
    }
    let activeJamo = element("game.jamo.active")
    XCTAssertTrue(activeJamo.waitForExistence(timeout: 3))
    XCTAssertGreaterThanOrEqual(activeJamo.frame.minX, jamoTrack.frame.minX)
    XCTAssertLessThanOrEqual(activeJamo.frame.maxX, jamoTrack.frame.maxX)
    attachScreenshot(named: "game-flow-long-jamo-track-ja")
  }

  func testAcidRainPresetsAndAddDeckRouteToDiscover() {
    app.tabBars.buttons["ゲーム"].tap()

    XCTAssertTrue(element("game.selection.screen").waitForExistence(timeout: 5))
    app.buttons["game.mode.acid_rain"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("game.acid_rain.preset.beginner").exists)
    XCTAssertTrue(element("game.acid_rain.preset.intermediate").exists)
    XCTAssertTrue(element("game.acid_rain.preset.advanced").exists)
    XCTAssertFalse(app.staticTexts["プレイできるデッキがありません"].exists)
    attachScreenshot(named: "acid-rain-presets-ja")

    let addDeck = element("game.acid_rain.add_deck")
    scrollToHittable(addDeck)
    addDeck.tap()

    XCTAssertTrue(element("discover.search").waitForExistence(timeout: 5))
    XCTAssertTrue(element("discover.catalog").waitForExistence(timeout: 5))
  }

  func testWordMatchPresetsAndAddDeckRouteToDiscover() {
    assertGamePresetsAndAddDeckRouteToDiscover(gameKind: "word_match")
  }

  func testChoseongPresetsAndAddDeckRouteToDiscover() {
    assertGamePresetsAndAddDeckRouteToDiscover(gameKind: "choseong")
  }

  func testDictationPresetsAndAddDeckRouteToDiscover() {
    assertGamePresetsAndAddDeckRouteToDiscover(gameKind: "dictation")
  }

  func testGameSelectionShowsGameCardsBeforeDecks() {
    app.tabBars.buttons["ゲーム"].tap()

    XCTAssertTrue(element("game.selection.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("game.mode.grid").exists)
    XCTAssertFalse(element("game_center.leaderboards").exists)
    XCTAssertFalse(app.staticTexts["Game Centerランキング"].exists)
    XCTAssertTrue(app.staticTexts["週間ピヨカップ"].exists)
    XCTAssertTrue(app.buttons["game.mode.flow"].exists)
    XCTAssertTrue(app.buttons["game.mode.acid_rain"].exists)
    let choseongMode = app.buttons["game.mode.choseong"]
    let dictationMode = app.buttons["game.mode.dictation"]
    let wordMatchMode = app.buttons["game.mode.word_match"]
    let spacingMode = app.buttons["game.mode.spacing"]
    XCTAssertTrue(choseongMode.exists)
    XCTAssertTrue(dictationMode.exists)
    XCTAssertTrue(wordMatchMode.exists)
    XCTAssertEqual(dictationMode.frame.midY, choseongMode.frame.midY, accuracy: 4)
    XCTAssertEqual(wordMatchMode.frame.midY, spacingMode.frame.midY, accuracy: 4)
    XCTAssertLessThan(dictationMode.frame.midY, wordMatchMode.frame.midY)
    XCTAssertTrue(app.buttons["game.mode.spacing"].exists)
    XCTAssertTrue(app.staticTexts["60秒で流れるカードをできるだけ多くタイピングしよう。"].exists)
    XCTAssertTrue(app.staticTexts["上から落ちる韓国語を地面に着く前にタイピングしよう。"].exists)
    XCTAssertTrue(app.staticTexts["韓国語の初声を見て、答えの単語を自分でタイピングしよう。"].exists)
    XCTAssertTrue(app.staticTexts["日本語の意味を見て、答えの韓国語を自分でタイピングしよう。"].exists)
    scrollToHittable(dictationMode)
    XCTAssertTrue(app.staticTexts["韓国語の音声だけを聞いて、答えをタイピングしよう。"].exists)
    XCTAssertFalse(element("game.deck_selection.screen").exists)
    XCTAssertFalse(element("game.deck.official_daily_words").exists)
    XCTAssertFalse(element("game.selection.hero_card").exists)
    XCTAssertFalse(element("game.selection.hero_mark").exists)
    XCTAssertFalse(element("mascot.current").exists)
    XCTAssertFalse(app.staticTexts["プレイできるデッキがありません"].exists)
    attachScreenshot(named: "game-mode-grid-ja")
  }

  func testWeeklyPiyoCupStartsBundledRankedFlowWithoutInstallingDeck() {
    app.tabBars.buttons["ゲーム"].tap()

    let piyoCup = app.staticTexts["週間ピヨカップ"]
    XCTAssertTrue(piyoCup.waitForExistence(timeout: 5))
    XCTAssertTrue(piyoCup.isHittable)
    XCTAssertFalse(element("game.deck.official_daily_words").exists)
    piyoCup.tap()

    XCTAssertTrue(element("game.play.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("game.target.value").waitForExistence(timeout: 3))
    XCTAssertTrue(app.buttons["keyboard.key.ㅎ"].exists)
    let remainingSeconds = Int(element("game.timer.value").label)
    XCTAssertNotNil(remainingSeconds)
    XCTAssertGreaterThan(remainingSeconds ?? 0, 55)
    XCTAssertLessThanOrEqual(remainingSeconds ?? 0, 60)
  }

  func testSpacingGameStartsWithoutADeckAndShowsTheAnswerResult() {
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: true)
    app.launchEnvironment["UITEST_SHORT_SPACING_PASSAGE"] = "1"
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))

    app.tabBars.buttons["ゲーム"].tap()

    let spacingMode = app.buttons["game.mode.spacing"]
    scrollToHittable(spacingMode)
    spacingMode.tap()

    XCTAssertTrue(element("spacing.selection.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(app.staticTexts["上から順に難しくなる6つの文章から選び、消えたスペースを元に戻しましょう。"].exists)

    element("spacing.passage.morning_commute").tap()
    XCTAssertTrue(element("spacing.play.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("spacing.passage.display").exists)
    XCTAssertFalse(element("spacing.focus").exists)
    XCTAssertTrue(element("spacing.progress.bar").exists)
    XCTAssertTrue(app.buttons["spacing.control.previous"].exists)
    XCTAssertTrue(app.buttons["spacing.control.space"].exists)
    XCTAssertTrue(app.buttons["spacing.control.next"].exists)
    XCTAssertFalse(app.buttons["spacing.control.previous"].isEnabled)
    XCTAssertFalse(app.buttons["spacing.submit"].isEnabled)
    XCTAssertFalse(element("game.deck_selection.screen").exists)

    app.buttons["spacing.control.space"].tap()
    XCTAssertTrue(element("spacing.feedback.incorrect").exists)
    app.buttons["spacing.control.space"].tap()

    let nextButton = app.buttons["spacing.control.next"]
    nextButton.tap()
    let previousButton = app.buttons["spacing.control.previous"]
    expectation(for: NSPredicate(format: "enabled == true"), evaluatedWith: previousButton)
    waitForExpectations(timeout: 2)
    app.buttons["spacing.control.space"].tap()
    XCTAssertTrue(element("spacing.feedback.correct").exists)

    nextButton.tap()
    nextButton.tap()

    let finishButton = app.buttons["spacing.finish"]
    XCTAssertTrue(finishButton.waitForExistence(timeout: 2))
    expectation(for: NSPredicate(format: "enabled == true"), evaluatedWith: finishButton)
    waitForExpectations(timeout: 2)
    finishButton.tap()
    XCTAssertTrue(element("spacing.result.screen").waitForExistence(timeout: 5))
    element("spacing.result.screen").tap()
    XCTAssertTrue(element("spacing.result.first_accuracy").exists)
    XCTAssertTrue(element("spacing.result.final_accuracy").exists)
    XCTAssertTrue(element("spacing.result.review").exists)
    XCTAssertTrue(element("spacing.result.answer").exists)
    XCTAssertTrue(app.buttons["spacing.result.retry"].exists)
  }

  func testSpacingSelectionShowsSixAscendingLevelsOnOneScreen() {
    app.tabBars.buttons["ゲーム"].tap()

    let spacingMode = app.buttons["game.mode.spacing"]
    scrollToHittable(spacingMode)
    spacingMode.tap()

    XCTAssertTrue(element("spacing.selection.screen").waitForExistence(timeout: 5))
    let passageIDs = [
      "morning_commute",
      "weekend_trip",
      "family_dinner",
      "library_afternoon",
      "language_practice",
      "careful_judgment",
    ]
    for (index, id) in passageIDs.enumerated() {
      let passage = element("spacing.passage.\(id)")
      XCTAssertTrue(passage.exists, "Missing spacing level \(index + 1)")
      XCTAssertTrue(passage.isHittable, "Spacing level \(index + 1) is outside the initial screen")
      XCTAssertTrue(app.staticTexts["レベル\(index + 1)"].exists)
    }
    attachScreenshot(named: "spacing-six-levels-ja")
  }

  func testAcidRainAndWordMatchStartFromTheirOwnGameCards() {
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: true, gameDuration: 5)
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))

    app.tabBars.buttons["さがす"].tap()
    let search = app.textFields["discover.search"]
    XCTAssertTrue(search.waitForExistence(timeout: 5))
    search.tap()
    search.typeText("毎日の韓国語")
    let deckCard = element("discover.deck.official_daily_words")
    XCTAssertTrue(deckCard.waitForExistence(timeout: 3))
    deckCard.tap()
    app.buttons["deck.detail.download"].tap()
    XCTAssertTrue(app.buttons["deck.detail.play"].waitForExistence(timeout: 5))

    app.tabBars.buttons["ゲーム"].tap()
    XCTAssertTrue(app.buttons["game.mode.acid_rain"].waitForExistence(timeout: 5))
    app.buttons["game.mode.acid_rain"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("game.acid_rain.preset.beginner").exists)
    XCTAssertTrue(element("game.acid_rain.preset.intermediate").exists)
    XCTAssertTrue(element("game.acid_rain.preset.advanced").exists)
    XCTAssertTrue(element("game.acid_rain.add_deck").exists)
    XCTAssertTrue(element("game.deck.official_daily_words").exists)
    element("game.acid_rain.preset.beginner").tap()
    XCTAssertTrue(element("acid_rain.play.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("acid_rain.lane").exists)
    XCTAssertTrue(element("acid_rain.target.value").exists)
    let fallingCards = app.descendants(matching: .any).matching(
      identifier: "acid_rain.falling_card"
    )
    let fallingCard = fallingCards.element(boundBy: 0)
    XCTAssertTrue(fallingCard.exists)
    XCTAssertTrue((fallingCard.value as? String)?.contains("ナ") == true)
    let secondFallingCard = fallingCards.element(boundBy: 1)
    XCTAssertTrue(secondFallingCard.waitForExistence(timeout: 4))
    XCTAssertNotEqual(fallingCard.frame, secondFallingCard.frame)
    XCTAssertTrue(element("game.mascot").exists)
    XCTAssertEqual(element("game.lives.value").label, "3")
    XCTAssertTrue(element("game.feedback.status").exists)
    XCTAssertEqual(
      element("game.feedback.status").frame.midX,
      app.frame.midX,
      accuracy: 3
    )
    XCTAssertTrue(app.buttons["game.end"].exists)
    XCTAssertFalse(app.buttons["practice.session_settings"].exists)
    XCTAssertFalse(element("debug.game_fps").exists)
    XCTAssertFalse(app.navigationBars.staticTexts["毎日の韓国語"].exists)
    attachScreenshot(named: "acid-rain-game-ja")

    XCTAssertTrue(element("game.result.screen").waitForExistence(timeout: 8))
    element("game.result.screen").tap()
    XCTAssertTrue(app.staticTexts["単語の雨クリア！"].exists)
    XCTAssertEqual(element("game.result.mode").label, "単語の雨")
    XCTAssertEqual(element("game.result.level").label, "初級")
    XCTAssertTrue(element("game.result.input_mode").exists)
    XCTAssertTrue(element("game.result.game_center").exists)
    XCTAssertFalse(element("game.result.game_center").isEnabled)
    XCTAssertTrue(app.buttons["game.result.share.save_image"].exists)
    XCTAssertTrue(app.buttons["game.result.share"].exists)
    app.buttons["result.done"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    app.navigationBars.buttons["ゲーム"].tap()
    XCTAssertTrue(app.buttons["game.mode.word_match"].waitForExistence(timeout: 5))
    app.buttons["game.mode.word_match"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("game.word_match.preset.beginner").exists)
    XCTAssertTrue(element("game.word_match.preset.intermediate").exists)
    XCTAssertTrue(element("game.word_match.preset.advanced").exists)
    XCTAssertTrue(element("game.word_match.add_deck").exists)
    element("game.word_match.preset.beginner").tap()
    XCTAssertTrue(element("word_match.play.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("word_match.prompt.value").exists)
    XCTAssertTrue(element("word_match.composition_card").exists)
    XCTAssertTrue(element("word_match.typing.value").exists)
    XCTAssertTrue(element("word_match.typing.feedback").exists)
    XCTAssertTrue(element("word_match.mascot").exists)
    XCTAssertTrue(app.buttons["keyboard.key.ㅎ"].exists)
    XCTAssertFalse(app.buttons["word_match.option.correct"].exists)
    attachScreenshot(named: "word-match-game-ja")
    for question in 1...10 {
      waitForLabel(
        "\(question)/10",
        on: element("word_match.question.value"),
        timeout: 3
      )
      let answerKeys = element("word_match.answer.keys")
      XCTAssertTrue(answerKeys.waitForExistence(timeout: 3))
      XCTAssertFalse(answerKeys.label.isEmpty)
      for key in answerKeys.label {
        if !app.buttons["keyboard.key.\(key)"].exists {
          let shift = app.buttons["keyboard.shift"]
          XCTAssertTrue(shift.exists)
          shift.tap()
        }
        let keyButton = app.buttons["keyboard.key.\(key)"]
        XCTAssertTrue(keyButton.exists)
        keyButton.tap()
      }
    }
    XCTAssertTrue(element("game.result.screen").waitForExistence(timeout: 5))
    element("game.result.screen").tap()
    XCTAssertTrue(app.staticTexts["単語クイズ完了！"].exists)
    XCTAssertEqual(element("game.result.mode").label, "単語クイズ")
    XCTAssertEqual(element("game.result.level").label, "初級")
    XCTAssertTrue(element("game.result.input_mode").exists)
  }

  func testAcidRainOSIMECanClearAnyVisibleFallingWord() {
    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: true,
      gameDuration: 10,
      koreanKeyboardAvailable: true
    )
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))

    openSettings()
    let osKeyboard = app.buttons["input_mode.os_ime"]
    scrollToHittable(osKeyboard)
    osKeyboard.tap()
    XCTAssertTrue(osKeyboard.isSelected)
    app.buttons["settings.done"].tap()

    app.tabBars.buttons["ゲーム"].tap()
    app.buttons["game.mode.acid_rain"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    element("game.acid_rain.preset.beginner").tap()
    XCTAssertTrue(element("acid_rain.play.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("acid_rain.os_ime.free_input_status").exists)

    let fallingCards = app.descendants(matching: .any).matching(
      identifier: "acid_rain.falling_card"
    )
    let firstCard = fallingCards.element(boundBy: 0)
    let freelyChosenCard = fallingCards.element(boundBy: 1)
    XCTAssertTrue(firstCard.exists)
    XCTAssertTrue(freelyChosenCard.waitForExistence(timeout: 4))
    let firstWord = firstCard.label
    let freelyChosenWord = freelyChosenCard.label
    XCTAssertNotEqual(firstWord, freelyChosenWord)

    let matchingChosenCard = fallingCards.matching(
      NSPredicate(format: "label == %@", freelyChosenWord)
    ).firstMatch
    let imeField = app.textFields["os_ime.input.chrome"]
    XCTAssertTrue(imeField.waitForExistence(timeout: 3))
    imeField.tap()
    imeField.typeText(freelyChosenWord)

    expectation(
      for: NSPredicate(format: "exists == false"),
      evaluatedWith: matchingChosenCard
    )
    waitForExpectations(timeout: 3)
    XCTAssertNotEqual(element("game.score.value").label, "0")
    attachScreenshot(named: "acid-rain-os-ime-free-target-ja")
  }

  func testInstalledDeckStartsTimedFlowGameAndRetriesFromResult() {
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: true, gameDuration: 8)
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))

    app.tabBars.buttons["さがす"].tap()
    let search = app.textFields["discover.search"]
    XCTAssertTrue(search.waitForExistence(timeout: 5))
    search.tap()
    search.typeText("毎日の韓国語")
    let deckCard = element("discover.deck.official_daily_words")
    XCTAssertTrue(deckCard.waitForExistence(timeout: 3))
    deckCard.tap()
    app.buttons["deck.detail.download"].tap()
    XCTAssertTrue(app.buttons["deck.detail.play"].waitForExistence(timeout: 5))

    app.tabBars.buttons["ゲーム"].tap()
    XCTAssertTrue(element("game.selection.screen").waitForExistence(timeout: 5))
    XCTAssertFalse(element("game.deck.official_daily_words").exists)
    attachScreenshot(named: "game-mode-grid-ja")
    app.buttons["game.mode.flow"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("game.flow.preset.beginner").exists)
    XCTAssertTrue(element("game.flow.preset.intermediate").exists)
    XCTAssertTrue(element("game.flow.preset.advanced").exists)
    XCTAssertTrue(element("game.flow.add_deck").exists)
    let installedFlowDeck = element("game.deck.official_daily_words")
    scrollToHittable(installedFlowDeck)
    attachScreenshot(named: "game-flow-decks-ja")
    installedFlowDeck.tap()

    XCTAssertTrue(element("game.play.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("game.mascot").waitForExistence(timeout: 3))
    XCTAssertEqual(element("game.lives.value").label, "3")
    XCTAssertTrue(app.buttons["game.end"].exists)
    XCTAssertFalse(app.buttons["practice.session_settings"].exists)
    XCTAssertFalse(element("debug.game_fps").exists)
    XCTAssertFalse(app.buttons["ゲームを最初からやり直す"].exists)
    XCTAssertTrue(element("game.flow.simple_lane").exists)
    XCTAssertFalse(element("game.flow.workshop_title").exists)
    XCTAssertTrue(element("game.target.value").waitForExistence(timeout: 3))
    XCTAssertEqual(element("game.target.value").label, "회사")
    XCTAssertTrue((element("game.target.value").value as? String)?.contains("フェサ") == true)
    XCTAssertTrue(element("game.feedback.status").exists)
    XCTAssertEqual(
      element("game.feedback.status").frame.midX,
      app.frame.midX,
      accuracy: 3
    )
    attachScreenshot(named: "game-flow-ja")

    let timerBeforeBackground = Int(element("game.timer.value").label)
    XCTAssertNotNil(timerBeforeBackground)
    XCUIDevice.shared.press(.home)
    Thread.sleep(forTimeInterval: 2)
    app.activate()
    XCTAssertTrue(element("game.play.screen").waitForExistence(timeout: 5))
    let timerAfterBackground = Int(element("game.timer.value").label)
    XCTAssertNotNil(timerAfterBackground)
    XCTAssertGreaterThanOrEqual(
      timerAfterBackground ?? 0,
      (timerBeforeBackground ?? 0) - 1,
      "Background time must not consume the game timer"
    )

    XCTAssertTrue(element("game.result.screen").waitForExistence(timeout: 13))
    element("game.result.screen").tap()
    XCTAssertEqual(element("game.result.score.value").label, "0")
    XCTAssertTrue(element("game.result.new_record").waitForExistence(timeout: 3))
    XCTAssertEqual(element("result.animation.state").label, "結果を操作できます")
    attachScreenshot(named: "game-result-ja")

    app.buttons["game.result.retry"].tap()
    XCTAssertTrue(element("game.play.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("game.timer.value").exists)
  }

  func testChoseongQuizCompletesTenQuestionsAndShowsSeparateResult() {
    app.tabBars.buttons["さがす"].tap()
    let search = app.textFields["discover.search"]
    XCTAssertTrue(search.waitForExistence(timeout: 5))
    search.tap()
    search.typeText("毎日の韓国語")
    let deckCard = element("discover.deck.official_daily_words")
    XCTAssertTrue(deckCard.waitForExistence(timeout: 3))
    deckCard.tap()
    app.buttons["deck.detail.download"].tap()
    XCTAssertTrue(app.buttons["deck.detail.play"].waitForExistence(timeout: 5))

    app.tabBars.buttons["ゲーム"].tap()
    let choseongMode = app.buttons["game.mode.choseong"]
    XCTAssertTrue(choseongMode.waitForExistence(timeout: 5))
    XCTAssertTrue(app.staticTexts["韓国語の初声を見て、答えの単語を自分でタイピングしよう。"].exists)
    choseongMode.tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("game.choseong.preset.beginner").exists)
    XCTAssertTrue(element("game.choseong.preset.intermediate").exists)
    XCTAssertTrue(element("game.choseong.preset.advanced").exists)
    XCTAssertTrue(element("game.choseong.add_deck").exists)
    element("game.choseong.preset.beginner").tap()

    XCTAssertTrue(element("choseong.play.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("choseong.initials.value").exists)
    XCTAssertTrue(element("choseong.hint.value").exists)
    XCTAssertFalse(app.buttons["choseong.hint.show"].exists)
    XCTAssertTrue(app.buttons["keyboard.key.ㅎ"].exists)
    XCTAssertTrue(element("choseong.typing.value").exists)
    XCTAssertTrue(element("choseong.mascot").exists)
    XCTAssertTrue(element("choseong.typing.feedback").exists)
    XCTAssertEqual(
      element("choseong.typing.feedback").frame.midX,
      app.frame.midX,
      accuracy: 3
    )
    XCTAssertFalse(app.buttons["choseong.option.correct"].exists)
    XCTAssertTrue(app.buttons["game.end"].exists)
    XCTAssertFalse(app.buttons["choseong.session_settings"].exists)
    XCTAssertFalse(app.buttons["input_mode.built_in"].exists)
    XCTAssertFalse(app.buttons["input_mode.os_ime"].exists)
    let meaningHint = element("choseong.hint.value")
    XCTAssertGreaterThan(meaningHint.frame.height, 30)
    attachScreenshot(named: "choseong-game-ja")
    for question in 1...10 {
      waitForLabel(
        "\(question)/10",
        on: element("choseong.question.value"),
        timeout: 3
      )
      let answerKeys = element("choseong.answer.keys")
      XCTAssertTrue(answerKeys.waitForExistence(timeout: 3))
      XCTAssertFalse(answerKeys.label.isEmpty)
      for key in answerKeys.label {
        if !app.buttons["keyboard.key.\(key)"].exists {
          let shift = app.buttons["keyboard.shift"]
          XCTAssertTrue(shift.exists)
          shift.tap()
        }
        let keyButton = app.buttons["keyboard.key.\(key)"]
        XCTAssertTrue(keyButton.exists)
        keyButton.tap()
      }
      if question == 1 {
        XCTAssertTrue(element("choseong.answer.reveal").waitForExistence(timeout: 1))
      }
    }

    XCTAssertTrue(element("game.result.screen").waitForExistence(timeout: 5))
    element("game.result.screen").tap()
    XCTAssertEqual(element("game.result.accuracy.value").label, "100.0%")
    XCTAssertEqual(element("game.result.combo.value").label, "10")
    XCTAssertTrue(element("game.result.input_mode").exists)
    XCTAssertTrue(app.staticTexts["初声クイズ完了！"].exists)
    attachScreenshot(named: "choseong-result-ja")

    app.buttons["game.result.retry"].tap()
    XCTAssertTrue(element("choseong.play.screen").waitForExistence(timeout: 5))
    XCTAssertEqual(element("choseong.question.value").label, "1/10")
  }

  func testChoseongAndWordMatchShareThreeQuestionPronunciationHintBudget() {
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: true, audioProbe: true)
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))

    app.tabBars.buttons["さがす"].tap()
    let search = app.textFields["discover.search"]
    XCTAssertTrue(search.waitForExistence(timeout: 5))
    search.tap()
    search.typeText("毎日の韓国語")
    let deckCard = element("discover.deck.official_daily_words")
    XCTAssertTrue(deckCard.waitForExistence(timeout: 3))
    deckCard.tap()
    app.buttons["deck.detail.download"].tap()
    XCTAssertTrue(app.buttons["deck.detail.play"].waitForExistence(timeout: 5))

    app.tabBars.buttons["ゲーム"].tap()
    app.buttons["game.mode.choseong"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    element("game.choseong.preset.beginner").tap()
    XCTAssertTrue(element("choseong.play.screen").waitForExistence(timeout: 5))

    let choseongHint = app.buttons["choseong.pronunciation_hint"]
    XCTAssertTrue(choseongHint.waitForExistence(timeout: 3))
    XCTAssertTrue(choseongHint.label.contains("残り3問"))
    let pronunciationStarts = element("debug.pronunciation.start_count")
    XCTAssertTrue(pronunciationStarts.waitForExistence(timeout: 3))
    let initialStartCount = pronunciationStarts.label
    choseongHint.tap()
    waitForLabelContaining("残り2問", on: choseongHint, timeout: 3)
    waitForLabelDifferentFrom(initialStartCount, on: pronunciationStarts, timeout: 3)
    let firstHintStartCount = pronunciationStarts.label
    choseongHint.tap()
    waitForLabelContaining("残り2問", on: choseongHint, timeout: 3)
    waitForLabelDifferentFrom(firstHintStartCount, on: pronunciationStarts, timeout: 3)

    app.buttons["game.end"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    app.navigationBars.buttons["ゲーム"].tap()
    app.buttons["game.mode.word_match"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    element("game.word_match.preset.beginner").tap()
    XCTAssertTrue(element("word_match.play.screen").waitForExistence(timeout: 5))

    let wordMatchHint = app.buttons["word_match.pronunciation_hint"]
    let hintReady = XCTNSPredicateExpectation(
      predicate: NSPredicate(format: "isHittable == true"),
      object: wordMatchHint
    )
    XCTAssertEqual(XCTWaiter.wait(for: [hintReady], timeout: 5), .completed)
    XCTAssertTrue(wordMatchHint.label.contains("残り3問"))
    wordMatchHint.tap()
    waitForLabelContaining("残り2問", on: wordMatchHint, timeout: 3)
    XCTAssertFalse(app.buttons["dictation.pronunciation_hint"].exists)
  }

  func testDictationUsesAudioOnlyPromptAndCompletesTypedAnswer() {
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: true, deckItemLimit: 1)
    app.launchEnvironment["UITEST_GAME_COUNTDOWN_STEP_SECONDS"] = "0.5"
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))

    app.tabBars.buttons["さがす"].tap()
    let search = app.textFields["discover.search"]
    XCTAssertTrue(search.waitForExistence(timeout: 5))
    search.tap()
    search.typeText("毎日の韓国語")
    let deckCard = element("discover.deck.official_daily_words")
    XCTAssertTrue(deckCard.waitForExistence(timeout: 3))
    deckCard.tap()
    app.buttons["deck.detail.download"].tap()
    XCTAssertTrue(app.buttons["deck.detail.play"].waitForExistence(timeout: 5))

    app.tabBars.buttons["ゲーム"].tap()
    let dictationMode = app.buttons["game.mode.dictation"]
    scrollToHittable(dictationMode)
    dictationMode.tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("game.dictation.preset.beginner").exists)
    XCTAssertTrue(element("game.dictation.preset.intermediate").exists)
    XCTAssertTrue(element("game.dictation.preset.advanced").exists)
    XCTAssertTrue(element("game.dictation.add_deck").exists)
    element("game.deck.official_daily_words").tap()

    XCTAssertTrue(element("dictation.play.screen").waitForExistence(timeout: 5))
    let replay = app.buttons["dictation.replay"]
    let replayReady = XCTNSPredicateExpectation(
      predicate: NSPredicate(format: "isHittable == true"),
      object: replay
    )
    XCTAssertEqual(XCTWaiter.wait(for: [replayReady], timeout: 5), .completed)
    let compositionCard = element("dictation.composition_card")
    let typingValue = element("dictation.typing.value")
    let mascot = element("dictation.mascot")
    XCTAssertTrue(compositionCard.exists)
    XCTAssertTrue(typingValue.exists)
    XCTAssertEqual(typingValue.value as? String, "…")
    XCTAssertTrue(mascot.exists)
    XCTAssertEqual(
      app.descendants(matching: .any).matching(identifier: "dictation.mascot").count,
      1
    )
    XCTAssertTrue(element("dictation.typing.feedback").exists)
    XCTAssertEqual(
      element("dictation.typing.feedback").frame.midX,
      app.frame.midX,
      accuracy: 3
    )
    XCTAssertFalse(element("choseong.initials.value").exists)
    XCTAssertFalse(element("dictation.answer.keys").exists)
    XCTAssertTrue(app.buttons["game.end"].exists)
    XCTAssertFalse(app.buttons["dictation.session_settings"].exists)
    XCTAssertFalse(app.buttons["input_mode.built_in"].exists)
    XCTAssertFalse(app.buttons["input_mode.os_ime"].exists)
    attachScreenshot(named: "dictation-game-ja")
    replay.tap()

    app.buttons["keyboard.key.ㅎ"].tap()
    waitForValue("ㅎ", on: typingValue, timeout: 3)
    for key in Array("ㅗㅣㅅㅏ") {
      app.buttons["keyboard.key.\(key)"].tap()
    }

    XCTAssertTrue(element("game.result.screen").waitForExistence(timeout: 5))
    element("game.result.screen").tap()
    XCTAssertTrue(app.staticTexts["書き取り完了！"].exists)
    XCTAssertTrue(element("game.result.input_mode").exists)
    app.buttons["game.result.retry"].tap()
    let retryCountdown = element("dictation.countdown")
    XCTAssertTrue(retryCountdown.waitForExistence(timeout: 3))
    XCTAssertFalse(element("dictation.play.screen").exists)
    XCTAssertFalse(element("dictation.typing.value").exists)
    XCTAssertFalse(app.staticTexts["회사"].exists)
    XCTAssertTrue(element("dictation.play.screen").waitForExistence(timeout: 5))
    XCTAssertEqual(element("dictation.question.value").label, "1/1")
    XCTAssertEqual(element("dictation.score.value").label, "0")
    XCTAssertEqual(element("dictation.typing.value").value as? String, "…")
  }

  func testM4SuccessFlowCompletesFlawlessCardAndRevealsResult() {
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: true, gameDuration: 6)
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))

    app.tabBars.buttons["さがす"].tap()
    let search = app.textFields["discover.search"]
    XCTAssertTrue(search.waitForExistence(timeout: 5))
    search.tap()
    search.typeText("毎日の韓国語")
    let deckCard = element("discover.deck.official_daily_words")
    XCTAssertTrue(deckCard.waitForExistence(timeout: 3))
    deckCard.tap()
    app.buttons["deck.detail.download"].tap()
    XCTAssertTrue(app.buttons["deck.detail.play"].waitForExistence(timeout: 5))

    app.tabBars.buttons["ゲーム"].tap()
    XCTAssertTrue(app.buttons["game.mode.flow"].waitForExistence(timeout: 5))
    app.buttons["game.mode.flow"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    let gameDeck = element("game.deck.official_daily_words")
    scrollToHittable(gameDeck)
    gameDeck.tap()
    XCTAssertTrue(element("game.play.screen").waitForExistence(timeout: 5))
    XCTAssertEqual(element("game.target.value").label, "회사")

    for key in Array("ㅎㅗㅣㅅㅏ") {
      app.buttons["keyboard.key.\(key)"].tap()
    }
    waitForLabel("50", on: element("game.score.value"), timeout: 3)
    XCTAssertEqual(element("game.combo.value").label, "1")

    XCTAssertTrue(element("game.result.screen").waitForExistence(timeout: 20))
    waitForLabel("50", on: element("game.result.score.value"), timeout: 4)
    waitForLabel("結果を操作できます", on: element("result.animation.state"), timeout: 4)
    XCTAssertEqual(element("game.result.accuracy.value").label, "100.0%")
    XCTAssertEqual(element("game.result.combo.value").label, "1")
    XCTAssertTrue(element("game.result.new_record").exists)
    attachScreenshot(named: "m4-success-result-ja")

    let shareButton = app.buttons["game.result.share"]
    XCTAssertTrue(shareButton.exists)
    scrollToHittable(shareButton)
    for _ in 0..<5 where shareButton.frame.maxY > app.frame.maxY - 170 {
      app.swipeUp()
    }
    XCTAssertTrue(shareButton.isHittable)
    XCTAssertLessThanOrEqual(shareButton.frame.maxY, app.frame.maxY - 170)
    shareButton.tap()
    let shareSheet = element("ActivityListView")
    XCTAssertTrue(shareSheet.waitForExistence(timeout: 5), app.debugDescription)
    XCTAssertTrue(app.buttons["header.closeButton"].exists)
    XCTAssertTrue(app.staticTexts["コピー"].waitForExistence(timeout: 3))
    attachScreenshot(named: "game-result-share-sheet-ja")
    app.buttons["header.closeButton"].tap()
    XCTAssertTrue(element("game.result.screen").waitForExistence(timeout: 3))

    app.buttons["game.result.retry"].tap()
    XCTAssertTrue(element("game.play.screen").waitForExistence(timeout: 5))
  }

  func testAppStoreScreenshotDailyAndPracticeShowCurrentProgressWithChick() {
    let dailyChallenge = element("retention.daily_challenge")
    scrollToHittable(dailyChallenge, direction: .down)
    dailyChallenge.tap()

    let dailyTargets: [(word: String, keys: String)] = [
      ("음식", "ㅇㅡㅁㅅㅣㄱ"),
      ("음악", "ㅇㅡㅁㅇㅏㄱ"),
      ("응원", "ㅇㅡㅇㅇㅜㅓㄴ"),
      ("학교", "ㅎㅏㄱㄱㅛ"),
      ("사진", "ㅅㅏㅈㅣㄴ"),
    ]
    storeScene("practice")
    for target in dailyTargets.prefix(2) {
      waitForLabel(target.word, on: element("practice.target.value"), timeout: 3)
      typeBuiltInKeys(target.keys)
    }
    waitForLabel("응원", on: element("practice.target.value"), timeout: 3)
    typeBuiltInKeys("ㅇㅡㅇ")
    XCTAssertTrue(element("mascot.current").exists)
    waitForValue("3 / 7", on: element("practice.jamo_progress.value"), timeout: 3)
    attachScreenshot(named: "appstore-current-daily-mission-ja")

    typeBuiltInKeys("ㅇㅜㅓㄴ")
    for target in dailyTargets.suffix(2) {
      waitForLabel(target.word, on: element("practice.target.value"), timeout: 3)
      typeBuiltInKeys(target.keys)
    }
    XCTAssertTrue(element("practice.result.screen").waitForExistence(timeout: 5))
    storeScene("result", hold: 4.5)
    let done = app.buttons["result.done"]
    XCTAssertTrue(done.waitForExistence(timeout: 3))
    done.tap()

    let myPiyoCard = element("home.my_piyo_card")
    XCTAssertTrue(myPiyoCard.waitForExistence(timeout: 5))
    waitForValueContaining(storeText("4日連続", "4-day streak", "4일 연속"), on: myPiyoCard, timeout: 3)
    attachScreenshot(named: "appstore-current-attendance-home-ja")
    storeScene("home", hold: 4)

    myPiyoCard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2)).tap()
    XCTAssertTrue(app.navigationBars[storeText("MY ピヨ", "MY PIYO", "MY 피요")].waitForExistence(timeout: 5))
    XCTAssertTrue(element("my_piyo.detail.screen").exists)
    XCTAssertTrue(element("my_piyo.detail.stamps").exists)
    attachScreenshot(named: "appstore-current-rewards-ja")
    app.navigationBars[storeText("MY ピヨ", "MY PIYO", "MY 피요")].buttons.firstMatch.tap()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))

    openPracticeTab()
    let batchimStage = element("curriculum.stage.chapter_4_batchim")
    scrollToHittable(batchimStage)
    batchimStage.tap()
    waitForLabel("간", on: element("practice.target.value"), timeout: 5)
    typeBuiltInKeys("ㄱㅏㄴ")
    waitForLabel("난", on: element("practice.target.value"), timeout: 3)
    typeBuiltInKeys("ㄴㅏ")
    XCTAssertTrue(element("mascot.current").exists)
    waitForValue("2 / 3", on: element("practice.jamo_progress.value"), timeout: 3)
    attachScreenshot(named: "appstore-current-curriculum-mission-ja")
    app.buttons[storeText("練習を終了する", "End practice", "연습 끝내기")].tap()
    XCTAssertTrue(element("curriculum.map.screen").waitForExistence(timeout: 5))
  }

  func testAppStoreScreenshotGamesShowCurrentProgressWithChick() {
    app.buttons[storeText("ゲーム", "Game", "게임")].firstMatch.tap()
    XCTAssertTrue(element("game.selection.screen").waitForExistence(timeout: 5))
    storeScene("game-hub", hold: 3.5)

    app.buttons["game.mode.flow"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    element("game.flow.preset.beginner").tap()
    XCTAssertTrue(element("game.play.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("game.mascot").exists)
    let flowWords = ["ㄴㅏ", "ㄴㅓ", "ㅂㅣ", "ㄱㅣㄹ", "ㄴㅜㄴ"]
    let flowScores = ["20", "40", "60", "90", "126"]
    storeScene("flow")
    for (word, score) in zip(flowWords, flowScores) {
      typeBuiltInKeys(word)
      waitForLabel(score, on: element("game.score.value"), timeout: 3)
    }
    waitForLabel("5", on: element("game.combo.value"), timeout: 3)
    Thread.sleep(forTimeInterval: 1.25)
    attachScreenshot(named: "appstore-current-flow-ja")
    returnToGameHub()

    app.buttons["game.mode.acid_rain"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    element("game.acid_rain.preset.beginner").tap()
    XCTAssertTrue(element("acid_rain.play.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("game.mascot").exists)
    let fallingCards = app.descendants(matching: .any).matching(
      identifier: "acid_rain.falling_card"
    )
    XCTAssertTrue(fallingCards.element(boundBy: 1).waitForExistence(timeout: 5))
    storeScene("acid-rain")
    let acidRainTarget = element("acid_rain.target.value").label
    let acidRainKeys = [
      "나": "ㄴㅏ",
      "너": "ㄴㅓ",
      "비": "ㅂㅣ",
      "길": "ㄱㅣㄹ",
      "눈": "ㄴㅜㄴ",
      "문": "ㅁㅜㄴ",
    ][acidRainTarget]
    XCTAssertNotNil(acidRainKeys, "Unexpected acid-rain target \(acidRainTarget)")
    if let acidRainKeys {
      typeBuiltInKeys(acidRainKeys)
    }
    waitForLabelDifferentFrom("0", on: element("game.score.value"), timeout: 3)
    XCTAssertTrue(fallingCards.element(boundBy: 1).waitForExistence(timeout: 5))
    waitForLabelDifferentFrom(acidRainTarget, on: element("acid_rain.target.value"), timeout: 3)
    let acidRainNextTarget = element("acid_rain.target.value").label
    let acidRainNextKeys = [
      "나": "ㄴㅏ",
      "너": "ㄴㅓ",
      "비": "ㅂㅣ",
      "길": "ㄱㅣㄹ",
      "눈": "ㄴㅜㄴ",
      "문": "ㅁㅜㄴ",
    ][acidRainNextTarget]
    XCTAssertNotNil(acidRainNextKeys, "Unexpected next acid-rain target \(acidRainNextTarget)")
    if let firstKey = acidRainNextKeys?.first {
      typeBuiltInKeys(String(firstKey))
    }
    attachScreenshot(named: "appstore-current-acid-rain-ja")
    returnToGameHub()

    app.buttons["game.mode.word_match"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    element("game.word_match.preset.beginner").tap()
    XCTAssertTrue(element("word_match.play.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("word_match.mascot").exists)
    let wordMatchAnswerKeys = element("word_match.answer.keys")
    XCTAssertTrue(wordMatchAnswerKeys.waitForExistence(timeout: 3))
    typeBuiltInKeys(wordMatchAnswerKeys.label)
    waitForLabel("2/10", on: element("word_match.question.value"), timeout: 3)
    let wordMatchNextKeys = element("word_match.answer.keys").label
    XCTAssertFalse(wordMatchNextKeys.isEmpty)
    typeBuiltInKeys(String(wordMatchNextKeys.prefix(1)))
    attachScreenshot(named: "appstore-current-word-match-ja")
    returnToGameHub()

    app.buttons["game.mode.choseong"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    element("game.choseong.preset.beginner").tap()
    XCTAssertTrue(element("choseong.play.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("choseong.mascot").exists)
    let choseongAnswerKeys = element("choseong.answer.keys")
    XCTAssertTrue(choseongAnswerKeys.waitForExistence(timeout: 3))
    typeBuiltInKeys(choseongAnswerKeys.label)
    waitForLabel("2/10", on: element("choseong.question.value"), timeout: 3)
    let choseongNextKeys = element("choseong.answer.keys").label
    XCTAssertFalse(choseongNextKeys.isEmpty)
    typeBuiltInKeys(String(choseongNextKeys.prefix(1)))
    attachScreenshot(named: "appstore-current-choseong-ja")
  }

  func testAppStoreScreenshotGlobalJA() { captureGlobalStoreAssets() }
  func testAppStoreScreenshotGlobalEN() { captureGlobalStoreAssets() }
  func testAppStoreScreenshotGlobalKO() { captureGlobalStoreAssets() }
  func testAppStoreScreenshotGlobalES() { captureGlobalStoreAssets() }
  func testAppStoreScreenshotGlobalDE() { captureGlobalStoreAssets() }
  func testAppStoreScreenshotGlobalFR() { captureGlobalStoreAssets() }

  private func captureGlobalStoreAssets() {
    let isIPadCapture = max(app.frame.width, app.frame.height) >= 1_000
    if isIPadCapture {
      // Launch in the intended orientation; rotating during the seeded home launch
      // can leave the simulator's interface in portrait despite its device orientation.
      app.terminate()
      XCUIDevice.shared.orientation = .landscapeLeft
      app = makeApplication(resetKeyboardPreferences: true, gameDuration: 60,
                            flowStartIndex: 0, seedsAppStoreCaptureState: true)
      app.launch()
      XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
      let landscape = XCTNSPredicateExpectation(
        predicate: NSPredicate { _, _ in self.app.frame.width > self.app.frame.height }, object: nil
      )
      XCTAssertEqual(XCTWaiter.wait(for: [landscape], timeout: 5), .completed)
    }
    testAppStoreScreenshotDailyAndPracticeShowCurrentProgressWithChick()
    testAppStoreScreenshotGamesShowCurrentProgressWithChick()
    returnToGameHub()

    // The 1.1 editor is a paid feature; the marketing overlay discloses Pro.
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: false)
    app.launchEnvironment["UITEST_DECK_MAKER_ACCESS"] = "1"
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    app.buttons[storeText("マイページ", "Profile", "마이페이지")].firstMatch.tap()
    let createDeck = app.buttons["my_decks.create"]
    scrollToHittable(createDeck)
    createDeck.tap()
    XCTAssertTrue(element("deck_editor.screen").waitForExistence(timeout: 5))
    attachScreenshot(named: "appstore-current-editor-ja")

    app.terminate()
    app = makeApplication(resetKeyboardPreferences: false)
    app.launchArguments += ["-keyboard.builtin_layout_default", "korean_10key"]
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    startPractice()
    attachScreenshot(named: "appstore-current-tenkey-ja")

    if isIPadCapture { XCUIDevice.shared.orientation = .portrait }
  }

  func testAppPreviewJapaneseFlowShowsMascotAndCompletesFirstCard() {
    Thread.sleep(forTimeInterval: 1.5)

    app.tabBars.buttons["ゲーム"].tap()
    XCTAssertTrue(element("game.selection.screen").waitForExistence(timeout: 5))
    Thread.sleep(forTimeInterval: 1.5)

    app.buttons["game.mode.flow"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    Thread.sleep(forTimeInterval: 1.5)

    element("game.flow.preset.beginner").tap()
    XCTAssertTrue(element("game.play.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("game.mascot").waitForExistence(timeout: 3))
    XCTAssertEqual(element("game.target.value").label, "나")

    for key in Array("ㄴㅏ") {
      app.buttons["keyboard.key.\(key)"].tap()
    }

    waitForLabel("20", on: element("game.score.value"), timeout: 3)
    XCTAssertEqual(element("game.combo.value").label, "1")
    Thread.sleep(forTimeInterval: 4)
  }

  func testAppPreviewPracticeTypingReel() {
    startPractice()
    XCTAssertEqual(element("practice.target.value").label, "사랑해요")
    XCTAssertTrue(element("practice.composition_card").exists)
    XCTAssertTrue(element("mascot.current").exists)
    Thread.sleep(forTimeInterval: 0.8)

    let syllables: [[Character]] = [
      Array("ㅅㅏ"),
      Array("ㄹㅏㅇ"),
      Array("ㅎㅐ"),
      Array("ㅇㅛ"),
    ]
    for syllable in syllables {
      for key in syllable {
        app.buttons["keyboard.key.\(key)"].tap()
        Thread.sleep(forTimeInterval: 0.13)
      }
      Thread.sleep(forTimeInterval: 0.22)
    }

    Thread.sleep(forTimeInterval: 1.0)
  }

  func testAppPreviewGameHubReel() {
    app.tabBars.buttons["ゲーム"].tap()
    XCTAssertTrue(element("game.selection.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("game.mode.grid").exists)
    Thread.sleep(forTimeInterval: 2.2)
  }

  func testAppPreviewFlowComboReel() {
    app.tabBars.buttons["ゲーム"].tap()
    app.buttons["game.mode.flow"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    element("game.flow.preset.beginner").tap()
    XCTAssertTrue(element("game.play.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("game.mascot").exists)
    Thread.sleep(forTimeInterval: 0.55)

    let words = ["ㄴㅏ", "ㄴㅓ", "ㅂㅣ", "ㄱㅣㄹ", "ㄴㅜㄴ"]
    let expectedScores = ["20", "40", "60", "90", "126"]
    for (index, word) in words.enumerated() {
      for key in Array(word) {
        app.buttons["keyboard.key.\(key)"].tap()
        Thread.sleep(forTimeInterval: 0.11)
      }
      waitForLabel(expectedScores[index], on: element("game.score.value"), timeout: 2)
      Thread.sleep(forTimeInterval: 0.24)
    }

    XCTAssertEqual(element("game.combo.value").label, "5")
    Thread.sleep(forTimeInterval: 1.2)
  }

  func testAppPreviewAcidRainReel() {
    app.tabBars.buttons["ゲーム"].tap()
    app.buttons["game.mode.acid_rain"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    element("game.acid_rain.preset.beginner").tap()
    XCTAssertTrue(element("acid_rain.play.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("game.mascot").exists)

    let fallingCards = app.descendants(matching: .any).matching(
      identifier: "acid_rain.falling_card"
    )
    XCTAssertTrue(fallingCards.element(boundBy: 1).waitForExistence(timeout: 5))
    Thread.sleep(forTimeInterval: 0.7)
    for key in Array("ㄴㅏ") {
      app.buttons["keyboard.key.\(key)"].tap()
      Thread.sleep(forTimeInterval: 0.16)
    }
    waitForLabel("20", on: element("game.score.value"), timeout: 2)
    Thread.sleep(forTimeInterval: 1.2)
  }

  func testAppPreviewResultReel() {
    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: false,
      gameDuration: 8,
      flowStartIndex: 0,
      resultAnimationScale: 0.3,
      flawlessBonus: 0
    )
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))

    app.tabBars.buttons["ゲーム"].tap()
    app.buttons["game.mode.flow"].tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    element("game.flow.preset.beginner").tap()
    XCTAssertTrue(element("game.play.screen").waitForExistence(timeout: 5))

    let words = ["ㄴㅏ", "ㄴㅓ", "ㅂㅣ", "ㄱㅣㄹ", "ㄴㅜㄴ"]
    for word in words {
      for key in Array(word) {
        app.buttons["keyboard.key.\(key)"].tap()
        Thread.sleep(forTimeInterval: 0.08)
      }
      Thread.sleep(forTimeInterval: 0.12)
    }

    XCTAssertTrue(element("game.result.screen").waitForExistence(timeout: 8))
    element("game.result.screen").tap()
    waitForLabel("結果を操作できます", on: element("result.animation.state"), timeout: 4)
    XCTAssertEqual(element("game.result.accuracy.value").label, "100.0%")
    XCTAssertEqual(element("game.result.combo.value").label, "5")
    Thread.sleep(forTimeInterval: 2.2)
  }

  func testOnboardingSelectsLevelBeforeFirstInputAndRoutesToForcedHatchMissions() {
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: true, showsOnboarding: true)
    app.launch()

    XCTAssertTrue(element("onboarding.goal.screen").waitForExistence(timeout: 5))
    app.buttons["onboarding.goal.keyboard"].tap()  // Tap 1
    app.buttons["onboarding.next"].tap()  // Tap 2
    XCTAssertTrue(element("onboarding.level.screen").waitForExistence(timeout: 3))
    XCTAssertFalse(app.buttons["onboarding.next"].isEnabled)
    app.buttons["onboarding.level.beginner"].tap()  // Tap 3
    attachScreenshot(named: "onboarding-level-ja")
    app.buttons["onboarding.next"].tap()  // Tap 4

    XCTAssertTrue(element("onboarding.keyboard.screen").waitForExistence(timeout: 3))
    app.buttons["onboarding.next"].tap()  // Tap 5

    XCTAssertTrue(element("onboarding.lesson.target.value").waitForExistence(timeout: 3))
    XCTAssertEqual(element("onboarding.lesson.target.value").label, "가")
    app.buttons["keyboard.key.ㄱ"].tap()  // Tap 6: first actual input
    XCTAssertEqual(element("onboarding.lesson.entered.value").value as? String, "ㄱ")
    app.buttons["keyboard.key.ㅏ"].tap()

    XCTAssertTrue(element("onboarding.hatch.handoff.screen").waitForExistence(timeout: 3))
    XCTAssertFalse(app.buttons["onboarding.reminder.allow"].exists)
    XCTAssertTrue(app.buttons["ふかミッションへ"].exists)

    let finish = app.buttons["onboarding.finish"]
    scrollToHittable(finish)
    finish.tap()
    XCTAssertTrue(element("onboarding.hatch.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(
      app.staticTexts["3つのミッションでピヨをかえそう"].waitForExistence(timeout: 3)
    )
    XCTAssertFalse(app.tabBars.buttons["ホーム"].exists)
    XCTAssertFalse(app.tabBars.buttons["さがす"].exists)
    XCTAssertFalse(app.tabBars.buttons["ゲーム"].exists)

    let firstMission = app.buttons["ミッション1をはじめる"]
    scrollToHittable(firstMission)
    firstMission.tap()
    XCTAssertTrue(element("practice.target.value").waitForExistence(timeout: 5))
    XCTAssertEqual(element("practice.target.value").label, "ㄱ")

    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: false,
      respectsOnboardingState: true
    )
    app.launch()
    XCTAssertTrue(element("onboarding.hatch.screen").waitForExistence(timeout: 5))
    XCTAssertFalse(element("onboarding.goal.screen").exists)
  }

  func testLevelScreenSupportsAllLanguagesAndRestoresSelection() {
    for (language, locale) in [("ja", "ja_JP"), ("en", "en_US"), ("es", "es_ES"), ("de", "de_DE"), ("fr", "fr_FR")] {
      app.terminate()
      app = makeApplication(resetKeyboardPreferences: true, showsOnboarding: true)
      app.launchArguments.replaceSubrange(0..<4, with: [
        "-AppleLanguages", "(\(language))", "-AppleLocale", locale,
      ])
      app.launch()
      XCTAssertTrue(element("onboarding.goal.screen").waitForExistence(timeout: 5))
      app.buttons["onboarding.goal.travel"].tap()
      scrollAndTap(app.buttons["onboarding.next"])
      XCTAssertTrue(element("onboarding.level.screen").waitForExistence(timeout: 3))
      XCTAssertFalse(app.buttons["onboarding.back"].exists)
      XCTAssertTrue(app.buttons["onboarding.level.words"].label.contains("학교"))
      XCTAssertTrue(app.buttons["onboarding.level.sentences"].label.contains("같이 걷고 싶어요"))
      scrollAndTap(app.buttons["onboarding.level.sentences"])
      XCTAssertTrue(app.buttons["onboarding.next"].isEnabled)
      attachScreenshot(named: "onboarding-level-\(language)")

      app.terminate()
      app = makeApplication(resetKeyboardPreferences: false, respectsOnboardingState: true)
      app.launchArguments.replaceSubrange(0..<4, with: [
        "-AppleLanguages", "(\(language))", "-AppleLocale", locale,
      ])
      app.launch()
      XCTAssertTrue(element("onboarding.level.screen").waitForExistence(timeout: 5))
      XCTAssertTrue(app.buttons["onboarding.level.sentences"].isSelected)
      scrollAndTap(app.buttons["onboarding.next"])
      XCTAssertTrue(element("onboarding.keyboard.screen").waitForExistence(timeout: 3))
    }
  }

  func testOnboardingDeviceKeyboardCompletesFirstInputAndCarriesIntoHatchMission() {
    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: true,
      curriculumItemLimit: 1,
      koreanKeyboardAvailable: true,
      showsOnboarding: true
    )
    app.launch()

    XCTAssertTrue(element("onboarding.goal.screen").waitForExistence(timeout: 5))
    app.buttons["onboarding.goal.keyboard"].tap()  // Interaction 1
    app.buttons["onboarding.next"].tap()  // Interaction 2
    XCTAssertTrue(element("onboarding.level.screen").waitForExistence(timeout: 3))
    app.buttons["onboarding.level.jamo"].tap()
    app.buttons["onboarding.next"].tap()
    XCTAssertTrue(element("onboarding.keyboard.screen").waitForExistence(timeout: 3))
    app.buttons["onboarding.input_device.hardware"].tap()  // Interaction 5

    XCTAssertTrue(element("onboarding.lesson.target.value").waitForExistence(timeout: 3))
    XCTAssertTrue(element("physical_keyboard.key.R").waitForExistence(timeout: 3))
    let firstInput = app.textFields["os_ime.text_field"]
    XCTAssertTrue(firstInput.waitForExistence(timeout: 3))
    firstInput.typeText("가")  // Interaction 6: first real input

    XCTAssertTrue(element("onboarding.hatch.handoff.screen").waitForExistence(timeout: 3))
    let finish = app.buttons["onboarding.finish"]
    scrollToHittable(finish)
    finish.tap()
    XCTAssertTrue(element("onboarding.hatch.screen").waitForExistence(timeout: 5))

    let firstMission = app.buttons["ミッション1をはじめる"]
    scrollToHittable(firstMission)
    firstMission.tap()
    XCTAssertTrue(element("practice.target.value").waitForExistence(timeout: 5))
    XCTAssertTrue(app.textFields["os_ime.text_field"].waitForExistence(timeout: 3))
    XCTAssertTrue(element("physical_keyboard.key.R").exists)
    XCTAssertFalse(element("keyboard.view").exists)
  }

  func testFirstHatchResultContinuesToSecondMissionWithoutRetry() {
    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: true,
      curriculumItemLimit: 1,
      showsHatchOnboarding: true
    )
    app.launch()

    XCTAssertTrue(element("onboarding.hatch.screen").waitForExistence(timeout: 5))
    let firstMission = app.buttons["ミッション1をはじめる"]
    scrollToHittable(firstMission)
    firstMission.tap()

    waitForLabel("ㄱ", on: element("practice.target.value"), timeout: 3)
    app.buttons["keyboard.key.ㄱ"].tap()

    XCTAssertTrue(element("practice.result.screen").waitForExistence(timeout: 5))
    XCTAssertFalse(app.buttons["practice.result.retry"].exists)
    XCTAssertFalse(app.buttons["practice.result.share.save_image"].exists)
    XCTAssertFalse(app.buttons["practice.result.share"].exists)
    XCTAssertFalse(app.buttons["practice.result.review"].exists)
    XCTAssertFalse(app.buttons["result.done"].exists)
    // The result cover can retain the practice screen in the accessibility tree.
    // Neither mascot may advance before the growth celebration is confirmed.
    let mascots = app.descendants(matching: .any)
      .matching(identifier: "mascot.current").allElementsBoundByIndex
    XCTAssertFalse(mascots.isEmpty)
    for mascot in mascots {
      XCTAssertEqual(mascot.value as? String, "ひびが入ったたまご")
    }
    let nextMission = app.buttons["onboarding.hatch.result.continue"]
    XCTAssertTrue(nextMission.exists)

    element("practice.result.screen").tap()
    XCTAssertTrue(nextMission.isEnabled)
    nextMission.tap()

    XCTAssertTrue(element("mascot.growth.celebration").waitForExistence(timeout: 5))
    let name = app.textFields["mascot.growth.name"]
    XCTAssertTrue(name.waitForExistence(timeout: 4))
    name.tap()
    name.typeText("Momo")
    app.buttons["mascot.growth.confirm"].tap()

    waitForLabel("ㅏ", on: element("practice.target.value"), timeout: 5)
    XCTAssertFalse(element("onboarding.hatch.screen").exists)
  }

  func testFutureCurriculumSchemaKeepsHatchResultRecoverable() {
    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: true,
      curriculumItemLimit: 1,
      showsHatchOnboarding: true,
      seedsFutureCurriculumSchema: true
    )
    app.launch()

    XCTAssertTrue(element("onboarding.hatch.screen").waitForExistence(timeout: 5))
    let firstMission = app.buttons["onboarding.hatch.continue"]
    scrollToHittable(firstMission)
    firstMission.tap()

    waitForLabel("ㄱ", on: element("practice.target.value"), timeout: 3)
    app.buttons["keyboard.key.ㄱ"].tap()

    let result = element("practice.result.screen")
    XCTAssertTrue(result.waitForExistence(timeout: 5))
    result.tap()
    XCTAssertTrue(
      element("onboarding.hatch.persistence.failure").waitForExistence(timeout: 5)
    )
    XCTAssertFalse(app.buttons["onboarding.hatch.result.continue"].isEnabled)

    app.buttons["onboarding.hatch.persistence.retry"].tap()
    XCTAssertTrue(
      element("onboarding.hatch.persistence.failure").waitForExistence(timeout: 5)
    )
    app.buttons["onboarding.hatch.persistence.back"].tap()

    XCTAssertTrue(element("onboarding.hatch.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("persistence.failure.banner").exists)
    XCTAssertTrue(app.buttons["onboarding.hatch.continue"].exists)
    XCTAssertFalse(app.buttons["onboarding.hatch.open_app"].exists)
  }

  func testThreeHatchMissionsLandOnHomeAndIntroduceMainTabs() {
    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: true,
      curriculumItemLimit: 1,
      showsHatchOnboarding: true
    )
    app.launch()

    XCTAssertTrue(element("onboarding.hatch.screen").waitForExistence(timeout: 5))
    let firstMission = app.buttons["onboarding.hatch.continue"]
    scrollToHittable(firstMission)
    firstMission.tap()

    waitForLabel("ㄱ", on: element("practice.target.value"), timeout: 5)
    app.buttons["keyboard.key.ㄱ"].tap()
    finishHatchMissionResult()

    XCTAssertTrue(element("mascot.growth.celebration").waitForExistence(timeout: 5))
    XCTAssertTrue(app.textFields["mascot.growth.name"].waitForExistence(timeout: 4))
    XCTAssertTrue(app.buttons["mascot.growth.confirm"].waitForExistence(timeout: 4))
    app.buttons["mascot.growth.confirm"].tap()

    waitForLabel("ㅏ", on: element("practice.target.value"), timeout: 5)
    app.buttons["keyboard.key.ㅏ"].tap()
    finishHatchMissionResult()

    waitForLabel("가", on: element("practice.target.value"), timeout: 5)
    app.buttons["keyboard.key.ㄱ"].tap()
    app.buttons["keyboard.key.ㅏ"].tap()
    finishHatchMissionResult()

    XCTAssertTrue(element("mascot.growth.celebration").waitForExistence(timeout: 5))
    XCTAssertFalse(app.textFields["mascot.growth.name"].exists)
    XCTAssertTrue(app.buttons["mascot.growth.confirm"].waitForExistence(timeout: 4))
    app.buttons["mascot.growth.confirm"].tap()

    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    assertAppTourStep("homePrimary")
    attachScreenshot(named: "app-tour-home-primary-ja")

    app.buttons["app_tour.next"].tap()
    assertAppTourStep("discoverSearch")
    XCTAssertTrue(element("discover.search").exists)
    attachScreenshot(named: "app-tour-discover-search-ja")

    app.buttons["app_tour.next"].tap()
    assertAppTourStep("practiceCurriculum")
    XCTAssertTrue(element("curriculum.map.screen").exists)
    attachScreenshot(named: "app-tour-practice-curriculum-ja")

    app.buttons["app_tour.next"].tap()
    assertAppTourStep("gameModes")
    XCTAssertTrue(element("game.selection.screen").exists)
    attachScreenshot(named: "app-tour-game-modes-ja")

    app.buttons["app_tour.next"].tap()
    assertAppTourStep("myPageProfile")
    XCTAssertTrue(element("my_page.screen").exists)
    attachScreenshot(named: "app-tour-my-page-profile-ja")

    app.buttons["app_tour.next"].tap()
    assertAppTourStep("settings")
    XCTAssertTrue(app.buttons["root.settings"].exists)
    attachScreenshot(named: "app-tour-settings-ja")

    app.buttons["app_tour.next"].tap()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    XCTAssertFalse(element("app_tour.step.settings").exists)
    XCTAssertTrue(element("home.primary.recommend_deck").exists)
    XCTAssertFalse(element("home.primary.resume_curriculum").exists)
    XCTAssertFalse(element("home.primary.resume_deck").exists)
    attachScreenshot(named: "first-home-recommendation-after-hatch-ja")

    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: false,
      showsHatchOnboarding: true
    )
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    XCTAssertFalse(element("app_tour.step.homePrimary").waitForExistence(timeout: 1))
    XCTAssertTrue(element("home.primary.recommend_deck").exists)
    XCTAssertFalse(element("home.primary.resume_curriculum").exists)
  }

  func testAppTourLayoutAcrossRepresentativeScreenSize() {
    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: true,
      forcesAppTour: true
    )
    app.launch()

    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    assertAppTourStep("homePrimary")
    attachScreenshot(named: "app-tour-size-home-ja")

    let steps: [(target: String, screen: String)] = [
      ("discoverSearch", "discover.search"),
      ("practiceCurriculum", "curriculum.map.screen"),
      ("gameModes", "game.selection.screen"),
      ("myPageProfile", "my_page.screen"),
      ("settings", "root.settings"),
    ]
    for step in steps {
      app.buttons["app_tour.next"].tap()
      assertAppTourStep(step.target)
      XCTAssertTrue(element(step.screen).exists)
      attachScreenshot(named: "app-tour-size-\(step.target)-ja")
    }

    app.buttons["app_tour.next"].tap()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))

    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: false,
      forcesAppTour: true
    )
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    XCTAssertFalse(element("app_tour.step.homePrimary").waitForExistence(timeout: 1))
  }

  func testIPadMainScreensPortraitAndLandscape() throws {
    guard max(app.frame.width, app.frame.height) >= 1_000 else {
      throw XCTSkip("iPad layout review")
    }
    for orientation in [UIDeviceOrientation.portrait, .landscapeLeft] {
      XCUIDevice.shared.orientation = orientation
      let suffix = orientation == .portrait ? "portrait" : "landscape"
      for (tab, identifier) in [("ホーム", "home.screen"), ("さがす", "discover.catalog"),
                                ("練習", "curriculum.map.screen"), ("ゲーム", "game.selection.screen"),
                                ("マイページ", "my_page.screen")] {
        let button = app.buttons[tab].firstMatch
        XCTAssertTrue(button.waitForExistence(timeout: 3))
        // The native iPad tab strip pages at smaller widths; AX still exposes
        // the clipped tab, so reveal it before tapping its center.
        let nextPage = app.buttons["次のページ"].firstMatch
        let previousPage = app.buttons["前のページ"].firstMatch
        if previousPage.exists, button.frame.minX < previousPage.frame.maxX {
          previousPage.tap()
        }
        if nextPage.exists, button.frame.maxX > nextPage.frame.minX {
          nextPage.tap()
        }
        button.tap()
        XCTAssertTrue(element(identifier).waitForExistence(timeout: 5))
        if identifier == "home.screen" {
          let nextStepCards = [
            element("home.next_step.official_consonants"),
            element("home.next_step.official_vowels"),
            element("home.next_step.official_syllable_building"),
          ]
          XCTAssertTrue(element("home.recommendations.personal").waitForExistence(timeout: 5))
          XCTAssertTrue(element("home.recommendations.next_step").exists)
          nextStepCards.forEach { XCTAssertTrue($0.exists) }
          scrollToHittable(nextStepCards[0])
          XCTAssertEqual(nextStepCards[0].frame.midY, nextStepCards[1].frame.midY, accuracy: 2)
          XCTAssertEqual(nextStepCards[1].frame.midY, nextStepCards[2].frame.midY, accuracy: 2)
          XCTAssertGreaterThanOrEqual(nextStepCards[0].frame.minX, app.frame.minX)
          XCTAssertLessThanOrEqual(nextStepCards[2].frame.maxX, app.frame.maxX)
        }
        attachScreenshot(named: "ipad-\(identifier)-\(suffix)-ja")
      }
      openSettings()
      XCTAssertTrue(app.buttons["settings.done"].isHittable)
      attachScreenshot(named: "ipad-settings-\(suffix)-ja")
      app.buttons["settings.done"].tap()
    }
  }

  func testIPadLandscapeGameControlsRemainVisible() throws {
    guard max(app.frame.width, app.frame.height) >= 1_000 else {
      throw XCTSkip("iPad landscape game layout review")
    }
    for (mode, screen, prompt) in [
      ("flow", "game.play.screen", ""),
      ("acid_rain", "acid_rain.play.screen", ""),
      ("word_match", "word_match.play.screen", "word_match.prompt.value"),
      ("choseong", "choseong.play.screen", "choseong.initials.value"),
      ("dictation", "dictation.play.screen", "dictation.replay"),
    ] {
      app.terminate()
      XCUIDevice.shared.orientation = .landscapeLeft
      app = makeApplication(resetKeyboardPreferences: true, gameDuration: 60, flowStartIndex: 0)
      app.launch()
      assertLandscapeOrientation()
      app.buttons["ゲーム"].firstMatch.tap()
      scrollAndTap(app.buttons["game.mode.\(mode)"])
      XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
      element("game.\(mode).preset.beginner").tap()
      XCTAssertTrue(element(screen).waitForExistence(timeout: 5))
      assertBuiltInKeyboardFillsIPadWidth()
      XCTAssertLessThanOrEqual(app.buttons["keyboard.backspace"].frame.maxY, app.frame.maxY)
      if !prompt.isEmpty {
        XCTAssertTrue(element(prompt).exists)
        XCTAssertGreaterThanOrEqual(element(prompt).frame.minY, app.frame.minY)
        XCTAssertLessThan(element(prompt).frame.maxY, app.buttons["keyboard.key.ㅂ"].frame.minY)
        let typing = element("\(mode).typing.value")
        XCTAssertGreaterThan(typing.frame.minY, element(prompt).frame.maxY)
        XCTAssertEqual(typing.frame.midX, app.frame.midX, accuracy: 80)
        let feedback = element("\(mode).typing.feedback")
        XCTAssertTrue(feedback.exists)
        XCTAssertLessThanOrEqual(feedback.frame.maxY, app.buttons["keyboard.key.ㅂ"].frame.minY - 14)
      }
      attachScreenshot(named: "ipad-game-\(mode)-landscape-ja")
    }
    returnToGameHub()
    scrollAndTap(app.buttons["game.mode.spacing"])
    XCTAssertTrue(element("spacing.selection.screen").waitForExistence(timeout: 5))
    element("spacing.passage.morning_commute").tap()
    XCTAssertTrue(element("spacing.play.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(app.buttons["spacing.control.space"].isHittable)
    XCTAssertTrue(app.buttons["spacing.control.next"].isHittable)
    XCTAssertLessThanOrEqual(app.buttons["spacing.control.space"].frame.maxY, app.frame.maxY)
    attachScreenshot(named: "ipad-game-spacing-landscape-ja")
  }

  func testIPadAdaptiveWidthRecalculatesAcrossRotationAndPreservesSelectedTab() throws {
    guard max(app.frame.width, app.frame.height) >= 1_000 else {
      throw XCTSkip("This adaptive rotation gate runs on iPad-sized destinations")
    }

    XCUIDevice.shared.orientation = .portrait
    let homeScreen = element("home.screen")
    waitForValue(adaptiveWidthClass(for: app.frame.width), on: homeScreen, timeout: 5)
    attachScreenshot(named: "ipad-adaptive-home-portrait-ja")

    XCUIDevice.shared.orientation = .landscapeLeft
    waitForValue(adaptiveWidthClass(for: app.frame.width), on: homeScreen, timeout: 5)
    attachScreenshot(named: "ipad-adaptive-home-landscape-ja")

    XCUIDevice.shared.orientation = .portrait
    waitForValue(adaptiveWidthClass(for: app.frame.width), on: homeScreen, timeout: 5)
  }

  func testIPadPracticeRotationPreservesProblemInputAndCentersKeyboard() throws {
    guard max(app.frame.width, app.frame.height) >= 1_000 else {
      throw XCTSkip("This adaptive session gate runs on iPad-sized destinations")
    }

    XCUIDevice.shared.orientation = .portrait
    startPractice()
    let target = element("practice.target.value")
    let progress = element("practice.jamo_progress.value")
    let mistakes = element("practice.mistakes.value")
    XCTAssertEqual(target.label, "사랑해요")
    XCTAssertEqual(progress.value as? String, "0 / 9")
    XCTAssertEqual(mistakes.value as? String, "0")

    app.buttons["keyboard.key.ㅅ"].tap()
    waitForValue("1 / 9", on: progress, timeout: 3)

    XCUIDevice.shared.orientation = .landscapeLeft
    assertLandscapeOrientation()
    XCTAssertTrue(target.waitForExistence(timeout: 5))
    waitForValue("1 / 9", on: progress, timeout: 5)
    XCTAssertEqual(target.label, "사랑해요")
    XCTAssertEqual(mistakes.value as? String, "0")
    assertBuiltInKeyboardFillsIPadWidth()
    let landscapeComposition = element("practice.composition.card")
    XCTAssertTrue(landscapeComposition.exists)
    XCTAssertGreaterThanOrEqual(landscapeComposition.frame.minY,
                               element("practice.target.card").frame.maxY)
    XCTAssertEqual(landscapeComposition.frame.midX, app.frame.midX, accuracy: 2)
    XCTAssertLessThanOrEqual(landscapeComposition.frame.maxY,
                            app.buttons["keyboard.key.ㅂ"].frame.minY - 10)
    XCTAssertLessThanOrEqual(element("practice.entered_text.value").frame.maxY,
                            app.buttons["keyboard.key.ㅂ"].frame.minY - 14)
    XCTAssertLessThanOrEqual(element("practice.target.card").frame.maxY,
                            app.buttons["keyboard.key.ㅂ"].frame.minY)
    XCTAssertLessThan(app.buttons["keyboard.key.ㅂ"].frame.minY - landscapeComposition.frame.maxY, 40)
    attachScreenshot(named: "ipad-practice-landscape-active-ja")

    XCUIDevice.shared.orientation = .portrait
    XCTAssertTrue(target.waitForExistence(timeout: 5))
    waitForValue("1 / 9", on: progress, timeout: 5)
    XCTAssertEqual(target.label, "사랑해요")
    assertBuiltInKeyboardFillsIPadWidth()
    let composition = element("practice.composition.card")
    XCTAssertTrue(composition.exists)
    XCTAssertLessThan(app.buttons["keyboard.key.ㅂ"].frame.minY - composition.frame.maxY, 100)
    XCTAssertGreaterThan(element("practice.target.card").frame.height, 240)
    attachScreenshot(named: "ipad-practice-portrait-active-ja")
  }

  func testIPadPhysicalKeyboardGuideCompletionKeepsSessionFramesStable() throws {
    guard max(app.frame.width, app.frame.height) >= 1_000 else {
      throw XCTSkip("This physical keyboard layout gate runs on iPad-sized destinations")
    }

    let scenarios: [(orientation: UIDeviceOrientation, accessibilityType: Bool)] = [
      (.portrait, false),
      (.landscapeLeft, false),
      (.portrait, true),
    ]
    for scenario in scenarios {
      app.terminate()
      XCUIDevice.shared.orientation = scenario.orientation
      app = makeApplication(
        resetKeyboardPreferences: true,
        deckItemLimit: 2,
        koreanKeyboardAvailable: true
      )
      if scenario.accessibilityType {
        app.launchEnvironment["UITEST_DYNAMIC_TYPE_ACCESSIBILITY"] = "1"
      }
      app.launchArguments += [
        "-keyboard.input_mode_default", "os_ime",
        "-keyboard.shows_physical_keyboard_guide", "YES",
      ]
      app.launch()
      XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
      startPractice()
      if scenario.orientation == .landscapeLeft { assertLandscapeOrientation() }

      let guide = element("physical_keyboard.guide")
      let instruction = element("physical_keyboard.guide.instruction")
      let targetCard = element("practice.target.card")
      let compositionCard = element("practice.composition.card")
      XCTAssertTrue(guide.waitForExistence(timeout: 5))
      XCTAssertTrue(instruction.exists)
      XCTAssertTrue(targetCard.exists)
      XCTAssertTrue(compositionCard.exists)

      let guideFrameBeforeCompletion = guide.frame
      let targetMidYBeforeCompletion = targetCard.frame.midY
      let compositionMidYBeforeCompletion = compositionCard.frame.midY

      let imeField = app.textFields["os_ime.text_field"]
      XCTAssertTrue(imeField.waitForExistence(timeout: 3))
      imeField.tap()
      imeField.typeText("사랑해요")

      let ready = element("physical_keyboard.guide.ready")
      XCTAssertTrue(ready.waitForExistence(timeout: 0.5))
      XCTAssertEqual(guide.frame.minY, guideFrameBeforeCompletion.minY, accuracy: 1)
      XCTAssertEqual(guide.frame.height, guideFrameBeforeCompletion.height, accuracy: 1)
      XCTAssertEqual(targetCard.frame.midY, targetMidYBeforeCompletion, accuracy: 1)
      XCTAssertEqual(compositionCard.frame.midY, compositionMidYBeforeCompletion, accuracy: 1)
    }
  }

  func testIPadAccessibilityDynamicTypeKeepsSettingsAndPracticeReachable() throws {
    guard max(app.frame.width, app.frame.height) >= 1_000 else {
      throw XCTSkip("This accessibility layout gate runs on iPad-sized destinations")
    }

    app.terminate()
    app = makeApplication(resetKeyboardPreferences: false)
    app.launchEnvironment["UITEST_DYNAMIC_TYPE_ACCESSIBILITY"] = "1"
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))

    openSettings()
    waitForValue("accessibility", on: element("debug.dynamic_type"), timeout: 3)
    XCTAssertTrue(element("settings.font_preview").waitForExistence(timeout: 3))
    XCTAssertTrue(app.buttons["settings.done"].isHittable)
    attachScreenshot(named: "ipad-settings-accessibility-xxxl-ja")
    app.buttons["settings.done"].tap()

    startPractice()
    XCTAssertTrue(app.buttons["keyboard.key.ㅅ"].isHittable)
    assertBuiltInKeyboardFillsIPadWidth()
  }

  func testAppTourBackgroundTapAdvancesAndNextButtonDoesNotDoubleAdvance() {
    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: true,
      forcesAppTour: true
    )
    app.launch()

    assertAppTourStep("homePrimary")
    let overlay = element("app_tour.step.homePrimary")
    overlay.coordinate(withNormalizedOffset: CGVector(dx: 0.96, dy: 0.96)).tap()
    assertAppTourStep("discoverSearch")

    app.buttons["app_tour.next"].tap()
    assertAppTourStep("practiceCurriculum")
    XCTAssertFalse(element("app_tour.step.gameModes").exists)
  }

  func testF10SettingsApplyImmediatelyAndPersistAcrossRelaunch() {
    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: true,
      koreanKeyboardAvailable: true
    )
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))

    openSettings()
    let settingsScreen = element("settings.screen")
    XCTAssertTrue(settingsScreen.waitForExistence(timeout: 5))
    XCTAssertFalse(settingsScreen.descendants(matching: .any)["mascot.current"].exists)
    XCTAssertFalse(settingsScreen.staticTexts["自分に合わせて調整"].exists)
    let immediatelyAvailableSound = app.switches["settings.sound"]
    XCTAssertTrue(immediatelyAvailableSound.waitForExistence(timeout: 3))
    XCTAssertTrue(immediatelyAvailableSound.isHittable)
    XCTAssertTrue(element("settings.font_scale").waitForExistence(timeout: 3))
    let largeFont = app.buttons["大"]
    scrollToHittable(largeFont)
    largeFont.tap()
    XCTAssertEqual(element("settings.font_preview").value as? String, "large")
    let darkTheme = app.buttons["ダーク"]
    scrollToHittable(darkTheme)
    darkTheme.tap()
    XCTAssertTrue(darkTheme.isSelected)

    app.buttons["English"].tap()
    XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 8))
    XCTAssertFalse(app.tabBars.buttons["Settings"].exists)
    XCTAssertTrue(app.tabBars.buttons["Home"].exists)

    let choseongMeaning = app.switches["settings.choseong_meaning"]
    scrollToHittable(choseongMeaning)
    XCTAssertEqual(choseongMeaning.value as? String, "1")
    choseongMeaning.tap()
    XCTAssertEqual(choseongMeaning.value as? String, "0")
    attachScreenshot(named: "settings-direct-content-en")

    let keyGuide = app.switches["settings.key_guide"]
    scrollToHittable(keyGuide)
    keyGuide.tap()
    let romanHints = app.switches["settings.roman_hints"]
    scrollToHittable(romanHints)
    romanHints.tap()
    let haptics = app.switches["settings.haptics"]
    scrollToHittable(haptics)
    haptics.tap()
    waitForValue("0", on: haptics, timeout: 3)

    let osKeyboard = app.buttons["input_mode.os_ime"]
    scrollToHittable(osKeyboard)
    osKeyboard.tap()
    XCTAssertTrue(osKeyboard.isSelected)

    let softSound = app.buttons["Soft"]
    scrollToHittable(softSound, direction: .down)
    softSound.tap()
    let sound = app.switches["settings.sound"]
    scrollToHittable(sound)
    sound.tap()
    XCTAssertEqual(sound.value as? String, "0")

    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: false,
      koreanKeyboardAvailable: true
    )
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    openSettings()
    XCTAssertTrue(element("settings.screen").waitForExistence(timeout: 5))

    XCTAssertEqual(element("settings.font_preview").value as? String, "large")
    XCTAssertTrue(app.buttons["Dark"].isSelected)
    XCTAssertTrue(app.buttons["English"].isSelected)

    let persistedChoseongMeaning = app.switches["settings.choseong_meaning"]
    scrollToHittable(persistedChoseongMeaning)
    XCTAssertEqual(persistedChoseongMeaning.value as? String, "0")
    let persistedKeyGuide = app.switches["settings.key_guide"]
    scrollToHittable(persistedKeyGuide)
    XCTAssertEqual(persistedKeyGuide.value as? String, "0")
    let persistedRomanHints = app.switches["settings.roman_hints"]
    scrollToHittable(persistedRomanHints)
    XCTAssertEqual(persistedRomanHints.value as? String, "0")
    let persistedHaptics = app.switches["settings.haptics"]
    scrollToHittable(persistedHaptics)
    XCTAssertEqual(persistedHaptics.value as? String, "0")
    let persistedOSKeyboard = app.buttons["input_mode.os_ime"]
    scrollToHittable(persistedOSKeyboard)
    XCTAssertTrue(persistedOSKeyboard.isSelected)
    let persistedSound = app.switches["settings.sound"]
    scrollToHittable(persistedSound, direction: .down)
    XCTAssertEqual(persistedSound.value as? String, "0")
    XCTAssertTrue(app.buttons["Soft"].isSelected)

    let japanese = app.buttons["日本語"]
    scrollToHittable(japanese, direction: .down)
    XCTAssertFalse(app.buttons["한국어"].exists)
    japanese.tap()
    XCTAssertTrue(app.navigationBars["設定"].waitForExistence(timeout: 5))
    XCTAssertTrue(app.tabBars.buttons["ホーム"].exists)
  }

  func testSpanishDeviceLanguageSwitchAndPersistence() {
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: true)
    app.launchArguments.replaceSubrange(0..<4, with: [
      "-AppleLanguages", "(es-MX)", "-AppleLocale", "es_MX",
    ])
    app.launch()
    XCTAssertTrue(app.buttons["Inicio"].firstMatch.waitForExistence(timeout: 8))
    attachScreenshot(named: "spanish-home")
    openSettings()
    XCTAssertTrue(app.navigationBars["Ajustes"].waitForExistence(timeout: 5))
    let spanish = app.buttons["Español"]
    scrollSettingsLanguageIntoView(spanish)
    XCTAssertTrue(spanish.isSelected)
    attachScreenshot(named: "spanish-settings")
    scrollSettingsLanguageIntoView(app.buttons["English"])
    app.buttons["English"].tap()
    XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
    scrollSettingsLanguageIntoView(app.buttons["Español"])
    app.buttons["Español"].tap()
    XCTAssertTrue(app.navigationBars["Ajustes"].waitForExistence(timeout: 5))
    app.buttons["settings.done"].tap()
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: false)
    app.launch()
    XCTAssertTrue(app.buttons["Inicio"].firstMatch.waitForExistence(timeout: 8))
    app.buttons["Práctica"].firstMatch.tap()
    XCTAssertTrue(element("curriculum.map.screen").waitForExistence(timeout: 5))
    openSettings()
    scrollSettingsLanguageIntoView(app.buttons["Español"])
    XCTAssertTrue(app.buttons["Español"].isSelected)
    XCTAssertFalse(app.buttons["한국어"].exists)
  }

  func testGermanAndFrenchLanguageSelectionSurvivesRelaunch() {
    for (region, code, title, home, name) in [
      ("de-AT", "de", "Einstellungen", "Start", "Deutsch"),
      ("fr-CA", "fr", "Réglages", "Accueil", "Français"),
    ] {
      app.terminate()
      app = makeApplication(resetKeyboardPreferences: true)
      app.launchArguments.replaceSubrange(0..<4, with: [
        "-AppleLanguages", "(\(region))", "-AppleLocale", region,
      ])
      app.launch()
      XCTAssertTrue(app.buttons[home].firstMatch.waitForExistence(timeout: 8))
      openSettings()
      XCTAssertTrue(app.navigationBars[title].waitForExistence(timeout: 5))
      scrollSettingsLanguageIntoView(app.buttons["English"])
      app.buttons["English"].tap()
      XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
      scrollSettingsLanguageIntoView(app.buttons[name])
      app.buttons[name].tap()
      XCTAssertTrue(app.navigationBars[title].waitForExistence(timeout: 5))
      attachScreenshot(named: "\(code)-settings")
      app.buttons["settings.done"].tap()
      app.terminate()
      app = makeApplication(resetKeyboardPreferences: false)
      app.launch()
      XCTAssertTrue(app.buttons[home].firstMatch.waitForExistence(timeout: 8))
      openSettings()
      scrollSettingsLanguageIntoView(app.buttons[name])
      XCTAssertTrue(app.buttons[name].isSelected)
      app.buttons["settings.done"].tap()
    }
  }

  func testRetiredKoreanLanguageShowsEnglishUIAndKeepsKoreanPractice() {
    app.terminate()
    app = makeApplication(resetKeyboardPreferences: true)
    app.launchArguments.replaceSubrange(0..<4, with: [
      "-AppleLanguages", "(ko)", "-AppleLocale", "ko_KR",
    ])
    app.launchArguments += ["-settings.language", "ko"]
    app.launch()
    XCTAssertTrue(app.tabBars.buttons["Home"].waitForExistence(timeout: 5))
    openSettings()
    XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
    let english = app.buttons["English"]
    scrollToHittable(english)
    XCTAssertTrue(english.exists)
    XCTAssertTrue(app.buttons["日本語"].exists)
    XCTAssertFalse(app.buttons["한국어"].exists)
    app.buttons["settings.done"].tap()
    app.tabBars.buttons["Practice"].tap()
    XCTAssertTrue(element("curriculum.map.screen").waitForExistence(timeout: 5))
    XCTAssertFalse(app.tabBars.buttons["연습"].exists)
  }

  func testPrivacyParticipateEnablesBothChoicesAndNoticeIsShownOnce() {
    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: true,
      showsPrivacyConsent: true,
      onboardingNotificationResult: "scheduled"
    )
    app.launch()

    XCTAssertTrue(element("privacy_consent.screen").waitForExistence(timeout: 5))
    XCTAssertFalse(app.switches["privacy_consent.analytics"].exists)
    XCTAssertFalse(app.switches["privacy_consent.diagnostics"].exists)
    XCTAssertTrue(element("privacy_consent.usage_information").exists)
    XCTAssertTrue(element("privacy_consent.error_information").exists)
    XCTAssertTrue(element("privacy_consent.excluded_data").exists)
    XCTAssertTrue(element("privacy_consent.privacy_policy").isHittable)
    XCTAssertFalse(app.staticTexts["PostHog Cloud EU"].exists)
    XCTAssertFalse(app.staticTexts["Firebase Crashlytics"].exists)
    XCTAssertTrue(app.buttons["privacy_consent.continue_without_sharing"].isHittable)
    app.buttons["privacy_consent.participate_and_continue"].tap()

    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    XCTAssertFalse(element("privacy_consent.screen").exists)

    openSettings()
    let analytics = app.switches["settings.anonymous_analytics"]
    scrollToHittable(analytics)
    XCTAssertEqual(analytics.value as? String, "1")
    let diagnostics = app.switches["settings.crash_diagnostics"]
    scrollToHittable(diagnostics)
    XCTAssertEqual(diagnostics.value as? String, "1")
    app.buttons["settings.done"].tap()

    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: false,
      showsPrivacyConsent: true,
      onboardingNotificationResult: "scheduled"
    )
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    XCTAssertFalse(element("privacy_consent.screen").exists)
  }

  func testPrivacyContinueWithoutSharingDisablesBothAndRevisitReusesButtons() {
    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: true,
      showsPrivacyConsent: true,
      onboardingNotificationResult: "denied"
    )
    app.launchEnvironment["UITEST_SEED_PRIVACY_CHOICES_ENABLED"] = "1"
    app.launch()

    XCTAssertTrue(element("privacy_consent.screen").waitForExistence(timeout: 5))
    app.buttons["privacy_consent.continue_without_sharing"].tap()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))

    openSettings()
    let analytics = app.switches["settings.anonymous_analytics"]
    scrollToHittable(analytics)
    XCTAssertEqual(analytics.value as? String, "0")
    let diagnostics = app.switches["settings.crash_diagnostics"]
    scrollToHittable(diagnostics)
    XCTAssertEqual(diagnostics.value as? String, "0")
    scrollToHittable(analytics)
    analytics.tap()
    XCTAssertEqual(analytics.value as? String, "1")
    XCTAssertEqual(diagnostics.value as? String, "0")

    let review = app.buttons["settings.review_privacy_choices"]
    scrollToHittable(review)
    review.tap()
    XCTAssertTrue(element("privacy_consent.screen").waitForExistence(timeout: 5))
    XCTAssertFalse(app.switches["privacy_consent.analytics"].exists)
    XCTAssertFalse(app.switches["privacy_consent.diagnostics"].exists)
    app.buttons["privacy_consent.participate_and_continue"].tap()
    XCTAssertTrue(element("settings.screen").waitForExistence(timeout: 5))
    scrollToHittable(analytics)
    XCTAssertEqual(analytics.value as? String, "1")
    scrollToHittable(diagnostics)
    XCTAssertEqual(diagnostics.value as? String, "1")
  }

  func testLegacyUpgradePreservesDisabledReminderAndContinuesPrivacyFlow() {
    app.terminate()
    app = makeApplication(
      resetKeyboardPreferences: true,
      showsPrivacyConsent: true,
      onboardingNotificationResult: "scheduled",
      seedsLegacyDisabledReminder: true
    )
    app.launch()

    XCTAssertTrue(element("privacy_consent.screen").waitForExistence(timeout: 5))
    app.buttons["privacy_consent.continue_without_sharing"].tap()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))

    openSettings()
    let reminder = app.switches["retention.reminder.toggle"]
    scrollToHittable(reminder)
    XCTAssertEqual(reminder.value as? String, "0")
  }

  func testPrivacyDefaultTypeFitsWithoutScrollingOnPhoneAndIPad() {
    let isIPad = max(app.frame.width, app.frame.height) >= 1_000
    app.terminate()
    if isIPad {
      XCUIDevice.shared.orientation = .landscapeLeft
    }
    app = makeApplication(
      resetKeyboardPreferences: true,
      showsPrivacyConsent: true,
      onboardingNotificationResult: "denied"
    )
    app.launch()

    XCTAssertTrue(element("privacy_consent.screen").waitForExistence(timeout: 5))
    XCTAssertFalse(app.scrollViews["privacy_consent.scroll"].exists)
    for identifier in [
      "privacy_consent.usage_information",
      "privacy_consent.error_information",
      "privacy_consent.excluded_data",
      "privacy_consent.privacy_policy",
      "privacy_consent.participate_and_continue",
      "privacy_consent.continue_without_sharing",
    ] {
      let item = element(identifier)
      XCTAssertTrue(item.exists, identifier)
      XCTAssertGreaterThanOrEqual(item.frame.minY, app.frame.minY, identifier)
      XCTAssertLessThanOrEqual(item.frame.maxY, app.frame.maxY, identifier)
    }
  }

  func testSettingsExposePublicPrivacySupportAndContentFeedbackLinks() {
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))

    openSettings()
    XCTAssertTrue(element("settings.screen").waitForExistence(timeout: 5))

    let privacyPolicy = element("settings.privacy_policy")
    scrollToHittable(privacyPolicy)
    XCTAssertTrue(privacyPolicy.isHittable)

    let support = element("settings.support")
    scrollToHittable(support)
    XCTAssertTrue(support.isHittable)

    let contentFeedback = element("settings.content_feedback")
    scrollToHittable(contentFeedback)
    XCTAssertTrue(contentFeedback.isHittable)
  }

  private func assertGamePresetsAndAddDeckRouteToDiscover(gameKind: String) {
    app.tabBars.buttons["ゲーム"].tap()
    let mode = app.buttons["game.mode.\(gameKind)"]
    scrollToHittable(mode)
    mode.tap()

    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("game.\(gameKind).preset.beginner").exists)
    XCTAssertTrue(element("game.\(gameKind).preset.intermediate").exists)
    XCTAssertTrue(element("game.\(gameKind).preset.advanced").exists)
    XCTAssertFalse(app.staticTexts["プレイできるデッキがありません"].exists)

    let addDeck = element("game.\(gameKind).add_deck")
    scrollToHittable(addDeck)
    addDeck.tap()
    XCTAssertTrue(element("discover.search").waitForExistence(timeout: 5))
    XCTAssertTrue(element("discover.catalog").waitForExistence(timeout: 5))
  }

  private func makeApplication(
    resetKeyboardPreferences: Bool,
    deckItemLimit: Int? = nil,
    curriculumItemLimit: Int? = nil,
    gameDuration: TimeInterval? = nil,
    flowStartIndex: Int? = nil,
    resultAnimationScale: Double? = nil,
    flawlessBonus: TimeInterval? = nil,
    koreanKeyboardAvailable: Bool? = nil,
    showsOnboarding: Bool = false,
    showsHatchOnboarding: Bool = false,
    respectsOnboardingState: Bool = false,
    forcesAppTour: Bool = false,
    showsPrivacyConsent: Bool = false,
    onboardingNotificationResult: String = "denied",
    seedsLegacyDisabledReminder: Bool = false,
    practiceAutoSpeaks: Bool? = nil,
    audioProbe: Bool = false,
    seedsAppStoreCaptureState: Bool = false,
    seedsFutureCurriculumSchema: Bool = false
  ) -> XCUIApplication {
    let application = XCUIApplication()
    let captureLocale = ["ja": "ja_JP", "en": "en_US", "ko": "ko_KR", "es": "es_ES", "de": "de_DE", "fr": "fr_FR"][storeCaptureLanguage]!
    application.launchArguments += ["-AppleLanguages", "(\(storeCaptureLanguage))", "-AppleLocale", captureLocale]
    if name.contains("testAppStoreScreenshotGlobal") {
      application.launchArguments += ["-settings.language", storeCaptureLanguage]
      application.launchArguments += ["-mascot.name", storeText("ピヨ", "Piyo", "피요")]
    }
    if name.contains("testDailyChallengeCompletesTodaysStampAndReminderDefaultsOff") || name.contains("testAppStoreScreenshotDailyAndPracticeShowCurrentProgressWithChick") {
      application.launchArguments += ["-onboarding.home_learning_started", "YES"]
    }
    if resetKeyboardPreferences {
      application.launchEnvironment["UITEST_RESET_KEYBOARD_PREFERENCES"] = "1"
      application.launchEnvironment["UITEST_RESET_DECK_LIBRARY"] = "1"
      application.launchEnvironment["UITEST_RESET_CATALOG_CACHE"] = "1"
      application.launchEnvironment["UITEST_RESET_GAME_PROGRESS"] = "1"
      application.launchEnvironment["UITEST_RESET_REVIEW_DECK"] = "1"
      application.launchEnvironment["UITEST_RESET_CURRICULUM_PROGRESS"] = "1"
      application.launchEnvironment["UITEST_RESET_RETENTION"] = "1"
      application.launchEnvironment["UITEST_RESET_ONBOARDING"] = "1"
      application.launchEnvironment["UITEST_RESET_MASCOT"] = "1"
    }
    if showsOnboarding {
      application.launchEnvironment["UITEST_FORCE_ONBOARDING"] = "1"
    } else if showsHatchOnboarding {
      application.launchEnvironment["UITEST_SKIP_ONBOARDING"] = "1"
    } else if !respectsOnboardingState {
      application.launchEnvironment["UITEST_SKIP_ONBOARDING"] = "1"
      application.launchEnvironment["UITEST_SKIP_HATCH_ONBOARDING"] = "1"
    }
    if forcesAppTour {
      application.launchEnvironment["UITEST_FORCE_APP_TOUR"] = "1"
    }
    if showsPrivacyConsent {
      application.launchArguments += [
        "-onboarding.app_tour.completed", "YES",
      ]
      application.launchEnvironment["UITEST_ONBOARDING_NOTIFICATION_RESULT"] =
        onboardingNotificationResult
      if seedsLegacyDisabledReminder {
        application.launchEnvironment["UITEST_SEED_LEGACY_REMINDER_DISABLED"] = "1"
      }
    } else {
      application.launchArguments += [
        "-settings.privacy_notice_version", "1",
      ]
    }
    application.launchEnvironment["UITEST_JST_DAY"] = "2026-07-19"
    application.launchEnvironment["UITEST_GAME_COUNTDOWN_STEP_SECONDS"] = "0.05"
    if let deckItemLimit {
      application.launchEnvironment["UITEST_DECK_ITEM_LIMIT"] = String(deckItemLimit)
    }
    if let curriculumItemLimit {
      application.launchEnvironment["UITEST_CURRICULUM_ITEM_LIMIT"] = String(curriculumItemLimit)
    }
    if let gameDuration {
      application.launchEnvironment["UITEST_GAME_DURATION_SECONDS"] = String(gameDuration)
    }
    if let flowStartIndex {
      application.launchEnvironment["UITEST_FLOW_START_INDEX"] = String(flowStartIndex)
    }
    if let resultAnimationScale {
      application.launchEnvironment["UITEST_RESULT_ANIMATION_SCALE"] = String(resultAnimationScale)
    }
    if let flawlessBonus {
      application.launchEnvironment["UITEST_FLOW_FLAWLESS_BONUS_SECONDS"] = String(flawlessBonus)
    }
    if let koreanKeyboardAvailable {
      application.launchEnvironment["UITEST_KOREAN_KEYBOARD_AVAILABLE"] =
        koreanKeyboardAvailable ? "1" : "0"
    }
    if let practiceAutoSpeaks {
      application.launchArguments += [
        "-settings.practice_auto_speaks",
        practiceAutoSpeaks ? "YES" : "NO",
      ]
    }
    if audioProbe {
      application.launchEnvironment["UITEST_AUDIO_PROBE"] = "1"
    }
    if seedsAppStoreCaptureState {
      application.launchEnvironment["UITEST_SEED_APP_STORE_CAPTURE"] = "1"
    }
    if seedsFutureCurriculumSchema {
      application.launchEnvironment["UITEST_SEED_FUTURE_CURRICULUM_SCHEMA"] = "1"
    }
    return application
  }

  private func startPractice() {
    // Preserve the caller's preferences while installing the old three-target fixture
    // through the existing deck store, then enter a real user-facing deck session.
    app.terminate()
    app.launchEnvironment = app.launchEnvironment.filter {
      !$0.key.hasPrefix("UITEST_RESET_") && !$0.key.hasPrefix("UITEST_SEED_")
    }
    app.launchEnvironment["UITEST_SEED_PRACTICE_DECK"] = "1"
    app.launch()
    XCTAssertTrue(element("home.screen").waitForExistence(timeout: 5))
    let profile = app.buttons[storeText("マイページ", "Profile", "마이페이지")].firstMatch
    XCTAssertTrue(profile.waitForExistence(timeout: 3))
    profile.tap()
    let deck = element("my_decks.deck.user_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
    scrollToHittable(deck)
    deck.tap()
    XCTAssertTrue(element("practice.target.value").waitForExistence(timeout: 5))
  }

  private func assertLandscapeOrientation(file: StaticString = #filePath, line: UInt = #line) {
    let landscape = XCTNSPredicateExpectation(
      predicate: NSPredicate { _, _ in self.app.frame.width > self.app.frame.height }, object: nil
    )
    XCTAssertEqual(XCTWaiter.wait(for: [landscape], timeout: 5), .completed,
                   "Device orientation must also rotate the actual app window", file: file, line: line)
  }

  private func assertBuiltInKeyboardFillsIPadWidth(
    file: StaticString = #filePath,
    line: UInt = #line
  ) {
    let leftKey = app.buttons["keyboard.key.ㅂ"]
    let rightKey = app.buttons["keyboard.key.ㅔ"]
    XCTAssertTrue(leftKey.waitForExistence(timeout: 3), file: file, line: line)
    XCTAssertTrue(rightKey.exists, file: file, line: line)
    let keyboardMinX = leftKey.frame.minX
    let keyboardMaxX = rightKey.frame.maxX
    XCTAssertGreaterThan(keyboardMaxX - keyboardMinX, app.frame.width * 0.90, file: file, line: line)
    XCTAssertLessThanOrEqual(keyboardMaxX, app.frame.maxX, file: file, line: line)
    XCTAssertGreaterThanOrEqual(leftKey.frame.height, 60, file: file, line: line)
    XCTAssertEqual(
      (keyboardMinX + keyboardMaxX) / 2,
      app.frame.midX,
      accuracy: 18,
      file: file,
      line: line
    )
  }

  private func openPracticeTab() {
    guard !element("curriculum.map.screen").exists else { return }
    let practice = app.buttons[storeText("練習", "Practice", "연습")].firstMatch
    XCTAssertTrue(practice.waitForExistence(timeout: 3))
    practice.tap()
    XCTAssertTrue(element("curriculum.map.screen").waitForExistence(timeout: 5))
  }

  private func adaptiveWidthClass(for width: CGFloat) -> String {
    if width < 600 { return "compact" }
    if width < 900 { return "medium" }
    return "wide"
  }

  private func openSettings() {
    let settings = app.buttons["root.settings"]
    XCTAssertTrue(settings.waitForExistence(timeout: 3))
    settings.tap()
    XCTAssertTrue(element("settings.screen").waitForExistence(timeout: 5))
  }

  private func openSeededDowngradeImport() {
    app.tabBars.buttons["マイページ"].tap()
    XCTAssertTrue(element("my_page.screen").waitForExistence(timeout: 5))
    XCTAssertTrue(element("piyodeck.import.preview").waitForExistence(timeout: 5))
    XCTAssertTrue(app.navigationBars["デッキを読み込む"].exists)
  }

  private func typeBuiltInKeys(_ keys: String) {
    for key in keys {
      var keyButton = app.buttons["keyboard.key.\(key)"]
      if !keyButton.exists {
        let shift = app.buttons["keyboard.shift"]
        XCTAssertTrue(shift.exists, "Missing keyboard key \(key) and shift")
        shift.tap()
        keyButton = app.buttons["keyboard.key.\(key)"]
      }
      XCTAssertTrue(keyButton.exists, "Missing keyboard key \(key)")
      keyButton.tap()
    }
  }

  private func returnToGameHub() {
    let end = app.buttons["game.end"]
    XCTAssertTrue(end.waitForExistence(timeout: 3))
    end.tap()
    XCTAssertTrue(element("game.deck_selection.screen").waitForExistence(timeout: 5))
    let gameBack = app.navigationBars.buttons[storeText("ゲーム", "Game", "게임")]
    XCTAssertTrue(gameBack.waitForExistence(timeout: 3))
    gameBack.tap()
    XCTAssertTrue(element("game.selection.screen").waitForExistence(timeout: 5))
  }

  private enum ScrollDirection {
    case up
    case down
  }

  private func scrollSettingsLanguageIntoView(_ target: XCUIElement) {
    let surface = app.scrollViews["settings.screen"]
    XCTAssertTrue(surface.waitForExistence(timeout: 3))
    // iPad sheet children can report hittable even above the sheet. Keep the
    // language segment inside the scroll viewport and below its navigation bar.
    func visibleFrame() -> CGRect {
      let frame = surface.frame.intersection(app.frame)
      let navigationBottom = app.navigationBars.allElementsBoundByIndex
        .filter { $0.isHittable && $0.frame.intersects(frame) }
        .map { $0.frame.maxY }.max() ?? frame.minY
      return CGRect(x: frame.minX, y: max(frame.minY, navigationBottom),
                    width: frame.width, height: frame.maxY - max(frame.minY, navigationBottom))
        .insetBy(dx: 8, dy: 12)
    }
    for _ in 0..<16 {
      if target.exists && target.isHittable && visibleFrame().contains(target.frame) { return }
      let movesDown = target.exists && target.frame.midY < visibleFrame().midY
      let start = surface.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.55))
      let end = surface.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: movesDown ? 0.75 : 0.35))
      start.press(forDuration: 0.05, thenDragTo: end)
    }
    XCTFail("Language picker is not inside the settings viewport: \(app.debugDescription)")
  }

  private func scrollToHittable(
    _ target: XCUIElement,
    direction: ScrollDirection = .up,
    maximumSwipes: Int = 12
  ) {
    for _ in 0..<maximumSwipes where !target.exists {
      scrollVisibleSurface(direction)
    }
    XCTAssertTrue(target.waitForExistence(timeout: 3), app.debugDescription)
    for _ in 0..<maximumSwipes where !isFullyVisible(target) {
      scrollVisibleSurface(direction)
    }
    XCTAssertTrue(isFullyVisible(target), app.debugDescription)
  }

  private func scrollVisibleSurface(_ direction: ScrollDirection) {
    let visibleScrollView = app.scrollViews.allElementsBoundByIndex.first {
      $0.exists && $0.isHittable
    }
    let surface: XCUIElement
    if let visibleScrollView {
      surface = visibleScrollView
    } else {
      surface = app
    }
    switch direction {
    case .up: surface.swipeUp()
    case .down: surface.swipeDown()
    }
  }

  private func scrollAndTap(
    _ target: XCUIElement,
    direction: ScrollDirection = .up,
    maximumSwipes: Int = 12
  ) {
    XCTAssertTrue(target.waitForExistence(timeout: 3), app.debugDescription)
    for _ in 0..<maximumSwipes where !target.isHittable {
      scrollVisibleSurface(direction)
    }
    if target.isHittable {
      target.tap()
    } else {
      let windowFrame = app.windows.element(boundBy: 0).frame.insetBy(dx: 8, dy: 8)
      XCTAssertTrue(windowFrame.intersects(target.frame), app.debugDescription)
      target.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
    }
  }

  private func isFullyVisible(_ target: XCUIElement) -> Bool {
    let visibleFrame = app.frame.insetBy(dx: 8, dy: 12)
    return target.exists && target.isHittable && visibleFrame.contains(target.frame)
  }

  private func element(_ identifier: String) -> XCUIElement {
    app.descendants(matching: .any)[identifier]
  }

  private func waitForLabel(_ label: String, on element: XCUIElement, timeout: TimeInterval) {
    let identifier = element.identifier
    let deadline = Date().addingTimeInterval(timeout)
    repeat {
      let current = identifier.isEmpty ? element : self.element(identifier)
      if current.exists, current.label == label { return }
      RunLoop.current.run(until: Date().addingTimeInterval(0.1))
    } while Date() < deadline

    let current = identifier.isEmpty ? element : self.element(identifier)
    XCTFail("Timed out waiting for \(identifier) label \(label); current label is \(current.label)")
  }

  private func waitForLabelContaining(
    _ text: String,
    on element: XCUIElement,
    timeout: TimeInterval
  ) {
    let identifier = element.identifier
    let deadline = Date().addingTimeInterval(timeout)
    repeat {
      let current = identifier.isEmpty ? element : self.element(identifier)
      if current.exists, current.label.contains(text) { return }
      RunLoop.current.run(until: Date().addingTimeInterval(0.1))
    } while Date() < deadline

    let current = identifier.isEmpty ? element : self.element(identifier)
    XCTFail(
      "Timed out waiting for \(identifier) label containing \(text); current label is \(current.label)"
    )
  }

  private func waitForNonexistence(_ element: XCUIElement, timeout: TimeInterval) {
    let expectation = XCTNSPredicateExpectation(
      predicate: NSPredicate(format: "exists == false"),
      object: element
    )
    XCTAssertEqual(XCTWaiter.wait(for: [expectation], timeout: timeout), .completed)
  }

  private func waitForLabelDifferentFrom(
    _ label: String,
    on element: XCUIElement,
    timeout: TimeInterval
  ) {
    let identifier = element.identifier
    let deadline = Date().addingTimeInterval(timeout)
    repeat {
      let current = identifier.isEmpty ? element : self.element(identifier)
      if current.exists, current.label != label { return }
      RunLoop.current.run(until: Date().addingTimeInterval(0.1))
    } while Date() < deadline

    let current = identifier.isEmpty ? element : self.element(identifier)
    XCTFail(
      "Timed out waiting for \(identifier) label to differ from \(label); current label is \(current.label)"
    )
  }

  private func finishHatchMissionResult() {
    let result = element("practice.result.screen")
    XCTAssertTrue(result.waitForExistence(timeout: 5))
    result.tap()
    let continueButton = app.buttons["onboarding.hatch.result.continue"]
    XCTAssertTrue(continueButton.waitForExistence(timeout: 3))
    XCTAssertTrue(continueButton.isEnabled)
    continueButton.tap()
  }

  private func assertAppTourStep(_ target: String) {
    XCTAssertTrue(element("app_tour.step.\(target)").waitForExistence(timeout: 5))
    let nextButton = app.buttons["app_tour.next"]
    XCTAssertTrue(nextButton.exists)
    XCTAssertTrue(nextButton.isHittable)
    XCTAssertTrue(app.buttons["app_tour.skip"].exists)
  }

  private func waitForValue(_ value: String, on element: XCUIElement, timeout: TimeInterval) {
    let expectation = XCTNSPredicateExpectation(
      predicate: NSPredicate(format: "value == %@", value),
      object: element
    )
    XCTAssertEqual(XCTWaiter.wait(for: [expectation], timeout: timeout), .completed)
  }

  private func waitForValueContaining(
    _ value: String,
    on element: XCUIElement,
    timeout: TimeInterval
  ) {
    let deadline = Date().addingTimeInterval(timeout)
    repeat {
      if let currentValue = element.value as? String,
        currentValue.contains(value)
      {
        return
      }
      RunLoop.current.run(until: Date().addingTimeInterval(0.05))
    } while Date() < deadline

    XCTFail(
      "Timed out waiting for value to contain \(value); current value is \(String(describing: element.value))"
    )
  }

  private func attachScreenshot(named name: String) {
    if self.name.contains("testAppStoreScreenshotGlobal"), max(app.frame.width, app.frame.height) >= 1_000 {
      XCTAssertGreaterThan(app.frame.width, app.frame.height, "Every iPad store scene must be landscape")
    }
    let screenshot = XCUIScreen.main.screenshot()
    let attachment: XCTAttachment
    // The simulator captures its native portrait framebuffer even after iPad rotation.
    // Normalize those pixels without cropping any of the actual app UI.
    if app.frame.width > app.frame.height, let pixels = screenshot.image.cgImage,
       pixels.width < pixels.height {
      let raw = UIImage(cgImage: pixels)
      let size = CGSize(width: pixels.height, height: pixels.width)
      let format = UIGraphicsImageRendererFormat()
      format.scale = 1
      format.opaque = true
      let upright = UIGraphicsImageRenderer(size: size, format: format).image { context in
        context.cgContext.translateBy(x: 0, y: size.height)
        context.cgContext.rotate(by: -.pi / 2)
        raw.draw(at: .zero)
      }
      attachment = XCTAttachment(image: upright)
    } else {
      attachment = XCTAttachment(screenshot: screenshot)
    }
    attachment.name = self.name.contains("testAppStoreScreenshotGlobal")
      ? name.replacingOccurrences(of: "-ja", with: "-\(storeCaptureLanguage)") : name
    attachment.lifetime = .keepAlways
    add(attachment)
  }
}
