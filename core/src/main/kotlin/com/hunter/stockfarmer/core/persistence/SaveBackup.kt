package com.hunter.stockfarmer.core.persistence

import java.time.Instant

/** A trailing copy of a slot's game that the restore menu can bring back. */
data class SaveBackup(
    val slot: Int,
    val kind: Kind,
    val savedAt: Instant?,
    val level: Int,
    val money: Double,
) {
    @Suppress("ktlint:standard:enum-entry-name-case")
    enum class Kind(
        val rawValue: String,
    ) {
        /** The game before its latest save. */
        latest("previous"),

        /** At least ten minutes further back, so autosaves cannot overwrite every copy. */
        older("older"),

        /** The last save before the farm clock passed midnight. */
        daily("daily"),
    }

    @Suppress("ktlint:standard:enum-entry-name-case")
    enum class RestoreError { missing, }

    val id: String get() = "$slot-" + kind.rawValue
}
