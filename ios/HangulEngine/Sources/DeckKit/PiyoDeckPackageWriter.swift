import CryptoKit
import Foundation

public enum PiyoDeckPackageWriter {
  public static func write(deck: Deck, deckSchemaData: Data? = nil) throws -> Data {
    let userIssues = UserDeckValidator.validate(deck)
    guard userIssues.isEmpty else {
      throw PiyoDeckImportError.invalidUserDeck(userIssues)
    }

    let deckData: Data
    do {
      let encoded = try DeckKitJSON.makeEncoder().encode(deck)
      guard var object = try JSONSerialization.jsonObject(with: encoded) as? [String: Any],
        var items = object["items"] as? [[String: Any]]
      else {
        throw PiyoDeckImportError.invalidJSON(
          name: "deck.json",
          reason: "encoded deck root or items are not JSON objects"
        )
      }
      for index in items.indices where items[index]["audio"] == nil {
        items[index]["audio"] = NSNull()
      }
      object["items"] = items
      deckData = try JSONSerialization.data(
        withJSONObject: object,
        options: [.sortedKeys, .withoutEscapingSlashes]
      )
    } catch let error as PiyoDeckImportError {
      throw error
    } catch {
      throw PiyoDeckImportError.invalidJSON(name: "deck.json", reason: String(describing: error))
    }
    if let deckSchemaData {
      do {
        let schemaIssues = try JSONSchemaValidator.validate(
          instanceData: deckData,
          schemaData: deckSchemaData
        )
        guard schemaIssues.isEmpty else {
          throw PiyoDeckImportError.deckSchemaViolation(schemaIssues)
        }
      } catch let error as PiyoDeckImportError {
        throw error
      } catch {
        throw PiyoDeckImportError.invalidJSON(
          name: "deck.json",
          reason: String(describing: error)
        )
      }
    }
    guard deckData.count <= PiyoDeckPackageLimits.maximumDeckBytes else {
      throw PiyoDeckImportError.entryTooLarge(
        name: "deck.json",
        actual: deckData.count,
        maximum: PiyoDeckPackageLimits.maximumDeckBytes
      )
    }

    let manifest = PiyoDeckManifest(
      deck: .init(
        deckId: deck.deckId,
        deckVersion: deck.version,
        itemCount: deck.items.count,
        sizeBytes: deckData.count,
        sha256: PiyoDeckDigest.sha256Hex(deckData)
      )
    )
    let manifestEncoder = JSONEncoder()
    manifestEncoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
    let manifestData: Data
    do {
      manifestData = try manifestEncoder.encode(manifest)
    } catch {
      throw PiyoDeckImportError.invalidJSON(
        name: "manifest.json",
        reason: String(describing: error)
      )
    }
    guard manifestData.count <= PiyoDeckPackageLimits.maximumManifestBytes else {
      throw PiyoDeckImportError.entryTooLarge(
        name: "manifest.json",
        actual: manifestData.count,
        maximum: PiyoDeckPackageLimits.maximumManifestBytes
      )
    }

    let package = try PiyoDeckZIP.write(
      entries: [
        .init(name: "manifest.json", data: manifestData),
        .init(name: "deck.json", data: deckData),
      ]
    )
    guard package.count <= PiyoDeckPackageLimits.maximumPackageBytes else {
      throw PiyoDeckImportError.packageTooLarge(
        actual: package.count,
        maximum: PiyoDeckPackageLimits.maximumPackageBytes
      )
    }
    return package
  }
}

enum PiyoDeckDigest {
  static func sha256Hex(_ data: Data) -> String {
    SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
  }
}
