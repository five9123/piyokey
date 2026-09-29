package app.piyokey.android.feature.library

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.piyokey.android.Services
import app.piyokey.android.data.AppData
import app.piyokey.android.platform.files.DocumentIO
import app.piyokey.android.ui.nav.Navigator
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Cross-feature entry points into My Page / review deck / `.typedeck` import. */
object LibraryLauncher {
  /** Process-wide document coordinator (iOS `PiyoDeckDocumentCoordinator` owned by the app root). */
  val documents: PiyoDeckImportCoordinator by lazy {
    PiyoDeckImportCoordinator(AppData.pendingImports, { AppData.bundled.deckSchema }, AppData.scope).also { coordinator ->
      AppData.scope.launch { coordinator.resumePendingIfNeeded() }
    }
  }

  /** My Page badge flag: an import preview or notice is waiting. */
  val hasPendingDocument: StateFlow<Boolean> get() = documents.hasPendingDocument

  /** Set by [importDocument]; My Page opens the system picker when it next appears. */
  internal var importRequested by mutableStateOf(false)

  /** Pushes the review deck list inside the current tab. */
  fun reviewDeck(nav: Navigator) {
    nav.push(ReviewDeckRoute())
  }

  /** Shows My Page's deck list (pops the My Page tab to its root; callers select the tab). */
  fun myDecks(nav: Navigator) {
    nav.popToRoot()
  }

  /** Opens the `.typedeck` picker from My Page (callers should select the My Page tab). */
  fun importDocument(nav: Navigator) {
    nav.popToRoot()
    importRequested = true
  }

  /** VIEW/SEND intent carrying a `.typedeck` (iOS `onOpenURL`). Staged immediately, previewed on My Page. */
  fun handleIncomingIntent(intent: Intent) {
    val uri = DocumentIO.uri(intent) ?: return
    val resolver = Services.context.contentResolver
    AppData.scope.launch { documents.receive { resolver.openInputStream(uri) } }
  }
}
