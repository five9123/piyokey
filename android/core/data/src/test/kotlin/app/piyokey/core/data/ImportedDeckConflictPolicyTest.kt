package app.piyokey.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportedDeckConflictPolicyTest {
  private val installed = ImportedDeckVersionSummary(3, "a".repeat(64))

  @Test fun classifiesEveryFreeImportConflictWithoutMerging() {
    assertEquals(
      ImportedDeckConflict.NEW,
      ImportedDeckConflictPolicy.classify(ImportedDeckVersionSummary(1, "b".repeat(64)), null),
    )
    assertEquals(
      ImportedDeckConflict.IDENTICAL,
      ImportedDeckConflictPolicy.classify(ImportedDeckVersionSummary(99, installed.contentSha256), installed),
    )
    assertEquals(
      ImportedDeckConflict.NEWER_VERSION,
      ImportedDeckConflictPolicy.classify(ImportedDeckVersionSummary(4, "b".repeat(64)), installed),
    )
    assertEquals(
      ImportedDeckConflict.OLDER_VERSION,
      ImportedDeckConflictPolicy.classify(ImportedDeckVersionSummary(2, "b".repeat(64)), installed),
    )
    assertEquals(
      ImportedDeckConflict.SAME_VERSION_DIFFERENT_CONTENT,
      ImportedDeckConflictPolicy.classify(ImportedDeckVersionSummary(3, "b".repeat(64)), installed),
    )
  }

  @Test fun onlyDifferentInstalledContentRequiresDestructiveConfirmation() {
    assertFalse(ImportedDeckConflictPolicy.requiresDestructiveConfirmation(ImportedDeckConflict.NEW))
    assertFalse(ImportedDeckConflictPolicy.requiresDestructiveConfirmation(ImportedDeckConflict.IDENTICAL))
    assertTrue(ImportedDeckConflictPolicy.requiresDestructiveConfirmation(ImportedDeckConflict.NEWER_VERSION))
    assertTrue(ImportedDeckConflictPolicy.requiresDestructiveConfirmation(ImportedDeckConflict.OLDER_VERSION))
    assertTrue(
      ImportedDeckConflictPolicy.requiresDestructiveConfirmation(
        ImportedDeckConflict.SAME_VERSION_DIFFERENT_CONTENT,
      ),
    )
  }
}
