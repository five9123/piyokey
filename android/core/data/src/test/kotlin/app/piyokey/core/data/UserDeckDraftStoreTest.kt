package app.piyokey.core.data

import app.piyokey.core.piyodeck.UserDeckDraft
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserDeckDraftStoreTest {
  private val now = Instant.parse("2026-08-25T04:05:06Z")

  @Test
  fun saveAndReloadRetainDraftIdentityForTheSameFlow() = withStore { root, store ->
    val draft = UserDeckDraft.new(now) { "1".repeat(32) }.copy(defaultLocale = "fr-CA")
    val first = store.save(draft, now)
    val second = store.save(draft.copy(name = "saved"), now.plusSeconds(10))

    assertEquals(first.draftId, second.draftId)
    assertEquals(first.createdAt, second.createdAt)
    assertEquals("saved", UserDeckDraftStore(root).load()?.draft?.name)
    assertEquals("fr-CA", UserDeckDraftStore(root).load()?.draft?.defaultLocale)
  }

  @Test
  fun aDifferentFlowAtomicallyBecomesTheOnlyActiveDraft() = withStore { _, store ->
    val first = store.save(UserDeckDraft.new(now) { "1".repeat(32) }, now)
    val second = store.save(UserDeckDraft.new(now) { "2".repeat(32) }, now.plusSeconds(1))

    assertTrue(first.draftId != second.draftId)
    assertEquals("user_${"2".repeat(32)}", store.load()?.draft?.deckId)
  }

  @Test
  fun corruptPrimaryRecoversLatestValidBackup() = withStore { root, store ->
    val draft = UserDeckDraft.new(now) { "1".repeat(32) }
    store.save(draft, now)
    store.save(draft.copy(name = "new"), now.plusSeconds(1))
    File(root, "active-user-deck-draft.json").writeText("broken")

    val recovered = store.load()
    assertNotNull(recovered)
    assertEquals("", recovered.draft.name)
    assertEquals(recovered, UserDeckDraftStore(root).load())
  }

  @Test
  fun futureSchemaIsPreservedAndNeverReplacedBySave() = withStore { root, store ->
    val primary = File(root, "active-user-deck-draft.json")
    val future = """{"schema_version":99}"""
    primary.writeText(future)

    assertFailsWith<UserDeckDraftStoreException.UnsupportedSchema> { store.load() }
    assertFailsWith<UserDeckDraftStoreException.UnsupportedSchema> {
      store.save(UserDeckDraft.new(now) { "1".repeat(32) }, now)
    }
    assertEquals(future, primary.readText())
  }

  @Test
  fun futureSchemaBackupAlsoBlocksOlderWriter() = withStore { root, store ->
    val draft = UserDeckDraft.new(now) { "1".repeat(32) }
    store.save(draft, now)
    File(root, "active-user-deck-draft.json.backup").writeText("""{"schema_version":99}""")

    assertFailsWith<UserDeckDraftStoreException.UnsupportedSchema> {
      store.save(draft.copy(name = "must not overwrite"), now.plusSeconds(1))
    }
    assertEquals("""{"schema_version":99}""", File(root, "active-user-deck-draft.json.backup").readText())
  }

  @Test
  fun clearOnlyRemovesTheMatchingCommittedDraft() = withStore { _, store ->
    val active = store.save(UserDeckDraft.new(now) { "1".repeat(32) }, now)

    assertFalse(store.clear("draft_${"0".repeat(32)}"))
    assertNotNull(store.load())
    assertTrue(store.clear(active.draftId))
    assertNull(store.load())
  }

  private fun withStore(block: (File, UserDeckDraftStore) -> Unit) {
    val root = Files.createTempDirectory("piyokey-draft-store").toFile()
    try {
      block(root, UserDeckDraftStore(root))
    } finally {
      root.deleteRecursively()
    }
  }
}
