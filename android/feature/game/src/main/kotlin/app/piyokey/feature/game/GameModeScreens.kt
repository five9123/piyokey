package app.piyokey.feature.game

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.game.ACID_RAIN_LANES
import app.piyokey.core.game.AcidRainEvent
import app.piyokey.core.game.AcidRainFactory
import app.piyokey.core.game.AcidRainReducer
import app.piyokey.core.game.AcidRainState
import app.piyokey.core.game.FlowPhase
import app.piyokey.core.game.SpacingEvent
import app.piyokey.core.game.SpacingGameState
import app.piyokey.core.game.SpacingPassage
import app.piyokey.core.game.SpacingReducer
import app.piyokey.core.game.SpacingEngine
import app.piyokey.core.game.TypingGameEvent
import app.piyokey.core.game.TypingGameFactory
import app.piyokey.core.game.TypingFeedback
import app.piyokey.core.game.TypingGameMode
import app.piyokey.core.game.TypingGamePhase
import app.piyokey.core.game.TypingGameReducer
import app.piyokey.core.game.TypingGameState
import app.piyokey.core.game.TypingRoundBuilder
import app.piyokey.core.hangul.HangulComposer
import app.piyokey.core.hangul.JamoDecomposer
import app.piyokey.feature.practice.DubeolsikKeyboard
import app.piyokey.feature.practice.KoreanIMEInput
import app.piyokey.feature.practice.PracticeKeyboardOptions
import app.piyokey.feature.practice.hasKoreanInputMethod
import kotlin.math.ceil
import kotlin.random.Random

private val GamePink = Color(0xFFFF7FA3)
private val GameLavender = Color(0xFFA88AF4)
private val GameInk = Color(0xFF302B3B)
private val GameGood = Color(0xFF2EAD75)
private val GameBad = Color(0xFFE45568)

@Composable
fun GameDeckSelectionScreen(
  kind: GameKind,
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
    modifier = Modifier.fillMaxSize().background(Color(0xFFFFF8F3)).testTag("game-select-${kind.route}"),
    contentPadding = PaddingValues(18.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      TextButton(onClick = onBack) { Text("‹ ${stringResource(R.string.games_title)}") }
      Text(stringResource(R.string.choose_game_course), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
      Text(stringResource(R.string.built_in_courses), color = Color.Gray)
    }
    items(presets.chunked(2), key = { row -> row.joinToString("|") { it.deckId } }) { row ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        row.forEach { deck ->
          GameDeckGridCard(kind, deck, language, bestScores[deck.deckId], onSelect, Modifier.weight(1f))
        }
        if (row.size == 1) {
          Card(
            modifier = Modifier.weight(1f).height(136.dp).clickable(onClick = onFindDeck).testTag("game-find-deck"),
            colors = CardDefaults.cardColors(containerColor = Color.White),
          ) {
            Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
              Text(stringResource(R.string.find_deck), textAlign = TextAlign.Center, fontWeight = FontWeight.Black, color = GameLavender)
            }
          }
        }
      }
    }
    item {
      if (installed.isNotEmpty()) Text(stringResource(R.string.added_decks), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
    }
    items(installed, key = Deck::deckId) { deck ->
      Card(Modifier.fillMaxWidth().clickable { onSelect(deck) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(deck.localizedName(language) ?: deck.deckId, Modifier.weight(1f), fontWeight = FontWeight.Black)
          Text(stringResource(R.string.words_count, deck.items.size), color = Color.Gray)
        }
      }
    }
  }
}

@Composable
private fun GameDeckGridCard(
  kind: GameKind,
  deck: Deck,
  language: String,
  best: Int?,
  onSelect: (Deck) -> Unit,
  modifier: Modifier,
) {
  Card(
    modifier = modifier.height(136.dp).clickable { onSelect(deck) }
      .testTag(if (kind == GameKind.FLOW) "flow-deck-${deck.deckId}" else "game-deck-${deck.deckId}"),
    colors = CardDefaults.cardColors(containerColor = Color.White),
  ) {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Surface(shape = CircleShape, color = GamePink.copy(alpha = 0.14f), modifier = Modifier.size(36.dp)) {
        Box(contentAlignment = Alignment.Center) { Text("ㅎ", fontWeight = FontWeight.Black, color = GamePink) }
      }
      Text(deck.localizedName(language) ?: deck.deckId, fontWeight = FontWeight.Black, maxLines = 2, fontSize = 14.sp)
      Text(stringResource(R.string.words_count, deck.items.size), color = Color.Gray, fontSize = 11.sp)
      best?.let { Text(stringResource(R.string.best_score, it), color = GameLavender, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
    }
  }
}

@Composable
fun TypingGameRoute(
  kind: GameKind,
  deck: Deck,
  useOSIME: Boolean,
  showChoseongMeaning: Boolean,
  seed: Long = Random.nextLong(),
  onClose: () -> Unit,
  onFinished: (TypingGameState) -> Unit,
) {
  val language = LocalConfiguration.current.locales[0].language
  val mode = when (kind) {
    GameKind.CHOSEONG -> TypingGameMode.CHOSEONG
    GameKind.WORD_MATCH -> TypingGameMode.WORD_MATCH
    GameKind.DICTATION -> TypingGameMode.DICTATION
    else -> error("Unsupported direct typing game: $kind")
  }
  val rounds = remember(deck.deckId, seed, language) { TypingRoundBuilder.build(mode, deck.items, language, seed = seed) }
  require(rounds.isNotEmpty()) { "Selected deck has no playable items" }
  var state by remember(deck.deckId, seed, mode) { mutableStateOf(TypingGameFactory.create(mode, rounds, SystemClock.elapsedRealtime())) }
  val lifecycleOwner = LocalLifecycleOwner.current
  val context = LocalContext.current
  val player = remember { OfflinePromptPlayer(context.assets) }

  fun dispatch(event: TypingGameEvent) { state = TypingGameReducer.reduce(state, event) }
  BackHandler(onBack = onClose)
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_STOP -> {
          player.stop()
          dispatch(TypingGameEvent.Pause(SystemClock.elapsedRealtime()))
        }
        Lifecycle.Event.ON_START -> if (state.phase == TypingGamePhase.PAUSED) dispatch(TypingGameEvent.Resume(SystemClock.elapsedRealtime()))
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      player.close()
    }
  }
  LaunchedEffect(state.phase, state.roundIndex) {
    var originFrame: Long? = null
    val originTime = SystemClock.elapsedRealtime()
    while (state.phase == TypingGamePhase.COUNTDOWN || state.phase == TypingGamePhase.PLAYING || state.phase == TypingGamePhase.ANSWER_HOLD) {
      withFrameNanos { frame ->
        val first = originFrame ?: frame.also { originFrame = it }
        dispatch(TypingGameEvent.Tick(originTime + (frame - first) / 1_000_000L))
      }
    }
  }
  LaunchedEffect(state.phase) { if (state.phase == TypingGamePhase.FINISHED) onFinished(state) }

  fun playPrompt() { state.currentRound.item.audio?.let(player::play) }
  LaunchedEffect(mode, state.roundIndex, state.phase) {
    if (mode == TypingGameMode.DICTATION && state.phase == TypingGamePhase.PLAYING) playPrompt()
  }

  val current = state.currentRound
  Column(
    Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFE7EF), Color(0xFFEDE7FF), Color.White)))
      .testTag("typing-game-${kind.route}"),
  ) {
    CompactGameHud(
      question = "${state.roundIndex + 1}/${state.rounds.size}", score = state.score, combo = state.combo, onClose = onClose,
    )
    Box(Modifier.fillMaxWidth().weight(1f).padding(16.dp), contentAlignment = Alignment.Center) {
      Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(
          Modifier.fillMaxWidth().padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          when (mode) {
            TypingGameMode.CHOSEONG -> {
              Text(
                if (state.answerVisible) AnnotatedString(current.item.ko) else choseongProgress(current.item.ko, state.acceptedKeys.size),
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                color = if (state.answerVisible) GameGood else GameInk,
              )
              val meaning = current.item.localizedMeaning(language).orEmpty()
              if ((showChoseongMeaning || state.hintVisible || current.requiresMeaningHint) && meaning.isNotBlank()) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF4F0FF)) { Text(meaning, Modifier.padding(14.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
              } else OutlinedButton(onClick = { dispatch(TypingGameEvent.ShowHint(!current.requiresMeaningHint)) }) { Text(stringResource(R.string.meaning_hint)) }
            }
            TypingGameMode.WORD_MATCH -> Text(current.item.localizedMeaning(language).orEmpty(), fontSize = 25.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            TypingGameMode.DICTATION -> {
              val promptDescription = stringResource(R.string.dictation_audio_prompt)
              Text(
                "♪",
                modifier = Modifier.clearAndSetSemantics { contentDescription = promptDescription },
                fontSize = 56.sp,
                color = GamePink,
              )
              Button(onClick = ::playPrompt) { Text(stringResource(R.string.listen)) }
            }
          }
        }
      }
      if (state.phase == TypingGamePhase.COUNTDOWN) CountdownBadge(state.countdownDurationMillis, state.countdownStartedAtMillis)
    }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
      Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.input_label), color = Color.Gray, fontWeight = FontWeight.Bold)
        Text(if (state.composition.text.isBlank()) "…" else state.composition.text, fontSize = 27.sp, fontWeight = FontWeight.Black, color = if (state.phase == TypingGamePhase.ANSWER_HOLD) GameGood else GameInk)
        if (state.phase == TypingGamePhase.ANSWER_HOLD) Text("+${state.lastPoints}", color = GameGood, fontWeight = FontWeight.Black)
        Text(
          when (state.feedback) {
            TypingFeedback.READY -> stringResource(R.string.input_ready)
            TypingFeedback.CORRECT -> stringResource(R.string.input_correct)
            TypingFeedback.WRONG -> stringResource(R.string.input_wrong)
            TypingFeedback.COMPLETE -> stringResource(R.string.game_answer_complete)
          },
          color = if (state.feedback == TypingFeedback.WRONG) GameBad else GameGood,
          fontWeight = FontWeight.Bold,
        )
      }
    }
    if (useOSIME) {
      if (!hasKoreanInputMethod(context)) Text(stringResource(R.string.korean_ime_required), Modifier.padding(16.dp), color = GameBad, textAlign = TextAlign.Center)
      KoreanIMEInput(
        visibleText = state.composition.text,
        onText = { dispatch(TypingGameEvent.IMEText(it.committedText, it.composingText, SystemClock.elapsedRealtime())) },
        modifier = Modifier.fillMaxWidth().height(120.dp),
        testTag = "game-os-ime",
      )
    } else {
      DubeolsikKeyboard(
        nextExpectedJamo = null,
        onJamo = { dispatch(TypingGameEvent.Key(it, SystemClock.elapsedRealtime())) },
        onBackspace = { dispatch(TypingGameEvent.Backspace) },
        options = PracticeKeyboardOptions(showsKeyGuide = false),
        modifier = Modifier.testTag("game-builtin-keyboard"),
      )
    }
  }
}

@Composable
fun AcidRainRoute(
  deck: Deck,
  useOSIME: Boolean,
  seed: Long = Random.nextLong(),
  onClose: () -> Unit,
  onFinished: (AcidRainState) -> Unit,
) {
  var state by remember(deck.deckId, seed) { mutableStateOf(AcidRainFactory.create(deck, SystemClock.elapsedRealtime(), seed)) }
  val lifecycleOwner = LocalLifecycleOwner.current
  val context = LocalContext.current
  fun dispatch(event: AcidRainEvent) { state = AcidRainReducer.reduce(state, event) }
  BackHandler(onBack = onClose)
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_STOP -> dispatch(AcidRainEvent.Pause)
        Lifecycle.Event.ON_START -> if (state.phase == FlowPhase.PAUSED) dispatch(AcidRainEvent.Resume(SystemClock.elapsedRealtime()))
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
  LaunchedEffect(state.phase) {
    var firstFrame: Long? = null
    val origin = maxOf(SystemClock.elapsedRealtime(), state.lastTickAtMillis ?: state.countdownStartedAtMillis)
    while (state.phase == FlowPhase.COUNTDOWN || state.phase == FlowPhase.PLAYING) withFrameNanos { frame ->
      val first = firstFrame ?: frame.also { firstFrame = it }
      dispatch(AcidRainEvent.Tick(origin + (frame - first) / 1_000_000L))
    }
  }
  LaunchedEffect(state.phase) { if (state.phase == FlowPhase.FINISHED) onFinished(state) }
  val language = LocalConfiguration.current.locales[0].language
  Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFE9E4FF), Color(0xFFFFE8F0), Color.White))).testTag("acid-rain-game")) {
    RainHud(state, onClose)
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(12.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFFFFFDF9))) {
      repeat(ACID_RAIN_LANES) { lane ->
        Box(Modifier.fillMaxSize().padding(start = maxWidth * lane / ACID_RAIN_LANES, end = maxWidth * (ACID_RAIN_LANES - lane - 1) / ACID_RAIN_LANES).background(Color.Transparent))
      }
      state.cards.forEach { card ->
        val laneWidth = maxWidth / ACID_RAIN_LANES
        val x = laneWidth * card.lane + 4.dp
        val y = (maxHeight - 92.dp) * card.progress.toFloat()
        Card(
          Modifier.offset(x, y).padding(4.dp).widthIn(max = laneWidth - 8.dp)
            .then(if (card.id == state.activeCardId) Modifier.semantics { contentDescription = card.item.ko } else Modifier),
          colors = CardDefaults.cardColors(containerColor = if (card.id == state.activeCardId) Color(0xFFFFF0F5) else Color.White),
        ) {
          Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(card.item.ko, fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text(card.item.localizedMeaning(language).orEmpty(), fontSize = 10.sp, maxLines = 1)
            card.item.localizedReading(language)?.takeIf(String::isNotBlank)?.let { reading ->
              Text("[$reading]", fontSize = 9.sp, color = Color.Gray, maxLines = 1)
            }
            LinearProgressIndicator(
              progress = { card.judge.currentIndex.toFloat() / card.judge.expectedSequence.size.coerceAtLeast(1) },
              modifier = Modifier.fillMaxWidth().height(3.dp),
              color = GameGood,
              trackColor = Color(0xFFFFE0EA),
            )
          }
        }
      }
      Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp).background(GameBad.copy(alpha = 0.65f)))
      if (state.phase == FlowPhase.COUNTDOWN) Surface(Modifier.align(Alignment.Center).size(100.dp), CircleShape, Color.White) {
        Box(contentAlignment = Alignment.Center) { Text("3·2·1", fontSize = 28.sp, fontWeight = FontWeight.Black, color = GamePink) }
      }
    }
    if (useOSIME) {
      if (!hasKoreanInputMethod(context)) Text(stringResource(R.string.korean_ime_required), Modifier.padding(12.dp), color = GameBad, textAlign = TextAlign.Center)
      KoreanIMEInput(
        visibleText = state.activeCard?.let { active ->
          HangulComposer.compose(active.judge.expectedSequence.take(active.judge.currentIndex)).text
        }.orEmpty(),
        onText = { dispatch(AcidRainEvent.IMEText(it.committedText, it.composingText)) },
        modifier = Modifier.fillMaxWidth().height(120.dp),
        testTag = "acid-rain-os-ime",
      )
    } else DubeolsikKeyboard(
      nextExpectedJamo = null,
      onJamo = { dispatch(AcidRainEvent.Key(it)) },
      onBackspace = { dispatch(AcidRainEvent.Backspace) },
      options = PracticeKeyboardOptions(showsKeyGuide = false),
      modifier = Modifier.testTag("acid-rain-keyboard"),
    )
  }
}

@Composable
fun SpacingSelectionScreen(passages: List<SpacingPassage>, onBack: () -> Unit, onSelect: (SpacingPassage) -> Unit) {
  BackHandler(onBack = onBack)
  LazyColumn(Modifier.fillMaxSize().background(Color(0xFFFFF8F3)).testTag("spacing-select"), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item { TextButton(onClick = onBack) { Text("‹ ${stringResource(R.string.games_title)}") }; Text(stringResource(R.string.spacing_choose), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) }
    items(passages, key = SpacingPassage::id) { passage ->
      Card(Modifier.fillMaxWidth().clickable { onSelect(passage) }.testTag("spacing-level-${passage.level}"), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp)) {
          Text(stringResource(R.string.spacing_level, passage.level), fontWeight = FontWeight.Black, fontSize = 18.sp)
          Text(stringResource(R.string.spacing_summary, passage.characterCount, passage.spaceCount), color = Color.Gray)
        }
      }
    }
  }
}

@Composable
fun SpacingGameRoute(passage: SpacingPassage, onClose: () -> Unit, onFinished: (SpacingGameState) -> Unit) {
  var state by remember(passage.id) { mutableStateOf(SpacingGameState(SpacingEngine(passage.text))) }
  val lifecycleOwner = LocalLifecycleOwner.current
  fun dispatch(event: SpacingEvent) { state = SpacingReducer.reduce(state, event) }
  LaunchedEffect(Unit) { dispatch(SpacingEvent.Start(SystemClock.elapsedRealtime())) }
  BackHandler(onBack = onClose)
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_STOP -> dispatch(SpacingEvent.Pause(SystemClock.elapsedRealtime()))
        Lifecycle.Event.ON_START -> dispatch(SpacingEvent.Resume(SystemClock.elapsedRealtime()))
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
  LaunchedEffect(state.result) { if (state.result != null) onFinished(state) }
  val visibleElapsedMillis = state.activeElapsedMillis + (state.activeStartedAtMillis?.let { started ->
    (SystemClock.elapsedRealtime() - started).coerceAtLeast(0)
  } ?: 0L)
  Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFE8F0), Color(0xFFF1EDFF), Color.White))).padding(14.dp).testTag("spacing-game")) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = onClose) { Text("× ${stringResource(R.string.close)}") }
      Text(
        "${state.cursor}/${state.engine.boundaryCount} · ${visibleElapsedMillis / 1_000}s",
        Modifier.weight(1f),
        textAlign = TextAlign.End,
        fontWeight = FontWeight.Black,
      )
    }
    LinearProgressIndicator(
      progress = { state.firstDecisions.size.toFloat() / state.engine.boundaryCount.coerceAtLeast(1) },
      modifier = Modifier.fillMaxWidth().height(4.dp).padding(horizontal = 4.dp),
      color = GameGood,
      trackColor = Color.White,
    )
    Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White)) {
      Text(spacingAnnotated(state), Modifier.padding(20.dp), fontSize = 21.sp, lineHeight = 34.sp)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = { dispatch(SpacingEvent.Previous) }, Modifier.weight(1f)) { Text(stringResource(R.string.previous)) }
      Button(onClick = { dispatch(SpacingEvent.ToggleSpace) }, Modifier.weight(1.25f)) { Text(stringResource(R.string.toggle_space)) }
      OutlinedButton(onClick = { dispatch(SpacingEvent.Next) }, Modifier.weight(1f)) { Text(stringResource(R.string.next)) }
    }
    if (state.cursor == state.engine.boundaryCount) Button(
      onClick = { dispatch(SpacingEvent.Submit(SystemClock.elapsedRealtime())) },
      modifier = Modifier.fillMaxWidth().height(52.dp).testTag("spacing-submit"),
    ) { Text(stringResource(R.string.submit_result), fontWeight = FontWeight.Black) }
  }
}

@Composable
fun GenericGameResultScreen(
  score: Int,
  accuracy: Double,
  maxCombo: Int,
  completed: Int,
  detailLabel: String? = null,
  detailValue: String? = null,
  reviewWords: List<String> = emptyList(),
  inputModeName: String? = null,
  onRetry: () -> Unit,
  onDone: () -> Unit,
) {
  BackHandler(onBack = onDone)
  Column(
    Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFE8F0), Color(0xFFF2EEFF), Color.White))).padding(22.dp).testTag("game-result"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(if (accuracy >= 95) "S" else if (accuracy >= 80) "A" else if (accuracy >= 60) "B" else "C", fontSize = 70.sp, fontWeight = FontWeight.Black, color = GamePink)
    Text(stringResource(R.string.game_complete), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
    Text(score.toString(), fontSize = 40.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(12.dp))
    ResultRow(stringResource(R.string.accuracy), "%.1f%%".format(accuracy))
    ResultRow(stringResource(R.string.max_combo), maxCombo.toString())
    ResultRow(stringResource(R.string.items_typed), completed.toString())
    if (detailLabel != null && detailValue != null) ResultRow(detailLabel, detailValue)
    inputModeName?.let { ResultRow(stringResource(R.string.input_mode), it) }
    if (reviewWords.isNotEmpty()) {
      Card(Modifier.fillMaxWidth().padding(top = 10.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp)) {
          Text(stringResource(R.string.review_words), fontWeight = FontWeight.Black)
          Text(reviewWords.joinToString(" · "), color = GameBad, modifier = Modifier.padding(top = 4.dp))
        }
      }
    }
    Button(onClick = onRetry, Modifier.fillMaxWidth().padding(top = 22.dp).height(54.dp)) { Text(stringResource(R.string.retry), fontWeight = FontWeight.Black) }
    TextButton(onClick = onDone, Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
  }
}

@Composable
fun SpacingResultScreen(
  state: SpacingGameState,
  onRetry: () -> Unit,
  onDone: () -> Unit,
) {
  val result = requireNotNull(state.result)
  BackHandler(onBack = onDone)
  LazyColumn(
    modifier = Modifier.fillMaxSize()
      .background(Brush.verticalGradient(listOf(Color(0xFFFFE8F0), Color(0xFFF2EEFF), Color.White)))
      .testTag("game-result"),
    contentPadding = PaddingValues(22.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Text(
        if (result.firstDecisionAccuracyPercent >= 95) "S" else if (result.firstDecisionAccuracyPercent >= 80) "A" else if (result.firstDecisionAccuracyPercent >= 60) "B" else "C",
        fontSize = 70.sp,
        fontWeight = FontWeight.Black,
        color = GamePink,
      )
      Text(stringResource(R.string.game_complete), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
      Text(result.score.toString(), fontSize = 40.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
    }
    item {
      Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
          ResultRow(stringResource(R.string.first_accuracy), "%.1f%%".format(result.firstDecisionAccuracyPercent))
          ResultRow(stringResource(R.string.final_accuracy), "%.1f%%".format(result.finalAccuracyPercent))
          ResultRow(stringResource(R.string.corrections), result.correctionCount.toString())
          ResultRow(stringResource(R.string.solve_time), "%.1fs".format(state.activeElapsedMillis / 1_000.0))
        }
      }
    }
    if (result.mistakes.isNotEmpty()) {
      item { Text(stringResource(R.string.mistake_review), Modifier.fillMaxWidth(), fontWeight = FontWeight.Black, fontSize = 19.sp) }
      items(result.mistakes, key = { it.boundary }) { mistake ->
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
          Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(mistake.context, fontWeight = FontWeight.Bold)
            Text(
              stringResource(
                R.string.spacing_answer,
                stringResource(if (mistake.expectedSpace) R.string.space_choice else R.string.no_space_choice),
                stringResource(if (mistake.choseSpace) R.string.space_choice else R.string.no_space_choice),
              ),
              color = GameBad,
            )
          }
        }
      }
    }
    item {
      Button(onClick = onRetry, Modifier.fillMaxWidth().padding(top = 12.dp).height(54.dp)) { Text(stringResource(R.string.retry), fontWeight = FontWeight.Black) }
      TextButton(onClick = onDone, Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
    }
  }
}

@Composable private fun CompactGameHud(question: String, score: Int, combo: Int, onClose: () -> Unit) {
  Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    Surface(Modifier.size(44.dp).clickable(onClick = onClose), CircleShape, Color.White) { Box(contentAlignment = Alignment.Center) { Text("×", fontSize = 28.sp) } }
    MiniMetric(stringResource(R.string.questions), question, Modifier.weight(1f))
    MiniMetric(stringResource(R.string.score), score.toString(), Modifier.weight(1f))
    MiniMetric(stringResource(R.string.combo), combo.toString(), Modifier.weight(1f))
  }
}

@Composable private fun RainHud(state: AcidRainState, onClose: () -> Unit) {
  Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
    Surface(Modifier.size(44.dp).clickable(onClick = onClose), CircleShape, Color.White) { Box(contentAlignment = Alignment.Center) { Text("×", fontSize = 28.sp) } }
    MiniMetric(stringResource(R.string.time), ceil(state.remainingTimeMillis / 1000.0).toInt().toString(), Modifier.weight(1f))
    MiniMetric(stringResource(R.string.score), state.score.toString(), Modifier.weight(1f))
    MiniMetric(stringResource(R.string.combo), state.combo.toString(), Modifier.weight(1f))
    MiniMetric(stringResource(R.string.lives), state.lives.toString(), Modifier.weight(1f))
  }
}

@Composable private fun MiniMetric(label: String, value: String, modifier: Modifier) {
  Surface(modifier.height(48.dp), RoundedCornerShape(14.dp), Color.White) { Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, fontWeight = FontWeight.Black, maxLines = 1); Text(label, fontSize = 8.sp, maxLines = 1) } }
}

@Composable private fun CountdownBadge(duration: Long, started: Long) {
  val left = ((duration - (SystemClock.elapsedRealtime() - started)).coerceAtLeast(1) + 999) / 1_000
  Surface(Modifier.size(104.dp), CircleShape, Color.White) { Box(contentAlignment = Alignment.Center) { Text(left.toString(), fontSize = 48.sp, fontWeight = FontWeight.Black, color = GamePink) } }
}

private fun spacingAnnotated(state: SpacingGameState): AnnotatedString = buildAnnotatedString {
  state.engine.compactText.forEachIndexed { index, character ->
    if (index > 0) {
      val selected = index in state.selectedBoundaries
      val current = index == state.cursor
      val decided = index in state.firstDecisions
      val correct = selected == (index in state.engine.correctBoundaries)
      val feedbackBackground = when {
        current -> GameLavender.copy(alpha = 0.35f)
        !decided -> Color.Transparent
        correct -> GameGood.copy(alpha = 0.22f)
        else -> GameBad.copy(alpha = 0.22f)
      }
      pushStyle(SpanStyle(background = feedbackBackground, color = if (selected) if (correct) GameGood else GameBad else Color.Transparent))
      append(if (selected) "␣" else " ")
      pop()
    }
    append(character)
  }
}

private fun choseongProgress(target: String, acceptedJamoCount: Int): AnnotatedString = buildAnnotatedString {
  var consumed = 0
  target.forEach { character ->
    when {
      character.isWhitespace() -> append(character)
      character.code in 0xAC00..0xD7A3 -> {
        val count = JamoDecomposer.keySequenceFor(character.toString()).size
        val color = when {
          acceptedJamoCount >= consumed + count -> GameGood
          acceptedJamoCount > consumed -> GamePink
          else -> GameInk
        }
        pushStyle(SpanStyle(color = color))
        append(TypingRoundBuilder.extractInitials(character.toString()))
        pop()
        consumed += count
      }
    }
  }
}

@Composable private fun ResultRow(label: String, value: String) {
  Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Color.Gray); Text(value, fontWeight = FontWeight.Black) }
}

private class OfflinePromptPlayer(private val assets: android.content.res.AssetManager) {
  private var player: MediaPlayer? = null
  fun play(path: String) {
    close()
    runCatching {
      assets.openFd(path).use { descriptor ->
        player = MediaPlayer().apply {
          setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
          setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
          setOnCompletionListener { close() }
          setOnErrorListener { media, _, _ -> media.release(); player = null; true }
          prepare()
          start()
        }
      }
    }
  }
  fun stop() { player?.release(); player = null }
  fun close() = stop()
}
