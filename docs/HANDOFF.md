# Current state

Stock Farmer is a farming game with a live stock market. Kotlin, Jetpack Compose for screens, libGDX for the farm. The iOS repo (`../stock-farmer-ios`) is the behavior spec. See `AGENTS.md`, `docs/DEVELOPMENT.md`, `docs/CONVENTIONS.md` and `docs/FEATURES.md`.

## Gate

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot"
.\gradlew.bat spotlessApply spotlessCheck :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --continue --console=plain
```

## Verified state

All unit tests pass, the debug APK builds and lint passes on `main`. No emulator or device verification has been done.

## Remaining steps (owned by the user)

1. Emulator pass on the `StockFarmerTablet` AVD: install `app/build/outputs/apk/debug/app-debug.apk`, play the first session and every screen, menu and sheet, and check `logcat`.
2. Physical-tablet pass.

## Standing rules

- Do not run, install to, or inspect the physical tablet (`192.168.1.109:37725`, `adb-HA2KYQBP*`) unless the user explicitly asks.
- Always pass the emulator serial (`-s emulator-5554`) to `adb`.
