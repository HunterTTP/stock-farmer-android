# Current state

Stock Farmer is a farming game with a live stock market. Kotlin, Jetpack Compose for screens, libGDX for the farm. The iOS repo (`../stock-farmer-ios`) is the spec for gameplay, visuals and sound; Android internals (rendering, threading, caching, persistence) follow modern Android practice and may differ for performance and hardware. See `AGENTS.md`, `docs/DEVELOPMENT.md`, `docs/CONVENTIONS.md` and `docs/FEATURES.md`.

## Gate

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot"
.\gradlew.bat spotlessApply spotlessCheck :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --continue --console=plain
```

## Verified state

`main` is expected to pass the gate above; the latest gate run is the record, so counts and APK sizes are not kept here. Screens and mechanics have had emulator passes on the `StockFarmerTablet` and `StockFarmerPhone` AVDs (no audio, software GPU). The gate does not launch the release build, and R8 once crashed it at launch while every check passed: after dependency or keep-rule changes, sign it with the debug key and launch it on an emulator.

## Structure

`game` holds the session and the shared view state and imports neither `ui` nor `farm` (`PackageLayeringTest` scans the imports and lists no exceptions). Placement routing (`selectionPlaces`, `beginPlacingSelection`, `currentPlacementOrigin`) and unlock tracking are `core/rules`. `MainActivity` is split into `GameRoot`, `LaunchOverlay` and `GameFeedbackDriver`; `Hud` into `ConfirmationDialog`, `HudMessages` and `HudNavigation`; `FarmGame` into `FarmCamera` and `FarmInput`; the item screen host is `ItemScreenHost`. `TILE` and `WORLD` are in `game/FarmProjection.kt`. Left alone on purpose: `FarmRenderer` and the long stable composables (`SaveSlotsScreen`, `TimelapseViewer`).

## Changes that need a device check

- Cancelling or dismissing a confirmation plays the cancel sound.
- A sprinkler picked up or removed mid-spray lets its drops and mist finish.
- The rebalanced mix from iOS: Master, Music, Effects and Ambience sliders in decibels, re-mastered effect clips, quieter rain, and music ducked under the level-up, quest and all-crops-ready fanfares. iOS also limits each bus with a peak limiter; SoundPool exposes no audio session to attach one to, so Android has none.

- Camera culling: no pop-in at the screen edges while panning and zooming, and a farm photo still captures everything.
- Timelapse frames still look right (they are now subsampled to the display size).
- Saves now fsync the data and the folder. Check what a save costs on real flash, as only a Windows JVM has timed it (about 7 ms more per save for a 134 KB farm). The folder sync is best effort and silently does nothing if Android refuses it, so check it succeeds too.

## Open items

- **Panning stops at the farm edge** (reported from a device run). Not reproduced on emulators at default zoom: the edge reaches screen centre. A drag starting on a placement footprint moves the placement instead of panning, as on iOS, so a large footprint while zoomed in is the likely cause (inferred). Minimum zoom and pinch were not driven.
- **A hard crash on a quiet farm loses more than a second of clock.** Autosave skips a farm where nothing but time played has changed, as iOS does, and a relaunch holds the farm's clock from the last write. So after a crash with no `onStop` (power loss, force stop), the farm's clock and time played go back to the last real change, however long ago; real changes are never more than a second behind. A slow heartbeat write would bound this, if wanted.
- **Saves written before the audit may not load.** The old-save migrations were removed (a new app has no old saves) and the save decoder rejects unknown keys, so a test save from an earlier debug build (for example one with a tractor run in progress) can fail to load and offer the backup flow. Start a new farm on devices that ran earlier builds.
- **Music is 55 MB** of the assets; re-encoding would change how it sounds, so it needs a decision.
- **Not done:** texture atlases (860 separate PNGs), a two-phase frame so the engine lock is not held across the draw (GL holds of 100-560 ms seen on a software GPU; measure on hardware first), a frame rate cap, exact-time field-ready reminders (needs a permission decision), audio focus for effects, `OldTargetApi`.
- **Wall-clock tests:** several core tests still use `Instant.now()` for tree planting (`HarvesterTest`, `PondDuckTest`, `RodFishingEngineTest`, `TreeRockEngineTest`).

## Remaining steps (owned by the user)

1. Physical phone and tablet pass with sound: the changes above, plus every new cue (Storage sale, panels, popups, photo saved, cancel), which no emulator run could hear.
2. Decided by the user: the game stays fullscreen with the Activity as sole owner of the system bars; Release keeps no confirmation, as on iOS (a quick second tap on an animal releases it); the 274 unreferenced sound effects and other unused art (about 5 MB) stay in the APK.

## Standing rules

- Do not run, install to, or inspect the physical tablet (`192.168.1.109:37725`, `adb-HA2KYQBP*`) unless the user explicitly asks.
- Always pass the emulator serial (`-s emulator-5554`) to `adb`.
