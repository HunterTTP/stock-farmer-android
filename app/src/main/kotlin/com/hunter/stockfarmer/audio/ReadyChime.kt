package com.hunter.stockfarmer.audio

data class ReadyCrop(
    val id: String,
    val name: String,
)

/** Crop-group transitions are silent on the first partial-field observation, just as on iOS. */
class ReadyChime(
    initialAllReady: Boolean = false,
) {
    private var cropsWereAllReady = initialAllReady
    private var readyCropIDs: Set<String>? = null

    fun clear(initialAllReady: Boolean = false) {
        cropsWereAllReady = initialAllReady
        readyCropIDs = null
    }

    fun update(
        isWholeFieldReady: Boolean,
        ready: List<ReadyCrop>,
    ): String? {
        val newlyReady = ready.filter { readyCropIDs?.contains(it.id) == false }
        readyCropIDs = ready.map { it.id }.toSet()
        val message =
            if (isWholeFieldReady && !cropsWereAllReady) {
                "All crops ready to harvest!"
            } else if (newlyReady.isNotEmpty()) {
                val names = newlyReady.map { it.name }
                val list =
                    if (names.size >
                        1
                    ) {
                        names.dropLast(1).joinToString(", ") + " and " + names.last()
                    } else {
                        names.single()
                    }
                "$list ${if (names.size > 1) "are" else "is"} ready to harvest!"
            } else {
                null
            }
        cropsWereAllReady = isWholeFieldReady
        return message
    }
}
