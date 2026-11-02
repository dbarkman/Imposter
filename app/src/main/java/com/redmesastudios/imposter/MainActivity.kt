package com.redmesastudios.imposter

import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.redmesastudios.imposter.ui.Night
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random

// The room lives as long as the app's process, so phones keep their seats if the screen is
// recreated.
private object Session {
    val room = Room(newPin())
}

private const val RecentWords = 200

class MainActivity : ComponentActivity() {
    private var server: Server? = null
    private val address = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Players.load(this)
        val prefs = getSharedPreferences("imposter", MODE_PRIVATE)
        val room = Session.room
        room.changeSettings(loadSettings(prefs))
        setContent {
            val bank by produceState<WordBank?>(null) {
                value = withContext(Dispatchers.Default) { WordBank.load(applicationContext) }
            }
            val state by room.state.collectAsState()
            var settingsOpen by remember { mutableStateOf(false) }
            // Back during a game asks first; a second press within a few seconds ends it.
            var backAt by remember { mutableLongStateOf(0L) }
            val warning = SystemClock.elapsedRealtime() - backAt < 3_000
            LaunchedEffect(backAt) {
                if (backAt > 0) {
                    delay(3_000)
                    backAt = 0
                }
            }
            BackHandler(enabled = state.phase != Phase.Lobby) {
                if (warning) {
                    backAt = 0
                    room.backToLobby()
                } else {
                    backAt = SystemClock.elapsedRealtime()
                }
            }

            fun startGame() {
                val words = bank ?: return
                val recent = prefs.getString("recent", "").orEmpty().split('\n').filter { it.isNotEmpty() }
                val secret = pickSecret(words, room.state.value.settings.category, room.state.value.settings.gets, recent.toSet(), Random.Default)
                if (room.start(secret, guessChoices(words, secret, Random.Default))) {
                    prefs.edit().putString("recent", (recent + "${secret.category}|${secret.word}").takeLast(RecentWords).joinToString("\n")).apply()
                }
            }

            Box(Modifier.fillMaxSize().background(Night)) {
                val game = state.game
                AnimatedContent(
                    targetState = Triple(if (settingsOpen && state.phase == Phase.Lobby) null else state.phase, state.number, game?.round),
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                    label = "screen",
                ) { (phase, _, _) ->
                    val words = bank
                    when {
                        phase == null && words != null -> SettingsScreen(words, state.settings, room.here().size) { chosen ->
                            room.changeSettings(chosen)
                            saveSettings(prefs, chosen)
                            settingsOpen = false
                        }
                        phase == Phase.Lobby || phase == null || game == null ->
                            Lobby(state, address.value, onStart = ::startGame, onSettings = { settingsOpen = true })
                        phase == Phase.Deal -> DealScreen(state, game, onBegin = room::beginClues)
                        phase == Phase.Clues -> CluesScreen(state, game, onNext = room::nextTurn, onVote = { room.callVote(null) })
                        phase == Phase.Vote -> VoteScreen(state, game, onClose = room::closeVote)
                        phase == Phase.Result -> ResultScreen(state, game, onContinue = room::afterResult)
                        phase == Phase.Guess -> GuessScreen(state, game, onContinue = room::afterGuess)
                        phase == Phase.Final -> FinalScreen(state, game, onAgain = ::startGame, onLobby = room::backToLobby)
                    }
                }
                BackWarning(warning && state.phase != Phase.Lobby)
            }
        }
    }

    // The phones' server runs while the app is on screen.
    override fun onStart() {
        super.onStart()
        server = Server(applicationContext, Session.room).also { address.value = it.start() }
    }

    override fun onStop() {
        server?.stop()
        server = null
        super.onStop()
    }

    // Catch up on the household's players whenever the app comes back to the front.
    override fun onResume() {
        super.onResume()
        Players.sync()
    }
}

private fun loadSettings(prefs: SharedPreferences): Settings {
    val defaults = Settings()
    return Settings(
        mode = Mode.entries.firstOrNull { it.name == prefs.getString("mode", null) } ?: defaults.mode,
        imposters = prefs.getInt("imposters", defaults.imposters).coerceIn(0, 3),
        gets = ImposterGets.entries.firstOrNull { it.name == prefs.getString("gets", null) } ?: defaults.gets,
        rule = VoteRule.entries.firstOrNull { it.name == prefs.getString("rule", null) } ?: defaults.rule,
        rounds = prefs.getInt("rounds", defaults.rounds).coerceIn(1, 3),
        category = prefs.getString("category", null),
    )
}

private fun saveSettings(prefs: SharedPreferences, settings: Settings) {
    prefs.edit()
        .putString("mode", settings.mode.name)
        .putInt("imposters", settings.imposters)
        .putString("gets", settings.gets.name)
        .putString("rule", settings.rule.name)
        .putInt("rounds", settings.rounds)
        .putString("category", settings.category)
        .apply()
}
