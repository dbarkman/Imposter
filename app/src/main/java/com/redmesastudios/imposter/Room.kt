package com.redmesastudios.imposter

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random

// A phone in the room. `profile` is the household's saved player it plays as; `seenAt` is when
// it last asked what to show, so a phone that went away isn't dealt into the next game.
data class Player(val id: String, val name: String, val color: Int, val profile: String? = null, val seenAt: Long = 0)

enum class Phase { Lobby, Deal, Clues, Vote, Result, Guess, Final }

// One game: who's in it and in what order they give clues, who the imposters are, who's been
// voted out, and where the clues and votes are.
data class GameState(
    val secret: Secret,
    val seats: List<String>,
    val imposters: Set<String>,
    val out: List<String> = emptyList(),
    val round: Int = 1,
    val turn: Int = 0,
    val ready: Set<String> = emptySet(),
    // Voter to the player they voted for; null is a skip.
    val votes: Map<String, String?> = emptyMap(),
    val calledBy: String? = null,
    // The last vote: everyone's votes and who it put out (null: nobody).
    val lastVotes: Map<String, String?> = emptyMap(),
    val votedOut: String? = null,
    val guessChoices: List<String> = emptyList(),
    val guess: String? = null,
    val winner: Side? = null,
    val ending: String = "",
) {
    val alive: List<String> get() = seats.filterNot { it in out }
    val impostersLeft: Int get() = alive.count { it in imposters }
    val crewLeft: Int get() = alive.size - impostersLeft

    // This round's speaking order: the players still in, starting one further along each round.
    val order: List<String>
        get() {
            val players = alive
            if (players.isEmpty()) return players
            val start = (round - 1) % players.size
            return players.drop(start) + players.take(start)
        }
    val speaker: String? get() = order.getOrNull(turn)
}

data class RoomState(
    val phase: Phase = Phase.Lobby,
    val players: List<Player> = emptyList(),
    val settings: Settings = Settings(),
    val game: GameState? = null,
    // Counts games started, so phones can tell a new game from the last one.
    val number: Int = 0,
) {
    fun player(id: String?): Player? = players.firstOrNull { it.id == id }
    fun name(id: String?): String = player(id)?.name ?: "Someone"
}

// How long a phone can go without checking in before it's left out of the next game.
const val AwayMillis = 20_000L

// The game's shared state. Phones change it from the web server's threads and the TV from the
// main thread, so every change happens under one lock and is published as a new RoomState.
class Room(val pin: String, private val clock: () -> Long = SystemClock::elapsedRealtime, private val random: Random = Random.Default) {
    private val lock = Any()
    private val mutable = MutableStateFlow(RoomState())
    val state: StateFlow<RoomState> = mutable.asStateFlow()

    private inline fun update(change: (RoomState) -> RoomState) = synchronized(lock) { mutable.value = change(mutable.value) }

    private inline fun updateGame(phase: Phase, change: (RoomState, GameState) -> RoomState?) = synchronized(lock) {
        val current = mutable.value
        val game = current.game
        if (current.phase == phase && game != null) change(current, game)?.let { mutable.value = it }
    }

    // A phone that already joined (it remembers its id) gets its seat back. A saved player can
    // only sit in one seat; null means another phone already has them.
    fun join(name: String, color: Int, id: String?, profile: String? = null): Player? = synchronized(lock) {
        val current = mutable.value
        if (profile != null && current.players.any { it.profile == profile && it.id != id }) return null
        current.players.firstOrNull { it.id == id }?.let { existing ->
            val renamed = existing.copy(name = cleanName(name, 0).takeIf { name.isNotBlank() } ?: existing.name, color = color.coerceIn(0, 7), profile = profile ?: existing.profile, seenAt = clock())
            mutable.value = current.copy(players = current.players.map { if (it.id == existing.id) renamed else it })
            return renamed
        }
        val player = Player(UUID.randomUUID().toString(), cleanName(name, current.players.size), color.coerceIn(0, 7), profile, clock())
        mutable.value = current.copy(players = current.players + player)
        player
    }

    // Only every few seconds, so a phone's polling doesn't redraw the TV twice a second.
    fun seen(id: String?) = synchronized(lock) {
        val current = mutable.value
        if (current.players.any { it.id == id && clock() - it.seenAt > 3_000 }) {
            mutable.value = current.copy(players = current.players.map { if (it.id == id) it.copy(seenAt = clock()) else it })
        }
    }

    // Phones that checked in lately; the next game deals them in.
    fun here(): List<Player> = synchronized(lock) { mutable.value.players.filter { clock() - it.seenAt < AwayMillis } }

    fun seatedProfiles(): Set<String> = synchronized(lock) { mutable.value.players.mapNotNull { it.profile }.toSet() }

    fun changeSettings(settings: Settings) = update { it.copy(settings = settings) }

    // Deals a new game to everyone here: shuffled seats, the imposters, and a first speaker who
    // isn't one (they'd have nothing to go on). False when fewer than three are here.
    fun start(secret: Secret, choices: List<String>): Boolean = synchronized(lock) {
        val current = mutable.value
        val players = current.players.filter { clock() - it.seenAt < AwayMillis }
        if (players.size < 3) return false
        val seats = players.map { it.id }.shuffled(random)
        val imposters = seats.shuffled(random).take(imposterCount(current.settings.imposters, seats.size)).toSet()
        val first = seats.indexOfFirst { it !in imposters }
        mutable.value = current.copy(
            phase = Phase.Deal,
            game = GameState(secret, seats.drop(first) + seats.take(first), imposters, guessChoices = choices),
            number = current.number + 1,
        )
        true
    }

    fun ready(id: String) = updateGame(Phase.Deal) { state, game ->
        if (id !in game.seats) return@updateGame null
        val ready = game.ready + id
        // Clues start once everyone has seen their word.
        if (game.seats.all { it in ready }) state.copy(phase = Phase.Clues, game = game.copy(ready = ready))
        else state.copy(game = game.copy(ready = ready))
    }

    fun beginClues() = updateGame(Phase.Deal) { state, _ -> state.copy(phase = Phase.Clues) }

    // The speaker taps Done on their phone; the TV can move on for them.
    fun done(id: String) = synchronized(lock) {
        if (mutable.value.game?.speaker == id) nextTurn()
    }

    fun nextTurn() = updateGame(Phase.Clues) { state, game ->
        val turn = game.turn + 1
        when {
            turn < game.order.size -> state.copy(game = game.copy(turn = turn))
            // Ruthless votes after every round; Classic after its last one.
            state.settings.mode == Mode.Ruthless || game.round >= state.settings.rounds ->
                state.copy(phase = Phase.Vote, game = game.copy(turn = turn, votes = emptyMap()))
            else -> state.copy(game = game.copy(round = game.round + 1, turn = 0))
        }
    }

    // Anyone still in can call a vote during the clues; the TV can too (id null).
    fun callVote(id: String?) = updateGame(Phase.Clues) { state, game ->
        if (id != null && id !in game.alive) return@updateGame null
        state.copy(phase = Phase.Vote, game = game.copy(votes = emptyMap(), calledBy = id))
    }

    // A vote for someone still in (not yourself), or a skip (null). The vote closes when every
    // player still in has voted.
    fun vote(id: String, target: String?) = updateGame(Phase.Vote) { state, game ->
        if (id !in game.alive || id in game.votes || target == id || (target != null && target !in game.alive)) return@updateGame null
        val votes = game.votes + (id to target)
        if (game.alive.all { it in votes }) close(state, game.copy(votes = votes)) else state.copy(game = game.copy(votes = votes))
    }

    // The TV closes the vote; anyone who hasn't voted skips.
    fun closeVote() = updateGame(Phase.Vote) { state, game -> close(state, game) }

    private fun close(state: RoomState, game: GameState): RoomState {
        val out = tally(game.votes, game.alive, state.settings.rule)
        return state.copy(
            phase = Phase.Result,
            game = game.copy(lastVotes = game.votes, votedOut = out, out = if (out == null) game.out else game.out + out, votes = emptyMap()),
        )
    }

    // After the TV shows who's out: a caught imposter gets one guess at the word (unless they
    // had a close word and never knew), otherwise the game ends or the next round starts.
    fun afterResult() = updateGame(Phase.Result) { state, game ->
        val caught = game.votedOut?.takeIf { it in game.imposters }
        if (caught != null && state.settings.gets != ImposterGets.CloseWord) state.copy(phase = Phase.Guess, game = game.copy(guess = null))
        else carryOn(state, game)
    }

    fun guess(id: String, word: String) = updateGame(Phase.Guess) { state, game ->
        if (id != game.votedOut || game.guess != null || word !in game.guessChoices) null else state.copy(game = game.copy(guess = word))
    }

    // After the TV shows the guess (or skips it, when the phone is stuck): a right guess wins
    // it for the imposters.
    fun afterGuess() = updateGame(Phase.Guess) { state, game ->
        if (game.guess == game.secret.word) {
            state.copy(phase = Phase.Final, game = game.copy(winner = Side.Imposters, ending = "${state.name(game.votedOut)} was caught but guessed the word"))
        } else {
            carryOn(state, game)
        }
    }

    private fun carryOn(state: RoomState, game: GameState): RoomState {
        val out = game.votedOut
        val ended: Pair<Side, String>? = when (state.settings.mode) {
            Mode.Classic -> when {
                out == null -> Side.Imposters to "Nobody was voted out"
                out in game.imposters -> Side.Crew to "${state.name(out)} was caught" + if (game.guess != null) " and guessed wrong" else ""
                else -> Side.Imposters to "${state.name(out)} was voted out but wasn't an imposter"
            }
            Mode.Ruthless -> when {
                game.impostersLeft == 0 -> Side.Crew to if (game.imposters.size == 1) "The imposter was voted out" else "Every imposter was voted out"
                game.impostersLeft >= game.crewLeft -> Side.Imposters to if (game.imposters.size == 1) "The imposter caught up with the crew" else "The imposters caught up with the crew"
                else -> null
            }
        }
        return if (ended != null) {
            state.copy(phase = Phase.Final, game = game.copy(winner = ended.first, ending = ended.second))
        } else {
            state.copy(phase = Phase.Clues, game = game.copy(round = game.round + 1, turn = 0, calledBy = null, guess = null))
        }
    }

    fun backToLobby() = update { it.copy(phase = Phase.Lobby, game = null) }

    // What one phone shows, as JSON for its page.
    fun phoneView(id: String?): JSONObject = synchronized(lock) {
        val state = mutable.value
        val me = state.player(id) ?: return JSONObject().put("phase", "join")
        val json = JSONObject().put("name", me.name).put("color", me.color).put("game", state.number)
        val game = state.game
        if (state.phase == Phase.Lobby || game == null) {
            return json.put("phase", "lobby").put("players", state.players.size).put("summary", state.settings.summary(state.players.size))
        }
        if (me.id !in game.seats) return json.put("phase", "watching")
        val imposter = me.id in game.imposters
        val knows = imposter && state.settings.gets != ImposterGets.CloseWord
        json.put("phase", state.phase.name.lowercase())
            .put("out", me.id in game.out)
            .put("imposter", knows)
            .put("word", if (!imposter) game.secret.word else game.secret.closeWord ?: "")
            .put("category", if (!imposter || state.settings.gets != ImposterGets.Nothing) game.secret.category else "")
            .put("partners", JSONArray(if (knows) game.imposters.filter { it != me.id }.map { state.name(it) } else emptyList()))
            .put("round", game.round)
        when (state.phase) {
            Phase.Deal -> json.put("ready", me.id in game.ready)
            Phase.Clues -> json.put("speaker", state.name(game.speaker)).put("myTurn", game.speaker == me.id)
                .put("speakerColor", state.player(game.speaker)?.color ?: 0)
            Phase.Vote -> json.put("voted", me.id in game.votes)
                .put("mine", game.votes[me.id]?.let { state.name(it) } ?: if (me.id in game.votes) "skip" else "")
                .put("calledBy", game.calledBy?.let { state.name(it) } ?: "")
                .put("candidates", JSONArray(game.alive.filter { it != me.id }.map {
                    JSONObject().put("id", it).put("name", state.name(it)).put("color", state.player(it)?.color ?: 0)
                }))
            Phase.Guess -> json.put("guessing", game.votedOut == me.id && game.guess == null)
                .put("guesser", state.name(game.votedOut))
                .put("choices", JSONArray(game.guessChoices))
            Phase.Final -> json.put("won", (game.winner == Side.Imposters) == imposter)
                .put("winner", game.winner?.name?.lowercase() ?: "")
                .put("ending", game.ending)
                .put("secret", game.secret.word)
                .put("secretCategory", game.secret.category)
                .put("closeWord", game.secret.closeWord ?: "")
                .put("imposters", JSONArray(game.seats.filter { it in game.imposters }.map { state.name(it) }))
            else -> Unit
        }
        json
    }
}

// Names show on the TV, so keep them short and printable; a blank one gets a stand-in.
fun cleanName(raw: String, seat: Int): String =
    raw.filter { !it.isISOControl() }.trim().take(14).ifEmpty { "Player ${seat + 1}" }
