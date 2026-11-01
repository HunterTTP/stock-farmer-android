# Porting the Swift Core to Kotlin

The Swift `StockFarmerCore` package in `../stock-farmer-ios/StockFarmer/Core` is the spec: `Sources/StockFarmerCore` for rules, `Tests/StockFarmerCoreTests` for the tests that pin them. Port behavior exactly; do not redesign, rebalance or add features.

## Layout

Swift folders become Kotlin packages under `core/src/main/kotlin/com/hunter/stockfarmer/core/`: `model`, `catalog`, `rules`, `persistence`, `feedback`, `presentation`, `market`. Shared helpers live in `util`. A Swift file `Catalog/Crop.swift` becomes `catalog/Crop.kt`; extensions `Crop+All.swift` fold into the same Kotlin file or sit beside it. Tests mirror the layout under `core/src/test/kotlin/...`, one JUnit 5 class per Swift test file (`FooTests.swift` -> `FooTest.kt`), same test names as backticked sentences.

## Types

| Swift | Kotlin |
|---|---|
| `struct` with `Codable` | `@Serializable data class` with immutable `val`s (`kotlinx.serialization`) |
| `enum` without payloads | `enum class`, same case names in lowerCamel as constants kept lowerCamel |
| `enum` with payloads | `sealed interface` with `data class` / `data object` cases |
| `Int` | `Int` for counts and indexes; `Long` where a value can pass 2^31 |
| `Double`, `TimeInterval` | `Double` (seconds for time) |
| `Date` | `java.time.Instant` |
| `[T]`, `[K: V]`, `Set<T>` | `List<T>`, `Map<K, V>`, `Set<T>` (read-only; never share mutable collections) |
| `mutating func` on a struct | a method returning the new value, or an engine that owns its `state` and replaces it |
| `static let` tables | `val` in a `companion object` or top-level `private val` |
| `RandomNumberGenerator` | `util.RandomSource`; use the helpers in `util/RandomSource.kt` (they follow Swift's algorithms) |
| `.rounded()` | `util.roundedHalfAwayFromZero()` (Kotlin's rounding differs on halves) |
| `String(format:)` | `String.format(Locale.US, ...)` |

Keep identifiers, IDs and raw string values identical to Swift (`"red_onion"`, `"prickly_pear"`). Keep public API shapes close to Swift so UI code ports one to one. Names carry meaning; keep the Swift doc comments that explain why, translated to KDoc, and drop ones that restate the code.

## Tests

Port the Swift tests that cover the file you port, translating `#expect` to JUnit assertions. Use `SeededRandom` (test sources) in place of Swift's `SeededGenerator`; it is the same algorithm. Tests must pass without wall-clock time or system randomness. If a Swift test depends on the exact numbers Swift's random generator draws, port it as written; if it fails only because a draw differs, report that instead of weakening the test.

## Checks

Before every commit run `./gradlew spotlessApply spotlessCheck :core:test`. Commit small, meaningful pieces. No new dependencies without need.
