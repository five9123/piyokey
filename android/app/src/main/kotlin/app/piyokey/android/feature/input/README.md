# feature/input — keyboards and OS-IME input

Port of iOS `Features/Practice/HangulKeyboardView.swift`, `OSIMEInputView.swift` and
`InputLatencyMonitor.swift`. Package `app.piyokey.android.feature.input`.
Pure judging lives in `:core:hangul` (`JamoSequenceJudge`, `HangulComposer`,
`Korean10KeyInterpreter`, `OSIMETextJudge`, `OSIMECandidateTextJudge`).

Session screens should only need **`SessionKeyboard` + `SessionKeyboardState`**.
The lower-level composables are public for onboarding and special layouts.

## 1. SessionKeyboard (use this in practice / game screens)

```kotlin
// Screen state (survives recomposition; reads the user's keyboard defaults once).
val keyboard = rememberSessionKeyboardState(
  allowsOsIme = true,          // iOS allowsOSKeyboard
  allowsKorean10Key = true,    // iOS allowsKorean10Key
)

// When the target / question changes (iOS onChange(currentTargetIndex)):
LaunchedEffect(targetIndex) { keyboard.resetForNewTarget() }

SessionKeyboard(
  state = keyboard,
  expectedNextJamo = judge.expectedNext,          // Char? — needed by 10-key even in recall games
  osImeTarget = currentTarget,                    // String judged by the OS IME path
  osImeAcceptedText = composition.text,           // field is reset to this on reset
  onJamo = { jamo -> vm.input(jamo) },            // → JamoSequenceJudge + HangulComposer
  onBackspace = { vm.backspace() },               // → HangulComposer Backspace + judge rewind
  onKorean10KeyMistake = { vm.recordMistake() },  // 10-key stroke/flick that cannot reach expected
  onOsImeAcceptedSequence = { seq -> vm.synchronizeOsIme(seq) }, // progress = seq.size
  onOsImeConfirmedMismatch = { vm.recordMistake() },
  // optional:
  modifier = Modifier,
  revealsExpectedKey = true,           // false in recall games (Choseong etc.): no highlight, no physical key
  options = rememberKeyboardOptions(), // key guide / roman hints / haptics prefs (live)
  showsPhysicalKeyboardGuide = /* pref keyboard.shows_physical_keyboard_guide */,
  osImeCandidateTargets = emptyList(), // acid rain: every visible word
  onOsImeAcceptedCandidateSequence = null, // (target, seq) -> Unit, required with candidates
  osImeResetRevision = 0,              // extra reset trigger (e.g. card revision), added to state's
  embedsOsImeField = true,             // false if the screen hosts OsImeInputPanel itself
  showsInputSourceBanner = true,       // false → draw OsImeInputSourceBannerLayer(keyboard.osImeBanner.value) yourself
  onInputStart = { uptime -> latency.beginInput(uptime) }, // debug latency probe
  onKeyFeedback = { role -> SoundEngine.keyTap(role) },    // default
  onOsImeInputStart = { SoundEngine.warmUp(combo) },       // default warmUp()
)
```

Behaviour (mirrors iOS `inputArea`):

| `state` | Renders | Events |
|---|---|---|
| built-in + dubeolsik | `DubeolsikKeyboard` | every key press → `onJamo` (wrong keys too; the judge rejects them), ⌫ → `onBackspace` |
| built-in + 10-key | `Korean10KeyKeyboard` + internal `Korean10KeyInterpreter` | completed jamo → `onJamo`; impossible stroke/flick → `onKorean10KeyMistake`; ⌫ rewinds pending strokes first, otherwise `onBackspace` |
| OS IME | `OsImeInputPanel` (56dp: invisible tap-to-refocus field on phones, compact "type with your keyboard" strip on tablets) + `PhysicalKeyboardGuide` (tablets, pref on) | `onOsImeAcceptedSequence`, `onOsImeConfirmedMismatch` |

Show the unfinished 10-key strokes in the composition preview:
`composition.text + (keyboard.korean10KeyPendingDisplay ?: "")`.

### SessionKeyboardState

```kotlin
class SessionKeyboardState(
  initialMode: SessionInputMode = BUILT_IN, initialLayout: BuiltInKeyboardLayout = DUBEOLSIK,
  val allowsOsIme: Boolean = true, val allowsKorean10Key: Boolean = true,
)
  var mode: SessionInputMode            // BUILT_IN or OS_IME (read-only; use selectMode)
  var layout: BuiltInKeyboardLayout     // read-only; use selectLayout
  val korean10Key: Korean10KeyInterpreter
  val korean10KeyPendingDisplay: String?
  val resetRevision: Int
  var showsOsImeUnavailable: Boolean    // "no Korean keyboard detected" hint banner
  var isOsImeFocusSuspended: Boolean    // true while a sheet/pause/result covers the session
  val osImeBanner: MutableState<OsImeInputSourceBannerPresentation>
  val usesOsIme: Boolean; val usesKorean10Key: Boolean
  val recordInputMode: SessionInputMode // for GameRecord / analytics: builtin | builtin_korean_10key | os_ime
  fun resetForNewTarget()
  fun selectMode(newMode: SessionInputMode, persist: Boolean = true)     // persists default like iOS
  fun selectLayout(newLayout: BuiltInKeyboardLayout, persist: Boolean = true)
  fun korean10KeyInput(key, expected): Korean10KeyInterpretation         // low-level, used internally
  fun korean10KeyCompletedJamo(jamo: Char?, expected): Korean10KeyInterpretation
  fun korean10KeyBackspace(): Korean10KeyBackspaceResult
```

`rememberSessionKeyboardState()` starts in OS-IME mode when the default is `os_ime`. If no Korean
keyboard is detected it **still** uses the OS IME (Android detection is unreliable) and sets
`showsOsImeUnavailable` (hint banner, no link — no interruptions in sessions).

## 2. Preferences — `KeyboardPreferences`

iOS keys verbatim: `keyboard.shows_key_guide` (true), `keyboard.shows_roman_hints` (true),
`keyboard.haptics_enabled` (true), `keyboard.input_mode_default` (`builtin`),
`keyboard.shows_physical_keyboard_guide` (false), `keyboard.builtin_layout_default` (`dubeolsik`).

```kotlin
KeyboardPreferences.showsKeyGuide / showsRomanHints / hapticsEnabled / showsPhysicalKeyboardGuide : BoolPref
KeyboardPreferences.inputModeDefault / builtInLayoutDefault : StringPref (raw)
KeyboardPreferences.defaultInputMode: SessionInputMode     // only builtin|os_ime
KeyboardPreferences.builtInLayout: BuiltInKeyboardLayout
KeyboardPreferences.snapshot: KeyboardPreferenceSnapshot
KeyboardPreferences.options: HangulKeyboardOptions
KeyboardPreferences.setDefaultInputMode(mode) // builtin_korean_10key persists as builtin
KeyboardPreferences.setBuiltInLayout(layout); setShowsPhysicalKeyboardGuide(Boolean)
KeyboardPreferences.repairInvalidValues(): KeyboardPreferenceSnapshot   // Settings onAppear

enum class SessionInputMode(val raw: String) { BUILT_IN("builtin"), BUILT_IN_KOREAN_10KEY("builtin_korean_10key"), OS_IME("os_ime") }
  SessionInputMode.decode(raw)   // records: also accepts legacy "built_in" / "os_keyboard"; null if unknown
  SessionInputMode.fromRaw(raw); .labelKey ("input_mode.<raw>")
enum class BuiltInKeyboardLayout(val raw: String) { DUBEOLSIK("dubeolsik"), KOREAN_10KEY("korean_10key") }
  .gameRecordInputMode; BuiltInKeyboardLayout.resolved(raw)
data class HangulKeyboardOptions(showsKeyGuide = true, showsRomanHints = true, hapticsEnabled = true)
@Composable fun rememberKeyboardOptions(): HangulKeyboardOptions
```

Settings UI: `SessionInputModeControl(selection, onSelect, onUnavailableOsIme = {})` (iOS segmented
control; selecting OS keyboard is never blocked) and `KoreanKeyboardGuideSheet(onDismiss)` /
`KoreanKeyboardGuideRoute()` / `KoreanKeyboardGuide(onClose)` (Android steps + "Open keyboard
settings" → `Settings.ACTION_INPUT_METHOD_SETTINGS`). Outside sessions,
`OsImeUnavailableBanner(onOpenGuide = { … })` adds a guide link.
`KoreanKeyboardAvailability.isAvailable(context)` is a hint only.

## 3. Low-level keyboards

```kotlin
@Composable fun DubeolsikKeyboard(
  nextExpectedKey: Char?, onKey: (Char) -> Unit, onBackspace: () -> Unit,
  modifier: Modifier = Modifier, options: HangulKeyboardOptions = HangulKeyboardOptions(),
  onInputStart: (eventUptimeMillis: Long) -> Unit = {}, onKeyFeedback: (TypingSoundKeyRole) -> Unit = {},
)
@Composable fun Korean10KeyKeyboard(
  nextExpectedKey: Korean10KeyKey?,            // interpreter.nextKey(expected)
  onKey: (Korean10KeyKey) -> Unit,             // tap (incl. NEXT "→" and SPACE)
  onCompletedJamo: (Char?) -> Unit,            // flick; null = invalid/unassigned flick (a mistake)
  onBackspace: () -> Unit,
  modifier, options = HangulKeyboardOptions(showsRomanHints = false), onInputStart, onKeyFeedback,
)
```

- Full width; key height 50dp (10-key 52dp) × `Piyo.metrics.keyboardScale`; 10-key max 600dp on tablets.
- Multi-touch rollover: every pointer is tracked separately and immediate keys commit on touch-down.
  Flick keys resolve on touch-up with `Korean10KeyFlickGestureResolver` (24dp, 0.45s, 1.15 axis
  dominance). No visual flick preview (TYP-111). A cancelled touch is not input.
- Press scale 0.95 (60ms), `KEYBOARD_TAP` haptic when `hapticsEnabled`, pulsing accent glow on the
  guided key (and on shift when the next jamo needs it). Shift is one-shot.
- Test tags = iOS identifiers: `keyboard.key.ㄱ`, `keyboard.shift`, `keyboard.backspace`,
  `keyboard.space`, `keyboard.10key.<rawValue>`, `keyboard.container`, `keyboard.10key.container`
  (`KeyboardTags`). Highlighted keys are `selected` in semantics.

`PhysicalKeyboardGuide(nextExpectedKey: Char?)` — tablet-only (`Piyo.metrics.isExpanded`) hardware
dubeolsik finger map. Pure helpers: `PhysicalDubeolsikLayout`, `HangulKeyboardGeometry` /
`Korean10KeyGeometry.normalizedHorizontalPosition` (mascot gaze), `KeyboardTouchTargetResolver`.

## 4. OS IME panel

```kotlin
@Composable fun OsImeInputPanel(
  target: String, acceptedText: String, resetRevision: Int,
  onAcceptedSequence: (List<Char>) -> Unit, onConfirmedMismatch: () -> Unit,
  modifier: Modifier = Modifier,
  candidateTargets: List<String> = emptyList(),
  onAcceptedCandidateSequence: ((target: String, sequence: List<Char>) -> Unit)? = null,
  onInputStart: () -> Unit = {},
  showsChrome: Boolean = true,          // visible labelled panel
  showsFocusRecovery: Boolean = false,  // tablets: compact strip; phones: invisible field filling modifier
  isFocusSuspended: Boolean = false,
  externalBanner: MutableState<OsImeInputSourceBannerPresentation>? = null,
)
@Composable fun OsImeInputSourceBannerLayer(presentation, modifier = Modifier, testTag = "os_ime.input_source_warning")
@Composable fun OsImeUnavailableBanner(modifier = Modifier, onOpenGuide: (() -> Unit)? = null)
```

Practice on phones (iOS `overlaysHiddenOSIMEInput`): put
`OsImeInputPanel(showsChrome = false, showsFocusRecovery = false, externalBanner = keyboard.osImeBanner, modifier = Modifier.matchParentSize())`
behind the scroll content, draw `OsImeInputSourceBannerLayer(keyboard.osImeBanner.value)` on top,
and pass `embedsOsImeField = false` to `SessionKeyboard`.

Field details (`ImeEditText`): plain `TYPE_CLASS_TEXT` (Korean IMEs keep composing), no
auto-correct flags, `IME_FLAG_NO_PERSONALIZED_LEARNING | NO_EXTRACT_UI | NO_FULLSCREEN`, Korean
IME hint locale, transparent text/cursor. It reports `(committed, composing)` once per IME batch
edit, keeps the caret at the end, handles Enter without losing focus, re-focuses on resume /
window focus, and resets with `InputMethodManager.restartInput` plus iOS's stale-snapshot
quarantine. Test tag / view tag: `os_ime.text_field`. English (ASCII) input shows the
"keyboard is set to English" banner (shake; colour flash only under "remove animations").

## 5. Latency probe (debug only)

```kotlin
val latency = remember { InputLatencyMonitor() }       // no-op in release (isEnabled = BuildConfig.DEBUG)
InputLatencyProbe(latency)                              // place anywhere in the session UI
SessionKeyboard(..., onInputStart = latency::beginInput)
latency.p50Milliseconds; latency.p95Milliseconds; latency.sampleCount  // Compose state
```

Pointer `uptimeMillis` → next frame commit (`ViewTreeObserver.registerFrameCommitCallback`,
API 29+; `Choreographer` fallback). Text: `R.string.debug_input_latency_value`.

## Strings

Shared iOS keys come from `strings_generated.xml`. Android-only copy (guide steps, English-keyboard
banner detail, not-detected hint) lives in `res/values*/strings_input.xml` (owned here, en/ja/es/de/fr).
