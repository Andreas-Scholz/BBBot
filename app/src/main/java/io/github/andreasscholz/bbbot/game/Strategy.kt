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
    /** Lets a combo go rather than spoil the board. The longest games. */
    SAFE(Weights(comboCap = 4.0)),

    /** In between. */
    BALANCED(Weights(comboCap = 7.0)),

    /** Defends a long combo at the cost of the board. The most points, but games end sooner. */
    RISKY(Weights(comboCap = 11.0)),
}
