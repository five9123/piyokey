package app.piyokey.core.platform

import app.piyokey.core.piyodeck.PiyoDeckImportException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import java.nio.file.Files

class PiyoDeckDocumentStreamsTest {
  @Test fun boundedCopyPreservesBytesAtTheLimit() {
    val source = ByteArray(32) { it.toByte() }
    val output = ByteArrayOutputStream()
    assertEquals(32, PiyoDeckDocumentStreams.copyBounded(ByteArrayInputStream(source), output, 32))
    assertContentEquals(source, output.toByteArray())
  }

  @Test fun boundedCopyRejectsBeforeWritingPastTheLimit() {
    val output = ByteArrayOutputStream()
    assertFailsWith<PiyoDeckImportException.PackageTooLarge> {
      PiyoDeckDocumentStreams.copyBounded(ByteArrayInputStream(ByteArray(33)), output, 32)
    }
    assertEquals(0, output.size())
  }

  @Test fun pendingStoreRecoversNewestDocumentAndDiscardsItsSidecar() {
    val directory = Files.createTempDirectory("piyokey-pending").toFile()
    try {
      val store = PiyoDeckPendingStore(directory)
      val older = File(directory, "older.typedeck").apply {
        writeText("older")
        setLastModified(1_000L)
      }
      store.record(StagedPiyoDeckDocument(older, "older.typedeck", "view"))
      val newest = File(directory, "newest.typedeck").apply {
        writeText("newest")
        setLastModified(2_000L)
      }
      store.record(StagedPiyoDeckDocument(newest, "새 덱.typedeck", "send"))

      val recovered = store.recoverLatest()
      assertEquals(newest.canonicalFile, recovered?.file?.canonicalFile)
      assertEquals("새 덱.typedeck", recovered?.displayName)
      assertEquals("send", recovered?.sourceContext)
      assertEquals(false, older.exists())

      store.discard(newest)
      assertNull(store.recoverLatest())
      assertEquals(false, directory.resolve("newest.pending").exists())
    } finally {
      directory.deleteRecursively()
    }
  }
}
