# Handoff: where the project stands and what to do next

Read this first in a fresh session, then `AGENTS.md`, `docs/DEVELOPMENT.md` and `docs/PARITY.md`.
Last updated on `main` (2026-11-01).

## The goal

An exact Android remake of the iOS game Stock Farmer (iOS repo: `C:\Users\hunter\workspace\stock-farmer-ios`, reference
commit `d01e7bb`; Swift sources under `StockFarmer/Sources/StockFarmer` and `StockFarmer/Core`). Same art, audio, rules and
the Yahoo stock feature. Kotlin, Jetpack Compose for screens, libGDX for the farm. iOS saves do not need to work on Android,
so old-save migrations are deliberately not ported.

Standing instructions from the user:

- Work in a single repository (`C:\Users\hunter\workspace\stock-farmer-android`) on branch `main` (all temporary worktrees, slice branches, and slice prompt files have been cleaned up).
- Clean code, SOLID, DRY, KISS, YAGNI, modern best practice, Spotless + ktlint (120 columns).
- Commit and push (`origin` = GitHub `HunterTTP/stock-farmer-android`) as `HunterTTP <hunterttp@gmail.com>`.
- Orchestrate work across **Claude Sonnet 5.5** (`claude`), **Codex Sol 6.1** (`codex.cmd`), and **Gemini Flash 3.8** (`invoke_subagent` `Model = "flash"`), preferring Claude Sonnet 5.5 -> Codex Sol 6.1 -> Gemini Flash 3.8 at `medium` or `high` reasoning effort.
- **Physical on-device testing (Lenovo tablet): DO NOT RUN unless the user explicitly asks.** Do not install, launch, inspect logs or otherwise access `192.168.1.109:37725` or `adb-HA2KYQBP*`.
- Test in the **Android Emulator** (`StockFarmerTablet` AVD), always passing the emulator serial (`-s emulator-5554`) to `adb`.

## Service rotation status (checked 2026-11-01 15:56 -05:00)

- **Gemini Flash 3.8 (`invoke_subagent` Model = `flash`)**: Rate-limited (resets at **16:16 -05:00**). Active wakeup timer `task-338` scheduled for **16:16:50 -05:00** (~20m remaining).
- **Claude Sonnet 5.5 (`claude`)**: Rate-limited (resets at **19:10 -05:00**). Active wakeup timer `task-443` scheduled for **19:10:35 -05:00**.
- **Codex Sol 6.1 (`codex.cmd`)**: Rate-limited (resets at **19:55 -05:00**). Active wakeup timer `task-554` scheduled for **19:55:01 -05:00**.

## Verified state (all green on `main`)

```
./gradlew spotlessCheck :core:test :app:assembleDebug :app:testDebugUnitTest   -> BUILD SUCCESSFUL
core tests: 1,319, 0 failures (100% of all 1,221 Swift core test cases across 132 suites mapped by tools/audit-ios-core-tests.mjs).
app unit tests: 580, 0 failures.   total unit tests: 1,899, 0 failures.   debug APK builds (85 MB, 16 KB aligned).
```

## Next steps, in order

1. **Android Emulator testing & fixing (`StockFarmerTablet` AVD)**:
   - Launch the prepared `StockFarmerTablet` AVD (`C:\Users\hunter\AppData\Local\Android\Sdk\emulator\emulator.exe -avd StockFarmerTablet`).
   - Install `app/build/outputs/apk/debug/app-debug.apk` onto the emulator (`adb -s emulator-5554 install -r ...`), launch `com.hunter.stockfarmer/.MainActivity`, play through the first session and all screens/menus/sheets, inspect screenshots and `adb -s emulator-5554 logcat`, and fix any runtime or visual issues found.
2. **Physical on-device testing (Lenovo tablet): DO NOT RUN unless the user explicitly asks.**

## Build and check

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot"
.\gradlew.bat spotlessApply spotlessCheck :core:test :app:assembleDebug :app:testDebugUnitTest --no-daemon --offline
```
