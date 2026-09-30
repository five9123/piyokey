package app.piyokey.android.data.decks

import app.piyokey.android.data.persistence.deleteTree
import app.piyokey.core.deckkit.PiyoDeckPackage
import app.piyokey.core.deckkit.PiyoDeckPackageLimits
import app.piyokey.core.deckkit.PiyoDeckPackageReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

class PiyoDeckFileTooLargeException : IOException("The .typedeck file exceeds ${PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES} bytes")

/**
 * `.typedeck` files received from other apps are first staged in `Hanco/PendingImports/` (iOS
 * `PiyoDeckDocumentService.stageDocument`) so an import survives process death until the user
 * confirms or dismisses it. Blocking file IO.
 */
class PendingImportsStore(val rootDir: File) {
  /** `.partial` files currently being written by [stage]; [pending] must not delete these. */
  private val activePartials = java.util.Collections.synchronizedSet(mutableSetOf<String>())

  /** Copies [input] (≤ 8 MiB) to `<uuid>.typedeck` via a `.partial` file. */
  fun stage(input: InputStream): File {
    if (!rootDir.isDirectory && !rootDir.mkdirs() && !rootDir.isDirectory) throw IOException("Could not create $rootDir")
    val staged = File(rootDir, "${UUID.randomUUID().toString().uppercase()}.typedeck")
    val partial = File(staged.path + ".partial")
    activePartials += partial.canonicalPath
    try {
      partial.outputStream().use { output ->
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
          val count = input.read(buffer)
          if (count < 0) break
          total += count
          if (total > PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES) throw PiyoDeckFileTooLargeException()
          output.write(buffer, 0, count)
        }
        output.fd.sync()
      }
      Files.move(partial.toPath(), staged.toPath(), StandardCopyOption.ATOMIC_MOVE)
      return staged
    } catch (error: Exception) {
      partial.delete()
      staged.delete()
      throw error
    } finally {
      activePartials -= partial.canonicalPath
    }
  }

  /** Staged documents in name order; interrupted `.partial` copies are deleted. */
  fun pending(): List<File> {
    val contents = rootDir.listFiles()?.filter { !it.name.startsWith(".") } ?: return emptyList()
    // A partial that belongs to an in-flight stage() is not an interrupted copy.
    contents.filter { it.extension.equals("partial", ignoreCase = true) && it.canonicalPath !in activePartials }
      .forEach { it.delete() }
    return contents.filter { it.extension.equals("typedeck", ignoreCase = true) && it.exists() }.sortedBy { it.name }
  }

  /** Reads and fully validates a staged package (throws `PiyoDeckImportError` / [PiyoDeckFileTooLargeException]). */
  fun read(file: File, deckSchemaData: ByteArray): PiyoDeckPackage {
    if (file.length() > PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES) throw PiyoDeckFileTooLargeException()
    return PiyoDeckPackageReader.read(file.readBytes(), deckSchemaData)
  }

  fun remove(file: File) {
    if (file.parentFile?.canonicalFile == rootDir.canonicalFile) file.delete()
  }

  fun reset() = rootDir.deleteTree()
}
