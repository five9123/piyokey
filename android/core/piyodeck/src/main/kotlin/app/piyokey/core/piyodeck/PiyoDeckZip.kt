package app.piyokey.core.piyodeck

import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

internal data class PiyoDeckZipEntry(val name: String, val data: ByteArray)

internal object PiyoDeckZip {
  private const val LOCAL_FILE_HEADER_SIGNATURE: Long = 0x0403_4B50L
  private const val CENTRAL_DIRECTORY_SIGNATURE: Long = 0x0201_4B50L
  private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE: Long = 0x0605_4B50L
  private const val UTF8_FLAG: Int = 0x0800
  private const val STORE_METHOD: Int = 0
  private const val ZIP_VERSION: Int = 20
  private const val DOS_TIME: Int = 0
  private const val DOS_DATE: Int = 0x0021
  private val requiredEntryNames = setOf("manifest.json", "deck.json")

  fun read(archive: ByteArray): Map<String, ByteArray> {
    if (archive.size > PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES) {
      throw PiyoDeckImportException.PackageTooLarge(
        archive.size,
        PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES,
      )
    }
    val endRecordSize = 22
    if (archive.size < endRecordSize) malformed("missing end-of-central-directory record")
    val endOffset = archive.size - endRecordSize
    if (archive.uint32(endOffset) != END_OF_CENTRAL_DIRECTORY_SIGNATURE) {
      malformed("archive comments, trailing data, or a missing end record")
    }

    val diskNumber = archive.uint16(endOffset + 4)
    val centralDirectoryDisk = archive.uint16(endOffset + 6)
    val entriesOnDisk = archive.uint16(endOffset + 8)
    val totalEntries = archive.uint16(endOffset + 10)
    val centralDirectorySize = archive.uint32(endOffset + 12)
    val centralDirectoryOffset = archive.uint32(endOffset + 16)
    val archiveCommentLength = archive.uint16(endOffset + 20)

    if (diskNumber == 0xFFFF || centralDirectoryDisk == 0xFFFF ||
      entriesOnDisk == 0xFFFF || totalEntries == 0xFFFF ||
      centralDirectorySize == 0xFFFF_FFFFL || centralDirectoryOffset == 0xFFFF_FFFFL
    ) {
      unsupported("ZIP64")
    }
    if (diskNumber != 0 || centralDirectoryDisk != 0) unsupported("split archive")
    if (archiveCommentLength != 0) unsupported("archive comment")
    if (entriesOnDisk != totalEntries) unsupported("multi-disk central directory")
    if (totalEntries != 2) throw PiyoDeckImportException.InvalidEntrySet(emptyList())
    if (centralDirectoryOffset > endOffset.toLong() ||
      centralDirectorySize != endOffset.toLong() - centralDirectoryOffset
    ) {
      malformed("invalid central-directory bounds")
    }

    val centralStart = centralDirectoryOffset.toInt()
    var cursor = centralStart
    val records = mutableListOf<CentralRecord>()
    repeat(totalEntries) {
      ensureRange(cursor, 46, endOffset)
      if (archive.uint32(cursor) != CENTRAL_DIRECTORY_SIGNATURE) {
        malformed("invalid central-directory entry")
      }
      val versionMadeBy = archive.uint16(cursor + 4)
      val versionNeeded = archive.uint16(cursor + 6)
      val flags = archive.uint16(cursor + 8)
      val method = archive.uint16(cursor + 10)
      val crc32 = archive.uint32(cursor + 16)
      val compressedSize = archive.uint32(cursor + 20)
      val uncompressedSize = archive.uint32(cursor + 24)
      val nameLength = archive.uint16(cursor + 28)
      val extraLength = archive.uint16(cursor + 30)
      val commentLength = archive.uint16(cursor + 32)
      val diskStart = archive.uint16(cursor + 34)
      val externalAttributes = archive.uint32(cursor + 38)
      val localHeaderOffset = archive.uint32(cursor + 42)

      if (versionNeeded > ZIP_VERSION) unsupported("ZIP version $versionNeeded")
      if (compressedSize == 0xFFFF_FFFFL || uncompressedSize == 0xFFFF_FFFFL ||
        localHeaderOffset == 0xFFFF_FFFFL
      ) {
        unsupported("ZIP64")
      }
      if (flags and UTF8_FLAG.inv() != 0) {
        unsupported(if (flags and 0x0001 != 0) "encryption" else "general-purpose flag 0x${flags.toString(16)}")
      }
      if (method != STORE_METHOD) unsupported("compression method $method")
      if (compressedSize != uncompressedSize) malformed("STORE entry sizes differ")
      if (extraLength != 0) unsupported("entry extra field")
      if (commentLength != 0) unsupported("entry comment")
      if (diskStart != 0) unsupported("split archive entry")

      val hostSystem = versionMadeBy ushr 8
      val unixFileType = ((externalAttributes ushr 16) and 0xF000).toInt()
      if (hostSystem == 3 || hostSystem == 19) {
        when (unixFileType) {
          0xA000 -> unsupported("symbolic link")
          0x4000 -> unsupported("directory entry")
          0, 0x8000 -> Unit
          else -> unsupported("non-regular entry")
        }
      }
      if (externalAttributes and 0x10L != 0L) unsupported("directory entry")

      val nameOffset = checkedEnd(cursor, 46, endOffset)
      val nameEnd = checkedEnd(nameOffset, nameLength, endOffset)
      val nameData = archive.copyOfRange(nameOffset, nameEnd)
      val name = decodeEntryName(nameData)
      records += CentralRecord(
        name = name,
        nameData = nameData,
        versionNeeded = versionNeeded,
        flags = flags,
        method = method,
        crc32 = crc32,
        compressedSize = compressedSize.toInt(),
        uncompressedSize = uncompressedSize.toInt(),
        localHeaderOffset = localHeaderOffset,
      )
      cursor = nameEnd
    }
    if (cursor != endOffset) malformed("unused central-directory bytes")

    val names = records.map { it.name }
    if (names.toSet() != requiredEntryNames || names.toSet().size != names.size) {
      throw PiyoDeckImportException.InvalidEntrySet(names)
    }

    val payloads = linkedMapOf<String, ByteArray>()
    val occupiedRanges = mutableListOf<IntRange>()
    records.forEach { record ->
      val maximum = if (record.name == "manifest.json") {
        PiyoDeckPackageLimits.MAXIMUM_MANIFEST_BYTES
      } else {
        PiyoDeckPackageLimits.MAXIMUM_DECK_BYTES
      }
      if (record.uncompressedSize > maximum) {
        throw PiyoDeckImportException.EntryTooLarge(record.name, record.uncompressedSize, maximum)
      }
      if (record.localHeaderOffset > Int.MAX_VALUE.toLong()) malformed("record exceeds archive bounds")
      val offset = record.localHeaderOffset.toInt()
      ensureRange(offset, 30, centralStart)
      if (archive.uint32(offset) != LOCAL_FILE_HEADER_SIGNATURE) {
        malformed("missing local header for ${record.name}")
      }
      val localVersionNeeded = archive.uint16(offset + 4)
      val localFlags = archive.uint16(offset + 6)
      val localMethod = archive.uint16(offset + 8)
      val localCrc32 = archive.uint32(offset + 14)
      val localCompressedSize = archive.uint32(offset + 18)
      val localUncompressedSize = archive.uint32(offset + 22)
      val localNameLength = archive.uint16(offset + 26)
      val localExtraLength = archive.uint16(offset + 28)
      if (localVersionNeeded > ZIP_VERSION) unsupported("ZIP version $localVersionNeeded")
      if (localFlags and UTF8_FLAG.inv() != 0) {
        unsupported(
          if (localFlags and 0x0001 != 0) {
            "encryption"
          } else {
            "general-purpose flag 0x${localFlags.toString(16)}"
          },
        )
      }
      if (localMethod != STORE_METHOD) unsupported("compression method $localMethod")
      if (localCompressedSize == 0xFFFF_FFFFL || localUncompressedSize == 0xFFFF_FFFFL) {
        unsupported("ZIP64")
      }
      if (localCompressedSize != localUncompressedSize) {
        malformed("STORE entry sizes differ for ${record.name}")
      }
      if (localVersionNeeded != record.versionNeeded ||
        localFlags != record.flags ||
        localMethod != record.method ||
        localCrc32 != record.crc32 ||
        localCompressedSize != record.compressedSize.toLong() ||
        localUncompressedSize != record.uncompressedSize.toLong()
      ) {
        malformed("local and central headers differ for ${record.name}")
      }
      if (localExtraLength != 0) unsupported("local entry extra field")
      val localNameOffset = checkedEnd(offset, 30, centralStart)
      val localNameEnd = checkedEnd(localNameOffset, localNameLength, centralStart)
      val localNameData = archive.copyOfRange(localNameOffset, localNameEnd)
      if (!localNameData.contentEquals(record.nameData)) {
        malformed("local and central paths differ for ${record.name}")
      }
      val dataEnd = checkedEnd(localNameEnd, record.compressedSize, centralStart)
      val payload = archive.copyOfRange(localNameEnd, dataEnd)
      payloads[record.name] = payload
      occupiedRanges += offset until dataEnd
    }

    var expectedOffset = 0
    occupiedRanges.sortedBy { it.first }.forEach { range ->
      if (range.first != expectedOffset || range.last + 1 < range.first) {
        malformed("overlapping entries, hidden bytes, or an archive preamble")
      }
      expectedOffset = range.last + 1
    }
    if (expectedOffset != centralStart) {
      malformed("hidden bytes before the central directory")
    }
    records.forEach { record ->
      val payload = payloads.getValue(record.name)
      if (crc32(payload) != record.crc32) {
        throw PiyoDeckImportException.CrcMismatch(record.name)
      }
    }
    return payloads
  }

  fun write(entries: List<PiyoDeckZipEntry>): ByteArray {
    val names = entries.map { it.name }
    if (names != listOf("manifest.json", "deck.json")) {
      throw PiyoDeckImportException.InvalidEntrySet(names)
    }
    val output = ByteAccumulator()
    val centralRecords = mutableListOf<WriterCentralRecord>()
    entries.forEach { entry ->
      val nameData = entry.name.toByteArray(StandardCharsets.US_ASCII)
      validateEntryName(entry.name, nameData)
      val localOffset = output.size
      val checksum = crc32(entry.data)
      output.uint32(LOCAL_FILE_HEADER_SIGNATURE)
      output.uint16(ZIP_VERSION)
      output.uint16(UTF8_FLAG)
      output.uint16(STORE_METHOD)
      output.uint16(DOS_TIME)
      output.uint16(DOS_DATE)
      output.uint32(checksum)
      output.uint32(entry.data.size.toLong())
      output.uint32(entry.data.size.toLong())
      output.uint16(nameData.size)
      output.uint16(0)
      output.bytes(nameData)
      output.bytes(entry.data)
      centralRecords += WriterCentralRecord(nameData, checksum, entry.data.size, localOffset)
    }

    val centralOffset = output.size
    centralRecords.forEach { record ->
      output.uint32(CENTRAL_DIRECTORY_SIGNATURE)
      output.uint16(ZIP_VERSION)
      output.uint16(ZIP_VERSION)
      output.uint16(UTF8_FLAG)
      output.uint16(STORE_METHOD)
      output.uint16(DOS_TIME)
      output.uint16(DOS_DATE)
      output.uint32(record.crc32)
      output.uint32(record.size.toLong())
      output.uint32(record.size.toLong())
      output.uint16(record.nameData.size)
      output.uint16(0)
      output.uint16(0)
      output.uint16(0)
      output.uint16(0)
      output.uint32(0)
      output.uint32(record.localHeaderOffset.toLong())
      output.bytes(record.nameData)
    }
    val centralSize = output.size - centralOffset
    output.uint32(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
    output.uint16(0)
    output.uint16(0)
    output.uint16(entries.size)
    output.uint16(entries.size)
    output.uint32(centralSize.toLong())
    output.uint32(centralOffset.toLong())
    output.uint16(0)
    val packageData = output.toByteArray()
    if (packageData.size > PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES) {
      throw PiyoDeckImportException.PackageTooLarge(
        packageData.size,
        PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES,
      )
    }
    return packageData
  }

  private fun decodeEntryName(data: ByteArray): String {
    val name = try {
      StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(java.nio.ByteBuffer.wrap(data))
        .toString()
    } catch (error: Exception) {
      throw PiyoDeckImportException.UnsafeEntryPath("<invalid UTF-8>")
    }
    validateEntryName(name, data)
    return name
  }

  private fun validateEntryName(name: String, data: ByteArray) {
    if (data.any { it.toInt() and 0x80 != 0 } ||
      name.isEmpty() || name == "." || name == ".." ||
      '/' in name || '\\' in name || ':' in name || '\u0000' in name
    ) {
      throw PiyoDeckImportException.UnsafeEntryPath(name)
    }
    if (!name.toByteArray(StandardCharsets.US_ASCII).contentEquals(data)) {
      throw PiyoDeckImportException.UnsafeEntryPath(name)
    }
  }

  private fun ensureRange(start: Int, length: Int, limit: Int) {
    if (start < 0 || length < 0 || start > limit || length > limit - start) {
      malformed("record exceeds archive bounds")
    }
  }

  private fun checkedEnd(start: Int, length: Int, limit: Int): Int {
    ensureRange(start, length, limit)
    return start + length
  }

  private fun crc32(data: ByteArray): Long = CRC32().apply { update(data) }.value

  private fun malformed(reason: String): Nothing =
    throw PiyoDeckImportException.MalformedArchive(reason)

  private fun unsupported(feature: String): Nothing =
    throw PiyoDeckImportException.UnsupportedArchiveFeature(feature)

  private data class CentralRecord(
    val name: String,
    val nameData: ByteArray,
    val versionNeeded: Int,
    val flags: Int,
    val method: Int,
    val crc32: Long,
    val compressedSize: Int,
    val uncompressedSize: Int,
    val localHeaderOffset: Long,
  )

  private data class WriterCentralRecord(
    val nameData: ByteArray,
    val crc32: Long,
    val size: Int,
    val localHeaderOffset: Int,
  )
}

private fun ByteArray.uint16(offset: Int): Int {
  if (offset < 0 || offset > size - 2) {
    throw PiyoDeckImportException.MalformedArchive("truncated 16-bit field")
  }
  return (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
}

private fun ByteArray.uint32(offset: Int): Long {
  if (offset < 0 || offset > size - 4) {
    throw PiyoDeckImportException.MalformedArchive("truncated 32-bit field")
  }
  return (this[offset].toLong() and 0xFF) or
    ((this[offset + 1].toLong() and 0xFF) shl 8) or
    ((this[offset + 2].toLong() and 0xFF) shl 16) or
    ((this[offset + 3].toLong() and 0xFF) shl 24)
}

private class ByteAccumulator {
  private var bytes = ByteArray(1_024)
  var size: Int = 0
    private set

  fun uint16(value: Int) {
    require(value in 0..0xFFFF)
    ensureCapacity(2)
    bytes[size++] = value.toByte()
    bytes[size++] = (value ushr 8).toByte()
  }

  fun uint32(value: Long) {
    if (value !in 0..0xFFFF_FFFFL) {
      throw PiyoDeckImportException.UnsupportedArchiveFeature("ZIP64")
    }
    ensureCapacity(4)
    bytes[size++] = value.toByte()
    bytes[size++] = (value ushr 8).toByte()
    bytes[size++] = (value ushr 16).toByte()
    bytes[size++] = (value ushr 24).toByte()
  }

  fun bytes(value: ByteArray) {
    ensureCapacity(value.size)
    value.copyInto(bytes, destinationOffset = size)
    size += value.size
  }

  fun toByteArray(): ByteArray = bytes.copyOf(size)

  private fun ensureCapacity(additional: Int) {
    if (additional < 0 || size > Int.MAX_VALUE - additional) {
      throw PiyoDeckImportException.UnsupportedArchiveFeature("ZIP64")
    }
    val required = size + additional
    if (required <= bytes.size) return
    var capacity = bytes.size
    while (capacity < required) {
      val next = (capacity * 2L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
      if (next <= capacity) throw PiyoDeckImportException.UnsupportedArchiveFeature("ZIP64")
      capacity = next
    }
    bytes = bytes.copyOf(capacity)
  }
}
