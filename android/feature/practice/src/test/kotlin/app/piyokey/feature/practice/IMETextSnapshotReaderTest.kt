package app.piyokey.feature.practice

import kotlin.test.Test
import kotlin.test.assertEquals

class IMETextSnapshotReaderTest {
  @Test
  fun separatesCommittedAndComposingText() {
    assertEquals(IMETextSnapshot("가", "나"), IMETextSnapshotReader.read("가나", 1, 2))
  }

  @Test
  fun returnsAllTextWhenNothingIsComposing() {
    assertEquals(IMETextSnapshot("가나", null), IMETextSnapshotReader.read("가나", -1, -1))
  }
}
