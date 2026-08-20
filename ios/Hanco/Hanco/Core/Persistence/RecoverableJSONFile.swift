import Foundation

/// Keeps one current backup next to a small JSON persistence file.
///
/// A corrupt primary is moved to `<name>.corrupt`, then restored from
/// `<name>.backup` when that backup still passes the caller's decoder and
/// semantic validation. Unsupported future schemas can opt out of recovery so
/// an older app never overwrites newer data.
enum RecoverableJSONFile {
  static func backupURL(for fileURL: URL) -> URL {
    fileURL.appendingPathExtension("backup")
  }

  static func corruptURL(for fileURL: URL) -> URL {
    fileURL.appendingPathExtension("corrupt")
  }

  static func load<Value>(
    from fileURL: URL,
    fileManager: FileManager = .default,
    shouldRecover: (Error) -> Bool = { _ in true },
    decode: (Data) throws -> Value
  ) throws -> Value? {
    let backupURL = backupURL(for: fileURL)
    guard fileManager.fileExists(atPath: fileURL.path) else {
      guard fileManager.fileExists(atPath: backupURL.path) else { return nil }
      do {
        let backupData = try Data(contentsOf: backupURL)
        let value = try decode(backupData)
        try restore(backupData, to: fileURL, fileManager: fileManager)
        return value
      } catch {
        guard shouldRecover(error) else { throw error }
        try? quarantine(backupURL, fileManager: fileManager)
        throw error
      }
    }

    do {
      let primaryData = try Data(contentsOf: fileURL)
      let value = try decode(primaryData)
      if !fileManager.fileExists(atPath: backupURL.path) {
        try? primaryData.write(to: backupURL, options: .atomic)
      }
      return value
    } catch {
      let primaryError = error
      guard shouldRecover(primaryError) else { throw primaryError }

      guard fileManager.fileExists(atPath: backupURL.path) else {
        try? quarantine(fileURL, fileManager: fileManager)
        throw primaryError
      }

      do {
        let backupData = try Data(contentsOf: backupURL)
        let value = try decode(backupData)
        try quarantine(fileURL, fileManager: fileManager)
        try restore(backupData, to: fileURL, fileManager: fileManager)
        return value
      } catch let backupError {
        try? quarantine(fileURL, fileManager: fileManager)
        if shouldRecover(backupError) {
          try? quarantine(backupURL, fileManager: fileManager)
        }
        throw primaryError
      }
    }
  }

  static func write(
    _ data: Data,
    to fileURL: URL,
    fileManager: FileManager = .default
  ) throws {
    try fileManager.createDirectory(
      at: fileURL.deletingLastPathComponent(),
      withIntermediateDirectories: true
    )
    try data.write(to: fileURL, options: .atomic)

    // The primary write is the transaction. A backup failure must not turn a
    // successfully persisted user action into a visible save error.
    try? data.write(to: backupURL(for: fileURL), options: .atomic)
  }

  static func removeArtifacts(
    for fileURL: URL,
    fileManager: FileManager = .default
  ) throws {
    for url in [fileURL, backupURL(for: fileURL), corruptURL(for: fileURL)]
    where fileManager.fileExists(atPath: url.path) {
      try fileManager.removeItem(at: url)
    }
  }

  private static func restore(
    _ data: Data,
    to fileURL: URL,
    fileManager: FileManager
  ) throws {
    try fileManager.createDirectory(
      at: fileURL.deletingLastPathComponent(),
      withIntermediateDirectories: true
    )
    try data.write(to: fileURL, options: .atomic)
  }

  private static func quarantine(
    _ fileURL: URL,
    fileManager: FileManager
  ) throws {
    guard fileManager.fileExists(atPath: fileURL.path) else { return }
    let destination = corruptURL(for: fileURL)
    if fileManager.fileExists(atPath: destination.path) {
      try fileManager.removeItem(at: destination)
    }
    try fileManager.moveItem(at: fileURL, to: destination)
  }
}
