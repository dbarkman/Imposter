# Imposter

A party word game for the onn 4K Google TV sticks (the same ones SimpleTV and Trivia Night run
on). Everyone joins from their phone's browser with the QR code on the TV (the family's phones
are iPhones, so nothing to install). Everyone gets the same secret word except the
imposters; players take turns saying one word about it, then vote out whoever doesn't fit.
It's a personal app for home, never sold.

## Build and run

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest assembleDebug
adb -s <stick-ip>:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

The sticks are Android TV 14, 320 dpi (1 dp = 2 px), UI at 1920×1080. Plain Compose
foundation only (no tv-material), like SimpleTV and Trivia Night.

## How a game goes

1. **Lobby** — QR code and players. Phones that stop checking in for 20 seconds show as away
   and aren't dealt in. Start needs three players.
2. **Deal** — each phone shows the word, or "THE IMPOSTER" (with the category if the setting
   gives it, and the other imposters' names). Clues start once everyone taps Got it.
3. **Clues** — one turn each, in seat order, starting one further along each round; the first
   speaker of a game is never an imposter. The speaker's phone says "Your turn" and has Done;
   the TV's Next player moves on for them. Anyone still in can call a vote from their phone
   (two taps), and the TV can too.
4. **Vote** — on the phones; the TV shows who has voted and can close it (missing votes skip).
   Majority means more than half of the players still in; Everyone means all but the accused.
   A tie puts nobody out.
5. **Result** — the votes per player, who's out, then whether they were an imposter.
6. **Guess** — a caught imposter picks the word from eight on their phone (unless they had a
   close word); a right guess wins it for the imposters.
7. **Final** — who won and why, the word, everyone's part.

Modes: **Classic** plays the chosen number of clue rounds and one vote (catch an imposter to
win). **Ruthless** votes after every round and whoever's voted out is out; the crew wins when
every imposter is out, the imposters when they're as many as the crew.

Imposters on Auto: one up to 6 players, two up to 10, three beyond, never more than
can start outnumbered.

## Code map

- `Rules.kt` — settings, the imposter count, the word bank, picking the secret word (avoiding
  recent ones), the guess choices and `tally`.
- `Room.kt` — the game's state under one lock, changed by phones (server threads) and the TV,
  published as an immutable `RoomState`; `phoneView` is what one phone shows.
- `Server.kt` — plain-socket web server on port 8726 while the app is on screen. Every call
  carries the four-digit code from the QR link. `GET /` is the phone page, `GET /api/state`,
  `GET /api/players`, `POST /api/join`, `POST /api/act` (ready, done, call, vote, guess).
- `PhonePage.kt` — the phone page (HTML + JS in a Kotlin string, so no dollar signs). Polls
  about twice a second and keeps its player id in localStorage. The word card hides once the
  clues start; tap to look.
- `Players.kt`, `Household.kt` — the household's players (first name and color), shared with
  Trivia Night: both apps swap the `trivia-players` collection with SimpleTV's shared box on
  127.0.0.1:8724 using SimpleTV's sync key (`~/.config/simpletv/sync_key`, baked into
  `BuildConfig.SYNC_KEY`). Players are removed in Trivia Night's High scores.
- `LobbyScreens.kt` — lobby and settings. `GameScreens.kt` — deal, clues, vote, result, guess,
  final, and the "Press Back again" warning. `MainActivity.kt` — the screens, Back handling,
  saved settings and recent words (SharedPreferences "imposter").
- `app/src/main/assets/words.json` — 720 words in 24 family-friendly categories, written for
  this app: `[{"category", "words": [...]}]`. A close word is another word from the same
  category.
- `scripts/banner.py` — draws the TV banner (`uv run --no-project --with pillow python -I
  scripts/banner.py app/src/main/res/font app/src/main/res/drawable-xhdpi/banner.png`).

## Design

A dark plum ground, red for the imposters, mint for the crew, gold for focus. Bricolage
Grotesque for headings and Figtree for text, both bundled (OFL licences in the repo root).
The players' eight colors match Trivia Night's.
