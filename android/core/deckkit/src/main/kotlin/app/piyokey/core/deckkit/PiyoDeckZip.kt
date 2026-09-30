package app.piyokey.core.deckkit

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.CRC32

internal class PiyoDeckZipEntry(val name: String, val data: ByteArray)

/** Strict STORE-only ZIP container used by `.typedeck` v1 (SPEC §1, §5 steps 2-5). */
internal object PiyoDeckZip {
  private const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034B50L
  private const val CENTRAL_DIRECTORY_HEADER_SIGNATURE = 0x02014B50L
  private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054B50L
  private const val UTF8_FLAG = 0x0800
  private const val STORE_METHOD = 0
  private const val MINIMUM_DOS_DATE = 0x0021
  private const val UINT32_MAX = 0xFFFFFFFFL
  private const val UINT16_MAX = 0xFFFF
  private val requiredEntryNames = setOf("manifest.json", "deck.json")

  @Throws(PiyoDeckImportError::class)
  fun write(entries: List<PiyoDeckZipEntry>): ByteArray {
    val names = entries.map { it.name }
    if (names != listOf("manifest.json", "deck.json")) {
      throw PiyoDeckImportError.InvalidEntrySet(names)
    }

    class CentralRecord(val nameData: ByteArray, val crc32: Long, val size: Long, val offset: Long)

    val result = ByteArrayOutputStream()
    val centralRecords = mutableListOf<CentralRecord>()

    for (entry in entries) {
      if (!isSafeEntryPath(entry.name)) throw PiyoDeckImportError.UnsafeEntryPath(entry.name)
      val nameData = entry.name.toByteArray(Charsets.UTF_8)
      if (entry.data.size.toLong() > UINT32_MAX || result.size().toLong() > UINT32_MAX ||
        nameData.size > UINT16_MAX
      ) {
        throw PiyoDeckImportError.UnsupportedArchiveFeature("ZIP64")
      }
      val crc32 = PiyoDeckCrc32.checksum(entry.data)
      val size = entry.data.size.toLong()
      val offset = result.size().toLong()

      result.u32(LOCAL_FILE_HEADER_SIGNATURE)
      result.u16(20)
      result.u16(UTF8_FLAG)
      result.u16(STORE_METHOD)
      result.u16(0)
      result.u16(MINIMUM_DOS_DATE)
      result.u32(crc32)
      result.u32(size)
      result.u32(size)
      result.u16(nameData.size)
      result.u16(0)
      result.write(nameData)
      result.write(entry.data)

      centralRecords.add(CentralRecord(nameData, crc32, size, offset))
    }

    if (result.size().toLong() > UINT32_MAX) {
      throw PiyoDeckImportError.UnsupportedArchiveFeature("ZIP64")
    }
    val centralDirectoryOffset = result.size().toLong()

    for (record in centralRecords) {
      result.u32(CENTRAL_DIRECTORY_HEADER_SIGNATURE)
      result.u16(20)
      result.u16(20)
      result.u16(UTF8_FLAG)
      result.u16(STORE_METHOD)
      result.u16(0)
      result.u16(MINIMUM_DOS_DATE)
      result.u32(record.crc32)
      result.u32(record.size)
      result.u32(record.size)
      result.u16(record.nameData.size)
      result.u16(0)
      result.u16(0)
      result.u16(0)
      result.u16(0)
      result.u32(0)
      result.u32(record.offset)
      result.write(record.nameData)
    }

    val centralDirectorySize = result.size().toLong() - centralDirectoryOffset
    if (centralDirectorySize > UINT32_MAX) {
      throw PiyoDeckImportError.UnsupportedArchiveFeature("ZIP64")
    }
    result.u32(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
    result.u16(0)
    result.u16(0)
    result.u16(centralRecords.size)
    result.u16(centralRecords.size)
    result.u32(centralDirectorySize)
    result.u32(centralDirectoryOffset)
    result.u16(0)
    return result.toByteArray()
  }

  @Throws(PiyoDeckImportError::class)
  fun read(archive: ByteArray): Map<String, ByteArray> {
    val endRecordSize = 22
    if (archive.size < endRecordSize) {
      throw PiyoDeckImportError.MalformedArchive("missing end-of-central-directory record")
    }
    val endOffset = archive.size - endRecordSize
    if (archive.u32(endOffset) != END_OF_CENTRAL_DIRECTORY_SIGNATURE) {
      throw PiyoDeckImportError.MalformedArchive(
        "archive comments, trailing data, or a missing end record",
      )
    }

    val diskNumber = archive.u16(endOffset + 4)
    val centralDirectoryDisk = archive.u16(endOffset + 6)
    val entriesOnDisk = archive.u16(endOffset + 8)
    val totalEntries = archive.u16(endOffset + 10)
    val centralDirectorySize = archive.u32(endOffset + 12)
    val centralDirectoryOffset = archive.u32(endOffset + 16)
    val archiveCommentLength = archive.u16(endOffset + 20)

    if (diskNumber != 0 || centralDirectoryDisk != 0) {
      throw PiyoDeckImportError.UnsupportedArchiveFeature("split archive")
    }
    if (archiveCommentLength != 0) {
      throw PiyoDeckImportError.UnsupportedArchiveFeature("archive comment")
    }
    if (entriesOnDisk != totalEntries) {
      throw PiyoDeckImportError.UnsupportedArchiveFeature("multi-disk central directory")
    }
    if (totalEntries != 2) throw PiyoDeckImportError.InvalidEntrySet(emptyList())
    if (centralDirectoryOffset > endOffset ||
      centralDirectorySize != endOffset - centralDirectoryOffset
    ) {
      throw PiyoDeckImportError.MalformedArchive("invalid central-directory bounds")
    }
    val cdOffset = centralDirectoryOffset.toInt()

    class CentralRecord(
      val name: String,
      val nameData: ByteArray,
      val versionNeeded: Int,
      val flags: Int,
      val method: Int,
      val crc32: Long,
      val compressedSize: Long,
      val uncompressedSize: Long,
      val localHeaderOffset: Long,
    )

    var cursor = cdOffset
    val records = mutableListOf<CentralRecord>()
    repeat(totalEntries) {
      if (archive.u32(cursor) != CENTRAL_DIRECTORY_HEADER_SIGNATURE) {
        throw PiyoDeckImportError.MalformedArchive("invalid central-directory entry")
      }
      val versionMadeBy = archive.u16(cursor + 4)
      val versionNeeded = archive.u16(cursor + 6)
      val flags = archive.u16(cursor + 8)
      val method = archive.u16(cursor + 10)
      val crc32 = archive.u32(cursor + 16)
      val compressedSize32 = archive.u32(cursor + 20)
      val uncompressedSize32 = archive.u32(cursor + 24)
      val nameLength = archive.u16(cursor + 28)
      val extraLength = archive.u16(cursor + 30)
      val commentLength = archive.u16(cursor + 32)
      val diskStart = archive.u16(cursor + 34)
      val externalAttributes = archive.u32(cursor + 38)
      val localHeaderOffset32 = archive.u32(cursor + 42)

      if (versionNeeded > 20) {
        throw PiyoDeckImportError.UnsupportedArchiveFeature("ZIP version $versionNeeded")
      }
      if (compressedSize32 == UINT32_MAX || uncompressedSize32 == UINT32_MAX ||
        localHeaderOffset32 == UINT32_MAX
      ) {
        throw PiyoDeckImportError.UnsupportedArchiveFeature("ZIP64")
      }
      if (flags and UTF8_FLAG.inv() and 0xFFFF != 0) {
        val feature =
          if (flags and 0x0001 != 0) "encryption"
          else "general-purpose flag 0x${flags.toString(16)}"
        throw PiyoDeckImportError.UnsupportedArchiveFeature(feature)
      }
      if (method != STORE_METHOD) {
        throw PiyoDeckImportError.UnsupportedArchiveFeature("compression method $method")
      }
      if (extraLength != 0) throw PiyoDeckImportError.UnsupportedArchiveFeature("entry extra field")
      if (commentLength != 0) throw PiyoDeckImportError.UnsupportedArchiveFeature("entry comment")
      if (diskStart != 0) throw PiyoDeckImportError.UnsupportedArchiveFeature("split archive entry")
      val hostSystem = (versionMadeBy shr 8) and 0xFF
      val unixFileType = ((externalAttributes shr 16) and 0xFFFF).toInt() and 0xF000
      val unixHost = hostSystem == 3 || hostSystem == 19
      if (unixHost && unixFileType == 0xA000) {
        throw PiyoDeckImportError.UnsupportedArchiveFeature("symbolic link")
      }
      if (unixHost && unixFileType == 0x4000) {
        throw PiyoDeckImportError.UnsupportedArchiveFeature("directory entry")
      }
      if (unixHost && unixFileType != 0 && unixFileType != 0x8000) {
        throw PiyoDeckImportError.UnsupportedArchiveFeature("non-regular entry")
      }
      if (externalAttributes and 0x10L != 0L) {
        throw PiyoDeckImportError.UnsupportedArchiveFeature("directory entry")
      }

      val headerSize = 46
      val nameOffset = checkedEnd(cursor, headerSize, endOffset)
      val entryEnd = checkedEnd(nameOffset, nameLength, endOffset)
      val nameData = archive.copyOfRange(nameOffset, entryEnd)
      val name = PiyoDeckStrictJson.decodeUtf8Strict(nameData)
        ?.takeIf { it.toByteArray(Charsets.UTF_8).contentEquals(nameData) }
        ?: throw PiyoDeckImportError.UnsafeEntryPath("<invalid UTF-8>")
      if (!isSafeEntryPath(name)) throw PiyoDeckImportError.UnsafeEntryPath(name)
      if (compressedSize32 != uncompressedSize32) {
        throw PiyoDeckImportError.MalformedArchive("STORE entry sizes differ for $name")
      }
      records.add(
        CentralRecord(
          name, nameData, versionNeeded, flags, method, crc32, compressedSize32,
          uncompressedSize32, localHeaderOffset32,
        ),
      )
      cursor = entryEnd
    }
    if (cursor != endOffset) {
      throw PiyoDeckImportError.MalformedArchive("unused central-directory bytes")
    }

    val names = records.map { it.name }
    if (names.toSet() != requiredEntryNames || names.toSet().size != names.size) {
      throw PiyoDeckImportError.InvalidEntrySet(names)
    }

    val payloads = LinkedHashMap<String, ByteArray>()
    val occupiedRanges = mutableListOf<Pair<Int, Int>>()
    for (record in records) {
      val maximum =
        if (record.name == "manifest.json") PiyoDeckPackageLimits.MAXIMUM_MANIFEST_BYTES
        else PiyoDeckPackageLimits.MAXIMUM_DECK_BYTES
      if (record.uncompressedSize > maximum) {
        throw PiyoDeckImportError.EntryTooLarge(
          record.name, record.uncompressedSize.toInt(), maximum,
        )
      }
      if (record.localHeaderOffset > Int.MAX_VALUE) {
        throw PiyoDeckImportError.MalformedArchive("truncated 32-bit field")
      }
      val offset = record.localHeaderOffset.toInt()
      if (archive.u32(offset) != LOCAL_FILE_HEADER_SIGNATURE) {
        throw PiyoDeckImportError.MalformedArchive("missing local header for ${record.name}")
      }
      val localVersionNeeded = archive.u16(offset + 4)
      val localFlags = archive.u16(offset + 6)
      val localMethod = archive.u16(offset + 8)
      val localCrc32 = archive.u32(offset + 14)
      val localCompressedSize = archive.u32(offset + 18)
      val localUncompressedSize = archive.u32(offset + 22)
      val localNameLength = archive.u16(offset + 26)
      val localExtraLength = archive.u16(offset + 28)

      if (localVersionNeeded != record.versionNeeded || localFlags != record.flags ||
        localMethod != record.method || localCrc32 != record.crc32 ||
        localCompressedSize != record.compressedSize ||
        localUncompressedSize != record.uncompressedSize
      ) {
        throw PiyoDeckImportError.MalformedArchive(
          "local and central headers differ for ${record.name}",
        )
      }
      if (localExtraLength != 0) {
        throw PiyoDeckImportError.UnsupportedArchiveFeature("local entry extra field")
      }
      val localNameOffset = checkedEnd(offset, 30, cdOffset)
      val localNameEnd = checkedEnd(localNameOffset, localNameLength, cdOffset)
      val localNameData = archive.copyOfRange(localNameOffset, localNameEnd)
      if (!localNameData.contentEquals(record.nameData)) {
        throw PiyoDeckImportError.MalformedArchive(
          "local and central paths differ for ${record.name}",
        )
      }
      val dataEnd = checkedEnd(localNameEnd, record.compressedSize.toInt(), cdOffset)
      payloads[record.name] = archive.copyOfRange(localNameEnd, dataEnd)
      occupiedRanges.add(offset to dataEnd)
    }

    var expectedOffset = 0
    for ((start, end) in occupiedRanges.sortedBy { it.first }) {
      if (start != expectedOffset || end < start) {
        throw PiyoDeckImportError.MalformedArchive(
          "overlapping entries, hidden bytes, or an archive preamble",
        )
      }
      expectedOffset = end
    }
    if (expectedOffset != cdOffset) {
      throw PiyoDeckImportError.MalformedArchive("hidden bytes before the central directory")
    }
    for (record in records) {
      val payload = payloads[record.name]
      if (payload == null || PiyoDeckCrc32.checksum(payload) != record.crc32) {
        throw PiyoDeckImportError.CrcMismatch(record.name)
      }
    }
    return payloads
  }

  private fun checkedEnd(start: Int, length: Int, limit: Int): Int {
    if (start < 0 || length < 0 || start > limit || length > limit - start) {
      throw PiyoDeckImportError.MalformedArchive("record exceeds archive bounds")
    }
    return start + length
  }

  fun isSafeEntryPath(name: String): Boolean {
    if (name.isEmpty() || name.any { it.code >= 0x80 }) return false
    if (name.contains('/') || name.contains('\\') || name.contains(':') || name.contains('\u0000')) {
      return false
    }
    return name != "." && name != ".."
  }

  private fun ByteArrayOutputStream.u16(value: Int) {
    write(value and 0xFF)
    write((value shr 8) and 0xFF)
  }

  private fun ByteArrayOutputStream.u32(value: Long) {
    write((value and 0xFF).toInt())
    write(((value shr 8) and 0xFF).toInt())
    write(((value shr 16) and 0xFF).toInt())
    write(((value shr 24) and 0xFF).toInt())
  }

  private fun ByteArray.u16(offset: Int): Int {
    if (offset < 0 || offset > size - 2) {
      throw PiyoDeckImportError.MalformedArchive("truncated 16-bit field")
    }
    return (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
  }

  private fun ByteArray.u32(offset: Int): Long {
    if (offset < 0 || offset > size - 4) {
      throw PiyoDeckImportError.MalformedArchive("truncated 32-bit field")
    }
    return (this[offset].toLong() and 0xFF) or
      ((this[offset + 1].toLong() and 0xFF) shl 8) or
      ((this[offset + 2].toLong() and 0xFF) shl 16) or
      ((this[offset + 3].toLong() and 0xFF) shl 24)
  }
}

/** Standard ZIP CRC-32 (IEEE 802.3, reflected 0xEDB88320). */
internal object PiyoDeckCrc32 {
  fun checksum(data: ByteArray): Long = CRC32().apply { update(data) }.value
}

internal object PiyoDeckDigest {
  fun sha256Hex(data: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
}
