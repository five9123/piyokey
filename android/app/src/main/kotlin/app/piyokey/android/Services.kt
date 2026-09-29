package app.piyokey.android

import android.app.Application
import android.content.Context

/**
 * Process-wide service locator. Each area (data, platform, features) exposes its own
 * `by lazy` singletons that read [Services.app], so no single file owns every dependency.
 */
object Services {
  @Volatile private var application: Application? = null

  val app: Application
    get() = checkNotNull(application) { "Services.install() must run in Application.onCreate" }

  val context: Context get() = app

  fun install(application: Application) {
    this.application = application
  }

  /** For JVM/Robolectric-free tests that need a context-less check. */
  val isInstalled: Boolean get() = application != null
}
