package app.piyokey.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class PhysicalKeyboardHand {
  LEFT,
  RIGHT,
  BOTH;

  val opposite: PhysicalKeyboardHand
    get() = when (this) {
      LEFT -> RIGHT
      RIGHT -> LEFT
      BOTH -> BOTH
    }
}

enum class PhysicalKeyboardFinger { LITTLE, RING, MIDDLE, INDEX, THUMB }

data class PhysicalKeyboardKeySpec(
  val latin: Char,
  val baseJamo: Char,
  val shiftedJamo: Char? = null,
  val hand: PhysicalKeyboardHand,
  val finger: PhysicalKeyboardFinger,
  val isHomePosition: Boolean = false,
)

data class PhysicalKeyboardTarget(
  val key: PhysicalKeyboardKeySpec?,
  val expected: Char,
  val requiresShift: Boolean,
  val shiftHand: PhysicalKeyboardHand?,
) {
  val hand: PhysicalKeyboardHand get() = key?.hand ?: PhysicalKeyboardHand.BOTH
  val finger: PhysicalKeyboardFinger get() = key?.finger ?: PhysicalKeyboardFinger.THUMB
}

object PhysicalDubeolsikLayout {
  val rows: List<List<PhysicalKeyboardKeySpec>> = listOf(
    listOf(
      key('Q', 'ㅂ', 'ㅃ', PhysicalKeyboardHand.LEFT, PhysicalKeyboardFinger.LITTLE),
      key('W', 'ㅈ', 'ㅉ', PhysicalKeyboardHand.LEFT, PhysicalKeyboardFinger.RING),
      key('E', 'ㄷ', 'ㄸ', PhysicalKeyboardHand.LEFT, PhysicalKeyboardFinger.MIDDLE),
      key('R', 'ㄱ', 'ㄲ', PhysicalKeyboardHand.LEFT, PhysicalKeyboardFinger.INDEX),
      key('T', 'ㅅ', 'ㅆ', PhysicalKeyboardHand.LEFT, PhysicalKeyboardFinger.INDEX),
      key('Y', 'ㅛ', hand = PhysicalKeyboardHand.RIGHT, finger = PhysicalKeyboardFinger.INDEX),
      key('U', 'ㅕ', hand = PhysicalKeyboardHand.RIGHT, finger = PhysicalKeyboardFinger.INDEX),
      key('I', 'ㅑ', hand = PhysicalKeyboardHand.RIGHT, finger = PhysicalKeyboardFinger.MIDDLE),
      key('O', 'ㅐ', 'ㅒ', PhysicalKeyboardHand.RIGHT, PhysicalKeyboardFinger.RING),
      key('P', 'ㅔ', 'ㅖ', PhysicalKeyboardHand.RIGHT, PhysicalKeyboardFinger.LITTLE),
    ),
    listOf(
      key('A', 'ㅁ', hand = PhysicalKeyboardHand.LEFT, finger = PhysicalKeyboardFinger.LITTLE),
      key('S', 'ㄴ', hand = PhysicalKeyboardHand.LEFT, finger = PhysicalKeyboardFinger.RING),
      key('D', 'ㅇ', hand = PhysicalKeyboardHand.LEFT, finger = PhysicalKeyboardFinger.MIDDLE),
      key('F', 'ㄹ', hand = PhysicalKeyboardHand.LEFT, finger = PhysicalKeyboardFinger.INDEX, home = true),
      key('G', 'ㅎ', hand = PhysicalKeyboardHand.LEFT, finger = PhysicalKeyboardFinger.INDEX),
      key('H', 'ㅗ', hand = PhysicalKeyboardHand.RIGHT, finger = PhysicalKeyboardFinger.INDEX),
      key('J', 'ㅓ', hand = PhysicalKeyboardHand.RIGHT, finger = PhysicalKeyboardFinger.INDEX, home = true),
      key('K', 'ㅏ', hand = PhysicalKeyboardHand.RIGHT, finger = PhysicalKeyboardFinger.MIDDLE),
      key('L', 'ㅣ', hand = PhysicalKeyboardHand.RIGHT, finger = PhysicalKeyboardFinger.RING),
    ),
    listOf(
      key('Z', 'ㅋ', hand = PhysicalKeyboardHand.LEFT, finger = PhysicalKeyboardFinger.LITTLE),
      key('X', 'ㅌ', hand = PhysicalKeyboardHand.LEFT, finger = PhysicalKeyboardFinger.RING),
      key('C', 'ㅊ', hand = PhysicalKeyboardHand.LEFT, finger = PhysicalKeyboardFinger.MIDDLE),
      key('V', 'ㅍ', hand = PhysicalKeyboardHand.LEFT, finger = PhysicalKeyboardFinger.INDEX),
      key('B', 'ㅠ', hand = PhysicalKeyboardHand.LEFT, finger = PhysicalKeyboardFinger.INDEX),
      key('N', 'ㅜ', hand = PhysicalKeyboardHand.RIGHT, finger = PhysicalKeyboardFinger.INDEX),
      key('M', 'ㅡ', hand = PhysicalKeyboardHand.RIGHT, finger = PhysicalKeyboardFinger.INDEX),
    ),
  )

  fun target(expected: Char?): PhysicalKeyboardTarget? {
    expected ?: return null
    if (expected == ' ') {
      return PhysicalKeyboardTarget(null, expected, requiresShift = false, shiftHand = null)
    }
    rows.flatten().forEach { key ->
      if (key.baseJamo == expected) {
        return PhysicalKeyboardTarget(key, expected, requiresShift = false, shiftHand = null)
      }
      if (key.shiftedJamo == expected) {
        return PhysicalKeyboardTarget(key, expected, requiresShift = true, shiftHand = key.hand.opposite)
      }
    }
    return null
  }

  private fun key(
    latin: Char,
    base: Char,
    shifted: Char? = null,
    hand: PhysicalKeyboardHand,
    finger: PhysicalKeyboardFinger,
    home: Boolean = false,
  ) = PhysicalKeyboardKeySpec(latin, base, shifted, hand, finger, home)
}

@Composable
fun PhysicalKeyboardGuide(
  nextExpectedJamo: Char?,
  modifier: Modifier = Modifier,
) {
  val target = PhysicalDubeolsikLayout.target(nextExpectedJamo)
  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
      .padding(horizontal = 9.dp, vertical = 8.dp)
      .testTag("physical-keyboard-guide"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    PhysicalKeyboardInstruction(target)
    PhysicalKeyboardRow(PhysicalDubeolsikLayout.rows[0], target)
    PhysicalKeyboardRow(
      PhysicalDubeolsikLayout.rows[1],
      target,
      modifier = Modifier.padding(horizontal = 12.dp),
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      PhysicalUtilityKey(
        label = "⇧",
        highlighted = target?.shiftHand == PhysicalKeyboardHand.LEFT,
        modifier = Modifier.weight(1f).testTag("physical-keyboard-shift-left"),
      )
      PhysicalKeyboardKeys(
        keys = PhysicalDubeolsikLayout.rows[2],
        target = target,
        modifier = Modifier.weight(7f),
      )
      PhysicalUtilityKey(
        label = "⇧",
        highlighted = target?.shiftHand == PhysicalKeyboardHand.RIGHT,
        modifier = Modifier.weight(1f).testTag("physical-keyboard-shift-right"),
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 34.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      PhysicalUtilityKey(
        label = stringResource(R.string.physical_keyboard_space),
        highlighted = target?.expected == ' ',
        modifier = Modifier.weight(4f).testTag("physical-keyboard-space"),
      )
      PhysicalUtilityKey(
        label = "⌫",
        highlighted = false,
        modifier = Modifier.weight(1f).testTag("physical-keyboard-backspace"),
      )
    }
  }
}

@Composable
private fun PhysicalKeyboardInstruction(target: PhysicalKeyboardTarget?) {
  if (target == null) {
    Text(
      text = stringResource(R.string.physical_keyboard_ready),
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
    )
    return
  }
  val key = target.key?.latin?.toString() ?: stringResource(R.string.physical_keyboard_space)
  val hand = handLabel(target.hand)
  val finger = fingerLabel(target.finger)
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = stringResource(R.string.physical_keyboard_next_key, target.expected.toString(), key),
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Black,
    )
    Text(
      text = if (target.requiresShift) {
        stringResource(
          R.string.physical_keyboard_shift_finger,
          handLabel(target.shiftHand ?: PhysicalKeyboardHand.BOTH),
          hand,
          finger,
        )
      } else {
        stringResource(R.string.physical_keyboard_finger, hand, finger)
      },
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun PhysicalKeyboardRow(
  keys: List<PhysicalKeyboardKeySpec>,
  target: PhysicalKeyboardTarget?,
  modifier: Modifier = Modifier,
) {
  PhysicalKeyboardKeys(keys, target, modifier.fillMaxWidth())
}

@Composable
private fun PhysicalKeyboardKeys(
  keys: List<PhysicalKeyboardKeySpec>,
  target: PhysicalKeyboardTarget?,
  modifier: Modifier = Modifier,
) {
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    keys.forEach { key ->
      val highlighted = target?.key?.latin == key.latin
      val highlightColor = if (key.hand == PhysicalKeyboardHand.LEFT) {
        MaterialTheme.colorScheme.secondary
      } else {
        MaterialTheme.colorScheme.primary
      }
      Column(
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 34.dp)
          .background(
            if (highlighted) highlightColor else MaterialTheme.colorScheme.surfaceVariant,
            RoundedCornerShape(7.dp),
          )
          .border(
            if (highlighted) 2.dp else 1.dp,
            if (highlighted) highlightColor else MaterialTheme.colorScheme.outlineVariant,
            RoundedCornerShape(7.dp),
          )
          .testTag("physical-keyboard-key-${key.latin}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = if (key.isHomePosition) "${key.baseJamo}―" else key.baseJamo.toString(),
          color = if (highlighted) Color.White else MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.Black,
          fontSize = 13.sp,
          maxLines = 1,
        )
        Text(
          text = key.latin.toString(),
          color = if (highlighted) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = FontWeight.Bold,
          fontSize = 8.sp,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
private fun PhysicalUtilityKey(
  label: String,
  highlighted: Boolean,
  modifier: Modifier,
) {
  Text(
    text = label,
    modifier = modifier
      .heightIn(min = 32.dp)
      .background(
        if (highlighted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
        RoundedCornerShape(7.dp),
      )
      .border(
        if (highlighted) 2.dp else 1.dp,
        if (highlighted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
        RoundedCornerShape(7.dp),
      )
      .padding(horizontal = 4.dp, vertical = 8.dp),
    color = if (highlighted) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
    fontWeight = FontWeight.Bold,
    fontSize = 10.sp,
    textAlign = TextAlign.Center,
    maxLines = 1,
  )
}

@Composable
private fun handLabel(hand: PhysicalKeyboardHand): String = stringResource(
  when (hand) {
    PhysicalKeyboardHand.LEFT -> R.string.physical_keyboard_hand_left
    PhysicalKeyboardHand.RIGHT -> R.string.physical_keyboard_hand_right
    PhysicalKeyboardHand.BOTH -> R.string.physical_keyboard_hand_both
  },
)

@Composable
private fun fingerLabel(finger: PhysicalKeyboardFinger): String = stringResource(
  when (finger) {
    PhysicalKeyboardFinger.LITTLE -> R.string.physical_keyboard_finger_little
    PhysicalKeyboardFinger.RING -> R.string.physical_keyboard_finger_ring
    PhysicalKeyboardFinger.MIDDLE -> R.string.physical_keyboard_finger_middle
    PhysicalKeyboardFinger.INDEX -> R.string.physical_keyboard_finger_index
    PhysicalKeyboardFinger.THUMB -> R.string.physical_keyboard_finger_thumb
  },
)
