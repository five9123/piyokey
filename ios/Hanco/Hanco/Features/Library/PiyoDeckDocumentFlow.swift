import DeckKit
import Foundation
import SwiftUI
import UIKit
import UniformTypeIdentifiers

extension UTType {
  static let piyoDeck = UTType(
    exportedAs: "app.piyokey.piyodeck",
    conformingTo: .zip
  )
}

enum PiyoDeckImportCollision: Equatable {
  case new
  case identical
  case different(existingVersion: Int, incomingVersion: Int)

  var isDowngrade: Bool {
    guard case .different(let existingVersion, let incomingVersion) = self else { return false }
    return incomingVersion < existingVersion
  }

  var isSameVersionConflict: Bool {
    guard case .different(let existingVersion, let incomingVersion) = self else { return false }
    return incomingVersion == existingVersion
  }
}

struct PiyoDeckComparisonSide: Equatable {
  let name: String
  let version: Int
  let updatedAt: Date
  let itemCount: Int

  init(deck: Deck, displayName: String? = nil) {
    name = displayName ?? deck.name
    version = deck.version
    updatedAt = deck.updatedAt
    itemCount = deck.items.count
  }
}

struct PiyoDeckCollisionComparison: Equatable {
  let current: PiyoDeckComparisonSide
  let incoming: PiyoDeckComparisonSide

  init(
    currentDeck: Deck,
    incomingDeck: Deck,
    currentDisplayName: String? = nil,
    incomingDisplayName: String? = nil
  ) {
    current = PiyoDeckComparisonSide(deck: currentDeck, displayName: currentDisplayName)
    incoming = PiyoDeckComparisonSide(deck: incomingDeck, displayName: incomingDisplayName)
  }
}

struct PiyoDeckImportCandidate: Identifiable {
  let id = UUID()
  let package: PiyoDeckPackage
  fileprivate let stagedURL: URL

  init(package: PiyoDeckPackage, stagedURL: URL) {
    self.package = package
    self.stagedURL = stagedURL
  }

  func collision(
    with record: InstalledDeckRecord?,
    installedDeck: Deck? = nil
  ) -> PiyoDeckImportCollision {
    guard let record else { return .new }
    if record.contentSHA256 == package.contentSHA256 || installedDeck == package.deck {
      return .identical
    }
    return .different(
      existingVersion: record.version,
      incomingVersion: package.deck.version
    )
  }
}

enum PiyoDeckDocumentNotice: String, Identifiable {
  case packageTooLarge
  case unsupportedVersion
  case invalidDocument
  case cannotRead
  case cannotSave
  case cannotExport
  case alreadyImported
  case imported

  var id: String { rawValue }

  var titleKey: String { "piyodeck.notice.\(rawValue).title" }
  var messageKey: String { "piyodeck.notice.\(rawValue).message" }
}

struct PiyoDeckExportArtifact: Identifiable {
  let id = UUID()
  let url: URL
  let directoryURL: URL
}

enum PiyoDeckDocumentService {
  enum ServiceError: Error {
    case missingDeckSchema
    case fileTooLarge
  }

  static func deckSchemaData(bundle: Bundle = .main) throws -> Data {
    guard let url = bundle.url(forResource: "deck.schema", withExtension: "json") else {
      throw ServiceError.missingDeckSchema
    }
    return try Data(contentsOf: url)
  }

  static func readPackage(at url: URL, schemaData: Data) throws -> PiyoDeckPackage {
    let data = try limitedData(at: url)
    return try PiyoDeckPackageReader.read(data: data, deckSchemaData: schemaData)
  }

  static func canonicalPackage(for deck: Deck, schemaData: Data) throws -> PiyoDeckPackage {
    let data = try PiyoDeckPackageWriter.write(deck: deck, deckSchemaData: schemaData)
    return try PiyoDeckPackageReader.read(data: data, deckSchemaData: schemaData)
  }

  static func exportArtifact(for deck: Deck, schemaData: Data) throws -> PiyoDeckExportArtifact {
    let data = try PiyoDeckPackageWriter.write(deck: deck, deckSchemaData: schemaData)
    let directoryURL = FileManager.default.temporaryDirectory
      .appendingPathComponent("PiyokeyDeckExports", isDirectory: true)
      .appendingPathComponent(UUID().uuidString, isDirectory: true)
    try FileManager.default.createDirectory(at: directoryURL, withIntermediateDirectories: true)
    let fileURL = directoryURL.appendingPathComponent("\(safeFilename(deck.name)).piyodeck")
    try data.write(to: fileURL, options: .atomic)
    return PiyoDeckExportArtifact(url: fileURL, directoryURL: directoryURL)
  }

  static func stageDocument(
    from sourceURL: URL,
    fileManager: FileManager = .default
  ) throws -> URL {
    let rootURL = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
      .appendingPathComponent("Hanco", isDirectory: true)
      .appendingPathComponent("PendingImports", isDirectory: true)
    try fileManager.createDirectory(at: rootURL, withIntermediateDirectories: true)
    let stagedURL = rootURL.appendingPathComponent("\(UUID().uuidString).piyodeck")
    let partialURL = stagedURL.appendingPathExtension("partial")

    let didStartSecurityScope = sourceURL.startAccessingSecurityScopedResource()
    defer {
      if didStartSecurityScope { sourceURL.stopAccessingSecurityScopedResource() }
    }

    do {
      try copyLimited(from: sourceURL, to: partialURL, fileManager: fileManager)
      try fileManager.moveItem(at: partialURL, to: stagedURL)
      return stagedURL
    } catch {
      try? fileManager.removeItem(at: partialURL)
      try? fileManager.removeItem(at: stagedURL)
      throw error
    }
  }

  static func makeUserCopy(of source: Deck, at date: Date = Date()) -> Deck {
    Deck(
      deckId: makeIdentifier(prefix: "user_"),
      version: 1,
      name: source.name,
      author: DeckAuthor(id: UserDeckDraft.localAuthorID, nickname: source.author.nickname),
      official: false,
      type: source.type,
      level: source.level,
      tags: source.tags,
      createdAt: date,
      updatedAt: date,
      items: source.items.map { item in
        DeckItem(
          id: makeIdentifier(prefix: "item_"),
          ko: item.ko,
          readingJa: item.readingJa,
          meaningJa: item.meaningJa,
          audio: nil,
          localizations: item.localizations
        )
      },
      localizations: source.localizations
    )
  }

  private static func limitedData(at url: URL) throws -> Data {
    let handle = try FileHandle(forReadingFrom: url)
    defer { try? handle.close() }
    let data = try handle.read(upToCount: PiyoDeckPackageLimits.maximumPackageBytes + 1) ?? Data()
    guard data.count <= PiyoDeckPackageLimits.maximumPackageBytes else {
      throw ServiceError.fileTooLarge
    }
    return data
  }

  private static func copyLimited(
    from sourceURL: URL,
    to destinationURL: URL,
    fileManager: FileManager
  ) throws {
    let input = try FileHandle(forReadingFrom: sourceURL)
    guard fileManager.createFile(atPath: destinationURL.path, contents: nil) else {
      throw CocoaError(.fileWriteUnknown)
    }
    let output = try FileHandle(forWritingTo: destinationURL)
    defer {
      try? input.close()
      try? output.close()
    }

    var byteCount = 0
    while let chunk = try input.read(upToCount: 64 * 1_024), !chunk.isEmpty {
      byteCount += chunk.count
      guard byteCount <= PiyoDeckPackageLimits.maximumPackageBytes else {
        throw ServiceError.fileTooLarge
      }
      try output.write(contentsOf: chunk)
    }
    try output.synchronize()
  }

  private static func safeFilename(_ value: String) -> String {
    let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-_"))
    let scalars = value.unicodeScalars.map { allowed.contains($0) ? Character(String($0)) : "-" }
    let collapsed = String(scalars)
      .split(separator: "-", omittingEmptySubsequences: true)
      .joined(separator: "-")
    return String((collapsed.isEmpty ? "piyokey-deck" : collapsed).prefix(80))
  }

  private static func makeIdentifier(prefix: String) -> String {
    prefix + UserDeckDraft.randomUUIDHex()
  }
}

@MainActor
final class PiyoDeckDocumentCoordinator: ObservableObject {
  @Published fileprivate(set) var candidate: PiyoDeckImportCandidate?
  @Published fileprivate(set) var notice: PiyoDeckDocumentNotice?
  @Published fileprivate(set) var exportArtifact: PiyoDeckExportArtifact?
  @Published private(set) var isReading = false

  private var queuedStagedURLs: [URL] = []
  private var activeReadCount = 0
  private var isPreparingCandidate = false

  func resumePendingIfNeeded(
    fileManager: FileManager = .default,
    pendingRootURL: URL? = nil
  ) async {
    guard candidate == nil, queuedStagedURLs.isEmpty, !isPreparingCandidate,
      activeReadCount == 0
    else { return }
    let rootURL =
      pendingRootURL
      ?? fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
      .appendingPathComponent("Hanco", isDirectory: true)
      .appendingPathComponent("PendingImports", isDirectory: true)
    let contents =
      (try? fileManager.contentsOfDirectory(
        at: rootURL,
        includingPropertiesForKeys: nil,
        options: [.skipsHiddenFiles]
      )) ?? []
    for partialURL in contents where partialURL.pathExtension.lowercased() == "partial" {
      try? fileManager.removeItem(at: partialURL)
    }
    let urls = contents.filter { $0.pathExtension.lowercased() == "piyodeck" }.sorted {
      $0.lastPathComponent < $1.lastPathComponent
    }
    queuedStagedURLs = urls
    await prepareNextIfPossible()
  }

  func receive(_ sourceURL: URL) async {
    beginReading()
    defer { endReading() }
    var stagedURL: URL?
    do {
      stagedURL = try await Task.detached {
        try PiyoDeckDocumentService.stageDocument(from: sourceURL)
      }.value
      guard let stagedURL else { return }
      queuedStagedURLs.append(stagedURL)
      await prepareNextIfPossible()
    } catch {
      if let stagedURL { try? FileManager.default.removeItem(at: stagedURL) }
      if notice == nil { notice = Self.notice(for: error) }
    }
  }

  func dismissCandidate(showNext: Bool = true) {
    // SwiftUI may set the sheet binding to false after a programmatic
    // dismissal. Treat that second callback as a no-op so it cannot dequeue
    // and skip the next staged document.
    guard let currentCandidate = candidate else { return }
    try? FileManager.default.removeItem(at: currentCandidate.stagedURL)
    candidate = nil
    if showNext { scheduleNextCandidate() }
  }

  func finishImport() {
    dismissCandidate(showNext: false)
    notice = .imported
  }

  func markAlreadyImported() {
    dismissCandidate(showNext: false)
    notice = .alreadyImported
  }

  func prepareExport(for deck: Deck) async {
    do {
      let schemaData = try PiyoDeckDocumentService.deckSchemaData()
      let artifact = try await Task.detached {
        try PiyoDeckDocumentService.exportArtifact(for: deck, schemaData: schemaData)
      }.value
      exportArtifact = artifact
    } catch {
      notice = .cannotExport
    }
  }

  func finishExport() {
    if let exportArtifact {
      try? FileManager.default.removeItem(at: exportArtifact.directoryURL)
    }
    exportArtifact = nil
  }

  func dismissNotice() {
    notice = nil
    scheduleNextCandidate()
  }

  func reportReadFailure() {
    notice = .cannotRead
  }

  private func prepareNextIfPossible() async {
    guard candidate == nil, notice == nil, !isPreparingCandidate,
      !queuedStagedURLs.isEmpty
    else { return }
    let stagedURL = queuedStagedURLs.removeFirst()
    isPreparingCandidate = true
    beginReading()
    defer {
      endReading()
      isPreparingCandidate = false
    }
    do {
      let schemaData = try PiyoDeckDocumentService.deckSchemaData()
      let package = try await Task.detached {
        try PiyoDeckDocumentService.readPackage(at: stagedURL, schemaData: schemaData)
      }.value
      candidate = PiyoDeckImportCandidate(package: package, stagedURL: stagedURL)
    } catch {
      try? FileManager.default.removeItem(at: stagedURL)
      notice = Self.notice(for: error)
    }
  }

  private func scheduleNextCandidate() {
    Task { @MainActor [weak self] in
      await self?.prepareNextIfPossible()
    }
  }

  private func beginReading() {
    activeReadCount += 1
    isReading = true
  }

  private func endReading() {
    activeReadCount = max(0, activeReadCount - 1)
    isReading = activeReadCount > 0
  }

  private static func notice(for error: Error) -> PiyoDeckDocumentNotice {
    if let serviceError = error as? PiyoDeckDocumentService.ServiceError {
      switch serviceError {
      case .fileTooLarge: return .packageTooLarge
      case .missingDeckSchema: return .cannotRead
      }
    }
    if let importError = error as? PiyoDeckImportError {
      switch importError {
      case .packageTooLarge, .entryTooLarge:
        return .packageTooLarge
      case .unsupportedFormatVersion, .unsupportedDeckSchemaVersion:
        return .unsupportedVersion
      default:
        return .invalidDocument
      }
    }
    return .cannotRead
  }
}

struct PiyoDeckImportPreviewView: View {
  @EnvironmentObject private var deckLibrary: DeckLibrary
  @EnvironmentObject private var reviewDeck: ReviewDeckLibrary
  @EnvironmentObject private var purchaseStore: DeckMakerPurchaseStore
  @EnvironmentObject private var coordinator: PiyoDeckDocumentCoordinator

  let candidate: PiyoDeckImportCandidate

  @State private var isSaving = false
  @State private var showsPaywall = false
  @State private var showsReplaceConfirmation = false
  @State private var shouldCopyAfterPurchase = false
  @State private var shouldInstallAfterPurchase = false
  @State private var currentExportArtifact: PiyoDeckExportArtifact?
  @State private var isExportingCurrent = false
  @State private var showsExportError = false

  private var deck: Deck { candidate.package.deck }
  private var installedDeck: Deck? { deckLibrary.installedDeck(deck.deckId) }
  private var collision: PiyoDeckImportCollision {
    candidate.collision(
      with: deckLibrary.records[deck.deckId],
      installedDeck: installedDeck
    )
  }

  private var comparison: PiyoDeckCollisionComparison? {
    guard case .different = collision, let installedDeck else { return nil }
    return PiyoDeckCollisionComparison(
      currentDeck: installedDeck,
      incomingDeck: deck,
      currentDisplayName: installedDeck.appName,
      incomingDisplayName: deck.appName
    )
  }

  private var interactionsAreDisabled: Bool {
    isSaving || isExportingCurrent
  }

  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 18) {
          header
          metadataCard
          previewCard
          collisionNotice
          if let comparison { comparisonCard(comparison) }
        }
        .padding(20)
      }
      .background(AppPalette.backgroundTop.ignoresSafeArea())
      .accessibilityIdentifier("piyodeck.import.preview")
      .safeAreaInset(edge: .bottom) {
        actionBar
      }
      .navigationTitle(Text("piyodeck.import.navigation_title"))
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) {
          Button("common.close") { coordinator.dismissCandidate() }
            .disabled(interactionsAreDisabled)
            .accessibilityIdentifier("piyodeck.import.close")
        }
      }
    }
    .interactiveDismissDisabled(interactionsAreDisabled)
    .confirmationDialog(
      "piyodeck.import.replace.confirm.title",
      isPresented: $showsReplaceConfirmation,
      titleVisibility: .visible
    ) {
      Button("piyodeck.import.replace.confirm.action", role: .destructive) {
        install(replacing: true)
      }
      Button("deck_editor.cancel", role: .cancel) {}
    } message: {
      replaceConfirmationMessage
    }
    .sheet(isPresented: $showsPaywall) {
      DeckMakerPaywallView(purchaseStore: purchaseStore) {
        if shouldInstallAfterPurchase {
          shouldInstallAfterPurchase = false
          install(replacing: false)
        } else if shouldCopyAfterPurchase {
          shouldCopyAfterPurchase = false
          importAsCopy()
        }
      }
    }
    .sheet(item: $currentExportArtifact, onDismiss: finishCurrentExport) { artifact in
      PiyoDeckActivityView(artifact: artifact) {
        Task { @MainActor in finishCurrentExport() }
      }
    }
    .alert(
      Text(AppLocalization.string(PiyoDeckDocumentNotice.cannotExport.titleKey)),
      isPresented: $showsExportError
    ) {
      Button("deck_maker.alert.ok", role: .cancel) {}
    } message: {
      Text(AppLocalization.string(PiyoDeckDocumentNotice.cannotExport.messageKey))
    }
  }

  private var header: some View {
    HStack(spacing: 14) {
      DeckCoverView(deck: deck, width: 68, height: 86)
      VStack(alignment: .leading, spacing: 5) {
        Text(verbatim: deck.appName)
          .font(.system(.title2, design: .rounded, weight: .heavy))
          .foregroundStyle(AppPalette.ink)
        Text(verbatim: deck.appAuthorNickname)
          .font(.subheadline.weight(.semibold))
          .foregroundStyle(AppPalette.mutedInk)
        Label(
          "piyodeck.import.user_badge", systemImage: "person.crop.square.filled.and.at.rectangle"
        )
        .font(.caption.weight(.bold))
        .foregroundStyle(AppPalette.ink)
      }
      Spacer(minLength: 0)
    }
    .accessibilityElement(children: .combine)
    .accessibilityIdentifier("piyodeck.import.header")
  }

  private var metadataCard: some View {
    VStack(spacing: 12) {
      metadataRow(
        "piyodeck.import.type",
        value: deck.type == .word
          ? AppLocalization.string("deck_editor.type.word")
          : AppLocalization.string("deck_editor.type.sentence"))
      metadataRow("piyodeck.import.level", value: String(deck.level))
      metadataRow("piyodeck.import.items", value: deck.items.count.formatted())
      metadataRow("piyodeck.import.version", value: deck.version.formatted())
      metadataRow("piyodeck.import.tags", value: deck.appTags.joined(separator: " · "))
    }
    .padding(16)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 20))
    .accessibilityIdentifier("piyodeck.import.metadata")
  }

  private func metadataRow(_ key: LocalizedStringKey, value: String) -> some View {
    HStack(alignment: .firstTextBaseline, spacing: 12) {
      Text(key)
        .font(.caption.weight(.bold))
        .foregroundStyle(AppPalette.mutedInk)
      Spacer()
      Text(verbatim: value)
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(AppPalette.ink)
        .multilineTextAlignment(.trailing)
    }
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(Text(key))
    .accessibilityValue(Text(verbatim: value))
  }

  private var previewCard: some View {
    VStack(alignment: .leading, spacing: 12) {
      Text("piyodeck.import.preview")
        .font(.headline.weight(.heavy))
        .foregroundStyle(AppPalette.ink)
      ForEach(Array(deck.items.prefix(3).enumerated()), id: \.element.id) { index, item in
        VStack(alignment: .leading, spacing: 3) {
          Text(verbatim: item.ko)
            .font(.headline.weight(.bold))
            .foregroundStyle(AppPalette.ink)
          if let meaning = item.appMeaning, !meaning.isEmpty {
            Text(verbatim: meaning)
              .font(.subheadline)
              .foregroundStyle(AppPalette.mutedInk)
          }
          if let reading = item.appReading, !reading.isEmpty {
            Text(verbatim: reading)
              .font(.caption)
              .foregroundStyle(AppPalette.mutedInk.opacity(0.8))
          }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("piyodeck.import.preview.item.\(index)")
        if item.id != deck.items.prefix(3).last?.id { Divider() }
      }
    }
    .padding(16)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 20))
    .accessibilityIdentifier("piyodeck.import.items_preview")
  }

  @ViewBuilder
  private var collisionNotice: some View {
    switch collision {
    case .new:
      Label("piyodeck.import.status.new", systemImage: "checkmark.seal.fill")
        .foregroundStyle(AppPalette.ink)
        .accessibilityElement(children: .ignore)
        .accessibilityIdentifier("piyodeck.import.status.new")
        .accessibilityLabel(Text("piyodeck.import.status.accessibility_label"))
        .accessibilityValue(Text("piyodeck.import.status.new"))
    case .identical:
      Label("piyodeck.import.status.identical", systemImage: "equal.circle.fill")
        .foregroundStyle(AppPalette.ink)
        .accessibilityElement(children: .ignore)
        .accessibilityIdentifier("piyodeck.import.status.identical")
        .accessibilityLabel(Text("piyodeck.import.status.accessibility_label"))
        .accessibilityValue(Text("piyodeck.import.status.identical"))
    case .different:
      VStack(alignment: .leading, spacing: 6) {
        Label(statusConflictKey, systemImage: "exclamationmark.triangle.fill")
          .font(.headline.weight(.bold))
          .foregroundStyle(AppPalette.errorText)
        Text("piyodeck.import.status.different_detail")
          .font(.caption)
          .foregroundStyle(AppPalette.ink)
      }
      .accessibilityElement(children: .ignore)
      .accessibilityIdentifier(
        collision.isDowngrade
          ? "piyodeck.import.status.downgrade"
          : collision.isSameVersionConflict
            ? "piyodeck.import.status.same_version"
            : "piyodeck.import.status.different"
      )
      .accessibilityLabel(Text("piyodeck.import.status.accessibility_label"))
      .accessibilityValue(
        Text(statusConflictKey) + Text(" ") + Text("piyodeck.import.status.different_detail")
      )
    }
  }

  private func comparisonCard(_ comparison: PiyoDeckCollisionComparison) -> some View {
    VStack(alignment: .leading, spacing: 14) {
      Label("piyodeck.import.compare.title", systemImage: "rectangle.split.2x1.fill")
        .font(.headline.weight(.heavy))
        .foregroundStyle(AppPalette.ink)

      HStack(spacing: 10) {
        comparisonColumnHeader("piyodeck.import.compare.current")
        comparisonColumnHeader("piyodeck.import.compare.incoming")
      }

      comparisonRow(
        "piyodeck.import.compare.name",
        current: comparison.current.name,
        incoming: comparison.incoming.name
      )
      comparisonRow(
        "piyodeck.import.version",
        current: comparison.current.version.formatted(),
        incoming: comparison.incoming.version.formatted(),
        highlightsIncoming: collision.isDowngrade || collision.isSameVersionConflict
      )
      comparisonRow(
        "piyodeck.import.compare.updated_at",
        current: formattedDate(comparison.current.updatedAt),
        incoming: formattedDate(comparison.incoming.updatedAt)
      )
      comparisonRow(
        "piyodeck.import.items",
        current: comparison.current.itemCount.formatted(),
        incoming: comparison.incoming.itemCount.formatted()
      )

      Button {
        exportCurrentDeck()
      } label: {
        HStack(spacing: 8) {
          if isExportingCurrent { ProgressView() }
          Label("piyodeck.import.action.export_current", systemImage: "square.and.arrow.up")
        }
        .font(.subheadline.weight(.bold))
        .frame(maxWidth: .infinity, minHeight: 44)
      }
      .buttonStyle(.bordered)
      .disabled(interactionsAreDisabled)
      .accessibilityIdentifier("piyodeck.import.action.export_current")
      .accessibilityHint(Text("piyodeck.import.action.export_current.hint"))
    }
    .padding(16)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 20))
    .accessibilityElement(children: .contain)
    .accessibilityIdentifier("piyodeck.import.comparison")
  }

  private func comparisonColumnHeader(_ key: LocalizedStringKey) -> some View {
    Text(key)
      .font(.caption2.weight(.heavy))
      .foregroundStyle(AppPalette.mutedInk)
      .frame(maxWidth: .infinity, alignment: .leading)
      .accessibilityHidden(true)
  }

  private func comparisonRow(
    _ key: LocalizedStringKey,
    current: String,
    incoming: String,
    highlightsIncoming: Bool = false
  ) -> some View {
    VStack(alignment: .leading, spacing: 6) {
      Text(key)
        .font(.caption.weight(.bold))
        .foregroundStyle(AppPalette.mutedInk)
      HStack(alignment: .top, spacing: 10) {
        comparisonValue(current)
        comparisonValue(incoming, isHighlighted: highlightsIncoming)
      }
    }
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(Text(key))
    .accessibilityValue(
      Text(
        verbatim: String(
          format: AppLocalization.string("piyodeck.import.compare.accessibility_value"),
          current,
          incoming
        )
      )
    )
  }

  private func comparisonValue(_ value: String, isHighlighted: Bool = false) -> some View {
    Text(verbatim: value)
      .font(.subheadline.weight(isHighlighted ? .heavy : .semibold))
      .foregroundStyle(isHighlighted ? AppPalette.errorText : AppPalette.ink)
      .frame(maxWidth: .infinity, alignment: .leading)
      .fixedSize(horizontal: false, vertical: true)
  }

  private func formattedDate(_ date: Date) -> String {
    let formatter = DateFormatter()
    formatter.locale = AppLocalization.locale
    formatter.dateStyle = .medium
    formatter.timeStyle = .none
    return formatter.string(from: date)
  }

  private var statusConflictKey: LocalizedStringKey {
    if collision.isDowngrade { return "piyodeck.import.status.downgrade" }
    if collision.isSameVersionConflict { return "piyodeck.import.status.same_version" }
    return "piyodeck.import.status.different"
  }

  private var actionBar: some View {
    VStack(spacing: 10) {
      switch collision {
      case .new:
        primaryButton(
          canInstallNewDeck
            ? "piyodeck.import.action.import"
            : "piyodeck.import.action.unlock_pro",
          systemImage: canInstallNewDeck ? "square.and.arrow.down" : "lock.fill",
          accessibilityIdentifier: "piyodeck.import.action.import"
        ) {
          shouldCopyAfterPurchase = false
          install(replacing: false)
        }
      case .identical:
        primaryButton(
          "piyodeck.import.action.done",
          systemImage: "checkmark",
          accessibilityIdentifier: "piyodeck.import.action.done"
        ) {
          coordinator.markAlreadyImported()
        }
      case .different:
        primaryButton(
          "piyodeck.import.action.keep",
          systemImage: "shield.checkered",
          accessibilityIdentifier: "piyodeck.import.action.keep"
        ) {
          coordinator.dismissCandidate()
        }
        Button {
          if purchaseStore.hasAccess {
            importAsCopy()
          } else {
            shouldInstallAfterPurchase = false
            shouldCopyAfterPurchase = true
            showsPaywall = true
          }
        } label: {
          Label(
            "piyodeck.import.action.copy",
            systemImage: purchaseStore.hasAccess ? "doc.on.doc.fill" : "lock.fill"
          )
          .font(.headline.weight(.bold))
          .frame(maxWidth: .infinity, minHeight: 48)
        }
        .buttonStyle(.bordered)
        .disabled(interactionsAreDisabled)
        .accessibilityIdentifier("piyodeck.import.action.copy")
        .accessibilityLabel(
          Text(
            purchaseStore.hasAccess
              ? "piyodeck.import.action.copy"
              : "piyodeck.import.action.copy.locked_label"
          )
        )
        .accessibilityHint(
          Text(
            purchaseStore.hasAccess
              ? "piyodeck.import.action.copy.hint"
              : "piyodeck.import.action.copy.locked_hint"
          )
        )

        Button(role: .destructive) {
          showsReplaceConfirmation = true
        } label: {
          Label("piyodeck.import.action.replace", systemImage: "arrow.triangle.2.circlepath")
            .font(.subheadline.weight(.bold))
            .frame(maxWidth: .infinity, minHeight: 44)
        }
        .buttonStyle(.bordered)
        .tint(.red)
        .disabled(interactionsAreDisabled)
        .accessibilityIdentifier("piyodeck.import.action.replace")
        .accessibilityHint(Text(replaceAccessibilityHintKey))
      }
    }
    .padding(.horizontal, 20)
    .padding(.top, 12)
    .padding(.bottom, 8)
    .background(.ultraThinMaterial)
  }

  private func primaryButton(
    _ key: LocalizedStringKey,
    systemImage: String,
    accessibilityIdentifier: String,
    action: @escaping () -> Void
  ) -> some View {
    Button(action: action) {
      HStack(spacing: 9) {
        if isSaving { ProgressView().tint(AppPalette.onAccent) }
        Label(key, systemImage: systemImage)
      }
      .font(.headline.weight(.heavy))
      .foregroundStyle(AppPalette.onAccent)
      .frame(maxWidth: .infinity, minHeight: 52)
      .background(AppPalette.accent, in: RoundedRectangle(cornerRadius: 17))
    }
    .buttonStyle(.plain)
    .disabled(interactionsAreDisabled)
    .accessibilityIdentifier(accessibilityIdentifier)
  }

  private var replaceAccessibilityHintKey: LocalizedStringKey {
    if collision.isDowngrade { return "piyodeck.import.action.replace.downgrade_hint" }
    if collision.isSameVersionConflict {
      return "piyodeck.import.action.replace.same_version_hint"
    }
    return "piyodeck.import.action.replace.hint"
  }

  private var replaceConfirmationMessage: Text {
    let historyMessage = Text("piyodeck.import.replace.confirm.message")
    if collision.isDowngrade {
      return Text("piyodeck.import.status.downgrade") + Text(" ") + historyMessage
    }
    if collision.isSameVersionConflict {
      return Text("piyodeck.import.status.same_version") + Text(" ") + historyMessage
    }
    return historyMessage
  }

  private func exportCurrentDeck() {
    guard let installedDeck else { return }
    isExportingCurrent = true
    Task { @MainActor in
      do {
        let schemaData = try PiyoDeckDocumentService.deckSchemaData()
        let artifact = try await Task.detached {
          try PiyoDeckDocumentService.exportArtifact(
            for: installedDeck,
            schemaData: schemaData
          )
        }.value
        isExportingCurrent = false
        currentExportArtifact = artifact
      } catch {
        isExportingCurrent = false
        showsExportError = true
      }
    }
  }

  private func finishCurrentExport() {
    if let currentExportArtifact {
      try? FileManager.default.removeItem(at: currentExportArtifact.directoryURL)
    }
    currentExportArtifact = nil
  }

  private func install(replacing: Bool) {
    guard replacing || collision == .new else { return }
    if !replacing, !canInstallNewDeck {
      shouldCopyAfterPurchase = false
      shouldInstallAfterPurchase = true
      showsPaywall = true
      return
    }
    // Re-importing a derived user deck replaces its payload, but it must not
    // erase where that deck originated. The incoming package intentionally
    // carries no local installation metadata, so retain lineage from the
    // installed record when this is a replacement.
    let derivedFromDeckId =
      replacing
      ? deckLibrary.records[deck.deckId]?.derivedFromDeckId
      : nil
    isSaving = true
    Task { @MainActor in
      do {
        let installed = try await deckLibrary.installUserDeck(
          data: candidate.package.deckData,
          source: .imported,
          contentSHA256: candidate.package.contentSHA256,
          packageFormatVersion: candidate.package.manifest.formatVersion,
          isLocallyModified: false,
          hasPiyokeyProAccess: purchaseStore.hasAccess,
          derivedFromDeckId: derivedFromDeckId
        )
        // A same-ID import after deletion is classified as a new install
        // because only the tombstone remains. Reconcile every successful
        // import so retained review history becomes available again in both
        // the re-import and active-replacement paths.
        _ = reviewDeck.reconcile(with: installed)
        isSaving = false
        coordinator.finishImport()
      } catch PiyokeyProAccessError.freeUserDeckLimitReached {
        isSaving = false
        shouldInstallAfterPurchase = true
        showsPaywall = true
      } catch {
        isSaving = false
        coordinator.dismissCandidate(showNext: false)
        coordinator.reportSaveFailure()
      }
    }
  }

  private func importAsCopy() {
    do {
      try purchaseStore.requireAccess()
    } catch {
      shouldCopyAfterPurchase = true
      showsPaywall = true
      return
    }
    isSaving = true
    Task { @MainActor in
      do {
        let copy = PiyoDeckDocumentService.makeUserCopy(of: deck)
        let schemaData = try PiyoDeckDocumentService.deckSchemaData()
        let package = try await Task.detached {
          try PiyoDeckDocumentService.canonicalPackage(for: copy, schemaData: schemaData)
        }.value
        try purchaseStore.requireAccess()
        _ = try await deckLibrary.installUserDeck(
          data: package.deckData,
          source: .created,
          contentSHA256: package.contentSHA256,
          packageFormatVersion: package.manifest.formatVersion,
          isLocallyModified: true,
          hasPiyokeyProAccess: purchaseStore.hasAccess
        )
        isSaving = false
        coordinator.finishImport()
      } catch {
        isSaving = false
        coordinator.dismissCandidate(showNext: false)
        coordinator.reportSaveFailure()
      }
    }
  }

  private var canInstallNewDeck: Bool {
    deckLibrary.canInstallNewUserDeck(hasPiyokeyProAccess: purchaseStore.hasAccess)
  }
}

extension PiyoDeckDocumentCoordinator {
  func reportSaveFailure() {
    notice = .cannotSave
  }
}

struct PiyoDeckActivityView: UIViewControllerRepresentable {
  let artifact: PiyoDeckExportArtifact
  let onComplete: () -> Void

  func makeUIViewController(context: Context) -> UIActivityViewController {
    let controller = UIActivityViewController(
      activityItems: [artifact.url],
      applicationActivities: nil
    )
    controller.completionWithItemsHandler = { _, _, _, _ in onComplete() }
    return controller
  }

  func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
