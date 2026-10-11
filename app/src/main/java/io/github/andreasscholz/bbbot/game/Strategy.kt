package io.github.andreasscholz.bbbot.game

/**
 * The ways of playing that can be chosen in the app. They differ in one thing only: what a
 * running combo is worth compared to a board in good shape. Everything else is the same,
 * because nothing else that was tried in simulated games traded safety for points.
 *
 * In 384 simulated games each (`StrategyLab`), with up to 1000 rounds of three pieces:
 *
 * | strategy | rounds survived | points per round | points per game |
 * |----------|-----------------|------------------|-----------------|
 * | safe     | 560             | 412              | 231,000         |
 * | balanced | 465             | 1139             | 530,000         |
 * | risky    | 304             | 2054             | 625,000         |
 *
 * The points come from an imitation of the game's scoring, and the real game draws its pieces
 * differently, so the table ranks the strategies and says little about points on the phone.
 */
enum class Strategy(val weights: Weights) {
    /** Gives up a combo early to keep the board open. Long games, short combos. */
    SAFE(Weights(comboBreak = -20.0)),

    /** In between. */
    BALANCED(Weights(comboBreak = -50.0)),

    /** Holds on to a combo at the cost of the board. Most points per piece, but games end sooner. */
    RISKY(Weights(comboBreak = -110.0)),
}
