package app.piyokey.core.hangul

import java.io.File
import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Runs every case of `shared/test_vectors.json` (PRD §6.4) against the Kotlin engine. */
class SharedTestVectorsTest {
  private data class CompositionCase(
    val name: String,
    val target: String,
    val keySequence: List<Char>,
    val usesShift: List<Char>,
    val nfdLength: Int,
  )

  private data class BackspaceCase(
    val name: String,
    val typeKeys: List<Char>,
    val thenBackspaces: Int,
    val expected: String,
  )

  private val vectors: JsonObject by lazy {
    val root = assertNotNull(System.getProperty("piyokey.sharedRoot"), "piyokey.sharedRoot is not set")
    val file = File(root, "test_vectors.json")
    assertTrue(file.isFile, "Missing ${file.absolutePath}")
    Json.parseToJsonElement(file.readText()).jsonObject
  }

  private fun singleCharacter(value: String): Char {
    assertEquals(1, value.length, "Expected exactly one character: $value")
    return value[0]
  }

  private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

  private fun JsonObject.chars(key: String) = getValue(key).jsonArray.map { singleCharacter(it.jsonPrimitive.content) }

  private val compositionCases: List<CompositionCase> by lazy {
    vectors.getValue("composition_cases").jsonArray.map {
      val o = it.jsonObject
      CompositionCase(
        name = o.string("name"),
        target = o.string("target"),
        keySequence = o.chars("key_sequence"),
        usesShift = o.chars("uses_shift"),
        nfdLength = o.getValue("nfd_length").jsonPrimitive.int,
      )
    }
  }

  private val backspaceCases: List<BackspaceCase> by lazy {
    vectors.getValue("backspace_cases").jsonArray.map {
      val o = it.jsonObject
      BackspaceCase(
        name = o.string("name"),
        typeKeys = o.chars("type_keys"),
        thenBackspaces = o.getValue("then_backspaces").jsonPrimitive.int,
        expected = o.string("expected"),
      )
    }
  }

  @Test
  fun allSharedCompositionCases() {
    assertEquals(15, compositionCases.size)

    for (case in compositionCases) {
      val keys = case.keySequence
      val state = HangulComposer.compose(keys)
      assertEquals(case.target, state.text, case.name)

      assertEquals(keys, JamoDecomposer.keySequence(case.target), "decomposition: ${case.name}")
      val nfd = Normalizer.normalize(case.target, Normalizer.Form.NFD)
      assertEquals(case.nfdLength, nfd.codePointCount(0, nfd.length), "NFD fixture: ${case.name}")

      var judge = JamoJudgeState(case.target)
      for ((index, key) in keys.withIndex()) {
        val evaluation = JamoSequenceJudge.evaluate(key, judge)
        judge = evaluation.state
        val result = evaluation.result as? JamoJudgeResult.Correct
          ?: fail("judge rejected correct input: ${case.name}[$index]")
        assertEquals(index == keys.size - 1, result.completed, case.name)
      }
      assertTrue(judge.isComplete, case.name)
      assertEquals(keys.size, judge.correctCount, case.name)
      assertEquals(0, judge.errorCount, case.name)
      assertEquals(1.0, judge.progress, 0.0001, case.name)

      for (shifted in case.usesShift) {
        assertTrue(JamoDecomposer.isShiftJamo(shifted), "shift metadata: ${case.name}")
        assertTrue(shifted in judge.expectedSequence, "shift sequence: ${case.name}")
      }
    }
  }

  @Test
  fun allSharedBackspaceCases() {
    assertEquals(10, backspaceCases.size)

    for (case in backspaceCases) {
      var state = HangulComposer.compose(case.typeKeys)
      repeat(case.thenBackspaces) { state = HangulComposer.reduce(state, CompositionEvent.Backspace) }
      assertEquals(case.expected, state.text, case.name)
    }
  }

  @Test
  fun osimeConfirmedTextPassesEverySharedCompositionVector() {
    assertEquals(15, compositionCases.size)

    for (case in compositionCases) {
      val evaluation = OSIMETextJudge.evaluate(target = case.target, committedText = case.target)
      assertEquals(OSIMETextJudgeStatus.Matching(completed = true, isComposing = false), evaluation.status, case.name)
      assertEquals(case.keySequence, evaluation.acceptedSequence, case.name)
    }
  }
}
