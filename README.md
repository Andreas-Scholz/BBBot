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

## Using it

1. Install the app and open it.
2. Switch on "BBBot" in the accessibility settings; the app has a button that leads there.
   The service takes screenshots and performs gestures. It does not read the contents of
   other apps, and the app has no internet permission.
3. Open Block Blast and start a game. A small BBBot panel appears on top of the game and
   disappears when the game is left. It can be dragged by its text.
4. Press the play button in the panel. The bot plays until it is stopped with the same
   button, the game is left or no piece fits any more. Touching the screen while it plays
   interrupts the piece it is dragging; it simply tries again.

The camera button in the panel saves what the bot sees as a PNG in
`Android/data/io.github.andreasscholz.bbbot/files/captures/` on the phone. The bot also saves
a picture there on its own (`unreadable-…`) when it cannot make sense of the screen for a
while. These pictures are what the recognition is developed and tested against:

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
1080×2400 phone in the wooden, the marble and the pink theme.

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

If the pieces cannot all be placed, the solver places as many as it can.

The weights were tuned by letting the solver play simulated games against uniformly drawn
pieces (`Simulator` in the unit tests). The real game draws its pieces differently, so those
games only rank settings against each other.

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
