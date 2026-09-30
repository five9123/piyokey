package app.piyokey.android.data.persistence

import app.piyokey.android.data.DataTestSupport
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecoverableJsonFileTest {
  private class FutureSchema : Exception()

  private val root = DataTestSupport.tempDir()
  private val file = File(root, "nested/state.json")

  private fun decode(data: ByteArray): String {
    val text = data.toString(Charsets.UTF_8)
    if (text == "future") throw FutureSchema()
    require(text.startsWith("ok:")) { "corrupt" }
    return text
  }

  private fun load() = RecoverableJsonFile.load(file, shouldRecover = { it !is FutureSchema }, decode = ::decode)

  @Test
  fun missingFilesLoadNullAndWriteCreatesPrimaryAndBackup() {
    assertNull(load())
    RecoverableJsonFile.write("ok:1".toByteArray(), file)
    assertEquals("ok:1", load())
    assertContentEquals("ok:1".toByteArray(), RecoverableJsonFile.backupFile(file).readBytes())
    assertTrue(root.listFiles()!!.single().listFiles()!!.none { it.name.endsWith(".tmp") })
  }

  @Test
  fun corruptPrimaryIsQuarantinedAndRestoredFromBackup() {
    RecoverableJsonFile.write("ok:1".toByteArray(), file)
    file.writeText("broken")
    assertEquals("ok:1", load())
    assertEquals("ok:1", file.readText())
    assertEquals("broken", RecoverableJsonFile.corruptFile(file).readText())
  }

  @Test
  fun missingPrimaryIsRestoredFromBackupAndBackupSeededFromValidPrimary() {
    RecoverableJsonFile.write("ok:1".toByteArray(), file)
    file.delete()
    assertEquals("ok:1", load())
    assertTrue(file.exists())
    RecoverableJsonFile.backupFile(file).delete()
    assertEquals("ok:1", load())
    assertTrue(RecoverableJsonFile.backupFile(file).exists())
  }

  @Test
  fun unrecoverablePrimaryAndBackupAreBothQuarantined() {
    RecoverableJsonFile.write("ok:1".toByteArray(), file)
    file.writeText("broken-primary")
    RecoverableJsonFile.backupFile(file).writeText("broken-backup")
    assertFailsWith<IllegalArgumentException> { load() }
    assertFalse(file.exists())
    assertFalse(RecoverableJsonFile.backupFile(file).exists())
    assertEquals("broken-backup", RecoverableJsonFile.corruptFile(RecoverableJsonFile.backupFile(file)).readText())
  }

  @Test
  fun futureSchemaIsNeverQuarantinedOrDowngraded() {
    file.parentFile.mkdirs()
    file.writeText("future")
    assertFailsWith<FutureSchema> { load() }
    assertEquals("future", file.readText())
    assertFalse(RecoverableJsonFile.corruptFile(file).exists())

    file.delete()
    RecoverableJsonFile.backupFile(file).writeText("future")
    assertFailsWith<FutureSchema> { load() }
    assertTrue(RecoverableJsonFile.backupFile(file).exists())
    assertFalse(RecoverableJsonFile.corruptFile(RecoverableJsonFile.backupFile(file)).exists())
  }

  @Test
  fun removeArtifactsDeletesPrimaryBackupAndCorruptCopies() {
    RecoverableJsonFile.write("ok:1".toByteArray(), file)
    RecoverableJsonFile.corruptFile(file).writeText("x")
    RecoverableJsonFile.removeArtifacts(file)
    assertFalse(file.exists() || RecoverableJsonFile.backupFile(file).exists() || RecoverableJsonFile.corruptFile(file).exists())
  }
}
