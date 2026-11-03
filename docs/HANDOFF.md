# Current state

Stock Farmer is a farming game with a live stock market. Kotlin, Jetpack Compose for screens, libGDX for the farm. The iOS repo (`../stock-farmer-ios`) is the spec for gameplay, visuals and sound; Android internals (rendering, threading, caching, persistence) follow modern Android practice and may differ for performance and hardware. See `AGENTS.md`, `docs/DEVELOPMENT.md`, `docs/CONVENTIONS.md` and `docs/FEATURES.md`.

## Gate

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot"
.\gradlew.bat spotlessApply spotlessCheck :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --continue --console=plain
```

## Verified state

`main` is expected to pass the gate above; the latest gate run is the record, so counts and APK sizes are not kept here. The debug build has been run on a device once, before the changes below.

## Structure

`game` holds the session and the shared view state and imports neither `ui` nor `farm` (`PackageLayeringTest` scans the imports and lists no exceptions). Placement routing (`selectionPlaces`, `beginPlacingSelection`, `currentPlacementOrigin`) and unlock tracking are `core/rules`. `MainActivity` is split into `GameRoot`, `LaunchOverlay` and `GameFeedbackDriver`; `Hud` into `ConfirmationDialog`, `HudMessages` and `HudNavigation`; `FarmGame` into `FarmCamera` and `FarmInput`; the item screen host is `ItemScreenHost`. `TILE` and `WORLD` are in `game/FarmProjection.kt`. Left alone on purpose: `FarmRenderer` and the long stable composables (`SaveSlotsScreen`, `TimelapseViewer`).

## Changes that need a device check

- Cancelling or dismissing a confirmation plays the cancel sound.
- A sprinkler picked up or removed mid-spray lets its drops and mist finish.

- The farm shows after switching farms or retrying a failed load (the farm fragment is now always re-attached).
- Rotating the device, and split-screen play: the game now pauses on stop, not pause, and the manifest handles configuration changes.
- Edge-to-edge insets and the new app theme: no overlap, no white flash on launch.
- Camera culling: no pop-in at the screen edges while panning and zooming, and a farm photo still captures everything.
- The release build launches under R8 (`app/proguard-rules.pro` keeps libGDX and serialization classes).
- Timelapse frames still look right (they are now subsampled to the display size).
- Saves now fsync the data and the folder. Check what a save costs on real flash, as only a Windows JVM has timed it (about 7 ms more per save for a 134 KB farm). The folder sync is best effort and silently does nothing if Android refuses it, so check it succeeds too.
- The disk thread now runs at `MIN_PRIORITY` (nice 19 on Android), raised while a load or flush waits on it. On a saturated Windows JVM one small save took 2.5 s against 0.28 s at normal priority, so autosaves can run later than the 1 s they aim for when the CPU is busy. Measure autosave delay during heavy play on a slow device; `THREAD_PRIORITY_BACKGROUND` (nice 10) is the next thing to try.
- A quiet farm should not write at all: watch `write_bytes` in `/proc/<pid>/io` with no animals on the farm. Home, then relaunch, should restore the time played.
- A failed rename, delete or restore from the Load Game screen should show its message there; iOS shows it inside that screen, Android only on the HUD behind it.

## Open items

- **Panning stops at the farm edge** (reported from a device run). The camera center is clamped to the farm square, as on iOS, so the edge should reach screen center. The code and its test agree, so the cause is not yet known. Reproduce with the zoom level and the tool or placement mode active, and check whether a drag is being taken by tile placement (`FarmGame` `pan` tries `placementDrag` first).
- **A hard crash on a quiet farm loses more than a second of clock.** Autosave skips a farm where nothing but time played has changed, as iOS does, and a relaunch holds the farm's clock from the last write. So after a crash with no `onStop` (power loss, force stop), the farm's clock and time played go back to the last real change, however long ago; real changes are never more than a second behind. A slow heartbeat write would bound this, if wanted.
- **Saves written before the audit may not load.** The old-save migrations were removed (a new app has no old saves) and the save decoder rejects unknown keys, so a test save from an earlier debug build (for example one with a tractor run in progress) can fail to load and offer the backup flow. Start a new farm on devices that ran earlier builds.
- **Music is 55 MB** of the assets; re-encoding would change how it sounds, so it needs a decision.
- **ABIs:** three are bundled; dropping `armeabi-v7a` would shrink the APK but exclude old 32-bit devices.
- **Not done:** texture atlases (860 separate PNGs), exact-time field-ready reminders (needs a permission decision), audio focus for effects, the engine lock held during draw, `OldTargetApi`.
- **Wall-clock tests:** several core tests still use `Instant.now()` for tree planting (`HarvesterTest`, `PondDuckTest`, `RodFishingEngineTest`, `TreeRockEngineTest`).

## Remaining steps (owned by the user)

1. Emulator pass on the `StockFarmerTablet` AVD: install `app/build/outputs/apk/debug/app-debug.apk`, play the first session and every screen, menu and sheet, and check `logcat`.
2. Physical-tablet pass.

## Standing rules

- Do not run, install to, or inspect the physical tablet (`192.168.1.109:37725`, `adb-HA2KYQBP*`) unless the user explicitly asks.
- Always pass the emulator serial (`-s emulator-5554`) to `adb`.
