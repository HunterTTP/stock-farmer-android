package com.hunter.stockfarmer.core.rules

/** What an action that can be refused came to: its result, or the [Rejection] that stopped it. */
internal sealed interface Attempt<out T> {
    data class Done<T>(val value: T) : Attempt<T>

    data class Refused(val rejection: Rejection) : Attempt<Nothing>
}

internal inline fun <T, R> Attempt<T>.map(transform: (T) -> R): Attempt<R> =
    when (this) {
        is Attempt.Done -> Attempt.Done(transform(value))
        is Attempt.Refused -> this
    }
