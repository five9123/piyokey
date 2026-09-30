package app.piyokey.android.data.persistence

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Keeps one current backup next to a small JSON persistence file (iOS `RecoverableJSONFile`).
 *
 * A corrupt primary is moved to `<name>.corrupt`, then restored from `<name>.backup` when that
 * backup still passes the caller's decoder and semantic validation. Unsupported future schemas
 * opt out of recovery via [load]'s `shouldRecover`, so an older app never overwrites newer data.
 */
object RecoverableJsonFile {
  fun backupFile(file: File): File = File(file.path + ".backup")

  fun corruptFile(file: File): File = File(file.path + ".corrupt")

  /**
   * Returns the decoded value, `null` when neither primary nor backup exists, or throws the
   * primary's error when nothing valid could be recovered.
   */
  fun <T> load(
    file: File,
    shouldRecover: (Exception) -> Boolean = { true },
    decode: (ByteArray) -> T,
  ): T? {
    val backup = backupFile(file)
    if (!file.exists()) {
      if (!backup.exists()) return null
      try {
        val backupData = backup.readBytes()
        val value = decode(backupData)
        writeAtomically(backupData, file)
        return value
      } catch (error: Exception) {
        if (!shouldRecover(error)) throw error
        runCatching { quarantine(backup) }
        throw error
      }
    }

    try {
      val primaryData = file.readBytes()
      val value = decode(primaryData)
      if (!backup.exists()) runCatching { writeAtomically(primaryData, backup) }
      return value
    } catch (primaryError: Exception) {
      if (!shouldRecover(primaryError)) throw primaryError
      if (!backup.exists()) {
        runCatching { quarantine(file) }
        throw primaryError
      }
      try {
        val backupData = backup.readBytes()
        val value = decode(backupData)
        quarantine(file)
        writeAtomically(backupData, file)
        return value
      } catch (backupError: Exception) {
        runCatching { quarantine(file) }
        if (shouldRecover(backupError)) runCatching { quarantine(backup) }
        throw primaryError
      }
    }
  }

  /** Atomic primary write (the transaction), then a best-effort backup. */
  fun write(data: ByteArray, file: File) {
    writeAtomically(data, file)
    runCatching { writeAtomically(data, backupFile(file)) }
  }

  fun removeArtifacts(file: File) {
    for (candidate in listOf(file, backupFile(file), corruptFile(file))) {
      if (candidate.exists() && !candidate.delete()) throw IOException("Could not delete $candidate")
    }
  }

  /** Writes [data] to a temp file in the same directory, fsyncs, then renames over [file]. */
  fun writeAtomically(data: ByteArray, file: File) {
    val directory = file.absoluteFile.parentFile ?: throw IOException("No parent directory for $file")
    if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
      throw IOException("Could not create $directory")
    }
    val temp = File(directory, ".${file.name}.${UUID.randomUUID()}.tmp")
    try {
      FileOutputStream(temp).use { stream ->
        stream.write(data)
        stream.flush()
        stream.fd.sync()
      }
      try {
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      } catch (_: AtomicMoveNotSupportedException) {
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
      }
    } finally {
      if (temp.exists()) temp.delete()
    }
  }

  private fun quarantine(file: File) {
    if (!file.exists()) return
    val destination = corruptFile(file)
    if (destination.exists() && !destination.delete()) throw IOException("Could not replace $destination")
    Files.move(file.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
  }
}
