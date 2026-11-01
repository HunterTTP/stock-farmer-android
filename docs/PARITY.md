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

Builds and runs in code but has never been run on a device. What is done, in flight and next is in [HANDOFF.md](HANDOFF.md); this file keeps the per-feature parity rows.

## Slices

| Feature | Rules (core) | UI | Verified |
|---|---|---|---|
| Plant, grow, Scythe harvest, Storage sale, save and relaunch (first slice) | yes, tested | no | no |
| App save/load: active slot, backup recovery, autosave, pause/stop, clock on reopen | existing core rules; JVM integration tests added | wired, including load/save errors and backup recovery | device verification pending |
| Build/decorate/terrain/storage | core + app tests | Compose host; HUD wiring pending | device pending |
| Stock quotes: ConnectivityManager monitor, tested polling interval, offline notice | existing core rules; StockPolling JUnit tests | wired in StockMarketScreen | device pending |
| Stock market screen on HUD theme: summary card, rows, Buy Stock search flow, trade sheet and MarketQuestSection | existing core rules; JVM state and upgrade tests | HUD-themed trade sheet with `QuantityControls` (slider, steppers, direct entry, Max), House/Market Stand `BuildingUpgradeScreen` navigation, and HUD-styled `MarketQuestSection` | device pending |
| Career screen on HUD theme: level ring, square cards, collection rows and tiles, charts | existing core rules | done; section tints, entry name colors and axis ticks approximated | device pending |
| Launcher icon (mipmaps, adaptive icon) and per-farm ambience reset | n/a | done | device pending |
| Fishing mini-game | existing core contest/catch rules; app JVM session tests | Compose Canvas overlay; tap/HUD/tick wiring pending | device pending |
| Timelapse capture and playback | existing core schedule/store/playback; app JVM orchestration and timing tests | recorder, history/settings screen and full-screen player; wired (MainActivity capture loop, Timelapse HUD button) | device pending; see [TIMELAPSE.md](TIMELAPSE.md) |
| Performance, changelog, privacy, lesson guide, launch | existing core metrics/lessons/launch sequence; app JVM presentation and recorder tests | wired: LaunchScreen (loading/ready/failed only), LessonGuide + pulse, Performance/Changelog/Privacy HUD buttons; renderer instrumentation, node count and renderer launch stages unwired | device pending |
| HUD visual primitives (`ui.theme`): palette, glass panels, sheet chrome, scale, pulse, scroll bar, new dot, quantity, detail, choice list, placement, toast | n/a | Compose primitives; real HUD pending; `ScreenTheme` replaced by the ported palette | JVM tests for scale, pulse, scroll bar, quantity, palette; device pending |
| Item, tool upgrade, building and animal menus; persisted unlock dots | existing core rules and saved seen keys; app JVM builders/action tests | detail pages, tool upgrades, Next Chick/Recipes navigation, pet summon and animal naming; HUD entry points exposed | device pending; see [ITEM_SCREENS.md](ITEM_SCREENS.md) |
| Reward flights, ambience, animal voices, ready chime | existing core chatter, triggers, sound and growth rules; app JVM tests | Compose reward overlay (HUD targets) and tick-driven audio driver; wired in MainActivity and FarmGame (fireworks on first building, butterfly net on Bucket tap) | device pending; see [AMBIENCE_REWARDS.md](AMBIENCE_REWARDS.md) |
| Farm visuals: paths, grid, preview, fertilizer, growth reveal, produce badges, animal reactions and weather corrections | core unchanged; JVM presentation and gesture tests | libGDX layers; animal sheet callback and explicit growth/flash hooks documented in FARM_VISUALS.md | Spotless clean; 1,019 core and 217 app tests pass; debug APK builds; device pending |
| In-game HUD (`ui/hud`): status panels, tool circle drag, tool ring, dropdown chips and lists, placement buttons, banner placement, toasts, Time sheet, save status | n/a (feedback text mapping in `game/Feedback`) | ported from iOS `GameView` with `ToolsSheet`, `ToolRoute` stack, `PlacementDestroy`, unlock dots, `LessonGuide` and `HudSheet` wrapping | JVM tests for gesture, ring layout, wrapping, palette, route stack, banners, feedback; device pending |
| Farm gestures, scene interactions, weather overlay, tractor and wild growth drivers | existing core rules; JVM touch tests | wired in `FarmGame`, `MainActivity` and `GameTick` | 1,019 core and 344 app tests pass; device pending |

Save/load uses `LocalSaveFileSystem` under the app's private `filesDir/Farms`. `FarmStartup` prepares the active farm on a worker; failed loads block play and preserve the original until the player explicitly restores a backup. The app snapshots through `GameSession.act`, debounces timer saves, flushes on pause/stop through the disk worker, and holds the farm clock while away. JVM tests cover startup, active-slot selection, backup restoration, corrupt/newer saves, snapshot isolation, lifecycle clocks, and failed-write retry. On-device relaunch, rotation, backgrounding, and storage-failure UI remain unverified (no device available).

`MenusHost(session, openMenu, onClose)` presents the requested menu and refreshes immutable presentation snapshots.
Catalog rows show item art, money and material costs, placement limits and level locks, with building styles,
decoration sizes, fence colors, tree colors and water styles. Selections go through `GameSession.act` and return
to the farm on success; refusals display `Rejection.message`. Storage supports selecting stored items, selling,
trashing and releasing, with quantity controls and core sale totals. JVM tests cover catalog ordering and variants,
snapshot isolation, capacity, locks, engine selections, transactions and bundled picture assets. HUD, activity,
farm and session wiring are outside this slice; on-device layout and interaction remain unverified.

## Assets

`app/src/main/assets/images` is a straight copy of the iOS `Images` folder. `app/src/main/assets/audio` comes from `node tools/convert-audio.mjs <iOS Resources/Audio> app/src/main/assets/audio`: the Core Audio and WAV effects become OGG Vorbis (quality 4; run `npm install` in `tools` once for the encoder, a tool-time dependency only) and the music is copied as is. `AudioAssetsTest` reads each clip's length from its Ogg headers (`OggClip`). The effects went from 34 MB to 5.7 MB and the debug APK from 116 MB to 85 MB; the rest is music.

Weather/ambient effects: app layers and pure JVM motion tests are implemented under `farm/effects`; integration is intentionally pending. See [FARM_EFFECTS.md](FARM_EFFECTS.md) for exact renderer/scene hooks, event APIs and verification limits. Includes rain/snow and rain tint, seasonal space sky, fall leaves, wind wisps, fireflies, butterflies (including released/resting behavior), building door lights, midnight pulse, water shimmer/glow and drop palettes, fireworks, and cached sun shadows. Existing FarmGame night tint is reused. No device verification; static grass matches the frozen reference.
