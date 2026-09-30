package app.piyokey.android.data.settings

import android.content.Context
import android.content.SharedPreferences
import app.piyokey.android.Services
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Key/value preferences that mirror iOS `UserDefaults` keys one-to-one
 * (e.g. `settings.theme`, `keyboard.input_mode_default`). All writes persist immediately.
 */
object Prefs {
  const val FILE = "piyokey.preferences"

  val shared: SharedPreferences by lazy {
    Services.app.getSharedPreferences(FILE, Context.MODE_PRIVATE)
  }
}

/** Observable preference value backed by [Prefs.shared]. */
abstract class PrefValue<T>(
  val key: String,
  protected val default: T,
  private val prefs: () -> SharedPreferences = { Prefs.shared },
) {
  private val state by lazy { MutableStateFlow(read(prefs())) }

  val flow: StateFlow<T> get() = state.asStateFlow()

  var value: T
    get() = state.value
    set(newValue) {
      prefs().edit().also { write(it, newValue) }.apply()
      state.value = newValue
    }

  val isSet: Boolean get() = prefs().contains(key)

  fun reload() {
    state.value = read(prefs())
  }

  protected abstract fun read(prefs: SharedPreferences): T
  protected abstract fun write(editor: SharedPreferences.Editor, value: T)
}

class BoolPref(key: String, default: Boolean) : PrefValue<Boolean>(key, default) {
  override fun read(prefs: SharedPreferences) = prefs.getBoolean(key, default)
  override fun write(editor: SharedPreferences.Editor, value: Boolean) { editor.putBoolean(key, value) }
}

class IntPref(key: String, default: Int) : PrefValue<Int>(key, default) {
  override fun read(prefs: SharedPreferences) = prefs.getInt(key, default)
  override fun write(editor: SharedPreferences.Editor, value: Int) { editor.putInt(key, value) }
}

class StringPref(key: String, default: String) : PrefValue<String>(key, default) {
  override fun read(prefs: SharedPreferences) = prefs.getString(key, default) ?: default
  override fun write(editor: SharedPreferences.Editor, value: String) { editor.putString(key, value) }
}

class NullableStringPref(key: String) : PrefValue<String?>(key, null) {
  override fun read(prefs: SharedPreferences): String? = prefs.getString(key, null)
  override fun write(editor: SharedPreferences.Editor, value: String?) {
    if (value == null) editor.remove(key) else editor.putString(key, value)
  }
}

class StringListPref(key: String) : PrefValue<List<String>>(key, emptyList()) {
  override fun read(prefs: SharedPreferences): List<String> =
    prefs.getString(key, null)?.let { runCatching { kotlinx.serialization.json.Json.decodeFromString<List<String>>(it) }.getOrNull() }
      ?: emptyList()
  override fun write(editor: SharedPreferences.Editor, value: List<String>) {
    editor.putString(key, kotlinx.serialization.json.Json.encodeToString(value))
  }
}
