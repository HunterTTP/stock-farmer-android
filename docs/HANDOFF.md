# Current state

Stock Farmer is a farming game with a live stock market. Kotlin, Jetpack Compose for screens, libGDX for the farm. The iOS repo (`../stock-farmer-ios`) is the behavior spec. See `AGENTS.md`, `docs/DEVELOPMENT.md`, `docs/CONVENTIONS.md` and `docs/FEATURES.md`.

## Gate

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot"
.\gradlew.bat spotlessApply spotlessCheck :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --continue --console=plain
```

## Verified state

`main` passes the gate above with fresh runs: 1,286 core tests, 586 app tests, lint with no errors, debug APK 82 MB and release APK 69 MB (R8 and resource shrinking on). The debug build has been run on a device once, before the audit changes below.

## Audit changes that need a device check

- The farm shows after switching farms or retrying a failed load (the farm fragment is now always re-attached).
- Rotating the device, and split-screen play: the game now pauses on stop, not pause, and the manifest handles configuration changes.
- Edge-to-edge insets and the new app theme: no overlap, no white flash on launch.
- Camera culling: no pop-in at the screen edges while panning and zooming, and a farm photo still captures everything.
- The release build launches under R8 (`app/proguard-rules.pro` keeps libGDX and serialization classes).
- Timelapse frames still look right (they are now subsampled to the display size).

## Open items

- **Panning stops at the farm edge** (reported from a device run). The camera center is clamped to the farm square, as on iOS, so the edge should reach screen center. The code and its test agree, so the cause is not yet known. Reproduce with the zoom level and the tool or placement mode active, and check whether a drag is being taken by tile placement (`FarmGame` `pan` tries `placementDrag` first).
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
