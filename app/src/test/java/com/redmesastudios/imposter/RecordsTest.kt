package com.redmesastudios.imposter

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordsTest {
    private val dad = Profile("dad", "Dad", 0, 1)
    private val mom = Profile("mom", "Mom", 1, 1)
    private val kid = Profile("kid", "Kid", 2, 1)
    private var next = 0

    private fun result(player: Profile, won: Boolean, imposter: Boolean = false) =
        GameResult("r${next++}", player.id, next.toLong(), imposter, won, Mode.Classic)

    @Test
    fun mostWinsLeadsAndOneLuckyGameDoesNot() {
        val saved = Saved(
            listOf(dad, mom, kid),
            listOf(result(kid, true)) + List(5) { result(mom, it < 3) } + List(4) { result(dad, it < 3) },
        )
        // Mom and Dad both have 3 wins; Dad's 3 of 4 beats Mom's 3 of 5. Kid's 1 of 1 comes last.
        assertEquals(listOf("Dad", "Mom", "Kid"), saved.scoreboard().map { it.profile.name })
    }

    @Test
    fun winsSplitIntoImposterAndCrew() {
        val saved = Saved(listOf(dad), listOf(result(dad, true, imposter = true), result(dad, false, imposter = true), result(dad, true)))
        val record = saved.scoreboard().single()
        assertEquals(3, record.played)
        assertEquals(2, record.won)
        assertEquals(1, record.wonAsImposter)
        assertEquals(2, record.asImposter)
        assertEquals(1, record.wonAsCrew)
        assertEquals(1, record.asCrew)
        assertEquals(66, record.winPercent)
    }

    @Test
    fun removedPlayersAndPlayersWithoutGamesStayOffTheBoard() {
        val saved = Saved(listOf(dad, mom.copy(gone = true), kid), listOf(result(dad, true), result(mom, true)))
        assertEquals(listOf("Dad"), saved.scoreboard().map { it.profile.name })
    }

    @Test
    fun mergingKeepsTheNewestPlayerAndEveryResultOnce() {
        val renamed = dad.copy(name = "Pops", at = 5)
        val shared = result(dad, true)
        val merged = mergeSaved(Saved(listOf(dad), listOf(shared)), Saved(listOf(renamed), listOf(shared, result(dad, false))))
        assertEquals("Pops", merged.players.single().name)
        assertEquals(2, merged.results.size)
    }
}
