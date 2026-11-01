# Handoff: where the project stands and what to do next

Read this first in a fresh session, then `AGENTS.md`, `docs/DEVELOPMENT.md` and `docs/PARITY.md`.
Last updated at `main` = `6d91f37` (2026-11-01).

## The goal

An exact Android remake of the iOS game Stock Farmer (iOS repo: `C:\Users\hunter\workspace\stock-farmer-ios`, reference
commit `d01e7bb`; Swift sources under `StockFarmer/Sources/StockFarmer` and `StockFarmer/Core`). Same art, audio, rules and
the Yahoo stock feature. Kotlin, Jetpack Compose for screens, libGDX for the farm. Eventual Play Store release (not yet
started). iOS saves do not need to work on Android, so old-save migrations are deliberately not ported.

Standing instructions from the user:

- Clean code, SOLID, DRY, KISS, YAGNI, modern best practice, a linter (Spotless + ktlint, 120 columns).
- Commit often and push (`origin` = GitHub `HunterTTP/stock-farmer-android`), one branch per slice, fast-forward merge to
  `main`. Commit as `git -c user.name=HunterTTP -c user.email=hunterttp@gmail.com commit`, message ending with
  `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` (add a `Co-Authored-By: Codex <noreply@openai.com>` line
  for Codex-written code).
- Orchestrate work across **Claude Sonnet 5.5** (`claude -p ... --model claude-sonnet-5-5`), **Codex Sol 6.1** (`codex.cmd exec -m gpt-6.1-sol`), and **Gemini Flash 3.8** (`invoke_subagent` `Model = "flash"`):
  - **Concurrency & fallback policy**: Run **2 agents per provider** by default. If a provider is rate-limited, spin up **2 additional agents of the next best choice provider** to fill the gap, with a **hard maximum of 4 agents per provider** (and bring rate-limited providers back into rotation as soon as their limits reset).
- **Model preference order by task complexity** (among services currently in rotation):
  1. **Claude Sonnet 5.5** (preferred for the most complex multi-file porting, architecture/wiring, and nuanced UI/state slices)
  2. **Codex Sol 6.1** (next preference for complex/medium feature and test slices)
  3. **Gemini Flash 3.8** (next preference for well-scoped slices, polish, and overflow when Claude/Codex are rate-limited or full)
- **Set reasoning/effort level to `medium` or higher (`high`/`max`)** matching slice complexity — never below `medium`:
  - Codex Sol 6.1: `-c model_reasoning_effort='"medium"'` (standard UI/wiring/fixes) or `'"high"'` (complex multi-file rules/rendering).
  - Claude Sonnet 5.5: `--effort medium` or `--effort high` matching slice complexity.
  - Gemini subagents: `flash` or `pro` (never `flash_lite`).
- **Do not install, launch or test on the tablet until the very end of development** (see "The tablet").
- Never claim a check passed that was not run. Use they/them for people whose pronouns are unknown.

## Service rotation status (checked 2026-11-01 13:33 -05:00)

- **Claude Sonnet 5.5 (`claude`)**: Rate-limited (`You've hit your session limit · resets 2:10pm (America/New_York)` = **14:10 -05:00**). Active wakeup timer `task-238` scheduled for **14:10:49 -05:00** (~37m remaining).
- **Codex Sol 6.1 (`codex.cmd`)**: Rate-limited (`try again at 2:54 PM` = **14:54 -05:00**). Active wakeup timer `task-65` scheduled for **14:55:26 -05:00** (~1h 22m remaining).
- **Gemini Flash 3.8 (`invoke_subagent` Model = `flash`)**: Rate-limited (`RESOURCE_EXHAUSTED 429: Individual quota reached`, resets at **16:16 -05:00**). Active wakeup timer `task-338` scheduled for **16:16:50 -05:00** (~2h 43m remaining).

## Verified state (all green on `main` = `58a0681`)

```
./gradlew spotlessCheck :core:test :app:assembleDebug :app:testDebugUnitTest   -> BUILD SUCCESSFUL (at 6d91f37)
core tests: 1,019, 0 failures.   app unit tests: 398, 0 failures.   debug APK builds (85 MB, 16 KB aligned).
```

### Completed on `main` (`736658c`)

- **Core (`core/`)**: Complete (1,019 tests passing).
- **App (`app/`)**:
  - libGDX host, `FarmRenderer`, `TextureLibrary`, `StandingSource`, `TractorLayer`, `FarmPhoto` (dedicated camera, season sky, water, finished shadows, night tint), `CountingSpriteBatch`
  - Weather & ambient effects (`farm/effects/`, `FarmWeather`), paths, grid, tile preview, fertilizer, growth reveal, produce badges, animal reactions & scene gestures (`4d9adae`, `c2b7411`)
  - Tractor & wild growth drivers, farm tap menus, placement drag, scene interactions (`c2b7411`)
  - Persistence & save slots (`GamePersistence`, `FarmSlots`, `SaveSlotsScreen`)
  - Audio (OGG Vorbis effects `9c638d3`, `SoundCenter`, `AmbienceDriver`, `AnimalVoices`, `ReadyChime`) & haptics (`HapticCue`, `Haptics`)
  - Reward flights (`RewardFlights`, `RewardFlightsHost`)
  - Timelapse capture, store, screen, and viewer (`ui/timelapse/`, HUD themed `a649e73`)
  - HUD visual primitives (`ui/theme/` `737e4d0`) & rebuilt in-game HUD (`ui/hud/` `13ae5ea`)
  - Full screens on HUD theme (`c2a192d`, `e7277e3`): `LaunchScreen` (with renderer stages `79ae019`), `LessonGuide`, `PerformanceScreen` (with sprite count `d04ca1a`), `ChangelogScreen`, `PrivacyScreen`, `CareerScreen` (with rarity & tier tints `49bb5d7`, `20d102f`), `StockMarketScreen` (with connectivity monitor `1f6e831` & Buy Stock search flow `589d3ee`)
  - Item detail screens, tool upgrade screen & flow, chick appearance screen, workshop recipe screen, building & animal sheets, and persisted unlock dots (`736658c`)
  - Sheet navigation & HUD wiring (`ToolsSheet`, `ToolRoute`, `PlacementDestroy`, unlock dots, lesson guide, `SheetHost` HUD sheet wrapping) (`bfd14d1`)
  - Stock market trade sheet (`QuantityControls`/slider/Max, House upgrade) & `MarketQuestSection` HUD polish (`4fa605d`)
  - Renderer `PerformanceMonitor` subsystem instrumentation & `LaunchScreen` failed-state `onChooseFarm` wiring (`421ce7d`)
  - Fishing overlay, Sound settings, and Building/Animal sheet sections HUD theme polish (`2d3902f`)
  - `TimelapseScreen` HUD theme conversion & `CareerScreen` collection search/filter/chart axis polish (`b15560d`)
  - `GameTick` `learnWheatIsRipe` tutorial hook & `PerformanceScreen`/`InformationScreens` HUD theme polish (`f9c20f7`)
  - 16 KB APK native alignment verification (`NativeAlignmentTest`) & `ItemDetailScreens`/`BuildingChoiceScreens`/`TimeSheet` HUD button polish (`6950222`)
  - `LaunchScreen`, `SaveStatusView`, `StatusHud`, `LessonGuide` & `RewardFlights` HUD theme polish (`a7499c7`)
  - `ToolBar`, `ToolRing`, `ToolCircle`, `ToolsSheet`, `PlacementButtons` & `HudSheet` parity audit (`e234fa0`)
  - Storage tutorial lesson progression/pulses (`StorageMenuScreen`) & `NoticeScreens`/`SaveSlotsScreen` HUD theme polish (`0881a31`)
  - Farm renderer tree/rock 96px/tile scale fix, full-workshop & dairy/oven animation parity, seasonal building textures & `FarmAssetCoverageTest` (`b9cc118`)
  - `Hud` `session.toasts` wiring & `MenusHost`/`Catalog` HUD theme parity (`e65677a`)
  - Farm touch/hold/tool-drag/scene-interactions & audio parity fixes (`6d91f37`)

## In flight right now (4 parallel slices — paused waiting for Claude reset at 14:10 -05:00)

| Slice | Branch | Worktree | Status |
|---|---|---|---|
| 1. Market session, `GamePersistence`/`FarmSlots` & `PhotoSaver`/`FarmPhotoTaker` parity audit | `app/market-persistence-photo-audit` | `..\stock-farmer-android-wt-perf-inst` | Paused (uncommitted changes ready for final test fix & commit on Claude reset) |
| 2. Farm layers geometry, anchors, `pixelsPerTile`, animated sprites & z-sorting parity audit | `app/farm-layers-geometry-audit` | `..\stock-farmer-android-wt-stock-quest` | Paused (resume on Claude reset) |
| 3. `FishingCanvas`/`FishingCast` & `TimelapseViewer`/`TimelapseRecorder` parity audit | `app/fishing-canvas-and-timelapse-audit` | `..\stock-farmer-android-wt-storage-notices` | Paused (resume on Claude reset) |
| 4. Core catalog, feedback, presentation & rules parity audit against `StockFarmerCore` | `core/parity-audit` | `..\stock-farmer-android-wt-sheet-nav` | Paused (resume on Claude reset) |

## Next steps, in order

1. Merge the 4 in-flight slices above into `main` as they complete, keeping `docs/PARITY.md` updated.
2. Run a final parity & quality audit across all screens, menus, sheets, and APK alignment (16 KB page-size check).
3. **Final testing on the tablet** (`192.168.1.109:37725` via `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`): install debug APK, launch, play through the first session, inspect `adb logcat`, and fix any runtime/visual issues.

## Build and check

JDK 25 only: `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot`. A fresh worktree needs the git-ignored
`local.properties` (`sdk.dir=C:/Users/hunter/AppData/Local/Android/Sdk`, forward slashes) copied in.

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot"
.\gradlew.bat spotlessApply spotlessCheck :core:test :app:assembleDebug :app:testDebugUnitTest --no-daemon --offline
```
