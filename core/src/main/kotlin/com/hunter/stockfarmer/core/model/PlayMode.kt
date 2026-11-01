package com.hunter.stockfarmer.core.model

import kotlinx.serialization.Serializable

@Serializable
@Suppress("ktlint:standard:enum-entry-name-case")
enum class PlayMode {
    plant,
    landscape,
    build,
    decorate,
    harvest,
    fertilize,
}
