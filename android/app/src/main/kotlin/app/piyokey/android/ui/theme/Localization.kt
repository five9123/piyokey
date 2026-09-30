package app.piyokey.android.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.piyokey.android.Services
import app.piyokey.android.data.settings.AppLanguage
import app.piyokey.android.data.settings.AppSettings
import java.util.Locale

/**
 * In-app UI language (ja/en/es/de/fr) independent of the device locale, like iOS
 * `AppLocalization`. Compose `stringResource` works automatically inside [LocalizedApp];
 * non-Compose code uses [L].
 */
object L {
  fun localizedContext(base: Context, language: AppLanguage = AppSettings.currentLanguage): Context {
    val configuration = Configuration(base.resources.configuration)
    configuration.setLocale(language.locale)
    return base.createConfigurationContext(configuration)
  }

  val resources: Resources get() = localizedContext(Services.context).resources

  val locale: Locale get() = AppSettings.currentLanguage.locale

  fun string(id: Int): String = resources.getString(id)

  fun string(id: Int, vararg args: Any): String = resources.getString(id, *args)

  fun plural(id: Int, count: Int, vararg args: Any): String =
    resources.getQuantityString(id, count, *(if (args.isEmpty()) arrayOf<Any>(count) else args))

  /** Brand name for the app language: `ピヨキー` for ja, `typee` otherwise (PRD §14). */
  fun brandName(language: AppLanguage = AppSettings.currentLanguage) =
    if (language == AppLanguage.JAPANESE) "ピヨキー" else "typee"
}

@Composable
fun LocalizedApp(language: AppLanguage, content: @Composable () -> Unit) {
  val base = LocalContext.current
  // Resolve Activity-backed locals from the real Activity context before it is replaced by a
  // configuration context (permission/SAF launchers, billing and back handling need them).
  val activity = androidx.activity.compose.LocalActivity.current
  val registryOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current
  val backOwner = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
  // Rebuild on configuration changes too (rotation, density, dark mode are handled in-process).
  val configuration = LocalConfiguration.current
  val localized = remember(base, language, configuration) { L.localizedContext(base, language) }
  CompositionLocalProvider(
    LocalContext provides localized,
    LocalConfiguration provides localized.resources.configuration,
    androidx.activity.compose.LocalActivity provides activity,
    *listOfNotNull(
      registryOwner?.let { androidx.activity.compose.LocalActivityResultRegistryOwner provides it },
      backOwner?.let { androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides it },
    ).toTypedArray(),
  ) {
    content()
  }
}
