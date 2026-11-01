package com.hunter.stockfarmer.ui.rewards

import com.hunter.stockfarmer.core.catalog.StorageItem

data class RewardPoint(
    val x: Double,
    val y: Double,
) {
    fun shifted(
        x: Double = 0.0,
        y: Double = 0.0,
    ): RewardPoint = RewardPoint(this.x + x, this.y + y)
}

enum class HUDTarget { Money, Level, Storage }

data class HarvestSource(
    val item: StorageItem,
    val isSold: Boolean,
)

sealed interface RewardKind {
    data class Spend(
        val value: Double,
    ) : RewardKind

    data class Earn(
        val value: Double,
    ) : RewardKind

    data class XP(
        val amount: Int,
    ) : RewardKind

    data class Item(
        val item: StorageItem,
        val count: Int,
        val isSold: Boolean,
    ) : RewardKind

    val target: HUDTarget
        get() =
            when (this) {
                is Spend, is Earn -> HUDTarget.Money
                is XP -> HUDTarget.Level
                is Item -> if (isSold) HUDTarget.Money else HUDTarget.Storage
            }
}

sealed interface RewardRoute {
    data object Direct : RewardRoute

    data object ToTally : RewardRoute

    data class FromTally(
        val offset: RewardPoint,
    ) : RewardRoute
}

data class RewardFlight(
    val id: Long,
    val kind: RewardKind,
    val start: RewardPoint,
    val launchedAt: Double,
    val startsAtBanner: Boolean = false,
    val delay: Double = 0.0,
    val route: RewardRoute = RewardRoute.Direct,
    val source: HarvestSource? = null,
) {
    val riseDuration: Double get() =
        when (route) {
            RewardRoute.Direct -> 0.35
            RewardRoute.ToTally -> 0.2
            is RewardRoute.FromTally -> 0.0
        }
    val holdDuration: Double get() = if (route == RewardRoute.Direct) 0.1 else 0.0
    val flyDuration: Double get() = if (route == RewardRoute.ToTally) 0.45 else 0.55
    val landingAt: Double get() = launchedAt + delay + riseDuration + holdDuration + flyDuration
}

data class HarvestTally(
    val items: List<Item> = emptyList(),
) {
    data class Item(
        val source: HarvestSource,
        val count: Int = 0,
        val earned: Double = 0.0,
        val xp: Int = 0,
    )

    val rows: List<List<RewardKind>>
        get() =
            items.map { item ->
                buildList {
                    if (item.earned > 0) add(RewardKind.Earn(item.earned))
                    if (item.xp > 0) add(RewardKind.XP(item.xp))
                    add(RewardKind.Item(item.source.item, item.count, item.source.isSold))
                }
            }

    val placedParts: List<Pair<RewardKind, RewardPoint>>
        get() =
            rows.flatMapIndexed { rowIndex, row ->
                row.mapIndexed { column, kind ->
                    kind to RewardPoint((column - (row.size - 1) / 2.0) * 92, rowIndex * 40.0)
                }
            }

    fun add(
        kind: RewardKind,
        source: HarvestSource,
    ): HarvestTally {
        val current = items.find { it.source == source } ?: Item(source)
        val updated =
            when (kind) {
                is RewardKind.Earn -> current.copy(earned = current.earned + kind.value)
                is RewardKind.XP -> current.copy(xp = current.xp + kind.amount)
                is RewardKind.Item -> current.copy(count = current.count + kind.count)
                is RewardKind.Spend -> current
            }
        val next =
            if (items.any { it.source == source }) {
                items.map { if (it.source == source) updated else it }
            } else {
                items + updated
            }
        return HarvestTally(next)
    }
}
