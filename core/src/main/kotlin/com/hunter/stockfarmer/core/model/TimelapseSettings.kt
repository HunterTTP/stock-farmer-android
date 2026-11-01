package com.hunter.stockfarmer.core.model

import com.hunter.stockfarmer.core.util.UuidSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class TimelapseSettings(
    val isEnabled: Boolean = true,
    val captureMinute: Int = 9 * 60,
    val crossfades: Boolean = true,
    val lastCapturedDay: Int? = null,
    @Serializable(with = UuidSerializer::class) val historyID: UUID? = null,
)
