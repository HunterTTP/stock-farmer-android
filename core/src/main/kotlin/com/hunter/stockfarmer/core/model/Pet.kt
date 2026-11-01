@file:Suppress("ktlint:standard:enum-entry-name-case", "ktlint:standard:filename")

package com.hunter.stockfarmer.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class PetSpecies(
    val displayName: String,
) {
    cat("Cat"),
    dog("Dog"),
    ;

    val id: PetSpecies get() = this
    val rawValue: String get() = name
}
