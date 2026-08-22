import Foundation

public enum PiyoDeckPackageReader {
  public static func read(data: Data, deckSchemaData: Data) throws -> PiyoDeckPackage {
    guard data.count <= PiyoDeckPackageLimits.maximumPackageBytes else {
      throw PiyoDeckImportError.packageTooLarge(
        actual: data.count,
        maximum: PiyoDeckPackageLimits.maximumPackageBytes
      )
    }

    let entries = try PiyoDeckZIP.read(data)
    guard let manifestData = entries["manifest.json"], let deckData = entries["deck.json"] else {
      throw PiyoDeckImportError.invalidEntrySet(Array(entries.keys).sorted())
    }

    // Both JSON transports are untrusted archive input. Validate them before
    // trusting version or metadata from either entry.
    try PiyoDeckStrictJSON.validate(manifestData, name: "manifest.json")
    try PiyoDeckStrictJSON.validate(deckData, name: "deck.json")
    let manifest = try decodeManifest(manifestData)

    guard manifest.deck.sizeBytes == deckData.count else {
      throw PiyoDeckImportError.manifestMismatch(field: "deck.size_bytes")
    }
    guard PiyoDeckDigest.sha256Hex(deckData) == manifest.deck.sha256 else {
      throw PiyoDeckImportError.sha256Mismatch
    }

    let deckObject: [String: Any]
    do {
      guard let object = try JSONSerialization.jsonObject(with: deckData) as? [String: Any] else {
        throw PiyoDeckImportError.invalidJSON(
          name: "deck.json",
          reason: "root must be a JSON object"
        )
      }
      deckObject = object
    } catch let error as PiyoDeckImportError {
      throw error
    } catch {
      throw PiyoDeckImportError.invalidJSON(
        name: "deck.json",
        reason: String(describing: error)
      )
    }
    guard manifest.deck.deckId == deckObject["deck_id"] as? String else {
      throw PiyoDeckImportError.manifestMismatch(field: "deck.deck_id")
    }
    guard manifest.deck.deckVersion == deckObject["version"] as? Int else {
      throw PiyoDeckImportError.manifestMismatch(field: "deck.deck_version")
    }
    guard
      let rawItems = deckObject["items"] as? [Any],
      manifest.deck.itemCount == rawItems.count
    else {
      throw PiyoDeckImportError.manifestMismatch(field: "deck.item_count")
    }

    let schemaIssues: [ContentValidationIssue]
    do {
      schemaIssues = try JSONSchemaValidator.validate(
        instanceData: deckData,
        schemaData: deckSchemaData
      )
    } catch {
      throw PiyoDeckImportError.invalidJSON(
        name: "deck.json",
        reason: String(describing: error)
      )
    }
    guard schemaIssues.isEmpty else {
      throw PiyoDeckImportError.deckSchemaViolation(schemaIssues)
    }

    let deck: Deck
    do {
      deck = try DeckKitJSON.decodeDeck(from: deckData)
    } catch {
      throw PiyoDeckImportError.invalidJSON(
        name: "deck.json",
        reason: String(describing: error)
      )
    }

    let semanticIssues = DeckValidator.validate(deck)
    guard semanticIssues.isEmpty else {
      throw PiyoDeckImportError.invalidUserDeck(semanticIssues)
    }
    let userIssues = UserDeckValidator.validatePackageRules(deck)
    guard userIssues.isEmpty else {
      throw PiyoDeckImportError.invalidUserDeck(userIssues)
    }
    return PiyoDeckPackage(manifest: manifest, deck: deck, deckData: deckData)
  }

  private static func decodeManifest(_ data: Data) throws -> PiyoDeckManifest {
    try PiyoDeckStrictJSON.validate(data, name: "manifest.json")

    let manifest: PiyoDeckManifest
    do {
      manifest = try JSONDecoder().decode(PiyoDeckManifest.self, from: data)
    } catch {
      throw PiyoDeckImportError.invalidManifest([
        .init(code: "manifest.decode", path: "$", message: String(describing: error))
      ])
    }

    guard manifest.format == PiyoDeckManifest.formatIdentifier else {
      throw PiyoDeckImportError.invalidManifest([
        .init(
          code: "schema.enum",
          path: "$.format",
          message: "piyokey.deck-package여야 합니다"
        )
      ])
    }
    if manifest.formatVersion != PiyoDeckManifest.currentFormatVersion {
      throw PiyoDeckImportError.unsupportedFormatVersion(manifest.formatVersion)
    }
    if manifest.deckSchemaVersion != PiyoDeckManifest.currentDeckSchemaVersion {
      throw PiyoDeckImportError.unsupportedDeckSchemaVersion(manifest.deckSchemaVersion)
    }

    var issues: [ContentValidationIssue] = []
    do {
      let object = try JSONSerialization.jsonObject(with: data)
      guard let root = object as? [String: Any] else {
        throw PiyoDeckImportError.invalidManifest([
          .init(code: "schema.type", path: "$", message: "object 타입이어야 합니다")
        ])
      }
      appendUnknownKeys(
        in: root,
        allowed: ["format", "format_version", "deck_schema_version", "deck"],
        path: "$",
        into: &issues
      )
      if let descriptor = root["deck"] as? [String: Any] {
        appendUnknownKeys(
          in: descriptor,
          allowed: [
            "path", "media_type", "deck_id", "deck_version", "item_count", "size_bytes",
            "sha256",
          ],
          path: "$.deck",
          into: &issues
        )
      }
    } catch let error as PiyoDeckImportError {
      throw error
    } catch {
      throw PiyoDeckImportError.invalidManifest([
        .init(code: "manifest.decode", path: "$", message: String(describing: error))
      ])
    }

    require(
      manifest.deck.path == PiyoDeckManifest.DeckDescriptor.path,
      code: "schema.enum",
      path: "$.deck.path",
      message: "deck.json이어야 합니다",
      into: &issues
    )
    require(
      manifest.deck.mediaType == PiyoDeckManifest.DeckDescriptor.mediaType,
      code: "schema.enum",
      path: "$.deck.media_type",
      message: "application/json이어야 합니다",
      into: &issues
    )
    require(
      manifest.deck.deckId.range(
        of: #"^user_[0-9a-f]{32}$"#,
        options: .regularExpression
      ) != nil,
      code: "schema.pattern",
      path: "$.deck.deck_id",
      message: "사용자 덱 ID 형식과 일치해야 합니다",
      into: &issues
    )
    require(
      manifest.deck.deckVersion >= 1,
      code: "schema.minimum",
      path: "$.deck.deck_version",
      message: "1 이상이어야 합니다",
      into: &issues
    )
    require(
      (1...PiyoDeckPackageLimits.maximumItemCount).contains(manifest.deck.itemCount),
      code: "schema.range",
      path: "$.deck.item_count",
      message: "1...\(PiyoDeckPackageLimits.maximumItemCount) 범위여야 합니다",
      into: &issues
    )
    require(
      (1...PiyoDeckPackageLimits.maximumDeckBytes).contains(manifest.deck.sizeBytes),
      code: "schema.range",
      path: "$.deck.size_bytes",
      message: "1...\(PiyoDeckPackageLimits.maximumDeckBytes) 범위여야 합니다",
      into: &issues
    )
    require(
      manifest.deck.sha256.range(of: #"^[0-9a-f]{64}$"#, options: .regularExpression) != nil,
      code: "schema.pattern",
      path: "$.deck.sha256",
      message: "소문자 SHA-256 형식이어야 합니다",
      into: &issues
    )

    guard issues.isEmpty else { throw PiyoDeckImportError.invalidManifest(issues) }
    return manifest
  }

  private static func appendUnknownKeys(
    in object: [String: Any],
    allowed: Set<String>,
    path: String,
    into issues: inout [ContentValidationIssue]
  ) {
    for key in object.keys where !allowed.contains(key) {
      issues.append(
        .init(
          code: "schema.additionalProperties",
          path: "\(path).\(key)",
          message: "정의되지 않은 필드입니다"
        )
      )
    }
  }

  private static func require(
    _ condition: @autoclosure () -> Bool,
    code: String,
    path: String,
    message: String,
    into issues: inout [ContentValidationIssue]
  ) {
    if !condition() {
      issues.append(.init(code: code, path: path, message: message))
    }
  }
}
