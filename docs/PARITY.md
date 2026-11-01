# Parity checklist

Reference: iOS repo at commit `d01e7bb` (`../stock-farmer-ios`). Port conventions are in [PORTING.md](PORTING.md). Mark a row done only with tests passing; app rows also need on-device evidence.

## Core port status (Swift `StockFarmerCore` -> `core`)

State: `main` builds, `spotlessCheck` is clean and 172 tests pass.

Done:

- Foundations: `GridPoint`, `Money` formatting, `util` (Swift-faithful random, rounding, instant math, serializers).
- Catalog: Cat, ChickenVariant, Dog, FenceColor, FencePiece, Fertilizer, FishKind, GroundCover, HarvestTool, HomeUpgrade, LandscapeTool, OreKind, Rarity, Rock, SeasonalSoundtrack, Tree, UnlockOrder, Crop, Building, Decoration (+farmyard and module tables, generated from the Swift by script), LivestockSpecies, Recipe, ArtisanGood, StorageItem.
- Model: GameState (derived indexes, deep `copy()`), Plot, Holding and stock types, Livestock/Herd, Chicken/Coop, Pet, FishPond, AnimalMeal, TractorRun, MarketQuest, ShopItem, ReleasedButterfly, CareerRecord, Changelog, DaySales, DurationText, Egg, NameCleaning, Playlist, TimelapseSettings, WaterLayout, Lesson, PlayMode.
- Rules: Season, DayNightCycle, Sun, Weather, Wind, ShadowProjection, PriceJitter, CropEconomy, LevelRules, LevelUnlocks, FarmlandRules (+watering), GrowthRules, QuestRules, StockMarketRules, ToolUpgrade data, Fireflies, Butterflies, NightPulse, Rejection, GameEvent, a `GameEngine` shell (state, copy, spend, credit, constants).
- Feedback: AnimalChatter, MusicLoudness, PerformanceMetrics, RandomMomentTrigger, SoundFalloff, SoundPlacement, SoundSettingsMigration, VariantPicker, VoiceLimiter.
- Presentation: LaunchSequence, TimelapsePlayback. Persistence: SaveBackup, SaveFileSystem, TimelapseStore. Market: StockQuoteError.
- Tests ported: those for the above that do not need the engine (economy, growth, additional crops, season, soundtrack, plus the Codex-ported feedback, persistence and rules tests).

Not started (Swift file -> Kotlin), in a sensible order:

1. Small leaves: Market `YahooChartParser` and `StockQuoteProvider`; Presentation `TractorBobbing`, `TractorMotion`, `FarmRenderChanges`; Rules `FishingContest`, `TractorNavigation`.
2. Persistence: `GameState+Validation` (corrupt-save checks, port as a `validate()`), `SaveStore`, `SaveSlots`, `SaveCoordinator`, `FarmStartup`; Feedback `SoundCue` (+Events).
3. The engine: the rest of `GameEngine.swift` (`tap`, `useTool`, selection, `setMode`) and its ~55 `GameEngine+*.swift` extensions, ported by feature together with their Swift tests. `WorldGeneration`, `WildGrowth`, `ToolStroke` and `TimelapseSchedule` call into these, so they come with them. Extensions become Kotlin extension functions on `GameEngine`; `internal` members stay `internal`.
4. The remaining Swift tests (about 120 files), ported with each feature.

Deviations from the Swift, on purpose: old-save migrations are not ported (a new app has no old saves); `Date` is `Instant`, `Int` is `Long` where values can pass 2^31, and `GameState` is a mutable object with a deep `copy()` instead of a Swift value type (see AGENTS.md).

## App

Nothing built yet beyond the Compose shell. The Android SDK licenses must be accepted (`sdkmanager --licenses`) before the `app` module builds. Planned spikes before the real build: Compose with libGDX hosting, blurred menu backdrops on API 26, the 4096x4096 farm photo, libGDX native libraries on 16 KB page sizes.

## Slices

| Feature | Rules (core) | UI | Verified |
|---|---|---|---|
| Plant, grow, Scythe harvest, Storage sale, save and relaunch (first slice) | partial | no | no |
