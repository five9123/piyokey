package app.piyokey.core.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

internal class AtomicPayloadStore(root: File) {
  private val rootDirectory = root.apply { mkdirs() }
  private val stagingDirectory = File(rootDirectory, "staging").apply { mkdirs() }
  private val backupDirectory = File(rootDirectory, "backup").apply { mkdirs() }
  private val quarantineDirectory = File(rootDirectory, "quarantine").apply { mkdirs() }

  fun deckTargetName(deckId: String): String = "decks/$deckId.json"

  fun catalogTargetName(): String = "catalog/catalog.json"

  fun stage(bytes: ByteArray): String {
    val file = File(stagingDirectory, "${UUID.randomUUID()}.json")
    file.outputStream().use { output ->
      output.write(bytes)
      output.fd.sync()
    }
    return relativeName(file)
  }

  fun makeBackupName(targetName: String): String =
    "backup/${targetName.substringAfterLast('/').removeSuffix(".json")}-${UUID.randomUUID()}.json"

  fun exists(name: String?): Boolean = name != null && resolve(name).isFile

  fun read(name: String): ByteArray = resolve(name).readBytes()

  fun sha256(name: String): String = sha256(read(name))

  fun replace(stagedName: String, targetName: String, backupName: String?) {
    val staged = resolve(stagedName)
    require(staged.isFile) { "Missing staged payload: $stagedName" }
    val target = resolve(targetName)
    target.parentFile?.mkdirs()
    if (target.exists() && backupName != null) {
      val backup = resolve(backupName)
      backup.parentFile?.mkdirs()
      move(target, backup)
    }
    move(staged, target)
  }

  fun moveToBackup(targetName: String, backupName: String) {
    val target = resolve(targetName)
    if (!target.exists()) return
    val backup = resolve(backupName)
    backup.parentFile?.mkdirs()
    move(target, backup)
  }

  fun restoreBackup(backupName: String, targetName: String) {
    val backup = resolve(backupName)
    if (!backup.exists()) return
    val target = resolve(targetName)
    target.parentFile?.mkdirs()
    move(backup, target)
  }

  fun delete(name: String?) {
    if (name != null) resolve(name).delete()
  }

  fun quarantine(name: String) {
    val source = resolve(name)
    if (!source.exists()) return
    val target = File(quarantineDirectory, "${source.nameWithoutExtension}-${UUID.randomUUID()}.json")
    move(source, target)
  }

  private fun resolve(name: String): File {
    require(!name.startsWith('/') && !name.contains("..")) { "Unsafe payload path" }
    val resolved = File(rootDirectory, name).canonicalFile
    require(resolved.path.startsWith(rootDirectory.canonicalPath + File.separator)) {
      "Payload path escaped content root"
    }
    return resolved
  }

  private fun relativeName(file: File): String =
    rootDirectory.canonicalFile.toPath().relativize(file.canonicalFile.toPath()).toString()

  private fun move(source: File, target: File) {
    try {
      Files.move(
        source.toPath(),
        target.toPath(),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING,
      )
    } catch (_: Exception) {
      Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
  }

  companion object {
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
      .digest(bytes)
      .joinToString("") { "%02x".format(it) }
  }
}
