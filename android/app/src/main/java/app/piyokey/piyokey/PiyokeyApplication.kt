package app.piyokey.piyokey

import android.app.Application
import app.piyokey.core.platform.PlayGamesInitializer

class PiyokeyApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    PlayGamesInitializer.initializeIfConfigured(this)
  }
}
