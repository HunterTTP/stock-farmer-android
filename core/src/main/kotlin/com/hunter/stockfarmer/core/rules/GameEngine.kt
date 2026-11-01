package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.model.GameState
import java.time.Instant
import java.util.concurrent.ThreadLocalRandom

/** How far the axe has got with one tree. */
internal class ChopPass(
    val origin: com.hunter.stockfarmer.core.model.GridPoint,
    var hits: Int = 0,
    /** The last tile the axe touched, null once it has been lifted. */
    var lastTile: com.hunter.stockfarmer.core.model.GridPoint? = null,
)

/**
 * The rules of the farm. The engine owns one [GameState] and changes it as actions are taken; to preview what an action
 * would do, run it on a [copy].
 */
class GameEngine(
    var state: GameState = GameState(),
) {
    /**
     * Until when a stored thing that has just been set down stays quiet about there being none left, as the finger that
     * placed the last one is still on the farm.
     */
    internal var storagePlacementQuietUntil: Instant? = null

    /** A number from 0 up to but not including 1 for each chance the game takes, which tests replace to settle it. */
    internal var bonusRoll: () -> Double = { ThreadLocalRandom.current().nextDouble() }

    /** An independent engine in the same situation, so an action can be previewed without changing this one. */
    fun copy(): GameEngine =
        GameEngine(state.copy()).also {
            it.storagePlacementQuietUntil = storagePlacementQuietUntil
            it.bonusRoll = bonusRoll
        }

    /** Takes the cost out of the player's money, or says how much is needed if there isn't enough. */
    internal fun spend(cost: Double): Rejection? {
        if (state.money < cost) return Rejection.NeedMoney(cost)
        state.money -= cost
        return null
    }

    internal fun credit(amount: Double) {
        state.money = minOf(GameState.MAX_MONEY, state.money + amount)
    }

    companion object {
        /** How long a pond takes to bring a fish, in seconds. */
        const val FISH_INTERVAL = 30.0 * 60

        /** What destroying something refunds, as a share of what it cost. */
        const val DESTROY_REFUND_SHARE = 0.8
    }
}
