# Development

## Environment (Windows)

- Single repository (`C:\Users\hunter\workspace\stock-farmer-android`) on branch `main`.
- JDK 25 (Temurin) with `JAVA_HOME` set to `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot`. Gradle 9.8 runs through the committed wrapper.
- Android SDK in `%LOCALAPPDATA%\Android\Sdk` (`local.properties` sets `sdk.dir=C:/Users/hunter/AppData/Local/Android/Sdk`).
- Android Emulator AVD: `StockFarmerTablet` (`pixel_tablet` profile, `system-images;android-36;google_apis;x86_64`).
- **Physical device rule**: Do NOT deploy to or interact with any physical device (`192.168.1.109:37725` or `adb-HA2KYQBP*`) unless explicitly asked by the user. Always pass the emulator serial (`-s emulator-5554`) to `adb`.

## Commands

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot"
.\gradlew.bat spotlessApply spotlessCheck :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --continue --console=plain
```

Run `node --test tools/` too when `tools/` changes. Releases to testers are described in [RELEASE.md](RELEASE.md).
