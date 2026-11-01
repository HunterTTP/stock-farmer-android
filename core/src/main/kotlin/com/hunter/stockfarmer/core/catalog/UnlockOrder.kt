package com.hunter.stockfarmer.core.catalog

object UnlockOrder {
    /** Retains catalog order within a level. */
    fun <Item> sorted(
        items: List<Item>,
        by: (Item) -> Int,
    ): List<Item> = items.sortedBy(by)
}
