package app.piyokey.core.hangul

import java.nio.file.Files
import java.nio.file.Path
import java.text.Normalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedTestVectorsTest {
  @Test
  fun allSharedCompositionCasesPass() {
    val vectors = loadVectors()
    assertEquals(15, vectors.compositionCases.size)

    vectors.compositionCases.forEach { testCase ->
      val keys = testCase.keySequence.map(::singleCharacter)
      val state = HangulComposer.compose(keys)
      assertEquals(testCase.target, state.text, testCase.name)

      val decomposed = JamoDecomposer.keySequenceFor(testCase.target)
      assertEquals(keys, decomposed, "decomposition: ${testCase.name}")
      val nfd = Normalizer.normalize(testCase.target, Normalizer.Form.NFD)
      assertEquals(testCase.nfdLength, nfd.codePointCount(0, nfd.length), "NFD: ${testCase.name}")

      var judge = JamoJudgeState.forTarget(testCase.target)
      keys.forEachIndexed { index, key ->
        val evaluation = JamoSequenceJudge.evaluate(key, judge)
        judge = evaluation.state
        assertEquals(
          JamoJudgeResult.Correct(completed = index == keys.lastIndex),
          evaluation.result,
          "judge: ${testCase.name}[$index]",
        )
      }
      assertTrue(judge.isComplete, testCase.name)
      assertEquals(keys.size, judge.correctCount, testCase.name)
      assertEquals(0, judge.errorCount, testCase.name)
      assertEquals(1.0, judge.progress, 0.0001, testCase.name)

      testCase.usesShift.map(::singleCharacter).forEach { shifted ->
        assertTrue(JamoDecomposer.isShiftJamo(shifted), "shift metadata: ${testCase.name}")
        assertTrue(shifted in judge.expectedSequence, "shift sequence: ${testCase.name}")
      }
    }
  }

  @Test
  fun allSharedBackspaceCasesPass() {
    val vectors = loadVectors()
    assertEquals(10, vectors.backspaceCases.size)

    vectors.backspaceCases.forEach { testCase ->
      var state = HangulComposer.compose(testCase.typeKeys.map(::singleCharacter))
      repeat(testCase.thenBackspaces) {
        state = HangulComposer.reduce(state, CompositionEvent.Backspace)
      }
      assertEquals(testCase.expected, state.text, testCase.name)
    }
  }

  private fun loadVectors(): SharedTestVectors {
    val configuredPath = checkNotNull(System.getProperty("piyokey.sharedTestVectors")) {
      "Gradle must provide piyokey.sharedTestVectors"
    }
    val root = Json.parseToJsonElement(Files.readString(Path.of(configuredPath))).jsonObject
    return SharedTestVectors(
      compositionCases = root.requiredArray("composition_cases").map { item ->
        val value = item.jsonObject
        CompositionCase(
          name = value.requiredString("name"),
          target = value.requiredString("target"),
          keySequence = value.requiredStrings("key_sequence"),
          usesShift = value.requiredStrings("uses_shift"),
          nfdLength = value.requiredInt("nfd_length"),
        )
      },
      backspaceCases = root.requiredArray("backspace_cases").map { item ->
        val value = item.jsonObject
        BackspaceCase(
          name = value.requiredString("name"),
          typeKeys = value.requiredStrings("type_keys"),
          thenBackspaces = value.requiredInt("then_backspaces"),
          expected = value.requiredString("expected"),
        )
      },
    )
  }

  private fun singleCharacter(value: String): Char {
    require(value.length == 1) { "Expected exactly one character: $value" }
    return value.single()
  }
}

private data class SharedTestVectors(
  val compositionCases: List<CompositionCase>,
  val backspaceCases: List<BackspaceCase>,
)

private data class CompositionCase(
  val name: String,
  val target: String,
  val keySequence: List<String>,
  val usesShift: List<String>,
  val nfdLength: Int,
)

private data class BackspaceCase(
  val name: String,
  val typeKeys: List<String>,
  val thenBackspaces: Int,
  val expected: String,
)

private fun JsonObject.requiredArray(key: String): JsonArray =
  checkNotNull(this[key]) { "Missing $key" }.jsonArray

private fun JsonObject.requiredString(key: String): String =
  checkNotNull(this[key]) { "Missing $key" }.jsonPrimitive.content

private fun JsonObject.requiredInt(key: String): Int =
  checkNotNull(this[key]) { "Missing $key" }.jsonPrimitive.int

private fun JsonObject.requiredStrings(key: String): List<String> =
  requiredArray(key).map { it.jsonPrimitive.content }
