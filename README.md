# Imposter

A party word game for Android TV. Everyone joins from their phone's browser by scanning the
QR code on the TV. Everyone gets the same secret word except the imposters; take turns saying
one word about it, then vote out whoever doesn't fit.

- **Classic**: a few rounds of clues, then one vote. Catch an imposter to win, unless they
  guess the word.
- **Ruthless**: a vote after every round, and whoever's voted out is out, imposter or not.
- The imposter's phone says "Imposter" where everyone else's says the word, in the same
  style, and can also give them the category. Votes need a majority or everyone.
- A scoreboard of wins and losses, as imposter and as crew.
- 720 words in 24 categories.

Built with Kotlin and Compose for the onn 4K Google TV stick; the phones need nothing
installed. See `CLAUDE.md` for how it's put together.
