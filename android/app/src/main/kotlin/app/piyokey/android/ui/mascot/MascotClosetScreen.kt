package app.piyokey.android.ui.mascot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.piyokey.android.R
import app.piyokey.android.data.mascot.MascotCompanionStore
import app.piyokey.android.data.mascot.MascotMood
import app.piyokey.android.data.mascot.MascotProp
import app.piyokey.android.data.mascot.MascotRules
import app.piyokey.android.data.mascot.MascotSelection
import app.piyokey.android.data.mascot.MascotStage
import app.piyokey.android.data.mascot.MascotStore
import app.piyokey.android.data.mascot.MascotUnlockState
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.nav.LocalTabNavigator
import app.piyokey.android.ui.nav.Route
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoBackground
import app.piyokey.android.ui.theme.PiyoCard
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.ui.theme.centeredContent
import java.text.NumberFormat

/**
 * iOS `MascotClosetView` (a sheet on iOS). Push on the app navigator (default) or, with
 * [inTab] = true, inside the current tab; "Done" pops whichever navigator presented it.
 */
class MascotClosetRoute(private val inTab: Boolean = false) : Route {
  @Composable
  override fun Content() {
    val navigator = if (inTab) LocalTabNavigator.current else LocalAppNavigator.current
    MascotClosetScreen(onDone = { navigator.pop() })
  }
}

@Composable
fun MascotClosetScreen(onDone: () -> Unit, modifier: Modifier = Modifier, store: MascotCompanionStore = MascotStore) {
  val signals by store.signals.collectAsState()
  val unlockedProps by store.unlockedProps.collectAsState()
  val selection by store.selection.collectAsState()
  val name by store.name.collectAsState()
  val typedJamo by store.typedJamoCount.collectAsState()
  val colors = Piyo.colors
  val defaultName = stringResource(R.string.mascot_default_name)

  val evaluated = store.evaluatedUnlocks(signals)
  val unlocks = MascotRules.mergeEarned(evaluated, unlockedProps)
  val fingerprint = "${signals.currentStreak}:${signals.clearedChapters}:${signals.installedDeckCount}:" +
    signals.installedDeckTags.sorted().joinToString("|")
  LaunchedEffect(fingerprint) { store.registerUnlocks(store.evaluatedUnlocks(signals)) }

  val previewStage = MascotRules.closetPreviewStage(signals.stage)
  val locale = LocalConfiguration.current.locales[0]
  val numberFormat = remember(locale) { NumberFormat.getIntegerInstance(locale) }

  PiyoBackground(modifier) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
      Row(
        Modifier.centeredContent(Piyo.metrics.formContentMaxWidth).padding(horizontal = Piyo.metrics.horizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(stringResource(R.string.closet_title), style = PiyoType.title2(), modifier = Modifier.weight(1f))
        TextButton(onClick = onDone, modifier = Modifier.defaultMinSize(minHeight = 44.dp)) {
          Text(stringResource(R.string.closet_done), style = PiyoType.headline().copy(color = colors.accent))
        }
      }
      LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Piyo.metrics.horizontalPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        item {
          ClosetSection(null) {
            Row(
              Modifier.testTag("closet.preview").semantics(mergeDescendants = true) {},
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
              Box(Modifier.size(116.dp, 148.dp), contentAlignment = Alignment.Center) {
                GrowingMascot(mood = MascotMood.IDLE, showsNameTag = true, interactive = true, size = 76.dp, calm = true, store = store)
              }
              Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(store.displayName(name, defaultName), style = PiyoType.title3().copy(fontWeight = FontWeight.Black))
                Text(stringResource(R.string.closet_preview_detail), style = PiyoType.caption().copy(color = colors.mutedInk))
              }
            }
          }
        }

        item {
          ClosetSection(stringResource(R.string.closet_name_section)) {
            TextField(
              value = name,
              onValueChange = store::setName,
              placeholder = { Text(defaultName) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
              colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = colors.ink,
                unfocusedTextColor = colors.ink,
                cursorColor = colors.accent,
                focusedIndicatorColor = colors.accent,
                unfocusedIndicatorColor = colors.mutedInk.copy(alpha = 0.3f),
              ),
              textStyle = PiyoType.body(),
              modifier = Modifier.fillMaxWidth().testTag("mascot.name"),
            )
          }
        }

        item {
          ClosetSection(stringResource(R.string.growth_cond_title)) {
            for ((stage, key, threshold) in MascotRules.growthConditions) {
              GrowthRow(stage, key, achieved = signals.clearedChapters >= threshold)
            }
            Text(stringResource(R.string.growth_cond_note), style = PiyoType.caption().copy(color = colors.mutedInk))
          }
        }

        item {
          ClosetSection(stringResource(R.string.growth_axis_title)) {
            GrowthAxisRow(
              stringResource(R.string.growth_axis_typed),
              numberFormat.format(typedJamo),
              typedJamo / 12_000f,
              colors.accent,
            )
            GrowthAxisRow(
              stringResource(R.string.growth_axis_streak),
              numberFormat.format(signals.longestStreak),
              signals.longestStreak / 30f,
              MascotColors.systemOrange,
            )
            GrowthAxisRow(
              stringResource(R.string.growth_axis_activity),
              stringResource(R.string.growth_axis_days, signals.activeDaysInLastWeek),
              signals.activeDaysInLastWeek / 7f,
              colors.success,
            )
          }
        }

        item {
          ClosetSection(stringResource(R.string.closet_prop_section)) {
            SelectionRow(
              label = stringResource(R.string.closet_auto),
              selected = selection == MascotSelection.Automatic,
              stage = previewStage,
              previewProp = MascotProp.NONE,
              identifier = "closet.selection.automatic",
              onClick = { store.selectProp(MascotSelection.Automatic) },
            )
            SelectionRow(
              label = stringResource(R.string.closet_bare),
              selected = selection == MascotSelection.None,
              stage = previewStage,
              previewProp = MascotProp.NONE,
              identifier = "closet.selection.none",
              onClick = { store.selectProp(MascotSelection.None) },
            )
            for (state in unlocks) {
              PropRow(state, previewStage, selection) { store.selectProp(MascotSelection.Fixed(state.prop)) }
            }
          }
        }
        item { Spacer(Modifier.height(24.dp)) }
      }
    }
  }
}

@Composable
private fun ClosetSection(header: String?, content: @Composable () -> Unit) {
  Column(Modifier.centeredContent(Piyo.metrics.formContentMaxWidth).padding(top = 10.dp)) {
    if (header != null) {
      Text(
        header,
        style = PiyoType.footnote().copy(color = Piyo.colors.mutedInk, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
      )
    }
    PiyoCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), cornerRadius = 14.dp) {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
  }
}

@Composable
private fun GrowthRow(stage: MascotStage, conditionKey: String, achieved: Boolean) {
  val colors = Piyo.colors
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
      ChickMascot(mood = if (achieved) MascotMood.HAPPY else MascotMood.IDLE, stage = stage, size = 32.dp, calm = true)
    }
    Text(
      stringResource(closetStringRes(conditionKey)),
      style = PiyoType.subheadline().copy(fontWeight = FontWeight.SemiBold, color = if (achieved) colors.ink else colors.mutedInk),
      modifier = Modifier.weight(1f),
    )
    Icon(
      if (achieved) Icons.Filled.Verified else Icons.Filled.Lock,
      contentDescription = null,
      tint = if (achieved) colors.success else colors.mutedInk,
    )
  }
}

@Composable
private fun GrowthAxisRow(label: String, value: String, progress: Float, tint: Color) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(label, style = PiyoType.subheadline().copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
      Text(
        value,
        style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Piyo.colors.mutedInk),
      )
    }
    LinearProgressIndicator(
      progress = { progress.coerceIn(0f, 1f) },
      color = tint,
      trackColor = Piyo.colors.mutedInk.copy(alpha = 0.18f),
      drawStopIndicator = {},
      gapSize = 0.dp,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

@Composable
private fun SelectionRow(
  label: String,
  selected: Boolean,
  stage: MascotStage,
  previewProp: MascotProp,
  identifier: String,
  onClick: () -> Unit,
) {
  Row(
    Modifier
      .fillMaxWidth()
      .clickable(role = Role.Button, onClick = onClick)
      .testTag(identifier),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
      ChickMascot(mood = MascotMood.HAPPY, stage = stage, prop = previewProp, size = 34.dp, calm = true)
    }
    Text(label, style = PiyoType.body(), modifier = Modifier.weight(1f))
    if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Piyo.colors.accent)
  }
}

@Composable
private fun PropRow(state: MascotUnlockState, stage: MascotStage, selection: MascotSelection, onSelect: () -> Unit) {
  val label = stringResource(closetStringRes(state.prop.labelKey))
  if (state.isUnlocked) {
    SelectionRow(
      label = label,
      selected = selection == MascotSelection.Fixed(state.prop),
      stage = stage,
      previewProp = state.prop,
      identifier = "closet.selection.${state.prop.raw}",
      onClick = onSelect,
    )
    return
  }
  val colors = Piyo.colors
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Box(Modifier.size(46.dp).grayscale(alpha = 0.45f), contentAlignment = Alignment.Center) {
      ChickMascot(stage = stage, prop = state.prop, size = 34.dp, calm = true)
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(label, style = PiyoType.body().copy(color = colors.mutedInk))
      Text(stringResource(closetStringRes(state.conditionKey)), style = PiyoType.caption2().copy(color = colors.mutedInk))
    }
    Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.mutedInk)
  }
}

/** `.saturation(0).opacity(alpha)` over the whole subtree (API 26 safe, no RenderEffect). */
private fun Modifier.grayscale(alpha: Float): Modifier = drawWithContent {
  val paint = Paint().apply {
    colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    this.alpha = alpha
  }
  // Include the mascot's overflow (its canvas is larger than the row slot).
  val bounds = Rect(-size.width, -size.height, size.width * 2, size.height * 2)
  drawIntoCanvas { canvas ->
    canvas.saveLayer(bounds, paint)
    drawContent()
    canvas.restore()
  }
}

/** iOS localisation key → generated resource id for the strings this screen uses. */
internal fun closetStringRes(key: String): Int = when (key) {
  "closet.bare" -> R.string.closet_bare
  "closet.prop_lightstick" -> R.string.closet_prop_lightstick
  "closet.prop_gradcap" -> R.string.closet_prop_gradcap
  "closet.prop_headphones" -> R.string.closet_prop_headphones
  "closet.prop_travel" -> R.string.closet_prop_travel
  "closet.prop_cafe" -> R.string.closet_prop_cafe
  "closet.prop_microphone" -> R.string.closet_prop_microphone
  "closet.prop_balloon" -> R.string.closet_prop_balloon
  "closet.prop_food" -> R.string.closet_prop_food
  "closet.prop_ribbon" -> R.string.closet_prop_ribbon
  "closet.prop_glasses" -> R.string.closet_prop_glasses
  "closet.prop_game_center" -> R.string.closet_prop_game_center
  "closet.cond_lightstick" -> R.string.closet_cond_lightstick
  "closet.cond_gradcap" -> R.string.closet_cond_gradcap
  "closet.cond_headphones" -> R.string.closet_cond_headphones
  "closet.cond_travel" -> R.string.closet_cond_travel
  "closet.cond_cafe" -> R.string.closet_cond_cafe
  "closet.cond_microphone" -> R.string.closet_cond_microphone
  "closet.cond_balloon" -> R.string.closet_cond_balloon
  "closet.cond_food" -> R.string.closet_cond_food
  "closet.cond_ribbon" -> R.string.closet_cond_ribbon
  "closet.cond_glasses" -> R.string.closet_cond_glasses
  "closet.cond_game_center" -> R.string.closet_cond_game_center
  "growth.cond_hatch" -> R.string.growth_cond_hatch
  "growth.cond_chick" -> R.string.growth_cond_chick
  "growth.cond_rooster" -> R.string.growth_cond_rooster
  else -> error("Unknown closet string key: $key")
}

