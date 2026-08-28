import DeckKit
import Foundation
import Security
import SwiftUI
import UIKit

/// Content can retain Korean translations even though the UI supports only ja/en.
enum DeckContentLanguage: String {
  case japanese = "ja"
  case english = "en"
  case korean = "ko"

  static var current: DeckContentLanguage {
    AppLanguage.current == .japanese ? .japanese : .english
  }
}

struct UserDeckItemDraft: Identifiable, Equatable {
  let id: String
  var ko: String
  var readingJa: String
  var meaningJa: String
  var localizations: [String: DeckItemLocalization]?

  init(
    id: String,
    ko: String = "",
    readingJa: String = "",
    meaningJa: String = "",
    localizations: [String: DeckItemLocalization]? = nil
  ) {
    self.id = id
    self.ko = ko
    self.readingJa = readingJa
    self.meaningJa = meaningJa
    self.localizations = localizations
  }

  func reading(for language: DeckContentLanguage) -> String {
    language == .japanese
      ? readingJa
      : localizations?[language.rawValue]?.reading ?? ""
  }

  func meaning(for language: DeckContentLanguage) -> String {
    language == .japanese
      ? meaningJa
      : localizations?[language.rawValue]?.meaning ?? ""
  }

  mutating func setReading(_ value: String, for language: DeckContentLanguage) {
    guard language != .japanese else {
      readingJa = value
      return
    }
    updateLocalization(language: language, reading: value)
  }

  mutating func setMeaning(_ value: String, for language: DeckContentLanguage) {
    guard language != .japanese else {
      meaningJa = value
      return
    }
    updateLocalization(language: language, meaning: value)
  }

  private mutating func updateLocalization(
    language: DeckContentLanguage,
    reading: String? = nil,
    meaning: String? = nil
  ) {
    let languageCode = language.rawValue
    let existing = localizations?[languageCode]
    var updated = localizations ?? [:]
    updated[languageCode] = DeckItemLocalization(
      meaning: meaning ?? existing?.meaning ?? "",
      reading: reading ?? existing?.reading ?? ""
    )
    localizations = updated
  }
}

struct UserDeckValidationSummary: Equatable {
  let issues: [ContentValidationIssue]
  let localizationKeys: [String]

  var isEmpty: Bool { localizationKeys.isEmpty }
  var firstIssue: ContentValidationIssue? { issues.first }
  var firstField: UserDeckValidationField? {
    issues.lazy.compactMap { Self.field(for: $0.path) }.first
  }

  fileprivate init(issues: [ContentValidationIssue]) {
    self.issues = issues
    var seen = Set<String>()
    localizationKeys = issues.compactMap { issue in
      let key = Self.localizationKey(for: issue)
      return seen.insert(key).inserted ? key : nil
    }
  }

  private static func localizationKey(for issue: ContentValidationIssue) -> String {
    if issue.path == "name", issue.code == "required" {
      return "deck_editor.validation.name_required"
    }
    if issue.path == "author.nickname", issue.code == "required" {
      return "deck_editor.validation.author_required"
    }
    if issue.path == "tags" {
      if issue.code == "duplicate" {
        return "deck_editor.validation.tags_duplicate"
      }
      return "deck_editor.validation.tags"
    }
    if issue.path == "items", issue.code == "min_items" {
      return "deck_editor.validation.items_required"
    }
    if issue.code == "max_target_length" {
      return "deck_editor.validation.korean_length"
    }
    if issue.code == "undecomposable_ko" || issue.code == "missing_hangul" {
      return "deck_editor.validation.korean_input"
    }
    if issue.code == "user_deck_item_limit" {
      return "deck_editor.validation.item_limit"
    }
    if issue.code == "max_length" {
      return "deck_editor.validation.text_length"
    }
    if issue.code == "duplicate" {
      return "deck_editor.validation.duplicate"
    }
    if issue.code == "required" {
      return "deck_editor.validation.required"
    }
    return "deck_editor.validation.invalid"
  }

  private static func field(for path: String) -> UserDeckValidationField? {
    if path == "name" || (path.hasPrefix("localizations.") && path.hasSuffix(".name")) {
      return .name
    }
    if path == "author.nickname"
      || (path.hasPrefix("localizations.") && path.hasSuffix(".author_nickname"))
    {
      return .author
    }
    if path == "tags" || path.hasPrefix("tags[")
      || (path.hasPrefix("localizations.") && path.contains(".tags"))
    {
      return .tags
    }
    if path == "items" { return .items }
    guard path.hasPrefix("items["),
      let closingBracket = path.firstIndex(of: "]"),
      let index = Int(path[path.index(path.startIndex, offsetBy: 6)..<closingBracket])
    else { return nil }

    let fieldPath = path[path.index(after: closingBracket)...]
    if fieldPath.hasPrefix(".ko") { return .itemKorean(index) }
    if fieldPath.hasSuffix(".meaning") || fieldPath.hasPrefix(".meaning_ja") {
      return .itemMeaning(index)
    }
    if fieldPath.hasSuffix(".reading") || fieldPath.hasPrefix(".reading_ja") {
      return .itemReading(index)
    }
    if fieldPath.contains(".localizations.") { return .itemReading(index) }
    return .item(index)
  }
}

enum UserDeckValidationField: Equatable {
  case name
  case author
  case tags
  case items
  case item(Int)
  case itemKorean(Int)
  case itemReading(Int)
  case itemMeaning(Int)
}

struct UserDeckDraftValidationError: Error, Equatable {
  let issues: [ContentValidationIssue]
}

struct UserDeckDraft: Equatable {
  enum Origin: Equatable {
    case new
    case editing
    case officialCopy(sourceDeckID: String)
  }

  static let localAuthorID = "user_local"

  let origin: Origin
  let deckID: String
  let createdAt: Date
  let baseVersion: Int
  let authorID: String
  var metadataLocalizations: [String: DeckMetadataLocalization]?

  var name: String
  var authorNickname: String
  var type: DeckType
  var level: Int
  var tags: [String]
  var items: [UserDeckItemDraft]

  var derivedFromDeckID: String? {
    guard case .officialCopy(let sourceDeckID) = origin else { return nil }
    return sourceDeckID
  }

  func name(for language: DeckContentLanguage) -> String {
    language == .japanese
      ? name
      : metadataLocalizations?[language.rawValue]?.name ?? ""
  }

  func authorNickname(for language: DeckContentLanguage) -> String {
    language == .japanese
      ? authorNickname
      : metadataLocalizations?[language.rawValue]?.authorNickname ?? ""
  }

  func tags(for language: DeckContentLanguage) -> [String] {
    language == .japanese
      ? tags
      : metadataLocalizations?[language.rawValue]?.tags ?? []
  }

  mutating func setName(_ value: String, for language: DeckContentLanguage) {
    guard language != .japanese else {
      name = value
      return
    }
    updateMetadataLocalization(language: language, name: value)
  }

  mutating func setAuthorNickname(_ value: String, for language: DeckContentLanguage) {
    guard language != .japanese else {
      authorNickname = value
      return
    }
    updateMetadataLocalization(language: language, authorNickname: value)
  }

  mutating func setTags(_ value: [String], for language: DeckContentLanguage) {
    guard language != .japanese else {
      tags = value
      return
    }
    updateMetadataLocalization(language: language, tags: value)
  }

  init(
    newAt date: Date = Date(),
    uuidHexGenerator: () -> String = UserDeckDraft.randomUUIDHex
  ) {
    origin = .new
    deckID = Self.makeIdentifier(prefix: "user_", using: uuidHexGenerator)
    createdAt = date
    baseVersion = 0
    authorID = Self.localAuthorID
    metadataLocalizations = nil
    name = ""
    authorNickname = ""
    type = .word
    level = 1
    tags = []
    items = [
      UserDeckItemDraft(
        id: Self.makeIdentifier(prefix: "item_", using: uuidHexGenerator)
      )
    ]
  }

  init(editing deck: Deck) {
    precondition(!deck.official, "Official decks must be copied instead of edited in place.")
    origin = .editing
    deckID = deck.deckId
    createdAt = deck.createdAt
    baseVersion = deck.version
    authorID = deck.author.id
    metadataLocalizations = deck.localizations
    name = deck.name
    authorNickname = deck.author.nickname
    type = deck.type
    level = deck.level
    tags = deck.tags
    items = deck.items.map {
      UserDeckItemDraft(
        id: $0.id,
        ko: $0.ko,
        readingJa: $0.readingJa,
        meaningJa: $0.meaningJa,
        localizations: $0.localizations
      )
    }
  }

  init(
    copyingOfficial deck: Deck,
    at date: Date = Date(),
    uuidHexGenerator: () -> String = UserDeckDraft.randomUUIDHex
  ) {
    precondition(deck.official, "Only official decks use the copy workflow.")
    origin = .officialCopy(sourceDeckID: deck.deckId)
    deckID = Self.makeIdentifier(prefix: "user_", using: uuidHexGenerator)
    createdAt = date
    baseVersion = 0
    authorID = Self.localAuthorID
    metadataLocalizations = deck.localizations
    name = deck.name
    authorNickname = deck.author.nickname
    type = deck.type
    level = deck.level
    tags = deck.tags
    items = deck.items.map {
      UserDeckItemDraft(
        id: Self.makeIdentifier(prefix: "item_", using: uuidHexGenerator),
        ko: $0.ko,
        readingJa: $0.readingJa,
        meaningJa: $0.meaningJa,
        localizations: $0.localizations
      )
    }
  }

  mutating func addItem(
    uuidHexGenerator: () -> String = UserDeckDraft.randomUUIDHex
  ) {
    guard items.count < PiyoDeckPackageLimits.maximumItemCount else { return }
    items.append(
      UserDeckItemDraft(
        id: Self.makeIdentifier(prefix: "item_", using: uuidHexGenerator)
      )
    )
  }

  mutating func removeItems(at offsets: IndexSet) {
    let validOffsets = offsets.filter(items.indices.contains)
    guard items.count - validOffsets.count >= 1 else { return }
    for index in validOffsets.sorted(by: >) {
      items.remove(at: index)
    }
  }

  mutating func moveItems(fromOffsets offsets: IndexSet, toOffset destination: Int) {
    items.move(fromOffsets: offsets, toOffset: destination)
  }

  func materializedDeck(
    at date: Date = Date(),
    language: DeckContentLanguage = .current
  ) -> Deck {
    let displayName = name(for: language)
    let displayAuthorNickname = authorNickname(for: language)
    let displayTags = tags(for: language)
    let baseName = mirroredBaseValue(name, currentValue: displayName)
    let baseAuthorNickname = mirroredBaseValue(
      authorNickname,
      currentValue: displayAuthorNickname
    )
    let baseTags = tags.isEmpty ? displayTags : tags
    return Deck(
      deckId: deckID,
      version: origin == .editing ? baseVersion + 1 : 1,
      name: baseName,
      author: DeckAuthor(id: authorID, nickname: baseAuthorNickname),
      official: false,
      type: type,
      level: level,
      tags: baseTags,
      createdAt: createdAt,
      updatedAt: max(date, createdAt),
      items: items.map {
        let currentReading = $0.reading(for: language)
        let currentMeaning = $0.meaning(for: language)
        return DeckItem(
          id: $0.id,
          ko: $0.ko,
          readingJa: mirroredBaseValue($0.readingJa, currentValue: currentReading),
          meaningJa: mirroredBaseValue($0.meaningJa, currentValue: currentMeaning),
          audio: nil,
          localizations: $0.localizations
        )
      },
      localizations: compatibleMetadataLocalizations(
        baseTags: baseTags,
        currentLanguage: language
      )
    )
  }

  func validationIssues(
    at date: Date = Date(),
    language: DeckContentLanguage = .current
  ) -> [ContentValidationIssue] {
    let deck = materializedDeck(at: date, language: language)
    return UserDeckValidator.validate(deck) + schemaBoundaryIssues(for: deck)
  }

  func validationSummary(
    at date: Date = Date(),
    language: DeckContentLanguage = .current
  ) -> UserDeckValidationSummary {
    UserDeckValidationSummary(issues: validationIssues(at: date, language: language))
  }

  func validatedDeck(
    at date: Date = Date(),
    language: DeckContentLanguage = .current
  ) throws -> Deck {
    let deck = materializedDeck(at: date, language: language)
    let issues = UserDeckValidator.validate(deck) + schemaBoundaryIssues(for: deck)
    guard issues.isEmpty else {
      throw UserDeckDraftValidationError(issues: issues)
    }
    return deck
  }

  private func mirroredBaseValue(_ baseValue: String, currentValue: String) -> String {
    baseValue.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
      ? currentValue
      : baseValue
  }

  static func parseTags(_ text: String) -> [String] {
    text.components(separatedBy: CharacterSet(charactersIn: ",、\n"))
      .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
      .filter { !$0.isEmpty }
  }

  private mutating func updateMetadataLocalization(
    language: DeckContentLanguage,
    name: String? = nil,
    authorNickname: String? = nil,
    tags: [String]? = nil
  ) {
    let languageCode = language.rawValue
    let existing = metadataLocalizations?[languageCode]
    var updated = metadataLocalizations ?? [:]
    updated[languageCode] = DeckMetadataLocalization(
      name: name ?? existing?.name ?? "",
      authorNickname: authorNickname ?? existing?.authorNickname ?? "",
      tags: tags ?? existing?.tags ?? []
    )
    metadataLocalizations = updated
  }

  private func compatibleMetadataLocalizations(
    baseTags: [String],
    currentLanguage: DeckContentLanguage
  ) -> [String: DeckMetadataLocalization]? {
    guard let metadataLocalizations else { return nil }
    let filtered = metadataLocalizations.filter { languageCode, localization in
      // Never discard the values being edited. Keeping an incomplete current
      // localization lets the validator surface required-field errors instead
      // of silently saving the untouched Japanese base.
      if currentLanguage != .japanese, languageCode == currentLanguage.rawValue {
        return true
      }
      guard !localization.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
        !localization.authorNickname.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
      else { return false }
      guard localization.tags.count == baseTags.count else { return false }
      guard languageCode == AppLanguage.english.rawValue else { return true }
      return items.allSatisfy { item in
        guard let localization = item.localizations?[languageCode] else { return false }
        return !localization.meaning.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
          && !localization.reading.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
      }
    }
    return filtered.isEmpty ? nil : filtered
  }

  private func schemaBoundaryIssues(for deck: Deck) -> [ContentValidationIssue] {
    var issues: [ContentValidationIssue] = []

    appendMaximumLengthIssue(deck.name, maximum: 120, path: "name", into: &issues)
    appendMaximumLengthIssue(
      deck.author.nickname,
      maximum: 40,
      path: "author.nickname",
      into: &issues
    )
    for (index, tag) in deck.tags.enumerated() {
      appendMaximumLengthIssue(tag, maximum: 40, path: "tags[\(index)]", into: &issues)
    }
    for (index, item) in deck.items.enumerated() {
      appendMaximumLengthIssue(
        item.readingJa,
        maximum: 300,
        path: "items[\(index)].reading_ja",
        into: &issues
      )
      appendMaximumLengthIssue(
        item.meaningJa,
        maximum: 500,
        path: "items[\(index)].meaning_ja",
        into: &issues
      )
    }
    return issues
  }

  private func appendMaximumLengthIssue(
    _ value: String,
    maximum: Int,
    path: String,
    into issues: inout [ContentValidationIssue]
  ) {
    if value.count > maximum {
      issues.append(
        ContentValidationIssue(
          code: "max_length",
          path: path,
          message: "Maximum length exceeded"
        )
      )
    }
  }

  private static func makeIdentifier(prefix: String, using generator: () -> String) -> String {
    let hex = generator()
    precondition(
      hex.range(of: "^[0-9a-f]{32}$", options: .regularExpression) != nil,
      "UUID generator must return 32 lowercase hexadecimal characters."
    )
    return prefix + hex
  }

  static func randomUUIDHex() -> String {
    var bytes = [UInt8](repeating: 0, count: 16)
    let status = bytes.withUnsafeMutableBytes { buffer in
      SecRandomCopyBytes(kSecRandomDefault, buffer.count, buffer.baseAddress!)
    }
    guard status == errSecSuccess else {
      return UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()
    }

    // RFC 4122 version 4 and variant bits, backed by the OS CSPRNG above.
    bytes[6] = (bytes[6] & 0x0F) | 0x40
    bytes[8] = (bytes[8] & 0x3F) | 0x80
    return bytes.map { String(format: "%02x", $0) }.joined()
  }
}

struct DeckEditorView: View {
  typealias SaveAction = @MainActor (Deck, String?) async throws -> Void
  typealias SaveAsCopyAction = @MainActor (Deck, String?) async throws -> Void
  typealias DeleteAction = @MainActor (String?) async throws -> Void
  typealias DraftChangeAction = @MainActor (UserDeckDraft) throws -> ActiveUserDeckDraft

  private enum FocusedField: Hashable {
    case name
    case author
    case tags
    case itemKorean(String)
    case itemReading(String)
    case itemMeaning(String)
  }

  @Environment(\.dismiss) private var dismiss
  @Environment(\.editMode) private var editMode
  @Environment(\.scenePhase) private var scenePhase
  @State private var draft: UserDeckDraft
  @State private var tagsText: String
  @State private var validationSummary = UserDeckValidationSummary(issues: [])
  @State private var saveErrorKey: String?
  @State private var isSaving = false
  @State private var isDeleting = false
  @State private var showsDeleteConfirmation = false
  @State private var expandedItemIDs: Set<String>
  @State private var validationFocusRequest = 0
  @State private var draftSaveErrorKey: String?
  @State private var draftSaveTask: Task<Void, Never>?
  @State private var completedTerminalAction = false
  @State private var latestDraftID: String?
  @FocusState private var focusedField: FocusedField?

  private let onDraftChange: DraftChangeAction?
  private let onSave: SaveAction
  private let onSaveAsCopy: SaveAsCopyAction?
  private let onDelete: DeleteAction?

  init(
    draft: UserDeckDraft,
    onSave: @escaping @MainActor (Deck) async throws -> Void
  ) {
    self.init(
      draft: draft,
      draftID: nil,
      onDraftChange: nil,
      onSave: { deck, _ in try await onSave(deck) },
      onSaveAsCopy: nil,
      onDelete: nil
    )
  }

  init(
    draft: UserDeckDraft,
    draftID: String? = nil,
    onDraftChange: DraftChangeAction? = nil,
    onSave: @escaping SaveAction,
    onSaveAsCopy: SaveAsCopyAction? = nil,
    onDelete: DeleteAction?
  ) {
    _draft = State(initialValue: draft)
    _latestDraftID = State(initialValue: draftID)
    _tagsText = State(
      initialValue: draft.tags(for: .current).joined(separator: ", ")
    )
    let initiallyExpanded =
      draft.items.count <= 8
      ? Set(draft.items.map(\.id))
      : Set(draft.items.prefix(1).map(\.id))
    _expandedItemIDs = State(initialValue: initiallyExpanded)
    self.onDraftChange = onDraftChange
    self.onSave = onSave
    self.onSaveAsCopy = onSaveAsCopy
    self.onDelete = onDelete
  }

  var body: some View {
    NavigationStack {
      ScrollViewReader { proxy in
        Form {
          metadataSection
          itemsSection
          validationSection
          deleteSection
        }
        .accessibilityIdentifier("deck_editor.screen")
        .onChange(of: validationFocusRequest) { _ in
          focusFirstValidationIssue(using: proxy)
        }
        .onChange(of: draft) { updatedDraft in
          scheduleDraftSave(updatedDraft)
        }
      }
      .navigationTitle(Text(navigationTitleKey))
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) {
          Button("deck_editor.cancel") {
            draftSaveTask?.cancel()
            if persistDraft(draft) {
              dismiss()
            }
          }
          .disabled(isSaving || isDeleting)
          .accessibilityIdentifier("deck_editor.cancel")
        }
        ToolbarItem(placement: .primaryAction) {
          Button {
            save()
          } label: {
            if isSaving {
              ProgressView()
                .accessibilityLabel(Text("deck_editor.saving"))
            } else {
              Text("deck_editor.save")
            }
          }
          .disabled(isSaving || isDeleting)
          .accessibilityIdentifier("deck_editor.save")
        }
        ToolbarItem(placement: .secondaryAction) {
          Button {
            withAnimation {
              editMode?.wrappedValue = isReordering ? .inactive : .active
            }
          } label: {
            Text(reorderButtonTitleKey)
          }
          .disabled(isSaving || isDeleting)
          .accessibilityIdentifier("deck_editor.reorder")
        }
      }
      .alert(
        "deck_editor.delete.confirm.title",
        isPresented: $showsDeleteConfirmation
      ) {
        Button("deck_editor.cancel", role: .cancel) {}
        Button("deck_editor.delete.confirm.action", role: .destructive) {
          deleteDeck()
        }
      } message: {
        Text("deck_editor.delete.confirm.message")
      }
    }
    .interactiveDismissDisabled(isSaving || isDeleting || onDraftChange != nil)
    .onDisappear {
      draftSaveTask?.cancel()
      if !completedTerminalAction {
        _ = persistDraft(draft)
      }
    }
    .onChange(of: scenePhase) { phase in
      guard phase != .active, !completedTerminalAction else { return }
      draftSaveTask?.cancel()
      _ = persistDraft(draft)
    }
  }

  private var navigationTitleKey: LocalizedStringKey {
    switch draft.origin {
    case .new:
      return "deck_editor.title.new"
    case .editing:
      return "deck_editor.title.edit"
    case .officialCopy:
      return "deck_editor.title.copy"
    }
  }

  private var isReordering: Bool {
    editMode?.wrappedValue.isEditing == true
  }

  private var reorderButtonTitleKey: LocalizedStringKey {
    isReordering ? "deck_editor.items.reorder.done" : "deck_editor.items.reorder"
  }

  private var currentNameBinding: Binding<String> {
    Binding(
      get: { draft.name(for: .current) },
      set: { draft.setName($0, for: .current) }
    )
  }

  private var currentAuthorNicknameBinding: Binding<String> {
    Binding(
      get: { draft.authorNickname(for: .current) },
      set: { draft.setAuthorNickname($0, for: .current) }
    )
  }

  private func currentReadingBinding(
    for item: Binding<UserDeckItemDraft>
  ) -> Binding<String> {
    Binding(
      get: { item.wrappedValue.reading(for: .current) },
      set: { value in item.wrappedValue.setReading(value, for: .current) }
    )
  }

  private func currentMeaningBinding(
    for item: Binding<UserDeckItemDraft>
  ) -> Binding<String> {
    Binding(
      get: { item.wrappedValue.meaning(for: .current) },
      set: { value in item.wrappedValue.setMeaning(value, for: .current) }
    )
  }

  private var metadataSection: some View {
    Section {
      TextField("deck_editor.name", text: currentNameBinding, axis: .vertical)
        .textInputAutocapitalization(.sentences)
        .focused($focusedField, equals: .name)
        .id("deck_editor.field.name")
        .accessibilityIdentifier("deck_editor.name")

      TextField("deck_editor.author", text: currentAuthorNicknameBinding, axis: .vertical)
        .textInputAutocapitalization(.words)
        .focused($focusedField, equals: .author)
        .id("deck_editor.field.author")
        .accessibilityIdentifier("deck_editor.author")

      Picker("deck_editor.type", selection: $draft.type) {
        Text("deck_editor.type.word").tag(DeckType.word)
        Text("deck_editor.type.sentence").tag(DeckType.sentence)
      }

      Picker("deck_editor.level", selection: $draft.level) {
        Text("deck_editor.level.1").tag(1)
        Text("deck_editor.level.2").tag(2)
        Text("deck_editor.level.3").tag(3)
      }

      TextField("deck_editor.tags", text: $tagsText, axis: .vertical)
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
        .focused($focusedField, equals: .tags)
        .id("deck_editor.field.tags")
        .accessibilityIdentifier("deck_editor.tags")
        .onChange(of: tagsText) { newValue in
          draft.setTags(UserDeckDraft.parseTags(newValue), for: .current)
        }

      Text("deck_editor.tags.help")
        .font(.caption)
        .foregroundStyle(AppPalette.mutedInk)
    } header: {
      ViewThatFits(in: .horizontal) {
        HStack {
          Text("deck_editor.metadata.section")
          Spacer()
          editingLanguageLabel
        }
        VStack(alignment: .leading, spacing: 4) {
          Text("deck_editor.metadata.section")
          editingLanguageLabel
        }
      }
    }
  }

  private var editingLanguageLabel: some View {
    Label(
      AppLocalization.string(editingLanguageNameKey),
      systemImage: "character.bubble"
    )
    .font(.caption.weight(.semibold))
    .foregroundStyle(AppPalette.mutedInk)
    .accessibilityLabel(
      Text(
        String(
          format: AppLocalization.string("deck_editor.language.accessibility_format"),
          AppLocalization.string(editingLanguageNameKey)
        )
      )
    )
  }

  private var editingLanguageNameKey: String {
    switch AppLanguage.current {
    case .japanese: "settings.language.japanese"
    case .english: "settings.language.english"
    }
  }

  private var itemsSection: some View {
    Section {
      ForEach($draft.items) { $item in
        itemDisclosure(item: $item)
          .deleteDisabled(draft.items.count == 1)
      }
      .onDelete { offsets in
        let removedIDs = offsets.compactMap { index in
          draft.items.indices.contains(index) ? draft.items[index].id : nil
        }
        draft.removeItems(at: offsets)
        expandedItemIDs.subtract(removedIDs)
      }
      .onMove { offsets, destination in
        draft.moveItems(fromOffsets: offsets, toOffset: destination)
      }

      Button {
        draft.addItem()
        if let addedItem = draft.items.last {
          expandedItemIDs.insert(addedItem.id)
          focusedField = .itemKorean(addedItem.id)
        }
      } label: {
        Label("deck_editor.items.add", systemImage: "plus.circle.fill")
      }
      .disabled(draft.items.count >= PiyoDeckPackageLimits.maximumItemCount)
      .accessibilityIdentifier("deck_editor.items.add")
    } header: {
      HStack {
        Text("deck_editor.items.section")
        Spacer()
        Text(draft.items.count, format: .number)
      }
    } footer: {
      Text("deck_editor.items.help")
    }
    .id("deck_editor.items")
  }

  private func itemDisclosure(item: Binding<UserDeckItemDraft>) -> some View {
    let itemID = item.wrappedValue.id
    let index = draft.items.firstIndex(where: { $0.id == itemID }) ?? 0
    let position = index + 1
    return DisclosureGroup(
      isExpanded: Binding(
        get: { expandedItemIDs.contains(itemID) },
        set: { expanded in
          if expanded {
            expandedItemIDs.insert(itemID)
          } else {
            expandedItemIDs.remove(itemID)
          }
        }
      )
    ) {
      itemEditor(item: item, index: index)
    } label: {
      VStack(alignment: .leading, spacing: 3) {
        Text(
          String(
            format: AppLocalization.string("deck_editor.item.position_format"),
            position,
            draft.items.count
          )
        )
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(AppPalette.ink)
        Text(
          item.wrappedValue.ko.isEmpty
            ? AppLocalization.string("deck_editor.item.empty")
            : item.wrappedValue.ko
        )
        .font(.caption)
        .foregroundStyle(AppPalette.mutedInk)
        .lineLimit(1)
      }
      .accessibilityElement(children: .combine)
    }
    .id("deck_editor.item.\(index)")
    .accessibilityIdentifier("deck_editor.item.\(index)")
  }

  private func itemEditor(
    item: Binding<UserDeckItemDraft>,
    index: Int
  ) -> some View {
    let itemID = item.wrappedValue.id
    return VStack(alignment: .leading, spacing: 10) {
      TextField("deck_editor.item.korean", text: item.ko, axis: .vertical)
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
        .focused($focusedField, equals: .itemKorean(itemID))
        .id("deck_editor.field.item.\(index).korean")
        .accessibilityIdentifier("deck_editor.item.\(index).korean")

      TextField("deck_editor.item.reading", text: currentReadingBinding(for: item), axis: .vertical)
        .textInputAutocapitalization(.never)
        .focused($focusedField, equals: .itemReading(itemID))
        .id("deck_editor.field.item.\(index).reading")
        .accessibilityIdentifier("deck_editor.item.\(index).reading")

      TextField("deck_editor.item.meaning", text: currentMeaningBinding(for: item), axis: .vertical)
        .focused($focusedField, equals: .itemMeaning(itemID))
        .id("deck_editor.field.item.\(index).meaning")
        .accessibilityIdentifier("deck_editor.item.\(index).meaning")
    }
    .padding(.vertical, 6)
  }

  @ViewBuilder
  private var validationSection: some View {
    if !validationSummary.isEmpty || saveErrorKey != nil || draftSaveErrorKey != nil {
      Section("deck_editor.validation.section") {
        if let draftSaveErrorKey {
          Label {
            Text(LocalizedStringKey(draftSaveErrorKey))
          } icon: {
            Image(systemName: "externaldrive.badge.exclamationmark")
          }
          .foregroundStyle(AppPalette.errorText)
        }
        if let saveErrorKey {
          Label {
            Text(LocalizedStringKey(saveErrorKey))
          } icon: {
            Image(systemName: "exclamationmark.triangle.fill")
          }
          .foregroundStyle(AppPalette.errorText)
          if saveErrorKey == "deck_editor.save.source_changed", onSaveAsCopy != nil {
            Button {
              saveAsCopy()
            } label: {
              Label("deck_editor.save_as_copy", systemImage: "doc.on.doc.fill")
            }
            .disabled(isSaving || isDeleting)
            .accessibilityHint(Text("deck_editor.save_as_copy.hint"))
            .accessibilityIdentifier("deck_editor.save_as_copy")
          }
        }

        ForEach(validationSummary.localizationKeys, id: \.self) { key in
          Label {
            Text(LocalizedStringKey(key))
          } icon: {
            Image(systemName: "exclamationmark.circle.fill")
          }
          .foregroundStyle(AppPalette.errorText)
        }
      }
    }
  }

  @ViewBuilder
  private var deleteSection: some View {
    if onDelete != nil {
      Section {
        Button(role: .destructive) {
          showsDeleteConfirmation = true
        } label: {
          Label("deck_editor.delete", systemImage: "trash.fill")
        }
        .disabled(isSaving || isDeleting)
      }
    }
  }

  private func save() {
    saveErrorKey = nil
    draftSaveTask?.cancel()
    guard persistDraft(draft) else { return }
    validationSummary = draft.validationSummary(language: .current)
    guard validationSummary.isEmpty else {
      validationFocusRequest += 1
      return
    }

    let deck: Deck
    do {
      deck = try draft.validatedDeck(language: .current)
    } catch {
      validationSummary = draft.validationSummary(language: .current)
      validationFocusRequest += 1
      return
    }

    isSaving = true
    Task { @MainActor in
      do {
        try await onSave(deck, latestDraftID)
        completedTerminalAction = true
        isSaving = false
        dismiss()
      } catch let error as UserDeckEditCommitError where error == .sourceChanged {
        isSaving = false
        saveErrorKey = "deck_editor.save.source_changed"
      } catch {
        isSaving = false
        saveErrorKey = "deck_editor.save.failed"
      }
    }
  }

  private func saveAsCopy() {
    guard let onSaveAsCopy else { return }
    draftSaveTask?.cancel()
    guard persistDraft(draft) else { return }
    validationSummary = draft.validationSummary(language: .current)
    guard validationSummary.isEmpty,
      let deck = try? draft.validatedDeck(language: .current)
    else {
      validationFocusRequest += 1
      return
    }

    isSaving = true
    Task { @MainActor in
      do {
        try await onSaveAsCopy(deck, latestDraftID)
        completedTerminalAction = true
        isSaving = false
        dismiss()
      } catch {
        isSaving = false
        saveErrorKey = "deck_editor.save.failed"
      }
    }
  }

  private func scheduleDraftSave(_ updatedDraft: UserDeckDraft) {
    guard onDraftChange != nil else { return }
    draftSaveTask?.cancel()
    draftSaveTask = Task { @MainActor in
      try? await Task.sleep(nanoseconds: 350_000_000)
      guard !Task.isCancelled else { return }
      persistDraft(updatedDraft)
    }
  }

  @discardableResult
  private func persistDraft(_ updatedDraft: UserDeckDraft) -> Bool {
    guard let onDraftChange else { return true }
    do {
      let active = try onDraftChange(updatedDraft)
      latestDraftID = active.draftID
      draftSaveErrorKey = nil
      return true
    } catch {
      draftSaveErrorKey = "deck_editor.draft.save_failed"
      return false
    }
  }

  @MainActor
  private func focusFirstValidationIssue(using proxy: ScrollViewProxy) {
    guard let field = validationSummary.firstField else { return }
    switch field {
    case .name:
      focusedField = .name
      scrollAndAnnounce("deck_editor.field.name", using: proxy)
    case .author:
      focusedField = .author
      scrollAndAnnounce("deck_editor.field.author", using: proxy)
    case .tags:
      focusedField = .tags
      scrollAndAnnounce("deck_editor.field.tags", using: proxy)
    case .items:
      scrollAndAnnounce("deck_editor.items", using: proxy)
    case .item(let index):
      revealItem(at: index, focusedPart: nil, using: proxy)
    case .itemKorean(let index):
      revealItem(at: index, focusedPart: .korean, using: proxy)
    case .itemReading(let index):
      revealItem(at: index, focusedPart: .reading, using: proxy)
    case .itemMeaning(let index):
      revealItem(at: index, focusedPart: .meaning, using: proxy)
    }
  }

  @MainActor
  private func scrollAndAnnounce(_ targetID: String, using proxy: ScrollViewProxy) {
    withAnimation {
      proxy.scrollTo(targetID, anchor: .center)
    }
    announceFirstValidationIssue()
  }

  @MainActor
  private func announceFirstValidationIssue() {
    let announcementKey =
      validationSummary.localizationKeys.first ?? "deck_editor.validation.invalid"
    UIAccessibility.post(
      notification: .announcement,
      argument: AppLocalization.string(announcementKey)
    )
  }

  private enum ItemFieldPart {
    case korean
    case reading
    case meaning
  }

  private func revealItem(
    at index: Int,
    focusedPart: ItemFieldPart?,
    using proxy: ScrollViewProxy
  ) {
    guard draft.items.indices.contains(index) else {
      scrollAndAnnounce("deck_editor.items", using: proxy)
      return
    }
    let itemID = draft.items[index].id
    expandedItemIDs.insert(itemID)
    withAnimation {
      proxy.scrollTo("deck_editor.item.\(index)", anchor: .center)
    }

    // DisclosureGroup inserts its field subtree on the next render pass. Wait
    // for that subtree before targeting a late item (notably item 1,000), or
    // ScrollViewReader and FocusState will silently miss the destination.
    Task { @MainActor in
      await Task.yield()
      await Task.yield()
      let targetID: String
      switch focusedPart {
      case .korean:
        focusedField = .itemKorean(itemID)
        targetID = "deck_editor.field.item.\(index).korean"
      case .reading:
        focusedField = .itemReading(itemID)
        targetID = "deck_editor.field.item.\(index).reading"
      case .meaning:
        focusedField = .itemMeaning(itemID)
        targetID = "deck_editor.field.item.\(index).meaning"
      case nil:
        targetID = "deck_editor.item.\(index)"
      }
      withAnimation {
        proxy.scrollTo(targetID, anchor: .center)
      }
      announceFirstValidationIssue()
    }
  }

  private func deleteDeck() {
    guard let onDelete else { return }
    draftSaveTask?.cancel()
    saveErrorKey = nil
    isDeleting = true
    Task { @MainActor in
      do {
        try await onDelete(latestDraftID)
        completedTerminalAction = true
        isDeleting = false
        dismiss()
      } catch {
        isDeleting = false
        saveErrorKey = "deck_editor.delete.failed"
      }
    }
  }
}
