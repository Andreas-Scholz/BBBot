package io.github.andreasscholz.bbbot.game

/**
 * The ways of playing that can be chosen in the app. They differ in one thing only: how much a
 * long combo may come to be worth compared to a board in good shape. A combo is worth little
 * when it begins and more with every piece that clears something; the strategies set where
 * that stops. Everything else is the same, because nothing else that was tried in simulated
 * games traded safety for points.
 *
 * The numbers for each strategy are in the README. They come from `StrategyLab`, from games
 * against uniformly drawn pieces that are scored by an imitation of the game, so they rank the
 * strategies and say little about the points to expect on the phone.
 */
enum class Strategy(val weights: Weights) {
    /** Lets a combo go rather than spoil the board. The longest games, the fewest points. */
    SAFE(Weights(comboCap = 3.0)),

    /** The setting that scored best in simulated games. */
    BALANCED(Weights(comboCap = 11.0)),

    /**
     * Defends a long combo at a higher cost to the board. In simulated games that ended games
     * sooner without scoring more per round; it is there for a game that is kinder than they are.
     */
    RISKY(Weights(comboCap = 20.0)),
}
