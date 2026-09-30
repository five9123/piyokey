package app.piyokey.android.feature.input

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.LocaleList
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import java.util.Locale

/**
 * The invisible OS-IME field (iOS `IMETextField`'s `UITextField`).
 *
 * - Plain `TYPE_CLASS_TEXT` so Korean IMEs keep composing (password/visible-password variations
 *   make Gboard/Samsung commit raw jamo). No auto-correct/auto-complete/cap flags are requested,
 *   personalized learning is off, the extract UI / fullscreen mode are disabled, and Korean is
 *   passed as the IME hint locale.
 * - Reports committed text and the composing ("marked") span separately
 *   ([BaseInputConnection.getComposingSpanStart]/`End`), once per IME batch edit, deduplicated.
 * - Enter is consumed (IME action Done keeps the keyboard up and the focus in the field).
 * - [scheduleReset] clears leftover IME composition with `InputMethodManager.restartInput` on the
 *   next loop, and quarantines stale previous-target snapshots ([ImeResetQuarantine]).
 */
@SuppressLint("ViewConstructor")
class ImeEditText(context: Context) : EditText(context) {
  var targets: List<String> = emptyList()
  var onTextChange: (committed: String, marked: String?) -> Unit = { _, _ -> }
  var onReturn: () -> Unit = {}
  var onFocusStateChanged: (Boolean) -> Unit = {}
  var isFocusSuspended: Boolean = false
    set(value) {
      if (field == value) return
      field = value
      if (value) post { if (isFocusSuspended) releaseImeFocus() } else requestImeFocus()
    }

  private val quarantine = ImeResetQuarantine()
  private var ready = false
  private var isApplyingReset = false
  private var hasPendingReset = false
  private var resetGeneration = 0
  private var batchDepth = 0
  private var changedDuringBatch = false
  private var lastReported: ImeTextSnapshot? = null
  private var wantsFocus = false

  private val imm: InputMethodManager?
    get() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

  init {
    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
    imeOptions = EditorInfo.IME_ACTION_DONE or
      EditorInfo.IME_FLAG_NO_EXTRACT_UI or
      EditorInfo.IME_FLAG_NO_FULLSCREEN or
      EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) imeHintLocales = LocaleList(Locale.KOREAN)
    maxLines = 1
    setHorizontallyScrolling(true)
    background = null
    setPadding(0, 0, 0, 0)
    setTextColor(Color.TRANSPARENT)
    setHintTextColor(Color.TRANSPARENT)
    highlightColor = Color.TRANSPARENT
    isCursorVisible = false
    isLongClickable = false
    isSaveEnabled = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
    customSelectionActionModeCallback = NoActionMode
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) customInsertionActionModeCallback = NoActionMode
    tag = TAG
    addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
      override fun afterTextChanged(s: Editable?) = contentChanged()
    })
    setOnEditorActionListener { _, _, _ ->
      onReturn()
      true
    }
    ready = true
  }

  // --- change reporting -------------------------------------------------------------------

  override fun onBeginBatchEdit() {
    super.onBeginBatchEdit()
    batchDepth++
  }

  override fun onEndBatchEdit() {
    super.onEndBatchEdit()
    batchDepth = maxOf(0, batchDepth - 1)
    if (batchDepth == 0 && changedDuringBatch) {
      changedDuringBatch = false
      contentChanged()
    }
  }

  override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
    val base = super.onCreateInputConnection(outAttrs) ?: return null
    // Composition can end without a text change (finishComposingText), so re-check the span.
    return object : InputConnectionWrapper(base, true) {
      override fun finishComposingText(): Boolean = super.finishComposingText().also { contentChanged() }
      override fun setComposingRegion(start: Int, end: Int): Boolean =
        super.setComposingRegion(start, end).also { contentChanged() }
    }
  }

  private fun contentChanged() {
    if (!ready || isApplyingReset || hasPendingReset) return
    if (batchDepth > 0) {
      changedDuringBatch = true
      return
    }
    val editable = text ?: return
    val fullText = editable.toString()
    when (quarantine.classify(fullText, targets)) {
      ImeResetQuarantine.Decision.IGNORE -> return
      ImeResetQuarantine.Decision.RESET_AGAIN -> {
        scheduleReset(quarantine.pendingResetDocument, preservingStaleDocuments = true)
        return
      }
      ImeResetQuarantine.Decision.ACCEPT -> Unit
    }
    val snapshot = currentSnapshot() ?: return
    if (snapshot == lastReported) return
    lastReported = snapshot
    onTextChange(snapshot.committed, snapshot.marked)
  }

  /** Current field text split around the IME composing span. */
  fun currentSnapshot(): ImeTextSnapshot? {
    val editable = text ?: return null
    return ImeTextSnapshot.split(
      editable.toString(),
      BaseInputConnection.getComposingSpanStart(editable),
      BaseInputConnection.getComposingSpanEnd(editable),
    )
  }

  private val isComposing: Boolean
    get() = text?.let { BaseInputConnection.getComposingSpanStart(it) >= 0 } == true

  // --- reset --------------------------------------------------------------------------------

  /**
   * Session reset (target change / explicit reset): replaces the document with [replacement] on
   * the next loop, discards the IME's composition and quarantines stale snapshots.
   */
  fun scheduleReset(replacement: String, preservingStaleDocuments: Boolean = false) {
    val generation = ++resetGeneration
    hasPendingReset = true
    quarantine.beginReset(text?.toString().orEmpty(), replacement, preservingStaleDocuments)
    post {
      if (generation != resetGeneration) return@post
      performReset(replacement)
    }
  }

  private fun performReset(replacement: String) {
    isApplyingReset = true
    try {
      text?.let { BaseInputConnection.removeComposingSpans(it) }
      setText(replacement)
      setSelection(replacement.length)
      if (isAttachedToWindow) imm?.restartInput(this)
      lastReported = ImeTextSnapshot(replacement, null)
    } finally {
      isApplyingReset = false
      hasPendingReset = false
    }
  }

  /**
   * iOS `updateUIView` text sync: after a confirmed mismatch / ASCII input the panel rewrites the
   * field to the accepted text, but never while the IME is composing.
   */
  fun replaceIfNotComposing(replacement: String) {
    post {
      if (hasPendingReset || isComposing || text?.toString() == replacement) return@post
      isApplyingReset = true
      try {
        setText(replacement)
        setSelection(replacement.length)
        if (isAttachedToWindow) imm?.restartInput(this)
        lastReported = ImeTextSnapshot(replacement, null)
      } finally {
        isApplyingReset = false
      }
    }
  }

  // --- selection pinning (iOS textFieldDidChangeSelection) -------------------------------------

  override fun onSelectionChanged(selStart: Int, selEnd: Int) {
    super.onSelectionChanged(selStart, selEnd)
    if (!ready || isApplyingReset || hasPendingReset || isComposing) return
    val length = text?.length ?: return
    if (selStart != length || selEnd != length) setSelection(length)
  }

  // --- focus -----------------------------------------------------------------------------------

  /** Focuses the field and shows the IME on the next loop (iOS `becomeFirstResponder`). */
  fun requestImeFocus() {
    wantsFocus = true
    post {
      if (isFocusSuspended || !isAttachedToWindow) return@post
      requestFocus()
      text?.let { setSelection(it.length) }
      imm?.showSoftInput(this, 0)
    }
  }

  private fun releaseImeFocus() {
    wantsFocus = false
    imm?.hideSoftInputFromWindow(windowToken, 0)
    clearFocus()
  }

  override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
    super.onWindowFocusChanged(hasWindowFocus)
    // Back from another window/app (resume, dialog, IME picker): restore the session keyboard.
    if (hasWindowFocus && wantsFocus && !isFocusSuspended) requestImeFocus()
  }

  override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
    super.onFocusChanged(focused, direction, previouslyFocusedRect)
    if (ready) onFocusStateChanged(focused)
  }

  private object NoActionMode : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = false
    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false
    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false
    override fun onDestroyActionMode(mode: ActionMode?) = Unit
  }

  companion object {
    /** View tag (and Compose test tag) of the field, iOS `os_ime.text_field`. */
    const val TAG = "os_ime.text_field"
  }
}
