# BBBot

An Android app that plays [Block Blast](https://play.google.com/store/apps/details?id=com.block.juggle)
on its own and tries to reach the highest score it can.

BBBot sits on top of the game: it looks at the screen, works out the board and the three
pieces on offer, decides where they should go and drags them there.

## How it works

```
screenshot ──► recognition ──► solver ──► gestures
    ▲                                        │
    └────────────── next turn ◄──────────────┘
```

- **Screen access and control** go through an Android `AccessibilityService`. It can take
  screenshots, dispatch drag gestures and show a small overlay with a start/stop button on
  top of the game. No root and no PC connection are needed.
- **Recognition** turns a screenshot into the 8×8 board and the pieces in the tray.
- **The solver** tries every order and position for the pieces on offer and rates the
  resulting boards.
- **Every turn starts from a fresh screenshot**, so the bot never relies on what it thinks
  the board should look like.

## Status

| Feature                                   | State   |
|-------------------------------------------|---------|
| Project setup                             | done    |
| Game model and solver                     | done    |
| Accessibility service, overlay, gestures  | done    |
| Board and piece recognition               | done    |
| Play loop                                 | done    |
| Combo strategy                            | done    |
| Stop and take a picture at the end        | done    |
| Lookahead and selectable strategies       | done    |

## Using it

1. Install the app and open it.
2. Switch on "BBBot" in the accessibility settings; the app has a button that leads there.
   The service takes screenshots and performs gestures. It does not read the contents of
   other apps, and the app has no internet permission.
3. Open Block Blast and start a game. A small BBBot panel appears on top of the game and
   disappears when the game is left. It can be dragged by its text.
4. Choose in the app how the bot should play: safe, balanced or risky (see "Strategies").
   The choice applies the next time the bot is started.
5. Press the play button in the panel. The bot plays until it is stopped with the same
   button, the game is left or the offered pieces do not all fit any more. Touching the
   screen while it plays interrupts the piece it is dragging; it simply tries again.

When the pieces no longer fit, the bot stops without placing what still would, and saves a
screenshot of the board and the score (`final-<strategy>-…`), so the end of every game can be
looked at afterwards. Every run is also added to `games.csv` in the same folder, with the
strategy, the reason it ended (`no_room` or `stopped`), the pieces placed, the lines cleared,
the seconds it took and the name of the screenshot. The score is only in the screenshot; the
bot does not read it.

The camera button in the panel saves what the bot sees as a PNG in
`Android/data/io.github.andreasscholz.bbbot/files/captures/` on the phone, where the `final-…`
pictures go as well. The bot also saves a picture there on its own when it cannot make sense
of the screen for a while (`unreadable-…`) or when the game keeps refusing a move
(`rejected-…`). These pictures are what the recognition is developed and tested against:

```sh
adb pull /sdcard/Android/data/io.github.andreasscholz.bbbot/files/captures
```

## Recognition

The game changes its colors with its themes, so `ScreenReader` knows no colors. It assumes
only this much:

- the background reaches the left edge of the screen; the color there is the background of
  that row of pixels. How far a pixel may stray from it and still be background is measured
  on the left edge of each screenshot, because plain wood needs little room and veined marble
  a lot,
- the board is the tallest band that is not background almost all the way across, and it is
  square,
- the darkest cell of the grid is an empty one, and every cell that looks different from it
  holds a block,
- the offered pieces lie below the board, one in each third of the screen, drawn at a fixed
  fraction of the board's scale.

Screens that do not fit (menus, ads, a line being cleared, pieces still flying in) are
reported as "no game", and the bot simply looks again.

The unit tests read real screenshots from `app/src/test/resources/screens`, taken on a
1080×2400 phone in the wooden, the marble, the pink and the purple theme. The themes differ
in more than colors: the border of the board and the shadows of the pieces have different
widths, which is why the border is measured in each screenshot.

## Moving a piece

The game does not keep a dragged piece under the finger. A touched piece jumps up by about
2.25 board cells, and from there it moves one and a half times as far as the finger does.
Where the piece is grabbed makes no difference. `DragPlan` works backwards from the cells the
piece should cover to the point where the finger has to be lifted. These numbers were
measured in the game by holding pieces in the air and comparing screenshots.

## The play loop

`Player` looks, thinks and moves one piece at a time. After a drag it waits until the screen
shows the board it expects, or until two looks in a row agree, so that it does not plan on
a board whose lines are still vanishing. A piece that slipped back into the tray is simply
tried again on the next turn.

## The solver

The board is a 64-bit number, one bit per cell, so placing a piece and finding full rows and
columns are a handful of bit operations. For the pieces on offer the solver walks through
every order and every position (at most 6 · 64³ sequences) and rates the board each sequence
ends with:

- empty cells are good,
- edges between filled and empty cells are bad, which keeps the board compact,
- single empty cells enclosed on three or four sides are bad,
- room for a 3×3 square and for lines of five is good, because those are the pieces that end
  games,
- cleared rows and columns give a small bonus on top.

The best 64 sequences then get a closer look, which is too slow to give to all of them: every
shape the game knows is tried in every position on the board a sequence leaves, and the board
is rated by how good it still is after that next piece, on average over the shapes.

If the pieces cannot all be placed, the bot stops instead of placing what still fits.

### Combos

Most points come from long combos: every clear is worth more the longer the row of clears
before it. The game publishes no rules, and the guides on the web disagree on the details;
they agree that a combo lasts as long as a line is cleared within three pieces. The solver
plays by the strict reading, in which the third piece in a row that clears nothing ends the
combo.

A running combo has a worth. Ending it costs that worth, and a board costs the worth times
the chance that the combo ends on it: the chance that neither the next piece nor the ones
after it, as far as the combo still allows, clear a line. What the next piece can do is read
off the board, as the share of all shapes that could clear something at once; the pieces
after it can be set up for and are given a chance of at least 30 %.

### Strategies

How much a combo is worth is the one setting that trades safety for points, and it is what
the three strategies in the app differ in. In 384 simulated games each, with up to 1000
rounds of three pieces:

| Strategy | Combo worth | Rounds survived | Points per round | Points per game | One game in ten ends before round |
|----------|-------------|-----------------|------------------|-----------------|-----------------------------------|
| Safe     | 20          | 560             | 412              | 231,000         | 78                                |
| Balanced | 50          | 465             | 1139             | 530,000         | 79                                |
| Risky    | 110         | 304             | 2054             | 625,000         | 44                                |

For comparison, the solver before the look at the next piece: 283 rounds, 1354 points per
round, 383,000 points per game.

These games are played against uniformly drawn pieces and scored by an imitation of the game
(a point per block, ten points per line and more for several at once, times the length of
the combo). The real game draws its pieces differently and scores differently in detail, so
the table ranks the strategies against each other and says little about the points to expect
on the phone.

`StrategyLab` in the unit tests is the tool these numbers come from:

```sh
BBBOT_LAB=1 ./gradlew :app:testDebugUnitTest --rerun --tests '*StrategyLab*' -i | grep LAB
```

Also tried there, and left out because it did not help or made things worse:

- a fixed penalty for a board without room for a 3×3 square or a line of five, on top of the
  gradual rating of that room (games got about a tenth shorter),
- a penalty for every shape that fits nowhere (no effect),
- rating a board by the shape that turns out worst instead of the average (no effect),
- more reward for clearing several lines at once (shorter games, fewer points),
- more than 64 sequences for the closer look (no effect beyond 64).

## Building

Requirements: JDK 17 or newer and the Android SDK (path in `local.properties` as `sdk.dir`).

```sh
./gradlew :app:assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # JVM unit tests
```

## Development

Every feature is developed on its own branch and merged into `main` when it is finished.

## Disclaimer

A private hobby project, not affiliated with the makers of Block Blast. Automating a game
may be against its terms of service; use it at your own risk.
