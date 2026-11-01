# Conventions

## Layout

Rules code lives under `core/src/main/kotlin/com/hunter/stockfarmer/core/` in the packages `model`, `catalog`, `rules`, `persistence`, `feedback`, `presentation` and `market`. Shared helpers live in `util`. Tests mirror the layout under `core/src/test/kotlin/...`, one JUnit 5 class per subject, with backticked sentences as test names that state the rule.

## Numbers and types

- Immutable data: `@Serializable data class` with `val`s (`kotlinx.serialization`). Enums without payloads are `enum class`; with payloads, a `sealed interface` with `data class` / `data object` cases.
- `Int` for counts and indexes; `Long` wherever a value can pass 2^31 (prices reach 100 billion cents).
- `Double` for arithmetic and seconds of time; `java.time.Instant` for points in time.
- Collections are read-only `List`, `Map` and `Set`; never share mutable collections.
- Random draws go through `util.RandomSource` (its algorithms are pinned so a seeded generator reproduces the reference sequence).
- Round with `util.roundedHalfAwayFromZero()`; Kotlin's rounding differs on halves.
- Format numbers with `String.format(Locale.US, ...)`.
- Identifiers, IDs and raw string values stay stable (`"red_onion"`, `"prickly_pear"`): they appear in saves.
- Tables are a `val` in a `companion object` or a top-level `private val`.

## Style

Names carry meaning. KDoc and comments say why, briefly, and are dropped when they restate the code. Spotless with ktlint enforces formatting at 120 columns.

## Tests

Tests pass without wall-clock time or system randomness: use fixed instants and `SeededRandom` (test sources). Do not weaken a test to make it pass.

## Checks

Before every commit run `./gradlew spotlessApply spotlessCheck :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`. Commit small, meaningful pieces. Add no dependency without need.
