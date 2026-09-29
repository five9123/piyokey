package app.piyokey.android.feature.discover

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.piyokey.android.data.AppData
import app.piyokey.android.data.decks.InstalledDeckSource
import app.piyokey.android.platform.analytics.Telemetry
import app.piyokey.android.ui.nav.Navigator
import app.piyokey.core.domain.library.DiscoverFilters

/** Cross-feature entry points into Discover (contract: `DiscoverLauncher.deckDetail`). */
object DiscoverLauncher {
  init {
    installDeckDownloadAnalytics()
  }

  /** Pushes the catalog deck detail (preview, install/update/play, related decks). */
  fun deckDetail(nav: Navigator, deckId: String) {
    installDeckDownloadAnalytics()
    AppData.catalog.loadIfNeeded()
    nav.push(DeckDetailRoute(deckId))
  }

  /**
   * iOS `DeckLibrary.install` captures `deck_downloaded` for every catalog install (Discover,
   * My Decks update, Home recommendation). Idempotent; call from any entry point.
   */
  fun installDeckDownloadAnalytics() {
    if (AppData.onDeckDownloaded != null) return
    AppData.onDeckDownloaded = { deck, source ->
      Telemetry.deckDownloaded(
        deckSource = if (source == InstalledDeckSource.BUNDLE) "bundled" else "catalog",
        deckCategory = Telemetry.deckCategory(deck.tags, deck.level),
      )
    }
  }
}

/** App-lifetime Discover filters (iOS `DiscoverViewModel` is owned by the app root). */
internal object DiscoverModel {
  var filters by mutableStateOf(DiscoverFilters())
}
