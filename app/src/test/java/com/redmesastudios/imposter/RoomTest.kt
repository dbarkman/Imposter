package com.redmesastudios.imposter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RoomTest {
    private var now = 100_000L
    private val secret = Secret("Food", "Pizza")
    private val choices = listOf("Pizza", "Tacos", "Soup", "Toast", "Pancakes", "Waffles", "Sushi", "Ramen")

    private fun room(players: Int, settings: Settings, seed: Int = 1): Room {
        val room = Room("1234", clock = { now }, random = Random(seed))
        room.changeSettings(settings)
        repeat(players) { room.join("P$it", it, null) }
        assertTrue(room.start(secret, choices))
        return room
    }

    private val Room.game: GameState get() = state.value.game!!

    private fun Room.everyoneReady() = game.seats.forEach { ready(it) }

    private fun Room.allClues() = repeat(game.order.size) { nextTurn() }

    // Everyone still in votes for `target`, who votes for someone else.
    private fun Room.voteOut(target: String) {
        val alive = game.alive
        alive.forEach { vote(it, if (it == target) alive.first { other -> other != target } else target) }
    }

    @Test
    fun theFirstSpeakerIsNeverAnImposter() {
        repeat(30) { seed ->
            val room = room(7, Settings(), seed)
            assertFalse(room.game.seats.first() in room.game.imposters)
        }
    }

    @Test
    fun cluesStartWhenEveryoneHasLooked() {
        val room = room(4, Settings())
        assertEquals(Phase.Deal, room.state.value.phase)
        room.everyoneReady()
        assertEquals(Phase.Clues, room.state.value.phase)
    }

    @Test
    fun classicVotesAfterItsLastRound() {
        val room = room(4, Settings(mode = Mode.Classic, rounds = 2))
        room.everyoneReady()
        room.allClues()
        assertEquals(Phase.Clues, room.state.value.phase)
        assertEquals(2, room.game.round)
        room.allClues()
        assertEquals(Phase.Vote, room.state.value.phase)
    }

    @Test
    fun onlyTheSpeakerCanSayDone() {
        val room = room(4, Settings())
        room.everyoneReady()
        val speaker = room.game.speaker!!
        room.done(room.game.order[1])
        assertEquals(speaker, room.game.speaker)
        room.done(speaker)
        assertEquals(room.game.order[1], room.game.speaker)
    }

    @Test
    fun aCaughtImposterWhoGuessesWrongLosesClassic() {
        val room = room(4, Settings(mode = Mode.Classic, gets = ImposterGets.Hint))
        room.everyoneReady()
        room.callVote(room.game.seats.first())
        val imposter = room.game.imposters.single()
        room.voteOut(imposter)
        assertEquals(Phase.Result, room.state.value.phase)
        assertEquals(imposter, room.game.votedOut)
        room.afterResult()
        assertEquals(Phase.Guess, room.state.value.phase)
        room.guess(imposter, "Tacos")
        room.afterGuess()
        assertEquals(Phase.Final, room.state.value.phase)
        assertEquals(Side.Crew, room.game.winner)
    }

    @Test
    fun aRightGuessWinsItForTheImposters() {
        val room = room(4, Settings(mode = Mode.Classic, gets = ImposterGets.Nothing))
        room.everyoneReady()
        room.callVote(null)
        val imposter = room.game.imposters.single()
        room.voteOut(imposter)
        room.afterResult()
        room.guess(imposter, "Pizza")
        room.afterGuess()
        assertEquals(Side.Imposters, room.game.winner)
    }

    @Test
    fun aFinishedGameGivesEverySavedPlayerAResult() {
        val room = Room("1234", clock = { now }, random = Random(1))
        repeat(4) { room.join("P$it", it, null, profile = "profile$it") }
        room.start(secret, choices)
        room.everyoneReady()
        room.callVote(null)
        val imposter = room.game.imposters.single()
        room.voteOut(room.game.seats.first { it !in room.game.imposters })
        room.afterResult()
        val results = room.state.value.results(5L)
        assertEquals(4, results.size)
        val imposterProfile = room.state.value.player(imposter)!!.profile
        assertEquals(listOf(imposterProfile), results.filter { it.imposter }.map { it.player })
        assertTrue(results.all { it.won == it.imposter })
    }

    @Test
    fun classicLosesWhenTheWrongPlayerGoes() {
        val room = room(4, Settings(mode = Mode.Classic))
        room.everyoneReady()
        room.callVote(null)
        room.voteOut(room.game.seats.first { it !in room.game.imposters })
        room.afterResult()
        assertEquals(Side.Imposters, room.game.winner)
    }

    @Test
    fun ruthlessPlaysOnUntilTheImpostersCatchUp() {
        val room = room(5, Settings(mode = Mode.Ruthless, imposters = 1))
        room.everyoneReady()
        room.allClues()
        assertEquals(Phase.Vote, room.state.value.phase)
        // Crew out: 1 imposter against 3 crew, so the game goes on.
        room.voteOut(room.game.alive.first { it !in room.game.imposters })
        room.afterResult()
        assertEquals(Phase.Clues, room.state.value.phase)
        assertEquals(2, room.game.round)
        assertEquals(4, room.game.order.size)
        // Out players give no clues and can't vote.
        val out = room.game.out.single()
        assertFalse(out in room.game.order)
        room.allClues()
        room.vote(out, room.game.alive.first())
        assertFalse(out in room.game.votes)
        // Crew out again: 1 against 2, still on. Once more: 1 against 1, the imposter wins.
        room.voteOut(room.game.alive.first { it !in room.game.imposters })
        room.afterResult()
        room.allClues()
        room.voteOut(room.game.alive.first { it !in room.game.imposters })
        room.afterResult()
        assertEquals(Phase.Final, room.state.value.phase)
        assertEquals(Side.Imposters, room.game.winner)
    }

    @Test
    fun nobodyVotesForThemselves() {
        val room = room(4, Settings())
        room.everyoneReady()
        room.callVote(null)
        val me = room.game.alive.first()
        room.vote(me, me)
        assertFalse(me in room.game.votes)
        room.vote(me, null)
        assertNull(room.game.votes.getValue(me))
    }

    @Test
    fun phonesThatWentAwayArentDealtIn() {
        val room = Room("1234", clock = { now }, random = Random(1))
        repeat(3) { room.join("P$it", it, null) }
        now += AwayMillis + 1
        val back = room.join("P0", 0, room.state.value.players[0].id)!!
        room.join("New", 4, null)
        assertFalse(room.start(secret, choices))
        room.join("Another", 5, null)
        assertTrue(room.start(secret, choices))
        assertEquals(setOf(back.id) + room.state.value.players.takeLast(2).map { it.id }, room.game.seats.toSet())
    }

    @Test
    fun aSavedPlayerSitsInOneSeatOnly() {
        val room = Room("1234", clock = { now })
        val first = room.join("Dad", 0, null, profile = "dad")!!
        assertNull(room.join("Dad", 0, null, profile = "dad"))
        assertEquals(first.id, room.join("Dad", 1, first.id, profile = "dad")!!.id)
    }
}
