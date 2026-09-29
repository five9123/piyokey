package app.piyokey.android.feature.input

import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.piyokey.android.R
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.nav.Route
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.ui.theme.PrimaryButton
import app.piyokey.android.ui.theme.centeredContent

/**
 * Port of iOS `KoreanKeyboardGuideView` with Android steps (Settings → System → Languages &
 * input → On-screen keyboard → add Korean) and a button that opens
 * `Settings.ACTION_INPUT_METHOD_SETTINGS`. The iOS mascot header is replaced by a keyboard badge
 * until the mascot view is ported.
 */
@Composable
fun KoreanKeyboardGuide(onClose: () -> Unit, modifier: Modifier = Modifier) {
  val colors = Piyo.colors
  val context = LocalContext.current
  Column(
    modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .centeredContent(Piyo.metrics.readableContentMaxWidth)
      .padding(20.dp)
      .testTag(KOREAN_KEYBOARD_GUIDE_TAG),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      TextButton(onClick = onClose) {
        Text(stringResource(R.string.common_close), style = PiyoType.headline().copy(color = colors.accent))
      }
    }
    Box(
      Modifier.size(72.dp).background(colors.accentSoft, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Filled.Keyboard, contentDescription = null, tint = colors.accent, modifier = Modifier.size(38.dp))
    }
    Text(
      stringResource(R.string.os_ime_guide_title),
      style = PiyoType.title2(),
      textAlign = TextAlign.Center,
    )
    Text(
      stringResource(R.string.keyboard_guide_android_subtitle),
      style = PiyoType.subheadline().copy(color = colors.mutedInk),
      textAlign = TextAlign.Center,
    )
    GuideStep(1, Icons.Filled.Settings, R.string.keyboard_guide_android_step_1_title, R.string.keyboard_guide_android_step_1_detail)
    GuideStep(2, Icons.Filled.Keyboard, R.string.keyboard_guide_android_step_2_title, R.string.keyboard_guide_android_step_2_detail)
    GuideStep(3, Icons.Filled.Language, R.string.keyboard_guide_android_step_3_title, R.string.keyboard_guide_android_step_3_detail)
    PrimaryButton(
      text = stringResource(R.string.keyboard_guide_android_open_settings),
      onClick = {
        try {
          context.startActivity(KoreanKeyboardAvailability.keyboardSettingsIntent())
        } catch (_: ActivityNotFoundException) {
          context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
              .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
          )
        }
      },
      icon = Icons.Filled.Settings,
      modifier = Modifier.fillMaxWidth().testTag("os_ime.guide.open_settings"),
    )
  }
}

const val KOREAN_KEYBOARD_GUIDE_TAG = "os_ime.guide.sheet"

@Composable
private fun GuideStep(step: Int, icon: ImageVector, title: Int, detail: Int) {
  val colors = Piyo.colors
  Box(Modifier.fillMaxWidth()) {
    Row(
      Modifier
        .fillMaxWidth()
        .background(colors.card, RoundedCornerShape(19.dp))
        .padding(15.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Box(Modifier.size(34.dp).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
        Text(step.toString(), style = PiyoType.headline().copy(color = Color.White, fontWeight = FontWeight.Black))
      }
      Icon(icon, contentDescription = null, tint = colors.secondary, modifier = Modifier.width(38.dp).size(28.dp))
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stringResource(title), style = PiyoType.subheadline().copy(fontWeight = FontWeight.Bold))
        Text(stringResource(detail), style = PiyoType.caption().copy(color = colors.mutedInk))
      }
    }
    Text(
      stringResource(R.string.os_ime_guide_preview),
      style = PiyoType.style(8f, FontWeight.Bold).copy(color = colors.mutedInk),
      modifier = Modifier
        .offset(x = 10.dp, y = (-7).dp)
        .background(colors.backgroundBottom, CircleShape)
        .padding(horizontal = 6.dp, vertical = 3.dp),
    )
  }
}

/** iOS presents the guide as a sheet (Settings, onboarding). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KoreanKeyboardGuideSheet(onDismiss: () -> Unit) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    containerColor = Piyo.colors.backgroundTop,
  ) {
    KoreanKeyboardGuide(onClose = onDismiss)
  }
}

/** Full-screen destination for the guide (push on `LocalAppNavigator` or a tab navigator). */
class KoreanKeyboardGuideRoute : Route {
  @Composable
  override fun Content() {
    val navigator = LocalAppNavigator.current
    Box(Modifier.fillMaxSize().background(Piyo.colors.backgroundTop).statusBarsPadding()) {
      KoreanKeyboardGuide(onClose = { navigator.pop() })
    }
  }
}
