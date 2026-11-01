@file:Suppress("ktlint:standard:enum-entry-name-case")

package com.hunter.stockfarmer.core.model

/**
 * The steps of the first-time walk through: plant wheat, wait for it to ripen, pick the Scythe, harvest with it, open
 * Storage, sell it and close Storage again. Each is shown until it has been done once, in this order.
 */
enum class Lesson {
    plant,
    grow,
    pickHarvest,
    harvest,
    openStorage,
    sellItem,
    pickMax,
    sell,
    closeStorage,
}
