# Development

## Environment (Windows)

- JDK 21 (Temurin) with `JAVA_HOME` set to it. Gradle 9.8 runs through the committed wrapper.
- Android SDK in `%LOCALAPPDATA%\Android\Sdk` with `ANDROID_HOME` set. Install packages with `sdkmanager` after accepting licenses.
- Real phone: enable USB debugging, then `adb devices` and authorize the computer.

## Commands

```powershell
./gradlew :core:test            # rules tests, no SDK needed
./gradlew :app:assembleDebug    # build the debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run `adb devices` first; install only when a device or emulator is listed.
