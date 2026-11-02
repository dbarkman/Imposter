package com.redmesastudios.imposter

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

private const val Tag = "Imposter.Records"

// The household's players, a first name and a favorite color, shared with the household's
// other games through the Game Bridge. A
// removed player stays as a "gone" note so the removal reaches every TV. `at` is when it last
// changed; the newest copy wins.
data class Profile(val id: String, val name: String, val color: Int, val at: Long, val gone: Boolean = false)

// One player's finished game: whether they were an imposter and whether their side won. Never
// changed once saved, so the TVs only ever add to the list.
data class GameResult(val id: String, val player: String, val playedAt: Long, val imposter: Boolean, val won: Boolean, val mode: Mode)

data class Saved(val profiles: List<Profile> = emptyList(), val results: List<GameResult> = emptyList()) {
    val players: List<Profile> get() = profiles.filterNot { it.gone }.sortedBy { it.name.lowercase() }

    fun player(id: String?): Profile? = profiles.firstOrNull { it.id == id && !it.gone }
}

// Two copies as one: each player at their newest, every result once.
fun mergeSaved(a: Saved, b: Saved): Saved = Saved(
    profiles = (a.profiles + b.profiles).groupBy { it.id }.values.map { same -> same.maxBy { it.at } },
    results = (a.results + b.results).distinctBy { it.id }.sortedBy { it.playedAt },
)

data class PlayerRecord(val profile: Profile, val played: Int, val won: Int, val asImposter: Int, val wonAsImposter: Int) {
    val lost: Int get() = played - won
    val asCrew: Int get() = played - asImposter
    val wonAsCrew: Int get() = won - wonAsImposter
    val winPercent: Int get() = if (played == 0) 0 else won * 100 / played
}

// The scoreboard: everyone still here who has played, most wins first, then the better win
// rate (so one lucky game doesn't top the board), then by name.
fun Saved.scoreboard(): List<PlayerRecord> {
    val byPlayer = results.groupBy { it.player }
    return players.mapNotNull { profile ->
        val mine = byPlayer[profile.id].orEmpty()
        if (mine.isEmpty()) null
        else PlayerRecord(profile, mine.size, mine.count { it.won }, mine.count { it.imposter }, mine.count { it.imposter && it.won })
    }.sortedWith(
        compareByDescending<PlayerRecord> { it.won }
            .thenByDescending { it.won.toDouble() / it.played }
            .thenBy { it.profile.name.lowercase() },
    )
}

// Everyone's part in a finished game, for the players who joined as a saved player.
fun RoomState.results(at: Long): List<GameResult> {
    val game = game ?: return emptyList()
    val winner = game.winner ?: return emptyList()
    return game.seats.mapNotNull { id ->
        val profile = player(id)?.profile ?: return@mapNotNull null
        val imposter = id in game.imposters
        GameResult(UUID.randomUUID().toString(), profile, at, imposter, (winner == Side.Imposters) == imposter, settings.mode)
    }
}

// The players and results in files/records.json, swapped with the household's other TVs and
// games through the Game Bridge (GameBridge.kt).
object Records {
    private val lock = Any()
    private val mutable = MutableStateFlow(Saved())
    val state: StateFlow<Saved> = mutable.asStateFlow()
    private val io = Executors.newSingleThreadExecutor()
    private var file: File? = null

    fun load(context: Context) = synchronized(lock) {
        if (file != null) return
        val saved = File(context.applicationContext.filesDir, "records.json")
        file = saved
        mutable.value = runCatching { if (saved.exists()) savedFromJson(JSONObject(saved.readText())) else Saved() }
            .onFailure { Log.w(Tag, "Couldn't read the records", it) }
            .getOrDefault(Saved())
    }

    fun find(id: String?): Profile? = state.value.player(id)

    // The same first name again is the same person.
    fun addPlayer(name: String, color: Int): Profile = synchronized(lock) {
        val clean = name.filter { !it.isISOControl() }.trim().take(14)
        mutable.value.players.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it }
        val profile = Profile(UUID.randomUUID().toString(), clean, color.coerceIn(0, 7), System.currentTimeMillis())
        change { it.copy(profiles = it.profiles + profile) }
        profile
    }

    fun updatePlayer(id: String, name: String, color: Int) = synchronized(lock) {
        val clean = name.filter { !it.isISOControl() }.trim().take(14)
        change { saved ->
            saved.copy(profiles = saved.profiles.map {
                if (it.id == id) it.copy(name = clean.ifEmpty { it.name }, color = color.coerceIn(0, 7), at = System.currentTimeMillis()) else it
            })
        }
    }

    fun removePlayer(id: String) = synchronized(lock) {
        change { saved -> saved.copy(profiles = saved.profiles.map { if (it.id == id) it.copy(gone = true, at = System.currentTimeMillis()) else it }) }
    }

    fun recordResults(results: List<GameResult>) {
        if (results.isNotEmpty()) synchronized(lock) { change { it.copy(results = it.results + results) } }
    }

    // Swaps records with SimpleTV, which shares them with the household's other TVs.
    fun sync() {
        io.execute {
            val household = runCatching { GameBridge.exchange(state.value) }
                .onFailure { Log.i(Tag, "No household sync: ${it.message}") }
                .getOrNull() ?: return@execute
            synchronized(lock) {
                val merged = mergeSaved(mutable.value, household)
                if (merged.profiles.toSet() != mutable.value.profiles.toSet() || merged.results != mutable.value.results) {
                    mutable.value = merged
                    writeLater(merged)
                }
            }
        }
    }

    private fun change(edit: (Saved) -> Saved) {
        mutable.value = edit(mutable.value)
        writeLater(mutable.value)
        sync()
    }

    private fun writeLater(saved: Saved) {
        val target = file ?: return
        io.execute {
            runCatching {
                val temp = File(target.parentFile, "records.json.tmp")
                temp.writeText(savedToJson(saved).toString())
                temp.renameTo(target)
            }.onFailure { Log.w(Tag, "Couldn't save the records", it) }
        }
    }
}

// The collections as the Game Bridge shares them, each a list of {"id", "at", fields}. Every
// Red Mesa Studios game on the stick shares "players"; results are this game's own.
const val PlayersCollection = "players"
const val ResultsCollection = "imposter-games"

fun savedToJson(saved: Saved): JSONObject = JSONObject()
    .put(PlayersCollection, JSONArray(saved.profiles.map {
        JSONObject().put("id", it.id).put("at", it.at).put("name", it.name).put("color", it.color).put("gone", it.gone)
    }))
    .put(ResultsCollection, JSONArray(saved.results.map {
        JSONObject().put("id", it.id).put("at", it.playedAt).put("player", it.player)
            .put("imposter", it.imposter).put("won", it.won).put("mode", it.mode.name)
    }))

fun savedFromJson(json: JSONObject): Saved = Saved(
    profiles = json.optJSONArray(PlayersCollection).objects().map {
        Profile(it.getString("id"), it.getString("name"), it.optInt("color"), it.getLong("at"), it.optBoolean("gone"))
    },
    results = json.optJSONArray(ResultsCollection).objects().map {
        GameResult(
            it.getString("id"), it.getString("player"), it.getLong("at"), it.optBoolean("imposter"), it.optBoolean("won"),
            Mode.entries.firstOrNull { mode -> mode.name == it.optString("mode") } ?: Mode.Classic,
        )
    }.sortedBy { it.playedAt },
)

private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }
