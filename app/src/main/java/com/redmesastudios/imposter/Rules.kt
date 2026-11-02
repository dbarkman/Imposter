package com.redmesastudios.imposter

import android.content.Context
import org.json.JSONArray
import kotlin.random.Random

enum class Mode(val title: String, val note: String) {
    Classic("Classic", "A few rounds of clues, then one vote. Catch an imposter to win."),
    Ruthless("Ruthless", "A vote after every round, and whoever's voted out is out, imposter or not."),
}

enum class ImposterGets(val title: String, val note: String) {
    Nothing("Nothing", "They only know they're the imposter."),
    Hint("The category", "They know it's, say, Food, but not the word."),
    CloseWord("A close word", "They get a different word from the same category and don't know they're the imposter."),
}

enum class VoteRule(val title: String, val note: String) {
    Majority("Majority", "More than half of the players still in."),
    Everyone("Everyone", "Everyone else has to agree."),
}

// What the lobby's Settings choose. `imposters` 0 means pick by the number of players.
data class Settings(
    val mode: Mode = Mode.Classic,
    val imposters: Int = 0,
    val gets: ImposterGets = ImposterGets.Hint,
    val rule: VoteRule = VoteRule.Majority,
    val rounds: Int = 2,
    val category: String? = null,
) {
    fun summary(players: Int): String = listOfNotNull(
        mode.title,
        if (mode == Mode.Classic) "$rounds ${if (rounds == 1) "round" else "rounds"}" else null,
        imposterCount(imposters, players).let { if (it == 1) "1 imposter" else "$it imposters" } + if (imposters == 0) " (auto)" else "",
        "imposter gets ${gets.title.lowercase()}",
        "${rule.title.lowercase()} vote",
        category ?: "every category",
    ).joinToString(" · ")
}

val RoundChoices = listOf(1, 2, 3)

// One imposter for up to six players, two up to ten, three beyond: about one for every four
// or five players, which is where most groups' advice lands.
fun autoImposters(players: Int): Int = when {
    players <= 6 -> 1
    players <= 10 -> 2
    else -> 3
}

// The imposters must start outnumbered, or they've already won.
fun imposterCount(setting: Int, players: Int): Int =
    (if (setting == 0) autoImposters(players) else setting).coerceAtMost((players - 1) / 2).coerceAtLeast(1)

enum class Side { Crew, Imposters }

data class WordCategory(val name: String, val words: List<String>)

class WordBank(val categories: List<WordCategory>) {
    companion object {
        fun load(context: Context): WordBank = parse(context.assets.open("words.json").bufferedReader().use { it.readText() })

        fun parse(json: String): WordBank {
            val array = JSONArray(json)
            return WordBank(List(array.length()) { i ->
                val category = array.getJSONObject(i)
                val words = category.getJSONArray("words")
                WordCategory(category.getString("category"), List(words.length()) { words.getString(it) })
            })
        }
    }
}

// The secret word, and the imposters' own word when they get a close one.
data class Secret(val category: String, val word: String, val closeWord: String?)

// A word from the chosen category (or any), avoiding the ones played most recently. `recent`
// holds "category|word" keys.
fun pickSecret(bank: WordBank, category: String?, gets: ImposterGets, recent: Collection<String>, random: Random): Secret {
    val pool = bank.categories.filter { category == null || it.name == category }.ifEmpty { bank.categories }
    val all = pool.flatMap { c -> c.words.map { c to it } }
    val fresh = all.filterNot { (c, w) -> "${c.name}|$w" in recent }.ifEmpty { all }
    val (picked, word) = fresh.random(random)
    val close = if (gets == ImposterGets.CloseWord) picked.words.filter { it != word }.random(random) else null
    return Secret(picked.name, word, close)
}

// What an imposter who's voted out picks their guess from: the word and seven others from its
// category.
fun guessChoices(bank: WordBank, secret: Secret, random: Random): List<String> {
    val others = bank.categories.firstOrNull { it.name == secret.category }?.words.orEmpty().filter { it != secret.word }
    return (others.shuffled(random).take(7) + secret.word).shuffled(random)
}

// Who the vote puts out, or null: the most-voted player, if there's no tie for the most and
// enough players agree. Skipped votes count against putting anyone out.
fun tally(votes: Map<String, String?>, alive: List<String>, rule: VoteRule): String? {
    val counts = votes.filterKeys { it in alive }.values.filterNotNull().filter { it in alive }.groupingBy { it }.eachCount()
    val most = counts.values.maxOrNull() ?: return null
    if (counts.count { it.value == most } > 1) return null
    val top = counts.entries.first { it.value == most }.key
    return when (rule) {
        VoteRule.Majority -> top.takeIf { most * 2 > alive.size }
        // Nobody votes for themselves, so everyone else is one fewer than the players still in.
        VoteRule.Everyone -> top.takeIf { most == alive.size - 1 }
    }
}
