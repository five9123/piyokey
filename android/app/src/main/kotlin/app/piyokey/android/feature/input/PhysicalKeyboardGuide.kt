package app.piyokey.android.feature.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.piyokey.android.R
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType

/**
 * iOS `PhysicalKeyboardGuideView`: hardware dubeolsik map with the next key and recommended
 * hand/finger. Renders nothing on phones ([PhysicalKeyboardGuidePolicy]). Pass
 * `nextExpectedKey = null` in recall games (no answer reveal).
 */
@Composable
fun PhysicalKeyboardGuide(nextExpectedKey: Char?, modifier: Modifier = Modifier) {
  if (!PhysicalKeyboardGuidePolicy.isVisible(Piyo.metrics)) return
  val colors = Piyo.colors
  val target = PhysicalDubeolsikLayout.target(nextExpectedKey)
  val shape = RoundedCornerShape(18.dp)
  Column(
    modifier
      .fillMaxWidth()
      .background(colors.card.copy(alpha = 0.96f), shape)
      .border(BorderStroke(1.dp, colors.keyShadow.copy(alpha = colors.keyShadow.alpha * 0.8f)), shape)
      .padding(horizontal = 10.dp, vertical = 9.dp)
      .testTag("physical_keyboard.guide"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    GuideHeader(target)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        PhysicalDubeolsikLayout.rows[0].forEach { PhysicalKey(it, target) }
      }
      Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        PhysicalDubeolsikLayout.rows[1].forEach { PhysicalKey(it, target) }
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        UtilityKey("⇧", "physical_keyboard.shift.left", target?.shiftHand == PhysicalKeyboardHand.LEFT)
        PhysicalDubeolsikLayout.rows[2].forEach { PhysicalKey(it, target) }
        UtilityKey("⇧", "physical_keyboard.shift.right", target?.shiftHand == PhysicalKeyboardHand.RIGHT)
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)) {
        UtilityKey(
          stringResource(R.string.physical_keyboard_space),
          "physical_keyboard.space",
          target?.expected == ' ',
          maxWidth = 210.dp,
        )
        UtilityKey("⌫", "physical_keyboard.backspace", highlighted = false, maxWidth = 58.dp)
      }
    }
  }
}

@Composable
private fun GuideHeader(target: PhysicalKeyboardTarget?) {
  val colors = Piyo.colors
  // Both lines are always laid out so the guide height never jumps (iOS reserved target).
  val shown = target ?: PhysicalKeyboardTarget(null, ' ', requiresShift = false, shiftHand = null)
  val hand = stringResource(shown.hand.labelRes)
  val finger = stringResource(shown.finger.labelRes)
  val keyName = shown.key?.latin?.toString() ?: stringResource(R.string.physical_keyboard_space)
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Column(
      Modifier
        .alpha(if (target == null) 0f else 1f)
        .then(if (target == null) Modifier.semantics { hideFromAccessibility() } else Modifier)
        .testTag("physical_keyboard.guide.instruction"),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        stringResource(R.string.physical_keyboard_next_key_format, shown.expected.toString(), keyName),
        style = PiyoType.caption().copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
      )
      Text(
        if (shown.requiresShift) {
          stringResource(
            R.string.physical_keyboard_shift_finger_format,
            stringResource((shown.shiftHand ?: PhysicalKeyboardHand.BOTH).labelRes),
            hand,
            finger,
          )
        } else {
          stringResource(R.string.physical_keyboard_finger_format, hand, finger)
        },
        style = PiyoType.caption2().copy(fontWeight = FontWeight.SemiBold, color = colors.secondary),
        maxLines = 1,
      )
    }
    Text(
      stringResource(R.string.physical_keyboard_ready),
      style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.mutedInk),
      textAlign = TextAlign.Center,
      maxLines = 2,
      modifier = Modifier
        .alpha(if (target == null) 1f else 0f)
        .then(if (target != null) Modifier.semantics { hideFromAccessibility() } else Modifier)
        .testTag("physical_keyboard.guide.ready"),
    )
  }
}

@Composable
private fun RowScope.PhysicalKey(key: PhysicalKeyboardKeySpec, target: PhysicalKeyboardTarget?) {
  val colors = Piyo.colors
  val highlighted = target?.key?.latin == key.latin
  val handColor = if (key.hand == PhysicalKeyboardHand.LEFT) colors.secondary else colors.accent
  val shape = RoundedCornerShape(8.dp)
  val ink = if (highlighted) Color.White else colors.ink
  val description = stringResource(
    R.string.physical_keyboard_key_accessibility_format,
    key.baseJamo.toString(),
    key.latin.toString(),
    stringResource(key.hand.labelRes),
    stringResource(key.finger.labelRes),
  )
  Column(
    Modifier
      .weight(1f)
      .heightIn(min = 36.dp)
      .background(if (highlighted) handColor else colors.backgroundTop, shape)
      .border(BorderStroke(if (highlighted) 2.dp else 1.dp, if (highlighted) handColor else colors.keyShadow), shape)
      .clearAndSetSemantics {
        contentDescription = description
        selected = highlighted
      }
      .testTag("physical_keyboard.key.${key.latin}"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Box(contentAlignment = Alignment.BottomCenter) {
      Text(key.baseJamo.toString(), color = ink, fontSize = Piyo.sp(15f), fontWeight = FontWeight.Bold)
      if (key.isHomePosition) {
        Box(
          Modifier
            .padding(bottom = 2.dp)
            .size(width = 10.dp, height = 2.dp)
            .background(if (highlighted) Color.White.copy(alpha = 0.85f) else colors.mutedInk.copy(alpha = 0.48f), CircleShape),
        )
      }
    }
    Text(key.latin.toString(), color = ink, fontSize = Piyo.sp(8f), fontWeight = FontWeight.SemiBold)
  }
}

@Composable
private fun RowScope.UtilityKey(title: String, tag: String, highlighted: Boolean, maxWidth: Dp = 46.dp) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(8.dp)
  Box(
    Modifier
      .weight(1f, fill = false)
      .widthIn(max = maxWidth)
      .fillMaxWidth()
      .heightIn(min = 34.dp)
      .background(if (highlighted) colors.secondary else colors.backgroundTop, shape)
      .border(BorderStroke(if (highlighted) 2.dp else 1.dp, if (highlighted) colors.secondary else colors.keyShadow), shape)
      .semantics { selected = highlighted }
      .testTag(tag),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      title,
      style = PiyoType.caption2().copy(fontWeight = FontWeight.Bold, color = if (highlighted) Color.White else colors.mutedInk),
      maxLines = 1,
    )
  }
}
