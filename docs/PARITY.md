# Parity checklist

Reference: iOS repo at commit `d01e7bb` (`../stock-farmer-ios`). Port conventions are in [PORTING.md](PORTING.md).

## Testing order and physical-device restriction

1. All code porting and unit tests are complete and passing (`:core:test` = 1,319 tests, `:app:testDebugUnitTest` = 580 tests; 1,899 total).
2. Next step: test in the **Android Emulator** (`StockFarmerTablet` AVD), recording runtime and visual evidence per feature.
3. **Physical on-device testing (Lenovo tablet): DO NOT RUN unless the user explicitly asks.** Do not install, launch, inspect logs or otherwise access `192.168.1.109:37725` or `adb-HA2KYQBP*`. Target the emulator serial explicitly (`-s emulator-5554`) for every ADB command.

## Core port status (Swift `StockFarmerCore` -> `core`)

State: `main` builds, `spotlessCheck` is clean and **1,319 core tests pass** (100% of all 1,221 Swift test cases across all 132 non-migration `StockFarmerCoreTests` suites mapped and verified by `node tools/audit-ios-core-tests.mjs`).

- Foundations: `GridPoint`, `Money` formatting, `util` (Swift-faithful random, rounding, instant math, serializers), `GameStateJson` and `GameStateDecoding` (save and load, rebuilds derived indexes).
- Catalog, model, rules, feedback, presentation, market and persistence leaves, and the whole `GameEngine` (every `GameEngine+*.swift` extension as Kotlin extension functions in `rules/Engine*.kt`, plus `FishingContest`, world generation, wild growth, tool strokes and the timelapse schedule).
- Intentional deviations from Swift: old-save migrations (`Retired*Tests`) are not ported (a new app has no old saves); `Date` is `Instant`, `Int` is `Long` where values can pass 2^31; `GameState` is a mutable object with a deep `copy()` instead of a Swift value type (see `AGENTS.md`).

## App port status (`StockFarmer` -> `app`)

State: `main` builds, `spotlessCheck` is clean, **580 app unit tests pass**, and the debug APK builds cleanly (85 MB, 16 KB uncompressed native library alignment verified by `NativeAlignmentTest`).

| Feature | Rules (`core`) | App / UI (`app`) | Status |
|---|---|---|---|
| Plant, grow, Scythe harvest, Storage sale, save and relaunch | Tested | Wired end-to-end (`FarmGame`, `Hud`, `StorageMenuScreen`, `GamePersistence`) | Emulator verification next |
| App save/load: active slot, backup recovery, autosave, pause/stop, clock on reopen | Tested | Wired (`GamePersistence`, `FarmSlots`, `SaveSlotsScreen`, `LaunchScreen` failed-state farm picker) | Emulator verification next |
| Build, decorate, terrain, seeds, animals, trees & storage menus | Tested | `MenusHost`, `CatalogModels`, `StorageMenuScreen` with tutorial progression, `lessonPulse` & completion toast | Emulator verification next |
| Stock quotes & Stock Market screen | Tested | `StockMarketSession`, `YahooStockQuoteClient`, `NetworkMonitor`, `StockMarketScreen`, trade sheet (`QuantityControls`) & House upgrade link | Emulator verification next |
| Market Quest board & section | Tested | `MarketQuestScreen` and `MarketQuestSection` with objective artwork, progress bars, replacement controls & Market Stand upgrade link | Emulator verification next |
| Career screen | Tested | Level ring, stat cards, 9 searchable collections with iOS filter dropdown menu, and 4-tick X/Y charts | Emulator verification next |
| Fishing mini-game | Tested | `FishingOverlay`, `FishingSession`, `FishingCast`, quest-complete sound on catch, swam-away toast & reel/catch haptics | Emulator verification next |
| Timelapse capture and playback | Tested | `TimelapseRecorder`, `TimelapseStore`, `TimelapseScreen`, `TimelapseViewer` (pauses `GameTick` while open) | Emulator verification next |
| Performance, changelog, privacy, lesson guide & launch screens | Tested | `LaunchScreen`, `LessonGuide`, `PerformanceScreen` (with subsystem timings & sprite count), `ChangelogScreen`, `PrivacyScreen` | Emulator verification next |
| HUD visual primitives & in-game HUD | Tested | `StatusHud`, `ToolBar`, `ToolRing`, `ToolCircle`, `ToolsSheet`, `PlacementButtons`, `TimeSheet`, `SaveStatusView`, `HudTextField`/`HudSearchField` | Emulator verification next |
| Item detail, tool upgrade, building & animal sheets | Tested | `ItemDetailScreens`, `BuildingSections`, `AnimalSection`, `BuildingChoiceScreens`, `SheetHost`, persisted unlock dots | Emulator verification next |
| Reward flights, audio, ambience, animal voices, chimes & haptics | Tested | `RewardFlights`, `SoundCenter`, `MusicPlayer`, `AmbienceDriver`, `AnimalVoices`, `ReadyChime`, `NightChime`, `Haptics` | Emulator verification next |
| Settings (Sound, Graphics, Gameplay) | Tested | `SettingsScreen`, `PlaySettings`, `PlaySettingsScreens` wired to `Hud` and `FarmGame` (`isDynamicShadowsEnabled`, `shadowIntensity`, `isGridShown`, `toolReach`) | Emulator verification next |
| Farm renderer, layers, weather & photo capture | Tested | `FarmRenderer`, `StandingLayout` (96 px/tile trees/rocks, 48 px/tile buildings/decorations/crops), `TreeLean`, `WeathercockTurn`, `TractorMovement`, `MovingOrigins`, `EffectLayers`, `ShadowLayer`, `FarmPhoto` | Emulator verification next |

## Assets

`app/src/main/assets/images` is a straight copy of the iOS `Images` folder (verified by `FarmAssetCoverageTest` and `MenuPicturesTest`). `app/src/main/assets/audio` comes from `node tools/convert-audio.mjs <iOS Resources/Audio> app/src/main/assets/audio`: sound effects are OGG Vorbis (`OggS`) and music tracks are copied as-is (verified by `AudioAssetsTest` and `SoundAssetTest`).
