package app.piyokey.feature.game

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.game.FlowFinishReason
import app.piyokey.core.game.FlowGameEvent
import app.piyokey.core.game.FlowGameFactory
import app.piyokey.core.game.FlowGameReducer
import app.piyokey.core.game.FlowGameState
import app.piyokey.core.game.FlowPhase
import app.piyokey.core.hangul.HangulComposer
import app.piyokey.core.design.PiyoAvatar
import app.piyokey.core.design.PiyokeyIcon
import app.piyokey.core.design.PiyokeyIconKind
import app.piyokey.core.platform.PiyokeySoundEngine
import app.piyokey.core.platform.SoundCue
import app.piyokey.core.platform.ResultShareModel
import app.piyokey.core.settings.KeySoundStyle
import app.piyokey.core.settings.PiyoGrowthStage
import app.piyokey.core.settings.PiyoSessionAppearance
import app.piyokey.feature.practice.DubeolsikKeyboard
import app.piyokey.feature.practice.KoreanIMEInput
import app.piyokey.feature.practice.PracticeKeyboardOptions
import app.piyokey.feature.practice.hasKoreanInputMethod
import kotlin.math.ceil
import kotlin.random.Random
import kotlinx.coroutines.delay

private val PiyoPink = Color(0xFFFF7FA3)
private val PiyoLavender = Color(0xFFA88AF4)
private val LaneBackground = Color(0xFFFFFDF9)
private val Ink = Color(0xFF302B3B)
private val Good = Color(0xFF2EAD75)
private val Bad = Color(0xFFE45568)

@Composable
fun GameHubScreen(
  onGame: (GameKind) -> Unit,
  onWeeklyCup: () -> Unit,
) {
  val games = listOf(
    GameTile(GameKind.FLOW, R.string.flow_title, R.string.flow_rule, PiyokeyIconKind.FLOW),
    GameTile(GameKind.ACID_RAIN, R.string.acid_rain_title, R.string.acid_rain_rule, PiyokeyIconKind.RAIN),
    GameTile(GameKind.CHOSEONG, R.string.choseong_title, R.string.choseong_rule, PiyokeyIconKind.INITIALS),
    GameTile(GameKind.DICTATION, R.string.dictation_title, R.string.dictation_rule, PiyokeyIconKind.DICTATION),
    GameTile(GameKind.WORD_MATCH, R.string.word_match_title, R.string.word_match_rule, PiyokeyIconKind.WORD_MATCH),
    GameTile(GameKind.SPACING, R.string.spacing_title, R.string.spacing_rule, PiyokeyIconKind.SPACING),
  )
  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("game-hub"),
    contentPadding = PaddingValues(18.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Text(stringResource(R.string.games_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
      Text(stringResource(R.string.games_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    item {
      Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onWeeklyCup).testTag("weekly-cup"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
      ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
          PiyokeyIcon(
            kind = PiyokeyIconKind.TROPHY,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
          )
          Spacer(Modifier.width(12.dp))
          Column {
            Text(
              stringResource(R.string.weekly_cup),
              fontWeight = FontWeight.Black,
              fontSize = 18.sp,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(stringResource(R.string.weekly_cup_rule), color = MaterialTheme.colorScheme.onTertiaryContainer)
          }
        }
      }
    }
    items(games.chunked(2)) { row ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        row.forEach { tile ->
          Card(
            modifier = Modifier.weight(1f).aspectRatio(1.0f)
              .clickable { onGame(tile.kind) }.testTag("game-${tile.kind.route}"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
              PiyokeyIcon(
                kind = tile.icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.secondary,
              )
              Column {
                Text(stringResource(tile.title), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Text(
                  stringResource(tile.rule),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2,
                )
              }
            }
          }
        }
      }
    }
  }
}

enum class GameKind(val route: String) { FLOW("flow"), ACID_RAIN("acid-rain"), CHOSEONG("choseong"), DICTATION("dictation"), WORD_MATCH("word-match"), SPACING("spacing") }

private data class GameTile(val kind: GameKind, val title: Int, val rule: Int, val icon: PiyokeyIconKind)

@Composable
fun FlowDeckSelectionScreen(
  presets: List<Deck>,
  installed: List<Deck>,
  bestScores: Map<String, Int>,
  onBack: () -> Unit,
  onSelect: (Deck) -> Unit,
  onFindDeck: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val language = LocalConfiguration.current.locales[0].language
  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(18.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      TextButton(onClick = onBack) {
        PiyokeyIcon(PiyokeyIconKind.BACK, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.games_title))
      }
      Text(stringResource(R.string.choose_course), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
      Text(stringResource(R.string.built_in_courses), color = Color.Gray)
    }
    items(presets, key = Deck::deckId) { deck ->
      FlowDeckCard(deck, language, bestScores[deck.deckId], onSelect)
    }
    item {
      OutlinedButton(onClick = onFindDeck, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.find_deck)) }
      if (installed.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.added_decks), fontWeight = FontWeight.Bold)
      }
    }
    items(installed, key = Deck::deckId) { deck ->
      FlowDeckCard(deck, language, bestScores[deck.deckId], onSelect)
    }
  }
}

@Composable
private fun FlowDeckCard(deck: Deck, language: String, best: Int?, onSelect: (Deck) -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth().clickable { onSelect(deck) }.testTag("flow-deck-${deck.deckId}"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      Surface(shape = CircleShape, color = PiyoPink.copy(alpha = 0.14f), modifier = Modifier.size(46.dp)) {
        Box(contentAlignment = Alignment.Center) { Text("ㅎ", fontWeight = FontWeight.Black, color = PiyoPink) }
      }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(deck.localizedName(language) ?: deck.deckId, fontWeight = FontWeight.Black)
        Text(stringResource(R.string.words_count, deck.items.size), color = Color.Gray)
      }
      if (best != null) Text(stringResource(R.string.best_score, best), fontWeight = FontWeight.Bold, color = PiyoLavender)
    }
  }
}

private enum class FlowFeedback { READY, CORRECT, WRONG, COMPLETE, LIFE_LOST }

@Composable
fun FlowGameRoute(
  deck: Deck,
  useOSIME: Boolean = false,
  piyoAppearance: PiyoSessionAppearance = PiyoSessionAppearance(PiyoGrowthStage.CHICK, null),
  soundEffectsEnabled: Boolean = true,
  keySoundStyle: KeySoundStyle = KeySoundStyle.DEFAULT,
  hapticsEnabled: Boolean = true,
  seed: Long = Random.nextLong(),
  onClose: () -> Unit,
  onFinished: (FlowGameState) -> Unit,
) {
  var state by remember(deck.deckId, seed) {
    mutableStateOf(FlowGameFactory.create(deck, SystemClock.elapsedRealtime(), seed))
  }
  var feedback by remember { mutableStateOf(FlowFeedback.READY) }
  var feedbackToken by remember { mutableLongStateOf(0L) }
  val lifecycleOwner = LocalLifecycleOwner.current
  val context = LocalContext.current
  val soundEngine = remember { PiyokeySoundEngine(context) }

  LaunchedEffect(soundEffectsEnabled) { soundEngine.setEnabled(soundEffectsEnabled) }

  fun dispatch(event: FlowGameEvent) {
    val previous = state
    val reduction = FlowGameReducer.reduce(state, event)
    state = reduction.state
    feedback = when {
      reduction.lostLife -> FlowFeedback.LIFE_LOST
      reduction.completedCard -> FlowFeedback.COMPLETE
      event is FlowGameEvent.Key && reduction.state.mistakeCount > previous.mistakeCount -> FlowFeedback.WRONG
      event is FlowGameEvent.Key -> FlowFeedback.CORRECT
      else -> feedback
    }
    if (reduction.lostLife || reduction.completedCard || event is FlowGameEvent.Key) feedbackToken += 1
    if (soundEffectsEnabled) {
      when {
        reduction.lostLife -> soundEngine.play(SoundCue.LIFE_LOST, keySoundStyle)
        reduction.completedCard -> soundEngine.play(SoundCue.CORRECT, keySoundStyle)
        reduction.state.mistakeCount > previous.mistakeCount -> soundEngine.play(SoundCue.MISTAKE, keySoundStyle)
        event is FlowGameEvent.Backspace -> soundEngine.play(SoundCue.BACKSPACE, keySoundStyle)
        event is FlowGameEvent.Key -> soundEngine.play(SoundCue.KEY, keySoundStyle)
      }
    }
  }

  BackHandler { onClose() }
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_STOP -> { soundEngine.release(); dispatch(FlowGameEvent.Pause) }
        Lifecycle.Event.ON_START -> if (state.phase == FlowPhase.PAUSED) {
          dispatch(FlowGameEvent.Resume(SystemClock.elapsedRealtime()))
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer); soundEngine.close() }
  }
  LaunchedEffect(state.phase) {
    val timelineOriginMillis = maxOf(
      SystemClock.elapsedRealtime(),
      state.lastTickAtMillis ?: state.countdownStartedAtMillis,
    )
    var firstFrameNanos: Long? = null
    while (state.phase == FlowPhase.COUNTDOWN || state.phase == FlowPhase.PLAYING) {
      withFrameNanos { frameNanos ->
        val originFrame = firstFrameNanos ?: frameNanos.also { firstFrameNanos = it }
        dispatch(FlowGameEvent.Tick(timelineOriginMillis + (frameNanos - originFrame) / 1_000_000L))
      }
    }
  }
  LaunchedEffect(feedbackToken) {
    if (feedbackToken > 0) {
      delay(600)
      if (feedback != FlowFeedback.READY) feedback = FlowFeedback.READY
    }
  }
  LaunchedEffect(state.phase, state.finishReason) {
    if (state.phase == FlowPhase.FINISHED && state.finishReason != FlowFinishReason.CLOSED) onFinished(state)
  }

  FlowGameScreen(
    state = state,
    feedback = feedback,
    onKey = { dispatch(FlowGameEvent.Key(it)) },
    onBackspace = { dispatch(FlowGameEvent.Backspace) },
    onIMEText = { committed, composing -> dispatch(FlowGameEvent.IMEText(committed, composing)) },
    useOSIME = useOSIME,
    piyoAppearance = piyoAppearance,
    hapticsEnabled = hapticsEnabled,
    onClose = {
      dispatch(FlowGameEvent.Close)
      onClose()
    },
  )
}

@Composable
private fun FlowGameScreen(
  state: FlowGameState,
  feedback: FlowFeedback,
  onKey: (Char) -> Unit,
  onBackspace: () -> Unit,
  onIMEText: (String, String?) -> Unit,
  useOSIME: Boolean,
  piyoAppearance: PiyoSessionAppearance,
  hapticsEnabled: Boolean,
  onClose: () -> Unit,
) {
  val context = LocalContext.current
  Box(
    Modifier.fillMaxSize().background(
      Brush.verticalGradient(listOf(Color(0xFFFFE7EF), Color(0xFFEDE7FF), Color(0xFFFFFBF4))),
    ),
  ) {
    Column(Modifier.fillMaxSize()) {
      FlowHud(state, onClose)
      BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)
          .clip(RoundedCornerShape(26.dp)).background(LaneBackground),
      ) {
        Canvas(Modifier.fillMaxSize()) {
          val y = size.height * 0.82f
          drawRect(Color(0xFFF3E6E3), topLeft = Offset(0f, y), size = androidx.compose.ui.geometry.Size(size.width, size.height - y))
          repeat(14) { index ->
            drawCircle(Color(0xFFD7C9D4), radius = 3.dp.toPx(), center = Offset(index * size.width / 13f, y + 18.dp.toPx()))
          }
        }
        val travel = maxWidth + 260.dp
        val x = travel * (1f - state.currentCard.progress.toFloat()) - 260.dp
        FlowWordCard(
          state = state,
          modifier = Modifier.align(Alignment.CenterStart).offset(x = x, y = (-10).dp),
        )
        PiyoAvatar(
          appearance = piyoAppearance,
          contentDescription = stringResource(R.string.game_piyo_accessibility),
          modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).size(74.dp),
        )
        if (feedback == FlowFeedback.COMPLETE) {
          Box(Modifier.align(Alignment.Center)) { CompletionBurst() }
        }
        if (state.phase == FlowPhase.COUNTDOWN) {
          Surface(
            modifier = Modifier.align(Alignment.Center).size(112.dp).shadow(10.dp, CircleShape),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.96f),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(state.countdownValue.toString(), fontSize = 54.sp, fontWeight = FontWeight.Black, color = PiyoPink)
            }
          }
        }
      }
      JamoProgressTrack(state)
      Text(
        text = when (feedback) {
          FlowFeedback.READY -> stringResource(R.string.input_ready)
          FlowFeedback.CORRECT -> stringResource(R.string.input_correct)
          FlowFeedback.WRONG -> stringResource(R.string.input_wrong)
          FlowFeedback.COMPLETE -> stringResource(R.string.input_complete)
          FlowFeedback.LIFE_LOST -> stringResource(R.string.life_lost)
        },
        modifier = Modifier.fillMaxWidth().height(28.dp),
        textAlign = TextAlign.Center,
        color = if (feedback == FlowFeedback.WRONG || feedback == FlowFeedback.LIFE_LOST) Bad else Good,
        fontWeight = FontWeight.Bold,
      )
      if (useOSIME) {
        if (!hasKoreanInputMethod(context)) {
          Text(stringResource(R.string.korean_ime_required), Modifier.fillMaxWidth().padding(10.dp), textAlign = TextAlign.Center, color = Bad)
        }
        KoreanIMEInput(
          visibleText = HangulComposer.compose(state.currentCard.judge.expectedSequence.take(state.currentCard.judge.currentIndex)).text,
          onText = { onIMEText(it.committedText, it.composingText) },
          modifier = Modifier.fillMaxWidth().height(120.dp),
          testTag = "flow-os-ime",
        )
      } else {
        DubeolsikKeyboard(
          nextExpectedJamo = state.currentCard.judge.expectedNext,
          onJamo = onKey,
          onBackspace = onBackspace,
          options = PracticeKeyboardOptions(hapticsEnabled = hapticsEnabled),
          modifier = Modifier.testTag("flow-keyboard"),
        )
      }
    }
  }
}

@Composable
private fun FlowHud(state: FlowGameState, onClose: () -> Unit) {
  val closeDescription = stringResource(R.string.close)
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(
      modifier = Modifier.size(44.dp).clickable(onClick = onClose).semantics { contentDescription = closeDescription },
      shape = CircleShape,
      color = Color.White,
    ) {
      Box(contentAlignment = Alignment.Center) {
        PiyokeyIcon(PiyokeyIconKind.CLOSE, null, Modifier.size(22.dp), tint = Ink)
      }
    }
    HudMetric("⏱", stringResource(R.string.time), ceil(state.remainingTimeMillis / 1000.0).toInt().toString(), Modifier.weight(1f))
    HudMetric("★", stringResource(R.string.score), state.score.toString(), Modifier.weight(1f))
    HudMetric("⚡", stringResource(R.string.combo), state.combo.toString(), Modifier.weight(1f))
    HudMetric("♥", stringResource(R.string.lives), state.lives.toString(), Modifier.weight(1f))
  }
}

@Composable
private fun HudMetric(symbol: String, label: String, value: String, modifier: Modifier) {
  Surface(modifier = modifier.height(48.dp), shape = RoundedCornerShape(15.dp), color = Color.White.copy(alpha = 0.95f)) {
    Row(Modifier.padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(symbol, color = PiyoPink, fontSize = 12.sp)
      Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Black, maxLines = 1, fontSize = if (value.length > 5) 12.sp else 16.sp)
        Text(label, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

@Composable
private fun FlowWordCard(state: FlowGameState, modifier: Modifier) {
  val item = state.currentCard.item
  val language = LocalConfiguration.current.locales[0].language
  val meaning = item.localizedMeaning(language).orEmpty()
  val reading = item.localizedReading(language).orEmpty()
  Card(
    modifier = modifier.widthIn(min = 104.dp, max = 260.dp).testTag("flow-card"),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(6.dp),
  ) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Text(item.ko, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Ink, maxLines = 1)
      if (meaning.isNotBlank()) Text(meaning, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (reading.isNotBlank()) Text("[$reading]", fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

@Composable
private fun JamoProgressTrack(state: FlowGameState) {
  val scroll = rememberScrollState()
  val density = LocalDensity.current
  val index = state.currentCard.judge.currentIndex
  LaunchedEffect(state.cardIndex, index) {
    scroll.animateScrollTo(with(density) { (index * 34.dp.toPx()).toInt() }.coerceAtLeast(0))
  }
  Row(
    Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp))
      .background(Color.White.copy(alpha = 0.9f)).horizontalScroll(scroll),
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Spacer(Modifier.width(4.dp))
    state.currentCard.judge.expectedSequence.forEachIndexed { offset, jamo ->
      Surface(
        modifier = Modifier.size(28.dp),
        shape = CircleShape,
        color = when {
          offset < index -> Good
          offset == index -> PiyoPink
          else -> Color(0xFFEDE8EE)
        },
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(jamo.toString(), fontWeight = FontWeight.Bold, color = if (offset <= index) Color.White else Ink, fontSize = 12.sp)
        }
      }
    }
    Spacer(Modifier.width(4.dp))
  }
}

@Composable
private fun CompletionBurst() {
  val progress = remember { Animatable(0f) }
  LaunchedEffect(Unit) { progress.animateTo(1f, animationSpec = tween(450)) }
  Canvas(Modifier.size(150.dp)) {
    val colors = listOf(PiyoPink, PiyoLavender, Color(0xFFFFCB54), Good)
    repeat(16) { index ->
      val angle = index * Math.PI * 2 / 16
      drawCircle(
        colors[index % colors.size],
        radius = 5.dp.toPx(),
        center = Offset(
          size.width / 2 + kotlin.math.cos(angle).toFloat() * 58.dp.toPx() * progress.value,
          size.height / 2 + kotlin.math.sin(angle).toFloat() * 58.dp.toPx() * progress.value,
        ),
      )
    }
  }
}

@Composable
fun FlowResultScreen(
  state: FlowGameState,
  rank: String,
  isNewBest: Boolean,
  shareModel: ResultShareModel? = null,
  onPlayGamesLeaderboard: (() -> Unit)? = null,
  onRetry: () -> Unit,
  onDone: () -> Unit,
) {
  BackHandler(onBack = onDone)
  Column(
    modifier = Modifier.fillMaxSize().testTag("flow-result").background(
      Brush.verticalGradient(listOf(Color(0xFFFFE8F0), Color(0xFFF2EEFF), Color.White)),
    ).padding(22.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(rank, fontSize = 76.sp, fontWeight = FontWeight.Black, color = PiyoPink)
    Text(stringResource(R.string.result_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
    if (isNewBest) Text(stringResource(R.string.new_best), color = PiyoLavender, fontWeight = FontWeight.Black)
    Text(state.score.toString(), fontSize = 42.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(vertical = 12.dp))
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
      Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ResultMetric(stringResource(R.string.accuracy), "%.1f%%".format(state.accuracyPercent))
        ResultMetric(stringResource(R.string.max_combo), state.maxCombo.toString())
        ResultMetric(stringResource(R.string.items_typed), state.completedItemCount.toString())
        ResultMetric(stringResource(R.string.items_missed), state.missedItemCount.toString())
      }
    }
    shareModel?.let { ResultShareActions(it, Modifier.padding(top = 12.dp)) }
    onPlayGamesLeaderboard?.let { open ->
      OutlinedButton(
        onClick = open,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).testTag("play-games-leaderboard"),
      ) { Text(stringResource(R.string.play_games_ranking), fontWeight = FontWeight.Bold) }
    }
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().padding(top = 22.dp).height(54.dp)) { Text(stringResource(R.string.retry), fontWeight = FontWeight.Black) }
    TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
  }
}

@Composable
private fun ResultMetric(label: String, value: String) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, color = Color.Gray)
    Text(value, fontWeight = FontWeight.Black)
  }
}
