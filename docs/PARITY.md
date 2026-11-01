# Parity checklist

Reference: iOS repo at commit `d01e7bb` (`../stock-farmer-ios`). Port conventions are in [PORTING.md](PORTING.md). Mark a row done only with tests passing; app rows also need on-device evidence.

## Core port status (Swift `StockFarmerCore` -> `core`)

State: `main` builds, `spotlessCheck` is clean and 396 tests pass. The whole rules engine and the persistence layer are ported.

Done:

- Foundations: `GridPoint`, `Money` formatting, `util` (Swift-faithful random, rounding, instant math, serializers), `GameStateJson` (save and load, rebuilds derived indexes).
- Catalog, model, rules, feedback, presentation, market and persistence leaves, and the whole `GameEngine` (every `GameEngine+*.swift` extension as Kotlin extension functions in `rules/Engine*.kt`, plus `FishingContest`, world generation, wild growth, tool strokes and the timelapse schedule).
- Tests ported (about 80 of the 136 Swift test files, 396 tests): economy, growth, crops, seasons, soundtrack, the Wheat slice, storage and trash, shop, unlocks, levels, buildings (placement, moving, demolish, logs), landscape, harvester, eggs and axe, persistence (save store, slots, backups, coordinator, startup, validation, safety), sound cues, plus everything Codex ported for the leaves.

Not started:

1. Still to port: Presentation `FarmRenderChanges`; the `SoundAssetTests` check (needs the audio assets copied in first).
2. The remaining ~55 Swift test files, which exercise the engine through gameplay (animals, buildings, decorations, fences, fish and rod, tractor, quests, stocks, workshops, trees, rocks, ore, wind, weather easing and more). Port each with its feature; a failing port test points at a real behavior difference to fix in the engine, which was written in one pass and has so far only been checked by the tests above.
3. Swift behavior that depends on exact random draws (seeded tests) may need the generator mapping checked: `util/RandomSource.kt` follows Swift's documented algorithms but could not be compared against a Swift toolchain here.

Deviations from the Swift, on purpose: old-save migrations are not ported (a new app has no old saves); `Date` is `Instant`, `Int` is `Long` where values can pass 2^31; `GameState` is a mutable object with a deep `copy()` instead of a Swift value type (see AGENTS.md); the market-quest and tractor-save upgrade paths for old saves are dropped.

## App

Nothing built yet beyond the Compose shell. The Android SDK licenses must be accepted (`sdkmanager --licenses`) before the `app` module builds. Planned spikes before the real build: Compose with libGDX hosting, blurred menu backdrops on API 26, the 4096x4096 farm photo, libGDX native libraries on 16 KB page sizes.

## Slices

| Feature | Rules (core) | UI | Verified |
|---|---|---|---|
| Plant, grow, Scythe harvest, Storage sale, save and relaunch (first slice) | yes, tested | no | no |
