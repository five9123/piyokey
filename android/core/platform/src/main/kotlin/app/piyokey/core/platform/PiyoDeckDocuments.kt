package app.piyokey.core.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.piyokey.core.data.PIYODECK_STAGING_DIRECTORY_NAME
import app.piyokey.core.piyodeck.PiyoDeckImportException
import app.piyokey.core.piyodeck.PiyoDeckPackageLimits
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StagedPiyoDeckDocument(
  val file: File,
  val displayName: String,
  val sourceContext: String,
)

class PiyoDeckDocumentGateway(context: Context) {
  private val applicationContext = context.applicationContext
  private val stagingDirectory = File(
    applicationContext.cacheDir,
    PIYODECK_STAGING_DIRECTORY_NAME,
  ).apply { mkdirs() }
  private val pendingStore = PiyoDeckPendingStore(stagingDirectory)

  suspend fun stage(uri: Uri, sourceContext: String): StagedPiyoDeckDocument =
    withContext(Dispatchers.IO) {
      pruneExpiredStaging()
      val file = File(stagingDirectory, "${UUID.randomUUID()}.piyodeck")
      try {
        val input = applicationContext.contentResolver.openInputStream(uri)
          ?: error("The selected document could not be opened.")
        input.use { source ->
          file.outputStream().use { destination ->
            PiyoDeckDocumentStreams.copyBounded(
              source,
              destination,
              PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES,
            )
            destination.fd.sync()
          }
        }
        val document = StagedPiyoDeckDocument(
          file = file,
          displayName = displayName(uri),
          sourceContext = sourceContext,
        )
        pendingStore.record(document)
        document
      } catch (error: Exception) {
        pendingStore.discard(file)
        throw error
      }
    }

  suspend fun recoverPending(): StagedPiyoDeckDocument? = withContext(Dispatchers.IO) {
    pruneExpiredStaging()
    pendingStore.recoverLatest()
  }

  suspend fun writeExport(uri: Uri, packageData: ByteArray) = withContext(Dispatchers.IO) {
    require(packageData.size <= PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES)
    val output = applicationContext.contentResolver.openOutputStream(uri, "wt")
      ?: error("The selected destination could not be opened.")
    output.use { destination ->
      destination.write(packageData)
      destination.flush()
    }
  }

  fun discard(document: StagedPiyoDeckDocument?) {
    document?.let { pendingStore.discard(it.file) }
  }

  private fun displayName(uri: Uri): String {
    val queried = runCatching {
      applicationContext.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
      )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
      }
    }.getOrNull()
    return queried?.takeIf(String::isNotBlank)
      ?.replace(Regex("[\\r\\n]"), " ")
      ?.take(120)
      ?: "deck.piyodeck"
  }

  private fun pruneExpiredStaging(now: Long = System.currentTimeMillis()) {
    val cutoff = now - STAGING_RETENTION_MILLIS
    stagingDirectory.listFiles().orEmpty().forEach { file ->
      if (file.isFile && file.lastModified() < cutoff) file.delete()
    }
  }

  companion object {
    const val MIME_TYPE: String = "application/vnd.piyokey.deck+zip"
    private const val STAGING_RETENTION_MILLIS: Long = 24L * 60L * 60L * 1_000L
  }
}

internal class PiyoDeckPendingStore(private val directory: File) {
  fun record(document: StagedPiyoDeckDocument) {
    require(document.file.parentFile?.canonicalFile == directory.canonicalFile)
    require(document.file.extension == "piyodeck")
    val properties = Properties().apply {
      setProperty(DISPLAY_NAME_KEY, document.displayName.replace(Regex("[\\r\\n]"), " ").take(120))
      setProperty(SOURCE_CONTEXT_KEY, document.sourceContext.replace(Regex("[^a-zA-Z0-9_-]"), "").take(32))
    }
    val target = metadataFile(document.file)
    val temporary = File(directory, "${target.name}.tmp")
    try {
      temporary.outputStream().use { output ->
        properties.store(output, null)
        output.flush()
        output.fd.sync()
      }
      check(temporary.renameTo(target)) { "Could not persist pending import metadata." }
    } finally {
      temporary.delete()
    }
  }

  fun recoverLatest(): StagedPiyoDeckDocument? {
    val packageFiles = directory.listFiles().orEmpty()
      .filter { it.isFile && it.extension == "piyodeck" }
    val candidates = packageFiles.mapNotNull { packageFile ->
      read(packageFile).also { if (it == null) discard(packageFile) }
    }
    val latest = candidates.maxByOrNull { it.file.lastModified() }
    candidates.filterNot { it.file == latest?.file }.forEach { discard(it.file) }
    return latest
  }

  fun discard(packageFile: File) {
    if (packageFile.parentFile?.canonicalFile != directory.canonicalFile) return
    packageFile.delete()
    metadataFile(packageFile).delete()
  }

  private fun read(packageFile: File): StagedPiyoDeckDocument? {
    val metadata = metadataFile(packageFile)
    if (!metadata.isFile) return null
    return runCatching {
      val properties = Properties().apply {
        metadata.inputStream().use(::load)
      }
      val displayName = properties.getProperty(DISPLAY_NAME_KEY)
        ?.takeIf(String::isNotBlank)?.take(120) ?: return null
      val sourceContext = properties.getProperty(SOURCE_CONTEXT_KEY)
        ?.takeIf(String::isNotBlank)?.take(32) ?: return null
      StagedPiyoDeckDocument(packageFile, displayName, sourceContext)
    }.getOrNull()
  }

  private fun metadataFile(packageFile: File): File =
    File(directory, "${packageFile.nameWithoutExtension}.pending")

  private companion object {
    const val DISPLAY_NAME_KEY = "displayName"
    const val SOURCE_CONTEXT_KEY = "sourceContext"
  }
}

internal object PiyoDeckDocumentStreams {
  fun copyBounded(input: InputStream, output: OutputStream, maximumBytes: Int): Int {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
      val read = input.read(buffer)
      if (read < 0) break
      total += read
      if (total > maximumBytes) {
        throw PiyoDeckImportException.PackageTooLarge(total, maximumBytes)
      }
      output.write(buffer, 0, read)
    }
    return total
  }
}
