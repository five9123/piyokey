package app.piyokey.android.feature.input

import androidx.compose.ui.unit.dp
import app.piyokey.android.ui.theme.AdaptiveMetrics
import app.piyokey.core.hangul.Korean10KeyBackspaceResult
import app.piyokey.core.hangul.Korean10KeyInterpretation
import app.piyokey.core.hangul.Korean10KeyKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyboardPreferencesTest {
  @Test
  fun keysMatchIos() {
    assertEquals(
      listOf(
        "keyboard.shows_key_guide",
        "keyboard.shows_roman_hints",
        "keyboard.haptics_enabled",
        "keyboard.input_mode_default",
        "keyboard.shows_physical_keyboard_guide",
        "keyboard.builtin_layout_default",
      ),
      KeyboardPreferences.ALL_KEYS,
    )
  }

  @Test
  fun sessionInputModeDecodesLegacyValues() {
    assertEquals(SessionInputMode.BUILT_IN, SessionInputMode.decode("builtin"))
    assertEquals(SessionInputMode.BUILT_IN, SessionInputMode.decode("built_in"))
    assertEquals(SessionInputMode.BUILT_IN_KOREAN_10KEY, SessionInputMode.decode("builtin_korean_10key"))
    assertEquals(SessionInputMode.OS_IME, SessionInputMode.decode("os_ime"))
    assertEquals(SessionInputMode.OS_IME, SessionInputMode.decode("os_keyboard"))
    assertNull(SessionInputMode.decode("keyboard"))
    assertNull(SessionInputMode.decode(null))
    // Strict raw lookup does not accept legacy spellings (iOS init?(rawValue:)).
    assertNull(SessionInputMode.fromRaw("built_in"))
  }

  @Test
  fun defaultInputModeOnlyAcceptsBuiltInOrOsIme() {
    assertEquals(SessionInputMode.OS_IME, SessionInputMode.resolvedDefault("os_ime"))
    assertEquals(SessionInputMode.BUILT_IN, SessionInputMode.resolvedDefault("builtin"))
    assertEquals(SessionInputMode.BUILT_IN, SessionInputMode.resolvedDefault("builtin_korean_10key"))
    assertEquals(SessionInputMode.BUILT_IN, SessionInputMode.resolvedDefault("os_keyboard"))
    assertEquals(SessionInputMode.BUILT_IN, SessionInputMode.resolvedDefault(null))
    assertEquals(SessionInputMode.BUILT_IN, SessionInputMode.persistedDefault(SessionInputMode.BUILT_IN_KOREAN_10KEY))
    assertEquals(SessionInputMode.OS_IME, SessionInputMode.persistedDefault(SessionInputMode.OS_IME))
    assertEquals("input_mode.builtin_korean_10key", SessionInputMode.BUILT_IN_KOREAN_10KEY.labelKey)
  }

  @Test
  fun builtInLayoutResolvesAndMapsToRecordMode() {
    assertEquals(BuiltInKeyboardLayout.KOREAN_10KEY, BuiltInKeyboardLayout.resolved("korean_10key"))
    assertEquals(BuiltInKeyboardLayout.DUBEOLSIK, BuiltInKeyboardLayout.resolved("qwerty"))
    assertEquals(BuiltInKeyboardLayout.DUBEOLSIK, BuiltInKeyboardLayout.resolved(null))
    assertEquals(SessionInputMode.BUILT_IN, BuiltInKeyboardLayout.DUBEOLSIK.gameRecordInputMode)
    assertEquals(SessionInputMode.BUILT_IN_KOREAN_10KEY, BuiltInKeyboardLayout.KOREAN_10KEY.gameRecordInputMode)
  }

  @Test
  fun optionsDefaultToIos() {
    assertEquals(HangulKeyboardOptions(showsKeyGuide = true, showsRomanHints = true, hapticsEnabled = true), HangulKeyboardOptions())
  }
}

class KeyboardGeometryTest {
  private val a = KeyboardAction.Character('ㄱ')
  private val b = KeyboardAction.Character('ㄴ')
  private val targets = listOf(
    KeyboardTouchTarget(a, KeyRect(0f, 0f, 10f, 10f)),
    KeyboardTouchTarget(b, KeyRect(14f, 0f, 24f, 10f)),
  )

  @Test
  fun touchInsideKeyResolvesThatKey() {
    assertEquals(a, KeyboardTouchTargetResolver.action(5f, 5f, targets, 4f, 5f))
    assertEquals(b, KeyboardTouchTargetResolver.action(20f, 5f, targets, 4f, 5f))
  }

  @Test
  fun gapTouchPicksNearestEdgeWithinOutset() {
    assertEquals(a, KeyboardTouchTargetResolver.action(11f, 5f, targets, 4f, 5f))
    assertEquals(b, KeyboardTouchTargetResolver.action(13f, 5f, targets, 4f, 5f))
    // Equidistant edges: the closer centre wins (a's centre is 7 away, b's 7 away → first stays).
    assertEquals(a, KeyboardTouchTargetResolver.action(12f, 5f, targets, 4f, 5f))
    // Below the key within the vertical outset.
    assertEquals(a, KeyboardTouchTargetResolver.action(5f, 14f, targets, 4f, 5f))
  }

  @Test
  fun touchOutsideOutsetMisses() {
    assertNull(KeyboardTouchTargetResolver.action(5f, 16f, targets, 4f, 5f))
    assertNull(KeyboardTouchTargetResolver.action(30f, 5f, targets, 4f, 5f))
    // Negative outsets are clamped to zero.
    assertNull(KeyboardTouchTargetResolver.action(11f, 5f, targets, -3f, -3f))
  }

  @Test
  fun normalizedPositions() {
    assertEquals(-1f, HangulKeyboardGeometry.normalizedHorizontalPosition('ㅂ'))
    assertEquals(1f, HangulKeyboardGeometry.normalizedHorizontalPosition('ㅔ'))
    assertEquals(-1f, HangulKeyboardGeometry.normalizedHorizontalPosition('ㅃ'))
    assertEquals(1f, HangulKeyboardGeometry.normalizedHorizontalPosition('ㅡ'))
    assertEquals(0f, HangulKeyboardGeometry.normalizedHorizontalPosition(null))
    assertEquals(0f, HangulKeyboardGeometry.normalizedHorizontalPosition(' '))
    assertEquals(-1f, Korean10KeyGeometry.normalizedHorizontalPosition(Korean10KeyKey.NEXT))
    assertEquals(0f, Korean10KeyGeometry.normalizedHorizontalPosition(Korean10KeyKey.IEUNG))
    assertEquals(1f, Korean10KeyGeometry.normalizedHorizontalPosition(Korean10KeyKey.JIEUT))
    assertEquals(0f, Korean10KeyGeometry.normalizedHorizontalPosition(null))
  }

  @Test
  fun dubeolsikLayoutAndShift() {
    assertEquals(10, DubeolsikKey.TOP_ROW.size)
    assertEquals(9, DubeolsikKey.HOME_ROW.size)
    assertEquals(7, DubeolsikKey.BOTTOM_ROW.size)
    val giyeok = DubeolsikKey.TOP_ROW[3]
    assertEquals('ㄱ', giyeok.output(isShifted = false))
    assertEquals('ㄲ', giyeok.output(isShifted = true))
    assertEquals('ㅛ', DubeolsikKey.TOP_ROW[5].output(isShifted = true))
    assertEquals("qwertyuiop", DubeolsikKey.TOP_ROW.joinToString("") { it.roman })
  }

  @Test
  fun keyGuideHighlightRules() {
    assertTrue(DubeolsikGuide.shiftHighlighted(true, 'ㄲ', isShifted = false))
    assertFalse(DubeolsikGuide.shiftHighlighted(true, 'ㄲ', isShifted = true))
    assertFalse(DubeolsikGuide.shiftHighlighted(false, 'ㄲ', isShifted = false))
    assertFalse(DubeolsikGuide.shiftHighlighted(true, 'ㄱ', isShifted = false))
    assertFalse(DubeolsikGuide.shiftHighlighted(true, null, isShifted = false))

    assertTrue(DubeolsikGuide.keyHighlighted(true, 'ㄱ', 'ㄱ', isShifted = false))
    assertFalse(DubeolsikGuide.keyHighlighted(true, 'ㄲ', 'ㄱ', isShifted = false))
    assertTrue(DubeolsikGuide.keyHighlighted(true, 'ㄲ', 'ㄲ', isShifted = true))
    assertFalse(DubeolsikGuide.keyHighlighted(false, 'ㄱ', 'ㄱ', isShifted = false))
    assertFalse(DubeolsikGuide.keyHighlighted(true, null, 'ㄱ', isShifted = false))
    assertTrue(DubeolsikGuide.spaceHighlighted(true, ' '))
    assertFalse(DubeolsikGuide.spaceHighlighted(false, ' '))
  }

  @Test
  fun tenKeyLayoutAndHints() {
    assertEquals(listOf("ㅣ", "ㆍ", "ㅡ"), Korean10KeyLayout.ROWS[0].map { it.displayText })
    assertEquals("keyboard.10key.giyeok", Korean10KeyLayout.accessibilityKey(Korean10KeyKey.GIYEOK))
    assertEquals("keyboard.space", Korean10KeyLayout.accessibilityKey(Korean10KeyKey.SPACE))
    assertEquals(Korean10KeyLayout.FlickHint.Three("ㄱ", "ㅋ", "ㄲ"), Korean10KeyLayout.flickHint(Korean10KeyKey.GIYEOK))
    assertEquals(Korean10KeyLayout.FlickHint.Two("ㅇ", "ㅁ"), Korean10KeyLayout.flickHint(Korean10KeyKey.IEUNG))
    assertEquals(Korean10KeyLayout.FlickHint.Fixed("keyboard.10key.flick.dot"), Korean10KeyLayout.flickHint(Korean10KeyKey.DOT))
    assertEquals(Korean10KeyLayout.FlickHint.None, Korean10KeyLayout.flickHint(Korean10KeyKey.NEXT))
  }
}

class PhysicalKeyboardLayoutTest {
  @Test
  fun baseShiftedAndSpaceTargets() {
    val base = PhysicalDubeolsikLayout.target('ㄹ')!!
    assertEquals('F', base.key?.latin)
    assertEquals(PhysicalKeyboardHand.LEFT, base.hand)
    assertEquals(PhysicalKeyboardFinger.INDEX, base.finger)
    assertFalse(base.requiresShift)
    assertTrue(base.key!!.isHomePosition)

    val shifted = PhysicalDubeolsikLayout.target('ㅖ')!!
    assertEquals('P', shifted.key?.latin)
    assertTrue(shifted.requiresShift)
    assertEquals(PhysicalKeyboardHand.LEFT, shifted.shiftHand)

    val space = PhysicalDubeolsikLayout.target(' ')!!
    assertNull(space.key)
    assertEquals(PhysicalKeyboardHand.BOTH, space.hand)
    assertEquals(PhysicalKeyboardFinger.THUMB, space.finger)

    assertNull(PhysicalDubeolsikLayout.target(null))
    assertNull(PhysicalDubeolsikLayout.target('A'))
    assertEquals(PhysicalKeyboardHand.BOTH, PhysicalKeyboardHand.BOTH.opposite)
  }

  @Test
  fun tabletOnlyPolicies() {
    val phone = AdaptiveMetrics(390.dp, 800.dp)
    val tablet = AdaptiveMetrics(820.dp, 1180.dp)
    assertFalse(PhysicalKeyboardGuidePolicy.isVisible(phone))
    assertTrue(PhysicalKeyboardGuidePolicy.isVisible(tablet))
    assertFalse(OsImeInputPanelPolicy.showsVisibleFocusRecovery(true, phone))
    assertTrue(OsImeInputPanelPolicy.showsVisibleFocusRecovery(true, tablet))
    assertFalse(OsImeInputPanelPolicy.showsVisibleFocusRecovery(false, tablet))
  }
}

class ImeTextSnapshotTest {
  @Test
  fun splitsAroundComposingSpan() {
    assertEquals(ImeTextSnapshot("안", "녀"), ImeTextSnapshot.split("안녀", 1, 2))
    assertEquals(ImeTextSnapshot("가다", "나"), ImeTextSnapshot.split("가나다", 1, 2))
    assertEquals(ImeTextSnapshot("가", null), ImeTextSnapshot.split("가", -1, -1))
    assertEquals(ImeTextSnapshot("가", null), ImeTextSnapshot.split("가", 1, 1))
    assertEquals(ImeTextSnapshot("가", null), ImeTextSnapshot.split("가", 0, 5))
    // Reversed span offsets are normalised.
    assertEquals(ImeTextSnapshot("", "가"), ImeTextSnapshot.split("가", 1, 0))
  }

  @Test
  fun quarantineSwallowsStalePreviousTargetSnapshots() {
    val q = ImeResetQuarantine()
    assertEquals(ImeResetQuarantine.Decision.ACCEPT, q.classify("사과", listOf("사과")))
    q.beginReset(currentText = "사과", replacement = "")
    assertTrue(q.isAwaitingPostResetInput)
    // Echo of the reset document.
    assertEquals(ImeResetQuarantine.Decision.IGNORE, q.classify("", listOf("바나나")))
    // The IME republishes the abandoned previous word: reset again.
    assertEquals(ImeResetQuarantine.Decision.RESET_AGAIN, q.classify("사과", listOf("바나나")))
    q.beginReset(currentText = "사과", replacement = "", preservingStaleDocuments = true)
    assertTrue(q.isAwaitingPostResetInput)
    // First real post-reset input is accepted and ends the quarantine.
    assertEquals(ImeResetQuarantine.Decision.ACCEPT, q.classify("바", listOf("바나나")))
    assertFalse(q.isAwaitingPostResetInput)
    assertEquals(ImeResetQuarantine.Decision.ACCEPT, q.classify("사과", listOf("바나나")))
  }

  @Test
  fun quarantineAcceptsValidPrefixSharingStaleMaterial() {
    val q = ImeResetQuarantine()
    q.classify("사과", listOf("사과"))
    q.beginReset("사과", "")
    // New target starts with the same word: a valid prefix is accepted.
    assertEquals(ImeResetQuarantine.Decision.ACCEPT, q.classify("사과", listOf("사과나무")))
  }

  @Test
  fun quarantineOnlyInspectsMaterialAfterRestoredPrefix() {
    val q = ImeResetQuarantine()
    q.classify("가나", listOf("가나"))
    q.beginReset("가나", replacement = "가")
    assertEquals("가", q.pendingResetDocument)
    assertEquals(ImeResetQuarantine.Decision.IGNORE, q.classify("가", listOf("가다")))
    // "가다" after the restored "가" does not contain the stale "가나".
    assertEquals(ImeResetQuarantine.Decision.ACCEPT, q.classify("가다", listOf("가다")))
  }

  @Test
  fun compositionSnapshotsCoverJamoPrefixes() {
    val snapshots = ImeResetQuarantine.compositionSnapshots("간")
    assertTrue("간" in snapshots)
    assertTrue("가" in snapshots)
    assertTrue("ㄱ" in snapshots)
    assertTrue(ImeResetQuarantine.compositionSnapshots("").isEmpty())
    assertTrue("a!" in ImeResetQuarantine.compositionSnapshots("a!"))
  }
}

class InputLatencyMonitorTest {
  @Test
  fun percentilesUseNearestRank() {
    val sorted = (1..20).map { it.toDouble() }
    assertEquals(10.0, InputLatencyMonitor.percentile(sorted, 0.5))
    assertEquals(19.0, InputLatencyMonitor.percentile(sorted, 0.95))
    assertEquals(7.0, InputLatencyMonitor.percentile(listOf(7.0), 0.95))
    assertNull(InputLatencyMonitor.percentile(emptyList(), 0.5))
  }

  @Test
  fun samplesArePendingUntilFrameAndWindowed() {
    InputLatencyMonitor.isEnabled = true
    val monitor = InputLatencyMonitor(sampleLimit = 3)
    monitor.finishPendingInputs(100)
    assertEquals(0, monitor.sampleCount)
    monitor.beginInput(100)
    monitor.beginInput(110)
    assertEquals(2, monitor.inputRevision)
    monitor.finishPendingInputs(130)
    assertEquals(2, monitor.sampleCount)
    assertEquals(20.0, monitor.p50Milliseconds)
    assertEquals(30.0, monitor.p95Milliseconds)
    monitor.beginInput(200)
    monitor.beginInput(200)
    monitor.finishPendingInputs(201)
    assertEquals(3, monitor.sampleCount)
    assertEquals(1.0, monitor.p50Milliseconds)
    monitor.beginInput(500)
    monitor.finishPendingInputs(400) // clock skew clamps to 0 → window [1, 1, 0]
    assertEquals(3, monitor.sampleCount)
    assertEquals(1.0, monitor.p50Milliseconds)
    monitor.reset()
    assertEquals(0, monitor.sampleCount)
    assertNull(monitor.p95Milliseconds)
  }

  @Test
  fun disabledMonitorIgnoresInput() {
    InputLatencyMonitor.isEnabled = false
    try {
      val monitor = InputLatencyMonitor()
      monitor.beginInput(1)
      assertEquals(0, monitor.inputRevision)
    } finally {
      InputLatencyMonitor.isEnabled = true
    }
  }
}

class KoreanKeyboardAvailabilityTest {
  @Test
  fun detectsKoreanLanguageTags() {
    assertTrue(KoreanKeyboardAvailability.containsKorean(listOf("en-US", "ko-KR")))
    assertTrue(KoreanKeyboardAvailability.containsKorean(listOf("KO")))
    assertTrue(KoreanKeyboardAvailability.containsKorean(listOf(null, "ko_KR")))
    assertFalse(KoreanKeyboardAvailability.containsKorean(listOf("ja-JP", null)))
    assertFalse(KoreanKeyboardAvailability.containsKorean(emptyList()))
  }
}

class SessionKeyboardStateTest {
  @Test
  fun recordModeFollowsModeAndLayout() {
    assertEquals(SessionInputMode.BUILT_IN, SessionKeyboardState().recordInputMode)
    assertEquals(
      SessionInputMode.BUILT_IN_KOREAN_10KEY,
      SessionKeyboardState(initialLayout = BuiltInKeyboardLayout.KOREAN_10KEY).recordInputMode,
    )
    assertEquals(
      SessionInputMode.BUILT_IN,
      SessionKeyboardState(initialLayout = BuiltInKeyboardLayout.KOREAN_10KEY, allowsKorean10Key = false).recordInputMode,
    )
    assertEquals(SessionInputMode.OS_IME, SessionKeyboardState(SessionInputMode.OS_IME).recordInputMode)
    assertEquals(SessionInputMode.BUILT_IN, SessionKeyboardState(SessionInputMode.OS_IME, allowsOsIme = false).recordInputMode)
    // The record-only value is never the live mode.
    assertEquals(SessionInputMode.BUILT_IN, SessionKeyboardState(SessionInputMode.BUILT_IN_KOREAN_10KEY).mode)
  }

  @Test
  fun tenKeyStrokesCommitOnlyCompletedJamo() {
    val state = SessionKeyboardState(initialLayout = BuiltInKeyboardLayout.KOREAN_10KEY)
    // ㅏ = ㅣ + ㆍ
    assertIs<Korean10KeyInterpretation.Pending>(state.korean10KeyInput(Korean10KeyKey.VERTICAL, 'ㅏ'))
    assertEquals("ㅣ", state.korean10KeyPendingDisplay)
    assertEquals(Korean10KeyInterpretation.Committed('ㅏ'), state.korean10KeyInput(Korean10KeyKey.DOT, 'ㅏ'))
    assertNull(state.korean10KeyPendingDisplay)
    // Flick: ㄱㅋ key right = ㅋ
    assertEquals(Korean10KeyInterpretation.Committed('ㅋ'), state.korean10KeyCompletedJamo('ㅋ', 'ㅋ'))
    assertIs<Korean10KeyInterpretation.Incorrect>(state.korean10KeyCompletedJamo(null, 'ㅋ'))
    // Backspace rewinds pending strokes before forwarding.
    state.korean10KeyInput(Korean10KeyKey.VERTICAL, 'ㅏ')
    assertIs<Korean10KeyBackspaceResult.PendingChanged>(state.korean10KeyBackspace())
    assertEquals(Korean10KeyBackspaceResult.ForwardToHangulEngine, state.korean10KeyBackspace())
  }

  @Test
  fun resetForNewTargetDropsPendingAndBumpsRevision() {
    val state = SessionKeyboardState(initialLayout = BuiltInKeyboardLayout.KOREAN_10KEY)
    state.korean10KeyInput(Korean10KeyKey.VERTICAL, 'ㅏ')
    state.resetForNewTarget()
    assertNull(state.korean10KeyPendingDisplay)
    assertEquals(1, state.resetRevision)
    state.selectMode(SessionInputMode.OS_IME, persist = false)
    assertTrue(state.usesOsIme)
    assertNull(state.korean10KeyPendingDisplay)
    assertEquals(2, state.resetRevision)
    state.selectMode(SessionInputMode.OS_IME, persist = false)
    assertEquals(2, state.resetRevision)
    state.selectLayout(BuiltInKeyboardLayout.DUBEOLSIK, persist = false)
    state.selectMode(SessionInputMode.BUILT_IN, persist = false)
    assertFalse(state.usesKorean10Key)
  }
}
