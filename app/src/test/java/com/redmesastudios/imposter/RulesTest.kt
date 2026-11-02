package com.redmesastudios.imposter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RulesTest {
    private val bank = WordBank(
        listOf(
            WordCategory("Food", listOf("Pizza", "Tacos", "Soup", "Toast", "Pancakes", "Waffles", "Sushi", "Ramen", "Nachos")),
            WordCategory("Animals", listOf("Dog", "Cat")),
        ),
    )

    @Test
    fun autoImpostersFollowTheGroupSize() {
        assertEquals(listOf(1, 1, 1, 1, 2, 2, 2, 2, 3), (3..11).map(::autoImposters))
    }

    @Test
    fun impostersAlwaysStartOutnumbered() {
        assertEquals(1, imposterCount(3, 3))
        assertEquals(1, imposterCount(2, 4))
        assertEquals(2, imposterCount(2, 5))
        assertEquals(2, imposterCount(0, 7))
    }

    @Test
    fun majorityNeedsMoreThanHalfOfThePlayersStillIn() {
        val alive = listOf("a", "b", "c", "d", "e")
        assertEquals("a", tally(mapOf("b" to "a", "c" to "a", "d" to "a", "e" to null, "a" to "b"), alive, VoteRule.Majority))
        // Two of five isn't a majority, even as the most votes.
        assertNull(tally(mapOf("b" to "a", "c" to "a", "d" to null, "e" to null, "a" to "b"), alive, VoteRule.Majority))
    }

    @Test
    fun aTiePutsNobodyOut() {
        assertNull(tally(mapOf("a" to "b", "b" to "a", "c" to "a", "d" to "b"), listOf("a", "b", "c", "d"), VoteRule.Majority))
    }

    @Test
    fun everyoneMeansEveryoneButTheAccused() {
        val alive = listOf("a", "b", "c", "d")
        assertEquals("a", tally(mapOf("a" to "b", "b" to "a", "c" to "a", "d" to "a"), alive, VoteRule.Everyone))
        assertNull(tally(mapOf("a" to "b", "b" to "a", "c" to "a", "d" to null), alive, VoteRule.Everyone))
    }

    @Test
    fun recentWordsWaitTheirTurn() {
        val recent = setOf("Animals|Dog")
        repeat(20) { seed -> assertEquals("Cat", pickSecret(bank, "Animals", recent, Random(seed)).word) }
    }

    @Test
    fun theGuessIncludesTheWordAndSevenOthers() {
        val secret = Secret("Food", "Pizza")
        val choices = guessChoices(bank, secret, Random(1))
        assertEquals(8, choices.size)
        assertTrue("Pizza" in choices)
        assertEquals(8, choices.toSet().size)
    }
}
