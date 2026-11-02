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

private const val Tag = "Imposter.Players"

// The household's players, shared with Trivia Night: a first name and a favorite color. A
// removed player stays as a "gone" note so the removal reaches every TV. `at` is when it last
// changed; the newest copy wins.
data class Profile(val id: String, val name: String, val color: Int, val at: Long, val gone: Boolean = false)

// Two copies as one: each player at their newest.
fun mergeProfiles(a: List<Profile>, b: List<Profile>): List<Profile> =
    (a + b).groupBy { it.id }.values.map { same -> same.maxBy { it.at } }

fun List<Profile>.live(): List<Profile> = filterNot { it.gone }.sortedBy { it.name.lowercase() }

// The household's players in files/players.json, swapped with the other TVs (and Trivia
// Night) through SimpleTV's shared box (Household.kt).
object Players {
    private val lock = Any()
    private val mutable = MutableStateFlow<List<Profile>>(emptyList())
    val state: StateFlow<List<Profile>> = mutable.asStateFlow()
    private val io = Executors.newSingleThreadExecutor()
    private var file: File? = null

    fun load(context: Context) = synchronized(lock) {
        if (file != null) return
        val saved = File(context.applicationContext.filesDir, "players.json")
        file = saved
        mutable.value = runCatching { if (saved.exists()) profilesFromJson(JSONArray(saved.readText())) else emptyList() }
            .onFailure { Log.w(Tag, "Couldn't read the players", it) }
            .getOrDefault(emptyList())
    }

    fun find(id: String?): Profile? = state.value.firstOrNull { it.id == id && !it.gone }

    // The same first name again is the same person.
    fun add(name: String, color: Int): Profile = synchronized(lock) {
        val clean = name.filter { !it.isISOControl() }.trim().take(14)
        mutable.value.live().firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it }
        val profile = Profile(UUID.randomUUID().toString(), clean, color.coerceIn(0, 7), System.currentTimeMillis())
        mutable.value = mutable.value + profile
        writeLater(mutable.value)
        sync()
        profile
    }

    // Swaps players with SimpleTV, which shares them with the household's other TVs.
    fun sync() {
        io.execute {
            val household = runCatching { Household.exchange(state.value) }
                .onFailure { Log.i(Tag, "No household sync: ${it.message}") }
                .getOrNull() ?: return@execute
            synchronized(lock) {
                val merged = mergeProfiles(mutable.value, household)
                if (merged.toSet() != mutable.value.toSet()) {
                    mutable.value = merged
                    writeLater(merged)
                }
            }
        }
    }

    private fun writeLater(profiles: List<Profile>) {
        val target = file ?: return
        io.execute {
            runCatching {
                val temp = File(target.parentFile, "players.json.tmp")
                temp.writeText(profilesToJson(profiles).toString())
                temp.renameTo(target)
            }.onFailure { Log.w(Tag, "Couldn't save the players", it) }
        }
    }
}

// The household's players collection, which Trivia Night started.
const val PlayersCollection = "trivia-players"

fun profilesToJson(profiles: List<Profile>): JSONArray = JSONArray(profiles.map {
    JSONObject().put("id", it.id).put("at", it.at).put("name", it.name).put("color", it.color).put("gone", it.gone)
})

fun profilesFromJson(array: JSONArray): List<Profile> = List(array.length()) { array.getJSONObject(it) }.map {
    Profile(it.getString("id"), it.getString("name"), it.optInt("color"), it.getLong("at"), it.optBoolean("gone"))
}
