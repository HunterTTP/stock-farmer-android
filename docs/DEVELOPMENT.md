# Development

## Environment (Windows)

- JDK 25 (Temurin) with `JAVA_HOME` set to it. Gradle 9.8 runs through the committed wrapper.
- Android SDK in `%LOCALAPPDATA%AndroidSdk`; licenses are accepted. Gradle needs `sdk.dir` in the git-ignored `local.properties` (forward slashes), which every new worktree must copy. `sdkmanager` forwards to `cmdline-toolslatestinandroid.exe sdk install <package>`.
- Devices: the Lenovo tablet is paired over wireless debugging (see `docs/HANDOFF.md`, "The tablet"); do not deploy until the end of development.

## Commands

```powershell
./gradlew spotlessApply         # format Kotlin and Gradle files (ktlint)
./gradlew spotlessCheck         # fail on style violations
./gradlew :core:test            # rules tests, no SDK needed
./gradlew :app:assembleDebug    # build the debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run `adb devices` first; install only when a device or emulator is listed.
