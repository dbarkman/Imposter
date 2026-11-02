package com.redmesastudios.imposter

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redmesastudios.imposter.ui.Line
import com.redmesastudios.imposter.ui.LineBright
import com.redmesastudios.imposter.ui.Night
import com.redmesastudios.imposter.ui.Panel
import com.redmesastudios.imposter.ui.PlayerColors
import com.redmesastudios.imposter.ui.Red
import com.redmesastudios.imposter.ui.Text
import com.redmesastudios.imposter.ui.Type
import kotlinx.coroutines.delay

// The QR code to join, who's joined, and Start. Phones that stopped checking in show as away
// and aren't dealt in.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Lobby(state: RoomState, address: String?, onStart: () -> Unit, onSettings: () -> Unit, onScores: () -> Unit) {
    val start = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { start.requestFocus() } }
    val now by produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            delay(2_000)
            value = SystemClock.elapsedRealtime()
        }
    }
    val here = state.players.count { now - it.seenAt < AwayMillis }
    Row(Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 36.dp, bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BasicText("IMPOSTER", style = Type.kicker.copy(color = Red))
            BasicText("Scan to join", style = Type.title)
            Box(Modifier.size(210.dp).background(Text, RoundedCornerShape(18.dp)).padding(14.dp), contentAlignment = Alignment.Center) {
                if (address != null) QrCode(address, Modifier.fillMaxSize())
                else BasicText("The TV isn't on Wi-Fi right now.", style = Type.body.copy(color = Night))
            }
            address?.let {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    BasicText("Or open this on a phone on the home Wi-Fi", style = Type.soft.copy(fontSize = 13.sp))
                    BasicText(it.substringBefore("/?"), style = Type.button.copy(fontSize = 20.sp, color = Text))
                    BasicText("and enter code ${it.substringAfter("pin=")}", style = Type.soft.copy(fontSize = 13.sp))
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText("Players", style = Type.title.copy(fontSize = 26.sp), modifier = Modifier.weight(1f))
                BasicText(
                    when (here) {
                        0 -> "Nobody yet"
                        1 -> "1 here"
                        else -> "$here here"
                    },
                    style = Type.soft,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp), maxItemsInEachRow = 3) {
                state.players.forEach { player ->
                    val away = now - player.seenAt >= AwayMillis
                    AnimatedVisibility(visible = true, enter = scaleIn(tween(300)) + fadeIn(tween(300)), modifier = Modifier.weight(1f)) {
                        Row(
                            Modifier.alpha(if (away) 0.4f else 1f).background(Panel, RoundedCornerShape(12.dp)).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(9.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlayerBadge(player.name, PlayerColors[player.color], 32.dp)
                            Column {
                                BasicText(player.name, style = Type.name.copy(fontSize = 17.sp), maxLines = 1)
                                if (away) BasicText("away", style = Type.soft.copy(fontSize = 11.sp))
                            }
                        }
                    }
                }
                if (state.players.size < 3) {
                    Box(Modifier.weight(1f).border(1.dp, LineBright, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 16.dp)) {
                        BasicText("Need 3 or more…", style = Type.soft)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            BasicText(
                "Everyone gets the same secret word, except the imposter. Take turns saying one word about it, then vote out whoever doesn't fit.",
                style = Type.body.copy(fontSize = 14.sp),
            )
            BasicText(state.settings.summary(here.coerceAtLeast(3)), style = Type.soft.copy(fontSize = 13.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(if (here < 3) "Need 3 players" else "Start the game", Modifier.focusRequester(start), large = true) {
                    if (here >= 3) onStart()
                }
                Pill("Settings", large = true, onClick = onSettings)
                Pill("Scores", large = true, onClick = onScores)
            }
        }
    }
}

// Every choice on one scrolling screen; Back or Done keeps them.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(bank: WordBank, initial: Settings, players: Int, onDone: (Settings) -> Unit) {
    var settings by remember { mutableStateOf(initial) }
    BackHandler { onDone(settings) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 60.dp, end = 60.dp, top = 32.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText("SETTINGS", style = Type.kicker)
                    BasicText("How do you want to play?", style = Type.title)
                }
                Pill("Done", large = true) { onDone(settings) }
            }
        }
        item {
            Choice("GAME", settings.mode.note) {
                Mode.entries.forEachIndexed { i, mode ->
                    Pill(mode.title, if (i == 0) Modifier.focusRequester(first) else Modifier, selected = settings.mode == mode) { settings = settings.copy(mode = mode) }
                }
            }
        }
        if (settings.mode == Mode.Classic) item {
            Choice("ROUNDS OF CLUES BEFORE THE VOTE", "Anyone can call the vote sooner from their phone.") {
                RoundChoices.forEach { n -> Pill(n.toString(), selected = settings.rounds == n) { settings = settings.copy(rounds = n) } }
            }
        }
        item {
            Choice(
                "IMPOSTERS",
                if (settings.imposters == 0) "Auto: one for up to 6 players, two up to 10, three beyond. ${players.coerceAtLeast(3)} players here gets ${autoImposters(players.coerceAtLeast(3))}."
                else "Never more than can be outnumbered: with 3 or 4 players there's one.",
            ) {
                Pill("Auto", selected = settings.imposters == 0) { settings = settings.copy(imposters = 0) }
                (1..3).forEach { n -> Pill(n.toString(), selected = settings.imposters == n) { settings = settings.copy(imposters = n) } }
            }
        }
        item {
            Choice("THE IMPOSTER GETS", settings.gets.note) {
                ImposterGets.entries.forEach { gets -> Pill(gets.title, selected = settings.gets == gets) { settings = settings.copy(gets = gets) } }
            }
        }
        item {
            Choice("TO VOTE SOMEONE OUT", settings.rule.note + " A tie puts nobody out.") {
                VoteRule.entries.forEach { rule -> Pill(rule.title, selected = settings.rule == rule) { settings = settings.copy(rule = rule) } }
            }
        }
        item {
            Choice("CATEGORY", "${bank.categories.sumOf { it.words.size }} words in ${bank.categories.size} categories.") {
                Pill("Any", selected = settings.category == null) { settings = settings.copy(category = null) }
                bank.categories.forEach { c -> Pill(c.name, selected = settings.category == c.name) { settings = settings.copy(category = c.name) } }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choice(label: String, note: String, pills: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(label, style = Type.label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { pills() }
        BasicText(note, style = Type.soft.copy(fontSize = 13.sp))
    }
}
