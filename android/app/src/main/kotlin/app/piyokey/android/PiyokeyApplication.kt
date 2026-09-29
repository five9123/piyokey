package app.piyokey.android

import android.app.Application

class PiyokeyApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    Services.install(this)
    app.piyokey.android.platform.PlatformServices.install()
    app.piyokey.android.data.AppData.proAccess = { app.piyokey.android.platform.billing.ProStore.hasAccess.value }
    app.piyokey.android.data.AppData.observeProcessLifecycle()
    app.piyokey.android.feature.discover.DiscoverLauncher.installDeckDownloadAnalytics()
  }
}
