# Parity checklist

Reference: iOS repo at commit `d01e7bb` (`../stock-farmer-ios`). Port conventions are in [PORTING.md](PORTING.md). Mark a row done only with tests passing; app rows also need on-device evidence.

## Core port status (Swift `StockFarmerCore` -> `core`)

State: `main` builds, `spotlessCheck` is clean and 1010 tests pass. The whole rules engine and the persistence layer are ported.

Done:

- Foundations: `GridPoint`, `Money` formatting, `util` (Swift-faithful random, rounding, instant math, serializers), `GameStateJson` (save and load, rebuilds derived indexes).
- Catalog, model, rules, feedback, presentation, market and persistence leaves, and the whole `GameEngine` (every `GameEngine+*.swift` extension as Kotlin extension functions in `rules/Engine*.kt`, plus `FishingContest`, world generation, wild growth, tool strokes and the timelapse schedule).
- Tests ported: all Swift gameplay test files except the ones listed under "Not started" (1010 Kotlin tests), covering economy, growth, crops, seasons, storage, shop, unlocks, buildings, landscape, decorations, animals, fish, tractor, quests, stocks, workshops, trees, rocks, ore, wind, career, persistence and sound cues. Old-save migration tests are left out on purpose.

Not started:

1. Still to port: the `SoundAssetTests` check (needs the audio assets copied in first); the legacy Retired* tests (migrations, skipped).
2. Ported tests that failed pointed at real behavior differences and were fixed; keep porting this way.
3. Swift behavior that depends on exact random draws (seeded tests) may need the generator mapping checked: `util/RandomSource.kt` follows Swift's documented algorithms but could not be compared against a Swift toolchain here.

Deviations from the Swift, on purpose: old-save migrations are not ported (a new app has no old saves); `Date` is `Instant`, `Int` is `Long` where values can pass 2^31; `GameState` is a mutable object with a deep `copy()` instead of a Swift value type (see AGENTS.md); the market-quest and tractor-save upgrade paths for old saves are dropped.

## App

Nothing built yet beyond the Compose shell. The SDK licenses are accepted and `./gradlew :app:assembleDebug` builds (compileSdk 37, targetSdk 36; `local.properties` holds `sdk.dir` and is git-ignored). Planned spikes before the real build: Compose with libGDX hosting, blurred menu backdrops on API 26, the 4096x4096 farm photo, libGDX native libraries on 16 KB page sizes.

## Slices

| Feature | Rules (core) | UI | Verified |
|---|---|---|---|
| Plant, grow, Scythe harvest, Storage sale, save and relaunch (first slice) | yes, tested | no | no |
