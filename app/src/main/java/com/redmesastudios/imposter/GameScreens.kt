package com.redmesastudios.imposter

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redmesastudios.imposter.ui.Gold
import com.redmesastudios.imposter.ui.Line
import com.redmesastudios.imposter.ui.LineBright
import com.redmesastudios.imposter.ui.Mint
import com.redmesastudios.imposter.ui.Panel
import com.redmesastudios.imposter.ui.PlayerColors
import com.redmesastudios.imposter.ui.Red
import com.redmesastudios.imposter.ui.Text
import com.redmesastudios.imposter.ui.TextFaint
import com.redmesastudios.imposter.ui.Type
import kotlinx.coroutines.delay

private val CardShape = RoundedCornerShape(14.dp)

private fun RoomState.color(id: String?): Color = PlayerColors[player(id)?.color ?: 0]

// Asks for focus on the screen's main button once it's shown.
@Composable
private fun rememberMain(key: Any? = Unit): FocusRequester {
    val main = remember { FocusRequester() }
    LaunchedEffect(key) { runCatching { main.requestFocus() } }
    return main
}

@Composable
private fun Screen(kicker: String, title: String, note: String? = null, kickerColor: Color = Gold, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 34.dp, bottom = 34.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(kicker, style = Type.kicker.copy(color = kickerColor))
            BasicText(title, style = Type.title)
            note?.let { BasicText(it, style = Type.soft) }
        }
        content()
    }
}

// Everyone looks at their phone: the word, or the news that they're the imposter.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DealScreen(state: RoomState, game: GameState, onBegin: () -> Unit) {
    val main = rememberMain()
    val count = game.imposters.size
    Screen(
        "GAME ${state.number} · ${if (count == 1) "1 IMPOSTER" else "$count IMPOSTERS"}",
        "Check your phone",
        "Everyone has the secret word except ${if (count == 1) "the imposter" else "the imposters"}. Tap Got it once you've looked.",
    ) {
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 4) {
            game.seats.forEach { id ->
                val ready = id in game.ready
                SeatCard(state, id, Modifier.weight(1f), dim = !ready, note = if (ready) "Got it" else "Looking…", noteColor = if (ready) Mint else TextFaint)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Pill("Start the clues", Modifier.focusRequester(main), large = true, onClick = onBegin)
            BasicText("The clues start on their own once everyone has looked.", style = Type.soft.copy(fontSize = 13.sp))
        }
    }
}

// One clue each, in turn. The speaker's phone says it's their turn; the TV shows it big.
@Composable
fun CluesScreen(state: RoomState, game: GameState, onNext: () -> Unit, onVote: () -> Unit) {
    val main = rememberMain()
    val rounds = if (state.settings.mode == Mode.Classic) " OF ${state.settings.rounds}" else ""
    Column(Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 34.dp, bottom = 34.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("ROUND ${game.round}$rounds · CLUES", style = Type.kicker, modifier = Modifier.weight(1f))
            val left = game.impostersLeft
            Tag(if (left == 1) "1 imposter among you" else "$left imposters among you", color = Red)
        }
        AnimatedContent(
            targetState = game.speaker,
            transitionSpec = { (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 6 }) togetherWith fadeOut(tween(200)) },
            label = "speaker",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { speaker ->
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                if (speaker != null) {
                    PlayerBadge(state.name(speaker), state.color(speaker), 120.dp)
                    BasicText("${state.name(speaker)}'s turn", style = Type.huge)
                    BasicText("Say one word that fits the secret word, without giving it away.", style = Type.soft.copy(fontSize = 17.sp))
                }
            }
        }
        // The speaking order: who's spoken, who's up, who's out.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            game.order.forEachIndexed { i, id ->
                OrderSeat(state, id, spoken = i < game.turn, current = i == game.turn)
            }
            game.out.forEach { id -> OrderSeat(state, id, spoken = false, current = false, out = true) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("Next player", Modifier.focusRequester(main), large = true, onClick = onNext)
            Pill("Call a vote now", large = true, onClick = onVote)
            Spacer(Modifier.weight(1f))
            BasicText("Phones tap Done after their clue", style = Type.soft.copy(fontSize = 13.sp))
        }
    }
}

@Composable
private fun OrderSeat(state: RoomState, id: String, spoken: Boolean, current: Boolean, out: Boolean = false) {
    val ring by animateColorAsState(if (current) Gold else Color.Transparent, tween(250), label = "ring")
    val fade by animateFloatAsState(if (spoken || out) 0.4f else 1f, tween(250), label = "fade")
    Column(Modifier.width(76.dp).alpha(fade), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(50.dp).border(3.dp, ring, CircleShape).padding(5.dp)) {
            PlayerBadge(state.name(id), state.color(id), 40.dp)
        }
        BasicText(state.name(id), style = Type.chip.copy(fontSize = 13.sp, color = Text), maxLines = 1)
        BasicText(
            when {
                out -> "OUT"
                spoken -> "done"
                current -> "now"
                else -> ""
            },
            style = Type.chip.copy(fontSize = 11.sp, color = if (out) Red else if (current) Gold else TextFaint),
        )
    }
}

// Everyone still in votes on their phone; the TV only shows who has voted.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoteScreen(state: RoomState, game: GameState, onClose: () -> Unit) {
    val main = rememberMain()
    val needed = when (state.settings.rule) {
        VoteRule.Majority -> "${game.alive.size / 2 + 1} votes for the same player puts them out"
        VoteRule.Everyone -> "Everyone else has to vote for the same player"
    }
    Screen(
        if (game.calledBy != null) "${state.name(game.calledBy).uppercase()} CALLED A VOTE" else "ROUND ${game.round} · VOTE",
        "Who's the imposter?",
        "Vote on your phone. $needed.",
        kickerColor = Red,
    ) {
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 4) {
            game.alive.forEach { id ->
                val voted = id in game.votes
                SeatCard(state, id, Modifier.weight(1f), dim = !voted, note = if (voted) "Voted" else "Thinking…", noteColor = if (voted) Mint else TextFaint)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Pill("Close the vote", Modifier.focusRequester(main), large = true, onClick = onClose)
            BasicText("Votes not in by then count as skips.", style = Type.soft.copy(fontSize = 13.sp))
        }
    }
}

// The votes, then who's out, then whether they were an imposter, a beat apart.
@Composable
fun ResultScreen(state: RoomState, game: GameState, onContinue: () -> Unit) {
    var stage by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(2_200)
        stage = 1
        if (game.votedOut != null) delay(1_800)
        stage = 2
    }
    val out = game.votedOut
    val byTarget = game.lastVotes.entries.groupBy({ it.value }, { it.key })
    Screen("THE VOTES", "Here's how everyone voted", kickerColor = Red) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                byTarget.entries.sortedWith(compareBy<Map.Entry<String?, List<String>>> { it.key == null }.thenByDescending { it.value.size }).forEach { (target, voters) ->
                    Row(
                        Modifier.fillMaxWidth().background(Panel, CardShape).border(1.dp, if (target != null && target == out && stage > 0) Red else Line, CardShape).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (target != null) PlayerBadge(state.name(target), state.color(target), 32.dp)
                        BasicText(if (target == null) "Skipped" else state.name(target), style = Type.name, modifier = Modifier.width(150.dp), maxLines = 1)
                        BasicText(if (target == null) (if (voters.size == 1) "1 player" else "${voters.size} players") else if (voters.size == 1) "1 vote" else "${voters.size} votes", style = Type.soft, modifier = Modifier.width(70.dp))
                        voters.forEach { PlayerBadge(state.name(it), state.color(it), 26.dp) }
                    }
                }
                if (byTarget.isEmpty()) BasicText("Nobody voted.", style = Type.soft)
            }
            Column(Modifier.width(340.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(stage >= 1, enter = fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.8f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (out != null) {
                            PlayerBadge(state.name(out), state.color(out), 96.dp)
                            BasicText("${state.name(out)} is out!", style = Type.title, maxLines = 1)
                        } else {
                            BasicText("Nobody is out", style = Type.title)
                            BasicText(
                                when {
                                    byTarget.keys.filterNotNull().isEmpty() -> "Everyone skipped."
                                    byTarget.filterKeys { it != null }.values.map { it.size }.let { sizes -> sizes.count { it == sizes.max() } > 1 } -> "It was a tie."
                                    else -> "Not enough players agreed."
                                },
                                style = Type.soft,
                            )
                        }
                    }
                }
                AnimatedVisibility(stage >= 2 && out != null, enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 }) {
                    val imposter = out in game.imposters
                    BasicText(
                        if (imposter) "${state.name(out)} was an imposter!" else "${state.name(out)} wasn't an imposter.",
                        style = Type.button.copy(fontSize = 24.sp, color = if (imposter) Red else Mint, textAlign = TextAlign.Center),
                    )
                }
                AnimatedVisibility(stage >= 2, enter = fadeIn(tween(400))) {
                    val main = rememberMain()
                    Pill("Continue", Modifier.focusRequester(main).padding(top = 8.dp), large = true, onClick = onContinue)
                }
            }
        }
    }
}

// A caught imposter picks the word on their phone; a right guess still wins it for them.
@Composable
fun GuessScreen(state: RoomState, game: GameState, onContinue: () -> Unit) {
    val name = state.name(game.votedOut)
    var revealed by remember(game.guess) { mutableIntStateOf(0) }
    LaunchedEffect(game.guess) {
        if (game.guess != null) {
            delay(1_800)
            revealed = 1
        }
    }
    val main = remember { FocusRequester() }
    LaunchedEffect(revealed) { if (revealed == 1) runCatching { main.requestFocus() } }
    Screen("LAST CHANCE", "$name was caught!", "$name gets one guess at the secret word. Guess it, and the imposters win anyway.", kickerColor = Red) {
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
            PlayerBadge(name, state.color(game.votedOut), 96.dp)
            val guess = game.guess
            if (guess == null) {
                BasicText("Choosing on their phone…", style = Type.title.copy(fontSize = 28.sp))
            } else {
                BasicText("$name guessed", style = Type.soft.copy(fontSize = 17.sp))
                BasicText(guess, style = Type.huge)
                AnimatedVisibility(revealed == 1, enter = fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.7f)) {
                    val right = guess == game.secret.word
                    BasicText(if (right) "Right!" else "Wrong!", style = Type.title.copy(color = if (right) Red else Mint))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (game.guess == null) {
                // Not focused, so a stray OK doesn't skip someone's guess.
                Pill("Skip the guess", large = true, onClick = onContinue)
                BasicText("Only if their phone is stuck.", style = Type.soft.copy(fontSize = 13.sp))
            } else if (revealed == 1) {
                Pill("Continue", Modifier.focusRequester(main), large = true, onClick = onContinue)
            }
        }
    }
}

// Who won and why, the word, and everyone's part in it.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FinalScreen(state: RoomState, game: GameState, onAgain: () -> Unit, onLobby: () -> Unit) {
    val main = rememberMain()
    val crew = game.winner == Side.Crew
    Column(Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 34.dp, bottom = 34.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BasicText(game.ending.uppercase(), style = Type.kicker.copy(color = if (crew) Mint else Red))
        BasicText(
            if (crew) "The crew wins!" else if (game.imposters.size == 1) "The imposter wins!" else "The imposters win!",
            style = Type.huge.copy(color = if (crew) Mint else Red),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("The word was", style = Type.soft.copy(fontSize = 17.sp))
            BasicText(game.secret.word, style = Type.title.copy(fontSize = 30.sp))
            Tag(game.secret.category)
            game.secret.closeWord?.let {
                BasicText("· the imposter's was", style = Type.soft.copy(fontSize = 17.sp))
                BasicText(it, style = Type.title.copy(fontSize = 30.sp, color = Red))
            }
        }
        FlowRow(Modifier.weight(1f).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 4) {
            // Imposters first.
            game.seats.sortedBy { it !in game.imposters }.forEach { id ->
                val imposter = id in game.imposters
                val round = game.out.indexOf(id)
                SeatCard(
                    state, id, Modifier.weight(1f),
                    note = (if (imposter) "Imposter" else "Crew") + if (round >= 0) " · voted out" else "",
                    noteColor = if (imposter) Red else Mint,
                    border = if (imposter) Red else Line,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("Play again", Modifier.focusRequester(main), large = true, onClick = onAgain)
            Pill("Back to the lobby", large = true, onClick = onLobby)
        }
    }
}

@Composable
private fun SeatCard(state: RoomState, id: String, modifier: Modifier, dim: Boolean = false, note: String, noteColor: Color, border: Color = Line) {
    val fade by animateFloatAsState(if (dim) 0.55f else 1f, tween(250), label = "seatFade")
    Row(
        modifier.alpha(fade).height(64.dp).background(Panel, CardShape).border(1.dp, border, CardShape).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlayerBadge(state.name(id), state.color(id), 38.dp)
        Column {
            BasicText(state.name(id), style = Type.name, maxLines = 1)
            BasicText(note, style = Type.chip.copy(fontSize = 12.sp, color = noteColor))
        }
    }
}

// Shown over a game after one Back press; a second press within a few seconds ends the game.
@Composable
fun BackWarning(visible: Boolean) {
    Box(Modifier.fillMaxSize().padding(bottom = 28.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(Modifier.background(Panel, CircleShape).border(1.dp, LineBright, CircleShape).padding(horizontal = 22.dp, vertical = 10.dp)) {
                BasicText("Press Back again to end this game", style = Type.body)
            }
        }
    }
}
