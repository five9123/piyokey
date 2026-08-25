package app.piyokey.core.data

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtomicPayloadStoreTest {
  @Test
  fun replaceAndRestoreKeepEitherOldOrNewPayload() {
    val root = Files.createTempDirectory("piyokey-payload-test").toFile()
    try {
      val store = AtomicPayloadStore(root)
      val target = store.deckTargetName("official_example")
      val firstStage = store.stage("old".toByteArray())
      store.replace(firstStage, target, null)

      val secondStage = store.stage("new".toByteArray())
      val backup = store.makeBackupName(target)
      store.replace(secondStage, target, backup)
      assertContentEquals("new".toByteArray(), store.read(target))
      assertTrue(store.exists(backup))

      store.delete(target)
      store.restoreBackup(backup, target)
      assertContentEquals("old".toByteArray(), store.read(target))
      assertFalse(store.exists(backup))
    } finally {
      root.deleteRecursively()
    }
  }

  @Test
  fun sha256IsStable() {
    assertTrue(AtomicPayloadStore.sha256("piyokey".toByteArray()).matches(Regex("[0-9a-f]{64}")))
  }
}
