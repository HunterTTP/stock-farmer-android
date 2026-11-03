# Features

Every row is implemented and covered by unit tests. Emulator and device verification of these features is pending the user.

| Feature | Rules (`core`) | App / UI (`app`) | Status |
|---|---|---|---|
| Plant, grow, Scythe harvest, Storage sale, save and relaunch | Tested | Wired end-to-end (`FarmGame`, `Hud`, `StorageMenuScreen`, `GamePersistence`); the first-planting notification question waits for the drag to end (`DeferredPrompt`, `GameSession.toolStrokes`) | Unit-tested |
| App save/load: active slot, backup recovery (centred restore dialog), autosave of real changes, pause/stop flush, clock on reopen | Tested | Wired (`GamePersistence`, `FarmSlots`, `SaveSlotsScreen`, `LaunchScreen` failed-state farm picker) | Unit-tested |
| Build, decorate, terrain, seeds, animals, trees & storage menus | Tested | `MenusHost`, `CatalogModels`, `StorageMenuScreen` with tutorial progression, `lessonPulse` & completion toast | Unit-tested |
| Stock quotes & Stock Market screen | Tested | `StockMarketSession`, `StockQuoteProvider`, `NetworkMonitor`, `StockMarketScreen`, trade sheet (`QuantityControls`) & House upgrade link | Unit-tested |
| Market Quest board & section | Tested | Every action turns in finished quests through `GameSession.publish` (news) or `GameSession.commit` (no news), as iOS `commit` does; `MarketQuestSection` with objective artwork, progress bars, replacement controls & Market Stand upgrade link | Unit-tested |
| Progress screen | Tested | Level ring, stat cards, 9 searchable collections with a filter dropdown menu, and 4-tick X/Y charts | Unit-tested |
| Fishing mini-game | Tested | `FishingMode`, `FishingCanvas`, `FishingSession`, `FishingCast`, quest-complete sound on catch, swam-away toast & reel/catch haptics | Unit-tested |
| Timelapse capture and playback | Tested | `TimelapseRecorder`, `TimelapseStore`, `TimelapseScreen`, `TimelapseViewer` (pauses `GameTick` while open) | Unit-tested |
| Performance, changelog, privacy, lesson guide & launch screens | Tested | `LaunchScreen`, `PerformanceScreen` (with subsystem timings & sprite count), `ChangelogScreen`, `PrivacyScreen` | Unit-tested |
| HUD visual primitives & in-game HUD | Tested | `StatusHud` (money and level panels sit below the safe area with a minimum gap, `HudTopSpacing`), `ToolBar`, `ToolRing` (faces sized by `RingFaceMetrics`: fractions of the button, font-scale independent), `ToolCircle`, `ToolsSheet`, `PlacementButtons`, `TimeSheet`, `SaveStatusView`, `HudTextField`/`HudSearchField` | Unit-tested |
| Item detail, tool upgrade, building & animal sheets | Tested | `ItemDetailScreens`, `ToolPalette` (lists stored items only while a tool sets down from storage), `BuildingSections`, `AnimalSection`, `BuildingChoiceScreens`, `SheetHost`, persisted unlock dots | Unit-tested |
| Reward flights, audio, ambience, animal voices, chimes & haptics | Tested | `PanelCues` (open/close panel) and `GameSession.cues` (popup and photo-saved sounds), `RewardFlights`, `SoundCenter`, `MusicPlayer`, `AmbienceDriver`, `AnimalVoices`, `ReadyChime`, `NightChime`, `Haptics` | Unit-tested |
| Settings (Sound, Graphics, Gameplay) | Tested | `SettingsScreen`, `PlaySettings`, `PlaySettingsScreens` wired to `Hud` and `FarmGame` (`isDynamicShadowsEnabled`, `shadowIntensity`, `isGridShown`, `toolReach`) | Unit-tested |
| Farm renderer, layers, weather & photo capture | Tested | `FarmRenderer`, `StandingLayout` (96 px/tile trees/rocks, 48 px/tile buildings/decorations/crops), `TreeLean`, `WeathercockTurn`, `TractorMovement`, `MovingOrigins`, `EffectLayers`, `ShadowLayer`, `NightTint` (multiplies the view by `NightShade`, as iOS does), `SprinklerLayer`/`SprinklerSprays` (drops and mist drawn just in front of each sprinkler; `GameSession.waterWithSprinklers` soaks the land at 4 Hz), duck wake in `WaterDrops` colors, `FarmPhoto` | Unit-tested |
| Natural vs. player-placed trees and rocks: only wild ones yield wood, stone and mined records | Tested (`EngineGathering`, `GameState.playerPlacedRocks`, `PlacedTree.isNaturallyGenerated`) | Nothing to show | Unit-tested |

## Assets

`app/src/main/assets/images` comes from the original game's `Images` folder (checked by `FarmAssetCoverageTest` and `MenuPicturesTest`). `app/src/main/assets/audio` is generated with `node tools/convert-audio.mjs <source Resources/Audio> app/src/main/assets/audio` (run `npm install` in `tools` once): sound effects are OGG Vorbis and music tracks are copied as-is (checked by `AudioAssetsTest` and `SoundAssetTest`).
