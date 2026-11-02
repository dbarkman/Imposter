package com.redmesastudios.imposter

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redmesastudios.imposter.ui.Gold
import com.redmesastudios.imposter.ui.Line
import com.redmesastudios.imposter.ui.Night
import com.redmesastudios.imposter.ui.Panel
import com.redmesastudios.imposter.ui.PlayerColors
import com.redmesastudios.imposter.ui.Red
import com.redmesastudios.imposter.ui.Text
import com.redmesastudios.imposter.ui.TextSoft
import com.redmesastudios.imposter.ui.Type

private val RowShape = RoundedCornerShape(12.dp)

// Everyone's wins and losses from every TV in the house, most wins first. OK on a player
// changes their name or color, or removes them; players who haven't played yet are listed
// below so they can be changed too.
@Composable
fun ScoresScreen(onBack: () -> Unit) {
    val saved by Records.state.collectAsState()
    LaunchedEffect(Unit) { Records.sync() }
    var editing by remember { mutableStateOf<Profile?>(null) }
    editing?.let { profile ->
        PlayerEditor(
            initial = profile,
            onCancel = { editing = null },
            onSave = { name, color ->
                Records.updatePlayer(profile.id, name, color)
                editing = null
            },
            onRemove = {
                Records.removePlayer(profile.id)
                editing = null
            },
        )
        return
    }
    BackHandler(onBack = onBack)
    val board = saved.scoreboard()
    val unplayed = saved.players.filter { p -> board.none { it.profile.id == p.id } }
    val first = remember { FocusRequester() }
    LaunchedEffect(board.size + unplayed.size) { runCatching { first.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 34.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText("SCORES", style = Type.kicker)
            BasicText(if (saved.players.isEmpty()) "No players yet" else "Who's winning", style = Type.title)
        }
        if (saved.players.isEmpty()) {
            BasicText("Join a game from a phone; everyone's wins and losses from every TV in the house show up here.", style = Type.soft)
            Pill("Back", Modifier.focusRequester(first), large = true, onClick = onBack)
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            Spacer(Modifier.width(36.dp))
            BasicText("PLAYER", style = Type.label, modifier = Modifier.weight(1f))
            Heading("PLAYED")
            Heading("WON")
            Heading("LOST")
            Heading("WIN %")
            Heading("AS IMPOSTER", wide = true)
            Heading("AS CREW", wide = true)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
            items(board, key = { it.profile.id }) { record ->
                val place = board.indexOf(record) + 1
                PlayerRow(record.profile, if (place == 1) Modifier.focusRequester(first) else Modifier, onClick = { editing = record.profile }) { ink ->
                    BasicText(place.toString(), style = Type.button.copy(fontSize = 18.sp), color = { if (place == 1 && ink == Text) Gold else ink }, modifier = Modifier.width(36.dp))
                    PlayerBadge(record.profile.name, PlayerColors[record.profile.color], 32.dp)
                    BasicText(record.profile.name, style = Type.name, color = { ink }, modifier = Modifier.weight(1f).padding(start = 12.dp), maxLines = 1)
                    Cell(record.played.toString(), ink)
                    Cell(record.won.toString(), ink, bold = true)
                    Cell(record.lost.toString(), ink)
                    Cell("${record.winPercent}%", ink)
                    Cell(fraction(record.wonAsImposter, record.asImposter), ink, wide = true)
                    Cell(fraction(record.wonAsCrew, record.asCrew), ink, wide = true)
                }
            }
            if (unplayed.isNotEmpty()) {
                item { BasicText("NOT PLAYED YET", style = Type.label, modifier = Modifier.padding(top = 10.dp, start = 10.dp)) }
                items(unplayed, key = { it.id }) { profile ->
                    PlayerRow(profile, if (board.isEmpty() && profile == unplayed.first()) Modifier.focusRequester(first) else Modifier, onClick = { editing = profile }) { ink ->
                        Spacer(Modifier.width(36.dp))
                        PlayerBadge(profile.name, PlayerColors[profile.color], 32.dp)
                        BasicText(profile.name, style = Type.name, color = { ink }, modifier = Modifier.weight(1f).padding(start = 12.dp), maxLines = 1)
                    }
                }
            }
        }
        HintRow("OK on a player to change their name or color", "Back to go back", "Imposter and crew show wins out of games")
    }
}

private fun fraction(won: Int, of: Int): String = if (of == 0) "–" else "$won of $of"

@Composable
private fun RowScope.Heading(text: String, wide: Boolean = false) {
    BasicText(text, style = Type.label.copy(textAlign = TextAlign.End), modifier = Modifier.width(if (wide) 120.dp else 76.dp))
}

@Composable
private fun RowScope.Cell(text: String, ink: Color, bold: Boolean = false, wide: Boolean = false) {
    val style: TextStyle = if (bold) Type.button.copy(fontSize = 18.sp, textAlign = TextAlign.End) else Type.body.copy(fontSize = 16.sp, textAlign = TextAlign.End)
    BasicText(text, style = style, color = { ink }, modifier = Modifier.width(if (wide) 120.dp else 76.dp), maxLines = 1)
}

@Composable
private fun PlayerRow(profile: Profile, modifier: Modifier, onClick: () -> Unit, content: @Composable RowScope.(Color) -> Unit) {
    val (focused, focus) = rememberFocus()
    val fill by animateColorAsState(if (focused) Gold else Panel, tween(200), label = "rowFill")
    val ink by animateColorAsState(if (focused) Night else Text, tween(200), label = "rowInk")
    Row(
        modifier
            .fillMaxWidth()
            .background(fill, RowShape)
            .border(1.dp, if (focused) Gold else Line, RowShape)
            .then(focus)
            .pressable(onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content(ink) }
}

// A player's first name and color, changed with the remote (the TV's keyboard for the name),
// or the player removed from every TV in the house.
@Composable
private fun PlayerEditor(initial: Profile, onCancel: () -> Unit, onSave: (String, Int) -> Unit, onRemove: () -> Unit) {
    BackHandler(onBack = onCancel)
    var name by remember { mutableStateOf(initial.name) }
    var color by remember { mutableIntStateOf(initial.color) }
    var confirmRemove by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val firstColor = remember { FocusRequester() }
    val save = remember { FocusRequester() }
    // Starts on Save, without the keyboard in the way.
    LaunchedEffect(Unit) { runCatching { save.requestFocus() } }
    val clean = name.trim()

    Column(Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 36.dp, bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            BasicText("PLAYERS", style = Type.kicker)
            BasicText("Change ${initial.name}", style = Type.title)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerBadge(clean.ifEmpty { "?" }, PlayerColors[color], 96.dp)
            Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText("FIRST NAME", style = Type.label)
                val (focused, focus) = rememberFocus()
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(14) },
                    singleLine = true,
                    textStyle = Type.title.copy(fontSize = 30.sp),
                    cursorBrush = SolidColor(Gold),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { runCatching { firstColor.requestFocus() } }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(field)
                        .then(focus)
                        .background(Panel, RoundedCornerShape(14.dp))
                        .border(2.dp, if (focused) Gold else Line, RoundedCornerShape(14.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText("FAVORITE COLOR", style = Type.label)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PlayerColors.forEachIndexed { i, swatch ->
                    ColorSwatch(swatch, selected = i == color, modifier = if (i == 0) Modifier.focusRequester(firstColor) else Modifier) { color = i }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("Save", Modifier.focusRequester(save), large = true) {
                if (clean.isEmpty()) runCatching { field.requestFocus() } else onSave(clean, color)
            }
            Pill("Cancel", large = true, onClick = onCancel)
            Spacer(Modifier.width(30.dp))
            Pill(if (confirmRemove) "Yes, remove ${initial.name}" else "Remove player", large = true) {
                if (confirmRemove) onRemove() else confirmRemove = true
            }
        }
        if (confirmRemove) BasicText("They come off the scores and the player list on every TV.", style = Type.soft.copy(fontSize = 13.sp, color = Red))
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (focused, focus) = rememberFocus()
    val scale by animateFloatAsState(if (focused) 1.15f else 1f, tween(200), label = "swatchScale")
    Box(
        modifier
            .size(58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(3.dp, if (focused) Text else if (selected) TextSoft else Color.Transparent, CircleShape)
            .then(focus)
            .pressable(onClick)
            .padding(7.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(14.dp).background(Night, CircleShape))
    }
}
