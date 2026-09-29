# :core:hangul

Pure Kotlin/JVM port of the iOS `HangulEngine` package (`ios/HangulEngine`) and the iOS app's
pure 10-key interpreter/flick code (`ios/Hanco/Hanco/Features/Practice/HangulKeyboardView.swift`).
There are no Android or Compose dependencies. Package: `app.piyokey.core.hangul`.

Everything is immutable. Each "mutating" call returns a new value, so keep the latest value in
your ViewModel state. The spec lives in PRD §6, F2 and F2a. Shared vectors are in `shared/test_vectors.json`.

```
cd android && ./gradlew :core:hangul:check   # tests + jacoco (>=95% line, >=90% branch)
```

## Types at a glance

| Swift | Kotlin |
|---|---|
| `Character` jamo | `Char` (all Hangul jamo and syllables are in the BMP) |
| `enum` with payloads | `sealed interface` + `data class` / `data object` |
| `throws` | throws `JamoDecompositionError` (a sealed `Exception`) |
| `(state:, result:)` tuple | `JamoJudgeEvaluation`, `Korean10KeyStep`, `Korean10KeyBackspaceStep` |
| `mutating func` | returns a new value |

## 1. Composition preview (dubeolsik keys)

```kotlin
var composition = CompositionState()                               // empty
composition = HangulComposer.reduce(composition, CompositionEvent.Key('ㄱ'))
composition = HangulComposer.reduce(composition, CompositionEvent.Backspace) // jamo-level undo

composition.text          // String: committedText + composingText (show this in the preview)
composition.committedText // String: finished part
composition.composingText // String: syllable still composing ("" when none)
composition.phase         // CompositionPhase.EMPTY | CHO | JUNG | CHO_JUNG | CHO_JUNG_JONG

HangulComposer.compose(keys: Iterable<Char>): CompositionState
HangulComposer.compose(keys: CharSequence): CompositionState      // compose("ㄱㅏㄴㅏ").text == "가나"
```

- Shift+jamo is fed as the resulting jamo, for example `Key('ㄲ')` or `Key('ㅒ')`. One key counts as one event.
- Non-jamo keys (space, digits, punctuation) flush the buffer and are appended literally.
- Dokkaebi carry-over and compound vowels/finals are automatic: `ㄷㅏㄹㄱㅏ` becomes `달가`.
- `Backspace` restores the exact state before the last key. For example, `가나` becomes `간`. Extra backspaces on an empty state do nothing.

## 2. Judging progress against a target (built-in keyboard)

```kotlin
var judge = JamoJudgeState(target = "안녕")   // throws JamoDecompositionError on ""/untypeable text
judge.expectedNext      // Char? next jamo to type (null when complete). Use it as the key-guide highlight.
judge.expectedSequence  // List<Char> full key sequence ("ㅇㅏㄴㄴㅕㅇ")
judge.currentIndex; judge.correctCount; judge.errorCount   // Int
judge.isComplete        // Boolean
judge.progress          // Double 0.0..1.0

val eval: JamoJudgeEvaluation = JamoSequenceJudge.evaluate(input = 'ㅇ', state = judge)
judge = eval.state
when (val r = eval.result) {
  is JamoJudgeResult.Correct -> { /* r.completed: Boolean. Also feed the key to the composer */ }
  is JamoJudgeResult.Incorrect -> { /* r.expected: Char. Counts one mistake; index does not advance */ }
  JamoJudgeResult.AlreadyComplete -> Unit
}
```

A wrong key must not be fed to the composer. Only `Correct` keys go into `CompositionState`.
That way the preview only ever shows target text.

Helpers:

```kotlin
JamoDecomposer.keySequence(target: String): List<Char>   // @Throws(JamoDecompositionError::class)
JamoDecomposer.isShiftJamo(character: Char): Boolean     // ㄲㄸㅃㅆㅉㅒㅖ
JamoDecomposer.containsHangul(text: String): Boolean

sealed class JamoDecompositionError : Exception
  data object EmptyTarget
  data class UnsupportedCharacter(val character: String /* grapheme */, val offset: Int /* grapheme index */)
```

## 3. Korean 10-key (Cheonjiin) strokes and flicks

`Korean10KeyInterpreter` sits in front of the composer and the judge. It holds unfinished strokes
and emits one standard jamo only when the recipe for the expected jamo is complete.

```kotlin
enum class Korean10KeyKey { VERTICAL, DOT, HORIZONTAL, GIYEOK, NIEUN, DIGEUT, BIEUP, SIOT, JIEUT, IEUNG, NEXT, SPACE }
  .displayText: String      // "ㅣ", "ㆍ", "ㅡ", "ㄱㅋ", ..., "→", " "
  .supportsFlick: Boolean   // false for NEXT, SPACE
  .rawValue: String         // iOS raw value ("vertical", ...)

var tenKey = Korean10KeyInterpreter()

// Tap (including NEXT = "→" boundary key and SPACE):
val step: Korean10KeyStep = tenKey.input(key = Korean10KeyKey.GIYEOK, expecting = judge.expectedNext)
tenKey = step.interpreter
when (val i = step.interpretation) {
  is Korean10KeyInterpretation.Pending -> preview(composition.text + i.display) // not judged
  Korean10KeyInterpretation.SeparatorAccepted -> Unit                           // not judged
  is Korean10KeyInterpretation.Committed -> feedJamo(i.jamo)   // -> JamoSequenceJudge + HangulComposer
  is Korean10KeyInterpretation.Incorrect -> recordMistake()    // i.expected: Char?; do not touch composer/judge
}

// Flick: classify the finished touch, then map it to a completed jamo.
val touch = Korean10KeyFlickGestureResolver.interpretation(
  translationX = dxDp, translationY = dyDp, durationSeconds = seconds)  // Doubles; dp, +y is down
when (touch) {
  Korean10KeyTouchInterpretation.Tap -> tenKey.input(key, expected)
  is Korean10KeyTouchInterpretation.Flick ->
    tenKey.inputCompletedJamo(jamo = Korean10KeyFlickMapping.completedJamo(key, touch.direction), expecting = expected)
  Korean10KeyTouchInterpretation.InvalidFlick -> recordMistake()   // slow or diagonal flick
}
// An unassigned direction (completedJamo returns null) passed to inputCompletedJamo is also Incorrect.
// A cancelled system touch is not input, so do not call anything.

// Backspace: rewind a pending stroke first. Otherwise backspace the Hangul engine.
val bs: Korean10KeyBackspaceStep = tenKey.backspace()
tenKey = bs.interpreter
when (val r = bs.result) {
  is Korean10KeyBackspaceResult.PendingChanged -> preview(composition.text + (r.display ?: ""))
  Korean10KeyBackspaceResult.ForwardToHangulEngine ->
    composition = HangulComposer.reduce(composition, CompositionEvent.Backspace) // and rewind the judge progress
}

tenKey.nextKey(expected = judge.expectedNext): Korean10KeyKey?  // guide highlight (NEXT when a boundary is needed)
tenKey.pendingDisplay: String?; tenKey.pendingKeys: List<Korean10KeyKey>
tenKey.reset(): Korean10KeyInterpreter                        // new question/target

Korean10KeyFlickGestureResolver.MINIMUM_DISTANCE          // 24.0 (iOS points, use dp)
Korean10KeyFlickGestureResolver.MAXIMUM_DURATION_SECONDS  // 0.45
Korean10KeyFlickGestureResolver.AXIS_DOMINANCE            // 1.15
Korean10KeyFlickMapping.completedJamo(key: Korean10KeyKey, direction: Korean10KeyFlickDirection): Char?
enum class Korean10KeyFlickDirection { LEFT, RIGHT, UP, DOWN }

Korean10KeyRecipe.recipe(jamo: Char): List<Korean10KeyKey>?          // golden recipes (41 entries)
Korean10KeyRecipe.jamoForExactRecipe(recipe: List<Korean10KeyKey>): Char?
Korean10KeyRecipe.isReachableIntermediateVowel(intermediate: Char, toward: Char): Boolean
Korean10KeyRecipe.isReachableIntermediateConsonant(intermediate: Char, toward: Char): Boolean
Korean10KeyInterpreter.recipe(jamo: Char)                            // same as Korean10KeyRecipe.recipe
```

Consecutive jamo on the same grouped consonant key (for example the two `ㄴ`s in `언니`) need `NEXT`.
Without it, the tap is `Incorrect`.

## 4. OS IME text judging (F2a)

Pass the text from the `EditText` / `BasicTextField`, split into committed text and composing text:

```kotlin
val e: OSIMETextEvaluation = OSIMETextJudge.evaluate(
  target = "레전드",
  committedText = textWithoutComposingRegion,   // String
  markedText = composingRegionText,             // String? (null or "" when not composing)
)                                               // @Throws(JamoDecompositionError::class) for a bad target
e.acceptedSequence   // List<Char>: longest safe target key-jamo prefix. Set progress to acceptedSequence.size
when (val s = e.status) {
  is OSIMETextJudgeStatus.Matching -> { s.completed; s.isComposing }   // valid prefix; complete when s.completed
  OSIMETextJudgeStatus.ComposingMismatch -> Unit        // unconfirmed or 10-key intermediate: no mistake
  OSIMETextJudgeStatus.UnsupportedASCIIInput -> showEnglishKeyboardHint() // ignore input: no progress, no mistake
  is OSIMETextJudgeStatus.ConfirmedMismatch -> recordMistake() // s.expectedIndex: Int (jamo index)
}
```

- On Android, `committedText` is the full field text minus the IME composing span (`BaseInputConnection.getComposingSpanStart/End`), and `markedText` is the composing span.
- Only committed ASCII (except the space character) triggers `UnsupportedASCIIInput`. ASCII inside the marked text is ignored.
- The judge tolerates Cheonjiin 10-key intermediates in committed text as `ComposingMismatch` and does not count them as mistakes: `되`→`돼`, `ㄷㆍ`, `ㅇᆢ`, `단`→`달`, `댓`→`대형`, `잀`→`일해`, `찬ㅅ`→`찮`, `핰`→`학교`, `앐`→`앓`.
- iOS handling (`OSIMEInputView.swift`) works like this. On `Matching` or `ComposingMismatch`, apply `acceptedSequence`. On `ConfirmedMismatch`, apply `acceptedSequence`, count one mistake, and reset the field text to `HangulComposer.compose(e.acceptedSequence).text`. The reset is what keeps the mistake from being counted again.
- The multi-target `OSIMECandidateTextJudge` in the iOS app layer is not part of this module.
