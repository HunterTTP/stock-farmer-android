package com.hunter.stockfarmer.core.model

import kotlinx.serialization.Serializable

/** What one kind of item sold for over a day: how many, and what they earned together. */
@Serializable
data class ItemSale(
    val itemID: String,
    val name: String,
    val quantity: Int,
    val earnings: Double,
)

/** A day's sales, shown as the next day begins. */
data class DaySummary(
    /** The day that has just begun. */
    val day: Int,
    /** What sold during the day before, the biggest earner first. */
    val sales: List<ItemSale>,
) {
    val earnings: Double get() = sales.sumOf { it.earnings }
}
