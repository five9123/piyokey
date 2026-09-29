package app.piyokey.android.ui.mascot

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tornado
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.ui.graphics.vector.ImageVector
import app.piyokey.android.data.mascot.MascotMood
import app.piyokey.android.data.mascot.MascotProp
import app.piyokey.android.data.mascot.MascotReaction

/** Material stand-ins for the SF Symbols the iOS mascot uses. */
internal object MascotIcons {
  val flame: ImageVector get() = Icons.Filled.LocalFireDepartment // flame.fill
  val tornado: ImageVector get() = Icons.Filled.Tornado // tornado
  val lightbulb: ImageVector get() = Icons.Filled.Lightbulb // lightbulb.fill
  val checkCircle: ImageVector get() = Icons.Filled.CheckCircle // checkmark.circle.fill
  val airplane: ImageVector get() = Icons.Filled.Flight // airplane
  val coffee: ImageVector get() = Icons.Filled.Coffee // cup.and.saucer.fill
  val mic: ImageVector get() = Icons.Filled.Mic // microphone.fill
  val forkKnife: ImageVector get() = Icons.Filled.Restaurant // fork.knife
  val rosette: ImageVector get() = Icons.Filled.WorkspacePremium // rosette
  val trophy: ImageVector get() = Icons.Filled.EmojiEvents // trophy.fill
  val trophyOutline: ImageVector get() = Icons.Outlined.EmojiEvents // trophy
  val star: ImageVector get() = Icons.Filled.Star // star.fill
  val check: ImageVector get() = Icons.Filled.Check // checkmark
  val seal: ImageVector get() = Icons.Filled.Verified // checkmark.seal.fill
  val arrowDownCircle: ImageVector get() = Icons.Filled.ArrowCircleDown // arrow.down.circle.fill
  val bolt: ImageVector get() = Icons.Filled.Bolt // bolt.fill
  val musicNote: ImageVector get() = Icons.Filled.MusicNote // music.note
  val exclamation: ImageVector get() = Icons.Filled.PriorityHigh // exclamationmark
  val sparkles: ImageVector get() = Icons.Filled.AutoAwesome // sparkles
  val sun: ImageVector get() = Icons.Filled.WbSunny // sun.max.fill
  val waveform: ImageVector get() = Icons.Filled.GraphicEq // waveform

  fun forMood(mood: MascotMood): List<ImageVector> = when (mood) {
    MascotMood.GRIT -> listOf(flame)
    MascotMood.DIZZY -> listOf(tornado)
    MascotMood.EUREKA -> listOf(lightbulb)
    MascotMood.SATISFIED -> listOf(checkCircle)
    else -> emptyList()
  }

  fun forProp(prop: MascotProp): List<ImageVector> = when (prop) {
    MascotProp.TRAVEL_CASE -> listOf(airplane)
    MascotProp.COFFEE_CUP -> listOf(coffee)
    MascotProp.MICROPHONE -> listOf(mic)
    MascotProp.FOOD_PLATE -> listOf(forkKnife)
    MascotProp.RIBBON -> listOf(rosette)
    MascotProp.GAME_CENTER_TROPHY -> listOf(trophy, trophyOutline, star)
    else -> emptyList()
  }

  fun forReaction(reaction: MascotReaction): List<ImageVector> = when (reaction) {
    MascotReaction.CorrectJamo -> listOf(check)
    MascotReaction.SyllableCompleted, MascotReaction.ReviewGraduated -> listOf(seal)
    MascotReaction.WordCompleted -> listOf(star)
    MascotReaction.Mistake -> listOf(arrowDownCircle)
    is MascotReaction.Rhythm -> listOf(if (reaction.count >= 15) bolt else musicNote)
    MascotReaction.Startle -> listOf(exclamation)
    MascotReaction.GrowthTransition -> listOf(sparkles)
    MascotReaction.Stretch -> listOf(sun)
    MascotReaction.EggKnock -> listOf(waveform)
    MascotReaction.NewBest -> listOf(trophy)
    is MascotReaction.LessonStreak -> if (reaction.count >= 3) emptyList() else listOf(star)
    MascotReaction.Eureka -> listOf(lightbulb)
    else -> emptyList()
  }
}
