# Parity checklist

Reference: iOS repo at commit `d01e7bb` (`../stock-farmer-ios`). Port conventions are in [PORTING.md](PORTING.md). Mark a row done only with tests passing; app rows also need on-device evidence.

## Core port status (Swift `StockFarmerCore` -> `core`)

State: `main` builds, `spotlessCheck` is clean and 1010 tests pass. The whole rules engine and the persistence layer are ported.

Done:

- Foundations: `GridPoint`, `Money` formatting, `util` (Swift-faithful random, rounding, instant math, serializers), `GameStateJson` (save and load, rebuilds derived indexes).
- Catalog, model, rules, feedback, presentation, market and persistence leaves, and the whole `GameEngine` (every `GameEngine+*.swift` extension as Kotlin extension functions in `rules/Engine*.kt`, plus `FishingContest`, world generation, wild growth, tool strokes and the timelapse schedule).
- Tests ported: all Swift gameplay test files except the ones listed under "Not started" (1010 Kotlin tests), covering economy, growth, crops, seasons, storage, shop, unlocks, buildings, landscape, decorations, animals, fish, tractor, quests, stocks, workshops, trees, rocks, ore, wind, career, persistence and sound cues. Old-save migration tests are left out on purpose.

Not started:

1. Still to port: the legacy Retired* tests (migrations, skipped).
2. Ported tests that failed pointed at real behavior differences and were fixed; keep porting this way.
3. Swift behavior that depends on exact random draws (seeded tests) may need the generator mapping checked: `util/RandomSource.kt` follows Swift's documented algorithms but could not be compared against a Swift toolchain here.

Deviations from the Swift, on purpose: old-save migrations are not ported (a new app has no old saves); `Date` is `Instant`, `Int` is `Long` where values can pass 2^31; `GameState` is a mutable object with a deep `copy()` instead of a Swift value type (see AGENTS.md); the market-quest and tractor-save upgrade paths for old saves are dropped.

## App

Nothing built yet beyond the Compose shell. The SDK licenses are accepted and `./gradlew :app:assembleDebug` builds (compileSdk 37, targetSdk 36; `local.properties` holds `sdk.dir` and is git-ignored). Planned spikes before the real build: Compose with libGDX hosting, blurred menu backdrops on API 26, the 4096x4096 farm photo, libGDX native libraries on 16 KB page sizes.

## Slices

| Feature | Rules (core) | UI | Verified |
|---|---|---|---|
| Plant, grow, Scythe harvest, Storage sale, save and relaunch (first slice) | yes, tested | no | no |
| App save/load: active slot, backup recovery, autosave, pause/stop, clock on reopen | existing core rules; JVM integration tests added | wired, including load/save errors and backup recovery | device verification pending |
| Build/decorate/terrain/storage | core + app tests | Compose host; HUD wiring pending | device pending |

Save/load uses `LocalSaveFileSystem` under the app's private `filesDir/Farms`. `FarmStartup` prepares the active farm on a worker; failed loads block play and preserve the original until the player explicitly restores a backup. The app snapshots through `GameSession.act`, debounces timer saves, flushes on pause/stop through the disk worker, and holds the farm clock while away. JVM tests cover startup, active-slot selection, backup restoration, corrupt/newer saves, snapshot isolation, lifecycle clocks, and failed-write retry. On-device relaunch, rotation, backgrounding, and storage-failure UI remain unverified (no device available).

`MenusHost(session, openMenu, onClose)` presents the requested menu and refreshes immutable presentation snapshots.
Catalog rows show item art, money and material costs, placement limits and level locks, with building styles,
decoration sizes, fence colors, tree colors and water styles. Selections go through `GameSession.act` and return
to the farm on success; refusals display `Rejection.message`. Storage supports selecting stored items, selling,
trashing and releasing, with quantity controls and core sale totals. JVM tests cover catalog ordering and variants,
snapshot isolation, capacity, locks, engine selections, transactions and bundled picture assets. HUD, activity,
farm and session wiring are outside this slice; on-device layout and interaction remain unverified.

## Assets

`app/src/main/assets/images` is a straight copy of the iOS `Images` folder. `app/src/main/assets/audio` comes from `node tools/convert-audio.mjs <iOS Resources/Audio> app/src/main/assets/audio`: the Core Audio effects become WAV (still 16-bit mono, checked by `SoundAssetTest`) and the music is copied as is. The effects are uncompressed, so a later pass should encode them to OGG to shrink the download.
