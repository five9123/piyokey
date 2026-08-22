import Foundation

struct PiyoDeckZIPEntry: Equatable, Sendable {
  let name: String
  let data: Data
}

enum PiyoDeckZIP {
  private static let localFileHeaderSignature: UInt32 = 0x0403_4B50
  private static let centralDirectoryHeaderSignature: UInt32 = 0x0201_4B50
  private static let endOfCentralDirectorySignature: UInt32 = 0x0605_4B50
  private static let utf8Flag: UInt16 = 0x0800
  private static let storeMethod: UInt16 = 0
  private static let minimumDOSDate: UInt16 = 0x0021
  private static let requiredEntryNames: Set<String> = ["manifest.json", "deck.json"]

  static func write(entries: [PiyoDeckZIPEntry]) throws -> Data {
    let names = entries.map(\.name)
    guard names == ["manifest.json", "deck.json"]
    else {
      throw PiyoDeckImportError.invalidEntrySet(names)
    }

    struct CentralRecord {
      let nameData: Data
      let crc32: UInt32
      let size: UInt32
      let localHeaderOffset: UInt32
    }

    var result = Data()
    var centralRecords: [CentralRecord] = []

    for entry in entries {
      guard isSafeEntryPath(entry.name), let nameData = entry.name.data(using: .utf8) else {
        throw PiyoDeckImportError.unsafeEntryPath(entry.name)
      }
      guard entry.data.count <= Int(UInt32.max), result.count <= Int(UInt32.max),
        nameData.count <= Int(UInt16.max)
      else {
        throw PiyoDeckImportError.unsupportedArchiveFeature("ZIP64")
      }

      let crc32 = PiyoDeckCRC32.checksum(entry.data)
      let size = UInt32(entry.data.count)
      let offset = UInt32(result.count)

      result.appendLittleEndian(localFileHeaderSignature)
      result.appendLittleEndian(UInt16(20))
      result.appendLittleEndian(utf8Flag)
      result.appendLittleEndian(storeMethod)
      result.appendLittleEndian(UInt16(0))
      result.appendLittleEndian(minimumDOSDate)
      result.appendLittleEndian(crc32)
      result.appendLittleEndian(size)
      result.appendLittleEndian(size)
      result.appendLittleEndian(UInt16(nameData.count))
      result.appendLittleEndian(UInt16(0))
      result.append(nameData)
      result.append(entry.data)

      centralRecords.append(
        CentralRecord(nameData: nameData, crc32: crc32, size: size, localHeaderOffset: offset)
      )
    }

    guard result.count <= Int(UInt32.max) else {
      throw PiyoDeckImportError.unsupportedArchiveFeature("ZIP64")
    }
    let centralDirectoryOffset = UInt32(result.count)

    for record in centralRecords {
      result.appendLittleEndian(centralDirectoryHeaderSignature)
      result.appendLittleEndian(UInt16(20))
      result.appendLittleEndian(UInt16(20))
      result.appendLittleEndian(utf8Flag)
      result.appendLittleEndian(storeMethod)
      result.appendLittleEndian(UInt16(0))
      result.appendLittleEndian(minimumDOSDate)
      result.appendLittleEndian(record.crc32)
      result.appendLittleEndian(record.size)
      result.appendLittleEndian(record.size)
      result.appendLittleEndian(UInt16(record.nameData.count))
      result.appendLittleEndian(UInt16(0))
      result.appendLittleEndian(UInt16(0))
      result.appendLittleEndian(UInt16(0))
      result.appendLittleEndian(UInt16(0))
      result.appendLittleEndian(UInt32(0))
      result.appendLittleEndian(record.localHeaderOffset)
      result.append(record.nameData)
    }

    let centralDirectorySize = result.count - Int(centralDirectoryOffset)
    guard centralDirectorySize <= Int(UInt32.max) else {
      throw PiyoDeckImportError.unsupportedArchiveFeature("ZIP64")
    }
    result.appendLittleEndian(endOfCentralDirectorySignature)
    result.appendLittleEndian(UInt16(0))
    result.appendLittleEndian(UInt16(0))
    result.appendLittleEndian(UInt16(centralRecords.count))
    result.appendLittleEndian(UInt16(centralRecords.count))
    result.appendLittleEndian(UInt32(centralDirectorySize))
    result.appendLittleEndian(centralDirectoryOffset)
    result.appendLittleEndian(UInt16(0))
    return result
  }

  static func read(_ archive: Data) throws -> [String: Data] {
    let endRecordSize = 22
    guard archive.count >= endRecordSize else {
      throw PiyoDeckImportError.malformedArchive("missing end-of-central-directory record")
    }
    let endOffset = archive.count - endRecordSize
    guard try archive.littleEndianUInt32(at: endOffset) == endOfCentralDirectorySignature else {
      throw PiyoDeckImportError.malformedArchive(
        "archive comments, trailing data, or a missing end record"
      )
    }

    let diskNumber = try archive.littleEndianUInt16(at: endOffset + 4)
    let centralDirectoryDisk = try archive.littleEndianUInt16(at: endOffset + 6)
    let entriesOnDisk = try archive.littleEndianUInt16(at: endOffset + 8)
    let totalEntries = try archive.littleEndianUInt16(at: endOffset + 10)
    let centralDirectorySize = Int(try archive.littleEndianUInt32(at: endOffset + 12))
    let centralDirectoryOffset = Int(try archive.littleEndianUInt32(at: endOffset + 16))
    let archiveCommentLength = try archive.littleEndianUInt16(at: endOffset + 20)

    guard diskNumber == 0, centralDirectoryDisk == 0 else {
      throw PiyoDeckImportError.unsupportedArchiveFeature("split archive")
    }
    guard archiveCommentLength == 0 else {
      throw PiyoDeckImportError.unsupportedArchiveFeature("archive comment")
    }
    guard entriesOnDisk == totalEntries else {
      throw PiyoDeckImportError.unsupportedArchiveFeature("multi-disk central directory")
    }
    guard totalEntries == 2 else {
      throw PiyoDeckImportError.invalidEntrySet([])
    }
    guard centralDirectoryOffset >= 0, centralDirectorySize >= 0,
      centralDirectoryOffset <= endOffset,
      centralDirectorySize == endOffset - centralDirectoryOffset
    else {
      throw PiyoDeckImportError.malformedArchive("invalid central-directory bounds")
    }

    struct CentralRecord {
      let name: String
      let nameData: Data
      let versionNeeded: UInt16
      let flags: UInt16
      let method: UInt16
      let crc32: UInt32
      let compressedSize: Int
      let uncompressedSize: Int
      let localHeaderOffset: Int
    }

    var cursor = centralDirectoryOffset
    var records: [CentralRecord] = []
    for _ in 0..<Int(totalEntries) {
      guard try archive.littleEndianUInt32(at: cursor) == centralDirectoryHeaderSignature else {
        throw PiyoDeckImportError.malformedArchive("invalid central-directory entry")
      }
      let versionMadeBy = try archive.littleEndianUInt16(at: cursor + 4)
      let versionNeeded = try archive.littleEndianUInt16(at: cursor + 6)
      let flags = try archive.littleEndianUInt16(at: cursor + 8)
      let method = try archive.littleEndianUInt16(at: cursor + 10)
      let crc32 = try archive.littleEndianUInt32(at: cursor + 16)
      let compressedSize32 = try archive.littleEndianUInt32(at: cursor + 20)
      let uncompressedSize32 = try archive.littleEndianUInt32(at: cursor + 24)
      let nameLength = Int(try archive.littleEndianUInt16(at: cursor + 28))
      let extraLength = Int(try archive.littleEndianUInt16(at: cursor + 30))
      let commentLength = Int(try archive.littleEndianUInt16(at: cursor + 32))
      let diskStart = try archive.littleEndianUInt16(at: cursor + 34)
      let externalAttributes = try archive.littleEndianUInt32(at: cursor + 38)
      let localHeaderOffset32 = try archive.littleEndianUInt32(at: cursor + 42)

      if versionNeeded > 20 {
        throw PiyoDeckImportError.unsupportedArchiveFeature("ZIP version \(versionNeeded)")
      }
      if compressedSize32 == UInt32.max || uncompressedSize32 == UInt32.max
        || localHeaderOffset32 == UInt32.max
      {
        throw PiyoDeckImportError.unsupportedArchiveFeature("ZIP64")
      }
      if flags & ~utf8Flag != 0 {
        let feature = flags & 0x0001 != 0 ? "encryption" : "general-purpose flag 0x\(hex(flags))"
        throw PiyoDeckImportError.unsupportedArchiveFeature(feature)
      }
      guard method == storeMethod else {
        throw PiyoDeckImportError.unsupportedArchiveFeature("compression method \(method)")
      }
      guard extraLength == 0 else {
        throw PiyoDeckImportError.unsupportedArchiveFeature("entry extra field")
      }
      guard commentLength == 0 else {
        throw PiyoDeckImportError.unsupportedArchiveFeature("entry comment")
      }
      guard diskStart == 0 else {
        throw PiyoDeckImportError.unsupportedArchiveFeature("split archive entry")
      }
      let hostSystem = UInt8(truncatingIfNeeded: versionMadeBy >> 8)
      let unixFileType = UInt16(truncatingIfNeeded: externalAttributes >> 16) & 0xF000
      if (hostSystem == 3 || hostSystem == 19) && unixFileType == 0xA000 {
        throw PiyoDeckImportError.unsupportedArchiveFeature("symbolic link")
      }
      if (hostSystem == 3 || hostSystem == 19) && unixFileType == 0x4000 {
        throw PiyoDeckImportError.unsupportedArchiveFeature("directory entry")
      }
      if (hostSystem == 3 || hostSystem == 19) && unixFileType != 0 && unixFileType != 0x8000 {
        throw PiyoDeckImportError.unsupportedArchiveFeature("non-regular entry")
      }
      if externalAttributes & 0x10 != 0 {
        throw PiyoDeckImportError.unsupportedArchiveFeature("directory entry")
      }

      let headerSize = 46
      let nameOffset = try checkedEnd(cursor, headerSize, limit: endOffset)
      let entryEnd = try checkedEnd(nameOffset, nameLength, limit: endOffset)
      let nameData = archive.subdata(in: nameOffset..<entryEnd)
      guard let name = String(data: nameData, encoding: .utf8), name.data(using: .utf8) == nameData
      else {
        throw PiyoDeckImportError.unsafeEntryPath("<invalid UTF-8>")
      }
      guard isSafeEntryPath(name) else {
        throw PiyoDeckImportError.unsafeEntryPath(name)
      }
      let compressedSize = Int(compressedSize32)
      let uncompressedSize = Int(uncompressedSize32)
      guard compressedSize == uncompressedSize else {
        throw PiyoDeckImportError.malformedArchive("STORE entry sizes differ for \(name)")
      }
      records.append(
        CentralRecord(
          name: name,
          nameData: nameData,
          versionNeeded: versionNeeded,
          flags: flags,
          method: method,
          crc32: crc32,
          compressedSize: compressedSize,
          uncompressedSize: uncompressedSize,
          localHeaderOffset: Int(localHeaderOffset32)
        )
      )
      cursor = entryEnd
    }
    guard cursor == endOffset else {
      throw PiyoDeckImportError.malformedArchive("unused central-directory bytes")
    }

    let names = records.map(\.name)
    guard Set(names) == requiredEntryNames, Set(names).count == names.count else {
      throw PiyoDeckImportError.invalidEntrySet(names)
    }

    var payloads: [String: Data] = [:]
    var occupiedRanges: [(start: Int, end: Int)] = []
    for record in records {
      let maximum =
        record.name == "manifest.json"
        ? PiyoDeckPackageLimits.maximumManifestBytes
        : PiyoDeckPackageLimits.maximumDeckBytes
      guard record.uncompressedSize <= maximum else {
        throw PiyoDeckImportError.entryTooLarge(
          name: record.name,
          actual: record.uncompressedSize,
          maximum: maximum
        )
      }
      let offset = record.localHeaderOffset
      guard try archive.littleEndianUInt32(at: offset) == localFileHeaderSignature else {
        throw PiyoDeckImportError.malformedArchive("missing local header for \(record.name)")
      }
      let localVersionNeeded = try archive.littleEndianUInt16(at: offset + 4)
      let localFlags = try archive.littleEndianUInt16(at: offset + 6)
      let localMethod = try archive.littleEndianUInt16(at: offset + 8)
      let localCRC32 = try archive.littleEndianUInt32(at: offset + 14)
      let localCompressedSize = Int(try archive.littleEndianUInt32(at: offset + 18))
      let localUncompressedSize = Int(try archive.littleEndianUInt32(at: offset + 22))
      let localNameLength = Int(try archive.littleEndianUInt16(at: offset + 26))
      let localExtraLength = Int(try archive.littleEndianUInt16(at: offset + 28))

      guard localVersionNeeded == record.versionNeeded, localFlags == record.flags,
        localMethod == record.method, localCRC32 == record.crc32,
        localCompressedSize == record.compressedSize,
        localUncompressedSize == record.uncompressedSize
      else {
        throw PiyoDeckImportError.malformedArchive(
          "local and central headers differ for \(record.name)"
        )
      }
      guard localExtraLength == 0 else {
        throw PiyoDeckImportError.unsupportedArchiveFeature("local entry extra field")
      }
      let localNameOffset = try checkedEnd(offset, 30, limit: centralDirectoryOffset)
      let localNameEnd = try checkedEnd(
        localNameOffset, localNameLength, limit: centralDirectoryOffset)
      let localNameData = archive.subdata(in: localNameOffset..<localNameEnd)
      guard localNameData == record.nameData else {
        throw PiyoDeckImportError.malformedArchive(
          "local and central paths differ for \(record.name)"
        )
      }
      let dataEnd = try checkedEnd(
        localNameEnd, record.compressedSize, limit: centralDirectoryOffset)
      let payload = archive.subdata(in: localNameEnd..<dataEnd)
      payloads[record.name] = payload
      occupiedRanges.append((start: offset, end: dataEnd))
    }

    let sortedRanges = occupiedRanges.sorted { $0.start < $1.start }
    var expectedOffset = 0
    for range in sortedRanges {
      guard range.start == expectedOffset, range.end >= range.start else {
        throw PiyoDeckImportError.malformedArchive(
          "overlapping entries, hidden bytes, or an archive preamble"
        )
      }
      expectedOffset = range.end
    }
    guard expectedOffset == centralDirectoryOffset else {
      throw PiyoDeckImportError.malformedArchive("hidden bytes before the central directory")
    }
    for record in records {
      guard let payload = payloads[record.name], PiyoDeckCRC32.checksum(payload) == record.crc32
      else {
        throw PiyoDeckImportError.crcMismatch(name: record.name)
      }
    }
    return payloads
  }

  private static func checkedEnd(_ start: Int, _ length: Int, limit: Int) throws -> Int {
    guard start >= 0, length >= 0, start <= limit, length <= limit - start else {
      throw PiyoDeckImportError.malformedArchive("record exceeds archive bounds")
    }
    return start + length
  }

  private static func isSafeEntryPath(_ name: String) -> Bool {
    guard !name.isEmpty, name.unicodeScalars.allSatisfy({ $0.value < 0x80 }) else { return false }
    guard !name.contains("/"), !name.contains("\\"), !name.contains(":"), !name.contains("\0")
    else { return false }
    return name != "." && name != ".."
  }

  private static func hex(_ value: UInt16) -> String {
    String(value, radix: 16, uppercase: false)
  }
}

enum PiyoDeckCRC32 {
  private static let table: [UInt32] = (0..<256).map { index in
    var value = UInt32(index)
    for _ in 0..<8 {
      value = value & 1 == 1 ? 0xEDB8_8320 ^ (value >> 1) : value >> 1
    }
    return value
  }

  static func checksum(_ data: Data) -> UInt32 {
    var crc: UInt32 = 0xFFFF_FFFF
    for byte in data {
      let index = Int((crc ^ UInt32(byte)) & 0xFF)
      crc = table[index] ^ (crc >> 8)
    }
    return crc ^ 0xFFFF_FFFF
  }
}

extension Data {
  fileprivate mutating func appendLittleEndian(_ value: UInt16) {
    append(UInt8(truncatingIfNeeded: value))
    append(UInt8(truncatingIfNeeded: value >> 8))
  }

  fileprivate mutating func appendLittleEndian(_ value: UInt32) {
    append(UInt8(truncatingIfNeeded: value))
    append(UInt8(truncatingIfNeeded: value >> 8))
    append(UInt8(truncatingIfNeeded: value >> 16))
    append(UInt8(truncatingIfNeeded: value >> 24))
  }

  fileprivate func littleEndianUInt16(at offset: Int) throws -> UInt16 {
    guard offset >= 0, offset <= count - 2 else {
      throw PiyoDeckImportError.malformedArchive("truncated 16-bit field")
    }
    let index = startIndex + offset
    return UInt16(self[index]) | UInt16(self[index + 1]) << 8
  }

  fileprivate func littleEndianUInt32(at offset: Int) throws -> UInt32 {
    guard offset >= 0, offset <= count - 4 else {
      throw PiyoDeckImportError.malformedArchive("truncated 32-bit field")
    }
    let index = startIndex + offset
    return UInt32(self[index]) | UInt32(self[index + 1]) << 8
      | UInt32(self[index + 2]) << 16 | UInt32(self[index + 3]) << 24
  }
}
