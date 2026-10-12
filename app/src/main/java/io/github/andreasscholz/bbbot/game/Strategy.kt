package io.github.andreasscholz.bbbot.game

/**
 * The ways of playing that can be chosen in the app. They differ in one thing only: how much a
 * long combo may come to be worth compared to a board in good shape. A combo is worth little
 * when it begins and more with every piece that clears something; the strategies set where
 * that stops. Everything else is the same, because nothing else that was tried in simulated
 * games traded safety for points.
 *
 * The numbers for each strategy are in the README. They come from `StrategyLab`, from games
 * against an imitation of the game that was fitted to traces, so they rank the strategies
 * rather than tell the points to expect on the phone.
 */
enum class Strategy(val weights: Weights) {
    /** Lets a combo go sooner to keep the board open. */
    SAFE(Weights(comboCap = 5.0)),

    /** The setting that scored best in simulated games. */
    BALANCED(Weights(comboCap = 11.0)),

    /**
     * Defends a long combo at a still higher cost to the board. In simulated games that scored
     * no more and ended a few more games; on the phone a bot that took its combo for much
     * longer than it was once filled the board until no round fit.
     */
    RISKY(Weights(comboCap = 25.0)),
}
