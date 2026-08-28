package app.piyokey.feature.practice

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.insertTextAtCursor
import androidx.compose.ui.semantics.requestFocus
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.viewinterop.AndroidView
import app.piyokey.core.session.PracticeSessionEvent

data class IMETextSnapshot(
  val committedText: String,
  val composingText: String?,
)

internal object IMETextSnapshotReader {
  fun read(editable: Editable): IMETextSnapshot {
    val text = editable.toString()
    val start = BaseInputConnection.getComposingSpanStart(editable)
    val end = BaseInputConnection.getComposingSpanEnd(editable)
    return read(text, start, end)
  }

  fun read(text: String, start: Int, end: Int): IMETextSnapshot {
    return if (start >= 0 && end > start && end <= text.length) {
      IMETextSnapshot(
        committedText = text.substring(0, start) + text.substring(end),
        composingText = text.substring(start, end),
      )
    } else {
      IMETextSnapshot(text, null)
    }
  }
}

fun hasKoreanInputMethod(context: Context): Boolean {
  val manager = context.getSystemService(InputMethodManager::class.java)
  return manager.enabledInputMethodList.any { info ->
    manager.getEnabledInputMethodSubtypeList(info, true).any { subtype ->
      subtype.locale.substringBefore('_').substringBefore('-').equals("ko", ignoreCase = true) ||
        subtype.languageTag.substringBefore('-').equals("ko", ignoreCase = true)
    }
  }
}

@Composable
internal fun OSIMEInput(
  visibleText: String,
  onEvent: (PracticeSessionEvent.IMEText) -> Unit,
  modifier: Modifier = Modifier,
  showSoftwareKeyboard: Boolean = true,
) = KoreanIMEInput(
  visibleText = visibleText,
  onText = { snapshot -> onEvent(PracticeSessionEvent.IMEText(snapshot.committedText, snapshot.composingText)) },
  modifier = modifier,
  testTag = "practice-os-ime-field",
  showSoftwareKeyboard = showSoftwareKeyboard,
)

@Composable
fun KoreanIMEInput(
  visibleText: String,
  onText: (IMETextSnapshot) -> Unit,
  modifier: Modifier = Modifier,
  testTag: String = "korean-os-ime-field",
  showSoftwareKeyboard: Boolean = true,
) {
  val context = LocalContext.current
  val controller = remember { OSIMEEditController() }
  AndroidView(
    modifier = modifier
      .testTag(testTag)
      .semantics {
        editableText = AnnotatedString(visibleText)
        requestFocus { controller.requestFocus() }
        insertTextAtCursor { value -> controller.insertText(value.text) }
        setText { value -> controller.replaceText(value.text) }
      },
    factory = {
      EditText(context).apply {
        setSingleLine(false)
        setTextColor(Color.TRANSPARENT)
        setHintTextColor(Color.TRANSPARENT)
        setBackgroundColor(Color.TRANSPARENT)
        isCursorVisible = false
        alpha = 0.02f
        inputType = android.text.InputType.TYPE_CLASS_TEXT or
          android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
          android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
        addTextChangedListener(object : TextWatcher {
          override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
          override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
          override fun afterTextChanged(editable: Editable) {
            if (controller.isSynchronizing) return
            val snapshot = IMETextSnapshotReader.read(editable)
            onText(snapshot)
          }
        })
        controller.editText = this
        post {
          requestFocus()
          setSelection(text.length)
          if (showSoftwareKeyboard) {
            context.getSystemService(InputMethodManager::class.java)
              .showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
          }
        }
      }
    },
    update = { editText -> controller.synchronize(editText, visibleText) },
  )
  LaunchedEffect(visibleText) {
    controller.editText?.let { controller.synchronize(it, visibleText) }
  }
}

private class OSIMEEditController {
  var editText: EditText? = null
  var isSynchronizing = false

  fun requestFocus(): Boolean = editText?.requestFocus() ?: false

  fun insertText(value: String): Boolean {
    val view = editText ?: return false
    val cursor = view.selectionStart.coerceIn(0, view.text.length)
    view.editableText.insert(cursor, value)
    view.setSelection(cursor + value.length)
    return true
  }

  fun replaceText(value: String): Boolean {
    val view = editText ?: return false
    view.requestFocus()
    view.setText(value)
    view.setSelection(view.text.length)
    return true
  }

  fun synchronize(view: EditText, expected: String) {
    if (BaseInputConnection.getComposingSpanStart(view.editableText) >= 0) return
    if (view.text.toString() == expected) return
    isSynchronizing = true
    view.setText(expected)
    view.setSelection(view.text.length)
    isSynchronizing = false
  }
}
