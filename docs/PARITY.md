# Parity checklist

Reference: iOS repo at commit `d01e7bb` (`../stock-farmer-ios`). Port conventions are in [PORTING.md](PORTING.md). Mark a row done only with tests passing; app rows also need on-device evidence.

## Core port status (Swift `StockFarmerCore` -> `core`)

Done, with their tests passing on `main`:

- Foundations: `GridPoint`, `Money` formatting, `util` (Swift-faithful random, rounding, serializers).
- Catalog leaves: Cat, ChickenVariant, Dog, FenceColor, FencePiece, Fertilizer, FishKind, GroundCover, HarvestTool, HomeUpgrade, LandscapeTool, OreKind, Rarity, Rock, SeasonalSoundtrack, Tree, UnlockOrder.
- Model leaves: Changelog, DaySales, DurationText, Egg, NameCleaning, Pet, PlayMode, Playlist, TimelapseSettings, WaterLayout.
- Feedback: AnimalChatter, MusicLoudness, PerformanceMetrics, RandomMomentTrigger, SoundFalloff, SoundPlacement, SoundSettingsMigration, VariantPicker, VoiceLimiter.
- Rules leaves: DayNightCycle, PriceJitter, Season, ShadowProjection, Sun, Weather, Wind.
- Presentation: LaunchSequence, TimelapsePlayback. Persistence: SaveBackup, SaveFileSystem, TimelapseStore. Market: StockQuoteError.

Layer 2, one compile unit because the Swift types depend on each other (develop on branch `port/layer2`, merge when `:core:test` is green):

- Catalog: Crop (+All), Building (+All, +Footprint), Decoration (+Choices, +Farmyard, +Modules), LivestockSpecies, Recipe, ArtisanGood, StorageItem.
- Model: GameState (+FullBuildings), Holding, Lesson, Livestock, Chicken, FishPond, AnimalMeal, MarketQuest, ShopItem, TractorRun, CareerRecord, ReleasedButterfly.
- Rules helpers: CropEconomy, LevelRules, LevelUnlocks, FarmlandRules, GrowthRules, Butterflies, Fireflies, NightPulse, QuestRules, Rejection, GameEvent+Rejections, StockMarketRules, TimelapseSchedule, ToolStroke, TractorNavigation, WildGrowth, WorldGeneration, FishingContest.
- Presentation: TractorBobbing, TractorMotion, FarmRenderChanges. Market: YahooChartParser, StockQuoteProvider.
- Persistence: FarmStartup, GameState+Validation, SaveCoordinator, SaveSlots, SaveStore. Feedback: SoundCue (+Events).

Layer 3: `GameEngine` and its ~55 `GameEngine+*.swift` extensions, ported by feature with their Swift tests (136 test files in total).

## App

Nothing built yet beyond the Compose shell. The Android SDK licenses must be accepted before the app module builds. Planned spikes before the real build: Compose with libGDX hosting, blurred menu backdrops on API 26, the 4096x4096 farm photo, libGDX native libraries on 16 KB page sizes.

## Slices

| Feature | Rules (core) | UI | Verified |
|---|---|---|---|
| Plant, grow, Scythe harvest, Storage sale, save and relaunch (first slice) | no | no | no |
