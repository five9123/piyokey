package app.piyokey.android.feature.library

import app.piyokey.android.data.DataTestSupport
import app.piyokey.android.data.decks.PendingImportsStore
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckAuthor
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.DeckType
import app.piyokey.core.deckkit.PiyoDeckPackageLimits
import app.piyokey.core.deckkit.PiyoDeckPackageWriter
import app.piyokey.core.domain.PiyoDeckDocumentNotice
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** Port of iOS `PiyoDeckDocumentFlowTests` coordinator cases (queue, notices, resume). */
@OptIn(ExperimentalCoroutinesApi::class)
class PiyoDeckImportCoordinatorTest {
  private val schema = File(DataTestSupport.sharedRoot, "schema/deck.schema.json").readBytes()
  private val root: File = Files.createTempDirectory("pending-imports").toFile()

  private fun deck(hex: String) = Deck(
    deckId = "user_$hex",
    version = 1,
    name = "テスト",
    author = DeckAuthor("user_local", "ピヨ"),
    official = false,
    type = DeckType.WORD,
    level = 1,
    tags = listOf("テスト"),
    createdAt = Instant.ofEpochSecond(1),
    updatedAt = Instant.ofEpochSecond(2),
    items = listOf(DeckItem("item_$hex", "사과", "サグァ", "りんご", audio = null)),
  )

  private fun bytes(hex: String) = PiyoDeckPackageWriter.write(deck(hex), schema)

  private fun TestScope.coordinator() =
    PiyoDeckImportCoordinator(PendingImportsStore(root), { schema }, this, StandardTestDispatcher(testScheduler))

  @Test
  fun repeatedDismissDoesNotSkipQueuedDocument() = runTest {
    val coordinator = coordinator()
    coordinator.receive { ByteArrayInputStream(bytes("1".repeat(32))) }
    coordinator.receive { ByteArrayInputStream(bytes("2".repeat(32))) }
    assertEquals("user_" + "1".repeat(32), coordinator.candidate.value?.pkg?.deck?.deckId)
    assertTrue(coordinator.hasPendingDocument.value)

    coordinator.markAlreadyImported()
    coordinator.dismissCandidate() // a second dismissal callback is a no-op
    assertNull(coordinator.candidate.value)
    assertEquals(PiyoDeckDocumentNotice.ALREADY_IMPORTED, coordinator.notice.value)
    coordinator.dismissNotice()
    advanceUntilIdle()
    assertEquals("user_" + "2".repeat(32), coordinator.candidate.value?.pkg?.deck?.deckId)
    coordinator.dismissCandidate(showNext = false)
    assertFalse(coordinator.hasPendingDocument.value)
    assertTrue(PendingImportsStore(root).pending().isEmpty())
  }

  @Test
  fun invalidPendingDocumentContinuesWithNextAfterNoticeDismissal() = runTest {
    root.mkdirs()
    File(root, "01-invalid.typedeck").writeText("not-a-package")
    File(root, "02-valid.typedeck").writeBytes(bytes("a".repeat(32)))
    File(root, "03.typedeck.partial").writeText("interrupted")
    val coordinator = coordinator()
    coordinator.resumePendingIfNeeded()
    assertNull(coordinator.candidate.value)
    assertEquals(PiyoDeckDocumentNotice.INVALID_DOCUMENT, coordinator.notice.value)
    assertFalse(File(root, "03.typedeck.partial").exists())

    coordinator.dismissNotice()
    advanceUntilIdle()
    assertEquals(deck("a".repeat(32)), coordinator.candidate.value?.pkg?.deck)
    coordinator.dismissCandidate(showNext = false)
  }

  @Test
  fun concurrentOpenRequestsAreQueuedWithoutDroppingEitherDocument() = runTest {
    val coordinator = coordinator()
    launch { coordinator.receive { ByteArrayInputStream(bytes("3".repeat(32))) } }
    launch { coordinator.receive { ByteArrayInputStream(bytes("4".repeat(32))) } }
    advanceUntilIdle()
    val first = coordinator.candidate.value?.pkg?.deck?.deckId
    assertTrue(first == "user_" + "3".repeat(32) || first == "user_" + "4".repeat(32))
    coordinator.dismissCandidate()
    advanceUntilIdle()
    val second = coordinator.candidate.value?.pkg?.deck?.deckId
    assertEquals(setOf("user_" + "3".repeat(32), "user_" + "4".repeat(32)), setOf(first, second))
    assertFalse(coordinator.isReading.value)
  }

  @Test
  fun oversizedAndUnreadableDocumentsBecomeNotices() = runTest {
    val coordinator = coordinator()
    coordinator.receive { ByteArrayInputStream(ByteArray(PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES + 1)) }
    assertEquals(PiyoDeckDocumentNotice.PACKAGE_TOO_LARGE, coordinator.notice.value)
    assertTrue(PendingImportsStore(root).pending().isEmpty())
    coordinator.dismissNotice()
    coordinator.receive { null }
    assertEquals(PiyoDeckDocumentNotice.CANNOT_READ, coordinator.notice.value)
    coordinator.dismissNotice()
    val future = File(DataTestSupport.sharedRoot, "piyodeck/fixtures/invalid/future-version.typedeck")
    coordinator.receive { future.inputStream() }
    assertEquals(PiyoDeckDocumentNotice.UNSUPPORTED_VERSION, coordinator.notice.value)
  }

  @Test
  fun finishImportShowsImportedNotice() = runTest {
    val coordinator = coordinator()
    coordinator.receive { File(DataTestSupport.sharedRoot, "piyodeck/fixtures/valid/basic.typedeck").inputStream() }
    val candidate = coordinator.candidate.value!!
    assertTrue(candidate.stagedFile.exists())
    coordinator.finishImport()
    assertFalse(candidate.stagedFile.exists())
    assertEquals(PiyoDeckDocumentNotice.IMPORTED, coordinator.notice.value)
  }
}
