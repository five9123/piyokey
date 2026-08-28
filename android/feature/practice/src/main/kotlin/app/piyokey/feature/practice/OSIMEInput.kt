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
) = KoreanIMEInput(
  visibleText = visibleText,
  onText = { snapshot -> onEvent(PracticeSessionEvent.IMEText(snapshot.committedText, snapshot.composingText)) },
  modifier = modifier,
  testTag = "practice-os-ime-field",
)

@Composable
fun KoreanIMEInput(
  visibleText: String,
  onText: (IMETextSnapshot) -> Unit,
  modifier: Modifier = Modifier,
  testTag: String = "korean-os-ime-field",
) {
  val context = LocalContext.current
  val controller = remember { OSIMEEditController() }
  AndroidView(
    modifier = modifier.testTag(testTag),
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
          context.getSystemService(InputMethodManager::class.java).showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
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

  fun synchronize(view: EditText, expected: String) {
    if (BaseInputConnection.getComposingSpanStart(view.editableText) >= 0) return
    if (view.text.toString() == expected) return
    isSynchronizing = true
    view.setText(expected)
    view.setSelection(view.text.length)
    isSynchronizing = false
  }
}
